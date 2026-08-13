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
import dev.boreaslab.boreas.ui.settings.AppearanceScreen
import dev.boreaslab.boreas.ui.settings.AppsScreen
import dev.boreaslab.boreas.ui.settings.CertificateScreen
import dev.boreaslab.boreas.ui.settings.DiagnosticsScreen
import dev.boreaslab.boreas.ui.settings.SettingsScreen
import dev.boreaslab.boreas.ui.settings.TunnelScreen
import dev.boreaslab.boreas.ui.shield.ShieldScreen
import kotlinx.coroutines.launch

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
        if (TopLevel.entries.any { it.route == route }) {
            BoreasNavigationBar(
                current = route,
                onSelect = { destination ->
                    navController.navigate(destination.route) {
                        // One entry per peer destination, and the reader's place
                        // inside each is restored when they come back to it.
                        popUpTo(TopLevel.Shield.route) { saveState = true }
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

/**
 * Starting the tunnel, with the notification permission asked for first.
 *
 * Android requires a VPN to run as a foreground service, and a foreground service
 * announces itself with a notification. From API 33 that notification is suppressed
 * unless the reader has granted POST_NOTIFICATIONS, so without this the tunnel would
 * run with no visible sign of it, which is the one thing a privacy tool must never
 * do. The request is made at the moment it becomes relevant rather than on launch,
 * so the reader is asked in the context that explains the answer.
 *
 * The tunnel starts either way. A declined notification is worth reporting, but it
 * is not a reason to refuse the thing the reader actually asked for.
 */
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

/** Below API 33 the permission is granted at install time, so there is nothing to ask. */
private fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Opening the system VPN settings, or null when no Activity handles that.
 *
 * Android owns the always-on switch, so the app can only hand the reader over. A
 * system image that ships no Activity for ACTION_VPN_SETTINGS is rare but real, and
 * on one the intent cannot be honored. Returning null rather than catching the
 * failure at the moment of the tap lets each caller leave the control out entirely,
 * which is the same rule the always-on card already follows: do not show a control
 * that would have to fail when used.
 */
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

    val startTunnel = rememberStartTunnel(viewModel)
    val openVpnSettings = rememberOpenVpnSettings()

    // Writing to the clipboard suspends, so it runs in a scope tied to this
    // composition and is cancelled with it rather than outliving the screen.
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
                ShieldScreen(
                    state = session,
                    savedConfig = engineConfig,
                    alwaysOn = alwaysOn,
                    onStart = startTunnel,
                    onStop = viewModel::stopTunnel,
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(TopLevel.Activity.route) {
            ScreenScaffold(title = stringResourceOf(TopLevel.Activity)) {
                ActivityScreen(state = session, modifier = Modifier.screenPadding())
            }
        }

        composable(TopLevel.Policy.route) {
            ScreenScaffold(title = stringResourceOf(TopLevel.Policy)) {
                val certificateInstalled by
                    viewModel.certificateInstalled.collectAsStateWithLifecycle()
                PolicyScreen(
                    config = engineConfig,
                    session = session,
                    certificateInstalled = certificateInstalled,
                    onProfile = viewModel::setProfile,
                    onInspectTls = viewModel::setInspectTls,
                    onUpstream = viewModel::setUpstream,
                    onOpenCertificate = { navController.navigate(Detail.Certificate.route) },
                    onRestart = {
                        viewModel.stopTunnel()
                        startTunnel()
                    },
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
                CertificateScreen(
                    installed = installed,
                    modifier = Modifier.screenPadding(),
                )
            }
        }

        composable(Detail.Appearance.route) {
            ScreenScaffold(title = stringResourceOf(Detail.Appearance), onBack = back) {
                val theme by viewModel.themeChoice.collectAsStateWithLifecycle()
                AppearanceScreen(
                    choice = theme,
                    onChoose = viewModel::setTheme,
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
