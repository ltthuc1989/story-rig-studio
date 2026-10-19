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
        // The other arm hangs loose; a small forward swing only (a big one shortens forearm and hand from the front).
        rot("shoulder_far") { at(0f, -6f); at(2f, -9f) }
        pitch("shoulder_far") { at(0f, 6f); at(2f, 10f) }
        pitch("elbow_far") { at(0f, 12f) }
    }

    /** Arms fling out (overshoot, hold), pump in, up in a V, pump in; a small squeeze before each fling. */
    val PUMP = Clip.build("pump", 4f) {
        groove(4, 30f)
        sway(4f, 4f)
        nod(4)
        rot("torso") { at(0f, 0f); at(2.3f, -4f); at(3.25f, 2f) }
        armHits("shoulder_near", 1f)
        armHits("shoulder_far", -1f)
        // Elbows bend in the picture: the arms are out to the sides, so seen from the front a pump folds the forearms
        // up or in across the picture, not towards the viewer (that would shorten forearm and hand to stubs). Near
        // arm: - folds up from an arm held out; the far arm mirrors it.
        // Overlap with the shoulders (armHits): an arm that opens out or goes up straightens at the elbow first, the
        // shoulder swinging after it; an arm coming down stays straight until it is near the side and folds last.
        // Both moving together bends the forearm against the swing, like a broken arm.
        for ((side, sign) in listOf("elbow_near" to 1f, "elbow_far" to -1f)) rot(side) {
            at(0f, -60f * sign, Ease.OUT); at(.12f, -10f * sign); at(.3f, -8f * sign); at(.95f, -8f * sign, Ease.IO)
            at(1.25f, -65f * sign); at(1.85f, -65f * sign, Ease.OUT); at(1.97f, -10f * sign); at(2.3f, -5f * sign)
            at(3.05f, -6f * sign, Ease.IO); at(3.3f, -60f * sign); at(3.85f, -72f * sign)
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
     * Walking in place, Ben's walk in the lake scene (reference/lake.html renderBoy) told as clip keys: one step a
     * beat; the hips dip as a foot lands and rise as the other passes (Ben: -2.6 cos 2ph on a 64-unit leg, about 8 px
     * on Kai's); the leading leg swings forward, the trailing one back, the passing knee folds; the arms swing
     * against the legs (Ben: +-15 degrees) with a soft elbow; the chest turns a little against the hips and the head
     * nods with each step.
     */
    val WALK = Clip.build("walk", 2f) {
        ty("root") { for (b in 0 until 2) { at(b + 0f, 8f, Ease.IO); at(b + .5f, -3f, Ease.IO) } }
        rot("root") { at(0f, 1.5f, Ease.IO); at(1f, -1.5f, Ease.IO) }
        rot("torso") { at(0f, -2f, Ease.IO); at(1f, 2f, Ease.IO) }
        nod(2)
        for ((side, phase) in listOf("near" to 0f, "far" to 1f)) {
            // Contact (leg ahead) at phase, passing at phase + .5 (knee folded), back at phase + 1.
            cycle("hip_$side", phase, 22f, 4f, -18f, 6f)
            cycle("knee_$side", phase, -4f, -6f, -14f, -45f)
            cycle("ankle_$side", phase, -8f, 2f, 12f, 10f)
        }
        rot("shoulder_near") { at(0f, 5f) }
        rot("shoulder_far") { at(0f, -5f) }
        for ((side, phase) in listOf("near" to 1f, "far" to 0f)) {
            cycle("shoulder_$side", phase, 15f, 0f, -15f, 0f)
            cycle("elbow_$side", phase, 18f, 12f, 6f, 12f)
        }
    }

    /** A 2-beat cycle of pitch keys at [phase], +.5, +1, +1.5 beats, wrapped into the clip. */
    private fun Clip.Builder.cycle(bone: String, phase: Float, vararg v: Float) = pitch(bone) {
        for ((t, x) in v.indices.map { ((phase + it * .5f) % 2f) to v[it] }.sortedBy { it.first }) at(t, x, Ease.IO)
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

    /**
     * Joint check, not a dance: one extreme pose per beat, held (tools/joint_sheet.sh shoots each near the end of its
     * beat, once the springs settle). Every joint at the far end of what the dances do now or the planned actions will
     * (sit, bow, kick, reach, point, hands on hips, a stride), so a joint that breaks shows before a clip needs it.
     * Pose k (beat k): 0 rest, 1 arms out, 2 arms up in a V, 3 hands to the chest, 4 elbows bent up, 5 hands turned
     * over, 6 knee up, 7 sit, 8 kick back, 9 ankles turned, 10 bow, 11 head tilt, 12 hair and skirt swung, 13 arms
     * reaching forward, 14 hands on hips, 15 stride.
     */
    val STRESS = Clip.build("stress", 16f) {
        held(
            mapOf(),
            mapOf("r:shoulder_near" to 90f, "r:shoulder_far" to -90f),
            mapOf("r:shoulder_near" to 150f, "r:shoulder_far" to -150f),
            mapOf("r:shoulder_near" to 15f, "r:elbow_near" to -110f, "r:shoulder_far" to -15f, "r:elbow_far" to 110f),
            mapOf("r:shoulder_near" to 90f, "r:elbow_near" to 120f, "r:shoulder_far" to -90f, "r:elbow_far" to -120f),
            mapOf("r:shoulder_near" to 30f, "r:shoulder_far" to -30f, "o:wrist_near" to 180f, "o:wrist_far" to 180f),
            mapOf("p:hip_near" to 90f, "p:knee_near" to -90f),
            mapOf("p:hip_near" to 90f, "p:knee_near" to -90f, "p:hip_far" to 90f, "p:knee_far" to -90f, "y:root" to 70f),
            mapOf("p:hip_near" to -40f, "p:knee_near" to -90f),
            mapOf("p:ankle_near" to 30f, "p:ankle_far" to -30f),
            mapOf("p:torso" to 30f, "p:head" to 15f),
            mapOf("r:head" to 15f, "r:torso" to 4f),
            mapOf("r:hair" to -7f, "r:skirt" to 8f, "r:torso" to -4f),
            mapOf("p:shoulder_near" to 90f, "p:shoulder_far" to 90f),
            mapOf("r:shoulder_near" to 40f, "r:elbow_near" to -100f, "r:shoulder_far" to -40f, "r:elbow_far" to 100f),
            mapOf(
                "p:hip_near" to 45f, "p:knee_near" to -30f, "p:hip_far" to -35f, "p:knee_far" to -60f,
                "p:shoulder_near" to -40f, "p:shoulder_far" to 40f, "p:elbow_near" to 60f, "p:elbow_far" to 60f,
            ),
        )
    }

    /** The dances the scene takes turns with, in [ALL]'s order. */
    val DANCES = arrayOf(WAVE, PUMP, MARCH, IDLE, WALK)

    /** Every clip a rig can play by name (`--es dance <name>`): the dances, then clips played only on request. */
    val ALL = DANCES + STRESS

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

    /**
     * sign = 1 for the near arm (out is clockwise), -1 for the far arm. Opening out and going up start slowly while
     * the elbow straightens (PUMP's elbow keys), then swing with an overshoot; coming down leads with a straight arm.
     */
    private fun Clip.Builder.armHits(bone: String, sign: Float) = rot(bone) {
        at(0f, 8f * sign, Ease.IN); at(.12f, 22f * sign, Ease.BACK); at(.35f, 95f * sign); at(.85f, 95f * sign, Ease.IO)
        at(1.25f, 15f * sign); at(1.85f, 15f * sign, Ease.IN); at(1.97f, 30f * sign, Ease.BACK); at(2.35f, 150f * sign)
        at(2.85f, 150f * sign, Ease.IO); at(3.2f, 22f * sign, Ease.OUT); at(3.3f, 18f * sign); at(3.85f, 8f * sign)
    }

    /** Knee lift starting at [beat], lasting one beat: thigh well up, shin hanging a little back, foot level. */
    private fun Clip.Builder.knee(side: String, beat: Float) {
        pitch("hip_$side") { at(beat, 0f); at(beat + .45f, 60f); at(beat + .95f, 0f, Ease.HOLD) }
        pitch("knee_$side") { at(beat, 0f); at(beat + .45f, -85f); at(beat + .95f, 0f, Ease.HOLD) }
        pitch("ankle_$side") { at(beat, 0f); at(beat + .45f, 25f); at(beat + .95f, 0f, Ease.HOLD) }
    }

    /**
     * One pose per beat, held for the beat. Keys are "<channel>:<bone>" (r rot, p pitch, o roll, x tx, y ty); a channel
     * a pose leaves out is 0 there.
     */
    private fun Clip.Builder.held(vararg poses: Map<String, Float>) {
        val props = mapOf('r' to Clip.ROT, 'p' to Clip.PITCH, 'o' to Clip.ROLL, 'x' to Clip.TX, 'y' to Clip.TY)
        for (key in poses.flatMap { it.keys }.distinct()) {
            channel(key.substringAfter(':'), props.getValue(key[0])) {
                for ((i, p) in poses.withIndex()) at(i.toFloat(), p[key] ?: 0f, Ease.HOLD)
            }
        }
    }

    private const val D2R = (kotlin.math.PI / 180.0).toFloat()
}
