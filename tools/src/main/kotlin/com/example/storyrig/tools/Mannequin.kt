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

/**
 * Grey pose mannequins on magenta that Gemini copies the pose and framing from (one per view). Same body sizes as
 * rig_3q_right.png (1024 px square, child proportions, arms ~30 degrees below horizontal, legs apart), so every view
 * of a character comes out at the same scale.
 *   mannequin front|back <out.png>
 */
internal fun drawMannequin(view: String, out: File) {
    val s = 1024
    val img = BufferedImage(s, s, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(255, 0, 255); g.fillRect(0, 0, s, s)
    val body = Color(165, 165, 165); val limb = Color(185, 185, 185)
    val cx = 512.0
    // Legs and arms first (behind the torso at the hips and shoulders), mirrored about the centre line.
    for (side in listOf(-1, 1)) {
        limb(g, limb, cx + side * 42, 640.0, cx + side * 57, 900.0, 31.0)
        foot(g, limb, cx + side * 66, 918.0, side)
        limb(g, limb, cx + side * 82, 420.0, cx + side * 307, 560.0, 31.0)
        g.color = limb; g.fill(Ellipse2D.Double(cx + side * 322 - 35, 570.0 - 35, 70.0, 70.0))
    }
    g.color = body
    g.fill(RoundRectangle2D.Double(cx - 100, 385.0, 200.0, 250.0, 70.0, 70.0))
    g.fill(RoundRectangle2D.Double(cx - 90, 600.0, 180.0, 90.0, 50.0, 50.0))
    g.fill(RoundRectangle2D.Double(cx - 30, 350.0, 60.0, 60.0, 20.0, 20.0))
    // Ears on both sides, then the head over them.
    g.color = body.darker()
    for (side in listOf(-1, 1)) g.fill(Ellipse2D.Double(cx + side * 122 - 20, 210.0, 40.0, 55.0))
    g.color = body
    g.fill(Ellipse2D.Double(cx - 125, 110.0, 250.0, 250.0))
    when (view) {
        "front" -> {
            // Eyes: the face looks at the viewer.
            g.color = Color(140, 140, 140)
            for (side in listOf(-1, 1)) g.fill(Ellipse2D.Double(cx + side * 42 - 11, 215.0, 22.0, 30.0))
        }
        "back" -> {
            // No face; a centre line down the back of the torso says we see the back.
            g.color = body.darker(); g.stroke = BasicStroke(6f)
            g.draw(Line2D.Double(cx, 420.0, cx, 600.0))
        }
        else -> error("view must be front or back")
    }
    out.parentFile?.mkdirs()
    ImageIO.write(img, "png", out)
    println("wrote $out")
}

private fun limb(g: Graphics2D, c: Color, x1: Double, y1: Double, x2: Double, y2: Double, r: Double) {
    g.color = c
    g.stroke = BasicStroke((2 * r).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(Line2D.Double(x1, y1, x2, y2))
}

private fun foot(g: Graphics2D, c: Color, x: Double, y: Double, side: Int) {
    g.color = c
    g.fill(Ellipse2D.Double(x - 30 + side * 6, y - 22, 60.0, 44.0))
}
