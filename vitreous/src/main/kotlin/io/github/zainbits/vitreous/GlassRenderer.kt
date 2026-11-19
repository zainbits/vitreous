package io.github.zainbits.vitreous

import android.os.Trace
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

/**
 * Shapes for one glass pass, in the pass's pixels. Filled by the owner before each draw.
 */
internal class GlassShapes {
    val rects = FloatArray(MAX_SHAPES * 4)
    val radii = FloatArray(MAX_SHAPES)

    /** Per shape: stretch direction (ux, uy), stretch along it, squash across it. */
    val deform = FloatArray(MAX_SHAPES * 4).also { identity(it) }
    var count = 0
        private set

    fun clear() {
        count = 0
    }

    fun add(
        x: Float, y: Float, w: Float, h: Float, radius: Float,
        ux: Float = 1f, uy: Float = 0f, stretch: Float = 1f, squash: Float = 1f,
    ) {
        if (count >= MAX_SHAPES || w <= 0f || h <= 0f) return
        val i = count * 4
        rects[i] = x
        rects[i + 1] = y
        rects[i + 2] = w
        rects[i + 3] = h
        radii[count] = minOf(radius, minOf(w, h) / 2f)
        deform[i] = ux
        deform[i + 1] = uy
        deform[i + 2] = stretch
        deform[i + 3] = squash
        count++
    }

    fun sameAs(o: GlassShapes): Boolean {
        if (count != o.count) return false
        for (i in 0 until count * 4) if (rects[i] != o.rects[i]) return false
        for (i in 0 until count) if (radii[i] != o.radii[i]) return false
        for (i in 0 until count * 4) if (deform[i] != o.deform[i]) return false
        return true
    }

    fun copyFrom(o: GlassShapes) {
        o.rects.copyInto(rects)
        o.radii.copyInto(radii)
        o.deform.copyInto(deform)
        count = o.count
    }

    private companion object {
        fun identity(d: FloatArray) {
            for (i in 0 until MAX_SHAPES) {
                d[i * 4] = 1f
                d[i * 4 + 1] = 0f
                d[i * 4 + 2] = 1f
                d[i * 4 + 3] = 1f
            }
        }
    }
}

internal fun GlassShape.radiusPx(scope: DrawScope, w: Float, h: Float): Float =
    if (cornerRadius == Dp.Infinity) minOf(w, h) / 2f else with(scope) { cornerRadius.toPx() }

/**
 * Draws one offscreen glass pass: the backdrop's display list replayed at [offset], blurred,
 * then shaded for all shapes at once. Re-records only when the offset or size changes and
 * rebuilds the effect only when the look or the shapes change; when the content behind
 * scrolls, the GPU thread refreshes the pass without any UI-thread work.
 */
internal class GlassRenderer {
    val shapes = GlassShapes()

    private var layer: GraphicsLayer? = null
    private var blurLayer: GraphicsLayer? = null

    private var effect: RenderEffect? = null
    private val effectShapes = GlassShapes()
    private var effectSize = IntSize.Zero
    private var effectDensity = 0f
    private var effectStyle: GlassStyle? = null
    private var effectMode: GlassMode? = null
    private var effectMerge = -1f
    private var effectDs = 1
    private var effectTaps = false
    private var effectMargin = 0
    private val uploadRects = FloatArray(MAX_SHAPES * 4)

    private var recordedOffset = Offset.Unspecified
    private var recordedSize = IntSize.Zero
    private var recordedSource: GraphicsLayer? = null

    fun attach(context: GraphicsContext) {
        layer = context.createGraphicsLayer().apply { clip = true }
    }

    fun detach(context: GraphicsContext) {
        layer?.let { context.releaseGraphicsLayer(it) }
        blurLayer?.let { context.releaseGraphicsLayer(it) }
        layer = null
        blurLayer = null
        effect = null
        effectStyle = null
        recordedOffset = Offset.Unspecified
        recordedSource = null
    }

    fun DrawScope.drawGlass(
        context: GraphicsContext,
        source: GraphicsLayer,
        offset: Offset,
        style: GlassStyle,
        mode: GlassMode,
        merge: Dp,
        margin: Dp = Dp(0f),
    ) {
        val layer = layer
        if (mode == GlassMode.Solid || layer == null || offset == Offset.Unspecified) {
            drawSolid(style)
            return
        }
        val tracing = Trace.isEnabled()
        if (tracing) Trace.beginSection("Vitreous#glass")

        // The pass may extend past the element by [margin] so stretched shapes are not cut off.
        val m = kotlin.math.ceil(margin.toPx()).toInt()
        val mf = m.toFloat()
        val intSize = IntSize(size.width.toInt() + 2 * m, size.height.toInt() + 2 * m)
        val blurPx = if (mode == GlassMode.NoBlur) 0f else style.blurRadius.toPx()
        val taps = blurPx >= 0.5f && VitreousDebug.blurMethod == 1
        val ds = when {
            blurPx < 0.5f -> 1
            VitreousDebug.blurDownsample > 0 -> VitreousDebug.blurDownsample
            // Keep the taps about 3 px apart in the small copy: smooth, never ghosted.
            taps -> kotlin.math.ceil(blurPx / 3f).toInt().coerceIn(1, 8)
            else -> 1
        }
        val mergePx = merge.toPx()
        if (effect == null || intSize != effectSize || density != effectDensity ||
            style != effectStyle || mode != effectMode || ds != effectDs ||
            mergePx != effectMerge || taps != effectTaps || m != effectMargin || !shapes.sameAs(effectShapes)
        ) {
            for (i in 0 until shapes.count) {
                uploadRects[i * 4] = shapes.rects[i * 4] + mf
                uploadRects[i * 4 + 1] = shapes.rects[i * 4 + 1] + mf
                uploadRects[i * 4 + 2] = shapes.rects[i * 4 + 2]
                uploadRects[i * 4 + 3] = shapes.rects[i * 4 + 3]
            }
            val lens = GlassShader.effect(
                width = intSize.width.toFloat(),
                height = intSize.height.toFloat(),
                rects = uploadRects,
                radii = shapes.radii,
                deform = shapes.deform,
                count = shapes.count,
                mergePx = mergePx,
                bandPx = style.refractionBand.toPx(),
                depthPx = style.refractionDepth.toPx(),
                rimWidthPx = style.rimWidth.toPx(),
                shaderBlurPx = if (taps) blurPx else 0f,
                style = style,
            )
            val combined = when {
                blurPx < 0.5f || taps -> lens
                // Blur and lens in one offscreen pass.
                ds == 1 -> android.graphics.RenderEffect.createChainEffect(lens, GlassShader.blur(blurPx))
                // Blur runs in its own smaller layer; the lens pass upsamples it.
                else -> lens
            }
            if (ds > 1) {
                val bl = blurLayer ?: context.createGraphicsLayer().also {
                    it.clip = true
                    blurLayer = it
                }
                if (taps) {
                    // Plain offscreen copy at low resolution; the shader's taps do the blur.
                    bl.renderEffect = null
                    bl.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                } else {
                    bl.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Auto
                    bl.renderEffect = GlassShader.blur(blurPx / ds).asComposeRenderEffect()
                }
            }
            effect = combined.asComposeRenderEffect()
            layer.renderEffect = effect
            if (ds != effectDs || taps != effectTaps) recordedSize = IntSize.Zero // layer structure changed
            effectTaps = taps
            effectMargin = m
            effectShapes.copyFrom(shapes)
            effectSize = intSize
            effectDensity = density
            effectStyle = style
            effectMode = mode
            effectMerge = mergePx
            effectDs = ds
        }

        val origin = Offset(offset.x - mf, offset.y - mf)
        if (origin != recordedOffset || intSize != recordedSize || source !== recordedSource) {
            val bl = blurLayer
            if (ds > 1 && bl != null) {
                val small = IntSize((intSize.width + ds - 1) / ds, (intSize.height + ds - 1) / ds)
                val inv = 1f / ds
                bl.record(small) {
                    scale(inv, inv, pivot = Offset.Zero) {
                        translate(-origin.x, -origin.y) { drawLayer(source) }
                    }
                }
                layer.record(intSize) {
                    scale(ds.toFloat(), ds.toFloat(), pivot = Offset.Zero) { drawLayer(bl) }
                }
            } else {
                layer.record(intSize) {
                    translate(-origin.x, -origin.y) { drawLayer(source) }
                }
            }
            recordedOffset = origin
            recordedSize = intSize
            recordedSource = source
            VitreousDebug.glassRecords++
        }
        if (m == 0) drawLayer(layer) else translate(-mf, -mf) { drawLayer(layer) }
        if (tracing) Trace.endSection()
    }

    private fun DrawScope.drawSolid(style: GlassStyle) {
        val s = shapes
        for (i in 0 until s.count) {
            val r = s.radii[i]
            drawRoundRect(
                color = style.solidColor,
                topLeft = Offset(s.rects[i * 4], s.rects[i * 4 + 1]),
                size = Size(s.rects[i * 4 + 2], s.rects[i * 4 + 3]),
                cornerRadius = CornerRadius(r, r),
            )
        }
    }
}
