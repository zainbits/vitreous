package io.github.zainbits.vitreous

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A glass panel with content on top. Look and mode come from [LocalGlassStyle]/[LocalGlassMode].
 * Inside a [GlassGroup] it becomes one of the group's pieces instead of a pass of its own.
 *
 * @param elastic the finger can pull the panel like liquid (it springs back on release).
 */
@Composable
public fun GlassSurface(
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    shape: GlassShape = GlassShape.rounded(24.dp),
    style: GlassStyle = LocalGlassStyle.current,
    mode: GlassMode = LocalGlassMode.current,
    contentAlignment: Alignment = Alignment.TopStart,
    elastic: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val outer = LocalGlassGroup.current
    val pull = if (elastic) rememberGlassPullState() else null
    val glass = when {
        outer != null -> Modifier.glassPart(outer, shape, pull)
        // An elastic piece needs room to stretch, which a one-piece group provides; same cost.
        pull != null -> {
            val own = rememberGlassGroup()
            Modifier.glassGroup(backdrop, own, style, mode).glassPart(own, shape, pull)
        }
        else -> Modifier.glass(backdrop, shape, style, mode)
    }
    Box(
        modifier = modifier
            .then(glass)
            .then(if (pull != null) Modifier.glassPullGesture(pull) else Modifier),
        contentAlignment = contentAlignment,
    ) {
        Box(
            modifier = if (pull != null) Modifier.pullContent(pull) else Modifier,
            contentAlignment = contentAlignment,
        ) {
            // Glass nested inside this surface is drawn on top of it, not merged into the group.
            CompositionLocalProvider(LocalGlassGroup provides null) { content() }
        }
    }
}

/**
 * A round glass button (a back button, an action). It is elastic: a press swells it, a drag
 * stretches it toward the finger like liquid, and it wobbles back on release. A press released
 * over the button is a click.
 */
@Composable
public fun GlassIconButton(
    onClick: () -> Unit,
    backdrop: GlassBackdrop,
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
        Box(Modifier.pullContent(pull), contentAlignment = Alignment.Center) { icon() }
    }
}

/** Moves and swells content with its elastic glass (a layer transform: no redraw). */
private fun Modifier.pullContent(pull: GlassPullState): Modifier = graphicsLayer {
    val shift = glassPullContentShift(pull.offset, DefaultMaxPull.toPx())
    translationX = shift.x
    translationY = shift.y
    scaleX = pull.scale
    scaleY = pull.scale
}

/**
 * A floating top bar: an optional round back button, a glass title capsule and trailing
 * actions, laid out below the status bar. All pieces (including [GlassIconButton]s in
 * [actions]) draw in one glass pass; the gaps between them stay clear. The back button and
 * title are elastic, and pulling one into its neighbour merges them like liquid.
 */
@Composable
public fun GlassTopBar(
    backdrop: GlassBackdrop,
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
