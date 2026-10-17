package com.example.storyrig.engine

import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas

actual fun uptimeMillis(): Long = SystemClock.uptimeMillis()

actual fun runtimeAllocatedBytes(): Long =
    Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: -1L

actual fun logInfo(message: String) {
    Log.i("StoryRig", message)
}

actual fun traceBegin(name: String) = Trace.beginSection(name)
actual fun traceEnd() = Trace.endSection()

actual fun traceBeginAsync(name: String, cookie: Int) {
    if (Build.VERSION.SDK_INT >= 29) Trace.beginAsyncSection(name, cookie)
}

actual fun traceEndAsync(name: String, cookie: Int) {
    if (Build.VERSION.SDK_INT >= 29) Trace.endAsyncSection(name, cookie)
}

actual fun Canvas.drawPointsBatch(points: FloatArray, count: Int, paint: Paint) {
    nativeCanvas.drawPoints(points, 0, count * 2, paint.asFrameworkPaint())
}

/** Choreographer callback re-posted every frame: no coroutine or lambda allocation per frame. */
@Composable
actual fun FrameLoop(onFrame: FrameCallback) {
    val current = rememberUpdatedState(onFrame)
    DisposableEffect(Unit) {
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            var alive = true
            override fun doFrame(frameTimeNanos: Long) {
                if (!alive) return
                current.value.onFrame(frameTimeNanos)
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose {
            callback.alive = false
            choreographer.removeFrameCallback(callback)
        }
    }
}

private val scratchMatrix = android.graphics.Matrix()
private val scratchValues = FloatArray(9)

actual fun Canvas.concatAffine(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
    val v = scratchValues
    v[0] = a; v[1] = c; v[2] = e
    v[3] = b; v[4] = d; v[5] = f
    v[6] = 0f; v[7] = 0f; v[8] = 1f
    scratchMatrix.setValues(v)
    nativeCanvas.concat(scratchMatrix)
}

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.exposeTestTags(): Modifier = semantics { testTagsAsResourceId = true }
