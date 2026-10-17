package com.example.storyrig.engine

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

/**
 * Plays [clips] on one skeleton, driven by a beat clock (scene time / beat length, or the audio clock), so the
 * motion stays on the music whatever the tempo. Switching clips crossfades over [play]'s fadeBeats.
 * Call [apply] after resetting the base pose and before [SecondaryMotion].
 *
 * [zeroDeg] lets a rig say where 0 degrees is for a bone (e.g. shoulders measured from the arm hanging down),
 * so clips are written in the same terms for every character.
 */
class ClipPlayer(
    private val sk: Skeleton,
    private val clips: Array<Clip>,
    boneIndex: (String) -> Int,
    zeroDeg: (String) -> Float = { 0f },
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
        for (b in 0 until sk.size) {
            val o = b * 5
            // Root translations are in the character's pixels: scale them like its parts.
            val tsx = if (sk.parent[b] < 0) sk.local[o + 3] else 1f
            val tsy = if (sk.parent[b] < 0) sk.local[o + 4] else 1f
            sk.local[o] += acc[o] * tsx
            sk.local[o + 1] += acc[o + 1] * tsy
            sk.local[o + 2] += acc[o + 2] * DEG
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
        }
    }
}
