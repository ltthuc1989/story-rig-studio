package com.example.storyrig.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `kai` (bitmap characters, P5 groundwork): 5 iterations x 20 s of the dance loop, no input.
 * Frames are only collected during the 20 s; the cold start and first 2 s happen in setupBlock.
 */
@RunWith(AndroidJUnit4::class)
class KaiBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun kai_5() = run(5)

    @Test
    fun kai_12() = run(12)

    @Test
    fun kai_5_mesh() = run(5, "mesh")

    @Test
    fun kai_12_mesh() = run(12, "mesh")

    /** Five different characters (Mia, Leo, Hana with the skirt bone, Bo), all cut in the 3q view. */
    @Test
    fun cast_5_mesh() = run(5, "mesh", "char" to "kai,mia,leo,hana,bo")

    /** Five Kai actors turning between 3q, front and back (three cuts loaded per actor). */
    @Test
    fun turn_5_mesh() = run(5, "mesh", "turn" to "kai")

    @OptIn(ExperimentalMetricApi::class)
    private fun run(count: Int, art: String = "bitmap", vararg extra: Pair<String, String>) = rule.measureRepeated(
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
                .putExtra("scene", "kai").putExtra("count", count.toString()).putExtra("hud", "0")
                .putExtra("art", art)
            for ((k, v) in extra) intent.putExtra(k, v)
            startActivityAndWait(intent)
            device.wait(Until.hasObject(By.res("stage")), 5_000)
            Thread.sleep(2_000) // SPIKE.md: judged after the first 2 s
        },
    ) {
        Thread.sleep(20_000)
    }

    private companion object {
        const val PKG = "com.example.storyrig"
    }
}
