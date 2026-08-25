package dev.boreaslab.boreas.core

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import dev.boreaslab.boreas.BuildConfig
import dev.boreaslab.boreas.model.TypedFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JNA declaration of the six blocking C functions, at the widths in api/abi.md.
 * Failures return [BoreasStatus] rather than setting `errno`; `nextEvent` may
 * park indefinitely, so callers must stay off the main thread.
 */
@Suppress("FunctionNaming") // These are C symbols; the names are not ours to choose.
internal interface BoreasLibrary : Library {

    fun boreas_abi_version(): Int

    fun boreas_tunnel_start(
        config: BoreasConfig,
        device: BoreasDevice,
        bypass: BoreasBypass,
        out: PointerByReference,
    ): Int

    fun boreas_tunnel_next_event(
        handle: Pointer,
        event: BoreasEvent,
        name: Pointer?,
        nameCapacity: SizeT,
        rule: Pointer?,
        ruleCapacity: SizeT,
    ): Int

    fun boreas_tunnel_reload(
        handle: Pointer,
        lists: Pointer?,
        count: SizeT,
        out: BoreasEvent,
    ): Int

    fun boreas_tunnel_authority(
        handle: Pointer,
        certificate: Pointer?,
        certificateCapacity: SizeT,
        certificateLength: SizeTByReference,
        keys: Pointer?,
        keysCapacity: SizeT,
        keysLength: SizeTByReference,
    ): Int

    fun boreas_tunnel_shutdown(handle: Pointer): Int

    fun boreas_tunnel_free(handle: Pointer?): Int
}

/** Library handle or its load failure, resolved exactly once. */
internal sealed interface CoreLibrary {
    data class Linked(val library: BoreasLibrary) : CoreLibrary
    data class Absent(val failure: TypedFailure) : CoreLibrary
}

/**
 * UI-visible load state without exposing the library handle. [Checking] covers
 * the off-main-thread `dlopen` of the 17 MB shared object.
 */
sealed interface EngineLoad {
    data object Checking : EngineLoad
    data class Linked(val abiVersion: Int) : EngineLoad
    data class Absent(val failure: TypedFailure) : EngineLoad
}

/**
 * Loads the shared object and rejects an ABI mismatch before any struct access.
 * The result is memoized as a value so load failures reach the lifecycle instead
 * of escaping as crashes.
 */
internal object BoreasCore {

    private const val LIBRARY = "boreas"

    val library: CoreLibrary by lazy { load() }

    /** Last defect JNA caught while returning from a callback. */
    @Volatile
    var lastCallbackDefect: Throwable? = null
        private set

    /** Resolves the load off the caller's thread without exposing the handle. */
    suspend fun describe(): EngineLoad = withContext(Dispatchers.IO) {
        when (val resolved = library) {
            is CoreLibrary.Linked -> EngineLoad.Linked(BuildConfig.BOREAS_ABI_VERSION)
            is CoreLibrary.Absent -> EngineLoad.Absent(resolved.failure)
        }
    }

    private fun load(): CoreLibrary {
        val loaded = try {
            Native.load(LIBRARY, BoreasLibrary::class.java)
        } catch (error: UnsatisfiedLinkError) {
            // A missing .so dependency is an APK packaging failure.
            return CoreLibrary.Absent(TypedFailure.CoreNotLoaded(error.message ?: "unlinkable"))
        } catch (error: NoClassDefFoundError) {
            return CoreLibrary.Absent(TypedFailure.CoreNotLoaded(error.message ?: "no binding"))
        }

        val reported = try {
            loaded.boreas_abi_version()
        } catch (error: UnsatisfiedLinkError) {
            return CoreLibrary.Absent(TypedFailure.CoreNotLoaded(error.message ?: "no version symbol"))
        }

        if (reported != BuildConfig.BOREAS_ABI_VERSION) {
            return CoreLibrary.Absent(
                TypedFailure.CoreAbiMismatch(compiled = BuildConfig.BOREAS_ABI_VERSION, loaded = reported),
            )
        }

        // Prevent callback exceptions from returning an undefined C value.
        Native.setCallbackExceptionHandler { _, error -> lastCallbackDefect = error }

        return CoreLibrary.Linked(loaded)
    }
}
