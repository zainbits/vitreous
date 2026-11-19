package io.github.zainbits.vitreous.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Showcase and test bench. Launch options (all optional) for scripted runs from adb:
 *
 *   adb shell am start -S -n io.github.zainbits.vitreous.sample.bench/io.github.zainbits.vitreous.sample.MainActivity \
 *     --es mode full|noblur|solid|none --ef clarity 0.35 --ei extra 4 --ei bench 12 --ei speed 2400
 *
 * A bench run auto-scrolls the list at a fixed speed and logs one `VitreousBench` line.
 * Every second the HUD numbers are also logged under `VitreousPerf`.
 */
class MainActivity : ComponentActivity() {
    private lateinit var stats: FrameStats

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        stats = FrameStats(window).also { it.start() }
        val i = intent
        io.github.zainbits.vitreous.VitreousDebug.blurDownsample = i.getIntExtra("ds", 0)
        io.github.zainbits.vitreous.VitreousDebug.blurMethod = i.getIntExtra("blur", 1)
        val config = SampleConfig(
            mode = SampleMode.parse(i.getStringExtra("mode")),
            clarity = i.getFloatExtra("clarity", 0.35f),
            extraGlass = i.getIntExtra("extra", 0),
            benchSeconds = i.getIntExtra("bench", 0),
            speedPxPerSec = i.getIntExtra("speed", 2400),
        )
        setContent { SampleApp(stats, config) }
    }

    override fun onDestroy() {
        stats.stop()
        super.onDestroy()
    }
}
