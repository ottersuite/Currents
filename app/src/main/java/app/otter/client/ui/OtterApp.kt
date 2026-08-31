package app.otter.client.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.otter.client.model.Comment
import app.otter.client.model.RedditAccountState
import app.otter.client.ui.components.OtterSideMenu
import app.otter.client.ui.components.PostComposerSheet
import app.otter.client.ui.components.ReplyComposerSheet
import app.otter.client.ui.screens.AboutScreen
import app.otter.client.ui.screens.FeedScreen
import app.otter.client.ui.screens.AdvancedSettingsScreen
import app.otter.client.ui.screens.MediaViewerScreen
import app.otter.client.ui.screens.PostScreen
import app.otter.client.ui.screens.SearchScreen
import app.otter.client.ui.screens.SettingsScreen
import app.otter.client.ui.theme.OtterTheme
import app.otter.client.ui.theme.otterColors
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow

private data class ReplyTarget(val id: String?, val author: String?)

/**
 * Shows queued messages, in a composable of its own.
 *
 * Nearly every action publishes one — a vote, a save, a failed refresh — so collecting the
 * message alongside the rest of the app's state made the whole content scope recompose each
 * time a snackbar appeared and again when it cleared. Here the invalidation stops at this
 * function, which emits nothing.
 */
@Composable
private fun MessageSnackbarEffect(
    viewModel: OtterViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
}

@Composable
fun OtterApp(
    viewModel: OtterViewModel = viewModel(),
    onLaunchRedditAuthorization: ((String, String) -> Unit)? = null,
) {
    val systemDark = isSystemInDarkTheme()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val view = LocalView.current
    SideEffect {
        val window = context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    OtterTheme(darkTheme = darkTheme) {
        OtterAppContent(
            viewModel = viewModel,
            settings = settings,
            onLaunchRedditAuthorization = onLaunchRedditAuthorization,
        )
    }
}

@Composable
private fun OtterAppContent(
    viewModel: OtterViewModel,
    settings: OtterSettings,
    onLaunchRedditAuthorization: ((String, String) -> Unit)?,
) {
    val colors = MaterialTheme.otterColors
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val mediaViewer by viewModel.mediaViewer.collectAsStateWithLifecycle()
    val canReturnToPreviousFeed by viewModel.canReturnToPreviousFeed.collectAsStateWithLifecycle()
    val feedScrollTarget by viewModel.feedScrollTarget.collectAsStateWithLifecycle()
    val searchDraft by viewModel.searchDraft.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.searchCommunitySuggestions.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val communities by viewModel.communities.collectAsStateWithLifecycle()
    val selectedPostId by viewModel.selectedPostId.collectAsStateWithLifecycle()
    val selectedFeed by viewModel.selectedFeed.collectAsStateWithLifecycle()
    val feedSort by viewModel.feedSort.collectAsStateWithLifecycle()
    val feedTimeframe by viewModel.feedTimeframe.collectAsStateWithLifecycle()
    val commentSort by viewModel.commentSort.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchVisible by viewModel.searchVisible.collectAsStateWithLifecycle()
    val hiddenPostIds by viewModel.hiddenPostIds.collectAsStateWithLifecycle()
    val collapsedCommentIds by viewModel.collapsedCommentIds.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val commentsLoading by viewModel.commentsLoading.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()
    val redditApiConfiguration by viewModel.redditApiConfiguration.collectAsStateWithLifecycle()
    val composerVisible by viewModel.composerVisible.collectAsStateWithLifecycle()
    val postDraft by viewModel.postDraft.collectAsStateWithLifecycle()

    // Remembered rather than rescanned: this composable recomposes on any of the states above,
    // and the scan is over the whole loaded feed, which is every page fetched so far.
    val selectedPost = remember(posts, selectedPostId) {
        posts.firstOrNull { it.id == selectedPostId }
    }
    val emptyComments = remember { MutableStateFlow(emptyList<Comment>()) }
    val commentsFlow = remember(selectedPostId) {
        selectedPostId?.let(viewModel::comments) ?: emptyComments
    }
    val comments by commentsFlow.collectAsStateWithLifecycle()
    val selectedCommunity = remember(selectedFeed, communities, posts) {
        selectedFeed
            .takeIf { it.startsWith("r/", ignoreCase = true) }
            ?.drop(2)
            ?.let { name ->
                communities.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: posts.firstOrNull {
                        it.community.name.equals(name, ignoreCase = true)
                    }?.community
            }
    }

    var drawerVisible by rememberSaveable { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf<ReplyTarget?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val feedListState = rememberLazyListState()
    val connectRedditAccount: () -> Unit = {
        viewModel.beginRedditSignIn()?.let { request ->
            if (onLaunchRedditAuthorization == null) {
                viewModel.cancelRedditSignIn("Secure Reddit sign-in is unavailable in this view")
            } else {
                onLaunchRedditAuthorization(
                    request.authorizationUrl,
                    request.redirectUri,
                )
            }
        }
    }

    MessageSnackbarEffect(viewModel, snackbarHostState)

    BackHandler(
        enabled = (screen != AppScreen.Feed || canReturnToPreviousFeed) && !drawerVisible,
    ) {
        viewModel.navigateBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.canvas)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val useTwoPanes = maxWidth >= 840.dp

            when (screen) {
                AppScreen.Feed -> FeedScreen(
                    posts = posts,
                    selectedFeed = selectedFeed,
                    sort = feedSort,
                    timeframe = feedTimeframe,
                    searchVisible = searchVisible,
                    searchQuery = searchQuery,
                    refreshing = refreshing,
                    loadingMore = loadingMore,
                    connectionState = connectionState,
                    // Highlighting the open post only means something beside a reader pane;
                    // on a phone it lingers as a tint over whatever you just came back from.
                    selectedPostId = null,
                    postsAreServerSorted = connectionState == RedditConnectionState.Connected,
                    hiddenPostIds = hiddenPostIds,
                    settings = settings,
                    community = selectedCommunity,
                    onToggleCommunitySubscription = viewModel::toggleCommunitySubscription,
                    onOpenDrawer = { drawerVisible = true },
                    onOpenSearch = viewModel::openSearch,
                    onToggleSearch = viewModel::toggleSearch,
                    onSearchQueryChange = viewModel::updateSearch,
                    onSubmitSearch = { query ->
                        viewModel.searchPosts(query, viewModel.currentCommunity)
                    },
                    onSortChange = viewModel::setFeedSort,
                    onTimeframeChange = viewModel::setFeedTimeframe,
                    onRefresh = viewModel::refresh,
                    onLoadMore = viewModel::loadMorePosts,
                    onCompose = viewModel::showComposer,
                    onShowSaved = { viewModel.selectFeed("Saved") },
                    onMarkRead = viewModel::markPostsRead,
                    onOpenPost = viewModel::openPost,
                    onOpenMedia = { viewModel.openMedia(it) },
                    scrollTarget = feedScrollTarget,
                    onScrollChanged = viewModel::reportFeedScroll,
                    onScrollTargetConsumed = viewModel::consumeFeedScrollTarget,
                    onVote = viewModel::togglePostVote,
                    onSave = viewModel::toggleSaved,
                    onHide = viewModel::hidePost,
                    listState = feedListState,
                )

                AppScreen.Post -> if (selectedPost != null) {
                    if (useTwoPanes) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(.42f)) {
                                FeedScreen(
                                    posts = posts,
                                    selectedFeed = selectedFeed,
                                    sort = feedSort,
                                    timeframe = feedTimeframe,
                                    searchVisible = searchVisible,
                                    searchQuery = searchQuery,
                                    refreshing = refreshing,
                                    loadingMore = loadingMore,
                                    connectionState = connectionState,
                                    selectedPostId = selectedPostId,
                                    postsAreServerSorted = connectionState == RedditConnectionState.Connected,
                                    hiddenPostIds = hiddenPostIds,
                                    settings = settings,
                                    community = selectedCommunity,
                                    onToggleCommunitySubscription = viewModel::toggleCommunitySubscription,
                                    onOpenDrawer = { drawerVisible = true },
                    onOpenSearch = viewModel::openSearch,
                                    onToggleSearch = viewModel::toggleSearch,
                                    onSearchQueryChange = viewModel::updateSearch,
                                    onSubmitSearch = { query ->
                                        viewModel.searchPosts(query, viewModel.currentCommunity)
                                    },
                                    onSortChange = viewModel::setFeedSort,
                                    onTimeframeChange = viewModel::setFeedTimeframe,
                                    onRefresh = viewModel::refresh,
                                    onLoadMore = viewModel::loadMorePosts,
                                    onCompose = viewModel::showComposer,
                                    onShowSaved = { viewModel.selectFeed("Saved") },
                                    onMarkRead = viewModel::markPostsRead,
                                    onOpenPost = viewModel::openPost,
                                    onOpenMedia = { viewModel.openMedia(it) },
                                    scrollTarget = feedScrollTarget,
                                    onScrollChanged = viewModel::reportFeedScroll,
                                    onScrollTargetConsumed = viewModel::consumeFeedScrollTarget,
                                    onVote = viewModel::togglePostVote,
                                    onSave = viewModel::toggleSaved,
                                    onHide = viewModel::hidePost,
                                    listState = feedListState,
                                )
                            }
                            VerticalDivider(
                                color = colors.divider,
                                modifier = Modifier.width(1.dp),
                            )
                            Box(Modifier.weight(.58f)) {
                                PostPane(
                                    onOpenDrawer = { drawerVisible = true },
                                    post = selectedPost,
                                    comments = comments,
                                    viewModel = viewModel,
                                    settings = settings,
                                    commentSort = commentSort,
                                    collapsedCommentIds = collapsedCommentIds,
                                    commentsLoading = commentsLoading,
                                    commentsAreServerSorted = connectionState == RedditConnectionState.Connected,
                                    onReply = { replyTarget = it },
                                    currentUsername = (accountState as? RedditAccountState.SignedIn)?.account?.username,
                                    postIsHidden = selectedPost.id in hiddenPostIds,
                                )
                            }
                        }
                    } else {
                        PostPane(
                            post = selectedPost,
                            comments = comments,
                            viewModel = viewModel,
                            settings = settings,
                            commentSort = commentSort,
                            collapsedCommentIds = collapsedCommentIds,
                            commentsLoading = commentsLoading,
                            commentsAreServerSorted = connectionState == RedditConnectionState.Connected,
                            onReply = { replyTarget = it },
                            currentUsername = (accountState as? RedditAccountState.SignedIn)?.account?.username,
                            postIsHidden = selectedPost.id in hiddenPostIds,
                            onOpenDrawer = { drawerVisible = true },
                        )
                    }
                } else {
                    LaunchedEffect(Unit) { viewModel.navigateBack() }
                }

                AppScreen.Settings -> SettingsScreen(
                    settings = settings,
                    connectionState = connectionState,
                    accountState = accountState,
                    onBack = { viewModel.navigateBack() },
                    onOpenAdvanced = viewModel::openAdvancedSettings,
                    onThemeModeChange = viewModel::updateThemeMode,
                    onPresentationChange = viewModel::updateFeedPresentation,
                    onTextScaleChange = viewModel::updateTextScale,
                    onToggleThumbnailSide = viewModel::toggleThumbnailsSide,
                    onToggleSwipeActions = viewModel::toggleSwipeActions,
                    onToggleHaptics = viewModel::toggleHaptics,
                    onToggleDimRead = viewModel::toggleDimReadPosts,
                    onToggleFlairs = viewModel::togglePostFlairs,
                    onToggleAlwaysShowNsfw = viewModel::toggleAlwaysShowNsfw,
                    onUpdateFeedActions = viewModel::updateFeedActions,
                    onUpdatePostSwipeActions = viewModel::updatePostSwipeActions,
                    onUpdateCommentSwipeActions = viewModel::updateCommentSwipeActions,
                    onToggleHideRead = viewModel::toggleHideReadPosts,
                    onUpdateFilters = viewModel::updateContentFilters,
                    onClearReadHistory = viewModel::clearReadHistory,
                    onReset = viewModel::resetSettings,
                    onMessage = viewModel::notify,
                )

                AppScreen.AdvancedSettings -> AdvancedSettingsScreen(
                    settings = settings,
                    connectionState = connectionState,
                    accountState = accountState,
                    redditApiConfiguration = redditApiConfiguration,
                    onBack = { viewModel.navigateBack() },
                    onConnectAccount = connectRedditAccount,
                    onDisconnectAccount = viewModel::disconnectRedditAccount,
                    onSaveRedditApiConfiguration = viewModel::saveRedditApiConfiguration,
                    onResetRedditApiConfiguration = viewModel::resetRedditApiConfiguration,
                    onToggleWebViewSignIn = viewModel::toggleWebViewSignIn,
                    onMessage = viewModel::notify,
                )

                AppScreen.Search -> SearchScreen(
                    query = searchDraft,
                    suggestions = searchSuggestions,
                    // Search opens over whatever feed was being read, so that feed is still the
                    // one selected and is what the search should be able to narrow to.
                    community = selectedFeed
                        .takeIf { it.startsWith("r/", ignoreCase = true) }
                        ?.drop(2)
                        ?.takeIf(String::isNotEmpty),
                    onQueryChange = viewModel::updateSearchDraft,
                    onSearchPosts = viewModel::searchPosts,
                    onOpenCommunity = { name -> viewModel.selectFeed("r/$name") },
                    onToggleSubscription = viewModel::toggleCommunitySubscription,
                    onOpenUser = viewModel::openUserPosts,
                    onBack = { viewModel.navigateBack() },
                )

                AppScreen.About -> AboutScreen(onBack = { viewModel.navigateBack() })
            }
        }

        if (!drawerVisible && (screen == AppScreen.Feed || screen == AppScreen.Post)) {
            MenuEdgeSwipe(
                onOpen = { drawerVisible = true },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        OtterSideMenu(
            visible = drawerVisible,
            selectedFeed = selectedFeed,
            communities = communities,
            connectionState = connectionState,
            accountState = accountState,
            onDismiss = { drawerVisible = false },
            onSelectFeed = viewModel::selectFeed,
            onOpenSearch = viewModel::openSearch,
            onRandomNsfw = viewModel::openRandomNsfw,
            onAccountClick = {
                if (accountState == RedditAccountState.SignedOut) {
                    connectRedditAccount()
                } else {
                    viewModel.openSettings()
                }
            },
            onOpenSettings = viewModel::openSettings,
            onOpenAbout = viewModel::openAbout,
        )
    }

    if (composerVisible) {
        PostComposerSheet(
            communities = communities,
            initialDraft = postDraft,
            onDraftChange = viewModel::updatePostDraft,
            onDismiss = viewModel::hideComposer,
            onSubmit = viewModel::submitPost,
        )
    }

    // Above every sheet and bar: full-screen media owns the screen while it is open.
    mediaViewer?.let { request ->
        MediaViewerScreen(
            request = request,
            onClose = viewModel::closeMedia,
            hapticsEnabled = settings.haptics,
            onSaveMedia = viewModel::saveMedia,
            onSaveDenied = { viewModel.notify("Currents needs storage access to save media") },
        )
    }

    // Last of all, and in a layer of its own: the media viewer is a sibling of the content Box
    // rather than a child of it, so a snackbar inside that Box was drawn *under* the viewer's
    // backdrop. Saving media from the viewer worked and looked like it had done nothing.
    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 84.dp),
        ) { data ->
            Surface(
                color = colors.surfaceGlass,
                contentColor = colors.textPrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp),
                shadowElevation = 10.dp,
            ) {
                Text(
                    data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }

    replyTarget?.let { target ->
        ReplyComposerSheet(
            replyingTo = target.author,
            onDismiss = { replyTarget = null },
            onSubmit = { body ->
                selectedPostId?.let { postId ->
                    viewModel.submitComment(postId, target.id, body)
                    replyTarget = null
                }
            },
        )
    }
}

@Composable
private fun PostPane(
    post: app.otter.client.model.Post,
    comments: List<Comment>,
    viewModel: OtterViewModel,
    settings: OtterSettings,
    commentSort: CommentSort,
    collapsedCommentIds: Set<String>,
    commentsLoading: Boolean,
    commentsAreServerSorted: Boolean,
    onReply: (ReplyTarget) -> Unit,
    currentUsername: String?,
    postIsHidden: Boolean,
    onOpenDrawer: () -> Unit,
) {
    val expandingComments by viewModel.expandingComments.collectAsStateWithLifecycle()
    key(post.id) {
        PostScreen(
            post = post,
            comments = comments,
            commentsLoading = commentsLoading,
            commentsAreServerSorted = commentsAreServerSorted,
            commentSort = commentSort,
            collapsedCommentIds = collapsedCommentIds,
            settings = settings,
            onBack = { viewModel.navigateBack() },
            onSortChange = viewModel::setCommentSort,
            onPostVote = { viewModel.togglePostVote(post.id, it) },
            onSavePost = { viewModel.toggleSaved(post.id) },
            onOpenCommunity = { name -> viewModel.selectFeed("r/$name") },
            onOpenUser = viewModel::openUserPosts,
            currentUsername = currentUsername,
            postIsHidden = postIsHidden,
            onHidePost = { viewModel.hidePost(post.id) },
            onReportPost = { reason -> viewModel.reportPost(post.id, reason) },
            onBlockUser = viewModel::blockUser,
            onEditPost = { body -> viewModel.editPost(post.id, body) },
            onDeletePost = { viewModel.deletePost(post.id) },
            onCommentVote = { commentId, vote -> viewModel.toggleCommentVote(post.id, commentId, vote) },
            onToggleCommentCollapsed = viewModel::toggleCommentCollapsed,
            onReply = { commentId ->
                val author = comments.firstOrNull { it.id == commentId }?.author
                onReply(ReplyTarget(commentId, author))
            },
            onReportComment = viewModel::reportComment,
            onEditComment = { commentId, body -> viewModel.editComment(post.id, commentId, body) },
            onDeleteComment = { commentId -> viewModel.deleteComment(post.id, commentId) },
            onOpenMedia = { index -> viewModel.openMedia(post, index) },
            onOpenCommentMedia = { url -> viewModel.openMediaLink(url, post.title) },
            onReloadComments = viewModel::reloadComments,
            onOpenDrawer = onOpenDrawer,
            expandingComments = expandingComments,
            onLoadMoreComments = viewModel::loadMoreComments,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The strip along the right edge that pulls the menu open.
 *
 * On a phone using gesture navigation this edge belongs to the system back gesture, and an app
 * that simply listens here never sees the swipe at all. [View.setSystemGestureExclusionRects]
 * asks the system to stand down over this strip. The platform caps that exclusion at 200dp per
 * edge, so the band is centred and explicitly sized rather than running the full height: a swipe
 * that starts near the very top or bottom of the screen is still the system's back gesture.
 */
@Composable
private fun MenuEdgeSwipe(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .height(EDGE_GESTURE_BAND)
            .width(EDGE_GESTURE_WIDTH)
            // Compose owns the view's exclusion rect list, so assigning it directly on the View
            // gets overwritten and the system keeps the swipe. This modifier registers through
            // Compose instead, which is why the back gesture was still firing alongside ours.
            .systemGestureExclusion()
            .pointerInput(Unit) {
                val openThreshold = with(density) { EDGE_OPEN_DISTANCE.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalX = 0f
                    var totalY = 0f
                    var opened = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        totalX += delta.x
                        totalY += delta.y
                        // Leftward and mostly horizontal, or it belongs to whatever is underneath.
                        if (totalX < 0f && abs(totalX) > abs(totalY)) change.consume()
                        if (!opened && totalX <= -openThreshold && abs(totalX) > abs(totalY)) {
                            opened = true
                            onOpen()
                        }
                    }
                }
            },
    )
}

private val EDGE_GESTURE_WIDTH = 28.dp
private val EDGE_GESTURE_BAND = 200.dp
private val EDGE_OPEN_DISTANCE = 40.dp
