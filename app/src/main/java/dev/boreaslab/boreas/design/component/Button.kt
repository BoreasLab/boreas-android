package dev.boreaslab.boreas.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.IconSize
import dev.boreaslab.boreas.design.LocalReducedMotion
import dev.boreaslab.boreas.design.Motion
import dev.boreaslab.boreas.design.Radius
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.Stroke
import dev.boreaslab.boreas.design.Target

/**
 * The one button.
 *
 * Varies along a single closed axis. A second axis would be composition, not a
 * flag: a button that needed a different structure would be a different component.
 */
enum class ButtonVariant {
    /** The accent fill. One per view, on the action that view exists for. */
    Primary,

    /** Outlined. Every action that is not the primary one. */
    Secondary,

    /** No fill, no outline. Inline actions inside a card or a row. */
    Quiet,

    /** Outlined in the danger role. Carries an icon so color is never the only signal. */
    Danger,
}

/**
 * @param loading keeps the label in place and adds an indicator beside it, so the
 *   control neither resizes nor moves under a thumb already on its way down.
 * @param enabled false is rare here. Where a control would be disabled, the
 *   surrounding copy says what would make it available.
 */
@Composable
fun BoreasButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Secondary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = BoreasTheme.colors
    val reduced = LocalReducedMotion.current
    val busy = androidx.compose.ui.res.stringResource(dev.boreaslab.boreas.R.string.container_busy)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val fill = when (variant) {
        ButtonVariant.Primary -> if (pressed) colors.primaryPressed else colors.primary
        ButtonVariant.Secondary, ButtonVariant.Quiet, ButtonVariant.Danger ->
            if (pressed) colors.surfaceStrong else Color.Transparent
    }
    val content = when (variant) {
        ButtonVariant.Primary -> colors.onPrimary
        ButtonVariant.Secondary -> colors.ink
        ButtonVariant.Quiet -> colors.primary
        ButtonVariant.Danger -> colors.danger
    }
    val outline = when (variant) {
        ButtonVariant.Primary, ButtonVariant.Quiet -> Color.Transparent
        ButtonVariant.Secondary -> colors.border
        ButtonVariant.Danger -> colors.danger
    }

    val animatedFill by animateColorAsState(
        targetValue = if (enabled) fill else colors.surfaceStrong,
        animationSpec = Motion.state(reduced),
        label = "buttonFill",
    )

    Box(
        modifier = modifier
            .focusRing(Radius.lg, interaction)
            .background(animatedFill, Radius.md)
            .border(Stroke.border, if (enabled) outline else Color.Transparent, Radius.md)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = Target.minimum)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) content else colors.muted,
                    modifier = Modifier.size(IconSize.sm),
                )
            }
            Text(
                text = label,
                style = BoreasTheme.type.button,
                color = if (enabled) content else colors.muted,
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(IconSize.sm)
                        .semantics { stateDescription = busy },
                    color = content,
                    strokeWidth = Stroke.indicator,
                )
            }
        }
    }
}
