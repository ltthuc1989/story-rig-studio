package com.example.storyrig.engine

import androidx.compose.ui.graphics.Canvas
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bone tree stored in flat arrays. Parents must come before children.
 * Local transform per bone: x, y, rotation (radians), scaleX, scaleY (T * R * S), times an optional
 * foreshortening along the bone's length ([fore], [axisX]/[axisY]).
 * World and skin matrices are 2D affine [a, b, c, d, e, f]: x' = a x + c y + e, y' = b x + d y + f.
 */
class Skeleton(val size: Int) {
    val parent = IntArray(size) { -1 }
    val local = FloatArray(size * 5)
    val world = FloatArray(size * 6)
    private val bindInv = FloatArray(size * 6)
    private val skin = FloatArray(size * 6)
    /** Per-bone dual-quaternion skin data, [DQ] floats per bone; valid after [updateSkin]. Read only. */
    val dq = FloatArray(size * DQ)

    /**
     * Foreshortening per bone: its parts (and its children's pivots) shrink by this factor along the unit axis
     * ([axisX], [axisY]) in the bone's rest local space, e.g. a forearm pointing at the viewer. 1 = none. Children
     * do not inherit it (their own frame is un-squashed), so a shin keeps its length when the thigh shortens.
     * Whoever writes it resets it each frame (KaiRig.reset).
     */
    val fore = FloatArray(size) { 1f }
    val axisX = FloatArray(size)
    val axisY = FloatArray(size) { 1f }
    /**
     * Width per bone: its parts (and its children's pivots) shrink by this factor along the bone's rest local x
     * (a torso seen turning sideways, [PseudoYaw]). 1 = none. Like [fore], children do not inherit it. Whoever
     * writes it resets it each frame.
     */
    val wide = FloatArray(size) { 1f }

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
            var la = cr * local[o + 3]
            var lb = sr * local[o + 3]
            var lc = -sr * local[o + 4]
            var ld = cr * local[o + 4]
            val le = local[o]
            val lf = local[o + 1]
            val w = i * 6
            val p = parent[i]
            val f = fore[i]
            if (f != 1f) {
                // L = L * A, A = I + (f - 1) u u^T: squash along the bone's own axis.
                val ux = axisX[i]; val uy = axisY[i]; val g = f - 1f
                val a00 = 1f + g * ux * ux; val a01 = g * ux * uy; val a11 = 1f + g * uy * uy
                val na = la * a00 + lc * a01; val nb = lb * a00 + ld * a01
                val nc = la * a01 + lc * a11; val nd = lb * a01 + ld * a11
                la = na; lb = nb; lc = nc; ld = nd
            }
            val wd = wide[i]
            if (wd != 1f) { la *= wd; lb *= wd }
            if (p >= 0 && fore[p] != 1f) {
                // L = A_parent^-1 * L: the child's own frame is not squashed with its parent (its pivot still is).
                val ux = axisX[p]; val uy = axisY[p]; val g = 1f / fore[p] - 1f
                val b00 = 1f + g * ux * ux; val b01 = g * ux * uy; val b11 = 1f + g * uy * uy
                val na = b00 * la + b01 * lb; val nb = b01 * la + b11 * lb
                val nc = b00 * lc + b01 * ld; val nd = b01 * lc + b11 * ld
                la = na; lb = nb; lc = nc; ld = nd
            }
            if (p >= 0 && wide[p] != 1f) {
                val iw = 1f / wide[p]
                la *= iw; lc *= iw
            }
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
            dualSkin(i)
        }
    }

    /**
     * Each skin matrix split for dual-quaternion blending ([SoftMesh]): skin = T * R(theta) * S, stored per bone as
     * [cos(theta/2), sin(theta/2), dualX, dualY, S00, S01, S11] (S10 is 0). The planar dual quaternion is
     * real = (cos, sin about z), dual = translation * real / 2.
     */
    private fun dualSkin(i: Int) {
        val w = i * 6
        val a = skin[w]; val b = skin[w + 1]; val c = skin[w + 2]; val d = skin[w + 3]
        val tx = skin[w + 4]; val ty = skin[w + 5]
        val len = sqrt(a * a + b * b)
        val o = i * DQ
        if (len == 0f) {
            dq[o] = 1f; dq[o + 1] = 0f; dq[o + 2] = .5f * tx; dq[o + 3] = .5f * ty
            dq[o + 4] = 0f; dq[o + 5] = 0f; dq[o + 6] = 0f
            return
        }
        val cosT = a / len
        val sinT = b / len
        val hc = sqrt(((1f + cosT) * .5f).coerceAtLeast(0f))
        val hs = sqrt(((1f - cosT) * .5f).coerceAtLeast(0f)).let { if (sinT < 0f) -it else it }
        dq[o] = hc
        dq[o + 1] = hs
        dq[o + 2] = .5f * (tx * hc + ty * hs)
        dq[o + 3] = .5f * (-tx * hs + ty * hc)
        dq[o + 4] = len
        dq[o + 5] = (a * c + b * d) / len
        dq[o + 6] = (a * d - b * c) / len
    }

    companion object {
        const val DQ = 7
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
