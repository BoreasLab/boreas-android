package dev.boreaslab.boreas.ui

import android.app.Activity
import android.content.Intent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.boreaslab.boreas.design.BoreasTheme
import dev.boreaslab.boreas.service.ConsentBroker
import dev.boreaslab.boreas.service.ConsentOutcome
import dev.boreaslab.boreas.ui.activity.ActivityScreen
import dev.boreaslab.boreas.ui.policy.PolicyScreen
import dev.boreaslab.boreas.ui.settings.AboutScreen
import dev.boreaslab.boreas.ui.settings.AppearanceScreen
import dev.boreaslab.boreas.ui.settings.AppsScreen
import dev.boreaslab.boreas.ui.settings.CertificateScreen
import dev.boreaslab.boreas.ui.settings.DiagnosticsScreen
import dev.boreaslab.boreas.ui.settings.SettingsScreen
import dev.boreaslab.boreas.ui.settings.TunnelScreen
import dev.boreaslab.boreas.ui.shield.ShieldScreen

/**
 * The application shell.
 *
 * The only place that knows about navigation and about the consent Activity result.
 * Every screen below takes values and callbacks, which is what lets each one be
 * previewed without a navigation graph or a running service.
 */
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
        if (Destination.topLevel.any { it.route == route }) {
            BoreasNavigationBar(
                current = route,
                onSelect = { destination ->
                    navController.navigate(destination.route) {
                        // One entry per peer destination, and the reader's place
                        // inside each is restored when they come back to it.
                        popUpTo(Destination.Shield.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

/**
 * Carries the service's consent request to the Activity result API and back.
 *
 * The service has no window, so the request cannot be launched from there. This is
 * the one bridge between them, and it exists only while a screen is composed.
 */
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

@Composable
private fun BoreasNavGraph(
    navController: NavHostController,
    viewModel: BoreasViewModel,
    modifier: Modifier = Modifier,
) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val engineConfig by viewModel.engineConfig.collectAsStateWithLifecycle()
    val alwaysOn by viewModel.alwaysOn.collectAsStateWithLifecycle()
    val back: () -> Unit = { navController.popBackStack() }

    // Android owns the always-on switch, so the app can only hand the reader over.
    // ACTION_VPN_SETTINGS has existed since API 24; the guard covers a device whose
    // system image ships no Activity for it rather than a version difference.
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val openVpnSettings: () -> Unit = {
        val intent = Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    NavHost(
        navController = navController,
        startDestination = Destination.Shield.route,
        modifier = modifier,
    ) {
        composable(Destination.Shield.route) {
            ScreenScaffold(title = stringResourceOf(Destination.Shield)) {
                ShieldScreen(
                    state = session,
                    savedConfig = engineConfig,
                    alwaysOn = alwaysOn,
                    onStart = viewModel::startTunnel,
                    onStop = viewModel::stopTunnel,
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(Destination.Activity.route) {
            ScreenScaffold(title = stringResourceOf(Destination.Activity)) {
                ActivityScreen(state = session, modifier = Modifier.screenPadding())
            }
        }

        composable(Destination.Policy.route) {
            ScreenScaffold(title = stringResourceOf(Destination.Policy)) {
                val certificateInstalled by
                    viewModel.certificateInstalled.collectAsStateWithLifecycle()
                PolicyScreen(
                    config = engineConfig,
                    session = session,
                    certificateInstalled = certificateInstalled,
                    onProfile = viewModel::setProfile,
                    onInspectTls = viewModel::setInspectTls,
                    onUpstream = viewModel::setUpstream,
                    onOpenCertificate = { navController.navigate(Destination.Certificate.route) },
                    onRestart = {
                        viewModel.stopTunnel()
                        viewModel.startTunnel()
                    },
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(Destination.Settings.route) {
            ScreenScaffold(title = stringResourceOf(Destination.Settings)) {
                SettingsScreen(
                    onOpen = { navController.navigate(it.route) },
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(Destination.Tunnel.route) {
            ScreenScaffold(title = stringResourceOf(Destination.Tunnel), onBack = back) {
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

        composable(Destination.Apps.route) {
            ScreenScaffold(title = stringResourceOf(Destination.Apps), onBack = back) {
                val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
                val excluded by viewModel.excludedPackages.collectAsStateWithLifecycle()
                val search by viewModel.appSearch.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { viewModel.loadInstalledApps() }
                AppsScreen(
                    apps = apps,
                    excluded = excluded,
                    search = search,
                    alwaysOn = alwaysOn,
                    onSearch = { viewModel.appSearch.value = it },
                    onToggle = viewModel::setAppExcluded,
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(Destination.Certificate.route) {
            ScreenScaffold(title = stringResourceOf(Destination.Certificate), onBack = back) {
                val installed by viewModel.certificateInstalled.collectAsStateWithLifecycle()
                CertificateScreen(
                    installed = installed,
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(Destination.Appearance.route) {
            ScreenScaffold(title = stringResourceOf(Destination.Appearance), onBack = back) {
                val theme by viewModel.themeChoice.collectAsStateWithLifecycle()
                AppearanceScreen(
                    choice = theme,
                    onChoose = viewModel::setTheme,
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(Destination.Diagnostics.route) {
            ScreenScaffold(title = stringResourceOf(Destination.Diagnostics), onBack = back) {
                val records by viewModel.transitions.collectAsStateWithLifecycle()
                val simulation by viewModel.simulationEnabled.collectAsStateWithLifecycle()
                DiagnosticsScreen(
                    records = records,
                    simulationAvailable = viewModel.simulationAvailable,
                    simulationEnabled = simulation,
                    onSimulationChange = viewModel::setSimulationEnabled,
                    onClear = viewModel::clearTransitions,
                    onRestore = viewModel::restoreTransitions,
                    onCopy = { clipboard.setText(AnnotatedString(it)) },
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(Destination.About.route) {
            ScreenScaffold(title = stringResourceOf(Destination.About), onBack = back) {
                AboutScreen(modifier = Modifier.screenPadding())
            }
        }
    }
}

@Composable
private fun stringResourceOf(destination: Destination) =
    androidx.compose.ui.res.stringResource(destination.label)

/** The single outer gutter, applied once here so no screen invents its own. */
private fun Modifier.screenPadding(): Modifier = this
    .fillMaxSize()
    .padding(horizontal = dev.boreaslab.boreas.design.Space.md)
    .padding(bottom = dev.boreaslab.boreas.design.Space.md)
