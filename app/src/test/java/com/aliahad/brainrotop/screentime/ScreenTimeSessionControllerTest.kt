package com.aliahad.brainrotop.screentime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeSessionControllerTest {
    @Test
    fun screenOnStartsCountdown() {
        val actions = ScreenTimeSessionController().onScreenOn(0L, enabledConfig(limitMinutes = 5))

        assertTrue(actions.contains(ScreenTimeAction.ScheduleWarning(240_000L)))
        assertTrue(actions.contains(ScreenTimeAction.ScheduleBlock(300_000L)))
    }

    @Test
    fun screenOffResetsCountdownAndClearsBlock() {
        val controller = ScreenTimeSessionController()
        controller.onScreenOn(0L, enabledConfig())

        val actions = controller.onScreenOff()

        assertTrue(actions.contains(ScreenTimeAction.CancelWarning))
        assertTrue(actions.contains(ScreenTimeAction.CancelBlock))
        assertTrue(actions.contains(ScreenTimeAction.HideBlock))
        assertEquals(emptyList<ScreenTimeAction>(), controller.onBlockTimer(300_000L, enabledConfig()))
    }

    @Test
    fun disabledFeatureNeverBlocks() {
        val controller = ScreenTimeSessionController()
        val config = ScreenTimeConfig(enabled = false, limitMinutes = 1)

        val actions = controller.onScreenOn(0L, config)

        assertFalse(actions.any { it is ScreenTimeAction.ScheduleBlock })
        assertEquals(emptyList<ScreenTimeAction>(), controller.onBlockTimer(60_000L, config))
    }

    @Test
    fun changingMinutesReschedulesActiveSession() {
        val controller = ScreenTimeSessionController()
        controller.onScreenOn(0L, enabledConfig(limitMinutes = 5))

        val actions = controller.onSettingsChanged(60_000L, enabledConfig(limitMinutes = 10))

        assertTrue(actions.contains(ScreenTimeAction.ScheduleWarning(480_000L)))
        assertTrue(actions.contains(ScreenTimeAction.ScheduleBlock(540_000L)))
    }

    @Test
    fun oneMinuteWarningFiresBeforeBlock() {
        val controller = ScreenTimeSessionController()
        val config = enabledConfig(limitMinutes = 5)
        controller.onScreenOn(0L, config)

        val warningActions = controller.onWarningTimer(240_000L, config)
        val blockActions = controller.onBlockTimer(300_000L, config)

        assertTrue(warningActions.contains(ScreenTimeAction.ShowWarning))
        assertTrue(blockActions.contains(ScreenTimeAction.ShowBlock))
    }

    @Test
    fun oneMinuteLimitShowsWarningImmediately() {
        val controller = ScreenTimeSessionController()
        val config = enabledConfig(limitMinutes = 1)
        val startActions = controller.onScreenOn(0L, config)
        val warningActions = controller.onWarningTimer(0L, config)

        assertTrue(startActions.contains(ScreenTimeAction.ScheduleWarning(0L)))
        assertTrue(warningActions.contains(ScreenTimeAction.ShowWarning))
    }

    private fun enabledConfig(limitMinutes: Int = 5): ScreenTimeConfig =
        ScreenTimeConfig(enabled = true, limitMinutes = limitMinutes, warningEnabled = true)
}
