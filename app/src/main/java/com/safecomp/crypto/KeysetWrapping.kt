package com.safecomp.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkProtoKeysetFormat

/**
 * Wraps (encrypts) a Tink keyset with a key-encryption key, and unwraps it again.
 *
 * Pure Tink, no Android: in the app the key-encryption key is the authentication-bound
 * Android Keystore key (see [VaultKeystore]); in unit tests it's an ordinary Tink AEAD.
 * The keyset's name is the associated data, so the file keyset's blob can't be swapped in
 * for the metadata keyset's (or the other way round) without decryption failing.
 */
object KeysetWrapping {

    fun wrap(handle: KeysetHandle, kek: Aead, name: String): ByteArray =
        TinkProtoKeysetFormat.serializeEncryptedKeyset(handle, kek, name.toByteArray())

    fun unwrap(blob: ByteArray, kek: Aead, name: String): KeysetHandle =
        TinkProtoKeysetFormat.parseEncryptedKeyset(blob, kek, name.toByteArray())

    /** Same keys? Compared by key IDs and the primary key, never by key material. */
    fun sameKeys(a: KeysetHandle, b: KeysetHandle): Boolean =
        a.keysetInfo.primaryKeyId == b.keysetInfo.primaryKeyId &&
            a.keysetInfo.keyInfoList.map { it.keyId } == b.keysetInfo.keyInfoList.map { it.keyId }
}

/** Where each keyset comes from when the vault is unlocked. */
enum class KeysetSource {
    /** Already wrapped under the authentication-bound key (normal case). */
    CURRENT,

    /** From a pre-Phase-7 install: re-wrap under the new key, keeping the same keys. */
    LEGACY,

    /** Doesn't exist yet (new install, or never used): generate it. */
    NEW,
}

/**
 * The one-time upgrade from the old Keystore key (no authentication) to the new,
 * authentication-bound one. Pure rules, unit-tested.
 */
object KeyMigration {

    fun sourceFor(name: String, current: Set<String>, legacy: Set<String>): KeysetSource = when (name) {
        in current -> KeysetSource.CURRENT
        in legacy -> KeysetSource.LEGACY
        else -> KeysetSource.NEW
    }

    /**
     * The old wrapped keysets and the old Keystore key may only be deleted once every keyset
     * has been stored under the new key AND read back correctly. A crash before that point
     * leaves the old copies untouched, so the next unlock simply tries again.
     */
    fun mayDeleteLegacy(names: Collection<String>, storedAndVerified: Set<String>): Boolean =
        names.all { it in storedAndVerified }
}
