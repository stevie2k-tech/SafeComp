package com.safecomp.ui.viewer

import android.graphics.Bitmap
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.safecomp.ui.components.GlassCard
import com.safecomp.ui.components.GraceBanner
import com.safecomp.ui.components.ScTopBar
import com.safecomp.ui.components.ScreenBackground
import com.safecomp.ui.theme.MonoFamily
import com.safecomp.ui.theme.Sc
import com.safecomp.ui.locked.LockedHedgehogScreen
import kotlinx.coroutines.flow.Flow

@Composable
fun ViewerScreen(viewModel: ViewerViewModel, graceSecondsLeft: Flow<Long?>, onBack: () -> Unit) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val locked = content as? ViewerContent.Locked

    ScreenBackground(glow = if (locked != null) Sc.Outside else null) {
        Column(Modifier.fillMaxSize()) {
            ScTopBar(
                title = name ?: if (locked != null) "Locked" else "",
                subtitle = if (locked != null) "CONTENT WIPED FROM MEMORY" else "DECRYPTED IN MEMORY ONLY",
                onBack = onBack,
            )
            GraceBanner(graceSecondsLeft)
            val modifier = Modifier
                .fillMaxSize()
                .weight(1f)
            when (val c = content) {
                ViewerContent.Loading -> Box(modifier, Alignment.Center) { CircularProgressIndicator(color = Sc.Accent) }
                is ViewerContent.Locked -> LockedHedgehogScreen(c.reason, modifier)
                is ViewerContent.Message -> Box(modifier.padding(32.dp), Alignment.Center) {
                    Text(c.text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = Sc.TextSecondary)
                }
                is ViewerContent.Image -> ZoomableImage(c.bitmap, modifier)
                is ViewerContent.Text -> Box(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    // Deliberately NOT inside a SelectionContainer: selecting would allow copying
                    // to the clipboard, which other apps can read.
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(c.text, fontFamily = MonoFamily, style = MaterialTheme.typography.bodyMedium, color = Sc.TextPrimary)
                    }
                }
                is ViewerContent.Pdf -> PdfPages(c.document, modifier)
                is ViewerContent.Media -> MediaPlayer(c.player, c.isVideo, modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun ZoomableImage(bitmap: Bitmap, modifier: Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offsetX += pan.x
        offsetY += pan.y
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Box(modifier.background(Color.Black).transformable(state), contentAlignment = Alignment.Center) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
        )
    }
}

@Composable
private fun PdfPages(document: PdfDocument, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { (maxWidth - 32.dp).roundToPx() }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(document.pageCount) { index -> PdfPage(document, index, widthPx) }
        }
    }
}

@Composable
private fun PdfPage(document: PdfDocument, index: Int, widthPx: Int) {
    val bitmap by produceState<Bitmap?>(null, document, index, widthPx) {
        value = document.renderPage(index, widthPx)
    }
    // Wipe each page's pixels as soon as it scrolls away or the viewer closes.
    DisposableEffect(bitmap) {
        val rendered = bitmap
        onDispose { rendered?.wipe() }
    }
    val b = bitmap
    if (b == null) {
        Box(Modifier.fillMaxWidth().height(400.dp).clip(RoundedCornerShape(12.dp)).background(Sc.SurfaceHigh), Alignment.Center) {
            CircularProgressIndicator(color = Sc.Accent)
        }
    } else {
        Image(
            bitmap = remember(b) { b.asImageBitmap() },
            contentDescription = "Page ${index + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(b.width.toFloat() / b.height)
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MediaPlayer(player: ExoPlayer, isVideo: Boolean, modifier: Modifier) {
    var view by remember { mutableStateOf<PlayerView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                // The video is drawn on its own SurfaceView. The window's FLAG_SECURE covers
                // it, but we mark the surface secure as well, as a second layer.
                (videoSurfaceView as? SurfaceView)?.setSecure(true)
                if (!isVideo) {
                    controllerShowTimeoutMs = 0 // Audio: keep the controls visible.
                    controllerHideOnTouch = false
                }
                view = this
            }
        },
    )
    DisposableEffect(player) {
        onDispose { view?.player = null }
    }
}
