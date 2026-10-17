package com.example.storyrig.tools

/**
 * Cutting Kai straight from ref.png (`cutref`): a dozen points measured on ref.png on a 10 px grid
 * (ref.png = the rig-pose image made with prompts/1_rig_pose.txt and the mannequin template).
 * Later the Studio will let a person click these; everything else is automatic.
 * Kai faces right in three-quarter view, so his near side is the viewer's left.
 */
object KaiRefSpec {
    class Seg(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
    class Pt(val x: Float, val y: Float)

    // Cut lines between parts (each spans the full width of the joint, a few px past both edges).
    val neck = Seg(478f, 392f, 552f, 392f)
    val shoulderNear = Seg(370f, 416f, 432f, 510f)
    val shoulderFar = Seg(650f, 416f, 598f, 510f)
    val hipNear = Seg(412f, 690f, 523f, 690f)
    val hipFar = Seg(521f, 690f, 614f, 690f)
    /** Inner seam between the two shorts legs, from the crotch down. */
    val seam = Seg(519f, 684f, 517f, 754f)
    /** Where the near shoe's toe overlaps the far shoe's heel. */
    val shoes = Seg(527f, 845f, 531f, 945f)

    // Joints inside a limb (the cut runs across the limb there) and their approximate direction (parent to child).
    val elbowNear = Pt(325f, 504f); val wristNear = Pt(250f, 546f)
    val elbowFar = Pt(702f, 503f); val wristFar = Pt(780f, 546f)
    val kneeNear = Pt(462f, 752f); val kneeFar = Pt(556f, 752f)
    val ankleNear = Pt(458f, 870f); val ankleFar = Pt(558f, 870f)

    /**
     * Arm capsules: how far from the shoulder-elbow-wrist-hand axis an arm pixel can be (upper arm, forearm, hand;
     * ref.png px). The near sleeve is wide (perspective); the far one is narrow and touches the shirt.
     */
    val armNearRadii = floatArrayOf(44f, 32f, 52f)
    val armFarRadii = floatArrayOf(44f, 32f, 52f)

    // Bone pivots that are not on a cut midpoint.
    val root = Pt(515f, 660f)
    val waist = Pt(515f, 615f)

    // Seeds: one point inside each region.
    val seedHead = Pt(560f, 250f)
    /** A point on the hair, and where the hair piece pivots (middle of the scalp). */
    val seedHair = Pt(520f, 130f)
    val hairPivot = Pt(518f, 250f)
    val seedTorso = Pt(515f, 540f)
    val seedPelvis = Pt(515f, 655f)
    val seedArmNear = Pt(310f, 515f)
    val seedArmFar = Pt(720f, 515f)
    val seedThighNear = Pt(465f, 722f); val seedThighFar = Pt(565f, 722f)
    val seedLegNear = Pt(462f, 800f); val seedLegFar = Pt(555f, 800f)

    /** Rough areas of the painted features to erase; the exact boxes come from what is found inside. */
    val eyeNearArea = Box(470, 244, 538, 303)
    val eyeFarArea = Box(575, 244, 630, 301)
    val browNearArea = Box(472, 219, 524, 246)
    val browFarArea = Box(584, 219, 627, 245)
    val mouthArea = Box(518, 320, 588, 346)
}
