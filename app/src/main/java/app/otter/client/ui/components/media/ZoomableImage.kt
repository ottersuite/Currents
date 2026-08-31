package app.otter.client.ui.components.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otter.client.data.MediaLoadProgress
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Shows how far the image has actually got.
 *
 * A determinate ring whenever the download reports a size, which on Reddit's image hosts is
 * nearly always. It falls back to an indeterminate one while the request is still being made, or
 * for a response that never declared a length — the difference between "this is 20% done" and
 * "something is happening" is the whole point of showing it.
 */
@Composable
private fun LoadingIndicator(url: String) {
    val fraction by remember(url) {
        MediaLoadProgress.fractions.map { it[url] }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    val current = fraction
    if (current == null) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(34.dp),
        )
    } else {
        CircularProgressIndicator(
            progress = { current },
            color = Color.White,
            trackColor = Color.White.copy(alpha = .22f),
            strokeWidth = 2.dp,
            modifier = Modifier.size(34.dp),
        )
    }
}

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * A pinch- and pan-zoomable image.
 *
 * [onZoomedChange] reports whether the image is currently magnified, so the surrounding pager and
 * swipe-to-dismiss can stand down while a drag belongs to the image instead of the gallery.
 *
 * [onLongPress] is left null by callers that have nothing to offer on a hold; passing null keeps
 * the gesture unclaimed rather than registering a handler that does nothing.
 */
@Composable
fun ZoomableImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onZoomedChange: (Boolean) -> Unit = {},
    onTap: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
) {
    // The callbacks are fresh lambdas on every recomposition, so keying the gesture detector on
    // them restarted it constantly -- and a restart mid-hold cancels the press the detector was
    // in the middle of timing. Only whether a hold is claimed at all is worth restarting for.
    val currentTap by rememberUpdatedState(onTap)
    val currentLongPress by rememberUpdatedState(onLongPress)
    val holdClaimed = onLongPress != null

    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    val settledScale by animateFloatAsState(scale, label = "zoom")

    LaunchedEffect(scale) { onZoomedChange(scale > 1.01f) }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val maxPanX = { current: Float -> maxWidth.value * (current - 1f) / 2f }
        val maxPanY = { current: Float -> maxHeight.value * (current - 1f) / 2f }

        fun clamp(candidate: Offset, current: Float): Offset =
            if (current <= 1f) {
                Offset.Zero
            } else {
                Offset(
                    x = candidate.x.coerceIn(-maxPanX(current), maxPanX(current)),
                    y = candidate.y.coerceIn(-maxPanY(current), maxPanY(current)),
                )
            }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(url, holdClaimed) {
                    detectTapGestures(
                        onTap = { currentTap() },
                        onLongPress = if (holdClaimed) {
                            { currentLongPress?.invoke() }
                        } else {
                            null
                        },
                        onDoubleTap = { tap ->
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = DOUBLE_TAP_SCALE
                                // Zoom toward the point that was tapped, not the middle.
                                val fromCenter = Offset(
                                    x = (size.width / 2f) - tap.x,
                                    y = (size.height / 2f) - tap.y,
                                )
                                offset = clamp(fromCenter * (DOUBLE_TAP_SCALE - 1f), DOUBLE_TAP_SCALE)
                            }
                        },
                    )
                }
                .pointerInput(url) {
                    // detectTransformGestures would swallow every pan, including the horizontal
                    // swipe that belongs to the gallery pager. Consume only what zoom owns: a
                    // pinch, or a drag while the image is already magnified.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val pinching = event.changes.size > 1
                            val magnified = scale > 1.01f
                            if (pinching || magnified) {
                                val next = (scale * zoomChange).coerceIn(1f, MAX_SCALE)
                                scale = next
                                offset = clamp(offset + panChange, next)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            var loaded by remember(url) { mutableStateOf(false) }
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                onState = { state ->
                    // Error counts as settled: a failed image should not spin forever.
                    loaded = state !is AsyncImagePainter.State.Loading &&
                        state !is AsyncImagePainter.State.Empty
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = settledScale
                        scaleY = settledScale
                        translationX = offset.x * density
                        translationY = offset.y * density
                    },
            )
            if (!loaded) LoadingIndicator(url)
        }
    }
}
