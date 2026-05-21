package com.aliahad.brainrotop.screentime

data class ScreenTimeConfig(
    val enabled: Boolean = false,
    val limitMinutes: Int = DEFAULT_LIMIT_MINUTES,
    val warningEnabled: Boolean = true,
) {
    val limitMs: Long = limitMinutes.coerceIn(MIN_LIMIT_MINUTES, MAX_LIMIT_MINUTES) * MINUTE_MS

    companion object {
        const val DEFAULT_LIMIT_MINUTES = 5
        const val MIN_LIMIT_MINUTES = 1
        const val MAX_LIMIT_MINUTES = 240
        const val MINUTE_MS = 60_000L
    }
}

