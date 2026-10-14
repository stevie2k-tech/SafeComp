package com.safecomp.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.security.GeneralSecurityException

/**
 * The key-wrapping and the one-time upgrade, with an ordinary Tink AEAD standing in for the
 * Android Keystore key (which only exists on a phone).
 */
class KeysetWrappingTest {

    companion object {
        @BeforeClass @JvmStatic fun register() {
            AeadConfig.register()
            StreamingAeadConfig.register()
        }
    }

    private fun kek(): Aead = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
        .getPrimitive(RegistryConfiguration.get(), Aead::class.java)

    private fun metadataKeyset() = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)

    @Test fun `wrap then unwrap gives the same keys`() {
        val kek = kek()
        val original = metadataKeyset()
        val back = KeysetWrapping.unwrap(KeysetWrapping.wrap(original, kek, "metadata"), kek, "metadata")
        assertTrue(KeysetWrapping.sameKeys(original, back))
    }

    @Test fun `the upgrade keeps old data readable`() {
        // A name encrypted before the upgrade must still decrypt after re-wrapping its keyset.
        val oldKeyset = metadataKeyset()
        val before = oldKeyset.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            .encrypt("Q3_plan.pdf".toByteArray(), "file-1".toByteArray())

        val newKek = kek()
        val upgraded = KeysetWrapping.unwrap(KeysetWrapping.wrap(oldKeyset, newKek, "metadata"), newKek, "metadata")
        val after = upgraded.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            .decrypt(before, "file-1".toByteArray())
        assertArrayEquals("Q3_plan.pdf".toByteArray(), after)
    }

    @Test fun `a blob can't be unwrapped under another keyset's name`() {
        val kek = kek()
        val blob = KeysetWrapping.wrap(metadataKeyset(), kek, "metadata")
        assertThrows(GeneralSecurityException::class.java) { KeysetWrapping.unwrap(blob, kek, "file") }
    }

    @Test fun `a blob can't be unwrapped with another key`() {
        val blob = KeysetWrapping.wrap(metadataKeyset(), kek(), "metadata")
        assertThrows(GeneralSecurityException::class.java) { KeysetWrapping.unwrap(blob, kek(), "metadata") }
    }

    @Test fun `a tampered blob is rejected`() {
        val kek = kek()
        val blob = KeysetWrapping.wrap(metadataKeyset(), kek, "metadata")
        blob[blob.size / 2] = (blob[blob.size / 2].toInt() xor 1).toByte()
        assertThrows(GeneralSecurityException::class.java) { KeysetWrapping.unwrap(blob, kek, "metadata") }
    }

    @Test fun `different keysets are not the same keys`() {
        assertFalse(KeysetWrapping.sameKeys(metadataKeyset(), metadataKeyset()))
        val streaming = KeysetHandle.generateNew(PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB)
        assertTrue(KeysetWrapping.sameKeys(streaming, streaming))
    }

    @Test fun `each keyset comes from the right place`() {
        assertEquals(KeysetSource.CURRENT, KeyMigration.sourceFor("file", current = setOf("file"), legacy = setOf("file")))
        assertEquals(KeysetSource.LEGACY, KeyMigration.sourceFor("file", current = emptySet(), legacy = setOf("file")))
        assertEquals(KeysetSource.NEW, KeyMigration.sourceFor("file", current = emptySet(), legacy = emptySet()))
    }

    @Test fun `old keys are deleted only after every keyset was stored and read back`() {
        val names = listOf("file", "metadata")
        assertTrue(KeyMigration.mayDeleteLegacy(names, storedAndVerified = setOf("file", "metadata")))
        // A crash or a failed check after the first keyset: keep the old copies, retry next time.
        assertFalse(KeyMigration.mayDeleteLegacy(names, storedAndVerified = setOf("file")))
        assertFalse(KeyMigration.mayDeleteLegacy(names, storedAndVerified = emptySet()))
    }
}
