package com.example.storyrig

import com.example.storyrig.engine.PerfStats
import com.example.storyrig.engine.SceneConfig
import com.example.storyrig.scenes.KaiAssets
import com.example.storyrig.scenes.KaiScene
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Leg check for the `kai` scene, no drawing: plays each dance on every character for [SECONDS] and measures, per
 * frame, what makes legs look wrong. Desktop only, geometry not timing.
 *   ./gradlew :desktopApp:motionCheck --args="out=<file> char=kai,mia,leo,hana,bo"
 * - knee: on a foot that is on the ground, how far the knee sits off the hip-ankle line, over that line's length.
 *   From the front a standing knee kinked sideways is a crooked leg; a turned cut rightly shows some of a bend
 *   (limit [KNEE_FRONT] + [KNEE_SIDE] |sin yaw|). A lifted leg is free (a knee lift seen turned goes sideways);
 * - slide: how far a foot on the ground drifts while it stays down (character px);
 * - foot: how much a foot on the ground tilts from its standing angle (degrees).
 * Exit code 1 when a limit is broken.
 */
private const val SECONDS = 16
private const val KNEE_FRONT = .06f
private const val KNEE_SIDE = .3f
private const val SLIDE_MAX = 1.5f
private const val FOOT_MAX = 5f

fun main(args: Array<String>) {
    val map = args.mapNotNull { a -> a.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    val chars = (map["char"] ?: "kai,mia,leo,hana,bo").split(',')
    val clips = (map["dance"] ?: "wave,pump,march,idle").split(',')
    val lines = ArrayList<String>()
    var fail = false
    lines += "%-10s %-6s %6s %6s %7s %7s %6s".format("char", "dance", "yaw", "knee95", "kneeMax", "slide", "foot")
    for (clip in clips) {
        val cfg = SceneConfig(map + mapOf("scene" to "kai", "char" to chars.joinToString(","), "count" to chars.size.toString(), "dance" to clip))
        @Suppress("UNCHECKED_CAST")
        val cast = runBlocking { loadSceneAssets(cfg) } as List<List<KaiAssets>>
        val scene = KaiScene(cfg, PerfStats(), cast)
        val n = scene.actorCount
        val frames = SECONDS * 60
        // Per actor, per leg (0 near, 1 far), per frame: ankle x, y (character px), foot angle, knee ratio.
        val ax = Array(n) { Array(2) { FloatArray(frames) } }
        val ay = Array(n) { Array(2) { FloatArray(frames) } }
        val fa = Array(n) { Array(2) { FloatArray(frames) } }
        val kr = Array(n) { Array(2) { FloatArray(frames) } }
        // Stage units per character px, from the first frame (the root's own scale later includes the squash).
        val unit = FloatArray(n)
        var t = 0L
        for (f in 0 until frames) {
            scene.update(t)
            t += 16_666_667L
            for (i in 0 until n) {
                val k = scene.bodyRigOf(i)
                val w = k.sk.world
                if (f == 0) unit[i] = hypot(w[0], w[1])
                val scale = unit[i]
                for (leg in 0..1) {
                    val h = (if (leg == 0) k.hipN else k.hipF) * 6
                    val kn = (if (leg == 0) k.kneeN else k.kneeF) * 6
                    val an = (if (leg == 0) k.ankleN else k.ankleF) * 6
                    val dx = w[an + 4] - w[h + 4]; val dy = w[an + 5] - w[h + 5]
                    val len = hypot(dx, dy)
                    val cross = (w[kn + 4] - w[h + 4]) * dy - (w[kn + 5] - w[h + 5]) * dx
                    kr[i][leg][f] = if (len > 0f) abs(cross) / (len * len) else 0f
                    ax[i][leg][f] = w[an + 4] / scale; ay[i][leg][f] = w[an + 5] / scale
                    fa[i][leg][f] = atan2(w[an + 1], w[an]) * 57.29578f
                }
            }
        }
        val skip = 60 // first second: loading, first crossfade
        for (i in 0 until n) {
            val k = scene.bodyRigOf(i)
            val kneeLimit = KNEE_FRONT + KNEE_SIDE * abs(sin(k.yawDeg * PI.toFloat() / 180f))
            val debug = map["debug"] == chars[i]
            var kneeMax = 0f
            val knees = ArrayList<Float>()
            var slide = 0f
            var foot = 0f
            for (leg in 0..1) {
                val ground = (skip until frames).maxOf { ay[i][leg][it] }
                val down = BooleanArray(frames) { it >= skip && ground - ay[i][leg][it] < 1f }
                val standAngle = (skip until frames).filter { down[it] }.map { fa[i][leg][it] }.sorted()
                    .let { if (it.isEmpty()) 0f else it[it.size / 2] }
                var segStart = -1
                for (f in skip until frames) {
                    if (debug) println("dbg %s f=%d leg=%d x=%.2f lift=%.2f knee=%.3f".format(clip, f, leg, ax[i][leg][f], ground - ay[i][leg][f], kr[i][leg][f]))
                    if (down[f]) {
                        knees += kr[i][leg][f]; kneeMax = maxOf(kneeMax, kr[i][leg][f])
                        if (segStart < 0) segStart = f
                        slide = maxOf(slide, abs(ax[i][leg][f] - ax[i][leg][segStart]))
                        foot = maxOf(foot, abs(fa[i][leg][f] - standAngle))
                    } else segStart = -1
                }
            }
            knees.sort()
            val k95 = if (knees.isEmpty()) 0f else knees[(knees.size * 95) / 100]
            val bad = kneeMax > kneeLimit || slide > SLIDE_MAX || foot > FOOT_MAX
            if (bad) fail = true
            lines += "%-10s %-6s %6.1f %6.3f %7.3f %7.2f %6.1f%s".format(
                chars[i], clip, k.yawDeg, k95, kneeMax, slide, foot, if (bad) "  FAIL" else "",
            )
        }
    }
    lines += "limits: knee <= $KNEE_FRONT + $KNEE_SIDE |sin yaw| (feet on the ground), slide <= $SLIDE_MAX px, foot <= $FOOT_MAX deg"
    lines.forEach(::println)
    map["out"]?.let { File(it).writeText(lines.joinToString("\n") + "\n") }
    if (fail) kotlin.system.exitProcess(1)
}
