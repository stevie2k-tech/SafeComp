package com.safecomp.crypto

import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * On the phone the keys come from the Android Keystore. Here we generate a throwaway keyset
 * in memory: the cipher code under test is identical.
 */
class FileCipherTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var cipher: FileCipher
    private val ad = FileCipher.fileAssociatedData("file-a")

    @Before fun setUp() {
        StreamingAeadConfig.register()
        cipher = FileCipher(newStreamingAead())
    }

    private fun newStreamingAead(): StreamingAead =
        KeysetHandle.generateNew(KeyTemplate.createFrom(PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB))
            .getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)

    private fun encrypt(plaintext: ByteArray, aad: ByteArray = ad, c: FileCipher = cipher): ByteArray {
        val out = ByteArrayOutputStream()
        val count = c.encrypt(ByteArrayInputStream(plaintext), out, aad)
        assertEquals(plaintext.size.toLong(), count)
        return out.toByteArray()
    }

    private fun decrypt(ciphertext: ByteArray, aad: ByteArray = ad): ByteArray =
        cipher.decryptToBytes(ByteArrayInputStream(ciphertext), aad, maxBytes = 64L * 1024 * 1024)

    @Test fun `round trip empty file`() {
        assertArrayEquals(ByteArray(0), decrypt(encrypt(ByteArray(0))))
    }

    @Test fun `round trip small file`() {
        val plaintext = "Quarterly report: confidential".toByteArray()
        assertArrayEquals(plaintext, decrypt(encrypt(plaintext)))
    }

    @Test fun `round trip multi-segment file (larger than 1 MB segments)`() {
        val plaintext = Random(42).nextBytes(3 * 1024 * 1024 + 123)
        assertArrayEquals(plaintext, decrypt(encrypt(plaintext)))
    }

    @Test fun `ciphertext does not contain the plaintext`() {
        val plaintext = "TOP-SECRET-MARKER".repeat(100).toByteArray()
        val ciphertext = encrypt(plaintext)
        assertFalse(String(ciphertext, Charsets.ISO_8859_1).contains("TOP-SECRET-MARKER"))
    }

    @Test fun `encrypting the same file twice gives different ciphertext`() {
        val plaintext = "same".toByteArray()
        assertFalse(encrypt(plaintext).contentEquals(encrypt(plaintext)))
    }

    @Test(expected = IOException::class)
    fun `a flipped bit is detected`() {
        val ciphertext = encrypt(Random(1).nextBytes(10_000))
        ciphertext[ciphertext.size / 2] = (ciphertext[ciphertext.size / 2].toInt() xor 1).toByte()
        decrypt(ciphertext)
    }

    @Test(expected = IOException::class)
    fun `a truncated file is detected`() {
        val ciphertext = encrypt(Random(2).nextBytes(2 * 1024 * 1024))
        decrypt(ciphertext.copyOf(ciphertext.size - 100))
    }

    @Test(expected = IOException::class)
    fun `swapping files (wrong associated data) is detected`() {
        val ciphertext = encrypt("file A".toByteArray(), aad = FileCipher.fileAssociatedData("file-a"))
        decrypt(ciphertext, aad = FileCipher.fileAssociatedData("file-b"))
    }

    @Test(expected = IOException::class)
    fun `a different key cannot decrypt`() {
        val otherKey = FileCipher(newStreamingAead())
        decrypt(encrypt("secret".toByteArray(), c = otherKey))
    }

    @Test(expected = FileTooLargeException::class)
    fun `decrypting into memory respects the size limit`() {
        val ciphertext = encrypt(ByteArray(2_000))
        cipher.decryptToBytes(ByteArrayInputStream(ciphertext), ad, maxBytes = 1_000)
    }

    @Test fun `seekable channel reads from the middle without decrypting from the start`() {
        val plaintext = Random(3).nextBytes(3 * 1024 * 1024)
        val file = tmp.newFile("video.bin")
        file.writeBytes(encrypt(plaintext))

        cipher.seekableDecryptingChannel(file, ad).use { channel ->
            assertEquals(plaintext.size.toLong(), channel.size())
            val start = 2 * 1024 * 1024 + 500 // inside the third segment
            channel.position(start.toLong())
            val buffer = ByteBuffer.allocate(4_096)
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == -1) break
            }
            assertArrayEquals(plaintext.copyOfRange(start, start + 4_096), buffer.array())
        }
    }
}
