package com.seifsayed0.wasalalarm.data

import com.seifsayed0.wasalalarm.domain.*
import org.junit.Assert.*
import org.junit.Test

class LocalAppDataLogicTest {
    private fun place(id: String, name: String, lat: Double = 30.0) = SavedPlace(
        id = id, name = name, coordinate = Coordinate(lat, 31.0), radiusMeters = 500,
        createdAtMillis = 10, updatedAtMillis = 10
    )

    private fun history(id: String, ended: Long) = TripHistoryRecord(
        id = "history-$id", tripId = id, destinationName = "Place $id", destination = Coordinate(30.0, 31.0),
        radiusMeters = 1_000, startedAtMillis = ended - 10, endedAtMillis = ended,
        outcome = TripOutcome.ARRIVED, warningTriggered = false, alarmTriggered = true
    )

    @Test fun detectsNearDuplicateButCanKeepTwoPlacesExplicitly() {
        val first = place("home-1", "Home")
        val data = LocalAppData(places = listOf(first))
        val candidate = place("home-2", " home ", lat = 30.0001)
        assertEquals(first, LocalAppDataLogic.findPotentialDuplicate(data, candidate))
        assertNull(LocalAppDataLogic.addPlace(data, candidate))
        val kept = LocalAppDataLogic.addPlace(data, candidate, keepPotentialDuplicate = true)
        assertEquals(2, kept!!.places.size)
        assertNotEquals(kept.places[0].id, kept.places[1].id)
    }

    @Test fun savedPlacesCanBeUsedOfflineAndDeletedIndividually() {
        val saved = place("station", "Station")
        val data = LocalAppData(places = listOf(saved))
        assertTrue(LocalAppDataLogic.canStartOffline(data, "station"))
        assertFalse(LocalAppDataLogic.canStartOffline(data, "missing"))
        assertTrue(LocalAppDataLogic.deletePlace(data, "station").places.isEmpty())
        assertEquals(data, LocalAppDataLogic.deletePlace(data, "missing"))
    }

    @Test fun addAndUpdatePlacesRespectLimitsAndIdentifiers() {
        val first = place("one", "Home")
        val data = LocalAppData()
        assertEquals(listOf(first), LocalAppDataLogic.addPlace(data, first)!!.places)
        assertNull(LocalAppDataLogic.addPlace(data.copy(places = listOf(first)), first))
        assertNull(LocalAppDataLogic.updatePlace(data, place("missing", "Work")))
        val updated = LocalAppDataLogic.updatePlace(data.copy(places = listOf(first)), first.copy(name = "House"))!!
        assertEquals("House", updated.places.single().name)
    }

    @Test fun historyIsBoundedNewestFirstAndDeduplicatedByTripId() {
        val data = LocalAppData(settings = AppSettings(historyLimit = 2))
        val first = LocalAppDataLogic.recordHistory(data, history("a", 100))
        val second = LocalAppDataLogic.recordHistory(first, history("b", 200))
        val third = LocalAppDataLogic.recordHistory(second, history("c", 300))
        assertEquals(listOf("c", "b"), third.history.map { it.tripId })
        val replacement = LocalAppDataLogic.recordHistory(third, history("b", 400))
        assertEquals(listOf("b", "c"), replacement.history.map { it.tripId })
        assertEquals(2, replacement.history.size)
    }

    @Test fun deleteOneClearAllAndDisabledHistoryBehaveAsExpected() {
        val records = LocalAppData(history = listOf(history("a", 100), history("b", 200)))
        assertEquals(listOf("b"), LocalAppDataLogic.deleteHistory(records, "history-a").history.map { it.tripId })
        assertTrue(LocalAppDataLogic.clearHistory(records).history.isEmpty())
        val disabled = records.copy(settings = records.settings.copy(historyEnabled = false))
        assertEquals(disabled, LocalAppDataLogic.recordHistory(disabled, history("c", 300)))
    }
}
