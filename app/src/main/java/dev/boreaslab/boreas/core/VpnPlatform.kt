package dev.boreaslab.boreas.core

import android.os.ParcelFileDescriptor
import dev.boreaslab.boreas.model.PlatformConfig

/**
 * The two things only a platform can supply, named as one seam.
 *
 * api/obligations.md calls these the client's half of the interface: a TUN device,
 * and a way to keep the core's own sockets out of it. Both are Android facts, and
 * both are things `BoreasVpnService` owns, so this is the whole of what the engine
 * host needs from the service and the whole of what a test would have to stand in
 * for.
 */
internal interface VpnPlatform {

    /** Builds the interface. The service is the only place `VpnService.Builder` appears. */
    fun establish(config: PlatformConfig): Establishment

    /** A bypass over this service. Not shared between sessions; the core releases it. */
    fun bypass(): VpnBypass
}

/** Whether Android gave us an interface. A closed set; `null` is a documented path. */
internal sealed interface Establishment {

    data class Established(val descriptor: ParcelFileDescriptor) : Establishment

    /**
     * `establish()` answered null.
     *
     * Documented rather than theoretical: it means the app is not prepared, or its
     * VPN permission was revoked between the consent grant and this call.
     */
    data object Refused : Establishment

    /** Android rejected the parameters, which is a configuration problem, not a race. */
    data class Rejected(val error: Throwable) : Establishment
}
