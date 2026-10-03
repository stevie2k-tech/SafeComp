package com.safecomp.crypto

import com.google.crypto.tink.StreamingAead
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/**
 * Encrypts and decrypts vault files with Tink StreamingAead (AES-256-GCM-HKDF, 1 MB segments).
 *
 * Why streaming: a video can be hundreds of MB, far too big to decrypt into one byte array.
 * The file is split into 1 MB segments, each with its own GCM authentication tag, so we can:
 *  - encrypt/decrypt with constant memory, and
 *  - SEEK: decrypt only the segment that holds minute 10 of a video.
 * Any modified, reordered or truncated segment fails authentication instead of producing
 * wrong plaintext.
 *
 * Every call takes associated data (AD) that binds the ciphertext to its file ID. The AD
 * isn't secret, but if an attacker swaps two .bin files on disk, decryption fails because
 * the AD no longer matches.
 */
class FileCipher(private val streamingAead: StreamingAead) {

    /**
     * Encrypts all of [plaintext] into [ciphertext] and CLOSES [ciphertext] (Tink writes the
     * final authenticated segment on close). Returns the number of plaintext bytes.
     */
    fun encrypt(plaintext: InputStream, ciphertext: OutputStream, associatedData: ByteArray): Long {
        var total = 0L
        streamingAead.newEncryptingStream(ciphertext, associatedData).use { out ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = plaintext.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                total += read
            }
            buffer.fill(0)
        }
        return total
    }

    /** A stream that decrypts as it's read. Throws IOException on tampering. */
    fun decryptingStream(ciphertext: InputStream, associatedData: ByteArray): InputStream =
        streamingAead.newDecryptingStream(ciphertext, associatedData)

    /** Random-access decryption, used for video/audio seeking. Closing it closes the file. */
    fun seekableDecryptingChannel(ciphertextFile: File, associatedData: ByteArray): SeekableByteChannel {
        val channel = streamingAead.newSeekableDecryptingChannel(
            FileInputStream(ciphertextFile).channel,
            associatedData,
        )
        try {
            // Tink's keyset-level channel only learns which key matches (and so the plaintext
            // size) on the first read; size() throws before that. Media players ask for the
            // size first, so do a 1-byte read now and rewind. This also authenticates the
            // first segment before anyone uses the channel.
            val probe = ByteBuffer.allocate(1)
            channel.read(probe)
            probe.put(0, 0)
            channel.position(0)
        } catch (e: Exception) {
            channel.close()
            throw e
        }
        return channel
    }

    /**
     * Decrypts a whole (small) file into memory. The caller owns the array and should
     * zero it with fill(0) when done.
     */
    fun decryptToBytes(
        ciphertext: InputStream,
        associatedData: ByteArray,
        maxBytes: Long,
        expectedSize: Int = BUFFER_SIZE,
    ): ByteArray {
        decryptingStream(ciphertext, associatedData).use { input ->
            // Pre-sized so the buffer normally never has to grow (each growth leaves an
            // unwiped copy behind for the garbage collector).
            val out = WipeableByteArrayOutputStream(expectedSize.coerceIn(1, maxBytes.toInt()))
            val buffer = ByteArray(BUFFER_SIZE)
            try {
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (out.size() + read > maxBytes) throw FileTooLargeException(maxBytes)
                    out.write(buffer, 0, read)
                }
                return out.toByteArray()
            } finally {
                buffer.fill(0)
                out.wipe()
            }
        }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024

        fun fileAssociatedData(fileId: String): ByteArray = "safecomp:v1:file:$fileId".toByteArray()
    }
}

class FileTooLargeException(val maxBytes: Long) : Exception("File is larger than $maxBytes bytes")

/** ByteArrayOutputStream whose current internal buffer can be zeroed. */
private class WipeableByteArrayOutputStream(size: Int) : java.io.ByteArrayOutputStream(size) {
    fun wipe() {
        buf.fill(0)
        count = 0
    }
}
