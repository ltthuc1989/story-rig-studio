package com.example.storyrig.engine

/**
 * Animated values live in one FloatArray addressed by index constants; tweens use a fixed slot pool.
 * Time is scene milliseconds. Nothing here allocates after construction.
 */
class Anim(valueCount: Int, private val maxTweens: Int = 64) {
    val v = FloatArray(valueCount)
    var now = 0f
        private set

    private val idx = IntArray(maxTweens) { -1 }
    private val from = FloatArray(maxTweens)
    private val to = FloatArray(maxTweens)
    private val t0 = FloatArray(maxTweens)
    private val dur = FloatArray(maxTweens)
    private val ease = IntArray(maxTweens)

    fun tween(i: Int, target: Float, ms: Float, easeType: Int = Ease.IO) {
        var free = -1
        for (s in 0 until maxTweens) {
            if (idx[s] == i) idx[s] = -1
            if (free < 0 && idx[s] < 0) free = s
        }
        if (ms <= 0f || free < 0) {
            v[i] = target
            return
        }
        idx[free] = i; from[free] = v[i]; to[free] = target; t0[free] = now; dur[free] = ms; ease[free] = easeType
    }

    fun cancel(i: Int) {
        for (s in 0 until maxTweens) if (idx[s] == i) idx[s] = -1
    }

    fun busy(i: Int): Boolean {
        for (s in 0 until maxTweens) if (idx[s] == i) return true
        return false
    }

    fun step(nowMs: Float) {
        now = nowMs
        for (s in 0 until maxTweens) {
            val i = idx[s]
            if (i < 0) continue
            val k = clamp((now - t0[s]) / dur[s])
            v[i] = lerp(from[s], to[s], Ease.apply(ease[s], k))
            if (k >= 1f) idx[s] = -1
        }
    }
}

/** Receives events and answers conditions for [SeqPlayer]. */
interface SeqHost {
    /** Runs a scene event; returns how many ms the sequence should wait afterwards. */
    fun onEvent(id: Int): Float
    fun condition(id: Int): Boolean
}

/** Compiled action timeline: ops packed 5 floats each. Built once at scene load. */
class Seq private constructor(val ops: FloatArray) {
    val size get() = ops.size / 5

    class Builder {
        private val ops = ArrayList<Float>()
        private fun op(code: Int, a: Float = 0f, b: Float = 0f, c: Float = 0f, d: Float = 0f) {
            ops += code.toFloat(); ops += a; ops += b; ops += c; ops += d
        }

        /** Starts a tween and continues immediately. */
        fun tween(i: Int, to: Float, ms: Float, ease: Int = Ease.IO) = op(TWEEN, i.toFloat(), to, ms, ease.toFloat())
        fun set(i: Int, value: Float) = op(SET, i.toFloat(), value)
        fun wait(ms: Float) = op(WAIT, ms)
        fun event(id: Int) = op(EVENT, id.toFloat())
        fun until(cond: Int) = op(UNTIL, cond.toFloat())

        /** Skips the next [n] ops when the condition holds. */
        fun skipIf(cond: Int, n: Int) = op(SKIP_IF, cond.toFloat(), n.toFloat())
        fun build() = Seq(ops.toFloatArray())
    }

    companion object {
        const val TWEEN = 0
        const val SET = 1
        const val WAIT = 2
        const val EVENT = 3
        const val UNTIL = 4
        const val SKIP_IF = 5

        fun build(block: Builder.() -> Unit): Seq = Builder().apply(block).build()
    }
}

class SeqPlayer(private val anim: Anim) {
    var seq: Seq? = null
        private set
    private var pc = 0
    private var waitUntil = 0f
    private var untilCond = -1

    val running get() = seq != null

    fun play(s: Seq) {
        seq = s; pc = 0; waitUntil = anim.now; untilCond = -1
    }

    fun stop() {
        seq = null
    }

    fun step(host: SeqHost) {
        while (true) {
            val s = seq ?: return
            if (anim.now < waitUntil) return
            if (untilCond >= 0) {
                if (!host.condition(untilCond)) return
                untilCond = -1
            }
            if (pc >= s.size) {
                seq = null
                return
            }
            val o = pc * 5
            val ops = s.ops
            pc++
            when (ops[o].toInt()) {
                Seq.TWEEN -> anim.tween(ops[o + 1].toInt(), ops[o + 2], ops[o + 3], ops[o + 4].toInt())
                Seq.SET -> anim.v[ops[o + 1].toInt()] = ops[o + 2]
                Seq.WAIT -> waitUntil = anim.now + ops[o + 1]
                Seq.EVENT -> {
                    val ms = host.onEvent(ops[o + 1].toInt())
                    if (seq !== s) return // event replaced the sequence
                    if (ms > 0f) waitUntil = anim.now + ms
                }
                Seq.UNTIL -> untilCond = ops[o + 1].toInt()
                Seq.SKIP_IF -> if (host.condition(ops[o + 1].toInt())) pc += ops[o + 2].toInt()
            }
        }
    }
}

/** Damped springs around a moving target (x follows target). */
class Springs(val size: Int) {
    val x = FloatArray(size)
    val v = FloatArray(size)
    val target = FloatArray(size)
    val k = FloatArray(size) { 120f }
    val damp = FloatArray(size) { 8f }

    fun step(dt: Float) {
        for (i in 0 until size) {
            val a = -k[i] * (x[i] - target[i]) - damp[i] * v[i]
            v[i] += a * dt
            x[i] += v[i] * dt
        }
    }

    fun kick(i: Int, dv: Float) {
        v[i] += dv
    }
}
