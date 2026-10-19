package com.example.storyrig.tools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Templates of the minimal flow (ART_PIPELINE.md section 2): one grey mannequin per view, 1024 px square, in the
 * rig pose (arms raised to just under the shoulder line, legs apart) so the armpits, the sides of the top and the inner
 * legs are all visible and nothing big has to be invented later; and one small face sheet (eyes, brows, mouths, hands).
 * Every joint of a view mannequin goes to its .csv (name,x,y) in the names AutoSpec.kt reads; every slot of the face
 * sheet to its .csv (name,x0,y0,x1,y1,scale).
 *   rigtemplate <dir>        writes rig_front.png/.csv, rig_3q.png/.csv, rig_face.png/.csv, rig_limbs.png/.csv
 */
internal fun drawRigTemplates(dir: File) {
    drawRigView(0, File(dir, "rig_front.png"))
    drawRigView(45, File(dir, "rig_3q.png"))
    drawFaceTemplate(File(dir, "rig_face.png"))
    drawLimbTemplate(File(dir, "rig_limbs.png"))
}

/** Arms this far below horizontal: the armpit stays open, the pose still reads as natural. */
internal const val RIG_ARM_DEG = 15.0
/** Each leg this far from vertical. */
internal const val RIG_LEG_DEG = 10.0

private const val MAG = 0xFF00FF

/**
 * One view of the mannequin, same body as Mannequin.kt (child proportions, head r125 at (512,235), shoulders at
 * (±82,420), limbs r31). [yaw] 0 = front, 45 = three-quarter turned towards the right of the image. Near side =
 * viewer's left, as in the old templates.
 */
internal fun drawRigView(yaw: Int, out: File, armDeg: Double = RIG_ARM_DEG, legDeg: Double = RIG_LEG_DEG) {
    val s = 1024
    val img = BufferedImage(s, s, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(MAG); g.fillRect(0, 0, s, s)
    val body = Color(165, 165, 165); val near = Color(190, 190, 190); val far = Color(150, 150, 150)
    val cx = 512.0
    val c = cos(Math.toRadians(yaw.toDouble())); val sn = sin(Math.toRadians(yaw.toDouble()))
    fun X(px: Double, pz: Double = 0.0) = cx + (px * c + pz * sn)
    fun depth(px: Double, pz: Double = 0.0) = -px * sn + pz * c
    val ar = Math.toRadians(armDeg); val lr = Math.toRadians(legDeg)
    val pts = LinkedHashMap<String, DoubleArray>()
    fun pt(n: String, x: Double, y: Double) { pts[n] = doubleArrayOf(x, y) }
    val symmetric = yaw == 0
    val sides = listOf(-1, 1).sortedBy { depth(it * 82.0) }
    val farSide = sides[0]; val nearSide = sides[1]
    fun name(side: Int) = if (side == -1) "near" else "far"

    // Leg: hip (±42,640) to the ankle, legDeg from vertical; foot ellipse (long seen from the side).
    fun ankleX(side: Int) = side * (42 + 260 * tan(lr))
    fun leg(side: Int, col: Color) {
        val hx = side * 42.0; val ax = ankleX(side)
        limb(g, col, X(hx), 640.0, X(ax), 900.0, 31.0)
        val fw = 44 * abs(c) + 84 * abs(sn)
        val fx = X(ax + side * 9, 22.0)
        g.color = col; g.fill(Ellipse2D.Double(fx - fw / 2, 918.0 - 22, fw, 44.0))
        val n = name(side)
        val hipX = hx + (ax - hx) * (690 - 640) / 260
        pt("hip_$n", X(hipX), 690.0)
        pt("ankle_$n", X(ax), 900.0)
        pt("foot_$n", fx, 918.0)
    }
    // Arm: shoulder (±82,420), 265 long at armDeg below horizontal, hand circle r35 at 286.
    fun arm(side: Int, col: Color) {
        val ex = side * (82 + 245 * cos(ar)); val ey = 420 + 245 * sin(ar)
        val hx = side * (82 + 286 * cos(ar)); val hy = 420 + 286 * sin(ar)
        limb(g, col, X(side * 82.0), 420.0, X(ex), ey, 31.0)
        // Hand: a plain circle (fingers drawn on the mannequin came back as a deformed hand: Gemini copies the shape).
        g.color = col; g.fill(Ellipse2D.Double(X(hx) - 35, hy - 35, 70.0, 70.0))
        val n = name(side)
        // Where the arm's axis leaves the torso side (screen half width tw/2), and where its lower edge does: the
        // arm runs cos(ar)*|c| screen px sideways and sin(ar) down per unit of its length.
        val half = (200 * abs(c) + 120 * abs(sn)) / 2
        fun axisY(screenDx: Double) = 420 + (screenDx - 82 * abs(c)) / (cos(ar) * abs(c)) * sin(ar)
        pt("shoulder_$n", cx + side * half, axisY(half))
        pt("armpit_$n", cx + side * half, axisY(half) + 31 / cos(ar))
        pt("hand_$n", X(hx), hy)
        // Background under the arm, 45 px out from the torso side and 45 under the arm's lower edge there.
        pt("gap_arm_$n", cx + side * (half + 45), axisY(half + 45) + 31 / cos(ar) + 45)
    }
    leg(farSide, if (symmetric) near else far)
    arm(farSide, if (symmetric) near else far)
    if (symmetric) leg(nearSide, near)
    // Torso 200 x 120 and pelvis 180 x 110 (width x depth) seen from the yaw; neck stub.
    val tw = 200 * abs(c) + 120 * abs(sn); val pw = 180 * abs(c) + 110 * abs(sn)
    g.color = body
    g.fill(RoundRectangle2D.Double(cx - tw / 2, 385.0, tw, 250.0, 70.0, 70.0))
    g.fill(RoundRectangle2D.Double(cx - pw / 2, 600.0, pw, 90.0, 50.0, 50.0))
    g.fill(RoundRectangle2D.Double(cx - 30, 350.0, 60.0, 60.0, 20.0, 20.0))
    if (!symmetric) leg(nearSide, near)
    // Ears behind the head where they face away, the head, the ears in front, eyes and nose that face the viewer.
    for (side in listOf(-1, 1)) if (depth(side * 122.0) < -1) ear(g, body.darker(), X(side * 122.0), 237.0)
    g.color = body
    g.fill(Ellipse2D.Double(cx - 125, 110.0, 250.0, 250.0))
    for (side in listOf(-1, 1)) if (depth(side * 122.0) >= -1) ear(g, body.darker(), X(side * 122.0), 237.0)
    val ez = sqrt(125.0 * 125 - 42.0 * 42 - 12.0 * 12)
    for (side in listOf(-1, 1)) {
        val d = depth(side * 42.0, ez)
        if (d < 15) continue
        // Big cartoon eyes (about a fifth of the head's width): the eye size is copied from the mannequin too.
        val ew = 48 * (d / ez).coerceIn(.35, 1.0)
        g.color = Color(140, 140, 140)
        g.fill(Ellipse2D.Double(X(side * 44.0, ez) - ew / 2, 205.0, ew, 56.0))
    }
    if (!symmetric) { g.color = body; g.fill(Ellipse2D.Double(X(0.0, 132.0) - 14, 250.0, 28.0, 26.0)) }
    arm(nearSide, near)

    pt("head_centre", cx, 235.0)
    pt("neck_top", cx, 360.0)
    pt("neck_bottom", cx, 385.0)
    pt("waist", cx, 620.0)
    pt("root", cx, 655.0)
    pt("crotch", cx, 690.0)
    pt("gap_legs", cx, 800.0)
    out.parentFile?.mkdirs()
    ImageIO.write(img, "png", out)
    val csv = File(out.path.removeSuffix(".png") + ".csv")
    val order = listOf(
        "head_centre", "neck_top", "neck_bottom", "shoulder_near", "armpit_near", "hand_near", "shoulder_far", "armpit_far", "hand_far",
        "waist", "root", "hip_near", "hip_far", "crotch", "ankle_near", "foot_near", "ankle_far", "foot_far", "gap_arm_near", "gap_arm_far", "gap_legs",
    )
    csv.writeText(buildString {
        append("# Joints of the mannequin in ${out.name} (1024x1024 px, magenta background), from tools Templates.kt: yaw $yaw, arms $armDeg degrees below horizontal, legs $legDeg degrees from vertical.\n")
        append("# near = viewer's left, far = viewer's right. shoulder = arm axis where it crosses the torso side; armpit = arm's lower edge meets the torso side; gap_* = background seeds.\n")
        append("# name,x,y\n")
        for (n in order) { val p = pts.getValue(n); append("$n,${"%.1f".format(p[0])},${"%.1f".format(p[1])}\n") }
    })
    println("wrote $out, ${csv.name}")
}

private fun ear(g: Graphics2D, c: Color, x: Double, y: Double) { g.color = c; g.fill(Ellipse2D.Double(x - 20, y - 27, 40.0, 55.0)) }

private fun limb(g: Graphics2D, c: Color, x1: Double, y1: Double, x2: Double, y2: Double, r: Double) {
    g.color = c
    g.stroke = BasicStroke((2 * r).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(Line2D.Double(x1, y1, x2, y2))
}

/** Gemini's 16:9 web image size. */
internal const val FACE_W = 1376
internal const val FACE_H = 768
/** Face and hand pieces are drawn this many times their size on the 1024 views; the splitter scales them back. */
internal const val FACE_SCALE = 2.0

private class FaceSlot(val name: String, val w: Double, val h: Double, val draw: (Graphics2D, Double, Double) -> Unit)

/**
 * The face sheet: row 1 the front eye (open, closed, happy, wide) and brows (neutral, raised, frown), row 2 nine mouths
 * (M, AI, E, O, U, FV, smile, sad, laugh), row 3 six hands (relaxed, fist, point, open palm, thumb up, grip), three fingers and a thumb.
 * Sizes are body units times FACE_SCALE (eye 36 x 26, mouth about 45 wide, hand 70 tall with a 24 px wrist stub).
 */
internal fun drawFaceTemplate(out: File) {
    val img = BufferedImage(FACE_W, FACE_H, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(MAG); g.fillRect(0, 0, FACE_W, FACE_H)
    val k = FACE_SCALE
    val grey = Color(165, 165, 165); val dark = Color(110, 110, 110); val light = Color(205, 205, 205)
    fun stroke(w: Double) { g.stroke = BasicStroke(w.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) }
    fun eye(name: String, kind: Int) = FaceSlot(name, 36 * k, 26 * k) { gg, x, y ->
        when (kind) {
            0 -> { gg.color = light; gg.fill(Ellipse2D.Double(x, y, 36 * k, 26 * k)); gg.color = dark; gg.fill(Ellipse2D.Double(x + 11 * k, y + 6 * k, 14 * k, 14 * k)) }
            1 -> { gg.color = dark; stroke(3 * k); gg.draw(Arc2D.Double(x, y + 4 * k, 36 * k, 18 * k, 200.0, 140.0, Arc2D.OPEN)) }
            2 -> { gg.color = dark; stroke(3 * k); gg.draw(Arc2D.Double(x, y + 4 * k, 36 * k, 20 * k, 20.0, 140.0, Arc2D.OPEN)) }
            else -> { gg.color = light; gg.fill(Ellipse2D.Double(x, y - 4 * k, 36 * k, 34 * k)); gg.color = dark; gg.fill(Ellipse2D.Double(x + 12 * k, y + 8 * k, 12 * k, 12 * k)) }
        }
    }
    fun brow(name: String, tilt: Double, lift: Double) = FaceSlot(name, 40 * k, 20 * k) { gg, x, y ->
        gg.color = dark; stroke(5 * k)
        gg.draw(Line2D.Double(x + 2 * k, y + (10 + lift - tilt) * k, x + 38 * k, y + (10 + lift + tilt) * k))
    }
    fun mouth(name: String, w: Double, h: Double, kind: Int) = FaceSlot(name, w * k, h * k) { gg, x, y ->
        gg.color = grey
        when (kind) {
            0 -> { stroke(4 * k); gg.draw(Line2D.Double(x, y + h / 2 * k, x + w * k, y + h / 2 * k)) }
            1 -> gg.fill(Ellipse2D.Double(x, y, w * k, h * k))
            2 -> { gg.color = dark; stroke(4 * k); gg.draw(Arc2D.Double(x, y - h * k, w * k, 2 * h * k, 200.0, 140.0, Arc2D.OPEN)) }
            3 -> { gg.color = dark; stroke(4 * k); gg.draw(Arc2D.Double(x, y, w * k, 2 * h * k, 20.0, 140.0, Arc2D.OPEN)) }
            else -> gg.fill(RoundRectangle2D.Double(x, y, w * k, h * k, 10 * k, 10 * k))
        }
    }
    // Hands: cartoon hands, three fingers and a thumb (the classic cut-out convention Kai's design follows): a palm
    // block with fingers (a list of finger heights, 0 = folded) and a thumb; a wrist stub below.
    fun hand(name: String, fingers: List<Double>, thumb: Double?, fist: Boolean = false) = FaceSlot(name, 60 * k, 94 * k) { gg, x, y ->
        val px = x + 8 * k; val py = y + 28 * k
        gg.color = grey
        if (!fist) for ((i, f) in fingers.withIndex()) if (f > 0) {
            gg.fill(RoundRectangle2D.Double(px + (1.5 + i * 13) * k, py - f * k, 11 * k, (f + 10) * k, 9 * k, 9 * k))
        }
        gg.fill(RoundRectangle2D.Double(px, py, 40 * k, 40 * k, 12 * k, 12 * k))
        if (thumb != null) { stroke(9 * k); gg.draw(Line2D.Double(px + 40 * k, py + 20 * k, px + (40 + 10) * k, py + (20 - thumb) * k)) }
        if (fist) { gg.color = dark; stroke(1.5 * k); for (i in 1..2) gg.draw(Line2D.Double(px + (i * 13.5) * k, py + 4 * k, px + (i * 13.5) * k, py + 20 * k)) }
        gg.color = light
        gg.fill(RoundRectangle2D.Double(px + 10 * k, py + 40 * k, 20 * k, 24 * k, 6 * k, 6 * k))
    }
    val rows = listOf(
        listOf(eye("eye_open", 0), eye("eye_closed", 1), eye("eye_happy", 2), eye("eye_wide", 3), brow("brow", 0.0, 0.0), brow("brow_raised", -3.0, -4.0), brow("brow_frown", 4.0, 2.0)),
        listOf(
            mouth("mouth_mbp", 40.0, 10.0, 0), mouth("mouth_ai", 44.0, 34.0, 1), mouth("mouth_e", 50.0, 20.0, 4), mouth("mouth_o", 30.0, 32.0, 1),
            mouth("mouth_u", 22.0, 22.0, 1), mouth("mouth_fv", 44.0, 16.0, 4), mouth("mouth_smile", 44.0, 14.0, 2), mouth("mouth_sad", 36.0, 12.0, 3), mouth("mouth_laugh", 52.0, 40.0, 1),
        ),
        listOf(
            hand("hand_relaxed", listOf(22.0, 26.0, 20.0), 12.0), hand("hand_fist", listOf(), 8.0, fist = true), hand("hand_point", listOf(30.0, 0.0, 0.0), 6.0),
            hand("hand_open", listOf(26.0, 30.0, 24.0), 16.0), hand("hand_thumb_up", listOf(), 30.0, fist = true), hand("hand_grip", listOf(10.0, 12.0, 10.0), 14.0),
        ),
    )
    val lines = ArrayList<String>()
    val rowH = listOf(140.0, 200.0, 300.0)
    var y = 40.0
    for ((ri, row) in rows.withIndex()) {
        val gap = (FACE_W - 2 * 60 - row.sumOf { it.w }) / (row.size - 1)
        var x = 60.0
        val cy = y + rowH[ri] / 2
        for (s in row) {
            val sy = cy - s.h / 2
            s.draw(g, x, sy)
            lines += "${s.name},${x.toInt()},${sy.toInt()},${(x + s.w).toInt()},${(sy + s.h).toInt()},$k"
            x += s.w + gap
        }
        y += rowH[ri]
    }
    out.parentFile?.mkdirs()
    ImageIO.write(img, "png", out)
    File(out.path.removeSuffix(".png") + ".csv").writeText(
        "# Slots of ${out.name} ($FACE_W x $FACE_H px, Gemini's 16:9 web size): name,x0,y0,x1,y1,scale (scale: times the 1024 views' body scale).\n" + lines.joinToString("\n") + "\n"
    )
    println("wrote $out (${lines.size} slots)")
}

/**
 * The limb sheet (substitute drawings, ART_PIPELINE.md section 1): what a rigid cut cannot show by turning its parts,
 * drawn instead of squashed. All of the character's RIGHT side (the near side of the front view); the far side uses
 * them mirrored, and the three-quarter cut (turned about 20 degrees) the same pieces. Row 1: upper arm pointing at the
 * viewer (sleeve seen end on), forearm pointing at the viewer (open hand facing the viewer, forearm end behind the
 * wrist), the relaxed hand from the back. Row 2: thigh pointing at the viewer (sitting, seen from the front: shorts
 * leg end on, knee in its opening), the shoe with its sole to the viewer (foot kicked back). Same scale as the face
 * sheet. The .csv adds each piece's joints as fractions of its box: jx,jy where it hangs from its parent bone, cx,cy
 * where its child bone starts (elbow, knee; the joint itself when the child is hidden by the piece).
 */
internal fun drawLimbTemplate(out: File) {
    val img = BufferedImage(FACE_W, FACE_H, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(MAG); g.fillRect(0, 0, FACE_W, FACE_H)
    val k = FACE_SCALE
    val grey = Color(165, 165, 165); val dark = Color(120, 120, 120); val light = Color(205, 205, 205)
    class Limb(val name: String, val w: Double, val h: Double, val j: DoubleArray, val c: DoubleArray, val draw: (Double, Double) -> Unit)
    // Body units (the views' mannequin: limbs 62 wide, sleeve about 80), times k.
    val upper = Limb("upper_toward", 84.0, 84.0, doubleArrayOf(.5, .5), doubleArrayOf(.5, .55)) { x, y ->
        g.color = grey; g.fill(Ellipse2D.Double(x, y, 84 * k, 84 * k))
        g.color = light; g.fill(Ellipse2D.Double(x + 14 * k, y + 18 * k, 56 * k, 56 * k))
    }
    val fore = Limb("fore_toward", 76.0, 104.0, doubleArrayOf(.5, .82), doubleArrayOf(.5, .82)) { x, y ->
        g.color = dark; g.fill(Ellipse2D.Double(x + 10 * k, y + 56 * k, 56 * k, 48 * k))
        g.color = grey
        for ((i, f) in listOf(30.0, 34.0, 28.0).withIndex()) g.fill(RoundRectangle2D.Double(x + (14 + i * 15) * k, y + (34 - f) * k, 13 * k, (f + 10) * k, 10 * k, 10 * k))
        g.fill(RoundRectangle2D.Double(x + 12 * k, y + 30 * k, 48 * k, 44 * k, 14 * k, 14 * k))
        g.stroke = BasicStroke((10 * k).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(Line2D.Double(x + 60 * k, y + 56 * k, x + 70 * k, y + 38 * k))
    }
    val back = Limb("hand_back", 60.0, 94.0, doubleArrayOf(.5, .75), doubleArrayOf(.5, .75)) { x, y ->
        val px = x + 12 * k; val py = y + 28 * k
        g.color = grey
        for ((i, f) in listOf(20.0, 26.0, 22.0).withIndex()) g.fill(RoundRectangle2D.Double(px + (1.5 + i * 13) * k, py - f * k, 11 * k, (f + 10) * k, 9 * k, 9 * k))
        g.fill(RoundRectangle2D.Double(px, py, 40 * k, 40 * k, 12 * k, 12 * k))
        g.stroke = BasicStroke((9 * k).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(Line2D.Double(px, py + 20 * k, px - 10 * k, py + 8 * k))
        g.color = light; g.fill(RoundRectangle2D.Double(px + 10 * k, py + 40 * k, 20 * k, 24 * k, 6 * k, 6 * k))
    }
    val thigh = Limb("thigh_toward", 80.0, 84.0, doubleArrayOf(.5, .12), doubleArrayOf(.5, .66)) { x, y ->
        g.color = grey; g.fill(RoundRectangle2D.Double(x, y, 80 * k, 70 * k, 30 * k, 30 * k))
        g.color = dark; g.fill(Ellipse2D.Double(x + 8 * k, y + 30 * k, 64 * k, 36 * k))
        g.color = light; g.fill(Ellipse2D.Double(x + 12 * k, y + 28 * k, 56 * k, 56 * k))
    }
    val sole = Limb("shoe_sole", 56.0, 96.0, doubleArrayOf(.5, .12), doubleArrayOf(.5, .12)) { x, y ->
        g.color = light; g.fill(RoundRectangle2D.Double(x + 14 * k, y, 28 * k, 24 * k, 8 * k, 8 * k))
        g.color = grey; g.fill(RoundRectangle2D.Double(x, y + 14 * k, 56 * k, 82 * k, 50 * k, 50 * k))
        g.color = dark; g.fill(RoundRectangle2D.Double(x + 8 * k, y + 24 * k, 40 * k, 66 * k, 40 * k, 40 * k))
    }
    val rows = listOf(listOf(upper, fore, back), listOf(thigh, sole))
    val lines = ArrayList<String>()
    val rowH = listOf(FACE_H * .52, FACE_H * .48)
    var y = 0.0
    for ((ri, row) in rows.withIndex()) {
        val gap = (FACE_W - row.sumOf { it.w * k }) / (row.size + 1)
        var x = gap
        val cy = y + rowH[ri] / 2
        for (s in row) {
            val sw = s.w * k; val sh = s.h * k; val sy = cy - sh / 2
            s.draw(x, sy)
            lines += "${s.name},${x.toInt()},${sy.toInt()},${(x + sw).toInt()},${(sy + sh).toInt()},$k,${s.j[0]},${s.j[1]},${s.c[0]},${s.c[1]}"
            x += sw + gap
        }
        y += rowH[ri]
    }
    out.parentFile?.mkdirs()
    ImageIO.write(img, "png", out)
    File(out.path.removeSuffix(".png") + ".csv").writeText(
        "# Slots of ${out.name} ($FACE_W x $FACE_H px): name,x0,y0,x1,y1,scale,jx,jy,cx,cy (joint to the parent bone and start of the child bone, as fractions of the box).\n" +
            lines.joinToString("\n") + "\n"
    )
    println("wrote $out (${lines.size} slots)")
}
