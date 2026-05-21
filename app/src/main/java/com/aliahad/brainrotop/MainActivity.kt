package com.aliahad.brainrotop

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aliahad.brainrotop.detector.ShortVideoDetector
import com.aliahad.brainrotop.ui.theme.BrainrotopTheme

class MainActivity : ComponentActivity() {
    private var blockerEnabled by mutableStateOf(false)
    private var batteryExempt by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshStatus()

        setContent {
            BrainrotopTheme {
                DashboardScreen(
                    blockerEnabled = blockerEnabled,
                    batteryExempt = batteryExempt,
                    onOpenAccessibility = ::openAccessibilitySettings,
                    onOpenBattery = ::openBatterySettings,
                    onOpenXiaomiAutostart = ::openXiaomiAutostart,
                    onTestBlocker = ::openTestBlocker,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        blockerEnabled = AccessibilityStatus.isBlockerEnabled(this)
        batteryExempt = AccessibilityStatus.isIgnoringBatteryOptimizations(this)
    }

    private fun openAccessibilitySettings() {
        safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openBatterySettings() {
        val requestExemption = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        if (!safeStart(requestExemption)) {
            safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openXiaomiAutostart() {
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
        val candidates = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            ),
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings",
                ),
            ),
            appDetails,
        )

        candidates.firstOrNull(::safeStart)
    }

    private fun openTestBlocker() {
        safeStart(
            Intent(this, BlockActivity::class.java)
                .putExtra(BlockActivity.EXTRA_APP_LABEL, "Test block")
                .putExtra(BlockActivity.EXTRA_REASON, "Manual Brainrotop check"),
        )
    }

    private fun safeStart(intent: Intent): Boolean =
        runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)
}

@Composable
private fun DashboardScreen(
    blockerEnabled: Boolean,
    batteryExempt: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenXiaomiAutostart: () -> Unit,
    onTestBlocker: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Brainrotop",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Short-video blocker for WeChat Channels, Shorts, Reels, and similar feeds.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            StatusPanel(
                blockerEnabled = blockerEnabled,
                batteryExempt = batteryExempt,
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onOpenAccessibility,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text("Open Accessibility Settings")
                }
                OutlinedButton(
                    onClick = onOpenXiaomiAutostart,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text("Open Xiaomi App Protection")
                }
                OutlinedButton(
                    onClick = onOpenBattery,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text("Allow Battery Exemption")
                }
                OutlinedButton(
                    onClick = onTestBlocker,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text("Show Test Block Screen")
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Protected Surfaces",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                ShortVideoDetector.rules.forEach { rule ->
                    RuleRow(
                        title = rule.appLabel,
                        subtitle = rule.packageNames.joinToString(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(
    blockerEnabled: Boolean,
    batteryExempt: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusRow(
            label = "Accessibility service",
            value = if (blockerEnabled) "Active" else "Off",
            active = blockerEnabled,
        )
        StatusRow(
            label = "Battery optimization",
            value = if (batteryExempt) "Allowed" else "Not exempt",
            active = batteryExempt,
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    active: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
        }
    }
}

@Composable
private fun RuleRow(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

