package com.example.storyrig.engine

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

abstract class DrawItem {
    var visible = true
}

/** Rigid shapes in a bone's local space, drawn with the bone's world matrix. Optional clip in the same space. */
class Part(val bone: Int, val shapes: Array<Shape>, val clip: Path? = null) : DrawItem()

/** Skinned path; its points are already in rig space after [SoftPath.deform]. */
class SoftItem(val soft: SoftPath) : DrawItem()

/**
 * Bitmap image rigidly attached to a bone. Swapping [sprite] (another mouth, other eyes) changes the picture
 * without allocating.
 */
class BitmapPart(val bone: Int, val atlas: Atlas, var sprite: Sprite, antiAlias: Boolean) : DrawItem() {
    /** Part name (parts.csv), for [DistortProbe] reports. */
    var label = ""
    /**
     * Drawn with [Skeleton.concatRigid]: foreshortening and roll move the bone's children but never squash this
     * picture (a rigid cut: what a squash would fake is a substitute drawing, KaiRig.applySwaps).
     */
    var rigid = false
    val paint = Paint().apply {
        isAntiAlias = antiAlias
        filterQuality = FilterQuality.Low
    }
    private var lastAlpha = 1f
    /**
     * Per-frame slide along the bone's local x and width about the sprite's centre, plus an opacity: a face feature
     * moving round the head when it turns ([PseudoYaw]). 0, 1, 1 = as cut.
     */
    var shiftX = 0f
    var scaleX = 1f
    var alpha = 1f

    fun draw(canvas: Canvas, sk: Skeleton, alphaMul: Float, stats: RenderStats) {
        val al = alphaMul * alpha
        if (al <= 0.003f) return
        if (al != lastAlpha) { paint.alpha = al; lastAlpha = al }
        val s = sprite
        val r = s.region
        canvas.save()
        if (rigid) sk.concatRigid(canvas, bone) else sk.concat(canvas, bone)
        if (shiftX != 0f || scaleX != 1f) {
            val cx = s.dx + r.w * .5f
            canvas.translate(cx + shiftX, 0f)
            canvas.scale(scaleX, 1f)
            canvas.translate(-cx, 0f)
        }
        canvas.drawImageRect(atlas.image, IntOffset(r.x, r.y), IntSize(r.w, r.h), IntOffset(s.dx, s.dy), IntSize(r.w, r.h), paint)
        canvas.restore()
        stats.draws++
        stats.images++
        if (DistortProbe.on) DistortProbe.record(sk, bone, scaleX, label, rigid)
    }
}

/**
 * Debug (desktop snapshots, tools/joint_sheet.sh): how far the most squashed drawn picture is from its cut shape
 * this frame. A rigid picture drawn with a matrix whose two axes differ (a thigh shortened towards the viewer, a
 * hand rolled edge on) looks deformed: [worst] is the smaller over the larger singular value of the part's matrix,
 * 1 = only turned and uniformly scaled. Reset before each frame.
 */
object DistortProbe {
    var on = false
    var worst = 1f
        private set
    var worstLabel = ""
        private set

    fun reset() { worst = 1f; worstLabel = "" }

    private val m = FloatArray(4)

    /** [rigid]: the part is drawn with [Skeleton.rigidMatrix], so that is what it shows. */
    fun record(sk: Skeleton, bone: Int, scaleX: Float, label: String, rigid: Boolean) {
        val w = bone * 6
        if (rigid) sk.rigidMatrix(bone, m) else { m[0] = sk.world[w]; m[1] = sk.world[w + 1]; m[2] = sk.world[w + 2]; m[3] = sk.world[w + 3] }
        val a = m[0] * scaleX; val b = m[1] * scaleX
        val c = m[2]; val d = m[3]
        val t = a * a + b * b + c * c + d * d
        val det = a * d - b * c
        val q = kotlin.math.sqrt(maxOf(t * t - 4f * det * det, 0f))
        val hi = kotlin.math.sqrt((t + q) / 2f)
        if (hi <= 0f) return
        val ratio = kotlin.math.sqrt(maxOf((t - q) / 2f, 0f)) / hi
        if (ratio < worst) { worst = ratio; worstLabel = label }
    }
}

/**
 * Textured triangle mesh skinned to several bones, so a limb bends smoothly at its joints instead of two rigid
 * pieces meeting at a hinge. Vertices are in rig space after [SoftMesh.deform]; drawn in one call.
 */
class MeshPart(val mesh: SoftMesh, val atlas: Atlas, antiAlias: Boolean) : DrawItem() {
    val paint = Paint().apply {
        isAntiAlias = antiAlias
        filterQuality = FilterQuality.Low
        shader = atlas.shader
    }
    private var lastAlpha = 1f

    fun draw(canvas: Canvas, alphaMul: Float, stats: RenderStats) {
        if (alphaMul <= 0.003f) return
        if (alphaMul != lastAlpha) { paint.alpha = alphaMul; lastAlpha = alphaMul }
        val d = mesh.data
        canvas.drawMeshBatch(mesh.positions, d.tex, d.vertexCount, d.indices, d.indices.size, paint)
        stats.draws++
    }
}

/** A skeleton plus its draw list. Draw code is shared by every character and prop. */
class Rig(val sk: Skeleton, val items: Array<DrawItem>) {
    val softPointCount: Int = items.sumOf { if (it is SoftItem) it.soft.pointCount else 0 }
    val meshVertCount: Int = items.sumOf { if (it is MeshPart) it.mesh.data.vertexCount else 0 }
    val meshTriangleCount: Int = items.sumOf { if (it is MeshPart) it.mesh.data.triangleCount else 0 }
    val shapeCount: Int = items.sumOf { if (it is Part) it.shapes.size else 1 }

    /** World matrices + skin matrices + soft path / mesh deform. Call once per frame after posing. */
    fun pose(stats: RenderStats) {
        sk.updateWorld()
        if (softPointCount == 0 && meshVertCount == 0) return
        sk.updateSkin()
        for (i in items.indices) {
            val it = items[i]
            if (!it.visible) continue
            if (it is SoftItem) it.soft.deform(sk, stats)
            else if (it is MeshPart) it.mesh.deform(sk, stats)
        }
    }

    /** Draws items [from] until [to] (all by default), so two rigs can share one figure (head of one, body of another). */
    fun draw(canvas: Canvas, alphaMul: Float, m: Matrix, stats: RenderStats, from: Int = 0, to: Int = items.size) {
        for (i in from until to) {
            val it = items[i]
            if (!it.visible) continue
            if (it is Part) {
                canvas.save()
                sk.concat(canvas, it.bone)
                val c = it.clip
                if (c != null) canvas.clipPath(c)
                it.shapes.drawAll(canvas, alphaMul, stats)
                canvas.restore()
            } else if (it is SoftItem) {
                it.soft.draw(canvas, alphaMul, stats)
            } else if (it is BitmapPart) {
                it.draw(canvas, sk, alphaMul, stats)
            } else if (it is MeshPart) {
                it.draw(canvas, alphaMul, stats)
            }
        }
    }
}
