package dev.boreaslab.boreas.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.boreaslab.boreas.design.BoreasIcons
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.IconSize
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.Stroke
import dev.boreaslab.boreas.R

/**
 * The state of anything that loads.
 *
 * A closed set rather than a bag of flags, so a container cannot be loading and
 * failed at once, and cannot silently render nothing. [Empty] and [Filtered] are
 * kept apart deliberately: "you have none" and "none match what you typed" need
 * different words and different actions, and collapsing them is the most common
 * way an empty screen ends up saying the wrong thing.
 *
 * There is no partial or paged variant. Nothing on this surface paginates, and a
 * state that can never be constructed is dead code rather than thoroughness.
 */
sealed interface ContainerState<out T> {
    data object Loading : ContainerState<Nothing>
    data class Failed(val message: String, val retry: (() -> Unit)? = null) : ContainerState<Nothing>
    data object Empty : ContainerState<Nothing>
    data class Filtered(val clearFilter: () -> Unit) : ContainerState<Nothing>
    data class Ready<T>(val value: T) : ContainerState<T>
}

/**
 * Renders a container's state, eliminating the set exhaustively.
 *
 * [empty] has no default because an empty state needs copy that names what belongs
 * there. The other branches have defaults that are complete designs rather than
 * placeholders, so a call site opts into different copy rather than out of a state.
 */
@Composable
fun <T> StateContainer(
    state: ContainerState<T>,
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    loadingLabel: String? = null,
    filtered: @Composable (clear: () -> Unit) -> Unit = { clear ->
        EmptyState(
            icon = BoreasIcons.Search,
            title = stringResource(R.string.container_filtered_title),
            detail = stringResource(R.string.container_filtered_detail),
            actionLabel = stringResource(R.string.container_clear_filter),
            onAction = clear,
        )
    },
    failed: @Composable (message: String, retry: (() -> Unit)?) -> Unit = { message, retry ->
        NoticeCard(
            tone = NoticeTone.Danger,
            title = message,
            actionLabel = if (retry != null) stringResource(R.string.action_retry) else null,
            onAction = retry,
        )
    },
    ready: @Composable (T) -> Unit,
) {
    Column(modifier) {
        when (state) {
            ContainerState.Loading -> LoadingRegion(loadingLabel)
            is ContainerState.Failed -> failed(state.message, state.retry)
            ContainerState.Empty -> empty()
            is ContainerState.Filtered -> filtered(state.clearFilter)
            is ContainerState.Ready -> ready(state.value)
        }
    }
}

/**
 * Waiting, announced politely.
 *
 * Shown in place rather than as an overlay, and only for waits long enough to be
 * worth acknowledging. Anything that resolves inside the flash threshold should
 * not reach this at all.
 */
@Composable
fun LoadingRegion(label: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Space.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(IconSize.sm),
            color = BoreasTheme.colors.primary,
            strokeWidth = Stroke.indicator,
        )
        if (label != null) {
            Text(label, style = BoreasTheme.type.bodySm, color = BoreasTheme.colors.muted)
        }
    }
}

/** What belongs here, why it is worth having, and the one action that populates it. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.xl, horizontal = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BoreasTheme.colors.muted,
            modifier = Modifier.size(IconSize.lg),
        )
        Text(title, style = BoreasTheme.type.titleMd, color = BoreasTheme.colors.ink)
        Text(detail, style = BoreasTheme.type.bodyMd, color = BoreasTheme.colors.muted)
        if (actionLabel != null && onAction != null) {
            BoreasButton(
                label = actionLabel,
                onClick = onAction,
                variant = ButtonVariant.Secondary,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

/** How loud a notice is. A closed set; each tone carries its own icon. */
enum class NoticeTone { Info, Warning, Danger }

/**
 * A message the reader must act on or must not miss.
 *
 * Persistent and adjacent to its cause, never a toast. Every tone pairs a color
 * with an icon and a written title, so the tone survives being read in greyscale
 * or by someone who does not perceive the hue difference between the accent and
 * the danger role.
 */
@Composable
fun NoticeCard(
    tone: NoticeTone,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = BoreasTheme.colors
    val accent = when (tone) {
        NoticeTone.Info -> colors.muted
        NoticeTone.Warning -> colors.warning
        NoticeTone.Danger -> colors.danger
    }
    val icon = when (tone) {
        NoticeTone.Info -> BoreasIcons.Info
        NoticeTone.Warning -> BoreasIcons.AlertTriangle
        NoticeTone.Danger -> BoreasIcons.AlertCircle
    }

    BoreasCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        surface = CardSurface.Filled,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(top = Space.xxs)
                    .size(IconSize.md),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(title, style = BoreasTheme.type.titleSm, color = colors.ink)
                if (detail != null) {
                    Text(detail, style = BoreasTheme.type.bodySm, color = colors.body)
                }
                if (actionLabel != null && onAction != null) {
                    BoreasButton(
                        label = actionLabel,
                        onClick = onAction,
                        variant = ButtonVariant.Quiet,
                        modifier = Modifier.padding(top = Space.xxs),
                    )
                }
            }
        }
    }
}
