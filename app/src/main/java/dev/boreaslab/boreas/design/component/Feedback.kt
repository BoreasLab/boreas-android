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

/** Closed container states; [Empty] and [Filtered] need different copy and actions. */
sealed interface ContainerState<out T> {
    data object Loading : ContainerState<Nothing>
    data class Failed(val message: String, val retry: (() -> Unit)? = null) : ContainerState<Nothing>
    data object Empty : ContainerState<Nothing>
    data class Filtered(val clearFilter: () -> Unit) : ContainerState<Nothing>
    data class Ready<T>(val value: T) : ContainerState<T>
}

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

/** Inline loading state announced politely. */
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

enum class NoticeTone { Info, Warning, Danger }

/** Persistent notice with icon and text, not color alone. */
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
