package io.github.zainbits.vitreous

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The outline of a glass surface: a rounded rectangle. */
@Immutable
public data class GlassShape(val cornerRadius: Dp) {
    public companion object {
        /** Fully rounded ends (a pill, or a circle when square). */
        public val Capsule: GlassShape = GlassShape(Dp.Infinity)

        public fun rounded(radius: Dp): GlassShape = GlassShape(radius)
    }
}

/**
 * Draws this element as a standalone glass surface over [backdrop], then its content on top.
 *
 * Each standalone surface is one offscreen pass. Neighbouring surfaces are cheaper inside a
 * [glassGroup], which draws all of its parts in a single pass.
 */
public fun Modifier.glass(
    backdrop: GlassBackdrop,
    shape: GlassShape = GlassShape.rounded(24.dp),
    style: GlassStyle = GlassStyle.Light,
    mode: GlassMode = GlassMode.Full,
): Modifier = this then GlassElement(backdrop, shape, style, mode)

private class GlassElement(
    val backdrop: GlassBackdrop,
    val shape: GlassShape,
    val style: GlassStyle,
    val mode: GlassMode,
) : ModifierNodeElement<GlassNode>() {
    override fun create(): GlassNode = GlassNode(backdrop, shape, style, mode)

    override fun update(node: GlassNode) {
        node.update(backdrop, shape, style, mode)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glass"
        properties["shape"] = shape
        properties["style"] = style
        properties["mode"] = mode
    }

    override fun equals(other: Any?): Boolean =
        other is GlassElement && other.backdrop === backdrop && other.shape == shape &&
            other.style == style && other.mode == mode

    override fun hashCode(): Int {
        var h = System.identityHashCode(backdrop)
        h = 31 * h + shape.hashCode()
        h = 31 * h + style.hashCode()
        h = 31 * h + mode.hashCode()
        return h
    }
}

private class GlassNode(
    var backdrop: GlassBackdrop,
    var shape: GlassShape,
    var style: GlassStyle,
    var mode: GlassMode,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    override val shouldAutoInvalidate: Boolean get() = false

    private val renderer = GlassRenderer()
    private var offset = Offset.Unspecified

    fun update(backdrop: GlassBackdrop, shape: GlassShape, style: GlassStyle, mode: GlassMode) {
        val changed = this.backdrop !== backdrop || this.shape != shape ||
            this.style != style || this.mode != mode
        this.backdrop = backdrop
        this.shape = shape
        this.style = style
        this.mode = mode
        if (changed) invalidateDraw()
    }

    override fun onAttach() {
        renderer.attach(requireGraphicsContext())
    }

    override fun onDetach() {
        renderer.detach(requireGraphicsContext())
        offset = Offset.Unspecified
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val o = backdrop.positionOf(coordinates) ?: return
        if (o != offset) {
            offset = o
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        if (offset == Offset.Unspecified) {
            backdrop.positionOf(requireLayoutCoordinates())?.let { offset = it }
        }
        renderer.shapes.clear()
        renderer.shapes.add(0f, 0f, size.width, size.height, shape.radiusPx(this, size.width, size.height))
        with(renderer) { drawGlass(requireGraphicsContext(), backdrop.layer, offset, style, mode, 0.dp) }
        drawContent()
    }
}

/** Where [coordinates] sit inside the backdrop content, or null before both are placed. */
internal fun GlassBackdrop.positionOf(coordinates: LayoutCoordinates): Offset? {
    val src = this.coordinates ?: return null
    if (!src.isAttached || !coordinates.isAttached) return null
    return src.localPositionOf(coordinates, Offset.Zero)
}
