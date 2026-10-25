package app.airgap.reminders

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.core.content.edit
import androidx.core.util.AtomicFile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * What the daily reminder check reads: card labels, due days and expiry dates.
 *
 * The vault itself can't be opened in the background (its key needs a fingerprint),
 * so this small list lives in its own file, encrypted with a separate Keystore key
 * that doesn't need one. It never holds card numbers or CVVs.
 */
class ReminderStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "reminders.bin"))
    private val sent = context.getSharedPreferences("airgap_reminders", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(ReminderCard.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    fun write(cards: List<ReminderCard>) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(json.encodeToString(serializer, cards).encodeToByteArray())
        val stream = file.startWrite()
        try {
            stream.write(byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + sealed)
            file.finishWrite(stream)
        } catch (e: IOException) {
            file.failWrite(stream)
            throw e
        }
    }

    fun read(): List<ReminderCard> {
        if (!file.baseFile.exists()) return emptyList()
        return runCatching {
            val bytes = file.readFully()
            val ivLength = bytes.first().toInt()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 1 + ivLength)))
            }
            json.decodeFromString(serializer, cipher.doFinal(bytes.copyOfRange(1 + ivLength, bytes.size)).decodeToString())
        }.getOrDefault(emptyList())
    }

    fun clear() {
        file.delete()
        sent.edit { clear() }
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    fun wasSent(key: String): Boolean = sent.contains(key)

    fun markSent(keys: List<String>) {
        sent.edit {
            keys.forEach { putLong(it, System.currentTimeMillis()) }
            // Keep the list short: forget reminders shown more than a year ago.
            val yearAgo = System.currentTimeMillis() - 366L * 24 * 60 * 60 * 1000
            sent.all.filter { (_, shownAt) -> (shownAt as? Long ?: 0L) < yearAgo }.keys.forEach(::remove)
        }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey = keyStore().getKey(ALIAS, null) as? SecretKey ?: generateKey()

    private fun generateKey(): SecretKey {
        fun generate(strongBox: Boolean): SecretKey {
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setIsStrongBoxBacked(strongBox)
                .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(spec)
                generateKey()
            }
        }
        return try {
            generate(strongBox = true)
        } catch (e: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    private companion object {
        const val ALIAS = "airgap.reminders"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
