package com.example.storyrig

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storyrig.engine.PerfStats
import com.example.storyrig.engine.Scene
import com.example.storyrig.engine.SceneConfig
import com.example.storyrig.engine.exposeTestTags
import com.example.storyrig.engine.f1
import com.example.storyrig.engine.logInfo
import com.example.storyrig.engine.uptimeMillis
import com.example.storyrig.scenes.KaiAssets
import com.example.storyrig.scenes.KaiScene
import com.example.storyrig.scenes.LakeScene
import kotlinx.coroutines.delay

fun createScene(cfg: SceneConfig, perf: PerfStats, assets: Any?): Scene = when (cfg.scene) {
    "kai" -> KaiScene(cfg, perf, assets as KaiAssets)
    // p1, p2, p4 arrive on day 2-3
    else -> LakeScene(cfg, perf)
}

/** Assets a scene needs before it can be built (image decoding is suspend). Vector scenes need none. */
suspend fun loadSceneAssets(cfg: SceneConfig): Any? = when (cfg.scene) {
    "kai" -> KaiAssets.load()
    else -> null
}

@Composable
fun App(args: Map<String, String> = emptyMap(), perf: PerfStats = PerfStats.shared) {
    val cfg = remember { SceneConfig(args) }
    var loaded by remember { mutableStateOf(false) }
    var assets by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(cfg) {
        assets = loadSceneAssets(cfg)
        loaded = true
    }
    if (!loaded) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0F1719)))
        return
    }
    SceneHost(cfg, perf, assets)
}

@Composable
private fun SceneHost(cfg: SceneConfig, perf: PerfStats, assets: Any?) {
    val scene = remember { createScene(cfg, perf, assets) }
    val graphics = LocalGraphicsContext.current
    DisposableEffect(scene) {
        val ls = Array(scene.layerCount) { graphics.createGraphicsLayer() }
        scene.layers = ls
        perf.resetWindow(uptimeMillis(), WARMUP_MS)
        logInfo("scene ${scene.name}: ${scene.complexity()}")
        onDispose {
            scene.layers = null
            ls.forEach { graphics.releaseGraphicsLayer(it) }
        }
    }
    val invalidator = remember { DrawInvalidator() }
    val onFrame = remember(scene) {
        { t: Long ->
            if (cfg.bisect != "draw") scene.update(t)
            if (cfg.bisect != "update") invalidator.invalidate()
        }
    }
    // `--es loop 0`: no per-frame redraw, to tell framework allocations from the scene's (measurement only)
    if (cfg.loop) com.example.storyrig.engine.FrameLoop(onFrame)

    var sub by remember { mutableStateOf(Triple("", "", "")) }
    DisposableEffect(scene) {
        scene.onSubtitle = { who, en, vi -> sub = Triple(who, en, vi) }
        onDispose { scene.onSubtitle = null }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0F1719)).exposeTestTags()) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("stage")
                .then(SceneDrawElement(scene, invalidator))
                .pointerInput(scene) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        scene.pointerDown(down.position.x, down.position.y, down.uptimeMillis)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                scene.pointerUp(ch.position.x, ch.position.y)
                                break
                            }
                            scene.pointerMove(ch.position.x, ch.position.y)
                            ch.consume()
                        }
                    }
                },
        )
        StatsLogger(scene, perf)
        // UI is anchored to the real screen edges, clear of notches / Dynamic Island / home indicator
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (sub.second.isNotEmpty()) Subtitle(sub, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
            if (cfg.hud) Hud(scene, perf, cfg, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
    }
}

@Composable
private fun Subtitle(sub: Triple<String, String, String>, modifier: Modifier) {
    Column(
        modifier.background(Color(0xCCFFFFFF), RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(sub.first.uppercase(), fontSize = 10.sp, color = Color(0xFF5A6B76), fontWeight = FontWeight.SemiBold)
        Text(sub.second, fontSize = 18.sp, color = Color(0xFF1C2A33), fontWeight = FontWeight.SemiBold)
        if (sub.third.isNotEmpty()) Text(sub.third, fontSize = 12.sp, color = Color(0xFF5A6B76))
    }
}

/** Measurement HUD. Text refreshes twice a second; tap it to collapse. Logs a summary every 5 s. */
@Composable
private fun Hud(scene: Scene, perf: PerfStats, cfg: SceneConfig, modifier: Modifier) {
    var open by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(scene) {
        while (true) {
            delay(500)
            val now = uptimeMillis()
            val s = scene.stats
            val late = if (perf.frames > 0) perf.lateFrames * 100f / perf.frames else 0f
            val alloc = perf.allocKbPerSec(now)
            text = "${scene.name}  ${perf.refreshHz.toInt()} Hz\n" +
                "frame P50 ${perf.recentPercentile(.5f).f1()}  P90 ${perf.recentPercentile(.9f).f1()}  P99 ${perf.recentPercentile(.99f).f1()} ms\n" +
                "late ${late.f1()}%  jank ${perf.jankFrames}/${perf.frames}  worst ${perf.worstMs.f1()} ms (after 2 s)\n" +
                "draws ${s.lastDraws}  images ${s.lastImages}  strokes ${s.lastStrokes}  soft pts ${s.lastSoftPoints}  layers ${s.lastLayersDrawn}  saveLayer ${s.lastSaveLayers}  particles ${s.lastParticles}\n" +
                "tap→react P50 ${perf.latencyPercentile(.5f).f1()}  max ${perf.latencyMax().f1()} ms (n=${perf.latencyCount})\n" +
                "load ${perf.loadMs.f1()} ms  alloc ${if (alloc < 0) "n/a" else alloc.f1() + " KB/s"}\n" +
                "cache=${cfg.cacheStatic} water=${cfg.waterMode} drops=${cfg.particleMode} aa=${cfg.antiAlias}"
        }
    }
    Column(modifier) {
        Box(
            Modifier.background(Color(0xB0000000), RoundedCornerShape(8.dp)).clickable { open = !open }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(if (open) text else "HUD", color = Color.White, fontSize = 11.sp, lineHeight = 13.sp)
        }
        if (open) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((label, act) in ACTIONS) {
                    Text(
                        label,
                        Modifier.background(Color(0xD0FFFFFF), RoundedCornerShape(12.dp)).clickable { scene.action(act) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp, color = Color(0xFF1C2A33),
                    )
                }
            }
        }
    }
}

/** Logcat summary every 5 s, independent of the HUD (so hud=0 runs still report). */
@Composable
private fun StatsLogger(scene: Scene, perf: PerfStats) {
    LaunchedEffect(scene) {
        while (true) {
            delay(5000)
            val s = scene.stats
            val late = if (perf.frames > 0) perf.lateFrames * 100f / perf.frames else 0f
            val alloc = perf.allocKbPerSecInterval(uptimeMillis())
            logInfo(
                "STATS frames=${perf.frames} late%=${late.f1()} jank=${perf.jankFrames} worst=${perf.worstMs.f1()} " +
                    "recentP50=${perf.recentPercentile(.5f).f1()} recentP90=${perf.recentPercentile(.9f).f1()} " +
                    "tapP50=${perf.latencyPercentile(.5f).f1()} tapMax=${perf.latencyMax().f1()} taps=${perf.latencyCount} " +
                    "allocKBs(last5s)=${alloc.f1()} draws=${s.lastDraws} images=${s.lastImages} strokes=${s.lastStrokes} soft=${s.lastSoftPoints} " +
                    "layers=${s.lastLayersDrawn} saveLayer=${s.lastSaveLayers} particles=${s.lastParticles}",
            )
        }
    }
}

private val ACTIONS = listOf(
    "Ra hồ" to "go", "Ngồi" to "sit", "Đứng" to "stand", "Vẫy" to "wave",
    "Chỉ vịt" to "point", "Nhảy" to "jump", "Ếch" to "frog", "Về" to "home",
)

const val WARMUP_MS = 2000L

/** Lets the frame loop invalidate only the scene's draw node, without snapshot state writes. */
class DrawInvalidator {
    internal var node: SceneDrawNode? = null
    fun invalidate() {
        node?.invalidateDraw()
    }
}

private class SceneDrawElement(val scene: Scene, val invalidator: DrawInvalidator) : ModifierNodeElement<SceneDrawNode>() {
    override fun create() = SceneDrawNode(scene, invalidator)
    override fun update(node: SceneDrawNode) {
        node.scene = scene
        node.invalidator = invalidator
        invalidator.node = node
    }

    override fun equals(other: Any?) = other is SceneDrawElement && other.scene === scene && other.invalidator === invalidator
    override fun hashCode() = scene.hashCode() * 31 + invalidator.hashCode()
}

internal class SceneDrawNode(var scene: Scene, var invalidator: DrawInvalidator) : Modifier.Node(), DrawModifierNode {
    override fun onAttach() {
        invalidator.node = this
    }

    override fun onDetach() {
        if (invalidator.node === this) invalidator.node = null
    }

    override fun ContentDrawScope.draw() {
        scene.draw(this)
    }
}
