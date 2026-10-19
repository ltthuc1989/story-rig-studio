package com.example.storyrig.tools

import java.awt.BasicStroke
import java.awt.Color
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Checks of the minimal flow's generated images (ART_PIPELINE.md section 3), so nobody has to spot the problems by eye:
 * every item prints ĐẠT or LỖI with its measurement, and the failures are boxed in red on a copy of the image.
 *   viewcheck <char> front|3q   checks assets/<char>/design/view_<view>.png against assets/templates/rig_<view>.png;
 *                               3q is also compared with the front view (height, ground line, colours, crotch, hands)
 *   facecheck <char>            checks assets/<char>/design/face.png against assets/templates/rig_face.csv
 * Both write <design>/<check>_<name>.txt and .png and exit 1 on any LỖI.
 */
private class Check(val name: String, val ok: Boolean, val measure: String, val box: Box? = null) {
    override fun toString() = "${if (ok) "ĐẠT" else "LỖI"}  $name: $measure"
}

/** Magenta-ness of a colour, as keyMagenta uses it: ~255 for the background, <= 0 for normal colours. */
private fun magenta(c: Int) = min(r(c), b(c)) - g(c) - abs(r(c) - b(c)) / 2

private fun report(out: File, src: Img, checks: List<Check>): Boolean {
    val txt = checks.joinToString("\n")
    println(txt)
    File(out.path + ".txt").writeText(txt + "\n")
    val bi = src.toBuffered()
    val g = bi.createGraphics()
    g.color = Color.RED; g.stroke = BasicStroke(4f)
    for (c in checks) if (!c.ok && c.box != null) g.drawRect(c.box.x0, c.box.y0, c.box.x1 - c.box.x0, c.box.y1 - c.box.y0)
    ImageIO.write(bi, "png", File(out.path + ".png"))
    val bad = checks.count { !it.ok }
    println(if (bad == 0) "=> ĐẠT (${checks.size} mục)" else "=> LỖI: $bad/${checks.size} mục; xem ${out.path}.png")
    return bad == 0
}

/** Enclosed background: background pixels not 4-connected to the image border (holes in the figure, pink patches). */
internal fun holes(keyed: Img, minArea: Int): List<Comp> {
    val w = keyed.w; val h = keyed.h
    val outside = BooleanArray(w * h)
    val st = ArrayDeque<Int>()
    fun push(i: Int) { if (!outside[i] && a(keyed.px[i]) <= 127) { outside[i] = true; st += i } }
    for (x in 0 until w) { push(x); push((h - 1) * w + x) }
    for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
    while (st.isNotEmpty()) {
        val i = st.removeLast(); val x = i % w; val y = i / w
        if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < h - 1) push(i + w)
    }
    // Holes as components of an image whose opaque pixels are the enclosed background.
    val inv = Img(w, h)
    for (i in inv.px.indices) if (a(keyed.px[i]) <= 127 && !outside[i]) inv.px[i] = -1
    return components(inv, minArea).second
}

internal fun viewCheck(char: String, view: String): Boolean {
    val design = File("assets/$char/design")
    val f = File(design, "view_$view.png")
    require(f.exists()) { "thiếu $f" }
    val img = Img.load(f)
    val checks = ArrayList<Check>()
    checks += Check("kích thước vuông", img.w == img.h && img.w >= 1000, "${img.w}x${img.h} (cần vuông, >= 1000)")
    val bgM = magenta(cornerColour(img))
    checks += Check("nền magenta", bgM >= 150, "độ magenta ở góc $bgM (cần >= 150)")
    val keyed = keyMagenta(img)
    val (_, comps) = components(keyed, 20)
    val big = comps.maxBy { it.area }
    val stray = comps.filter { it !== big && it.area >= 200 }
    checks += Check("một cụm duy nhất", stray.isEmpty(), "${comps.size} cụm >= 20 px, ${stray.size} cụm lạ >= 200 px" +
        (stray.firstOrNull()?.let { " (ở ${it.x0},${it.y0})" } ?: ""), stray.firstOrNull()?.let { Box(it.x0, it.y0, it.x1 + 1, it.y1 + 1) })
    val sil = Silhouette(keyed)
    val tpl = RigTemplate.load(File("assets/templates/rig_$view.png"))
    val tm = TemplateMap(tpl, sil)
    val cH = sil.box.y1 - sil.box.y0; val tH = tpl.box.y1 - tpl.box.y0
    val hScale = cH.toFloat() / tH * img.w / 1024f
    // Loose: the cut maps the mannequin onto the character's own box anyway; this only catches a tiny or cropped figure.
    checks += Check("chiều cao như hình nộm", abs(hScale - 1) <= .12f, "cao $cH px, hình nộm $tH px (tỉ lệ ${"%.2f".format(hScale)}, cho phép 0,88–1,12)", sil.box)
    val bottomOff = sil.box.y1 - tpl.box.y1 * img.w / 1024f
    checks += Check("đế chân trên đường đất", abs(bottomOff) <= 40, "lệch ${bottomOff.roundToInt()} px (cho phép 40)")
    // Pose: the hands are the outermost pixels between the neck and the crotch, near the mannequin's hands.
    val neckY = tm.map(tpl["neck_bottom"]).y.roundToInt(); val crotchY = tm.map(tpl["crotch"]).y.roundToInt()
    val waistY = tm.map(tpl["waist"]).y
    for ((side, left) in listOf("near" to true, "far" to false)) {
        val hand = tm.map(tpl["hand_$side"])
        var ext = if (left) Int.MAX_VALUE else Int.MIN_VALUE; var ey = -1
        for (y in neckY..crotchY) for (x in 0 until sil.w) if (sil.at(x, y) && (if (left) x < ext else x > ext)) { ext = x; ey = y }
        val ok = ey >= 0 && abs(ey - hand.y) <= .06f * cH && ey < waistY && abs(ext - hand.x) <= .12f * img.w
        checks += Check("tay $side giơ như hình nộm", ok, "đầu tay ở $ext,$ey; hình nộm ${hand.x.roundToInt()},${hand.y.roundToInt()} (cho phép lệch ${(.06f * cH).roundToInt()} px dọc)",
            Box(max(0, ext - 40), max(0, ey - 40), min(img.w, ext + 40), min(img.h, ey + 40)))
    }
    // Gaps: the mannequin's background seeds must be background with room around them.
    for (n in listOf("gap_arm_near", "gap_arm_far", "gap_legs")) {
        val p = tm.map(tpl[n]); val x = p.x.roundToInt(); val y = p.y.roundToInt()
        var r = 0
        while (r < 40 && (-r..r).all { dx -> (-r..r).all { dy -> !sil.at(x + dx, y + dy) } }) r++
        checks += Check("khe nền $n", r >= 12, "nền trống ${r}px quanh $x,$y (cần >= 12)", Box(x - 30, y - 30, x + 30, y + 30))
    }
    // Pink on the figure (inside it, 3 px clear of the anti-aliased edge), and holes inside it.
    var pink = 0; var opaque = 0
    for (y in 3 until sil.h - 3) for (x in 3 until sil.w - 3) {
        if (!sil.at(x, y)) continue
        if ((-3..3).any { dy -> (-3..3).any { dx -> !sil.at(x + dx, y + dy) } }) continue
        // 20: near keyMagenta's lower bound (30), where a colour starts to key out; dark hair with a purple cast
        // (#291D2D) measures about 10 and is fine.
        opaque++; if (magenta(img[x, y]) > 20) pink++
    }
    checks += Check("không có hồng trên nhân vật", pink <= opaque / 1000, "$pink điểm hồng / $opaque bên trong (cho phép 0,1%)")
    val hs = holes(keyed, 30)
    checks += Check("không có lỗ trong dáng", hs.none { it.area >= 200 }, "${hs.size} lỗ >= 30 px" + (hs.maxByOrNull { it.area }?.let { ", lớn nhất ${it.area} px ở ${it.x0},${it.y0}" } ?: ""),
        hs.maxByOrNull { it.area }?.let { Box(it.x0 - 10, it.y0 - 10, it.x1 + 10, it.y1 + 10) })
    val pal = mainColours(keyed.px.filterIndexed { i, _ -> sil.on[i] }, .03f)
    checks += Check("bảng màu", true, pal.joinToString(" ") { "#%06X".format(it and 0xFFFFFF) })
    // Crotch: up the mapped leg-gap column to the first figure pixel.
    fun crotchOf(s: Silhouette, t: TemplateMap): Int { val p = t.map(tpl["gap_legs"]); var y = p.y.roundToInt(); while (y > 0 && !s.at(p.x.roundToInt(), y)) y--; return y }
    val crotch = crotchOf(sil, tm)
    checks += Check("đũng quần", crotch > 0, "y=$crotch (${"%.2f".format((sil.box.y1 - crotch).toFloat() / cH)} chiều cao từ đế)")
    if (view != "front") {
        val ff = File(design, "view_front.png")
        if (!ff.exists()) checks += Check("so với trực diện", false, "thiếu $ff")
        else {
            val fk = keyMagenta(Img.load(ff)); val fs = Silhouette(fk)
            val fH = fs.box.y1 - fs.box.y0
            checks += Check("cùng chiều cao với trực diện", abs(cH - fH) <= .03f * fH, "$cH px, trực diện $fH px (cho phép 3%)", sil.box)
            checks += Check("cùng đường đất với trực diện", abs(sil.box.y1 - fs.box.y1) <= 15, "đế $${sil.box.y1}, trực diện ${fs.box.y1} (cho phép 15 px)")
            val ftm = TemplateMap(RigTemplate.load(File("assets/templates/rig_front.png")), fs)
            val fc = crotchOf(fs, ftm)
            val dc = abs((sil.box.y1 - crotch) - (fs.box.y1 - fc))
            checks += Check("đũng quần cùng độ cao", dc <= .03f * fH, "lệch $dc px (cho phép 3%)")
            val fpal = mainColours(fk.px.filterIndexed { i, _ -> fs.on[i] }, .05f)
            val missing = fpal.filter { c -> pal.none { colourDist(it, c) <= 45 } }
            checks += Check("cùng bảng màu với trực diện", missing.isEmpty(), if (missing.isEmpty()) "mọi màu chính của trực diện có ở góc này" else "thiếu " + missing.joinToString(" ") { "#%06X".format(it and 0xFFFFFF) })
        }
    }
    return report(File(design, "viewcheck_$view"), img, checks)
}

internal fun faceCheck(char: String): Boolean {
    val design = File("assets/$char/design")
    val f = File(design, "face.png")
    require(f.exists()) { "thiếu $f" }
    val img = Img.load(f)
    val k = img.w.toFloat() / FACE_W
    val slots = File("assets/templates/rig_face.csv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { l ->
        val p = l.split(","); p[0] to Box((p[1].toFloat() * k).roundToInt(), (p[2].toFloat() * k).roundToInt(), (p[3].toFloat() * k).roundToInt(), (p[4].toFloat() * k).roundToInt())
    }
    val checks = ArrayList<Check>()
    checks += Check("khổ 16:9", abs(img.w * 9 - img.h * 16) <= img.w / 10, "${img.w}x${img.h}")
    checks += Check("nền magenta", magenta(cornerColour(img)) >= 150, "độ magenta ở góc ${magenta(cornerColour(img))}")
    val keyed = keyMagenta(img)
    val (_, comps) = components(keyed, 15)
    val taken = HashSet<Comp>()
    val widths = LinkedHashMap<String, Int>()
    for ((name, b) in slots) {
        val mx = (b.x1 - b.x0) / 2; val my = (b.y1 - b.y0) / 2
        val inside = comps.filter { it.cx in (b.x0 - mx)..(b.x1 + mx) && it.cy in (b.y0 - my)..(b.y1 + my) }
        taken += inside
        val main = inside.maxByOrNull { it.area }
        val ok = main != null && inside.count { it.area >= 60 } <= 2
        checks += Check("ô $name", ok, when { main == null -> "trống"; else -> "${inside.size} cụm, chính ${main.x1 - main.x0 + 1}x${main.y1 - main.y0 + 1} px" }, b)
        if (main != null) widths[name] = main.x1 - main.x0 + 1
    }
    val stray = comps.filter { it !in taken && it.area >= 60 && !(it.cx > img.w - 160 * k && it.cy > img.h - 160 * k) }
    checks += Check("không có mảnh ngoài ô", stray.isEmpty(), "${stray.size} mảnh lạ" + (stray.firstOrNull()?.let { " (ở ${it.x0},${it.y0}, ${it.area} px)" } ?: ""),
        stray.firstOrNull()?.let { Box(it.x0, it.y0, it.x1 + 1, it.y1 + 1) })
    val mouths = widths.filterKeys { it.startsWith("mouth_") }.values
    if (mouths.isNotEmpty()) checks += Check("miệng cùng cỡ", mouths.max() <= 3 * mouths.min(), "rộng ${mouths.min()}–${mouths.max()} px (cho phép gấp 3)")
    val hands = widths.filterKeys { it.startsWith("hand_") }.values
    if (hands.isNotEmpty()) checks += Check("bàn tay cùng cỡ", hands.max() <= 1.5f * hands.min(), "rộng ${hands.min()}–${hands.max()} px (cho phép gấp 1,5)")
    // Skin: the hands' main colour must be a main colour of the front view.
    val ff = File(design, "view_front.png")
    if (ff.exists()) {
        val fk = keyMagenta(Img.load(ff)); val fs = Silhouette(fk)
        val fpal = mainColours(fk.px.filterIndexed { i, _ -> fs.on[i] }, .03f)
        val hb = slots.filter { it.first.startsWith("hand_") }.map { it.second }
        val handPx = ArrayList<Int>()
        for (b in hb) for (y in b.y0 until min(b.y1, img.h)) for (x in b.x0 until min(b.x1, img.w)) { val c = keyed[x, y]; if (a(c) > 200) handPx += c }
        val skin = mainColours(handPx, .2f).firstOrNull()
        val d = skin?.let { s -> fpal.minOf { colourDist(it, s) } } ?: 999
        checks += Check("màu da bàn tay như thân", d <= 45, "tay #%06X, lệch %d so với bảng màu trực diện (cho phép 45)".format((skin ?: 0) and 0xFFFFFF, d))
    }
    return report(File(design, "facecheck"), img, checks)
}
