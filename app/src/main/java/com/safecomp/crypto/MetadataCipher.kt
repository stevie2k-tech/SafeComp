package com.safecomp.crypto

import com.google.crypto.tink.Aead

/**
 * Encrypts small values such as file names with AES-256-GCM. A name like
 * "2026_layoffs_draft.pdf" is sensitive by itself, so the database only stores it encrypted.
 */
class MetadataCipher(private val aead: Aead) {

    fun encryptName(name: String, fileId: String): ByteArray =
        aead.encrypt(name.toByteArray(Charsets.UTF_8), nameAssociatedData(fileId))

    fun decryptName(ciphertext: ByteArray, fileId: String): String =
        String(aead.decrypt(ciphertext, nameAssociatedData(fileId)), Charsets.UTF_8)

    private fun nameAssociatedData(fileId: String) = "safecomp:v1:name:$fileId".toByteArray()
}
