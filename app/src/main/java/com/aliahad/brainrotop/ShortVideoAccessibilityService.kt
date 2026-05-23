package com.aliahad.brainrotop

import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aliahad.brainrotop.analytics.AnalyticsRepository
import com.aliahad.brainrotop.analytics.ScreenSessionEndReason
import com.aliahad.brainrotop.analytics.ScreenSessionTracker
import com.aliahad.brainrotop.detector.DetectionResult
import com.aliahad.brainrotop.detector.NodeSnapshot
import com.aliahad.brainrotop.detector.ShortVideoDetector
import com.aliahad.brainrotop.screentime.ScreenTimeAction
import com.aliahad.brainrotop.screentime.ScreenTimeConfig
import com.aliahad.brainrotop.screentime.ScreenTimeSessionController
import com.aliahad.brainrotop.screentime.ScreenTimeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShortVideoAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analyticsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val analyticsRepository by lazy { AnalyticsRepository.get(this) }
    private val lastBlockAtByPackage = mutableMapOf<String, Long>()
    private var overlayView: View? = null
    private var overlayKind: OverlayKind? = null
    private val screenTimeController = ScreenTimeSessionController()
    private val screenSessionTracker = ScreenSessionTracker()
    private var screenReceiverRegistered = false
    private var screenTimePreferences: SharedPreferences? = null
    private val watchdog = object : Runnable {
        override fun run() {
            scanActiveWindow(source = "watchdog")
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }
    private val screenTimeWarningRunnable = Runnable {
        handleScreenTimeActions(
            screenTimeController.onWarningTimer(nowMs(), screenTimeConfig()),
        )
    }
    private val screenTimeBlockRunnable = Runnable {
        handleScreenTimeActions(
            screenTimeController.onBlockTimer(nowMs(), screenTimeConfig()),
        )
    }
    private val warningOverlayHideRunnable = Runnable {
        if (overlayKind == OverlayKind.ScreenTimeWarning) hideOverlay()
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    startScreenSession()
                    handleScreenTimeActions(
                        screenTimeController.onScreenOn(nowMs(), screenTimeConfig()),
                    )
                }

                Intent.ACTION_SCREEN_OFF -> {
                    recordScreenSessionEnd(ScreenSessionEndReason.SCREEN_OFF)
                    handleScreenTimeActions(
                        screenTimeController.onScreenOff(),
                    )
                }
            }
        }
    }
    private val screenTimeSettingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (ScreenTimeSettings.isScreenTimeKey(key)) {
                handleScreenTimeActions(
                    screenTimeController.onSettingsChanged(nowMs(), screenTimeConfig()),
                )
            }
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerScreenReceiver()
        registerScreenTimeSettingsListener()
        initializeScreenTimeSession()
        mainHandler.removeCallbacks(watchdog)
        mainHandler.post(watchdog)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || overlayView != null) return

        val eventPackage = event?.packageName?.toString()
        val root = rootInActiveWindow
        val packageName = eventPackage ?: root?.packageName?.toString() ?: return
        val activityClassName = event?.className?.toString()

        if (packageName == applicationContext.packageName) return

        scanForBlock(
            packageName = packageName,
            root = root,
            activityClassName = activityClassName,
            eventSnapshot = event.toSnapshot(),
            source = "event:${event.eventType}",
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacks(watchdog)
        mainHandler.removeCallbacks(screenTimeWarningRunnable)
        mainHandler.removeCallbacks(screenTimeBlockRunnable)
        mainHandler.removeCallbacks(warningOverlayHideRunnable)
        recordScreenSessionEnd(ScreenSessionEndReason.SERVICE_STOPPED)
        unregisterScreenTimeSettingsListener()
        unregisterScreenReceiver()
        hideOverlay()
        super.onDestroy()
    }

    private fun scanActiveWindow(source: String) {
        if (overlayView != null) return

        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return

        scanForBlock(
            packageName = packageName,
            root = root,
            activityClassName = null,
            eventSnapshot = null,
            source = source,
        )
    }

    private fun scanForBlock(
        packageName: String,
        root: AccessibilityNodeInfo?,
        activityClassName: String?,
        eventSnapshot: NodeSnapshot?,
        source: String,
    ) {
        val now = SystemClock.elapsedRealtime()
        val snapshot = combineSnapshots(
            packageName = packageName,
            activityClassName = activityClassName,
            rootSnapshot = root?.toSnapshot(),
            eventSnapshot = eventSnapshot,
        )
        val result = ShortVideoDetector.detect(
            packageName = packageName,
            root = snapshot,
            activityClassName = activityClassName,
        ) ?: return
        val lastBlockAt = lastBlockAtByPackage[result.packageName] ?: 0L
        if (now - lastBlockAt < BLOCK_DEBOUNCE_MS) return

        lastBlockAtByPackage[result.packageName] = now
        Log.i(
            TAG,
            "Blocking ${result.packageName}: ${result.reason}; source=$source; eventClass=$activityClassName",
        )
        block(result)
    }

    private fun block(result: DetectionResult) {
        recordShortVideoBlock(result)
        exitBlockedScreen(result)
        mainHandler.postDelayed(
            {
                showOverlay(result)
            },
            LAUNCH_BLOCKER_DELAY_MS,
        )
    }

    private fun exitBlockedScreen(result: DetectionResult) {
        if (result.ruleId == WECHAT_CHANNELS_RULE_ID) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            mainHandler.postDelayed(
                { performGlobalAction(GLOBAL_ACTION_HOME) },
                WECHAT_EXIT_DELAY_MS,
            )
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun showOverlay(result: DetectionResult) {
        showOverlay(
            kind = OverlayKind.ShortVideo,
            view = createShortVideoOverlayView(result),
        )
    }

    private fun showOverlay(kind: OverlayKind, view: View) {
        val windowManager = getSystemService(WindowManager::class.java) ?: return
        hideOverlay(windowManager)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        runCatching {
            windowManager.addView(view, params)
            overlayView = view
            overlayKind = kind
        }.onFailure { error ->
            Log.e(TAG, "Could not show accessibility overlay", error)
        }
    }

    private fun createShortVideoOverlayView(result: DetectionResult): View {
        val density = resources.displayMetrics.density
        fun Int.dp(): Int = (this * density).toInt()
        val colors = overlayColors(OverlayTone.Calm)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28.dp(), 42.dp(), 28.dp(), 42.dp())
            setBackgroundColor(colors.background)

            addView(
                TextView(context).apply {
                    text = "Stopped"
                    textSize = 42f
                    setTextColor(colors.accent)
                    gravity = Gravity.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            addView(
                TextView(context).apply {
                    text = result.appLabel
                    textSize = 24f
                    setTextColor(colors.primaryText)
                    gravity = Gravity.CENTER
                    setPadding(0, 18.dp(), 0, 0)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            addView(
                TextView(context).apply {
                    text = result.reason
                    textSize = 17f
                    setTextColor(colors.secondaryText)
                    gravity = Gravity.CENTER
                    setPadding(0, 10.dp(), 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            addView(
                TextView(context).apply {
                    text = "Take one minute. Drink water. Stand up. Let the loop break here."
                    textSize = 19f
                    setTextColor(colors.primaryText)
                    gravity = Gravity.CENTER
                    setPadding(0, 28.dp(), 0, 28.dp())
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            addView(
                Button(context).apply {
                    text = "Back to Home"
                    textSize = 17f
                    backgroundTintList = ColorStateList.valueOf(colors.buttonBackground)
                    setTextColor(colors.buttonText)
                    setOnClickListener {
                        hideOverlay()
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    56.dp(),
                ).apply {
                    leftMargin = 18.dp()
                    rightMargin = 18.dp()
                },
            )
        }
    }

    private fun hideOverlay(windowManager: WindowManager? = getSystemService(WindowManager::class.java)) {
        val overlay = overlayView ?: return
        mainHandler.removeCallbacks(warningOverlayHideRunnable)
        overlayView = null
        overlayKind = null
        runCatching { windowManager?.removeView(overlay) }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return

        runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    private fun registerScreenTimeSettingsListener() {
        if (screenTimePreferences != null) return

        screenTimePreferences = ScreenTimeSettings.preferences(this).also { preferences ->
            preferences.registerOnSharedPreferenceChangeListener(screenTimeSettingsListener)
        }
    }

    private fun unregisterScreenTimeSettingsListener() {
        screenTimePreferences?.unregisterOnSharedPreferenceChangeListener(screenTimeSettingsListener)
        screenTimePreferences = null
    }

    private fun initializeScreenTimeSession() {
        val powerManager = getSystemService(PowerManager::class.java)
        val actions = if (powerManager?.isInteractive == true) {
            startScreenSession()
            screenTimeController.onScreenOn(nowMs(), screenTimeConfig())
        } else {
            screenTimeController.onScreenOff()
        }
        handleScreenTimeActions(actions)
    }

    private fun handleScreenTimeActions(actions: List<ScreenTimeAction>) {
        actions.forEach { action ->
            when (action) {
                is ScreenTimeAction.ScheduleWarning -> {
                    mainHandler.removeCallbacks(screenTimeWarningRunnable)
                    mainHandler.postDelayed(screenTimeWarningRunnable, action.delayMs)
                }

                is ScreenTimeAction.ScheduleBlock -> {
                    mainHandler.removeCallbacks(screenTimeBlockRunnable)
                    mainHandler.postDelayed(screenTimeBlockRunnable, action.delayMs)
                }

                ScreenTimeAction.CancelWarning -> {
                    mainHandler.removeCallbacks(screenTimeWarningRunnable)
                    mainHandler.removeCallbacks(warningOverlayHideRunnable)
                    if (overlayKind == OverlayKind.ScreenTimeWarning) hideOverlay()
                }

                ScreenTimeAction.CancelBlock -> {
                    mainHandler.removeCallbacks(screenTimeBlockRunnable)
                }

                ScreenTimeAction.ShowWarning -> {
                    val config = screenTimeConfig()
                    recordScreenTimeWarning(config)
                    showScreenTimeWarningOverlay(config)
                }

                ScreenTimeAction.ShowBlock -> {
                    val config = screenTimeConfig()
                    recordScreenTimeBlock(config)
                    Log.i(TAG, "Blocking screen-time session limit")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    mainHandler.postDelayed(
                        { showScreenTimeBlockOverlay(config) },
                        SCREEN_TIME_OVERLAY_DELAY_MS,
                    )
                }

                ScreenTimeAction.HideBlock -> {
                    if (overlayKind == OverlayKind.ScreenTime) hideOverlay()
                }
            }
        }
    }

    private fun showScreenTimeWarningOverlay(config: ScreenTimeConfig) {
        if (overlayView != null) return

        showOverlay(
            kind = OverlayKind.ScreenTimeWarning,
            view = createScreenTimeWarningOverlayView(config),
        )
        mainHandler.postDelayed(warningOverlayHideRunnable, WARNING_OVERLAY_DURATION_MS)
    }

    private fun showScreenTimeBlockOverlay(config: ScreenTimeConfig) {
        showOverlay(
            kind = OverlayKind.ScreenTime,
            view = createScreenTimeOverlayView(config),
        )
    }

    private fun createScreenTimeWarningOverlayView(config: ScreenTimeConfig): View {
        val density = resources.displayMetrics.density
        fun Int.dp(): Int = (this * density).toInt()
        val colors = overlayColors(OverlayTone.Warning)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28.dp(), 42.dp(), 28.dp(), 42.dp())
            setBackgroundColor(colors.background)

            addView(
                TextView(context).apply {
                    text = "1 minute left"
                    textSize = 36f
                    setTextColor(colors.accent)
                    gravity = Gravity.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            addView(
                TextView(context).apply {
                    text = "${config.limitMinutes} minute screen session is almost done"
                    textSize = 19f
                    setTextColor(colors.primaryText)
                    gravity = Gravity.CENTER
                    setPadding(0, 18.dp(), 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun createScreenTimeOverlayView(config: ScreenTimeConfig): View {
        val density = resources.displayMetrics.density
        fun Int.dp(): Int = (this * density).toInt()
        val colors = overlayColors(OverlayTone.Stop)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28.dp(), 42.dp(), 28.dp(), 42.dp())
            setBackgroundColor(colors.background)

            addView(
                TextView(context).apply {
                    text = "Time is up"
                    textSize = 40f
                    setTextColor(colors.accent)
                    gravity = Gravity.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            addView(
                TextView(context).apply {
                    text = "${config.limitMinutes} minute screen session reached"
                    textSize = 22f
                    setTextColor(colors.primaryText)
                    gravity = Gravity.CENTER
                    setPadding(0, 18.dp(), 0, 0)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            addView(
                TextView(context).apply {
                    text = "Turn the screen off to reset this session."
                    textSize = 19f
                    setTextColor(colors.secondaryText)
                    gravity = Gravity.CENTER
                    setPadding(0, 24.dp(), 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun screenTimeConfig(): ScreenTimeConfig =
        ScreenTimeSettings.read(this)

    private fun nowMs(): Long = SystemClock.elapsedRealtime()

    private fun wallTimeMs(): Long = System.currentTimeMillis()

    private fun startScreenSession() {
        screenSessionTracker.onScreenOn(
            startedAtWallMs = wallTimeMs(),
            startedAtElapsedMs = nowMs(),
            limitMinutes = screenTimeConfig().limitMinutes,
        )
    }

    private fun recordScreenSessionEnd(reason: ScreenSessionEndReason) {
        val session = screenSessionTracker.onScreenOff(
            endedAtWallMs = wallTimeMs(),
            endedAtElapsedMs = nowMs(),
            endReason = reason,
        ) ?: return

        analyticsScope.launch {
            analyticsRepository.recordSession(session)
        }
    }

    private fun recordShortVideoBlock(result: DetectionResult) {
        analyticsScope.launch {
            analyticsRepository.recordShortVideoBlock(result)
        }
    }

    private fun recordScreenTimeWarning(config: ScreenTimeConfig) {
        analyticsScope.launch {
            analyticsRepository.recordScreenTimeWarning(config)
        }
    }

    private fun recordScreenTimeBlock(config: ScreenTimeConfig) {
        analyticsScope.launch {
            analyticsRepository.recordScreenTimeBlock(config)
        }
    }

    private fun overlayColors(tone: OverlayTone): OverlayColors {
        val dark = isDarkMode()
        return when (tone) {
            OverlayTone.Calm -> if (dark) {
                OverlayColors(
                    background = Color.rgb(13, 22, 18),
                    primaryText = Color.rgb(232, 241, 236),
                    secondaryText = Color.rgb(169, 186, 177),
                    accent = Color.rgb(126, 211, 176),
                    buttonBackground = Color.rgb(126, 211, 176),
                    buttonText = Color.rgb(4, 27, 18),
                )
            } else {
                OverlayColors(
                    background = Color.rgb(246, 250, 247),
                    primaryText = Color.rgb(27, 35, 31),
                    secondaryText = Color.rgb(83, 98, 91),
                    accent = Color.rgb(24, 92, 64),
                    buttonBackground = Color.rgb(24, 92, 64),
                    buttonText = Color.WHITE,
                )
            }

            OverlayTone.Warning -> if (dark) {
                OverlayColors(
                    background = Color.rgb(28, 22, 12),
                    primaryText = Color.rgb(246, 236, 215),
                    secondaryText = Color.rgb(210, 188, 145),
                    accent = Color.rgb(239, 190, 103),
                    buttonBackground = Color.rgb(239, 190, 103),
                    buttonText = Color.rgb(34, 22, 4),
                )
            } else {
                OverlayColors(
                    background = Color.rgb(255, 250, 238),
                    primaryText = Color.rgb(49, 40, 24),
                    secondaryText = Color.rgb(100, 83, 48),
                    accent = Color.rgb(132, 81, 17),
                    buttonBackground = Color.rgb(132, 81, 17),
                    buttonText = Color.WHITE,
                )
            }

            OverlayTone.Stop -> if (dark) {
                OverlayColors(
                    background = Color.rgb(30, 17, 16),
                    primaryText = Color.rgb(248, 232, 228),
                    secondaryText = Color.rgb(218, 180, 173),
                    accent = Color.rgb(248, 143, 124),
                    buttonBackground = Color.rgb(248, 143, 124),
                    buttonText = Color.rgb(45, 11, 7),
                )
            } else {
                OverlayColors(
                    background = Color.rgb(255, 248, 245),
                    primaryText = Color.rgb(45, 32, 28),
                    secondaryText = Color.rgb(99, 74, 67),
                    accent = Color.rgb(139, 50, 34),
                    buttonBackground = Color.rgb(139, 50, 34),
                    buttonText = Color.WHITE,
                )
            }
        }
    }

    private fun isDarkMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun combineSnapshots(
        packageName: String,
        activityClassName: String?,
        rootSnapshot: NodeSnapshot?,
        eventSnapshot: NodeSnapshot?,
    ): NodeSnapshot? {
        if (rootSnapshot == null) return eventSnapshot
        if (eventSnapshot == null && activityClassName == null) return rootSnapshot

        return NodeSnapshot(
            packageName = packageName,
            className = activityClassName,
            children = listOfNotNull(eventSnapshot, rootSnapshot),
        )
    }

    private fun AccessibilityEvent.toSnapshot(): NodeSnapshot {
        val eventText = text.joinToString(separator = " ") { it.toString() }
        val sourceSnapshot = source?.toSnapshot(depth = 0, counter = NodeCounter())
        return NodeSnapshot(
            packageName = packageName?.toString(),
            className = className?.toString(),
            text = eventText,
            contentDescription = contentDescription?.toString(),
            children = listOfNotNull(sourceSnapshot),
        )
    }

    private fun AccessibilityNodeInfo.toSnapshot(
        depth: Int = 0,
        counter: NodeCounter = NodeCounter(),
    ): NodeSnapshot {
        counter.count += 1
        if (depth >= MAX_TREE_DEPTH || counter.count >= MAX_TREE_NODES) {
            return toShallowSnapshot(children = emptyList())
        }

        val childSnapshots = buildList {
            for (index in 0 until childCount) {
                if (counter.count >= MAX_TREE_NODES) break
                val child = getChild(index) ?: continue
                add(child.toSnapshot(depth = depth + 1, counter = counter))
            }
        }

        return toShallowSnapshot(children = childSnapshots)
    }

    private fun AccessibilityNodeInfo.toShallowSnapshot(children: List<NodeSnapshot>): NodeSnapshot =
        NodeSnapshot(
            packageName = packageName?.toString(),
            className = className?.toString(),
            text = text?.toString(),
            contentDescription = contentDescription?.toString(),
            viewIdResourceName = viewIdResourceName,
            children = children,
        )

    private class NodeCounter(var count: Int = 0)

    private enum class OverlayKind {
        ShortVideo,
        ScreenTimeWarning,
        ScreenTime,
    }

    private enum class OverlayTone {
        Calm,
        Warning,
        Stop,
    }

    private data class OverlayColors(
        val background: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val accent: Int,
        val buttonBackground: Int,
        val buttonText: Int,
    )

    companion object {
        private const val MAX_TREE_DEPTH = 9
        private const val MAX_TREE_NODES = 300
        private const val WATCHDOG_INTERVAL_MS = 300L
        private const val BLOCK_DEBOUNCE_MS = 700L
        private const val WECHAT_EXIT_DELAY_MS = 180L
        private const val LAUNCH_BLOCKER_DELAY_MS = 320L
        private const val SCREEN_TIME_OVERLAY_DELAY_MS = 120L
        private const val WARNING_OVERLAY_DURATION_MS = 4_500L
        private const val WECHAT_CHANNELS_RULE_ID = "wechat_channels"
        private const val TAG = "BrainrotopBlocker"
    }
}
