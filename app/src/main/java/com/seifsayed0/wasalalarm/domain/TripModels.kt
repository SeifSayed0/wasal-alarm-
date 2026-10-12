package com.seifsayed0.wasalalarm.domain

import java.util.UUID

data class Coordinate(val latitude: Double, val longitude: Double)

enum class PlaceCategory { HOME, UNIVERSITY, WORK, STATION, OTHER }
enum class AppLanguage { ARABIC, ENGLISH }
enum class ThemePreference { SYSTEM, LIGHT, DARK }
enum class TripOutcome { ARRIVED, CANCELLED, INTERRUPTED }
enum class TripStatus {
    WAITING_FOR_FIX, TRACKING, APPROACHING, ARRIVED, PAUSED, GPS_DISABLED,
    PERMISSION_REQUIRED, LOCATION_UNAVAILABLE, WEAK_SIGNAL, STALE_FIX,
    INACCURATE_FIX, GPS_JUMP, SERVICE_ERROR
}

data class SavedPlace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val coordinate: Coordinate,
    val category: PlaceCategory = PlaceCategory.OTHER,
    val radiusMeters: Int = 1_000,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis
)

data class AppSettings(
    val defaultRadiusMeters: Int = 1_000,
    val earlyWarningEnabled: Boolean = false,
    val earlyWarningDistanceMeters: Int = 1_000,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val language: AppLanguage = AppLanguage.ARABIC,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val historyEnabled: Boolean = true,
    val historyLimit: Int = 100,
    val alarmSoundUri: String? = null,
    val roadDistanceEnabled: Boolean = false
)

data class TripHistoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val tripId: String,
    val destinationName: String,
    val destination: Coordinate,
    val radiusMeters: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val outcome: TripOutcome,
    val warningTriggered: Boolean,
    val alarmTriggered: Boolean,
    val earlyWarningEnabled: Boolean = false,
    val earlyWarningDistanceMeters: Int = 1_000,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)

data class LocalAppData(
    val settings: AppSettings = AppSettings(),
    val places: List<SavedPlace> = emptyList(),
    val history: List<TripHistoryRecord> = emptyList()
)

data class Trip(
    val id: String = UUID.randomUUID().toString(),
    val destination: Coordinate,
    val radiusMeters: Int,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val active: Boolean = true,
    val alarmTriggered: Boolean = false,
    val alarmDismissed: Boolean = false,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastAccuracyMeters: Float? = null,
    val lastLocationMillis: Long? = null,
    val lastDistanceMeters: Float? = null,
    val statusMessage: String = "بانتظار الموقع",
    val status: TripStatus = TripStatus.WAITING_FOR_FIX,
    val destinationName: String = "",
    val earlyWarningEnabled: Boolean = false,
    val earlyWarningDistanceMeters: Int = 1_000,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val warningTriggered: Boolean = false,
    val warningFixCount: Int = 0,
    val finalFixCount: Int = 0,
    val paused: Boolean = false,
    val lastLocationElapsedRealtimeMillis: Long? = null,
    val alarmSoundUri: String? = null
)

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMillis: Long,
    val elapsedRealtimeMillis: Long? = null
)

enum class FixStatus {
    ACCEPTED,
    INVALID_COORDINATES,
    INVALID_ACCURACY,
    STALE,
    INACCURATE,
    OUT_OF_ORDER,
    JUMP,
    TRIP_INACTIVE
}

data class FixEvaluation(
    val status: FixStatus,
    val distanceMeters: Float? = null,
    val enteredRadius: Boolean = false,
    val warningTriggered: Boolean = false,
    val finalTriggered: Boolean = false,
    val nextTrip: Trip? = null
)
