package dev.boreaslab.boreas.engine

import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.SessionStatus
import dev.boreaslab.boreas.model.TypedFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Release host until shared engine is linked; every start returns a typed refusal. */
public object UnlinkedEngineHost : EngineHost {

    override val isAvailable: Boolean = false

    override suspend fun start(engine: EngineConfig, platform: PlatformConfig): EngineStart =
        EngineStart.Refused(TypedFailure.EngineUnavailable)

    override suspend fun stop(session: SessionId, reason: StopReason): Unit = Unit

    override fun status(session: SessionId): Flow<SessionStatus> = emptyFlow()
}
