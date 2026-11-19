package io.github.zainbits.vitreous

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

/**
 * How a glass surface looks. All lengths are in dp so a style works on any screen density.
 *
 * @property blurRadius frost of the content behind; 0 turns the blur pass off entirely.
 * @property refractionBand how far in from the edge the content bends.
 * @property refractionDepth how far (at the very edge) content is pulled in from.
 * @property saturation colour boost of what shows through; 1 = unchanged.
 * @property tint colour laid over the glass.
 * @property tintAlpha strength of [tint]; 0 = clear glass, 1 = solid.
 * @property rim strength of the light catching the edge; 0 = none.
 * @property rimWidth width of the lit edge.
 * @property lightAngle direction the light comes from, in degrees (0 = from the right,
 *   -90 = from the top).
 * @property dispersion colour fringing at the edge (costs two extra samples per pixel when > 0).
 * @property solidColor what is drawn instead of glass in [GlassMode.Solid].
 */
@Immutable
public data class GlassStyle(
    val blurRadius: Dp = 10.dp,
    val refractionBand: Dp = 16.dp,
    val refractionDepth: Dp = 12.dp,
    val saturation: Float = 1.5f,
    val tint: Color = Color.White,
    val tintAlpha: Float = 0.10f,
    val rim: Float = 0.55f,
    val rimWidth: Dp = 1.2.dp,
    val lightAngle: Float = -120f,
    val dispersion: Float = 0f,
    val solidColor: Color = Color(0xF2F4F4F6),
) {
    public companion object {
        /** The default look for a light theme. */
        public val Light: GlassStyle = forClarity(0.35f, dark = false)

        /** The default look for a dark theme. */
        public val Dark: GlassStyle = forClarity(0.35f, dark = true)

        /**
         * A look on one slider, like the iOS 27 Liquid Glass setting.
         *
         * @param clarity 0 = ultra-clear, 1 = fully tinted (and cheapest: no blur pass).
         */
        public fun forClarity(clarity: Float, dark: Boolean): GlassStyle {
            val c = clarity.coerceIn(0f, 1f)
            val tint = if (dark) Color(0xFF1C1C1E) else Color(0xFFF7F7F9)
            // Blur rises with tint until the glass is nearly opaque, then switches off:
            // a fully tinted surface hides the content anyway, so the pass would be wasted.
            val blur = if (c >= 0.97f) 0.dp else lerp(2.dp, 18.dp, (c / 0.8f).coerceAtMost(1f))
            return GlassStyle(
                blurRadius = blur,
                refractionBand = 16.dp,
                refractionDepth = lerp(14.dp, 6.dp, c),
                saturation = 1.6f - 0.5f * c,
                tint = tint,
                tintAlpha = if (c >= 0.97f) 1f else (if (dark) 0.04f else 0.06f) + 0.80f * c * c,
                rim = lerp(0.7f, 0.3f, c),
                rimWidth = 1.2.dp,
                lightAngle = -120f,
                dispersion = 0f,
                solidColor = lerp(tint, if (dark) Color.Black else Color.White, 0.04f).copy(alpha = 0.95f),
            )
        }

        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    }
}

/** Where glass surfaces get their look when none is passed explicitly. */
public val LocalGlassStyle: androidx.compose.runtime.ProvidableCompositionLocal<GlassStyle> =
    compositionLocalOf { GlassStyle.Light }

/** How much rendering work glass surfaces do. */
public enum class GlassMode {
    /** Blur, refraction, tint and lit edge. */
    Full,

    /** Refraction, tint and lit edge without the blur pass. */
    NoBlur,

    /** A plain tinted shape: no offscreen pass at all. Used for battery saver and heat. */
    Solid,
}

/** The mode glass surfaces use when none is passed explicitly. */
public val LocalGlassMode: androidx.compose.runtime.ProvidableCompositionLocal<GlassMode> =
    compositionLocalOf { GlassMode.Full }
