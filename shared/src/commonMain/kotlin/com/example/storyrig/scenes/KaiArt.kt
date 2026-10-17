package com.example.storyrig.scenes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.example.storyrig.engine.Atlas
import com.example.storyrig.engine.MeshData
import com.example.storyrig.engine.MeshInfluence
import com.example.storyrig.engine.MeshPart
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
import com.example.storyrig.engine.uptimeMillis
import org.jetbrains.compose.resources.decodeToImageBitmap
import storyrig.shared.generated.resources.Res

/**
 * Kai as bitmap parts. Data comes from `composeResources/files/kai/` written by `:tools` (KaiCut):
 * bones.csv (bone, parent, rest pivot) and parts.csv (part, bone, top-left, size, drawn), all in ref.png pixels.
 */
class KaiAssets(
    val atlas: Atlas,
    val boneNames: List<String>,
    val boneParent: IntArray,
    val pivotX: FloatArray,
    val pivotY: FloatArray,
    val parts: List<PartRow>,
    /** Skinned meshes ([MESH_GROUPS]), built only for `art=mesh` (empty otherwise). */
    val meshes: List<Mesh>,
    /** Texture of [meshes] (one combined image per limb), null without meshes. */
    val meshAtlas: Atlas?,
    /** Read + decode + atlas packing (+ limb images and meshes). */
    val loadMs: Float,
) {
    class PartRow(val name: String, val bone: Int, val x: Int, val y: Int, val w: Int, val h: Int, val drawn: Boolean)

    /**
     * Cut parts that become one skinned mesh (drawn once into one image at load), the bones it follows and its
     * grid cell in ref.png pixels.
     */
    class MeshGroup(val name: String, val parts: List<String>, val bones: List<String>, val cell: Float)

    /**
     * Where a bone pulls on mesh vertices ([MeshInfluence]), in ref.png pixels. [seg] is (x0, y0, x1, y1); null
     * means from the bone's pivot to its child's pivot, or to the centre of its parts when it has no child.
     * [attachTo]: the bone's start stays on that parent across the joint, blended over [band].
     */
    class Influence(
        val bone: String,
        val radius: Float = 0f,
        val seg: FloatArray? = null,
        val attachTo: String? = null,
        val band: Float = 0f,
    )

    /** A built [MeshGroup]: it replaces [parts] in the draw list, drawn where the first of them was. */
    class Mesh(val group: MeshGroup, val parts: Set<String>, val firstPart: String, val data: MeshData)

    fun bone(name: String) = boneNames.indexOf(name).also { require(it >= 0) { "no bone $name" } }
    fun boneOrNull(name: String) = boneNames.indexOf(name)
    fun part(name: String) = parts.first { it.name == name }

    companion object {
        /**
         * Limbs, torso and pelvis as meshes, all weighted from the one [INFLUENCES] table so that a sleeve and the
         * shirt under it (a shorts leg and the pelvis) get the same weights where they overlap and bend as one
         * cloth. Shoulders and hips are attached across the joint: the top of a sleeve stays on the shirt and the
         * top of a shorts leg on the pelvis, the cloth in between stretches. The torso also follows the pelvis at
         * the hem. The shoes stay rigid bitmaps on the ankles (they sit over the shin's lower end).
         */
        val MESH_GROUPS = listOf(
            MeshGroup("arm_far", listOf("arm_upper_far", "arm_fore_far", "hand_far"), listOf("shoulder_far", "elbow_far", "wrist_far"), 8f),
            MeshGroup("leg_far", listOf("shin_far", "thigh_far"), listOf("hip_far", "knee_far", "ankle_far"), 8f),
            MeshGroup("leg_near", listOf("shin_near", "thigh_near"), listOf("hip_near", "knee_near", "ankle_near"), 8f),
            MeshGroup("pelvis", listOf("pelvis"), listOf("root", "hip_near", "hip_far"), 8f),
            MeshGroup("torso", listOf("torso"), listOf("torso", "shoulder_near", "shoulder_far", "root"), 8f),
            MeshGroup("arm_near", listOf("arm_upper_near", "arm_fore_near", "hand_near"), listOf("shoulder_near", "elbow_near", "wrist_near"), 8f),
        )

        /** Torso and pelvis are fat capsules (about the body's half width); limb bones are plain segments. */
        val INFLUENCES = listOf(
            Influence("torso", 80f, floatArrayOf(515f, 450f, 515f, 590f)),
            Influence("root", 45f, floatArrayOf(490f, 640f, 540f, 640f)),
            Influence("shoulder_near", attachTo = "torso", band = 40f), Influence("elbow_near"), Influence("wrist_near"),
            Influence("shoulder_far", attachTo = "torso", band = 40f), Influence("elbow_far"), Influence("wrist_far"),
            Influence("hip_near", attachTo = "root", band = 40f), Influence("knee_near"), Influence("ankle_near"),
            Influence("hip_far", attachTo = "root", band = 40f), Influence("knee_far"), Influence("ankle_far"),
        )

        /** Joint blend width in ref.png pixels. */
        const val BLEND = 30f

        /**
         * [screenScale]: ref.png pixels to stage units for this scene; with [cellPx] > 0 the mesh cell is chosen so
         * it is about [cellPx] stage units on screen (never finer than the group's own cell). [block]: see
         * [MeshData.grid].
         */
        suspend fun load(mesh: Boolean = false, screenScale: Float = 1f, cellPx: Float = 0f, block: Int = 1): KaiAssets {
            val t0 = uptimeMillis()
            val boneRows = csv(Res.readBytes("files/kai/bones.csv").decodeToString())
            val partRows = csv(Res.readBytes("files/kai/parts.csv").decodeToString())
            val names = boneRows.map { it[0] }
            val parent = IntArray(names.size) { i -> if (boneRows[i][1].isEmpty()) -1 else names.indexOf(boneRows[i][1]) }
            val px = FloatArray(names.size) { boneRows[it][2].toFloat() }
            val py = FloatArray(names.size) { boneRows[it][3].toFloat() }
            val parts = partRows.map { PartRow(it[0], names.indexOf(it[1]), it[2].toInt(), it[3].toInt(), it[4].toInt(), it[5].toInt(), it[6] == "1") }
            val images = parts.map { it.name to Res.readBytes("files/kai/parts/${it.name}.png").decodeToImageBitmap() }
            val atlas = Atlas.pack(images)
            var meshes = emptyList<Mesh>()
            var meshAtlas: Atlas? = null
            if (mesh) {
                val byName = images.toMap()
                val limbs = MESH_GROUPS.map { g -> limbImage(g, parts, byName) }
                val ma = Atlas.pack(limbs.map { it.group.name to it.image })
                meshes = limbs.map {
                    val cell = if (cellPx > 0f) maxOf(it.group.cell, cellPx / screenScale) else it.group.cell
                    buildMesh(it, ma, parts, names, parent, px, py, cell, block)
                }
                meshAtlas = ma
            }
            return KaiAssets(atlas, names, parent, px, py, parts, meshes, meshAtlas, (uptimeMillis() - t0).toFloat())
        }

        private class Limb(val group: MeshGroup, val image: ImageBitmap, val x: Int, val y: Int)

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
            return Limb(g, out, x0, y0)
        }

        /**
         * Bind space is the rest pose with the root at the origin (what [KaiRig] captures), so a ref.png pixel p
         * is at p - rootPivot.
         */
        private fun buildMesh(
            limb: Limb, atlas: Atlas, parts: List<PartRow>, names: List<String>, parent: IntArray, px: FloatArray, py: FloatArray,
            cell: Float, block: Int,
        ): Mesh {
            val g = limb.group
            val root = parent.indexOfFirst { it < 0 }
            val bx = px[root]; val by = py[root]
            fun boneIndex(name: String) = names.indexOf(name).also { require(it >= 0) { "no bone $name" } }
            val influences = g.bones.map { name ->
                val inf = INFLUENCES.firstOrNull { it.bone == name } ?: error("no influence for $name")
                val b = boneIndex(name)
                val s = inf.seg
                val x0: Float; val y0: Float; val x1: Float; val y1: Float
                if (s != null) {
                    x0 = s[0]; y0 = s[1]; x1 = s[2]; y1 = s[3]
                } else {
                    x0 = px[b]; y0 = py[b]
                    val child = parent.indexOfFirst { it == b }
                    val own = parts.filter { it.drawn && it.bone == b }
                    x1 = if (child >= 0) px[child] else if (own.isEmpty()) px[b] else own.sumOf { it.x + it.w / 2.0 }.toFloat() / own.size
                    y1 = if (child >= 0) py[child] else if (own.isEmpty()) py[b] else own.sumOf { it.y + it.h / 2.0 }.toFloat() / own.size
                }
                MeshInfluence(
                    b, x0 - bx, y0 - by, x1 - bx, y1 - by, inf.radius, BLEND,
                    parent = inf.attachTo?.let { boneIndex(it) } ?: -1, attachBand = inf.band,
                )
            }
            val r = atlas[g.name]
            val data = MeshData.grid(atlas.image, r.x, r.y, r.w, r.h, limb.x - bx, limb.y - by, cell, influences, block)
            val first = parts.first { it.name in g.parts }.name
            return Mesh(g, g.parts.toSet(), first, data)
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

    /** Shoulder angles (degrees) that make each arm hang straight down; poses are written relative to that. */
    val hangNear: Float
    val hangFar: Float

    /** Springs and auto layers (engine, shared by every humanoid); run them after the base pose. */
    val motion: SecondaryMotion
    val auto: AutoLayers
    /** Dance clips (data in [DanceClips]); run after [reset], before [motion]. */
    val player: ClipPlayer

    val mouth: BitmapPart
    val eyeNear: BitmapPart
    val eyeFar: BitmapPart
    val mouths: Array<Sprite>
    val eyesNear: Array<Sprite>
    val eyesFar: Array<Sprite>

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
        mouth = byName.getValue("mouth_smile")
        eyeNear = byName.getValue("eye_near_open")
        eyeFar = byName.getValue("eye_far_open")
        val springs = HumanoidMotion.springs { a.boneOrNull(it) }
        for (s in springs) massDir(s)
        motion = SecondaryMotion(sk, springs)
        auto = AutoLayers(sk, root, torso, phase = phase)
        hangNear = hang(shoulderN, wristN)
        hangFar = hang(shoulderF, wristF)
        player = ClipPlayer(sk, clips, { a.boneOrNull(it) }) {
            when (it) { "shoulder_near" -> hangNear; "shoulder_far" -> hangFar; else -> 0f }
        }
        mouths = MOUTHS.map { sprite(it) }.toTypedArray()
        eyesNear = EYES.map { sprite("eye_near_$it") }.toTypedArray()
        eyesFar = EYES.map { sprite("eye_far_$it") }.toTypedArray()
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

    private fun hang(shoulder: Int, wrist: Int): Float {
        val dx = a.pivotX[wrist] - a.pivotX[shoulder]; val dy = a.pivotY[wrist] - a.pivotY[shoulder]
        return 90f - kotlin.math.atan2(dy, dx) / DEG2RAD
    }

    /** Sprite in its bone's local space: top-left relative to the bone's rest pivot. */
    private fun sprite(name: String): Sprite {
        val row = a.part(name)
        return Sprite(a.atlas[name], (row.x - a.pivotX[row.bone]).toInt(), (row.y - a.pivotY[row.bone]).toInt())
    }

    /** Back to rest pose, root at (x, y) with uniform [scale]. Angles are then set with [rot] (degrees). */
    fun reset(x: Float, y: Float, scale: Float) {
        for (i in 0 until sk.size) sk.set(i, restX[i], restY[i])
        sk.set(root, x, y, 0f, scale, scale)
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
        private const val DEG2RAD = (kotlin.math.PI / 180.0).toFloat()
    }
}
