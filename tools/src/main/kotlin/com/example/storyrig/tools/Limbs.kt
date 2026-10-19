package com.example.storyrig.tools

import java.io.File
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Substitute drawings of a rigid cut (ART_PIPELINE.md section 1) from the limb sheet assets/<char>/design/limbs.png
 * (template rig_limbs, checked by limbcheck): what the engine would otherwise fake by squashing a picture. Every
 * piece is the character's right side; the far side gets it mirrored. Each one is
 *   - scaled to its part in the cut: upper arm and forearm pieces to the sleeve's width, the thigh and the shoe to
 *     the thigh's width, the hand from the back to the hand's width (Gemini draws each row at its own scale);
 *   - turned to the bone: arm pieces so they stand upright when the arm hangs (the clips point an arm at the viewer
 *     from hanging), the hand along the wrist's length axis, leg pieces as drawn (legs rest hanging);
 *   - placed with its joint (rig_limbs.csv jx,jy) on the bone's pivot.
 * Writes parts/<piece>_<side>.png, appends drawn=0 rows to parts.csv, and writes swaps.csv (KaiAssets.SwapRow).
 */
internal fun cutLimbs(sheet: File, outDir: File) {
    val keyed = defringe(keyMagenta(Img.load(sheet)))
    val k = keyed.w.toFloat() / FACE_W
    val (_, comps) = components(keyed, 15)
    class Slot(val name: String, val x0: Float, val y0: Float, val x1: Float, val y1: Float, val jx: Float, val jy: Float, val cx: Float, val cy: Float)
    val slots = File("assets/templates/rig_limbs.csv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { l ->
        val p = l.split(",")
        Slot(p[0], p[1].toFloat() * k, p[2].toFloat() * k, p[3].toFloat() * k, p[4].toFloat() * k, p[6].toFloat(), p[7].toFloat(), p[8].toFloat(), p[9].toFloat())
    }
    // One piece per slot: the blobs whose centre is in the slot (grown by half), as one image with its joints.
    class Piece(val img: Img, val jx: Float, val jy: Float, val cx: Float, val cy: Float)
    val pieces = slots.associate { s ->
        val mx = (s.x1 - s.x0) / 2; val my = (s.y1 - s.y0) / 2
        val mine = comps.filter { it.area >= 60 && it.cx.toFloat() in (s.x0 - mx)..(s.x1 + mx) && it.cy.toFloat() in (s.y0 - my)..(s.y1 + my) }
        require(mine.isNotEmpty()) { "tờ tay chân: ô ${s.name} trống (chạy limbcheck)" }
        // Everything in the blobs' box grown by 2 px, anti-aliased edge included (the pieces stand far apart).
        val x0 = max(0, mine.minOf { it.x0 } - 2); val y0 = max(0, mine.minOf { it.y0 } - 2)
        val x1 = min(keyed.w - 1, mine.maxOf { it.x1 } + 2); val y1 = min(keyed.h - 1, mine.maxOf { it.y1 } + 2)
        val img = Img(x1 - x0 + 1, y1 - y0 + 1)
        for (y in y0..y1) for (x in x0..x1) img[x - x0, y - y0] = keyed[x, y]
        s.name to Piece(img, s.jx * img.w, s.jy * img.h, s.cx * img.w, s.cy * img.h)
    }
    // The forearm piece alone: Gemini draws it as the whole arm pointing at the viewer, sleeve ring around the arm
    // end. With the upper arm hanging and only the forearm forward that ring sits at the elbow, so it goes: every
    // colour of the upper-arm piece (the same sleeve end on) that is not skin. Pointing the whole arm, the upper-arm
    // piece under it puts the ring back.
    run {
        fun pal(p: Piece, share: Float) = mainColours(p.img.px.filter { a(it) > 200 }, share)
        // Hand colours: skin and its shades (palm creases, the arm end), from the hand piece.
        val hand = pal(pieces.getValue("hand_back"), .01f)
        if (hand.isEmpty()) return@run
        fun near(c: Int, cs: List<Int>) = cs.minOf { colourDist(it, c) }
        val sleeve = pal(pieces.getValue("upper_toward"), .05f).filter { near(it, hand) > 90 }
        if (sleeve.isEmpty()) return@run
        val fore = pieces.getValue("fore_toward").img
        var n = 0
        for (i in fore.px.indices) {
            val c = fore.px[i]
            if (a(c) > 0 && near(c, sleeve) <= 60 && near(c, sleeve) < near(c, hand)) { fore.px[i] = 0; n++ }
        }
        println("  fore_toward: bỏ $n điểm ảnh tay áo (${sleeve.joinToString(" ") { "#%06X".format(it and 0xFFFFFF) }})")
    }
    val bones = File(outDir, "bones.csv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(",") }
    val pivot = bones.associate { it[0] to floatArrayOf(it[2].toFloat(), it[3].toFloat()) }
    val parentOf = bones.associate { it[0] to it[1] }
    val partsCsv = File(outDir, "parts.csv")
    val rows = partsCsv.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(",") }
    // Rows from an earlier run of this step are replaced.
    val kept = partsCsv.readLines().filter { l -> slots.none { s -> l.startsWith("${s.name}_near,") || l.startsWith("${s.name}_far,") } }
    fun partImg(name: String) = Img.load(File(outDir, "parts/$name.png"))
    fun partXY(name: String) = rows.first { it[0] == name }.let { floatArrayOf(it[2].toFloat(), it[3].toFloat()) }
    fun axis(bone: String): FloatArray {
        val child = bones.firstOrNull { it[1] == bone }?.get(0)
        val p = pivot.getValue(bone)
        val (dx, dy) = if (child != null) pivot.getValue(child).let { (it[0] - p[0]) to (it[1] - p[1]) } else {
            // End bone (hand, foot): towards the centre of its drawn parts, as KaiRig.lengthAxes.
            val r = rows.first { it[1] == bone && it[6] == "1" }
            (r[2].toFloat() + r[4].toFloat() / 2 - p[0]) to (r[3].toFloat() + r[5].toFloat() / 2 - p[1])
        }
        val l = hypot(dx, dy)
        return floatArrayOf(dx / l, dy / l, l)
    }
    /** Width of [part] across its bone's length axis (ref px). */
    fun thickness(part: String, bone: String): Float {
        val img = partImg(part); val o = partXY(part); val u = axis(bone)
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (y in 0 until img.h) for (x in 0 until img.w) if (a(img[x, y]) > 127) {
            val v = -(o[0] + x) * u[1] + (o[1] + y) * u[0]
            lo = min(lo, v); hi = max(hi, v)
        }
        return hi - lo
    }
    val out = ArrayList<String>(); val swaps = ArrayList<String>()
    for (side in listOf("near", "far")) {
        val mirror = side == "far"
        val shoulder = "shoulder_$side"; val elbow = "elbow_$side"; val wrist = "wrist_$side"
        val hip = "hip_$side"; val ankle = "ankle_$side"
        if (listOf(shoulder, elbow, wrist, hip, ankle).any { it !in pivot }) continue
        // Clip angle that makes the arm hang (KaiRig.hang), in radians.
        val hang = (Math.PI / 2 - atan2(pivot.getValue(wrist)[1] - pivot.getValue(shoulder)[1], pivot.getValue(wrist)[0] - pivot.getValue(shoulder)[0])).toFloat()
        val armScale = thickness("arm_upper_$side", shoulder) / pieces.getValue("upper_toward").img.w
        val legScale = thickness("thigh_$side", hip) / pieces.getValue("thigh_toward").img.w
        val handScale = thickness("hand_$side", wrist) / pieces.getValue("hand_back").img.w
        val wu = axis(wrist)
        val handTurn = (atan2(wu[1], wu[0]) + Math.PI / 2).toFloat()
        class Use(val piece: String, val part: String, val bone: String, val scale: Float, val turn: Float, val dir: String, val hides: String)
        val uses = listOf(
            Use("upper_toward", "arm_upper_$side", shoulder, armScale, -hang, "toward", ""),
            Use("fore_toward", "arm_fore_$side", elbow, armScale, -hang, "toward", "hand_$side"),
            Use("hand_back", "hand_$side", wrist, handScale, handTurn, "back", ""),
            Use("thigh_toward", "thigh_$side", hip, legScale, 0f, "toward", ""),
            Use("shoe_sole", "shoe_$side", ankle, legScale, 0f, "away", ""),
        )
        for (u in uses) {
            val pc = pieces.getValue(u.piece)
            val name = "${u.piece}_$side"
            val pv = pivot.getValue(u.bone)
            // Piece px -> ref px: mirror (far side), scale, turn, joint onto the pivot.
            val cs = cos(u.turn) * u.scale; val sn = sin(u.turn) * u.scale; val mx = if (mirror) -1f else 1f
            fun fx(x: Float, y: Float) = pv[0] + cs * (x - pc.jx) * mx - sn * (y - pc.jy)
            fun fy(x: Float, y: Float) = pv[1] + sn * (x - pc.jx) * mx + cs * (y - pc.jy)
            val xs = listOf(fx(0f, 0f), fx(pc.img.w.toFloat(), 0f), fx(0f, pc.img.h.toFloat()), fx(pc.img.w.toFloat(), pc.img.h.toFloat()))
            val ys = listOf(fy(0f, 0f), fy(pc.img.w.toFloat(), 0f), fy(0f, pc.img.h.toFloat()), fy(pc.img.w.toFloat(), pc.img.h.toFloat()))
            val ox = floor(xs.min()).toInt(); val oy = floor(ys.min()).toInt()
            val img = Img(ceil(xs.max()).toInt() - ox + 1, ceil(ys.max()).toInt() - oy + 1)
            // Inverse map with premultiplied bilinear sampling (as scaleImg).
            val ic = cos(-u.turn) / u.scale; val isn = sin(-u.turn) / u.scale
            for (y in 0 until img.h) for (x in 0 until img.w) {
                val qx = ox + x + .5f - pv[0]; val qy = oy + y + .5f - pv[1]
                val sx = (ic * qx - isn * qy) * mx + pc.jx - .5f; val sy = isn * qx + ic * qy + pc.jy - .5f
                val ix = floor(sx).toInt(); val iy = floor(sy).toInt(); val tx = sx - ix; val ty = sy - iy
                var sa = 0f; var sr = 0f; var sg = 0f; var sb = 0f
                for (q in 0 until 4) {
                    val px = ix + (q and 1); val py = iy + (q shr 1)
                    if (!pc.img.inside(px, py)) continue
                    val c = pc.img[px, py]
                    val al = a(c) / 255f * (if (q and 1 == 1) tx else 1 - tx) * (if (q shr 1 == 1) ty else 1 - ty)
                    sa += al; sr += r(c) * al; sg += g(c) * al; sb += b(c) * al
                }
                img[x, y] = if (sa <= .001f) 0 else argb((sa * 255).roundToInt().coerceAtMost(255), (sr / sa).roundToInt(), (sg / sa).roundToInt(), (sb / sa).roundToInt())
            }
            img.save(File(outDir, "parts/$name.png"))
            out += "$name,${u.bone},$ox,$oy,${img.w},${img.h},0"
            // Where the drawing has the child's joint, as a share of the bone's length (KaiRig puts the child there).
            val ax = axis(u.bone)
            val childFore = (((fx(pc.cx, pc.cy) - pv[0]) * ax[0] + (fy(pc.cx, pc.cy) - pv[1]) * ax[1]) / ax[2]).coerceIn(.02f, 1f)
            swaps += "${u.part},${u.dir},$name,${"%.3f".format(java.util.Locale.ROOT, childFore)},${u.hides}"
        }
    }
    partsCsv.writeText((kept + out).joinToString("\n") + "\n")
    File(outDir, "swaps.csv").writeText(
        "# part,dir (toward|away|back),swap part,child fore (share of the bone where its child starts),hides (;): substitute drawings, Limbs.kt\n" +
            swaps.joinToString("\n") + "\n"
    )
    println("cutlimbs $sheet -> $outDir: ${out.size} hình thay thế")
}
