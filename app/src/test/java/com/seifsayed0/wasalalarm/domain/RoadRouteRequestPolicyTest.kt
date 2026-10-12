package com.seifsayed0.wasalalarm.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadRouteRequestPolicyTest {
    private val start = Coordinate(30.0444, 31.2357)
    private val moved = Coordinate(30.0460, 31.2357)

    @Test fun firstValidFixCanRequestImmediately() {
        assertTrue(RoadRouteRequestPolicy.shouldRequest(1_000L, null, null, start))
    }

    @Test fun movementCannotBypassMinimumRequestInterval() {
        assertFalse(RoadRouteRequestPolicy.shouldRequest(
            30_999L, 1_000L, start, Coordinate(30.0500, 31.2357)
        ))
    }

    @Test fun meaningfulMovementRefreshesAfterMinimumInterval() {
        assertTrue(RoadRouteRequestPolicy.shouldRequest(
            31_000L, 1_000L, start, moved
        ))
    }

    @Test fun smallMovementWaitsForIdleRefresh() {
        val nearby = Coordinate(30.0445, 31.2357)
        assertFalse(RoadRouteRequestPolicy.shouldRequest(31_000L, 1_000L, start, nearby))
        assertTrue(RoadRouteRequestPolicy.shouldRequest(61_000L, 1_000L, start, nearby))
    }

    @Test fun invalidCoordinatesNeverTriggerARequest() {
        assertFalse(RoadRouteRequestPolicy.shouldRequest(
            1_000L, null, null, Coordinate(Double.NaN, 31.0)
        ))
    }
}
