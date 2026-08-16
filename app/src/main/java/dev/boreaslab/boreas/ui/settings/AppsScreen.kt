package dev.boreaslab.boreas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasIcons
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.design.component.BoreasTextField
import dev.boreaslab.boreas.design.component.ContainerState
import dev.boreaslab.boreas.design.component.EmptyState
import dev.boreaslab.boreas.design.component.StateContainer
import dev.boreaslab.boreas.design.component.SwitchRow
import dev.boreaslab.boreas.design.component.NoticeCard
import dev.boreaslab.boreas.design.component.NoticeTone
import dev.boreaslab.boreas.service.AlwaysOn
import dev.boreaslab.boreas.ui.InstalledApp
import dev.boreaslab.boreas.ui.PreviewSurface

/** Per-app tunnel inclusion; stable package keys preserve switch state while filtering. */
@Composable
fun AppsScreen(
    apps: List<InstalledApp>?,
    excluded: Set<String>,
    search: String,
    alwaysOn: AlwaysOn,
    onSearch: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container: ContainerState<List<InstalledApp>> = when {
        apps == null -> ContainerState.Loading
        apps.isEmpty() && search.isNotBlank() -> ContainerState.Filtered { onSearch("") }
        apps.isEmpty() -> ContainerState.Empty
        else -> ContainerState.Ready(apps)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            text = stringResource(R.string.apps_intro),
            style = BoreasTheme.type.bodyMd,
            color = BoreasTheme.colors.body,
        )

        if (alwaysOn is AlwaysOn.On && alwaysOn.lockdown) {
            NoticeCard(
                tone = NoticeTone.Warning,
                title = stringResource(R.string.always_on_lockdown_apps),
            )
        }

        BoreasTextField(
            label = stringResource(R.string.apps_search),
            value = search,
            onValueChange = onSearch,
        )

        Text(
            text = if (excluded.isEmpty()) {
                stringResource(R.string.apps_none_excluded)
            } else {
                pluralStringResource(R.plurals.apps_excluded_count, excluded.size, excluded.size)
            },
            style = BoreasTheme.type.label,
            color = BoreasTheme.colors.muted,
        )

        StateContainer(
            state = container,
            loadingLabel = stringResource(R.string.apps_loading),
            empty = {
                EmptyState(
                    icon = BoreasIcons.Apps,
                    title = stringResource(R.string.apps_empty_title),
                    detail = stringResource(R.string.apps_empty_detail),
                )
            },
            filtered = { clear ->
                EmptyState(
                    icon = BoreasIcons.Search,
                    title = stringResource(R.string.apps_filtered_title),
                    detail = stringResource(R.string.container_filtered_detail),
                    actionLabel = stringResource(R.string.apps_clear_search),
                    onAction = clear,
                )
            },
        ) { list ->
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(list, key = { it.packageName }) { app ->
                    SwitchRow(
                        title = app.label,
                        detail = app.packageName,
                        checked = app.packageName !in excluded,
                        onCheckedChange = { uses -> onToggle(app.packageName, !uses) },
                    )
                }
            }
        }
    }
}

@Preview(name = "Apps: loading", showBackground = true)
@Composable
private fun AppsLoadingPreview() = PreviewSurface {
    AppsScreen(null, emptySet(), "", AlwaysOn.Off, {}, { _, _ -> })
}

@Preview(name = "Apps: no search match", showBackground = true)
@Composable
private fun AppsFilteredPreview() = PreviewSurface {
    AppsScreen(
        apps = emptyList(),
        excluded = emptySet(),
        search = "zzz",
        alwaysOn = AlwaysOn.Off,
        onSearch = {},
        onToggle = { _, _ -> },
    )
}
