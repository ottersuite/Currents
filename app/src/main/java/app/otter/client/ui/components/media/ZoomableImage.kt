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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * A pinch- and pan-zoomable image.
 *
 * [onZoomedChange] reports whether the image is currently magnified, so the surrounding pager and
 * swipe-to-dismiss can stand down while a drag belongs to the image instead of the gallery.
 */
@Composable
fun ZoomableImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onZoomedChange: (Boolean) -> Unit = {},
    onTap: () -> Unit = {},
) {
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
                .pointerInput(url) {
                    detectTapGestures(
                        onTap = { onTap() },
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
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = settledScale
                        scaleY = settledScale
                        translationX = offset.x * density
                        translationY = offset.y * density
                    },
            )
        }
    }
}
