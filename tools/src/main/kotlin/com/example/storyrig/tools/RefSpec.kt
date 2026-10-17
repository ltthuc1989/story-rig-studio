package com.example.storyrig.tools

class Seg(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
class Pt(val x: Float, val y: Float)

/**
 * Everything `cutref`/`cutauto` need to cut one character out of its rig-pose image: cut lines, joints, seeds, feature
 * areas (ref.png pixels) and the few colour tests that separate parts drawn over each other. [KaiRefSpec] fills it by
 * hand for Kai; [autoSpec] measures it from the mannequin template and the character's shape.
 * The character faces right in three-quarter view, so its near side is the viewer's left.
 */
class RefSpec(
    // Cut lines between parts (each spans the full width of the joint, a few px past both edges).
    val neck: Seg,
    val shoulderNear: Seg,
    val shoulderFar: Seg,
    val hipNear: Seg,
    val hipFar: Seg,
    /** Inner seam between the two thighs, from the crotch down. */
    val seam: Seg,
    /** Where the two feet touch (near toe over far heel). */
    val shoes: Seg,
    // Joints inside a limb (the cut runs across the limb there).
    val elbowNear: Pt, val wristNear: Pt,
    val elbowFar: Pt, val wristFar: Pt,
    val kneeNear: Pt, val kneeFar: Pt,
    val ankleNear: Pt, val ankleFar: Pt,
    /** Arm capsules: how far an arm pixel can be from the shoulder-elbow-wrist-hand axis (upper arm, forearm, hand). */
    val armNearRadii: FloatArray,
    val armFarRadii: FloatArray,
    /** Highest row of the shin+foot regions (just above where the thigh's edge ends). */
    val legTop: Int,
    // Bone pivots that are not on a cut midpoint.
    val root: Pt,
    val waist: Pt,
    // Seeds: one point inside each region.
    val seedHead: Pt,
    val seedHair: Pt,
    val hairPivot: Pt,
    val seedTorso: Pt,
    val seedPelvis: Pt,
    val seedArmNear: Pt,
    val seedArmFar: Pt,
    val seedThighNear: Pt, val seedThighFar: Pt,
    val seedLegNear: Pt, val seedLegFar: Pt,
    /** Rough areas of the painted features to erase; the exact boxes come from what is found inside. */
    val eyeNearArea: Box,
    val eyeFarArea: Box,
    /** Null when the brows cannot be told apart (hidden by a fringe, joined to the hair): they stay painted. */
    val browNearArea: Box?,
    val browFarArea: Box?,
    val mouthArea: Box,
    val colours: CutColours,
    /** How far (RGB L1) a painted feature's colour is from the skin. */
    val featureContrast: Int = 110,
)

/** Colour tests used where parts touch with no cut line between them. */
class CutColours(
    /** Pixels an arm region may take (inside its capsule). */
    val arm: (Int) -> Boolean,
    /** Pixels a shin+foot region may take (it must stop at the thigh garment drawn over it). */
    val leg: (Int) -> Boolean,
    /** Pixels the pelvis region may take (it must stop at the shirt drawn over it). */
    val pelvis: (Int) -> Boolean,
    /** Skin of the face, for erasing painted features. */
    val skin: (Int) -> Boolean,
    /** Hair pixels, and the anti-aliased edge pixels kept with the hair when they touch it. */
    val hair: (Int) -> Boolean,
    val hairEdge: (Int) -> Boolean,
)
