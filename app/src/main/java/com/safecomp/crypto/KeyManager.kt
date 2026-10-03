package com.safecomp.crypto

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.security.KeyStore

/**
 * Owns SafeComp's encryption keys.
 *
 * Key hierarchy:
 *   Android Keystore master key (AES-256, generated inside the Keystore, can never be exported)
 *     └─ wraps (encrypts) two Tink keysets stored in a private SharedPreferences file:
 *          • file keyset: AES-256-GCM-HKDF streaming, for file contents
 *          • metadata keyset: AES-256-GCM, for small values like file names
 *
 * Nothing is hardcoded. The keysets are generated randomly on first use. On disk they only
 * exist encrypted under the Keystore key, and the Keystore key never leaves the Keystore
 * (it's hardware-backed on most phones). So copying the app's files off the phone, e.g. from
 * a backup or a forensic image, gives an attacker ciphertext and wrapped keys, but no way to
 * unwrap them.
 */
class KeyManager(private val context: Context) {

    init {
        AeadConfig.register()
        StreamingAeadConfig.register()
    }

    @Volatile private var fileAead: StreamingAead? = null
    @Volatile private var metadataAead: Aead? = null

    @Synchronized
    fun fileCipher(): FileCipher = FileCipher(
        fileAead ?: loadKeyset(FILE_KEYSET, KeyTemplate.createFrom(PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB))
            .getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
            .also { fileAead = it },
    )

    @Synchronized
    fun metadataCipher(): MetadataCipher = MetadataCipher(
        metadataAead ?: loadKeyset(METADATA_KEYSET, KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            .also { metadataAead = it },
    )

    private fun loadKeyset(name: String, template: KeyTemplate): KeysetHandle {
        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(context, name, PREF_FILE)
            .withKeyTemplate(template)
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
        // If the Keystore is broken, Tink silently falls back to storing the keyset in
        // plaintext. For SafeComp that would quietly void the whole security model, so we
        // refuse to continue instead (fail closed).
        if (!manager.isUsingKeystore) {
            throw IllegalStateException("Android Keystore is unavailable; refusing to store keys unprotected.")
        }
        return manager.keysetHandle
    }

    /**
     * Crypto-shredding: delete the keysets and the Keystore master key. Any ciphertext left
     * anywhere (in flash memory blocks, old backups) becomes permanently unreadable.
     */
    @Synchronized
    fun destroyKeys() {
        fileAead = null
        metadataAead = null
        context.deleteSharedPreferences(PREF_FILE)
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
        }
    }

    private companion object {
        const val PREF_FILE = "safecomp_keysets"
        const val FILE_KEYSET = "file_keyset"
        const val METADATA_KEYSET = "metadata_keyset"
        const val MASTER_KEY_ALIAS = "safecomp_master_key"
        const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
    }
}
