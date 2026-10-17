package com.example.storyrig.engine

import kotlin.math.PI
import kotlin.math.pow

const val DEG = (PI / 180.0).toFloat()

fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k
fun clamp(v: Float, lo: Float = 0f, hi: Float = 1f): Float = if (v < lo) lo else if (v > hi) hi else v

object Ease {
    const val IO = 0
    const val IN = 1
    const val OUT = 2
    const val LIN = 3
    /** Sine in-out: softer start and stop than [IO], for swings. */
    const val SINE = 4
    /** Out with ~10% overshoot, then settles: snappy hits. */
    const val BACK = 5
    /** Holds the start value, jumps at the end (step keys). */
    const val HOLD = 6

    fun apply(type: Int, t: Float): Float = when (type) {
        IO -> if (t < .5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f
        IN -> t * t * t
        OUT -> 1f - (1f - t).pow(3)
        SINE -> .5f - .5f * kotlin.math.cos(PI.toFloat() * t)
        BACK -> { val u = t - 1f; 1f + u * u * (2.7f * u + 1.7f) }
        HOLD -> if (t >= 1f) 1f else 0f
        else -> t
    }
}

/** Small deterministic PRNG (xorshift) so scenes are repeatable from a seed. */
class Rng(seed: Long) {
    private var s = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    fun next(): Float {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        s = x
        return ((x ushr 40).toFloat() / (1L shl 24).toFloat())
    }

    fun range(a: Float, b: Float): Float = a + (b - a) * next()
}

/**
 * Two-bone IK, ported from reference/lake.html `ik()`.
 * Writes elbow (ex, ey) to out[0..1] and wrist (wx, wy) to out[2..3].
 */
fun ik2(
    sx: Float, sy: Float, tx: Float, ty: Float,
    l1: Float, l2: Float, outward: Int, out: FloatArray,
) {
    val dx = tx - sx
    val dy = ty - sy
    var d = kotlin.math.hypot(dx, dy)
    if (d == 0f) d = 1f
    val dd = clamp(d, kotlin.math.abs(l1 - l2) + 1f, l1 + l2 - .5f)
    val ux = dx / d
    val uy = dy / d
    val a = kotlin.math.acos(clamp((l1 * l1 + dd * dd - l2 * l2) / (2f * l1 * dd), -1f, 1f))
    val ca = kotlin.math.cos(a)
    val sa = kotlin.math.sin(a)
    // candidate elbows for +a and -a
    val e0x = sx + l1 * (ux * ca - uy * sa)
    val e0y = sy + l1 * (ux * sa + uy * ca)
    val e1x = sx + l1 * (ux * ca + uy * sa)
    val e1y = sy + l1 * (-ux * sa + uy * ca)
    val pick0 = if (outward < 0) e0x < e1x else e0x > e1x
    out[0] = if (pick0) e0x else e1x
    out[1] = if (pick0) e0y else e1y
    out[2] = sx + ux * dd
    out[3] = sy + uy * dd
}
