package io.github.zainbits.vitreous

import androidx.compose.runtime.Composable
import io.github.zainbits.cohesion.ElasticDeform
import io.github.zainbits.cohesion.GlassBackdrop
import io.github.zainbits.cohesion.GlassMode
import io.github.zainbits.cohesion.GlassPass
import io.github.zainbits.cohesion.GlassPullState
import io.github.zainbits.cohesion.GlassStyle
import io.github.zainbits.cohesion.GlassTone
import io.github.zainbits.cohesion.GlassToneProbe
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.node.requireDensity
import kotlinx.coroutines.flow.MutableStateFlow
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
 * @param backdrop the content the glass shows; null draws the pieces flat in the style's
 *   [GlassStyle.solidColor] with no offscreen pass (merging and stretch still work), for liquid
 *   pieces over plain backgrounds such as cards in a list.
 * @param merge pieces closer than this flow into each other; 0 keeps them separate.
 */
@Composable
public fun GlassGroup(
    backdrop: GlassBackdrop?,
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

/**
 * Draws the glass of every [glassPart] of [group] in one pass behind this element's content.
 * With no [backdrop] the pieces are drawn flat in [GlassStyle.solidColor], still merging and stretching.
 */
public fun Modifier.glassGroup(
    backdrop: GlassBackdrop?,
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
 * @param tone the group keeps it set to dark or light content from what is behind this piece.
 */
public fun Modifier.glassPart(
    group: GlassGroupState,
    shape: GlassShape = GlassShape.rounded(24.dp),
    pull: GlassPullState? = null,
    tone: GlassContentTone? = null,
): Modifier = this then GlassPartElement(group, shape, pull, pull?.spec?.maxPull ?: DefaultMaxPull, tone)

private class GlassGroupElement(
    val backdrop: GlassBackdrop?,
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
    var backdrop: GlassBackdrop?,
    var group: GlassGroupState,
    var style: GlassStyle,
    var mode: GlassMode,
    var merge: Dp,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, LayoutAwareModifierNode,
    CompositionLocalConsumerModifierNode {

    override val shouldAutoInvalidate: Boolean get() = false

    private val scene = RoundRectScene()
    private val pass = GlassPass(scene)
    private val elastic = ElasticDeform()
    private var offset = Offset.Unspecified

    // Adaptive content colour: each toned piece's box (group coordinates), read back only on change.
    private val toneRegions = FloatArray(RoundRectScene.MAX * 4)
    private val toneDrawn = FloatArray(RoundRectScene.MAX * 4)
    private val tones = arrayOfNulls<GlassContentTone>(RoundRectScene.MAX)
    private var toneCount = 0
    private var toneBounds = Rect.Zero
    private val toneMoved = MutableStateFlow(0L)
    private var probe: GlassToneProbe? = null

    fun update(backdrop: GlassBackdrop?, group: GlassGroupState, style: GlassStyle, mode: GlassMode, merge: Dp) {
        val changed = this.backdrop !== backdrop || this.group !== group ||
            this.style != style || this.mode != mode || this.merge != merge
        val newBackdrop = this.backdrop !== backdrop
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
        if (newBackdrop && isAttached) startSampler()
        else if (changed) toneMoved.value++
    }

    /** Flat pieces (no backdrop): nothing to read, the content colour follows the solid colour. */
    private val flat: Boolean get() = backdrop == null || mode == GlassMode.Solid

    private fun startSampler() {
        val backdrop = backdrop
        if (backdrop == null) {
            probe?.stop()
            probe = null
            return
        }
        val p = probe ?: GlassToneProbe(coroutineScope) { sampleTones() }.also { probe = it }
        p.watch(backdrop, toneMoved)
    }

    /** Reads the strip beside the group (below it in the upper half of the screen, above it in the lower). */
    private fun sampleTones() {
        val n = toneCount
        if (!isAttached || n == 0) return
        if (flat) {
            val l = style.solidColor.luminance()
            for (k in 0 until n) tones[k]?.let { it.isDark = GlassTone.wantsDarkContent(l, it.isDark) }
            return
        }
        val me = requireLayoutCoordinates()
        if (!me.isAttached) return
        val view = currentValueOf(LocalView)
        val at = me.positionInWindow()
        val w = me.size.width
        val h = me.size.height
        if (w < 2) return
        val density = requireDensity()
        val gap = with(density) { 2.dp.roundToPx() }
        val strip = with(density) { 6.dp.roundToPx() }
        val left = at.x.toInt()
        val below = at.y + h / 2f < view.rootView.height / 2f
        val top = if (below) at.y.toInt() + h + gap else at.y.toInt() - gap - strip
        val style = style
        probe?.read(view, left, top, left + w, top + strip) { columns ->
            if (!isAttached) return@read
            for (k in 0 until minOf(n, toneCount)) {
                val tone = tones[k] ?: continue
                val content = GlassToneProbe.average(columns, toneRegions[k * 4] / w, toneRegions[k * 4 + 2] / w)
                tone.isDark = GlassTone.wantsDarkContent(GlassTone.throughGlass(content, style), tone.isDark)
            }
        }
    }

    fun invalidateFromParts() {
        if (isAttached) invalidateDraw()
    }

    override fun onAttach() {
        group.node = this
        group.backdrop = backdrop
        pass.attach(requireGraphicsContext())
        startSampler()
    }

    override fun onDetach() {
        probe?.stop()
        probe = null
        if (group.node === this) group.node = null
        pass.detach(requireGraphicsContext())
        offset = Offset.Unspecified
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        group.coordinates = coordinates
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val o = backdrop?.positionOf(coordinates) ?: return
        if (o != offset) {
            offset = o
            invalidateDraw()
        }
    }

    /** After a draw: if a toned piece or the group moved, ask for a new reading. */
    private fun ContentDrawScope.noteTones(count: Int) {
        for (k in count until toneCount) tones[k] = null
        val bounds = if (offset == Offset.Unspecified) Rect.Zero else Rect(offset, size)
        var moved = count != toneCount || bounds != toneBounds
        for (i in 0 until count * 4) {
            if (toneRegions[i] != toneDrawn[i]) {
                moved = true
                toneRegions[i] = toneDrawn[i]
            }
        }
        toneCount = count
        toneBounds = bounds
        if (moved && count > 0) {
            if (backdrop == null) sampleTones() else toneMoved.value++
        }
    }

    override fun ContentDrawScope.draw() {
        val me = requireLayoutCoordinates()
        val backdrop = backdrop
        if (offset == Offset.Unspecified) {
            backdrop?.positionOf(me)?.let { offset = it }
        }
        val shapes = scene
        shapes.clear(style.refractionBand.toPx(), merge.toPx())
        var margin = 0.dp
        var toned = 0
        for (part in group.parts) {
            if (!part.isAttached) continue
            val c = part.requireLayoutCoordinates()
            if (!c.isAttached) continue
            val r = me.localBoundingBoxOf(c, clipBounds = false)
            val tone = part.tone
            if (tone != null && toned < RoundRectScene.MAX) {
                tones[toned] = tone
                toneDrawn[toned * 4] = r.left
                toneDrawn[toned * 4 + 1] = r.top
                toneDrawn[toned * 4 + 2] = r.right
                toneDrawn[toned * 4 + 3] = r.bottom
                toned++
            }
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
        noteTones(toned)
        if (shapes.size > 0) {
            with(pass) {
                if (backdrop == null) drawSolid(style, margin * 1.25f)
                else draw(requireGraphicsContext(), backdrop, offset, style, mode, margin * 1.25f)
            }
        }
        drawContent()
    }
}

private class GlassPartElement(
    val group: GlassGroupState,
    val shape: GlassShape,
    val pull: GlassPullState?,
    val maxPull: Dp,
    val tone: GlassContentTone?,
) : ModifierNodeElement<GlassPartNode>() {
    override fun create() = GlassPartNode(group, shape, pull, maxPull, tone)

    override fun update(node: GlassPartNode) {
        val changed = node.group !== group || node.shape != shape || node.pull !== pull || node.maxPull != maxPull ||
            node.tone !== tone
        node.tone = tone
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
            other.pull === pull && other.maxPull == maxPull && other.tone === tone

    override fun hashCode(): Int {
        var h = System.identityHashCode(group)
        h = 31 * h + shape.hashCode()
        h = 31 * h + System.identityHashCode(pull)
        h = 31 * h + maxPull.hashCode()
        h = 31 * h + System.identityHashCode(tone)
        return h
    }
}

internal class GlassPartNode(
    var group: GlassGroupState,
    var shape: GlassShape,
    var pull: GlassPullState?,
    var maxPull: Dp,
    var tone: GlassContentTone?,
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
