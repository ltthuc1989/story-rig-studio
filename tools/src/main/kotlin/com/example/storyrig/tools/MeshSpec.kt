package com.example.storyrig.tools

import java.io.File
import kotlin.math.roundToInt

/*
 * mesh.csv: how the app skins the cut parts as meshes (`art=mesh`, engine/SoftMesh.kt), derived from the cut so a new
 * character needs no Kotlin. Two kinds of rows, ref.png pixels:
 *   group,<name>,<cell>,<part;part;..>,<bone;bone;..>
 *       parts drawn once into one image and skinned as one grid mesh (cell = grid cell size), following those bones
 *   influence,<bone>,<x0>,<y0>,<x1>,<y1>,<radius>,<blend>,<attachTo>,<band>
 *       where the bone pulls on vertices: a capsule (segment + radius); another bone fades in over [blend] px;
 *       with attachTo the bone's start stays on that parent across the joint, blended over [band] px
 *
 * The groups are the same for every humanoid cut (same part and bone names). The numbers come from the cut:
 *  - limb bones: segment from the pivot to the child's pivot (to the centre of its drawn parts at a limb's end);
 *  - torso: a vertical capsule on the body's centre line, radius = half the narrowest shirt width below the
 *    shoulders, from halfway between neck and shoulders down to where it meets the pelvis capsule at the shirt's hem;
 *  - pelvis: a short horizontal capsule halfway between waist and root, half as long as the hips are apart, radius
 *    just under half the hip spacing (so the hip bones win on the thighs); with a skirt bone the pelvis group follows
 *    `skirt` instead of `root`, through the same capsule;
 *  - blend, attach band and cell scale with the limbs' thickness (the elbows' half width w): limb blend 0.5 w,
 *    body (torso, root, skirt) blend 1.15 w, band 1.55 w, cell w / 3.25 (Kai: w = 26 px gives 13, 30, 40, 8). The
 *    engine measures a limb's blend along the bone, so 0.5 w is a joint crease about one limb width long and the
 *    segments either side stay straight (1.15 w measured straight to the bone blended limb outlines over most of
 *    their length and bowed them); the body capsules keep the wide, soft waist.
 */

/** What the cut measured for the mesh, ref.png pixels. */
class BodyMeasure(
    /** Half the narrowest width of the shirt between the shoulders and the hem. */
    val torsoHalfWidth: Float,
    /** First row below the shirt's hem on the body's centre line. */
    val hemY: Float,
    /** Mean half width of the arms at the elbows. */
    val limbHalfWidth: Float,
)

internal fun writeMesh(outDir: File, bones: List<Bone>, parts: Map<String, Part>, m: BodyMeasure) {
    val byName = bones.associateBy { it.name }
    fun b(n: String) = byName.getValue(n)
    val w = m.limbHalfWidth
    val blend = .5f * w
    val bodyBlend = 1.15f * w
    val band = 1.55f * w
    val cell = (w / 3.25f).roundToInt().coerceAtLeast(2)

    // Hands are not in the arm meshes: they stay rigid parts on the wrist bone, so turning a hand over (a mirror,
    // Clip.ROLL) doesn't pinch the wrist where its weights blend with the forearm's; the hand covers the wrist joint.
    val groups = listOf(
        "arm_far" to (listOf("arm_upper_far", "arm_fore_far") to listOf("shoulder_far", "elbow_far", "wrist_far")),
        "leg_far" to (listOf("shin_far", "thigh_far") to listOf("hip_far", "knee_far", "ankle_far")),
        "leg_near" to (listOf("shin_near", "thigh_near") to listOf("hip_near", "knee_near", "ankle_near")),
        // A skirt follows its own bone (the pelvis part is on it), not the root.
        "pelvis" to (listOf("pelvis") to listOf(if ("skirt" in byName) "skirt" else "root", "hip_near", "hip_far")),
        "torso" to (listOf("torso") to listOf("torso", "shoulder_near", "shoulder_far", "root")),
        "arm_near" to (listOf("arm_upper_near", "arm_fore_near") to listOf("shoulder_near", "elbow_near", "wrist_near")),
    )

    class Inf(val bone: String, val x0: Float, val y0: Float, val x1: Float, val y1: Float, val radius: Float = 0f, val attachTo: String? = null)
    val infs = ArrayList<Inf>()

    // Pelvis: halfway between waist and root, as long as half the hip spacing.
    val root = b("root"); val waist = b("torso")
    val hipSpacing = b("hip_far").px - b("hip_near").px
    val pelvisY = (waist.py + root.py) / 2
    val pelvisR = 0.45f * hipSpacing
    // Torso: its capsule and the pelvis's are equally far at the hem, so the shirt follows the torso down to its hem.
    val torsoR = m.torsoHalfWidth
    val shoulderY = (b("shoulder_near").py + b("shoulder_far").py) / 2
    val torsoTop = (b("head").py + shoulderY) / 2
    val torsoBottom = 2 * m.hemY - pelvisY + pelvisR - torsoR
    infs += Inf("torso", waist.px, torsoTop, waist.px, torsoBottom, torsoR)
    infs += Inf("root", root.px - hipSpacing / 4, pelvisY, root.px + hipSpacing / 4, pelvisY, pelvisR)
    // Skirt: the same capsule as the pelvis's (only the skirt's mesh uses it; the shirt and the hips keep the root).
    if ("skirt" in byName) infs += Inf("skirt", root.px - hipSpacing / 4, pelvisY, root.px + hipSpacing / 4, pelvisY, pelvisR)

    // Limb bones: pivot to the next joint, or to the centre of the bone's drawn parts at the limb's end.
    fun limb(bone: String, child: String?, attachTo: String? = null) {
        val p = b(bone)
        val end = if (child != null) b(child).let { it.px to it.py } else {
            val own = parts.values.filter { it.bone == bone && it.name in drawOrder }.map { it.piece }
            (own.sumOf { it.ox + it.w / 2.0 }.toFloat() / own.size) to (own.sumOf { it.oy + it.h / 2.0 }.toFloat() / own.size)
        }
        infs += Inf(bone, p.px, p.py, end.first, end.second, attachTo = attachTo)
    }
    for (side in listOf("near", "far")) {
        limb("shoulder_$side", "elbow_$side", attachTo = "torso"); limb("elbow_$side", "wrist_$side"); limb("wrist_$side", null)
    }
    for (side in listOf("near", "far")) {
        limb("hip_$side", "knee_$side", attachTo = "root"); limb("knee_$side", "ankle_$side"); limb("ankle_$side", null)
    }

    fun f(v: Float) = "%.1f".format(v)
    val sb = StringBuilder()
    sb.append("# Skinned meshes (art=mesh), ref.png pixels. Written by :tools (MeshSpec.kt); see there for how each number is derived.\n")
    sb.append("# group,name,cell,parts (;),bones (;)\n")
    for ((name, pb) in groups) sb.append("group,$name,$cell,${pb.first.joinToString(";")},${pb.second.joinToString(";")}\n")
    sb.append("# influence,bone,x0,y0,x1,y1,radius,blend,attachTo,band\n")
    for (i in infs) {
        sb.append("influence,${i.bone},${f(i.x0)},${f(i.y0)},${f(i.x1)},${f(i.y1)},${f(i.radius)},${f(if (i.radius > 0f) bodyBlend else blend)},${i.attachTo ?: ""},${if (i.attachTo != null) f(band) else "0"}\n")
    }
    File(outDir, "mesh.csv").writeText(sb.toString())
    println(
        "mesh: torso r=${f(torsoR)} ${f(torsoTop)}..${f(torsoBottom)} (hem ${f(m.hemY)}), pelvis r=${f(pelvisR)} y=${f(pelvisY)}, " +
            "limb half width ${f(w)} -> blend ${f(blend)} band ${f(band)} cell $cell",
    )
}
