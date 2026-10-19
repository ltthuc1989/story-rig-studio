package com.example.storyrig.tools

import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

/*
 * Limb tubes for a cut (hoses.csv, read by KaiAssets.HoseRow): the arms and legs drawn as skinned vector tubes like
 * Ben's in the lake scene, so a knee or an elbow bends as one smooth curve instead of two pictures meeting at a hinge.
 * Measured from the cut's own limb pictures, so the tubes keep the design's colours and widths:
 *   - along each limb's axis (shoulder -> elbow -> wrist, hip -> knee -> ankle; t 0..1 on the upper bone, 1..2 on the
 *     lower), a cross-section every 1% of a bone: the run of opaque pixels through the axis gives the width, its
 *     middle the colour;
 *   - samples within SAME_GARMENT of each other are one colour (a garment and its shading); runs along t of one
 *     colour are the stretches: sleeve, cuff, skin, shorts, hem, sock, stripes;
 *   - the skin (the hand's colour and every shade near it, the shadow under a hem included) is one tube the whole
 *     length, round ends; every other stretch is drawn over it at its own width with butt ends (a hem is a straight
 *     edge; at the body the cut's shoulder ball and pelvis cover the start).
 *   hosespec <cutDir>      writes <cutDir>/hoses.csv (cutview runs it)
 */
/** How far (RGB L1) a limb colour may be from the hand's and still be skin: its shading, the shadow under a hem. */
private const val SKIN_SHADE = 110

internal fun hoseSpec(outDir: File) {
    val bones = File(outDir, "bones.csv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(",") }
    val pivot = bones.associate { it[0] to floatArrayOf(it[2].toFloat(), it[3].toFloat()) }
    val rows = File(outDir, "parts.csv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(",") }
    class Pic(val img: Img, val x: Int, val y: Int) {
        fun at(rx: Float, ry: Float): Int { val ix = (rx - x).toInt(); val iy = (ry - y).toInt(); return if (img.inside(ix, iy)) img[ix, iy] else 0 }
    }
    fun pic(name: String) = rows.firstOrNull { it[0] == name }?.let { Pic(Img.load(File(outDir, "parts/$name.png")), it[2].toInt(), it[3].toInt()) }
    val out = ArrayList<String>()
    for (side in listOf("near", "far")) for (limb in listOf("arm", "leg")) {
        val (b0, b1, b2) = if (limb == "arm") Triple("shoulder_$side", "elbow_$side", "wrist_$side") else Triple("hip_$side", "knee_$side", "ankle_$side")
        val (p0, p1) = if (limb == "arm") "arm_upper_$side" to "arm_fore_$side" else "thigh_$side" to "shin_$side"
        val upper = pic(p0); val lower = pic(p1)
        if (upper == null || lower == null || listOf(b0, b1, b2).any { it !in pivot }) continue
        // Skin is the hand's colour, for the legs too (a shin's main colour can be its sock).
        val hand = pic("hand_$side")
        val a = pivot.getValue(b0); val b = pivot.getValue(b1); val c = pivot.getValue(b2)
        // Cross-sections.
        class Sample(val t: Float, val width: Float, val colour: Int)
        val samples = ArrayList<Sample>()
        for (i in 0..200) {
            val t = i / 100f
            val (s, e, pc) = if (t < 1f) Triple(a, b, upper) else Triple(b, c, lower)
            val f = if (t < 1f) t else t - 1f
            val ux = e[0] - s[0]; val uy = e[1] - s[1]; val l = hypot(ux, uy)
            val vx = -uy / l; val vy = ux / l
            val cx = s[0] + ux * f; val cy = s[1] + uy * f
            // The opaque run through the axis (or the nearest one within 8 px of it).
            fun opaque(k: Float) = a(pc.at(cx + vx * k, cy + vy * k)) > 127
            var mid = Float.NaN
            for (d in 0..16) { val k = d / 2f; if (opaque(k)) { mid = k; break }; if (opaque(-k)) { mid = -k; break } }
            if (mid.isNaN()) continue
            var lo = mid; while (lo > mid - 120f && opaque(lo - .5f)) lo -= .5f
            var hi = mid; while (hi < mid + 120f && opaque(hi + .5f)) hi += .5f
            val w = hi - lo
            if (w < 4f) continue
            // Colour: the middle half of the run, most common colour there.
            val cols = ArrayList<Int>()
            var k = lo + w * .25f
            while (k <= hi - w * .25f) { cols += pc.at(cx + vx * k, cy + vy * k); k += .5f }
            val col = mainColours(cols, .2f).firstOrNull() ?: continue
            samples += Sample(t, w, col)
        }
        if (samples.size < 20) { println("  $limb $side: ít mẫu (${samples.size}), bỏ"); continue }
        // Skin: the hand's main colour; every shade within SKIN_SHADE of it (the shadow under a hem, the elbow) is skin.
        val ref = hand?.let { h -> mainColours(h.img.px.filter { a(it) > 200 }, .2f).firstOrNull() } ?: samples.last().colour
        // Colour classes: one per garment (shades within SAME_GARMENT merge), class 0 the skin.
        val classes = arrayListOf(ref)
        val cls = samples.map { s ->
            if (colourDist(s.colour, ref) <= SKIN_SHADE) 0
            else classes.indexOfFirst { colourDist(it, s.colour) <= SAME_GARMENT }.let { if (it > 0) it else { classes += s.colour; classes.size - 1 } }
        }.toMutableList()
        // Smooth: a sample unlike both neighbours takes theirs (anti-aliased seams, a crease).
        for (i in 1 until cls.size - 1) if (cls[i - 1] == cls[i + 1] && cls[i] != cls[i - 1]) cls[i] = cls[i - 1]
        // Runs along t, the short ones (under 3% of a bone) folded into the one before.
        class Run(val cl: Int, var t0: Float, var t1: Float, val widths: ArrayList<Float>, val colours: ArrayList<Int>)
        val runs = ArrayList<Run>()
        for ((i, s) in samples.withIndex()) {
            val last = runs.lastOrNull()
            if (last != null && last.cl == cls[i]) { last.t1 = s.t; last.widths += s.width; last.colours += s.colour }
            else runs += Run(cls[i], s.t, s.t, arrayListOf(s.width), arrayListOf(s.colour))
        }
        val merged = ArrayList<Run>()
        for (r in runs) {
            val prev = merged.lastOrNull()
            if (prev != null && (r.t1 - r.t0 < .03f || prev.cl == r.cl)) { prev.t1 = r.t1; if (prev.cl == r.cl) { prev.widths += r.widths; prev.colours += r.colours } }
            else merged += r
        }
        val skin = 0
        fun median(xs: List<Float>) = xs.sorted()[xs.size / 2]
        fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
        val skinRuns = merged.filter { it.cl == skin }
        if (skinRuns.isEmpty()) { println("  $limb $side: không thấy da, bỏ"); continue }
        val skinCol = mainColours(skinRuns.flatMap { it.colours }, .3f).first()
        out += "${limb}_$side,$b0,$b1,${hex(skinCol)},0,2,${"%.1f".format(java.util.Locale.ROOT, median(skinRuns.flatMap { it.widths }))},round"
        for (r in merged) if (r.cl != skin) {
            val col = mainColours(r.colours, .3f).first()
            // Ends: the stretch reaches a half sample further each way, so stretches meet without a gap. A shorts leg
            // that starts at the hip goes on up under the pelvis (t < 0), so the two legs meet behind the crotch.
            val t0 = if (limb == "leg" && r.t0 <= .02f) -.3f else (r.t0 - .005f).coerceAtLeast(0f)
            val t1 = (r.t1 + .005f).coerceAtMost(2f)
            // Butt ends (a stroke's cap is on both ends): a hem is a straight edge; at the body the cut's shoulder
            // ball or pelvis covers the end.
            val cap = "butt"
            out += "${limb}_$side,$b0,$b1,${hex(col)},${"%.3f".format(java.util.Locale.ROOT, t0)},${"%.3f".format(java.util.Locale.ROOT, t1)},${"%.1f".format(java.util.Locale.ROOT, median(r.widths) + 1f)},$cap"
        }
        println("  $limb $side: " + merged.joinToString(" ") { "${hex(classes[it.cl])}[${"%.2f".format(java.util.Locale.ROOT, it.t0)}-${"%.2f".format(java.util.Locale.ROOT, it.t1)} w${median(it.widths).roundToInt()}]" })
    }
    File(outDir, "hoses.csv").writeText(
        "# limb,upper bone,lower bone,colour,t0,t1 (0..1 upper bone, 1..2 lower),width (ref px),round|butt: limb tubes, HoseSpec.kt\n" + out.joinToString("\n") + "\n"
    )
    println("hosespec $outDir: ${out.size} đoạn ống")
}
