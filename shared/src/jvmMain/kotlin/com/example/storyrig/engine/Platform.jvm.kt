package com.example.storyrig.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.nativeCanvas

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

actual fun Canvas.drawMeshBatch(
    positions: FloatArray,
    texCoords: FloatArray,
    vertexCount: Int,
    indices: ShortArray,
    indexCount: Int,
    paint: Paint,
) {
    // Skia takes whole arrays; copies only when a caller passes a prefix (desktop is for looking, not numbers).
    val n = vertexCount * 2
    nativeCanvas.drawVertices(
        org.jetbrains.skia.VertexMode.TRIANGLES,
        if (positions.size == n) positions else positions.copyOf(n),
        null,
        if (texCoords.size == n) texCoords else texCoords.copyOf(n),
        if (indices.size == indexCount) indices else indices.copyOf(indexCount),
        org.jetbrains.skia.BlendMode.MODULATE,
        paint.asFrameworkPaint(),
    )
}

actual fun meshDrawSupported(): Boolean = true

actual fun meshShader(image: ImageBitmap): Shader = image.asSkiaBitmap().makeShader(
    org.jetbrains.skia.FilterTileMode.CLAMP, org.jetbrains.skia.FilterTileMode.CLAMP, org.jetbrains.skia.SamplingMode.LINEAR, null,
)

@Composable
actual fun FrameLoop(onFrame: FrameCallback) {
    val current = rememberUpdatedState(onFrame)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) PerfStats.shared.onFrame((t - last) / 1e6f, false, t / 1_000_000)
                last = t
                current.value.onFrame(t)
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
