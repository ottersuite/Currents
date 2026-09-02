package app.otter.client.ui.components.media

import androidx.annotation.OptIn
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ScrubbingModeParameters
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPresentationState
import app.otter.client.model.MediaAsset
import app.otter.client.model.MediaKind
import app.otter.client.BuildConfig
import app.otter.client.data.MediaCache
import app.otter.client.ui.MediaQuality
import coil3.compose.AsyncImage

/**
 * An ExoPlayer bound to one asset and to the composition that asked for it.
 *
 * Reddit hands out adaptive streams that some networks refuse, so a failure falls through to the
 * asset's remaining sources before giving up. Playback stops when the app leaves the foreground —
 * a muted loop running behind a locked screen is still decoding video.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberMediaPlayer(
    asset: MediaAsset,
    play: Boolean,
    muted: Boolean,
    mediaQuality: MediaQuality = MediaQuality.Auto,
    onExhausted: () -> Unit = {},
): Player {
    val context = LocalContext.current
    val player: Player = remember(asset, mediaQuality) {
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters().apply {
                when (mediaQuality) {
                    MediaQuality.Low -> {
                        setMaxVideoSize(854, 480)
                        setMaxVideoBitrate(1_500_000)
                    }
                    MediaQuality.Auto -> Unit
                    MediaQuality.High -> setForceHighestSupportedBitrate(true)
                }
            }.build()
        }
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(MediaCache.dataSourceFactory(context)))
            // Keep recently played samples available. Backward scrubs otherwise force the
            // network/extractor path to rebuild from an older keyframe on every drag update.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBackBuffer(MEDIA_BACK_BUFFER_MS, true)
                    .build(),
            )
            .build().apply {
            repeatMode = if (asset.kind == MediaKind.ANIMATED) {
                Player.REPEAT_MODE_ALL
            } else {
                Player.REPEAT_MODE_OFF
            }
            asset.playbackUrls.firstOrNull()?.let { setMediaItem(MediaItem.fromUri(it)) }
            prepare()
        }
    }
    var sourceIndex by remember(asset) { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                traceMedia("error=${error.errorCodeName} cause=${error.cause?.javaClass?.simpleName}")
                val next = asset.playbackUrls.getOrNull(sourceIndex + 1)
                if (next == null) {
                    // Nothing left to try: the caller decides what to show in place of video.
                    onExhausted()
                    return
                }
                sourceIndex += 1
                player.setMediaItem(MediaItem.fromUri(next))
                player.prepare()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, muted) {
        // Some services label an MP4 as a GIF even when it still carries an audio track. Let the
        // player decide whether audio exists instead of permanently silencing animated assets.
        player.volume = if (muted) 0f else 1f
    }

    LaunchedEffect(player, play) {
        player.playWhenReady = play
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val shouldPlay = rememberUpdatedState(play)
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                // The effect above keys on `play`, which does not change while the app is in the
                // background -- so without this the pause above is never undone on return.
                Lifecycle.Event.ON_START -> player.playWhenReady = shouldPlay.value
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return player
}

/** Debug-only note when playback gives up, explaining why a still frame took over. */
private fun traceMedia(message: String) {
    if (BuildConfig.DEBUG) Log.d("OtterMedia", message)
}

/**
 * Video output with the post's still frame underneath, so a slow first frame shows the preview
 * Reddit already provided instead of a black rectangle.
 */
@OptIn(UnstableApi::class)
@Composable
fun MediaSurface(
    player: Player,
    previewUrl: String?,
    contentDescription: String?,
    fallbackAspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    val presentation = rememberPresentationState(player)
    // The surface stretches to whatever box it is given, so the box has to match the video. The
    // player knows the true size once it has decoded a frame; the parsed ratio only stands in
    // until then, and for a comment link there is no metadata to parse at all.
    val measured = presentation.videoSizeDp
        ?.takeIf { it.isSpecified && it.width > 0f && it.height > 0f }
        ?.let { it.width / it.height }

    Box(modifier.aspectRatio(measured ?: fallbackAspectRatio)) {
        // A TextureView, not the default SurfaceView: the viewer translates and scales its pages
        // while dismissing, and a SurfaceView draws in its own window that ignores both — which
        // leaves a black rectangle where the video should be.
        PlayerSurface(
            player = player,
            modifier = Modifier.fillMaxSize(),
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        )
        if (presentation.coverSurface && previewUrl != null) {
            AsyncImage(
                model = previewUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Puts the player into ExoPlayer's scrubbing mode for the duration of a drag.
 *
 * The mode exists for exactly this: it drops audio and keeps the decoder primed during a rapid
 * stream of seeks. A small tolerance lets Media3 use a nearby sync frame instead of decoding a
 * long group of pictures for every finger movement. The caller performs one exact seek after the
 * drag, so the final playback position remains precise.
 */
@OptIn(UnstableApi::class)
fun Player.useScrubbingMode(enabled: Boolean) {
    val exo = this as? ExoPlayer ?: return
    if (enabled) {
        // A fixed window works for a three-second GIF and a three-minute video alike. It is wide
        // enough to catch the surrounding GOP's sync frame, which is especially important when
        // dragging backward because the decoder cannot reuse already-decoded forward frames.
        exo.setSeekParameters(
            SeekParameters(
                SCRUB_SYNC_TOLERANCE_US,
                SCRUB_SYNC_TOLERANCE_US,
            ),
        )
        exo.setScrubbingModeParameters(
            ScrubbingModeParameters.DEFAULT
                .buildUpon()
                // Use the fixed player-level window above instead of a duration fraction.
                .setFractionalSeekTolerance(null, null)
                .build(),
        )
    }
    exo.setScrubbingModeEnabled(enabled)
    if (!enabled) exo.setSeekParameters(SeekParameters.DEFAULT)
}

private const val SCRUB_SYNC_TOLERANCE_US = 2_000_000L
private const val MEDIA_BACK_BUFFER_MS = 30_000
