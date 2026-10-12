package com.seifsayed0.wasalalarm.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ArrivalLogicTest {
    private val destination = Coordinate(30.0444, 31.2357)
    private val now = 1_800_000_000_000L
    private fun trip(radius: Int = 500) = Trip(destination = destination, radiusMeters = radius, startedAtMillis = now)

    private fun pointAtMeters(northMeters: Double): Coordinate = Coordinate(
        destination.latitude + Math.toDegrees(northMeters / 6_371_000.0), destination.longitude
    )

    private fun fixAt(meters: Double, time: Long, accuracy: Float = 10f) =
        pointAtMeters(meters).let { LocationFix(it.latitude, it.longitude, accuracy, time) }

    @Test fun finalAlarmRequiresTwoFixesWhoseAccuracyCircleFitsInsideRadius() {
        val t = trip(500)
        val inside = LocationFix(destination.latitude, destination.longitude, 5f, now)
        val first = ArrivalLogic.evaluate(t, inside, now)
        assertEquals(FixStatus.ACCEPTED, first.status)
        assertFalse(first.finalTriggered)
        val second = ArrivalLogic.evaluate(first.nextTrip!!, inside.copy(timeMillis = now + 10_000), now + 10_000)
        assertTrue(second.finalTriggered)
        assertEquals(0f, second.distanceMeters!!, 0.01f)

        val uncertainEdge = fixAt(495.0, now, accuracy = 10f)
        val edgeFirst = ArrivalLogic.evaluate(t, uncertainEdge, now)
        val edgeSecond = ArrivalLogic.evaluate(edgeFirst.nextTrip!!, uncertainEdge.copy(timeMillis = now + 10_000), now + 10_000)
        assertFalse(edgeSecond.finalTriggered)

        val confidentlyInside = fixAt(480.0, now, accuracy = 10f)
        val insideFirst = ArrivalLogic.evaluate(t, confidentlyInside, now)
        val insideSecond = ArrivalLogic.evaluate(insideFirst.nextTrip!!, confidentlyInside.copy(timeMillis = now + 10_000), now + 10_000)
        assertTrue(insideSecond.finalTriggered)

        val outside = fixAt(500.5, now)
        assertFalse(ArrivalLogic.evaluate(t, outside, now).finalTriggered)
    }

    @Test fun rejectsInvalidCoordinatesRadiusStaleFutureAndInaccurateFixes() {
        assertFalse(ArrivalLogic.validCoordinate(Coordinate(91.0, 0.0)))
        assertFalse(ArrivalLogic.validCoordinate(Coordinate(Double.NaN, 0.0)))
        assertFalse(ArrivalLogic.validRadius(99))
        assertFalse(ArrivalLogic.validRadius(50_001))
        assertTrue(ArrivalLogic.validWarningDistance(100))
        assertFalse(ArrivalLogic.validWarningDistance(99))
        val badTrip = trip().copy(destination = Coordinate(999.0, 0.0))
        assertEquals(FixStatus.INVALID_COORDINATES, ArrivalLogic.evaluate(badTrip, LocationFix(0.0, 0.0, 5f, now), now).status)
        assertEquals(FixStatus.STALE, ArrivalLogic.evaluate(trip(), fixAt(30.0, now - 120_001), now).status)
        assertEquals(FixStatus.STALE, ArrivalLogic.evaluate(trip(), fixAt(30.0, now + 60_000), now).status)
        assertEquals(FixStatus.INACCURATE, ArrivalLogic.evaluate(trip(), fixAt(30.0, now, 251f), now).status)
        assertEquals(FixStatus.INVALID_ACCURACY, ArrivalLogic.evaluate(trip(), fixAt(30.0, now, Float.NaN), now).status)
    }

    @Test fun warningRequiresApproachAndFiresOnlyOnceBeforeFinalAlarm() {
        val configured = trip().copy(earlyWarningEnabled = true, earlyWarningDistanceMeters = 1_500)
        val first = ArrivalLogic.evaluate(configured, fixAt(2_500.0, now), now).nextTrip!!
        val stationary = ArrivalLogic.evaluate(first, fixAt(2_500.0, now + 10_000), now + 10_000)
        assertFalse(stationary.warningTriggered)
        val approach1 = ArrivalLogic.evaluate(stationary.nextTrip!!, fixAt(1_800.0, now + 20_000), now + 20_000)
        assertFalse(approach1.warningTriggered)
        val approach2 = ArrivalLogic.evaluate(approach1.nextTrip!!, fixAt(1_600.0, now + 30_000), now + 30_000)
        assertTrue(approach2.warningTriggered)
        val afterWarning = approach2.nextTrip!!
        val approach3 = ArrivalLogic.evaluate(afterWarning, fixAt(1_400.0, now + 40_000), now + 40_000)
        assertFalse(approach3.warningTriggered)
        val final1 = ArrivalLogic.evaluate(approach3.nextTrip!!, fixAt(300.0, now + 50_000), now + 50_000)
        assertFalse(final1.finalTriggered)
        val final2 = ArrivalLogic.evaluate(final1.nextTrip!!, fixAt(250.0, now + 60_000), now + 60_000)
        assertTrue(final2.finalTriggered)
    }

    @Test fun rejectsOldOrOutOfOrderFixesAndLargeGpsJumps() {
        val first = ArrivalLogic.evaluate(trip(), fixAt(20_000.0, now), now)
        val accepted = first.nextTrip!!
        val old = ArrivalLogic.evaluate(accepted, fixAt(19_900.0, now), now + 10_000)
        assertEquals(FixStatus.OUT_OF_ORDER, old.status)
        val jump = ArrivalLogic.evaluate(accepted, fixAt(0.0, now + 1_000), now + 1_000)
        assertEquals(FixStatus.JUMP, jump.status)
        assertNull(jump.nextTrip)
    }

    @Test fun monotonicClockAndAntimeridianDistanceAreHandled() {
        val currentElapsed = 50_000L
        val withinAge = LocationFix(30.0, 31.0, 10f, now - 10_000, elapsedRealtimeMillis = 40_000L)
        assertEquals(FixStatus.ACCEPTED, ArrivalLogic.evaluate(trip(), withinAge, now, currentElapsed).status)
        val staleElapsed = withinAge.copy(elapsedRealtimeMillis = 1L)
        assertEquals(FixStatus.STALE, ArrivalLogic.evaluate(trip(), staleElapsed, now, 200_000L).status)
        val acrossDateline = ArrivalLogic.distanceMeters(Coordinate(0.0, 179.9), Coordinate(0.0, -179.9))
        assertTrue(abs(acrossDateline - 22_239f) < 150f)
    }

    @Test fun alarmIsIdempotentDismissalEndsTripAndNewTripResetsState() {
        val t = trip()
        val triggered = ArrivalLogic.markAlarmTriggered(t)
        assertTrue(triggered.alarmTriggered)
        assertEquals(triggered, ArrivalLogic.markAlarmTriggered(triggered))
        val dismissed = ArrivalLogic.dismissAlarm(triggered)
        assertFalse(dismissed.active)
        assertTrue(dismissed.alarmDismissed)
        assertFalse(ArrivalLogic.evaluate(dismissed, LocationFix(30.0444, 31.2357, 5f, now), now).finalTriggered)
        assertEquals(FixStatus.TRIP_INACTIVE, ArrivalLogic.evaluate(dismissed, LocationFix(30.0444, 31.2357, 5f, now), now).status)
        val fresh = ArrivalLogic.startNewTrip(destination, 1_000, now + 1_000, "البيت")
        assertNotEquals(triggered.id, fresh.id)
        assertTrue(fresh.active)
        assertFalse(fresh.alarmTriggered)
        assertFalse(fresh.alarmDismissed)
        assertEquals("البيت", fresh.destinationName)
        assertEquals(1_000, fresh.radiusMeters)
    }

    @Test fun recoveryRequiresValidSavedTripAndAllowsPausedTrip() {
        assertFalse(ArrivalLogic.canRecover(null))
        assertTrue(ArrivalLogic.canRecover(trip()))
        assertTrue(ArrivalLogic.canRecover(trip().copy(paused = true)))
        assertFalse(ArrivalLogic.canRecover(trip().copy(active = false)))
        assertFalse(ArrivalLogic.canRecover(trip().copy(destination = Coordinate(Double.NaN, 1.0))))
        assertFalse(ArrivalLogic.canRecover(trip().copy(alarmDismissed = true)))
    }
}
