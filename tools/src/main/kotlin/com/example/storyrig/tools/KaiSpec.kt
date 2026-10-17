package com.example.storyrig.tools

/**
 * Which piece of which Gemini sheet becomes which Kai part. A piece is picked by a point on its sheet
 * (the separate piece nearest to that point, see the `scan` overview), so the data survives changes in how pieces
 * are numbered. Kai faces right in three-quarter view, so his near side is the viewer's left.
 */
object KaiSpec {
    class Pt(val x: Int, val y: Int)

    // body.png
    val HEAD = Pt(211, 173)
    val HAIR = Pt(516, 160)
    val TORSO = Pt(484, 487) // includes neck stub, star patch and the backpack seen past his back
    val PELVIS = Pt(515, 682)
    val ARM_NEAR = Pt(114, 526)
    val ARM_FAR = Pt(900, 525)
    val LEG_NEAR = Pt(158, 795)
    val LEG_FAR = Pt(860, 795)

    // Where the features sit in ref.png (measured on a 10 px grid). The face sheets only give shapes.
    val refEyeNear = Box(477, 250, 530, 297)
    val refEyeFar = Box(581, 250, 622, 295)
    val refBrowNear = Box(479, 224, 517, 240)
    val refBrowFar = Box(590, 224, 620, 239)
    val refMouth = Box(525, 326, 581, 339)
    val refCheeks = Box(450, 290, 640, 330)

    /** Nose: not on any sheet, cut from ref.png around this box. */
    val noseBox = Box(552, 274, 583, 315)

    /**
     * Face pieces: name, sheet, the point(s) picking its piece(s), and either the ref.png box it is centred on,
     * or the part whose centre it shares.
     */
    class FacePart(val name: String, val sheet: String, val at: List<Pt>, val refBox: Box? = null, val like: String? = null)

    private fun p(x: Int, y: Int) = Pt(x, y)

    val face = listOf(
        // Eyes and brows from face.png, columns 3 (near) and 4 (far, narrower).
        FacePart("eye_near_open", "face", listOf(p(763, 165)), refBox = refEyeNear),
        FacePart("eye_far_open", "face", listOf(p(904, 161)), refBox = refEyeFar),
        FacePart("eye_near_half", "face", listOf(p(764, 338)), like = "eye_near_open"),
        FacePart("eye_far_half", "face", listOf(p(904, 336)), like = "eye_far_open"),
        FacePart("eye_near_happy", "face", listOf(p(765, 502)), like = "eye_near_open"),
        FacePart("eye_far_happy", "face", listOf(p(903, 503)), like = "eye_far_open"),
        FacePart("eye_near_wide", "face", listOf(p(764, 659)), like = "eye_near_open"),
        FacePart("eye_far_wide", "face", listOf(p(904, 658)), like = "eye_far_open"),
        FacePart("brow_near", "face", listOf(p(761, 97)), refBox = refBrowNear),
        FacePart("brow_far", "face", listOf(p(906, 98)), refBox = refBrowFar),
        FacePart("brow_near_raised", "face", listOf(p(759, 790)), like = "brow_near"),
        FacePart("brow_far_raised", "face", listOf(p(905, 786)), like = "brow_far"),
        // Mouths from face_extra.png (no skin patch around them); the small pink bit under each is its lower lip.
        FacePart("mouth_smile", "face_extra", listOf(p(181, 95), p(183, 139)), refBox = refMouth),
        FacePart("mouth_e", "face_extra", listOf(p(181, 238)), like = "mouth_smile"),
        FacePart("mouth_a", "face_extra", listOf(p(180, 390), p(181, 454)), like = "mouth_smile"),
        FacePart("mouth_o", "face_extra", listOf(p(180, 553), p(181, 596)), like = "mouth_smile"),
        FacePart("mouth_s", "face_extra", listOf(p(184, 713), p(185, 778)), like = "mouth_smile"),
        FacePart("mouth_laugh", "face_extra", listOf(p(185, 900)), like = "mouth_smile"),
        FacePart("mouth_sad", "face_extra", listOf(p(512, 931)), like = "mouth_smile"),
    )

    /** Scale per face sheet: this part's width must equal its ref.png box width. */
    val faceScaleBy = mapOf("face" to "eye_near_open", "face_extra" to "mouth_smile")
}
