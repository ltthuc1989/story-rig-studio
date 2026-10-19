package com.example.storyrig.tools

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * `selftest <outRoot>`: cuts every character on disk (assets/<name>/gemini, all views) and the synthetic variants of
 * Kai, then checks the output files the way the app reads them. Prints one row per case, writes selftest.txt and
 * selftest_sheet.png (regions + bent pose of every case) to outRoot. Exit code 1 when a check fails.
 *
 * Checks: the cut runs; all body parts and bones are there (and the face parts / expressions when expected);
 * the rest pose drawn from the parts matches ref.png pixel for pixel (missing, extra, wrong colour); no part
 * carries stray bits; the skirt rule fires only where expected; Kai matches the app's data byte for byte;
 * variants find their joints within tolerance of the moved hand measurements.
 */
/** Shoulder to wrist, as a share of the figure's height, below which an arm counts as not found. */
private const val ARM_MIN = .15f

internal fun selfTest(outRoot: File): Boolean {
    outRoot.mkdirs()
    val cases = ArrayList<Case>()
    for (dir in File("assets").listFiles().orEmpty().sortedBy { it.name }) {
        val gem = File(dir, "gemini")
        if (File(gem, "ref.png").exists()) cases += Case(dir.name, gem, "3q", skirt = dir.name == "hana",
            golden = File("shared/src/commonMain/composeResources/files/${dir.name}").takeIf { dir.name == "kai" })
        for (v in listOf("front", "back")) if (File(gem, "view_$v.png").exists()) cases += Case("${dir.name}_$v", gem, v, skirt = dir.name == "hana")
    }
    // Synthetic variants of Kai: other colours, smaller and moved, stretched.
    val kaiDir = File("assets/kai/gemini")
    val src = Img.load(File(kaiDir, "ref.png"))
    for (v in variants) {
        val gem = File(outRoot, "variants/${v.name}/gemini").apply { mkdirs() }
        render(src, v).save(File(gem, "ref.png"))
        for (f in listOf("face.png", "face_extra.png", "face_map.csv")) File(kaiDir, f).takeIf { it.exists() }?.copyTo(File(gem, f), overwrite = true)
        cases += Case("kai~${v.name}", gem, "3q", expected = moved(KaiRefSpec.spec(), v, src.w, src.h))
    }

    val rows = cases.map { runCase(it, outRoot) }
    val report = buildString {
        appendLine("%-14s %-4s %6s  %s".format("case", "ok", "ms", "checks (failed ones in CAPS)"))
        for (r in rows) appendLine("%-14s %-4s %6d  %s".format(r.name, if (r.ok) "ok" else "FAIL", r.ms, r.notes.joinToString("; ")))
        val failed = rows.count { !it.ok }
        appendLine(if (failed == 0) "all ${rows.size} cases pass" else "$failed of ${rows.size} cases FAIL")
    }
    print(report)
    File(outRoot, "selftest.txt").writeText(report)
    contactSheet(rows, File(outRoot, "selftest_sheet.png"))
    return rows.all { it.ok }
}

private class Case(
    val name: String, val dir: File, val view: String,
    val skirt: Boolean = false, val golden: File? = null, val expected: RefSpec? = null,
)

private class Row(val name: String, val ok: Boolean, val ms: Long, val notes: List<String>, val preview: File)

private val BODY = listOf("head", "torso", "pelvis") +
    listOf("near", "far").flatMap { s -> listOf("arm_upper_$s", "arm_fore_$s", "shoulder_ball_$s", "hand_$s", "thigh_$s", "shin_$s", "shoe_$s") }
private val BONES = listOf("root", "torso", "head") +
    listOf("near", "far").flatMap { s -> listOf("shoulder_$s", "shoulder_ball_$s", "elbow_$s", "wrist_$s", "hip_$s", "knee_$s", "ankle_$s") }

private fun runCase(c: Case, outRoot: File): Row {
    val out = File(outRoot, "${c.name}/out"); val prev = File(outRoot, "${c.name}/prev")
    out.deleteRecursively(); prev.deleteRecursively()
    val notes = ArrayList<String>()
    var ok = true
    fun check(pass: Boolean, label: String) { if (!pass) ok = false; notes += if (pass) label else label.uppercase() }
    val t0 = System.nanoTime()
    var spec: RefSpec? = null
    val log = java.io.ByteArrayOutputStream()
    val ran = runCatching {
        // The cut prints a lot; keep it out of the report.
        val saved = System.out
        System.setOut(java.io.PrintStream(log))
        try {
            val image = if (c.view == "3q") "ref.png" else "view_${c.view}.png"
            val template = if (c.view == "3q") File("assets/templates/legacy/rig_3q_right.png") else File("assets/templates/legacy/rig_${c.view}.png")
            val img = defringe(keyMagenta(Img.load(File(c.dir, image))))
            val res = autoSpec(img, RigTemplate.load(template), view = c.view)
            spec = res.spec
            cutWith(c.dir, out, prev, res.spec)
        } finally { System.setOut(saved) }
    }
    File(outRoot, "${c.name}/cut.log").apply { parentFile.mkdirs() }.writeBytes(log.toByteArray())
    val ms = (System.nanoTime() - t0) / 1_000_000
    if (ran.isFailure) {
        check(false, "cut failed: ${ran.exceptionOrNull()?.message?.take(80)}")
        return Row(c.name, false, ms, notes, prev)
    }

    // Output as the app reads it.
    val parts = File(out, "parts.csv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(",") }
    val names = parts.map { it[0] }.toSet()
    val bones = File(out, "bones.csv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(",") }
    val boneNames = bones.map { it[0] }.toSet()
    val missingParts = BODY.filter { it !in names }
    check(missingParts.isEmpty(), if (missingParts.isEmpty()) "parts ${parts.count { it[6] == "1" }} drawn" else "missing ${missingParts.joinToString(",")}")
    val missingBones = BONES.filter { it !in boneNames }
    check(missingBones.isEmpty(), if (missingBones.isEmpty()) "bones ${bones.size}" else "missing bones ${missingBones.joinToString(",")}")
    if (c.view != "back") {
        val face = listOf("eye_near_open", "eye_far_open", "mouth_smile").filter { it !in names }
        check(face.isEmpty(), if (face.isEmpty()) "face" else "no ${face.joinToString(",")}")
        if (c.view == "3q" && (File(c.dir, "face.png").exists() || File(c.dir, "face_map.csv").exists())) {
            val need = REQUIRED_FACE.filter { it !in names && !it.startsWith("brow") }
            check(need.isEmpty(), if (need.isEmpty()) "expressions" else "no ${need.joinToString(",")}")
        }
    }
    check(File(out, "mesh.csv").exists(), "mesh.csv")
    // Arms really found: shoulder to wrist at least ARM_MIN of the figure's height, and limbs measured as thick
    // (mesh.csv's cell is limb half width / 3.25; a failed arm cut gives cell 2). A collapsed arm still cuts "fine".
    run {
        val at = bones.associate { it[0] to (it[2].toFloat() to it[3].toFloat()) }
        val top = parts.filter { it[6] == "1" }.minOf { it[3].toInt() }; val bottom = parts.filter { it[6] == "1" }.maxOf { it[3].toInt() + it[5].toInt() }
        val arms = listOf("near", "far").map { sd ->
            val (sx, sy) = at.getValue("shoulder_$sd"); val (wx, wy) = at.getValue("wrist_$sd")
            hypot(wx - sx, wy - sy) / (bottom - top)
        }
        val cell = File(out, "mesh.csv").readLines().firstOrNull { it.startsWith("group,") }?.split(',')?.get(2)?.toIntOrNull() ?: 0
        check(arms.min() >= ARM_MIN && cell >= 4, "arms %.2f,%.2f of height, mesh cell $cell".format(arms[0], arms[1]))
    }

    // Rest pose from the parts vs the keyed reference.
    val image = if (c.view == "3q") "ref.png" else "view_${c.view}.png"
    val ref = defringe(keyMagenta(Img.load(File(c.dir, image))))
    val rest = Img(ref.w, ref.h)
    for (p in parts) if (p[6] == "1") {
        val im = Img.load(File(out, "parts/${p[0]}.png"))
        val ox = p[2].toInt(); val oy = p[3].toInt()
        for (y in 0 until im.h) for (x in 0 until im.w) {
            val gx = x + ox; val gy = y + oy
            if (!rest.inside(gx, gy)) continue
            rest[gx, gy] = over(im[x, y], rest[gx, gy])
        }
    }
    var refOn = 0; var missing = 0; var extra = 0; var colour = 0
    for (i in ref.px.indices) {
        val ra = a(ref.px[i]); val sa = a(rest.px[i])
        if (ra > 128) refOn++
        if (ra > 128 && sa < 64) missing++
        if (ra < 64 && sa > 128) extra++
        if (ra > 200 && sa > 200 && dist3(ref.px[i], rest.px[i]) > 90) colour++
    }
    // Where they differ, for looking: red missing, blue extra, yellow wrong colour, on the faded reference.
    val diffImg = Img(ref.w, ref.h)
    for (i in ref.px.indices) {
        val ra = a(ref.px[i]); val sa = a(rest.px[i]); val c = ref.px[i]
        diffImg.px[i] = when {
            ra > 128 && sa < 64 -> argb(255, 230, 0, 0)
            ra < 64 && sa > 128 -> argb(255, 0, 90, 255)
            ra > 200 && sa > 200 && dist3(c, rest.px[i]) > 90 -> argb(255, 255, 210, 0)
            ra > 0 -> argb(255, (r(c) + 3 * 255) / 4, (g(c) + 3 * 255) / 4, (b(c) + 3 * 255) / 4)
            else -> argb(255, 255, 255, 255)
        }
    }
    diffImg.save(File(prev, "rest_diff.png"))
    fun pct(n: Int) = 100f * n / max(1, refOn)
    check(pct(missing) <= .5f && pct(extra) <= 1f && pct(colour) <= 1f,
        "rest vs ref: missing %.2f%% extra %.2f%% colour %.2f%%".format(pct(missing), pct(extra), pct(colour)))

    // Stray bits: every drawn part is one blob (95% of its pixels in the biggest piece).
    var worst = "" ; var worstShare = 1f
    for (p in parts) if (p[6] == "1" && (p[0] in BODY || p[0] == "hair")) {
        val share = mainBlobShare(Img.load(File(out, "parts/${p[0]}.png")))
        if (share < worstShare) { worstShare = share; worst = p[0] }
    }
    check(worstShare >= .95f, if (worst.isEmpty()) "no stray bits" else "least whole: $worst %.0f%%".format(worstShare * 100))

    // Skirt rule.
    // Skirt rule: fires where expected, and then the skirt swings on its own bone (pelvis on `skirt`, parent root).
    val skirt = spec?.hipPivotY != null
    val pelvisBone = parts.firstOrNull { it[0] == "pelvis" }?.get(1)
    val skirtBone = bones.firstOrNull { it[0] == "skirt" }
    val skirtOk = if (skirt) skirtBone?.get(1) == "root" && pelvisBone == "skirt" else skirtBone == null && pelvisBone == "root"
    check(skirt == c.skirt && skirtOk, when {
        skirt && skirtOk -> "skirt (bone skirt)"
        skirt -> "skirt but pelvis on ${pelvisBone}, skirt bone ${skirtBone?.joinToString(",") ?: "missing"}"
        skirtOk -> "no skirt"
        else -> "no skirt but pelvis on $pelvisBone${if (skirtBone != null) ", has a skirt bone" else ""}"
    })

    // View the app reads from character.csv.
    val view = File(out, "character.csv").takeIf { it.exists() }?.readLines()?.firstOrNull { it.startsWith("view,") }?.substringAfter(",")?.trim()
    check(view == c.view, if (view == c.view) "view $view" else "character.csv view ${view ?: "missing"}, expected ${c.view}")

    // Byte-for-byte against the app's data.
    c.golden?.let { g ->
        val files = (listOf("bones.csv", "parts.csv", "mesh.csv", "character.csv") + File(out, "parts").list().orEmpty().map { "parts/$it" })
        val diff = files.filter { f -> !File(g, f).exists() || !File(g, f).readBytes().contentEquals(File(out, f).readBytes()) }
        check(diff.isEmpty(), if (diff.isEmpty()) "same as app data (${files.size} files)" else "differs from app data: ${diff.take(4).joinToString(",")}${if (diff.size > 4) "…" else ""}")
    }

    // Variants: joints against the moved hand measurements.
    c.expected?.let { e ->
        val s = spec!!
        fun d(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
        fun m(q: Seg) = Pt((q.x1 + q.x2) / 2, (q.y1 + q.y2) / 2)
        val errs = mapOf(
            "neck" to d(m(s.neck), m(e.neck)),
            "elbowN" to d(s.elbowNear, e.elbowNear), "elbowF" to d(s.elbowFar, e.elbowFar),
            "wristN" to d(s.wristNear, e.wristNear), "wristF" to d(s.wristFar, e.wristFar),
            "kneeN" to d(s.kneeNear, e.kneeNear), "kneeF" to d(s.kneeFar, e.kneeFar),
            "ankleN" to d(s.ankleNear, e.ankleNear), "ankleF" to d(s.ankleFar, e.ankleFar),
        )
        val (wn, wv) = errs.maxBy { it.value }
        check(wv <= 15f, "joints vs hand-measured: worst $wn %.1f px".format(wv))
    }
    return Row(c.name, ok, ms, notes, prev)
}

private fun dist3(c1: Int, c2: Int) = abs(r(c1) - r(c2)) + abs(g(c1) - g(c2)) + abs(b(c1) - b(c2))

/** Source-over of [s] onto [d] (non-premultiplied ARGB). */
private fun over(s: Int, d: Int): Int {
    val sa = a(s) / 255f
    if (sa <= 0f) return d
    val da = a(d) / 255f
    val oa = sa + da * (1 - sa)
    fun ch(cs: Int, cd: Int) = ((cs * sa + cd * da * (1 - sa)) / oa).toInt().coerceIn(0, 255)
    return argb((oa * 255).toInt(), ch(r(s), r(d)), ch(g(s), g(d)), ch(b(s), b(d)))
}

/** Share of a part's opaque pixels (alpha > 128) in its biggest 8-connected blob. */
private fun mainBlobShare(im: Img): Float {
    val seen = BooleanArray(im.px.size)
    var total = 0; var best = 0
    for (s in im.px.indices) {
        if (seen[s] || a(im.px[s]) <= 128) continue
        var n = 0
        val st = ArrayDeque<Int>(); st += s; seen[s] = true
        while (st.isNotEmpty()) {
            val q = st.removeLast(); n++
            val x = q % im.w; val y = q / im.w
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (!im.inside(nx, ny)) continue
                val k = ny * im.w + nx
                if (!seen[k] && a(im.px[k]) > 128) { seen[k] = true; st += k }
            }
        }
        total += n; best = max(best, n)
    }
    return if (total == 0) 1f else best.toFloat() / total
}

/** One row per case: name and result, regions.png, pose_bend.png. */
private fun contactSheet(rows: List<Row>, out: File) {
    val h = 260; val label = 150
    val sheet = BufferedImage(label + 2 * h, h * rows.size, BufferedImage.TYPE_INT_RGB)
    val g = sheet.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.color = Color.WHITE; g.fillRect(0, 0, sheet.width, sheet.height)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 16)
    for ((i, r) in rows.withIndex()) {
        val y = i * h
        g.color = if (r.ok) Color(30, 130, 60) else Color(200, 30, 30)
        g.drawString(r.name, 8, y + 30)
        g.drawString(if (r.ok) "ok" else "FAIL", 8, y + 54)
        for ((k, f) in listOf("regions.png", "pose_bend.png").withIndex()) {
            val file = File(r.preview, f)
            if (file.exists()) g.drawImage(ImageIO.read(file), label + k * h, y, h, h, null)
        }
    }
    g.dispose()
    ImageIO.write(sheet, "png", out)
}
