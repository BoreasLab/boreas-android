package dev.boreaslab.boreas.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.boreaslab.boreas.design.BoreasIcons
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.IconSize
import dev.boreaslab.boreas.design.Radius
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.Stroke
import dev.boreaslab.boreas.design.Target

/**
 * A labelled text field.
 *
 * The label sits above the field and stays there. A placeholder that doubles as a
 * label disappears exactly when the reader needs it and cannot be relied on for
 * recall, so this component has no placeholder parameter at all.
 *
 * Help text is always visible rather than hidden behind an icon, and the error
 * replaces it in the same position so nothing below the field moves when
 * validation resolves.
 *
 * @param error the problem with the current value, already turned into copy that
 *   names the fix. Presence of an error is announced through the semantics error
 *   property as well as shown, so it does not depend on seeing the color.
 */
@Composable
fun BoreasTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    help: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = BoreasTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val outline = when {
        !enabled -> colors.hairline
        error != null -> colors.danger
        focused -> colors.primary
        else -> colors.border
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(
            text = label,
            style = BoreasTheme.type.label,
            color = if (enabled) colors.body else colors.muted,
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            interactionSource = interaction,
            textStyle = BoreasTheme.type.code.copy(
                color = if (enabled) colors.ink else colors.muted,
            ),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .background(if (enabled) colors.canvas else colors.surface, Radius.md)
                .border(if (focused) Stroke.borderStrong else Stroke.border, outline, Radius.md)
                .defaultMinSize(minHeight = Target.minimum)
                .padding(horizontal = Space.sm, vertical = Space.sm)
                .semantics { if (error != null) this.error(error) },
        )

        // One slot for help and error, so resolving a problem never shifts the layout.
        if (error != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = BoreasIcons.AlertCircle,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(IconSize.sm),
                )
                Text(error, style = BoreasTheme.type.bodySm, color = colors.danger)
            }
        } else if (help != null) {
            Text(help, style = BoreasTheme.type.bodySm, color = colors.muted)
        }
    }
}
