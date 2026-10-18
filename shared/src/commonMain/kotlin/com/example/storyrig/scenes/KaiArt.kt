package com.example.storyrig.scenes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.example.storyrig.engine.Atlas
import com.example.storyrig.engine.MeshData
import com.example.storyrig.engine.MeshInfluence
import com.example.storyrig.engine.MeshPart
import com.example.storyrig.engine.PseudoYaw
import com.example.storyrig.engine.SoftMesh
import com.example.storyrig.engine.Clip
import com.example.storyrig.engine.ClipPlayer
import com.example.storyrig.engine.AutoLayers
import com.example.storyrig.engine.HumanoidMotion
import com.example.storyrig.engine.SecondaryMotion
import com.example.storyrig.engine.SpringBone
import com.example.storyrig.engine.BitmapPart
import com.example.storyrig.engine.DrawItem
import com.example.storyrig.engine.Rig
import com.example.storyrig.engine.Skeleton
import com.example.storyrig.engine.Sprite
import com.example.storyrig.engine.View
import com.example.storyrig.engine.uptimeMillis
import org.jetbrains.compose.resources.decodeToImageBitmap
import storyrig.shared.generated.resources.Res

/**
 * Kai as bitmap parts. Data comes from `composeResources/files/kai/` written by `:tools` (KaiCut):
 * bones.csv (bone, parent, rest pivot), parts.csv (part, bone, top-left, size, drawn) and mesh.csv (skinned mesh
 * groups and bone influences, for `art=mesh`), all in ref.png pixels. Nothing here is specific to one character.
 */
class KaiAssets(
    val atlas: Atlas,
    val boneNames: List<String>,
    val boneParent: IntArray,
    val pivotX: FloatArray,
    val pivotY: FloatArray,
    val parts: List<PartRow>,
    /** Skinned meshes (mesh.csv), built only for `art=mesh` (empty otherwise, or without mesh.csv). */
    val meshes: List<Mesh>,
    /** Texture of [meshes] (one combined image per limb), null without meshes. */
    val meshAtlas: Atlas?,
    /** Read + decode + atlas packing (+ limb images and meshes). */
    val loadMs: Float,
    /** Real height relative to Kai (character.csv `heightScale`): ref.png always fills the frame, people don't. */
    val heightScale: Float = 1f,
    /** Folder name under files/ (kai, kai_front, ...). */
    val name: String = "",
    /** Which way the cut faces (character.csv `view`; else the folder's _front/_back suffix; else 3q). */
    val view: View = View.Q3,
    /** character.csv `yaw` (degrees), when the cut says how far it is turned; see [yawDeg]. */
    private val yawOverride: Float? = null,
) {
    /** Rest pose, from the data (ref.png pixels): root pivot to the soles, and hair top to the soles. */
    val rootToSole: Float
    val height: Float

    /**
     * How far the cut is turned from facing the viewer (degrees, > 0 = towards screen right; ClipPlayer draws
     * forward swings with it). character.csv `yaw`, else from the face: the eyes' midpoint off the head's centre
     * is R sin(yaw) on a head of radius R; seen from behind 180; else the view's default.
     */
    val yawDeg: Float by lazy {
        yawOverride ?: run {
            if (view == View.BACK) return@run 180f
            val head = parts.firstOrNull { it.name == "head" }
            val e1 = parts.firstOrNull { it.name == "eye_near_open" }
            val e2 = parts.firstOrNull { it.name == "eye_far_open" }
            if (head == null || e1 == null || e2 == null) return@run view.yawDeg
            val mid = (e1.x + e1.w / 2f + e2.x + e2.w / 2f) / 2f
            val off = (mid - (head.x + head.w / 2f)) / (head.w * FACE_R)
            kotlin.math.asin(off.coerceIn(-1f, 1f)) * 180f / kotlin.math.PI.toFloat()
        }
    }

    init {
        val drawn = parts.filter { it.drawn }
        val sole = drawn.maxOf { it.y + it.h }.toFloat()
        rootToSole = sole - pivotY[boneParent.indexOfFirst { it < 0 }]
        height = sole - drawn.minOf { it.y }
    }

    class PartRow(val name: String, val bone: Int, val x: Int, val y: Int, val w: Int, val h: Int, val drawn: Boolean)

    /**
     * Cut parts that become one skinned mesh (drawn once into one image at load), the bones it follows and its
     * grid cell in ref.png pixels. A `group` row of mesh.csv.
     */
    class MeshGroup(val name: String, val parts: List<String>, val bones: List<String>, val cell: Float)

    /**
     * Where a bone pulls on mesh vertices ([MeshInfluence]), in ref.png pixels: the capsule [seg] (x0, y0, x1, y1)
     * plus [radius], another bone fading in over [blend]. [attachTo]: the bone's start stays on that parent across
     * the joint, blended over [band]. An `influence` row of mesh.csv.
     */
    class Influence(
        val bone: String,
        val seg: FloatArray,
        val radius: Float,
        val blend: Float,
        val attachTo: String?,
        val band: Float,
    )

    /** A built [MeshGroup]: it replaces [parts] in the draw list, drawn where the first of them was. */
    class Mesh(val group: MeshGroup, val parts: Set<String>, val firstPart: String, val data: MeshData)

    fun bone(name: String) = boneNames.indexOf(name).also { require(it >= 0) { "no bone $name" } }
    fun boneOrNull(name: String) = boneNames.indexOf(name)
    fun part(name: String) = parts.first { it.name == name }

    companion object {
        /** Face sphere radius as a share of the head picture's width (ears included). */
        private const val FACE_R = .42f
        /**
         * [screenScale]: ref.png pixels to stage units for this scene; with [cellPx] > 0 the mesh cell is chosen so
         * it is about [cellPx] stage units on screen (never finer than the group's own cell). [block]: see
         * [MeshData.grid].
         */
        suspend fun load(name: String = "kai", mesh: Boolean = false, screenScale: Float = 1f, cellPx: Float = 0f, block: Int = 1): KaiAssets {
            val dir = "files/$name"
            val t0 = uptimeMillis()
            val boneRows = csv(Res.readBytes("$dir/bones.csv").decodeToString())
            val partRows = csv(Res.readBytes("$dir/parts.csv").decodeToString())
            val names = boneRows.map { it[0] }
            val parent = IntArray(names.size) { i -> if (boneRows[i][1].isEmpty()) -1 else names.indexOf(boneRows[i][1]) }
            val px = FloatArray(names.size) { boneRows[it][2].toFloat() }
            val py = FloatArray(names.size) { boneRows[it][3].toFloat() }
            val parts = partRows.map { PartRow(it[0], names.indexOf(it[1]), it[2].toInt(), it[3].toInt(), it[4].toInt(), it[5].toInt(), it[6] == "1") }
            val images = parts.map { it.name to Res.readBytes("$dir/parts/${it.name}.png").decodeToImageBitmap() }
            val atlas = Atlas.pack(images)
            var meshes = emptyList<Mesh>()
            var meshAtlas: Atlas? = null
            // Limbs, torso and pelvis as meshes, all weighted from the one influence table so that a sleeve and the
            // shirt under it (a shorts leg and the pelvis) get the same weights where they overlap and bend as one
            // cloth. Without mesh.csv the rig keeps its rigid parts.
            val meshRows = if (mesh) runCatching { csv(Res.readBytes("$dir/mesh.csv").decodeToString()) }.getOrNull() else null
            if (mesh && meshRows == null) println("StoryRig: no $dir/mesh.csv, drawing rigid parts")
            if (meshRows != null) {
                val groups = meshRows.filter { it[0] == "group" }.map {
                    MeshGroup(it[1], it[3].split(';'), it[4].split(';'), it[2].toFloat())
                }
                val influences = meshRows.filter { it[0] == "influence" }.associate {
                    it[1] to Influence(
                        it[1], FloatArray(4) { k -> it[2 + k].toFloat() }, it[6].toFloat(), it[7].toFloat(),
                        it[8].ifEmpty { null }, it[9].toFloat(),
                    )
                }
                val byName = images.toMap()
                val limbs = groups.map { g -> limbImage(g, parts, byName) }
                val ma = Atlas.pack(limbs.map { it.group.name to it.image })
                meshes = limbs.map {
                    val cell = if (cellPx > 0f) maxOf(it.group.cell, cellPx / screenScale) else it.group.cell
                    buildMesh(it, ma, influences, names, parent, px, py, cell, block)
                }
                meshAtlas = ma
            }
            val facts = runCatching { csv(Res.readBytes("$dir/character.csv").decodeToString()) }.getOrNull().orEmpty()
                .associate { it[0] to it.getOrElse(1) { "" } }
            val heightScale = facts["heightScale"]?.toFloatOrNull() ?: 1f
            val view = View.parse(facts["view"]) ?: viewFromName(name)
            return KaiAssets(
                atlas, names, parent, px, py, parts, meshes, meshAtlas, (uptimeMillis() - t0).toFloat(), heightScale, name, view,
                facts["yaw"]?.toFloatOrNull(),
            )
        }

        /** Fallback before character.csv says `view`: kai_front, kai_back. */
        fun viewFromName(name: String) = when {
            name.endsWith("_front") -> View.FRONT
            name.endsWith("_back") -> View.BACK
            else -> View.Q3
        }

        /** Folder names of every view of [base] that may exist (three-quarter first). */
        fun viewNames(base: String) = listOf(base, "${base}_front", "${base}_back")

        /** [firstPart]: the group's part drawn first (parts.csv order); the mesh is drawn in its place. */
        private class Limb(val group: MeshGroup, val image: ImageBitmap, val x: Int, val y: Int, val firstPart: String)

        /** The group's parts drawn once at their rest positions (parts.csv order) into one image. */
        private fun limbImage(g: MeshGroup, parts: List<PartRow>, images: Map<String, ImageBitmap>): Limb {
            val rows = parts.filter { it.name in g.parts }
            require(rows.size == g.parts.size) { "mesh ${g.name}: missing parts" }
            val x0 = rows.minOf { it.x }; val y0 = rows.minOf { it.y }
            val x1 = rows.maxOf { it.x + it.w }; val y1 = rows.maxOf { it.y + it.h }
            val out = ImageBitmap(x1 - x0, y1 - y0)
            val canvas = Canvas(out)
            val paint = Paint()
            for (r in rows) canvas.drawImage(images.getValue(r.name), Offset((r.x - x0).toFloat(), (r.y - y0).toFloat()), paint)
            return Limb(g, out, x0, y0, rows.first().name)
        }

        /**
         * Bind space is the rest pose with the root at the origin (what [KaiRig] captures), so a ref.png pixel p
         * is at p - rootPivot.
         */
        private fun buildMesh(
            limb: Limb, atlas: Atlas, table: Map<String, Influence>, names: List<String>, parent: IntArray,
            px: FloatArray, py: FloatArray, cell: Float, block: Int,
        ): Mesh {
            val g = limb.group
            val root = parent.indexOfFirst { it < 0 }
            val bx = px[root]; val by = py[root]
            fun boneIndex(name: String) = names.indexOf(name).also { require(it >= 0) { "no bone $name" } }
            val influences = g.bones.map { name ->
                val inf = table[name] ?: error("mesh.csv: no influence for $name")
                val s = inf.seg
                MeshInfluence(
                    boneIndex(name), s[0] - bx, s[1] - by, s[2] - bx, s[3] - by, inf.radius, inf.blend,
                    parent = inf.attachTo?.let { boneIndex(it) } ?: -1, attachBand = inf.band,
                )
            }
            val r = atlas[g.name]
            val data = MeshData.grid(atlas.image, r.x, r.y, r.w, r.h, limb.x - bx, limb.y - by, cell, influences, block)
            return Mesh(g, g.parts.toSet(), limb.firstPart, data)
        }

        private fun csv(text: String) = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split(',') }
            .toList()
    }
}

/**
 * One Kai: own skeleton and draw list over the shared atlas. Bone locals are rest pivots relative to the parent,
 * so a pose is just angles; the root's position and scale place him in the scene.
 */
class KaiRig(
    private val a: KaiAssets,
    antiAlias: Boolean,
    phase: Float = 0f,
    clips: Array<Clip> = DanceClips.ALL,
    /** Draw [KaiAssets.meshes] (skinned limbs, torso, pelvis) instead of their rigid parts. */
    useMeshes: Boolean = false,
    /** Draw the clips' forward swings as seen from the cut's yaw ([KaiAssets.yawDeg]); false draws them side on. */
    adaptView: Boolean = true,
    /** Turning actors: the yaw this cut is shown at (radians, see [PseudoYaw]); null = no pseudo-3D turn. */
    facingYaw: Float? = null,
    /** Turning actors: this rig is always drawn mirrored (the three-quarter cut facing left). */
    facingMirror: Boolean = false,
) {
    val sk = Skeleton(a.boneNames.size)
    private val restX = FloatArray(sk.size)
    private val restY = FloatArray(sk.size)
    val rig: Rig

    val root = a.bone("root")
    val torso = a.bone("torso")
    val head = a.bone("head")
    val shoulderN = a.bone("shoulder_near"); val elbowN = a.bone("elbow_near"); val wristN = a.bone("wrist_near")
    val shoulderF = a.bone("shoulder_far"); val elbowF = a.bone("elbow_far"); val wristF = a.bone("wrist_far")
    val hipN = a.bone("hip_near"); val kneeN = a.bone("knee_near"); val ankleN = a.bone("ankle_near")
    val hipF = a.bone("hip_far"); val kneeF = a.bone("knee_far"); val ankleF = a.bone("ankle_far")

    val view get() = a.view
    /** The cut's yaw (degrees), see [KaiAssets.yawDeg]. */
    val yawDeg get() = a.yawDeg
    /** Root pivot to the soles in the rest pose (ref.png px). */
    val rootToSole get() = a.rootToSole

    /** Shoulder angles (degrees) that make each arm hang straight down; poses are written relative to that. */
    val hangNear: Float
    val hangFar: Float

    /** Springs and auto layers (engine, shared by every humanoid); run them after the base pose. */
    val motion: SecondaryMotion
    val auto: AutoLayers
    /** Dance clips (data in [DanceClips]); run after [reset], before [motion]. */
    val player: ClipPlayer

    /** Null in a view without a face (seen from behind); the arrays below are then empty. */
    val mouth: BitmapPart?
    val eyeNear: BitmapPart?
    val eyeFar: BitmapPart?
    val mouths: Array<Sprite>
    val eyesNear: Array<Sprite>
    val eyesFar: Array<Sprite>

    /** Pseudo-3D turn of this cut; null unless [facingYaw] was given. */
    val yaw: PseudoYaw?
    /**
     * Items headFrom until headTo of [rig] are the head and what rides on it (face, hair), contiguous in the draw
     * order; -1 when they are not, and then the head can't be drawn on another rig's body.
     */
    val headFrom: Int
    val headTo: Int

    init {
        for (i in 0 until sk.size) {
            val p = a.boneParent[i]
            sk.parent[i] = p
            restX[i] = if (p < 0) 0f else a.pivotX[i] - a.pivotX[p]
            restY[i] = if (p < 0) 0f else a.pivotY[i] - a.pivotY[p]
            sk.set(i, restX[i], restY[i])
        }
        // Rest pose with the root at the origin: the bind pose of the meshes.
        sk.captureBind()
        val items = ArrayList<DrawItem>()
        val byName = HashMap<String, BitmapPart>()
        val meshes = if (useMeshes) a.meshes else emptyList()
        for (row in a.parts) if (row.drawn) {
            val mesh = meshes.firstOrNull { row.name in it.parts }
            if (mesh != null) {
                if (row.name == mesh.firstPart) items += MeshPart(SoftMesh(mesh.data), a.meshAtlas!!, antiAlias)
                continue
            }
            val bp = BitmapPart(row.bone, a.atlas, sprite(row.name), antiAlias)
            byName[row.name] = bp
            items += bp
        }
        rig = Rig(sk, items.toTypedArray())
        mouth = byName["mouth_smile"]
        eyeNear = byName["eye_near_open"]
        eyeFar = byName["eye_far_open"]
        val springs = HumanoidMotion.springs { a.boneOrNull(it) }
        for (s in springs) massDir(s)
        motion = SecondaryMotion(sk, springs)
        auto = AutoLayers(sk, root, torso, phase = phase, soleBelow = a.rootToSole)
        hangNear = hang(shoulderN, wristN)
        hangFar = hang(shoulderF, wristF)
        lengthAxes()
        player = ClipPlayer(
            sk, clips, { a.boneOrNull(it) },
            zeroDeg = { when (it) { "shoulder_near" -> hangNear; "shoulder_far" -> hangFar; else -> 0f } },
            yawDeg = if (adaptView) a.yawDeg else null,
        )
        val inHead = BooleanArray(sk.size) { b -> var x = b; while (x >= 0 && x != head) x = sk.parent[x]; x == head }
        val hi = rig.items.indices.filter { val it = rig.items[it]; it is BitmapPart && inHead[it.bone] }
        val contiguous = hi.isNotEmpty() && hi.last() - hi.first() + 1 == hi.size
        headFrom = if (contiguous) hi.first() else -1
        headTo = if (contiguous) hi.last() + 1 else -1
        yaw = facingYaw?.let { fy ->
            val lateral = listOf("shoulder_near", "shoulder_far", "hip_near", "hip_far").map { a.boneOrNull(it) }
                .filter { it >= 0 && (sk.parent[it] == torso || sk.parent[it] == root) }.toIntArray()
            val features = byName.filter { (n, p) -> p.bone == head && n != "head" }.values.toTypedArray()
            val headPart = a.parts.firstOrNull { it.name == "head" }
            PseudoYaw(
                sk, fy, facingMirror, root, torso, head, a.boneOrNull("hair"),
                lateral, FloatArray(lateral.size) { restX[lateral[it]] }, features,
                headR = (headPart?.w ?: 300) * HEAD_R,
            )
        }
        mouths = if (mouth == null) emptyArray() else MOUTHS.map { spriteOr(it, "mouth_smile") }.toTypedArray()
        eyesNear = if (eyeNear == null) emptyArray() else EYES.map { spriteOr("eye_near_$it", "eye_near_open") }.toTypedArray()
        eyesFar = if (eyeFar == null) emptyArray() else EYES.map { spriteOr("eye_far_$it", "eye_far_open") }.toTypedArray()
    }

    /** Direction from the bone's pivot to the centre of its drawn parts (rest pose = local space). */
    private fun massDir(s: SpringBone) {
        var cx = 0f; var cy = 0f; var area = 0f
        for (row in a.parts) if (row.drawn && row.bone == s.bone) {
            val ar = (row.w * row.h).toFloat()
            cx += (row.x + row.w / 2f - a.pivotX[s.bone]) * ar
            cy += (row.y + row.h / 2f - a.pivotY[s.bone]) * ar
            area += ar
        }
        if (area <= 0f) return
        val l = kotlin.math.hypot(cx, cy)
        if (l > 1f) { s.massX = cx / l; s.massY = cy / l }
    }

    /**
     * Each bone's length axis for foreshortening ([Skeleton.fore]), in its rest local space: towards its first child
     * (thigh to knee), or for an end bone (hand, foot) towards the centre of its drawn parts, else its parent's.
     */
    private fun lengthAxes() {
        for (b in 0 until sk.size) {
            var dx = 0f; var dy = 0f
            val child = (0 until sk.size).firstOrNull { a.boneParent[it] == b }
            if (child != null) {
                dx = a.pivotX[child] - a.pivotX[b]; dy = a.pivotY[child] - a.pivotY[b]
            } else {
                var area = 0f
                for (row in a.parts) if (row.drawn && row.bone == b) {
                    val ar = (row.w * row.h).toFloat()
                    dx += (row.x + row.w / 2f - a.pivotX[b]) * ar; dy += (row.y + row.h / 2f - a.pivotY[b]) * ar
                    area += ar
                }
            }
            val l = kotlin.math.hypot(dx, dy)
            val p = a.boneParent[b]
            if (l > 1f) { sk.axisX[b] = dx / l; sk.axisY[b] = dy / l }
            else if (p in 0 until b) { sk.axisX[b] = sk.axisX[p]; sk.axisY[b] = sk.axisY[p] }
        }
    }

    private fun hang(shoulder: Int, wrist: Int): Float {
        val dx = a.pivotX[wrist] - a.pivotX[shoulder]; val dy = a.pivotY[wrist] - a.pivotY[shoulder]
        return 90f - kotlin.math.atan2(dy, dx) / DEG2RAD
    }

    /** [name]'s sprite, or [fallback]'s when the character has no such expression (no face sheet yet). */
    private fun spriteOr(name: String, fallback: String) = sprite(if (a.parts.any { it.name == name }) name else fallback)

    /** Sprite in its bone's local space: top-left relative to the bone's rest pivot. */
    private fun sprite(name: String): Sprite {
        val row = a.part(name)
        return Sprite(a.atlas[name], (row.x - a.pivotX[row.bone]).toInt(), (row.y - a.pivotY[row.bone]).toInt())
    }

    /**
     * Back to rest pose, root at (x, y) with uniform [scale]; [mirror] flips the rig to face left (root scaleX < 0).
     * Angles are then set with [rot] (degrees).
     */
    fun reset(x: Float, y: Float, scale: Float, mirror: Boolean = false) {
        for (i in 0 until sk.size) sk.set(i, restX[i], restY[i])
        sk.fore.fill(1f)
        sk.roll.fill(1f)
        sk.wide.fill(1f)
        sk.set(root, x, y, 0f, if (mirror) -scale else scale, scale)
    }

    /** Forget spring and squash motion (after a jump the viewer doesn't see, e.g. mirroring a hidden rig). */
    fun resetMotion() {
        motion.reset()
        auto.reset()
    }

    fun rot(bone: Int, deg: Float) = sk.setRot(bone, deg * DEG2RAD)

    /** Shoulder rotation relative to the arm hanging straight down. */
    fun arm(near: Boolean, deg: Float) = if (near) rot(shoulderN, hangNear + deg) else rot(shoulderF, hangFar + deg)

    companion object {
        /** Lip-sync shapes, in the order the scene picks from. */
        val MOUTHS = listOf("mouth_smile", "mouth_e", "mouth_a", "mouth_o", "mouth_s", "mouth_laugh")
        val EYES = listOf("open", "half", "happy", "wide")
        const val EYE_OPEN = 0
        const val EYE_HALF = 1
        const val EYE_HAPPY = 2

        /** Rest-pose height in ref.png pixels (hair top to sole). */
        const val HEIGHT = 850f
        /** Head sphere radius for face features turning, as a share of the head picture's width (ears included). */
        private const val HEAD_R = .42f
        private const val DEG2RAD = (kotlin.math.PI / 180.0).toFloat()
    }
}
