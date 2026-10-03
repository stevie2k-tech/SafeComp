package com.safecomp.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.safecomp.AppContainer
import com.safecomp.crypto.FileTooLargeException
import com.safecomp.data.FileKind
import com.safecomp.data.VaultFile
import com.safecomp.security.StateReason
import com.safecomp.vault.VaultLockedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

sealed interface ViewerContent {
    data object Loading : ViewerContent
    class Image(val bitmap: Bitmap) : ViewerContent
    class Text(val text: String) : ViewerContent
    class Pdf(val document: PdfDocument) : ViewerContent
    class Media(val player: ExoPlayer, val isVideo: Boolean) : ViewerContent
    data class Message(val text: String) : ViewerContent
    data class Locked(val reason: StateReason) : ViewerContent
}

/**
 * Decrypts one file for viewing, in memory only, and throws it away the moment the vault
 * locks. Each type has its own path:
 *  - image: decrypt to bytes → downsampled Bitmap → bytes zeroed
 *  - text:  decrypt to bytes → String → bytes zeroed
 *  - PDF:   decrypt into an anonymous memory file (memfd) → PdfRenderer
 *  - video/audio: ExoPlayer reads through a decrypting DataSource, chunk by chunk
 */
class ViewerViewModel(
    private val application: Application,
    private val container: AppContainer,
    private val fileId: String,
) : ViewModel() {

    private val repository = container.vaultRepository

    private val _name = MutableStateFlow<String?>(null)
    val name: StateFlow<String?> = _name.asStateFlow()

    private val _content = MutableStateFlow<ViewerContent>(ViewerContent.Loading)
    val content: StateFlow<ViewerContent> = _content.asStateFlow()

    init {
        viewModelScope.launch {
            container.securityState.snapshot
                .map { it.isUnlocked to it.reason }
                .distinctUntilChanged()
                .collect { (unlocked, reason) -> if (!unlocked) lock(reason) }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val file = repository.get(fileId)
        if (file == null) {
            setContent(ViewerContent.Message("This file is no longer in the vault."))
            return
        }
        try {
            _name.value = repository.decryptNames(listOf(file))[file.id]
            setContent(
                when (file.kind) {
                    FileKind.IMAGE -> loadImage(file)
                    FileKind.TEXT -> loadText(file)
                    FileKind.PDF -> loadPdf(file)
                    FileKind.VIDEO -> loadMedia(file, isVideo = true)
                    FileKind.AUDIO -> loadMedia(file, isVideo = false)
                    // No "open with another app": that would hand the plaintext to an app
                    // SafeComp can't lock.
                    FileKind.OTHER -> ViewerContent.Message(
                        "SafeComp can't preview this type of file. It stays encrypted in the vault.",
                    )
                },
            )
        } catch (e: VaultLockedException) {
            lock(container.securityState.snapshot.value.reason)
        } catch (e: FileTooLargeException) {
            setContent(ViewerContent.Message("This file is too large to preview."))
        } catch (e: Exception) {
            // Includes authentication failures: a tampered file is refused, never shown.
            setContent(ViewerContent.Message("This file couldn't be decrypted. It may be damaged or tampered with."))
        }
    }

    private suspend fun loadImage(file: VaultFile): ViewerContent {
        val bytes = repository.readBytes(file, MAX_IMAGE_BYTES)
        try {
            val bitmap = withContext(Dispatchers.Default) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_DIMENSION) sample *= 2
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    // Mutable (and so a plain in-memory bitmap, not a GPU "hardware" one),
                    // so its pixels can be overwritten when the vault locks.
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inMutable = true
                    },
                )
            }
            return if (bitmap != null) ViewerContent.Image(bitmap) else ViewerContent.Message("This image format isn't supported.")
        } finally {
            bytes.fill(0)
        }
    }

    private suspend fun loadText(file: VaultFile): ViewerContent {
        val bytes = repository.readBytes(file, MAX_TEXT_BYTES)
        try {
            return ViewerContent.Text(String(bytes, Charsets.UTF_8))
        } finally {
            bytes.fill(0)
        }
    }

    private suspend fun loadPdf(file: VaultFile): ViewerContent {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ViewerContent.Message("Viewing PDFs needs Android 11 or newer.")
        }
        val descriptor = repository.openPdf(file)
        return withContext(Dispatchers.IO) { ViewerContent.Pdf(PdfDocument(descriptor)) }
    }

    @OptIn(UnstableApi::class)
    private suspend fun loadMedia(file: VaultFile, isVideo: Boolean): ViewerContent {
        val dataSourceFactory = repository.mediaDataSourceFactory(file)
        // No CacheDataSource anywhere in this chain, so ExoPlayer never writes media to disk.
        val source = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri("safecomp://vault/${file.id}"))
        val player = ExoPlayer.Builder(application).build().apply {
            setMediaSource(source)
            prepare()
            playWhenReady = true
        }
        return ViewerContent.Media(player, isVideo)
    }

    /** Sets new content, unless the vault locked while it was loading. Then it's discarded. */
    private fun setContent(new: ViewerContent) {
        if (_content.value is ViewerContent.Locked) {
            release(new)
        } else {
            _content.value = new
        }
    }

    private fun lock(reason: StateReason) {
        val old = _content.value
        _content.value = ViewerContent.Locked(reason)
        _name.value = null
        release(old)
    }

    /** Best-effort wipe of decrypted data. Phase 4 adds a vault-wide wipe on top of this. */
    private fun release(content: ViewerContent) {
        when (content) {
            // eraseColor overwrites the pixels in place, which wipes them. Unlike recycle(),
            // it's safe even if the UI draws this bitmap one last time.
            is ViewerContent.Image -> content.bitmap.wipe()
            is ViewerContent.Pdf -> container.appScope.launch { content.document.close() }
            is ViewerContent.Media -> content.player.release()
            else -> Unit
        }
    }

    override fun onCleared() {
        release(_content.value)
        _content.value = ViewerContent.Loading
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
        const val MAX_IMAGE_DIMENSION = 2048
        const val MAX_TEXT_BYTES = 1L * 1024 * 1024
    }
}

/** Overwrites the pixels in place. Immutable bitmaps can't be overwritten, so they're only dropped. */
internal fun Bitmap.wipe() {
    if (isMutable && !isRecycled) eraseColor(0)
}
