package com.example.storyrig.scenes

import com.example.storyrig.engine.Atlas
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
    /** Read + decode + atlas packing. */
    val loadMs: Float,
) {
    class PartRow(val name: String, val bone: Int, val x: Int, val y: Int, val w: Int, val h: Int, val drawn: Boolean)

    fun bone(name: String) = boneNames.indexOf(name).also { require(it >= 0) { "no bone $name" } }
    fun boneOrNull(name: String) = boneNames.indexOf(name)
    fun part(name: String) = parts.first { it.name == name }

    companion object {
        suspend fun load(): KaiAssets {
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
            return KaiAssets(atlas, names, parent, px, py, parts, (uptimeMillis() - t0).toFloat())
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
class KaiRig(private val a: KaiAssets, antiAlias: Boolean, phase: Float = 0f, clips: Array<Clip> = DanceClips.ALL) {
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
        val items = ArrayList<DrawItem>()
        val byName = HashMap<String, BitmapPart>()
        for (row in a.parts) if (row.drawn) {
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
