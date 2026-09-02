package app.otter.client.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.otter.client.model.MediaKind
import app.otter.client.model.PostMedia
import app.otter.client.model.PostPreview
import app.otter.client.ui.MediaQuality
import app.otter.client.ui.components.media.MediaSurface
import app.otter.client.ui.components.media.rememberMediaPlayer
import coil3.compose.AsyncImage

/**
 * A held-open look at a post's media, without leaving the feed.
 *
 * Opening the viewer is a commitment: it takes over the screen, marks the post read and has to be
 * backed out of. Most of the time the question is only "what is this a picture of", and a peek
 * answers it for as long as the thumb stays down. The caller owns that lifetime — this composable
 * is on screen exactly while it is being held.
 *
 * It deliberately does not accept touches of its own. The gesture belongs to the thumbnail
 * underneath, which is still tracking the same unbroken press.
 */
@Composable
fun MediaPeek(
    preview: PostPreview,
    media: PostMedia?,
    autoplay: Boolean,
    mediaQuality: MediaQuality,
) {
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        // Grows into place rather than appearing at full size, so the peek reads as coming out
        // of the thumbnail that was held.
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { settled = true }
        val scale by animateFloatAsState(if (settled) 1f else .92f, label = "peek scale")
        val dim by animateFloatAsState(if (settled) .82f else 0f, label = "peek scrim")

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(.94f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = if (settled) 1f else 0f
                    }
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PeekContent(
                    preview = preview,
                    media = media,
                    autoplay = autoplay,
                    mediaQuality = mediaQuality,
                )
            }

            media?.takeIf { it.isGallery }?.let { gallery ->
                Text(
                    text = "1 / ${gallery.assets.size}",
                    color = Color.White.copy(alpha = .85f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 56.dp),
                )
            }
        }
    }
}

/**
 * A clip peeks as a clip: a still frame of a video answers far less than three seconds of it.
 * Muted throughout — a peek is a glance, and it should not interrupt whatever is already playing.
 */
@Composable
private fun PeekContent(
    preview: PostPreview,
    media: PostMedia?,
    autoplay: Boolean,
    mediaQuality: MediaQuality,
) {
    val asset = media?.first
    if (autoplay && asset != null && asset.needsPlayer) {
        val player = rememberMediaPlayer(
            asset = asset,
            play = true,
            muted = true,
            mediaQuality = mediaQuality,
        )
        MediaSurface(
            player = player,
            previewUrl = asset.previewUrl ?: preview.cardImageUrl ?: preview.imageUrl,
            contentDescription = preview.altText,
            fallbackAspectRatio = asset.aspectRatio,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    // An animated image with no player-friendly encoding still moves; Coil's GIF decoder drives
    // it from the same surface a still would use.
    val url = asset?.takeIf { autoplay && it.kind == MediaKind.ANIMATED }?.animatedImageUrl
        ?: preview.imageUrl
        ?: preview.cardImageUrl
        ?: preview.thumbnailUrl
    AsyncImage(
        model = url,
        contentDescription = preview.altText,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(asset?.aspectRatio ?: preview.aspectRatio),
    )
}
