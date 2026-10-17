package com.example.storyrig.engine

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.PathSegment
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a stroked open path into the fill of its outline (SPIKE.md §7 plan B item 2, `--es outline fill`), so the
 * GPU fills a few curves instead of Skia stroking the path on the CPU every frame.
 *
 * Input is the [SoftPath] layout: verbs M/L/Q/C (no Z), points as x,y pairs, shared end points not repeated.
 * Each subpath becomes one closed contour: the left offset forward, end cap, the right offset backward, start cap.
 * Curves are offset by moving their control points along the control-polygon normals (Tiller-Hanson); joins between
 * segments are a quad through the miter point (between a round and a miter join); caps are butt or round
 * (two cubic quarter circles). The inner side of a sharp bend overlaps itself, which the non-zero fill covers.
 *
 * Sized once for at most [maxPoints] points / [maxSegments] segments; [build] does not allocate.
 */
class StrokeOutline(maxPoints: Int, maxSegments: Int) {
    private val lp = FloatArray(maxPoints * 2) // left offset of each point (end normal of its segment)
    private val rp = FloatArray(maxPoints * 2)
    private val sl = FloatArray(maxSegments * 2) // left / right offset of each segment's start point
    private val sr = FloatArray(maxSegments * 2)
    private val ns = FloatArray(maxSegments * 2) // unit left normal at segment start / end
    private val ne = FloatArray(maxSegments * 2)
    private val segVerb = ByteArray(maxSegments)
    private val segP0 = IntArray(maxSegments) // point index of the segment's start point
    private val segP1 = IntArray(maxSegments) // point index of the segment's first own point
    private val nrm = FloatArray(6) // edge normals inside one segment

    /** Writes the outline of [verbs]/[pts] stroked with [width] into [out] (reset first). */
    fun build(verbs: ByteArray, verbCount: Int, pts: FloatArray, width: Float, round: Boolean, out: Path) {
        out.reset()
        val h = width / 2f
        var p = 0
        var seg = 0
        var subStart = 0
        var cur = -1 // point index of the current point
        for (vi in 0 until verbCount) {
            when (verbs[vi]) {
                SoftPath.M -> {
                    if (seg > subStart) emit(subStart, seg, pts, h, round, out)
                    subStart = seg
                    cur = p; p += 1
                }
                SoftPath.L, SoftPath.Q, SoftPath.C -> {
                    val v = verbs[vi]
                    val n = if (v == SoftPath.L) 1 else if (v == SoftPath.Q) 2 else 3
                    segVerb[seg] = v; segP0[seg] = cur; segP1[seg] = p
                    offsetSegment(seg, n, pts, h)
                    cur = p + n - 1; p += n
                    seg++
                }
            }
        }
        if (seg > subStart) emit(subStart, seg, pts, h, round, out)
    }

    private fun offsetSegment(seg: Int, n: Int, pts: FloatArray, h: Float) {
        val p0 = segP0[seg]; val p1 = segP1[seg]
        // edge normals of the control polygon; a zero-length edge borrows its neighbour's normal
        var firstGood = -1
        for (j in 0 until n) {
            val a = if (j == 0) p0 else p1 + j - 1
            val b = p1 + j
            val dx = pts[b * 2] - pts[a * 2]
            val dy = pts[b * 2 + 1] - pts[a * 2 + 1]
            val len = hypot(dx, dy)
            if (len > 1e-4f) {
                nrm[j * 2] = -dy / len; nrm[j * 2 + 1] = dx / len
                if (firstGood < 0) firstGood = j
            } else {
                nrm[j * 2] = Float.NaN
            }
        }
        if (firstGood < 0) {
            // degenerate segment: keep the previous direction (or any, for a lone dot)
            val px = if (seg > 0) ne[(seg - 1) * 2] else 0f
            val py = if (seg > 0) ne[(seg - 1) * 2 + 1] else 1f
            for (j in 0 until n) { nrm[j * 2] = px; nrm[j * 2 + 1] = py }
        } else {
            for (j in 0 until n) if (nrm[j * 2].isNaN()) {
                val k = if (j < firstGood) firstGood else j - 1
                nrm[j * 2] = nrm[k * 2]; nrm[j * 2 + 1] = nrm[k * 2 + 1]
            }
        }
        ns[seg * 2] = nrm[0]; ns[seg * 2 + 1] = nrm[1]
        ne[seg * 2] = nrm[(n - 1) * 2]; ne[seg * 2 + 1] = nrm[(n - 1) * 2 + 1]
        val x0 = pts[p0 * 2]; val y0 = pts[p0 * 2 + 1]
        sl[seg * 2] = x0 + h * nrm[0]; sl[seg * 2 + 1] = y0 + h * nrm[1]
        sr[seg * 2] = x0 - h * nrm[0]; sr[seg * 2 + 1] = y0 - h * nrm[1]
        for (j in 0 until n) {
            val i = p1 + j
            val x = pts[i * 2]; val y = pts[i * 2 + 1]
            val ox: Float; val oy: Float
            if (j == n - 1) {
                ox = nrm[j * 2]; oy = nrm[j * 2 + 1]
            } else {
                // interior control point: where the two offset edges meet
                val ax = nrm[j * 2]; val ay = nrm[j * 2 + 1]
                val bx = nrm[(j + 1) * 2]; val by = nrm[(j + 1) * 2 + 1]
                val d = max(MITER_MIN, 1f + ax * bx + ay * by)
                ox = (ax + bx) / d; oy = (ay + by) / d
            }
            lp[i * 2] = x + h * ox; lp[i * 2 + 1] = y + h * oy
            rp[i * 2] = x - h * ox; rp[i * 2 + 1] = y - h * oy
        }
    }

    private fun emit(s0: Int, s1: Int, pts: FloatArray, h: Float, round: Boolean, out: Path) {
        out.moveTo(sl[s0 * 2], sl[s0 * 2 + 1])
        // left side, forward
        for (k in s0 until s1) {
            if (k > s0) join(out, pts, segP0[k], ne, (k - 1) * 2, ns, k * 2, h, sl[k * 2], sl[k * 2 + 1])
            val q = segP1[k]
            when (segVerb[k]) {
                SoftPath.L -> out.lineTo(lp[q * 2], lp[q * 2 + 1])
                SoftPath.Q -> out.quadraticTo(lp[q * 2], lp[q * 2 + 1], lp[q * 2 + 2], lp[q * 2 + 3])
                else -> out.cubicTo(lp[q * 2], lp[q * 2 + 1], lp[q * 2 + 2], lp[q * 2 + 3], lp[q * 2 + 4], lp[q * 2 + 5])
            }
        }
        val last = s1 - 1
        val endPt = segP1[last] + (if (segVerb[last] == SoftPath.L) 0 else if (segVerb[last] == SoftPath.Q) 1 else 2)
        cap(out, pts[endPt * 2], pts[endPt * 2 + 1], ne[last * 2], ne[last * 2 + 1], h, round, rp[endPt * 2], rp[endPt * 2 + 1])
        // right side, backward
        for (k in last downTo s0) {
            val q = segP1[k]
            when (segVerb[k]) {
                SoftPath.L -> out.lineTo(sr[k * 2], sr[k * 2 + 1])
                SoftPath.Q -> out.quadraticTo(rp[q * 2], rp[q * 2 + 1], sr[k * 2], sr[k * 2 + 1])
                else -> out.cubicTo(rp[q * 2 + 2], rp[q * 2 + 3], rp[q * 2], rp[q * 2 + 1], sr[k * 2], sr[k * 2 + 1])
            }
            if (k > s0) {
                val e = segP0[k]
                join(out, pts, e, ns, k * 2, ne, (k - 1) * 2, -h, rp[e * 2], rp[e * 2 + 1])
            }
        }
        // start cap: walking from the right offset back to the left one, facing backwards
        cap(out, pts[segP0[s0] * 2], pts[segP0[s0] * 2 + 1], -ns[s0 * 2], -ns[s0 * 2 + 1], h, round, sl[s0 * 2], sl[s0 * 2 + 1])
        out.close()
    }

    /** From the offset point with normal a to the one with normal b, around point [pi] (signed half width [h]). */
    private fun join(out: Path, pts: FloatArray, pi: Int, a: FloatArray, ai: Int, b: FloatArray, bi: Int, h: Float, ex: Float, ey: Float) {
        val ax = a[ai]; val ay = a[ai + 1]; val bx = b[bi]; val by = b[bi + 1]
        val dot = ax * bx + ay * by
        if (dot > .9999f) { out.lineTo(ex, ey); return }
        val d = max(MITER_MIN, 1f + dot)
        out.quadraticTo(pts[pi * 2] + h * (ax + bx) / d, pts[pi * 2 + 1] + h * (ay + by) / d, ex, ey)
    }

    /**
     * Cap at (x, y) with left normal (nx, ny) of the direction of travel: from the left offset (current point)
     * to the right offset (ex, ey).
     */
    private fun cap(out: Path, x: Float, y: Float, nx: Float, ny: Float, h: Float, round: Boolean, ex: Float, ey: Float) {
        if (!round) { out.lineTo(ex, ey); return }
        // direction of travel = normal rotated clockwise: (ny, -nx)
        val dx = ny; val dy = -nx
        val k = KAPPA * h
        val tx = x + h * dx; val ty = y + h * dy
        out.cubicTo(x + h * nx + k * dx, y + h * ny + k * dy, tx + k * nx, ty + k * ny, tx, ty)
        out.cubicTo(tx - k * nx, ty - k * ny, ex + k * dx, ey + k * dy, ex, ey)
    }

    companion object {
        /** Cubic quarter-circle handle length. */
        const val KAPPA = .5523f
        /** 1 + cos(turn) floor: caps the miter at 4 half widths for very sharp bends. */
        const val MITER_MIN = .25f

        /** Verbs and points of a path in [SoftPath] layout, or null when it has a close, a conic or nothing to stroke. */
        fun flatten(src: Path): Pair<ByteArray, FloatArray>? {
            val verbs = ArrayList<Byte>()
            val pts = ArrayList<Float>()
            val it = src.iterator(PathIterator.ConicEvaluation.AsQuadratics, .25f)
            var segs = 0
            while (it.hasNext()) {
                val s = it.next()
                val p = s.points
                when (s.type) {
                    PathSegment.Type.Move -> { verbs += SoftPath.M; pts += p[0]; pts += p[1] }
                    PathSegment.Type.Line -> { verbs += SoftPath.L; pts += p[2]; pts += p[3]; segs++ }
                    PathSegment.Type.Quadratic -> { verbs += SoftPath.Q; for (i in 2 until 6) pts += p[i]; segs++ }
                    PathSegment.Type.Cubic -> { verbs += SoftPath.C; for (i in 2 until 8) pts += p[i]; segs++ }
                    PathSegment.Type.Close, PathSegment.Type.Conic -> return null
                    PathSegment.Type.Done -> break
                }
            }
            if (segs == 0) return null
            return verbs.toByteArray() to pts.toFloatArray()
        }

        /** Load time: the fill outline of [src] stroked with [width], or null when [flatten] cannot take it. */
        fun of(src: Path, width: Float, round: Boolean): Path? {
            val (verbs, pts) = flatten(src) ?: return null
            val out = Path()
            StrokeOutline(pts.size / 2, verbs.size).build(verbs, verbs.size, pts, width, round, out)
            return out
        }

        /** True for a single straight segment: Skia already draws such a butt-capped stroke as a rectangle. */
        fun isSingleLine(src: Path): Boolean {
            val f = flatten(src) ?: return false
            return f.first.size == 2 && f.first[1] == SoftPath.L
        }

        /**
         * Share of the outline length (curves sampled into chords) running more than [slantDeg] off both axes.
         * Used by the big-shape anti-aliasing rule ([AaRule]).
         */
        fun slantedShare(src: Path, slantDeg: Float): Float {
            val it = src.iterator(PathIterator.ConicEvaluation.AsQuadratics, .25f)
            var total = 0f
            var slanted = 0f
            var sx = 0f; var sy = 0f // subpath start, for the closing edge
            var lx = 0f; var ly = 0f
            val lim = slantDeg * DEG
            fun chord(x: Float, y: Float) {
                val dx = x - lx; val dy = y - ly
                val len = hypot(dx, dy)
                if (len > 0f) {
                    val a = atan2(abs(dy), abs(dx)) // 0..90 deg
                    total += len
                    if (min(a, HALF_PI - a) > lim) slanted += len
                }
                lx = x; ly = y
            }
            while (it.hasNext()) {
                val s = it.next()
                val p = s.points
                when (s.type) {
                    PathSegment.Type.Move -> {
                        if (lx != sx || ly != sy) chord(sx, sy) // previous subpath closes implicitly
                        sx = p[0]; sy = p[1]; lx = sx; ly = sy
                    }
                    PathSegment.Type.Line -> chord(p[2], p[3])
                    PathSegment.Type.Quadratic, PathSegment.Type.Conic -> for (i in 1..CHORDS) {
                        val t = i / CHORDS.toFloat(); val u = 1f - t
                        chord(u * u * p[0] + 2 * u * t * p[2] + t * t * p[4], u * u * p[1] + 2 * u * t * p[3] + t * t * p[5])
                    }
                    PathSegment.Type.Cubic -> for (i in 1..CHORDS) {
                        val t = i / CHORDS.toFloat(); val u = 1f - t
                        val a = u * u * u; val b = 3 * u * u * t; val c = 3 * u * t * t; val d = t * t * t
                        chord(a * p[0] + b * p[2] + c * p[4] + d * p[6], a * p[1] + b * p[3] + c * p[5] + d * p[7])
                    }
                    PathSegment.Type.Close -> { chord(sx, sy) }
                    PathSegment.Type.Done -> break
                }
            }
            if (lx != sx || ly != sy) chord(sx, sy) // fills close implicitly
            return if (total > 0f) slanted / total else 0f
        }

        private const val CHORDS = 8
        private val HALF_PI = (kotlin.math.PI / 2).toFloat()
    }
}

/**
 * Plan B item 2 anti-aliasing rule (`--es aa big0`), applied once at load to fills only:
 * anti-aliasing is off for a path or rectangle whose bounds are at least [bigSize] art units on their long side
 * and whose outline runs more than [slantDeg] off both axes for at most [maxSlanted] of its length.
 * Ellipses and circles keep it (Skia's analytic AA there costs nothing extra), so do small shapes and strokes,
 * also strokes drawn as outline fills (a thin band shows stair steps far more than a big area does).
 */
class AaRule(val bigSize: Float, val maxSlanted: Float = .25f, val slantDeg: Float = 20f) {
    fun keepAa(path: Path): Boolean {
        val r = path.getBounds()
        if (max(r.width, r.height) < bigSize) return true
        return StrokeOutline.slantedShare(path, slantDeg) > maxSlanted
    }

    fun keepAaRect(w: Float, h: Float) = max(abs(w), abs(h)) < bigSize
}
