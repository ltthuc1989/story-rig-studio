package com.example.storyrig.tools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
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

/**
 * Turnaround mannequin sheet (21:9) for one Gemini call that draws every view of a character at once: front, 3/4,
 * side, 3/4 back and back, turning towards the viewer's right like rig_3q_right.png. The figures are one simple 3D
 * body (same sizes as [drawMannequin], units of the 1024 px templates) seen from yaw 0, 45, 90, 135 and 180 degrees,
 * so every view has the same height, ground line and proportions. Arms in the 45 degree A-pose; seen from the side
 * that would hide the arm over the torso, so there the arms swing [SIDE_ARM_FORWARD] degrees forward instead.
 * Near limbs light, far limbs dark (as the templates). Writes the figures' boxes next to it as <out>.csv for the
 * splitter.
 *   mannequin sheet <out.png>
 */
internal fun drawMannequinSheet(out: File) {
    val w = 2520; val h = 1080
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(255, 0, 255); g.fillRect(0, 0, w, h)
    // Units to px: the figure (head top 110 to soles 940) takes 62% of the height, soles on one ground line.
    val k = h * .62 / 830.0
    val top = (h - 830 * k) / 2 - 110 * k
    val widths = SHEET_YAWS.map { halfWidth(it) * 2 }
    val gap = (w / k - widths.sum()) / (SHEET_YAWS.size + 1)
    var x = gap
    val rows = ArrayList<String>()
    rows += "# view,yaw,x0,y0,x1,y1 (px of this sheet: the box each figure is drawn in)"
    for ((i, yaw) in SHEET_YAWS.withIndex()) {
        val cx = x + widths[i] / 2
        drawFigure(g, yaw, cx * k, top, k)
        rows += "%s,%d,%d,%d,%d,%d".format(
            SHEET_VIEWS[i], yaw, (x * k).toInt(), (top + 90 * k).toInt(), ((x + widths[i]) * k).toInt(), (top + 960 * k).toInt(),
        )
        x += widths[i] + gap
    }
    out.parentFile?.mkdirs()
    ImageIO.write(img, "png", out)
    File(out.path.removeSuffix(".png") + ".csv").writeText(rows.joinToString("\n") + "\n")
    println("wrote $out")
}

internal val SHEET_YAWS = listOf(0, 45, 90, 135, 180)
internal val SHEET_VIEWS = listOf("front", "3q", "side", "3q_back", "back")
private const val SIDE_ARM_FORWARD = 25.0

/** A body point (x right in the front view, y down, z towards the viewer) seen from [yaw]: screen x offset, depth. */
private class View(yaw: Int) {
    val c = cos(Math.toRadians(yaw.toDouble())); val s = sin(Math.toRadians(yaw.toDouble()))
    fun x(px: Double, pz: Double) = px * c + pz * s
    fun z(px: Double, pz: Double) = -px * s + pz * c
}

/** Arm end (shoulder at (sx, 420, 0)) for one side: A-pose, or swung forward in the side view. */
private fun armEnd(side: Int, yaw: Int, len: Double): DoubleArray = if (yaw == 90) {
    val f = Math.toRadians(SIDE_ARM_FORWARD)
    doubleArrayOf(side * 82.0 + side * 10, 420 + len * cos(f), len * sin(f))
} else {
    val a = Math.toRadians(45.0)
    doubleArrayOf(side * (82 + len * cos(a)), 420 + len * sin(a), 0.0)
}

private fun halfWidth(yaw: Int): Double {
    val v = View(yaw)
    var m = 125.0
    for (side in listOf(-1, 1)) {
        val e = armEnd(side, yaw, 286.0)
        m = maxOf(m, abs(v.x(e[0], e[2])) + 40, abs(v.x(side * 82.0, 0.0)) + 40)
    }
    return m + 10
}

private fun drawFigure(g: Graphics2D, yaw: Int, cx: Double, top: Double, k: Double) {
    val v = View(yaw)
    fun X(px: Double, pz: Double) = cx + v.x(px, pz) * k
    fun Y(py: Double) = top + py * k
    val body = Color(165, 165, 165); val near = Color(190, 190, 190); val far = Color(120, 120, 120)
    // Limbs on the side away from the viewer first (dark), then the torso, then the near limbs (light) over it.
    val sides = listOf(-1, 1).sortedBy { v.z(it * 82.0, 0.0) }
    fun leg(side: Int, c: Color) {
        val hx = side * 42.0; val ax = side * 57.0
        limb(g, c, X(hx, 0.0), Y(640.0), X(ax, 0.0), Y(900.0), 31 * k)
        // Foot points forward (+z): long seen from the side, short from the front.
        val fx = X(ax + side * 9, 22.0)
        val fw = (44 * abs(v.c) + 84 * abs(v.s)) * k
        g.color = c; g.fill(Ellipse2D.Double(fx - fw / 2, Y(918.0) - 22 * k, fw, 44 * k))
    }
    fun arm(side: Int, c: Color) {
        val e = armEnd(side, yaw, 265.0); val hnd = armEnd(side, yaw, 286.0)
        val sx = if (yaw == 90) side * 92.0 else side * 82.0
        limb(g, c, X(sx, 0.0), Y(420.0), X(e[0], e[2]), Y(e[1]), 31 * k)
        g.color = c; g.fill(Ellipse2D.Double(X(hnd[0], hnd[2]) - 35 * k, Y(hnd[1]) - 35 * k, 70 * k, 70 * k))
    }
    val farSide = sides[0]; val nearSide = sides[1]
    val symmetric = abs(v.s) < 1e-6
    leg(farSide, if (symmetric) near else far)
    arm(farSide, if (symmetric) near else far)
    if (symmetric) leg(nearSide, near)
    // Torso and pelvis: boxes 200 x 120 and 180 x 110 (width x depth) seen from the yaw.
    val tw = 200 * abs(v.c) + 120 * abs(v.s); val pw = 180 * abs(v.c) + 110 * abs(v.s)
    g.color = body
    g.fill(RoundRectangle2D.Double(cx - tw / 2 * k, Y(385.0), tw * k, 250 * k, 70 * k, 70 * k))
    g.fill(RoundRectangle2D.Double(cx - pw / 2 * k, Y(600.0), pw * k, 90 * k, 50 * k, 50 * k))
    g.fill(RoundRectangle2D.Double(cx - 30 * k, Y(350.0), 60 * k, 60 * k, 20 * k, 20 * k))
    if (v.c < -.1) {
        // Back seen: a centre line down the back of the torso.
        g.color = body.darker(); g.stroke = BasicStroke((6 * k).toFloat())
        val bx = X(0.0, -60.0)
        g.draw(Line2D.Double(bx, Y(420.0), bx, Y(600.0)))
    }
    if (!symmetric) leg(nearSide, near)
    // Head: ears behind it where they face away, the head, then the face features that face the viewer.
    for (side in listOf(-1, 1)) if (v.z(side * 122.0, 0.0) < -1) ear(g, body.darker(), X(side * 122.0, 0.0), Y(237.0), k)
    g.color = body
    g.fill(Ellipse2D.Double(cx - 125 * k, Y(110.0), 250 * k, 250 * k))
    for (side in listOf(-1, 1)) if (v.z(side * 122.0, 0.0) >= -1) ear(g, body.darker(), X(side * 122.0, 0.0), Y(237.0), k)
    val ez = sqrt(125.0 * 125 - 42.0 * 42 - 12.0 * 12)
    for (side in listOf(-1, 1)) {
        val d = v.z(side * 42.0, ez)
        if (d < 15) continue
        val ew = 22 * (d / ez).coerceIn(.35, 1.0)
        g.color = Color(140, 140, 140)
        g.fill(Ellipse2D.Double(X(side * 42.0, ez) - ew / 2 * k, Y(215.0), ew * k, 30 * k))
    }
    // Nose: a bump on the face's centre line, shows the facing in the turned views.
    if (v.z(0.0, 125.0) > 0 && !symmetric) {
        g.color = body
        g.fill(Ellipse2D.Double(X(0.0, 132.0) - 14 * k, Y(250.0), 28 * k, 26 * k))
    }
    if (!symmetric || v.c > 0) arm(nearSide, near) else arm(nearSide, near)
}

private fun ear(g: Graphics2D, c: Color, x: Double, y: Double, k: Double) {
    g.color = c; g.fill(Ellipse2D.Double(x - 20 * k, y - 27 * k, 40 * k, 55 * k))
}

private fun limb(g: Graphics2D, c: Color, x1: Double, y1: Double, x2: Double, y2: Double, r: Double) {
    g.color = c
    g.stroke = BasicStroke((2 * r).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(Line2D.Double(x1, y1, x2, y2))
}

/*
 * The two kit sheets (ART_PIPELINE.md section 2), 16:9, drawn at 2752 x 1536 (Gemini returns 5504 x 3072 at 4K, twice
 * this). One body unit (the 1024 px templates' units) is KIT_K px here, so a full figure (830 units) is about 1410 px
 * tall at 4K. Every slot is written to <out>.csv as name,x0,y0,x1,y1,scale (px of this sheet; scale: how many times
 * the body scale the slot is drawn, 2.4 for the face and hand pieces so they get more pixels; splitsheet scales them back).
 *   mannequin kit <dir>   writes <dir>/rig_body.png and <dir>/rig_face_hands.png with their .csv
 */
internal fun drawKitSheets(dir: File) {
    drawBodySheet(File(dir, "rig_body.png"))
    drawFaceHandsSheet(File(dir, "rig_face_hands.png"))
}

private const val KIT_W = 2752
private const val KIT_H = 1536
private const val KIT_K = .85

private val GREY = Color(165, 165, 165)
private val LIGHT = Color(190, 190, 190)
private val DARK = Color(120, 120, 120)
private val INK = Color(95, 95, 95)

/** A piece on a kit sheet: [w] x [h] body units, [draw] puts it with its box's top-left at (x, y) px, k px per unit. */
private class Slot(val name: String, val w: Double, val h: Double, val scale: Double = 1.0, val draw: (Graphics2D, Double, Double, Double) -> Unit)

/** Lays [slots] out in one row from [y] px, spread evenly across the sheet; returns the csv rows and the row's height. */
private fun row(g: Graphics2D, slots: List<Slot>, y: Double, rows: MutableList<String>, groupGap: Map<Int, Double> = emptyMap()): Double {
    val widths = slots.map { it.w * KIT_K * it.scale }
    val extra = groupGap.values.sum()
    val gap = (KIT_W - widths.sum() - extra) / (slots.size + 1)
    var x = gap
    var hMax = 0.0
    for ((i, s) in slots.withIndex()) {
        x += groupGap[i] ?: 0.0
        val k = KIT_K * s.scale
        s.draw(g, x, y, k)
        rows += "%s,%d,%d,%d,%d,%s".format(s.name, x.toInt(), y.toInt(), (x + s.w * k).toInt(), (y + s.h * k).toInt(), s.scale.toString())
        hMax = maxOf(hMax, s.h * k)
        x += widths[i] + gap
    }
    return hMax
}

private fun disc(g: Graphics2D, c: Color, x: Double, y: Double, r: Double) { g.color = c; g.fill(Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r)) }
private fun box(g: Graphics2D, c: Color, x0: Double, y0: Double, x1: Double, y1: Double, round: Double) {
    g.color = c; g.fill(RoundRectangle2D.Double(x0, y0, x1 - x0, y1 - y0, round, round))
}

/**
 * Sheet 1, the body: row A the five views (as rig_sheet.png); row B the loose parts seen from the front and from the side;
 * row C the loose parts seen at three-quarter. Loose parts are drawn the way they hang under their neighbours: a torso
 * with round shoulders and the armpits and the shirt's sides under the arms, a neck reaching up under the chin; an arm
 * with a round head at the shoulder; a leg with a round head at the hip, reaching down inside the shoe; the pelvis
 * reaching up under the shirt's hem; a shoe on its own.
 */
private fun drawBodySheet(out: File) {
    val img = BufferedImage(KIT_W, KIT_H, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(255, 0, 255); g.fillRect(0, 0, KIT_W, KIT_H)
    val rows = ArrayList<String>()
    rows += "# name,x0,y0,x1,y1,scale (px of this 2752 x 1536 sheet; Gemini's 4K sheet is twice this)"
    val margin = 30.0; val gapY = 40.0
    // Row A: the five views, figure boxes from y 90 to 960 units like rig_sheet.csv.
    val views = SHEET_YAWS.mapIndexed { i, yaw ->
        Slot("view_${SHEET_VIEWS[i]}", halfWidth(yaw) * 2, 870.0) { gg, x, y, k -> drawFigure(gg, yaw, x + halfWidth(yaw) * k, y - 90 * k, k) }
    }
    var y = margin
    y += row(g, views, y, rows) + gapY
    // Row B: front parts, then side parts.
    val front = partSet("front", 0)
    val side = partSet("side", 90)
    y += row(g, front + side, y, rows, mapOf(front.size to 120.0)) + gapY
    // Row C: three-quarter parts (near and far arm both: the far one is seen from its other side).
    row(g, partSet("3q", 45), y, rows)
    g.dispose()
    out.parentFile?.mkdirs()
    ImageIO.write(img, "png", out)
    File(out.path.removeSuffix(".png") + ".csv").writeText(rows.joinToString("\n") + "\n")
    println("wrote $out")
}

/** Loose parts of one view, in body units: torso, pelvis, arm(s), leg, shoe. */
private fun partSet(view: String, yaw: Int): List<Slot> {
    val v = View(yaw)
    val tw = 200 * abs(v.c) + 120 * abs(v.s)
    val pw = 180 * abs(v.c) + 110 * abs(v.s)
    // Shoulder balls where the arms join, seen from the yaw (both on the centre line seen from the side).
    val ballX = listOf(-1, 1).map { v.x(it * 82.0, 0.0) }
    val halfT = maxOf(tw / 2, ballX.maxOf { abs(it) } + 40)
    val torso = Slot("${view}_torso", 2 * halfT, 310.0) { g, x, y, k ->
        val cx = x + halfT * k
        fun Y(u: Double) = y + (u - 325) * k
        // Neck reaching up under the chin; the torso one grey shape with round shoulders (the shoulder balls drawn in
        // the torso's own grey: a lighter disc there came back as a patch of skin).
        box(g, GREY, cx - 30 * k, Y(330.0), cx + 30 * k, Y(420.0), 20 * k)
        for (side in listOf(-1, 1)) disc(g, GREY, cx + v.x(side * 82.0, 0.0) * k, Y(425.0), 40 * k)
        box(g, GREY, cx - tw / 2 * k, Y(385.0), cx + tw / 2 * k, Y(635.0), 70 * k)
    }
    val pelvis = Slot("${view}_pelvis", pw, 110.0) { g, x, y, k ->
        // The top of the shorts only: the waistband (darker, reaching up under the shirt's hem), then the seat down to
        // just below the crotch, cut straight across; the legs of the shorts belong to the leg pieces. A plain box
        // came back as the whole shorts, legs and all.
        box(g, DARK, x, y, x + pw * k, y + 30 * k, 16 * k)
        g.color = GREY; g.fill(java.awt.geom.Rectangle2D.Double(x + 4 * k, y + 26 * k, (pw - 8) * k, 84 * k))
    }
    fun arm(name: String, c: Color) = Slot(name, 80.0, 360.0) { g, x, y, k ->
        val cx = x + 40 * k
        disc(g, c, cx, y + 40 * k, 40 * k)
        limb(g, c, cx, y + 40 * k, cx, y + 300 * k, 31 * k)
        disc(g, c, cx, y + 320 * k, 35 * k)
    }
    val leg = Slot("${view}_leg", 100.0, 360.0) { g, x, y, k ->
        val cx = x + 50 * k
        // The leg of the shorts: a wide round-topped block (unlike an arm's narrow sleeve), then the leg down past the
        // ankle (the part inside the shoe), cut flat.
        box(g, GREY, cx - 50 * k, y, cx + 50 * k, y + 120 * k, 90 * k)
        g.color = LIGHT; g.fill(java.awt.geom.Rectangle2D.Double(cx - 31 * k, y + 110 * k, 62 * k, 220 * k))
    }
    val fw = 44 * abs(v.c) + 84 * abs(v.s) + 16
    val shoe = Slot("${view}_shoe", fw, 50.0) { g, x, y, k ->
        g.color = LIGHT; g.fill(Ellipse2D.Double(x, y + 3 * k, fw * k, 44 * k))
    }
    val arms = if (yaw == 45) listOf(arm("3q_arm_near", LIGHT), arm("3q_arm_far", DARK)) else listOf(arm("${view}_arm", LIGHT))
    return listOf(torso, pelvis) + arms + listOf(leg, shoe)
}

/**
 * Sheet 2, face, hands, hair: row 1 the front eye (white and pupil apart), its states and the brows; row 2 the 12 front
 * mouths; row 3 the side eye, brow and the 12 side mouths; rows 4 and 5 the right hand's shapes; row 6 the head without
 * hair, the hair layers and the accessory. Face and hand pieces at 2.4 times the body scale, the head row at body scale.
 */
private fun drawFaceHandsSheet(out: File) {
    val img = BufferedImage(KIT_W, KIT_H, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(255, 0, 255); g.fillRect(0, 0, KIT_W, KIT_H)
    val rows = ArrayList<String>()
    rows += "# name,x0,y0,x1,y1,scale (px of this 2752 x 1536 sheet; Gemini's 4K sheet is twice this)"
    val s = 2.4
    var y = 40.0; val gapY = 46.0
    fun stroke(gg: Graphics2D, w: Double) { gg.stroke = BasicStroke(w.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) }
    fun arc(gg: Graphics2D, x: Double, yy: Double, w: Double, h: Double, k: Double, up: Boolean, t: Double = 5.0) {
        gg.color = INK; stroke(gg, t * k)
        gg.draw(java.awt.geom.Arc2D.Double(x + t * k, yy + (if (up) 0.0 else h * k / 2), w * k - 2 * t * k, h * k, if (up) 200.0 else 20.0, 140.0, java.awt.geom.Arc2D.OPEN))
    }
    fun brow(name: String, tilt: Double, lift: Double) = Slot(name, 56.0, 30.0, s) { gg, x, yy, k ->
        gg.color = INK; stroke(gg, 8 * k)
        val cy = yy + 18 * k - lift * k
        gg.draw(Line2D.Double(x + 6 * k, cy + tilt * k, x + 50 * k, cy - tilt * k))
    }
    // Row 1: eye white, pupil (iris and pupil, apart), half, closed, happy, wide; brows.
    val eyes = listOf(
        Slot("eye_white", 44.0, 34.0, s) { gg, x, yy, k -> g.color = LIGHT; gg.color = LIGHT; gg.fill(Ellipse2D.Double(x, yy, 44 * k, 34 * k)) },
        Slot("eye_pupil", 22.0, 22.0, s) { gg, x, yy, k -> disc(gg, INK, x + 11 * k, yy + 11 * k, 11 * k) },
        Slot("eye_half", 44.0, 34.0, s) { gg, x, yy, k ->
            gg.color = LIGHT; gg.fill(Ellipse2D.Double(x, yy, 44 * k, 34 * k))
            gg.color = GREY; gg.fill(java.awt.geom.Arc2D.Double(x, yy, 44 * k, 34 * k, 0.0, 180.0, java.awt.geom.Arc2D.CHORD))
        },
        Slot("eye_closed", 44.0, 20.0, s) { gg, x, yy, k -> gg.color = INK; stroke(gg, 5 * k); gg.draw(Line2D.Double(x + 4 * k, yy + 10 * k, x + 40 * k, yy + 10 * k)) },
        Slot("eye_happy", 44.0, 26.0, s) { gg, x, yy, k -> arc(gg, x, yy, 44.0, 26.0, k, up = true) },
        Slot("eye_wide", 50.0, 42.0, s) { gg, x, yy, k -> gg.color = LIGHT; gg.fill(Ellipse2D.Double(x, yy, 50 * k, 42 * k)) },
        brow("brow", 0.0, 0.0), brow("brow_raised", -3.0, 6.0), brow("brow_frown", 6.0, 0.0), brow("brow_sad", -6.0, 0.0),
    )
    y += row(g, eyes, y, rows) + gapY
    // Row 2: the 12 front mouths (phonics), grey placeholders of their shapes.
    fun mouth(name: String, w: Double, h: Double, kind: Int) = Slot(name, w, h, s) { gg, x, yy, k ->
        when (kind) {
            0 -> { gg.color = INK; stroke(gg, 6 * k); gg.draw(Line2D.Double(x + 4 * k, yy + h * k / 2, x + (w - 4) * k, yy + h * k / 2)) }
            1 -> arc(gg, x, yy, w, h, k, up = false, t = 6.0)
            2 -> arc(gg, x, yy, w, h, k, up = true, t = 6.0)
            else -> { gg.color = DARK; gg.fill(Ellipse2D.Double(x, yy, w * k, h * k)) }
        }
    }
    val mouths = listOf(
        mouth("mouth_rest_mbp", 56.0, 20.0, 0), mouth("mouth_a_i", 60.0, 38.0, 3), mouth("mouth_e", 62.0, 20.0, 3),
        mouth("mouth_o", 36.0, 36.0, 3), mouth("mouth_u_w", 24.0, 24.0, 3), mouth("mouth_f_v", 58.0, 22.0, 3),
        mouth("mouth_l_th", 52.0, 30.0, 3), mouth("mouth_s_t", 60.0, 18.0, 3), mouth("mouth_smile", 58.0, 26.0, 1),
        mouth("mouth_laugh", 64.0, 42.0, 3), mouth("mouth_sad", 50.0, 24.0, 2), mouth("mouth_surprised", 34.0, 46.0, 3),
    )
    y += row(g, mouths, y, rows) + gapY
    // Row 3: side face: eye open, closed, happy, wide, brow, then the 12 mouths in profile (about half as wide).
    val sideFace = listOf(
        Slot("side_eye_open", 24.0, 34.0, s) { gg, x, yy, k -> gg.color = LIGHT; gg.fill(Ellipse2D.Double(x, yy, 24 * k, 34 * k)) },
        Slot("side_eye_closed", 24.0, 16.0, s) { gg, x, yy, k -> gg.color = INK; stroke(gg, 5 * k); gg.draw(Line2D.Double(x + 3 * k, yy + 8 * k, x + 21 * k, yy + 8 * k)) },
        Slot("side_eye_happy", 24.0, 20.0, s) { gg, x, yy, k -> arc(gg, x, yy, 24.0, 20.0, k, up = true) },
        Slot("side_eye_wide", 28.0, 40.0, s) { gg, x, yy, k -> gg.color = LIGHT; gg.fill(Ellipse2D.Double(x, yy, 28 * k, 40 * k)) },
        Slot("side_brow", 30.0, 20.0, s) { gg, x, yy, k -> gg.color = INK; stroke(gg, 8 * k); gg.draw(Line2D.Double(x + 4 * k, yy + 12 * k, x + 26 * k, yy + 8 * k)) },
    ) + mouths.map { m -> Slot("side_" + m.name, maxOf(16.0, m.w / 2), m.h, s) { gg, x, yy, k -> m.draw(gg, x, yy, k * .5); } }
    y += row(g, sideFace, y, rows) + gapY
    // Rows 4 and 5: the right hand, front then side.
    fun hand(name: String, fingers: List<Double>, thumb: Double?, palmLight: Boolean = false, fist: Boolean = false, edge: Boolean = false) =
        Slot(name, 90.0, 110.0, s) { gg, x, yy, k ->
            val cx = x + 45 * k; val cy = yy + 72 * k
            val c = if (palmLight) LIGHT else GREY
            if (edge) { box(gg, c, cx - 12 * k, yy + 10 * k, cx + 12 * k, yy + 106 * k, 20 * k); return@Slot }
            box(gg, c, cx - 26 * k, cy - 26 * k, cx + 26 * k, cy + 32 * k, 26 * k)
            if (fist) { box(gg, c, cx - 28 * k, cy - 34 * k, cx + 28 * k, cy - 6 * k, 22 * k) }
            gg.color = c; stroke(gg, 13 * k)
            for ((i, a) in fingers.withIndex()) {
                val fx = cx + (-19 + i * 12.7) * k; val r = Math.toRadians(a)
                gg.draw(Line2D.Double(fx, cy - 20 * k, fx + sin(r) * 44 * k, cy - 20 * k - cos(r) * 44 * k))
            }
            if (thumb != null) { val r = Math.toRadians(thumb); gg.draw(Line2D.Double(cx + 22 * k, cy, cx + 22 * k + sin(r) * 34 * k, cy - cos(r) * 34 * k)) }
        }
    val handsFront = listOf(
        hand("hand_relaxed", listOf(-8.0, -3.0, 3.0, 8.0), 70.0),
        hand("hand_fist", emptyList(), 30.0, fist = true),
        hand("hand_point", listOf(0.0), null, fist = true),
        hand("hand_thumb_up", emptyList(), 0.0, fist = true),
        hand("hand_wave_palm", listOf(-14.0, -5.0, 5.0, 14.0), 60.0, palmLight = true),
        hand("hand_wave_back", listOf(-14.0, -5.0, 5.0, 14.0), 60.0),
        hand("hand_palm_up", listOf(70.0, 75.0, 80.0, 85.0), 30.0, palmLight = true),
        hand("hand_grip_c", listOf(40.0, 45.0, 50.0, 55.0), 100.0),
    )
    y += row(g, handsFront, y, rows) + gapY
    val handsMore = listOf(
        hand("hand_count_1", listOf(0.0), null, fist = true),
        hand("hand_count_2", listOf(-8.0, 8.0), null, fist = true),
        hand("hand_count_3", listOf(-10.0, 0.0, 10.0), null, fist = true),
        hand("hand_count_4", listOf(-12.0, -4.0, 4.0, 12.0), null),
        hand("hand_clap_edge", emptyList(), null, edge = true),
        hand("side_hand_relaxed", emptyList(), null, edge = true),
        hand("side_hand_fist", emptyList(), 20.0, fist = true),
    )
    y += row(g, handsMore, y, rows) + gapY
    // Row 6 at body scale: the head without hair, back hair, front hair (fringe), the accessory (a backpack).
    val heads = listOf(
        Slot("head_bald", 290.0, 260.0) { gg, x, yy, k ->
            disc(gg, GREY.darker(), x + 25 * k, yy + 130 * k, 22 * k); disc(gg, GREY.darker(), x + 265 * k, yy + 130 * k, 22 * k)
            disc(gg, GREY, x + 145 * k, yy + 130 * k, 125 * k)
        },
        Slot("hair_back", 280.0, 260.0) { gg, x, yy, k -> box(gg, DARK, x, yy, x + 280 * k, yy + 250 * k, 160 * k) },
        Slot("hair_front", 270.0, 120.0) { gg, x, yy, k ->
            gg.color = DARK; gg.fill(java.awt.geom.Arc2D.Double(x, yy, 270 * k, 220 * k, 0.0, 180.0, java.awt.geom.Arc2D.CHORD))
        },
        Slot("accessory", 200.0, 240.0) { gg, x, yy, k ->
            box(gg, GREY, x + 10 * k, yy + 30 * k, x + 190 * k, yy + 240 * k, 50 * k)
            gg.color = DARK; stroke(gg, 12 * k); gg.draw(java.awt.geom.Arc2D.Double(x + 60 * k, yy, 80 * k, 70 * k, 0.0, 180.0, java.awt.geom.Arc2D.OPEN))
        },
    )
    row(g, heads, y, rows)
    g.dispose()
    out.parentFile?.mkdirs()
    ImageIO.write(img, "png", out)
    File(out.path.removeSuffix(".png") + ".csv").writeText(rows.joinToString("\n") + "\n")
    println("wrote $out")
}
