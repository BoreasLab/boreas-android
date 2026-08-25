package dev.boreaslab.boreas.core

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import dev.boreaslab.boreas.BuildConfig
import dev.boreaslab.boreas.model.TypedFailure

/**
 * The six functions, plus the version check and the Android bypass builder.
 *
 * Declared against the shipped `boreas.h`, at the widths api/abi.md fixes. Every
 * one of them returns a `BoreasStatus` and nothing signals failure any other way;
 * none sets `errno`.
 *
 * Every one of them also blocks the calling thread. There is no async variant, by
 * design, and `nextEvent` in particular parks indefinitely, so nothing here may be
 * called from the main thread.
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

/** The library, or the reason there is not one. A closed set, resolved exactly once. */
internal sealed interface CoreLibrary {
    data class Linked(val library: BoreasLibrary) : CoreLibrary
    data class Absent(val failure: TypedFailure) : CoreLibrary
}

/**
 * Loads the shared object and refuses it if it is not the one this app was built
 * against.
 *
 * The comparison happens here, before anything else, because that is the only
 * cheap moment. A library whose ABI differs reads every field at the wrong offset
 * and behaves inexplicably; there is no later point at which the cause is
 * recoverable from the symptom.
 *
 * Resolved once and remembered. Both outcomes are values rather than exceptions,
 * so a device where the load fails shows a sentence instead of a crash, and the
 * failure is one the lifecycle already knows how to display.
 */
internal object BoreasCore {

    private const val LIBRARY = "boreas"

    val library: CoreLibrary by lazy { load() }

    private fun load(): CoreLibrary {
        val loaded = try {
            Native.load(LIBRARY, BoreasLibrary::class.java)
        } catch (error: UnsatisfiedLinkError) {
            // The dynamic linker refused it. On a device that usually means a
            // dependency of the .so is not in the APK, which is a packaging fact
            // and not something the user can act on.
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

        // A callback that throws would otherwise return whatever was in the return
        // register. Every callback below also catches for itself; this is the net
        // under that, and it is why nothing here can turn a Kotlin bug into a
        // packet written from uninitialised memory.
        Native.setCallbackExceptionHandler { _, _ -> }

        return CoreLibrary.Linked(loaded)
    }
}
