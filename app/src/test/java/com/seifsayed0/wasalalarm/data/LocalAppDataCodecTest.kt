package com.seifsayed0.wasalalarm.data

import com.seifsayed0.wasalalarm.domain.*
import org.junit.Assert.*
import org.junit.Test

class LocalAppDataCodecTest {
    @Test fun roundTripPreservesArabicPlacesSettingsAndHistory() {
        val data = LocalAppData(
            settings = AppSettings(
                defaultRadiusMeters = 2_000,
                earlyWarningEnabled = true,
                earlyWarningDistanceMeters = 1_500,
                soundEnabled = false,
                language = AppLanguage.ENGLISH,
                theme = ThemePreference.DARK,
                historyLimit = 80,
                roadDistanceEnabled = true
            ),
            places = listOf(SavedPlace(
                id = "place-1", name = "محطة مصر = وسط البلد", coordinate = Coordinate(30.06, 31.25),
                category = PlaceCategory.STATION, radiusMeters = 500, createdAtMillis = 10, updatedAtMillis = 20
            )),
            history = listOf(TripHistoryRecord(
                id = "history-1", tripId = "trip-1", destinationName = "جامعة القاهرة",
                destination = Coordinate(30.027, 31.21), radiusMeters = 1_000,
                startedAtMillis = 100, endedAtMillis = 200, outcome = TripOutcome.ARRIVED,
                warningTriggered = true, alarmTriggered = true
            ))
        )
        assertEquals(data, LocalAppDataCodec.decode(LocalAppDataCodec.encode(data)))
    }

    @Test fun rejectsUnknownVersionAndMalformedCoordinates() {
        val encoded = LocalAppDataCodec.encode(LocalAppData())
        assertNull(LocalAppDataCodec.decode(encoded.replace("version=2", "version=99")))
        val withBadPlace = LocalAppData(places = listOf(SavedPlace(
            id = "p", name = "Home", coordinate = Coordinate(91.0, 0.0), createdAtMillis = 1, updatedAtMillis = 1
        )))
        assertFalse(LocalAppDataCodec.valid(withBadPlace))
        assertNull(LocalAppDataCodec.decode("version=1\nplaces.count=1\nplace.0.lat=NaN\n"))
    }

    @Test fun legacyV1DefaultsOnlineRoadDistanceToDisabled() {
        val legacy = LocalAppDataCodec.encode(LocalAppData())
            .replace("version=2", "version=1")
            .lines()
            .filterNot { it.startsWith("settings.roadDistanceEnabled=") }
            .joinToString("\n")
        val decoded = LocalAppDataCodec.decode(legacy)
        assertNotNull(decoded)
        assertFalse(decoded!!.settings.roadDistanceEnabled)
    }

    @Test fun v2RejectsMalformedRoadDistancePreference() {
        val encoded = LocalAppDataCodec.encode(LocalAppData())
            .replace("settings.roadDistanceEnabled=false", "settings.roadDistanceEnabled=maybe")
        assertNull(LocalAppDataCodec.decode(encoded))
    }

    @Test fun validatesLimitsNamesAndUniqueIdsWithoutMergingPlaces() {
        assertTrue(LocalAppDataCodec.valid(LocalAppData()))
        val duplicateIds = LocalAppData(places = listOf(
            SavedPlace(id = "same", name = "Home", coordinate = Coordinate(1.0, 2.0), createdAtMillis = 1, updatedAtMillis = 1),
            SavedPlace(id = "same", name = "Work", coordinate = Coordinate(3.0, 4.0), createdAtMillis = 1, updatedAtMillis = 1)
        ))
        assertFalse(LocalAppDataCodec.valid(duplicateIds))
        assertFalse(LocalAppDataCodec.valid(LocalAppData(places = listOf(
            SavedPlace(id = "p", name = " ".repeat(2), coordinate = Coordinate(1.0, 2.0))
        ))))
        assertNull(LocalAppDataCodec.decode("x".repeat(LocalAppDataCodec.MAX_ENCODED_CHARS + 1)))
    }

    @Test fun invalidRadiusAndHistoryOrderingAreRejected() {
        val badSettings = LocalAppData(settings = AppSettings(defaultRadiusMeters = 50_001))
        assertFalse(LocalAppDataCodec.valid(badSettings))
        val badHistory = LocalAppData(history = listOf(TripHistoryRecord(
            id = "h", tripId = "t", destinationName = "Trip", destination = Coordinate(1.0, 2.0),
            radiusMeters = 500, startedAtMillis = 100, endedAtMillis = 99,
            outcome = TripOutcome.CANCELLED, warningTriggered = false, alarmTriggered = false
        )))
        assertFalse(LocalAppDataCodec.valid(badHistory))
    }
}
