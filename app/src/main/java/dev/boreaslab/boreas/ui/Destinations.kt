package dev.boreaslab.boreas.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import dev.boreaslab.boreas.R
import dev.boreaslab.boreas.design.BoreasIcons

/**
 * Every place the reader can be. A closed set.
 *
 * Each destination's [label] is both its navigation entry and its screen title, so
 * the two can never drift apart. The four top-level destinations are peers reached
 * from the bar; the rest are details reached from Settings and are two levels deep
 * at most.
 */
sealed interface Destination {
    val route: String

    @get:StringRes
    val label: Int

    /** A destination in the navigation bar. */
    sealed interface TopLevel : Destination {
        val icon: ImageVector
    }

    data object Shield : TopLevel {
        override val route = "shield"
        override val label = R.string.nav_shield
        override val icon = BoreasIcons.Shield
    }

    data object Activity : TopLevel {
        override val route = "activity"
        override val label = R.string.nav_activity
        override val icon = BoreasIcons.Activity
    }

    data object Policy : TopLevel {
        override val route = "policy"
        override val label = R.string.nav_policy
        override val icon = BoreasIcons.Policy
    }

    data object Settings : TopLevel {
        override val route = "settings"
        override val label = R.string.nav_settings
        override val icon = BoreasIcons.Settings
    }

    data object Tunnel : Destination {
        override val route = "settings/tunnel"
        override val label = R.string.tunnel_title
    }

    data object Apps : Destination {
        override val route = "settings/apps"
        override val label = R.string.apps_title
    }

    data object Certificate : Destination {
        override val route = "settings/certificate"
        override val label = R.string.certificate_title
    }

    data object Appearance : Destination {
        override val route = "settings/appearance"
        override val label = R.string.appearance_title
    }

    data object Diagnostics : Destination {
        override val route = "settings/diagnostics"
        override val label = R.string.diagnostics_title
    }

    data object About : Destination {
        override val route = "settings/about"
        override val label = R.string.about_title
    }

    companion object {
        val topLevel = listOf(Shield, Activity, Policy, Settings)
    }
}
