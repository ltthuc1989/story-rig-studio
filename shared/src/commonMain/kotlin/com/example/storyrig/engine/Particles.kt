package com.example.storyrig.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap

/**
 * Fixed-capacity particle pool in parallel FloatArrays. Dead particles are swap-removed.
 * A particle dies when its age passes its life, or when it falls back below its floor.
 */
class Particles(val max: Int, color: Color, antiAlias: Boolean) {
    val x = FloatArray(max)
    val y = FloatArray(max)
    val vx = FloatArray(max)
    val vy = FloatArray(max)
    val age = FloatArray(max)
    val life = FloatArray(max)
    val size = FloatArray(max)
    val floor = FloatArray(max)
    var count = 0
        private set

    private val pts = FloatArray(max * 2)
    private val paint = Paint().apply { isAntiAlias = antiAlias; this.color = color }
    private val pointPaint = Paint().apply {
        isAntiAlias = antiAlias
        this.color = color
        style = PaintingStyle.Stroke
        strokeCap = StrokeCap.Round
    }

    fun emit(px: Float, py: Float, pvx: Float, pvy: Float, r: Float, lifeSec: Float, floorY: Float = Float.NaN) {
        if (count >= max) return
        val i = count++
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; age[i] = 0f; life[i] = lifeSec; size[i] = r; floor[i] = floorY
    }

    fun step(dt: Float, gravity: Float) {
        var i = 0
        while (i < count) {
            vy[i] += gravity * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            age[i] += dt
            val f = floor[i]
            val dead = age[i] >= life[i] || (!f.isNaN() && y[i] > f && vy[i] > 0f)
            if (dead) {
                val last = --count
                x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
                age[i] = age[last]; life[i] = life[last]; size[i] = size[last]; floor[i] = floor[last]
            } else i++
        }
    }

    /** One drawCircle per particle (sizes vary). */
    fun drawCircles(canvas: Canvas, stats: RenderStats) {
        for (i in 0 until count) {
            canvas.drawCircle(Offset(x[i], y[i]), size[i], paint)
        }
        stats.draws += count
        stats.particles += count
    }

    /** One batched points call; every particle uses [diameter]. */
    fun drawPoints(canvas: Canvas, diameter: Float, stats: RenderStats) {
        if (count == 0) return
        for (i in 0 until count) {
            pts[i * 2] = x[i]; pts[i * 2 + 1] = y[i]
        }
        pointPaint.strokeWidth = diameter
        canvas.drawPointsBatch(pts, count, pointPaint)
        stats.draws++
        stats.particles += count
    }
}

/** Expanding stroked ellipses on the water (pooled). */
class Ripples(val max: Int, antiAlias: Boolean) {
    private val x = FloatArray(max)
    private val y = FloatArray(max)
    private val size = FloatArray(max)
    private val t0 = FloatArray(max)
    private val dur = FloatArray(max)
    var count = 0
        private set
    private val paint = Paint().apply {
        isAntiAlias = antiAlias
        color = Color.White
        style = PaintingStyle.Stroke
    }

    fun add(px: Float, py: Float, maxSize: Float, ms: Float, now: Float) {
        if (count >= max) return
        val i = count++
        x[i] = px; y[i] = py; size[i] = maxSize; t0[i] = now; dur[i] = ms
    }

    fun draw(canvas: Canvas, now: Float, stats: RenderStats) {
        var i = 0
        while (i < count) {
            if (now < t0[i]) { i++; continue } // delayed start
            val k = clamp((now - t0[i]) / dur[i])
            if (k >= 1f) {
                val last = --count
                x[i] = x[last]; y[i] = y[last]; size[i] = size[last]; t0[i] = t0[last]; dur[i] = dur[last]
                continue
            }
            val rx = 4f + size[i] * k
            val ry = rx * .28f
            paint.strokeWidth = 2.2f * (1f - k) + .4f
            paint.alpha = (1f - k) * .85f
            canvas.drawOval(x[i] - rx, y[i] - ry, x[i] + rx, y[i] + ry, paint)
            stats.draws++
            stats.strokes++
            i++
        }
    }
}
