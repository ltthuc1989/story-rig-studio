package com.example.storyrig.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.abs

/** Per-frame draw counters shown in the HUD and written to REPORT.md. */
class RenderStats {
    var draws = 0
    var strokes = 0
    var softPoints = 0
    var saveLayers = 0
    var particles = 0
    var layersDrawn = 0
    var images = 0
    /** Mesh vertices deformed this frame ([SoftMesh]). */
    var meshVerts = 0

    fun reset() {
        draws = 0; strokes = 0; softPoints = 0; saveLayers = 0; particles = 0; layersDrawn = 0; images = 0; meshVerts = 0
    }

    // Values of the last completed frame, for the HUD (the live counters are mid-frame most of the time).
    var lastDraws = 0; var lastStrokes = 0; var lastSoftPoints = 0
    var lastSaveLayers = 0; var lastParticles = 0; var lastLayersDrawn = 0; var lastImages = 0; var lastMeshVerts = 0

    fun commit() {
        lastDraws = draws; lastStrokes = strokes; lastSoftPoints = softPoints
        lastSaveLayers = saveLayers; lastParticles = particles; lastLayersDrawn = layersDrawn; lastImages = images
        lastMeshVerts = meshVerts
    }
}

/**
 * One drawable SVG element. Geometry is fixed at load time; only alpha may change per draw.
 * kind: PATH uses [path]; ELLIPSE/CIRCLE/RECT use a..d (+ r for rounded rect corners).
 */
class Shape(
    val kind: Int,
    val path: Path?,
    val a: Float, val b: Float, val c: Float, val d: Float, val r: Float,
    val paint: Paint,
    val baseAlpha: Float,
) {
    val isStroke = paint.style == PaintingStyle.Stroke
    private var lastAlpha = -1f

    fun draw(canvas: Canvas, alphaMul: Float, stats: RenderStats) {
        val al = baseAlpha * alphaMul
        if (al <= 0.003f) return
        if (lastAlpha != al) {
            paint.alpha = al
            lastAlpha = al
        }
        when (kind) {
            PATH -> canvas.drawPath(path!!, paint)
            ELLIPSE -> canvas.drawOval(a - c, b - d, a + c, b + d, paint)
            CIRCLE -> canvas.drawCircle(Offset(a, b), c, paint)
            RECT -> if (r > 0f) canvas.drawRoundRect(a, b, a + c, b + d, r, r, paint)
            else canvas.drawRect(a, b, a + c, b + d, paint)
        }
        stats.draws++
        if (isStroke) stats.strokes++
    }

    companion object {
        const val PATH = 0
        const val ELLIPSE = 1
        const val CIRCLE = 2
        const val RECT = 3
    }
}

fun Array<Shape>.drawAll(canvas: Canvas, alphaMul: Float, stats: RenderStats) {
    for (i in indices) this[i].draw(canvas, alphaMul, stats)
}

/** Parses each distinct SVG `d` string once. Returned paths are shared and must not be mutated. */
class PathCache {
    private val map = HashMap<String, Path>()
    private val parser = PathParser()
    var parseCount = 0
        private set

    fun get(d: String): Path = map.getOrPut(d) {
        parseCount++
        parser.clear()
        parser.parsePathString(d).toPath()
    }
}

/**
 * Builds shape arrays from SVG-like calls. A translate/scale/rotate "group transform" can be set
 * and is baked into the geometry at load, so the hot path never sees it.
 */
class ArtBuilder(private val cache: PathCache, private val antiAlias: Boolean) {
    private val out = ArrayList<Shape>()
    private var tx = 0f
    private var ty = 0f
    private var sc = 1f
    private var rotDeg = 0f
    private var px = 0f
    private var py = 0f

    /** Group transform, SVG order: translate(tx ty) rotate(rot px py) scale(s). */
    fun group(tx: Float = 0f, ty: Float = 0f, s: Float = 1f, rotDeg: Float = 0f, px: Float = 0f, py: Float = 0f, block: ArtBuilder.() -> Unit) {
        val o0 = this.tx; val o1 = this.ty; val o2 = this.sc; val o3 = this.rotDeg; val o4 = this.px; val o5 = this.py
        this.tx = tx; this.ty = ty; this.sc = s; this.rotDeg = rotDeg; this.px = px; this.py = py
        block()
        this.tx = o0; this.ty = o1; this.sc = o2; this.rotDeg = o3; this.px = o4; this.py = o5
    }

    private val identity get() = tx == 0f && ty == 0f && sc == 1f && rotDeg == 0f

    private fun bakeMatrix(): Matrix {
        val m = Matrix()
        m.translate(tx, ty)
        if (rotDeg != 0f) {
            m.translate(px, py)
            m.rotateZ(rotDeg)
            m.translate(-px, -py)
        }
        m.scale(sc, sc)
        return m
    }

    private fun paint(color: Color, stroke: Float, cap: StrokeCap, shader: Shader?): Paint = Paint().apply {
        isAntiAlias = antiAlias
        this.color = color
        if (shader != null) this.shader = shader
        if (stroke > 0f) {
            style = PaintingStyle.Stroke
            strokeWidth = stroke * abs(sc)
            strokeCap = cap
            strokeJoin = if (cap == StrokeCap.Round) StrokeJoin.Round else StrokeJoin.Miter
        }
    }

    private fun addPathShape(src: Path, color: Color, alpha: Float, stroke: Float, cap: StrokeCap, shader: Shader?) {
        val p = if (identity) src else Path().apply { addPath(src); transform(bakeMatrix()) }
        out += Shape(Shape.PATH, p, 0f, 0f, 0f, 0f, 0f, paint(color, stroke, cap, shader), alpha)
    }

    fun path(d: String, color: Color, alpha: Float = 1f, shader: Shader? = null) =
        addPathShape(cache.get(d), color, alpha, 0f, StrokeCap.Butt, shader)

    fun stroke(d: String, color: Color, width: Float, alpha: Float = 1f, round: Boolean = true) =
        addPathShape(cache.get(d), color, alpha, width, if (round) StrokeCap.Round else StrokeCap.Butt, null)

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, alpha: Float = 1f, stroke: Float = 0f) {
        if (rotDeg != 0f) {
            val p = Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
            addPathShape(p, color, alpha, stroke, StrokeCap.Butt, null)
            return
        }
        out += Shape(Shape.ELLIPSE, null, tx + cx * sc, ty + cy * sc, rx * abs(sc), ry * abs(sc), 0f, paint(color, stroke, StrokeCap.Butt, null), alpha)
    }

    fun circle(cx: Float, cy: Float, r: Float, color: Color, alpha: Float = 1f, shader: Shader? = null) {
        if (rotDeg != 0f) return ellipse(cx, cy, r, r, color, alpha)
        out += Shape(Shape.CIRCLE, null, tx + cx * sc, ty + cy * sc, r * abs(sc), 0f, 0f, paint(color, 0f, StrokeCap.Butt, shader), alpha)
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float = 1f, rx: Float = 0f, shader: Shader? = null) {
        require(rotDeg == 0f)
        out += Shape(Shape.RECT, null, tx + x * sc, ty + y * sc, w * sc, h * sc, rx * sc, paint(color, 0f, StrokeCap.Butt, shader), alpha)
    }

    fun build(): Array<Shape> {
        val a = out.toTypedArray()
        out.clear()
        return a
    }
}

fun hex(v: Long): Color = Color(0xFF000000L or v)
