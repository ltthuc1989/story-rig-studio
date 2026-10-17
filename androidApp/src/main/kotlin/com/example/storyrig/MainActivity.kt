package com.example.storyrig

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.metrics.performance.JankStats
import com.example.storyrig.engine.PerfStats
import kotlin.math.abs

/**
 * Opens a scene from intent extras, e.g.
 * `adb shell am start -n com.example.storyrig/.MainActivity --es scene p3 --es cache 0 --es hz default`.
 */
class MainActivity : ComponentActivity() {
    private var jankStats: JankStats? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val args = HashMap<String, String>()
        intent.extras?.let { ex -> for (k in ex.keySet()) ex.get(k)?.let { args[k] = it.toString() } }

        val perf = PerfStats.shared
        perf.refreshHz = if (args["hz"] == "default") currentRefreshRate() else request60Hz()
        Log.i("StoryRig", "device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} hz=${perf.refreshHz} args=$args")

        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // Long measurement runs must not be cut by the screen timeout, and benchmark cold starts
        // must render even if the phone locked itself between iterations.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContent { App(args, perf) }

        // `--es jank 0` turns JankStats off, to separate its allocations from the scene's
        if (args["jank"] != "0") jankStats = JankStats.createAndTrack(window) { fd ->
            perf.onFrame(fd.frameDurationUiNanos / 1_000_000f, fd.isJank, SystemClock.uptimeMillis())
        }
    }

    override fun onResume() {
        super.onResume()
        jankStats?.isTrackingEnabled = true
    }

    override fun onPause() {
        super.onPause()
        jankStats?.isTrackingEnabled = false
    }

    @Suppress("DEPRECATION")
    private fun currentRefreshRate(): Float = windowManager.defaultDisplay.refreshRate

    /** SPIKE.md §3: measure at 60 Hz even on 90/120 Hz panels. */
    @Suppress("DEPRECATION")
    private fun request60Hz(): Float {
        val display = windowManager.defaultDisplay
        val cur = display.mode
        Log.i("StoryRig", "display modes: " + display.supportedModes.joinToString { "${it.modeId}:${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate}" } + " current=${cur.modeId}")
        val mode = display.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .minByOrNull { abs(it.refreshRate - 60f) }
        if (mode != null) {
            window.attributes = window.attributes.also {
                it.preferredDisplayModeId = mode.modeId
                it.preferredRefreshRate = mode.refreshRate
            }
            return mode.refreshRate
        }
        return display.refreshRate
    }
}
