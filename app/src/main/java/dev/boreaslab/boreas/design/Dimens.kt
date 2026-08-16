package dev.boreaslab.boreas.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 4dp spacing scale; handheld section separation uses [xxl]. */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Radius {
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(6.dp)
    val md = RoundedCornerShape(8.dp)
    val lg = RoundedCornerShape(12.dp)
    val xl = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/** State motion; reduced mode uses zero-duration transitions. */
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

object Target {
    val minimum = 48.dp
    val row = 56.dp
    val control = 40.dp
}

object Stroke {
    val hairline = 1.dp
    val border = 1.dp

    val borderStrong = 2.dp
    val focus = 2.dp
    val focusGap = 2.dp
    val indicator = 2.dp
}

/** Three shared icon sizes for controls, rows, and empty states. */
object IconSize {
    val sm = 16.dp
    val md = 20.dp
    val lg = 24.dp
}
