package dev.boreaslab.boreas.service

/** Android-owned always-on state; the app observes and reports it but cannot set it. */
public sealed interface AlwaysOn {

    public data object Unobserved : AlwaysOn

    public data object Off : AlwaysOn

    /** @param lockdown whether Android blocks traffic while the tunnel is down. */
    public data class On(val lockdown: Boolean) : AlwaysOn
}
