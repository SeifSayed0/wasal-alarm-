package com.seifsayed0.wasalalarm.data

import android.content.Context
import android.annotation.SuppressLint
import com.seifsayed0.wasalalarm.domain.Coordinate
import com.seifsayed0.wasalalarm.domain.ArrivalLogic
import com.seifsayed0.wasalalarm.domain.Trip
import com.seifsayed0.wasalalarm.domain.TripStatus
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

sealed interface TripRead {
    data class Loaded(val trip: Trip) : TripRead
    data object Missing : TripRead
    data object Corrupt : TripRead
}

/** Minimal local-only active-trip persistence. No coordinates leave the device through this store. */
class TripStore(context: Context) {
    private val prefs = context.getSharedPreferences("wasal_trip_v1", Context.MODE_PRIVATE)

    @SuppressLint("ApplySharedPref", "UseKtx")
    fun save(trip: Trip): Boolean = synchronized(PROCESS_LOCK) {
        val encoded = runCatching { TripRecordCodec.encode(trip) }.getOrNull() ?: return false
        val editor = prefs.edit()
        val existing = prefs.getString(KEY, null)
        if (existing != null && TripRecordCodec.decode(existing) == null && !prefs.contains(CORRUPT_BACKUP_KEY)) {
            editor.putString(CORRUPT_BACKUP_KEY, existing.take(TripRecordCodec.MAX_RECORD_CHARS))
        }
        editor.putString(KEY, encoded).commit()
    }

    fun read(): TripRead = synchronized(PROCESS_LOCK) {
        val raw = prefs.getString(KEY, null) ?: return TripRead.Missing
        TripRecordCodec.decode(raw)?.let(TripRead::Loaded) ?: TripRead.Corrupt
    }

    fun load(): Trip? = (read() as? TripRead.Loaded)?.trip

    @SuppressLint("ApplySharedPref", "UseKtx")
    fun clear(): Boolean = synchronized(PROCESS_LOCK) { prefs.edit().remove(KEY).commit() }

    private companion object {
        const val KEY = "active_trip"
        const val CORRUPT_BACKUP_KEY = "corrupt_active_trip_backup"
        val PROCESS_LOCK = Any()
    }
}

/** Properties-backed codec kept pure so restoration/corrupt-state cases are unit-testable. */
object TripRecordCodec {
    const val MAX_RECORD_CHARS = 10_000

    fun encode(trip: Trip): String {
        require(validStoredTrip(trip)) { "Invalid active trip" }
        val p = Properties().apply {
            setProperty("recordVersion", "2")
            setProperty("id", trip.id)
            setProperty("lat", trip.destination.latitude.toString())
            setProperty("lon", trip.destination.longitude.toString())
            setProperty("radius", trip.radiusMeters.toString())
            setProperty("started", trip.startedAtMillis.toString())
            setProperty("active", trip.active.toString())
            setProperty("triggered", trip.alarmTriggered.toString())
            setProperty("dismissed", trip.alarmDismissed.toString())
            setProperty("status", trip.statusMessage)
            setProperty("statusCode", trip.status.name)
            setProperty("destinationName", trip.destinationName.take(80))
            setProperty("warningEnabled", trip.earlyWarningEnabled.toString())
            setProperty("warningDistance", trip.earlyWarningDistanceMeters.toString())
            setProperty("soundEnabled", trip.soundEnabled.toString())
            setProperty("vibrationEnabled", trip.vibrationEnabled.toString())
            setProperty("warningTriggered", trip.warningTriggered.toString())
            setProperty("warningFixCount", trip.warningFixCount.toString())
            setProperty("finalFixCount", trip.finalFixCount.toString())
            setProperty("paused", trip.paused.toString())
            trip.alarmSoundUri?.let { setProperty("alarmSoundUri", it.take(2_048)) }
            trip.lastLatitude?.let { setProperty("lastLat", it.toString()) }
            trip.lastLongitude?.let { setProperty("lastLon", it.toString()) }
            trip.lastAccuracyMeters?.let { setProperty("accuracy", it.toString()) }
            trip.lastLocationMillis?.let { setProperty("lastTime", it.toString()) }
            trip.lastDistanceMeters?.let { setProperty("distance", it.toString()) }
            trip.lastLocationElapsedRealtimeMillis?.let { setProperty("lastElapsed", it.toString()) }
        }
        return StringWriter().also { p.store(it, null) }.toString().also {
            require(it.length <= MAX_RECORD_CHARS) { "Trip record exceeds storage limit" }
        }
    }

    fun decode(raw: String): Trip? {
        return try {
            if (raw.length > MAX_RECORD_CHARS) return null
            val p = Properties().apply { load(StringReader(raw)) }
            fun boolean(key: String, default: Boolean): Boolean {
                val value = p.getProperty(key) ?: return default
                return value.toBooleanStrictOrNull() ?: throw IllegalArgumentException("Invalid $key")
            }
            val version = p.getProperty("recordVersion")?.toIntOrNull() ?: 1
            if (version !in 1..2) return null
            val latitude = p.getProperty("lat")?.toDouble() ?: return null
            val longitude = p.getProperty("lon")?.toDouble() ?: return null
            val lastLatitude = p.getProperty("lastLat")?.toDoubleOrNull()
            val lastLongitude = p.getProperty("lastLon")?.toDoubleOrNull()
            if ((p.getProperty("lastLat") != null && lastLatitude == null) || (p.getProperty("lastLon") != null && lastLongitude == null)) return null
            if ((lastLatitude == null) != (lastLongitude == null)) return null
            val accuracy = p.getProperty("accuracy")?.toFloatOrNull()
            val distance = p.getProperty("distance")?.toFloatOrNull()
            if ((p.getProperty("accuracy") != null && accuracy == null) || (p.getProperty("distance") != null && distance == null)) return null
            if (accuracy != null && (!accuracy.isFinite() || accuracy <= 0f)) return null
            if (distance != null && (!distance.isFinite() || distance < 0f)) return null
            val started = p.getProperty("started")?.toLong() ?: return null
            if (started < 0L) return null
            val warningEnabled = boolean("warningEnabled", false)
            val warningDistance = p.getProperty("warningDistance")?.toIntOrNull() ?: 1_000
            val warningCount = p.getProperty("warningFixCount")?.toIntOrNull() ?: 0
            val finalCount = p.getProperty("finalFixCount")?.toIntOrNull() ?: 0
            val destinationName = p.getProperty("destinationName") ?: ""
            val status = p.getProperty("status") ?: "بانتظار الموقع"
            val statusCodeRaw = p.getProperty("statusCode")
            val parsedStatusCode = statusCodeRaw?.let { raw -> TripStatus.entries.firstOrNull { it.name == raw } }
            if (version >= 2 && statusCodeRaw != null && parsedStatusCode == null) return null
            val alarmSoundUri = p.getProperty("alarmSoundUri")
            if (alarmSoundUri != null && alarmSoundUri.length > 2_048) return null
            val statusCode = parsedStatusCode ?: when {
                    status.contains("وصلت") -> TripStatus.ARRIVED
                    status.contains("اقتربت") -> TripStatus.APPROACHING
                    status.contains("تتبع يعمل") -> TripStatus.TRACKING
                    status.contains("مؤقتاً") -> TripStatus.PAUSED
                    status.contains("إذن") -> TripStatus.PERMISSION_REQUIRED
                    else -> TripStatus.WAITING_FOR_FIX
                }
            if (destinationName.length > 80 || status.length > 200 || warningCount !in 0..2 || finalCount !in 0..2 ||
                (warningEnabled && !ArrivalLogic.validWarningDistance(warningDistance))) return null
            val trip = Trip(
                id = p.getProperty("id")?.takeIf { it.isNotBlank() && it.length <= 100 } ?: return null,
                destination = Coordinate(latitude, longitude),
                radiusMeters = p.getProperty("radius")?.toInt() ?: return null,
                startedAtMillis = started,
                active = p.getProperty("active")?.let { boolean("active", false) } ?: return null,
                alarmTriggered = boolean("triggered", false),
                alarmDismissed = boolean("dismissed", false),
                lastLatitude = lastLatitude,
                lastLongitude = lastLongitude,
                lastAccuracyMeters = accuracy,
                lastLocationMillis = p.getProperty("lastTime")?.toLongOrNull(),
                lastDistanceMeters = distance,
                statusMessage = status,
                status = statusCode,
                destinationName = destinationName,
                earlyWarningEnabled = warningEnabled,
                earlyWarningDistanceMeters = warningDistance,
                soundEnabled = boolean("soundEnabled", true),
                vibrationEnabled = boolean("vibrationEnabled", true),
                warningTriggered = boolean("warningTriggered", false),
                warningFixCount = warningCount,
                finalFixCount = finalCount,
                paused = boolean("paused", false),
                lastLocationElapsedRealtimeMillis = p.getProperty("lastElapsed")?.toLongOrNull(),
                alarmSoundUri = alarmSoundUri
            )
            if ((p.getProperty("lastTime") != null && trip.lastLocationMillis == null) ||
                (p.getProperty("lastElapsed") != null && trip.lastLocationElapsedRealtimeMillis == null)) return null
            trip.takeIf(::validStoredTrip)
        } catch (_: Exception) {
            null
        }
    }

    private fun validStoredTrip(trip: Trip): Boolean =
        ArrivalLogic.validCoordinate(trip.destination) && ArrivalLogic.validRadius(trip.radiusMeters) &&
            trip.id.isNotBlank() && trip.id.length <= 100 && trip.destinationName.length <= 80 &&
            trip.statusMessage.length <= 200 && (trip.alarmSoundUri?.length ?: 0) <= 2_048 &&
            trip.startedAtMillis >= 0 && (!trip.earlyWarningEnabled || ArrivalLogic.validWarningDistance(trip.earlyWarningDistanceMeters)) &&
            trip.warningFixCount in 0..2 && trip.finalFixCount in 0..2 &&
            ((trip.lastLatitude == null && trip.lastLongitude == null) ||
                (trip.lastLatitude != null && trip.lastLongitude != null && ArrivalLogic.validCoordinate(Coordinate(trip.lastLatitude, trip.lastLongitude)))) &&
            trip.lastAccuracyMeters?.let { it.isFinite() && it > 0f } != false &&
            trip.lastDistanceMeters?.let { it.isFinite() && it >= 0f } != false &&
            trip.lastLocationMillis?.let { it >= 0L } != false &&
            trip.lastLocationElapsedRealtimeMillis?.let { it >= 0L } != false
}
