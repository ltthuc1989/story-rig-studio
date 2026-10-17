package com.example.storyrig.engine

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer

/** Options read from intent extras (`--es key value`), so benchmarks can flip them. */
class SceneConfig(private val args: Map<String, String>) {
    val scene = args["scene"] ?: "p3"
    /** Static-layer cache is plan B item 1 (SPIKE.md §7), so the main numbers run with it off. */
    val cacheStatic = flag("cache", false)
    /** "alpha": draw under-water parts with per-shape alpha; "layer": one saveLayer per group. */
    val waterMode = args["water"] ?: "alpha"
    /** "circle": drawCircle per particle; "points": one batched points call. */
    val particleMode = args["drops"] ?: "circle"
    val antiAlias = flag("aa", true)
    /** Character art in scenes that have a choice (`kai`): "bitmap" (rigid parts) or "mesh" (skinned limbs). */
    val art = args["art"] ?: "bitmap"
    /** Mesh grid (`art=mesh`): "adaptive" (big quads where only one bone pulls) or "uniform" (to compare). */
    val meshGrid = args["meshGrid"] ?: "adaptive"
    /** Target mesh cell size in screen px for the character's on-screen size; 0 = fixed cell in art pixels. */
    val meshPx = args["meshPx"]?.toFloatOrNull() ?: 6f
    val autoplay = flag("auto", true)
    val hud = flag("hud", true)
    val seed = args["seed"]?.toLongOrNull() ?: 7L
    /** "seat": Ben starts at the log instead of walking in, so benchmark taps hit known spots. */
    val start = args["start"] ?: "walk"
    /** Number of characters in scenes that take one (`kai`). */
    val count = (args["count"]?.toIntOrNull() ?: 5).coerceIn(1, 12)
    /** false: no per-frame redraw (allocation bisecting only). */
    val loop = flag("loop", true)
    /** "update": per frame only update(), no redraw; "draw": only redraw, no update() (allocation bisecting only). */
    val bisect = args["bisect"] ?: ""

    /** Any boolean extra, for scene-specific switches. */
    fun flagOf(key: String, def: Boolean) = flag(key, def)

    private fun flag(key: String, def: Boolean): Boolean = when (args[key]?.lowercase()) {
        null -> def
        "1", "true", "on", "yes" -> true
        else -> false
    }

    override fun toString() =
        "scene=$scene cache=$cacheStatic water=$waterMode drops=$particleMode aa=$antiAlias art=$art meshGrid=$meshGrid meshPx=$meshPx auto=$autoplay seed=$seed start=$start count=$count"
}

/**
 * Screen layout rule shared by every scene on every platform (Android phones/tablets, iPhone, iPad).
 *
 * Frame units: the safe area is 1920x1080 and is always fully visible; the bleed area is 2520x1440
 * (safe area centred) and is background art that fills whatever the screen shape adds:
 * 20:9 / 19.5:9 phones see extra bleed left and right, 4:3 / 16:10 tablets see extra bleed top and bottom.
 * Gameplay, touch targets and dialogue must sit inside the safe area; background layers must cover the bleed.
 *
 * A scene may author its art in its own units ([unitsPerFrame] art units per frame unit, e.g. 0.5 for the
 * 960x540 demo coordinates). Only screens outside ~4:3..21:9 (e.g. an unfolded foldable) show [outside] colour.
 */
class Stage(val unitsPerFrame: Float = 1f) {
    val safeW = SAFE_W * unitsPerFrame
    val safeH = SAFE_H * unitsPerFrame
    val bleedX = (BLEED_W - SAFE_W) / 2f * unitsPerFrame
    val bleedY = (BLEED_H - SAFE_H) / 2f * unitsPerFrame

    companion object {
        const val SAFE_W = 1920f
        const val SAFE_H = 1080f
        const val BLEED_W = 2520f
        const val BLEED_H = 1440f
    }
}

/** Fits a [Stage]'s safe area into the canvas (scale = min), centred; the rest of the screen shows bleed. */
class Viewport(val stage: Stage) {
    var scale = 1f
    var ox = 0f
    var oy = 0f
    var width = 0f
    var height = 0f

    /** Visible rectangle in scene units (part of the bleed area on non-16:9 screens). */
    var visLeft = 0f
    var visTop = 0f
    var visRight = 0f
    var visBottom = 0f

    fun fit(w: Float, h: Float) {
        width = w; height = h
        scale = minOf(w / stage.safeW, h / stage.safeH)
        ox = (w - stage.safeW * scale) / 2f
        oy = (h - stage.safeH * scale) / 2f
        visLeft = maxOf(-ox / scale, -stage.bleedX)
        visTop = maxOf(-oy / scale, -stage.bleedY)
        visRight = minOf((w - ox) / scale, stage.safeW + stage.bleedX)
        visBottom = minOf((h - oy) / scale, stage.safeH + stage.bleedY)
    }

    fun toDesignX(px: Float) = (px - ox) / scale
    fun toDesignY(py: Float) = (py - oy) / scale
}

interface Scene {
    val name: String
    val stats: RenderStats
    /** Number of GraphicsLayers the host must provide for static-layer caching. */
    val layerCount: Int
    var layers: Array<GraphicsLayer>?
    /** Complexity numbers that go into REPORT.md. */
    fun complexity(): String

    fun update(frameNanos: Long)
    fun draw(scope: DrawScope)

    fun pointerDown(px: Float, py: Float, uptime: Long)
    fun pointerMove(px: Float, py: Float)
    fun pointerUp(px: Float, py: Float)

    /** Dialogue line changed (who, English, Vietnamese). Called on events only, not per frame. */
    var onSubtitle: ((who: String, en: String, vi: String) -> Unit)?
    fun action(name: String) {}
}
