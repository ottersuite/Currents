package app.otter.client.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.otter.client.ui.theme.otterColors
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SwipeAction {
    Upvote,
    Downvote,
    Save,
    Hide,
    Reply,
    Collapse,
}

/**
 * A two-stage, gesture-first action row. A short swipe chooses the primary
 * action and a deeper swipe crosses a second haptic threshold.
 */
@Composable
fun SwipeActionRow(
    enabled: Boolean = true,
    hapticsEnabled: Boolean = true,
    rightShortAction: SwipeAction = SwipeAction.Upvote,
    rightLongAction: SwipeAction = SwipeAction.Downvote,
    leftShortAction: SwipeAction = SwipeAction.Save,
    leftLongAction: SwipeAction = SwipeAction.Hide,
    onAction: (SwipeAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val shortThreshold = with(density) { 84.dp.toPx() }
    val longThreshold = with(density) { 168.dp.toPx() }
    val maxDrag = with(density) { 200.dp.toPx() }
    // Deliberately larger than the system's touch slop. A swipe has to be asked for, because the
    // same finger is usually scrolling, and a scroll that drifts sideways must stay a scroll.
    val activationSlop = with(density) { 32.dp.toPx() }
    val abandonSlop = with(density) { 20.dp.toPx() }

    var rawOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var thresholdStage by remember { mutableIntStateOf(0) }
    val currentOnAction by rememberUpdatedState(onAction)
    val restingTranslation by animateFloatAsState(
        targetValue = if (dragging) rawOffset else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "swipe row translation",
    )
    // Track the pointer directly while dragging; only the release-to-rest motion is animated.
    val translation = if (dragging) rawOffset else restingTranslation

    val direction = if (translation >= 0f) 1 else -1
    val distance = abs(translation)
    val action = resolveSwipeAction(
        translation,
        longThreshold,
        rightShortAction,
        rightLongAction,
        leftShortAction,
        leftLongAction,
    )
    val actionColor = when (action) {
        SwipeAction.Upvote -> colors.upvote
        SwipeAction.Downvote -> colors.downvote
        SwipeAction.Save -> colors.saved
        SwipeAction.Hide -> colors.textSecondary
        SwipeAction.Reply -> colors.accent
        SwipeAction.Collapse -> colors.textSecondary
    }
    val actionLabel = when (action) {
        SwipeAction.Upvote -> "Upvote"
        SwipeAction.Downvote -> "Downvote"
        SwipeAction.Save -> "Save"
        SwipeAction.Hide -> "Hide"
        SwipeAction.Reply -> "Reply"
        SwipeAction.Collapse -> "Collapse"
    }
    val actionIcon = when (action) {
        SwipeAction.Upvote -> Icons.Outlined.ArrowUpward
        SwipeAction.Downvote -> Icons.Outlined.ArrowDownward
        SwipeAction.Save -> Icons.Outlined.BookmarkBorder
        SwipeAction.Hide -> Icons.Outlined.VisibilityOff
        SwipeAction.Reply -> Icons.AutoMirrored.Outlined.Reply
        SwipeAction.Collapse -> Icons.Outlined.KeyboardArrowUp
    }
    val backgroundColor by animateColorAsState(
        targetValue = if (distance >= shortThreshold) actionColor else colors.surfaceRaised,
        label = "swipe action color",
    )

    Box(
        modifier = Modifier
            .background(backgroundColor)
            .pointerInput(
                enabled,
                hapticsEnabled,
                shortThreshold,
                longThreshold,
                activationSlop,
                rightShortAction,
                rightLongAction,
                leftShortAction,
                leftLongAction,
            ) {
                if (!enabled) return@pointerInput
                // detectHorizontalDragGestures claims a gesture on horizontal slop alone, which
                // is why a scroll with any sideways drift used to turn into a swipe. This waits
                // to see which way the finger is really going, and stays out of the way until it
                // is sure. Nothing is consumed before then, so taps and scrolling are untouched.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalX = 0f
                    var totalY = 0f
                    var claimed = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val delta = change.positionChange()
                        totalX += delta.x
                        totalY += delta.y

                        if (!claimed) {
                            // The list scrolls by consuming; if it already has, this gesture is
                            // not ours, and joining in would slide the row while it scrolls.
                            if (change.isConsumed) break
                            // Vertical intent first: the list keeps the gesture.
                            if (abs(totalY) > abandonSlop && abs(totalY) >= abs(totalX)) break
                            val horizontalEnough = abs(totalX) >= activationSlop &&
                                abs(totalX) > abs(totalY) * DIRECTION_RATIO
                            if (!horizontalEnough) continue
                            claimed = true
                            dragging = true
                            thresholdStage = 0
                        }

                        change.consume()
                        rawOffset = (rawOffset + delta.x).coerceIn(-maxDrag, maxDrag)
                        val nextStage = when {
                            abs(rawOffset) >= longThreshold -> 2
                            abs(rawOffset) >= shortThreshold -> 1
                            else -> 0
                        }
                        if (nextStage != thresholdStage) {
                            thresholdStage = nextStage
                            if (hapticsEnabled && nextStage > 0) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }

                    if (claimed) {
                        val releasedOffset = rawOffset
                        if (abs(releasedOffset) >= shortThreshold) {
                            currentOnAction(
                                resolveSwipeAction(
                                    releasedOffset,
                                    longThreshold,
                                    rightShortAction,
                                    rightLongAction,
                                    leftShortAction,
                                    leftLongAction,
                                ),
                            )
                        }
                        dragging = false
                        rawOffset = 0f
                        thresholdStage = 0
                    }
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(if (direction > 0) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 25.dp)
                .graphicsLayer {
                    val progress = (distance / shortThreshold).coerceIn(0f, 1f)
                    scaleX = 0.75f + progress * 0.25f
                    scaleY = scaleX
                }
                .alpha((distance / (shortThreshold * .65f)).coerceIn(0f, 1f)),
        ) {
            Icon(
                imageVector = actionIcon,
                contentDescription = actionLabel,
                tint = if (distance >= shortThreshold) Color.White else colors.textSecondary,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
            Text(
                text = actionLabel,
                color = if (distance >= shortThreshold) Color.White else colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(translation.roundToInt(), 0) },
        ) {
            content()
        }
    }
}

/** How much more horizontal than vertical a drag must be before it counts as a swipe. */
private const val DIRECTION_RATIO = 1.5f

private fun resolveSwipeAction(
    offset: Float,
    longThreshold: Float,
    rightShortAction: SwipeAction,
    rightLongAction: SwipeAction,
    leftShortAction: SwipeAction,
    leftLongAction: SwipeAction,
): SwipeAction {
    val distance = abs(offset)
    return when {
        offset >= 0f && distance >= longThreshold -> rightLongAction
        offset >= 0f -> rightShortAction
        distance >= longThreshold -> leftLongAction
        else -> leftShortAction
    }
}
