package com.safecomp.vault

import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.annotation.RequiresApi
import java.io.InputStream

/**
 * Anonymous in-memory files (Linux memfd_create).
 *
 * Android's PdfRenderer needs a seekable file descriptor; it can't render from a byte array.
 * Writing the decrypted PDF to a temp file would put plaintext on disk. A memfd behaves like
 * a file but lives only in RAM: it has no path, no other app can open it, and it disappears
 * when the last descriptor is closed.
 */
@RequiresApi(Build.VERSION_CODES.R)
object MemFd {
    fun fromStream(name: String, input: InputStream): ParcelFileDescriptor {
        val fd = Os.memfd_create(name, 0)
        try {
            val buffer = ByteArray(64 * 1024)
            try {
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    var written = 0
                    while (written < read) written += Os.write(fd, buffer, written, read - written)
                }
            } finally {
                buffer.fill(0)
            }
            Os.lseek(fd, 0, OsConstants.SEEK_SET)
            return ParcelFileDescriptor.dup(fd)
        } finally {
            Os.close(fd)
        }
    }
}
