package com.example.storyrig.scenes

import com.example.storyrig.engine.Clip
import com.example.storyrig.engine.Ease
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dance clips for the standard humanoid skeleton (bone names from `:tools` cutref). Pure data: any character
 * with these bones plays them. Rotations in degrees, positive = clockwise on screen; shoulders are measured
 * from the arm hanging straight down (see KaiRig.hang*). Characters face right, so "forward" is negative
 * for hips and shoulders.
 */
object DanceClips {
    val WAVE = Clip.build("wave", 4f) {
        groove(4, 34f)
        nod(4)
        rot("torso") { at(0f, 3f); at(2f, -3f) }
        // Raised near arm, waving from the elbow; the wrist trails the elbow by a tenth of a beat.
        rot("shoulder_near") { at(0f, 112f); at(2f, 120f) }
        rot("elbow_near") { for (b in 0 until 4) { at(b + 0f, 18f); at(b + .5f, 62f) } }
        rot("wrist_near") { for (b in 0 until 4) { at(b + .1f, -22f); at(b + .6f, 18f) } }
        rot("shoulder_far") { at(0f, -18f); at(2f, -26f) }
        rot("elbow_far") { at(0f, -45f) }
    }

    /** Arms fling out (overshoot, hold), pump in, up in a V, pump in; a small squeeze before each fling. */
    val PUMP = Clip.build("pump", 4f) {
        groove(4, 30f)
        nod(4)
        rot("torso") { at(0f, 0f); at(2.3f, -4f); at(3.25f, 2f) }
        armHits("shoulder_near", 1f)
        armHits("shoulder_far", -1f)
        for (side in listOf("elbow_near", "elbow_far")) rot(side) {
            at(0f, -60f, Ease.BACK); at(.3f, -8f); at(.85f, -8f, Ease.IO)
            at(1.25f, -65f); at(1.85f, -65f, Ease.BACK); at(2.3f, -5f); at(2.85f, -5f, Ease.IO)
            at(3.25f, -60f); at(3.85f, -72f)
        }
    }

    /** Knees up in turn, arms swinging against the legs. */
    val MARCH = Clip.build("march", 2f) {
        ty("root") { for (b in 0 until 2) { at(b + 0f, 5f, Ease.OUT); at(b + .5f, -7f, Ease.IN) } }
        nod(2)
        rot("torso") { at(0f, -2f); at(1f, 2f) }
        knee("near", 0f)
        knee("far", 1f)
        rot("shoulder_near") { at(0f, 25f); at(1f, -32f) }
        rot("elbow_near") { at(0f, -10f); at(1f, -48f) }
        rot("shoulder_far") { at(0f, -32f); at(1f, 25f) }
        rot("elbow_far") { at(0f, -48f); at(1f, -10f) }
    }

    val ALL = arrayOf(WAVE, PUMP, MARCH)

    /** Thigh and shin in the cut (ref.png px): used to keep the feet on the ground while the knees bend. */
    private const val THIGH = 62f
    private const val SHIN = 118f

    /**
     * Bounce on every beat with the feet planted: the root drops by exactly what the bent legs lose in height,
     * and the shin angle keeps the foot under the hip. Down on the beat (fast), up between beats (slower).
     */
    private fun Clip.Builder.groove(beats: Int, hipDeg: Float) {
        val a = hipDeg * D2R
        val c = asin(THIGH / SHIN * sin(a))
        val drop = THIGH * (1f - cos(a)) + SHIN * (1f - cos(c))
        val knee = (a + c) / D2R
        val ankle = -c / D2R
        ty("root") { for (b in 0 until beats) { at(b + 0f, drop, Ease.OUT); at(b + .5f, 0f, Ease.IN) } }
        for (side in listOf("near", "far")) {
            rot("hip_$side") { for (b in 0 until beats) { at(b + 0f, -hipDeg, Ease.OUT); at(b + .5f, 0f, Ease.IN) } }
            rot("knee_$side") { for (b in 0 until beats) { at(b + 0f, knee, Ease.OUT); at(b + .5f, 0f, Ease.IN) } }
            rot("ankle_$side") { for (b in 0 until beats) { at(b + 0f, ankle, Ease.OUT); at(b + .5f, 0f, Ease.IN) } }
        }
    }

    /** Head dips just after each beat (follows the body, doesn't lead it). */
    private fun Clip.Builder.nod(beats: Int) = rot("head") {
        for (b in 0 until beats) { at(b + .15f, 5f); at(b + .65f, -1.5f) }
    }

    /** sign = 1 for the near arm (out is clockwise), -1 for the far arm. */
    private fun Clip.Builder.armHits(bone: String, sign: Float) = rot(bone) {
        at(0f, 8f * sign, Ease.BACK); at(.3f, 95f * sign); at(.85f, 95f * sign, Ease.IO)
        at(1.25f, 15f * sign); at(1.85f, 15f * sign, Ease.BACK); at(2.3f, 150f * sign); at(2.85f, 150f * sign, Ease.IO)
        at(3.25f, 18f * sign); at(3.85f, 8f * sign)
    }

    /** Knee lift starting at [beat], lasting one beat. */
    private fun Clip.Builder.knee(side: String, beat: Float) {
        rot("hip_$side") { at(beat, 0f); at(beat + .45f, -42f); at(beat + .95f, 0f, Ease.HOLD) }
        rot("knee_$side") { at(beat, 0f); at(beat + .45f, 72f); at(beat + .95f, 0f, Ease.HOLD) }
        rot("ankle_$side") { at(beat, 0f); at(beat + .45f, -18f); at(beat + .95f, 0f, Ease.HOLD) }
    }

    private const val D2R = (kotlin.math.PI / 180.0).toFloat()
}
