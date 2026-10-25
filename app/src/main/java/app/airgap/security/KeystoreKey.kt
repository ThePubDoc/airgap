package app.airgap.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The hardware-backed master key. It never leaves the secure hardware, and every
 * use has to be approved by the user through BiometricPrompt (fingerprint, face,
 * or the device PIN). It only ever encrypts one thing: the vault's data key.
 */
internal object KeystoreKey {
    private const val ALIAS = "airgap.vault.master"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    private const val AUTHENTICATORS =
        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL

    /** For first run: creates the key if needed. Hand the result to BiometricPrompt. */
    fun encryptCipher(): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, existingKey() ?: generate()) }

    /** For unlocking. Hand the result to BiometricPrompt. */
    fun decryptCipher(iv: ByteArray): Cipher {
        // Android deletes auth-bound keys when the screen lock is removed. Without
        // the key the vault can't be decrypted, the same as an invalidated key.
        val key = existingKey() ?: throw KeyPermanentlyInvalidatedException("Vault key is missing")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
    }

    fun delete() {
        keyStore().deleteEntry(ALIAS)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(ALIAS, null) as? SecretKey

    private fun generate(): SecretKey =
        try {
            generate(strongBox = true)
        } catch (e: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // A timeout of 0 means each use needs its own approval, bound to that exact Cipher.
            .setUserAuthenticationParameters(0, AUTHENTICATORS)
            // The device PIN unlocks the vault anyway, so enrolling a new fingerprint
            // shouldn't make every saved card unrecoverable.
            .setInvalidatedByBiometricEnrollment(false)
            .setUnlockedDeviceRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(spec)
            generateKey()
        }
    }
}
