package com.safecomp.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException

class MetadataCipherTest {

    private lateinit var cipher: MetadataCipher

    @Before fun setUp() {
        AeadConfig.register()
        val aead = KeysetHandle.generateNew(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        cipher = MetadataCipher(aead)
    }

    @Test fun `file name round trip, including non-ASCII`() {
        val name = "Отчёт_2026 — финал.pdf"
        assertEquals(name, cipher.decryptName(cipher.encryptName(name, "id-1"), "id-1"))
    }

    @Test fun `encrypted name does not reveal the name`() {
        val encrypted = cipher.encryptName("layoffs.pdf", "id-1")
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("layoffs"))
    }

    @Test(expected = GeneralSecurityException::class)
    fun `a name cannot be moved to another file's row`() {
        val encrypted = cipher.encryptName("a.pdf", "id-1")
        cipher.decryptName(encrypted, "id-2")
    }
}
