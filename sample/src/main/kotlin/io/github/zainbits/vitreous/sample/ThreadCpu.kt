package io.github.zainbits.vitreous.sample

import java.io.File

/** CPU time per thread of this process, from /proc/self/task (clock ticks -> ms). */
object ThreadCpu {
    private const val TICK_MS = 10.0 // USER_HZ = 100 on Android

    fun snapshot(): Map<String, Double> {
        val out = HashMap<String, Double>()
        File("/proc/self/task").listFiles()?.forEach { t ->
            val stat = runCatching { File(t, "stat").readText() }.getOrNull() ?: return@forEach
            val name = stat.substring(stat.indexOf('(') + 1, stat.lastIndexOf(')'))
            val f = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
            val ticks = f[11].toLong() + f[12].toLong() // utime, stime
            val key = when {
                t.name == android.os.Process.myPid().toString() -> "main"
                name == "RenderThread" -> "render"
                else -> "other"
            }
            out[key] = (out[key] ?: 0.0) + ticks * TICK_MS
        }
        return out
    }

    /** "main=..ms/s render=..ms/s other=..ms/s" between two snapshots. */
    fun rate(a: Map<String, Double>, b: Map<String, Double>, seconds: Float): String =
        listOf("main", "render", "other").joinToString(" ") { k ->
            "cpu_$k=%.0f".format(((b[k] ?: 0.0) - (a[k] ?: 0.0)) / seconds)
        } + "ms/s"
}
