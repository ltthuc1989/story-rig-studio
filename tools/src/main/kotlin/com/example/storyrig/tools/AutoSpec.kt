package com.example.storyrig.tools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * `cutauto`: the same cut as `cutref`, but the RefSpec is measured instead of typed in.
 *
 * 1. The mannequin template (assets/templates/rig_3q_right.png + .csv) gives every joint in template pixels. Gemini
 *    copies the mannequin's pose and framing, so mapping the template's silhouette box onto the character's gives a
 *    first guess for each joint (off by up to ~50 px: heads, hair and sleeves differ).
 * 2. Each guess is then refined on the character's own silhouette (alpha after keying), by shape only:
 *    neck = bottom of the narrow part between head and shoulders; shoulder cut = through the armpit (the top of the
 *    background gap under the arm), across the arm axis; wrist = narrowest cross-section before the hand; elbow =
 *    halfway; seam and shoe split = the background gap between the legs; knee = where the thigh garment's edge ends
 *    (a step in leg width), ankle = where the foot widens. Capsule radii are measured arm widths.
 * 3. Colour is used only where two parts are drawn over each other with no gap, and only relative to the character:
 *    the thigh garment's own colours (sampled around the hip line), the head's dominant colour (skin) and the colour at
 *    the top of the head (hair). Face features are blobs that contrast with the skin, classified by position and shape.
 */

private val TEMPLATE = File("assets/templates/rig_3q_right.png")

/** Mannequin joints (template pixels) and the box of its silhouette. */
class RigTemplate(val pts: Map<String, Pt>, val box: Box) {
    operator fun get(n: String) = pts[n] ?: error("template has no point $n")

    companion object {
        fun load(png: File): RigTemplate {
            val csv = File(png.parentFile, png.nameWithoutExtension + ".csv")
            val pts = csv.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.associate { l ->
                val f = l.split(",")
                f[0].trim() to Pt(f[1].trim().toFloat(), f[2].trim().toFloat())
            }
            return RigTemplate(pts, Silhouette(keyMagenta(Img.load(png))).box)
        }
    }
}

/** The character's opaque pixels (largest 8-connected component, alpha > 127). */
class Silhouette(val img: Img) {
    val w = img.w
    val h = img.h
    val on = BooleanArray(w * h)
    val box: Box

    init {
        val (labels, comps) = components(img, 1)
        val big = comps.maxBy { it.area }
        for (i in on.indices) on[i] = labels[i] == big.id
        box = Box(big.x0, big.y0, big.x1 + 1, big.y1 + 1)
    }

    fun at(x: Int, y: Int) = x in 0 until w && y in 0 until h && on[y * w + x]
    fun at(x: Float, y: Float) = at(x.roundToInt(), y.roundToInt())

    /** How far from (x, y) along (ux, uy) the silhouette goes on (0 when (x, y) is off it). */
    fun reach(x: Float, y: Float, ux: Float, uy: Float, max: Float = 800f): Float {
        var t = 0f
        while (t < max && at(x + ux * t, y + uy * t)) t += 0.5f
        return t
    }

    /** Horizontal run of the silhouette through (x, y), or the nearest run within [search] px; null if none. */
    fun run(x: Int, y: Int, search: Int = 0): IntRange? {
        var sx = -1
        for (d in 0..search) {
            if (at(x - d, y)) { sx = x - d; break }
            if (at(x + d, y)) { sx = x + d; break }
        }
        if (sx < 0) return null
        var x0 = sx; var x1 = sx
        while (at(x0 - 1, y)) x0--
        while (at(x1 + 1, y)) x1++
        return x0..x1
    }
}

/** Template to character: the silhouette boxes' widths and heights give the scales, the box centre and bottom the offsets. */
class TemplateMap(t: RigTemplate, c: Silhouette) {
    val sx = (c.box.x1 - c.box.x0).toFloat() / (t.box.x1 - t.box.x0)
    val sy = (c.box.y1 - c.box.y0).toFloat() / (t.box.y1 - t.box.y0)
    val s = (sx + sy) / 2
    private val tcx = (t.box.x0 + t.box.x1) / 2f
    private val ccx = (c.box.x0 + c.box.x1) / 2f
    private val tBottom = t.box.y1.toFloat()
    private val cBottom = c.box.y1.toFloat()
    fun map(p: Pt) = Pt(ccx + (p.x - tcx) * sx, cBottom + (p.y - tBottom) * sy)
}

private fun dist(c1: Int, c2: Int) = abs(r(c1) - r(c2)) + abs(g(c1) - g(c2)) + abs(b(c1) - b(c2))

/** Main colours of a set of pixels: 16-level bins holding >= [minShare] of them, as mean colours. */
private fun palette(img: Img, pixels: List<Int>, minShare: Float = 0.03f): List<Int> {
    val bins = HashMap<Int, IntArray>()
    for (p in pixels) {
        val c = img.px[p]
        val k = (r(c) shr 4 shl 8) or (g(c) shr 4 shl 4) or (b(c) shr 4)
        val s = bins.getOrPut(k) { IntArray(4) }
        s[0]++; s[1] += r(c); s[2] += g(c); s[3] += b(c)
    }
    return bins.values.filter { it[0] >= minShare * pixels.size }.map { argb(255, it[1] / it[0], it[2] / it[0], it[3] / it[0]) }
}

private fun mid(s: Seg) = Pt((s.x1 + s.x2) / 2, (s.y1 + s.y2) / 2)
private fun norm(x: Float, y: Float): Pt { val l = hypot(x, y); return Pt(x / l, y / l) }

/** Least-squares line x = a + b*y through (x, y) pairs. */
private fun fitX(pts: List<Pt>): Pair<Float, Float> {
    val n = pts.size
    val my = pts.sumOf { it.y.toDouble() } / n; val mx = pts.sumOf { it.x.toDouble() } / n
    var sxy = 0.0; var syy = 0.0
    for (p in pts) { sxy += (p.y - my) * (p.x - mx); syy += (p.y - my) * (p.y - my) }
    val b = if (syy < 1e-6) 0.0 else sxy / syy
    return (mx - b * my).toFloat() to b.toFloat()
}

/** Measurements kept for the report: the template guess of each point. */
class AutoResult(val spec: RefSpec, val guesses: Map<String, Pt>, val sil: Silhouette)

fun autoSpec(img: Img, template: RigTemplate = RigTemplate.load(TEMPLATE), debug: Boolean = false, view: String = "3q"): AutoResult {
    val face = view != "back"
    val sil = Silhouette(img)
    val tm = TemplateMap(template, sil)
    val s = tm.s
    val guesses = LinkedHashMap<String, Pt>()
    fun guess(n: String) = tm.map(template[n]).also { guesses[n] = it }
    fun log(msg: String) { if (debug) println("  [auto] $msg") }
    log("template -> character: sx=${"%.3f".format(tm.sx)} sy=${"%.3f".format(tm.sy)} box ${sil.box.x0},${sil.box.y0}-${sil.box.x1},${sil.box.y1}")

    // Nearest background pixel to a point (within r px).
    fun background(p: Pt, r: Int = 40): Pt {
        var best: Pt? = null; var bd = Float.MAX_VALUE
        for (dy in -r..r) for (dx in -r..r) {
            val x = p.x.roundToInt() + dx; val y = p.y.roundToInt() + dy
            if (x !in 0 until sil.w || y !in 0 until sil.h || sil.at(x, y)) continue
            val d = hypot(dx.toFloat(), dy.toFloat())
            if (d < bd) { bd = d; best = Pt(x.toFloat(), y.toFloat()) }
        }
        return best ?: error("no background near ${p.x},${p.y}")
    }
    // Background pixels 4-connected to [seed] that pass [ok].
    fun flood(seed: Pt, ok: (Int, Int) -> Boolean): List<Int> {
        val out = ArrayList<Int>()
        val seen = BooleanArray(sil.w * sil.h)
        val st = ArrayDeque<Int>()
        val s0 = seed.y.roundToInt() * sil.w + seed.x.roundToInt()
        st += s0; seen[s0] = true
        while (st.isNotEmpty()) {
            val q = st.removeLast(); out += q
            val x = q % sil.w; val y = q / sil.w
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until sil.w || ny !in 0 until sil.h) continue
                val n = ny * sil.w + nx
                if (seen[n] || sil.on[n] || !ok(nx, ny)) continue
                seen[n] = true; st += n
            }
        }
        return out
    }

    // ---- Neck: the narrowest row between the head and the shoulders; the cut sits where the shoulders start to flare.
    val headC = guess("head_centre")
    val neckGuess = guess("neck_bottom")
    val pitNearG = guess("armpit_near"); val pitFarG = guess("armpit_far")
    val bodyX = neckGuess.x.roundToInt()
    val neckFrom = guess("neck_top").y.roundToInt() - (40 * s).roundToInt()
    val neckTo = max(pitNearG.y, pitFarG.y).roundToInt()
    val widths = (neckFrom..neckTo).associateWith { y -> sil.run(bodyX, y, (30 * s).roundToInt())?.let { it.last - it.first + 1 } ?: 0 }
    val narrowY = widths.filter { it.value > 0 }.minBy { it.value }.key
    val neckW = widths.getValue(narrowY)
    // A few px above the collar, so the torso keeps a neck stub under the head.
    val neckY = (narrowY..neckTo).first { widths.getValue(it) > neckW * 1.3f } - 1 - (3 * s).roundToInt()
    val neckRun = sil.run(bodyX, neckY, (30 * s).roundToInt())!!
    val neck = Seg(neckRun.first - 8f, neckY.toFloat(), neckRun.last + 8f, neckY.toFloat())
    log("neck: narrowest $neckW px at y=$narrowY, cut at y=$neckY x ${neckRun.first}..${neckRun.last}")

    // ---- Arms.
    class Arm(val shoulder: Seg, val elbow: Pt, val wrist: Pt, val radii: FloatArray)

    fun arm(side: String): Arm {
        val sh = guess("shoulder_$side"); val hand = guess("hand_$side"); val pit = guess("armpit_$side")
        var d = norm(hand.x - sh.x, hand.y - sh.y)
        // Armpit: the point of the gap under the arm that reaches furthest into the corner between arm and torso.
        val gapSeed = background(guess("gap_arm_$side"))
        val radius = 0.2f * (sil.box.y1 - sil.box.y0)
        val gap = flood(gapSeed) { x, y -> hypot(x - gapSeed.x, y - gapSeed.y) <= radius }
        val into = norm(-d.x, -(d.y + 1f))
        // Slits narrower than ~12 px (a sleeve hem hanging over the shirt) are not the gap: keep pixels 6 px clear of it.
        val clear = (6 * s).roundToInt().coerceAtLeast(2)
        val wide = gap.filter { q ->
            val x = q % sil.w; val y = q / sil.w
            (-clear..clear).all { dy -> (-clear..clear).all { dx -> dx * dx + dy * dy > clear * clear || !sil.at(x + dx, y + dy) } }
        }
        var apexScore = -Float.MAX_VALUE
        for (q in wide) { val v = (q % sil.w) * into.x + (q / sil.w) * into.y; if (v > apexScore) apexScore = v }
        val tip = wide.filter { (it % sil.w) * into.x + (it / sil.w) * into.y >= apexScore - 1f }
        val apex = Pt(tip.map { it % sil.w + .5f }.average().toFloat() + into.x * clear, tip.map { it / sil.w + .5f }.average().toFloat() + into.y * clear)
        log("armpit $side: guess ${pit.x.roundToInt()},${pit.y.roundToInt()} -> ${apex.x.roundToInt()},${apex.y.roundToInt()}")

        // Axis: centres of cross-sections along the arm, fitted to a line (three rounds, each with the new direction).
        val len = hypot(hand.x - sh.x, hand.y - sh.y)
        var origin = Pt((sh.x + hand.x) / 2, (sh.y + hand.y) / 2)
        repeat(3) {
            val n = Pt(-d.y, d.x)
            val cs = ArrayList<Pt>()
            for (k in 0..20) {
                val t = (-0.25f + 0.5f * k / 20) * len
                var cx = origin.x + d.x * t; var cy = origin.y + d.y * t
                if (!sil.at(cx, cy)) {
                    // Off the arm: look for it along the cross-section line.
                    val off = (1..(60 * s).roundToInt()).flatMap { listOf(it, -it) }.firstOrNull { sil.at(cx + n.x * it, cy + n.y * it) } ?: continue
                    cx += n.x * off; cy += n.y * off
                }
                val a = sil.reach(cx, cy, n.x, n.y); val b = sil.reach(cx, cy, -n.x, -n.y)
                cs += Pt(cx + n.x * (a - b) / 2, cy + n.y * (a - b) / 2)
            }
            // Principal direction of the centres.
            val mx = cs.map { it.x }.average().toFloat(); val my = cs.map { it.y }.average().toFloat()
            var sxx = 0f; var sxy = 0f; var syy = 0f
            for (p in cs) { sxx += (p.x - mx) * (p.x - mx); sxy += (p.x - mx) * (p.y - my); syy += (p.y - my) * (p.y - my) }
            val ang = 0.5f * kotlin.math.atan2(2 * sxy, sxx - syy)
            var nd = Pt(kotlin.math.cos(ang), kotlin.math.sin(ang))
            if (nd.x * d.x + nd.y * d.y < 0) nd = Pt(-nd.x, -nd.y)
            d = nd; origin = Pt(mx, my)
        }
        val n0 = Pt(-d.y, d.x)
        // Normal pointing from the axis to the armpit side.
        val n = if ((apex.x - origin.x) * n0.x + (apex.y - origin.y) * n0.y > 0) n0 else Pt(-n0.x, -n0.y)
        // Shoulder cut: through the armpit, across the arm, out past the top of the shoulder.
        var t = 0f
        while (t < 10f && !sil.at(apex.x - n.x * t, apex.y - n.y * t)) t += .5f
        t += sil.reach(apex.x - n.x * t, apex.y - n.y * t, -n.x, -n.y)
        val shoulder = Seg(apex.x - n.x * (t + 6f), apex.y - n.y * (t + 6f), apex.x + n.x * 6f, apex.y + n.y * 6f)
        val shMid = mid(shoulder)
        // Axis parameter of a point, and the axis point at a parameter.
        fun tOf(p: Pt) = (p.x - origin.x) * d.x + (p.y - origin.y) * d.y
        fun at(tt: Float) = Pt(origin.x + d.x * tt, origin.y + d.y * tt)
        val tS = tOf(shMid)
        var tTip = tS
        run {
            var miss = 0; var tt = tOf(origin)
            while (miss < 6 && tt < tS + 3 * len) { val p = at(tt); if (sil.at(p.x, p.y)) { tTip = tt; miss = 0 } else miss++; tt += 1f }
        }
        val armLen = tTip - tS
        fun section(tt: Float): Pair<Float, Float> { val p = at(tt); return sil.reach(p.x, p.y, n.x, n.y) to sil.reach(p.x, p.y, -n.x, -n.y) }
        // Full extent across the arm: outermost silhouette pixels on each side, gaps included. Through spread fingers
        // it is the whole hand, so the narrowest place is the real wrist, not one finger.
        val spanMax = 90 * s
        fun span(tt: Float): Pair<Float, Float> {
            val p = at(tt)
            fun far(ux: Float, uy: Float): Float {
                var last = 0f; var t = 0f
                while (t <= spanMax) { if (sil.at(p.x + ux * t, p.y + uy * t)) last = t; t += .5f }
                return last
            }
            return far(n.x, n.y) to far(-n.x, -n.y)
        }
        // Wrist: narrowest cross-section in the outer part of the arm, before the hand.
        val prof = (0..60).map { k -> val tt = tS + armLen * (0.5f + 0.42f * k / 60); tt to span(tt) }
        // The forearm is often a long even plateau: the wrist sits near its outer end, a little before the hand widens.
        val wMin = prof.minOf { it.second.first + it.second.second }
        var kW = prof.indexOfFirst { it.second.first + it.second.second == wMin }
        while (kW + 1 < prof.size && prof[kW + 1].second.let { it.first + it.second } <= wMin * 1.06f + 1f) kW++
        val tW = max(prof.first { it.second.first + it.second.second == wMin }.first, prof[kW].first - 5 * s)
        val wsec = section(tW)
        val wp = at(tW)
        val wrist = Pt(wp.x + n.x * (wsec.first - wsec.second) / 2, wp.y + n.y * (wsec.first - wsec.second) / 2)
        val elbow = Pt((shMid.x + wrist.x) / 2, (shMid.y + wrist.y) / 2)
        val tE = tOf(elbow)
        // Capsule radii: the widest cross-section of each segment (furthest edge from the axis), plus a margin.
        fun widest(from: Float, to: Float) = (0..20).maxOf { k -> val (a, b) = section(from + (to - from) * k / 20); max(a, b) }
        val rUpper = widest(tS + 0.15f * (tE - tS), tE) + 6f
        val rFore = widest(tE, tW) + 6f
        // Hand: every pixel past the wrist must fit (the hand is a free end, nothing else is near).
        val handEnd = Pt(wrist.x + (wrist.x - elbow.x) * .9f, wrist.y + (wrist.y - elbow.y) * .9f)
        var rHand = rFore
        val hx0 = (min(wrist.x, handEnd.x) - 3 * rFore).toInt(); val hx1 = (max(wrist.x, handEnd.x) + 3 * rFore).toInt()
        val hy0 = (min(wrist.y, handEnd.y) - 3 * rFore).toInt(); val hy1 = (max(wrist.y, handEnd.y) + 3 * rFore).toInt()
        val ex = handEnd.x - wrist.x; val ey = handEnd.y - wrist.y
        for (y in hy0..hy1) for (x in hx0..hx1) {
            if (!sil.at(x, y)) continue
            val px = x + .5f; val py = y + .5f
            if ((px - wrist.x) * d.x + (py - wrist.y) * d.y <= 0f) continue
            val u = (((px - wrist.x) * ex + (py - wrist.y) * ey) / (ex * ex + ey * ey)).coerceIn(0f, 1f)
            rHand = max(rHand, hypot(px - wrist.x - u * ex, py - wrist.y - u * ey) + 3f)
        }
        log("arm $side: shoulder ${shMid.x.roundToInt()},${shMid.y.roundToInt()} elbow ${elbow.x.roundToInt()},${elbow.y.roundToInt()} wrist ${wrist.x.roundToInt()},${wrist.y.roundToInt()} radii ${rUpper.roundToInt()},${rFore.roundToInt()},${rHand.roundToInt()}")
        return Arm(shoulder, elbow, wrist, floatArrayOf(rUpper, rFore, rHand))
    }
    val armNear = arm("near")
    val armFar = arm("far")

    // ---- Legs. The gap between them gives the seam (its top) and where the feet touch (its bottom).
    val hipNearG = guess("hip_near"); val hipFarG = guess("hip_far")
    val ankNearG = guess("ankle_near"); val ankFarG = guess("ankle_far")
    fun lineX(a: Pt, b: Pt, y: Float) = a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y)
    val legGap = flood(background(guess("gap_legs"))) { x, y ->
        y >= hipNearG.y && x > lineX(hipNearG, ankNearG, y.toFloat()) && x < lineX(hipFarG, ankFarG, y.toFloat())
    }
    val gapRows = legGap.groupBy { it / sil.w }.toSortedMap()
    val apexY = gapRows.firstKey()
    val gapBottom = gapRows.lastKey()
    val feetTouch = gapBottom < sil.box.y1 - 2
    fun gapMid(y: Int) = gapRows[y]?.let { r -> (r.minOf { it % sil.w } + r.maxOf { it % sil.w }) / 2f + .5f }
    val apex = Pt(gapMid(apexY)!!, apexY.toFloat())
    // Hip line at the crotch, unless the gap starts far from the template's hips (a skirt, or legs pressed together).
    val hipY = if (abs(apexY - hipNearG.y) < 25 * s) apexY else hipNearG.y.roundToInt()
    // Between the legs the cut lines run along the legs' mean direction (from the template).
    val slope = ((ankNearG.x - hipNearG.x) / (ankNearG.y - hipNearG.y) + (ankFarG.x - hipFarG.x) / (ankFarG.y - hipFarG.y)) / 2
    // Seam: from above the hip line down the gap while it is a slit (the two thighs nearly touching).
    var slitEnd = apexY
    while (slitEnd < gapBottom && (gapRows[slitEnd + 1]?.size ?: 0) < 4) slitEnd++
    val seamTopY = hipY - 6f
    val seam = Seg(apex.x + slope * (seamTopY - apex.y), seamTopY, apex.x + slope * (slitEnd + 6f - apex.y), slitEnd + 6f)
    // Feet: from where they first touch (the gap's bottom) to the notch between the soles in the bottom edge below it
    // (straight down along the legs if there is none).
    val shoes = if (feetTouch) {
        val bx = gapMid(gapBottom)!!
        val by = gapBottom + 1f
        val span = (40 * s).roundToInt()
        val bottomOf = IntArray(2 * span + 1) { i -> val x = bx.roundToInt() - span + i; (gapBottom..sil.box.y1).lastOrNull { sil.at(x, it) } ?: -1 }
        val reachN = (25 * s).roundToInt()
        var notch: Pt? = null; var depth = 1
        for (i in bottomOf.indices) {
            if (bottomOf[i] < 0) continue
            val left = (max(0, i - reachN) until i).maxOfOrNull { bottomOf[it] } ?: continue
            val right = (i + 1..min(bottomOf.size - 1, i + reachN)).maxOfOrNull { bottomOf[it] } ?: continue
            val dpt = min(left, right) - bottomOf[i]
            if (dpt > depth) { depth = dpt; notch = Pt(bx.roundToInt() - span + i + .5f, bottomOf[i] + 1f) }
        }
        val ex = notch?.x ?: (bx + slope * (sil.box.y1 - by)); val ey = notch?.y ?: sil.box.y1.toFloat()
        val ux = (ex - bx) / (ey - by); val y0 = by - 15f; val y1 = max(ey, sil.box.y1.toFloat()) + 8f
        log("feet touch at ${bx.roundToInt()},${by.roundToInt()}, notch below at ${notch?.let { "${it.x.roundToInt()},${it.y.roundToInt()}" } ?: "none"}")
        Seg(bx + ux * (y0 - by), y0, bx + ux * (y1 - by), y1)
    } else Seg(apex.x, gapBottom - 1f, apex.x, gapBottom.toFloat()) // in the gap: blocks nothing
    log("leg gap: top ${apex.x.roundToInt()},$apexY bottom y=$gapBottom feet touch=$feetTouch")
    fun divX(y: Int): Float = when {
        y < apexY -> seam.x1 + (seam.x2 - seam.x1) * (y - seam.y1) / (seam.y2 - seam.y1)
        y <= gapBottom && gapMid(y) != null -> gapMid(y)!!
        else -> shoes.x1 + (shoes.x2 - shoes.x1) * (y - shoes.y1) / (shoes.y2 - shoes.y1)
    }

    class Leg(val axisA: Float, val axisB: Float, val widths: Map<Int, Int>) {
        fun x(y: Int) = axisA + axisB * y
    }
    fun legRun(y: Int, near: Boolean, x0: Float): IntRange? {
        val dv = divX(y)
        val x = if (near) min(x0, dv - 2) else max(x0, dv + 2)
        val r = sil.run(x.roundToInt(), y, (40 * s).roundToInt()) ?: return null
        val first = if (near) r.first else max(r.first, kotlin.math.ceil(dv).toInt())
        val last = if (near) min(r.last, kotlin.math.floor(dv).toInt()) else r.last
        return if (last >= first) first..last else null
    }
    fun leg(near: Boolean): Leg {
        val hip = if (near) hipNearG else hipFarG; val ank = if (near) ankNearG else ankFarG
        val rows = (apexY + (10 * s).roundToInt()..(if (feetTouch) gapBottom else ank.y.roundToInt()) - (10 * s).roundToInt())
        val centres = rows.mapNotNull { y -> legRun(y, near, lineX(hip, ank, y.toFloat()))?.let { Pt((it.first + it.last) / 2f, y.toFloat()) } }
        val (a, b) = fitX(centres)
        val ws = (hipY..sil.box.y1).associateWith { y -> legRun(y, near, a + b * y)?.let { it.last - it.first + 1 } ?: 0 }
        return Leg(a, b, ws)
    }
    val legNear = leg(true)
    val legFar = leg(false)

    // Knee: a thigh garment that ends above the knee (shorts, a skirt) shows as a step in leg width; the shin starts
    // below it. With no step the knee is halfway down and cut straight across.
    class LegJoints(val knee: Pt, val ankle: Pt, val step: Int?, val seedThigh: Pt, val seedLeg: Pt)
    fun joints(leg: Leg, side: String, ignoreStep: Boolean = false, thighTop: Int = hipY): LegJoints {
        val ws = leg.widths
        val bottom = sil.box.y1 - 1
        val shinFrom = apexY + (15 * s).roundToInt(); val shinTo = (if (feetTouch) gapBottom else guesses.getValue("ankle_$side").y.roundToInt()) - (25 * s).roundToInt()
        val shin = (shinFrom..shinTo).map { ws.getValue(it) }.filter { it > 0 }.sorted()
        val shinW = shin[shin.size / 2]
        val step = if (ignoreStep) null else (hipY..(hipY + bottom) / 2).lastOrNull { ws.getValue(it) > shinW * 1.3f }
        val kneeY = if (step != null) step + 1 + (7 * s).roundToInt() else (hipY + guesses.getValue("ankle_$side").y.roundToInt()) / 2
        // Ankle: below the narrowest part of the shin, the first row where the foot makes the leg clearly wider.
        val lowFrom = kneeY + ((bottom - kneeY) * 0.3f).roundToInt()
        val narrow = (lowFrom..bottom - (20 * s).roundToInt()).minBy { ws.getValue(it).let { w -> if (w == 0) Int.MAX_VALUE else w } }
        val minW = ws.getValue(narrow)
        val widenY = (narrow..bottom).first { ws.getValue(it) > minW * 1.25f }
        // The shoe's top edge is usually above where the foot widens: from the widening, go up while the rows keep
        // the shoe's colour; the shoe starts where they stop (a sock stripe higher up is a different colour band).
        fun rowColour(y: Int): Int? {
            val r = legRun(y, side == "near", leg.x(y)) ?: return null
            var n = 0; var sr = 0; var sg = 0; var sb = 0
            for (x in r) { val c = img[x, y]; if (a(c) > 230) { n++; sr += r(c); sg += g(c); sb += b(c) } }
            return if (n < 3) null else argb(255, sr / n, sg / n, sb / n)
        }
        val shoeCol = rowColour(widenY + (3 * s).roundToInt()) ?: rowColour(widenY)
        var edgeY = widenY
        if (shoeCol != null) while (edgeY - 1 > narrow && rowColour(edgeY - 1)?.let { dist(it, shoeCol) < 70 } == true) edgeY--
        // Only a clear colour change counts (a bare foot or a same-colour sock has none).
        val edgeD = rowColour(edgeY - 2)?.let { c -> shoeCol?.let { dist(c, it) } } ?: 0
        val ankleY = if (edgeY < widenY && edgeD >= 80) edgeY - 1 else widenY
        fun centre(y: Int) = legRun(y, side == "near", leg.x(y))!!.let { (it.first + it.last) / 2f }
        val thighY = if (step != null) (hipY + step) / 2 else (thighTop + kneeY) / 2
        log("leg $side: shin ${shinW}px, step ${step ?: "none"}, knee y=$kneeY, narrowest $minW px at y=$narrow, ankle y=$ankleY (widens at $widenY, colour edge $edgeD at $edgeY)")
        return LegJoints(
            Pt(centre(kneeY), kneeY.toFloat()), Pt(centre(ankleY), ankleY.toFloat()), step,
            Pt(centre(thighY), thighY.toFloat()), Pt(centre((kneeY + ankleY) / 2), ((kneeY + ankleY) / 2).toFloat()),
        )
    }
    var jNear = joints(legNear, "near")
    var jFar = joints(legFar, "far")
    val steps = listOfNotNull(jNear.step, jFar.step)
    val cuffs = steps.size == 2
    // A skirt: the gap between the legs starts right at the garment's hem (shorts end well below the crotch).
    // The whole skirt then stays on the pelvis, the thighs are the skin below it, and the knees come from the
    // template's proportions since the hem says nothing about them.
    val skirt = cuffs && apexY >= steps.min() - (4 * s).roundToInt()
    if (skirt) {
        log("skirt: the gap starts at the hem y=$apexY; hips turn at y=$hipY, cut at the hem")
        jNear = joints(legNear, "near", ignoreStep = true, thighTop = apexY)
        jFar = joints(legFar, "far", ignoreStep = true, thighTop = apexY)
    }
    // Shins start where the thigh garment ends (the leg colour test keeps the rest of it out).
    val legTop = if (cuffs && !skirt) steps.average().roundToInt() else min(jNear.knee.y, jFar.knee.y).roundToInt()

    // Garment over the thighs (and the pelvis): its colours, sampled on both thighs from just above the hip line down
    // to where it ends.
    val bandPx = ArrayList<Int>()
    val bandTo = if (cuffs) steps.min() - 1 else hipY + (15 * s).roundToInt()
    for (y in hipY - (15 * s).roundToInt()..bandTo) for (near in listOf(true, false)) {
        val lg = if (near) legNear else legFar
        val r = legRun(y, near, lg.x(y)) ?: continue
        for (x in r) if (a(img[x, y]) > 230) bandPx += y * sil.w + x
    }
    val garment = palette(img, bandPx)
    val isGarment = { c: Int -> garment.any { dist(c, it) <= 60 } }
    log("thigh garment colours: " + garment.joinToString(" ") { "%06x".format(it and 0xFFFFFF) })

    // Hip lines: across each thigh at the crotch, meeting at the seam.
    val cutY = if (skirt) apexY else hipY
    val seamAtHip = seam.x1 + (seam.x2 - seam.x1) * (cutY - seam.y1) / (seam.y2 - seam.y1)
    val hipNear = Seg(legRun(cutY, true, legNear.x(cutY))!!.first - 8f, cutY.toFloat(), seamAtHip + 4f, cutY.toFloat())
    val hipFar = Seg(seamAtHip - 2f, cutY.toFloat(), legRun(cutY, false, legFar.x(cutY))!!.last + 8f, cutY.toFloat())

    // Pivots of the pelvis and the torso: the template's, measured up from the hip line, on the body's centre.
    // Torso length varies (big heads push the shoulders down), so they keep the template's share of neck-to-hip.
    fun share(n: String) = (template["hip_near"].y - template[n].y) / (template["hip_near"].y - template["neck_bottom"].y)
    val rootY = hipY - share("root") * (hipY - neckY)
    val waistY = hipY - share("waist") * (hipY - neckY)
    val rootRun = sil.run(seamAtHip.roundToInt(), rootY.roundToInt(), 10)!!
    val bodyMidX = (rootRun.first + rootRun.last) / 2f
    val root = Pt(bodyMidX, rootY)
    val waist = Pt(bodyMidX, waistY)
    guess("root"); guess("waist")

    // ---- Head. Skin: the most common colour low on the head (cheeks, chin), which no hairstyle covers in this
    // pose. Hair: the colour at the top of the head, unless that is skin (a bald head), then the most common
    // colour of the upper head far from the skin (hair on the sides), seeded at its pixel nearest the top.
    var hx0 = sil.w; var hx1 = 0
    for (y in sil.box.y0 until neckY) for (x in sil.box.x0 until sil.box.x1) if (sil.at(x, y)) { hx0 = min(hx0, x); hx1 = max(hx1, x) }
    val headMid = Pt((hx0 + hx1) / 2f, (sil.box.y0 + neckY) / 2f)
    val topY = (0 until sil.h).first { sil.at(headC.x.roundToInt(), it) }
    val headPx = ArrayList<Int>()
    for (y in sil.box.y0 until neckY) for (x in sil.box.x0 until sil.box.x1) if (sil.at(x, y) && a(img[x, y]) > 230) headPx += y * sil.w + x
    fun mostCommon(px: List<Int>): Int {
        val pal = palette(img, px, 0f)
        return pal.maxBy { c -> px.count { dist(img.px[it], c) < 24 } }
    }
    val headH = neckY - topY
    // Seen from behind the head is hair: the skin is the neck's colour.
    val skinCol = if (!face) img[bodyX, narrowY]
        else mostCommon(headPx.filter { val y = it / sil.w; y >= topY + .55f * headH && y <= topY + .85f * headH })
    var seedHair = Pt(headC.x.roundToInt().toFloat(), topY + (12 * s).roundToInt().toFloat())
    var hairCol = img[seedHair.x.toInt(), seedHair.y.toInt()]
    if (dist(hairCol, skinCol) < 90) {
        val upper = headPx.filter { it / sil.w < topY + .6f * headH && dist(img.px[it], skinCol) > 150 }
        require(upper.isNotEmpty()) { "bald head with no hair at all: not supported yet" }
        val c = mostCommon(upper)
        val p0 = upper.filter { dist(img.px[it], c) < 24 }.minBy { it / sil.w }
        seedHair = Pt((p0 % sil.w).toFloat(), (p0 / sil.w).toFloat())
        hairCol = img.px[p0]
        log("bald top of the head: hair taken from the sides")
    }
    val hairSkin = dist(hairCol, skinCol)
    val isHair = { c: Int -> a(c) > 100 && dist(c, hairCol) < hairSkin * 0.4f }
    val isHairEdge = { c: Int -> dist(c, skinCol) > hairSkin * 0.25f }
    val isSkin = { c: Int -> dist(c, skinCol) < 70 }
    // Painted features contrast less with darker skin: the threshold follows the skin's brightness (110 for Kai's tan).
    val contrast = (110 * (r(skinCol) * .299f + g(skinCol) * .587f + b(skinCol) * .114f) / 190f).roundToInt().coerceIn(50, 110)
    log("hair colour ${"%06x".format(hairCol and 0xFFFFFF)} at ${seedHair.x.roundToInt()},${seedHair.y.roundToInt()}, skin ${"%06x".format(skinCol and 0xFFFFFF)}, feature contrast $contrast")

    // Face features: blobs inside the face that contrast with the skin (same test as when erasing them).
    class Blob(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val area: Int) {
        val cx get() = (x0 + x1) / 2f
        val cy get() = (y0 + y1) / 2f
        val bw get() = x1 - x0 + 1
        val bh get() = y1 - y0 + 1
    }
    val fx0 = sil.box.x0; val fy0 = sil.box.y0; val fw = sil.box.x1 - fx0; val fh = neckY - fy0
    // The hair itself: hair-coloured pixels connected to the seed (eyes and brows can be just as dark).
    val hairMask = BooleanArray(fw * fh)
    run {
        val st = ArrayDeque<Int>()
        val s0 = (seedHair.y.toInt() - fy0) * fw + seedHair.x.toInt() - fx0
        st += s0; hairMask[s0] = true
        while (st.isNotEmpty()) {
            val q = st.removeLast(); val x = q % fw; val y = q / fw
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until fw || ny !in 0 until fh) continue
                val k = ny * fw + nx
                if (!hairMask[k] && sil.at(nx + fx0, ny + fy0) && isHair(img[nx + fx0, ny + fy0])) { hairMask[k] = true; st += k }
            }
        }
    }
    val non = BooleanArray(fw * fh) { i ->
        val x = fx0 + i % fw; val y = fy0 + i / fw
        val c = img[x, y]
        sil.at(x, y) && a(c) > 100 && dist(c, skinCol) >= contrast && !hairMask[i]
    }
    val seen = BooleanArray(fw * fh)
    val blobs = ArrayList<Blob>()
    for (s0 in non.indices) {
        if (!non[s0] || seen[s0]) continue
        var bx0 = Int.MAX_VALUE; var by0 = Int.MAX_VALUE; var bx1 = -1; var by1 = -1; var n = 0
        var open = false
        val st = ArrayDeque<Int>(); st += s0; seen[s0] = true
        while (st.isNotEmpty()) {
            val q = st.removeLast(); n++
            val x = q % fw; val y = q / fw
            bx0 = min(bx0, x); by0 = min(by0, y); bx1 = max(bx1, x); by1 = max(by1, y)
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until fw || ny !in 0 until fh) { open = true; continue }
                val k = ny * fw + nx
                // A feature sits on skin: touching the hair means a lock. Touching the face contour is fine (the far
                // eye in a three-quarter view often does).
                if (hairMask[k]) open = true
                if (non[k] && !seen[k]) { seen[k] = true; st += k }
            }
        }
        if (n >= 15 && debug) log("  face blob ${bx0 + fx0},${by0 + fy0}-${bx1 + fx0},${by1 + fy0} area $n${if (open) " OPEN (touches hair)" else ""}")
        if (!open && n >= 15) blobs += Blob(bx0 + fx0, by0 + fy0, bx1 + fx0, by1 + fy0, n)
    }
    // Eyes: the best pair of roundish blobs side by side at about the same height and of similar size (an ear
    // crease or a cheek alone is never chosen).
    val round = blobs.filter { it.bh.toFloat() / it.bw in 0.6f..1.8f }
    var eyes: List<Blob> = emptyList()
    var bestScore = 0
    if (face) for (i in round.indices) for (j in round.indices) {
        val a0 = round[i]; val b0 = round[j]
        if (a0.cx >= b0.cx) continue
        val h = max(a0.bh, b0.bh); val w = max(a0.bw, b0.bw)
        if (abs(a0.cy - b0.cy) > .6f * h || b0.cx - a0.cx < 1.2f * w || b0.cx - a0.cx > 5f * w) continue
        if (min(a0.area, b0.area) < .3f * max(a0.area, b0.area)) continue
        val score = min(a0.area, b0.area)
        if (score > bestScore) { bestScore = score; eyes = listOf(a0, b0) }
    }
    require(!face || eyes.size == 2) { "could not find two eyes" }
    fun browOf(e: Blob) = blobs.filter { it !== e && it.y1 < e.y0 && abs(it.cx - e.cx) < e.bw && it.bw > 1.5f * it.bh }.minByOrNull { e.y0 - it.y1 }
        .also { if (it == null) log("WARNING no brow above the eye at ${e.cx},${e.cy}: brows stay painted on the head (not swappable)") }
    val browN = eyes.getOrNull(0)?.let(::browOf); val browF = eyes.getOrNull(1)?.let(::browOf)
    val mouth = if (!face) null else {
        val eyeBottom = eyes.maxOf { it.cy } + 0.6f * eyes.maxOf { it.bh }
        blobs.filter { it.cy > eyeBottom && it.cx in eyes[0].cx..eyes[1].cx && it.bw > it.bh }.maxByOrNull { it.bw } ?: error("no mouth found")
    }
    // Search area of each feature: its box plus a margin that stays clear of every other blob.
    fun area(f: Blob): Box {
        var pad = 8
        fun box(p: Int) = Box(f.x0 - p, f.y0 - p, f.x1 + 1 + p, f.y1 + 1 + p)
        while (pad > 2 && blobs.any { o -> o !== f && box(pad).let { b -> o.x0 < b.x1 && o.x1 >= b.x0 && o.y0 < b.y1 && o.y1 >= b.y0 } }) pad--
        return box(pad)
    }

    // ---- Seeds.
    fun onPt(x: Float, y: Float, ok: (Int) -> Boolean = { true }): Pt {
        for (d in 0..40) for ((dx, dy) in listOf(0 to 0, d to 0, -d to 0, 0 to d, 0 to -d)) {
            val px = x.roundToInt() + dx; val py = y.roundToInt() + dy
            if (sil.at(px, py) && ok(img[px, py])) return Pt(px.toFloat(), py.toFloat())
        }
        error("no suitable pixel near $x,$y")
    }
    val seedPelvis = onPt(bodyMidX, hipY - 8 * s, isGarment)
    val legAllow: (Int) -> Boolean = if (cuffs) { c -> !isGarment(c) } else { _ -> true }

    val spec = RefSpec(
        neck = neck, shoulderNear = armNear.shoulder, shoulderFar = armFar.shoulder,
        hipNear = hipNear, hipFar = hipFar, seam = seam, shoes = shoes,
        elbowNear = armNear.elbow, wristNear = armNear.wrist, elbowFar = armFar.elbow, wristFar = armFar.wrist,
        kneeNear = jNear.knee, kneeFar = jFar.knee, ankleNear = jNear.ankle, ankleFar = jFar.ankle,
        armNearRadii = armNear.radii, armFarRadii = armFar.radii, legTop = legTop,
        root = root, waist = waist,
        seedHead = onPt(headMid.x, headMid.y), seedHair = seedHair, hairPivot = headMid,
        seedTorso = onPt(bodyMidX, (neckY + waistY) / 2),
        seedPelvis = seedPelvis,
        seedArmNear = onPt(armNear.elbow.x, armNear.elbow.y), seedArmFar = onPt(armFar.elbow.x, armFar.elbow.y),
        seedThighNear = onPt(jNear.seedThigh.x, jNear.seedThigh.y), seedThighFar = onPt(jFar.seedThigh.x, jFar.seedThigh.y),
        seedLegNear = onPt(jNear.seedLeg.x, jNear.seedLeg.y, legAllow), seedLegFar = onPt(jFar.seedLeg.x, jFar.seedLeg.y, legAllow),
        eyeNearArea = eyes.getOrNull(0)?.let(::area), eyeFarArea = eyes.getOrNull(1)?.let(::area),
        browNearArea = browN?.let(::area), browFarArea = browF?.let(::area), mouthArea = mouth?.let(::area),
        colours = CutColours(arm = { true }, leg = legAllow, pelvis = isGarment, skin = isSkin, hair = isHair, hairEdge = isHairEdge),
        featureContrast = contrast,
        hipPivotY = if (skirt) hipY.toFloat() else null,
        view = view, splitHair = face,
    )
    return AutoResult(spec, guesses, sil)
}

internal fun cutAuto(dir: File, outDir: File, previewDir: File, compareKai: Boolean, view: String = "3q") {
    val image = if (view == "3q") "ref.png" else "view_$view.png"
    val template = if (view == "3q") TEMPLATE else File("assets/templates/rig_$view.png")
    val img = defringe(keyMagenta(Img.load(File(dir, image))))
    val res = autoSpec(img, RigTemplate.load(template), debug = true, view = view)
    if (compareKai) compareWithKai(res.spec)
    drawPoints(File(previewDir, "points.png"), img, res, if (compareKai) KaiRefSpec.spec() else null)
    cutWith(dir, outDir, previewDir, res.spec)
}

/** Every point of [auto] next to KaiRefSpec's hand-measured one, with the distance in px. */
fun compareWithKai(auto: RefSpec, expected: RefSpec = KaiRefSpec.spec(), label: String = "manual") {
    println("point                 auto            $label          error px")
    fun row(n: String, a: Pt, m: Pt) =
        println("%-20s %6.1f,%6.1f   %6.1f,%6.1f   %5.1f".format(n, a.x, a.y, m.x, m.y, hypot(a.x - m.x, a.y - m.y)))
    fun seg(n: String, a: Seg, m: Seg) {
        row("$n.mid", mid(a), mid(m))
        val angA = Math.toDegrees(kotlin.math.atan2((a.y2 - a.y1).toDouble(), (a.x2 - a.x1).toDouble()))
        val angM = Math.toDegrees(kotlin.math.atan2((m.y2 - m.y1).toDouble(), (m.x2 - m.x1).toDouble()))
        var dAng = abs(angA - angM) % 180; if (dAng > 90) dAng = 180 - dAng
        println("%-20s len %5.1f vs %5.1f, angle off %4.1f deg".format("", hypot(a.x2 - a.x1, a.y2 - a.y1), hypot(m.x2 - m.x1, m.y2 - m.y1), dAng))
    }
    seg("neck", auto.neck, expected.neck)
    seg("shoulderNear", auto.shoulderNear, expected.shoulderNear)
    seg("shoulderFar", auto.shoulderFar, expected.shoulderFar)
    seg("hipNear", auto.hipNear, expected.hipNear)
    seg("hipFar", auto.hipFar, expected.hipFar)
    seg("seam", auto.seam, expected.seam)
    seg("shoes", auto.shoes, expected.shoes)
    row("elbowNear", auto.elbowNear, expected.elbowNear); row("wristNear", auto.wristNear, expected.wristNear)
    row("elbowFar", auto.elbowFar, expected.elbowFar); row("wristFar", auto.wristFar, expected.wristFar)
    row("kneeNear", auto.kneeNear, expected.kneeNear); row("kneeFar", auto.kneeFar, expected.kneeFar)
    row("ankleNear", auto.ankleNear, expected.ankleNear); row("ankleFar", auto.ankleFar, expected.ankleFar)
    row("root", auto.root, expected.root); row("waist", auto.waist, expected.waist)
    row("hairPivot", auto.hairPivot, expected.hairPivot)
    println("legTop               ${auto.legTop}   ${expected.legTop}")
    println("armNearRadii         ${auto.armNearRadii.joinToString { "%.0f".format(it) }}   ${expected.armNearRadii.joinToString { "%.0f".format(it) }}")
    println("armFarRadii          ${auto.armFarRadii.joinToString { "%.0f".format(it) }}   ${expected.armFarRadii.joinToString { "%.0f".format(it) }}")
    fun box(n: String, a: Box, m: Box) = println("%-20s %d,%d-%d,%d   %d,%d-%d,%d".format(n, a.x0, a.y0, a.x1, a.y1, m.x0, m.y0, m.x1, m.y1))
    box("eyeNearArea", auto.eyeNearArea!!, expected.eyeNearArea!!); box("eyeFarArea", auto.eyeFarArea!!, expected.eyeFarArea!!)
    auto.browNearArea?.let { box("browNearArea", it, expected.browNearArea!!) }; auto.browFarArea?.let { box("browFarArea", it, expected.browFarArea!!) }
    box("mouthArea", auto.mouthArea!!, expected.mouthArea!!)
}

/** points.png: template guesses (blue), measured cut lines and joints (green), and [expected] ones (red) if given. */
fun drawPoints(f: File, img: Img, res: AutoResult, expected: RefSpec? = null) {
    val bi = java.awt.image.BufferedImage(img.w, img.h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = bi.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color.WHITE; g.fillRect(0, 0, img.w, img.h)
    g.drawImage(img.toBuffered(), 0, 0, null)
    g.stroke = BasicStroke(2f)
    fun spec(sp: RefSpec, col: Color) {
        g.color = col
        for (sg in listOf(sp.neck, sp.shoulderNear, sp.shoulderFar, sp.hipNear, sp.hipFar, sp.seam, sp.shoes))
            g.drawLine(sg.x1.roundToInt(), sg.y1.roundToInt(), sg.x2.roundToInt(), sg.y2.roundToInt())
        for (p in listOf(sp.elbowNear, sp.wristNear, sp.elbowFar, sp.wristFar, sp.kneeNear, sp.kneeFar, sp.ankleNear, sp.ankleFar, sp.root, sp.waist))
            g.drawOval(p.x.roundToInt() - 5, p.y.roundToInt() - 5, 10, 10)
        for (b in listOfNotNull(sp.eyeNearArea, sp.eyeFarArea, sp.browNearArea, sp.browFarArea, sp.mouthArea)) g.drawRect(b.x0, b.y0, b.x1 - b.x0, b.y1 - b.y0)
    }
    if (expected != null) spec(expected, Color.RED)
    spec(res.spec, Color(0, 170, 0))
    g.color = Color.BLUE
    for (p in res.guesses.values) { g.fillOval(p.x.roundToInt() - 4, p.y.roundToInt() - 4, 8, 8) }
    g.dispose()
    f.parentFile.mkdirs()
    ImageIO.write(bi, "png", f)
}
