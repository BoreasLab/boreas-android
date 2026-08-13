package dev.boreaslab.boreas.engine

import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.SessionStatus
import dev.boreaslab.boreas.model.TypedFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The only host in a release build.
 *
 * The shared engine is not linked into this repository yet, so every start is
 * refused with a typed reason the surface can explain. This is not a stub standing
 * in for behavior that exists elsewhere: it is the accurate answer for a build
 * that carries the Kotlin half of the product.
 */
object UnlinkedEngineHost : EngineHost {

    override val isAvailable = false

    override suspend fun start(engine: EngineConfig, platform: PlatformConfig): EngineStart =
        EngineStart.Refused(TypedFailure.EngineUnavailable)

    override suspend fun stop(session: SessionId, reason: StopReason) = Unit

    override fun status(session: SessionId): Flow<SessionStatus> = emptyFlow()
}
