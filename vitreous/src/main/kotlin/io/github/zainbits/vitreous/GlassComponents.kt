package io.github.zainbits.vitreous

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A glass panel with content on top. Look and mode come from [LocalGlassStyle]/[LocalGlassMode].
 * Inside a [GlassGroup] it becomes one of the group's pieces instead of a pass of its own.
 */
@Composable
public fun GlassSurface(
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    shape: GlassShape = GlassShape.rounded(24.dp),
    style: GlassStyle = LocalGlassStyle.current,
    mode: GlassMode = LocalGlassMode.current,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val group = LocalGlassGroup.current
    Box(
        modifier = if (group != null) modifier.glassPart(group, shape) else modifier.glass(backdrop, shape, style, mode),
        contentAlignment = contentAlignment,
    ) {
        // Glass nested inside this surface is drawn on top of it, not merged into the group.
        CompositionLocalProvider(LocalGlassGroup provides null) { content() }
    }
}

/**
 * A round glass button (a back button, an action). Pressing swells it slightly; the swell is a
 * layer transform, so the glass is not re-rendered on the UI thread while it animates.
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "glassPress",
    )
    val group = LocalGlassGroup.current
    val glass = if (group != null) {
        // One piece of the group's pass; the swell only changes the group's shader uniforms.
        Modifier.glassPart(group, GlassShape.Capsule) { scale }
    } else {
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glass(backdrop, GlassShape.Capsule, style, mode)
    }
    Box(
        modifier = modifier
            .size(size)
            .then(glass)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/**
 * A floating top bar: an optional round back button, a glass title capsule and trailing
 * actions, laid out below the status bar. All pieces (including [GlassIconButton]s in
 * [actions]) draw in one glass pass; the gaps between them stay clear.
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
            ) {
                Box(Modifier.padding(horizontal = 18.dp)) { title() }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
    }
}
