package com.example.storyrig.tools

import java.awt.BasicStroke
import java.awt.Color
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.min

/*
 * Joint check by numbers (ART_PIPELINE.md section 3): tools/joint_sheet.sh <out> <cuts> green renders each cut in the
 * 16 STRESS poses on a flat magenta background (bg=0), from a copy of the cut in which every pixel the cutter invented
 * is painted green; then this counts, per pose, the green that shows and the holes in the figure.
 *   greencut <cutDir> <ref.png> <outDir>   the copy: a part pixel is invented when the reference image is transparent
 *                                          there or has a clearly different colour (the armpit painted as shirt, caps)
 *   jointcheck <sheetDir> <cut> [baseline]  reads <sheetDir>/<cut>/pose_NN.png, writes <sheetDir>/<cut>_jointcheck.txt,
 *                                          boxes the holes in <sheetDir>/<cut>/check_NN.png; compares with
 *                                          tools/jointcheck_baseline.txt (`baseline` rewrites that cut's lines, only
 *                                          after the user looked at every boxed hole: accepted holes then pass)
 * A tear is thin enclosed background (under 150 px, or about 12 px thick); fatter loops, like the one under an arm
 * resting on the hip, are listed, not counted.
 */
private const val GREEN = 0xFF00FF00.toInt()
private val BASELINE = File("tools/jointcheck_baseline.txt")

internal fun greenCut(cutDir: File, refPng: File, outDir: File) {
    val ref = defringe(keyMagenta(Img.load(refPng)))
    outDir.deleteRecursively(); File(outDir, "parts").mkdirs()
    for (n in listOf("bones.csv", "parts.csv", "mesh.csv", "character.csv")) File(cutDir, n).takeIf { it.exists() }?.copyTo(File(outDir, n))
    val rows = File(cutDir, "parts.csv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(",") }
    var total = 0
    for (r in rows) {
        val name = r[0]; val ox = r[2].toInt(); val oy = r[3].toInt()
        val img = Img.load(File(cutDir, "parts/$name.png"))
        // Face sprites are drawings, not joint fills; the head's erased features are under them.
        val sprite = name.startsWith("eye_") || name.startsWith("brow_") || name.startsWith("mouth_") || name == "nose"
        var n = 0
        if (!sprite) for (ly in 0 until img.h) for (lx in 0 until img.w) {
            val c = img[lx, ly]
            if (a(c) <= 127) continue
            val rx = ox + lx; val ry = oy + ly
            val rc = if (ref.inside(rx, ry)) ref[rx, ry] else 0
            val invented = a(rc) <= 127 || (name != "head" && colourDist(rc, c) > 40)
            if (invented) { img[lx, ly] = (c and (0xFF shl 24)) or (GREEN and 0xFFFFFF); n++ }
        }
        // Hands become solid mitts (their convex hull filled): the gaps between spread fingers, and between thumb and
        // index finger, close up when a hand hangs over the shorts and would count as holes in the figure.
        if (name.startsWith("hand_")) {
            val skin = mainColours(img.px.filter { a(it) > 200 }, .2f).firstOrNull() ?: 0xFFF2B387.toInt()
            val hull = convexHull(img)
            for (ly in 0 until img.h) for (lx in 0 until img.w) if (a(img[lx, ly]) <= 127 && insideHull(hull, lx + .5, ly + .5)) img[lx, ly] = skin
        }
        img.save(File(outDir, "parts/$name.png"))
        if (n > 0) println("  $name: $n điểm ảnh bịa")
        total += n
    }
    println("greencut $cutDir -> $outDir: $total điểm ảnh bịa")
}

/** Convex hull (monotone chain) of the opaque pixels' corners, as a counter-clockwise polygon. */
private fun convexHull(img: Img): List<DoubleArray> {
    val pts = ArrayList<DoubleArray>()
    for (y in 0 until img.h) for (x in 0 until img.w) if (a(img[x, y]) > 127) {
        pts += doubleArrayOf(x.toDouble(), y.toDouble()); pts += doubleArrayOf(x + 1.0, y + 1.0)
    }
    if (pts.size < 3) return pts
    pts.sortWith(compareBy({ it[0] }, { it[1] }))
    fun cross(o: DoubleArray, p: DoubleArray, q: DoubleArray) = (p[0] - o[0]) * (q[1] - o[1]) - (p[1] - o[1]) * (q[0] - o[0])
    val lower = ArrayList<DoubleArray>(); val upper = ArrayList<DoubleArray>()
    for (p in pts) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeLast(); lower += p }
    for (p in pts.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeLast(); upper += p }
    return lower.dropLast(1) + upper.dropLast(1)
}

private fun insideHull(hull: List<DoubleArray>, x: Double, y: Double): Boolean {
    if (hull.size < 3) return false
    for (i in hull.indices) {
        val p = hull[i]; val q = hull[(i + 1) % hull.size]
        if ((q[0] - p[0]) * (y - p[1]) - (q[1] - p[1]) * (x - p[0]) < 0) return false
    }
    return true
}

/** Largest distance (px) from a pixel of [comp]'s region of [inv] (opaque = enclosed background) to its boundary. */
private fun maxThickness(inv: Img, comp: Comp): Int {
    val w = inv.w
    var best = 0
    for (y in comp.y0..comp.y1) for (x in comp.x0..comp.x1) {
        if (a(inv[x, y]) <= 127) continue
        var r = 0
        while (r < 40 && (-r - 1..r + 1).all { dx -> (-r - 1..r + 1).all { dy -> val nx = x + dx; val ny = y + dy; inv.inside(nx, ny) && a(inv[nx, ny]) > 127 } }) r++
        if (r > best) best = r
    }
    return best
}

private class PoseResult(
    val pose: Int, val green: Int, val greenBlobs: List<Comp>, val tears: List<Comp>, val tearBones: List<String>, val loops: Int, val figure: Int,
    /** Shape ratio of the most squashed drawn picture (1 = undeformed) and its part. */
    val shape: Float, val shapePart: String,
)

/** parts.csv order (part -> bone) and bones.csv (bone -> parent) of a cut, for reading the ids_NN.png renders. */
private class CutGraph(val partBone: List<String>, val parent: Map<String, String?>) {
    companion object {
        fun load(cutDir: File): CutGraph? {
            val p = File(cutDir, "parts.csv"); val b = File(cutDir, "bones.csv")
            if (!p.exists() || !b.exists()) return null
            val partBone = p.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(",")[1] }
            val parent = b.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.associate { l -> val f = l.split(","); f[0] to f[1].ifEmpty { null } }
            return CutGraph(partBone, parent)
        }
    }

    /** One joint apart: the same bone, parent and child, or two children of one bone (an arm and its shoulder ball). */
    fun adjacent(b1: String, b2: String) = b1 == b2 || parent[b1] == b2 || parent[b2] == b1 || (parent[b1] != null && parent[b1] == parent[b2])

    /** Decodes a tinted pixel of an ids render to its part's bone; null for background, edges and blends. */
    fun boneOf(c: Int): String? {
        val r = r(c)
        if (r == 0 || r > partBone.size || kotlin.math.abs(g(c) - (255 - r)) > 3 || kotlin.math.abs(b(c) - ((r * 7) and 0xFF)) > 3) return null
        return partBone[r - 1]
    }
}

/** Bones of the parts within 3 px around [hole] (enclosed background of [inv]) in the ids render. */
private fun bonesAround(ids: Img, inv: Img, hole: Comp, graph: CutGraph): Set<String> {
    val out = HashSet<String>()
    for (y in hole.y0 - 3..hole.y1 + 3) for (x in hole.x0 - 3..hole.x1 + 3) {
        if (!inv.inside(x, y) || a(inv[x, y]) > 127) continue
        if ((-3..3).none { dy -> (-3..3).any { dx -> inv.inside(x + dx, y + dy) && a(inv[x + dx, y + dy]) > 127 } }) continue
        graph.boneOf(ids[x, y])?.let { out += it }
    }
    return out
}

/** A pose frame must show at least this many figure pixels, or the app drew nothing (a crash passes no check). */
private const val MIN_FIGURE = 50_000

/**
 * Exposed invented pixels a pose may show. A cut-out puppet always shows some cap when a joint bends (the neck stub
 * under a tilted head, the waistband under a lifted hem, a round thigh top): plain fills in the garment's own colour,
 * accepted by ART_PIPELINE.md section 1. The old A-pose armpit guess showed 800+ px of flat shirt per arm pose and
 * looked torn; the rig-pose cut shows at most ~180 (the waistband in a bow). Anything above this is a sleeve-sized
 * fill that eyes must check.
 */
private const val MAX_GREEN = 300

/**
 * Smallest shape ratio (smaller over larger axis of a drawn picture's matrix, Snapshot's distort.csv) a pose may
 * show. A rigid picture squashed more than 10% looks deformed (a foreshortened arm, a flattened shoe, a hand rolled
 * edge on); such a pose needs a substitute drawing, not a squash (ART_PIPELINE.md section 1).
 */
private const val MIN_SHAPE = .9f

internal fun jointCheck(sheetDir: File, cut: String, writeBaseline: Boolean) {
    val dir = File(sheetDir, cut)
    val graph = CutGraph.load(File("shared/src/commonMain/composeResources/files/$cut"))
    if (graph == null) println("không đọc được parts.csv/bones.csv của $cut: lỗ hở phân loại theo bề dày")
    val results = ArrayList<PoseResult>()
    // distort.csv rows are the written frames in pose order: frame,ratio,part.
    val shapes = File(dir, "distort.csv").takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.map { it.split(",") }
    if (shapes == null) println("thiếu $dir/distort.csv: chưa đo méo (chạy lại tools/joint_sheet.sh)")
    for (k in 0 until 16) {
        val f = File(dir, "pose_%02d.png".format(k))
        if (!f.exists()) { println("thiếu $f"); continue }
        val img = Img.load(f)
        // Exposed green: a green pixel whose four neighbours are green too, so a blob at least 3 px wide. Single
        // lines along a cut are the covering part's anti-aliased edge blending with the cap under it: in the real
        // render the cap has the garment's colour there, so they are not a tear.
        fun isGreen(x: Int, y: Int): Boolean { if (!img.inside(x, y)) return false; val c = img[x, y]; return g(c) > 150 && r(c) < 110 && b(c) < 110 }
        val solid = Img(img.w, img.h)
        for (y in 0 until img.h) for (x in 0 until img.w) if (isGreen(x, y) && isGreen(x - 1, y) && isGreen(x + 1, y) && isGreen(x, y - 1) && isGreen(x, y + 1)) solid[x, y] = -1
        val greenBlobs = components(solid, 1).second.sortedByDescending { it.area }
        val green = greenBlobs.sumOf { it.area }
        val keyed = keyMagenta(img)
        val figure = keyed.px.count { a(it) > 127 }
        val (inv, hs) = holesImg(keyed, 20)
        // A tear is enclosed background between parts one joint apart (thigh and shin, arm and torso, the two thighs):
        // the ids render says which parts bound a hole. Between parts further apart (a hanging hand and the shorts,
        // the triangle under a hand on the hip) it is a loop the pose closes on purpose: listed, not counted. Without
        // an ids render a tear is what is thin everywhere (nowhere thicker than about 10 px).
        val idsFile = File(dir, "ids_%02d.png".format(k))
        val ids = if (graph != null && idsFile.exists()) Img.load(idsFile) else null
        val tearBones = ArrayList<String>()
        val tears = hs.filter { h ->
            if (ids == null) maxThickness(inv, h) <= 5 else {
                val bs = bonesAround(ids, inv, h, graph!!).toList()
                val tear = bs.isEmpty() && maxThickness(inv, h) <= 5 || bs.isNotEmpty() && bs.all { x -> bs.all { y -> graph.adjacent(x, y) } }
                if (tear) tearBones += bs.joinToString("+").ifEmpty { "?" }
                tear
            }
        }
        val shape = shapes?.getOrNull(k)
        results += PoseResult(k, green, greenBlobs, tears, tearBones, hs.size - tears.size, figure, shape?.get(1)?.toFloat() ?: 1f, shape?.getOrNull(2) ?: "")
        if (tears.isNotEmpty() || greenBlobs.isNotEmpty()) {
            val bi = img.toBuffered(); val gr = bi.createGraphics()
            gr.color = Color.RED; gr.stroke = BasicStroke(3f)
            for (t in tears) gr.drawRect(t.x0 - 8, t.y0 - 8, t.x1 - t.x0 + 16, t.y1 - t.y0 + 16)
            gr.color = Color.YELLOW
            for (t in greenBlobs) gr.drawRect(t.x0 - 8, t.y0 - 8, t.x1 - t.x0 + 16, t.y1 - t.y0 + 16)
            ImageIO.write(bi, "png", File(dir, "check_%02d.png".format(k)))
        }
    }
    class Base(val green: Int, val tears: Int, val worst: Int)
    val base = if (BASELINE.exists()) BASELINE.readLines().filter { it.startsWith("$cut,") }.associate { l ->
        val p = l.split(","); p[1].toInt() to Base(p[2].toInt(), p[3].toInt(), p.getOrNull(4)?.toInt() ?: 0)
    } else emptyMap()
    val lines = ArrayList<String>()
    lines += "# $cut: tư thế, điểm ảnh xanh (bịa) lộ ra, lỗ hở (số, lớn nhất px, chỗ), vòng kín lớn; so với baseline"
    var bad = 0; var regress = 0; var reviewed = 0
    for (p in results) {
        val worst = p.tears.maxByOrNull { it.area }
        val b = base[p.pose]
        val reg = b != null && (p.green > b.green * 1.2 + 20 || p.tears.size > b.tears || (worst?.area ?: 0) > b.worst * 1.2 + 5)
        if (reg) regress++
        val drawn = p.figure >= MIN_FIGURE
        // Some holes between neighbouring parts are no tear (a flared cuff's notch that the folded forearm closes),
        // but by shape they look like one (the slit between hem and shorts in a bow). Eyes decide, once: holes
        // accepted in the baseline pass as long as they do not grow or multiply (`reg`).
        val held = p.tears.isNotEmpty() && b != null && !reg
        if (held) reviewed++
        val ok = drawn && p.green <= MAX_GREEN && (p.tears.isEmpty() || held) && !reg && p.shape >= MIN_SHAPE
        if (!ok) bad++
        lines += if (!drawn) "LỖI  tư thế %2d: KHÔNG CÓ NHÂN VẬT (%d điểm ảnh, cần >= %d): app không vẽ được bộ cắt này, xem log snapshot".format(p.pose, p.figure, MIN_FIGURE)
        else "%s  tư thế %2d: méo %3d%%%s, xanh %6d%s, lỗ %2d%s, vòng %d%s%s".format(
            if (ok) "ĐẠT" else "LỖI", p.pose, Math.round((1f - p.shape) * 100f), if (p.shape < 1f) " (${p.shapePart})" else "", p.green,
            p.greenBlobs.take(2).joinToString("") { " (${it.area} px ở ${it.x0},${it.y0})" }, p.tears.size,
            if (worst != null) " (lớn nhất ${worst.area} px ở ${worst.x0},${worst.y0}; ${p.tearBones.joinToString(", ")})" else "", p.loops,
            if (b != null) "  [baseline xanh ${b.green}, lỗ ${b.tears}, lớn nhất ${b.worst}]" else "",
            if (reg) "  HỒI QUY" else if (held) "  (lỗ đã duyệt bằng mắt)" else "",
        )
    }
    lines += if (bad == 0) "=> ĐẠT: ${results.size} tư thế, không mảnh nào méo quá ${Math.round((1f - MIN_SHAPE) * 100f)}%, lỗ hở ${if (reviewed > 0) "chỉ còn lỗ đã duyệt ($reviewed tư thế)" else "không có"}, xanh lộ ra <= $MAX_GREEN px (tổng ${results.sumOf { it.green }})"
        else "=> LỖI: $bad/${results.size} tư thế" + (if (regress > 0) ", $regress hồi quy so với baseline" else "") +
            (if (results.any { it.tears.isNotEmpty() }) "; lỗ hở: xem check_NN.png, sửa bộ cắt, hoặc nếu nhìn thấy không phải rách thì người dùng duyệt rồi ghi baseline" else "")
    val txt = lines.joinToString("\n")
    println(txt)
    File(sheetDir, "${cut}_jointcheck.txt").writeText(txt + "\n")
    if (writeBaseline) {
        val kept = if (BASELINE.exists()) BASELINE.readLines().filter { !it.startsWith("$cut,") && it.isNotBlank() } else listOf("# cut,pose,green,tears,largest tear px: numbers of the last joint sheet the user accepted (tools/joint_sheet.sh <out> <cuts> green baseline)")
        BASELINE.writeText((kept + results.map { "$cut,${it.pose},${it.green},${it.tears.size},${it.tears.maxOfOrNull { t -> t.area } ?: 0}" }).joinToString("\n") + "\n")
        println("baseline của $cut ghi vào $BASELINE")
    }
}
