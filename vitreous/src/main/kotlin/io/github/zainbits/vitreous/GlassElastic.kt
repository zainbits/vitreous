package io.github.zainbits.vitreous

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** How far a pulled piece can stretch toward the finger, by default. */
public val DefaultMaxPull: Dp = 22.dp

/**
 * How elastic glass feels, in Apple's spring terms so values can be compared with SwiftUI.
 *
 * Apple does not publish the Liquid Glass constants; the default starts from SwiftUI's
 * `.snappy` spring (bounce 0.15) with a slightly quicker response, to be tuned on device.
 *
 * @property bounce 0 = no overshoot, 0.3 = SwiftUI `.bouncy`, higher wobbles more (max 0.9).
 * @property response seconds the spring takes to settle, roughly (SwiftUI's `duration`).
 * @property maxPull how far a piece can stretch toward the finger.
 * @property stretch how much a piece lengthens per unit of pull (0 = it only moves).
 * @property pressScale swell while pressed (1 = none).
 */
@Immutable
public data class GlassPullSpec(
    val bounce: Float = 0.15f,
    val response: Float = 0.40f,
    val maxPull: Dp = DefaultMaxPull,
    val stretch: Float = 0.8f,
    val pressScale: Float = 1.08f,
) {
    internal val dampingRatio: Float get() = (1f - bounce).coerceIn(0.1f, 1f)
    internal val stiffness: Float get() {
        val w = (2.0 * Math.PI / response.coerceIn(0.05f, 3f)).toFloat()
        return w * w
    }
}

/** The pull feel elastic glass pieces use when none is passed explicitly. */
public val LocalGlassPullSpec: ProvidableCompositionLocal<GlassPullSpec> = compositionLocalOf { GlassPullSpec() }

/**
 * The elastic pull of one glass piece: how far the finger has dragged it (raw, unbounded) and
 * how much it swells while pressed. Both spring back on release with [spec].
 *
 * Only the group's shader numbers change while it moves; nothing is recomposed or re-recorded.
 */
@Stable
public class GlassPullState internal constructor(internal val scope: CoroutineScope, spec: GlassPullSpec) {
    internal val pull = Animatable(Offset.Zero, Offset.VectorConverter)
    internal val press = Animatable(1f)

    /** The feel; changes apply from the next press. */
    public var spec: GlassPullSpec = spec
        internal set

    /** Finger drag in pixels (raw). Read it only in draw or layer blocks. */
    public val offset: Offset get() = pull.value

    /** Press swell (1 = rest). Read it only in draw or layer blocks. */
    public val scale: Float get() = press.value

    internal fun down() {
        scope.launch { press.animateTo(spec.pressScale, pressSpring()) }
    }

    internal fun drag(to: Offset) {
        scope.launch { pull.snapTo(to) }
    }

    internal fun release() {
        scope.launch { press.animateTo(1f, pressSpring()) }
        scope.launch {
            pull.animateTo(Offset.Zero, spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness))
        }
    }

    // The swell settles a little quicker and calmer than the pull.
    private fun pressSpring() = spring<Float>(
        dampingRatio = (spec.dampingRatio + 0.1f).coerceAtMost(1f),
        stiffness = spec.stiffness * 1.5f,
    )
}

@Composable
public fun rememberGlassPullState(spec: GlassPullSpec = LocalGlassPullSpec.current): GlassPullState {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { GlassPullState(scope, spec) }
    state.spec = spec
    return state
}

/**
 * Lets the finger pull this element: press swells it, dragging stretches it toward the finger
 * (with rubber-band resistance), release springs it back. A press that ends without leaving the
 * element (beyond the touch slop) counts as a tap and calls [onTap].
 */
public fun Modifier.glassPullGesture(
    state: GlassPullState,
    enabled: Boolean = true,
    onTap: (() -> Unit)? = null,
): Modifier = if (!enabled) this else this.pointerInput(state, onTap) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        state.down()
        val slop = viewConfiguration.touchSlop
        var moved = false
        var last = down.position
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                last = change.position
                break
            }
            last = change.position
            val delta = change.position - down.position
            if (!moved && delta.getDistance() > slop) moved = true
            if (moved) {
                state.drag(delta)
                change.consume()
            }
        }
        val inside = last.x >= -slop && last.y >= -slop &&
            last.x <= size.width + slop && last.y <= size.height + slop
        state.release()
        if (onTap != null && inside) onTap()
    }
}

/**
 * Turns a raw drag into the piece's deformation. The stretch saturates at [maxPx] (rubber band),
 * the piece lengthens toward the finger while its far side stays almost anchored, and it thins
 * across the drag so it keeps roughly its area.
 */
internal class Elastic {
    var shiftX = 0f
    var shiftY = 0f
    var ux = 1f
    var uy = 0f
    var stretch = 1f
    var squash = 1f

    fun compute(pull: Offset, w: Float, h: Float, maxPx: Float, stretchFactor: Float) {
        val len = pull.getDistance()
        if (len < 0.01f || maxPx <= 0f) {
            shiftX = 0f; shiftY = 0f; ux = 1f; uy = 0f; stretch = 1f; squash = 1f
            return
        }
        ux = pull.x / len
        uy = pull.y / len
        val e = maxPx * (1f - exp(-len / maxPx))
        val along = abs(ux) * w + abs(uy) * h
        stretch = 1f + stretchFactor * e / along.coerceAtLeast(1f)
        squash = 1f / sqrt(stretch)
        shiftX = ux * e * 0.5f
        shiftY = uy * e * 0.5f
    }
}

/** Where a pulled piece's content should move so it rides with the glass (layer translation). */
public fun glassPullContentShift(pull: Offset, maxPx: Float): Offset {
    val len = pull.getDistance()
    if (len < 0.01f || maxPx <= 0f) return Offset.Zero
    val e = maxPx * (1f - exp(-len / maxPx))
    return Offset(pull.x / len * e * 0.5f, pull.y / len * e * 0.5f)
}
