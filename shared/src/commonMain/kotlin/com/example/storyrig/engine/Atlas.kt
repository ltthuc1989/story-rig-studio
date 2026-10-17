package com.example.storyrig.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

/** A rectangle of the atlas texture. */
class AtlasRegion(val name: String, val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Part images packed into one texture at load (shelf packing, tallest first), so every bitmap part of every
 * character draws from the same texture.
 */
class Atlas(val image: ImageBitmap, private val regions: HashMap<String, AtlasRegion>) {
    /** Decoded size in memory (ARGB_8888). */
    val bytes: Long get() = image.width.toLong() * image.height * 4
    val regionCount get() = regions.size

    operator fun get(name: String): AtlasRegion = regions[name] ?: error("no atlas region '$name'")

    companion object {
        fun pack(images: List<Pair<String, ImageBitmap>>, maxWidth: Int = 2048, pad: Int = 2): Atlas {
            val order = images.sortedByDescending { it.second.height }
            val placed = HashMap<String, AtlasRegion>()
            var x = pad; var y = pad; var shelf = 0; var width = 0
            for ((name, img) in order) {
                if (x + img.width + pad > maxWidth) { x = pad; y += shelf + pad; shelf = 0 }
                placed[name] = AtlasRegion(name, x, y, img.width, img.height)
                x += img.width + pad
                shelf = maxOf(shelf, img.height)
                width = maxOf(width, x)
            }
            val height = y + shelf + pad
            val out = ImageBitmap(width, height)
            val canvas = Canvas(out)
            val paint = Paint()
            for ((name, img) in images) {
                val r = placed.getValue(name)
                canvas.drawImage(img, Offset(r.x.toFloat(), r.y.toFloat()), paint)
            }
            return Atlas(out, placed)
        }
    }
}

/** An atlas region placed in a bone's local space: (dx, dy) is its top-left corner. */
class Sprite(val region: AtlasRegion, val dx: Int, val dy: Int)
