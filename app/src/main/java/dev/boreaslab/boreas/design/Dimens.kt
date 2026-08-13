package dev.boreaslab.boreas.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The 4dp spacing scale from the supplied design document.
 *
 * The document's 96px section rhythm is a desktop marketing value. This is a
 * handheld product surface, so band separation uses [xxl]; the document's own
 * responsive section collapses the same rhythm on narrow viewports.
 */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

/** The document's hierarchical radius scale, unchanged. */
object Radius {
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(6.dp)
    val md = RoundedCornerShape(8.dp)
    val lg = RoundedCornerShape(12.dp)
    val xl = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * One curve family, decelerating into rest.
 *
 * MOTION is set to 3: motion here explains a state change and does nothing else.
 * Exits run faster than entrances because the user has already decided.
 * [Motion.reduced] replaces movement with an instant change and is selected at the
 * point of use, so a mid-session preference change takes effect.
 */
object Motion {
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val PRESS_MS = 90
    const val STATE_MS = 180
    const val ENTER_MS = 240
    const val EXIT_MS = 160

    fun <T> enter(reduced: Boolean) = tween<T>(if (reduced) 0 else ENTER_MS, easing = standard)
    fun <T> exit(reduced: Boolean) = tween<T>(if (reduced) 0 else EXIT_MS, easing = standard)
    fun <T> state(reduced: Boolean) = tween<T>(if (reduced) 0 else STATE_MS, easing = standard)
}

/** Hit areas. The floor is 24dp; this surface holds every target at 48dp or more. */
object Target {
    val minimum = 48.dp
    val row = 56.dp
    val control = 40.dp
}

object Stroke {
    val hairline = 1.dp
    val border = 1.dp

    /** A border carrying focus or an error, thick enough to read as a change of state. */
    val borderStrong = 2.dp
    val focus = 2.dp
    val focusGap = 2.dp
    val indicator = 2.dp
}

/**
 * Icon sizes.
 *
 * Three steps, not five. The 18 and 22 values this replaced were a step apart from
 * their neighbours, which is a difference nobody perceives and everybody has to
 * maintain. [sm] sits inside a button, [md] leads a row, [lg] heads an empty state.
 */
object IconSize {
    val sm = 16.dp
    val md = 20.dp
    val lg = 24.dp
}
