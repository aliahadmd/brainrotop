package com.aliahad.brainrotop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class ScreenSessionTrackerTest {
    @Test
    fun screenOnThenOffCreatesSessionWithElapsedDuration() {
        val tracker = ScreenSessionTracker()

        tracker.onScreenOn(
            startedAtWallMs = 1_000L,
            startedAtElapsedMs = 50_000L,
            limitMinutes = 5,
        )
        val session = tracker.onScreenOff(
            endedAtWallMs = 121_000L,
            endedAtElapsedMs = 170_000L,
            endReason = ScreenSessionEndReason.SCREEN_OFF,
            timeZone = TimeZone.getTimeZone("UTC"),
        )

        assertEquals(120_000L, session?.durationMs)
        assertEquals(5, session?.limitMinutes)
        assertEquals(ScreenSessionEndReason.SCREEN_OFF.name, session?.endReason)
    }

    @Test
    fun screenOffWithoutActiveSessionDoesNothing() {
        val session = ScreenSessionTracker().onScreenOff(
            endedAtWallMs = 100L,
            endedAtElapsedMs = 200L,
            endReason = ScreenSessionEndReason.SCREEN_OFF,
        )

        assertNull(session)
    }
}
