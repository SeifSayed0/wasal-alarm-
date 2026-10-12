package com.seifsayed0.wasalalarm.data

import com.seifsayed0.wasalalarm.domain.Coordinate
import com.seifsayed0.wasalalarm.domain.Trip
import org.junit.Assert.*
import org.junit.Test

class TripRecordCodecTest {
    @Test fun activeTripAndLastValidFixRestoreExactly() {
        val original = Trip(
            id = "trip-test-7", destination = Coordinate(30.0444, 31.2357), radiusMeters = 2_000,
            startedAtMillis = 1_700_000_000L, active = true, alarmTriggered = false,
            lastLatitude = 30.05, lastLongitude = 31.24, lastAccuracyMeters = 18.5f,
            lastLocationMillis = 1_700_001_000L, lastDistanceMeters = 754.25f,
            statusMessage = "التتبّع يعمل"
        )
        val restored = TripRecordCodec.decode(TripRecordCodec.encode(original))
        assertEquals(original, restored)
    }

    @Test fun absentAndMalformedStateNeverInventCoordinates() {
        assertNull(TripRecordCodec.decode(""))
        assertNull(TripRecordCodec.decode("id=missing\nactive=true\n"))
        assertNull(TripRecordCodec.decode("id=bad\nlat=999\nlon=0\nradius=500\nstarted=1\nactive=true\n"))
    }

    @Test fun dismissalPersistsAndIsNotRecoverable() {
        val dismissed = Trip(destination = Coordinate(1.0, 2.0), radiusMeters = 500, active = false, alarmTriggered = true, alarmDismissed = true)
        val restored = TripRecordCodec.decode(TripRecordCodec.encode(dismissed))
        assertEquals(dismissed, restored)
        assertFalse(com.seifsayed0.wasalalarm.domain.ArrivalLogic.canRecover(restored))
    }

    @Test fun legacyVersionOneRecordStillRestoresWithSafeDefaults() {
        val legacy = "id=legacy-1\nlat=30.0\nlon=31.0\nradius=1000\nstarted=1700000000000\nactive=true\ntriggered=false\n"
        val restored = TripRecordCodec.decode(legacy)
        assertNotNull(restored)
        assertEquals("", restored!!.destinationName)
        assertFalse(restored.earlyWarningEnabled)
        assertTrue(restored.soundEnabled)
        assertFalse(restored.paused)
    }

    @Test fun newTripFieldsRoundTripIncludingWarningAndPauseState() {
        val original = Trip(
            id = "trip-v2", destination = Coordinate(30.0, 31.0), radiusMeters = 500,
            startedAtMillis = 1_700_000_000_000L, destinationName = "المحطة",
            earlyWarningEnabled = true, earlyWarningDistanceMeters = 1_500,
            soundEnabled = false, vibrationEnabled = true, warningTriggered = true,
            warningFixCount = 1, finalFixCount = 0, paused = true,
            lastLocationElapsedRealtimeMillis = 98_765L
        )
        assertEquals(original, TripRecordCodec.decode(TripRecordCodec.encode(original)))
    }

    @Test fun rejectsOversizedRecordsInvalidBooleansAndBadLastFixCoordinates() {
        val encoded = TripRecordCodec.encode(Trip(destination = Coordinate(30.0, 31.0), radiusMeters = 500))
        assertNull(TripRecordCodec.decode(encoded.replace("active=true", "active=perhaps")))
        assertNull(TripRecordCodec.decode(encoded + " ".repeat(TripRecordCodec.MAX_RECORD_CHARS)))
        val badLastFix = encoded + "lastLat=NaN\nlastLon=31.0\n"
        assertNull(TripRecordCodec.decode(badLastFix))
    }
}
