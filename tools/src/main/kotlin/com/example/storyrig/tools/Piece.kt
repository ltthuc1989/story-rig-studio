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
 * A half whose cut edge is a natural edge drawn on top (a shorts cuff, the top of a shoe) gets no cap, and its
 * colours stay out of the other half's cap (a shin's cap is trouser or sock, not shoe).
 *
 * With [ref] (the image the piece was cut from) a cap pixel over the same garment takes the reference's own pixel,
 * so patterns (stripes) line up where the cap overlaps the other half ([capColour]). The +u half is drawn over the
 * -u half (limbs are drawn from the body out): where its cap would cover a different colour of the other half (a
 * hand's cap over the sleeve's cuff) it is left out, since the -u half's cap already closes the joint.
 * Returns (side towards -u, side towards +u).
 */
fun cutAt(p: Piece, j: Joint, capBefore: Boolean = true, capAfter: Boolean = true, ref: Img? = null): Pair<Piece, Piece> {
    val before = Img(p.w, p.h); val after = Img(p.w, p.h)
    val r = j.radius
    // Each half's colours next to the cut (past the cut line's own soft row).
    val bandAfter = bandColours(p, j, 2f, r)
    // The capless half's own colour: its most common one next to the cut that is not this half's most common one
    // (a shoe's, not the white of a sock showing inside it).
    val other = if (capAfter) emptyList() else {
        val mine = mainColours(bandPixels(p, j, -r, -2f), 0f).firstOrNull()
        listOfNotNull(mainColours(bandPixels(p, j, 2f, r), 0f).firstOrNull { mine == null || colourDist(it, mine) > SAME_GARMENT })
    }
    fun notOther(c: Int) = other.none { colourDist(c, it) <= SAME_GARMENT }
    val bandBefore = bandColours(p, j, -r, -2f).filter(::notOther)
    // Next to a capless half the cap is one flat colour: this half's most common colour right above the cut (the
    // trouser, sock or skin above a shoe whose top the cut runs through), or deeper in if the cut row is all shoe.
    val ownFill = if (other.isEmpty()) null else
        (mainColours(bandPixels(p, j, -6f, -1f).filter(::notOther), 0f) + bandBefore).firstOrNull()
    for (ly in 0 until p.h) for (lx in 0 until p.w) {
        val x = lx + p.ox + 0.5f; val y = ly + p.oy + 0.5f
        val t = (x - j.x) * j.ux + (y - j.y) * j.uy
        val c = p.img[lx, ly]
        if (t <= 0f) { if (a(c) > 0) before[lx, ly] = c } else { if (a(c) > 0) after[lx, ly] = c }
        // Cap: inside the disc around the joint, on the other side of the cut.
        if (hypot(x - j.x, y - j.y) > r) continue
        if (if (t > 0f) !capBefore else !capAfter) continue
        fun colourBack(k: Float): Int {
            val sx = (x - (t + k) * j.ux - 0.5f).roundToInt() - p.ox
            val sy = (y - (t + k) * j.uy - 0.5f).roundToInt() - p.oy
            return if (p.img.inside(sx, sy)) p.img[sx, sy] else 0
        }
        var sc = colourBack(0f)
        if (a(sc) <= 127) continue
        if (t > 0f && ownFill != null) sc = ownFill
        val gx = lx + p.ox; val gy = ly + p.oy
        if (t > 0f) before[lx, ly] = capColour(ref, gx, gy, bandBefore, sc or (0xFF shl 24), avoid = other)
        else after[lx, ly] = capColour(ref, gx, gy, bandAfter, sc or (0xFF shl 24), dropOther = capBefore)
    }
    return Piece(before, p.ox, p.oy).trimmed() to Piece(after, p.ox, p.oy).trimmed()
}

/** RGB L1 distance. */
fun colourDist(c1: Int, c2: Int) = kotlin.math.abs(r(c1) - r(c2)) + kotlin.math.abs(g(c1) - g(c2)) + kotlin.math.abs(b(c1) - b(c2))

/** How close (RGB L1) a reference pixel must be to a band colour to count as the same garment. */
const val SAME_GARMENT = 60

/** Main colours of [cols]: 16-level bins holding >= [minShare] of them, as mean colours, most common first. */
fun mainColours(cols: List<Int>, minShare: Float = 0.05f): List<Int> {
    val bins = HashMap<Int, IntArray>()
    for (c in cols) {
        val k = (r(c) shr 4 shl 8) or (g(c) shr 4 shl 4) or (b(c) shr 4)
        val s = bins.getOrPut(k) { IntArray(4) }
        s[0]++; s[1] += r(c); s[2] += g(c); s[3] += b(c)
    }
    return bins.values.filter { it[0] >= minShare * cols.size }.sortedByDescending { it[0] }
        .map { argb(255, it[1] / it[0], it[2] / it[0], it[3] / it[0]) }
}

/**
 * Main colours of a piece's opaque pixels between [from] and [to] px along a joint's u from its cut line (negative:
 * before the cut) and within the joint's radius of the limb's axis: the garment right next to the cut. One radius
 * deep is at least one period of the stripes seen so far.
 */
fun bandColours(p: Piece, j: Joint, from: Float, to: Float): List<Int> = mainColours(bandPixels(p, j, from, to))

/** The opaque pixels' colours of the band [bandColours] looks at. */
fun bandPixels(p: Piece, j: Joint, from: Float, to: Float): List<Int> {
    val cols = ArrayList<Int>()
    for (ly in 0 until p.h) for (lx in 0 until p.w) {
        val c = p.img[lx, ly]
        if (a(c) <= 200) continue
        val dx = lx + p.ox + .5f - j.x; val dy = ly + p.oy + .5f - j.y
        val t = dx * j.ux + dy * j.uy
        if (t < from || t > to || kotlin.math.abs(-dx * j.uy + dy * j.ux) > j.radius) continue
        cols += c
    }
    return cols
}

/**
 * Colour of a cap pixel at (x, y) (reference pixels): the reference's own pixel when it is opaque and close to one of
 * the [band] colours (the same garment, so stripes line up), otherwise [extrap], the colour smeared from the cut (a
 * shin's knee cap under the shorts stays skin or sock). [dropOther]: transparent instead of [extrap] where the
 * reference shows another colour. [avoid]: colours never taken from the reference (the shoe under a shin's cap).
 */
fun capColour(ref: Img?, x: Int, y: Int, band: List<Int>, extrap: Int, dropOther: Boolean = false, avoid: List<Int> = emptyList()): Int {
    if (ref == null || !ref.inside(x, y)) return extrap
    val c = ref[x, y]
    if (a(c) <= 200) return extrap
    if (band.any { colourDist(c, it) <= SAME_GARMENT } && avoid.none { colourDist(c, it) <= SAME_GARMENT }) return c or (0xFF shl 24)
    return if (dropOther) 0 else extrap
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

/**
 * Removes a skin-coloured disc a generator drew behind a face piece (it would cover glasses or brows once pasted
 * on the face). Only when most of the piece's outer ring is close to [skin]: then the pixels of that colour joined
 * to the outside are cleared. An eyelid is a shade darker and stays.
 */
fun stripSkinPatch(p: Piece, skin: Int): Piece {
    val w = p.w; val h = p.h
    fun near(c: Int, ref: Int) = kotlin.math.abs(r(c) - r(ref)) + kotlin.math.abs(g(c) - g(ref)) + kotlin.math.abs(b(c) - b(ref))
    // Outer ring: opaque pixels next to a transparent one.
    val ring = ArrayList<Int>()
    for (y in 0 until h) for (x in 0 until w) {
        if (a(p.img[x, y]) < 200) continue
        if (NEIGH4.any { (dx, dy) -> !p.img.inside(x + dx, y + dy) || a(p.img[x + dx, y + dy]) < 128 }) ring += p.img[x, y]
    }
    if (ring.isEmpty()) return p
    val skinRing = ring.count { near(it, skin) < 60 }
    if (skinRing == 0 || skinRing < ring.size * 3 / 4) return p
    // The disc's own colour (median of its ring), then flood it from the outside.
    fun med(f: (Int) -> Int) = ring.filter { near(it, skin) < 60 }.map(f).sorted().let { it[it.size / 2] }
    val disc = argb(255, med(::r), med(::g), med(::b))
    val out = Img(w, h); System.arraycopy(p.img.px, 0, out.px, 0, p.img.px.size)
    val seen = BooleanArray(w * h)
    val st = ArrayDeque<Int>()
    for (y in 0 until h) for (x in 0 until w) if ((x == 0 || y == 0 || x == w - 1 || y == h - 1) && !seen[y * w + x]) { seen[y * w + x] = true; st += y * w + x }
    while (st.isNotEmpty()) {
        val q = st.removeLast(); val x = q % w; val y = q / w
        val c = out.px[q]
        if (a(c) >= 128 && near(c, disc) >= DISC_CUT) continue
        out.px[q] = 0
        for ((dx, dy) in NEIGH4) {
            val nx = x + dx; val ny = y + dy
            if (nx !in 0 until w || ny !in 0 until h || seen[ny * w + nx]) continue
            seen[ny * w + nx] = true; st += ny * w + nx
        }
    }
    // Specks the flood stopped at (compression noise on the disc): keep only blobs of a real size.
    run {
        val lab = IntArray(w * h) { -1 }
        val sizes = ArrayList<Int>()
        for (s in 0 until w * h) {
            if (lab[s] >= 0 || a(out.px[s]) == 0) continue
            val id = sizes.size; var n = 0
            val q = ArrayDeque<Int>(); q += s; lab[s] = id
            while (q.isNotEmpty()) {
                val c = q.removeLast(); n++
                val x = c % w; val y = c / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val k = ny * w + nx
                    if (lab[k] < 0 && a(out.px[k]) > 0) { lab[k] = id; q += k }
                }
            }
            sizes += n
        }
        val biggest = sizes.maxOrNull() ?: 0
        for (k in 0 until w * h) if (lab[k] >= 0 && sizes[lab[k]] < biggest / 20) out.px[k] = 0
    }
    // Soft edge where the disc met the feature: alpha from how far each kept pixel's colour is from the disc.
    val cleared = BooleanArray(w * h) { out.px[it] == 0 && a(p.img.px[it]) > 0 }
    for (y in 0 until h) for (x in 0 until w) {
        val q = y * w + x
        val c = out.px[q]
        if (a(c) == 0 || NEIGH4.none { (dx, dy) -> val nx = x + dx; val ny = y + dy; nx in 0 until w && ny in 0 until h && cleared[ny * w + nx] }) continue
        val k = ((near(c, disc) - DISC_SOFT).toFloat() / (DISC_CUT - DISC_SOFT) + 1f).coerceIn(0f, 1f)
        out.px[q] = argb((a(c) * k).roundToInt(), r(c), g(c), b(c))
    }
    return Piece(out, p.ox, p.oy).trimmed()
}

/** Colour distance (RGB L1) to the disc: below DISC_CUT is disc, the edge fades in from DISC_SOFT. */
private const val DISC_CUT = 55
private const val DISC_SOFT = 30
