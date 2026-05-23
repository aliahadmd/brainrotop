package com.aliahad.brainrotop

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.aliahad.brainrotop.analytics.AnalyticsDateKeys
import com.aliahad.brainrotop.analytics.AnalyticsRepository
import com.aliahad.brainrotop.analytics.AnalyticsSummary
import com.aliahad.brainrotop.analytics.DailyAnalytics
import com.aliahad.brainrotop.detector.ShortVideoDetector
import com.aliahad.brainrotop.screentime.ScreenTimeConfig
import com.aliahad.brainrotop.screentime.ScreenTimeSettings
import com.aliahad.brainrotop.ui.theme.BrainrotopTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val analyticsRepository by lazy { AnalyticsRepository.get(this) }
    private var blockerEnabled by mutableStateOf(false)
    private var batteryExempt by mutableStateOf(false)
    private var screenTimeConfig by mutableStateOf(ScreenTimeConfig())
    private var analyticsSummary by mutableStateOf(AnalyticsSummary.empty())
    private var analyticsLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshStatus()
        refreshAnalytics()

        setContent {
            BrainrotopTheme {
                DashboardScreen(
                    blockerEnabled = blockerEnabled,
                    batteryExempt = batteryExempt,
                    screenTimeConfig = screenTimeConfig,
                    analyticsSummary = analyticsSummary,
                    analyticsLoading = analyticsLoading,
                    onOpenAccessibility = ::openAccessibilitySettings,
                    onOpenBattery = ::openBatterySettings,
                    onOpenXiaomiAutostart = ::openXiaomiAutostart,
                    onTestBlocker = ::openTestBlocker,
                    onScreenTimeEnabledChange = ::setScreenTimeEnabled,
                    onScreenTimeLimitChange = ::setScreenTimeLimitMinutes,
                    onResetAnalytics = ::resetAnalytics,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshAnalytics()
    }

    private fun refreshStatus() {
        blockerEnabled = AccessibilityStatus.isBlockerEnabled(this)
        batteryExempt = AccessibilityStatus.isIgnoringBatteryOptimizations(this)
        screenTimeConfig = ScreenTimeSettings.read(this)
    }

    private fun refreshAnalytics() {
        lifecycleScope.launch {
            analyticsLoading = true
            analyticsSummary = runCatching {
                analyticsRepository.loadSummary()
            }.getOrElse {
                AnalyticsSummary.empty()
            }
            analyticsLoading = false
        }
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

    private fun setScreenTimeEnabled(enabled: Boolean) {
        ScreenTimeSettings.setEnabled(this, enabled)
        refreshStatus()
    }

    private fun setScreenTimeLimitMinutes(minutes: Int) {
        ScreenTimeSettings.setLimitMinutes(this, minutes)
        refreshStatus()
    }

    private fun resetAnalytics() {
        lifecycleScope.launch {
            analyticsRepository.reset()
            refreshAnalytics()
        }
    }

    private fun safeStart(intent: Intent): Boolean =
        runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)
}

private enum class DashboardTab(val title: String) {
    Overview("Overview"),
    Analytics("Analytics"),
    Rules("Rules"),
    Setup("Setup"),
}

@Composable
private fun DashboardScreen(
    blockerEnabled: Boolean,
    batteryExempt: Boolean,
    screenTimeConfig: ScreenTimeConfig,
    analyticsSummary: AnalyticsSummary,
    analyticsLoading: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenXiaomiAutostart: () -> Unit,
    onTestBlocker: () -> Unit,
    onScreenTimeEnabledChange: (Boolean) -> Unit,
    onScreenTimeLimitChange: (Int) -> Unit,
    onResetAnalytics: () -> Unit,
) {
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val tabs = remember { DashboardTab.entries }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Brainrotop",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (blockerEnabled) "Protection active" else "Protection off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            when (tabs[selectedTabIndex]) {
                DashboardTab.Overview -> OverviewTab(
                    blockerEnabled = blockerEnabled,
                    batteryExempt = batteryExempt,
                    screenTimeConfig = screenTimeConfig,
                    today = analyticsSummary.today,
                    onScreenTimeEnabledChange = onScreenTimeEnabledChange,
                    onScreenTimeLimitChange = onScreenTimeLimitChange,
                )

                DashboardTab.Analytics -> AnalyticsTab(
                    summary = analyticsSummary,
                    loading = analyticsLoading,
                )

                DashboardTab.Rules -> RulesTab()
                DashboardTab.Setup -> SetupTab(
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenBattery = onOpenBattery,
                    onOpenXiaomiAutostart = onOpenXiaomiAutostart,
                    onTestBlocker = onTestBlocker,
                    onResetAnalytics = onResetAnalytics,
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(
    blockerEnabled: Boolean,
    batteryExempt: Boolean,
    screenTimeConfig: ScreenTimeConfig,
    today: DailyAnalytics,
    onScreenTimeEnabledChange: (Boolean) -> Unit,
    onScreenTimeLimitChange: (Int) -> Unit,
) {
    ScreenColumn {
        StatusPanel(
            blockerEnabled = blockerEnabled,
            batteryExempt = batteryExempt,
        )
        HorizontalDivider()
        MetricGrid(
            metrics = listOf(
                Metric("Today", formatDuration(today.screenTimeMs)),
                Metric("Blocks", today.totalBlockCount.toString()),
            ),
        )
        HorizontalDivider()
        ScreenTimePanel(
            config = screenTimeConfig,
            onEnabledChange = onScreenTimeEnabledChange,
            onLimitChange = onScreenTimeLimitChange,
        )
    }
}

@Composable
private fun AnalyticsTab(
    summary: AnalyticsSummary,
    loading: Boolean,
) {
    ScreenColumn {
        Text(
            text = if (loading) "Updating analytics..." else "Today",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        MetricGrid(
            metrics = listOf(
                Metric("Screen time", formatDuration(summary.today.screenTimeMs)),
                Metric("Sessions", summary.today.sessionCount.toString()),
                Metric("Warnings", summary.today.warningCount.toString()),
                Metric("Time blocks", summary.today.screenTimeBlockCount.toString()),
                Metric("Video blocks", summary.today.shortVideoBlockCount.toString()),
                Metric("Top app", summary.topBlockedApp ?: "None"),
            ),
        )
        HorizontalDivider()
        Text(
            text = "7-day blocks",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        TrendChart(days = summary.sevenDayTrend)
    }
}

@Composable
private fun RulesTab() {
    ScreenColumn {
        Text(
            text = "Protected surfaces",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        ShortVideoDetector.rules.forEach { rule ->
            RuleRow(
                title = rule.appLabel,
                subtitle = rule.packageNames.joinToString(),
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SetupTab(
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenXiaomiAutostart: () -> Unit,
    onTestBlocker: () -> Unit,
    onResetAnalytics: () -> Unit,
) {
    ScreenColumn {
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
        HorizontalDivider()
        TextButton(
            onClick = onResetAnalytics,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text("Reset Analytics")
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}

@Composable
private fun ScreenTimePanel(
    config: ScreenTimeConfig,
    onEnabledChange: (Boolean) -> Unit,
    onLimitChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Screen-Time Limit",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (config.enabled) {
                        "${config.limitMinutes} minutes from screen on"
                    } else {
                        "Off"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = onEnabledChange,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(
                onClick = { onLimitChange(config.limitMinutes - 1) },
                enabled = config.limitMinutes > ScreenTimeConfig.MIN_LIMIT_MINUTES,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text("-")
            }
            Text(
                text = "${config.limitMinutes} min",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = { onLimitChange(config.limitMinutes + 1) },
                enabled = config.limitMinutes < ScreenTimeConfig.MAX_LIMIT_MINUTES,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text("+")
            }
        }
    }
}

@Composable
private fun StatusPanel(
    blockerEnabled: Boolean,
    batteryExempt: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

private data class Metric(
    val label: String,
    val value: String,
)

@Composable
private fun MetricGrid(metrics: List<Metric>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowMetrics.forEach { metric ->
                    MetricTile(
                        metric = metric,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowMetrics.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    metric: Metric,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.sizeIn(minHeight = 82.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metric.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TrendChart(days: List<DailyAnalytics>) {
    val maxBlocks = days.maxOfOrNull { it.totalBlockCount }?.coerceAtLeast(1) ?: 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val barHeight = ((day.totalBlockCount.toFloat() / maxBlocks) * 78f)
                .roundToInt()
                .coerceAtLeast(if (day.totalBlockCount > 0) 8 else 3)

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = day.totalBlockCount.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = AnalyticsDateKeys.shortLabel(day.dayKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L

    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m"
        durationMs > 0L -> "<1m"
        else -> "0m"
    }
}
