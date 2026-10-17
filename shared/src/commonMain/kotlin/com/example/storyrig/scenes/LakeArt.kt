package com.example.storyrig.scenes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RadialGradientShader
import com.example.storyrig.engine.ArtBuilder
import com.example.storyrig.engine.DrawItem
import com.example.storyrig.engine.Part
import com.example.storyrig.engine.PathCache
import com.example.storyrig.engine.Rig
import com.example.storyrig.engine.Shape
import com.example.storyrig.engine.Skeleton
import com.example.storyrig.engine.SoftItem
import com.example.storyrig.engine.SoftPath
import com.example.storyrig.engine.SoftPath.Companion.HALF
import com.example.storyrig.engine.hex
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shapes, colours and rigs of P3 `explore_lake`, transcribed from reference/lake.html
 * (SVG path strings kept verbatim). Pure data: no drawing or animation logic here.
 * Coordinates are the demo's 960x540 viewBox (the 1920x1080 frame at 2x).
 */
object LakeArt {
    object C {
        val skin = hex(0xffd9bd); val skin2 = hex(0xefbd99); val hair = hex(0x5a3a2a)
        val shirt = hex(0x4fb3e8); val shirt2 = hex(0x3c97cb); val shorts = hex(0x34507a)
        val shoe = hex(0xff5d5d); val shoe2 = hex(0xd64848); val eye = hex(0x2b2b3a)
        val cheek = hex(0xff9fa6); val line = hex(0xb5475a); val mouth = hex(0x8f2d3f)
        val white = Color.White
    }

    // Art covers the stage bleed (x -150..1110, y -90..630 in these units) plus parallax margin.
    // Paths reaching past -40 / 1000 / 560 are the bleed extension; inside that, strings are verbatim from the demo.
    const val LAKE = "M360 314 Q680 306 1000 312 Q1070 313 1140 314 V650 H360Z"
    const val SHIRT = "M-16 -62 Q4 -70 22 -60 Q28 -36 24 -6 L-20 -6 Q-24 -36 -16 -62Z"

    // pad x, y, rx, ry, flower
    val PADS = arrayOf(
        floatArrayOf(700f, 452f, 38f, 10f, 0f),
        floatArrayOf(862f, 398f, 29f, 8f, 0f),
        floatArrayOf(596f, 496f, 32f, 9f, 1f),
    )

    // reed x, y, height. First three are the demo's cattails; 17 more make the 20 from SPIKE.md.
    val REEDS = arrayOf(
        floatArrayOf(486f, 456f, 62f), floatArrayOf(498f, 450f, 76f), floatArrayOf(512f, 468f, 58f),
        floatArrayOf(452f, 352f, 44f), floatArrayOf(462f, 364f, 52f), floatArrayOf(470f, 384f, 48f),
        floatArrayOf(478f, 398f, 58f), floatArrayOf(482f, 412f, 50f), floatArrayOf(470f, 424f, 46f),
        floatArrayOf(490f, 430f, 66f), floatArrayOf(494f, 478f, 70f), floatArrayOf(506f, 490f, 60f),
        floatArrayOf(518f, 502f, 72f), floatArrayOf(530f, 514f, 64f), floatArrayOf(542f, 526f, 58f),
        floatArrayOf(554f, 540f, 66f), floatArrayOf(578f, 524f, 46f), floatArrayOf(600f, 538f, 52f),
        floatArrayOf(614f, 530f, 44f), floatArrayOf(538f, 550f, 60f),
    )

    // daisy x, y, petal colour (0 white, 1 pink)
    val DAISIES = arrayOf(
        floatArrayOf(150f, 392f, 0f), floatArrayOf(230f, 410f, 1f), floatArrayOf(310f, 384f, 0f),
        floatArrayOf(120f, 500f, 1f), floatArrayOf(260f, 512f, 0f),
    )

    // cloud x, y, scale, opacity, drift seconds, delay seconds
    val CLOUDS = arrayOf(
        floatArrayOf(160f, 70f, .9f, .95f, 95f, -20f),
        floatArrayOf(520f, 52f, .7f, .85f, 130f, -80f),
        floatArrayOf(760f, 110f, .55f, .8f, 110f, -50f),
    )

    val PINES_X = floatArrayOf(380f, 402f, 430f, 466f, 500f, 540f, 590f, 630f, 676f, 720f, 760f, 806f, 850f, 890f, 930f, 968f, 1006f, 1044f, 1082f, 1118f)

    fun sky(b: ArtBuilder): Array<Shape> {
        val g = LinearGradientShader(
            Offset(0f, 0f), Offset(0f, 540f),
            listOf(hex(0x8fd2ff), hex(0xd9f2ff), hex(0xf1fbff)), listOf(0f, .75f, 1f),
        )
        b.rect(-170f, -110f, 1300f, 760f, Color.White, shader = g)
        return b.build()
    }

    fun sun(b: ArtBuilder): Array<Shape> {
        // drawn in sun-local space (centre 0,0)
        b.circle(0f, 0f, 70f, hex(0xfff3b0), .35f)
        val g = RadialGradientShader(Offset(0f, 0f), 44f, listOf(hex(0xfff6c2), hex(0xffd84d)), listOf(0f, 1f))
        b.circle(0f, 0f, 44f, Color.White, shader = g)
        return b.build()
    }

    fun cloud(b: ArtBuilder): Array<Shape> {
        b.ellipse(0f, 0f, 60f, 24f, C.white)
        b.circle(-26f, -12f, 24f, C.white)
        b.circle(14f, -22f, 30f, C.white)
        b.circle(42f, -4f, 20f, C.white)
        return b.build()
    }

    fun mountains(b: ArtBuilder): Array<Shape> {
        b.path("M-170 318 L-170 262 L-110 232 L-40 280 L60 226 L130 270 L230 150 L330 252 L420 196 L520 290 L600 212 L700 262 L800 168 L900 252 L1000 214 L1060 250 L1140 196 V318Z", hex(0x9db8d6))
        b.path("M230 150 L208 172 L220 168 L230 178 L242 166 L253 172Z", C.white)
        b.path("M800 168 L776 192 L789 187 L800 197 L812 185 L825 192Z", C.white)
        b.path("M420 196 L405 210 L414 208 L420 214 L428 207 L436 210Z", C.white)
        b.path("M-170 318 L-170 290 L-90 262 L-40 290 L90 268 L170 300 L300 238 L420 300 L560 250 L680 296 L860 236 L1000 290 L1070 264 L1140 280 V318Z", hex(0x86a6c8))
        return b.build()
    }

    fun hills(b: ArtBuilder): Array<Shape> {
        b.path("M-170 330 Q-100 296 -40 316 Q120 286 260 304 Q380 270 520 300 Q660 280 780 302 Q880 288 1000 300 Q1070 292 1140 302 V340 H-170Z", hex(0x7cc274))
        for (i in PINES_X.indices) {
            val s = .55f + ((i * 37) % 5) / 20f
            val c = if (i % 2 == 1) hex(0x3f8f4f) else hex(0x4b9d58)
            b.group(tx = PINES_X[i], ty = 316f + (i % 3), s = s) {
                rect(-2.5f, -8f, 5f, 10f, hex(0x7a5a3a))
                path("M0 -46 L13 -20 L7 -20 L16 -6 L-16 -6 L-7 -20 L-13 -20Z", c)
            }
        }
        return b.build()
    }

    fun lakeBody(b: ArtBuilder): Array<Shape> {
        val g = LinearGradientShader(
            Offset(0f, 308f), Offset(0f, 560f),
            listOf(hex(0xa7def2), hex(0x6cbde0), hex(0x3d93c2)), listOf(0f, .45f, 1f),
        )
        b.path(LAKE, Color.White, shader = g)
        return b.build()
    }

    fun shoreline(b: ArtBuilder): Array<Shape> {
        b.stroke("M362 314 Q680 306 1000 312 Q1070 313 1140 314", C.white, 3f, .5f, round = false)
        return b.build()
    }

    fun groundStatic(b: ArtBuilder): Array<Shape> {
        b.path("M-170 300 Q-110 294 -40 296 Q120 286 300 304 Q410 314 452 344 Q480 374 488 424 Q500 484 576 560 Q610 600 640 650 H-170Z", hex(0xead8a2))
        b.path("M-170 300 Q-110 294 -40 296 Q120 286 300 304 Q400 314 440 342 Q466 372 474 422 Q484 482 556 560 Q590 600 618 650 H-170Z", hex(0x94d36f))
        b.stroke("M-170 364 Q-100 360 -40 360 Q160 340 380 356 Q420 360 446 380", hex(0xa4dc80), 18f, .6f, round = false)
        b.path("M-170 450 L-40 446 Q180 430 430 442 Q452 450 432 458 Q180 450 -40 472 L-170 476Z", hex(0xe4cf98))
        // log
        b.rect(370f, 404f, 80f, 34f, hex(0xa0683f), rx = 16f)
        b.rect(376f, 408f, 66f, 6f, hex(0xb9814f), .7f, rx = 3f)
        b.ellipse(450f, 421f, 9f, 17f, hex(0xd9a86c))
        b.ellipse(450f, 421f, 5f, 10f, hex(0xb07a45), stroke = 2f)
        return b.build()
    }

    fun foreground(b: ArtBuilder): Array<Shape> {
        b.path("M-170 560 Q-150 520 -130 560 Q-110 508 -90 560 Q-66 524 -40 560 Q-10 512 10 560 Q24 506 44 560 Q60 520 80 560 Q100 500 120 560 Q140 526 160 560Z", hex(0x6fbf58))
        b.ellipse(560f, 528f, 26f, 12f, hex(0x9aa3ad))
        b.ellipse(590f, 534f, 16f, 8f, hex(0xb4bcc5))
        return b.build()
    }

    fun tree(b: ArtBuilder): Array<Shape> {
        b.rect(-9f, -80f, 18f, 82f, hex(0xa8744c), rx = 8f)
        b.circle(0f, -112f, 54f, hex(0x5fb85f))
        b.circle(-34f, -88f, 32f, hex(0x52a955))
        b.circle(34f, -92f, 34f, hex(0x52a955))
        b.circle(10f, -140f, 28f, hex(0x6cc46a))
        return b.build()
    }

    fun daisy(b: ArtBuilder, pink: Boolean): Array<Shape> {
        val c = if (pink) hex(0xffb3c7) else C.white
        b.stroke("M0 0 Q-2 -12 0 -22", hex(0x4caf6a), 3f, round = false)
        for (a in 0 until 6) b.group(rotDeg = a * 60f, px = 0f, py = -22f) { ellipse(0f, -28f, 3.2f, 6f, c) }
        b.circle(0f, -22f, 3.5f, hex(0xffd34d))
        return b.build()
    }

    fun pad(b: ArtBuilder, rx: Float, ry: Float, flower: Boolean): Array<Shape> {
        val a = .32f
        val d = "M0 0 L${rx * cos(-a)} ${ry * sin(-a)} A$rx $ry 0 1 0 ${rx * cos(a)} ${ry * sin(a)}Z"
        b.path(d, hex(0x4fae55))
        b.stroke("M0 0 L${-rx * .8f} 0 M0 0 L${-rx * .5f} ${-ry * .7f} M0 0 L${-rx * .5f} ${ry * .7f}", hex(0x3f9447), 1.6f, round = false)
        if (flower) {
            for (r in 0 until 5) b.group(tx = -8f, ty = -4f, rotDeg = r * 72f) { ellipse(0f, -7f, 4f, 8f, hex(0xffb3cf)) }
            b.circle(-8f, -4f, 3.6f, hex(0xffe066))
        }
        return b.build()
    }

    fun hat(b: ArtBuilder): Array<Shape> {
        // origin = bottom centre of the cap, brim to +x (the way Ben faces)
        b.path("M-22 0 Q-22 -27 0 -27 Q22 -27 22 0Z", hex(0xff5d5d))
        b.stroke("M-19 -11 Q0 -17 19 -11", C.white, 3f, .8f)
        b.ellipse(10f, 0f, 27f, 5f, hex(0xd64848))
        b.circle(0f, -27f, 3.5f, hex(0xffd34d))
        return b.build()
    }

    // ---------------------------------------------------------------- reeds (soft)

    /** One skeleton for all reeds: 3 bones each (base, mid, top). */
    fun reedRig(b: ArtBuilder, aa: Boolean): Rig {
        val n = REEDS.size
        val sk = Skeleton(n * 3)
        for (i in 0 until n) {
            val (x, y, h) = REEDS[i]
            sk.set(i * 3, x, y)
            sk.parent[i * 3 + 1] = i * 3; sk.set(i * 3 + 1, 0f, -h / 3f)
            sk.parent[i * 3 + 2] = i * 3 + 1; sk.set(i * 3 + 2, 0f, -h / 3f)
        }
        sk.captureBind()
        val items = ArrayList<DrawItem>()
        val stemCol = hex(0x5f9e45)
        val leafCol = hex(0x6fb352)
        for (i in 0 until n) {
            val h = REEDS[i][2]
            val b0 = i * 3; val b1 = b0 + 1; val b2 = b0 + 2
            val i0 = intArrayOf(b0); val i01 = intArrayOf(b0, b1); val i12 = intArrayOf(b1, b2)
            val i1 = intArrayOf(b1); val i2 = intArrayOf(b2)
            // stem: demo "M0 0 Q2 -h/2 0 -h" as a cubic over the 3 bones (points in base-bone space)
            items += SoftItem(
                SoftPath.Builder(sk, b0)
                    .moveTo(0f, 0f, i0)
                    .cubicTo(2f, -h / 3f, i01, HALF, 2f, -2f * h / 3f, i12, HALF, 0f, -h, i2)
                    .build(stemCol, 3f, aa, round = false, outlineFill = b.outlineFill)
            )
            // leaf: "M0 -.4h Q14 -.7h 18 -.9h"
            items += SoftItem(
                SoftPath.Builder(sk, b0)
                    .moveTo(0f, -.4f * h, i1)
                    .quadTo(14f, -.7f * h, i12, HALF, 18f, -.9f * h, i2)
                    .build(leafCol, 3f, aa, round = false, outlineFill = b.outlineFill)
            )
            // head + spike are rigid on the top bone (top bone origin is at -2h/3)
            b.group(ty = 2f * h / 3f) {
                rect(-4f, -h - 2f, 8f, 20f, hex(0x8a5a36), rx = 4f)
                stroke("M0 ${-h - 2f} v-8", stemCol, 2f, round = false)
            }
            items += Part(b2, b.build())
        }
        return Rig(sk, items.toTypedArray())
    }

    // ---------------------------------------------------------------- duck

    const val D_INNER = 0
    const val D_FEET = 1
    const val D_WING = 2

    fun duckRig(b: ArtBuilder): Rig {
        val sk = Skeleton(3)
        sk.parent[D_FEET] = D_INNER; sk.set(D_FEET, 0f, 6f)
        sk.parent[D_WING] = D_INNER; sk.set(D_WING, 12f, -9f)
        sk.captureBind()
        val cream = hex(0xfff4c4)
        val orange = hex(0xff9f1c)
        b.group(ty = -6f) {
            path("M-8 5 L-13 17 L-3 17Z", orange)
            path("M6 5 L1 17 L11 17Z", orange)
        }
        val feet = Part(D_FEET, b.build())
        b.path("M-30 -10 Q-48 -28 -40 -34 Q-30 -24 -18 -16Z", cream)
        b.ellipse(-2f, -3f, 32f, 16f, cream)
        b.ellipse(-4f, 5f, 27f, 8f, hex(0xefdc9a))
        b.ellipse(17f, -17f, 9f, 13f, cream)
        b.circle(20f, -30f, 13f, cream)
        b.path("M30 -32 Q44 -33 47 -27 Q41 -22 30 -25Z", orange)
        b.circle(24f, -33f, 2.8f, C.eye)
        b.circle(25f, -34f, 1f, C.white)
        b.ellipse(21f, -24f, 4f, 2.4f, hex(0xffb3b3), .75f)
        val body = Part(D_INNER, b.build())
        b.group(tx = -12f, ty = 9f) { path("M-22 -9 Q-4 -24 14 -9 Q0 3 -22 -9Z", hex(0xffe58a)) }
        val wing = Part(D_WING, b.build())
        return Rig(sk, arrayOf(feet, body, wing))
    }

    // ---------------------------------------------------------------- frog

    const val F_INNER = 0
    const val F_SAC = 1
    const val F_NEAR_T = 2
    const val F_NEAR_S = 3
    const val F_FAR_T = 4
    const val F_FAR_S = 5
    const val F_NEAR_FOOT = 6
    const val F_FAR_FOOT = 7

    class FrogRig(val rig: Rig, val sitItems: Array<DrawItem>, val swimItems: Array<DrawItem>)

    fun frogRig(b: ArtBuilder, aa: Boolean): FrogRig {
        val sk = Skeleton(8)
        for (i in 1 until 8) sk.parent[i] = F_INNER
        sk.set(F_SAC, 20f, -24f)
        // legs: unit-length bind bones, stretched with scaleX each frame
        sk.set(F_NEAR_S, 1f, 0f); sk.set(F_FAR_S, 1f, 0f)
        sk.captureBind()
        val g1 = hex(0x4f9a3c); val g2 = hex(0x6cc04a); val g3 = hex(0x5fb548); val dark = hex(0x2f6b22)

        fun leg(t: Int, s: Int, col: Color, w: Float) = SoftItem(
            SoftPath.Builder(sk, t)
                .moveTo(0f, 0f, intArrayOf(t))
                .lineTo(1f, 0f, intArrayOf(t, s), HALF)
                .lineTo(2f, 0f, intArrayOf(s))
                .build(col, w, aa, outlineFill = b.outlineFill)
        )

        // sit pose
        b.ellipse(-8f, -10f, 15f, 10f, g1)
        b.path("M-18 -4 Q-22 -30 0 -38 Q20 -42 26 -26 Q28 -10 14 -2Z", g2)
        b.ellipse(11f, -16f, 9f, 10f, hex(0xd8f0a8))
        b.ellipse(-6f, -9f, 15f, 9f, g3)
        b.ellipse(8f, -1.5f, 11f, 3.5f, g1)
        b.stroke("M14 -16 Q18 -8 20 -2", g1, 5f)
        val sitA = Part(F_INNER, b.build())
        b.ellipse(0f, 0f, 6f, 5f, hex(0xfff1b0))
        val sac = Part(F_SAC, b.build())
        b.ellipse(13f, -34f, 15f, 10f, g2)
        b.circle(6f, -44f, 7f, g2); b.circle(19f, -44f, 7f, g2)
        b.circle(6f, -45f, 4.6f, C.white); b.circle(19f, -45f, 4.6f, C.white)
        b.circle(8f, -45f, 2.5f, C.eye); b.circle(21f, -45f, 2.5f, C.eye)
        b.stroke("M11 -31 Q20 -26 28 -32", dark, 2.2f)
        b.ellipse(9f, -31f, 3.5f, 2f, C.cheek, .7f)
        val sitB = Part(F_INNER, b.build())

        // swim pose
        val legFar = leg(F_FAR_T, F_FAR_S, g1, 6f)
        b.ellipse(0f, 0f, 6f, 3.5f, g1)
        val footFar = Part(F_FAR_FOOT, b.build())
        b.ellipse(-2f, 2f, 19f, 9f, g3)
        b.circle(-8f, -3f, 3f, g1); b.circle(2f, -5f, 2.4f, g1)
        b.ellipse(14f, 0f, 11f, 8f, g3)
        b.circle(10f, -8f, 5f, g3); b.circle(10f, -8.5f, 3.4f, C.white); b.circle(11.2f, -8.5f, 1.8f, C.eye)
        b.circle(17f, -7f, 5.5f, g2); b.circle(17f, -7.5f, 3.8f, C.white); b.circle(18.5f, -7.5f, 2f, C.eye)
        b.stroke("M17 3 Q23 4 25 0", dark, 1.8f)
        val swimBody = Part(F_INNER, b.build())
        val legNear = leg(F_NEAR_T, F_NEAR_S, g2, 7f)
        b.ellipse(0f, 0f, 7f, 4f, g3)
        val footNear = Part(F_NEAR_FOOT, b.build())
        b.stroke("M8 6 L13 12", g2, 4f)
        val arm = Part(F_INNER, b.build())

        val sit = arrayOf<DrawItem>(sitA, sac, sitB)
        val swim = arrayOf<DrawItem>(legFar, footFar, swimBody, legNear, footNear, arm)
        return FrogRig(Rig(sk, sit + swim), sit, swim)
    }

    // ---------------------------------------------------------------- boy (Ben)

    const val B_ROOT = 0
    const val B_SHADOW = 1
    const val B_THIGH_F = 2
    const val B_SHIN_F = 3
    const val B_SHOE_F = 4
    const val B_THIGH_N = 5
    const val B_SHIN_N = 6
    const val B_SHOE_N = 7
    const val B_TORSO = 8
    const val B_UARM_F = 9
    const val B_FARM_F = 10
    const val B_HAND_F = 11
    const val B_HEAD = 12
    const val B_HEAD_FRONT = 13
    const val B_FACE_FRONT = 14
    const val B_TUFT = 15
    const val B_HAT = 16
    const val B_UARM_N = 17
    const val B_FARM_N = 18
    const val B_HAND_N = 19
    const val BOY_BONES = 20

    const val LEG1 = 32f
    const val LEG2 = 32f
    const val ARM1 = 25f
    const val ARM2 = 24f

    class BoyRig(
        val rig: Rig,
        val headSide: Array<DrawItem>,
        val headFront: Array<DrawItem>,
        val eyeOpen: DrawItem, val eyeBlink: DrawItem, val eyeHappy: DrawItem,
        val mouthSmile: DrawItem, val mouthOpen: DrawItem,
        val hat: DrawItem,
    )

    fun boyRig(b: ArtBuilder, aa: Boolean, cache: PathCache, hatShapes: Array<Shape>): BoyRig {
        val sk = Skeleton(BOY_BONES)
        for (i in 1 until BOY_BONES) sk.parent[i] = B_ROOT
        for (i in intArrayOf(B_UARM_F, B_FARM_F, B_HAND_F, B_HEAD, B_UARM_N, B_FARM_N, B_HAND_N)) sk.parent[i] = B_TORSO
        sk.parent[B_HEAD_FRONT] = B_HEAD; sk.parent[B_TUFT] = B_HEAD; sk.parent[B_HAT] = B_HEAD
        sk.parent[B_FACE_FRONT] = B_HEAD_FRONT
        // bind pose: limb bones laid along +x
        sk.set(B_SHIN_F, LEG1, 0f); sk.set(B_SHIN_N, LEG1, 0f)
        sk.set(B_FARM_F, ARM1, 0f); sk.set(B_FARM_N, ARM1, 0f)
        sk.captureBind()

        /** reference/lake.html hose(): S -> p1 (55%) -> Q(knee) -> p2 (45%) -> W, as a skinned path. */
        fun hose(upper: Int, lower: Int, l1: Float, l2: Float, col: Color, w: Float) = SoftItem(
            SoftPath.Builder(sk, upper)
                .moveTo(0f, 0f, intArrayOf(upper))
                .lineTo(l1 * .55f, 0f, intArrayOf(upper))
                .quadTo(l1, 0f, intArrayOf(upper, lower), HALF, l1 + l2 * .45f, 0f, intArrayOf(lower))
                .lineTo(l1 + l2, 0f, intArrayOf(lower))
                .build(col, w, aa, outlineFill = b.outlineFill)
        )

        val items = ArrayList<DrawItem>()
        b.ellipse(0f, 0f, 30f, 6f, hex(0x2f6b2a), .16f)
        items += Part(B_SHADOW, b.build())
        items += hose(B_THIGH_F, B_SHIN_F, LEG1, LEG2, C.skin2, 15f)
        b.ellipse(0f, 0f, 14f, 8f, C.shoe2); items += Part(B_SHOE_F, b.build())
        items += hose(B_THIGH_N, B_SHIN_N, LEG1, LEG2, C.skin, 15f)
        b.ellipse(0f, 0f, 14f, 8f, C.shoe); items += Part(B_SHOE_N, b.build())
        // torso group
        items += hose(B_UARM_F, B_FARM_F, ARM1, ARM2, C.skin2, 12f)
        b.circle(0f, 0f, 6.5f, C.skin2); items += Part(B_HAND_F, b.build())
        b.circle(4f, -54f, 9.5f, C.shirt2)
        b.path("M-18 -12 L20 -12 Q24 4 20 10 L-16 10 Q-22 2 -18 -12Z", C.shorts)
        b.path(SHIRT, C.shirt)
        items += Part(B_TORSO, b.build())
        b.rect(-26f, -32f, 56f, 8f, C.white, .85f)
        items += Part(B_TORSO, b.build(), clip = cache.get(SHIRT))
        // head, side view
        val side = ArrayList<DrawItem>()
        b.circle(4f, -40f, 42f, C.skin)
        b.path("M38 -62 Q30 -86 0 -84 Q-38 -82 -40 -44 Q-41 -26 -30 -14 Q-26 -30 -22 -40 Q-16 -36 -10 -44 Q-2 -54 6 -56 Q18 -52 26 -58 Q32 -54 38 -62Z", C.hair)
        side += Part(B_HEAD, b.build())
        b.group(tx = -2f, ty = 83f) { stroke("M2 -83 q-4 -14 9 -16", C.hair, 5f) }
        side += Part(B_TUFT, b.build())
        b.circle(-6f, -36f, 9f, C.skin); b.circle(-6f, -36f, 4.5f, C.skin2)
        b.circle(45f, -35f, 6.5f, C.skin)
        b.stroke("M20 -58 Q26 -61 32 -58", C.hair, 3f)
        side += Part(B_HEAD, b.build())
        b.ellipse(27f, -46f, 4.8f, 6.8f, C.eye); b.circle(28.6f, -48.6f, 1.7f, C.white)
        val eyeOpen = Part(B_HEAD, b.build()); side += eyeOpen
        b.stroke("M22 -46 L32 -46", C.eye, 3f)
        val eyeBlink = Part(B_HEAD, b.build()); side += eyeBlink
        b.stroke("M22 -44 Q27 -52 32 -44", C.eye, 3.4f)
        val eyeHappy = Part(B_HEAD, b.build()); side += eyeHappy
        b.ellipse(24f, -28f, 7f, 4.5f, C.cheek, .6f)
        side += Part(B_HEAD, b.build())
        b.stroke("M30 -20 Q36 -14 42 -21", C.line, 3f)
        val mouthSmile = Part(B_HEAD, b.build()); side += mouthSmile
        b.ellipse(36f, -18f, 4.6f, 5.4f, C.mouth); b.ellipse(36f, -15.5f, 2.6f, 1.8f, hex(0xff7d8f))
        val mouthOpen = Part(B_HEAD, b.build()); side += mouthOpen
        items += side
        // head, front view (shown while turning)
        val front = ArrayList<DrawItem>()
        b.circle(-42f, -36f, 9f, C.skin); b.circle(42f, -36f, 9f, C.skin)
        b.circle(0f, -40f, 42f, C.skin)
        b.path("M-42 -38 Q-44 -84 0 -84 Q44 -84 42 -38 Q36 -62 18 -60 Q4 -66 -10 -58 Q-28 -64 -42 -38Z", C.hair)
        front += Part(B_HEAD_FRONT, b.build())
        b.ellipse(-14f, -42f, 5f, 7f, C.eye); b.ellipse(14f, -42f, 5f, 7f, C.eye)
        b.ellipse(0f, -32f, 3.5f, 2.5f, C.skin2)
        b.ellipse(-24f, -28f, 7f, 4.5f, C.cheek, .6f); b.ellipse(24f, -28f, 7f, 4.5f, C.cheek, .6f)
        b.stroke("M-8 -20 Q0 -13 8 -20", C.line, 3f)
        front += Part(B_FACE_FRONT, b.build())
        items += front
        val hat = Part(B_HAT, hatShapes)
        items += hat
        items += hose(B_UARM_N, B_FARM_N, ARM1, ARM2, C.skin, 12f)
        b.circle(0f, 0f, 7f, C.skin); items += Part(B_HAND_N, b.build())
        b.circle(4f, -54f, 10f, C.shirt); items += Part(B_TORSO, b.build())

        return BoyRig(
            Rig(sk, items.toTypedArray()), side.toTypedArray(), front.toTypedArray(),
            eyeOpen, eyeBlink, eyeHappy, mouthSmile, mouthOpen, hat,
        )
    }

    fun lakeClip(cache: PathCache): Path = cache.get(LAKE)

    /**
     * `--es waterclip rect`: the lake clip as a rectangle. The shoreline curve runs between y 309.5 (crest) and 314
     * (ends); y 312 leaves at most ~1 unit of reflection off or over the bank next to the 3-unit shoreline stroke.
     */
    val LAKE_CLIP_RECT = floatArrayOf(360f, 312f, 1140f, 650f)
}
