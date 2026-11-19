package io.github.zainbits.vitreous

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.zainbits.cohesion.GlassStyle

/**
 * Whether a glass piece's content should be dark (over bright content) or light (over dark content).
 * The piece's group sets it from what is behind the glass ([io.github.zainbits.cohesion.GlassTone]),
 * only when that content or the glass moved.
 */
@Stable
public class GlassContentTone internal constructor(initialDark: Boolean) {
    /** True: dark text and icons read better here. */
    public var isDark: Boolean by mutableStateOf(initialDark)
        internal set
}

/** A [GlassContentTone] that starts from [style]'s own tint until the first reading. */
@Composable
public fun rememberGlassContentTone(style: GlassStyle): GlassContentTone =
    remember { GlassContentTone(initialDark = style.tint.luminance() > 0.5f) }

/** The two colours adaptive glass content switches between. */
@Immutable
public data class GlassContentColors(
    /** Over dark content. */
    val light: Color = Color(0xF2FFFFFF),
    /** Over bright content. */
    val dark: Color = Color(0xE6000000),
)

/** The colours [GlassSurface] and [GlassIconButton] give their content when it adapts. */
public val LocalGlassContentColors: ProvidableCompositionLocal<GlassContentColors> =
    staticCompositionLocalOf { GlassContentColors() }
