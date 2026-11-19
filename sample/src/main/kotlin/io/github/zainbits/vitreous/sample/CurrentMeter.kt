package io.github.zainbits.vitreous.sample

import android.content.Context
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import kotlin.math.abs

/** Average battery current while running on battery (no USB), sampled every 200 ms. */
class CurrentMeter(context: Context) {
    private val battery = context.getSystemService(BatteryManager::class.java)
    private val thread = HandlerThread("current-meter").apply { start() }
    private val handler = Handler(thread.looper)
    @Volatile private var sum = 0.0
    @Volatile private var n = 0
    @Volatile private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val ua = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (ua != Int.MIN_VALUE) {
                sum += abs(ua.toDouble())
                n++
            }
            handler.postDelayed(this, 200)
        }
    }

    fun start() {
        running = true
        handler.post(tick)
    }

    /** Stops and returns the average in mA (the property is in microamps on most phones). */
    fun stop(): Double {
        running = false
        thread.quitSafely()
        if (n == 0) return 0.0
        val avg = sum / n
        return if (avg > 20_000) avg / 1000.0 else avg
    }
}
