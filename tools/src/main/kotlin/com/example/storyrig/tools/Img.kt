package com.example.storyrig.tools

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Non-premultiplied ARGB pixels. */
class Img(val w: Int, val h: Int, val px: IntArray = IntArray(w * h)) {
    operator fun get(x: Int, y: Int) = px[y * w + x]
    operator fun set(x: Int, y: Int, v: Int) {
        px[y * w + x] = v
    }

    fun inside(x: Int, y: Int) = x in 0 until w && y in 0 until h

    fun save(f: File) {
        f.parentFile?.mkdirs()
        val bi = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        bi.setRGB(0, 0, w, h, px, 0, w)
        ImageIO.write(bi, "png", f)
    }

    fun toBuffered(): BufferedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also { it.setRGB(0, 0, w, h, px, 0, w) }

    companion object {
        fun load(f: File): Img {
            val bi = ImageIO.read(f) ?: error("cannot read $f")
            val img = Img(bi.width, bi.height)
            bi.getRGB(0, 0, bi.width, bi.height, img.px, 0, bi.width)
            return img
        }
    }
}

fun a(c: Int) = c ushr 24
fun r(c: Int) = (c shr 16) and 255
fun g(c: Int) = (c shr 8) and 255
fun b(c: Int) = c and 255
fun argb(a: Int, r: Int, g: Int, b: Int) =
    (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

/**
 * Removes a magenta/pink chroma background, including the darker magenta ground shadows Gemini adds.
 * "Magenta-ness" = min(R,B) - G - |R-B|/2: high for the background and its shadows, <= ~0 for every colour
 * in Kai's palette (skin, red, blue, yellow, white, dark brown). Edge pixels get partial alpha and their
 * colour is un-mixed from the background colour.
 */
fun keyMagenta(src: Img, lo: Int = 30, hi: Int = 75): Img {
    val bg = cornerColour(src)
    val out = Img(src.w, src.h)
    for (i in src.px.indices) {
        val c = src.px[i]
        val m = min(r(c), b(c)) - g(c) - abs(r(c) - b(c)) / 2
        val al = when {
            m <= lo -> 1f
            m >= hi -> 0f
            else -> 1f - (m - lo).toFloat() / (hi - lo)
        }
        out.px[i] = if (al <= 0f) 0 else if (al >= 1f) (c or (0xFF shl 24)) else {
            fun un(v: Int, bv: Int) = ((v - (1 - al) * bv) / al).roundToInt()
            argb((al * 255).roundToInt(), un(r(c), r(bg)), un(g(c), g(bg)), un(b(c), b(bg)))
        }
    }
    return out
}

/**
 * Kills the pink halo left by anti-aliasing against the background: pixels within [band] px of the edge take
 * the colour of the nearest pixel deeper inside (flat-colour art, so this is exact), and the outermost ring
 * loses some alpha.
 */
fun defringe(img: Img, band: Int = 2): Img {
    val w = img.w; val h = img.h
    val dist = IntArray(w * h) { Int.MAX_VALUE }
    val queue = IntArray(w * h)
    var qh = 0; var qt = 0
    for (i in img.px.indices) if (a(img.px[i]) <= 127) { dist[i] = 0; queue[qt++] = i }
    while (qh < qt) {
        val p = queue[qh++]
        val x = p % w; val y = p / w
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx; val ny = y + dy
            if (nx !in 0 until w || ny !in 0 until h) continue
            val q = ny * w + nx
            if (dist[q] > dist[p] + 1) { dist[q] = dist[p] + 1; queue[qt++] = q }
        }
    }
    val out = Img(w, h, img.px.copyOf())
    val reach = band + 2
    for (y in 0 until h) for (x in 0 until w) {
        val i = y * w + x
        val c = img.px[i]
        if (a(c) == 0) continue
        if (dist[i] > band && a(c) > 127) continue
        var best = -1; var bestD = Int.MAX_VALUE
        for (dy in -reach..reach) for (dx in -reach..reach) {
            val nx = x + dx; val ny = y + dy
            if (nx !in 0 until w || ny !in 0 until h) continue
            val q = ny * w + nx
            if (dist[q] <= band) continue
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = q }
        }
        val al = if (dist[i] <= 1) (a(c) * 0.7f).toInt() else a(c)
        out.px[i] = if (best >= 0) (img.px[best] and 0xFFFFFF) or (al shl 24) else (c and 0xFFFFFF) or (al shl 24)
    }
    return out
}

/** Median of the four 16x16 corners. */
fun cornerColour(img: Img): Int {
    val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
    for ((cx, cy) in listOf(0 to 0, img.w - 16 to 0, 0 to img.h - 16, img.w - 16 to img.h - 16))
        for (y in cy until cy + 16) for (x in cx until cx + 16) {
            val c = img[x, y]; rs += r(c); gs += g(c); bs += b(c)
        }
    rs.sort(); gs.sort(); bs.sort()
    return argb(255, rs[rs.size / 2], gs[gs.size / 2], bs[bs.size / 2])
}

class Comp(val id: Int, var x0: Int, var y0: Int, var x1: Int, var y1: Int, var area: Int) {
    val cx get() = (x0 + x1) / 2
    val cy get() = (y0 + y1) / 2
}

/** 8-connected components of pixels with alpha > 127. labels[i] = component id (1-based) or 0. */
fun components(img: Img, minArea: Int): Pair<IntArray, List<Comp>> {
    val labels = IntArray(img.w * img.h)
    val comps = ArrayList<Comp>()
    val stack = IntArray(img.w * img.h)
    var next = 1
    for (start in img.px.indices) {
        if (labels[start] != 0 || a(img.px[start]) <= 127) continue
        val id = next++
        val c = Comp(id, img.w, img.h, 0, 0, 0)
        var sp = 0
        stack[sp++] = start
        labels[start] = id
        while (sp > 0) {
            val p = stack[--sp]
            val x = p % img.w; val y = p / img.w
            c.area++
            c.x0 = min(c.x0, x); c.y0 = min(c.y0, y); c.x1 = max(c.x1, x); c.y1 = max(c.y1, y)
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (!img.inside(nx, ny)) continue
                val q = ny * img.w + nx
                if (labels[q] == 0 && a(img.px[q]) > 127) {
                    labels[q] = id
                    stack[sp++] = q
                }
            }
        }
        comps += c
    }
    val keep = comps.filter { it.area >= minArea }
    val keepIds = keep.map { it.id }.toHashSet()
    for (i in labels.indices) if (labels[i] != 0 && labels[i] !in keepIds) labels[i] = 0
    return labels to keep
}
