package com.example.storyrig.tools

import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

/*
 * The minimal flow's cut (ART_PIPELINE.md section 3): assets/<char>/design/view_<view>.png, drawn in the rig pose of
 * assets/templates/rig_<view>.png, measured by AutoSpec against that template and cut by cutWith.
 *   cutview <char> front|3q    writes tools/build/cut/<cut>/out (bones, parts, mesh, character.csv) and /preview
 *                              (points.png, points.txt, regions, previews); <cut> = <char>_front, or <char> for 3q
 * points.txt compares each measured joint with the mannequin's (mapped onto the character): the character copies the
 * mannequin's pose, so a joint far from its guess (over 6% of the height) is a mis-measurement, printed as LỖI.
 */
internal fun cutView(char: String, view: String) {
    val design = File("assets/$char/design")
    val src = File(design, "view_$view.png")
    require(src.exists()) { "thiếu $src (viewcheck $char $view phải ĐẠT trước)" }
    val cut = if (view == "3q") char else "${char}_$view"
    val outDir = File("tools/build/cut/$cut/out"); val previewDir = File("tools/build/cut/$cut/preview")
    previewDir.mkdirs()
    val template = RigTemplate.load(File("assets/templates/rig_$view.png"))
    val img = defringe(keyMagenta(Img.load(src)))
    val res = autoSpec(img, template, debug = true, view = view)
    drawPoints(File(previewDir, "points.png"), img, res)
    val tm = TemplateMap(template, res.sil)
    val lim = .06f * (res.sil.box.y1 - res.sil.box.y0)
    fun mid(s: Seg) = Pt((s.x1 + s.x2) / 2, (s.y1 + s.y2) / 2)
    val S = res.spec
    val joints = listOf(
        "neck_bottom" to mid(S.neck), "shoulder_near" to mid(S.shoulderNear), "shoulder_far" to mid(S.shoulderFar),
        "hip_near" to mid(S.hipNear), "hip_far" to mid(S.hipFar), "ankle_near" to S.ankleNear, "ankle_far" to S.ankleFar,
        "waist" to S.waist, "root" to S.root,
    )
    var bad = 0
    val lines = joints.map { (n, p) ->
        val g = tm.map(template[n])
        // Shoulders: height only. The template's x scale comes from the silhouette's width, i.e. the arm span, and
        // Gemini draws the three-quarter arms at full length where the mannequin foreshortens them, so the mapped
        // shoulder x lands on the sleeve; the measured one (the torso's side) is the right one.
        val d = if (n.startsWith("shoulder")) kotlin.math.abs(p.y - g.y) else hypot(p.x - g.x, p.y - g.y)
        if (d > lim) bad++
        "%s  %-14s đo %4d,%4d  hình nộm %4d,%4d  lệch %3d px".format(if (d > lim) "LỖI" else "ĐẠT", n, p.x.roundToInt(), p.y.roundToInt(), g.x.roundToInt(), g.y.roundToInt(), d.roundToInt())
    } + (if (bad == 0) "=> ĐẠT: mọi khớp trong ${lim.roundToInt()} px của hình nộm" else "=> LỖI: $bad khớp lệch quá ${lim.roundToInt()} px, xem $previewDir/points.png")
    println(lines.joinToString("\n"))
    File(previewDir, "points.txt").writeText(lines.joinToString("\n") + "\n")
    // Rigid pieces (no skinned mesh): cut-out joints with round caps, as the minimal flow decided.
    cutWith(design, outDir, previewDir, S, srcName = "view_$view.png", faceSheet = false, rigid = true)
    // Substitute drawings for what a rigid part cannot show turned (limb sheet, Limbs.kt).
    File(design, "limbs.png").takeIf { it.exists() }?.let { cutLimbs(it, outDir) }
    println("cutview $cut -> $outDir")
}
