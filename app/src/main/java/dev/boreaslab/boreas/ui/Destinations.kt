package dev.boreaslab.boreas.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasIcons

/**
 * Every place the reader can be. A closed set.
 *
 * Each destination's [label] is both its navigation entry and its screen title, so
 * the two can never drift apart. The top-level destinations are peers reached from
 * the bar; the rest are details reached from Settings and are two levels deep at
 * most.
 */
sealed interface Destination {
    val route: String

    @get:StringRes
    val label: Int

    /**
     * The destinations in the navigation bar, in bar order.
     *
     * An enum rather than sealed objects so that the bar's contents and the type's
     * membership are the same declaration. As a set of objects this needed a list
     * beside it naming which ones the bar shows, and that list was a second source
     * of truth: adding a peer and forgetting the list compiled cleanly and lost the
     * destination from the bar. `entries` is generated from the declaration, so
     * there is nothing left to forget.
     */
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

    /** A destination reached from Settings. Not in the bar, so it carries no icon. */
    enum class Detail(
        override val route: String,
        @param:StringRes override val label: Int,
    ) : Destination {
        Tunnel("settings/tunnel", R.string.tunnel_title),
        Apps("settings/apps", R.string.apps_title),
        Certificate("settings/certificate", R.string.certificate_title),
        Appearance("settings/appearance", R.string.appearance_title),
        Diagnostics("settings/diagnostics", R.string.diagnostics_title),
        About("settings/about", R.string.about_title),
    }
}
