package com.example.storyrig.scenes

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
import com.example.storyrig.engine.Anim
import com.example.storyrig.engine.ArtBuilder
import com.example.storyrig.engine.DEG
import com.example.storyrig.engine.Ease
import com.example.storyrig.engine.Particles
import com.example.storyrig.engine.PathCache
import com.example.storyrig.engine.PerfStats
import com.example.storyrig.engine.RenderStats
import com.example.storyrig.engine.Rig
import com.example.storyrig.engine.Ripples
import com.example.storyrig.engine.Rng
import com.example.storyrig.engine.Scene
import com.example.storyrig.engine.SceneConfig
import com.example.storyrig.engine.Seq
import com.example.storyrig.engine.SeqHost
import com.example.storyrig.engine.SeqPlayer
import com.example.storyrig.engine.Shape
import com.example.storyrig.engine.Springs
import com.example.storyrig.engine.Stage
import com.example.storyrig.engine.Viewport
import com.example.storyrig.engine.clamp
import com.example.storyrig.engine.drawAll
import com.example.storyrig.engine.hex
import com.example.storyrig.engine.ik2
import com.example.storyrig.engine.lerp
import com.example.storyrig.engine.logInfo
import com.example.storyrig.engine.traceBegin
import com.example.storyrig.engine.traceBeginAsync
import com.example.storyrig.engine.traceEnd
import com.example.storyrig.engine.traceEndAsync
import com.example.storyrig.engine.uptimeMillis
import com.example.storyrig.scenes.LakeArt.B_FACE_FRONT
import com.example.storyrig.scenes.LakeArt.B_FARM_F
import com.example.storyrig.scenes.LakeArt.B_FARM_N
import com.example.storyrig.scenes.LakeArt.B_HAND_F
import com.example.storyrig.scenes.LakeArt.B_HAND_N
import com.example.storyrig.scenes.LakeArt.B_HAT
import com.example.storyrig.scenes.LakeArt.B_HEAD
import com.example.storyrig.scenes.LakeArt.B_HEAD_FRONT
import com.example.storyrig.scenes.LakeArt.B_ROOT
import com.example.storyrig.scenes.LakeArt.B_SHADOW
import com.example.storyrig.scenes.LakeArt.B_SHIN_F
import com.example.storyrig.scenes.LakeArt.B_SHIN_N
import com.example.storyrig.scenes.LakeArt.B_SHOE_F
import com.example.storyrig.scenes.LakeArt.B_SHOE_N
import com.example.storyrig.scenes.LakeArt.B_THIGH_F
import com.example.storyrig.scenes.LakeArt.B_THIGH_N
import com.example.storyrig.scenes.LakeArt.B_TORSO
import com.example.storyrig.scenes.LakeArt.B_TUFT
import com.example.storyrig.scenes.LakeArt.B_UARM_F
import com.example.storyrig.scenes.LakeArt.B_UARM_N
import com.example.storyrig.scenes.LakeArt.CLOUDS
import com.example.storyrig.scenes.LakeArt.DAISIES
import com.example.storyrig.scenes.LakeArt.D_FEET
import com.example.storyrig.scenes.LakeArt.D_INNER
import com.example.storyrig.scenes.LakeArt.D_WING
import com.example.storyrig.scenes.LakeArt.F_FAR_FOOT
import com.example.storyrig.scenes.LakeArt.F_FAR_S
import com.example.storyrig.scenes.LakeArt.F_FAR_T
import com.example.storyrig.scenes.LakeArt.F_INNER
import com.example.storyrig.scenes.LakeArt.F_NEAR_FOOT
import com.example.storyrig.scenes.LakeArt.F_NEAR_S
import com.example.storyrig.scenes.LakeArt.F_NEAR_T
import com.example.storyrig.scenes.LakeArt.F_SAC
import com.example.storyrig.scenes.LakeArt.PADS
import com.example.storyrig.scenes.LakeArt.REEDS
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/**
 * P3 `explore_lake`, ported from reference/lake.html.
 * update() runs once per frame (state, tweens, bones, soft paths); draw() only issues canvas calls.
 */
class LakeScene(private val cfg: SceneConfig, private val perf: PerfStats) : Scene, SeqHost {
    override val name = "p3 explore_lake"
    override val stats = RenderStats()
    override val layerCount = 7
    override var layers: Array<GraphicsLayer>? = null
    override var onSubtitle: ((String, String, String) -> Unit)? = null

    private val aa = cfg.antiAlias
    private val alphaWater = cfg.waterMode != "layer"
    private val rng = Rng(cfg.seed)
    /** Demo units (960x540 safe area) = half of the 1920x1080 frame. */
    private val vp = Viewport(Stage(unitsPerFrame = .5f))
    private var density = 1f
    private val m = Matrix()
    private val pathCache = PathCache()

    // ---------------------------------------------------------------- art (built in init, timed)
    private val sky: Array<Shape>
    private val sun: Array<Shape>
    private val cloud: Array<Shape>
    private val mountains: Array<Shape>
    private val hills: Array<Shape>
    private val lakeBody: Array<Shape>
    private val shoreline: Array<Shape>
    private val groundStatic: Array<Shape>
    private val foreground: Array<Shape>
    private val tree: Array<Shape>
    private val daisies: Array<Array<Shape>>
    private val pads: Array<Array<Shape>>
    private val hatShapes: Array<Shape>
    private val reeds: Rig
    private val duck: Rig
    private val frog: LakeArt.FrogRig
    private val boy: LakeArt.BoyRig
    private val lakeClip: Path
    private val staticShapeCount: Int
    val loadMs: Float

    private val birdPath = arrayOf(Path(), Path())
    private val birdPaint = arrayOf(strokePaint(2.5f), strokePaint(2.2f))
    private val shimmerPaint = Paint().apply { isAntiAlias = aa; color = androidx.compose.ui.graphics.Color.White }
    private val layerPaint = Paint()
    private val ripples = Ripples(48, aa)
    private val drops = Particles(96, hex(0xe8f7ff), aa)

    // shimmer x, y, width, phase
    private val shim = FloatArray(12 * 4)
    private val reedSway = FloatArray(REEDS.size)
    private val daisySway = FloatArray(DAISIES.size)

    // ---------------------------------------------------------------- animated values
    private val anim = Anim(V_COUNT)
    private val v = anim.v
    private val springs = Springs(SP_COUNT)
    private val boyPlayer = SeqPlayer(anim)
    private val duckPlayer = SeqPlayer(anim)
    private val frogPlayer = SeqPlayer(anim)
    private val seqGo: Seq; private val seqSit: Seq; private val seqStand: Seq; private val seqWave: Seq
    private val seqPoint: Seq; private val seqJump: Seq; private val seqWalk: Seq
    private val seqDabble: Seq; private val seqDuckTap: Seq; private val seqRibbit: Seq

    private var t0Nanos = -1L
    private var nowMs = 0f
    private var lastMs = 0f
    private val ikOut = FloatArray(4)

    // boy
    private var boyTarget = Float.NaN
    private var boyDir = 1f
    private var lastStepIdx = 0
    private var happy = false
    private var talking = false
    private var talkUntil = 0f
    private var blink = false
    private var nextBlink = 3400f
    private var prevRootY = GROUND
    private var prevRootVy = 0f

    // hat
    private var hatOnHead = false
    private var hatDragging = false
    private var hatX = HAT_HOME_X
    private var hatY = HAT_HOME_Y
    private var dragDx = 0f
    private var dragDy = 0f

    // duck
    private var duckDir = -1f
    private var duckBusy = false
    private var nextDab = 7000f
    private var nextWake = 0f

    // frog
    private var frogState = FS_START
    private var frogStateUntil = 2600f
    private var frogPad = 0
    private var frogPoked = false
    private var frogNextRib = 3000f
    private var frogKp = 0f
    private var frogV = 0f
    private var frogTx = 0f
    private var frogTy = 0f
    private var leapX0 = 0f; private var leapY0 = 0f; private var leapX1 = 0f; private var leapY1 = 0f
    private var leapT0 = 0f; private var leapDur = 1f; private var leapH = 0f; private var leapToSwim = false

    // water object order (0..2 pads, 3 duck, 4 frog)
    private val waterOrder = intArrayOf(0, 1, 2, 3, 4)
    private val waterY = FloatArray(5)

    // camera
    private var camX = 0f
    private var zoom = 1f

    // autoplay
    private var nextAuto = 0f
    private var autoIdx = 0

    // input + latency
    private var pendingTapUptime = 0L
    private var tapSeenByUpdate = false
    private var tapCookie = 0
    private var tapsTotal = 0

    // cached static layers
    private var recordedW = -1f
    private var recordedH = -1f

    // reusable bounds for saveLayer
    private val belowRect = Rect(-140f, 0f, 140f, 90f)
    private val reflRect = Rect(355f, 300f, 1145f, 655f)
    private val cloudRect = Rect(-95f, -60f, 70f, 30f)

    init {
        traceBegin("P3_load")
        val start = uptimeMillis()
        val b = ArtBuilder(pathCache, aa)
        sky = LakeArt.sky(b)
        sun = LakeArt.sun(b)
        cloud = LakeArt.cloud(b)
        mountains = LakeArt.mountains(b)
        hills = LakeArt.hills(b)
        lakeBody = LakeArt.lakeBody(b)
        shoreline = LakeArt.shoreline(b)
        groundStatic = LakeArt.groundStatic(b)
        foreground = LakeArt.foreground(b)
        tree = LakeArt.tree(b)
        daisies = Array(DAISIES.size) { LakeArt.daisy(b, DAISIES[it][2] > 0f) }
        pads = Array(PADS.size) { LakeArt.pad(b, PADS[it][2], PADS[it][3], PADS[it][4] > 0f) }
        hatShapes = LakeArt.hat(b)
        reeds = LakeArt.reedRig(b, aa)
        duck = LakeArt.duckRig(b)
        frog = LakeArt.frogRig(b, aa)
        boy = LakeArt.boyRig(b, aa, pathCache, hatShapes)
        lakeClip = LakeArt.lakeClip(pathCache)
        staticShapeCount = sky.size + mountains.size * 2 + hills.size + lakeBody.size + groundStatic.size + foreground.size

        for (i in 0 until 12) {
            shim[i * 4 + 1] = 330f + rng.next() * 190f
            shim[i * 4 + 2] = 14f + floor(rng.next() * 30f)
            shim[i * 4] = 380f + rng.next() * 580f
            shim[i * 4 + 3] = rng.next() * 6f
        }
        for (i in REEDS.indices) {
            springs.k[SP_REED + i] = 60f; springs.damp[SP_REED + i] = 3.5f
            reedSway[i] = rng.range(2.6f, 3.6f)
        }
        for (i in DAISIES.indices) {
            springs.k[SP_DAISY + i] = 90f; springs.damp[SP_DAISY + i] = 5f
            daisySway[i] = rng.range(2.4f, 4f)
        }
        springs.k[SP_TREE] = 30f; springs.damp[SP_TREE] = 2.5f
        springs.k[SP_TUFT] = 260f; springs.damp[SP_TUFT] = 9f
        springs.k[SP_HAT_ROT] = 180f; springs.damp[SP_HAT_ROT] = 7f
        springs.k[SP_HAT_Y] = 320f; springs.damp[SP_HAT_Y] = 10f

        v[B_X] = if (cfg.start == "seat") SEAT else -90f; v[B_SX] = 1f
        v[D_X] = 780f; v[D_Y] = 414f; v[D_SX] = -1f
        v[F_SX] = 1f
        v[SUN_S] = 1f
        for (i in 0 until 3) v[CLOUD_S + i] = 1f
        nextDab = 7000f

        seqGo = Seq.build {
            event(E_SAY_GO); set(B_GOAL, SEAT); walkOps()
            tween(B_HD, -5f, 300f); wait(300f); event(E_SAY_WOW); event(E_HAPPY_ON); wait(1200f); event(E_HAPPY_OFF)
            tween(B_HD, 0f, 400f); wait(400f)
        }
        seqSit = Seq.build {
            set(B_GOAL, SEAT); walkOps(); event(E_TURN_RIGHT)
            tween(B_S, .55f, 330f, Ease.IN); tween(B_LEAN, 16f, 330f, Ease.IN); wait(330f)
            tween(B_S, 1f, 260f, Ease.OUT); tween(B_LEAN, -4f, 260f, Ease.OUT); tween(B_SQ, .06f, 260f, Ease.OUT); wait(260f)
            tween(B_SQ, 0f, 200f); wait(200f); event(E_SAY_SIT)
        }
        seqStand = Seq.build { standOps(); event(E_SAY_STAND) }
        seqWave = Seq.build {
            event(E_SAY_HELLO); tween(B_WAVE, 1f, 220f, Ease.OUT); tween(B_POINT, 0f, 220f, Ease.OUT); wait(1620f)
            tween(B_WAVE, 0f, 260f); wait(260f)
        }
        seqPoint = Seq.build {
            event(E_TURN_RIGHT); tween(B_HD, -7f, 300f); tween(B_POINT, 1f, 260f, Ease.OUT); tween(B_WAVE, 0f, 260f, Ease.OUT)
            event(E_SAY_LOOK); wait(1500f); event(E_DUCK_TAP); event(E_HAPPY_ON); wait(1100f); event(E_HAPPY_OFF)
            tween(B_POINT, 0f, 320f); tween(B_HD, 0f, 320f); wait(320f)
        }
        seqJump = Seq.build {
            skipIf(C_STANDING, 8); standBody()
            tween(B_SQ, .12f, 110f, Ease.OUT); wait(110f)
            tween(B_JUMP, -64f, 280f, Ease.OUT); tween(B_SQ, -.08f, 280f, Ease.OUT); event(E_SAY_JUMP); wait(280f)
            tween(B_JUMP, 0f, 250f, Ease.IN); tween(B_SQ, 0f, 250f); wait(250f)
            tween(B_SQ, .1f, 80f, Ease.OUT); wait(80f); tween(B_SQ, 0f, 160f); wait(160f)
        }
        seqWalk = Seq.build { walkOps() }
        seqDabble = Seq.build {
            event(E_DUCK_BUSY); tween(D_TILT, 78f, 480f, Ease.IN); wait(480f); event(E_RIPPLE_DUCK_FRONT); wait(1000f)
            tween(D_TILT, 0f, 420f, Ease.OUT); wait(420f); event(E_RIPPLE_DUCK); event(E_DUCK_FREE)
        }
        seqDuckTap = Seq.build {
            event(E_DUCK_BUSY); event(E_RIPPLE_DUCK)
            repeat(3) {
                tween(D_FLAP, 1f, 130f, Ease.OUT); tween(D_HOP, -12f, 130f, Ease.OUT); wait(130f)
                tween(D_FLAP, 0f, 150f, Ease.IN); tween(D_HOP, 0f, 150f, Ease.IN); wait(150f)
            }
            event(E_RIPPLE_DUCK); event(E_DUCK_FREE)
        }
        seqRibbit = Seq.build {
            repeat(2) { tween(F_SAC_V, 1f, 100f, Ease.OUT); wait(100f); tween(F_SAC_V, 0f, 110f, Ease.IN); wait(110f) }
        }
        loadMs = (uptimeMillis() - start).toFloat()
        traceEnd()
        perf.loadMs = loadMs
        logInfo("P3 load ${loadMs}ms paths parsed=${pathCache.parseCount} config=$cfg")
    }

    private fun Seq.Builder.standBody() {
        tween(B_LEAN, 20f, 260f); tween(B_S, .85f, 260f); wait(260f)
        tween(B_S, 0f, 340f, Ease.OUT); tween(B_LEAN, -2f, 340f, Ease.OUT); wait(340f)
        tween(B_LEAN, 0f, 180f); wait(180f)
    }

    private fun Seq.Builder.standOps() {
        skipIf(C_STANDING, 8); standBody()
    }

    private fun Seq.Builder.walkOps() {
        standOps(); event(E_TURN_TO_GOAL); event(E_START_WALK); until(C_ARRIVED)
    }

    private fun strokePaint(w: Float) = Paint().apply {
        isAntiAlias = aa
        color = hex(0x3a4a5a)
        style = PaintingStyle.Stroke
        strokeWidth = w
        strokeCap = StrokeCap.Round
    }

    override fun complexity(): String {
        val soft = reeds.softPointCount + boy.rig.softPointCount + frog.rig.softPointCount
        return "static shapes=$staticShapeCount paths parsed=${pathCache.parseCount} soft pts/frame≈$soft " +
            "reeds=${REEDS.size} hit regions=$HIT_COUNT layers=6 parallax"
    }

    // ================================================================= SeqHost

    override fun onEvent(id: Int): Float {
        when (id) {
            E_SAY_GO -> say("Ben", "Let's go to the lake!", "Mình ra hồ nào!")
            E_SAY_WOW -> say("Ben", "Wow! A duck and a frog!", "Ồ! Có con vịt và con ếch!")
            E_SAY_SIT -> say("Ben", "I sit down on the log.", "Mình ngồi xuống khúc gỗ.")
            E_SAY_STAND -> say("Ben", "Stand up!", "Đứng lên nào!")
            E_SAY_HELLO -> say("Ben", "Hello! Hello!", "Xin chào! Xin chào!")
            E_SAY_LOOK -> say("Ben", "Look! A duck!", "Nhìn kìa! Một con vịt!")
            E_SAY_JUMP -> say("Ben", if (hatOnHead) "My hat!" else "Jump!", if (hatOnHead) "Mũ của mình!" else "Nhảy nào!")
            E_HAPPY_ON -> happy = true
            E_HAPPY_OFF -> happy = false
            E_TURN_TO_GOAL -> return turnTo(sign(v[B_GOAL] - v[B_X]))
            E_TURN_RIGHT -> return turnTo(1f)
            E_START_WALK -> boyTarget = if (abs(v[B_GOAL] - v[B_X]) < 1f) Float.NaN else v[B_GOAL]
            E_DUCK_TAP -> duckTap()
            E_DUCK_BUSY -> duckBusy = true
            E_DUCK_FREE -> duckBusy = false
            E_RIPPLE_DUCK -> ripple(v[D_X], v[D_Y], 26f, 900f)
            E_RIPPLE_DUCK_FRONT -> ripple(v[D_X] + duckDir * 14f, v[D_Y], 22f, 900f)
        }
        return 0f
    }

    override fun condition(id: Int): Boolean = when (id) {
        C_STANDING -> v[B_S] < .05f
        C_ARRIVED -> boyTarget.isNaN()
        else -> false
    }

    private fun turnTo(d: Float): Float {
        if (d == 0f || d == boyDir) return 0f
        boyDir = d
        anim.tween(B_SX, d, 340f)
        return 340f
    }

    private fun say(who: String, en: String, vi: String) {
        talking = who == "Ben"
        talkUntil = nowMs + 600f + (en.count { it == ' ' } + 1) * 360f
        onSubtitle?.invoke(who, en, vi)
    }

    // ================================================================= actions

    private fun play(seq: Seq) {
        boyTarget = Float.NaN
        boyPlayer.play(seq)
    }

    override fun action(name: String) {
        nextAuto = nowMs + 6000f
        when (name) {
            "go" -> play(seqGo)
            "sit" -> if (v[B_S] < .9f) play(seqSit)
            "stand" -> if (v[B_S] > .05f) play(seqStand) else say("Ben", "I am standing.", "Mình đang đứng mà.")
            "wave" -> play(seqWave)
            "point" -> play(seqPoint)
            "jump" -> play(seqJump)
            "frog" -> pokeFrog()
            "home" -> walkTo(-90f)
        }
    }

    private fun walkTo(x: Float) {
        v[B_GOAL] = x
        play(seqWalk)
    }

    private fun duckTap() {
        if (duckBusy && v[D_TILT] > 5f) return
        say("Duck", "Quack, quack!", "Quạc quạc!")
        duckPlayer.play(seqDuckTap)
    }

    private fun pokeFrog() {
        if (frogState == FS_SIT) {
            frogPoked = true
            frogPlayer.play(seqRibbit)
            say("Ben", "Jump, frog, jump!", "Ếch ơi, nhảy đi!")
        } else {
            ripple(v[F_X], v[F_Y], 24f, 800f)
            say("Frog", "Ribbit!", "Ộp ộp!")
        }
    }

    private fun autoplay() {
        if (!cfg.autoplay || boyPlayer.running || hatDragging || nowMs < nextAuto) return
        nextAuto = nowMs + 2500f
        when (AUTO[autoIdx % AUTO.size]) {
            0 -> play(seqSit)
            1 -> play(seqStand)
            2 -> play(seqWave)
            3 -> play(seqPoint)
            4 -> play(seqJump)
            5 -> walkTo(150f)
            6 -> walkTo(SEAT)
            7 -> pokeFrog()
        }
        autoIdx++
    }

    // ================================================================= water fx

    private fun depth(y: Float) = .72f + clamp((y - 390f) / 110f) * .3f

    private fun ripple(x: Float, y: Float, max: Float, ms: Float, delay: Float = 0f) =
        ripples.add(x, y, max * depth(y), ms, nowMs + delay)

    private fun splash(x: Float, y: Float) {
        for (i in 0 until 3) ripple(x, y, 40f + i * 14f, 1200f, i * 140f)
        for (i in 0 until 9) drops.emit(x, y, (rng.next() - .5f) * 110f, -90f - rng.next() * 110f, 2f + rng.next() * 2f, 3f, y)
    }

    // ================================================================= update

    override fun update(frameNanos: Long) {
        if (t0Nanos < 0) {
            t0Nanos = frameNanos
            nextAuto = 9000f
        }
        nowMs = (frameNanos - t0Nanos) / 1_000_000f
        val dt = min(.05f, (nowMs - lastMs) / 1000f)
        lastMs = nowMs
        val t = nowMs / 1000f
        anim.step(nowMs)
        if (nowMs > 700f && !started) {
            started = true
            play(seqGo)
        }
        boyPlayer.step(this); duckPlayer.step(this); frogPlayer.step(this)
        autoplay()
        if (talking && nowMs > talkUntil) talking = false
        if (nowMs > nextBlink) {
            blink = true
            if (nowMs > nextBlink + 130f) { blink = false; nextBlink += 3400f }
        }
        updateBoy(dt)
        updateDuck(dt)
        updateFrog(dt)
        springs.step(dt)
        drops.step(dt, 420f)

        camX = sin(t * .2f) * .3f
        zoom = 1f + .012f * (1f - cos(t * .17f))

        stats.reset()
        poseBoy(t, dt)
        poseDuck(t)
        poseFrog(t)
        poseScenery(t)
        updateHitRegions()
        if (pendingTapUptime != 0L) tapSeenByUpdate = true
    }

    private var started = false

    private fun updateBoy(dt: Float) {
        if (!boyTarget.isNaN()) {
            val d = boyTarget - v[B_X]
            val ad = abs(d)
            if (ad < .8f) {
                v[B_X] = boyTarget; boyTarget = Float.NaN
            } else {
                v[B_W] = min(1f, v[B_W] + dt * 3.2f)
                val sp = SPEED * v[B_W] * clamp(ad / 45f + .28f)
                val mv = min(ad, sp * dt)
                v[B_X] += sign(d) * mv
                v[B_PHASE] += mv / STRIDE
            }
        } else v[B_W] = max(0f, v[B_W] - dt * 4.5f)
        lastStepIdx = floor((v[B_PHASE] - PI.toFloat() / 2f) / PI.toFloat()).toInt()
    }

    private fun poseBoy(t: Float, dt: Float) {
        val sk = boy.rig.sk
        val w = v[B_W]; val ph = v[B_PHASE]; val s = v[B_S]
        val bob = -2.6f * cos(2f * ph) * w
        val hipX = lerp(0f, -36f, s)
        val hipY = lerp(-62f, -40f, s) + bob
        val jump = v[B_JUMP]
        for (leg in 0..1) {
            val off = if (leg == 0) -2f else 2f
            val p = if (leg == 0) ph + PI.toFloat() else ph
            val fx = off + STRIDE * sin(p) * w
            val fy = -max(0f, cos(p)) * 11f * w
            val hx = hipX + off * .5f
            ik2(hx, hipY, fx, fy, LakeArt.LEG1, LakeArt.LEG2, 1, ikOut)
            val thigh = if (leg == 0) B_THIGH_F else B_THIGH_N
            val shin = if (leg == 0) B_SHIN_F else B_SHIN_N
            val shoe = if (leg == 0) B_SHOE_F else B_SHOE_N
            sk.set(thigh, hx, hipY, atan2(ikOut[1] - hipY, ikOut[0] - hx))
            sk.set(shin, ikOut[0], ikOut[1], atan2(ikOut[3] - ikOut[1], ikOut[2] - ikOut[0]))
            sk.set(shoe, ikOut[2] + 6f, ikOut[3] - 3f)
        }
        val shrink = 1f + jump / 160f
        sk.set(B_SHADOW, hipX * .6f, 2f - jump, 0f, shrink, shrink)
        val breath = sin(t * 2.1f) * .01f * (1f - w)
        sk.set(B_TORSO, hipX, hipY, (v[B_LEAN] + w * 2.5f) * DEG, 1f + v[B_SQ], 1f - v[B_SQ] + breath)

        val sx0 = 4f; val sy0 = -54f
        val nearX = lerp(-15f * sin(ph) * w, 24f, s); val nearY = lerp(46f, 36f, s)
        val farX = lerp(15f * sin(ph) * w, 20f, s); val farY = lerp(46f, 38f, s)
        val wvX = 16f + 9f * sin(t * 12f); val wvY = -50f
        val ntX = lerp(lerp(nearX, wvX, v[B_WAVE]), 50f, v[B_POINT])
        val ntY = lerp(lerp(nearY, wvY, v[B_WAVE]), -16f, v[B_POINT])
        for (arm in 0..1) {
            val tx = if (arm == 0) ntX else farX
            val ty = if (arm == 0) ntY else farY
            ik2(sx0, sy0, sx0 + tx, sy0 + ty, LakeArt.ARM1, LakeArt.ARM2, -1, ikOut)
            val up = if (arm == 0) B_UARM_N else B_UARM_F
            val fo = if (arm == 0) B_FARM_N else B_FARM_F
            val ha = if (arm == 0) B_HAND_N else B_HAND_F
            sk.set(up, sx0, sy0, atan2(ikOut[1] - sy0, ikOut[0] - sx0))
            sk.set(fo, ikOut[0], ikOut[1], atan2(ikOut[3] - ikOut[1], ikOut[2] - ikOut[0]))
            sk.set(ha, ikOut[2], ikOut[3])
        }
        val headRot = v[B_HD] + sin(t * 1.4f) * 1.2f + (if (talking) sin(t * 9f) * 1.5f else 0f)
        sk.set(B_HEAD, 4f, -62f, headRot * DEG)

        // pseudo-3D turn: front face while passing edge-on; features slide toward the facing side
        val sx = v[B_SX]
        val asx = abs(sx)
        val front = asx < .6f
        val sxE = (if (sx < 0f) -1f else 1f) * max(asx, .55f)
        sk.set(B_HEAD_FRONT, 0f, 0f, 0f, 1f / sxE, 1f)
        sk.set(B_FACE_FRONT, sx * 12f, 0f)
        for (it in boy.headSide) it.visible = !front
        for (it in boy.headFront) it.visible = front
        boy.eyeOpen.visible = !front && !happy && !blink
        boy.eyeBlink.visible = !front && blink && !happy
        boy.eyeHappy.visible = !front && happy
        val open = talking && sin(t * 19f) > -.3f
        boy.mouthOpen.visible = !front && (open || happy)
        boy.mouthSmile.visible = !front && !(open || happy)

        // springs: hair tuft and hat lag behind vertical motion
        val rootY = GROUND + jump + bob
        val vy = if (dt > 0f) (rootY - prevRootY) / dt else 0f
        val ay = if (dt > 0f) (vy - prevRootVy) / dt else 0f
        prevRootY = rootY; prevRootVy = vy
        springs.kick(SP_TUFT, -ay * dt * .015f)
        springs.kick(SP_HAT_ROT, ay * dt * .01f)
        springs.kick(SP_HAT_Y, ay * dt * .02f)
        sk.set(B_TUFT, 2f, -83f, springs.x[SP_TUFT] * 30f * DEG)
        sk.set(B_HAT, 6f, -78f + clamp(springs.x[SP_HAT_Y], -6f, 6f), (-6f + springs.x[SP_HAT_ROT] * 40f) * DEG)
        boy.hat.visible = hatOnHead

        sk.set(B_ROOT, v[B_X], GROUND + jump, 0f, sxE, 1f)
        boy.rig.pose(stats)
    }

    // ---------------------------------------------------------------- duck

    private fun updateDuck(dt: Float) {
        if (duckBusy) return
        v[D_X] += duckDir * 24f * dt
        if (v[D_X] > 905f && duckDir > 0f) { duckDir = -1f; anim.tween(D_SX, -1f, 700f) }
        if (v[D_X] < 575f && duckDir < 0f) { duckDir = 1f; anim.tween(D_SX, 1f, 700f) }
        if (nowMs > nextWake) { nextWake = nowMs + 650f; ripple(v[D_X] - duckDir * 26f * depth(v[D_Y]), v[D_Y] + 1f, 14f, 900f) }
        if (nowMs > nextDab) { nextDab = nowMs + 9000f + rng.next() * 6000f; duckPlayer.play(seqDabble) }
    }

    private fun poseDuck(t: Float) {
        val sk = duck.sk
        val sc = depth(v[D_Y])
        val bob = sin(t * 2.3f) * 1.6f
        val s = if (v[D_SX] < 0f) -1f else 1f
        sk.set(D_INNER, 0f, bob + v[D_HOP], (v[D_TILT] * s + sin(t * 2.3f + 1f) * 2f) * DEG, v[D_SX] * sc, sc)
        sk.setRot(D_FEET, sin(t * 8f) * 24f * DEG)
        sk.setRot(D_WING, -v[D_FLAP] * 55f * DEG)
        duck.pose(stats)
    }

    // ---------------------------------------------------------------- frog

    private fun startLeap(x1: Float, y1: Float, dur: Float, h: Float, toSwim: Boolean) {
        frogState = FS_LEAP
        leapX0 = v[F_X]; leapY0 = v[F_Y]; leapX1 = x1; leapY1 = y1; leapT0 = nowMs; leapDur = dur; leapH = h; leapToSwim = toSwim
    }

    private fun enterSit() {
        val a = PADS[frogPad]; val b = PADS[1 - frogPad]
        frogState = FS_SIT
        v[F_X] = a[0]; v[F_Y] = a[1] - 1f; v[F_SX] = if (b[0] > a[0]) 1f else -1f
        frogStateUntil = nowMs + 7000f + rng.next() * 4000f
        frogPoked = false
    }

    private fun updateFrog(dt: Float) {
        val a = PADS[frogPad]; val b = PADS[1 - frogPad]
        when (frogState) {
            FS_START -> if (nowMs > frogStateUntil) enterSit()
            FS_SIT -> {
                if (nowMs > frogNextRib) { frogNextRib = nowMs + 3800f + rng.next() * 3500f; frogPlayer.play(seqRibbit) }
                if (nowMs > frogStateUntil || frogPoked) {
                    frogState = FS_CROUCH1; frogStateUntil = nowMs + 140f; anim.tween(F_SQ, .12f, 140f, Ease.OUT)
                }
            }
            FS_CROUCH1 -> if (nowMs > frogStateUntil) {
                frogState = FS_CROUCH2; frogStateUntil = nowMs + 90f; anim.tween(F_SQ, -.06f, 90f, Ease.IN)
            }
            FS_CROUCH2 -> if (nowMs > frogStateUntil) startLeap(lerp(a[0], b[0], .3f), lerp(a[1], b[1], .3f) + 6f, 560f, 52f, true)
            FS_LEAP -> {
                val u = clamp((nowMs - leapT0) / leapDur)
                v[F_X] = lerp(leapX0, leapX1, u); v[F_Y] = lerp(leapY0, leapY1, u); v[F_HOP] = -leapH * 4f * u * (1f - u)
                val vx = leapX1 - leapX0
                val vy = (leapY1 - leapY0) - leapH * 4f * (1f - 2f * u)
                if (vx != 0f) v[F_SX] = sign(vx)
                v[F_ROT] = atan2(v[F_SX] * vy, abs(vx) + 1f) * 57.3f * .75f
                if (u >= 1f) {
                    v[F_ROT] = 0f; v[F_HOP] = 0f
                    if (leapToSwim) {
                        v[F_SQ] = 0f; splash(v[F_X], v[F_Y])
                        frogTx = lerp(a[0], b[0], .8f); frogTy = lerp(a[1], b[1], .8f) + 5f; frogKp = .3f
                        frogState = FS_SWIM
                    } else {
                        frogPad = 1 - frogPad
                        ripple(leapX1, leapY1, 30f, 900f)
                        anim.tween(F_SQ, .14f, 90f, Ease.OUT)
                        frogState = FS_LAND; frogStateUntil = nowMs + 90f
                    }
                }
            }
            FS_SWIM -> {
                val dx = frogTx - v[F_X]; val dy = frogTy - v[F_Y]; val d = hypot(dx, dy)
                if (abs(dx) > 2f) v[F_SX] = sign(dx)
                val p = frogKp
                frogKp = (frogKp + dt / .95f) % 1f
                if (p < .6f && frogKp >= .6f) { frogV = 78f; ripple(v[F_X] - v[F_SX] * 22f * depth(v[F_Y]), v[F_Y], 14f, 700f) }
                v[F_K] = if (frogKp < .6f) Ease.apply(Ease.IO, frogKp / .6f) else if (frogKp < .72f) 1f - (frogKp - .6f) / .12f else 0f
                frogV *= exp(-dt * 2.4f)
                val mv = min(d, (frogV + 5f) * dt)
                if (d > 0f) { v[F_X] += dx / d * mv; v[F_Y] += dy / d * mv }
                if (d < 2.5f) startLeap(b[0], b[1] - 1f, 420f, 28f, false)
            }
            FS_LAND -> if (nowMs > frogStateUntil) {
                anim.tween(F_SQ, 0f, 220f, Ease.OUT)
                enterSit()
            }
        }
    }

    private fun poseFrog(t: Float) {
        val sk = frog.rig.sk
        val sit = frogState == FS_SIT || frogState == FS_START || frogState == FS_CROUCH1 || frogState == FS_CROUCH2 || frogState == FS_LAND
        for (it in frog.sitItems) it.visible = sit
        for (it in frog.swimItems) it.visible = !sit
        val sc = depth(v[F_Y]) * 1.05f
        val y = if (frogState == FS_SWIM) 2f + sin(t * 3f) else 0f
        val sq = v[F_SQ]
        sk.set(F_INNER, 0f, y + v[F_HOP], v[F_ROT] * DEG, v[F_SX] * sc * (1f + sq), sc * (1f - sq))
        val sac = 1f + v[F_SAC_V] * .9f
        sk.set(F_SAC, 20f, -24f, 0f, sac, sac)
        val k = if (frogState == FS_LEAP) 0f else v[F_K]
        for (leg in 0..1) {
            val oy = if (leg == 0) 5f else 1f
            val hx = -16f; val hy = oy
            val kx = lerp(-34f, -6f, k); val ky = lerp(6f, 13f, k) + oy - 4f
            val px = lerp(-54f, -25f, k); val py = lerp(7f, 10f, k) + oy - 4f
            val th = if (leg == 0) F_NEAR_T else F_FAR_T
            val sh = if (leg == 0) F_NEAR_S else F_FAR_S
            val ft = if (leg == 0) F_NEAR_FOOT else F_FAR_FOOT
            sk.set(th, hx, hy, atan2(ky - hy, kx - hx), hypot(kx - hx, ky - hy), 1f)
            sk.set(sh, kx, ky, atan2(py - ky, px - kx), hypot(px - kx, py - ky), 1f)
            sk.set(ft, px - 4f, py)
        }
        frog.rig.pose(stats)
    }

    // ---------------------------------------------------------------- scenery

    private fun poseScenery(t: Float) {
        val sk = reeds.sk
        for (i in REEDS.indices) {
            val a = (5f * sin(PI.toFloat() * t / reedSway[i]) + springs.x[SP_REED + i] * 25f) * DEG
            sk.setRot(i * 3, a * .34f); sk.setRot(i * 3 + 1, a * .33f); sk.setRot(i * 3 + 2, a * .33f)
        }
        reeds.pose(stats)
    }

    private fun swayDeg(t: Float, period: Float) = -5f * cos(PI.toFloat() * t / period)

    // ================================================================= input

    private val hitCx = FloatArray(HIT_COUNT)
    private val hitCy = FloatArray(HIT_COUNT)
    private val hitRx = FloatArray(HIT_COUNT)
    private val hitRy = FloatArray(HIT_COUNT)
    private val hitDepth = FloatArray(HIT_COUNT)

    private fun setHit(i: Int, cx: Float, cy: Float, rx: Float, ry: Float, d: Float) {
        hitCx[i] = cx; hitCy[i] = cy; hitRx[i] = rx; hitRy[i] = ry; hitDepth[i] = d
    }

    private fun updateHitRegions() {
        val bsk = boy.rig.sk
        if (hatOnHead) setHit(H_HAT, bsk.worldX(B_HAT, 0f, -12f), bsk.worldY(B_HAT, 0f, -12f), 30f, 24f, 20f)
        else setHit(H_HAT, hatX, hatY - 12f, 34f, 26f, 20f)
        setHit(H_BOY, v[B_X], GROUND + v[B_JUMP] - 95f, 48f, 100f, 20f)
        setHit(H_DUCK, v[D_X], v[D_Y] - 14f, 44f, 34f, 14f)
        setHit(H_FROG, v[F_X], v[F_Y] - 14f, 40f, 32f, 14f)
        for (i in REEDS.indices) {
            val r = REEDS[i]
            setHit(H_REED + i, r[0], r[1] - r[2] * .6f, 11f, r[2] * .6f, 20f)
        }
        for (i in DAISIES.indices) setHit(H_DAISY + i, DAISIES[i][0], DAISIES[i][1] - 24f, 16f, 18f, 20f)
        for (i in PADS.indices) setHit(H_PAD + i, PADS[i][0], PADS[i][1], PADS[i][2] + 6f, PADS[i][3] + 8f, 14f)
        setHit(H_ROCK, 560f, 528f, 30f, 16f, 32f)
        setHit(H_ROCK + 1, 590f, 534f, 20f, 12f, 32f)
        setHit(H_LOG, 410f, 421f, 46f, 22f, 20f)
        setHit(H_TREE, 70f, 334f - 120f, 80f, 80f, 20f)
        for (i in CLOUDS.indices) setHit(H_CLOUD + i, cloudX(i), CLOUDS[i][1] - 6f, 80f * CLOUDS[i][2], 36f * CLOUDS[i][2], 2f)
        setHit(H_SUN, 132f, 92f, 48f, 48f, 2f)
    }

    private fun cloudX(i: Int): Float {
        val c = CLOUDS[i]
        val u = ((nowMs / 1000f - c[5]) / c[4]) % 1f
        return -260f + 1480f * u + c[0]
    }

    /** Screen px -> design units after camera, before parallax. */
    private fun sceneX(px: Float) = (vp.toDesignX(px) - 480f) / zoom + 480f
    private fun sceneY(py: Float) = (vp.toDesignY(py) - 270f) / zoom + 270f
    private fun parallax(d: Float) = -camX * d

    private fun hitTest(x: Float, y: Float): Int {
        // touch targets are at least MIN_TOUCH_DP across on any screen (phone scenes are drawn smaller than iPad ones)
        val minR = MIN_TOUCH_DP / 2f * density / (vp.scale * zoom)
        for (i in 0 until HIT_COUNT) {
            val lx = x - parallax(hitDepth[i])
            val dx = (lx - hitCx[i]) / max(hitRx[i], minR)
            val dy = (y - hitCy[i]) / max(hitRy[i], minR)
            if (dx * dx + dy * dy <= 1f) return i
        }
        return -1
    }

    override fun pointerDown(px: Float, py: Float, uptime: Long) {
        val x = sceneX(px); val y = sceneY(py)
        val hit = hitTest(x, y)
        val gx = x - parallax(20f) // ground-layer coords
        val reacted = when {
            hit == H_HAT -> {
                hatDragging = true
                if (hatOnHead) {
                    val bsk = boy.rig.sk
                    hatX = bsk.worldX(B_HAT, 0f, 0f); hatY = bsk.worldY(B_HAT, 0f, 0f)
                    hatOnHead = false
                }
                dragDx = hatX - gx; dragDy = hatY - y
                true
            }
            hit == H_BOY -> { action(if (v[B_S] > .5f) "wave" else "jump"); true }
            hit == H_DUCK -> { nextAuto = nowMs + 6000f; duckTap(); true }
            hit == H_FROG -> { pokeFrog(); true }
            hit >= H_REED && hit < H_REED + REEDS.size -> { springs.kick(SP_REED + hit - H_REED, if (gx < hitCx[hit]) 4f else -4f); true }
            hit >= H_DAISY && hit < H_DAISY + DAISIES.size -> { springs.kick(SP_DAISY + hit - H_DAISY, 9f); true }
            hit >= H_PAD && hit < H_PAD + PADS.size -> {
                val p = PADS[hit - H_PAD]; springs.kick(SP_PAD + hit - H_PAD, 40f); ripple(p[0], p[1], 34f, 900f); true
            }
            hit == H_ROCK || hit == H_ROCK + 1 -> { splash(hitCx[hit], hitCy[hit] + 6f); true }
            hit == H_LOG -> { action("sit"); true }
            hit == H_TREE -> { springs.kick(SP_TREE, 3f); true }
            hit >= H_CLOUD && hit < H_CLOUD + CLOUDS.size -> { springs.kick(SP_CLOUD + hit - H_CLOUD, 3f); true }
            hit == H_SUN -> { springs.kick(SP_SUN, 4f); true }
            insideBank(gx, y) -> { nextAuto = nowMs + 6000f; walkTo(clamp(gx, 24f, SEAT)); true }
            y > 312f && x > 360f -> { ripple(x - parallax(14f), y, 30f, 900f); true }
            else -> false
        }
        if (reacted) {
            tapsTotal++
            if (pendingTapUptime == 0L) {
                pendingTapUptime = uptime
                tapSeenByUpdate = false
                tapCookie++
                traceBeginAsync("P3_tap", tapCookie)
            }
        }
    }

    override fun pointerMove(px: Float, py: Float) {
        if (!hatDragging) return
        hatX = sceneX(px) - parallax(20f) + dragDx
        hatY = sceneY(py) + dragDy
    }

    override fun pointerUp(px: Float, py: Float) {
        if (!hatDragging) return
        hatDragging = false
        val bsk = boy.rig.sk
        val hx = bsk.worldX(B_HAT, 0f, 0f); val hy = bsk.worldY(B_HAT, 0f, 0f)
        if (hypot(hatX - hx, hatY - hy) < 70f) {
            hatOnHead = true
            springs.kick(SP_HAT_ROT, 1.5f)
            say("Ben", "A hat for me!", "Mũ cho mình!")
        } else if (!insideBank(hatX, hatY) || hatY < 300f) {
            if (hatX > 470f && hatY > 312f) splash(hatX, hatY)
            hatX = HAT_HOME_X; hatY = HAT_HOME_Y
        }
    }

    private fun insideBank(x: Float, y: Float): Boolean {
        // polygon approximation of the bank path
        var inside = false
        var j = BANK.size / 2 - 1
        for (i in 0 until BANK.size / 2) {
            val xi = BANK[i * 2]; val yi = BANK[i * 2 + 1]; val xj = BANK[j * 2]; val yj = BANK[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    // ================================================================= draw

    override fun draw(scope: DrawScope) {
        val canvas = scope.drawContext.canvas
        val w = scope.size.width
        val h = scope.size.height
        vp.fit(w, h)
        val t = nowMs / 1000f
        val ls = layers
        val cached = cfg.cacheStatic && ls != null
        if (cached && (recordedW != w || recordedH != h)) recordLayers(scope, ls!!)

        canvas.save()
        density = scope.density
        canvas.translate(vp.ox, vp.oy)
        canvas.scale(vp.scale, vp.scale)
        // full screen: everything inside the stage bleed is drawn, the safe area is centred
        canvas.clipRect(vp.visLeft, vp.visTop, vp.visRight, vp.visBottom)
        canvas.translate(480f, 270f); canvas.scale(zoom, zoom); canvas.translate(-480f, -270f)

        // sky
        staticLayer(scope, canvas, L_SKY, 0f, cached)

        // d=2: sun, clouds, birds
        canvas.save(); canvas.translate(parallax(2f), 0f)
        canvas.save(); canvas.translate(132f, 92f)
        val sunS = v[SUN_S] + springs.x[SP_SUN] * .25f
        canvas.scale(sunS, sunS)
        sun.drawAll(canvas, 1f, stats)
        canvas.restore()
        for (i in CLOUDS.indices) {
            val c = CLOUDS[i]
            val s = c[2] * (1f + springs.x[SP_CLOUD + i] * .3f)
            canvas.save(); canvas.translate(cloudX(i), c[1]); canvas.scale(s, s)
            groupAlpha(canvas, cloud, c[3], cloudRect)
            canvas.restore()
        }
        drawBirds(canvas, t)
        canvas.restore()

        staticLayer(scope, canvas, L_MTN, parallax(5f), cached)
        staticLayer(scope, canvas, L_HILLS, parallax(9f), cached)

        // d=14: water
        canvas.save(); canvas.translate(parallax(14f), 0f)
        staticLayer(scope, canvas, L_LAKE, 0f, cached)
        canvas.save()
        canvas.clipPath(lakeClip)
        staticLayer(scope, canvas, L_REFL, sin(t * .9f) * 2.5f, cached)
        drawShimmer(canvas, t)
        canvas.restore()
        shoreline.drawAll(canvas, 1f, stats)
        ripples.draw(canvas, nowMs, stats)
        if (cfg.particleMode == "points") drops.drawPoints(canvas, 6f, stats) else drops.drawCircles(canvas, stats)
        drawWaterObjects(canvas)
        canvas.restore()

        // d=20: ground
        canvas.save(); canvas.translate(parallax(20f), 0f)
        staticLayer(scope, canvas, L_GROUND, 0f, cached)
        drawBone(canvas, 70f, 334f, (swayDeg(t, 4.2f) + springs.x[SP_TREE] * 6f) * DEG, 1.05f, tree)
        for (i in DAISIES.indices) {
            val d = DAISIES[i]
            drawBone(canvas, d[0], d[1], (swayDeg(t, daisySway[i]) + springs.x[SP_DAISY + i] * 8f) * DEG, 1f + abs(springs.x[SP_DAISY + i]) * .05f, daisies[i])
        }
        reeds.draw(canvas, 1f, m, stats)
        boy.rig.draw(canvas, 1f, m, stats)
        if (!hatOnHead) {
            canvas.save(); canvas.translate(hatX, hatY)
            val hs = if (hatDragging) 1.12f else 1f
            canvas.scale(hs, hs)
            hatShapes.drawAll(canvas, 1f, stats)
            canvas.restore()
        }
        canvas.restore()

        staticLayer(scope, canvas, L_FG, parallax(32f), cached)
        canvas.restore()

        if (cached) stats.draws += staticShapeCount
        stats.commit()
        reportLatency()
    }

    private fun reportLatency() {
        if (pendingTapUptime == 0L || !tapSeenByUpdate) return
        val lat = (uptimeMillis() - pendingTapUptime).toFloat()
        perf.onLatency(lat)
        traceEndAsync("P3_tap", tapCookie)
        pendingTapUptime = 0L
    }

    private fun drawBone(canvas: Canvas, x: Float, y: Float, rot: Float, s: Float, shapes: Array<Shape>) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rot / DEG)
        canvas.scale(s, s)
        shapes.drawAll(canvas, 1f, stats)
        canvas.restore()
    }

    /** SVG group opacity: per-shape alpha, or one saveLayer (cfg.waterMode = "layer"). */
    private fun groupAlpha(canvas: Canvas, shapes: Array<Shape>, alpha: Float, bounds: Rect) {
        if (alphaWater) {
            shapes.drawAll(canvas, alpha, stats)
        } else {
            layerPaint.alpha = alpha
            canvas.saveLayer(bounds, layerPaint)
            shapes.drawAll(canvas, 1f, stats)
            canvas.restore()
            stats.saveLayers++
        }
    }

    private fun drawBirds(canvas: Canvas, t: Float) {
        for (i in 0..1) {
            val u = if (i == 0) t * .024f else t * .019f + .4f
            val y = if (i == 0) 140f else 176f
            val s = if (i == 0) 1f else .8f
            val x = (u % 1f) * 1260f - 150f
            val fl = sin(t * 7f + y) * 7f * s
            val p = birdPath[i]
            p.reset()
            p.moveTo(x - 12f * s, y - fl)
            p.quadraticTo(x - 5f * s, y - 4f * s, x, y)
            p.quadraticTo(x + 5f * s, y - 4f * s, x + 12f * s, y - fl)
            canvas.drawPath(p, birdPaint[i])
            stats.draws++; stats.strokes++
        }
    }

    private fun drawShimmer(canvas: Canvas, t: Float) {
        for (i in 0 until 12) {
            val o = i * 4
            val x = shim[o] + sin(t * .35f + shim[o + 3]) * 24f
            val y = shim[o + 1]
            shimmerPaint.alpha = .12f + .25f * (.5f + .5f * sin(t * 1.4f + shim[o + 3] * 2f))
            canvas.drawRoundRect(x, y, x + shim[o + 2], y + 2.4f, 1.2f, 1.2f, shimmerPaint)
            stats.draws++
        }
    }

    private fun drawWaterObjects(canvas: Canvas) {
        for (i in 0 until 3) waterY[i] = PADS[i][1]
        waterY[3] = v[D_Y]
        waterY[4] = v[F_Y] + if (frogState == FS_SIT) 1f else 0f
        // insertion sort by y (5 items, no allocation)
        for (i in 0 until 5) waterOrder[i] = i
        for (i in 1 until 5) {
            val key = waterOrder[i]
            var j = i - 1
            while (j >= 0 && waterY[waterOrder[j]] > waterY[key]) { waterOrder[j + 1] = waterOrder[j]; j-- }
            waterOrder[j + 1] = key
        }
        for (k in 0 until 5) {
            when (val o = waterOrder[k]) {
                3 -> drawSwimmer(canvas, duck, v[D_X], v[D_Y])
                4 -> drawSwimmer(canvas, frog.rig, v[F_X], v[F_Y])
                else -> {
                    canvas.save()
                    canvas.translate(PADS[o][0], PADS[o][1] + springs.x[SP_PAD + o])
                    pads[o].drawAll(canvas, 1f, stats)
                    canvas.restore()
                }
            }
        }
    }

    /** reference/lake.html swimmer(): the rig twice, below the waterline at 0.38 opacity and above it. */
    private fun drawSwimmer(canvas: Canvas, rig: Rig, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.save()
        canvas.clipRect(-140f, 0f, 140f, 90f)
        if (alphaWater) {
            rig.draw(canvas, .38f, m, stats)
        } else {
            layerPaint.alpha = .38f
            canvas.saveLayer(belowRect, layerPaint)
            rig.draw(canvas, 1f, m, stats)
            canvas.restore()
            stats.saveLayers++
        }
        canvas.restore()
        canvas.save()
        canvas.clipRect(-140f, -220f, 140f, 0f)
        rig.draw(canvas, 1f, m, stats)
        canvas.restore()
        canvas.restore()
    }

    // ---------------------------------------------------------------- static layers

    private fun staticLayer(scope: DrawScope, canvas: Canvas, id: Int, dx: Float, cached: Boolean) {
        canvas.save()
        canvas.translate(dx, 0f)
        if (cached) {
            canvas.translate(-PAD_X, -PAD_Y)
            scope.drawLayer(layers!![id])
            stats.layersDrawn++
        } else drawStatic(canvas, id)
        canvas.restore()
    }

    private fun drawStatic(canvas: Canvas, id: Int) {
        when (id) {
            L_SKY -> sky.drawAll(canvas, 1f, stats)
            L_MTN -> mountains.drawAll(canvas, 1f, stats)
            L_HILLS -> hills.drawAll(canvas, 1f, stats)
            L_LAKE -> lakeBody.drawAll(canvas, 1f, stats)
            L_REFL -> {
                canvas.save()
                canvas.translate(0f, 628f); canvas.scale(1f, -1f)
                groupAlpha(canvas, mountains, .28f, reflRectFlipped)
                canvas.restore()
            }
            L_GROUND -> groundStatic.drawAll(canvas, 1f, stats)
            L_FG -> foreground.drawAll(canvas, 1f, stats)
        }
    }

    private val reflRectFlipped = Rect(-175f, 140f, 1145f, 325f)

    private fun recordLayers(scope: DrawScope, ls: Array<GraphicsLayer>) {
        traceBegin("P3_recordLayers")
        val size = IntSize((vp.stage.safeW + PAD_X * 2).toInt(), (vp.stage.safeH + PAD_Y * 2).toInt())
        for (id in 0 until layerCount) {
            ls[id].record(scope, scope.layoutDirection, size) {
                val c = drawContext.canvas
                c.translate(PAD_X, PAD_Y)
                drawStatic(c, id)
            }
        }
        recordedW = scope.size.width; recordedH = scope.size.height
        traceEnd()
    }

    companion object {
        const val MIN_TOUCH_DP = 48f
        const val GROUND = 440f
        const val SEAT = 446f
        const val SPEED = 96f
        const val STRIDE = 18f
        const val HAT_HOME_X = 205f
        const val HAT_HOME_Y = 478f
        const val PAD_X = 180f // >= stage bleed + parallax margin
        const val PAD_Y = 110f

        const val L_SKY = 0; const val L_MTN = 1; const val L_HILLS = 2; const val L_LAKE = 3
        const val L_REFL = 4; const val L_GROUND = 5; const val L_FG = 6

        // anim value indices
        const val B_X = 0; const val B_SX = 1; const val B_W = 2; const val B_PHASE = 3; const val B_S = 4
        const val B_LEAN = 5; const val B_SQ = 6; const val B_HD = 7; const val B_WAVE = 8; const val B_POINT = 9
        const val B_JUMP = 10; const val B_GOAL = 11
        const val D_X = 12; const val D_Y = 13; const val D_SX = 14; const val D_TILT = 15; const val D_FLAP = 16; const val D_HOP = 17
        const val F_X = 18; const val F_Y = 19; const val F_SX = 20; const val F_ROT = 21; const val F_HOP = 22; const val F_SQ = 23
        const val F_SAC_V = 24; const val F_K = 25
        const val SUN_S = 26; const val CLOUD_S = 27
        const val V_COUNT = 30

        // springs
        const val SP_REED = 0
        val SP_DAISY = SP_REED + REEDS.size
        val SP_PAD = SP_DAISY + DAISIES.size
        val SP_TREE = SP_PAD + PADS.size
        val SP_TUFT = SP_TREE + 1
        val SP_HAT_ROT = SP_TUFT + 1
        val SP_HAT_Y = SP_HAT_ROT + 1
        val SP_SUN = SP_HAT_Y + 1
        val SP_CLOUD = SP_SUN + 1
        val SP_COUNT = SP_CLOUD + CLOUDS.size

        // hit regions, topmost first
        const val H_HAT = 0; const val H_BOY = 1; const val H_DUCK = 2; const val H_FROG = 3
        const val H_REED = 4
        val H_DAISY = H_REED + REEDS.size
        val H_PAD = H_DAISY + DAISIES.size
        val H_ROCK = H_PAD + PADS.size
        val H_LOG = H_ROCK + 2
        val H_TREE = H_LOG + 1
        val H_CLOUD = H_TREE + 1
        val H_SUN = H_CLOUD + CLOUDS.size
        val HIT_COUNT = H_SUN + 1 // + bank polygon + water (tested after)

        val BANK = floatArrayOf(
            -170f, 300f, -40f, 296f, 120f, 286f, 300f, 304f, 400f, 314f, 440f, 342f, 466f, 372f,
            474f, 422f, 484f, 482f, 556f, 560f, 618f, 650f, -170f, 650f,
        )

        // events / conditions
        const val E_SAY_GO = 1; const val E_SAY_WOW = 2; const val E_SAY_SIT = 3; const val E_SAY_STAND = 4
        const val E_SAY_HELLO = 5; const val E_SAY_LOOK = 6; const val E_SAY_JUMP = 7; const val E_HAPPY_ON = 8
        const val E_HAPPY_OFF = 9; const val E_TURN_TO_GOAL = 10; const val E_TURN_RIGHT = 11; const val E_START_WALK = 12
        const val E_DUCK_TAP = 13; const val E_DUCK_BUSY = 14; const val E_DUCK_FREE = 15; const val E_RIPPLE_DUCK = 16
        const val E_RIPPLE_DUCK_FRONT = 17
        const val C_STANDING = 1; const val C_ARRIVED = 2

        // frog states
        const val FS_START = 0; const val FS_SIT = 1; const val FS_CROUCH1 = 2; const val FS_CROUCH2 = 3
        const val FS_LEAP = 4; const val FS_SWIM = 5; const val FS_LAND = 6

        val AUTO = intArrayOf(0, 2, 1, 3, 4, 5, 2, 6, 7, 4)
    }
}
