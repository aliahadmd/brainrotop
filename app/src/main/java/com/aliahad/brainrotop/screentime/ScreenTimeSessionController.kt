package com.aliahad.brainrotop.screentime

class ScreenTimeSessionController {
    private var screenOn = false
    private var sessionStartedAtMs: Long? = null
    private var warningShown = false
    private var blocked = false
    private var lastEnabled = false

    fun onScreenOn(nowMs: Long, config: ScreenTimeConfig): List<ScreenTimeAction> {
        screenOn = true
        sessionStartedAtMs = nowMs
        warningShown = false
        blocked = false
        lastEnabled = config.enabled
        return schedule(nowMs, config, hideBlock = true)
    }

    fun onScreenOff(): List<ScreenTimeAction> {
        screenOn = false
        sessionStartedAtMs = null
        warningShown = false
        blocked = false
        return listOf(
            ScreenTimeAction.CancelWarning,
            ScreenTimeAction.CancelBlock,
            ScreenTimeAction.HideBlock,
        )
    }

    fun onSettingsChanged(nowMs: Long, config: ScreenTimeConfig): List<ScreenTimeAction> {
        if (!screenOn) {
            lastEnabled = config.enabled
            return listOf(ScreenTimeAction.CancelWarning, ScreenTimeAction.CancelBlock)
        }

        if (!config.enabled) {
            lastEnabled = false
            warningShown = false
            blocked = false
            return listOf(
                ScreenTimeAction.CancelWarning,
                ScreenTimeAction.CancelBlock,
                ScreenTimeAction.HideBlock,
            )
        }

        if (!lastEnabled) {
            sessionStartedAtMs = nowMs
            warningShown = false
            blocked = false
        }

        lastEnabled = true
        return schedule(nowMs, config, hideBlock = false)
    }

    fun onWarningTimer(nowMs: Long, config: ScreenTimeConfig): List<ScreenTimeAction> {
        if (!shouldRun(config) || !config.warningEnabled || warningShown) return emptyList()

        val startedAt = sessionStartedAtMs ?: return emptyList()
        val warningAt = warningAtMs(config)
        val elapsed = nowMs - startedAt
        if (elapsed < warningAt) {
            return listOf(ScreenTimeAction.ScheduleWarning(warningAt - elapsed))
        }

        warningShown = true
        return listOf(ScreenTimeAction.ShowWarning)
    }

    fun onBlockTimer(nowMs: Long, config: ScreenTimeConfig): List<ScreenTimeAction> {
        if (!shouldRun(config)) return emptyList()

        val startedAt = sessionStartedAtMs ?: return emptyList()
        val elapsed = nowMs - startedAt
        if (elapsed < config.limitMs) {
            return listOf(ScreenTimeAction.ScheduleBlock(config.limitMs - elapsed))
        }

        blocked = true
        return listOf(
            ScreenTimeAction.CancelWarning,
            ScreenTimeAction.CancelBlock,
            ScreenTimeAction.ShowBlock,
        )
    }

    private fun schedule(
        nowMs: Long,
        config: ScreenTimeConfig,
        hideBlock: Boolean,
    ): List<ScreenTimeAction> {
        val actions = mutableListOf<ScreenTimeAction>(
            ScreenTimeAction.CancelWarning,
            ScreenTimeAction.CancelBlock,
        )
        if (hideBlock) actions += ScreenTimeAction.HideBlock

        if (!shouldRun(config)) return actions

        val startedAt = sessionStartedAtMs ?: return actions
        val elapsed = nowMs - startedAt
        if (elapsed >= config.limitMs) {
            blocked = true
            actions += ScreenTimeAction.ShowBlock
            return actions
        }

        blocked = false
        if (config.warningEnabled && !warningShown) {
            actions += ScreenTimeAction.ScheduleWarning((warningAtMs(config) - elapsed).coerceAtLeast(0L))
        }
        actions += ScreenTimeAction.ScheduleBlock(config.limitMs - elapsed)
        return actions
    }

    private fun shouldRun(config: ScreenTimeConfig): Boolean =
        screenOn && config.enabled && !blocked

    private fun warningAtMs(config: ScreenTimeConfig): Long =
        (config.limitMs - WARNING_BEFORE_LIMIT_MS).coerceAtLeast(0L)

    companion object {
        private const val WARNING_BEFORE_LIMIT_MS = ScreenTimeConfig.MINUTE_MS
    }
}

sealed class ScreenTimeAction {
    data class ScheduleWarning(val delayMs: Long) : ScreenTimeAction()
    data class ScheduleBlock(val delayMs: Long) : ScreenTimeAction()
    object CancelWarning : ScreenTimeAction()
    object CancelBlock : ScreenTimeAction()
    object ShowWarning : ScreenTimeAction()
    object ShowBlock : ScreenTimeAction()
    object HideBlock : ScreenTimeAction()
}

