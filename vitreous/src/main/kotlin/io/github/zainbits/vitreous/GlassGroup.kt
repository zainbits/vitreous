package io.github.zainbits.vitreous

import androidx.compose.runtime.Composable
import io.github.zainbits.cohesion.ElasticDeform
import io.github.zainbits.cohesion.GlassBackdrop
import io.github.zainbits.cohesion.GlassMode
import io.github.zainbits.cohesion.GlassPass
import io.github.zainbits.cohesion.GlassPullState
import io.github.zainbits.cohesion.GlassStyle
import io.github.zainbits.cohesion.DefaultMaxPull
import io.github.zainbits.cohesion.LocalGlassMode
import io.github.zainbits.cohesion.LocalGlassStyle
import io.github.zainbits.cohesion.RoundRectScene
import io.github.zainbits.cohesion.positionOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glass pieces that share one offscreen pass, like a bar's back button, title and actions, or a
 * composer's attach button, field and send button.
 *
 * Every offscreen pass has a fixed cost on the render thread (and a blur costs several passes),
 * so a group of N pieces costs about as much as one piece. Pieces closer than the group's merge
 * distance flow into each other like liquid.
 *
 * Put [glassGroup] on a container and [glassPart] on each piece inside it, or use [GlassGroup],
 * which also makes [GlassSurface] and [GlassIconButton] inside it join the group by themselves.
 * A group draws at most 8 parts.
 */
@Stable
public class GlassGroupState internal constructor() {
    internal var backdrop: GlassBackdrop? = null
    internal var coordinates: LayoutCoordinates? = null
    internal val parts = ArrayList<GlassPartNode>(RoundRectScene.MAX)
    internal var node: GlassGroupNode? = null

    internal fun invalidate() {
        node?.invalidateFromParts()
    }
}

/** Creates a [GlassGroupState] owned by the current composition. */
@Composable
public fun rememberGlassGroup(): GlassGroupState = remember { GlassGroupState() }

/** The group that [GlassSurface] and [GlassIconButton] join when one is provided. */
public val LocalGlassGroup: ProvidableCompositionLocal<GlassGroupState?> = compositionLocalOf { null }

/**
 * A container whose glass pieces draw in one pass. [GlassSurface]s and [GlassIconButton]s
 * anywhere inside [content] join it automatically.
 *
 * @param merge pieces closer than this flow into each other; 0 keeps them separate.
 */
@Composable
public fun GlassGroup(
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
    mode: GlassMode = LocalGlassMode.current,
    merge: Dp = 0.dp,
    content: @Composable (Modifier) -> Unit,
) {
    val group = rememberGlassGroup()
    CompositionLocalProvider(LocalGlassGroup provides group) {
        content(modifier.glassGroup(backdrop, group, style, mode, merge))
    }
}

/** Draws the glass of every [glassPart] of [group] in one pass behind this element's content. */
public fun Modifier.glassGroup(
    backdrop: GlassBackdrop,
    group: GlassGroupState,
    style: GlassStyle = GlassStyle.Light,
    mode: GlassMode = GlassMode.Full,
    merge: Dp = 0.dp,
): Modifier = this then GlassGroupElement(backdrop, group, style, mode, merge)

/**
 * Marks this element as one glass piece of [group] (on this element or an ancestor).
 *
 * @param pull makes the piece elastic: it swells when pressed and stretches toward the finger
 *   (pair it with [glassPullGesture]); its [GlassPullState.spec] sets the feel. Animating it only
 *   changes the group's shader numbers; nothing is recomposed or re-recorded.
 */
public fun Modifier.glassPart(
    group: GlassGroupState,
    shape: GlassShape = GlassShape.rounded(24.dp),
    pull: GlassPullState? = null,
): Modifier = this then GlassPartElement(group, shape, pull, pull?.spec?.maxPull ?: DefaultMaxPull)

private class GlassGroupElement(
    val backdrop: GlassBackdrop,
    val group: GlassGroupState,
    val style: GlassStyle,
    val mode: GlassMode,
    val merge: Dp,
) : ModifierNodeElement<GlassGroupNode>() {
    override fun create() = GlassGroupNode(backdrop, group, style, mode, merge)

    override fun update(node: GlassGroupNode) {
        node.update(backdrop, group, style, mode, merge)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassGroup"
        properties["style"] = style
        properties["mode"] = mode
        properties["merge"] = merge
    }

    override fun equals(other: Any?): Boolean =
        other is GlassGroupElement && other.backdrop === backdrop && other.group === group &&
            other.style == style && other.mode == mode && other.merge == merge

    override fun hashCode(): Int {
        var h = System.identityHashCode(backdrop)
        h = 31 * h + System.identityHashCode(group)
        h = 31 * h + style.hashCode()
        h = 31 * h + mode.hashCode()
        h = 31 * h + merge.hashCode()
        return h
    }
}

internal class GlassGroupNode(
    var backdrop: GlassBackdrop,
    var group: GlassGroupState,
    var style: GlassStyle,
    var mode: GlassMode,
    var merge: Dp,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, LayoutAwareModifierNode {

    override val shouldAutoInvalidate: Boolean get() = false

    private val scene = RoundRectScene()
    private val pass = GlassPass(scene)
    private val elastic = ElasticDeform()
    private var offset = Offset.Unspecified

    fun update(backdrop: GlassBackdrop, group: GlassGroupState, style: GlassStyle, mode: GlassMode, merge: Dp) {
        val changed = this.backdrop !== backdrop || this.group !== group ||
            this.style != style || this.mode != mode || this.merge != merge
        if (this.group !== group) {
            if (this.group.node === this) this.group.node = null
            group.node = this
        }
        this.backdrop = backdrop
        this.group = group
        this.style = style
        this.mode = mode
        this.merge = merge
        group.backdrop = backdrop
        if (changed) invalidateDraw()
    }

    fun invalidateFromParts() {
        if (isAttached) invalidateDraw()
    }

    override fun onAttach() {
        group.node = this
        group.backdrop = backdrop
        pass.attach(requireGraphicsContext())
    }

    override fun onDetach() {
        if (group.node === this) group.node = null
        pass.detach(requireGraphicsContext())
        offset = Offset.Unspecified
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        group.coordinates = coordinates
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val o = backdrop.positionOf(coordinates) ?: return
        if (o != offset) {
            offset = o
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val me = requireLayoutCoordinates()
        if (offset == Offset.Unspecified) {
            backdrop.positionOf(me)?.let { offset = it }
        }
        val shapes = scene
        shapes.clear(style.refractionBand.toPx(), merge.toPx())
        var margin = 0.dp
        for (part in group.parts) {
            if (!part.isAttached) continue
            val c = part.requireLayoutCoordinates()
            if (!c.isAttached) continue
            val r = me.localBoundingBoxOf(c, clipBounds = false)
            val pull = part.pull
            if (pull == null) {
                shapes.add(r.left, r.top, r.width, r.height, part.shape.radiusPx(this, r.width, r.height))
                continue
            }
            // Reading the pull state here makes this draw (only) follow its animation.
            val k = pull.scale
            val w = r.width * k
            val h = r.height * k
            val radius = part.shape.radiusPx(this, w, h)
            val round = ElasticDeform.isRound(w, h, radius)
            pull.round = round
            elastic.compute(pull.offset, w, h, pull.spec.maxPull.toPx(), pull.spec.stretch, round)
            val x = r.left - (w - r.width) / 2f + elastic.shiftX
            val y = r.top - (h - r.height) / 2f + elastic.shiftY
            shapes.add(
                x, y, w, h, radius,
                elastic.ux, elastic.uy, elastic.stretch, elastic.squash,
            )
            if (pull.spec.maxPull > margin) margin = pull.spec.maxPull
        }
        shapes.commit()
        if (shapes.size > 0) {
            with(pass) { draw(requireGraphicsContext(), backdrop, offset, style, mode, margin * 1.25f) }
        }
        drawContent()
    }
}

private class GlassPartElement(
    val group: GlassGroupState,
    val shape: GlassShape,
    val pull: GlassPullState?,
    val maxPull: Dp,
) : ModifierNodeElement<GlassPartNode>() {
    override fun create() = GlassPartNode(group, shape, pull, maxPull)

    override fun update(node: GlassPartNode) {
        val changed = node.group !== group || node.shape != shape || node.pull !== pull || node.maxPull != maxPull
        if (node.group !== group) {
            node.group.parts.remove(node)
            node.group.invalidate()
            group.parts.add(node)
        }
        node.group = group
        node.shape = shape
        node.pull = pull
        node.maxPull = maxPull
        if (changed) group.invalidate()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassPart"
        properties["shape"] = shape
    }

    override fun equals(other: Any?): Boolean =
        other is GlassPartElement && other.group === group && other.shape == shape &&
            other.pull === pull && other.maxPull == maxPull

    override fun hashCode(): Int {
        var h = System.identityHashCode(group)
        h = 31 * h + shape.hashCode()
        h = 31 * h + System.identityHashCode(pull)
        h = 31 * h + maxPull.hashCode()
        return h
    }
}

internal class GlassPartNode(
    var group: GlassGroupState,
    var shape: GlassShape,
    var pull: GlassPullState?,
    var maxPull: Dp,
) : Modifier.Node(), GlobalPositionAwareModifierNode {
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastW = Float.NaN
    private var lastH = Float.NaN

    override fun onAttach() {
        group.parts.add(this)
        group.invalidate()
    }

    override fun onDetach() {
        group.parts.remove(this)
        group.invalidate()
        lastX = Float.NaN
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val g = group.coordinates ?: return
        if (!g.isAttached || !coordinates.isAttached) return
        val r = g.localBoundingBoxOf(coordinates, clipBounds = false)
        if (r.left != lastX || r.top != lastY || r.width != lastW || r.height != lastH) {
            lastX = r.left
            lastY = r.top
            lastW = r.width
            lastH = r.height
            group.invalidate()
        }
    }
}
