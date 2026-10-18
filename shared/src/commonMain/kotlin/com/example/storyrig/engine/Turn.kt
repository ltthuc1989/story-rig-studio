package com.example.storyrig.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pseudo-3D turn of a flat cut around the vertical axis (reference/bobo.html: `yaw`, `FEAT`), for any rig with
 * these bones. A cut is drawn at [viewYaw] (0 = facing the viewer, > 0 = turned towards screen right, PI = back);
 * [apply] bends it towards another yaw, so between two cuts the figure keeps turning instead of flipping:
 * - shoulders and hips orbit the body: their distance from the middle follows cos(yaw);
 * - torso and pelvis narrow towards the side view (.74 + .26 |cos|), the head a little (.82 + .18 |cos|);
 * - face features ride round the head as on a sphere: x = R sin(asin(x0 / R) + yaw), width cos(...), fading out
 *   as they go round the side.
 * The head has its own yaw (it leads the body in a turn). Call [apply] after the rig's rest reset, before clips.
 */
class PseudoYaw(
    private val sk: Skeleton,
    /** The cut's own yaw (radians). */
    val viewYaw: Float,
    /** The rig is drawn mirrored (root scaleX < 0): its local x runs the other way on screen. */
    private val mirror: Boolean,
    private val root: Int,
    private val torso: Int,
    private val head: Int,
    /** Bones under [head] drawn with it (hair); -1 when none. */
    private val hair: Int,
    /** Shoulders (children of [torso]) and hips (children of [root]): the bones that orbit. */
    private val lateral: IntArray,
    /** Their rest local x. */
    private val lateralRestX: FloatArray,
    /** Face parts on [head] (eyes, brows, mouth), cut separately from the head picture. */
    private val features: Array<BitmapPart>,
    /** Head sphere radius, in head-local px. */
    private val headR: Float,
) {
    private val cosView = cos(viewYaw)
    private val bodyView = bodyWidth(viewYaw)
    private val headView = headWidth(viewYaw)

    fun apply(yaw: Float, headYaw: Float) {
        val wBody = bodyWidth(yaw) / bodyView
        sk.wide[torso] = wBody
        sk.wide[root] = wBody
        // Orbit: |cos| shrinks the spread; a cut seen from behind (cos < 0) keeps its own left and right.
        val k = maxOf(cos(yaw) / cosView, MIN_SPREAD)
        for (j in lateral.indices) {
            val b = lateral[j]
            sk.local[b * 5] = lateralRestX[j] * k / wBody
        }
        val wHead = headWidth(headYaw) / headView
        sk.wide[head] = wHead
        if (hair >= 0) sk.wide[hair] = wHead
        val d = if (mirror) viewYaw - headYaw else headYaw - viewYaw
        for (f in features) {
            val s = f.sprite
            val cx0 = s.dx + s.region.w * .5f
            val a0 = asin((cx0 / headR).coerceIn(-.98f, .98f))
            val a = a0 + d
            val c = cos(a)
            f.alpha = ((c - FADE_FROM) / FADE_WIDTH).coerceIn(0f, 1f)
            if (f.alpha <= 0f) continue
            f.shiftX = headR * sin(a) / wHead - cx0
            f.scaleX = maxOf(c / cos(a0), .05f) / wHead
        }
    }

    /** Back to the cut as drawn. */
    fun clear() {
        for (f in features) { f.shiftX = 0f; f.scaleX = 1f; f.alpha = 1f }
    }

    companion object {
        fun bodyWidth(yaw: Float) = .74f + .26f * abs(cos(yaw))
        fun headWidth(yaw: Float) = .82f + .18f * abs(cos(yaw))
        /** At the side view both shoulders meet in the middle; never let them cross over inside one cut. */
        private const val MIN_SPREAD = .08f
        /** A feature starts fading when cos(its angle on the head) drops below FADE_FROM + FADE_WIDTH. */
        private const val FADE_FROM = .12f
        private const val FADE_WIDTH = .3f
    }
}

/**
 * The turn move of reference/bobo.html (`ACT.turn`) as data: wind up the other way, the head leads, two little
 * hops, overshoot, settle. Sampled at u in 0..1 for a turn of [delta] radians; small turns get a smaller wind-up,
 * overshoot, head lead and hops ([amount]).
 */
object TurnMove {
    /** Key times (fraction of the move), from Bobo's 0, 220, 440, 650, 860, 1070, 1280, 1560 ms. */
    private val T = floatArrayOf(0f, .141f, .282f, .417f, .551f, .686f, .821f, 1f)
    /** Ease of the segment that ends at each key. */
    private val E = intArrayOf(Ease.IO, Ease.OUT, Ease.IN, Ease.LIN, Ease.LIN, Ease.OUT, Ease.IO, Ease.IO)
    /** Share of the turn done (Bobo: yaw 1.25, 3.4, 5.5 of 2 PI). */
    private val DONE = floatArrayOf(0f, 0f, .199f, .541f, .875f, 1f, 1f, 1f)
    /** Wind-up and overshoot (radians, scaled by [amount]). */
    private val EXTRA = floatArrayOf(0f, -.35f, 0f, 0f, 0f, .2f, 0f, 0f)
    /** Head yaw ahead of the body (radians). */
    private val LEAD = floatArrayOf(0f, -.12f, .55f, .5f, .3f, -.12f, 0f, 0f)
    /** Height off the ground (Bobo px, < 0 = crouch): jy negated. */
    private val HOP = floatArrayOf(0f, -8f, 16f, 0f, 14f, -7f, 0f, 0f)
    /** Squash: drawn (1 + sq) wide, (1 - sq) tall. */
    private val SQ = floatArrayOf(0f, .08f, -.04f, .03f, -.03f, .08f, -.02f, 0f)

    /** Length (beats at 120 BPM) for a turn of [delta]: Bobo's full spin is 1.56 s. */
    fun beats(delta: Float) = 1f + 2.1f * abs(delta) / (2f * PI.toFloat())

    /** 1 for a quarter turn or more, less for a small one. */
    fun amount(delta: Float) = minOf(1f, abs(delta) / (.5f * PI.toFloat()))

    fun yaw(u: Float, delta: Float): Float {
        val s = if (delta < 0f) -1f else 1f
        return s * (sample(DONE, u) * abs(delta) + sample(EXTRA, u) * amount(delta))
    }

    fun lead(u: Float, delta: Float) = (if (delta < 0f) -1f else 1f) * sample(LEAD, u) * amount(delta)
    fun hop(u: Float, delta: Float) = sample(HOP, u) * amount(delta)
    fun squash(u: Float, delta: Float) = sample(SQ, u) * amount(delta)

    private fun sample(v: FloatArray, u: Float): Float {
        if (u <= 0f) return v[0]
        if (u >= 1f) return v[v.size - 1]
        var i = 0
        while (i < T.size - 2 && T[i + 1] <= u) i++
        val k = Ease.apply(E[i + 1], ((u - T[i]) / (T[i + 1] - T[i])).coerceIn(0f, 1f))
        return v[i] + (v[i + 1] - v[i]) * k
    }
}
