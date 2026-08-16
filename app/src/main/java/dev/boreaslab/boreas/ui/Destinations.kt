package dev.boreaslab.boreas.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasIcons

/** Closed set of destinations; each label also serves as its screen title. */
sealed interface Destination {
    val route: String

    @get:StringRes
    val label: Int

    /** Enum keeps bar membership and bar order in one declaration. */
    enum class TopLevel(
        override val route: String,
        @param:StringRes override val label: Int,
        val icon: ImageVector,
    ) : Destination {
        Shield("shield", R.string.nav_shield, BoreasIcons.Shield),
        Activity("activity", R.string.nav_activity, BoreasIcons.Activity),
        Policy("policy", R.string.nav_policy, BoreasIcons.Policy),
        Settings("settings", R.string.nav_settings, BoreasIcons.Settings),
    }

    /** Settings detail destination; not shown in the bar. */
    enum class Detail(
        override val route: String,
        @param:StringRes override val label: Int,
    ) : Destination {
        Tunnel("settings/tunnel", R.string.tunnel_title),
        Apps("settings/apps", R.string.apps_title),
        Certificate("settings/certificate", R.string.certificate_title),
        Diagnostics("settings/diagnostics", R.string.diagnostics_title),
        About("settings/about", R.string.about_title),
    }
}
