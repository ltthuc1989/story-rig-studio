package com.example.storyrig.scenes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.example.storyrig.engine.ArtBuilder
import com.example.storyrig.engine.FootLock
import com.example.storyrig.engine.PathCache
import com.example.storyrig.engine.PerfStats
import com.example.storyrig.engine.RenderStats
import com.example.storyrig.engine.Rng
import com.example.storyrig.engine.Scene
import com.example.storyrig.engine.SceneConfig
import com.example.storyrig.engine.Shape
import com.example.storyrig.engine.Stage
import com.example.storyrig.engine.View
import com.example.storyrig.engine.Viewport
import com.example.storyrig.engine.drawAll
import com.example.storyrig.engine.MeshPart
import com.example.storyrig.engine.PseudoYaw
import com.example.storyrig.engine.TurnMove
import com.example.storyrig.engine.hex
import com.example.storyrig.engine.logInfo
import com.example.storyrig.engine.meshDrawSupported
import com.example.storyrig.engine.uptimeMillis
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * `kai`: first look at bitmap characters (SPIKE.md P5 groundwork, not the full P5 yet: no audio clock, karaoke
 * or confetti). [SceneConfig.count] actors dance to a 120 BPM beat, swap mouths every 80-120 ms and blink.
 *
 * [cast]: one list per kind of actor, holding the views it was cut in (one, unless `--es turn`).
 * Turning (`--es turn kai`), the pseudo-3D turn of reference/bobo.html: the actor has one rig per facing it was cut
 * in (three-quarter right, front, three-quarter left = that cut mirrored, back), all playing the same clip on the
 * same beat. Every [TURN_BEATS] it turns to the next yaw of [TURN_PLAN] with [TurnMove] (wind-up, head first, hops,
 * overshoot); each rig bends towards the current yaw with [PseudoYaw], the body is drawn by the rig nearest the
 * body's yaw and the head by the rig nearest the head's, so cuts change where the figure is narrowest (side on).
 */
class KaiScene(private val cfg: SceneConfig, private val perf: PerfStats, private val cast: List<List<KaiAssets>>) : Scene {
    /** First of the cast: what the complexity line reports. */
    private val assets = cast[0][0]
    /** `--es art mesh`: arms, legs, torso and pelvis as skinned meshes; anything else: rigid bitmap parts. */
    private val mesh = cfg.art == "mesh" && cast.all { v -> v.all { it.meshes.isNotEmpty() } } && meshDrawSupported()
    override val name = if (mesh) "kai mesh_dance (preview)" else "kai bitmap_dance (preview)"
    override val stats = RenderStats()
    override val layerCount = 0
    override var layers: Array<GraphicsLayer>? = null
    override var onSubtitle: ((String, String, String) -> Unit)? = null

    private val vp = Viewport(Stage())
    private val m = Matrix()
    private val rng = Rng(cfg.seed)
    /** Actors on stage. */
    private val n = cfg.count
    /** Rigs of actor i: actorFirst[i] until actorFirst[i + 1] (one per view). */
    private val actorFirst = IntArray(n + 1)
    private val kais: Array<KaiRig>
    private val background: Array<Shape>
    private val shadow: Array<Shape>
    private val scale: Float
    /** Per actor: [scale] times its real height (character.csv), so a grown-up stands taller than the kids. */
    private val kaiScale = FloatArray(n)
    /** Per rig: root pivot height above the ground line, in character px (from the cut data). */
    private val rootY: FloatArray
    /** Per rig: drawn facing left (the root's scaleX < 0). */
    private val mirrored: BooleanArray
    private val homeX = FloatArray(n)
    private val nextMouth = FloatArray(n)
    private val mouthIndex = IntArray(n)
    private val nextBlink = FloatArray(n)
    private val blinkEnd = FloatArray(n)

    /** Per rig: the yaw its cut is shown at (radians), when turning. */
    private val rigYaw: FloatArray
    /** Per actor, this frame: body and head yaw, the rigs drawing body and head, and whether a turn is under way. */
    private val bodyYaw = FloatArray(n)
    private val headYaw = FloatArray(n)
    private val bodyRig = IntArray(n)
    private val headRig = IntArray(n)
    private val inTurn = BooleanArray(n)
    /** Per actor, this frame: draw-time scaleX, scaleY and lift (stage px) from the turn's squash and hops. */
    private val squashX = FloatArray(n) { 1f }
    private val squashY = FloatArray(n) { 1f }
    private val hop = FloatArray(n)

    private var t0Nanos = -1L
    private var nowMs = 0f
    private var dtSec = 1f / 60f
    /** `--es motion 0` turns springs and auto layers off, to compare. */
    private val layers2 = cfg.flagOf("motion", true)
    /** `--es clip 0` goes back to the old sine-wave poses, to compare. */
    private val useClips = cfg.flagOf("clip", true)
    /** `--es feet 0` lets the feet slide with the pose, to compare with them pinned to the ground. */
    private val pinFeet = cfg.flagOf("feet", true)
    /** `--es views 0` plays the clips as written for three-quarter in front/back views too, to compare. */
    private val adaptViews = cfg.flagOf("views", true)
    /** `--es dance pump`: everyone dances that clip only (wave, pump, march, idle; stress for tools/joint_sheet.sh) instead of changing every 8 beats. */
    private val dance = DanceClips.ALL.indexOfFirst { it.name == cfg.valueOf("dance") }
    private val turning = cfg.turn.isNotEmpty()
    private val feetNear: Array<FootLock>
    private val feetFar: Array<FootLock>

    init {
        val start = uptimeMillis()
        if (cfg.art == "mesh" && !mesh) logInfo("art=mesh unavailable (drawVertices needs API 29+), drawing bitmap parts")
        // Turning: one rig per facing the actor has the cut for (only a three-quarter cut: right and mirrored left).
        val facings = Array(n) { i ->
            if (!turning) intArrayOf(-1) else FACINGS.indices.filter { f -> views(i).any { it.view == FACINGS[f] } }
                .ifEmpty { listOf(-1) }.toIntArray()
        }
        for (i in 0 until n) actorFirst[i + 1] = actorFirst[i] + facings[i].size
        val rigs = actorFirst[n]
        val rigActor = IntArray(rigs)
        val rigFacing = IntArray(rigs)
        for (i in 0 until n) for (r in actorFirst[i] until actorFirst[i + 1]) { rigActor[r] = i; rigFacing[r] = facings[i][r - actorFirst[i]] }
        rigYaw = FloatArray(rigs) { val f = rigFacing[it]; if (f < 0) 0f else FACING_YAW[f] * DEG }
        mirrored = BooleanArray(rigs) { val f = rigFacing[it]; f >= 0 && FACING_MIRROR[f] }
        kais = Array(rigs) {
            val i = rigActor[it]
            val f = rigFacing[it]
            val a = if (f < 0) views(i)[0] else views(i).first { v -> v.view == FACINGS[f] }
            KaiRig(
                a, cfg.antiAlias, phase = i * .7f, useMeshes = mesh, adaptView = adaptViews,
                facingYaw = if (f < 0) null else rigYaw[it], facingMirror = mirrored[it],
            )
        }
        rootY = FloatArray(rigs) { kais[it].rootToSole + SOLE_LIFT }
        // Knees bend across the picture only as far as the cut is side on (|sin yaw|); from the front, at the viewer.
        fun side(k: KaiRig) = if (adaptViews) kotlin.math.abs(kotlin.math.sin(k.yawDeg * DEG)) else 1f
        feetNear = Array(rigs) { val k = kais[it]; FootLock(k.sk, k.hipN, k.kneeN, k.ankleN, bend = 1, side = side(k)) }
        feetFar = Array(rigs) { val k = kais[it]; FootLock(k.sk, k.hipF, k.kneeF, k.ankleF, bend = 1, side = side(k)) }
        for (i in 0 until n) { bodyRig[i] = actorFirst[i]; headRig[i] = actorFirst[i] }

        val art = ArtBuilder(PathCache(), cfg.antiAlias)
        val l = -Stage.BLEED_W / 2f + Stage.SAFE_W / 2f
        val t = -Stage.BLEED_H / 2f + Stage.SAFE_H / 2f
        val sky = LinearGradientShader(Offset(0f, t), Offset(0f, 760f), listOf(hex(0x8fd3ff), hex(0xcfefff)))
        art.rect(l, t, Stage.BLEED_W, 760f - t, hex(0xcfefff), shader = sky)
        art.ellipse(300f, 820f, 700f, 220f, hex(0x7fd18b))
        art.ellipse(1500f, 840f, 820f, 240f, hex(0x6cc47a))
        art.rect(l, 780f, Stage.BLEED_W, Stage.BLEED_H, hex(0x3fbf6b))
        art.circle(1650f, 170f, 90f, hex(0xffc83d))
        background = art.build()
        art.ellipse(0f, 0f, 120f, 18f, hex(0x2a8a4a), alpha = .35f)
        shadow = art.build()

        // Each character gets a share of the width in proportion to its height (arms flung out ~ARM_SPAN), so a
        // grown-up's arms don't reach into the next child. Kids stay ~62% of the safe height at most; everyone
        // shrinks only if the tallest would leave the top ~10% of the safe area.
        val h = FloatArray(n) { views(it)[0].heightScale }
        val widthUnits = h.sum()
        scale = minOf(
            .62f * Stage.SAFE_H / KaiRig.HEIGHT,
            .95f * Stage.SAFE_W / (widthUnits * ARM_SPAN),
            .9f * Stage.SAFE_H / (KaiRig.HEIGHT * h.max()),
        )
        var x = 0f
        for (i in 0 until n) {
            kaiScale[i] = scale * h[i]
            val slot = Stage.SAFE_W * h[i] / widthUnits
            homeX[i] = x + slot / 2f
            x += slot
            nextMouth[i] = rng.range(0f, 120f)
            nextBlink[i] = rng.range(800f, 3000f)
        }
        perf.loadMs = cast.sumOf { v -> v.sumOf { it.loadMs.toDouble() } }.toFloat() + (uptimeMillis() - start)
    }

    private fun views(actor: Int) = cast[actor % cast.size]

    /** Actors on stage, and the rig drawing actor [i]'s body this frame (for checks: desktop MotionCheck). */
    val actorCount get() = n
    fun bodyRigOf(i: Int): KaiRig = kais[bodyRig[i]]

    override fun complexity(): String {
        val img = assets.atlas.image
        val r = kais[0].rig
        val ma = assets.meshAtlas
        val meshInfo = if (!mesh || ma == null) "" else
            "meshes/kai=${r.items.count { it is MeshPart }} mesh verts/kai=${r.meshVertCount} tris/kai=${r.meshTriangleCount} " +
                "(${assets.meshes.joinToString(" ") { "${it.group.name}=${it.data.vertexCount}v/${it.data.triangleCount}t" }}) " +
                "mesh atlas=${ma.image.width}x${ma.image.height} (${(ma.bytes / 1024f / 1024f).toString().take(4)} MB) "
        val turnInfo = if (!turning) "" else "turning rigs=${kais.size} (${cast.joinToString(" ") { v -> v.joinToString("+") { it.name } }}) "
        return "kais=$n art=${if (mesh) "mesh" else "bitmap"} items/kai=${r.items.size} $meshInfo$turnInfo" +
            "atlas=${img.width}x${img.height} " +
            "(${assets.atlas.regionCount} regions, ${(assets.atlas.bytes / 1024f / 1024f).toString().take(4)} MB) " +
            "assets load=${assets.loadMs.toInt()} ms scale=${scale.toString().take(4)}"
    }

    override fun update(frameNanos: Long) {
        stats.reset()
        if (t0Nanos < 0) t0Nanos = frameNanos
        val now = (frameNanos - t0Nanos) / 1_000_000f
        dtSec = ((now - nowMs) / 1000f).coerceIn(0f, .05f)
        nowMs = now
        val beat = nowMs / BEAT_MS
        for (i in 0 until n) {
            // A small offset per actor so they are not robot-synchronised.
            val b = beat - i * .04f
            turn(i, b)
            face(i)
            for (r in actorFirst[i] until actorFirst[i + 1]) pose(r, i, b, r == bodyRig[i] || r == headRig[i])
        }
    }

    /**
     * Actor [i] at beat [b]: body and head yaw from [TURN_PLAN] and [TurnMove], the rigs that draw body and head
     * (nearest yaw), and the squash and hops of the move.
     */
    private fun turn(i: Int, b: Float) {
        squashX[i] = 1f; squashY[i] = 1f; hop[i] = 0f; inTurn[i] = false
        val first = actorFirst[i]
        if (actorFirst[i + 1] - first < 2) return
        val steps = TURN_PLAN.size - 1
        val t = b - TURN_START
        var yaw = TURN_PLAN[0] * DEG
        var lead = 0f
        if (t >= 0f) {
            val local = t % (steps * TURN_BEATS)
            val s = (local / TURN_BEATS).toInt()
            val from = TURN_PLAN[s] * DEG
            val delta = TURN_PLAN[s + 1] * DEG - from
            val u = (local - s * TURN_BEATS) / TurnMove.beats(delta)
            yaw = from + TurnMove.yaw(u, delta)
            if (u < 1f) {
                inTurn[i] = true
                lead = TurnMove.lead(u, delta)
                hop[i] = TurnMove.hop(u, delta) * HOP_SCALE * kaiScale[i]
                val sq = TurnMove.squash(u, delta)
                squashX[i] = 1f + sq; squashY[i] = 1f - sq
            }
        }
        bodyYaw[i] = yaw
        headYaw[i] = yaw + lead
        bodyRig[i] = nearest(i, yaw)
        headRig[i] = nearest(i, yaw + lead)
        // Side on, the figure narrows a little more, so the change of cut happens on a thin shape.
        val side = 1f - abs(cos(yaw)) / SIDE_BAND
        if (side > 0f) squashX[i] *= 1f - SIDE_SQUEEZE * side
    }

    /** Actor [i]'s rig whose cut is nearest [yaw]. */
    private fun nearest(i: Int, yaw: Float): Int {
        var best = actorFirst[i]
        var bestD = Float.MAX_VALUE
        for (r in actorFirst[i] until actorFirst[i + 1]) {
            val d = abs(wrap(yaw - rigYaw[r]))
            if (d < bestD) { bestD = d; best = r }
        }
        return best
    }

    /** Angle in -PI..PI. */
    private fun wrap(a: Float): Float {
        val tau = 2f * PI.toFloat()
        var x = a % tau
        if (x > PI.toFloat()) x -= tau
        if (x < -PI.toFloat()) x += tau
        return x
    }

    private fun pose(r: Int, i: Int, beat: Float, shown: Boolean) {
        val k = kais[r]
        val y = GROUND_Y - rootY[r] * kaiScale[i]
        if (useClips) {
            // Everyone changes dance every 8 beats, on the beat, with a half-beat crossfade.
            val bar = (beat / SWITCH_BEATS).toInt().coerceAtLeast(0)
            val clip = if (dance >= 0) dance else (i + bar) % DanceClips.DANCES.size
            k.player.play(clip, bar * SWITCH_BEATS, fadeBeats = .5f)
            k.reset(homeX[i], y, kaiScale[i], mirrored[r])
            k.yaw?.apply(bodyYaw[i], headYaw[i])
            feetNear[r].captureGround(); feetFar[r].captureGround()
            k.player.apply(beat)
        } else {
            sinePose(k, i, beat, y, mirrored[r])
        }
        if (layers2) {
            k.motion.apply(dtSec)
            k.auto.apply(dtSec)
        }
        if (useClips) {
            // Mid-turn the feet step round with the body instead of staying planted.
            feetNear[r].apply(dtSec, pinFeet && !inTurn[i])
            feetFar[r].apply(dtSec, pinFeet && !inTurn[i])
        }
        k.slideShoulders()
        // Hidden views keep their springs and feet running (above) but skip skinning: nobody sees them.
        if (shown) k.rig.pose(stats)
    }

    private fun sinePose(k: KaiRig, i: Int, beat: Float, y: Float, mirror: Boolean) {
        val ph = (beat + i * .15f) * PI.toFloat()
        val s = sin(ph)
        val bounce = abs(s)
        k.reset(homeX[i], y - 22f * bounce, kaiScale[i], mirror)
        k.rot(k.torso, 4f * sin(ph * .5f))
        k.rot(k.head, 6f * sin(ph + .6f))
        when (i % 3) {
            0 -> { // wave
                k.arm(true, 105f + 12f * s); k.rot(k.elbowN, 30f + 25f * s); k.rot(k.wristN, -15f * s)
                k.arm(false, -25f); k.rot(k.elbowF, -40f)
            }
            1 -> { // arms out and in
                k.arm(true, 60f + 35f * s); k.rot(k.elbowN, 30f * bounce)
                k.arm(false, -60f - 35f * s); k.rot(k.elbowF, -30f * bounce)
            }
            else -> { // swing
                k.arm(true, 30f * s); k.rot(k.elbowN, 20f + 20f * s)
                k.arm(false, 30f * s); k.rot(k.elbowF, -20f + 20f * s)
            }
        }
        k.rot(k.hipN, -12f * bounce); k.rot(k.kneeN, 24f * bounce); k.rot(k.ankleN, -12f * bounce)
        k.rot(k.hipF, 8f * s); k.rot(k.kneeF, 12f * bounce)
    }

    /** Mouth and eyes of actor [i], the same on all its views so a swap doesn't change the face. */
    private fun face(i: Int) {
        if (nowMs >= nextMouth[i]) {
            var next = (rng.next() * KaiRig.MOUTHS.size).toInt().coerceAtMost(KaiRig.MOUTHS.size - 1)
            if (next == mouthIndex[i]) next = (next + 1) % KaiRig.MOUTHS.size
            mouthIndex[i] = next
            nextMouth[i] = nowMs + rng.range(80f, 120f)
        }
        if (nowMs >= nextBlink[i]) {
            blinkEnd[i] = nowMs + 130f
            nextBlink[i] = nowMs + rng.range(2000f, 4000f)
        }
        val eye = if (nowMs < blinkEnd[i]) KaiRig.EYE_HAPPY else KaiRig.EYE_OPEN
        for (r in actorFirst[i] until actorFirst[i + 1]) {
            val k = kais[r]
            k.mouth?.sprite = k.mouths[mouthIndex[i]]
            k.eyeNear?.sprite = k.eyesNear[eye]
            k.eyeFar?.sprite = k.eyesFar[eye]
        }
    }

    override fun draw(scope: DrawScope) {
        val canvas = scope.drawContext.canvas
        vp.fit(scope.size.width, scope.size.height)
        canvas.save()
        canvas.translate(vp.ox, vp.oy)
        canvas.scale(vp.scale, vp.scale)
        canvas.clipRect(vp.visLeft, vp.visTop, vp.visRight, vp.visBottom)
        background.drawAll(canvas, 1f, stats)
        for (i in 0 until n) {
            val sx = squashX[i]
            canvas.save()
            canvas.translate(homeX[i], GROUND_Y)
            // The shadow narrows less than the body and stays on the ground during the hop.
            canvas.save()
            canvas.scale(kaiScale[i] * 1.4f * (.5f + .5f * sx), kaiScale[i] * 1.4f)
            shadow.drawAll(canvas, 1f, stats)
            canvas.restore()
            if (sx != 1f || hop[i] != 0f) {
                canvas.translate(0f, -hop[i])
                canvas.scale(sx, squashY[i])
            }
            canvas.translate(-homeX[i], -GROUND_Y)
            val body = kais[bodyRig[i]]
            val head = kais[headRig[i]]
            if (head === body || body.headFrom < 0 || head.headFrom < 0) {
                body.rig.draw(canvas, 1f, m, stats)
            } else {
                // Head leads the turn: the next cut's head on this cut's body, in the body's draw order.
                body.rig.draw(canvas, 1f, m, stats, 0, body.headFrom)
                head.rig.draw(canvas, 1f, m, stats, head.headFrom, head.headTo)
                body.rig.draw(canvas, 1f, m, stats, body.headTo, body.rig.items.size)
            }
            canvas.restore()
        }
        canvas.restore()
        stats.commit()
    }

    override fun pointerDown(px: Float, py: Float, uptime: Long) {}
    override fun pointerMove(px: Float, py: Float) {}
    override fun pointerUp(px: Float, py: Float) {}

    companion object {
        /** Fit n Kais across the safe area (arms spread ~520 ref px), never taller than ~62% of it. */
        fun scaleFor(n: Int) = min(.62f * Stage.SAFE_H / KaiRig.HEIGHT, .95f * (Stage.SAFE_W / n) / ARM_SPAN)

        /** Arm span (ref px) to leave room for: the dances fling the arms out to the sides. */
        private const val ARM_SPAN = 640f

        private const val BEAT_MS = 500f
        private const val SWITCH_BEATS = 8f
        private const val GROUND_Y = 960f
        /** Soles sit this far (character px) above the shadow's centre line, so the shadow shows under them. */
        private const val SOLE_LIFT = 11f

        /** Facings a turning actor can be cut in: view, yaw it stands for (degrees, > 0 = towards screen right), mirrored. */
        private val FACINGS = arrayOf(View.Q3, View.FRONT, View.Q3, View.BACK)
        private val FACING_YAW = floatArrayOf(30f, 0f, -30f, 180f)
        private val FACING_MIRROR = booleanArrayOf(false, false, true, false)
        /**
         * Turning: yaw (degrees) after each turn, one turn every [TURN_BEATS] from beat [TURN_START]: to the front,
         * three-quarter left, round to the back, on to three-quarter right, then a full spin. Ends where it starts
         * (30 = -330 = 30 + 360 on the circle).
         */
        private val TURN_PLAN = floatArrayOf(30f, 0f, -30f, -180f, -330f, 30f)
        private const val TURN_BEATS = 4f
        private const val TURN_START = 2f
        /** Bobo's hop heights are in his ~300 px figure; Kai is ~850 character px. */
        private const val HOP_SCALE = 2.5f
        /** Extra narrowing side on: |cos(yaw)| below SIDE_BAND, down to 1 - SIDE_SQUEEZE exactly side on. */
        private const val SIDE_BAND = .3f
        private const val SIDE_SQUEEZE = .15f
        private const val DEG = (PI / 180.0).toFloat()
    }
}
