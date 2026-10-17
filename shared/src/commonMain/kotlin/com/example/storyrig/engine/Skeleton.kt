package com.example.storyrig.engine

import androidx.compose.ui.graphics.Canvas
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bone tree stored in flat arrays. Parents must come before children.
 * Local transform per bone: x, y, rotation (radians), scaleX, scaleY (T * R * S).
 * World and skin matrices are 2D affine [a, b, c, d, e, f]: x' = a x + c y + e, y' = b x + d y + f.
 */
class Skeleton(val size: Int) {
    val parent = IntArray(size) { -1 }
    val local = FloatArray(size * 5)
    val world = FloatArray(size * 6)
    private val bindInv = FloatArray(size * 6)
    private val skin = FloatArray(size * 6)

    init {
        for (i in 0 until size) {
            local[i * 5 + 3] = 1f
            local[i * 5 + 4] = 1f
        }
    }

    fun set(i: Int, x: Float, y: Float, rotRad: Float = 0f, sx: Float = 1f, sy: Float = 1f) {
        val o = i * 5
        local[o] = x; local[o + 1] = y; local[o + 2] = rotRad; local[o + 3] = sx; local[o + 4] = sy
    }

    fun setPos(i: Int, x: Float, y: Float) {
        local[i * 5] = x; local[i * 5 + 1] = y
    }

    fun setRot(i: Int, rotRad: Float) {
        local[i * 5 + 2] = rotRad
    }

    fun setScale(i: Int, sx: Float, sy: Float) {
        local[i * 5 + 3] = sx; local[i * 5 + 4] = sy
    }

    fun updateWorld() {
        for (i in 0 until size) {
            val o = i * 5
            val r = local[o + 2]
            val cr = cos(r)
            val sr = sin(r)
            val la = cr * local[o + 3]
            val lb = sr * local[o + 3]
            val lc = -sr * local[o + 4]
            val ld = cr * local[o + 4]
            val le = local[o]
            val lf = local[o + 1]
            val w = i * 6
            val p = parent[i]
            if (p < 0) {
                world[w] = la; world[w + 1] = lb; world[w + 2] = lc; world[w + 3] = ld; world[w + 4] = le; world[w + 5] = lf
            } else {
                val q = p * 6
                val pa = world[q]; val pb = world[q + 1]; val pc = world[q + 2]
                val pd = world[q + 3]; val pe = world[q + 4]; val pf = world[q + 5]
                world[w] = pa * la + pc * lb
                world[w + 1] = pb * la + pd * lb
                world[w + 2] = pa * lc + pc * ld
                world[w + 3] = pb * lc + pd * ld
                world[w + 4] = pa * le + pc * lf + pe
                world[w + 5] = pb * le + pd * lf + pf
            }
        }
    }

    /** Freezes the current pose as bind pose for soft (skinned) shapes. */
    fun captureBind() {
        updateWorld()
        for (i in 0 until size) {
            val w = i * 6
            val a = world[w]; val b = world[w + 1]; val c = world[w + 2]
            val d = world[w + 3]; val e = world[w + 4]; val f = world[w + 5]
            val det = a * d - b * c
            val id = if (det == 0f) 0f else 1f / det
            bindInv[w] = d * id
            bindInv[w + 1] = -b * id
            bindInv[w + 2] = -c * id
            bindInv[w + 3] = a * id
            bindInv[w + 4] = (c * f - d * e) * id
            bindInv[w + 5] = (b * e - a * f) * id
        }
    }

    /** skin = world * bindInverse; call after [updateWorld] each frame before deforming soft shapes. */
    fun updateSkin() {
        for (i in 0 until size) {
            val w = i * 6
            val pa = world[w]; val pb = world[w + 1]; val pc = world[w + 2]
            val pd = world[w + 3]; val pe = world[w + 4]; val pf = world[w + 5]
            val la = bindInv[w]; val lb = bindInv[w + 1]; val lc = bindInv[w + 2]
            val ld = bindInv[w + 3]; val le = bindInv[w + 4]; val lf = bindInv[w + 5]
            skin[w] = pa * la + pc * lb
            skin[w + 1] = pb * la + pd * lb
            skin[w + 2] = pa * lc + pc * ld
            skin[w + 3] = pb * lc + pd * ld
            skin[w + 4] = pa * le + pc * lf + pe
            skin[w + 5] = pb * le + pd * lf + pf
        }
    }

    fun skinX(bone: Int, x: Float, y: Float): Float {
        val w = bone * 6
        return skin[w] * x + skin[w + 2] * y + skin[w + 4]
    }

    fun skinY(bone: Int, x: Float, y: Float): Float {
        val w = bone * 6
        return skin[w + 1] * x + skin[w + 3] * y + skin[w + 5]
    }

    fun worldX(bone: Int, x: Float, y: Float): Float {
        val w = bone * 6
        return world[w] * x + world[w + 2] * y + world[w + 4]
    }

    fun worldY(bone: Int, x: Float, y: Float): Float {
        val w = bone * 6
        return world[w + 1] * x + world[w + 3] * y + world[w + 5]
    }

    /** Multiplies the canvas by the bone's world transform (no allocation). */
    fun concat(canvas: Canvas, bone: Int) {
        val w = bone * 6
        canvas.concatAffine(world[w], world[w + 1], world[w + 2], world[w + 3], world[w + 4], world[w + 5])
    }
}
