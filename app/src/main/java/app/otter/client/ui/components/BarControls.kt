package app.otter.client.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.otter.client.ui.theme.otterColors
import kotlin.math.abs

/**
 * Scrolls to [index] so that the arrival is animated but the journey is not.
 *
 * An animated scroll travels the list, and every row it passes has to be composed, measured and
 * laid out purely to be scrolled by — between two top-level comments that is routinely dozens of
 * rows carrying nested rails and rendered markdown, and from the bottom of a feed to the top it
 * is all of them.
 *
 * So only the last stretch animates. Anything beyond that is closed instantly first, leaving the
 * animation a fixed, small number of rows to cross however far apart the two ends actually are.
 * The motion that tells the reader which way the list moved is the part right before it lands.
 */
suspend fun LazyListState.smoothScrollTo(index: Int) {
    val distance = index - firstVisibleItemIndex
    if (abs(distance) > SMOOTH_SCROLL_RUNWAY) {
        val runway = if (distance > 0) SMOOTH_SCROLL_RUNWAY else -SMOOTH_SCROLL_RUNWAY
        scrollToItem((index - runway).coerceAtLeast(0))
    }
    animateScrollToItem(index)
}

/**
 * A round action set apart from the action bar, sized and aligned to sit level with it.
 *
 * Matches the bar's own 50dp touch target rather than a smaller floating button: these are for
 * repeated one-handed taps at the edges of the screen, so the target is the point.
 *
 * [onLongClick] is for the reverse of whatever the tap does. Pairing both directions on one
 * button keeps the thumb in one place, which is the whole reason the control is out here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoundBarButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    Surface(
        color = colors.surfaceGlass,
        shape = CircleShape,
        shadowElevation = 8.dp,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 12.dp)
            .size(50.dp)
            // Surface applies its own shape after this modifier chain, so a ripple started
            // inside the chain is drawn before anything clips it -- which showed up as a grey
            // square behind a round button, obvious against a light surface and easy to miss
            // against a dark one. Clip here so the press stays inside the circle it belongs to.
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.accent,
            modifier = Modifier.padding(13.dp),
        )
    }
}

/** How far the flanking round buttons sit in from the screen edge, on every screen. */
val BAR_EDGE_INSET = 14.dp

/** How many rows a jump actually animates across, however far it travels. */
private const val SMOOTH_SCROLL_RUNWAY = 5
