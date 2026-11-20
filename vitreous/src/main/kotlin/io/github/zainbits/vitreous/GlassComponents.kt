package io.github.zainbits.vitreous

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import io.github.zainbits.cohesion.GlassBackdrop
import io.github.zainbits.cohesion.GlassMode
import io.github.zainbits.cohesion.GlassStyle
import io.github.zainbits.cohesion.LocalGlassMode
import io.github.zainbits.cohesion.LocalGlassStyle
import io.github.zainbits.cohesion.glassPullContent
import io.github.zainbits.cohesion.glassPullGesture
import io.github.zainbits.cohesion.rememberGlassPullState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import io.github.zainbits.cohesion.GlassPullState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A glass panel with content on top. Look and mode come from [LocalGlassStyle]/[LocalGlassMode].
 * Inside a [GlassGroup] it becomes one of the group's pieces instead of a pass of its own.
 *
 * @param backdrop the content the glass shows; null draws a flat panel in [GlassStyle.solidColor] that still
 *   stretches (with [elastic]) and merges inside a group, with no offscreen pass.
 * @param elastic the finger can pull the panel like liquid (it springs back on release).
 * @param onClick makes the panel a button; with [elastic], a press released over it is a click.
 * @param onLongClick a press held still (a menu); with [elastic] the glass springs back and it is not a click.
 * @param scrollAxis the panel sits in a list scrolling along this axis (a tab strip): with [elastic], a drag
 *   along it scrolls the list instead of stretching the glass.
 * @param clipContent clips the content to the shape (it follows the stretch), so a pressed or focused
 *   control inside never shows a square corner. Off for content that reaches past the edge on purpose.
 */
@Composable
public fun GlassSurface(
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    shape: GlassShape = GlassShape.rounded(24.dp),
    style: GlassStyle = LocalGlassStyle.current,
    mode: GlassMode = LocalGlassMode.current,
    contentAlignment: Alignment = Alignment.TopStart,
    elastic: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    scrollAxis: Orientation? = null,
    clipContent: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val outer = LocalGlassGroup.current
    val pull = if (elastic) rememberGlassPullState() else null
    val glass = when {
        outer != null -> Modifier.glassPart(outer, shape, pull)
        // An elastic or flat piece uses a one-piece group (room to stretch); same cost.
        pull != null || backdrop == null -> {
            val own = rememberGlassGroup()
            Modifier.glassGroup(backdrop, own, style, mode).glassPart(own, shape, pull)
        }
        else -> Modifier.glass(backdrop, shape, style, mode)
    }
    val click = if (enabled) onClick else null
    val longClick = if (enabled) onLongClick else null
    Box(
        modifier = modifier
            .then(glass)
            .then(
                when {
                    pull != null -> Modifier.glassPullGesture(pull, onTap = click, onLongPress = longClick, scrollAxis = scrollAxis)
                    click != null || longClick != null -> Modifier.combinedClickable(onLongClick = longClick, onClick = click ?: {})
                    else -> Modifier
                },
            )
            .then(
                if (click != null || longClick != null) {
                    Modifier.semantics {
                        role = Role.Button
                        if (click != null) {
                            this.onClick(label = null) {
                                click()
                                true
                            }
                        }
                        if (longClick != null) {
                            this.onLongClick(label = null) {
                                longClick()
                                true
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = contentAlignment,
        // The inner box gets the panel's full size, so the content's transform matches the glass.
        propagateMinConstraints = true,
    ) {
        Box(
            modifier = (if (pull != null) Modifier.glassPullContent(pull) else Modifier)
                .then(if (clipContent) Modifier.clip(shape.toShape()) else Modifier),
            contentAlignment = contentAlignment,
        ) {
            // Glass nested inside this surface is drawn on top of it, not merged into the group.
            CompositionLocalProvider(LocalGlassGroup provides null, LocalGlassPull provides pull) {
                content()
            }
        }
    }
}

/**
 * A round glass button (a back button, an action). It is elastic: a press swells it, a drag
 * stretches it toward the finger like liquid, and it wobbles back on release. A press released
 * over the button is a click. With no [backdrop] it is a flat button in [GlassStyle.solidColor] that still stretches.
 */
@Composable
public fun GlassIconButton(
    onClick: () -> Unit,
    backdrop: GlassBackdrop?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    style: GlassStyle = LocalGlassStyle.current,
    mode: GlassMode = LocalGlassMode.current,
    icon: @Composable () -> Unit,
) {
    val pull = rememberGlassPullState()
    val outer = LocalGlassGroup.current
    val own = rememberGlassGroup()
    val group = outer ?: own
    Box(
        modifier = modifier
            .size(size)
            .then(if (outer == null) Modifier.glassGroup(backdrop, own, style, mode) else Modifier)
            .glassPart(group, GlassShape.Capsule, pull)
            .glassPullGesture(pull, enabled, onTap = onClick)
            .semantics {
                role = Role.Button
                if (contentDescription != null) this.contentDescription = contentDescription
                this.onClick(label = null) {
                    onClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().glassPullContent(pull), contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

/**
 * A floating top bar: an optional round back button, a glass title capsule and trailing
 * actions, laid out below the status bar. All pieces (including [GlassIconButton]s in
 * [actions]) draw in one glass pass; the gaps between them stay clear. The back button and
 * title are elastic, and pulling one into its neighbour merges them like liquid.
 */
@Composable
public fun GlassTopBar(
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backIcon: @Composable () -> Unit = {},
    style: GlassStyle = LocalGlassStyle.current,
    mode: GlassMode = LocalGlassMode.current,
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit,
) {
    GlassGroup(
        backdrop = backdrop,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(48.dp),
        style = style,
        mode = mode,
        // Pieces pulled close together flow into one another, like liquid.
        merge = 10.dp,
    ) { groupModifier ->
    Row(
        modifier = groupModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            GlassIconButton(
                onClick = onBack,
                backdrop = backdrop,
                contentDescription = "Back",
                style = style,
                mode = mode,
                icon = backIcon,
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.height(40.dp).widthIn(min = 96.dp),
                shape = GlassShape.Capsule,
                style = style,
                mode = mode,
                contentAlignment = Alignment.Center,
                elastic = true,
            ) {
                Box(Modifier.padding(horizontal = 18.dp)) { title() }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
    }
}

/**
 * The pull state of the elastic [GlassSurface] around this content (null when it is not elastic). Mark a
 * control inside it with `Modifier.glassPullExempt(LocalGlassPull.current)` so touching the control does
 * not bounce the glass.
 */
public val LocalGlassPull: ProvidableCompositionLocal<GlassPullState?> = compositionLocalOf { null }
