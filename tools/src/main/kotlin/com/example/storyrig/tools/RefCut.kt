package com.example.storyrig.tools

import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * `cutref`: Kai's body parts cut straight out of ref.png, so every part has exactly the reference's scale,
 * colours and shapes. Only the face comes from a second sheet (eyes, brows, mouths to swap).
 *
 * 1. Key the background.
 * 2. Flood-fill each part from a seed, stopped by cut lines and, where parts touch, by colour.
 * 3. Split limbs at elbow/wrist/knee/ankle and add round caps (smeared from the cut line) so joints never gap.
 * 4. Erase the painted eyes, brows and mouth from the head; their boxes place the swappable face parts.
 *
 * Where the points and colour tests come from is up to the [RefSpec]: hand-measured (KaiRefSpec, `cutref`) or
 * measured from the mannequin template and the character's shape (AutoSpec.kt, `cutauto`).
 */

private class Canvas(val w: Int, val h: Int) {
    val blocked = BooleanArray(w * h)
    val taken = BooleanArray(w * h)

    fun block(s: Seg) {
        val len = hypot(s.x2 - s.x1, s.y2 - s.y1)
        var t = 0f
        while (t <= len) {
            val x = (s.x1 + (s.x2 - s.x1) * t / len).roundToInt()
            val y = (s.y1 + (s.y2 - s.y1) * t / len).roundToInt()
            for ((dx, dy) in listOf(0 to 0, 1 to 0, 0 to 1)) if (x + dx in 0 until w && y + dy in 0 until h) blocked[(y + dy) * w + x + dx] = true
            t += .4f
        }
    }
}

/** Points within [radius] of a polyline (a limb's axis): where a limb may be when its colour matches its neighbour's. */
private class Capsule(val pts: List<Pt>, val radius: FloatArray) {
    fun contains(x: Float, y: Float): Boolean {
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            val dx = b.x - a.x; val dy = b.y - a.y
            val t = (((x - a.x) * dx + (y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
            if (hypot(x - a.x - t * dx, y - a.y - t * dy) <= radius[i]) return true
        }
        return false
    }
}

/** 4-connected flood from [seed] over opaque, untaken, unblocked pixels that pass [allow], inside [clip] and [within]. */
private fun region(img: Img, cv: Canvas, seed: Pt, clip: Box? = null, within: Capsule? = null, allow: (Int) -> Boolean = { true }): BooleanArray {
    val w = img.w
    val mask = BooleanArray(img.px.size)
    val start = seed.y.roundToInt() * w + seed.x.roundToInt()
    require(a(img.px[start]) > 127 && !cv.taken[start]) { "seed ${seed.x},${seed.y} is not on a free opaque pixel" }
    val stack = IntArray(img.px.size)
    var sp = 0
    stack[sp++] = start; mask[start] = true
    while (sp > 0) {
        val p = stack[--sp]
        val x = p % w; val y = p / w
        for ((dx, dy) in NEIGH4) {
            val nx = x + dx; val ny = y + dy
            if (!img.inside(nx, ny)) continue
            if (clip != null && (nx < clip.x0 || nx >= clip.x1 || ny < clip.y0 || ny >= clip.y1)) continue
            val q = ny * w + nx
            if (mask[q] || cv.taken[q] || cv.blocked[q]) continue
            if (within != null && !within.contains(nx + .5f, ny + .5f)) continue
            val c = img.px[q]
            if (a(c) <= 127 || !allow(c)) continue
            mask[q] = true
            stack[sp++] = q
        }
    }
    // Soft edge pixels next to the region (within 2 px) belong to it too.
    val soft = mask.copyOf()
    for (p in mask.indices) {
        if (mask[p] || cv.taken[p] || a(img.px[p]) == 0 || a(img.px[p]) > 127) continue
        val x = p % w; val y = p / w
        loop@ for (dy in -2..2) for (dx in -2..2) {
            val nx = x + dx; val ny = y + dy
            if (img.inside(nx, ny) && mask[ny * w + nx]) { soft[p] = true; break@loop }
        }
    }
    for (p in soft.indices) if (soft[p]) cv.taken[p] = true
    return soft
}

private val NEIGH4 = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

private fun pieceOf(img: Img, mask: BooleanArray): Piece {
    val out = Img(img.w, img.h)
    for (p in mask.indices) if (mask[p]) out.px[p] = img.px[p]
    return Piece(out, 0, 0).trimmed()
}

/** Joint on a cut line: midpoint, direction pointing to the side the cap grows into, radius = half its length. */
private fun jointOn(s: Seg, towardX: Float, towardY: Float): Joint {
    val mx = (s.x1 + s.x2) / 2; val my = (s.y1 + s.y2) / 2
    var nx = -(s.y2 - s.y1); var ny = s.x2 - s.x1
    val l = hypot(nx, ny); nx /= l; ny /= l
    if ((towardX - mx) * nx + (towardY - my) * ny < 0) { nx = -nx; ny = -ny }
    return Joint(mx, my, nx, ny, hypot(s.x2 - s.x1, s.y2 - s.y1) / 2)
}

/** Round cap past a cut on the +u side of [j], filled by smearing the piece's colours at the cut along u. */
private fun capInto(p: Piece, j: Joint): Piece {
    val r = j.radius
    val x0 = min(p.ox, (j.x - r).toInt() - 2); val y0 = min(p.oy, (j.y - r).toInt() - 2)
    val x1 = max(p.ox + p.w, (j.x + r).toInt() + 3); val y1 = max(p.oy + p.h, (j.y + r).toInt() + 3)
    val out = Img(x1 - x0, y1 - y0)
    for (y in 0 until p.h) for (x in 0 until p.w) out[x + p.ox - x0, y + p.oy - y0] = p.img[x, y]
    for (y in y0 until y1) for (x in x0 until x1) {
        val fx = x + .5f; val fy = y + .5f
        val t = (fx - j.x) * j.ux + (fy - j.y) * j.uy
        if (t <= 0f || hypot(fx - j.x, fy - j.y) > r) continue
        if (a(out[x - x0, y - y0]) > 200) continue
        // Colour of the piece just before the cut on this line.
        val sx = (fx - (t + 2f) * j.ux).roundToInt(); val sy = (fy - (t + 2f) * j.uy).roundToInt()
        val c = p.colourAt(sx, sy)
        if (a(c) > 127) out[x - x0, y - y0] = c or (0xFF shl 24)
    }
    return Piece(out, x0, y0).trimmed()
}

/** Copies each column's top colour [px] pixels upward (the hidden top of the shorts under the shirt). */
private fun extendUp(p: Piece, px: Int): Piece {
    val out = Img(p.w, p.h + px)
    for (y in 0 until p.h) for (x in 0 until p.w) out[x, y + px] = p.img[x, y]
    for (x in 0 until p.w) {
        val top = (0 until p.h).firstOrNull { a(p.img[x, it]) > 200 } ?: continue
        val src = p.img[x, min(p.h - 1, top + 3)]
        for (k in 1..px) out[x, top + px - k] = src or (0xFF shl 24)
    }
    return Piece(out, p.ox, p.oy - px)
}

/** Fills pixels of [hole] that touch [p] (up to [depth] px in) with the nearest colour of [p]: behind a removed arm. */
private fun fillBehind(p: Piece, hole: BooleanArray, w: Int, depth: Int): Piece {
    val x0 = p.ox - depth; val y0 = p.oy - depth
    val out = Img(p.w + 2 * depth, p.h + 2 * depth)
    for (y in 0 until p.h) for (x in 0 until p.w) out[x + depth, y + depth] = p.img[x, y]
    repeat(depth) {
        val next = out.px.copyOf()
        for (y in 0 until out.h) for (x in 0 until out.w) {
            if (a(out[x, y]) > 0) continue
            val gx = x + x0; val gy = y + y0
            if (gx < 0 || gy < 0 || gx >= w || !hole.indices.contains(gy * w + gx) || !hole[gy * w + gx]) continue
            for ((dx, dy) in NEIGH4) {
                val nx = x + dx; val ny = y + dy
                if (out.inside(nx, ny) && a(out[nx, ny]) > 200) { next[y * out.w + x] = out[nx, ny]; break }
            }
        }
        System.arraycopy(next, 0, out.px, 0, next.size)
    }
    return Piece(out, x0, y0).trimmed()
}

/**
 * The hair as its own piece (dark pixels connected to [seed], plus their anti-aliased edge). The head keeps its
 * own copy underneath, so the hair piece can sway a little without opening a hole.
 */
private fun hairOf(head: Piece, seed: Pt, dark: (Int) -> Boolean, edge: (Int) -> Boolean): Piece {
    val w = head.w; val h = head.h
    val mask = BooleanArray(w * h)
    val sx = seed.x.roundToInt() - head.ox; val sy = seed.y.roundToInt() - head.oy
    require(head.img.inside(sx, sy) && dark(head.img[sx, sy])) { "hair seed is not on hair" }
    val st = ArrayDeque<Int>(); st += sy * w + sx; mask[sy * w + sx] = true
    while (st.isNotEmpty()) {
        val q = st.removeLast(); val x = q % w; val y = q / w
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx; val ny = y + dy
            if (nx !in 0 until w || ny !in 0 until h) continue
            val n = ny * w + nx
            if (!mask[n] && dark(head.img[nx, ny])) { mask[n] = true; st += n }
        }
    }
    val out = Img(w, h)
    for (y in 0 until h) for (x in 0 until w) {
        val c = head.img[x, y]
        if (a(c) == 0) continue
        if (mask[y * w + x]) { out[x, y] = c; continue }
        // Edge pixels between hair and skin/background: keep them when a hair pixel touches them.
        if (edge(c)) {
            var touch = false
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until w && ny in 0 until h && mask[ny * w + nx]) touch = true
            }
            if (touch) out[x, y] = c
        }
    }
    return Piece(out, head.ox, head.oy).trimmed()
}

/**
 * Erases painted features (non-skin blobs not touching the area border) and returns their exact box plus the
 * feature itself cut out before erasing (it becomes the default sprite, so the rest pose is exactly ref.png).
 */
private fun eraseFeature(p: Piece, area: Box, isSkin: (Int) -> Boolean, contrast: Int): Pair<Box, Piece> {
    val skinR = ArrayList<Int>(); val skinG = ArrayList<Int>(); val skinB = ArrayList<Int>()
    for (y in area.y0 until area.y1) for (x in area.x0 until area.x1) {
        val c = p.colourAt(x, y)
        if (a(c) > 200 && isSkin(c)) { skinR += r(c); skinG += g(c); skinB += b(c) }
    }
    skinR.sort(); skinG.sort(); skinB.sort()
    val skin = argb(255, skinR[skinR.size / 2], skinG[skinG.size / 2], skinB[skinB.size / 2])
    val w = area.x1 - area.x0; val h = area.y1 - area.y0
    val non = BooleanArray(w * h) { i ->
        val c = p.colourAt(area.x0 + i % w, area.y0 + i / w)
        a(c) > 100 && abs(r(c) - r(skin)) + abs(g(c) - g(skin)) + abs(b(c) - b(skin)) >= contrast
    }
    val seen = BooleanArray(w * h)
    var bx0 = Int.MAX_VALUE; var by0 = Int.MAX_VALUE; var bx1 = -1; var by1 = -1
    for (s in non.indices) {
        if (!non[s] || seen[s]) continue
        val comp = ArrayList<Int>()
        val st = ArrayDeque<Int>(); st += s; seen[s] = true
        var border = false
        while (st.isNotEmpty()) {
            val q = st.removeLast(); comp += q
            val x = q % w; val y = q / w
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) border = true
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) continue
                val n = ny * w + nx
                if (non[n] && !seen[n]) { seen[n] = true; st += n }
            }
        }
        if (border || comp.size < 8) continue
        for (q in comp) {
            val x = q % w + area.x0; val y = q / w + area.y0
            bx0 = min(bx0, x); by0 = min(by0, y); bx1 = max(bx1, x); by1 = max(by1, y)
        }
    }
    require(bx1 >= 0) { "nothing to erase in ${area.x0},${area.y0}" }
    val feature = Img(bx1 - bx0 + 5, by1 - by0 + 5)
    for (y in 0 until feature.h) for (x in 0 until feature.w) {
        val c = p.colourAt(bx0 - 2 + x, by0 - 2 + y)
        // Keep the feature's own pixels; skin around it becomes transparent, its anti-aliased ring half so.
        val d = abs(r(c) - r(skin)) + abs(g(c) - g(skin)) + abs(b(c) - b(skin))
        val al = ((d - contrast * 25f / 110) / (contrast * 85f / 110)).coerceIn(0f, 1f) * a(c)
        feature[x, y] = (c and 0xFFFFFF) or (al.roundToInt() shl 24)
    }
    // Erase the box plus a 2 px ring (anti-aliased edge) with flat skin.
    for (y in by0 - 2..by1 + 2) for (x in bx0 - 2..bx1 + 2) {
        val lx = x - p.ox; val ly = y - p.oy
        if (!p.img.inside(lx, ly) || a(p.img[lx, ly]) == 0) continue
        if (isSkin(p.img[lx, ly]) && abs(r(p.img[lx, ly]) - r(skin)) < 12) continue
        p.img[lx, ly] = (skin and 0xFFFFFF) or (a(p.img[lx, ly]) shl 24)
    }
    return Box(bx0, by0, bx1 + 1, by1 + 1) to Piece(feature, bx0 - 2, by0 - 2)
}

internal fun cutRef(dir: File, outDir: File, previewDir: File) = cutWith(dir, outDir, previewDir, KaiRefSpec.spec())

internal fun cutWith(dir: File, outDir: File, previewDir: File, S: RefSpec) {
    val col = S.colours
    val ref = Img.load(File(dir, "ref.png"))
    val img = defringe(keyMagenta(ref))
    val cv = Canvas(img.w, img.h)
    for (s in listOf(S.neck, S.shoulderNear, S.shoulderFar, S.hipNear, S.hipFar, S.seam, S.shoes)) cv.block(s)

    // Order matters: each region takes its pixels away from the ones after it.
    val masks = ArrayList<BooleanArray>()
    fun reg(seed: Pt, clip: Box? = null, within: Capsule? = null, allow: (Int) -> Boolean = { true }): BooleanArray {
        val m = runCatching { region(img, cv, seed, clip, within, allow) }.getOrElse {
            debugMasks(previewDir, img, cv, masks); throw it
        }
        masks += m
        return m
    }
    val headM = reg(S.seedHead, clip = Box(0, 0, img.w, S.neck.y1.toInt() + 1))
    fun armCapsule(sh: Seg, el: Pt, wr: Pt, radii: FloatArray): Capsule {
        val s = Pt((sh.x1 + sh.x2) / 2, (sh.y1 + sh.y2) / 2)
        val hand = Pt(wr.x + (wr.x - el.x) * .9f, wr.y + (wr.y - el.y) * .9f)
        return Capsule(listOf(s, el, wr, hand), radii)
    }
    val armNearM = reg(S.seedArmNear, within = armCapsule(S.shoulderNear, S.elbowNear, S.wristNear, S.armNearRadii), allow = col.arm)
    val armFarM = reg(S.seedArmFar, within = armCapsule(S.shoulderFar, S.elbowFar, S.wristFar, S.armFarRadii), allow = col.arm)
    val legNearM = reg(S.seedLegNear, clip = Box(0, S.legTop, img.w, img.h), allow = col.leg)
    val legFarM = reg(S.seedLegFar, clip = Box(0, S.legTop, img.w, img.h), allow = col.leg)
    val thighNearM = reg(S.seedThighNear, clip = Box(0, S.hipNear.y1.toInt(), img.w, img.h))
    val thighFarM = reg(S.seedThighFar, clip = Box(0, S.hipFar.y1.toInt(), img.w, img.h))
    val pelvisM = reg(S.seedPelvis, allow = col.pelvis)
    val torsoM = reg(S.seedTorso)
    // Islands no seed reached (finger lines, bits boxed in by an arm and a cut line) join the region they touch most.
    var islands = 0
    val seen = BooleanArray(img.px.size)
    for (s in img.px.indices) {
        if (seen[s] || a(img.px[s]) == 0 || cv.taken[s] || cv.blocked[s]) continue
        val comp = ArrayList<Int>()
        val votes = IntArray(masks.size)
        val st = ArrayDeque<Int>(); st += s; seen[s] = true
        while (st.isNotEmpty()) {
            val q = st.removeLast(); comp += q
            val x = q % img.w; val y = q / img.w
            for ((dx, dy) in NEIGH4) {
                val nx = x + dx; val ny = y + dy
                if (!img.inside(nx, ny)) continue
                val n = ny * img.w + nx
                if (a(img.px[n]) == 0 || cv.blocked[n]) continue
                if (cv.taken[n]) { for (k in masks.indices) if (masks[k][n]) votes[k]++ } else if (!seen[n]) { seen[n] = true; st += n }
            }
        }
        val best = votes.indices.maxBy { votes[it] }
        val target = if (votes[best] > 0) masks[best] else torsoM
        for (q in comp) { target[q] = true; cv.taken[q] = true }
        islands += comp.size
    }
    // Pixels on a cut line go to every part next to them, so no seam shows in the rest pose.
    for (p in img.px.indices) {
        if (!cv.blocked[p] || a(img.px[p]) == 0) continue
        val x = p % img.w; val y = p / img.w
        for (m in masks) {
            var near = false
            for (dy in -2..2) for (dx in -2..2) {
                val nx = x + dx; val ny = y + dy
                if (img.inside(nx, ny) && !cv.blocked[ny * img.w + nx] && m[ny * img.w + nx]) near = true
            }
            if (near) m[p] = true
        }
    }
    debugMasks(previewDir, img, cv, masks)
    println("cutref: $islands island pixels joined a neighbouring region")

    val bones = ArrayList<Bone>()
    val parts = HashMap<String, Part>()
    fun mid(s: Seg) = Pt((s.x1 + s.x2) / 2, (s.y1 + s.y2) / 2)

    bones += Bone("root", null, S.root.x, S.root.y)
    bones += Bone("torso", "root", S.waist.x, S.waist.y)
    bones += Bone("head", "torso", mid(S.neck).x, mid(S.neck).y)

    // Torso: fill in a little behind the removed arms, cap the neck (all hidden in the rest pose).
    var torso = pieceOf(img, torsoM)
    torso = fillBehind(torso, armNearM, img.w, 8)
    torso = fillBehind(torso, armFarM, img.w, 8)
    torso = capInto(torso, jointOn(S.neck, S.seedHead.x, S.seedHead.y))
    parts["torso"] = Part("torso", "torso", torso)
    parts["pelvis"] = Part("pelvis", "root", extendUp(pieceOf(img, pelvisM), 18))

    for ((side, data) in listOf(
        "near" to listOf(armNearM, S.shoulderNear, S.elbowNear, S.wristNear),
        "far" to listOf(armFarM, S.shoulderFar, S.elbowFar, S.wristFar),
    )) {
        val mask = data[0] as BooleanArray; val sh = data[1] as Seg; val el = data[2] as Pt; val wr = data[3] as Pt
        var arm = pieceOf(img, mask)
        arm = capInto(arm, jointOn(sh, S.seedTorso.x, S.seedTorso.y))
        val shoulder = mid(sh)
        val elbow = jointAcross(arm, el.x, el.y, el.x - shoulder.x, el.y - shoulder.y)
        val (upper, rest) = cutAt(arm, elbow)
        val wrist = jointAcross(rest, wr.x, wr.y, wr.x - el.x, wr.y - el.y)
        val (fore, hand) = cutAt(rest, wrist)
        bones += Bone("shoulder_$side", "torso", shoulder.x, shoulder.y + 8f)
        bones += Bone("elbow_$side", "shoulder_$side", elbow.x, elbow.y)
        bones += Bone("wrist_$side", "elbow_$side", wrist.x, wrist.y)
        parts["arm_upper_$side"] = Part("arm_upper_$side", "shoulder_$side", upper)
        parts["arm_fore_$side"] = Part("arm_fore_$side", "elbow_$side", fore)
        parts["hand_$side"] = Part("hand_$side", "wrist_$side", hand)
    }

    for ((side, data) in listOf(
        "near" to listOf(thighNearM, legNearM, S.hipNear, S.kneeNear, S.ankleNear),
        "far" to listOf(thighFarM, legFarM, S.hipFar, S.kneeFar, S.ankleFar),
    )) {
        val hip = data[2] as Seg; val kn = data[3] as Pt; val an = data[4] as Pt
        val thigh = capInto(pieceOf(img, data[0] as BooleanArray), jointOn(hip, S.seedPelvis.x, S.seedPelvis.y))
        var leg = pieceOf(img, data[1] as BooleanArray)
        // Shin top hides under the shorts cuff: cap it upward.
        val knee = jointAcross(leg, kn.x, leg.oy + 4f, 0f, 1f)
        leg = capInto(leg, Joint(knee.x, leg.oy + 2f, 0f, -1f, knee.radius))
        val ankle = jointAcross(leg, an.x, an.y, an.x - kn.x, an.y - kn.y)
        val (shin, shoe) = cutAt(leg, ankle, capAfter = false)
        bones += Bone("hip_$side", "root", mid(hip).x, mid(hip).y)
        bones += Bone("knee_$side", "hip_$side", knee.x, kn.y)
        bones += Bone("ankle_$side", "knee_$side", ankle.x, ankle.y)
        parts["thigh_$side"] = Part("thigh_$side", "hip_$side", thigh)
        parts["shin_$side"] = Part("shin_$side", "knee_$side", shin)
        parts["shoe_$side"] = Part("shoe_$side", "ankle_$side", shoe)
    }

    // Head (with hair and nose): erase the painted eyes, brows, mouth; what was found gives the face boxes.
    val head = pieceOf(img, headM)
    val found = mapOf(
        "eye_near_open" to eraseFeature(head, S.eyeNearArea, col.skin, S.featureContrast), "eye_far_open" to eraseFeature(head, S.eyeFarArea, col.skin, S.featureContrast),
        "brow_near" to eraseFeature(head, S.browNearArea, col.skin, S.featureContrast), "brow_far" to eraseFeature(head, S.browFarArea, col.skin, S.featureContrast),
        "mouth_smile" to eraseFeature(head, S.mouthArea, col.skin, S.featureContrast),
    )
    for ((k, f) in found) println("  $k found at ${f.first.x0},${f.first.y0}-${f.first.x1},${f.first.y1}")
    parts["head"] = Part("head", "head", head)
    parts["hair"] = Part("hair", "hair", hairOf(head, S.seedHair, col.hair, col.hairEdge))
    bones += Bone("hair", "head", S.hairPivot.x, S.hairPivot.y)
    addFaceParts(dir, parts, found.mapValues { it.value.first })
    // Default sprites are ref.png's own features; the sheets only add the other expressions.
    for ((k, f) in found) parts[k] = Part(k, "head", f.second)

    writeOut(outDir, bones, parts)
    renderPreviews(previewDir, ref, bones, parts)
}

/** regions.png: every region in its own colour, cut lines in black, unassigned pixels as they are. */
private fun debugMasks(dir: File, img: Img, cv: Canvas, masks: List<BooleanArray>) {
    val palette = intArrayOf(0xE6194B, 0x3CB44B, 0x4363D8, 0xF58231, 0x911EB4, 0x46F0F0, 0xF032E6, 0xBCF60C, 0x008080, 0x9A6324, 0x800000)
    val out = Img(img.w, img.h)
    for (p in img.px.indices) {
        val c = img.px[p]
        out.px[p] = if (a(c) == 0) 0xFFFFFFFF.toInt() else c or (0xFF shl 24)
        val k = masks.indexOfFirst { it[p] }
        if (k >= 0) {
            val col = palette[k % palette.size]
            out.px[p] = argb(255, (r(c) + 2 * r(col)) / 3, (g(c) + 2 * g(col)) / 3, (b(c) + 2 * b(col)) / 3)
        }
        if (cv.blocked[p]) out.px[p] = 0xFF000000.toInt()
    }
    out.save(File(dir, "regions.png"))
}
