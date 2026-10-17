package com.example.storyrig

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import java.io.File

/**
 * Renders the app off screen to PNGs, to look at a scene without a window. Desktop only, no numbers.
 *   ./gradlew :desktopApp:run -PmainClass=com.example.storyrig.SnapshotKt --args="out=/tmp/kai scene=kai"
 * Writes frames at 1, 2 and 3.3 s of scene time.
 */
fun main(args: Array<String>) {
    val map = args.mapNotNull { a -> a.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    val out = File(map["out"] ?: "snapshots").also { it.mkdirs() }
    val w = map["w"]?.toInt() ?: 1600
    val h = map["h"]?.toInt() ?: 900
    val shots = setOf(60, 120, 200)
    // strip=<from>: 8 frames, 4 apart (one 500 ms beat at 60 fps is 30 frames), in a 4x2 grid
    val strip = map["strip"]?.toInt()
    ImageComposeScene(w, h).use { scene ->
        scene.setContent { App(map + ("hud" to (map["hud"] ?: "0"))) }
        var frame = 0
        var t = 0L
        // Let asset loading (Dispatchers.IO) finish before counting frames.
        repeat(200) { scene.render(t); Thread.sleep(5); t += 16_666_667L }
        // frames=<n>: every frame as frame_0000.png.. for ffmpeg
        val seq = map["frames"]?.toInt()
        if (seq != null) {
            repeat(seq) { i ->
                val data = scene.render(t).encodeToData() ?: error("encode failed")
                File(out, "frame_%04d.png".format(i)).writeBytes(data.bytes)
                t += 16_666_667L
            }
            println("snapshots in $out")
            return@use
        }
        if (strip != null) {
            val tiles = ArrayList<org.jetbrains.skia.Image>()
            while (tiles.size < 8) {
                val img = scene.render(t)
                if (frame >= strip && (frame - strip) % 4 == 0) tiles += img
                t += 16_666_667L; frame++
            }
            val tw = w / 2; val th = h / 2
            val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(tw * 4, th * 2)
            for ((i, img) in tiles.withIndex()) {
                surface.canvas.drawImageRect(img, org.jetbrains.skia.Rect.makeXYWH(((i % 4) * tw).toFloat(), ((i / 4) * th).toFloat(), tw.toFloat(), th.toFloat()))
            }
            val data = surface.makeImageSnapshot().encodeToData() ?: error("encode failed")
            File(out, "${map["scene"] ?: "p3"}_strip_${map["motion"] ?: "1"}.png").writeBytes(data.bytes)
            println("snapshots in $out")
            return@use
        }
        while (frame <= shots.max()) {
            val img = scene.render(t)
            if (frame in shots) {
                val data = img.encodeToData() ?: error("encode failed")
                File(out, "${map["scene"] ?: "p3"}_$frame.png").writeBytes(data.bytes)
            }
            t += 16_666_667L
            frame++
        }
    }
    println("snapshots in $out")
}
