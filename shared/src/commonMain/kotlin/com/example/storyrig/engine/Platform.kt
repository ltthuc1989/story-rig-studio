package com.example.storyrig.engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint

/** Same time base as PointerInputChange.uptimeMillis. */
expect fun uptimeMillis(): Long

/** Total bytes the managed runtime has allocated since process start, or -1 when unknown. */
expect fun runtimeAllocatedBytes(): Long

expect fun logInfo(message: String)

expect fun traceBegin(name: String)
expect fun traceEnd()
expect fun traceBeginAsync(name: String, cookie: Int)
expect fun traceEndAsync(name: String, cookie: Int)

/**
 * Draws the first [count] points of [points] (x,y pairs) with one call.
 * Compose's Canvas.drawRawPoints always draws the whole array, so the engine goes through this.
 */
expect fun Canvas.drawPointsBatch(points: FloatArray, count: Int, paint: Paint)

/** Calls [onFrame] once per display frame with the frame time in nanoseconds, without allocating. */
@Composable
expect fun FrameLoop(onFrame: (Long) -> Unit)

/**
 * Multiplies the canvas by the 2D affine [a b c d e f]. Compose's Canvas.concat(Matrix) allocates a new
 * android.graphics.Matrix on every call (ui-graphics 1.10), so the engine uses this instead.
 */
expect fun Canvas.concatAffine(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float)

/** Makes Compose test tags visible to UiAutomator as resource ids (Android only). */
expect fun Modifier.exposeTestTags(): Modifier
