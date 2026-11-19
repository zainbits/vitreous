package io.github.zainbits.vitreous.sample

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import io.github.zainbits.vitreous.VitreousDebug

/** Frame timing from the platform's own FrameMetrics (the same numbers `gfxinfo` reports). */
class FrameStats(private val window: Window) {

    data class Snapshot(
        val seconds: Float,
        val frames: Int,
        val fps: Float,
        val avgTotal: Float,
        val p95Total: Float,
        val avgGpu: Float,
        val p95Gpu: Float,
        val avgIssue: Float,
        val missedPct: Float,
        val sourceRecords: Long,
        val glassRecords: Long,
        val effectBuilds: Long,
    ) {
        fun line(): String = "frames=$frames fps=%.1f total=%.2f/%.2fms gpu=%.2f/%.2fms issue=%.2fms missed=%.1f%% src=%d glass=%d fx=%d"
            .format(fps, avgTotal, p95Total, avgGpu, p95Gpu, avgIssue, missedPct, sourceRecords, glassRecords, effectBuilds)
    }

    private class Acc {
        val total = FloatArray(CAP)
        val gpu = FloatArray(CAP)
        val issue = FloatArray(CAP)
        var n = 0
        var missed = 0
        var startNs = System.nanoTime()
        var src = 0L
        var glass = 0L
        var fx = 0L
    }

    private val thread = HandlerThread("frame-stats").apply { start() }
    private val handler = Handler(thread.looper)
    private val lock = Any()
    private val accs = arrayOf(Acc(), Acc())

    private val listener = Window.OnFrameMetricsAvailableListener { _, m, _ ->
        if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        val total = m.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6f
        val gpu = m.getMetric(FrameMetrics.GPU_DURATION) / 1e6f
        val issue = m.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) / 1e6f
        val late = m.getMetric(FrameMetrics.TOTAL_DURATION) > m.getMetric(FrameMetrics.DEADLINE)
        synchronized(lock) {
            for (a in accs) {
                if (a.n < CAP) {
                    a.total[a.n] = total
                    a.gpu[a.n] = gpu
                    a.issue[a.n] = issue
                    a.n++
                    if (late) a.missed++
                }
            }
        }
    }

    fun start() = window.addOnFrameMetricsAvailableListener(listener, handler)

    fun stop() {
        window.removeOnFrameMetricsAvailableListener(listener)
        thread.quitSafely()
    }

    /** Stats since the last call for this accumulator (HUD = 0, bench = 1), then resets it. */
    fun drain(which: Int): Snapshot {
        val a = accs[which]
        val t: FloatArray
        val g: FloatArray
        val i: FloatArray
        val n: Int
        val missed: Int
        val start: Long
        synchronized(lock) {
            n = a.n
            t = a.total.copyOf(n)
            g = a.gpu.copyOf(n)
            i = a.issue.copyOf(n)
            missed = a.missed
            start = a.startNs
            a.n = 0
            a.missed = 0
            a.startNs = System.nanoTime()
        }
        // Debug counters are UI-thread fields; drain() is called on the UI thread.
        val src = VitreousDebug.sourceRecords - a.src
        val glass = VitreousDebug.glassRecords - a.glass
        val fx = VitreousDebug.effectBuilds - a.fx
        a.src = VitreousDebug.sourceRecords
        a.glass = VitreousDebug.glassRecords
        a.fx = VitreousDebug.effectBuilds
        val secs = ((System.nanoTime() - start) / 1e9f).coerceAtLeast(0.001f)
        return Snapshot(
            seconds = secs,
            frames = n,
            fps = n / secs,
            avgTotal = t.avg(),
            p95Total = t.p95(),
            avgGpu = g.avg(),
            p95Gpu = g.p95(),
            avgIssue = i.avg(),
            missedPct = if (n == 0) 0f else 100f * missed / n,
            sourceRecords = src,
            glassRecords = glass,
            effectBuilds = fx,
        )
    }

    private fun FloatArray.avg(): Float = if (isEmpty()) 0f else sum() / size
    private fun FloatArray.p95(): Float {
        if (isEmpty()) return 0f
        val s = sortedArray()
        return s[((s.size - 1) * 0.95f).toInt()]
    }

    companion object {
        private const val CAP = 8192
    }
}
