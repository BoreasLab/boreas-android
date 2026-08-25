package dev.boreaslab.boreas.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.design.Space
import dev.boreaslab.boreas.service.ConsentBroker
import dev.boreaslab.boreas.service.ConsentOutcome
import dev.boreaslab.boreas.ui.Destination.Detail
import dev.boreaslab.boreas.ui.Destination.TopLevel
import dev.boreaslab.boreas.ui.activity.ActivityScreen
import dev.boreaslab.boreas.ui.policy.PolicyScreen
import dev.boreaslab.boreas.ui.settings.AboutScreen
import dev.boreaslab.boreas.ui.settings.AppsScreen
import dev.boreaslab.boreas.ui.settings.CertificateScreen
import dev.boreaslab.boreas.ui.settings.DiagnosticsScreen
import dev.boreaslab.boreas.ui.settings.SettingsScreen
import dev.boreaslab.boreas.ui.settings.TunnelScreen
import dev.boreaslab.boreas.ui.shield.ShieldScreen
import kotlinx.coroutines.launch

/** Navigation shell and consent bridge; screens receive values and callbacks. */
@Composable
fun BoreasApp(viewModel: BoreasViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    ConsentBridge(viewModel)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BoreasTheme.colors.canvas),
    ) {
        BoreasNavGraph(
            navController = navController,
            viewModel = viewModel,
            modifier = Modifier.weight(1f),
        )
        if (TopLevel.entries.any { it.route == route }) {
            BoreasNavigationBar(
                current = route,
                onSelect = { destination ->
                    navController.navigate(destination.route) {
                        // Preserve one back-stack entry and saved state per peer destination.
                        popUpTo(TopLevel.Shield.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

/** Bridges service consent to the Activity result API while composed. */
@Composable
private fun ConsentBridge(viewModel: BoreasViewModel) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.deliverConsent(
            if (result.resultCode == Activity.RESULT_OK) {
                ConsentOutcome.Granted
            } else {
                ConsentOutcome.Denied
            },
        )
    }

    LaunchedEffect(Unit) {
        ConsentBroker.requests.collect { intent -> launcher.launch(intent) }
    }
}

/** Requests API 33+ notification permission before starting; denial does not block VPN start. */
@Composable
private fun rememberStartTunnel(viewModel: BoreasViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.startTunnel() }

    return {
        if (context.canPostNotifications()) {
            viewModel.startTunnel()
        } else {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** API <33 grants notification permission at install; API 33+ requires a runtime check. */
private fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** Returns a launcher only when the system provides a VPN settings Activity. */
@Composable
private fun rememberOpenVpnSettings(): (() -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        val intent = Intent(Settings.ACTION_VPN_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            null
        } else {
            { context.startActivity(intent) }
        }
    }
}

/**
 * Opens the screen a CA certificate has to be installed from.
 *
 * There is no public action that lands on the CA installer itself, so this is the
 * closest documented destination and the screen says what to do once there. See
 * CertificateScreen for why the one-tap intent is not used.
 */
@Composable
private fun rememberOpenSecuritySettings(): (() -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            null
        } else {
            { context.startActivity(intent) }
        }
    }
}

@Composable
private fun BoreasNavGraph(
    navController: NavHostController,
    viewModel: BoreasViewModel,
    modifier: Modifier = Modifier,
) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val alwaysOn by viewModel.alwaysOn.collectAsStateWithLifecycle()
    val back: () -> Unit = { navController.popBackStack() }

    val startTunnel = rememberStartTunnel(viewModel)
    val openVpnSettings = rememberOpenVpnSettings()
    val openSecuritySettings = rememberOpenSecuritySettings()

    // Clipboard writes are cancelled with this composition rather than outliving it.
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.diagnostics_clip_label)

    NavHost(
        navController = navController,
        startDestination = TopLevel.Shield.route,
        modifier = modifier,
    ) {
        composable(TopLevel.Shield.route) {
            ScreenScaffold(title = stringResourceOf(TopLevel.Shield)) {
                val pending by viewModel.policyPending.collectAsStateWithLifecycle()
                ShieldScreen(
                    state = session,
                    policyPending = pending,
                    alwaysOn = alwaysOn,
                    onStart = startTunnel,
                    onStop = viewModel::stopTunnel,
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(TopLevel.Activity.route) {
            ScreenScaffold(title = stringResourceOf(TopLevel.Activity)) {
                val resolutions by viewModel.resolutions.collectAsStateWithLifecycle()
                ActivityScreen(
                    state = session,
                    resolutions = resolutions,
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(TopLevel.Policy.route) {
            ScreenScaffold(title = stringResourceOf(TopLevel.Policy)) {
                val draft by viewModel.policyDraft.collectAsStateWithLifecycle()
                val parse by viewModel.policyParse.collectAsStateWithLifecycle()
                val pending by viewModel.policyPending.collectAsStateWithLifecycle()
                PolicyScreen(
                    draft = draft,
                    parse = parse,
                    session = session,
                    pending = pending,
                    onChange = viewModel::setPolicyDraft,
                    onApply = viewModel::applyPolicy,
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(TopLevel.Settings.route) {
            ScreenScaffold(title = stringResourceOf(TopLevel.Settings)) {
                SettingsScreen(
                    onOpen = { navController.navigate(it.route) },
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(Detail.Tunnel.route) {
            ScreenScaffold(title = stringResourceOf(Detail.Tunnel), onBack = back) {
                val draft by viewModel.tunnelDraft.collectAsStateWithLifecycle()
                val parse by viewModel.tunnelParse.collectAsStateWithLifecycle()
                TunnelScreen(
                    draft = draft,
                    parse = parse,
                    session = session,
                    alwaysOn = alwaysOn,
                    onChange = viewModel::setTunnelDraft,
                    onOpenVpnSettings = openVpnSettings,
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(Detail.Apps.route) {
            ScreenScaffold(title = stringResourceOf(Detail.Apps), onBack = back) {
                val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
                val excluded by viewModel.excludedPackages.collectAsStateWithLifecycle()
                val search by viewModel.appSearch.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { viewModel.loadInstalledApps() }
                AppsScreen(
                    apps = apps,
                    excluded = excluded,
                    search = search,
                    alwaysOn = alwaysOn,
                    onSearch = viewModel::setAppSearch,
                    onToggle = viewModel::setAppExcluded,
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(Detail.Certificate.route) {
            ScreenScaffold(title = stringResourceOf(Detail.Certificate), onBack = back) {
                val installed by viewModel.certificateInstalled.collectAsStateWithLifecycle()
                val authority by viewModel.authority.collectAsStateWithLifecycle()
                val export by viewModel.export.collectAsStateWithLifecycle()
                CertificateScreen(
                    installed = installed,
                    authority = authority,
                    export = export,
                    onExport = viewModel::exportRootCertificate,
                    onOpenSecuritySettings = openSecuritySettings,
                    onInstalledChange = viewModel::setCertificateInstalled,
                    modifier = Modifier.screenPadding(),
                )
            }
        }


        composable(Detail.Diagnostics.route) {
            ScreenScaffold(title = stringResourceOf(Detail.Diagnostics), onBack = back) {
                val records by viewModel.transitions.collectAsStateWithLifecycle()
                val simulation by viewModel.simulationEnabled.collectAsStateWithLifecycle()
                DiagnosticsScreen(
                    records = records,
                    simulationAvailable = viewModel.simulationAvailable,
                    simulationEnabled = simulation,
                    onSimulationChange = viewModel::setSimulationEnabled,
                    onClear = viewModel::clearTransitions,
                    onRestore = viewModel::restoreTransitions,
                    onCopy = { transcript ->
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText(clipLabel, transcript)),
                            )
                        }
                    },
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(Detail.About.route) {
            ScreenScaffold(title = stringResourceOf(Detail.About), onBack = back) {
                AboutScreen(modifier = Modifier.screenPadding())
            }
        }
    }
}

@Composable
private fun stringResourceOf(destination: Destination) = stringResource(destination.label)

/** The single outer gutter, applied once here so no screen invents its own. */
private fun Modifier.screenPadding(): Modifier = this
    .fillMaxSize()
    .padding(horizontal = Space.md)
    .padding(bottom = Space.md)
