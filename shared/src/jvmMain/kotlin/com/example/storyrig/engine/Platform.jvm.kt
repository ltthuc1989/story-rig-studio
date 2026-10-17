package com.example.storyrig.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint

// Desktop is only for looking at the picture while writing code; no numbers come from here.

actual fun uptimeMillis(): Long = System.nanoTime() / 1_000_000

actual fun runtimeAllocatedBytes(): Long = -1L

actual fun logInfo(message: String) = println("StoryRig: $message")

actual fun traceBegin(name: String) {}
actual fun traceEnd() {}
actual fun traceBeginAsync(name: String, cookie: Int) {}
actual fun traceEndAsync(name: String, cookie: Int) {}

actual fun Canvas.drawPointsBatch(points: FloatArray, count: Int, paint: Paint) {
    val r = paint.strokeWidth / 2f
    for (i in 0 until count) drawCircle(Offset(points[i * 2], points[i * 2 + 1]), r, paint)
}

@Composable
actual fun FrameLoop(onFrame: (Long) -> Unit) {
    val current = rememberUpdatedState(onFrame)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) PerfStats.shared.onFrame((t - last) / 1e6f, false, t / 1_000_000)
                last = t
                current.value(t)
            }
        }
    }
}

private val scratch = androidx.compose.ui.graphics.Matrix()

actual fun Canvas.concatAffine(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
    val v = scratch.values
    v.fill(0f)
    v[0] = a; v[1] = b; v[4] = c; v[5] = d; v[10] = 1f; v[12] = e; v[13] = f; v[15] = 1f
    concat(scratch)
}

actual fun Modifier.exposeTestTags(): Modifier = this
