package io.github.zainbits.vitreous.sample

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.heightIn

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zainbits.cohesion.GlassBackdrop
import io.github.zainbits.vitreous.GlassGroup
import io.github.zainbits.vitreous.GlassIconButton
import io.github.zainbits.cohesion.GlassMode
import io.github.zainbits.cohesion.GlassStyle
import io.github.zainbits.cohesion.LocalGlassStyle
import io.github.zainbits.cohesion.GlassPullSpec
import io.github.zainbits.cohesion.LocalGlassPullSpec
import io.github.zainbits.vitreous.GlassShape
import io.github.zainbits.vitreous.GlassSurface
import io.github.zainbits.vitreous.GlassTopBar
import io.github.zainbits.cohesion.ProvideGlass
import io.github.zainbits.cohesion.glassSource
import io.github.zainbits.cohesion.rememberAdaptiveGlassMode
import io.github.zainbits.cohesion.rememberGlassBackdrop
import kotlin.math.abs

enum class SampleMode(val label: String) {
    Auto("Auto"), Full("Full"), NoBlur("No blur"), Solid("Solid"), None("Baseline (test)");

    companion object {
        fun parse(s: String?): SampleMode = when (s?.lowercase()) {
            "full" -> Full
            "noblur" -> NoBlur
            "solid" -> Solid
            "none" -> None
            else -> Auto
        }
    }
}

data class SampleConfig(
    val mode: SampleMode,
    val clarity: Float,
    val extraGlass: Int,
    val benchSeconds: Int,
    val speedPxPerSec: Int,
    /** "light" or "dark" to override the phone's theme; null = follow it. */
    val theme: String? = null,
    /** Plain backdrop (true) or the chat (false); null = plain unless benchmarking. */
    val plain: Boolean? = null,
)

private const val TAG_PERF = "VitreousPerf"
private const val TAG_BENCH = "VitreousBench"

@Composable
fun SampleApp(stats: FrameStats, config: SampleConfig) {
    val systemDark = isSystemInDarkTheme()
    var themeOverride by remember { mutableStateOf(config.theme) }
    val dark = when (themeOverride) { "light" -> false; "dark" -> true; else -> systemDark }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        var mode by remember { mutableStateOf(config.mode) }
        var clarity by remember { mutableFloatStateOf(config.clarity) }
        var extra by remember { mutableIntStateOf(config.extraGlass) }
        var showPanel by remember { mutableStateOf(false) }
        var blurMethod by remember { mutableIntStateOf(io.github.zainbits.cohesion.CohesionDebug.blurMethod) }
        var showHud by remember { mutableStateOf(true) }
        var pullSpec by remember { mutableStateOf(GlassPullSpec()) }
        var look by remember { mutableStateOf(LookTune()) }
        // Plain white (light) or black (dark) behind a big composer, to judge the glass itself.
        // Benchmarks scroll the chat, so they start on it.
        var plain by remember { mutableStateOf(config.plain ?: (config.benchSeconds == 0)) }
        var hud by remember { mutableStateOf("") }
        var benchRun by remember { mutableIntStateOf(if (config.benchSeconds > 0) 1 else 0) }
        var benchResult by remember { mutableStateOf("") }
        val listState = rememberLazyListState()
        val context = androidx.compose.ui.platform.LocalContext.current
        val backdrop = rememberGlassBackdrop()

        val adaptive = rememberAdaptiveGlassMode()
        val glassMode = when (mode) {
            SampleMode.Auto -> adaptive
            SampleMode.Full -> GlassMode.Full
            SampleMode.NoBlur -> GlassMode.NoBlur
            SampleMode.Solid, SampleMode.None -> GlassMode.Solid
        }

        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val s = stats.drain(0)
                hud = s.line()
                if (s.frames > 0) Log.i(TAG_PERF, "mode=${mode.name} clarity=%.2f extra=$extra ".format(clarity) + s.line())
            }
        }

        LaunchedEffect(benchRun) {
            if (benchRun == 0) return@LaunchedEffect
            val seconds = if (config.benchSeconds > 0) config.benchSeconds else 10
            benchResult = "bench running…"
            kotlinx.coroutines.delay(1500) // let startup settle
            stats.drain(1)
            val speed = config.speedPxPerSec.toFloat()
            val meter = CurrentMeter(context).also { it.start() }
            val cpu0 = ThreadCpu.snapshot()
            listState.scroll {
                var dir = 1f
                var last = withFrameNanos { it }
                val end = last + seconds * 1_000_000_000L
                while (last < end) {
                    val now = withFrameNanos { it }
                    val dt = (now - last) / 1e9f
                    last = now
                    val want = dir * speed * dt
                    val got = scrollBy(want)
                    if (abs(got) < abs(want) * 0.5f) dir = -dir
                }
            }
            val s = stats.drain(1)
            val mA = meter.stop()
            val cpu = ThreadCpu.rate(cpu0, ThreadCpu.snapshot(), s.seconds)
            benchResult = s.line() + " current=%.0fmA ".format(mA) + cpu
            Log.i(TAG_BENCH, "mode=${mode.name} clarity=%.2f extra=$extra speed=${config.speedPxPerSec} ".format(clarity) + benchResult)
        }

        androidx.compose.runtime.key(blurMethod) {
        ProvideGlass(clarity = clarity, dark = dark, mode = glassMode) {
          val baseStyle = LocalGlassStyle.current
          CompositionLocalProvider(
              LocalContentColor provides MaterialTheme.colorScheme.onSurface,
              LocalGlassPullSpec provides pullSpec,
              LocalGlassStyle provides look.applyTo(baseStyle),
          ) {
            Box(Modifier.fillMaxSize()) {
                // The content glass shows through.
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (mode == SampleMode.None) Modifier else Modifier.glassSource(backdrop))
                ) {
                    if (plain) {
                        Box(Modifier.fillMaxSize().background(if (dark) Color.Black else Color.White))
                    } else {
                        ChatContent(listState, dark)
                    }
                }

                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (plain) BigComposer(backdrop)
                    QuickLook(look, baseStyle, dark) { look = it }
                }

                // Glass on top.
                GlassTopBar(
                    backdrop = backdrop,
                    onBack = {},
                    backIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                    actions = {
                        GlassIconButton(
                            onClick = { showPanel = !showPanel },
                            backdrop = backdrop,
                            contentDescription = "Tune glass",
                        ) { Icon(Icons.Filled.Tune, null) }
                    },
                ) {
                    Text("Vitreous", fontWeight = FontWeight.SemiBold)
                }

                if (extra > 0) {
                    Column(
                        Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repeat(extra) {
                            GlassIconButton(onClick = {}, backdrop = backdrop, contentDescription = null, size = 52.dp) {
                                Icon(Icons.Filled.MoreVert, null)
                            }
                        }
                    }
                }

                if (showHud) {
                    Text(
                        text = hud + if (benchResult.isNotEmpty()) "\nbench: $benchResult" else "",
                        modifier = Modifier
                            .graphicsLayer()
                            .statusBarsPadding()
                            .padding(top = 64.dp, start = 12.dp, end = 12.dp)
                            .background(Color(0xB0000000), RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        color = Color.White,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp),
                    )
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showPanel) {
                        TunePanel(
                            backdrop = backdrop,
                            mode = mode,
                            onMode = { mode = it },
                            clarity = clarity,
                            onClarity = { clarity = it },
                            extra = extra,
                            onExtra = { extra = it },
                            showHud = showHud,
                            onHud = { showHud = it },
                            onBench = { benchRun++ },
                            pullSpec = pullSpec,
                            onPullSpec = { pullSpec = it },
                            plain = plain,
                            onPlain = { plain = it },
                            theme = themeOverride,
                            onTheme = { themeOverride = it },
                            look = look,
                            baseStyle = baseStyle,
                            onLook = { look = it },
                            blurMethod = blurMethod,
                            onBlurMethod = {
                                io.github.zainbits.cohesion.CohesionDebug.blurMethod = it
                                blurMethod = it
                            },
                        )
                    }
                    Composer(backdrop)
                }
            }
          }
        }
        }
    }
}

@Composable
private fun Composer(backdrop: GlassBackdrop) {
    var text by remember { mutableStateOf("") }
    GlassGroup(backdrop) { groupModifier ->
    Row(groupModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassIconButton(onClick = {}, backdrop = backdrop, contentDescription = "Attach", size = 48.dp) {
            Icon(Icons.Filled.Add, null)
        }
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = GlassShape.Capsule,
            contentAlignment = Alignment.CenterStart,
        ) {
            val color = MaterialTheme.colorScheme.onSurface
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = color, fontSize = 16.sp),
                cursorBrush = SolidColor(color),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("Message", color = color.copy(alpha = 0.5f), fontSize = 16.sp)
                    inner()
                },
            )
        }
        GlassIconButton(onClick = { text = "" }, backdrop = backdrop, contentDescription = "Send", size = 48.dp) {
            Icon(Icons.AutoMirrored.Filled.Send, null)
        }
    }
    }
}

@Composable
private fun TunePanel(
    backdrop: GlassBackdrop,
    mode: SampleMode,
    onMode: (SampleMode) -> Unit,
    clarity: Float,
    onClarity: (Float) -> Unit,
    extra: Int,
    onExtra: (Int) -> Unit,
    showHud: Boolean,
    onHud: (Boolean) -> Unit,
    onBench: () -> Unit,
    pullSpec: GlassPullSpec,
    onPullSpec: (GlassPullSpec) -> Unit,
    plain: Boolean,
    onPlain: (Boolean) -> Unit,
    theme: String?,
    onTheme: (String?) -> Unit,
    look: LookTune,
    baseStyle: GlassStyle,
    onLook: (LookTune) -> Unit,
    blurMethod: Int,
    onBlurMethod: (Int) -> Unit,
) {
    GlassSurface(backdrop = backdrop, modifier = Modifier.fillMaxWidth(), shape = GlassShape.rounded(28.dp)) {
        // Scrolls: the panel is taller than the space above the composer on most phones.
        Column(
            Modifier.heightIn(max = 440.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Clear  ←  Liquid Glass  →  Tinted   ${Math.round(clarity * 100)}", style = MaterialTheme.typography.labelLarge)
            Slider(value = clarity, onValueChange = { onClarity(Math.round(it * 100f) / 100f) })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Behind", style = MaterialTheme.typography.labelLarge)
                FilterChip(selected = plain, onClick = { onPlain(true) }, label = { Text("Plain + big composer", fontSize = 11.sp) })
                FilterChip(selected = !plain, onClick = { onPlain(false) }, label = { Text("Chat", fontSize = 11.sp) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                listOf(null to "Phone", "light" to "Light", "dark" to "Dark").forEach { (v, label) ->
                    FilterChip(selected = theme == v, onClick = { onTheme(v) }, label = { Text(label, fontSize = 11.sp) })
                }
            }
            val st = look.applyTo(baseStyle)
            Text(
                "Look: tint %.2f · edge %.2f × %.1f dp · reflect %.2f · bend %.0f dp over %.0f dp · rim %.2f × %.1f dp · even %.2f, distance %.2f"
                    .format(st.tintAlpha, st.edgeAlpha, st.edgeWidth.value, st.reflection, st.refractionDepth.value, st.refractionBand.value, st.rim, st.rimWidth.value, st.evenLight, st.lightDistance),
                style = MaterialTheme.typography.labelMedium,
            )
            TuneSlider("Tint", st.tintAlpha, 0f..1f) { onLook(look.copy(tint = it)) }
            TuneSlider("Edge", st.edgeAlpha, 0f..0.6f) { onLook(look.copy(edgeAlpha = it)) }
            TuneSlider("Edge w", st.edgeWidth.value, 0.5f..4f) { onLook(look.copy(edgeWidth = it)) }
            TuneSlider("Reflect", st.reflection, 0f..1f) { onLook(look.copy(reflection = it)) }
            TuneSlider("Bend", st.refractionDepth.value, 0f..40f) { onLook(look.copy(depth = it)) }
            TuneSlider("Bend band", st.refractionBand.value, 4f..48f) { onLook(look.copy(band = it)) }
            TuneSlider("Rim", st.rim, 0f..1.5f) { onLook(look.copy(rim = it)) }
            TuneSlider("Rim w", st.rimWidth.value, 0.5f..4f) { onLook(look.copy(rimWidth = it)) }
            TuneSlider("Even light", st.evenLight, 0f..1f) { onLook(look.copy(evenLight = it)) }
            TuneSlider("Light dist", st.lightDistance, 0f..1f) { onLook(look.copy(lightDistance = it)) }
            TextButton(onClick = { onLook(LookTune()) }) { Text("Reset look") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SampleMode.entries.forEach {
                    FilterChip(selected = mode == it, onClick = { onMode(it) }, label = { Text(it.label, fontSize = 11.sp) })
                }
            }
            Text(
                "Release: bounce %.2f · response %.2f s · max pull %.0f dp · stretch %.2f"
                    .format(pullSpec.bounce, pullSpec.response, pullSpec.maxPull.value, pullSpec.stretch),
                style = MaterialTheme.typography.labelMedium,
            )
            TuneSlider("Bounce", pullSpec.bounce, 0f..0.6f) { onPullSpec(pullSpec.copy(bounce = it)) }
            TuneSlider("Response", pullSpec.response, 0.15f..0.8f) { onPullSpec(pullSpec.copy(response = it)) }
            TuneSlider("Max pull", pullSpec.maxPull.value, 8f..60f) { onPullSpec(pullSpec.copy(maxPull = it.dp)) }
            TuneSlider("Stretch", pullSpec.stretch, 0f..2f) { onPullSpec(pullSpec.copy(stretch = it)) }
            TextButton(onClick = { onPullSpec(GlassPullSpec()) }) { Text("Reset pull feel") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Blur", style = MaterialTheme.typography.labelLarge)
                FilterChip(selected = blurMethod == 0, onClick = { onBlurMethod(0) }, label = { Text("Android (default)", fontSize = 11.sp) })
                FilterChip(selected = blurMethod == 1, onClick = { onBlurMethod(1) }, label = { Text("Shader taps", fontSize = 11.sp) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Extra buttons: $extra", Modifier.width(120.dp), style = MaterialTheme.typography.labelLarge)
                Slider(value = extra.toFloat(), onValueChange = { onExtra(it.toInt()) }, valueRange = 0f..8f, steps = 7)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onHud(!showHud) }) { Text(if (showHud) "Hide HUD" else "Show HUD") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBench) {
                    Icon(Icons.Filled.Speed, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bench 10 s")
                }
            }
        }
    }
}

@Composable
private fun TuneSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(76.dp), style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun ChatContent(listState: androidx.compose.foundation.lazy.LazyListState, dark: Boolean) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bg = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF0B1026), Color(0xFF2A0F3A), Color(0xFF07252A)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFF1E6), Color(0xFFE8F0FF), Color(0xFFF2FFF4)))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(bg),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top + 72.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(count = 240, key = { it }) { i -> ChatItem(i, dark) }
    }
}

private val cardColors = listOf(
    listOf(Color(0xFFFF5F6D), Color(0xFFFFC371)),
    listOf(Color(0xFF36D1DC), Color(0xFF5B86E5)),
    listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFFC466B), Color(0xFF3F5EFB)),
)

private val lines = listOf(
    "Can you check the deploy logs for the last run?",
    "Done. Two warnings, no errors. The cache warmed in 41 s.",
    "Nice. Ship it to the phone when the build is green.",
    "Building now, about two minutes.",
    "Liquid glass bends the edge of whatever scrolls under it.",
    "The blur is one GPU pass; the lens, tint and rim are another.",
)

@Composable
private fun ChatItem(i: Int, dark: Boolean) {
    if (i % 5 == 2) {
        val c = cardColors[(i / 5) % cardColors.size]
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2.2f)
                .background(Brush.linearGradient(c), RoundedCornerShape(22.dp))
                .padding(16.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text("Card #$i", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        return
    }
    val mine = i % 2 == 0
    val bubble = when {
        mine -> Color(0xFF0A84FF)
        dark -> Color(0xFF2C2C2E)
        else -> Color.White
    }
    val fg = if (mine || dark) Color.White else Color.Black
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Text(
            text = lines[i % lines.size],
            color = fg,
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .background(bubble, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            fontSize = 15.sp,
        )
    }
}

/** Slider overrides on top of the clarity look; null = keep what the clarity gives. */
private data class LookTune(
    val edgeAlpha: Float? = null,
    val edgeWidth: Float? = null,
    val depth: Float? = null,
    val band: Float? = null,
    val rim: Float? = null,
    val rimWidth: Float? = null,
    val reflection: Float? = null,
    val evenLight: Float? = null,
    val tint: Float? = null,
    val lightDistance: Float? = null,
) {
    fun applyTo(s: GlassStyle): GlassStyle = s.copy(
        edgeAlpha = edgeAlpha ?: s.edgeAlpha,
        edgeWidth = edgeWidth?.dp ?: s.edgeWidth,
        refractionDepth = depth?.dp ?: s.refractionDepth,
        refractionBand = band?.dp ?: s.refractionBand,
        rim = rim ?: s.rim,
        rimWidth = rimWidth?.dp ?: s.rimWidth,
        reflection = reflection ?: s.reflection,
        evenLight = evenLight ?: s.evenLight,
        tintAlpha = tint ?: s.tintAlpha,
        lightDistance = lightDistance ?: s.lightDistance,
    )
}

/** The composer, scaled up: a 72 dp field between two 72 dp buttons, with a tall card above it. */
@Composable
private fun BigComposer(backdrop: GlassBackdrop, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth().height(140.dp),
            shape = GlassShape.rounded(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Glass card", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        GlassGroup(backdrop) { groupModifier ->
            Row(groupModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(onClick = {}, backdrop = backdrop, contentDescription = "Attach", size = 72.dp) {
                    Icon(Icons.Filled.Add, null, Modifier.size(30.dp))
                }
                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f).height(72.dp),
                    shape = GlassShape.Capsule,
                    contentAlignment = Alignment.CenterStart,
                    onClick = {},
                ) {
                    Text("Message", Modifier.padding(horizontal = 24.dp), fontSize = 20.sp, color = LocalContentColor.current.copy(alpha = 0.6f))
                }
                GlassIconButton(onClick = {}, backdrop = backdrop, contentDescription = "Send", size = 72.dp) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(30.dp))
                }
            }
        }
    }
}

/** The two settings tuned most, right on the page: the tint's strength and how far away the lights are. */
@Composable
private fun QuickLook(look: LookTune, base: GlassStyle, dark: Boolean, onLook: (LookTune) -> Unit) {
    val st = look.applyTo(base)
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("${if (dark) "Dark" else "White"} tint  ${Math.round(st.tintAlpha * 100)}", style = MaterialTheme.typography.labelLarge)
            Slider(value = st.tintAlpha, onValueChange = { onLook(look.copy(tint = Math.round(it * 100f) / 100f)) })
            Text("Light distance  ${Math.round(st.lightDistance * 100)}", style = MaterialTheme.typography.labelLarge)
            Slider(value = st.lightDistance, onValueChange = { onLook(look.copy(lightDistance = Math.round(it * 100f) / 100f)) })
        }
    }
}
