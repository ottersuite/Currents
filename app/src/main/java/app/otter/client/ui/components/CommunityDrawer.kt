package app.otter.client.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.otter.client.R
import app.otter.client.model.Community
import app.otter.client.model.RedditAccountState
import app.otter.client.ui.RedditConnectionState
import app.otter.client.ui.theme.otterColors

private data class DrawerLink(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
)

@Composable
fun OtterSideMenu(
    visible: Boolean,
    selectedFeed: String,
    communities: List<Community>,
    connectionState: RedditConnectionState,
    accountState: RedditAccountState,
    onDismiss: () -> Unit,
    onSelectFeed: (String) -> Unit,
    onOpenSearch: () -> Unit,
    showRandomNsfw: Boolean,
    onRandomNsfw: () -> Unit,
    onAccountClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.otterColors
    val mainLinks = remember {
        listOf(
            DrawerLink("Home", "Your personalized front page", Icons.Outlined.Home),
            DrawerLink("Popular", "Trending across Reddit", Icons.Outlined.Whatshot),
            DrawerLink("All", "Everything in one stream", Icons.Outlined.Public),
            DrawerLink("Saved", "Posts kept for later", Icons.Outlined.BookmarkBorder),
        )
    }
    val favorites = remember(communities) {
        communities.filter(Community::isFavorite)
            .distinctBy { community -> community.name.lowercase() }
            // Alphabetical, ignoring case, so r/Android sits next to r/androiddev rather than
            // above every lowercase name. Reddit's own suggestions below keep their ranking.
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Community::name))
    }
    val shownFavorites = favorites

    // Held outside the AnimatedVisibility below, which disposes its content when the drawer
    // closes -- taking a scroll position remembered inside it along too. A long subreddit list
    // reopening at the top every time is the whole reason to hoist it.
    val listScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    BackHandler(enabled = visible, onBack = onDismiss)

    Box(modifier = modifier.fillMaxSize().zIndex(20f)) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .48f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(DRAWER_WIDTH_FRACTION)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                colors.drawerSurface,
                                colors.drawerSurface.copy(alpha = 1f),
                            ),
                        ),
                    )
                    .drawBehind {
                        drawLine(
                            color = colors.divider.copy(alpha = .9f),
                            start = Offset.Zero,
                            end = Offset(0f, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .pointerInput(Unit) {
                        var dragDistance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragDistance = 0f },
                            onHorizontalDrag = { change, amount ->
                                if (amount > 0f) change.consume()
                                dragDistance += amount
                            },
                            onDragEnd = {
                                if (dragDistance > 72.dp.toPx()) onDismiss()
                            },
                            onDragCancel = { dragDistance = 0f },
                        )
                    }
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OtterMark(modifier = Modifier.size(46.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Currents",
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = when (accountState) {
                                RedditAccountState.Unavailable -> when (connectionState) {
                                    RedditConnectionState.Unconfigured -> "Add your Reddit API settings"
                                    RedditConnectionState.SignedOut -> "Connect your Reddit account"
                                    RedditConnectionState.Connecting -> "Connecting to Reddit…"
                                    RedditConnectionState.Connected -> "Reddit browsing connected"
                                    RedditConnectionState.Error -> "Reddit is currently unavailable"
                                }
                                RedditAccountState.SignedOut -> "Connect your Reddit account"
                                RedditAccountState.Authorizing -> "Finish signing in with Reddit…"
                                is RedditAccountState.SignedIn -> "u/${accountState.account.username} · connected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.surfaceRaised)
                            .clickable(onClickLabel = "Reddit account") {
                                onAccountClick()
                                onDismiss()
                            }
                            .padding(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonOutline,
                            contentDescription = "Account",
                            tint = colors.textSecondary,
                        )
                    }
                }

                SearchEntry(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    onClick = {
                        onDismiss()
                        onOpenSearch()
                    },
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(listScrollState)
                        .padding(top = 12.dp),
                ) {
                    DrawerSectionLabel("MAIN")
                    mainLinks.forEach { link ->
                        DrawerRow(
                            title = link.title,
                            subtitle = link.subtitle,
                            icon = link.icon,
                            selected = selectedFeed == link.title,
                            onClick = {
                                onSelectFeed(link.title)
                                onDismiss()
                            },
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    DrawerSectionLabel("SUBREDDITS")
                    shownFavorites.forEach { community ->
                        CommunityRow(
                            community = community,
                            selected = selectedFeed == "r/${community.name}",
                            onClick = {
                                onSelectFeed("r/${community.name}")
                                onDismiss()
                            },
                        )
                    }
                    if (shownFavorites.isEmpty()) {
                        Text(
                            text = "Communities you subscribe to appear here",
                            color = colors.textTertiary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                    if (showRandomNsfw) {
                        Spacer(Modifier.height(12.dp))
                        DrawerSectionLabel("DISCOVER")
                        DrawerRow(
                            title = "Random NSFW",
                            subtitle = "Open a random adult community",
                            icon = Icons.Outlined.Casino,
                            selected = false,
                            onClick = {
                                onRandomNsfw()
                                onDismiss()
                            },
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }

                HorizontalDivider(color = colors.divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    DrawerFooterAction(Icons.Outlined.Explore, "Explore") {
                        onSelectFeed("Popular")
                        onDismiss()
                    }
                    DrawerFooterAction(Icons.Outlined.Settings, "Settings") {
                        onOpenSettings()
                        onDismiss()
                    }
                    DrawerFooterAction(Icons.Outlined.Info, "About") {
                        onOpenAbout()
                        onDismiss()
                    }
                }
            }
        }
    }
}

/** Reddit reports raw subscriber counts; the drawer only has room for the shape of the number. */
private fun memberSummary(memberCount: Int): String = when {
    memberCount <= 0 -> "Community"
    memberCount >= 1_000_000 -> "%.1fM members".format(memberCount / 1_000_000f)
    memberCount >= 10_000 -> "%dK members".format(memberCount / 1_000)
    memberCount >= 1_000 -> "%.1fK members".format(memberCount / 1_000f)
    else -> "$memberCount members"
}

@Composable
private fun SearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.otterColors
    Surface(
        color = colors.surfaceRaised,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Search Reddit", onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = "Search",
                color = colors.textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * A subreddit in the list: its own icon, its name, and as little else as possible.
 *
 * Deliberately tighter and quieter than [DrawerRow]. That row is for a handful of destinations
 * that each earn a subtitle; this one repeats for every community subscribed to, where the list
 * being scannable matters more than any single line being prominent.
 */
@Composable
private fun CommunityRow(
    community: Community,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent.copy(alpha = .14f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = COMMUNITY_ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(COMMUNITY_ICON_SIZE)
                .clip(CircleShape)
                // The community's accent sits underneath, so a subreddit with no icon still
                // reads as itself rather than as a grey hole in the list.
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(community.accentStartArgb),
                            Color(community.accentEndArgb),
                        ),
                    ),
                ),
        ) {
            val icon = community.iconUrl
            if (icon != null) {
                AsyncImage(
                    model = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    text = community.name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = community.name,
            color = if (selected) colors.accent else colors.textPrimary,
            fontSize = COMMUNITY_ROW_FONT_SIZE,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * How much of the screen the panel covers.
 *
 * The cap above only matters on a tablet; on a phone this fraction is what decides the width,
 * and it also decides how much of a community's name survives before it is ellipsized. At .86
 * the panel covered the feed almost entirely; at .25 a name got about six characters.
 */
private const val DRAWER_WIDTH_FRACTION = .65f

/** Sized for a list that repeats, rather than for a row that appears once. */
private val COMMUNITY_ICON_SIZE = 26.dp
private val COMMUNITY_ROW_PADDING = 5.dp
private val COMMUNITY_ROW_FONT_SIZE = 14.sp

@Composable
private fun DrawerSectionLabel(text: String) {
    val colors = MaterialTheme.otterColors
    Text(
        text = text,
        color = colors.textTertiary,
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 7.dp, bottom = 7.dp),
    )
}

@Composable
private fun DrawerRow(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    val colors = MaterialTheme.otterColors
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accent.copy(alpha = .14f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (subtitle == null) 11.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(35.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) colors.accent.copy(alpha = .16f) else colors.surfaceRaised),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                text = title,
                color = if (selected) colors.accent else colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DrawerFooterAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.otterColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
    ) {
        Icon(icon, contentDescription = label, tint = colors.textSecondary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = colors.textTertiary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun OtterMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_artwork),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape),
    )
}
