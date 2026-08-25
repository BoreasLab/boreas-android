package dev.boreaslab.boreas.engine

import dev.boreaslab.boreas.model.CoreEvent
import dev.boreaslab.boreas.model.CoreStatus
import dev.boreaslab.boreas.model.EngineConfig
import dev.boreaslab.boreas.model.PlatformConfig
import dev.boreaslab.boreas.model.SessionId
import dev.boreaslab.boreas.model.TypedFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The host for a build with nothing linked. Every start is a typed refusal.
 *
 * It is not dead code now that the library ships: the pure module has no way to
 * load one, so this is what `:domain`'s own tests run against, and it is the
 * honest answer on a device where the shared object failed to load.
 */
public object UnlinkedEngineHost : EngineHost {

    override val isAvailable: Boolean = false

    override val simulated: Boolean = false

    override suspend fun start(engine: EngineConfig, platform: PlatformConfig): EngineStart =
        EngineStart.Refused(TypedFailure.EngineUnavailable)

    override fun events(session: SessionId): Flow<CoreEvent> = emptyFlow()

    override suspend fun reload(session: SessionId, lists: List<String>): CoreStatus =
        CoreStatus.Stopped

    override suspend fun authority(session: SessionId): AuthorityRead = AuthorityRead.None

    override suspend fun stop(session: SessionId, reason: StopReason): Unit = Unit
}
