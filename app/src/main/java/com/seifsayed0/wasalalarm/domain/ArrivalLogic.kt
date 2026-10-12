package com.seifsayed0.wasalalarm.domain

import kotlin.math.*

object ArrivalLogic {
    const val MAX_FIX_AGE_MILLIS = 120_000L
    const val FUTURE_TOLERANCE_MILLIS = 30_000L
    const val CONFIRMATION_FIXES = 2
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND = 150.0
    private const val BASE_JUMP_ALLOWANCE_METERS = 1_500.0

    fun validCoordinate(point: Coordinate): Boolean =
        point.latitude.isFinite() && point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

    fun validRadius(radiusMeters: Int): Boolean = radiusMeters in 100..50_000
    fun validWarningDistance(distanceMeters: Int): Boolean = distanceMeters in 100..20_000
    fun validPlaceName(name: String): Boolean = name.trim().isNotEmpty() && name.trim().length <= 80

    fun canRecover(trip: Trip?): Boolean {
        if (trip == null || !trip.active || trip.alarmDismissed || !validCoordinate(trip.destination) ||
            !validRadius(trip.radiusMeters) || (trip.earlyWarningEnabled && !validWarningDistance(trip.earlyWarningDistanceMeters)) ||
            trip.warningFixCount !in 0..2 || trip.finalFixCount !in 0..2 || trip.destinationName.length > 80 ||
            trip.statusMessage.length > 200 || (trip.alarmSoundUri?.length ?: 0) > 2_048 || trip.startedAtMillis < 0) return false
        if ((trip.lastLatitude == null) != (trip.lastLongitude == null)) return false
        if (trip.lastLatitude != null && trip.lastLongitude != null && !validCoordinate(Coordinate(trip.lastLatitude, trip.lastLongitude))) return false
        if (trip.lastAccuracyMeters?.let { !it.isFinite() || it <= 0f } == true) return false
        if (trip.lastDistanceMeters?.let { !it.isFinite() || it < 0f } == true) return false
        if (trip.lastLocationMillis?.let { it < 0L } == true || trip.lastLocationElapsedRealtimeMillis?.let { it < 0L } == true) return false
        return true
    }

    fun distanceMeters(a: Coordinate, b: Coordinate): Float {
        require(validCoordinate(a) && validCoordinate(b)) { "Invalid geographic coordinate" }
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val rawLon = Math.toRadians(b.longitude - a.longitude)
        val dLon = ((rawLon + Math.PI) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI) - Math.PI
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return (2.0 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))).toFloat()
    }

    /** Applies one location fix and returns the next immutable trip state plus any one-shot events. */
    fun evaluate(
        trip: Trip,
        fix: LocationFix,
        nowMillis: Long,
        nowElapsedRealtimeMillis: Long? = null
    ): FixEvaluation {
        if (!trip.active || trip.paused || trip.alarmTriggered || trip.alarmDismissed) {
            return FixEvaluation(FixStatus.TRIP_INACTIVE)
        }
        if (!validCoordinate(trip.destination) || !validRadius(trip.radiusMeters) ||
            !validCoordinate(Coordinate(fix.latitude, fix.longitude))) {
            return FixEvaluation(FixStatus.INVALID_COORDINATES)
        }
        if (!fix.accuracyMeters.isFinite() || fix.accuracyMeters <= 0f) {
            return FixEvaluation(FixStatus.INVALID_ACCURACY)
        }
        val age = if (fix.elapsedRealtimeMillis != null && nowElapsedRealtimeMillis != null) {
            nowElapsedRealtimeMillis - fix.elapsedRealtimeMillis
        } else nowMillis - fix.timeMillis
        if (age < -FUTURE_TOLERANCE_MILLIS || age > MAX_FIX_AGE_MILLIS) {
            return FixEvaluation(FixStatus.STALE)
        }
        val maxAccuracy = min(250.0, trip.radiusMeters * 0.5).toFloat()
        if (fix.accuracyMeters > maxAccuracy) return FixEvaluation(FixStatus.INACCURATE)

        val usesElapsedTime = fix.elapsedRealtimeMillis != null && trip.lastLocationElapsedRealtimeMillis != null
        val priorTime = if (usesElapsedTime) trip.lastLocationElapsedRealtimeMillis else trip.lastLocationMillis
        val currentTime = if (usesElapsedTime) fix.elapsedRealtimeMillis else fix.timeMillis
        if (priorTime != null && currentTime <= priorTime) {
            return FixEvaluation(FixStatus.OUT_OF_ORDER)
        }

        val coordinate = Coordinate(fix.latitude, fix.longitude)
        val distance = distanceMeters(trip.destination, coordinate)
        if (trip.lastLatitude != null && trip.lastLongitude != null && priorTime != null) {
            val previous = Coordinate(trip.lastLatitude, trip.lastLongitude)
            if (validCoordinate(previous)) {
                val deltaTimeSeconds = ((currentTime - priorTime).coerceAtLeast(1L)) / 1_000.0
                val movement = distanceMeters(previous, coordinate)
                val accuracyAllowance = (fix.accuracyMeters + (trip.lastAccuracyMeters ?: 0f)) * 2.0
                val allowedMovement = BASE_JUMP_ALLOWANCE_METERS +
                    MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND * deltaTimeSeconds + accuracyAllowance
                if (movement > allowedMovement) return FixEvaluation(FixStatus.JUMP, distance)
            }
        }

        val approaching = trip.lastDistanceMeters?.let { oldDistance ->
            oldDistance - distance > max(15f, fix.accuracyMeters * 0.15f)
        } ?: false
        val inEarlyBand = trip.earlyWarningEnabled && !trip.warningTriggered && !trip.alarmTriggered &&
            distance > trip.radiusMeters &&
            distance <= trip.radiusMeters + trip.earlyWarningDistanceMeters
        val warningCount = if (inEarlyBand && approaching) trip.warningFixCount + 1 else 0
        val warningNow = inEarlyBand && approaching && warningCount >= CONFIRMATION_FIXES
        // A fix is only confidently inside the final geofence when its uncertainty circle fits within it.
        val confidentlyInside = distance + fix.accuracyMeters <= trip.radiusMeters
        val finalCount = if (confidentlyInside) trip.finalFixCount + 1 else 0
        val finalNow = trip.active && !trip.paused && !trip.alarmTriggered && finalCount >= CONFIRMATION_FIXES
        val next = trip.copy(
            lastLatitude = fix.latitude,
            lastLongitude = fix.longitude,
            lastAccuracyMeters = fix.accuracyMeters,
            lastLocationMillis = fix.timeMillis,
            lastLocationElapsedRealtimeMillis = fix.elapsedRealtimeMillis,
            lastDistanceMeters = distance,
            warningFixCount = if (warningNow) 0 else warningCount,
            finalFixCount = if (finalNow) 0 else finalCount,
            warningTriggered = trip.warningTriggered || warningNow,
            alarmTriggered = trip.alarmTriggered || finalNow,
            status = when {
                finalNow || trip.alarmTriggered -> TripStatus.ARRIVED
                warningNow || trip.warningTriggered -> TripStatus.APPROACHING
                else -> TripStatus.TRACKING
            },
            statusMessage = when {
                finalNow || trip.alarmTriggered -> "وصلت إلى نطاق الوجهة"
                warningNow || trip.warningTriggered -> "اقتربت من نطاق الوجهة"
                else -> "التتبّع يعمل"
            }
        )
        return FixEvaluation(
            status = FixStatus.ACCEPTED,
            distanceMeters = distance,
            enteredRadius = finalNow,
            warningTriggered = warningNow,
            finalTriggered = finalNow,
            nextTrip = next
        )
    }

    fun markAlarmTriggered(trip: Trip): Trip =
        if (trip.active && !trip.alarmTriggered && !trip.alarmDismissed) {
            trip.copy(alarmTriggered = true, finalFixCount = 0, status = TripStatus.ARRIVED, statusMessage = "وصلت إلى نطاق الوجهة")
        } else trip

    fun dismissAlarm(trip: Trip): Trip = trip.copy(active = false, paused = false, alarmDismissed = true)
    fun pauseTrip(trip: Trip): Trip = if (trip.active && !trip.alarmTriggered) trip.copy(paused = true, status = TripStatus.PAUSED) else trip
    fun resumeTrip(trip: Trip): Trip = if (trip.active && !trip.alarmTriggered) trip.copy(paused = false, status = TripStatus.TRACKING) else trip

    fun startNewTrip(
        destination: Coordinate,
        radiusMeters: Int,
        nowMillis: Long,
        destinationName: String = "",
        settings: AppSettings = AppSettings()
    ): Trip {
        require(validCoordinate(destination))
        require(validRadius(radiusMeters))
        require(destinationName.length <= 80)
        require(!settings.earlyWarningEnabled || validWarningDistance(settings.earlyWarningDistanceMeters))
        return Trip(
            destination = destination,
            destinationName = destinationName.trim(),
            radiusMeters = radiusMeters,
            startedAtMillis = nowMillis,
            earlyWarningEnabled = settings.earlyWarningEnabled,
            earlyWarningDistanceMeters = settings.earlyWarningDistanceMeters,
            soundEnabled = settings.soundEnabled,
            vibrationEnabled = settings.vibrationEnabled,
            alarmSoundUri = settings.alarmSoundUri
        )
    }
}
