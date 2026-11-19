package io.github.zainbits.vitreous

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * The mode the device can afford right now: [preferred] normally, [GlassMode.Solid] while
 * battery saver is on or the phone is hot enough to throttle (thermal status SEVERE or worse).
 * Updates live.
 */
@Composable
public fun rememberAdaptiveGlassMode(preferred: GlassMode = GlassMode.Full): GlassMode {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var saver by remember { mutableStateOf(power.isPowerSaveMode) }
    var hot by remember { mutableStateOf(power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) }
    DisposableEffect(power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                saver = power.isPowerSaveMode
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        val thermal = PowerManager.OnThermalStatusChangedListener { status ->
            hot = status >= PowerManager.THERMAL_STATUS_SEVERE
        }
        power.addThermalStatusListener(context.mainExecutor, thermal)
        saver = power.isPowerSaveMode
        onDispose {
            context.unregisterReceiver(receiver)
            power.removeThermalStatusListener(thermal)
        }
    }
    return if (saver || hot) GlassMode.Solid else preferred
}

/**
 * Sets the look and mode for every glass surface inside [content].
 *
 * @param clarity the iOS 27-style slider: 0 = ultra-clear, 1 = fully tinted.
 */
@Composable
public fun ProvideGlass(
    clarity: Float,
    dark: Boolean,
    mode: GlassMode = rememberAdaptiveGlassMode(),
    content: @Composable () -> Unit,
) {
    val style = remember(clarity, dark) { GlassStyle.forClarity(clarity, dark) }
    CompositionLocalProvider(
        LocalGlassStyle provides style,
        LocalGlassMode provides mode,
        content = content,
    )
}
