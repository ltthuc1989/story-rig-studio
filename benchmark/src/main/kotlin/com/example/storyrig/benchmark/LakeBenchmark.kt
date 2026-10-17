package com.example.storyrig.benchmark

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P3 `explore_lake`: 5 iterations x 20 s of scripted taps and hat drags (SPIKE.md §5, §6).
 * Frames are only collected during the 20 s; the cold start and first 2 s happen in setupBlock.
 */
@RunWith(AndroidJUnit4::class)
class LakeBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Main number: pure vector, no static-layer cache, per-shape alpha under water. */
    @Test
    fun p3_vector() = run()

    /** Plan B item 1, for comparison. */
    @Test
    fun p3_cache() = run("cache" to "1")

    /** Under-water parts and reflection through saveLayer instead of per-shape alpha. */
    @Test
    fun p3_saveLayer() = run("water" to "layer")

    @OptIn(ExperimentalMetricApi::class)
    private fun run(vararg extra: Pair<String, String>) = rule.measureRepeated(
        packageName = PKG,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        // No startupMode: COLD would kill the app right before the measure block. Each iteration still
        // starts cold, because setupBlock kills the process itself before launching.
        startupMode = null,
        iterations = 5,
        setupBlock = {
            killProcess()
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            val intent = Intent().setClassName(PKG, "$PKG.MainActivity")
                .putExtra("scene", "p3").putExtra("auto", "0").putExtra("start", "seat").putExtra("hud", "0")
            for ((k, v) in extra) intent.putExtra(k, v)
            startActivityAndWait(intent)
            device.wait(Until.hasObject(By.res("stage")), 5_000)
            Thread.sleep(2_000) // SPIKE.md: worst frame and allocation are judged after the first 2 s
        },
    ) {
        Script(this).play()
    }

    /** Taps and drags in design units (960x540), mapped the same way the app letterboxes. */
    private class Script(private val scope: MacrobenchmarkScope) {
        private val device = scope.device
        private val bounds: Rect = device.findObject(By.res("stage"))?.visibleBounds
            ?: Rect(0, 0, device.displayWidth, device.displayHeight)
        private val s = minOf(bounds.width() / 960f, bounds.height() / 540f)
        private val ox = bounds.left + (bounds.width() - 960f * s) / 2f
        private val oy = bounds.top + (bounds.height() - 540f * s) / 2f
        private val t0 = SystemClock.uptimeMillis()

        private fun x(dx: Float) = (ox + dx * s).toInt()
        private fun y(dy: Float) = (oy + dy * s).toInt()

        private fun at(sec: Float) {
            val wait = t0 + (sec * 1000).toLong() - SystemClock.uptimeMillis()
            if (wait > 0) Thread.sleep(wait)
        }

        private fun tap(sec: Float, dx: Float, dy: Float) {
            at(sec); device.click(x(dx), y(dy))
        }

        private fun drag(sec: Float, x0: Float, y0: Float, x1: Float, y1: Float) {
            at(sec); device.drag(x(x0), y(y0), x(x1), y(y1), 90) // ~0.5 s, like a child's drag
        }

        fun play() {
            drag(0f, 205f, 466f, 452f, 238f) // hat from the grass onto Ben's head (Ben at the log, standing)
            tap(1.2f, 446f, 350f) // Ben jumps, hat follows the head bone
            tap(2.4f, 498f, 410f) // reeds
            tap(2.8f, 530f, 480f)
            tap(3.2f, 230f, 388f) // daisy
            tap(3.6f, 700f, 455f) // lily pad (frog sits here)
            tap(4.0f, 800f, 480f) // open water ripple
            tap(4.4f, 560f, 528f) // rock splash
            tap(4.8f, 70f, 214f) // tree
            tap(5.2f, 132f, 92f) // sun
            tap(5.6f, 700f, 436f) // frog
            tap(6.2f, 200f, 420f) // bank: Ben walks left wearing the hat
            tap(9.6f, 200f, 350f) // Ben jumps
            tap(10.6f, 410f, 421f) // log: walk back and sit
            tap(14.6f, 410f, 330f) // Ben (sitting) waves
            drag(15.6f, 420f, 262f, 205f, 470f) // hat back to the grass
            tap(16.6f, 486f, 420f)
            tap(17.0f, 150f, 370f)
            tap(17.4f, 596f, 496f)
            tap(17.8f, 590f, 534f)
            tap(18.2f, 760f, 110f) // cloud
            tap(18.6f, 862f, 398f)
            tap(19.0f, 640f, 430f)
            at(20f)
        }
    }

    companion object {
        const val PKG = "com.example.storyrig"
    }
}
