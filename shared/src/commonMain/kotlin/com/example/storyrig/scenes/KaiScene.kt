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
import com.example.storyrig.engine.Viewport
import com.example.storyrig.engine.drawAll
import com.example.storyrig.engine.MeshPart
import com.example.storyrig.engine.hex
import com.example.storyrig.engine.logInfo
import com.example.storyrig.engine.meshDrawSupported
import com.example.storyrig.engine.uptimeMillis
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * `kai`: first look at bitmap characters (SPIKE.md P5 groundwork, not the full P5 yet: no audio clock, karaoke
 * or confetti). [SceneConfig.count] Kais dance to a 120 BPM beat, swap mouths every 80-120 ms and blink.
 */
class KaiScene(private val cfg: SceneConfig, private val perf: PerfStats, private val cast: List<KaiAssets>) : Scene {
    /** First of the cast: what the complexity line reports. */
    private val assets = cast[0]
    /** `--es art mesh`: arms, legs, torso and pelvis as skinned meshes; anything else: rigid bitmap parts. */
    private val mesh = cfg.art == "mesh" && cast.all { it.meshes.isNotEmpty() } && meshDrawSupported()
    override val name = if (mesh) "kai mesh_dance (preview)" else "kai bitmap_dance (preview)"
    override val stats = RenderStats()
    override val layerCount = 0
    override var layers: Array<GraphicsLayer>? = null
    override var onSubtitle: ((String, String, String) -> Unit)? = null

    private val vp = Viewport(Stage())
    private val m = Matrix()
    private val rng = Rng(cfg.seed)
    private val n = cfg.count
    private val kais: Array<KaiRig>
    private val background: Array<Shape>
    private val shadow: Array<Shape>
    private val scale: Float
    /** Per character: [scale] times its real height (character.csv), so a grown-up stands taller than the kids. */
    private val kaiScale = FloatArray(cfg.count.coerceIn(1, 12))
    /** Root pivot height above the ground line per character, in character px (from the cut data). */
    private val rootY = FloatArray(cfg.count.coerceIn(1, 12)) { cast[it % cast.size].rootToSole + SOLE_LIFT }
    private val homeX = FloatArray(n)
    private val nextMouth = FloatArray(n)
    private val nextBlink = FloatArray(n)
    private val blinkEnd = FloatArray(n)

    private var t0Nanos = -1L
    private var nowMs = 0f
    private var dtSec = 1f / 60f
    /** `--es motion 0` turns springs and auto layers off, to compare. */
    private val layers2 = cfg.flagOf("motion", true)
    /** `--es clip 0` goes back to the old sine-wave poses, to compare. */
    private val useClips = cfg.flagOf("clip", true)
    /** `--es feet 0` lets the feet slide with the pose, to compare with them pinned to the ground. */
    private val pinFeet = cfg.flagOf("feet", true)
    private val feetNear: Array<FootLock>
    private val feetFar: Array<FootLock>

    init {
        val start = uptimeMillis()
        if (cfg.art == "mesh" && !mesh) logInfo("art=mesh unavailable (drawVertices needs API 29+), drawing bitmap parts")
        kais = Array(n) { KaiRig(cast[it % cast.size], cfg.antiAlias, phase = it * .7f, useMeshes = mesh) }
        feetNear = Array(n) { FootLock(kais[it].sk, kais[it].hipN, kais[it].kneeN, kais[it].ankleN) }
        feetFar = Array(n) { FootLock(kais[it].sk, kais[it].hipF, kais[it].kneeF, kais[it].ankleF) }
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
        val h = FloatArray(n) { cast[it % cast.size].heightScale }
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
        perf.loadMs = cast.sumOf { it.loadMs.toDouble() }.toFloat() + (uptimeMillis() - start)
    }

    override fun complexity(): String {
        val img = assets.atlas.image
        val r = kais[0].rig
        val ma = assets.meshAtlas
        val meshInfo = if (!mesh || ma == null) "" else
            "meshes/kai=${r.items.count { it is MeshPart }} mesh verts/kai=${r.meshVertCount} tris/kai=${r.meshTriangleCount} " +
                "(${assets.meshes.joinToString(" ") { "${it.group.name}=${it.data.vertexCount}v/${it.data.triangleCount}t" }}) " +
                "mesh atlas=${ma.image.width}x${ma.image.height} (${(ma.bytes / 1024f / 1024f).toString().take(4)} MB) "
        return "kais=$n art=${if (mesh) "mesh" else "bitmap"} items/kai=${r.items.size} $meshInfo" +
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
        for (i in 0 until n) pose(i, beat)
    }

    private fun pose(i: Int, beat: Float) {
        val k = kais[i]
        if (useClips) {
            // Everyone changes dance every 8 beats, on the beat, with a half-beat crossfade; a small offset per
            // Kai so they are not robot-synchronised.
            val b = beat - i * .04f
            val bar = (b / SWITCH_BEATS).toInt().coerceAtLeast(0)
            k.player.play((i + bar) % DanceClips.ALL.size, bar * SWITCH_BEATS, fadeBeats = .5f)
            k.reset(homeX[i], GROUND_Y - rootY[i] * kaiScale[i], kaiScale[i])
            feetNear[i].captureGround(); feetFar[i].captureGround()
            k.player.apply(b)
        } else {
            sinePose(k, i, beat)
        }
        face(k, i)
        if (layers2) {
            k.motion.apply(dtSec)
            k.auto.apply(dtSec)
        }
        if (useClips) {
            feetNear[i].apply(dtSec, pinFeet)
            feetFar[i].apply(dtSec, pinFeet)
        }
        k.rig.pose(stats)
    }

    private fun sinePose(k: KaiRig, i: Int, beat: Float) {
        val ph = (beat + i * .15f) * PI.toFloat()
        val s = sin(ph)
        val bounce = abs(s)
        k.reset(homeX[i], GROUND_Y - rootY[i] * kaiScale[i] - 22f * bounce, kaiScale[i])
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

    private fun face(k: KaiRig, i: Int) {
        if (nowMs >= nextMouth[i]) {
            var next = (rng.next() * KaiRig.MOUTHS.size).toInt().coerceAtMost(KaiRig.MOUTHS.size - 1)
            if (k.mouth.sprite === k.mouths[next]) next = (next + 1) % KaiRig.MOUTHS.size
            k.mouth.sprite = k.mouths[next]
            nextMouth[i] = nowMs + rng.range(80f, 120f)
        }
        if (nowMs >= nextBlink[i]) {
            blinkEnd[i] = nowMs + 130f
            nextBlink[i] = nowMs + rng.range(2000f, 4000f)
        }
        val eye = if (nowMs < blinkEnd[i]) KaiRig.EYE_HAPPY else KaiRig.EYE_OPEN
        k.eyeNear.sprite = k.eyesNear[eye]
        k.eyeFar.sprite = k.eyesFar[eye]
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
            canvas.save()
            canvas.translate(homeX[i], GROUND_Y)
            canvas.scale(kaiScale[i] * 1.4f, kaiScale[i] * 1.4f)
            shadow.drawAll(canvas, 1f, stats)
            canvas.restore()
            kais[i].rig.draw(canvas, 1f, m, stats)
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
    }
}
