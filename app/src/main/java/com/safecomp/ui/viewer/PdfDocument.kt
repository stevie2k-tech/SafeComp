package com.safecomp.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * A PDF opened from an in-memory file descriptor (see MemFd). PdfRenderer isn't thread-safe
 * and only allows one open page at a time, so everything runs on a single-thread dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfDocument(private val descriptor: ParcelFileDescriptor) {
    private val renderer = PdfRenderer(descriptor)
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    @Volatile private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Renders one page to a bitmap [widthPx] wide, or null if the document was closed. */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? = withContext(dispatcher) {
        if (closed) return@withContext null
        renderer.openPage(index).use { page ->
            val heightPx = (widthPx.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
            createBitmap(widthPx, heightPx).also { bitmap ->
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    /** Closing the descriptor frees the memfd, so the decrypted PDF is gone from RAM. */
    suspend fun close() = withContext(dispatcher) {
        if (closed) return@withContext
        closed = true
        renderer.close()
        descriptor.close()
    }
}
