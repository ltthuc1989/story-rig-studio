package com.example.storyrig.tools

/**
 * Which piece of the legacy body.png sheet becomes which Kai part (`build` only). A piece is picked by a point on its sheet
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

    // Where the features sit in ref.png (measured on a 10 px grid), for `build`; cutref/cutauto find them while
    // erasing. The face sheets only give shapes; which piece is which is in assets/kai/gemini/face_map.csv.
    val refEyeNear = Box(477, 250, 530, 297)
    val refEyeFar = Box(581, 250, 622, 295)
    val refBrowNear = Box(479, 224, 517, 240)
    val refBrowFar = Box(590, 224, 620, 239)
    val refMouth = Box(525, 326, 581, 339)
    val refCheeks = Box(450, 290, 640, 330)

    /** Nose: not on any sheet, cut from ref.png around this box. */
    val noseBox = Box(552, 274, 583, 315)
}
