package com.example.storyrig.scenes

import com.example.storyrig.engine.Clip
import com.example.storyrig.engine.Ease
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dance clips for the standard humanoid skeleton (bone names from `:tools` cutref). Pure data: any character
 * with these bones plays them. Degrees. Two kinds of rotation (see [Clip]):
 * - `rot`: in the picture plane, positive = clockwise on screen; arms raised to the side (near arm: + is out,
 *   far arm: - is out). Shoulders are measured from the arm hanging straight down (KaiRig.hang*).
 * - `pitch`: forward (> 0) or back round the body's left-right axis: knee lifts, knee bends (negative), arm and
 *   forearm swings. Drawn the way they look from the cut's yaw, so they are right from the front, side or back.
 * The near leg and arm are on the viewer's left.
 */
object DanceClips {
    val WAVE = Clip.build("wave", 4f) {
        groove(4, 34f)
        sway(4f, 5f)
        nod(4)
        rot("torso") { at(0f, 3f); at(2f, -3f) }
        // A child's wave: upper arm out to the side, elbow a little above the shoulder; the forearm rocks between ~30
        // degrees out from vertical and vertical (elbow 45..76 on a ~104 degree arm). Cartoon upper arms are short,
        // so rocking past vertical would put the hand on the ear; it never opens past straight either. The wrist
        // trails the forearm by a tenth of a beat.
        rot("shoulder_near") { at(0f, 102f); at(2f, 106f) }
        rot("elbow_near") { for (b in 0 until 4) { at(b + 0f, 45f); at(b + .5f, 76f) } }
        rot("wrist_near") { for (b in 0 until 4) { at(b + .1f, -12f); at(b + .6f, 12f) } }
        // The cut draws the back of the hand at the viewer; a wave shows the palm (thumb towards the head).
        roll("wrist_near") { at(0f, 180f) }
        rot("shoulder_far") { at(0f, -4f) }
        pitch("shoulder_far") { at(0f, 18f); at(2f, 26f) }
        pitch("elbow_far") { at(0f, 45f) }
    }

    /** Arms fling out (overshoot, hold), pump in, up in a V, pump in; a small squeeze before each fling. */
    val PUMP = Clip.build("pump", 4f) {
        groove(4, 30f)
        sway(4f, 4f)
        nod(4)
        rot("torso") { at(0f, 0f); at(2.3f, -4f); at(3.25f, 2f) }
        armHits("shoulder_near", 1f)
        armHits("shoulder_far", -1f)
        for (side in listOf("elbow_near", "elbow_far")) pitch(side) {
            at(0f, 60f, Ease.BACK); at(.3f, 8f); at(.85f, 8f, Ease.IO)
            at(1.25f, 65f); at(1.85f, 65f, Ease.BACK); at(2.3f, 5f); at(2.85f, 5f, Ease.IO)
            at(3.25f, 60f); at(3.85f, 72f)
        }
    }

    /**
     * Knees up in turn, arms swinging against the legs. The weight goes onto the standing leg before the other
     * lifts (hips over it), the lifted side's hip rises and the chest leans back against it; the body dips as each
     * foot lands, never rising off the standing leg.
     */
    val MARCH = Clip.build("march", 2f) {
        ty("root") { for (b in 0 until 2) { at(b + 0f, 6f, Ease.OUT); at(b + .5f, 0f, Ease.IN) } }
        tx("root") { at(.35f, 6f); at(.9f, 0f); at(1.35f, -6f); at(1.9f, 0f) }
        rot("root") { at(.35f, 2.5f); at(.9f, 0f); at(1.35f, -2.5f); at(1.9f, 0f) }
        rot("torso") { at(.35f, -4f); at(.9f, 0f); at(1.35f, 4f); at(1.9f, 0f) }
        nod(2)
        knee("near", 0f)
        knee("far", 1f)
        rot("shoulder_near") { at(0f, 4f) }
        rot("shoulder_far") { at(0f, -4f) }
        pitch("shoulder_near") { at(.45f, -25f); at(1.45f, 32f) }
        pitch("elbow_near") { at(.45f, 10f); at(1.45f, 40f) }
        pitch("shoulder_far") { at(.45f, 32f); at(1.45f, -25f) }
        pitch("elbow_far") { at(.45f, 40f); at(1.45f, 10f) }
    }

    /**
     * Standing still, for any scene: slow weight shift from foot to foot (the feet stay planted by FootLock,
     * so the legs lean), the torso leaning against it, the head turning to look one way, holding, then the
     * other way; arms hanging loose. Breathing comes from AutoLayers.
     */
    val IDLE = Clip.build("idle", 8f) {
        tx("root") { at(0f, -7f); at(4f, 7f) }
        ty("root") { at(0f, 0f); at(2f, 3f); at(4f, 0f); at(6f, 3f) }
        rot("torso") { at(0f, 1.5f); at(4f, -1.5f) }
        rot("head") { at(0f, 2f); at(1.2f, -6f); at(3.4f, -6f); at(4.6f, 4f); at(6.8f, 4f) }
        rot("shoulder_near") { at(0f, 5f); at(4f, 8f) }
        pitch("elbow_near") { at(0f, 8f); at(4f, 14f) }
        rot("shoulder_far") { at(0f, -8f); at(4f, -5f) }
        pitch("elbow_far") { at(0f, 14f); at(4f, 8f) }
        // Relaxed hands turn in towards the thighs: seen from the front they narrow, instead of showing their back.
        roll("wrist_near") { at(0f, 55f) }
        roll("wrist_far") { at(0f, 55f) }
    }

    val ALL = arrayOf(WAVE, PUMP, MARCH, IDLE)

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
            pitch("hip_$side") { for (b in 0 until beats) { at(b + 0f, hipDeg, Ease.OUT); at(b + .5f, 0f, Ease.IN) } }
            pitch("knee_$side") { for (b in 0 until beats) { at(b + 0f, -knee, Ease.OUT); at(b + .5f, 0f, Ease.IN) } }
            pitch("ankle_$side") { for (b in 0 until beats) { at(b + 0f, -ankle, Ease.OUT); at(b + .5f, 0f, Ease.IN) } }
        }
    }

    /** Hips shift side to side over [period] beats: weight from one foot to the other (FootLock keeps the feet). */
    private fun Clip.Builder.sway(period: Float, px: Float) {
        tx("root") { at(0f, -px); at(period / 2f, px) }
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

    /** Knee lift starting at [beat], lasting one beat: thigh well up, shin hanging a little back, foot level. */
    private fun Clip.Builder.knee(side: String, beat: Float) {
        pitch("hip_$side") { at(beat, 0f); at(beat + .45f, 60f); at(beat + .95f, 0f, Ease.HOLD) }
        pitch("knee_$side") { at(beat, 0f); at(beat + .45f, -85f); at(beat + .95f, 0f, Ease.HOLD) }
        pitch("ankle_$side") { at(beat, 0f); at(beat + .45f, 25f); at(beat + .95f, 0f, Ease.HOLD) }
    }

    private const val D2R = (kotlin.math.PI / 180.0).toFloat()
}
