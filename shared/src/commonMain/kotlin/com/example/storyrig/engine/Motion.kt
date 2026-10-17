package com.example.storyrig.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Motion layers that sit on top of any skeleton, for every character and scene:
 *   base pose (clip or scene code sets local angles)
 *   -> [SecondaryMotion] (springs: joints lag, overshoot and swing with their parent's motion)
 *   -> [AutoLayers] (squash/stretch from the root's vertical acceleration, breathing)
 *   -> Rig.pose()
 * All state is in FloatArrays sized at construction; nothing allocates per frame.
 */

/**
 * Spring settings for one bone. [inertia] scales how much the pivot's acceleration swings it (hair, hands).
 * (massX, massY): unit direction, in the bone's rest local space, from the pivot to where its parts' weight is
 * (a hand lies along the arm, hair sits above the head's pivot). Defaults to hanging straight down.
 */
class SpringBone(val bone: Int, val k: Float, val damp: Float, val inertia: Float = 0f, val maxDev: Float = .6f) {
    var massX = 0f
    var massY = 1f
}

class SecondaryMotion(private val sk: Skeleton, specs: List<SpringBone>) {
    private val n = specs.size
    private val bone = IntArray(n) { specs[it].bone }
    private val k = FloatArray(n) { specs[it].k }
    private val damp = FloatArray(n) { specs[it].damp }
    private val inertia = FloatArray(n) { specs[it].inertia }
    private val maxDev = FloatArray(n) { specs[it].maxDev }
    private val mx = FloatArray(n) { specs[it].massX }
    private val my = FloatArray(n) { specs[it].massY }
    private val angle = FloatArray(n)
    private val vel = FloatArray(n)
    private val px = FloatArray(n)
    private val py = FloatArray(n)
    private val pvx = FloatArray(n)
    private val pvy = FloatArray(n)
    private var started = false

    /** Call after the base pose has set local angles. Replaces each spring bone's angle by its spring value. */
    fun apply(dtSec: Float) {
        if (n == 0) return
        val dt = dtSec.coerceIn(1f / 240f, 1f / 30f)
        sk.updateWorld()
        for (i in 0 until n) {
            val b = bone[i]
            val target = sk.local[b * 5 + 2]
            val w = b * 6
            val wx = sk.world[w + 4]; val wy = sk.world[w + 5]
            if (!started) {
                angle[i] = target; vel[i] = 0f; px[i] = wx; py[i] = wy; pvx[i] = 0f; pvy[i] = 0f
                continue
            }
            val vx = (wx - px[i]) / dt; val vy = (wy - py[i]) / dt
            val ax = (vx - pvx[i]) / dt; val ay = (vy - pvy[i]) / dt
            px[i] = wx; py[i] = wy; pvx[i] = vx; pvy[i] = vy
            // Where the bone's weight is, in world space; inertia pushes it against the pivot's acceleration,
            // like a pendulum.
            var dx = sk.world[w] * mx[i] + sk.world[w + 2] * my[i]
            var dy = sk.world[w + 1] * mx[i] + sk.world[w + 3] * my[i]
            val l = hypot(dx, dy)
            if (l > 0f) { dx /= l; dy /= l }
            val torque = inertia[i] * (dy * ax - dx * ay) * TORQUE_SCALE
            val acc = k[i] * (target - angle[i]) - damp[i] * vel[i] + torque
            vel[i] += acc * dt
            angle[i] += vel[i] * dt
            val dev = angle[i] - target
            if (abs(dev) > maxDev[i]) { angle[i] = target + if (dev > 0f) maxDev[i] else -maxDev[i]; vel[i] = 0f }
            sk.local[b * 5 + 2] = angle[i]
        }
        started = true
    }

    private companion object {
        /** World units are stage px; this keeps inertia values around 0..2. */
        const val TORQUE_SCALE = .0006f
    }
}

/** Squash and stretch on the root, breathing on the torso. Multiplies the scales the base pose set. */
class AutoLayers(
    private val sk: Skeleton,
    private val root: Int,
    private val torso: Int,
    /** Squash per unit of upward acceleration (stage px/s^2). */
    private val squashGain: Float = .00004f,
    private val squashMax: Float = .07f,
    private val breathAmp: Float = .012f,
    private val breathPeriodSec: Float = 3.4f,
    phase: Float = 0f,
) {
    private var lastY = Float.NaN
    private var lastVy = 0f
    private var squash = 0f
    private var squashVel = 0f
    private var t = phase

    fun apply(dtSec: Float) {
        val dt = dtSec.coerceIn(1f / 240f, 1f / 30f)
        t += dt
        val o = root * 5
        val y = sk.local[o + 1]
        if (lastY.isNaN()) lastY = y
        val vy = (y - lastY) / dt
        val ay = (vy - lastVy) / dt
        lastY = y; lastVy = vy
        // Upward acceleration (negative y on screen) at the bottom of a bounce squashes, downward stretches.
        val target = (-ay * squashGain).coerceIn(-squashMax, squashMax)
        squashVel += (400f * (target - squash) - 28f * squashVel) * dt
        squash += squashVel * dt
        sk.local[o + 3] *= 1f + squash * .6f
        sk.local[o + 4] *= 1f - squash
        val breath = 1f + breathAmp * sin(2f * PI.toFloat() * t / breathPeriodSec)
        sk.local[torso * 5 + 4] *= breath
    }
}

/**
 * Default springs for the standard humanoid skeleton (bone names from `:tools` cutref). Bones a character
 * does not have are skipped, so the same table works for every humanoid.
 */
object HumanoidMotion {
    fun springs(boneIndex: (String) -> Int): List<SpringBone> {
        val out = ArrayList<SpringBone>()
        fun add(name: String, k: Float, damp: Float, inertia: Float, maxDev: Float = .6f) {
            val i = boneIndex(name)
            if (i >= 0) out += SpringBone(i, k, damp, inertia, maxDev)
        }
        // Parents first: the arm chain lags more the further it is from the body.
        add("head", 160f, 13f, .25f, .25f)
        add("hair", 70f, 5.5f, 1.4f, .12f)
        for (side in listOf("near", "far")) {
            add("shoulder_$side", 420f, 26f, 0f)
            add("elbow_$side", 260f, 17f, .3f)
            add("wrist_$side", 170f, 10f, .8f)
        }
        return out
    }
}
