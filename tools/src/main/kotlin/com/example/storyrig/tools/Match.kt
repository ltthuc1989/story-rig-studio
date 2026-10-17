package com.example.storyrig.tools

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Search window in the reference image. */
class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

/** Opaque pixels of a piece as (x, y, colour) triples in its placed coordinates, every [step] px. */
fun samples(p: Piece, step: Int): IntArray {
    val out = ArrayList<Int>()
    for (ly in 0 until p.h step step) for (lx in 0 until p.w step step) {
        val c = p.img[lx, ly]
        if (a(c) >= 230) { out += lx + p.ox; out += ly + p.oy; out += c }
    }
    return out.toIntArray()
}

private fun cost(ref: Img, s: Float, smp: IntArray, dx: Int, dy: Int, limit: Long): Long {
    var sum = 0L
    var i = 0
    while (i < smp.size) {
        val x = (smp[i] * s).roundToInt() + dx
        val y = (smp[i + 1] * s).roundToInt() + dy
        val c = smp[i + 2]
        sum += if (!ref.inside(x, y)) 765 else {
            val q = ref[x, y]
            abs(r(q) - r(c)) + abs(g(q) - g(c)) + abs(b(q) - b(c))
        }
        if (sum >= limit) return sum
        i += 3
    }
    return sum
}

class Fit(val dx: Int, val dy: Int, val cost: Long, val perPixel: Float)

/** Best translation so the scaled piece lands inside [box]: coarse grid, then a local refine. */
fun fitOffset(ref: Img, s: Float, p: Piece, smp: IntArray, box: Box, coarse: Int = 4): Fit {
    val minDx = box.x0 - (p.ox * s).roundToInt()
    val maxDx = box.x1 - ((p.ox + p.w) * s).roundToInt()
    val minDy = box.y0 - (p.oy * s).roundToInt()
    val maxDy = box.y1 - ((p.oy + p.h) * s).roundToInt()
    var best = Fit(minDx, minDy, Long.MAX_VALUE, 0f)
    var dy = minDy
    while (dy <= max(minDy, maxDy)) {
        var dx = minDx
        while (dx <= max(minDx, maxDx)) {
            val c = cost(ref, s, smp, dx, dy, best.cost)
            if (c < best.cost) best = Fit(dx, dy, c, 0f)
            dx += coarse
        }
        dy += coarse
    }
    val cx = best.dx; val cy = best.dy
    for (yy in cy - coarse..cy + coarse) for (xx in cx - coarse..cx + coarse) {
        val c = cost(ref, s, smp, xx, yy, best.cost)
        if (c < best.cost) best = Fit(xx, yy, c, 0f)
    }
    return Fit(best.dx, best.dy, best.cost, best.cost.toFloat() / max(1, smp.size / 3))
}

/** Sheet-to-reference scale: the one where [pieces] fit best in total. */
fun fitScale(ref: Img, pieces: List<Piece>, box: Box, from: Float, to: Float): Float {
    fun total(s: Float, step: Int, coarse: Int) = pieces.sumOf { p ->
        fitOffset(ref, s, p, samples(p, step), box, coarse).perPixel.toDouble()
    }
    var bestS = from; var bestC = Double.MAX_VALUE
    var s = from
    while (s <= to + 1e-4f) {
        val c = total(s, 5, 6)
        if (c < bestC) { bestC = c; bestS = s }
        s += 0.03f
    }
    val centre = bestS
    s = centre - 0.03f
    while (s <= centre + 0.03f + 1e-4f) {
        val c = total(s, 3, 3)
        if (c < bestC) { bestC = c; bestS = s }
        s += 0.005f
    }
    return bestS
}

/** Moves a piece (already scaled to reference pixels) to where it matches the reference best. */
fun place(ref: Img, p: Piece, box: Box): Fit {
    val f = fitOffset(ref, 1f, p, samples(p, 2), box, 3)
    p.ox += f.dx; p.oy += f.dy
    return f
}

fun Piece.scaledToRef(s: Float): Piece {
    val big = scaleImg(img, s)
    return Piece(big, (ox * s).roundToInt(), (oy * s).roundToInt())
}

fun clampBox(b: Box, img: Img) = Box(max(0, b.x0), max(0, b.y0), min(img.w, b.x1), min(img.h, b.y1))
