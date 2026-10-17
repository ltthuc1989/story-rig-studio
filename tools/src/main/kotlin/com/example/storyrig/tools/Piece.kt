package com.example.storyrig.tools

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A cut-out image placed at (ox, oy) in some coordinate space (sheet first, reference image later). */
class Piece(val img: Img, var ox: Int, var oy: Int) {
    val w get() = img.w
    val h get() = img.h

    /** Alpha at a point in placed coordinates (0 outside). */
    fun alphaAt(x: Int, y: Int): Int {
        val lx = x - ox; val ly = y - oy
        return if (img.inside(lx, ly)) a(img[lx, ly]) else 0
    }

    fun colourAt(x: Int, y: Int): Int {
        val lx = x - ox; val ly = y - oy
        return if (img.inside(lx, ly)) img[lx, ly] else 0
    }

    /** Shrinks to the opaque bounding box (+1 px). */
    fun trimmed(): Piece {
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if (a(img[x, y]) > 0) {
            x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y)
        }
        if (x1 < 0) return Piece(Img(1, 1), ox, oy)
        x0 = max(0, x0 - 1); y0 = max(0, y0 - 1); x1 = min(w - 1, x1 + 1); y1 = min(h - 1, y1 + 1)
        val out = Img(x1 - x0 + 1, y1 - y0 + 1)
        for (y in 0 until out.h) for (x in 0 until out.w) out[x, y] = img[x + x0, y + y0]
        return Piece(out, ox + x0, oy + y0)
    }
}

/** Copies the given components (plus their soft edge) out of a keyed sheet. */
fun extract(keyed: Img, labels: IntArray, comps: List<Comp>, ids: IntArray): Piece {
    val sel = comps.filter { it.id in ids }
    require(sel.size == ids.size) { "missing component in ${ids.toList()}" }
    val x0 = max(0, sel.minOf { it.x0 } - 3); val y0 = max(0, sel.minOf { it.y0 } - 3)
    val x1 = min(keyed.w - 1, sel.maxOf { it.x1 } + 3); val y1 = min(keyed.h - 1, sel.maxOf { it.y1 } + 3)
    val out = Img(x1 - x0 + 1, y1 - y0 + 1)
    for (y in y0..y1) for (x in x0..x1) {
        // Soft edge pixels have label 0; take them when a selected component is within 2 px.
        val own = labels[y * keyed.w + x]
        var take = own in ids
        if (own == 0 && a(keyed[x, y]) > 0) {
            for (dy in -2..2) for (dx in -2..2) {
                val nx = x + dx; val ny = y + dy
                if (keyed.inside(nx, ny) && labels[ny * keyed.w + nx] in ids) take = true
            }
        }
        if (take) out[x - x0, y - y0] = keyed[x, y]
    }
    return Piece(out, x0, y0).trimmed()
}

/** Premultiplied bilinear rescale of a whole image. */
fun scaleImg(src: Img, s: Float): Img {
    val out = Img(max(1, (src.w * s).roundToInt()), max(1, (src.h * s).roundToInt()))
    for (y in 0 until out.h) for (x in 0 until out.w) {
        val fx = (x + 0.5f) / s - 0.5f; val fy = (y + 0.5f) / s - 0.5f
        val ix = floor(fx).toInt(); val iy = floor(fy).toInt()
        val tx = fx - ix; val ty = fy - iy
        var sa = 0f; var sr = 0f; var sg = 0f; var sb = 0f
        for (k in 0 until 4) {
            val px = ix + (k and 1); val py = iy + (k shr 1)
            val wgt = (if (k and 1 == 1) tx else 1 - tx) * (if (k shr 1 == 1) ty else 1 - ty)
            if (!src.inside(px, py)) continue
            val c = src[px, py]
            val al = a(c) / 255f * wgt
            sa += al; sr += r(c) * al; sg += g(c) * al; sb += b(c) * al
        }
        out[x, y] = if (sa <= 0.001f) 0 else argb((sa * 255).roundToInt(), (sr / sa).roundToInt(), (sg / sa).roundToInt(), (sb / sa).roundToInt())
    }
    return out
}

class Joint(val x: Float, val y: Float, val ux: Float, val uy: Float, val radius: Float)

/**
 * Finds the joint on the line through (x, y) perpendicular to (ux, uy): recentres on the piece's width there
 * and uses half that width as the cap radius.
 */
fun jointAcross(p: Piece, x: Float, y: Float, ux: Float, uy: Float): Joint {
    val l = hypot(ux, uy); val dx = ux / l; val dy = uy / l
    val nx = -dy; val ny = dx
    fun reach(sign: Int): Float {
        var t = 0f
        while (t < 400f && p.alphaAt((x + nx * t * sign).roundToInt(), (y + ny * t * sign).roundToInt()) > 127) t += 0.5f
        return t
    }
    val a = reach(1); val b = reach(-1)
    val mid = (a - b) / 2f
    return Joint(x + nx * mid, y + ny * mid, dx, dy, (a + b) / 2f)
}

/**
 * Splits a limb at a joint. Both halves get a round cap of the joint's radius past the cut, filled by smearing
 * the cut line's colours along the limb, so in the rest pose the two halves line up exactly and when the joint
 * turns there is no gap. Flat-colour art without outlines makes the seam invisible.
 * A half whose cut edge is a natural edge drawn on top (a shorts cuff, the top of a shoe) gets no cap.
 * Returns (side towards -u, side towards +u).
 */
fun cutAt(p: Piece, j: Joint, capBefore: Boolean = true, capAfter: Boolean = true): Pair<Piece, Piece> {
    val before = Img(p.w, p.h); val after = Img(p.w, p.h)
    val r = j.radius
    for (ly in 0 until p.h) for (lx in 0 until p.w) {
        val x = lx + p.ox + 0.5f; val y = ly + p.oy + 0.5f
        val t = (x - j.x) * j.ux + (y - j.y) * j.uy
        val c = p.img[lx, ly]
        if (t <= 0f) { if (a(c) > 0) before[lx, ly] = c } else { if (a(c) > 0) after[lx, ly] = c }
        // Cap: inside the disc around the joint, on the other side of the cut.
        if (hypot(x - j.x, y - j.y) > r) continue
        val sx = (x - t * j.ux - 0.5f).roundToInt() - p.ox
        val sy = (y - t * j.uy - 0.5f).roundToInt() - p.oy
        if (!p.img.inside(sx, sy)) continue
        val sc = p.img[sx, sy]
        if (a(sc) <= 127) continue
        if (t > 0f) { if (capBefore) before[lx, ly] = sc or (0xFF shl 24) } else if (capAfter) after[lx, ly] = sc or (0xFF shl 24)
    }
    return Piece(before, p.ox, p.oy).trimmed() to Piece(after, p.ox, p.oy).trimmed()
}

/** Mean x of opaque pixels in a row (placed coordinates), or NaN. */
fun rowCentre(p: Piece, y: Int): Float {
    var s = 0f; var n = 0
    for (x in p.ox until p.ox + p.w) if (p.alphaAt(x, y) > 127) { s += x; n++ }
    return if (n == 0) Float.NaN else s / n
}

fun rowCount(p: Piece, y: Int, pred: (Int) -> Boolean): Int {
    var n = 0
    for (x in p.ox until p.ox + p.w) {
        val c = p.colourAt(x, y)
        if (a(c) > 127 && pred(c)) n++
    }
    return n
}

fun rowWidth(p: Piece, y: Int) = rowCount(p, y) { true }

/** Joint at row y with the limb direction taken from row centres 12 px above and below. */
fun jointAtRow(p: Piece, y: Int): Joint {
    val above = rowCentre(p, y - 12); val below = rowCentre(p, y + 12)
    val cx = rowCentre(p, y)
    return jointAcross(p, cx, y.toFloat(), below - above, 24f)
}

/** Centres of the two rosy cheeks (pink blobs) inside a box of the piece, left one first. */
fun cheeks(p: Piece, box: Box): Pair<FloatArray, FloatArray> {
    val isCheek = { c: Int -> r(c) > 210 && r(c) - g(c) in 60..130 && kotlin.math.abs(g(c) - b(c)) < 30 }
    val xs = ArrayList<Int>(); val ys = ArrayList<Int>()
    for (y in box.y0 until box.y1) for (x in box.x0 until box.x1) {
        val c = p.colourAt(x, y)
        if (a(c) > 200 && isCheek(c)) { xs += x; ys += y }
    }
    require(xs.size > 50) { "no cheeks found" }
    val mid = (xs.min() + xs.max()) / 2
    fun centre(left: Boolean): FloatArray {
        var sx = 0f; var sy = 0f; var n = 0
        for (i in xs.indices) if ((xs[i] < mid) == left) { sx += xs[i]; sy += ys[i]; n++ }
        return floatArrayOf(sx / n, sy / n)
    }
    return centre(true) to centre(false)
}
