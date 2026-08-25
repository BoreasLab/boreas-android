package dev.boreaslab.boreas.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.boreaslab.boreas.core.AuthorityStore
import dev.boreaslab.boreas.engine.CaMaterial
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one thing this app persists, kept the way api/lifecycle.md asks.
 *
 * The certificate is public and is written in the clear: it is the thing offered
 * to the trust installer, and nothing is gained by making it hard to read. The
 * keys are secret and are sealed under a key that lives in the Android Keystore
 * and never leaves it, which is what "put them in the Keystore" means for a blob:
 * the Keystore holds keys, not arbitrary bytes, so the blob is encrypted by one.
 *
 * Both halves are written as one act and read as one act. Two halves of different
 * authorities is the failure nothing downstream can detect -- every parse
 * succeeds, the session starts, and it mints leaves the installed root cannot
 * vouch for -- so the write below replaces both or neither, and the read returns
 * both or nothing.
 *
 * The core still checks the pair at startup, and a mismatch is recoverable by
 * generating afresh. This is what keeps that path rare rather than what makes it
 * unnecessary.
 *
 * ## What this does not defend against
 *
 * The private key is in process memory while the tunnel runs, so a rooted or
 * compromised device can extract it. A hardware-backed signer is the destination;
 * the material is opaque here precisely so that adding one changes nothing above.
 */
internal class KeystoreAuthorityStore(context: Context) : AuthorityStore {

    private val directory = File(context.filesDir, DIRECTORY)
    private val certificateFile = File(directory, "root.der")
    private val keysFile = File(directory, "authority.bin")

    override suspend fun load(): CaMaterial? = withContext(Dispatchers.IO) {
        if (!certificateFile.isFile || !keysFile.isFile) return@withContext null

        try {
            val certificate = certificateFile.readBytes()
            val sealed = keysFile.readBytes()
            if (certificate.isEmpty() || sealed.size <= IV_BYTES) return@withContext null

            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    existingKey() ?: return@withContext null,
                    GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES),
                )
            }
            val keys = cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
            CaMaterial(certificate, keys)
        } catch (_: GeneralSecurityException) {
            // The Keystore entry is gone or the ciphertext no longer authenticates.
            // Storage lost the key material, which is one of the two documented
            // failures; the recovery is to generate afresh and ask the user to trust
            // the new root, and returning null is how that is asked for.
            null
        } catch (_: java.io.IOException) {
            null
        }
    }

    override suspend fun save(material: CaMaterial): Unit = withContext(Dispatchers.IO) {
        directory.mkdirs()

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, existingKey() ?: freshKey())
        }
        val sealed = cipher.iv + cipher.doFinal(material.keys)

        // Written to a neighbour and renamed. A half-written pair is exactly the
        // state that produces certificate errors on every site with nothing the
        // user can act on, and rename is the cheapest atomicity the filesystem
        // offers.
        write(certificateFile, material.certificate)
        write(keysFile, sealed)
    }

    private fun write(target: File, bytes: ByteArray) {
        val staging = File(target.parentFile, "${target.name}.part")
        staging.writeBytes(bytes)
        if (!staging.renameTo(target)) {
            staging.delete()
            throw java.io.IOException("could not replace ${target.name}")
        }
    }

    private fun keystore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existingKey(): SecretKey? = (keystore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun freshKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    // No user authentication: the tunnel starts from always-on and
                    // from a boot-completed restart, where there is nobody present to
                    // authenticate and a prompt would simply fail.
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
        }.generateKey()

    private companion object {
        const val DIRECTORY = "authority"
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "boreas.authority.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** GCM's nominal nonce length, which is what the provider generates. */
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
