package com.aliahad.brainrotop

import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings

object AccessibilityStatus {
    fun isBlockerEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ShortVideoAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val flattened = expected.flattenToString()
        val shortFlattened = expected.flattenToShortString()
        return enabledServices
            .split(':')
            .any { service ->
                service.equals(flattened, ignoreCase = true) ||
                    service.equals(shortFlattened, ignoreCase = true)
            }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
}

