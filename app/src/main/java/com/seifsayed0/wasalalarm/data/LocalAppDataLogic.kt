package com.seifsayed0.wasalalarm.data

import com.seifsayed0.wasalalarm.domain.*
import java.util.Locale

object LocalAppDataLogic {
    fun findPotentialDuplicate(data: LocalAppData, candidate: SavedPlace): SavedPlace? {
        val name = candidate.name.trim().lowercase(Locale.ROOT)
        return data.places.firstOrNull { existing ->
            existing.name.trim().lowercase(Locale.ROOT) == name &&
                runCatching { ArrivalLogic.distanceMeters(existing.coordinate, candidate.coordinate) <= 50f }.getOrDefault(false)
        }
    }

    fun addPlace(data: LocalAppData, place: SavedPlace, keepPotentialDuplicate: Boolean = false): LocalAppData? {
        if (!ArrivalLogic.validPlaceName(place.name) || !ArrivalLogic.validCoordinate(place.coordinate) ||
            !ArrivalLogic.validRadius(place.radiusMeters) || data.places.size >= LocalAppDataCodec.MAX_PLACES ||
            data.places.any { it.id == place.id }) return null
        if (!keepPotentialDuplicate && findPotentialDuplicate(data, place) != null) return null
        return data.copy(places = data.places + place).takeIf(LocalAppDataCodec::valid)
    }

    fun updatePlace(data: LocalAppData, place: SavedPlace): LocalAppData? {
        if (data.places.none { it.id == place.id }) return null
        val next = data.copy(places = data.places.map { if (it.id == place.id) place else it })
        return next.takeIf(LocalAppDataCodec::valid)
    }

    fun deletePlace(data: LocalAppData, placeId: String): LocalAppData =
        data.copy(places = data.places.filterNot { it.id == placeId })

    fun canStartOffline(data: LocalAppData, placeId: String): Boolean =
        data.places.firstOrNull { it.id == placeId }?.let {
            ArrivalLogic.validCoordinate(it.coordinate) && ArrivalLogic.validRadius(it.radiusMeters)
        } == true

    fun recordHistory(data: LocalAppData, record: TripHistoryRecord): LocalAppData {
        if (!data.settings.historyEnabled || !LocalAppDataCodec.valid(data.copy(history = emptyList()))) return data
        if (!ArrivalLogic.validCoordinate(record.destination) || !ArrivalLogic.validRadius(record.radiusMeters) ||
            record.tripId.isBlank() || record.endedAtMillis < record.startedAtMillis) return data
        val bounded = (listOf(record) + data.history.filterNot { it.tripId == record.tripId })
            .sortedByDescending { it.endedAtMillis }
            .take(data.settings.historyLimit.coerceIn(1, LocalAppDataCodec.MAX_HISTORY))
        return data.copy(history = bounded).takeIf(LocalAppDataCodec::valid) ?: data
    }

    fun deleteHistory(data: LocalAppData, recordId: String): LocalAppData =
        data.copy(history = data.history.filterNot { it.id == recordId })

    fun clearHistory(data: LocalAppData): LocalAppData = data.copy(history = emptyList())
}
