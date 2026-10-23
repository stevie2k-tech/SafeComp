package com.safecomp.vault

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.safecomp.crypto.FileCipher
import com.safecomp.crypto.KeyManager
import com.safecomp.data.VaultFile
import com.safecomp.data.VaultFileDao
import com.safecomp.log.EventLogRepository
import com.safecomp.security.AuthSession
import com.safecomp.security.SecurityStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.UUID

/** Passes writes through, but close() only flushes, leaving the wrapped stream open. */
private class NonClosingOutputStream(private val target: OutputStream) : FilterOutputStream(target) {
    override fun write(b: ByteArray, off: Int, len: Int) = target.write(b, off, len)
    override fun close() = target.flush()
}

class VaultLockedException : Exception("The vault is locked")

data class ImportProgress(
    val total: Int,
    val done: Int = 0,
    val failed: Int = 0,
    /** Originals the user asked us to delete but the file's provider wouldn't allow it. */
    val originalsKept: Int = 0,
) {
    val running: Boolean get() = done + failed < total
}

/**
 * The vault: encrypted files in app-private storage, plus their database rows.
 *
 * EVERY path that produces plaintext (file names, contents, PDFs, media) goes through
 * [requireUnlocked] first. The check lives here, in the data layer, not in the UI, so a bug
 * in a screen can't accidentally show a file while the phone is outside the office.
 *
 * WHY SAFECOMP CAN ONLY PROTECT FILES INSIDE ITS OWN VAULT (Android scoped storage):
 * Android gives each app a private sandbox. Since Android 10, scoped storage also stops apps
 * from reading or rewriting other apps' files and most of shared storage. That's a good
 * security property, and it means SafeComp *cannot* lock a PDF sitting in Downloads or a
 * photo in another app. It can't encrypt those files in place, and it can't stop other
 * apps from opening them. So the model is "import": the user picks a file with the system
 * file picker (Storage Access Framework), which grants SafeComp access to just that one
 * file, and SafeComp copies it into its sandbox *encrypted*. The original stays readable
 * outside SafeComp, so we offer to delete it, if its provider allows deletion.
 */
class VaultRepository(
    context: Context,
    private val dao: VaultFileDao,
    private val keyManager: KeyManager,
    private val authSession: AuthSession,
    private val securityState: SecurityStateRepository,
    private val eventLog: EventLogRepository,
    private val scope: CoroutineScope,
) {
    private val resolver: ContentResolver = context.contentResolver

    /** noBackupFilesDir: app-private AND excluded from every kind of backup by Android. */
    private val vaultDir = File(context.noBackupFilesDir, "vault")

    val files: Flow<List<VaultFile>> = dao.observeAll()

    /**
     * Held while one file is being imported, and while orphans are being cleaned up. Without
     * it, startup cleanup could see a freshly renamed .bin whose database row isn't written
     * yet, treat it as an orphan, and delete it.
     */
    private val storageLock = Mutex()

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    /**
     * Quick, non-suspending check, for code that can't suspend (e.g. a media DataSource).
     * Both factors: INSIDE (where) and the session's keys in memory (who, within 5 minutes).
     */
    val isUnlockedNow: Boolean get() = securityState.snapshot.value.isUnlocked && keyManager.isUnlocked

    /** Where you are AND who you are. The keys are loaded only after a fresh authentication. */
    private suspend fun isUnlocked(): Boolean = securityState.isUnlockedNow() && authSession.unlockVault()

    private suspend fun requireUnlocked() {
        if (!isUnlocked()) throw VaultLockedException()
    }

    /** Same check as [requireUnlocked], plus an event-log entry, allowed or denied. */
    private suspend fun requireUnlockedToOpen(file: VaultFile) {
        val allowed = isUnlocked()
        eventLog.fileOpenAttempt(file.id, securityState.snapshot.value, allowed)
        if (!allowed) throw VaultLockedException()
    }

    // --- Import -------------------------------------------------------------------------

    /** Runs in the app scope so leaving the screen doesn't cancel a half-finished import. */
    fun startImport(uris: List<Uri>, deleteOriginals: Boolean) {
        if (uris.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            var progress = ImportProgress(total = uris.size)
            _importProgress.value = progress
            for (uri in uris) {
                progress = try {
                    val keptOriginal = storageLock.withLock { importOne(uri, deleteOriginals) }
                    progress.copy(
                        done = progress.done + 1,
                        originalsKept = progress.originalsKept + if (keptOriginal) 1 else 0,
                    )
                } catch (e: Exception) {
                    // Log the error type only: never file names, URIs or contents. (A stack
                    // trace would include the exception message, which can name the file.)
                    Log.w(TAG, "Import failed: ${e.javaClass.simpleName}")
                    progress.copy(failed = progress.failed + 1)
                }
                _importProgress.value = progress
            }
        }
    }

    fun dismissImportProgress() {
        if (_importProgress.value?.running == false) _importProgress.value = null
    }

    /** Encrypts one picked file into the vault. Returns true if the original was NOT deleted. */
    private suspend fun importOne(uri: Uri, deleteOriginal: Boolean): Boolean {
        // Importing needs the keys too: only inside the office, after authentication.
        requireUnlocked()
        val name = displayName(uri) ?: "Untitled"
        val mime = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val id = UUID.randomUUID().toString()
        vaultDir.mkdirs()
        val temp = File(vaultDir, "$id.tmp")
        val target = File(vaultDir, "$id.bin")

        try {
            val cipher = keyManager.fileCipher()
            val size = resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    // Plaintext goes straight from the picker's stream into the encryptor.
                    // Only ciphertext ever touches our disk, even for the temp file.
                    // Tink must close its stream to write the final segment, which would also
                    // close `output`. The wrapper keeps it open so we can fsync before renaming.
                    val count = cipher.encrypt(input, NonClosingOutputStream(output), FileCipher.fileAssociatedData(id))
                    output.fd.sync()
                    count
                }
            } ?: throw IllegalStateException("Couldn't open the picked file")
            // Rename is atomic: a crash leaves either no .bin or a complete one, never half a file.
            if (!temp.renameTo(target)) throw IllegalStateException("Couldn't store encrypted file")
            dao.insert(
                VaultFile(
                    id = id,
                    encryptedName = keyManager.metadataCipher().encryptName(name, id),
                    mimeType = mime,
                    sizeBytes = size,
                    importedAt = System.currentTimeMillis(),
                ),
            )
        } catch (e: Exception) {
            temp.delete()
            target.delete()
            throw e
        }

        if (!deleteOriginal) return false
        val deleted = runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
        return !deleted
    }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    // --- Reading (all gated) ------------------------------------------------------------

    /** Decrypted names by file ID. A row whose name won't decrypt shows as unreadable. */
    suspend fun decryptNames(files: List<VaultFile>): Map<String, String> {
        requireUnlocked()
        return withContext(Dispatchers.IO) {
            val cipher = keyManager.metadataCipher()
            files.associate { file ->
                file.id to (runCatching { cipher.decryptName(file.encryptedName, file.id) }.getOrNull() ?: "(unreadable)")
            }
        }
    }

    suspend fun get(id: String): VaultFile? = dao.get(id)

    /** Decrypts a small file into memory. The caller must zero the array when done. */
    suspend fun readBytes(file: VaultFile, maxBytes: Long): ByteArray {
        requireUnlockedToOpen(file)
        return withContext(Dispatchers.IO) {
            cipherFile(file).inputStream().use { input ->
                keyManager.fileCipher().decryptToBytes(
                    ciphertext = input,
                    associatedData = FileCipher.fileAssociatedData(file.id),
                    maxBytes = maxBytes,
                    expectedSize = file.sizeBytes.coerceAtMost(maxBytes).toInt(),
                )
            }
        }
    }

    /** A PDF decrypted into anonymous memory (memfd), never onto disk. Android 11+. */
    suspend fun openPdf(file: VaultFile): ParcelFileDescriptor {
        requireUnlockedToOpen(file)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) throw UnsupportedOperationException()
        return withContext(Dispatchers.IO) {
            cipherFile(file).inputStream().use { input ->
                keyManager.fileCipher().decryptingStream(input, FileCipher.fileAssociatedData(file.id)).use {
                    MemFd.fromStream("safecomp-view", it)
                }
            }
        }
    }

    /** Factory for a player data source that decrypts on the fly (video/audio). */
    suspend fun mediaDataSourceFactory(file: VaultFile): DecryptingDataSource.Factory {
        requireUnlockedToOpen(file)
        val cipher = withContext(Dispatchers.IO) { keyManager.fileCipher() }
        return DecryptingDataSource.Factory(
            openChannel = { cipher.seekableDecryptingChannel(cipherFile(file), FileCipher.fileAssociatedData(file.id)) },
            isAllowed = { isUnlockedNow },
        )
    }

    suspend fun delete(file: VaultFile) {
        requireUnlocked()
        withContext(Dispatchers.IO) {
            dao.delete(file.id)
            cipherFile(file).delete()
        }
    }

    private fun cipherFile(file: VaultFile) = File(vaultDir, "${file.id}.bin")

    private companion object {
        const val TAG = "SafeCompVault"
    }

    // --- Maintenance ----------------------------------------------------------------------

    /** Removes leftovers from a crash mid-import: temp files, and .bin files with no row. */
    suspend fun cleanUpOrphans() = storageLock.withLock {
        withContext(Dispatchers.IO) { deleteOrphans() }
    }

    private suspend fun deleteOrphans() {
        val known = dao.allIds().toSet()
        vaultDir.listFiles()?.forEach { f ->
            val orphan = f.name.endsWith(".tmp") || f.nameWithoutExtension !in known
            if (orphan) f.delete()
        }
    }

    /**
     * Reset: delete every encrypted file and row, then destroy the keys. Destroying the keys
     * is what really matters (crypto-shredding): flash storage may keep old blocks after a
     * delete, but without the keys they're random noise.
     */
    suspend fun wipeAll() = storageLock.withLock {
        withContext(Dispatchers.IO) { wipeEverything() }
    }

    private suspend fun wipeEverything() {
        dao.clear()
        vaultDir.deleteRecursively()
        keyManager.destroyKeys()
    }
}
