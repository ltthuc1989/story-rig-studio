package com.example.storyrig.tools

import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Which piece of which face sheet is which expression.
 *
 * Shape tells some of it (an eye has white and no tongue, a half-closed eye a skin-coloured lid, a closed eye or a
 * smile is a thin curved stroke, an O mouth is a small round dark blob), but not all: a raised brow looks like a
 * neutral one, A / S / laugh are all big open mouths, a happy closed eye is the same arc as a sad mouth. So the
 * names come from a fixed layout that prompts/2_face_sheet.txt asks for ([LAYOUT]: rows top to bottom, pieces left
 * to right), and shape only checks it. Kai's sheets predate that layout: their mapping is data, face_map.csv next to
 * the sheets (part, sheet, a point on the piece), which wins when present.
 */

/** A sheet piece chosen for a face part: components [ids] of [sheet]'s keyed image. */
class FacePick(val name: String, val sheet: String, val ids: Set<Int>)

/** What shape says a piece is. */
enum class FaceKind { EYE_OPEN, EYE_HALF, ARC_DOWN, ARC_UP, BROW, MOUTH_OPEN, MOUTH_ROUND }

/** The fixed layout of a face sheet (prompts/2_face_sheet.txt): one list per row, near before far. */
val LAYOUT = listOf(
    listOf("eye_near_open", "eye_far_open", "eye_near_half", "eye_far_half"),
    listOf("eye_near_happy", "eye_far_happy", "eye_near_wide", "eye_far_wide"),
    listOf("brow_near", "brow_far", "brow_near_raised", "brow_far_raised"),
    listOf("brow_near_frown", "brow_far_frown", "brow_near_sad", "brow_far_sad"),
    listOf("mouth_smile", "mouth_e", "mouth_a", "mouth_o"),
    listOf("mouth_s", "mouth_laugh", "mouth_sad"),
)

/** Face parts the app needs (KaiRig.MOUTHS, EYES and the brows). */
val REQUIRED_FACE = listOf("open", "half", "happy", "wide").flatMap { listOf("eye_near_$it", "eye_far_$it") } +
    listOf("brow_near", "brow_far") + listOf("smile", "e", "a", "o", "s", "laugh").map { "mouth_$it" }

/** The shape a part's piece must have. */
fun expectedKind(name: String): FaceKind = when {
    name.startsWith("brow_") -> FaceKind.BROW
    name.endsWith("_half") -> FaceKind.EYE_HALF
    name.endsWith("_happy") -> FaceKind.ARC_DOWN
    name.startsWith("eye_") -> FaceKind.EYE_OPEN
    name == "mouth_smile" -> FaceKind.ARC_UP
    name == "mouth_sad" -> FaceKind.ARC_DOWN
    name == "mouth_o" -> FaceKind.MOUTH_ROUND
    else -> FaceKind.MOUTH_OPEN
}

/** The part a face part is centred on and scaled with (the base parts come from ref.png itself). */
fun faceBase(name: String): String = when {
    name.startsWith("eye_near") -> "eye_near_open"
    name.startsWith("eye_far") -> "eye_far_open"
    name.startsWith("brow_near") -> "brow_near"
    name.startsWith("brow_far") -> "brow_far"
    else -> "mouth_smile"
}

/** Base part whose ref.png width sets the scale of [name] (eyes and brows by the near eye, mouths by the smile). */
fun faceScaleBase(name: String) = if (name.startsWith("mouth")) "mouth_smile" else "eye_near_open"

/** One piece of a face sheet: a component plus small light bits next to it (a lower lip under a mouth). */
class SheetPiece(
    val sheet: String, val ids: Set<Int>, val x0: Int, val y0: Int, val x1: Int, val y1: Int, val kind: FaceKind,
    /** Drawn on a patch of skin (the prompt asks for none: the patch would cover the face around it). */
    val patch: Boolean,
    val note: String,
) {
    val cx get() = (x0 + x1) / 2f
    val cy get() = (y0 + y1) / 2f
    val w get() = x1 - x0 + 1
}

/**
 * Pieces of a sheet, each classified by shape against the character's [skin] colour, in reading order (rows
 * top to bottom, left to right; a piece joins a row when its centre is inside the row's height).
 */
internal fun sheetPieces(name: String, sh: Sheet, skin: Int): List<List<SheetPiece>> {
    val img = sh.img
    val comps = sh.comps.sortedByDescending { it.area }
    val owner = HashMap<Int, Comp>()
    // Satellites: small, not dark (a dark one is a brow next to its eye), within a short gap of a piece 3x bigger:
    // a lower lip drawn apart from its mouth, moustache tips beside it. The gap allowed grows with the host.
    val gapMin = (15f * img.w / 1024).roundToInt()
    fun gapMax(host: Comp) = max(gapMin, (host.y1 - host.y0 + 1) / 2)
    fun gap(a: Comp, b: Comp) = max(max(a.x0 - b.x1, b.x0 - a.x1), max(a.y0 - b.y1, b.y0 - a.y1))
    fun meanLum(c: Comp): Float {
        var s = 0f; var n = 0
        for (y in c.y0..c.y1) for (x in c.x0..c.x1) if (sh.labels[y * img.w + x] == c.id) { val p = img[x, y]; s += lum(p); n++ }
        return s / max(1, n)
    }
    for (c in comps.reversed()) {
        val host = comps.filter { it !== c && it.area >= 3 * c.area && gap(c, it) <= gapMax(it) }.minByOrNull { gap(c, it) } ?: continue
        // A dark one is still a satellite below or beside its host (a lower lip); a brow sits above its eye.
        val below = (c.y0 + c.y1) / 2 > host.y0 + (host.y1 - host.y0) / 4
        if (meanLum(c) > 100f || below) owner[c.id] = host
    }
    val groups = comps.filter { it.id !in owner }.map { main -> main to (listOf(main) + comps.filter { owner[it.id] === main }) }
    val pieces = groups.map { (main, all) -> classify(name, img, sh.labels, main, all, skin) }
    // Rows.
    val rows = ArrayList<MutableList<SheetPiece>>()
    for (p in pieces.sortedBy { it.cy }) {
        val row = rows.lastOrNull()
        if (row != null && p.cy <= row.maxOf { it.y1 } && p.cy >= row.minOf { it.y0 }) row += p else rows += mutableListOf(p)
    }
    val out = rows.map { r -> r.sortedBy { it.cx } }
    return out
}

private fun lum(c: Int) = r(c) * .299f + g(c) * .587f + b(c) * .114f

private const val WHITE = 1
private const val DARK = 2
private const val PINK = 3
private const val SKIN = 4

/** Colour class of an opaque pixel: white (eye, teeth), dark (iris, brow, mouth inside), pink (tongue, lip), skin-like. */
private fun colourClass(p: Int, darkLum: Float): Int {
    val mx = max(r(p), max(g(p), b(p))); val mn = min(r(p), min(g(p), b(p)))
    return when {
        mn > 200 && mx - mn < 40 -> WHITE
        lum(p) < darkLum -> DARK
        r(p) > 150 && r(p) - g(p) > 50 && g(p) - b(p) < 25 -> PINK
        r(p) > 150 && r(p) >= g(p) && g(p) - b(p) >= 25 -> SKIN
        else -> 0
    }
}

private fun classify(sheet: String, img: Img, labels: IntArray, main: Comp, all: List<Comp>, skin: Int): SheetPiece {
    val ids = all.map { it.id }.toSet()
    val darkLum = 0.45f * lum(skin)
    val y0 = all.minOf { it.y0 }; val y1 = all.maxOf { it.y1 }
    fun own(x: Int, y: Int) = img.inside(x, y) && labels[y * img.w + x] in ids && a(img[x, y]) > 127
    // A skin patch around the feature (older sheets): most of the outline (2 px in), or most of the piece, is skin.
    // Then only the rest counts.
    var edge = 0; var edgeSkin = 0; var all0 = 0; var allSkin = 0
    for (c in all) for (y in c.y0..c.y1) for (x in c.x0..c.x1) {
        if (labels[y * img.w + x] != c.id || a(img[x, y]) < 200) continue
        val k = colourClass(img[x, y], darkLum)
        all0++; if (k == SKIN) allSkin++
        if ((-2..2).all { d -> own(x + d, y) && own(x, y + d) }) continue
        edge++
        if (k == SKIN) edgeSkin++
    }
    val patch = edgeSkin > 0.75f * edge || allSkin > 0.55f * all0
    val count = IntArray(5); var pinkY = 0f
    for (c in all) for (y in c.y0..c.y1) for (x in c.x0..c.x1) {
        if (labels[y * img.w + x] != c.id || a(img[x, y]) < 200) continue
        val k = colourClass(img[x, y], darkLum)
        if (patch && k == SKIN) continue
        count[k]++
        if (k == PINK) pinkY += (y - y0).toFloat() / max(1, y1 - y0)
    }
    val n = count.sum().toFloat().coerceAtLeast(1f)
    val sWhite = count[WHITE] / n; val sDark = count[DARK] / n; val sPink = count[PINK] / n; val sSkin = count[SKIN] / n
    pinkY /= max(1, count[PINK])
    // Shape of the main component (without a patch): column centres against the chord between the end columns.
    fun inShape(x: Int, y: Int) = labels[y * img.w + x] == main.id && a(img[x, y]) > 127 && !(patch && colourClass(img[x, y], darkLum) == SKIN)
    val centres = FloatArray(main.x1 - main.x0 + 1) { Float.NaN }
    var area = 0; var sy0 = Int.MAX_VALUE; var sy1 = -1
    for (x in main.x0..main.x1) {
        var s = 0f; var k = 0
        for (y in main.y0..main.y1) if (inShape(x, y)) { s += y; k++; sy0 = min(sy0, y); sy1 = max(sy1, y) }
        if (k > 0) centres[x - main.x0] = s / k
        area += k
    }
    val cols = centres.indices.filter { !centres[it].isNaN() }
    val e0 = cols.first(); val e1 = cols.last()
    val w = e1 - e0 + 1; val h = sy1 - sy0 + 1
    // Ends: mean over the outer 10% so a pointed tip does not decide.
    val tip = max(1, (cols.size * .1f).roundToInt())
    val yL = cols.take(tip).map { centres[it] }.average().toFloat(); val yR = cols.takeLast(tip).map { centres[it] }.average().toFloat()
    var sag = 0f
    for (i in cols) {
        val chord = yL + (yR - yL) * (i - e0) / max(1, e1 - e0)
        val d = centres[i] - chord
        if (abs(d) > abs(sag)) sag = d
    }
    val bend = abs(sag) / w
    val thick = area.toFloat() / cols.size / h
    val round = h.toFloat() / w in 0.7f..1.8f && area > 0.6f * w * h
    val kind = when {
        // Eye: white, and no tongue (pink low in the piece; a pink lid edge sits at the top).
        sWhite >= 0.12f && (sPink < 0.05f || pinkY < 0.5f) -> if (sSkin >= 0.12f || sPink >= 0.05f) FaceKind.EYE_HALF else FaceKind.EYE_OPEN
        round && sWhite < 0.03f -> FaceKind.MOUTH_ROUND
        sPink >= 0.05f && sWhite >= 0.03f -> FaceKind.MOUTH_OPEN
        // A curved stroke: a closed eye, a closed or sad mouth. Brows bend less for their thickness.
        bend >= 0.18f || (bend >= 0.1f && thick < 0.3f) -> if (sag < 0) FaceKind.ARC_DOWN else FaceKind.ARC_UP
        else -> FaceKind.BROW
    }
    val note = "white %.2f dark %.2f pink %.2f (y %.2f) skin %.2f bend %.2f thick %.2f edge skin %.2f%s".format(sWhite, sDark, sPink, pinkY, sSkin, sag / w, thick, edgeSkin / max(1f, edge.toFloat()), if (patch) " on skin patch" else "")
    return SheetPiece(sheet, ids, all.minOf { it.x0 }, y0, all.maxOf { it.x1 }, y1, kind, patch, note)
}

/**
 * Names by the fixed [LAYOUT]: one sheet whose rows hold exactly the layout's counts. Shape mismatches are
 * printed as warnings (check the sheet, or make it again). Null when the rows do not fit the layout.
 */
internal fun byLayout(rows: List<List<SheetPiece>>): List<Pair<String, SheetPiece>>? {
    if (rows.map { it.size } != LAYOUT.map { it.size }) return null
    val out = ArrayList<Pair<String, SheetPiece>>()
    for ((r, names) in LAYOUT.withIndex()) for ((i, n) in names.withIndex()) out += n to rows[r][i]
    for ((n, p) in out) if (p.kind != expectedKind(n)) println("  WARNING face: $n looks like ${p.kind}, expected ${expectedKind(n)} (${p.note})")
    for ((n, p) in out) if (p.patch) println("  WARNING face: $n is drawn on a skin patch")
    for ((n, p) in out) if (n.contains("_far")) {
        val near = out.first { it.first == n.replace("_far", "_near") }.second
        if (p.w > near.w * 1.1f) println("  WARNING face: $n is wider than ${n.replace("_far", "_near")}")
    }
    return out
}

/**
 * Names by shape alone, for a sheet without the layout (best effort; Kai's old sheets). The cues:
 *  - near and far come in pairs side by side in a row, the far one at least 10% narrower;
 *  - a closed eye (an arc bending down) sits under a brow; the same arc with no brow over it is a sad mouth;
 *  - a brow right over an eye repeats the neutral brow; brows on their own are the expressions (raised, frown, sad);
 *  - otherwise each kind's pieces keep [LAYOUT] order in reading order (open before wide, A before S before laugh).
 * Raised vs neutral brows and the open mouths are only told apart by order, so this is a guess to check.
 */
internal fun byShape(rows: List<List<SheetPiece>>): List<Pair<String, SheetPiece>> {
    val all = rows.flatten()
    fun overlapX(a: SheetPiece, b: SheetPiece) = (min(a.x1, b.x1) - max(a.x0, b.x0) + 1).toFloat() / min(a.w, b.w)
    fun above(top: SheetPiece, below: SheetPiece) =
        overlapX(top, below) >= 0.5f && below.y0 - top.y1 in -((below.y1 - below.y0) * 0.15f).roundToInt()..((below.y1 - below.y0) * 0.6f).roundToInt()
    val eyeKinds = setOf(FaceKind.EYE_OPEN, FaceKind.EYE_HALF, FaceKind.ARC_DOWN)
    val closedEye = all.filter { e -> e.kind == FaceKind.ARC_DOWN && all.any { it.kind == FaceKind.BROW && above(it, e) } }.toSet()
    val sadMouth = all.filter { it.kind == FaceKind.ARC_DOWN && it !in closedEye }
    fun eyeLike(p: SheetPiece) = p.kind in eyeKinds && (p.kind != FaceKind.ARC_DOWN || p in closedEye)
    val eyeBrows = all.filter { b -> b.kind == FaceKind.BROW && all.any { eyeLike(it) && above(b, it) } }.toSet()

    // Near/far pairs among [ps] (reading order): side by side in one row, the right one narrower.
    fun pairs(ps: List<SheetPiece>): List<Pair<SheetPiece, SheetPiece>> {
        val byRow = rows.map { r -> r.filter { it in ps } }
        val out = ArrayList<Pair<SheetPiece, SheetPiece>>()
        for (r in byRow) {
            var i = 0
            while (i + 1 < r.size) if (r[i + 1].w < 0.9f * r[i].w) { out += r[i] to r[i + 1]; i += 2 } else i++
        }
        if (out.isNotEmpty()) return out
        return byRow.flatMap { r -> r.chunked(2).filter { it.size == 2 }.map { it[0] to it[1] } }
    }
    val out = ArrayList<Pair<String, SheetPiece>>()
    fun name(pairs: List<Pair<SheetPiece, SheetPiece>>, vararg kinds: String) {
        for ((k, pr) in kinds.zip(pairs)) {
            if (k.isEmpty()) continue
            out += k.replace("SIDE", "near") to pr.first
            out += k.replace("SIDE", "far") to pr.second
        }
    }
    name(pairs(all.filter { it.kind == FaceKind.EYE_OPEN }), "eye_SIDE_open", "eye_SIDE_wide")
    name(pairs(all.filter { it.kind == FaceKind.EYE_HALF }), "eye_SIDE_half")
    name(pairs(closedEye.toList()), "eye_SIDE_happy")
    val brows = all.filter { it.kind == FaceKind.BROW }
    val neutral = pairs(brows.filter { it in eyeBrows }.ifEmpty { brows }).take(1)
    name(neutral, "brow_SIDE")
    val used = neutral.flatMap { listOf(it.first, it.second) }.toSet()
    name(pairs(brows.filter { it !in eyeBrows && it !in used }), "brow_SIDE_raised", "brow_SIDE_frown", "brow_SIDE_sad")
    all.firstOrNull { it.kind == FaceKind.ARC_UP }?.let { out += "mouth_smile" to it }
    for ((n, p) in listOf("mouth_e", "mouth_a", "mouth_s", "mouth_laugh").zip(all.filter { it.kind == FaceKind.MOUTH_OPEN })) out += n to p
    all.firstOrNull { it.kind == FaceKind.MOUTH_ROUND }?.let { out += "mouth_o" to it }
    sadMouth.firstOrNull()?.let { out += "mouth_sad" to it }
    return out
}

/** face_map.csv: `part,sheet,x,y[,x,y..]` (a point inside or nearest each piece on the sheet). Null if absent. */
internal fun readFaceMap(dir: File, sheets: (String) -> Sheet): List<FacePick>? {
    val f = File(dir, "face_map.csv")
    if (!f.exists()) return null
    return f.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { line ->
        val c = line.split(',').map { it.trim() }
        val pts = (2 until c.size step 2).map { KaiSpec.Pt(c[it].toInt(), c[it + 1].toInt()) }
        FacePick(c[0], c[1], sheets(c[1]).ids(pts))
    }
}

/**
 * The face parts to cut: face_map.csv if present (auto detection is then only printed next to it), else the
 * layout of face.png. [skin]: the character's skin colour (for the shape checks).
 */
internal fun facePicks(dir: File, skin: Int): Pair<List<FacePick>, Map<String, Sheet>> {
    val sheets = HashMap<String, Sheet>()
    fun sheet(n: String) = sheets.getOrPut(n) { Sheet(File(dir, "$n.png")) }
    val manual = readFaceMap(dir, ::sheet)
    val names = (manual?.map { it.sheet }?.distinct() ?: listOf("face"))
    val auto = HashMap<String, Pair<String, List<Pair<String, SheetPiece>>>>()
    for (n in names) {
        val rows = sheetPieces(n, sheet(n), skin)
        val lay = byLayout(rows)
        auto[n] = if (lay != null) "layout" to lay else "shape" to byShape(rows)
        println("face sheet $n: ${rows.size} rows of ${rows.map { it.size }}, names by ${auto.getValue(n).first}")
    }
    if (manual == null) {
        val (how, named) = auto.getValue("face")
        if (how != "layout") {
            println("  WARNING face: face.png does not follow the layout of prompts/2_face_sheet.txt (rows of ${LAYOUT.map { it.size }}); names guessed by shape, check them:")
            val rows = sheetPieces("face", sheet("face"), skin)
            for ((i, r) in rows.withIndex()) for (p in r) {
                val n = named.firstOrNull { it.second === p }?.first ?: "-"
                println("    row $i #${p.ids.sorted().joinToString("+")} ${p.x0},${p.y0}-${p.x1},${p.y1} ${p.kind} -> $n (${p.note})")
            }
        }
        for ((n, p) in named) println("  face $n: face#${p.ids.sorted().joinToString("+")} ${p.kind}")
        val missing = REQUIRED_FACE.filter { r -> named.none { it.first == r } }
        require(missing.isEmpty()) { "face.png: no ${missing.joinToString()}; make it again (prompts/2_face_sheet.txt) or write face_map.csv" }
        return named.map { (n, p) -> FacePick(n, "face", p.ids) } to sheets
    }
    // Auto next to manual: the kind shape gives the manual piece, and what auto would have picked.
    println("face part           manual piece (kind)                    auto (same sheet)        ok")
    var kindOk = 0; var pickOk = 0
    for (m in manual) {
        val rows = sheetPieces(m.sheet, sheet(m.sheet), skin).flatten()
        val mp = rows.firstOrNull { it.ids == m.ids } ?: rows.first { m.ids.containsAll(it.ids) || it.ids.containsAll(m.ids) }
        val ap = auto.getValue(m.sheet).second.firstOrNull { it.first == m.name }?.second
        val kOk = mp.kind == expectedKind(m.name); if (kOk) kindOk++
        val pOk = ap != null && ap.ids == m.ids; if (pOk) pickOk++
        println(
            "%-19s %-10s %-26s %-24s %s".format(
                m.name, "${m.sheet}#${m.ids.sorted().joinToString("+")}", "${mp.kind}${if (kOk) "" else " (want ${expectedKind(m.name)})"}",
                ap?.let { "#${it.ids.sorted().joinToString("+")} ${it.kind}" } ?: "-", if (pOk) "same" else "differs",
            ),
        )
    }
    println("face auto vs manual: kind right $kindOk/${manual.size}, same piece $pickOk/${manual.size}")
    return manual to sheets
}
