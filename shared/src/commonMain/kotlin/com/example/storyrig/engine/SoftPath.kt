package com.example.storyrig.engine

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

/**
 * A path whose control points follow up to 4 bones each (linear blend skinning).
 * Every frame: [deform] computes new points into a reused FloatArray, then the Path is rebuilt
 * with reset() + moveTo/lineTo/quadraticTo/cubicTo. No allocation after load.
 */
class SoftPath private constructor(
    private val verbs: ByteArray,
    private val bind: FloatArray,
    private val bones: IntArray,
    private val weights: FloatArray,
    val paint: Paint,
    val alpha: Float,
    /** > 0: a stroke of this width drawn as the fill of its outline (plan B item 2, `--es outline fill`). */
    private val outlineWidth: Float,
    private val outlineRound: Boolean,
) {
    val pointCount = bind.size / 2
    private val pts = FloatArray(bind.size)
    private val path = Path()
    private val outline = if (outlineWidth > 0f) StrokeOutline(pointCount, verbs.size) else null
    private val shape = Shape(Shape.PATH, path, 0f, 0f, 0f, 0f, 0f, paint, alpha)

    fun deform(sk: Skeleton, stats: RenderStats) {
        for (i in 0 until pointCount) {
            val x = bind[i * 2]
            val y = bind[i * 2 + 1]
            var ox = 0f
            var oy = 0f
            for (k in 0 until 4) {
                val w = weights[i * 4 + k]
                if (w == 0f) continue
                val b = bones[i * 4 + k]
                ox += w * sk.skinX(b, x, y)
                oy += w * sk.skinY(b, x, y)
            }
            pts[i * 2] = ox
            pts[i * 2 + 1] = oy
        }
        stats.softPoints += pointCount
        if (outline != null) {
            outline.build(verbs, verbs.size, pts, outlineWidth, outlineRound, path)
            return
        }
        path.reset()
        var p = 0
        for (v in verbs) {
            when (v) {
                M -> { path.moveTo(pts[p], pts[p + 1]); p += 2 }
                L -> { path.lineTo(pts[p], pts[p + 1]); p += 2 }
                Q -> { path.quadraticTo(pts[p], pts[p + 1], pts[p + 2], pts[p + 3]); p += 4 }
                C -> { path.cubicTo(pts[p], pts[p + 1], pts[p + 2], pts[p + 3], pts[p + 4], pts[p + 5]); p += 6 }
                Z -> path.close()
            }
        }
    }

    fun draw(canvas: Canvas, alphaMul: Float, stats: RenderStats) = shape.draw(canvas, alphaMul, stats)

    /**
     * Points are given in the bind-pose local space of [frame] (rig space when -1).
     * The skeleton must be in bind pose, with [Skeleton.captureBind] already called.
     */
    class Builder(private val sk: Skeleton, private val frame: Int = -1) {
        private val verbs = ArrayList<Byte>()
        private val bind = ArrayList<Float>()
        private val bones = ArrayList<Int>()
        private val weights = ArrayList<Float>()

        private fun pt(x: Float, y: Float, inf: IntArray, w: FloatArray) {
            if (frame < 0) {
                bind += x; bind += y
            } else {
                sk.updateWorld()
                bind += sk.worldX(frame, x, y)
                bind += sk.worldY(frame, x, y)
            }
            for (k in 0 until 4) {
                bones += if (k < inf.size) inf[k] else 0
                weights += if (k < w.size) w[k] else 0f
            }
        }

        fun moveTo(x: Float, y: Float, inf: IntArray, w: FloatArray = ONE) = apply { verbs += M; pt(x, y, inf, w) }
        fun lineTo(x: Float, y: Float, inf: IntArray, w: FloatArray = ONE) = apply { verbs += L; pt(x, y, inf, w) }
        fun quadTo(cx: Float, cy: Float, ci: IntArray, cw: FloatArray, x: Float, y: Float, inf: IntArray, w: FloatArray = ONE) =
            apply { verbs += Q; pt(cx, cy, ci, cw); pt(x, y, inf, w) }

        fun cubicTo(
            c1x: Float, c1y: Float, c1i: IntArray, c1w: FloatArray,
            c2x: Float, c2y: Float, c2i: IntArray, c2w: FloatArray,
            x: Float, y: Float, inf: IntArray, w: FloatArray = ONE,
        ) = apply { verbs += C; pt(c1x, c1y, c1i, c1w); pt(c2x, c2y, c2i, c2w); pt(x, y, inf, w) }

        fun close() = apply { verbs += Z }

        /** [outlineFill]: a stroke is drawn as the fill of its outline, rebuilt from the skinned points each frame. */
        fun build(
            color: Color, strokeWidth: Float, antiAlias: Boolean, alpha: Float = 1f, round: Boolean = true,
            outlineFill: Boolean = false,
        ): SoftPath {
            val asOutline = outlineFill && strokeWidth > 0f && Z !in verbs
            val paint = Paint().apply {
                isAntiAlias = antiAlias
                this.color = color
                if (strokeWidth > 0f && !asOutline) {
                    style = PaintingStyle.Stroke
                    this.strokeWidth = strokeWidth
                    strokeCap = if (round) StrokeCap.Round else StrokeCap.Butt
                    strokeJoin = StrokeJoin.Round
                }
            }
            return SoftPath(
                verbs.toByteArray(), bind.toFloatArray(), bones.toIntArray(), weights.toFloatArray(), paint, alpha,
                if (asOutline) strokeWidth else 0f, round,
            )
        }
    }

    companion object {
        const val M: Byte = 0
        const val L: Byte = 1
        const val Q: Byte = 2
        const val C: Byte = 3
        const val Z: Byte = 4
        val ONE = floatArrayOf(1f)
        val HALF = floatArrayOf(.5f, .5f)
    }
}
