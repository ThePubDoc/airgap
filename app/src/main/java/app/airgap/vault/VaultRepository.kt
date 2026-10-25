package app.airgap.vault

import android.content.Context
import androidx.core.util.AtomicFile
import app.airgap.security.KeystoreKey
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Stores the cards as one encrypted file, using two keys:
 *
 * - A random 256-bit **data key** encrypts the vault file. It sits in memory only
 *   while the vault is unlocked, so adding or editing a card doesn't need another
 *   fingerprint prompt.
 * - The Keystore **master key** encrypts the data key. Unwrapping it needs the
 *   user's approval through BiometricPrompt, once per unlock.
 *
 * Both files live in `noBackupFilesDir`, which Android never backs up or transfers.
 *
 * Not thread-safe: call it from one thread at a time.
 */
class VaultRepository(context: Context) {
    private val directory = context.noBackupFilesDir
    private val keyFile = AtomicFile(File(directory, "vault.key"))
    private val dataFile = AtomicFile(File(directory, "vault.bin"))
    private val json = Json { ignoreUnknownKeys = true }

    private var dataKey: ByteArray? = null

    val isCreated: Boolean get() = keyFile.baseFile.exists()

    /** A Cipher for the master key. The user must approve it with BiometricPrompt before [unlock]. */
    fun unlockCipher(): Cipher =
        if (isCreated) {
            KeystoreKey.decryptCipher(unframe(keyFile.readFully()).iv)
        } else {
            KeystoreKey.encryptCipher()
        }

    /** Takes the Cipher BiometricPrompt approved. Creates the vault on first run. */
    fun unlock(approvedCipher: Cipher): List<Card> {
        if (isCreated) {
            dataKey = approvedCipher.doFinal(unframe(keyFile.readFully()).body)
            return try {
                load()
            } catch (e: Exception) {
                lock()
                throw e
            }
        }
        val newKey = ByteArray(DATA_KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = approvedCipher.doFinal(newKey)
        writeAtomically(keyFile, frame(approvedCipher.iv, wrapped))
        dataKey = newKey
        save(emptyList())
        return emptyList()
    }

    fun save(cards: List<Card>) {
        val key = checkNotNull(dataKey) { "Vault is locked" }
        val plaintext = json.encodeToString(VaultContents.serializer(), VaultContents(cards = cards)).encodeToByteArray()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
                updateAAD(AAD)
            }
            writeAtomically(dataFile, frame(cipher.iv, cipher.doFinal(plaintext)))
        } finally {
            plaintext.fill(0)
        }
    }

    /** Wipes the data key from memory. The cards can't be read again until the next unlock. */
    fun lock() {
        dataKey?.fill(0)
        dataKey = null
    }

    /** Permanently deletes every card and the master key. */
    fun erase() {
        lock()
        keyFile.delete()
        dataFile.delete()
        KeystoreKey.delete()
    }

    private fun load(): List<Card> {
        val key = checkNotNull(dataKey) { "Vault is locked" }
        // The key file is written before the first save, so a crash in between leaves no data file.
        if (!dataFile.baseFile.exists()) return emptyList()
        val sealed = unframe(dataFile.readFully())
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, sealed.iv))
            updateAAD(AAD)
        }
        val plaintext = cipher.doFinal(sealed.body)
        try {
            return json.decodeFromString(VaultContents.serializer(), plaintext.decodeToString()).cards
        } finally {
            plaintext.fill(0)
        }
    }

    private class Sealed(val iv: ByteArray, val body: ByteArray)

    /** File layout: [IV length: 1 byte][IV][ciphertext + GCM tag]. */
    private fun frame(iv: ByteArray, body: ByteArray): ByteArray = byteArrayOf(iv.size.toByte()) + iv + body

    private fun unframe(bytes: ByteArray): Sealed {
        val ivLength = bytes.first().toInt()
        return Sealed(
            iv = bytes.copyOfRange(1, 1 + ivLength),
            body = bytes.copyOfRange(1 + ivLength, bytes.size),
        )
    }

    private fun writeAtomically(file: AtomicFile, bytes: ByteArray) {
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: IOException) {
            file.failWrite(stream)
            throw e
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val DATA_KEY_BYTES = 32
        val AAD = "airgap.vault.v1".encodeToByteArray()
    }
}
