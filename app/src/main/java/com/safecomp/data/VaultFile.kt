package com.safecomp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One file in the vault. The contents live encrypted in `vault/<id>.bin`. This row holds
 * only what's needed to list and open it.
 *
 * The NAME is encrypted (it can be sensitive by itself). Type, size and date are left in
 * plaintext to pick an icon and viewer. That's a deliberate trade-off: the ciphertext size
 * already reveals the size, and a type like "PDF" says little about the contents.
 */
@Entity(tableName = "vault_files")
class VaultFile(
    /** Random UUID. Also bound into the file's encryption as associated data. */
    @PrimaryKey val id: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val encryptedName: ByteArray,
    val mimeType: String,
    val sizeBytes: Long,
    /** Wall-clock time, for display only. Never used for security decisions. */
    val importedAt: Long,
) {
    val kind: FileKind get() = FileKind.fromMime(mimeType)
}

enum class FileKind {
    IMAGE, PDF, TEXT, VIDEO, AUDIO, OTHER;

    companion object {
        fun fromMime(mime: String): FileKind = when {
            mime.startsWith("image/") -> IMAGE
            mime == "application/pdf" -> PDF
            mime.startsWith("text/") || mime == "application/json" -> TEXT
            mime.startsWith("video/") -> VIDEO
            mime.startsWith("audio/") -> AUDIO
            else -> OTHER
        }
    }
}
