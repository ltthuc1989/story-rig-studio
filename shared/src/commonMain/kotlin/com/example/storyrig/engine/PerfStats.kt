package com.example.storyrig.engine

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Frame and input-latency bookkeeping for the HUD and logcat. On Android, frames come from
 * JankStats; on desktop from frame-clock intervals. Arrays are fixed size.
 */
class PerfStats {
    private val ring = FloatArray(RING)
    private var ringCount = 0
    private var ringPos = 0
    private val scratch = FloatArray(RING)

    var warmupEndMs = 0L
    var frames = 0
        private set
    var lateFrames = 0
        private set
    var jankFrames = 0
        private set
    var worstMs = 0f
        private set
    var loadMs = 0f

    private val latency = FloatArray(LAT)
    var latencyCount = 0
        private set
    private val latScratch = FloatArray(LAT)

    var allocBaseBytes = -1L
    var allocBaseMs = 0L

    var refreshHz = 60f
    val budgetMs get() = 1000f / refreshHz

    fun onFrame(durationMs: Float, isJank: Boolean, nowMs: Long) {
        ring[ringPos] = durationMs
        ringPos = (ringPos + 1) % RING
        if (ringCount < RING) ringCount++
        if (nowMs < warmupEndMs) return
        frames++
        if (durationMs > budgetMs) lateFrames++
        if (isJank) jankFrames++
        worstMs = max(worstMs, durationMs)
    }

    fun onLatency(ms: Float) {
        latency[latencyCount % LAT] = ms
        latencyCount++
    }

    fun resetWindow(nowMs: Long, warmupMs: Long) {
        warmupEndMs = nowMs + warmupMs
        frames = 0; lateFrames = 0; jankFrames = 0; worstMs = 0f; latencyCount = 0
        allocBaseBytes = runtimeAllocatedBytes(); allocBaseMs = nowMs
    }

    /** Percentile of the last ~10 s of frame durations. */
    fun recentPercentile(p: Float): Float {
        val n = ringCount
        if (n == 0) return 0f
        ring.copyInto(scratch, 0, 0, n)
        scratch.sort(0, n)
        return scratch[((n - 1) * p).roundToInt()]
    }

    fun latencyPercentile(p: Float): Float {
        val n = minOf(latencyCount, LAT)
        if (n == 0) return 0f
        latency.copyInto(latScratch, 0, 0, n)
        latScratch.sort(0, n)
        return latScratch[((n - 1) * p).roundToInt()]
    }

    fun latencyMax(): Float {
        val n = minOf(latencyCount, LAT)
        var m = 0f
        for (i in 0 until n) m = max(m, latency[i])
        return m
    }

    private var intervalBytes = -1L
    private var intervalMs = 0L

    /** KB/s allocated since the previous call (steady-state view; first call measures from window start). */
    fun allocKbPerSecInterval(nowMs: Long): Float {
        val b = runtimeAllocatedBytes()
        if (intervalBytes < 0) { intervalBytes = allocBaseBytes; intervalMs = allocBaseMs }
        val r = if (b < 0 || intervalBytes < 0 || nowMs <= intervalMs) -1f
        else (b - intervalBytes) / 1024f / ((nowMs - intervalMs) / 1000f)
        intervalBytes = b; intervalMs = nowMs
        return r
    }

    /** KB allocated per second since the window started (whole process, managed heap). */
    fun allocKbPerSec(nowMs: Long): Float {
        val b = runtimeAllocatedBytes()
        if (b < 0 || allocBaseBytes < 0 || nowMs <= allocBaseMs) return -1f
        return (b - allocBaseBytes) / 1024f / ((nowMs - allocBaseMs) / 1000f)
    }

    companion object {
        const val RING = 600
        const val LAT = 256
        val shared = PerfStats()
    }
}

fun Float.f1(): String {
    val r = (this * 10f).roundToInt()
    val sign = if (r < 0) "-" else ""
    val a = kotlin.math.abs(r)
    return "$sign${a / 10}.${a % 10}"
}
