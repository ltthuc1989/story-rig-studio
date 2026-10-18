package com.example.storyrig.engine

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Motion clips as data, shared by every character with the same bone names.
 * A clip is a set of channels (bone name + property); each channel is a list of keys in **beats**, and each key
 * carries the [Ease] of the segment that starts at it. Values are offsets added to the base pose (rotations in
 * degrees, translations in the character's own pixels, scales as multipliers), so the same clip fits any rig.
 * Two kinds of rotation: [ROT] turns a bone in the picture plane (an arm raised to the side), [PITCH] swings it
 * forwards (> 0) or back round the body's left-right axis (a knee lift, an arm swing); [ClipPlayer] draws a pitch
 * the way it looks from the cut's yaw, so one clip is right seen from the front, the side or behind.
 * Clips loop: the last key eases back to the first across the loop point.
 * Built once at load; [ClipPlayer] samples them without allocating.
 */
class Clip private constructor(
    val name: String,
    val beats: Float,
    val channelBone: Array<String>,
    val channelProp: IntArray,
    /** Keys of channel c are keyStart[c] until keyStart[c + 1]. */
    private val keyStart: IntArray,
    private val keyBeat: FloatArray,
    private val keyValue: FloatArray,
    private val keyEase: IntArray,
) {
    val channels get() = channelProp.size

    /** Channel [c] at [beat] (0 until [beats]). */
    fun sample(c: Int, beat: Float): Float {
        val s = keyStart[c]
        val e = keyStart[c + 1]
        if (e - s == 1) return keyValue[s]
        val t0: Float; val t1: Float; val v0: Float; val v1: Float; val ease: Int
        if (beat < keyBeat[s]) {
            t0 = keyBeat[e - 1] - beats; t1 = keyBeat[s]; v0 = keyValue[e - 1]; v1 = keyValue[s]; ease = keyEase[e - 1]
        } else {
            var i = s
            while (i + 1 < e && keyBeat[i + 1] <= beat) i++
            if (i == e - 1) {
                t0 = keyBeat[i]; t1 = keyBeat[s] + beats; v0 = keyValue[i]; v1 = keyValue[s]
            } else {
                t0 = keyBeat[i]; t1 = keyBeat[i + 1]; v0 = keyValue[i]; v1 = keyValue[i + 1]
            }
            ease = keyEase[i]
        }
        val k = if (t1 > t0) clamp((beat - t0) / (t1 - t0)) else 1f
        return lerp(v0, v1, Ease.apply(ease, k))
    }

    class Keys internal constructor() {
        internal val beat = ArrayList<Float>()
        internal val value = ArrayList<Float>()
        internal val ease = ArrayList<Int>()

        /** Key at [beat] with [value]; [ease] shapes the move from here to the next key. */
        fun at(beat: Float, value: Float, ease: Int = Ease.SINE) {
            this.beat += beat; this.value += value; this.ease += ease
        }
    }

    class Builder(private val name: String, private val beats: Float) {
        private val bones = ArrayList<String>()
        private val props = ArrayList<Int>()
        private val keys = ArrayList<Keys>()

        fun channel(bone: String, prop: Int, block: Keys.() -> Unit) {
            val k = Keys().apply(block)
            require(k.beat.isNotEmpty()) { "$name: $bone has no keys" }
            bones += bone; props += prop; keys += k
        }

        fun rot(bone: String, block: Keys.() -> Unit) = channel(bone, ROT, block)
        fun tx(bone: String, block: Keys.() -> Unit) = channel(bone, TX, block)
        fun ty(bone: String, block: Keys.() -> Unit) = channel(bone, TY, block)
        fun sx(bone: String, block: Keys.() -> Unit) = channel(bone, SX, block)
        fun sy(bone: String, block: Keys.() -> Unit) = channel(bone, SY, block)
        /** Forwards (> 0) or back swing, degrees; see [ClipPlayer]. */
        fun pitch(bone: String, block: Keys.() -> Unit) = channel(bone, PITCH, block)
        /** Turn round the bone's own length, degrees: 180 shows a hand's other face (palm up instead of down). */
        fun roll(bone: String, block: Keys.() -> Unit) = channel(bone, ROLL, block)

        fun build(): Clip {
            val start = IntArray(keys.size + 1)
            val kb = ArrayList<Float>(); val kv = ArrayList<Float>(); val ke = ArrayList<Int>()
            for ((c, k) in keys.withIndex()) {
                start[c] = kb.size
                val order = k.beat.indices.sortedBy { k.beat[it] }
                for (i in order) {
                    require(k.beat[i] in 0f..<beats) { "$name: key at ${k.beat[i]} outside 0..$beats" }
                    kb += k.beat[i]; kv += k.value[i]; ke += k.ease[i]
                }
            }
            start[keys.size] = kb.size
            return Clip(name, beats, bones.toTypedArray(), props.toIntArray(), start, kb.toFloatArray(), kv.toFloatArray(), ke.toIntArray())
        }
    }

    companion object {
        /** Property ids are the offsets in [Skeleton.local]. */
        const val TX = 0
        const val TY = 1
        const val ROT = 2
        const val SX = 3
        const val SY = 4
        /** Not a [Skeleton.local] slot: a swing round the body's left-right axis, projected by [ClipPlayer]. */
        const val PITCH = 5
        /** Not a [Skeleton.local] slot: a turn round the bone's own length (a hand turning over), [Skeleton.roll]. */
        const val ROLL = 6

        fun build(name: String, beats: Float, block: Builder.() -> Unit) = Builder(name, beats).apply(block).build()
    }
}

/** Which way a character's cut faces. */
enum class View(
    /** Yaw it stands for when the cut doesn't say (degrees, 0 = facing the viewer, > 0 = turned to screen right). */
    val yawDeg: Float,
) {
    Q3(30f), FRONT(0f), BACK(180f);

    companion object {
        /** character.csv `view` value (3q, front, back); null when unknown. */
        fun parse(s: String?): View? = when (s?.trim()?.lowercase()) {
            "3q" -> Q3
            "front" -> FRONT
            "back" -> BACK
            else -> null
        }
    }
}

/**
 * Plays [clips] on one skeleton, driven by a beat clock (scene time / beat length, or the audio clock), so the
 * motion stays on the music whatever the tempo. Switching clips crossfades over [play]'s fadeBeats.
 * Call [apply] after resetting the base pose and before [SecondaryMotion].
 *
 * [zeroDeg] lets a rig say where 0 degrees is for a bone (e.g. shoulders measured from the arm hanging down),
 * so clips are written in the same terms for every character.
 *
 * [yawDeg]: how far the cut is turned from facing the viewer (0 = front, 90 = side on facing screen right, 180 =
 * back). A [Clip.PITCH] swing adds up down the chain (thigh, then shin, then foot: absolute pitch P), and a bone
 * hanging down and pitched by P is seen from that yaw as turned by -atan2(sin P sin yaw, cos P) in the picture and
 * shortened along its length to sqrt(cos^2 P + sin^2 P sin^2 yaw) ([Skeleton.fore]): from the front a knee lift is
 * a shorter thigh, from the side a forward kick. null draws pitches as plain picture rotations (side on, as
 * before views existed). The player writes [Skeleton.fore], so the rig resets it each frame.
 */
class ClipPlayer(
    private val sk: Skeleton,
    private val clips: Array<Clip>,
    boneIndex: (String) -> Int,
    zeroDeg: (String) -> Float = { 0f },
    yawDeg: Float? = null,
) {
    /** Per clip, per channel: index into [acc] (a [Skeleton.local] slot, or the pitch slot), -1 when the rig lacks that bone. */
    private val slot = Array(clips.size) { ci ->
        val c = clips[ci]
        IntArray(c.channels) { ch ->
            boneIndex(c.channelBone[ch]).let { b ->
                when {
                    b < 0 -> -1
                    c.channelProp[ch] == Clip.PITCH -> sk.size * 5 + b
                    c.channelProp[ch] == Clip.ROLL -> sk.size * 6 + b
                    else -> b * 5 + c.channelProp[ch]
                }
            }
        }
    }
    private val zero = Array(clips.size) { ci ->
        val c = clips[ci]
        FloatArray(c.channels) { ch -> if (c.channelProp[ch] == Clip.ROT) zeroDeg(c.channelBone[ch]) else 0f }
    }
    /** Local offsets per bone (5 slots), then one pitch and one roll per bone. */
    private val acc = FloatArray(sk.size * 7)
    private val sinYaw = if (yawDeg == null) 1f else sin(yawDeg * DEG)
    private val projected = yawDeg != null
    /** Absolute pitch (radians) per bone and its picture angle, this frame. */
    private val pitch = FloatArray(sk.size)
    private val shown = FloatArray(sk.size)

    var current = -1
        private set
    private var startBeat = 0f
    private var prev = -1
    private var prevStart = 0f
    private var fadeStart = 0f
    private var fadeBeats = 0f

    /** Starts clip [index] at [beat] (round it to a whole beat to land on the music). */
    fun play(index: Int, beat: Float, fadeBeats: Float = .5f) {
        if (index == current) return
        prev = if (current >= 0 && fadeBeats > 0f) current else -1
        prevStart = startBeat
        current = index; startBeat = beat; fadeStart = beat; this.fadeBeats = fadeBeats
    }

    fun apply(beat: Float) {
        if (current < 0) return
        acc.fill(0f)
        var w = 1f
        if (prev >= 0) {
            w = Ease.apply(Ease.SINE, clamp((beat - fadeStart) / fadeBeats))
            if (w >= 1f) prev = -1 else add(prev, beat - prevStart, 1f - w)
        }
        add(current, beat - startBeat, w)
        val po = sk.size * 5
        for (b in 0 until sk.size) {
            val o = b * 5
            // Root translations are in the character's pixels: scale them like its parts.
            val tsx = if (sk.parent[b] < 0) sk.local[o + 3] else 1f
            val tsy = if (sk.parent[b] < 0) sk.local[o + 4] else 1f
            sk.local[o] += acc[o] * tsx
            sk.local[o + 1] += acc[o + 1] * tsy
            var r = acc[o + 2] * DEG
            val p = sk.parent[b]
            val pp = if (p >= 0) pitch[p] else 0f
            val pt = pp + acc[po + b] * DEG
            pitch[b] = pt
            if (pt == 0f && pp == 0f) {
                shown[b] = 0f
            } else if (!projected) {
                shown[b] = -pt
                r += shown[b] - (if (p >= 0) shown[p] else 0f)
            } else {
                val sp = sin(pt); val cp = cos(pt)
                shown[b] = -atan2(sp * sinYaw, cp)
                r += shown[b] - (if (p >= 0) shown[p] else 0f)
                if (pt != 0f) sk.fore[b] *= maxOf(sqrt(cp * cp + sp * sp * sinYaw * sinYaw), MIN_FORE)
            }
            sk.local[o + 2] += r
            val rl = acc[sk.size * 6 + b]
            if (rl != 0f) {
                // Never exactly edge on: the inverse for the bone's children must stay finite.
                val c = cos(rl * DEG)
                sk.roll[b] = if (c >= 0f) maxOf(c, MIN_ROLL) else minOf(c, -MIN_ROLL)
            }
            sk.local[o + 3] *= 1f + acc[o + 3]
            sk.local[o + 4] *= 1f + acc[o + 4]
        }
    }

    private fun add(ci: Int, localBeat: Float, w: Float) {
        val c = clips[ci]
        var t = localBeat % c.beats
        if (t < 0f) t += c.beats
        val s = slot[ci]
        val z = zero[ci]
        for (ch in 0 until c.channels) {
            val j = s[ch]
            if (j < 0) continue
            var v = c.sample(ch, t) + z[ch]
            val prop = c.channelProp[ch]
            if (prop == Clip.SX || prop == Clip.SY) v -= 1f
            acc[j] += v * w
        }
    }

    private companion object {
        /** A bone pointing straight at the viewer still shows a little (and the inverse stays finite). */
        const val MIN_FORE = .25f
        /** A hand edge on still shows a sliver. */
        const val MIN_ROLL = .08f
    }
}
