package com.aliahad.brainrotop

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
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
import com.aliahad.brainrotop.detector.DetectionResult
import com.aliahad.brainrotop.detector.NodeSnapshot
import com.aliahad.brainrotop.detector.ShortVideoDetector

class ShortVideoAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lastBlockAtByPackage = mutableMapOf<String, Long>()
    private var overlayView: View? = null
    private val watchdog = object : Runnable {
        override fun run() {
            scanActiveWindow(source = "watchdog")
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
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
        val windowManager = getSystemService(WindowManager::class.java) ?: return
        hideOverlay(windowManager)

        val overlay = createOverlayView(result)
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
            windowManager.addView(overlay, params)
            overlayView = overlay
        }.onFailure { error ->
            Log.e(TAG, "Could not show accessibility overlay", error)
        }
    }

    private fun createOverlayView(result: DetectionResult): View {
        val density = resources.displayMetrics.density
        fun Int.dp(): Int = (this * density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28.dp(), 42.dp(), 28.dp(), 42.dp())
            setBackgroundColor(Color.rgb(248, 252, 247))

            addView(
                TextView(context).apply {
                    text = "Stopped"
                    textSize = 42f
                    setTextColor(Color.rgb(24, 92, 64))
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
                    setTextColor(Color.rgb(27, 35, 31))
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
                    setTextColor(Color.rgb(83, 98, 91))
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
                    setTextColor(Color.rgb(47, 79, 62))
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
        overlayView = null
        runCatching { windowManager?.removeView(overlay) }
    }

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

    companion object {
        private const val MAX_TREE_DEPTH = 9
        private const val MAX_TREE_NODES = 300
        private const val WATCHDOG_INTERVAL_MS = 300L
        private const val BLOCK_DEBOUNCE_MS = 700L
        private const val WECHAT_EXIT_DELAY_MS = 180L
        private const val LAUNCH_BLOCKER_DELAY_MS = 320L
        private const val WECHAT_CHANNELS_RULE_ID = "wechat_channels"
        private const val TAG = "BrainrotopBlocker"
    }
}
