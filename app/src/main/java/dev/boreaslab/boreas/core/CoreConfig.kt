package dev.boreaslab.boreas.core

import dev.boreaslab.boreas.engine.CaMaterial
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.Filtering
import dev.boreaslab.boreas.model.Interception

/**
 * One trusted domain value turned into one `BoreasConfig`.
 *
 * This is the only place a `BoreasConfig` is filled in, and it is a total function
 * of its inputs: every combination the ABI would refuse is one the domain types
 * cannot express, so there is nothing here to validate and no branch that can be
 * forgotten. Document rewriting without interception, interception without
 * filtering, and filtering without a resolver are all unrepresentable upstream of
 * this file.
 *
 * The two halves of the authority travel together in [CaMaterial] for the same
 * reason: supplying one without the other is a configuration error, and here it is
 * not a thing to get wrong.
 */
internal class CoreConfig(
    private val engine: EngineConfig,
    /** The number the interface was built with. The same one, not a second opinion. */
    val mtu: Int,
    /** Restored material, or absent to have the core generate a fresh authority. */
    private val stored: CaMaterial?,
) {

    /**
     * Writes the struct, borrowing from [arena] for the duration of the call.
     *
     * Every pointer written here is read before `boreas_tunnel_start` returns and
     * copied by it, so the arena closes as soon as that call does.
     */
    fun marshal(arena: NativeArena): BoreasConfig = BoreasConfig().also { config ->
        // Direct is the only egress this app can produce; see EngineConfig. The
        // WireGuard half of the struct stays zeroed, which the ABI does not read.
        config.egress = BoreasConfig.EGRESS_DIRECT
        config.natBehavior = engine.nat.code
        config.mtu = mtu.toShort()

        when (val filtering = engine.filtering) {
            Filtering.Off -> {
                // A null resolver forwards questions untouched, which is legal
                // exactly because there are no lists to decide them against.
                config.resolver = null
            }

            is Filtering.Names -> {
                config.resolver = arena.utf8(filtering.upstream.text)
                config.lists = arena.utf8Array(filtering.lists)
                config.listCount = SizeT.of(filtering.lists.size)
                filtering.interception?.let { intercept(config, arena, it) }
            }
        }
    }

    private fun intercept(config: BoreasConfig, arena: NativeArena, interception: Interception) {
        val hosts = interception.hosts.map { it.text }
        config.interceptHosts = arena.utf8Array(hosts)
        config.interceptHostCount = SizeT.of(hosts.size)
        config.rewriteDocuments = if (interception.rewriteDocuments) 1 else 0

        // Both halves or neither. Absent means generate, which is the first run and
        // every run after the user was asked to trust a replacement.
        stored?.let { material ->
            config.rootCertificate = arena.bytes(material.certificate)
            config.rootCertificateLength = SizeT.of(material.certificate.size)
            config.authorityKeys = arena.bytes(material.keys)
            config.authorityKeysLength = SizeT.of(material.keys.size)
        }
    }
}
