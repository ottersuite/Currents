package app.otter.client.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import app.otter.client.model.Post
import app.otter.client.model.Community
import app.otter.client.model.VoteState
import app.otter.client.ui.OtterSettings
import app.otter.client.ui.FeedScroll
import app.otter.client.ui.FeedAction
import app.otter.client.ui.FeedSort
import app.otter.client.ui.FeedTimeframe
import app.otter.client.ui.RedditConnectionState
import app.otter.client.ui.components.ActionBarItem
import app.otter.client.ui.components.compactNumber
import app.otter.client.ui.components.FeedPost
import app.otter.client.ui.components.GlassActionBar
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import app.otter.client.ui.components.smoothScrollTo
import app.otter.client.ui.components.RoundBarButton
import app.otter.client.ui.components.BAR_EDGE_INSET
import app.otter.client.ui.theme.otterColors
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun FeedScreen(
    posts: List<Post>,
    selectedFeed: String,
    sort: FeedSort,
    timeframe: FeedTimeframe,
    searchVisible: Boolean,
    searchQuery: String,
    refreshing: Boolean,
    loadingMore: Boolean,
    connectionState: RedditConnectionState,
    selectedPostId: String?,
    postsAreServerSorted: Boolean,
    hiddenPostIds: Set<String>,
    settings: OtterSettings,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onSortChange: (FeedSort) -> Unit,
    onTimeframeChange: (FeedTimeframe) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onCompose: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenMedia: (Post) -> Unit,
    scrollTarget: FeedScroll?,
    onScrollChanged: (Int, Int) -> Unit,
    onScrollTargetConsumed: () -> Unit,
    onVote: (String, VoteState) -> Unit,
    onSave: (String) -> Unit,
    onHide: (String) -> Unit,
    modifier: Modifier = Modifier,
    community: Community? = null,
    onToggleCommunitySubscription: (Community) -> Unit = {},
    onShowSaved: () -> Unit = {},
    onMarkRead: (Collection<String>) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val colors = MaterialTheme.otterColors
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val coroutineScope = rememberCoroutineScope()
    val filteredPosts = remember(
        posts,
        selectedFeed,
        sort,
        searchQuery,
        hiddenPostIds,
        postsAreServerSorted,
        settings,
    ) {
        posts
            .asSequence()
            .filterNot { it.id in hiddenPostIds }
            .filterNot { settings.hideReadPosts && it.isRead }
            .filterNot { post ->
                post.community.name.lowercase() in settings.blockedCommunities ||
                    post.author.lowercase() in settings.blockedAuthors ||
                    settings.blockedKeywords.any { word ->
                        post.title.contains(word, ignoreCase = true) ||
                            post.body?.contains(word, ignoreCase = true) == true
                    }
            }
            .filter { post ->
                when {
                    selectedFeed == "Saved" -> post.isSaved
                    selectedFeed.startsWith("r/") -> post.community.path.equals(selectedFeed, ignoreCase = true)
                    else -> true
                }
            }
            .filter { post ->
                searchQuery.isBlank() ||
                    post.title.contains(searchQuery, ignoreCase = true) ||
                    post.community.name.contains(searchQuery, ignoreCase = true) ||
                    post.author.contains(searchQuery, ignoreCase = true)
            }
            .let { sequence ->
                if (postsAreServerSorted && selectedFeed != "Saved") {
                    sequence
                } else when (sort) {
                    FeedSort.Best -> sequence
                    FeedSort.Hot -> sequence.sortedByDescending { it.score + it.commentCount * 3 }
                    FeedSort.New -> sequence.sortedByDescending { it.createdAtEpochSeconds }
                    FeedSort.Top -> sequence.sortedByDescending { it.score }
                    FeedSort.Rising -> sequence.sortedByDescending {
                        val ageMinutes = max(1L, (System.currentTimeMillis() / 1000L - it.createdAtEpochSeconds) / 60L)
                        it.score.toDouble() / ageMinutes
                    }
                }
            }
            .toList()
    }

    LaunchedEffect(scrollTarget) {
        scrollTarget?.let { target ->
            listState.scrollToItem(target.index, target.offset)
            onScrollTargetConsumed()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onScrollChanged(index, offset) }
    }

    LaunchedEffect(listState, filteredPosts.size) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layout.totalItemsCount - LOAD_MORE_LEAD
        }
            .distinctUntilChanged()
            // Start fetching before the end arrives, so the next page is usually already there.
            .collect { nearEnd -> if (nearEnd && filteredPosts.isNotEmpty()) onLoadMore() }
    }

    val pullState = rememberPullToRefreshState()
    val postsAboveIds by remember(listState, filteredPosts) {
        derivedStateOf {
            val firstVisiblePost = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { item -> item.key is String && filteredPosts.any { it.id == item.key } }
                ?.key as? String
            firstVisiblePost
                ?.let { id -> filteredPosts.indexOfFirst { it.id == id } }
                ?.takeIf { it > 0 }
                ?.let(filteredPosts::take)
                .orEmpty()
                .map(Post::id)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.canvas)) {
      PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            // The default indicator would hide under the floating top bar.
            Indicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarHeight + 58.dp),
                containerColor = colors.surfaceRaised,
                color = colors.accent,
            )
        },
        state = pullState,
      ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = statusBarHeight + 58.dp,
                bottom = navigationBarHeight + 112.dp,
            ),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            if (connectionState != RedditConnectionState.Connected) {
                item(key = "connection-banner") {
                    ConnectionBanner(connectionState)
                }
            }

            community?.let { selectedCommunity ->
                item(key = "community-header-${selectedCommunity.name}") {
                    CommunityHeader(
                        community = selectedCommunity,
                        onToggleSubscription = {
                            onToggleCommunitySubscription(selectedCommunity)
                        },
                    )
                    HorizontalDivider(color = colors.divider, thickness = .6.dp)
                }
            }

            if (filteredPosts.isEmpty()) {
                item(key = "empty") {
                    EmptyFeed(
                        title = if (searchQuery.isNotBlank()) "No posts match “$searchQuery”" else "Nothing here yet",
                        body = when {
                            connectionState == RedditConnectionState.Unconfigured ->
                                "Add your Reddit API settings, then connect your account."
                            connectionState == RedditConnectionState.SignedOut ->
                                "Connect your Reddit account to load the feed."
                            connectionState == RedditConnectionState.Connecting ->
                                "Your Reddit feed is loading."
                            connectionState == RedditConnectionState.Error ->
                                "Check your connection and Reddit API settings, then try again."
                            selectedFeed == "Saved" ->
                                "Swipe a post left or tap its bookmark to keep it here."
                            else -> "Try another community or refresh the feed."
                        },
                    )
                }
            } else {
                items(filteredPosts, key = { it.id }) { post ->
                    FeedPost(
                        post = post,
                        presentation = settings.feedPresentation,
                        thumbnailsOnRight = settings.thumbnailsOnRight,
                        textScale = settings.textScale,
                        dimRead = settings.dimReadPosts,
                        showFlairs = settings.showPostFlairs,
                        swipeEnabled = settings.swipeActions,
                        hapticsEnabled = settings.haptics,
                        swipeActions = settings.postSwipeActions,
                        isSelected = post.id == selectedPostId,
                        revealNsfw = settings.alwaysShowNsfw,
                        onOpen = { onOpenPost(post.id) },
                        onOpenMedia = { onOpenMedia(post) },
                        onVote = { onVote(post.id, it) },
                        onSave = { onSave(post.id) },
                        onHide = { onHide(post.id) },
                    )
                    HorizontalDivider(color = colors.divider, thickness = .6.dp)
                }

                if (loadingMore) {
                    item(key = "loading-more") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                color = colors.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
      }

        FeedTopBar(
            title = feedTitle(selectedFeed),
            sort = sort,
            timeframe = timeframe,
            searchVisible = searchVisible,
            searchQuery = searchQuery,
            onToggleSearch = onToggleSearch,
            onSearchQueryChange = onSearchQueryChange,
            onSubmitSearch = onSubmitSearch,
            onSortChange = onSortChange,
            onTimeframeChange = onTimeframeChange,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // The same control the post screen puts here: same size, same icon, same corner, and
        // the same scroll that animates only the arrival rather than the whole way up.
        RoundBarButton(
            icon = Icons.Outlined.KeyboardDoubleArrowUp,
            contentDescription = "Jump to top",
            modifier = Modifier.align(Alignment.BottomStart).padding(start = BAR_EDGE_INSET),
        ) {
            coroutineScope.launch { listState.smoothScrollTo(0) }
        }

        GlassActionBar(
            items = settings.feedActions.map { action ->
                when (action) {
                    FeedAction.Search -> ActionBarItem(Icons.Outlined.Search, action.label) { onOpenSearch() }
                    FeedAction.Refresh -> ActionBarItem(Icons.Outlined.Refresh, action.label) { onRefresh() }
                    FeedAction.Saved -> ActionBarItem(
                        Icons.Outlined.BookmarkBorder,
                        action.label,
                        selected = selectedFeed == "Saved",
                    ) { onShowSaved() }
                    FeedAction.Compose -> ActionBarItem(
                        Icons.Outlined.Add,
                        action.label,
                        emphasized = true,
                    ) { onCompose() }
                    FeedAction.MarkAboveRead -> ActionBarItem(
                        Icons.Outlined.DoneAll,
                        action.label,
                    ) { onMarkRead(postsAboveIds) }
                    FeedAction.Menu -> ActionBarItem(Icons.Outlined.Menu, action.label) { onOpenDrawer() }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun CommunityHeader(
    community: Community,
    onToggleSubscription: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Surface(
            color = Color(community.accentStartArgb).copy(alpha = .16f),
            shape = CircleShape,
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    community.name.take(1).uppercase(),
                    color = Color(community.accentStartArgb),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = community.name,
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (community.memberCount > 0) {
                        append(compactNumber(community.memberCount))
                        append(" members")
                    } else {
                        append("Community")
                    }
                },
                color = colors.textTertiary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        Surface(
            color = if (community.isFavorite) {
                colors.accent.copy(alpha = .15f)
            } else {
                colors.accent
            },
            contentColor = if (community.isFavorite) colors.accent else Color.White,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.clickable(
                onClickLabel = if (community.isFavorite) {
                    "Unfollow ${community.name}"
                } else {
                    "Follow ${community.name}"
                },
                role = Role.Button,
                onClick = onToggleSubscription,
            ),
        ) {
            Text(
                text = if (community.isFavorite) "Following" else "Follow",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Internal feed names carry a prefix; the bar shows what the user actually asked for. */
/** How many rows from the end to start the next page. */
private const val LOAD_MORE_LEAD = 6


private fun feedTitle(feedName: String): String = when {
    feedName.startsWith("qr/") -> feedName.removePrefix("qr/").let { scoped ->
        "${scoped.substringBefore('/')} · “${scoped.substringAfter('/', "")}”"
    }
    feedName.startsWith("q/") -> "“${feedName.removePrefix("q/")}”"
    feedName.startsWith("u/") -> feedName
    feedName.startsWith("r/", ignoreCase = true) -> feedName.drop(2)
    else -> feedName
}

@Composable
private fun FeedTopBar(
    title: String,
    sort: FeedSort,
    timeframe: FeedTimeframe,
    searchVisible: Boolean,
    searchQuery: String,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onSortChange: (FeedSort) -> Unit,
    onTimeframeChange: (FeedTimeframe) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.otterColors
    var sortExpanded by remember { mutableStateOf(false) }
    var timeframeExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(colors.surface.copy(alpha = .99f), colors.surface.copy(alpha = .93f)),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(58.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(14.dp))

        if (searchVisible) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceRaised)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (searchQuery.isBlank()) {
                        Text("Search this feed", color = colors.textTertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                        // Typing narrows what is already loaded, which is instant and is often
                        // all that was wanted. Enter escalates the same words into a real search
                        // of the feed itself, for the posts that were never loaded to filter.
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { searchQuery.trim().takeIf(String::isNotEmpty)?.let(onSubmitSearch) },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            IconButton(onClick = onToggleSearch) {
                Icon(Icons.Outlined.Close, contentDescription = "Close search", tint = colors.textSecondary)
            }
        } else {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "for Reddit",
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Box {
                Surface(
                    color = colors.surfaceRaised,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable(role = Role.Button) { sortExpanded = true },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                    ) {
                        Text(sort.label, color = colors.accent, style = MaterialTheme.typography.labelLarge)
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = "Change sort",
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                    containerColor = colors.surfaceRaised,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    FeedSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.label,
                                    color = if (option == sort) colors.accent else colors.textPrimary,
                                    fontWeight = if (option == sort) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                onSortChange(option)
                                sortExpanded = false
                            },
                        )
                    }
                }
            }
            // Reddit only applies a window to Top, so the control appears only alongside it.
            if (sort == FeedSort.Top) {
                Spacer(Modifier.width(5.dp))
                Box {
                    Surface(
                        color = colors.surfaceRaised,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable(role = Role.Button) { timeframeExpanded = true },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(
                                start = 10.dp,
                                end = 6.dp,
                                top = 7.dp,
                                bottom = 7.dp,
                            ),
                        ) {
                            Text(
                                timeframe.label,
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Icon(
                                Icons.Outlined.ExpandMore,
                                contentDescription = "Change time frame",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = timeframeExpanded,
                        onDismissRequest = { timeframeExpanded = false },
                        containerColor = colors.surfaceRaised,
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        FeedTimeframe.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option.label,
                                        color = if (option == timeframe) {
                                            colors.accent
                                        } else {
                                            colors.textPrimary
                                        },
                                        fontWeight = if (option == timeframe) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    )
                                },
                                onClick = {
                                    onTimeframeChange(option)
                                    timeframeExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(5.dp))
        }
    }
}

@Composable
private fun ConnectionBanner(connectionState: RedditConnectionState) {
    val colors = MaterialTheme.otterColors
    val title = when (connectionState) {
        RedditConnectionState.Unconfigured -> "REDDIT NOT CONFIGURED"
        RedditConnectionState.SignedOut -> "REDDIT SIGN-IN REQUIRED"
        RedditConnectionState.Connecting -> "CONNECTING"
        RedditConnectionState.Error -> "REDDIT UNAVAILABLE"
        RedditConnectionState.Connected -> return
    }
    val description = when (connectionState) {
        RedditConnectionState.Unconfigured -> "Add your API settings to continue"
        RedditConnectionState.SignedOut -> "Connect your account to load the feed"
        RedditConnectionState.Connecting -> "Loading your Reddit feed"
        RedditConnectionState.Error -> "Nothing loaded · check your connection and API settings"
        RedditConnectionState.Connected -> return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = .09f))
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(colors.accent))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = .7.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = description,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyFeed(title: String, body: String) {
    val colors = MaterialTheme.otterColors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.NotificationsNone,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(title, color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text(body, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}
