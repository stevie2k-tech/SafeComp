package com.safecomp.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.Parameters
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig

/** The vault keys aren't in memory: the owner hasn't authenticated in this session. */
class VaultKeysLockedException : IllegalStateException("Vault keys are locked")

/**
 * Owns SafeComp's encryption keys.
 *
 * Key hierarchy (Phase 7):
 *   Android Keystore key-encryption key: AES-256, never leaves the Keystore, and usable only
 *   within 5 minutes after the owner authenticated (see [VaultKeystore])
 *     └─ wraps two Tink keysets, stored encrypted in a private SharedPreferences file:
 *          • file keyset: AES-256-GCM-HKDF streaming, for file contents
 *          • metadata keyset: AES-256-GCM, for small values like file names
 *
 * The unwrapped keysets exist only in memory and only during an unlock session: [unlock] loads
 * them right after an authentication, [lock] drops them (on background, screen-off, leaving the
 * office, or when the 5 minutes run out). Outside a session nothing in the app can decrypt.
 *
 * Nothing is hardcoded: keysets are generated randomly on the phone. Copying the app's files
 * off the phone gives an attacker ciphertext and wrapped keys, but no way to unwrap them.
 */
class KeyManager(private val context: Context, private val keystore: VaultKeystore) {

    init {
        AeadConfig.register()
        StreamingAeadConfig.register()
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    @Volatile private var fileAead: StreamingAead? = null
    @Volatile private var metadataAead: Aead? = null

    val isUnlocked: Boolean get() = fileAead != null && metadataAead != null

    /** True if this install has keys (current or pre-Phase-7), i.e. something to protect. */
    fun hasKeys(): Boolean = storedNames().isNotEmpty() || legacyNames().isNotEmpty()

    /**
     * Loads the keysets into memory. Call only right after the owner authenticated: otherwise the
     * Keystore refuses ([AuthenticationRequiredException]), and if the screen lock was removed it
     * has destroyed the key ([KeysDestroyedException]).
     *
     * On the first unlock after updating from a pre-Phase-7 install, this also moves the existing
     * keysets under the new authentication-bound key, keeping the very same keys, so every file
     * stays readable. Returns true if that upgrade happened now.
     */
    @Synchronized
    fun unlock(): Boolean {
        if (isUnlocked) return false
        val kek = keystore.kek()
        val current = storedNames()
        val legacy = legacyNames()
        var upgraded = false
        val handles = KEYSETS.associate { (name, parameters) ->
            name to when (KeyMigration.sourceFor(name, current, legacy)) {
                KeysetSource.CURRENT -> KeysetWrapping.unwrap(read(name), kek, name)
                KeysetSource.LEGACY -> legacyKeyset(name, parameters).also {
                    store(name, it, kek)
                    upgraded = true
                }
                KeysetSource.NEW -> KeysetHandle.generateNew(parameters).also { store(name, it, kek) }
            }
        }
        if (legacy.isNotEmpty()) {
            // Read every keyset back under the new key before deleting the old copies.
            val verified = handles.filter { (name, handle) ->
                runCatching { KeysetWrapping.sameKeys(KeysetWrapping.unwrap(read(name), kek, name), handle) }
                    .getOrDefault(false)
            }.keys
            if (KeyMigration.mayDeleteLegacy(handles.keys, verified)) {
                context.deleteSharedPreferences(LEGACY_PREF_FILE)
                keystore.deleteLegacyKey()
            }
        }
        fileAead = handles.getValue(FILE_KEYSET).getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
        metadataAead = handles.getValue(METADATA_KEYSET).getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        return upgraded
    }

    /** Drops the unwrapped keys from memory. Decryption is impossible until the next [unlock]. */
    @Synchronized
    fun lock() {
        fileAead = null
        metadataAead = null
    }

    fun fileCipher(): FileCipher = FileCipher(fileAead ?: throw VaultKeysLockedException())

    fun metadataCipher(): MetadataCipher = MetadataCipher(metadataAead ?: throw VaultKeysLockedException())

    /**
     * Crypto-shredding: delete the wrapped keysets and every Keystore key. Any ciphertext left
     * anywhere (in flash memory blocks, old backups) becomes permanently unreadable.
     */
    @Synchronized
    fun destroyKeys() {
        lock()
        context.deleteSharedPreferences(PREF_FILE)
        context.deleteSharedPreferences(LEGACY_PREF_FILE)
        keystore.deleteAll()
    }

    private fun storedNames(): Set<String> = KEYSETS.map { it.first }.filter { prefs.contains(it) }.toSet()

    private fun read(name: String): ByteArray =
        Base64.decode(prefs.getString(name, null) ?: throw VaultKeysLockedException(), Base64.NO_WRAP)

    /** commit(), not apply(): the keys must be on disk before anything is encrypted with them. */
    private fun store(name: String, handle: KeysetHandle, kek: Aead) {
        val blob = KeysetWrapping.wrap(handle, kek, name)
        prefs.edit(commit = true) { putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)) }
    }

    // --- Pre-Phase-7 installs -----------------------------------------------------------------

    private fun legacyNames(): Set<String> {
        val legacy = context.getSharedPreferences(LEGACY_PREF_FILE, Context.MODE_PRIVATE)
        return KEYSETS.map { it.first }.filter { legacy.contains(LEGACY_NAMES.getValue(it)) }.toSet()
    }

    /** Reads an old keyset, wrapped by the old Keystore key that needed no authentication. */
    private fun legacyKeyset(name: String, parameters: Parameters): KeysetHandle {
        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(context, LEGACY_NAMES.getValue(name), LEGACY_PREF_FILE)
            .withKeyTemplate(KeyTemplate.createFrom(parameters))
            .withMasterKeyUri("android-keystore://${VaultKeystore.LEGACY_ALIAS}")
            .build()
        // Never accept a keyset Tink couldn't protect with the Keystore (fail closed).
        if (!manager.isUsingKeystore) throw IllegalStateException("Old keyset isn't Keystore-protected")
        return manager.keysetHandle
    }

    private companion object {
        const val PREF_FILE = "safecomp_keysets_v2"
        const val FILE_KEYSET = "file"
        const val METADATA_KEYSET = "metadata"
        val KEYSETS: List<Pair<String, Parameters>> = listOf(
            FILE_KEYSET to PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB,
            METADATA_KEYSET to PredefinedAeadParameters.AES256_GCM,
        )

        const val LEGACY_PREF_FILE = "safecomp_keysets"
        val LEGACY_NAMES = mapOf(FILE_KEYSET to "file_keyset", METADATA_KEYSET to "metadata_keyset")
    }
}
