package app.otter.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.otter.client.model.MediaAsset
import app.otter.client.model.MediaKind
import app.otter.client.ui.components.media.MediaSurface
import app.otter.client.ui.components.media.ZoomableImage
import app.otter.client.ui.components.media.rememberMediaPlayer
import app.otter.client.ui.components.media.useScrubbingMode
import app.otter.client.ui.theme.otterColors
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** What the viewer was opened with: one post's assets, and which one was tapped. */
data class MediaViewerRequest(
    val title: String,
    val assets: List<MediaAsset>,
    val startIndex: Int = 0,
) {
    init {
        require(assets.isNotEmpty()) { "Media viewer needs at least one asset" }
    }

    val safeStartIndex: Int get() = startIndex.coerceIn(0, assets.lastIndex)
}

private const val DISMISS_DISTANCE_PX = 320f
private const val SCRUB_PREVIEW_INTERVAL_MS = 33L

/**
 * Full-screen media, gallery-aware.
 *
 * Only the settled page plays: swiping to the next clip stops the previous one rather than
 * leaving several decoders running. A vertical drag dismisses, fading the backdrop as it goes,
 * and stands down while an image is zoomed so the drag pans instead.
 */
@Composable
fun MediaViewerScreen(
    request: MediaViewerRequest,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = request.safeStartIndex,
        pageCount = { request.assets.size },
    )
    var chromeVisible by remember { mutableStateOf(true) }
    var muted by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val backdropAlpha = (1f - abs(dragOffset) / (DISMISS_DISTANCE_PX * 2.4f)).coerceIn(.35f, 1f)

    BackHandler(onBack = onClose)

    // A muted loop should not hold the screen awake, but an unmuted GIF is being actively watched.
    val view = LocalView.current
    val currentAsset = request.assets[pagerState.currentPage.coerceIn(0, request.assets.lastIndex)]
    DisposableEffect(currentAsset, muted) {
        view.keepScreenOn = currentAsset.needsPlayer &&
            (currentAsset.kind == MediaKind.VIDEO || !muted)
        onDispose { view.keepScreenOn = false }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropAlpha)),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffset
                    // Shrinking as it goes makes the throw feel like it is leaving the screen.
                    val shrink = (1f - abs(dragOffset) / 2600f).coerceIn(.86f, 1f)
                    scaleX = shrink
                    scaleY = shrink
                },
        ) { page ->
            MediaPage(
                asset = request.assets[page],
                // settledPage, not currentPage: playback waits until the swipe finishes.
                active = pagerState.settledPage == page && abs(dragOffset) < 1f,
                muted = muted,
                chromeVisible = chromeVisible,
                dismissEnabled = !zoomed,
                // Scrubbing claims horizontal drags, which is how the pager moves between
                // gallery items — so a gallery keeps its swipe and only a lone clip scrubs.
                scrubbable = request.assets.size == 1,
                onMutedChange = { muted = it },
                onZoomedChange = { zoomed = it },
                onTap = { chromeVisible = !chromeVisible },
                onDismissDrag = { delta -> dragOffset += delta },
                onDismissDragEnd = {
                    if (abs(dragOffset) > DISMISS_DISTANCE_PX) onClose() else dragOffset = 0f
                },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ViewerTopBar(
                title = request.title,
                position = pagerState.currentPage + 1,
                count = request.assets.size,
                onClose = onClose,
            )
        }

        currentAsset.caption?.takeIf { chromeVisible && it.isNotBlank() }?.let { caption ->
            Text(
                text = caption,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 96.dp),
            )
        }
    }

    // Keep the pager honest if the request changes underneath an open viewer.
    LaunchedEffect(request) { pagerState.scrollToPage(request.safeStartIndex) }
}

@Composable
private fun ViewerTopBar(title: String, position: Int, count: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = .35f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = "Close media", tint = Color.White)
        }
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            lineHeight = 18.sp,
            // A post title is a sentence, not a label: let it use the width it needs.
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
        )
        if (count > 1) {
            Text(
                text = "$position / $count",
                color = Color.White.copy(alpha = .85f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}

@Composable
private fun MediaPage(
    asset: MediaAsset,
    active: Boolean,
    muted: Boolean,
    chromeVisible: Boolean,
    dismissEnabled: Boolean,
    scrubbable: Boolean,
    onMutedChange: (Boolean) -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onDismissDrag: (Float) -> Unit,
    onDismissDragEnd: () -> Unit,
) {
    // The drag lives on the page rather than around the pager: as a parent of the pager it only
    // ever saw gestures the pages had already handled.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(dismissEnabled, asset) {
                if (!dismissEnabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = onDismissDragEnd,
                    onDragCancel = onDismissDragEnd,
                    onVerticalDrag = { _, delta -> onDismissDrag(delta) },
                )
            },
    ) {
    val imageUrl = asset.animatedImageUrl ?: asset.url

    // A GIF with no MP4 encoding is still an image as far as playback goes.
    when {
        !asset.needsPlayer -> ZoomableImage(
            url = imageUrl,
            contentDescription = asset.caption,
            modifier = Modifier.fillMaxSize(),
            onZoomedChange = onZoomedChange,
            onTap = onTap,
        )

        else -> PlayablePage(
            asset = asset,
            active = active,
            muted = muted,
            chromeVisible = chromeVisible,
            scrubbable = scrubbable,
            onMutedChange = onMutedChange,
            onTap = onTap,
        )
    }
    }
}

@Composable
private fun PlayablePage(
    asset: MediaAsset,
    active: Boolean,
    muted: Boolean,
    chromeVisible: Boolean,
    scrubbable: Boolean,
    onMutedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    var playbackFailed by remember(asset) { mutableStateOf(false) }
    val player = rememberMediaPlayer(
        asset = asset,
        play = active,
        muted = muted,
        onExhausted = { playbackFailed = true },
    )

    if (playbackFailed) {
        // Every playable source failed. A GIF post still has frames worth showing.
        val stillUrl = asset.animatedImageUrl ?: asset.previewUrl
        if (stillUrl != null) {
            ZoomableImage(
                url = stillUrl,
                contentDescription = asset.caption,
                modifier = Modifier.fillMaxSize(),
                onTap = onTap,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "This media could not be played",
                    color = Color.White.copy(alpha = .8f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(asset.durationSeconds * 1000L) }
    var playing by remember(player) { mutableStateOf(true) }
    var scrubbing by remember(player) { mutableStateOf(false) }
    var resumeAfterScrub by remember(player) { mutableStateOf(false) }
    var settleAfterScrub by remember(player) { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (isActive) {
            // While dragging, the finger owns the position; polling would drag it backwards.
            if (!scrubbing) positionMs = player.currentPosition.coerceAtLeast(0L)
            player.duration.takeIf { it > 0L }?.let { durationMs = it }
            playing = player.isPlaying
            delay(120)
        }
    }

    // Touch events can arrive at 120 Hz or faster. The decoder cannot produce useful frames at
    // that rate, so sample the newest target at roughly display-video cadence. Skipped touch
    // positions are intentional: the preview stays with the finger instead of replaying a queue.
    LaunchedEffect(scrubbing, player) {
        if (!scrubbing) return@LaunchedEffect
        var seeked = -1L
        while (isActive) {
            val target = positionMs
            if (target != seeked) {
                seeked = target
                player.seekTo(target)
            }
            delay(SCRUB_PREVIEW_INTERVAL_MS)
        }
    }

    // Preview seeks are tolerant and fast; settle exactly once where the finger was released.
    LaunchedEffect(scrubbing, settleAfterScrub, player) {
        if (scrubbing || !settleAfterScrub) return@LaunchedEffect
        settleAfterScrub = false
        player.useScrubbingMode(false)
        player.seekTo(positionMs)
        if (resumeAfterScrub) {
            resumeAfterScrub = false
            player.play()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Wraps the surface so the progress fill can sit on the media itself rather than
        // floating somewhere over the black backdrop.
        Box {
            MediaSurface(
                player = player,
                previewUrl = asset.previewUrl,
                contentDescription = asset.caption,
                fallbackAspectRatio = asset.aspectRatio,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(asset, scrubbable, durationMs) {
                        detectTapGestures(onTap = { onTap() })
                    }
                    .pointerInput(asset, scrubbable, durationMs) {
                        if (!scrubbable || durationMs <= 0L) return@pointerInput
                        // Dragging across the full width covers the whole clip, the same
                        // mapping the fill below uses, so the two agree with each other.
                        var startMs = 0L
                        var travelled = 0f

                        detectHorizontalDragGestures(
                            onDragStart = {
                                startMs = player.currentPosition.coerceAtLeast(0L)
                                travelled = 0f
                                // Playback would otherwise keep advancing under the drag and
                                // fight it; the frame should follow the finger and nothing else.
                                resumeAfterScrub = player.isPlaying
                                settleAfterScrub = false
                                player.pause()
                                player.useScrubbingMode(true)
                                scrubbing = true
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                travelled += amount
                                val moved = travelled / size.width.toFloat() * durationMs
                                // Only report where the finger is. The coalescing driver below
                                // samples the newest position instead of replaying every event.
                                positionMs = (startMs + moved).toLong().coerceIn(0L, durationMs)
                            },
                            onDragEnd = {
                                settleAfterScrub = true
                                scrubbing = false
                            },
                            onDragCancel = {
                                settleAfterScrub = true
                                scrubbing = false
                            },
                        )
                    },
            )

            if (scrubbable && durationMs > 0L) {
                ScrubFill(
                    progress = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f),
                    active = scrubbing,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
        }

        if (asset.kind == MediaKind.ANIMATED) {
            GifBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 18.dp, top = 64.dp),
            )
        }

        AnimatedVisibility(
            visible = chromeVisible && asset.needsPlayer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            VideoControls(
                player = player,
                playing = playing,
                positionMs = positionMs,
                durationMs = durationMs,
                muted = muted,
                onMutedChange = onMutedChange,
            )
        }
    }
}

/**
 * Position as a plain fill, no handle: the media itself is the scrub surface, so a knob would
 * only be something else to miss.
 */
@Composable
private fun ScrubFill(progress: Float, active: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.otterColors
    val height by animateDpAsState(if (active) 5.dp else 3.dp, label = "scrub height")

    Box(
        modifier = modifier
            .height(height)
            .background(Color.Black.copy(alpha = .35f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(if (active) colors.accent else Color.White.copy(alpha = .8f)),
        )
    }
}

@Composable
private fun GifBadge(modifier: Modifier = Modifier) {
    Text(
        text = "GIF",
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier
            .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun VideoControls(
    player: Player,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    muted: Boolean,
    onMutedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = .45f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
            Icon(
                imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = "${clock(positionMs)} / ${clock(durationMs)}",
            color = Color.White.copy(alpha = .9f),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { onMutedChange(!muted) }) {
            Icon(
                imageVector = if (muted) {
                    Icons.AutoMirrored.Outlined.VolumeOff
                } else {
                    Icons.AutoMirrored.Outlined.VolumeUp
                },
                contentDescription = if (muted) "Unmute" else "Mute",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun clock(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
