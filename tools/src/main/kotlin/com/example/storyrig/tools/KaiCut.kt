package com.example.storyrig.tools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Kai asset cutter. Run from the repo root:
 *   ./gradlew :tools:run --args="scan <sheet.png> <outDir>"         key the background, number every separate piece
 *   ./gradlew :tools:run --args="build <geminiDir> <outDir> <previewDir>"
 *       cut the parts (joints with round caps), place them on ref.png, write parts/<name>.png + bones.csv + parts.csv,
 *       and render test poses into previewDir (body parts from the body.png parts sheet)
 *   ./gradlew :tools:run --args="cutref <geminiDir> <outDir> <previewDir>"
 *       same outputs, body parts cut straight from ref.png (RefCut.kt); only the face comes from face sheets
 *   ./gradlew :tools:run --args="cutauto <geminiDir> <outDir> <previewDir> [kai]"
 *       same as cutref, but the cut points are measured from the mannequin template and the character's shape
 *       (AutoSpec.kt) instead of KaiRefSpec; `kai` prints each point next to KaiRefSpec's. Also writes points.png.
 *   ./gradlew :tools:run --args="autotest <geminiDir> <outRoot>"
 *       recoloured / scaled / stretched copies of ref.png, each cut with cutauto and checked against KaiRefSpec
 *       (and then facetest)
 *   ./gradlew :tools:run --args="facetest <geminiDir> <outRoot>"
 *       Kai's face pieces laid out as prompts/2_face_sheet.txt asks, read back by the layout (no face_map.csv)
 */
fun main(args: Array<String>) {
    when (args.getOrNull(0)) {
        "scan" -> scan(File(args[1]), File(args[2]))
        "build" -> build(File(args[1]), File(args[2]), File(args[3]))
        "cutref" -> cutRef(File(args[1]), File(args[2]), File(args[3]))
        "cutauto" -> cutAuto(File(args[1]), File(args[2]), File(args[3]), compareKai = args.getOrNull(4) == "kai")
        "autotest" -> autoTest(File(args[1]), File(args[2]))
        "mannequin" -> drawMannequin(args[1], File(args[2]))
        "facetest" -> faceLayoutTest(File(args[1]), File(args[2]))
        else -> println("usage: scan <sheet.png> <outDir> | build|cutref|cutauto <geminiDir> <outDir> <previewDir> | autotest|facetest <geminiDir> <outRoot>")
    }
}

private fun scan(sheet: File, outDir: File) {
    val keyed = defringe(keyMagenta(Img.load(sheet)))
    val (_, comps) = components(keyed, minArea = 40)
    val name = sheet.nameWithoutExtension
    keyed.save(File(outDir, "$name.keyed.png"))

    // Overview: keyed sheet on grey with a numbered box per piece.
    val bi = BufferedImage(keyed.w, keyed.h, BufferedImage.TYPE_INT_ARGB)
    val g = bi.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(0x80, 0x80, 0x80)
    g.fillRect(0, 0, keyed.w, keyed.h)
    g.drawImage(keyed.toBuffered(), 0, 0, null)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
    g.stroke = BasicStroke(1.5f)
    for (c in comps) {
        g.color = Color(0, 255, 255)
        g.drawRect(c.x0, c.y0, c.x1 - c.x0, c.y1 - c.y0)
        g.color = Color.BLACK
        g.drawString("${c.id}", c.x0 + 3, c.y0 + 19)
        g.color = Color.YELLOW
        g.drawString("${c.id}", c.x0 + 2, c.y0 + 18)
    }
    g.dispose()
    ImageIO.write(bi, "png", File(outDir, "$name.overview.png"))
    for (c in comps) println("$name #${c.id}: box ${c.x0},${c.y0}-${c.x1},${c.y1} area ${c.area}")
}

class Bone(val name: String, val parent: String?, val px: Float, val py: Float)
class Part(val name: String, val bone: String, val piece: Piece)

internal class Sheet(file: File) {
    val img = defringe(keyMagenta(Img.load(file)))
    val labels: IntArray
    val comps: List<Comp>

    init {
        val (l, c) = components(img, 40)
        labels = l; comps = c
    }

    /** The separate piece nearest to each point (distance to its box, 0 inside). */
    fun piece(vararg at: KaiSpec.Pt): Piece = piece(ids(at.toList()))

    fun ids(at: List<KaiSpec.Pt>): Set<Int> = at.map { pt ->
        comps.minBy { c ->
            val dx = maxOf(c.x0 - pt.x, 0, pt.x - c.x1); val dy = maxOf(c.y0 - pt.y, 0, pt.y - c.y1)
            dx * dx + dy * dy
        }.id
    }.toSet()

    fun piece(ids: Set<Int>): Piece = extract(img, labels, comps, ids.toIntArray())
}

private fun build(dir: File, outDir: File, previewDir: File) {
    val ref = Img.load(File(dir, "ref.png"))
    val refChar = components(keyMagenta(ref), 1000).second.maxBy { it.area }
    val charBox = clampBox(Box(refChar.x0 - 40, refChar.y0 - 40, refChar.x1 + 40, refChar.y1 + 40), ref)
    val body = Sheet(File(dir, "body.png"))

    val s = fitScale(ref, listOf(body.piece(KaiSpec.TORSO), body.piece(KaiSpec.PELVIS)), charBox, 0.8f, 1.2f)
    println("body sheet -> ref scale ${"%.3f".format(s)}")
    fun placed(id: KaiSpec.Pt, scale: Float = s, box: Box = charBox): Piece {
        val p = body.piece(id).scaledToRef(scale)
        val f = place(ref, p, box)
        println("  piece at ${id.x},${id.y} ->  ${p.ox},${p.oy} (${p.w}x${p.h}), mean colour error ${"%.1f".format(f.perPixel)}")
        return p
    }

    // Gemini drew the head and hair smaller than the body on the sheet, so they get their own scale (fitted on the hair).
    val headBox = clampBox(Box(charBox.x0, charBox.y0, charBox.x1, refChar.y0 + (refChar.y1 - refChar.y0) / 2), ref)
    val sHead = fitScale(ref, listOf(body.piece(KaiSpec.HAIR)), headBox, 0.9f, 1.3f)
    println("head sheet -> ref scale ${"%.3f".format(sHead)}")
    val hair = placed(KaiSpec.HAIR, sHead, headBox)
    // The blank head has nothing to match on, so it is scaled and placed by its cheeks against ref.png's cheeks.
    val rawHead = body.piece(KaiSpec.HEAD)
    val (hl, hr) = cheeks(rawHead, Box(rawHead.ox, rawHead.oy, rawHead.ox + rawHead.w, rawHead.oy + rawHead.h))
    val refPiece = Piece(ref, 0, 0)
    val (rl, rr) = cheeks(refPiece, KaiSpec.refCheeks)
    val sCheek = (rr[0] - rl[0]) / (hr[0] - hl[0])
    val head = rawHead.scaledToRef(sCheek)
    head.ox = ((rl[0] + rr[0]) / 2 - ((hl[0] + hr[0]) / 2 - rawHead.ox) * sCheek).roundToInt()
    head.oy = ((rl[1] + rr[1]) / 2 - ((hl[1] + hr[1]) / 2 - rawHead.oy) * sCheek).roundToInt()
    println("  head by cheeks: scale ${"%.3f".format(sCheek)} at ${head.ox},${head.oy} (${head.w}x${head.h})")
    val torso = placed(KaiSpec.TORSO)
    val pelvis = placed(KaiSpec.PELVIS)
    // The waistband sits under the shirt hem; matching alone leaves a gap, so tuck it in.
    pelvis.oy = torso.oy + torso.h - 14
    val bones = ArrayList<Bone>()
    val parts = HashMap<String, Part>()

    bones += Bone("root", null, pelvis.ox + pelvis.w / 2f, pelvis.oy + pelvis.h / 2f)
    bones += Bone("torso", "root", rowCentre(torso, torso.oy + torso.h - 10), torso.oy + torso.h - 10f)
    val maxW = (torso.oy until torso.oy + torso.h).maxOf { rowWidth(torso, it) }
    val collar = (torso.oy until torso.oy + torso.h).first { rowWidth(torso, it) > maxW / 2 }
    bones += Bone("head", "torso", rowCentre(torso, collar - 8), collar - 8f)
    parts["pelvis"] = Part("pelvis", "root", pelvis)
    parts["torso"] = Part("torso", "torso", torso)
    parts["head"] = Part("head", "head", head)
    parts["hair"] = Part("hair", "head", hair)

    for ((side, id) in listOf("near" to KaiSpec.ARM_NEAR, "far" to KaiSpec.ARM_FAR)) {
        val arm = placed(id)
        val top = arm.oy; val h = arm.h
        val shoulderY = (top + 0.13f * h).roundToInt()
        val wristY = ((top + 0.6f * h).roundToInt()..(top + 0.85f * h).roundToInt()).minBy { rowWidth(arm, it) }
        val elbowY = (shoulderY + wristY) / 2
        val elbow = jointAtRow(arm, elbowY)
        val (upper, rest) = cutAt(arm, elbow)
        val wrist = jointAtRow(rest, wristY)
        val (fore, hand) = cutAt(rest, wrist)
        bones += Bone("shoulder_$side", "torso", rowCentre(arm, shoulderY), shoulderY.toFloat())
        bones += Bone("elbow_$side", "shoulder_$side", elbow.x, elbow.y)
        bones += Bone("wrist_$side", "elbow_$side", wrist.x, wrist.y)
        parts["arm_upper_$side"] = Part("arm_upper_$side", "shoulder_$side", upper)
        parts["arm_fore_$side"] = Part("arm_fore_$side", "elbow_$side", fore)
        parts["hand_$side"] = Part("hand_$side", "wrist_$side", hand)
        println("  arm $side: shoulder y=$shoulderY elbow ${elbow.x.roundToInt()},${elbow.y.roundToInt()} r=${elbow.radius.roundToInt()} wrist ${wrist.x.roundToInt()},${wrist.y.roundToInt()} r=${wrist.radius.roundToInt()}")
    }

    val isBlue = { c: Int -> b(c) > r(c) + 40 && b(c) > g(c) + 10 }
    val isShoe = { c: Int -> r(c) > 190 && r(c) - g(c) > 90 && b(c) < 120 }
    for ((side, id) in listOf("near" to KaiSpec.LEG_NEAR, "far" to KaiSpec.LEG_FAR)) {
        val leg = placed(id)
        val top = leg.oy; val h = leg.h
        val kneeY = (top until (top + 0.6f * h).roundToInt()).last { rowCount(leg, it, isBlue) > 0.3f * rowWidth(leg, it) } + 1
        val shoeTop = (max(kneeY + 10, (top + 0.7f * h).roundToInt()) until top + h).first { rowCount(leg, it, isShoe) > 0.2f * max(1, rowWidth(leg, it)) }
        val ankleY = shoeTop - 1
        val hipY = (top + 0.2f * (kneeY - top)).roundToInt()
        val knee = jointAtRow(leg, kneeY)
        val (thigh, rest) = cutAt(leg, knee, capBefore = false)
        val ankle = jointAtRow(rest, ankleY)
        val (shin, shoe) = cutAt(rest, ankle, capAfter = false)
        bones += Bone("hip_$side", "root", rowCentre(leg, hipY), hipY.toFloat())
        bones += Bone("knee_$side", "hip_$side", knee.x, knee.y)
        bones += Bone("ankle_$side", "knee_$side", ankle.x, ankle.y)
        parts["thigh_$side"] = Part("thigh_$side", "hip_$side", thigh)
        parts["shin_$side"] = Part("shin_$side", "knee_$side", shin)
        parts["shoe_$side"] = Part("shoe_$side", "ankle_$side", shoe)
        println("  leg $side: hip y=$hipY knee ${knee.x.roundToInt()},${knee.y.roundToInt()} r=${knee.radius.roundToInt()} ankle ${ankle.x.roundToInt()},${ankle.y.roundToInt()} r=${ankle.radius.roundToInt()}")
    }

    addFaceParts(
        dir, parts, mapOf(
            "eye_near_open" to KaiSpec.refEyeNear, "eye_far_open" to KaiSpec.refEyeFar,
            "brow_near" to KaiSpec.refBrowNear, "brow_far" to KaiSpec.refBrowFar, "mouth_smile" to KaiSpec.refMouth,
        ),
        skin = null,
    )
    parts["nose"] = Part("nose", "head", cutNose(ref, KaiSpec.noseBox))

    writeOut(outDir, bones, parts)
    renderPreviews(previewDir, ref, bones, parts)
}

/**
 * Face parts: shapes from the face sheets (FaceSheet.kt picks which piece is which); sizes and positions from the
 * base parts' ref.png [boxes] (what `cutref`/`cutauto` found while erasing). Each variant is scaled like its sheet's
 * scale base (eyes and brows by the near eye, mouths by the smile) and centred on its base part. [skin]: the
 * character's skin colour for the shape checks of auto detection (null: no checks, face_map.csv is required).
 */
internal fun addFaceParts(dir: File, parts: HashMap<String, Part>, boxes: Map<String, Box>, skin: Int?) {
    if (!File(dir, "face.png").exists() && !File(dir, "face_map.csv").exists()) {
        println("  no face sheet in $dir: only ref.png's own eyes and mouth (the app falls back to them)")
        return
    }
    val (picks, sheets) = if (skin != null) facePicks(dir, skin) else {
        val sh = HashMap<String, Sheet>()
        val fs = readFaceMap(dir) { n -> sh.getOrPut(n) { Sheet(File(dir, "$n.png")) } } ?: error("no face_map.csv in $dir")
        fs to sh
    }
    val byName = picks.associateBy { it.name }
    // Sheet pieces without any skin disc a generator drew behind them (sizes are measured on the feature itself).
    val cache = HashMap<FacePick, Piece>()
    fun sheetPiece(fp: FacePick): Piece = cache.getOrPut(fp) {
        val raw = sheets.getValue(fp.sheet).piece(fp.ids)
        if (skin == null) raw else stripSkinPatch(raw, skin).also { if (it.w != raw.w || it.h != raw.h) println("  ${fp.name}: skin disc removed") }
    }
    val scale = HashMap<Pair<String, String>, Float>()
    fun scaleOf(fp: FacePick): Float = scale.getOrPut(fp.sheet to faceScaleBase(fp.name)) {
        val baseName = faceScaleBase(fp.name)
        val base = byName[baseName]?.takeIf { it.sheet == fp.sheet } ?: error("${fp.name}: no $baseName on sheet ${fp.sheet} to scale by")
        val box = boxes.getValue(baseName)
        ((box.x1 - box.x0).toFloat() / sheetPiece(base).w)
            .also { println("${fp.sheet} sheet ($baseName) -> ref scale ${"%.3f".format(it)}") }
    }
    for (fp in picks) {
        if (faceScaleBase(fp.name) !in boxes || (fp.name !in boxes && faceBase(fp.name) !in boxes)) {
            println("  ${fp.name}: skipped, ref.png has no ${faceScaleBase(fp.name)} / ${faceBase(fp.name)} to size and place it by")
            continue
        }
        val p = sheetPiece(fp).scaledToRef(scaleOf(fp))
        val box = boxes[fp.name]
        val cx: Int; val cy: Int
        if (box != null) { cx = (box.x0 + box.x1) / 2; cy = (box.y0 + box.y1) / 2 } else {
            val base = boxes[faceBase(fp.name)] ?: error("${fp.name}: no ${faceBase(fp.name)} to centre on")
            cx = (base.x0 + base.x1) / 2; cy = (base.y0 + base.y1) / 2
        }
        p.ox = cx - p.w / 2; p.oy = cy - p.h / 2
        parts[fp.name] = Part(fp.name, "head", p)
    }
}

/** The nose is a thin line on flat skin: alpha from the distance to the skin colour. */
private fun cutNose(ref: Img, box: Box): Piece {
    val w = box.x1 - box.x0; val h = box.y1 - box.y0
    val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
    for (y in box.y0 until box.y1) for (x in box.x0 until box.x1) { val c = ref[x, y]; rs += r(c); gs += g(c); bs += b(c) }
    rs.sort(); gs.sort(); bs.sort()
    val skin = intArrayOf(rs[rs.size / 2], gs[gs.size / 2], bs[bs.size / 2])
    val out = Img(w, h)
    var darkest = 0; var darkD = -1
    for (y in 0 until h) for (x in 0 until w) {
        val c = ref[x + box.x0, y + box.y0]
        val d = kotlin.math.abs(r(c) - skin[0]) + kotlin.math.abs(g(c) - skin[1]) + kotlin.math.abs(b(c) - skin[2])
        if (d > darkD) { darkD = d; darkest = c }
        val al = ((d - 25) / 45f).coerceIn(0f, 1f)
        out[x, y] = argb((al * 255).roundToInt(), 0, 0, 0)
    }
    for (i in out.px.indices) out.px[i] = (out.px[i] and (0xFF shl 24)) or (darkest and 0xFFFFFF)
    return Piece(out, box.x0, box.y0).trimmed()
}

/** Back-to-front draw order of the rest pose. Variants (other eyes, mouths) are written but not drawn. */
val drawOrder = listOf(
    "arm_upper_far", "arm_fore_far", "hand_far",
    "shin_far", "shoe_far", "thigh_far",
    "shin_near", "shoe_near", "thigh_near",
    "pelvis", "torso", "head", "nose",
    "eye_near_open", "eye_far_open", "brow_near", "brow_far", "mouth_smile",
    "hair",
    "arm_upper_near", "arm_fore_near", "hand_near",
)

internal fun writeOut(outDir: File, bones: List<Bone>, parts: Map<String, Part>) {
    val partsDir = File(outDir, "parts")
    partsDir.deleteRecursively()
    for (p in parts.values) p.piece.img.save(File(partsDir, "${p.name}.png"))
    File(outDir, "bones.csv").writeText(
        "# bone,parent,pivotX,pivotY (ref.png pixels, rest pose)\n" +
            bones.joinToString("\n") { "${it.name},${it.parent ?: ""},${"%.1f".format(it.px)},${"%.1f".format(it.py)}" } + "\n",
    )
    val drawn = drawOrder.filter { it in parts }
    val order = drawn + parts.keys.filter { it !in drawn }.sorted()
    File(outDir, "parts.csv").writeText(
        "# part,bone,x,y,w,h,drawn (x,y = top-left in ref.png pixels; drawn=1 in the rest pose, in draw order)\n" +
            order.joinToString("\n") {
                val p = parts.getValue(it).piece
                "$it,${parts.getValue(it).bone},${p.ox},${p.oy},${p.w},${p.h},${if (it in drawn) 1 else 0}"
            } + "\n",
    )
    println("wrote ${parts.size} parts, ${bones.size} bones to $outDir")
}

private fun renderPose(ref: Img, bones: List<Bone>, parts: Map<String, Part>, angles: Map<String, Double>, swap: Map<String, String> = emptyMap(), pivots: Boolean = false): BufferedImage {
    val bi = BufferedImage(ref.w, ref.h, BufferedImage.TYPE_INT_ARGB)
    val g = bi.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(0xCF, 0xEF, 0xFF)
    g.fillRect(0, 0, ref.w, ref.h)
    val byName = bones.associateBy { it.name }
    val world = HashMap<String, AffineTransform>()
    fun worldOf(n: String): AffineTransform = world.getOrPut(n) {
        val b = byName.getValue(n)
        val t = if (b.parent == null) AffineTransform() else AffineTransform(worldOf(b.parent))
        t.rotate(Math.toRadians(angles[n] ?: 0.0), b.px.toDouble(), b.py.toDouble())
        t
    }
    for (name in drawOrder) {
        if (name !in parts) continue
        val part = parts.getValue(swap[name] ?: name)
        val old = g.transform
        g.transform(worldOf(part.bone))
        g.drawImage(part.piece.img.toBuffered(), part.piece.ox, part.piece.oy, null)
        g.transform = old
    }
    if (pivots) {
        g.color = Color.RED
        for (b in bones) {
            val p = worldOf(b.name).transform(java.awt.geom.Point2D.Double(b.px.toDouble(), b.py.toDouble()), null)
            g.fillOval(p.x.roundToInt() - 4, p.y.roundToInt() - 4, 8, 8)
        }
    }
    g.dispose()
    return bi
}

internal fun renderPreviews(dir: File, ref: Img, bones: List<Bone>, parts: Map<String, Part>) {
    dir.mkdirs()
    // Pose angles below are relative to "arms hanging straight down", whatever the rest pose is.
    val byName = bones.associateBy { it.name }
    fun hang(side: String): Double {
        val s = byName.getValue("shoulder_$side"); val w = byName.getValue("wrist_$side")
        return 90.0 - Math.toDegrees(kotlin.math.atan2((w.py - s.py).toDouble(), (w.px - s.px).toDouble()))
    }
    val hangN = hang("near"); val hangF = hang("far")
    fun relax(m: Map<String, Double>) = m + mapOf(
        "shoulder_near" to (m["shoulder_near"] ?: 0.0) + hangN,
        "shoulder_far" to (m["shoulder_far"] ?: 0.0) + hangF,
    )
    val rest = renderPose(ref, bones, parts, emptyMap())
    val side = BufferedImage(ref.w * 2, ref.h, BufferedImage.TYPE_INT_ARGB)
    side.createGraphics().apply { drawImage(ref.toBuffered(), 0, 0, null); drawImage(rest, ref.w, 0, null); dispose() }
    ImageIO.write(side, "png", File(dir, "rest_vs_ref.png"))
    ImageIO.write(renderPose(ref, bones, parts, emptyMap(), pivots = true), "png", File(dir, "pivots.png"))
    // Sign: positive = clockwise on screen.
    val dance = mapOf(
        "torso" to -4.0, "head" to 8.0,
        "shoulder_near" to 120.0, "elbow_near" to 40.0, "wrist_near" to -20.0,
        "shoulder_far" to -40.0, "elbow_far" to -70.0,
        "hip_near" to 25.0, "knee_near" to -45.0, "ankle_near" to 15.0,
        "hip_far" to -15.0, "knee_far" to 20.0,
    )
    ImageIO.write(
        renderPose(ref, bones, parts, relax(dance), swap = mapOf("mouth_smile" to "mouth_laugh", "eye_near_open" to "eye_near_happy", "eye_far_open" to "eye_far_happy").filterValues { it in parts }),
        "png", File(dir, "pose_dance.png"),
    )
    val bend = mapOf(
        "shoulder_near" to 30.0, "elbow_near" to -60.0, "wrist_near" to 30.0,
        "shoulder_far" to -30.0, "elbow_far" to 60.0, "wrist_far" to -30.0,
        "hip_near" to -45.0, "knee_near" to 60.0, "ankle_near" to -20.0,
        "hip_far" to 30.0, "knee_far" to 45.0, "ankle_far" to 20.0,
    )
    ImageIO.write(renderPose(ref, bones, parts, relax(bend), swap = mapOf("mouth_smile" to "mouth_o", "eye_near_open" to "eye_near_wide", "eye_far_open" to "eye_far_wide").filterValues { it in parts }), "png", File(dir, "pose_bend.png"))
}
