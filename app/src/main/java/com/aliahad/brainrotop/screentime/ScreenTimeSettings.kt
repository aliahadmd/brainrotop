package com.aliahad.brainrotop.screentime

import android.content.Context
import android.content.SharedPreferences

object ScreenTimeSettings {
    private const val PREFS_NAME = "screen_time_settings"
    private const val KEY_ENABLED = "screen_time_enabled"
    private const val KEY_LIMIT_MINUTES = "screen_time_limit_minutes"
    private const val KEY_WARNING_ENABLED = "screen_time_warning_enabled"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): ScreenTimeConfig {
        val prefs = preferences(context)
        return ScreenTimeConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            limitMinutes = prefs.getInt(
                KEY_LIMIT_MINUTES,
                ScreenTimeConfig.DEFAULT_LIMIT_MINUTES,
            ).coerceIn(
                ScreenTimeConfig.MIN_LIMIT_MINUTES,
                ScreenTimeConfig.MAX_LIMIT_MINUTES,
            ),
            warningEnabled = prefs.getBoolean(KEY_WARNING_ENABLED, true),
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setLimitMinutes(context: Context, minutes: Int) {
        preferences(context)
            .edit()
            .putInt(
                KEY_LIMIT_MINUTES,
                minutes.coerceIn(
                    ScreenTimeConfig.MIN_LIMIT_MINUTES,
                    ScreenTimeConfig.MAX_LIMIT_MINUTES,
                ),
            )
            .apply()
    }

    fun setWarningEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_WARNING_ENABLED, enabled).apply()
    }

    fun isScreenTimeKey(key: String?): Boolean =
        key == KEY_ENABLED || key == KEY_LIMIT_MINUTES || key == KEY_WARNING_ENABLED
}

