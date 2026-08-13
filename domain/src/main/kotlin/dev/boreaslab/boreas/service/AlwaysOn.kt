package dev.boreaslab.boreas.service

/**
 * Whether Android is keeping this tunnel up on its own.
 *
 * Android calls this always-on VPN. The system starts the service without any
 * user interaction, restarts it when necessary, and optionally refuses to carry
 * traffic at all while the tunnel is down.
 *
 * Only the system can turn it on: an ordinary app cannot, and only a device or
 * profile owner may set it programmatically through
 * `DevicePolicyManager.setAlwaysOnVpnPackage`. So this type is something the app
 * observes and reports, never something it sets.
 *
 * [Unobserved] is not "off". `isAlwaysOn` is a method on the running service, so
 * the state is unreadable until the service has run at least once in this
 * process. Collapsing that into [Off] would let the interface state, as fact,
 * something it has never checked.
 */
sealed interface AlwaysOn {

    data object Unobserved : AlwaysOn

    data object Off : AlwaysOn

    /**
     * @param lockdown when true the system also blocks traffic while the tunnel
     *   is down, so nothing leaves the device unfiltered between sessions.
     */
    data class On(val lockdown: Boolean) : AlwaysOn
}
