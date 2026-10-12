package com.seifsayed0.wasalalarm.domain

/** Limits online route requests while still refreshing after meaningful vehicle movement. */
object RoadRouteRequestPolicy {
    const val MIN_REQUEST_INTERVAL_MILLIS = 30_000L
    const val IDLE_REFRESH_INTERVAL_MILLIS = 60_000L
    const val MIN_MOVEMENT_METERS = 150.0

    fun shouldRequest(
        nowElapsedRealtimeMillis: Long,
        lastRequestElapsedRealtimeMillis: Long?,
        lastOrigin: Coordinate?,
        currentOrigin: Coordinate
    ): Boolean {
        if (!ArrivalLogic.validCoordinate(currentOrigin)) return false
        if (lastRequestElapsedRealtimeMillis == null || lastOrigin == null || !ArrivalLogic.validCoordinate(lastOrigin)) return true
        val elapsed = nowElapsedRealtimeMillis - lastRequestElapsedRealtimeMillis
        if (elapsed < MIN_REQUEST_INTERVAL_MILLIS) return false
        return elapsed >= IDLE_REFRESH_INTERVAL_MILLIS ||
            ArrivalLogic.distanceMeters(lastOrigin, currentOrigin) >= MIN_MOVEMENT_METERS
    }
}
