package com.example.storyrig.engine

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * How a bone pulls on mesh vertices when weights are computed automatically ([MeshData.grid]), bind space.
 * The bone is a capsule: segment ([x0], [y0]) to ([x1], [y1]) with [radius] (0 for a limb bone, about the body's
 * half width for a torso or pelvis). A vertex's distance to it is measured along the bone (how far past either
 * end) plus across it (beyond the radius), not straight to the nearest point: a vertex on a limb's outline is then
 * as far from the next bone as its centre line is, so the joint blends over the same short stretch across the
 * whole width, and the limb segments stay straight (the usual rule in 2D rigs: each segment fully on its bone,
 * a narrow blend at the joint). Another bone fades in over [blend] units of that distance.
 *
 * [parent] >= 0 attaches the bone's start to its parent across the joint (a sleeve to the shirt, a shorts leg to
 * the pelvis): along the bone's axis, vertices more than [attachBand]/2 behind the start follow [parent] only,
 * vertices more than [attachBand]/2 past it follow this bone, with a smooth blend in between.
 */
class MeshInfluence(
    val bone: Int,
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val radius: Float = 0f,
    val blend: Float = 30f,
    val parent: Int = -1,
    val attachBand: Float = 0f,
)

/**
 * Geometry of a textured triangle mesh, built once at load and shared by every rig that uses it.
 * Each vertex follows up to 4 bones (blended as dual quaternions by [SoftMesh]).
 *  - [bind]: x,y per vertex in the skeleton's bind-pose rig space
 *  - [tex]: u,v per vertex in texture pixels
 *  - [bones], [weights]: 4 per vertex, weights sum to 1 (unused slots have weight 0)
 */
class MeshData(
    val bind: FloatArray,
    val tex: FloatArray,
    val indices: ShortArray,
    val bones: IntArray,
    val weights: FloatArray,
) {
    val vertexCount = bind.size / 2
    val triangleCount = indices.size / 3

    companion object {
        /**
         * Grid over the texture rectangle ([rx], [ry], [rw], [rh]) of [image], cells about [cell] texture pixels
         * wide; cells without a single visible pixel are dropped (alpha is read once, here).
         * One texture pixel is one bind-space unit; ([ox], [oy]) is the bind-space position of the rectangle's
         * top-left corner.
         *
         * Adaptive: a [block] x [block] group of cells whose grid points all follow one bone only (the middle of
         * the shirt, a forearm) is one quad, since a rigid area needs no inner vertices; fine cells stay only
         * where weights change (joints). Two adjacent rigid blocks share their edge points, so they follow the
         * same bone, and a rigid block next to fine cells shares the fine edge points, which are rigid too and
         * stay on the block's straight edge after any affine bone transform: no cracks. [block] = 1 is a plain grid.
         *
         * Weights from [influences]: the bone whose capsule is nearest to a vertex (along + across, see
         * [MeshInfluence]) gets full weight; another bone fades in when the vertex is less than its blend further
         * from its capsule. So parts stay rigid away
         * from joints and bend smoothly near them. Then attached bones hand weight to their parent across the
         * joint ([MeshInfluence.parent]). Meshes that overlap (a sleeve on a shirt) get the same weights where
         * they overlap when built from the same influences, so they deform as one cloth.
         */
        fun grid(
            image: ImageBitmap,
            rx: Int, ry: Int, rw: Int, rh: Int,
            ox: Float, oy: Float,
            cell: Float,
            influences: List<MeshInfluence>,
            block: Int = 1,
        ): MeshData {
            require(influences.isNotEmpty() && block >= 1)
            val nx = ceil(rw / cell).toInt().coerceAtLeast(1)
            val ny = ceil(rh / cell).toInt().coerceAtLeast(1)
            val alpha = IntArray(rw * rh)
            image.readPixels(alpha, rx, ry, rw, rh)

            // Weight slots: one per influence, then one per attach parent that is not an influence itself.
            val slotBone = ArrayList<Int>()
            for (f in influences) slotBone += f.bone
            for (f in influences) if (f.parent >= 0 && f.parent !in slotBone) slotBone += f.parent
            val slots = slotBone.size
            val dist = FloatArray(influences.size)
            val raw = FloatArray(slots)
            /** Up to 4 strongest bones and normalised weights for bind point (x, y), into [ob]/[ow] at [o]. */
            fun weigh(x: Float, y: Float, ob: IntArray, ow: FloatArray, o: Int) {
                var dMin = Float.MAX_VALUE
                for ((s, f) in influences.withIndex()) {
                    dist[s] = capsuleDistance(x, y, f)
                    if (dist[s] < dMin) dMin = dist[s]
                }
                raw.fill(0f)
                var sum = 0f
                for ((s, f) in influences.withIndex()) {
                    raw[s] = 1f - smooth((dist[s] - dMin) / f.blend)
                    sum += raw[s]
                }
                for (s in influences.indices) raw[s] /= sum
                for ((s, f) in influences.withIndex()) {
                    if (f.parent < 0 || raw[s] == 0f) continue
                    val ax = f.x1 - f.x0; val ay = f.y1 - f.y0
                    val len = sqrt(ax * ax + ay * ay)
                    val along = if (len == 0f) 0f else ((x - f.x0) * ax + (y - f.y0) * ay) / len
                    val toParent = 1f - smooth((along + f.attachBand / 2f) / f.attachBand)
                    val moved = raw[s] * toParent
                    raw[s] -= moved
                    raw[slotBone.indexOf(f.parent)] += moved
                }
                // Keep the 4 strongest, normalised.
                sum = 0f
                for (k in 0 until 4) {
                    ob[o + k] = 0; ow[o + k] = 0f
                    var best = -1
                    for (s in 0 until slots) if (raw[s] > 0f && (best < 0 || raw[s] > raw[best])) best = s
                    if (best < 0) continue
                    ob[o + k] = slotBone[best]
                    ow[o + k] = raw[best]
                    sum += raw[best]
                    raw[best] = 0f
                }
                for (k in 0 until 4) ow[o + k] /= sum
            }

            // Grid point (i, j) sits at pixel (i * rw / nx, j * rh / ny); cell (i, j) covers [point(i,j), point(i+1,j+1)).
            val pw = nx + 1
            val rigidBone = IntArray(pw * (ny + 1))
            run {
                val b = IntArray(4); val w = FloatArray(4)
                for (j in 0..ny) for (i in 0..nx) {
                    weigh(ox + i * rw / nx.toFloat(), oy + j * rh / ny.toFloat(), b, w, 0)
                    rigidBone[j * pw + i] = if (w[0] >= RIGID) b[0] else -1
                }
            }
            val visible = BooleanArray(nx * ny)
            for (j in 0 until ny) for (i in 0 until nx) {
                val x0 = i * rw / nx; val x1 = (i + 1) * rw / nx
                val y0 = j * rh / ny; val y1 = (j + 1) * rh / ny
                loop@ for (y in y0 until y1) for (x in x0 until x1) {
                    if ((alpha[y * rw + x] ushr 24) >= ALPHA_MIN) { visible[j * nx + i] = true; break@loop }
                }
            }

            val pointIndex = IntArray(pw * (ny + 1)) { -1 }
            val bind = ArrayList<Float>()
            val tex = ArrayList<Float>()
            val indices = ArrayList<Short>()
            fun point(i: Int, j: Int): Int {
                val k = j * pw + i
                if (pointIndex[k] < 0) {
                    pointIndex[k] = tex.size / 2
                    val px = i * rw / nx.toFloat()
                    val py = j * rh / ny.toFloat()
                    tex += rx + px; tex += ry + py
                    bind += ox + px; bind += oy + py
                }
                return pointIndex[k]
            }
            fun quad(i0: Int, j0: Int, i1: Int, j1: Int) {
                val a = point(i0, j0); val b = point(i1, j0); val c = point(i1, j1); val d = point(i0, j1)
                indices += a.toShort(); indices += b.toShort(); indices += c.toShort()
                indices += a.toShort(); indices += c.toShort(); indices += d.toShort()
            }
            for (bj in 0 until ny step block) for (bi in 0 until nx step block) {
                val i1 = minOf(bi + block, nx); val j1 = minOf(bj + block, ny)
                var any = false
                for (j in bj until j1) for (i in bi until i1) if (visible[j * nx + i]) any = true
                if (!any) continue
                val bone = rigidBone[bj * pw + bi]
                var rigid = block > 1 && bone >= 0
                if (rigid) loop@ for (j in bj..j1) for (i in bi..i1) if (rigidBone[j * pw + i] != bone) { rigid = false; break@loop }
                if (rigid) quad(bi, bj, i1, j1)
                else for (j in bj until j1) for (i in bi until i1) if (visible[j * nx + i]) quad(i, j, i + 1, j + 1)
            }
            val n = tex.size / 2
            require(n < Short.MAX_VALUE) { "mesh too large: $n vertices" }

            val bones = IntArray(n * 4)
            val weights = FloatArray(n * 4)
            for (v in 0 until n) weigh(bind[v * 2], bind[v * 2 + 1], bones, weights, v * 4)
            return MeshData(bind.toFloatArray(), tex.toFloatArray(), indices.toShortArray(), bones, weights)
        }

        /** Smoothstep of t clamped to 0..1. */
        private fun smooth(t: Float): Float {
            val c = t.coerceIn(0f, 1f)
            return c * c * (3f - 2f * c)
        }

        /** Along the bone past its ends, plus across it beyond the radius; see [MeshInfluence]. */
        private fun capsuleDistance(px: Float, py: Float, f: MeshInfluence): Float {
            val dx = f.x1 - f.x0; val dy = f.y1 - f.y0
            val len = sqrt(dx * dx + dy * dy)
            val qx = px - f.x0; val qy = py - f.y0
            if (len == 0f) return (sqrt(qx * qx + qy * qy) - f.radius).coerceAtLeast(0f)
            val along = (qx * dx + qy * dy) / len
            val across = kotlin.math.abs(qx * dy - qy * dx) / len
            val past = if (along < 0f) -along else if (along > len) along - len else 0f
            return past + (across - f.radius).coerceAtLeast(0f)
        }

        /** Cells whose pixels are all below this alpha (0..255) are dropped. */
        private const val ALPHA_MIN = 4
        /** A grid point with this much weight on one bone counts as rigid. */
        private const val RIGID = .999f
    }
}

/**
 * One rig's copy of a [MeshData]: only the deformed positions are per rig. [deform] writes rig-space positions
 * into a reused FloatArray; no allocation after load.
 *
 * Skinning blends the bones' rotations (planar dual quaternions, [Skeleton.dq]) instead of their transformed
 * positions: a joint bent 150 degrees bends the cloth round an arc instead of pinching it towards the pivot,
 * as linear blending ([SoftPath]) does. Scale/shear (the root's uniform scale) is blended linearly.
 */
class SoftMesh(val data: MeshData) {
    val positions = FloatArray(data.bind.size)

    /** Call after [Skeleton.updateSkin]. */
    fun deform(sk: Skeleton, stats: RenderStats) {
        val bind = data.bind
        val bones = data.bones
        val weights = data.weights
        val dq = sk.dq
        for (i in 0 until data.vertexCount) {
            val x = bind[i * 2]
            val y = bind[i * 2 + 1]
            var rc = 0f; var rs = 0f; var dx = 0f; var dy = 0f
            var ux = 0f; var uy = 0f
            var c0 = 0f; var s0 = 0f
            for (k in 0 until 4) {
                val w = weights[i * 4 + k]
                if (w == 0f) continue
                val o = bones[i * 4 + k] * Skeleton.DQ
                if (k == 0) { c0 = dq[o]; s0 = dq[o + 1] }
                // q and -q are the same motion: take the one on the first bone's side.
                val sw = if (dq[o] * c0 + dq[o + 1] * s0 < 0f) -w else w
                rc += sw * dq[o]; rs += sw * dq[o + 1]
                dx += sw * dq[o + 2]; dy += sw * dq[o + 3]
                ux += w * (dq[o + 4] * x + dq[o + 5] * y)
                uy += w * (dq[o + 6] * y)
            }
            val n2 = rc * rc + rs * rs
            val inv = if (n2 > 0f) 1f / sqrt(n2) else 0f
            rc *= inv; rs *= inv; dx *= inv; dy *= inv
            val cosT = rc * rc - rs * rs
            val sinT = 2f * rc * rs
            positions[i * 2] = cosT * ux - sinT * uy + 2f * (dx * rc - dy * rs)
            positions[i * 2 + 1] = sinT * ux + cosT * uy + 2f * (dx * rs + dy * rc)
        }
        stats.meshVerts += data.vertexCount
    }
}
