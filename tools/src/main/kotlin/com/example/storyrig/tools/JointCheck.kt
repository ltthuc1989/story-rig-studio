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
 *                                          tools/jointcheck_baseline.txt (`baseline` rewrites that cut's lines)
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
        img.save(File(outDir, "parts/$name.png"))
        if (n > 0) println("  $name: $n điểm ảnh bịa")
        total += n
    }
    println("greencut $cutDir -> $outDir: $total điểm ảnh bịa")
}

private class PoseResult(val pose: Int, val green: Int, val tears: List<Comp>, val loops: Int)

internal fun jointCheck(sheetDir: File, cut: String, writeBaseline: Boolean) {
    val dir = File(sheetDir, cut)
    val results = ArrayList<PoseResult>()
    for (k in 0 until 16) {
        val f = File(dir, "pose_%02d.png".format(k))
        if (!f.exists()) { println("thiếu $f"); continue }
        val img = Img.load(f)
        var green = 0
        for (c in img.px) if (g(c) > 150 && r(c) < 110 && b(c) < 110) green++
        val keyed = keyMagenta(img)
        val hs = holes(keyed, 20)
        // A tear is thin (mesh slivers, a seam that opened): under 150 px, or under ~12 px thick along its long side.
        // Anything fatter is a loop the pose closes on purpose (a hand resting on the hip): listed, not counted.
        val tears = hs.filter { it.area < 150 || it.area.toFloat() / maxOf(it.x1 - it.x0 + 1, it.y1 - it.y0 + 1) <= 12f }
        results += PoseResult(k, green, tears, hs.size - tears.size)
        if (tears.isNotEmpty()) {
            val bi = img.toBuffered(); val gr = bi.createGraphics()
            gr.color = Color.RED; gr.stroke = BasicStroke(3f)
            for (t in tears) gr.drawRect(t.x0 - 8, t.y0 - 8, t.x1 - t.x0 + 16, t.y1 - t.y0 + 16)
            ImageIO.write(bi, "png", File(dir, "check_%02d.png".format(k)))
        }
    }
    val base = if (BASELINE.exists()) BASELINE.readLines().filter { it.startsWith("$cut,") }.associate { l -> val p = l.split(","); p[1].toInt() to (p[2].toInt() to p[3].toInt()) } else emptyMap()
    val lines = ArrayList<String>()
    lines += "# $cut: tư thế, điểm ảnh xanh (bịa) lộ ra, lỗ hở (số, lớn nhất px, chỗ), vòng kín lớn; so với baseline"
    var bad = 0; var regress = 0
    for (p in results) {
        val worst = p.tears.maxByOrNull { it.area }
        val b = base[p.pose]
        val reg = b != null && (p.green > b.first * 1.2 + 20 || p.tears.size > b.second)
        if (reg) regress++
        val ok = p.green == 0 && p.tears.isEmpty()
        if (!ok) bad++
        lines += "%s  tư thế %2d: xanh %6d, lỗ %2d%s, vòng %d%s%s".format(
            if (ok) "ĐẠT" else "LỖI", p.pose, p.green, p.tears.size,
            if (worst != null) " (lớn nhất ${worst.area} px ở ${worst.x0},${worst.y0})" else "", p.loops,
            if (b != null) "  [baseline xanh ${b.first}, lỗ ${b.second}]" else "", if (reg) "  HỒI QUY" else "",
        )
    }
    lines += if (bad == 0) "=> ĐẠT: ${results.size} tư thế không xanh, không lỗ" else "=> LỖI: $bad/${results.size} tư thế" + (if (regress > 0) ", $regress hồi quy so với baseline" else "")
    val txt = lines.joinToString("\n")
    println(txt)
    File(sheetDir, "${cut}_jointcheck.txt").writeText(txt + "\n")
    if (writeBaseline) {
        val kept = if (BASELINE.exists()) BASELINE.readLines().filter { !it.startsWith("$cut,") && it.isNotBlank() } else listOf("# cut,pose,green,tears: numbers of the last accepted joint sheet (tools/joint_sheet.sh <out> <cuts> green)")
        BASELINE.writeText((kept + results.map { "$cut,${it.pose},${it.green},${it.tears.size}" }).joinToString("\n") + "\n")
        println("baseline của $cut ghi vào $BASELINE")
    }
}
