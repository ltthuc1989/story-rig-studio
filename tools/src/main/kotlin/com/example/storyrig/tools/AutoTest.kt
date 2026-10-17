package com.example.storyrig.tools

import java.awt.Color
import java.io.File
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * `autotest`: checks that `cutauto` follows shape, not Kai's colours or size, without asking Gemini for new images.
 * Writes synthetic variants of ref.png (other colours, smaller and moved, stretched), cuts each with cutauto and
 * compares its points with KaiRefSpec's moved by the same transform.
 */

/** A variant: a colour change and an affine map (x' = cx + (x - cx) * kx + dx, same for y) of the character. */
private class Variant(val name: String, val kx: Float = 1f, val ky: Float = 1f, val dx: Float = 0f, val dy: Float = 0f, val recolour: (Int) -> Int = { it })

private fun hsb(c: Int) = Color.RGBtoHSB(r(c), g(c), b(c), null)
private fun fromHsb(h: Float, s: Float, v: Float, al: Int) = (Color.HSBtoRGB(((h % 1f) + 1f) % 1f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)) and 0xFFFFFF) or (al shl 24)

private val variants = listOf(
    // Every hue turned 150 degrees: red shirt -> green, blue shorts -> red, tan skin -> cyan (no magenta results).
    Variant("hue150") { c -> val (h, s, v) = hsb(c); fromHsb(h + 150f / 360f, s, v, a(c)) },
    // Shirt and shoes yellow-green, shorts dark green, skin (with cheeks, ears, nose) a darker brown: less contrast
    // with the eyes, brows and mouth.
    Variant("recolour") { c ->
        val (h, s, v) = hsb(c)
        val deg = h * 360
        when {
            deg in 190f..250f && s > .3f -> fromHsb(110f / 360, s * .8f, v * .7f, a(c))
            (deg < 12f || deg > 348f) && s > .6f && v > .8f -> fromHsb(70f / 360, s, v, a(c))
            (deg < 45f || deg > 340f) && s in .12f..0.75f && v > .55f -> fromHsb(h, s, v * .7f, a(c))
            else -> c
        }
    },
    Variant("small", kx = .85f, ky = .85f, dx = 60f, dy = 40f),
    Variant("wide", kx = 1.12f, dx = -20f),
)

/** The variant's image: key the background, change the character, put it back on the same background colour. */
private fun render(src: Img, v: Variant): Img {
    val bg = cornerColour(src)
    val keyed = keyMagenta(src)
    val cx = src.w / 2f; val cy = src.h / 2f
    val out = Img(src.w, src.h)
    for (y in 0 until src.h) for (x in 0 until src.w) {
        // Inverse map, premultiplied bilinear sample.
        val fx = cx + (x + .5f - v.dx - cx) / v.kx - .5f; val fy = cy + (y + .5f - v.dy - cy) / v.ky - .5f
        val ix = floor(fx).toInt(); val iy = floor(fy).toInt()
        val tx = fx - ix; val ty = fy - iy
        var sa = 0f; var sr = 0f; var sg = 0f; var sb = 0f
        for (k in 0 until 4) {
            val px = ix + (k and 1); val py = iy + (k shr 1)
            if (!keyed.inside(px, py)) continue
            val wgt = (if (k and 1 == 1) tx else 1 - tx) * (if (k shr 1 == 1) ty else 1 - ty)
            val c = keyed[px, py]
            val al = a(c) / 255f * wgt
            sa += al; sr += r(c) * al; sg += g(c) * al; sb += b(c) * al
        }
        val c = if (sa <= .001f) 0 else v.recolour(argb((sa * 255).roundToInt(), (sr / sa).roundToInt(), (sg / sa).roundToInt(), (sb / sa).roundToInt()))
        val al = a(c) / 255f
        out[x, y] = argb(255, (r(c) * al + r(bg) * (1 - al)).roundToInt(), (g(c) * al + g(bg) * (1 - al)).roundToInt(), (b(c) * al + b(bg) * (1 - al)).roundToInt())
    }
    return out
}

/** KaiRefSpec moved by the variant's transform (points only; for comparing). */
private fun moved(sp: RefSpec, v: Variant, w: Int, h: Int): RefSpec {
    val cx = w / 2f; val cy = h / 2f
    fun p(q: Pt) = Pt(cx + (q.x - cx) * v.kx + v.dx, cy + (q.y - cy) * v.ky + v.dy)
    fun s(q: Seg) = p(Pt(q.x1, q.y1)).let { a -> p(Pt(q.x2, q.y2)).let { b -> Seg(a.x, a.y, b.x, b.y) } }
    fun bx(q: Box) = p(Pt(q.x0.toFloat(), q.y0.toFloat())).let { a -> p(Pt(q.x1.toFloat(), q.y1.toFloat())).let { b -> Box(a.x.roundToInt(), a.y.roundToInt(), b.x.roundToInt(), b.y.roundToInt()) } }
    val k = (v.kx + v.ky) / 2
    return RefSpec(
        s(sp.neck), s(sp.shoulderNear), s(sp.shoulderFar), s(sp.hipNear), s(sp.hipFar), s(sp.seam), s(sp.shoes),
        p(sp.elbowNear), p(sp.wristNear), p(sp.elbowFar), p(sp.wristFar), p(sp.kneeNear), p(sp.kneeFar), p(sp.ankleNear), p(sp.ankleFar),
        FloatArray(3) { sp.armNearRadii[it] * k }, FloatArray(3) { sp.armFarRadii[it] * k }, p(Pt(0f, sp.legTop.toFloat())).y.roundToInt(),
        p(sp.root), p(sp.waist), p(sp.seedHead), p(sp.seedHair), p(sp.hairPivot), p(sp.seedTorso), p(sp.seedPelvis),
        p(sp.seedArmNear), p(sp.seedArmFar), p(sp.seedThighNear), p(sp.seedThighFar), p(sp.seedLegNear), p(sp.seedLegFar),
        bx(sp.eyeNearArea), bx(sp.eyeFarArea), bx(sp.browNearArea), bx(sp.browFarArea), bx(sp.mouthArea), sp.colours,
    )
}

internal fun autoTest(dir: File, outRoot: File) {
    val src = Img.load(File(dir, "ref.png"))
    for (v in variants) {
        println("=== variant ${v.name}")
        val vDir = File(outRoot, v.name)
        val gem = File(vDir, "gemini").apply { mkdirs() }
        render(src, v).save(File(gem, "ref.png"))
        for (sheet in listOf("face.png", "face_extra.png")) File(dir, sheet).copyTo(File(gem, sheet), overwrite = true)
        val img = defringe(keyMagenta(Img.load(File(gem, "ref.png"))))
        val res = autoSpec(img)
        val expected = moved(KaiRefSpec.spec(), v, src.w, src.h)
        compareWithKai(res.spec, expected, "manual moved")
        val prev = File(vDir, "preview")
        drawPoints(File(prev, "points.png"), img, res, expected)
        cutWith(gem, File(vDir, "out"), prev, res.spec)
    }
}
