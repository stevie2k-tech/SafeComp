package com.safecomp.vault

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import kotlin.math.min

/**
 * Feeds ExoPlayer from an encrypted vault file, decrypting only the bytes it asks for.
 *
 * ExoPlayer reads media through a DataSource. Normally that's a file or a URL. This one
 * wraps Tink's seekable decrypting channel, so:
 *  - no decrypted copy is written anywhere (no temp file, no ExoPlayer disk cache),
 *  - seeking to minute 10 decrypts only the 1 MB segments around minute 10.
 *
 * [isAllowed] is checked every time the player opens the source (on start and on every
 * seek). If the vault has locked, playback fails even before the viewer has been closed.
 */
@OptIn(UnstableApi::class)
class DecryptingDataSource(
    private val openChannel: () -> SeekableByteChannel,
    private val isAllowed: () -> Boolean,
) : BaseDataSource(/* isNetwork = */ false) {

    private var channel: SeekableByteChannel? = null
    private var uri: Uri? = null
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        if (!isAllowed()) throw IOException("Vault is locked")
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val ch = openChannel()
        channel = ch
        val size = ch.size()
        if (dataSpec.position > size) throw IOException("Position beyond end of file")
        ch.position(dataSpec.position)
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            size - dataSpec.position
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val ch = channel ?: throw IOException("Not open")
        val toRead = min(length.toLong(), bytesRemaining).toInt()
        val target = ByteBuffer.wrap(buffer, offset, toRead)
        var read = 0
        while (read == 0) read = ch.read(target)
        if (read == -1) return C.RESULT_END_OF_INPUT
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            channel?.close()
        } finally {
            channel = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(
        private val openChannel: () -> SeekableByteChannel,
        private val isAllowed: () -> Boolean,
    ) : DataSource.Factory {
        @OptIn(UnstableApi::class)
        override fun createDataSource(): DataSource = DecryptingDataSource(openChannel, isAllowed)
    }
}
