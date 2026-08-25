package dev.boreaslab.boreas.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What is known about the stored root, without exposing the key half of the pair. */
data class AuthoritySummary(
    /** SHA-256 over the DER, the way a certificate viewer shows it. */
    val fingerprint: String,
)

/** Where the export got to. A closed set the screen eliminates exhaustively. */
sealed interface ExportState {
    data object Idle : ExportState
    data object Working : ExportState
    data class Written(val name: String) : ExportState
    data object NoAuthority : ExportState
    data class Failed(val reason: String) : ExportState
}

/**
 * Writes the root certificate somewhere the system's certificate installer can
 * reach it.
 *
 * This is the documented route, not a fallback. `KeyChain.createInstallIntent()`
 * is the obvious one-tap flow and it does not work for this:
 *
 * > Starting from `android.os.Build.VERSION_CODES#R`, the intent returned by this
 * > method cannot be used for installing CA certificates. Since CA certificates
 * > can only be installed via Settings, the app should provide the user with a
 * > file containing the CA certificate. One way to do this would be to use the
 * > `android.provider.MediaStore` API to write the certificate to the
 * > `MediaStore.Downloads` collection.
 * >
 * > -- AOSP, frameworks/base/keystore/java/android/security/KeyChain.java
 *
 * So the file goes to Downloads and the user is walked to Settings. `minSdk` is
 * 29, where the one-tap intent still works, but building a second flow for the
 * single API level that has it would be a path nobody can test and everybody has
 * to maintain.
 *
 * The certificate is public, so writing it to shared storage discloses nothing.
 * The key half never leaves [KeystoreAuthorityStore].
 */
class CertificateExport(private val context: Context) {

    private val store = KeystoreAuthorityStore(context)

    /** The stored root's fingerprint, or absent when no authority has been generated. */
    suspend fun summary(): AuthoritySummary? = withContext(Dispatchers.IO) {
        val material = store.load() ?: return@withContext null
        AuthoritySummary(fingerprint = fingerprint(material.certificate))
    }

    suspend fun writeToDownloads(): ExportState = withContext(Dispatchers.IO) {
        val material = store.load() ?: return@withContext ExportState.NoAuthority

        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE)
            // Nothing else may see it until the bytes are all there, so a half
            // written certificate is never offered to the installer.
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        try {
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
                ?: return@withContext ExportState.Failed("no download collection")

            resolver.openOutputStream(uri)?.use { stream -> stream.write(material.certificate) }
                ?: return@withContext ExportState.Failed("could not open the file")

            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            ExportState.Written(FILE_NAME)
        } catch (error: IOException) {
            ExportState.Failed(error.message ?: "write failed")
        } catch (error: IllegalStateException) {
            ExportState.Failed(error.message ?: "write failed")
        }
    }

    private fun fingerprint(der: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(der)
            .joinToString(":") { "%02X".format(it) }

    private companion object {
        const val FILE_NAME = "boreas-root.crt"

        /** What Android's installer recognises a CA certificate by. */
        const val MIME_TYPE = "application/x-x509-ca-cert"
    }
}
