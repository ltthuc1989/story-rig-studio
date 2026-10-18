package com.example.storyrig.engine

import kotlin.math.cos

/**
 * Motion clips as data, shared by every character with the same bone names.
 * A clip is a set of channels (bone name + property); each channel is a list of keys in **beats**, and each key
 * carries the [Ease] of the segment that starts at it. Values are offsets added to the base pose (rotations in
 * degrees, translations in the character's own pixels, scales as multipliers), so the same clip fits any rig.
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

        fun build(name: String, beats: Float, block: Builder.() -> Unit) = Builder(name, beats).apply(block).build()
    }
}

/** Which way a character's cut faces. Clips are written for [Q3] (three-quarter, facing right). */
enum class View {
    Q3, FRONT, BACK;

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
 * How clips written for the three-quarter view show in another [view], per bone name (data: one table per view,
 * shared by every character with the same bone names; see DanceClips.VIEWS).
 *
 * A clip rotation r of a listed bone is split in two: [Gain.keep] * r stays a rotation on screen (signed, so the
 * near and far leg can mirror each other), and [Gain.depth] * r is the part that turns towards or away from the
 * viewer. That depth angle adds up down the chain (thigh, then shin, then foot) and each bone is foreshortened
 * along its length by cos(depth angle) ([Skeleton.fore]): a knee lift seen from the front is a shorter thigh, not
 * a sideways kick. Angles > 0 may use their own gains ([Gain.keepPos], [Gain.depthPos]): an elbow bends forward
 * (towards the viewer) with negative angles but waves in the picture plane with positive ones.
 * Bones not listed keep their rotation.
 */
class ViewAdapt private constructor(val view: View, val gains: Map<String, Gain>) {
    class Gain(val keep: Float, val depth: Float, val keepPos: Float = keep, val depthPos: Float = depth)

    class Builder(private val view: View) {
        private val gains = LinkedHashMap<String, Gain>()
        fun bone(name: String, keep: Float, depth: Float, keepPos: Float = keep, depthPos: Float = depth) {
            gains[name] = Gain(keep, depth, keepPos, depthPos)
        }
        /** Same gains as another view (front and back differ only in which side is near). */
        fun copy(from: ViewAdapt) = gains.putAll(from.gains)
        fun build() = ViewAdapt(view, gains.toMap())
    }

    companion object {
        fun build(view: View, block: Builder.() -> Unit) = Builder(view).apply(block).build()
    }
}

/**
 * Plays [clips] on one skeleton, driven by a beat clock (scene time / beat length, or the audio clock), so the
 * motion stays on the music whatever the tempo. Switching clips crossfades over [play]'s fadeBeats.
 * Call [apply] after resetting the base pose and before [SecondaryMotion].
 *
 * [zeroDeg] lets a rig say where 0 degrees is for a bone (e.g. shoulders measured from the arm hanging down),
 * so clips are written in the same terms for every character.
 * [adapt]: how the clips show in the rig's view (front/back); null or a [View.Q3] table plays them as written.
 * An adapted player writes [Skeleton.fore] for every bone, so the rig resets that each frame.
 */
class ClipPlayer(
    private val sk: Skeleton,
    private val clips: Array<Clip>,
    boneIndex: (String) -> Int,
    zeroDeg: (String) -> Float = { 0f },
    adapt: ViewAdapt? = null,
) {
    /** Per clip, per channel: index into [Skeleton.local], or -1 when the rig lacks that bone. */
    private val slot = Array(clips.size) { ci ->
        val c = clips[ci]
        IntArray(c.channels) { ch -> boneIndex(c.channelBone[ch]).let { b -> if (b < 0) -1 else b * 5 + c.channelProp[ch] } }
    }
    private val zero = Array(clips.size) { ci ->
        val c = clips[ci]
        FloatArray(c.channels) { ch -> if (c.channelProp[ch] == Clip.ROT) zeroDeg(c.channelBone[ch]) else 0f }
    }
    private val acc = FloatArray(sk.size * 5)
    /** The [zeroDeg] part of [acc], so view gains apply to the clip's own angle only. */
    private val zacc = FloatArray(sk.size * 5)
    private val adapted = adapt != null && adapt.view != View.Q3
    private val keepNeg = FloatArray(sk.size) { 1f }
    private val depthNeg = FloatArray(sk.size)
    private val keepPos = FloatArray(sk.size) { 1f }
    private val depthPos = FloatArray(sk.size)
    /** Accumulated depth angle (radians) per bone, this frame. */
    private val pitch = FloatArray(sk.size)

    init {
        if (adapt != null && adapted) for ((name, g) in adapt.gains) {
            val b = boneIndex(name)
            if (b < 0) continue
            keepNeg[b] = g.keep; depthNeg[b] = g.depth; keepPos[b] = g.keepPos; depthPos[b] = g.depthPos
        }
    }

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
        zacc.fill(0f)
        var w = 1f
        if (prev >= 0) {
            w = Ease.apply(Ease.SINE, clamp((beat - fadeStart) / fadeBeats))
            if (w >= 1f) prev = -1 else add(prev, beat - prevStart, 1f - w)
        }
        add(current, beat - startBeat, w)
        for (b in 0 until sk.size) {
            val o = b * 5
            // Root translations are in the character's pixels: scale them like its parts.
            val tsx = if (sk.parent[b] < 0) sk.local[o + 3] else 1f
            val tsy = if (sk.parent[b] < 0) sk.local[o + 4] else 1f
            sk.local[o] += acc[o] * tsx
            sk.local[o + 1] += acc[o + 1] * tsy
            if (adapted) {
                val z = zacc[o + 2]
                val r = acc[o + 2] - z
                val pos = r > 0f
                val p = sk.parent[b]
                val pt = (if (pos) depthPos[b] else depthNeg[b]) * r * DEG + (if (p >= 0) pitch[p] else 0f)
                pitch[b] = pt
                sk.local[o + 2] += (z + (if (pos) keepPos[b] else keepNeg[b]) * r) * DEG
                sk.fore[b] = if (pt == 0f) 1f else maxOf(cos(pt), MIN_FORE)
            } else {
                sk.local[o + 2] += acc[o + 2] * DEG
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
            if (c.channelProp[ch] >= Clip.SX) v -= 1f
            acc[j] += v * w
            zacc[j] += z[ch] * w
        }
    }

    private companion object {
        /** A bone pointing straight at the viewer still shows a little (and the inverse stays finite). */
        const val MIN_FORE = .25f
    }
}
