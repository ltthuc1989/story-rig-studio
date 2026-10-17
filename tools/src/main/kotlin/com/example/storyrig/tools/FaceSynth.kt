package com.example.storyrig.tools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Eye expressions made from the character's own open eye, for the ones no face sheet gave (no sheet yet, an
 * unusable one, or a view without sheets). They always match the eye's design, which suits simple eyes best:
 *  - half:  the top half covered by an eyelid (skin a shade darker) with a dark lid line
 *  - happy: a closed eye, an arch in the eye's darkest colour (what the app shows when blinking)
 *  - wide:  the open eye 15% bigger
 */
internal fun synthEyes(parts: HashMap<String, Part>, skin: Int) {
    for (side in listOf("near", "far")) {
        val open = parts["eye_${side}_open"]?.piece ?: continue
        val cx = open.ox + open.w / 2; val cy = open.oy + open.h / 2
        val dark = darkest(open.img)
        fun put(name: String, img: Img) {
            if (name in parts) return
            parts[name] = Part(name, "head", Piece(img, cx - img.w / 2, cy - img.h / 2))
            println("  $name: made from the open eye")
        }
        put("eye_${side}_half", half(open.img, skin, dark))
        put("eye_${side}_happy", happy(open.img, dark))
        put("eye_${side}_wide", scaleImg(open.img, 1.15f))
    }
}

private fun lum(c: Int) = r(c) * .299f + g(c) * .587f + b(c) * .114f

/** Median of the darkest fifth of the eye's opaque pixels. */
private fun darkest(img: Img): Int {
    val px = img.px.filter { a(it) > 200 }.sortedBy(::lum)
    if (px.isEmpty()) return argb(255, 40, 25, 20)
    val low = px.take(max(1, px.size / 5))
    return low[low.size / 2] or (0xFF shl 24)
}

private fun half(src: Img, skin: Int, dark: Int): Img {
    val rows = (0 until src.h).filter { y -> (0 until src.w).any { a(src[it, y]) > 128 } }
    if (rows.isEmpty()) return src
    val top = rows.first(); val bottom = rows.last()
    val lidY = top + (bottom - top) / 2
    val line = max(2, ((bottom - top) * .1f).roundToInt())
    val lid = argb(255, (r(skin) * .9f).roundToInt(), (g(skin) * .9f).roundToInt(), (b(skin) * .9f).roundToInt())
    val out = Img(src.w, src.h)
    for (y in 0 until src.h) for (x in 0 until src.w) {
        val c = src[x, y]
        out[x, y] = when {
            a(c) == 0 -> 0
            y < lidY -> (lid and 0xFFFFFF) or (a(c) shl 24)
            y < lidY + line -> (dark and 0xFFFFFF) or (a(c) shl 24)
            else -> c
        }
    }
    return out
}

private fun happy(src: Img, dark: Int): Img {
    val w = src.w; val h = src.h
    val bi = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = bi.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val t = max(2.5f, h * .11f)
    g.stroke = BasicStroke(t, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.color = Color(r(dark), g(dark), b(dark))
    // An arch over the lower part of the eye: the top half of an ellipse.
    val aw = w * .78f; val ah = h * .62f
    g.draw(Arc2D.Float((w - aw) / 2, h * .38f, aw, ah, 0f, 180f, Arc2D.OPEN))
    g.dispose()
    val out = Img(w, h)
    for (y in 0 until h) for (x in 0 until w) out[x, y] = bi.getRGB(x, y)
    return out
}
