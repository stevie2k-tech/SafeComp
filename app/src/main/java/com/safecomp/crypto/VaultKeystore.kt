package com.safecomp.crypto

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricManager
import com.google.crypto.tink.Aead
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The phone's owner must authenticate (fingerprint, face or PIN) before the vault keys can be used. */
class AuthenticationRequiredException : GeneralSecurityException("Authenticate to unlock the vault keys")

/** Android destroyed the key: the screen lock was removed. Wrapped keys can never be recovered. */
class KeysDestroyedException : GeneralSecurityException("The vault keys were destroyed")

enum class KekState { ABSENT, USABLE, NEEDS_AUTHENTICATION, DESTROYED }

enum class CanaryState { ABSENT, VALID, INVALIDATED }

/**
 * SafeComp's two Android Keystore keys. Neither can ever leave the Keystore (hardware-backed
 * on most phones).
 *
 * 1. The KEY-ENCRYPTION KEY wraps the vault keysets. It requires user authentication: it can
 *    be used only within [AUTH_WINDOW_SECONDS] after the owner proved who they are with a
 *    strong fingerprint/face or the phone's PIN, pattern or password. Android destroys it if
 *    the screen lock is removed. It is a *time-window* key, so it accepts the PIN as well as a
 *    fingerprint; Android's docs say such keys are NOT destroyed when a new fingerprint is
 *    added (only per-use, fingerprint-only keys are).
 *
 * 2. The FINGERPRINT CHECK KEY ("canary") is fingerprint-only and per-use, so Android DOES
 *    destroy it when a fingerprint is added (or all are removed). It protects nothing; SafeComp
 *    only checks whether it still exists, to notice that the phone's fingerprints changed and
 *    ask for the PIN once instead.
 */
class VaultKeystore(private val context: Context) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** A screen lock (PIN, pattern or password) is set. Without one there is no owner to check. */
    fun isDeviceSecure(): Boolean = context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    // --- Key-encryption key ------------------------------------------------------------------

    fun kekState(): KekState {
        val key = secretKey(KEK_ALIAS) ?: return KekState.ABSENT
        return try {
            Cipher.getInstance(TRANSFORMATION).init(Cipher.ENCRYPT_MODE, key)
            KekState.USABLE
        } catch (e: KeyPermanentlyInvalidatedException) {
            KekState.DESTROYED
        } catch (e: UserNotAuthenticatedException) {
            KekState.NEEDS_AUTHENTICATION
        }
    }

    /** The key-encryption key as a Tink AEAD, created on first use. Needs a screen lock. */
    fun kek(): Aead {
        val key = secretKey(KEK_ALIAS) ?: createKek()
        return KeystoreAead(key)
    }

    private fun createKek(): SecretKey {
        if (!isDeviceSecure()) throw AuthenticationRequiredException()
        val spec = KeyGenParameterSpec.Builder(KEK_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // A new fingerprint is handled by the check key below, not by destroying files.
            .setInvalidatedByBiometricEnrollment(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        AUTH_WINDOW_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                } else {
                    @Suppress("DEPRECATION") // The API 26–29 way; accepts fingerprint or PIN.
                    setUserAuthenticationValidityDurationSeconds(AUTH_WINDOW_SECONDS)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    // --- Fingerprint check key ----------------------------------------------------------------

    fun canaryState(): CanaryState {
        val key = secretKey(CANARY_ALIAS) ?: return CanaryState.ABSENT
        return try {
            // A per-use key can be initialised without authentication; a destroyed one can't.
            Cipher.getInstance(TRANSFORMATION).init(Cipher.ENCRYPT_MODE, key)
            CanaryState.VALID
        } catch (e: KeyPermanentlyInvalidatedException) {
            CanaryState.INVALIDATED
        } catch (e: GeneralSecurityException) {
            CanaryState.VALID
        }
    }

    /** Replaces the check key with one tied to the fingerprints enrolled right now. */
    fun resetCanary() {
        deleteEntry(CANARY_ALIAS)
        val hasStrongBiometric = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if (!hasStrongBiometric || !isDeviceSecure()) return // Nothing to watch.
        val spec = KeyGenParameterSpec.Builder(CANARY_ALIAS, KeyProperties.PURPOSE_ENCRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        runCatching {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
        }
    }

    // --- Housekeeping ---------------------------------------------------------------------------

    fun deleteAll() {
        deleteEntry(KEK_ALIAS)
        deleteEntry(CANARY_ALIAS)
        deleteEntry(LEGACY_ALIAS)
    }

    fun deleteLegacyKey() = deleteEntry(LEGACY_ALIAS)

    private fun secretKey(alias: String): SecretKey? = keyStore.getKey(alias, null) as? SecretKey

    private fun deleteEntry(alias: String) {
        runCatching { keyStore.deleteEntry(alias) }
    }

    /** AES-256-GCM with a Keystore key: random 12-byte IV, stored in front of the ciphertext. */
    private class KeystoreAead(private val key: SecretKey) : Aead {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray = guarded {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            associatedData?.let { cipher.updateAAD(it) }
            cipher.iv + cipher.doFinal(plaintext)
        }

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray = guarded {
            if (ciphertext.size < IV_BYTES) throw GeneralSecurityException("Ciphertext too short")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ciphertext, 0, IV_BYTES))
            associatedData?.let { cipher.updateAAD(it) }
            cipher.doFinal(ciphertext, IV_BYTES, ciphertext.size - IV_BYTES)
        }

        /** Turns Android's exceptions into the two cases SafeComp must handle explicitly. */
        private inline fun guarded(block: () -> ByteArray): ByteArray = try {
            block()
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw KeysDestroyedException()
        } catch (e: UserNotAuthenticatedException) {
            throw AuthenticationRequiredException()
        }
    }

    companion object {
        /** How long one authentication keeps the vault keys usable (hardware-enforced). */
        const val AUTH_WINDOW_SECONDS = 5 * 60

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val KEK_ALIAS = "safecomp_master_key_v2"
        private const val CANARY_ALIAS = "safecomp_fingerprint_check"

        /** The pre-Phase-7 key (no authentication), used only to upgrade old installs. */
        const val LEGACY_ALIAS = "safecomp_master_key"
    }
}
