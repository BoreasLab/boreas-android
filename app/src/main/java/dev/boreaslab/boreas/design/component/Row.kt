package dev.boreaslab.boreas.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.boreaslab.boreas.design.BoreasIcons
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.IconSize
import dev.boreaslab.boreas.design.Radius
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.Stroke
import dev.boreaslab.boreas.design.Target

/**
 * The row.
 *
 * Every list on this surface is built from this one component, so hit area, text
 * alignment, pressed feedback, and focus behave identically whether the row opens
 * a screen, toggles a setting, or picks one of a closed set. The three wrappers
 * below compose it rather than reimplement it.
 *
 * @param trailing content on the right. Passed in rather than switched on a flag,
 *   so a new kind of row needs no new parameter here.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    leading: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = BoreasTheme.colors

    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusRing(Radius.md, interaction)
            .background(if (pressed) colors.surfaceStrong else Color.Transparent, Radius.md)
            .then(clickable)
            .defaultMinSize(minHeight = Target.row)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (leading != null) {
            Icon(
                imageVector = leading,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(IconSize.md),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.xxs),
        ) {
            Text(title, style = BoreasTheme.type.titleSm, color = colors.ink)
            if (detail != null) {
                Text(detail, style = BoreasTheme.type.bodySm, color = colors.muted)
            }
        }
        trailing()
    }
}

/** A row that opens another screen. The chevron is decorative; the row carries the label. */
@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    leading: ImageVector? = null,
) {
    ListRow(
        title = title,
        detail = detail,
        leading = leading,
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = BoreasIcons.ChevronRight,
            contentDescription = null,
            tint = BoreasTheme.colors.muted,
            modifier = Modifier.size(IconSize.md),
        )
    }
}

/**
 * A row that toggles one setting.
 *
 * The whole row is the target, and the switch is cleared from the semantics tree so
 * assistive technology is offered one control rather than two that do the same
 * thing.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    leading: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = BoreasTheme.colors
    ListRow(
        title = title,
        detail = detail,
        leading = leading,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        modifier = modifier,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics { },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.muted,
                uncheckedTrackColor = colors.surfaceStrong,
                uncheckedBorderColor = colors.border,
            ),
        )
    }
}

/**
 * One option of a closed set.
 *
 * Selection is announced through the selectable role, and shown with a control and
 * a position rather than with color alone.
 */
@Composable
fun ChoiceRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = BoreasTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusRing(Radius.md, interaction)
            .background(
                when {
                    selected -> colors.surfaceStrong
                    pressed -> colors.surfaceStrong
                    else -> Color.Transparent
                },
                Radius.md,
            )
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .defaultMinSize(minHeight = Target.row)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics { },
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = colors.primary,
                unselectedColor = colors.border,
            ),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.xxs),
        ) {
            Text(title, style = BoreasTheme.type.titleSm, color = colors.ink)
            if (detail != null) {
                Text(detail, style = BoreasTheme.type.bodySm, color = colors.muted)
            }
        }
    }
}

/** A hairline between rows inside one group. Decoration, so it is contrast-exempt. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = Space.md),
        thickness = Stroke.hairline,
        color = BoreasTheme.colors.hairline,
    )
}
