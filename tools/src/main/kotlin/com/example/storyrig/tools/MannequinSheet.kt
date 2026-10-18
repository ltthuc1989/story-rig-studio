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
