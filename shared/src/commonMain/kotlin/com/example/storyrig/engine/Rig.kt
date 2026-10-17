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
    val paint = Paint().apply {
        isAntiAlias = antiAlias
        filterQuality = FilterQuality.Low
    }
    private var lastAlpha = 1f

    fun draw(canvas: Canvas, sk: Skeleton, alphaMul: Float, stats: RenderStats) {
        if (alphaMul <= 0.003f) return
        if (alphaMul != lastAlpha) { paint.alpha = alphaMul; lastAlpha = alphaMul }
        val s = sprite
        val r = s.region
        canvas.save()
        sk.concat(canvas, bone)
        canvas.drawImageRect(atlas.image, IntOffset(r.x, r.y), IntSize(r.w, r.h), IntOffset(s.dx, s.dy), IntSize(r.w, r.h), paint)
        canvas.restore()
        stats.draws++
        stats.images++
    }
}

/** A skeleton plus its draw list. Draw code is shared by every character and prop. */
class Rig(val sk: Skeleton, val items: Array<DrawItem>) {
    val softPointCount: Int = items.sumOf { if (it is SoftItem) it.soft.pointCount else 0 }
    val shapeCount: Int = items.sumOf { if (it is Part) it.shapes.size else 1 }

    /** World matrices + skin matrices + soft path rebuild. Call once per frame after posing. */
    fun pose(stats: RenderStats) {
        sk.updateWorld()
        if (softPointCount == 0) return
        sk.updateSkin()
        for (i in items.indices) {
            val it = items[i]
            if (it is SoftItem && it.visible) it.soft.deform(sk, stats)
        }
    }

    fun draw(canvas: Canvas, alphaMul: Float, m: Matrix, stats: RenderStats) {
        for (i in items.indices) {
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
            }
        }
    }
}
