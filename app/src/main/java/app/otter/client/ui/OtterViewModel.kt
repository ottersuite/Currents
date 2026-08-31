package app.otter.client.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import app.otter.client.BuildConfig
import app.otter.client.data.InMemoryRedditRepository
import app.otter.client.data.CachedComments
import app.otter.client.data.CachedFeed
import app.otter.client.data.MediaCache
import app.otter.client.data.OfflineCacheStore
import app.otter.client.data.OtterPreferences
import app.otter.client.data.ReadPostStore
import app.otter.client.data.MediaSaveResult
import app.otter.client.data.MediaSaver
import app.otter.client.data.RedGifsClient
import app.otter.client.data.RedditVideoLinks
import app.otter.client.data.RedditApiRepository
import app.otter.client.data.RedditRepository
import app.otter.client.data.normalizeSubmissionUrl
import app.otter.client.data.oauth.AndroidRedditApiConfigurationStore
import app.otter.client.data.oauth.AndroidRedditOAuthStore
import app.otter.client.data.oauth.RedditApiConfiguration
import app.otter.client.data.oauth.RedditOAuthManager
import app.otter.client.model.Comment
import app.otter.client.model.Community
import app.otter.client.model.MediaAsset
import app.otter.client.model.MediaKind
import app.otter.client.model.Post
import app.otter.client.ui.screens.MediaViewerRequest
import app.otter.client.model.RedditAccountState
import app.otter.client.model.SubmissionKind
import app.otter.client.model.VoteState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.otter.client.ui.components.SwipeAction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

enum class AppScreen {
    Feed,
    Search,
    Post,
    Settings,
    AdvancedSettings,
    About,
}

enum class ThemeMode {
    System,
    Light,
    Dark,
}

enum class FeedPresentation {
    Compact,
    LargePreview,
}

enum class FeedSort(val label: String) {
    Best("Best"),
    Hot("Hot"),
    New("New"),
    Top("Top"),
    Rising("Rising"),
}

/** The window a Top listing covers. Reddit calls this `t` and only honours it for Top. */
enum class FeedTimeframe(val label: String, val redditParam: String) {
    Today("Today", "day"),
    ThisWeek("This week", "week"),
    ThisMonth("This month", "month"),
    ThisYear("This year", "year"),
    AllTime("All time", "all"),
}

enum class CommentSort(val label: String) {
    Best("Best"),
    Top("Top"),
    New("New"),
    Controversial("Controversial"),
}

enum class RedditConnectionState {
    Unconfigured,
    SignedOut,
    Connecting,
    Connected,
    Error,
}

data class OtterSettings(
    val themeMode: ThemeMode = ThemeMode.Dark,
    val feedPresentation: FeedPresentation = FeedPresentation.Compact,
    val textScale: Float = 1f,
    val thumbnailsOnRight: Boolean = true,
    val swipeActions: Boolean = true,
    val haptics: Boolean = true,
    val dimReadPosts: Boolean = true,
    val showPostFlairs: Boolean = true,
    /** Skip the NSFW cover on media. Spoilers stay covered; they hide plot, not skin. */
    val alwaysShowNsfw: Boolean = false,
    /** Opt-in: sign in inside Otter instead of an Auth Tab. See [WEB_VIEW_SIGN_IN_RATIONALE]. */
    val webViewSignIn: Boolean = false,
    val feedActions: List<FeedAction> = listOf(
        FeedAction.Search,
        FeedAction.Compose,
        FeedAction.Menu,
    ),
    val postSwipeActions: SwipeActionConfig = SwipeActionConfig(),
    val commentSwipeActions: SwipeActionConfig = DefaultCommentSwipeActions,
    val hideReadPosts: Boolean = false,
    val blockedKeywords: Set<String> = emptySet(),
    val blockedCommunities: Set<String> = emptySet(),
    val blockedAuthors: Set<String> = emptySet(),
)

/** Shown when the in-app flow is switched on, since it trades away the Auth Tab's isolation. */
const val WEB_VIEW_SIGN_IN_RATIONALE: String =
    "Reddit's login page will now open inside Currents. Only use this for a client ID whose " +
        "redirect URI no browser can hand back to this app."


/** Where a feed was scrolled to, so returning to it does not start at the top. */
data class FeedScroll(val index: Int = 0, val offset: Int = 0)

private data class FeedSnapshot(
    val posts: List<Post>,
    val scroll: FeedScroll,
    val sort: FeedSort,
    val timeframe: FeedTimeframe,
)

data class RedditAuthorizationRequest(
    val authorizationUrl: String,
    val redirectUri: String,
)

data class PostDraft(
    val title: String = "",
    val body: String = "",
    val community: String = "",
    val kind: SubmissionKind = SubmissionKind.TEXT,
    /** Only carried by a [SubmissionKind.LINK] draft; kept across kind switches so toggling back and forth does not discard what was typed. */
    val linkUrl: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@androidx.annotation.OptIn(UnstableApi::class)
class OtterViewModel @JvmOverloads constructor(
    application: Application,
    repositoryOverride: RedditRepository? = null,
) : AndroidViewModel(application) {
    // This persisted identifier predates the Otter name; changing it would discard user settings.
    private val preferences =
        application.getSharedPreferences(OtterPreferences.SETTINGS, Context.MODE_PRIVATE)
    private val usesRepositoryOverride = repositoryOverride != null
    private val apiConfigurationStore = AndroidRedditApiConfigurationStore(application)
    private val _redditApiConfiguration = MutableStateFlow(apiConfigurationStore.load())
    val redditApiConfiguration = _redditApiConfiguration.asStateFlow()

    private val repositoryState = MutableStateFlow(
        repositoryOverride ?: createRepository(_redditApiConfiguration.value),
    )
    private val repository: RedditRepository
        get() = repositoryState.value

    private val redGifs = RedGifsClient()
    private var mediaViewerGeneration = 0
    private val readPostStore = ReadPostStore(application)
    private val offlineCache = OfflineCacheStore(application)
    private val _readPostIds = MutableStateFlow(readPostStore.ids())

    val posts: StateFlow<List<Post>> = repositoryState
        .flatMapLatest { activeRepository -> activeRepository.feed }
        // Read state is remembered locally: Reddit does not report what this client has opened.
        .combine(_readPostIds) { feed, readIds ->
            if (readIds.isEmpty()) {
                feed
            } else {
                feed.map { post ->
                    if (!post.isRead && post.id in readIds) post.copy(isRead = true) else post
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.feed.value)
    val communities = repositoryState
        .flatMapLatest { activeRepository -> activeRepository.communities }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.communities.value)
    val accountState = repositoryState
        .flatMapLatest { activeRepository -> activeRepository.accountState }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.accountState.value)
    /**
     * Comment flows per post, capped and evicted least-recently-used.
     *
     * Each entry owns a child scope because `stateIn` keeps a coroutine alive for whatever scope
     * it was given: flows launched straight into [viewModelScope] would survive every eviction
     * and accumulate — along with the comment lists they hold — for the life of the process.
     */
    private val commentFlows = object : LinkedHashMap<String, CachedCommentFlow>(
        COMMENT_FLOW_CACHE_LIMIT,
        DEFAULT_LOAD_FACTOR,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, CachedCommentFlow>,
        ): Boolean {
            if (size <= COMMENT_FLOW_CACHE_LIMIT) return false
            eldest.value.scope.cancel()
            return true
        }
    }
    private val apiConfigurationMutex = Mutex()
    private val _apiConfigurationChanging = MutableStateFlow(false)

    private val _screen = MutableStateFlow(AppScreen.Feed)
    val screen = _screen.asStateFlow()

    private val _selectedPostId = MutableStateFlow<String?>(null)
    val selectedPostId = _selectedPostId.asStateFlow()

    private val _selectedFeed = MutableStateFlow("Home")
    val selectedFeed = _selectedFeed.asStateFlow()

    private val _feedSort = MutableStateFlow(FeedSort.Best)
    val feedSort = _feedSort.asStateFlow()

    private val _commentSort = MutableStateFlow(CommentSort.Best)
    val commentSort = _commentSort.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchVisible = MutableStateFlow(false)
    val searchVisible = _searchVisible.asStateFlow()

    /** Feeds already loaded this session, so returning to one does not refetch it. */
    private val feedCache = LinkedHashMap<String, FeedSnapshot>()
    private var currentScroll = FeedScroll()
    private val _feedScrollTarget = MutableStateFlow<FeedScroll?>(null)

    /** Set when a restored feed should be put back at the offset it was left at. */
    val feedScrollTarget = _feedScrollTarget.asStateFlow()
    private val feedHistory = FeedHistory()
    private val _canReturnToPreviousFeed = MutableStateFlow(false)

    /** True while back at the feed should return to an earlier feed instead of leaving. */
    val canReturnToPreviousFeed = _canReturnToPreviousFeed.asStateFlow()
    private val _searchDraft = MutableStateFlow("")

    /** What the search screen currently has typed into it. */
    val searchDraft = _searchDraft.asStateFlow()
    private val _mediaViewer = MutableStateFlow<MediaViewerRequest?>(null)

    /** Non-null while full-screen media is open, over whatever screen is showing. */
    val mediaViewer = _mediaViewer.asStateFlow()
    private val _settings = MutableStateFlow(loadSettings())
    val settings = _settings.asStateFlow()

    private val _hiddenPostIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenPostIds = _hiddenPostIds.asStateFlow()

    private val _collapsedCommentIds = MutableStateFlow<Set<String>>(emptySet())
    val collapsedCommentIds = _collapsedCommentIds.asStateFlow()

    private val _feedTimeframe = MutableStateFlow(loadFeedTimeframe())

    /** Only meaningful while [feedSort] is Top; kept across sort changes so it is not forgotten. */
    val feedTimeframe = _feedTimeframe.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)

    /** True while another page is on its way in behind the current one. */
    val loadingMore = _loadingMore.asStateFlow()
    private var feedHasMorePages = true

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading = _commentsLoading.asStateFlow()

    private val _connectionState = MutableStateFlow(
        when {
            !repository.isLive -> RedditConnectionState.Unconfigured
            repository.accountState.value is RedditAccountState.SignedIn -> RedditConnectionState.Connecting
            else -> RedditConnectionState.SignedOut
        },
    )
    val connectionState = _connectionState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _composerVisible = MutableStateFlow(false)
    val composerVisible = _composerVisible.asStateFlow()
    private val _postDraft = MutableStateFlow(PostDraft())
    val postDraft = _postDraft.asStateFlow()

    /** Restores the random-community pool from disk; the random button waits on it. */
    private var adultPoolSeedJob: Job? = null
    private var feedJob: Job? = null
    private var commentJob: Job? = null
    private var feedRequestGeneration = 0
    private var commentRequestGeneration = 0

    init {
        if (!usesRepositoryOverride) {
            adultPoolSeedJob = viewModelScope.launch {
                val stored = withContext(Dispatchers.IO) { offlineCache.storedValue(ADULT_POOL_KEY) }
                    ?: return@launch
                val names = decodeCommunityNames(stored.payload)
                if (names.isNotEmpty()) {
                    repository.seedRandomCommunityPool(names, stored.updatedAtMillis)
                }
            }
        }

        // The saved draft is only needed once the composer opens, so it gets its own coroutine.
        // Read in sequence it sat in front of the cached feed, which is the thing the user is
        // actually waiting to see.
        if (!usesRepositoryOverride) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { offlineCache.draft(POST_DRAFT_KEY) }
                    ?.let(::decodePostDraft)
                    ?.let { _postDraft.value = it }
            }
        }
        viewModelScope.launch {
            // Started before the disk read rather than after it. The first Reddit request cannot
            // go out until an access token exists, and obtaining one can cost a round trip, so
            // that wait overlaps reading the cache instead of queueing behind it.
            if (repository.isLive && repository.accountState.value is RedditAccountState.SignedIn) {
                launch { repository.warmAccountSession() }
            }
            // One request now turns the session's first RedGifs lookup from two round trips
            // into one, and that first one is the wait that gets noticed.
            if (!usesRepositoryOverride) launch { redGifs.warmUp() }
            if (!usesRepositoryOverride && repository.feed.value.isEmpty()) {
                val cached = withContext(Dispatchers.IO) { offlineCache.feed("Home") }
                if (cached != null && _selectedFeed.value == "Home") {
                    restoreCachedFeed("Home", cached, resuming = true)
                }
            }
            if (repository.isLive && repository.accountState.value is RedditAccountState.SignedIn) {
                refreshInternal(showSuccessMessage = false)
            }
        }
        if (repository.isLive) {
            // A previous sign-out may have been unable to reach Reddit. Retry quietly: the
            // account is already gone locally, so there is nothing to report either way.
            viewModelScope.launch { repository.retryPendingAccountRevocations() }
        }
    }

    /**
     * Opens a random adult community. The choice is made before navigating, so the feed carries
     * the real community's name from the first frame.
     */
    fun openRandomNsfw() {
        if (repository.accountState.value !is RedditAccountState.SignedIn) {
            _message.value = "Connect your Reddit account to browse random communities"
            return
        }
        viewModelScope.launch {
            // Without this the first tap of a launch could start harvesting before the stored
            // pool had finished loading, and pay for a search it already had the answer to.
            adultPoolSeedJob?.join()
            _refreshing.value = true
            val chosen = repository.randomNsfwCommunity()
            chosen
                .onSuccess { community ->
                    selectFeed("r/$community")
                    persistAdultPool()
                }
                .onFailure { error ->
                    _refreshing.value = false
                    _message.value = error.message ?: "Could not find a random community"
                }
        }
    }

    fun selectFeed(feed: String) {
        cacheCurrentFeed()
        if (feedHistory.record(previous = _selectedFeed.value, next = feed)) {
            _canReturnToPreviousFeed.value = true
        }
        // Choosing a feed is a request for what is there now, so it always reloads.
        showFeed(feed, restoreCached = false)
    }

    /** Switches feeds without recording the move, so returning cannot loop back on itself. */
    private fun showFeed(feed: String, restoreCached: Boolean) {
        feedHasMorePages = true
        if (!restoreCached) {
            // Every feed opens the way Reddit presents it by default; a sort chosen for one
            // community should not silently follow you into the next.
            _feedSort.value = FeedSort.Best
        }
        _selectedFeed.value = feed
        _screen.value = AppScreen.Feed
        _searchQuery.value = ""

        val cached = if (restoreCached) feedCache[feed]?.takeIf { it.posts.isNotEmpty() } else null
        if (cached != null) {
            // Returning to where you were should look like returning, not like starting over:
            // cancel any in-flight load, put the posts back, and land on the same row.
            feedJob?.cancel()
            feedRequestGeneration++
            _refreshing.value = false
            repository.restoreFeed(cached.posts)
            // The chip has to describe the posts being shown, so it comes back with them.
            _feedSort.value = cached.sort
            _feedTimeframe.value = cached.timeframe
            currentScroll = cached.scroll
            _feedScrollTarget.value = cached.scroll
            return
        }
        currentScroll = FeedScroll()
        // A new feed explicitly starts at the top. Keeping this as a real target (instead of
        // using null to mean "top") lets the UI distinguish a feed change from returning to the
        // same feed after reading a post, when its existing LazyListState must be left alone.
        _feedScrollTarget.value = currentScroll
        if (repository.accountState.value is RedditAccountState.SignedIn) {
            if (!usesRepositoryOverride) {
                viewModelScope.launch {
                    val diskCached = withContext(Dispatchers.IO) { offlineCache.feed(feed) }
                    if (_selectedFeed.value == feed && diskCached != null) {
                        restoreCachedFeed(feed, diskCached, resuming = restoreCached)
                    }
                    if (_selectedFeed.value == feed) refreshInternal(showSuccessMessage = false)
                }
            } else {
                refreshInternal(showSuccessMessage = false)
            }
        }
    }

    private fun cacheCurrentFeed() {
        val current = _selectedFeed.value
        val loaded = posts.value
        if (current.isBlank() || loaded.isEmpty()) return
        feedCache.remove(current)
        feedCache[current] = FeedSnapshot(
            posts = loaded,
            scroll = currentScroll,
            sort = _feedSort.value,
            timeframe = _feedTimeframe.value,
        )
        while (feedCache.size > FEED_CACHE_LIMIT) {
            feedCache.remove(feedCache.keys.first())
        }
        if (!usesRepositoryOverride) {
            val snapshot = feedCache.getValue(current)
            // The in-memory snapshot above keeps every loaded page so returning to a feed in
            // this session is complete. What goes to disk is capped: this runs again after every
            // page, and by page five an uncapped write was re-serializing hundreds of posts on
            // each one. The cache only has to cover what a cold start shows before the network
            // answers, which is a screenful, not the whole scrollback.
            val persisted = snapshot.posts.take(MAX_PERSISTED_POSTS)
            viewModelScope.launch(Dispatchers.IO) {
                offlineCache.putFeed(
                    current,
                    CachedFeed(
                        posts = persisted,
                        scrollIndex = snapshot.scroll.index
                            .coerceAtMost((persisted.size - 1).coerceAtLeast(0)),
                        scrollOffset = snapshot.scroll.offset,
                        sort = snapshot.sort.name,
                        timeframe = snapshot.timeframe.name,
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /**
     * Publishes a feed read back from disk.
     *
     * This lands *after* [showFeed] has already positioned the feed, because the read is
     * asynchronous, so it has to know why the feed is being shown. [resuming] is true when the
     * user is returning to a feed — a cold start, or back out of one they navigated into — and
     * false when they have just picked this feed, which is a request to see what is at the top
     * of it right now. Restoring the old position and sort in that second case is what made a
     * freshly chosen community open part-way down, under whatever sort was last used there.
     */
    private fun restoreCachedFeed(key: String, cached: CachedFeed, resuming: Boolean) {
        if (cached.posts.isEmpty()) return
        repository.restoreFeed(cached.posts)
        currentScroll = if (resuming) {
            // Returning should look like returning: the chip has to describe the posts being
            // put back, and the list has to land on the row it was left on.
            _feedSort.value = enumValueOr(cached.sort, FeedSort.Best)
            _feedTimeframe.value = enumValueOr(cached.timeframe, FeedTimeframe.Today)
            FeedScroll(cached.scrollIndex, cached.scrollOffset)
        } else {
            // A fresh choice keeps the defaults showFeed just set. These posts are only here so
            // the screen is not empty while the refresh already in flight arrives — and that
            // refresh reads _feedSort, so restoring the cached sort here would also silently
            // carry the last visit's sort into the new request.
            FeedScroll()
        }
        _feedScrollTarget.value = currentScroll
        feedCache[key] = FeedSnapshot(
            cached.posts,
            currentScroll,
            _feedSort.value,
            _feedTimeframe.value,
        )
    }

    /** The feed reports where it is scrolled so returning can put it back there. */
    fun reportFeedScroll(index: Int, offset: Int) {
        currentScroll = FeedScroll(index, offset)
    }

    fun consumeFeedScrollTarget() {
        _feedScrollTarget.value = null
    }

    fun setFeedTimeframe(timeframe: FeedTimeframe) {
        if (_feedTimeframe.value == timeframe) return
        _feedTimeframe.value = timeframe
        resetFeedScroll()
        preferences.edit { putString("feedTimeframe", timeframe.name) }
        if (repository.accountState.value is RedditAccountState.SignedIn) {
            refreshInternal(showSuccessMessage = false)
        }
    }

    fun setFeedSort(sort: FeedSort) {
        if (_feedSort.value == sort) return
        _feedSort.value = sort
        resetFeedScroll()
        if (repository.accountState.value is RedditAccountState.SignedIn) {
            refreshInternal(showSuccessMessage = false)
        }
    }

    private fun resetFeedScroll() {
        currentScroll = FeedScroll()
        _feedScrollTarget.value = currentScroll
    }

    /** Re-fetches the open post's comments; the pull gesture on the post screen calls this. */
    /**
     * Fetches the next page of the current feed.
     *
     * Called as the list nears its end, so it has to be safe to call repeatedly: an in-flight
     * page, an ongoing refresh, or a listing Reddit has already run out of all mean no-op.
     */
    fun loadMorePosts() {
        if (_loadingMore.value || _refreshing.value || !feedHasMorePages) return
        if (repository.accountState.value !is RedditAccountState.SignedIn) return

        viewModelScope.launch {
            _loadingMore.value = true
            repository.loadMoreFeed(
                feedName = _selectedFeed.value,
                sort = _feedSort.value.label.lowercase(),
                timeframe = _feedTimeframe.value.redditParam.takeIf {
                    _feedSort.value == FeedSort.Top
                },
            ).onSuccess { hasMore ->
                feedHasMorePages = hasMore
                cacheCurrentFeed()
            }.onFailure { error ->
                // Stop asking rather than retrying into the same failure on every scroll.
                feedHasMorePages = false
                _message.value = error.message ?: "Could not load more posts"
            }
            _loadingMore.value = false
        }
    }

    private val _expandingComments = MutableStateFlow<Set<String>>(emptySet())

    /** Placeholder rows currently fetching their replies. */
    val expandingComments = _expandingComments.asStateFlow()

    /** Expands one truncated branch of the open thread. */
    fun loadMoreComments(stubId: String) {
        val postId = _selectedPostId.value ?: return
        if (stubId in _expandingComments.value) return
        if (repository.accountState.value !is RedditAccountState.SignedIn) return

        viewModelScope.launch {
            _expandingComments.value = _expandingComments.value + stubId
            repository.loadMoreComments(
                postId = postId,
                stubId = stubId,
                sort = _commentSort.value.label.lowercase(),
            ).onFailure { error ->
                _message.value = error.message ?: "Could not load more comments"
            }
            _expandingComments.value = _expandingComments.value - stubId
        }
    }

    fun reloadComments() {
        val postId = _selectedPostId.value ?: return
        if (repository.accountState.value is RedditAccountState.SignedIn) {
            loadCommentsInternal(postId)
        }
    }

    fun setCommentSort(sort: CommentSort) {
        _commentSort.value = sort
        val postId = _selectedPostId.value ?: return
        if (repository.accountState.value is RedditAccountState.SignedIn) {
            loadCommentsInternal(postId)
        }
    }

    fun toggleSearch() {
        _searchVisible.value = !_searchVisible.value
        if (!_searchVisible.value) _searchQuery.value = ""
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun openPost(postId: String) {
        markPostRead(postId)
        // Opening a post is the best warning that its media is about to be tapped, so the
        // RedGifs lookup starts now rather than when the viewer is already on screen.
        prefetchRedGifs(repository.post(postId)?.destinationUrl)
        _selectedPostId.value = postId
        _collapsedCommentIds.value = emptySet()
        _screen.value = AppScreen.Post
        if (!usesRepositoryOverride && repository.comments(postId).value.isEmpty()) {
            viewModelScope.launch {
                val cached = withContext(Dispatchers.IO) { offlineCache.comments(postId) }
                if (_selectedPostId.value == postId && cached != null) {
                    repository.restoreComments(postId, cached.comments)
                }
            }
        }
        if (repository.accountState.value is RedditAccountState.SignedIn) {
            loadCommentsInternal(postId)
        }
    }

    fun openSettings() {
        _screen.value = AppScreen.Settings
    }

    fun openSearch() {
        _screen.value = AppScreen.Search
    }

    /** Everything the search screen is typing against. */
    fun updateSearchDraft(query: String) {
        _searchDraft.value = query
    }

    /** Site-wide post search, opened as a feed so sorting and refreshing work as usual. */
    /**
     * Opens a search as a feed, so it sorts, refreshes and pages like any other.
     *
     * With a [community] the search is restricted to it. Searching from inside a community is
     * almost always meant to be about that community — the whole of Reddit is a different
     * question, and one the search screen still offers separately.
     */
    fun searchPosts(query: String, community: String? = null) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val scope = community?.removePrefix("r/")?.trim()?.takeIf(String::isNotEmpty)
        selectFeed(if (scope == null) "q/$trimmed" else "qr/$scope/$trimmed")
    }

    /** The community currently being browsed, or null when the feed is not one. */
    val currentCommunity: String?
        get() = _selectedFeed.value
            .takeIf { it.startsWith("r/", ignoreCase = true) }
            ?.drop(2)
            ?.takeIf(String::isNotEmpty)

    fun openUserPosts(username: String) {
        val trimmed = username.trim().removePrefix("u/").removePrefix("/u/")
        if (trimmed.isEmpty()) return
        selectFeed("u/$trimmed")
    }

    fun openAdvancedSettings() {
        _screen.value = AppScreen.AdvancedSettings
    }

    fun openAbout() {
        _screen.value = AppScreen.About
    }

    fun navigateBack(): Boolean = when (_screen.value) {
        // At the top level, back retraces the feeds this session visited before it leaves.
        AppScreen.Feed -> returnToPreviousFeed()
        AppScreen.Search -> {
            _screen.value = AppScreen.Feed
            true
        }
        // Advanced is reached from Settings, so back returns there rather than to the feed.
        AppScreen.AdvancedSettings -> {
            _screen.value = AppScreen.Settings
            true
        }
        AppScreen.Post, AppScreen.Settings, AppScreen.About -> {
            _screen.value = AppScreen.Feed
            true
        }
    }

    private fun returnToPreviousFeed(): Boolean {
        val previous = feedHistory.back() ?: return false
        cacheCurrentFeed()
        _canReturnToPreviousFeed.value = feedHistory.canGoBack
        showFeed(previous, restoreCached = true)
        return true
    }

    fun comments(postId: String): StateFlow<List<Comment>> {
        // The `get` is what marks this post as recently used, so an open thread cannot be
        // evicted out from under its own screen.
        commentFlows[postId]?.let { cached -> return cached.flow }
        val scope = CoroutineScope(
            viewModelScope.coroutineContext +
                SupervisorJob(viewModelScope.coroutineContext[Job]),
        )
        val flow = repositoryState
            .flatMapLatest { activeRepository -> activeRepository.comments(postId) }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = repository.comments(postId).value,
            )
        commentFlows[postId] = CachedCommentFlow(scope, flow)
        return flow
    }

    private class CachedCommentFlow(
        val scope: CoroutineScope,
        val flow: StateFlow<List<Comment>>,
    )

    fun beginRedditSignIn(): RedditAuthorizationRequest? {
        if (_apiConfigurationChanging.value) {
            _message.value = "Wait for the Reddit API settings to finish updating"
            return null
        }
        val activeRepository = repository
        val activeConfiguration = _redditApiConfiguration.value
        // Fail before opening an Auth Tab that could only ever come back rejected.
        activeConfiguration.validationError()?.let { error ->
            _message.value = "Reddit sign-in needs valid API settings: $error"
            return null
        }
        return activeRepository.beginAccountAuthorization()
            .map { authorizationUrl ->
                RedditAuthorizationRequest(
                    authorizationUrl = authorizationUrl,
                    redirectUri = activeConfiguration.redirectUri,
                )
            }
            .onFailure { error ->
                _message.value = error.message ?: "Reddit sign-in could not start"
            }
            .getOrNull()
    }

    fun acceptsOAuthRedirect(callbackUrl: String): Boolean =
        _redditApiConfiguration.value.matchesCallback(callbackUrl)

    /** True once a usable client ID, User-Agent, and redirect URI are all configured. */
    val isRedditApiConfigured: Boolean
        get() = _redditApiConfiguration.value.isUsable

    /** The redirect URI currently sent to Reddit; surfaced when a callback is rejected. */
    val configuredRedirectUri: String
        get() = _redditApiConfiguration.value.normalized().redirectUri

    fun cancelRedditSignIn(message: String) {
        repository.cancelAccountAuthorization()
        _connectionState.value = if (repository.isLive) {
            RedditConnectionState.SignedOut
        } else {
            RedditConnectionState.Unconfigured
        }
        _message.value = message
    }

    fun handleOAuthRedirect(callbackUrl: String) {
        viewModelScope.launch {
            apiConfigurationMutex.withLock {
                repository.completeAccountAuthorization(callbackUrl)
                    .onSuccess { account ->
                        _message.value = "Connected as u/${account.username}"
                        refreshInternal(showSuccessMessage = false)
                    }
                    .onFailure { error ->
                        _message.value = error.message ?: "Reddit sign-in failed"
                    }
            }
        }
    }

    fun disconnectRedditAccount() {
        invalidateReadRequests()
        viewModelScope.launch {
            apiConfigurationMutex.withLock {
                val result = repository.disconnectAccount()
                _selectedFeed.value = "Home"
                _screen.value = AppScreen.Settings
                _message.value = if (result.isSuccess) {
                    "Reddit account disconnected"
                } else {
                    "Disconnected on this device; Reddit could not be reached to revoke access"
                }
                _connectionState.value = if (repository.isLive) {
                    RedditConnectionState.SignedOut
                } else {
                    RedditConnectionState.Unconfigured
                }
            }
        }
    }

    fun saveRedditApiConfiguration(
        clientId: String,
        userAgent: String,
        redirectUri: String,
    ): Boolean {
        val configuration = RedditApiConfiguration(clientId, userAgent, redirectUri).normalized()
        configuration.validationError()?.let { error ->
            _message.value = error
            return false
        }
        if (!canStartApiConfigurationUpdate()) return false
        val persisted = runCatching { apiConfigurationStore.save(configuration) }.getOrDefault(false)
        if (!persisted) {
            _message.value = "Reddit API settings could not be saved"
            return false
        }
        applyRedditApiConfiguration(configuration, resetToBuildDefaults = false)
        return true
    }

    fun resetRedditApiConfiguration(): Boolean {
        if (!canStartApiConfigurationUpdate()) return false
        if (!runCatching(apiConfigurationStore::reset).getOrDefault(false)) {
            _message.value = "Reddit API settings could not be cleared"
            return false
        }
        applyRedditApiConfiguration(
            configuration = apiConfigurationStore.emptyConfiguration(),
            resetToBuildDefaults = true,
        )
        return true
    }

    private fun canStartApiConfigurationUpdate(): Boolean {
        if (!_apiConfigurationChanging.value) return true
        _message.value = "Reddit API settings are already being updated"
        return false
    }

    private fun applyRedditApiConfiguration(
        configuration: RedditApiConfiguration,
        resetToBuildDefaults: Boolean,
    ) {
        val current = _redditApiConfiguration.value
        if (current == configuration) {
            _message.value = if (resetToBuildDefaults) {
                "Reddit API settings cleared"
            } else {
                "Reddit API settings are already active"
            }
            return
        }
        _apiConfigurationChanging.value = true
        viewModelScope.launch {
            try {
                apiConfigurationMutex.withLock {
                    invalidateReadRequests()

                    // This clears the local credential and outstanding OAuth state before the
                    // replacement client becomes active. Revocation failure does not block it.
                    repository.disconnectAccount()

                    val replacement = if (usesRepositoryOverride) {
                        repository
                    } else {
                        createRepository(configuration)
                    }
                    _redditApiConfiguration.value = configuration
                    if (!usesRepositoryOverride) repositoryState.value = replacement
                    _selectedFeed.value = "Home"
                    // Cached feeds and the back trail belong to the account that loaded them.
                    feedCache.clear()
                    feedHistory.clear()
                    _canReturnToPreviousFeed.value = false
                    _selectedPostId.value = null
                    _searchQuery.value = ""
                    _searchVisible.value = false
                    _hiddenPostIds.value = emptySet()
                    _collapsedCommentIds.value = emptySet()
                    _composerVisible.value = false
                    _screen.value = AppScreen.Settings
                    _connectionState.value = when {
                        !repository.isLive -> RedditConnectionState.Unconfigured
                        repository.accountState.value is RedditAccountState.SignedIn ->
                            RedditConnectionState.Connecting
                        else -> RedditConnectionState.SignedOut
                    }
                    _message.value = if (resetToBuildDefaults) {
                        "Reddit API settings cleared"
                    } else {
                        "Reddit API settings saved"
                    }
                    if (repository.accountState.value is RedditAccountState.SignedIn) {
                        refreshInternal(showSuccessMessage = false)
                    }
                }
            } finally {
                _apiConfigurationChanging.value = false
            }
        }
    }

    fun togglePostVote(postId: String, vote: VoteState) {
        viewModelScope.launch {
            repository.applyPostVote(postId, vote).onFailure { error ->
                handleAuthenticatedFailure(error, "Vote could not be saved")
            }
        }
    }

    fun toggleCommentVote(postId: String, commentId: String, vote: VoteState) {
        viewModelScope.launch {
            repository.applyCommentVote(postId, commentId, vote).onFailure { error ->
                handleAuthenticatedFailure(error, "Vote could not be saved")
            }
        }
    }

    fun toggleSaved(postId: String) {
        viewModelScope.launch {
            repository.applySaved(postId)
                .onSuccess {
                    val saved = repository.post(postId)?.isSaved == true
                    _message.value = if (saved) "Saved for later" else "Removed from saved"
                }
                .onFailure { error ->
                    handleAuthenticatedFailure(error, "Saved posts could not be updated")
                }
        }
    }

    fun hidePost(postId: String) {
        val hidden = postId !in _hiddenPostIds.value
        _hiddenPostIds.value = if (hidden) _hiddenPostIds.value + postId else _hiddenPostIds.value - postId
        _message.value = if (hidden) "Post hidden on Reddit" else "Post unhidden"
        viewModelScope.launch {
            repository.applyHidden(postId, hidden).onFailure { error ->
                _hiddenPostIds.value = if (hidden) {
                    _hiddenPostIds.value - postId
                } else {
                    _hiddenPostIds.value + postId
                }
                handleAuthenticatedFailure(error, "Reddit could not update hidden posts")
            }
        }
    }

    fun reportPost(postId: String, reason: String) = reportThing("t3_$postId", reason)

    fun reportComment(commentId: String, reason: String) = reportThing("t1_$commentId", reason)

    private fun reportThing(fullname: String, reason: String) {
        if (reason.isBlank()) return
        viewModelScope.launch {
            repository.reportThing(fullname, reason)
                .onSuccess {
                    if (fullname.startsWith("t3_")) {
                        _hiddenPostIds.value += fullname.removePrefix("t3_")
                    }
                    _message.value = "Report sent to Reddit"
                }
                .onFailure { handleAuthenticatedFailure(it, "Report could not be sent") }
        }
    }

    fun blockUser(username: String) {
        viewModelScope.launch {
            repository.blockUser(username)
                .onSuccess { _message.value = "u/$username blocked" }
                .onFailure { error ->
                    if (error.message?.contains("403") == true) {
                        _message.value = "Reddit only allows approved API clients to block users"
                    } else {
                        handleAuthenticatedFailure(error, "User could not be blocked")
                    }
                }
        }
    }

    fun editPost(postId: String, body: String) = editThing("t3_$postId", body, postId)

    fun editComment(postId: String, commentId: String, body: String) =
        editThing("t1_$commentId", body, postId)

    private fun editThing(fullname: String, body: String, postId: String) {
        viewModelScope.launch {
            repository.editThing(fullname, body)
                .onSuccess {
                    _message.value = "Changes saved to Reddit"
                    cacheCurrentFeed()
                    if (fullname.startsWith("t1_")) loadCommentsInternal(postId)
                }
                .onFailure { handleAuthenticatedFailure(it, "Changes could not be saved") }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            repository.deleteThing("t3_$postId")
                .onSuccess {
                    _selectedPostId.value = null
                    _screen.value = AppScreen.Feed
                    _message.value = "Post deleted from Reddit"
                    cacheCurrentFeed()
                }
                .onFailure { handleAuthenticatedFailure(it, "Post could not be deleted") }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        viewModelScope.launch {
            repository.deleteThing("t1_$commentId")
                .onSuccess {
                    _message.value = "Comment deleted from Reddit"
                    loadCommentsInternal(postId)
                }
                .onFailure { handleAuthenticatedFailure(it, "Comment could not be deleted") }
        }
    }

    fun toggleCommunitySubscription(community: Community) {
        val next = !community.isFavorite
        viewModelScope.launch {
            repository.setCommunitySubscription(community.name, next)
                .onSuccess {
                    _message.value = if (next) {
                        "Following ${community.name}"
                    } else {
                        "Unfollowed ${community.name}"
                    }
                }
                .onFailure { handleAuthenticatedFailure(it, "Subscription could not be updated") }
        }
    }

    fun toggleCommentCollapsed(commentId: String) {
        _collapsedCommentIds.value = _collapsedCommentIds.value.toMutableSet().apply {
            if (!add(commentId)) remove(commentId)
        }
    }

    fun refresh() {
        if (!repository.isLive) {
            _connectionState.value = RedditConnectionState.Unconfigured
            _message.value = "Add your Reddit API settings and connect your account first"
            return
        }
        if (repository.accountState.value !is RedditAccountState.SignedIn) {
            _connectionState.value = RedditConnectionState.SignedOut
            _message.value = "Connect your Reddit account to load the feed"
            return
        }
        refreshInternal(showSuccessMessage = true)
    }

    private fun refreshInternal(showSuccessMessage: Boolean) {
        feedHasMorePages = true
        if (!repository.isLive || repository.accountState.value !is RedditAccountState.SignedIn) {
            _connectionState.value = if (repository.isLive) {
                RedditConnectionState.SignedOut
            } else {
                RedditConnectionState.Unconfigured
            }
            return
        }
        feedJob?.cancel()
        val requestGeneration = ++feedRequestGeneration
        feedJob = viewModelScope.launch {
            _refreshing.value = true
            if (repository.isLive && _connectionState.value != RedditConnectionState.Connected) {
                _connectionState.value = RedditConnectionState.Connecting
            }
            val result = repository.refresh(
                feedName = _selectedFeed.value,
                sort = _feedSort.value.label.lowercase(),
                // Sending a window for any other sort would be noise Reddit ignores anyway.
                timeframe = _feedTimeframe.value.redditParam.takeIf {
                    _feedSort.value == FeedSort.Top
                },
            )
            if (requestGeneration != feedRequestGeneration) return@launch
            _refreshing.value = false
            result.onSuccess {
                _connectionState.value = RedditConnectionState.Connected
                // A completed refresh is a fresh listing, so whatever was concluded about
                // further pages while it was in flight no longer applies. Without this, a
                // load-more that raced the refresh could leave paging switched off for a feed
                // that has plenty more to give.
                feedHasMorePages = true
                cacheCurrentFeed()
                MediaCache.prefetch(
                    getApplication(),
                    posts.value.asSequence()
                        .mapNotNull { it.media?.first?.playbackUrls?.firstOrNull() }
                        .asIterable(),
                )
                if (showSuccessMessage) {
                    _message.value = "Reddit feed refreshed"
                }
            }.onFailure { error ->
                val stillSignedIn = repository.accountState.value is RedditAccountState.SignedIn
                if (stillSignedIn) {
                    _connectionState.value = RedditConnectionState.Error
                } else {
                    transitionToSignedOut()
                }
                _message.value = error.message ?: "Reddit could not be reached"
            }
        }
    }

    private fun loadCommentsInternal(postId: String) {
        commentJob?.cancel()
        val requestGeneration = ++commentRequestGeneration
        commentJob = viewModelScope.launch {
            _commentsLoading.value = true
            val result = repository.loadComments(postId, _commentSort.value.label.lowercase())
            if (requestGeneration != commentRequestGeneration || postId != _selectedPostId.value) {
                return@launch
            }
            _commentsLoading.value = false
            result.onSuccess {
                if (!usesRepositoryOverride) {
                    val loaded = repository.comments(postId).value
                    viewModelScope.launch(Dispatchers.IO) {
                        offlineCache.putComments(
                            postId,
                            CachedComments(
                                comments = loaded,
                                sort = _commentSort.value.name,
                                updatedAtMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
            result.onFailure { error ->
                if (repository.accountState.value !is RedditAccountState.SignedIn) {
                    transitionToSignedOut()
                }
                _message.value = error.message ?: "Comments could not be loaded"
            }
        }
    }

    fun showComposer() {
        if (repository.accountState.value is RedditAccountState.SignedIn) {
            seedComposerCommunity()
            _composerVisible.value = true
        } else {
            _message.value = "Connect your Reddit account before creating a post"
        }
    }

    /**
     * Points a fresh composer at the community being browsed, which is where the compose button
     * pressed inside a community almost always means to post.
     *
     * An unfinished draft keeps the destination it was written for: the community is part of what
     * was drafted, so retargeting it would quietly redirect a post someone had already started.
     */
    private fun seedComposerCommunity() {
        val community = composerCommunity ?: return
        val draft = _postDraft.value
        if (draft.title.isNotBlank() || draft.body.isNotBlank() || draft.linkUrl.isNotBlank()) return
        if (draft.community.equals(community, ignoreCase = true)) return
        updatePostDraft(draft.copy(community = community))
    }

    /**
     * The community a new post should default to, as an `r/` path. Covers a community's own feed
     * and a search scoped to one. Null on Home, Saved, user feeds and site-wide search, where
     * there is no community to infer and the composer falls back to the subscription list.
     */
    private val composerCommunity: String?
        get() {
            val feed = _selectedFeed.value
            val name = when {
                feed.startsWith("r/", ignoreCase = true) -> feed.drop(2)
                feed.startsWith("qr/", ignoreCase = true) -> feed.drop(3).substringBefore('/')
                else -> null
            }
            return name?.takeIf(String::isNotEmpty)?.let { "r/$it" }
        }

    fun hideComposer() {
        _composerVisible.value = false
    }

    fun updatePostDraft(draft: PostDraft) {
        _postDraft.value = draft
        if (!usesRepositoryOverride) {
            viewModelScope.launch(Dispatchers.IO) {
                offlineCache.putDraft(POST_DRAFT_KEY, encodePostDraft(draft))
            }
        }
    }

    fun submitPost(draft: PostDraft) {
        if (draft.title.isBlank()) return
        if (repository.accountState.value !is RedditAccountState.SignedIn) {
            _message.value = "Connect your Reddit account before creating a post"
            return
        }
        // Caught here rather than at the API, where a missing or unusable address comes back as a
        // generic Reddit failure that says nothing about which field is at fault.
        if (draft.kind == SubmissionKind.LINK && normalizeSubmissionUrl(draft.linkUrl) == null) {
            _message.value = "A link post needs a web address"
            return
        }
        viewModelScope.launch {
            repository.publishPost(
                communityName = draft.community.removePrefix("r/"),
                title = draft.title.trim(),
                body = draft.body.trim(),
                kind = draft.kind,
                linkUrl = draft.linkUrl,
            ).onSuccess {
                _selectedFeed.value = "Home"
                _composerVisible.value = false
                _postDraft.value = PostDraft()
                if (!usesRepositoryOverride) {
                    viewModelScope.launch(Dispatchers.IO) { offlineCache.putDraft(POST_DRAFT_KEY, null) }
                }
                _message.value = "Posted to Reddit"
                refreshInternal(showSuccessMessage = false)
            }.onFailure { error ->
                handleAuthenticatedFailure(error, "Post could not be submitted")
            }
        }
    }

    fun submitComment(postId: String, parentId: String?, body: String) {
        if (body.isBlank()) return
        if (repository.accountState.value !is RedditAccountState.SignedIn) {
            _message.value = "Connect your Reddit account before replying"
            return
        }
        viewModelScope.launch {
            repository.publishComment(postId, parentId, body)
                .onSuccess {
                    _message.value = "Reply posted to Reddit"
                    loadCommentsInternal(postId)
                }
                .onFailure { error ->
                    handleAuthenticatedFailure(error, "Reply could not be submitted")
                }
        }
    }

    private suspend fun handleAuthenticatedFailure(error: Throwable, fallbackMessage: String) {
        if (repository.isLive && repository.accountState.value !is RedditAccountState.SignedIn) {
            transitionToSignedOut()
        }
        _message.value = error.message ?: fallbackMessage
    }

    private fun invalidateReadRequests(except: Job? = null) {
        feedRequestGeneration++
        commentRequestGeneration++
        feedJob?.takeUnless { it === except }?.cancel()
        commentJob?.takeUnless { it === except }?.cancel()
        feedJob = null
        commentJob = null
        _refreshing.value = false
        _commentsLoading.value = false
    }

    private suspend fun transitionToSignedOut() {
        invalidateReadRequests(except = currentCoroutineContext()[Job])
        repository.disconnectAccount()
        _selectedFeed.value = "Home"
        _selectedPostId.value = null
        _composerVisible.value = false
        _collapsedCommentIds.value = emptySet()
        _screen.value = AppScreen.Feed
        _connectionState.value = if (repository.isLive) {
            RedditConnectionState.SignedOut
        } else {
            RedditConnectionState.Unconfigured
        }
    }

    fun updateThemeMode(mode: ThemeMode) = updateSettings { copy(themeMode = mode) }

    fun updateFeedPresentation(value: FeedPresentation) =
        updateSettings { copy(feedPresentation = value) }

    fun updateTextScale(value: Float) =
        updateSettings { copy(textScale = value.coerceIn(.85f, 1.3f)) }

    fun toggleThumbnailsSide() = updateSettings { copy(thumbnailsOnRight = !thumbnailsOnRight) }

    fun toggleSwipeActions() = updateSettings { copy(swipeActions = !swipeActions) }

    fun toggleHaptics() = updateSettings { copy(haptics = !haptics) }

    fun toggleDimReadPosts() = updateSettings { copy(dimReadPosts = !dimReadPosts) }

    fun togglePostFlairs() = updateSettings { copy(showPostFlairs = !showPostFlairs) }

    fun toggleAlwaysShowNsfw() = updateSettings { copy(alwaysShowNsfw = !alwaysShowNsfw) }

    fun toggleWebViewSignIn() = updateSettings { copy(webViewSignIn = !webViewSignIn) }

    fun updateFeedActions(actions: List<FeedAction>) = updateSettings {
        copy(feedActions = actions.distinct().take(MAX_ACTION_BAR_ITEMS).ifEmpty { DEFAULT_FEED_ACTIONS })
    }

    fun updatePostSwipeActions(actions: SwipeActionConfig) = updateSettings {
        copy(postSwipeActions = actions)
    }

    fun updateCommentSwipeActions(actions: SwipeActionConfig) = updateSettings {
        copy(commentSwipeActions = actions)
    }

    fun toggleHideReadPosts() = updateSettings { copy(hideReadPosts = !hideReadPosts) }

    fun updateContentFilters(keywords: Set<String>, communities: Set<String>, authors: Set<String>) =
        updateSettings {
            copy(
                blockedKeywords = normalizeFilters(keywords),
                blockedCommunities = normalizeFilters(communities).mapTo(linkedSetOf()) {
                    it.removePrefix("r/")
                },
                blockedAuthors = normalizeFilters(authors).mapTo(linkedSetOf()) {
                    it.removePrefix("u/")
                },
            )
        }

    fun markPostsRead(postIds: Collection<String>) {
        if (postIds.isEmpty()) return
        // One feed update and one preference write for the whole set. Marking them one at a
        // time published a new feed snapshot per post, and every snapshot re-ran the read-state
        // merge below and the feed's filter and sort before the next post was even marked.
        repository.markRead(postIds)
        if (readPostStore.addAll(postIds)) _readPostIds.value = readPostStore.ids()
        cacheCurrentFeed()
        _message.value = "Marked ${postIds.size} posts as read"
    }

    fun clearReadHistory() {
        readPostStore.clear()
        repository.markRead(posts.value.filter(Post::isRead).map(Post::id), isRead = false)
        _readPostIds.value = emptySet()
        _message.value = "Read history cleared"
    }

    /** Communities matching the search screen's query. */
    val searchCommunitySuggestions: StateFlow<List<Community>> = _searchDraft
        .map(String::trim)
        .distinctUntilChanged()
        .debounce { query -> if (query.length < MIN_COMMUNITY_QUERY) 0L else COMMUNITY_QUERY_DEBOUNCE_MS }
        .flatMapLatest { query ->
            if (query.length < MIN_COMMUNITY_QUERY) {
                flowOf(emptyList())
            } else {
                flow {
                    emit(
                        repository.searchCommunities(query, SEARCH_SUGGESTION_LIMIT)
                            .getOrDefault(emptyList()),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Opens the full-screen viewer on a post's media, starting at [index]. */
    fun openMedia(post: Post, index: Int = 0) {
        val media = post.media ?: return
        // Looking at the media is reading the post; the feed should dim it either way.
        markPostRead(post.id)
        _mediaViewer.value = MediaViewerRequest(
            title = post.title,
            assets = media.assets,
            startIndex = index,
        )
        val generation = ++mediaViewerGeneration
        // Opens on Reddit's silent preview straight away and upgrades in place once RedGifs
        // answers. Waiting for the network before showing anything would make every one of
        // these posts feel slower in exchange for a second or two of missing audio.
        //
        // Single-asset posts only: a gallery is not a RedGifs link, and swapping its contents
        // for one file would throw away the pages either side of the one being viewed.
        if (media.assets.size == 1) {
            resolveRedGifs(post.destinationUrl, generation) { resolved ->
                val current = _mediaViewer.value ?: return@resolveRedGifs
                _mediaViewer.value = current.copy(assets = listOf(resolved), startIndex = 0)
            }
        }
    }

    /** Opens a single media URL — a GIF or clip linked from a comment — in the same viewer. */
    fun openMediaLink(url: String, title: String) {
        // A RedGifs link points at a web page, which no player can open. Here there is nothing
        // to show in the meantime, so this one waits for the real file rather than opening a
        // viewer that could only fail.
        if (RedGifsClient.idFrom(url) != null) {
            val generation = ++mediaViewerGeneration
            resolveRedGifs(url, generation) { resolved ->
                _mediaViewer.value = MediaViewerRequest(title = title, assets = listOf(resolved))
            }
            return
        }
        // A `v.redd.it` address is a web page; the streams live underneath it at fixed names.
        RedditVideoLinks.asset(url)?.let { asset ->
            _mediaViewer.value = MediaViewerRequest(title = title, assets = listOf(asset))
            return
        }
        val path = url.substringBefore('?').lowercase()
        val kind = when {
            path.endsWith(".gif") || path.endsWith(".gifv") -> MediaKind.ANIMATED
            path.endsWith(".mp4") || path.endsWith(".webm") -> MediaKind.ANIMATED
            // A still handed to a player renders nothing and reports a playback failure, so
            // anything that looks like an image has to be recognised before the video default.
            LINK_IMAGE_SUFFIXES.any(path::endsWith) -> MediaKind.IMAGE
            LINK_IMAGE_HOSTS.any(path::contains) -> MediaKind.IMAGE
            else -> MediaKind.VIDEO
        }
        _mediaViewer.value = MediaViewerRequest(
            title = title,
            assets = listOf(MediaAsset(kind = kind, url = url, hasAudio = false)),
        )
    }

    /**
     * Writes the harvested pool down so the next launch does not have to search for it again.
     *
     * Harvesting is a round of searches against Reddit, and it was being paid once per process.
     * The set of communities it finds barely changes, so it belongs on disk next to everything
     * else this app keeps between launches.
     */
    private fun persistAdultPool() {
        if (usesRepositoryOverride) return
        val names = repository.randomCommunityPoolSnapshot()
        if (names.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            offlineCache.putDraft(ADULT_POOL_KEY, encodeCommunityNames(names))
        }
    }

    private fun encodeCommunityNames(names: List<String>): String =
        JSONArray().apply { names.forEach(::put) }.toString()

    private fun decodeCommunityNames(value: String): List<String> = runCatching {
        val array = JSONArray(value)
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf(String::isNotBlank)
        }
    }.getOrDefault(emptyList())

    private fun markPostRead(postId: String) {
        repository.markRead(postId)
        if (readPostStore.add(postId)) _readPostIds.value = readPostStore.ids()
    }

    /**
     * Writes the asset being viewed into the device gallery.
     *
     * The viewer stays where it is: a save is something done to what is on screen, not a reason
     * to leave it. The snackbar is the whole of the feedback.
     */
    fun saveMedia(asset: MediaAsset) {
        viewModelScope.launch {
            _message.value = when (val result = MediaSaver.save(getApplication(), asset)) {
                is MediaSaveResult.Saved -> "Saved to ${result.album}"
                is MediaSaveResult.Failed -> result.reason
            }
        }
    }

    fun closeMedia() {
        // Anything still resolving belongs to a viewer that is no longer open.
        mediaViewerGeneration++
        _mediaViewer.value = null
    }

    /**
     * Looks up a RedGifs link and hands back the real file, if it is one and it resolves.
     *
     * [generation] guards against a slow answer landing in a viewer the reader has since closed
     * or moved past: by the time the network replies the screen may be showing something else
     * entirely, and replacing its contents then would be worse than never resolving at all.
     */
    private fun prefetchRedGifs(sourceUrl: String?) {
        if (usesRepositoryOverride) return
        val url = sourceUrl ?: return
        if (RedGifsClient.idFrom(url) == null) return
        viewModelScope.launch { redGifs.prefetch(url) }
    }

    private fun resolveRedGifs(
        sourceUrl: String?,
        generation: Int,
        onResolved: (MediaAsset) -> Unit,
    ) {
        if (usesRepositoryOverride) return
        val url = sourceUrl ?: return
        if (RedGifsClient.idFrom(url) == null) return
        viewModelScope.launch {
            val resolved = redGifs.resolve(url)
            if (generation != mediaViewerGeneration) return@launch
            if (resolved == null) {
                // Silence beats a wrong screen: a post already shows Reddit's preview, and only
                // a link has nothing at all to fall back to.
                if (_mediaViewer.value == null) {
                    _message.value = "Could not open that RedGifs link"
                }
                return@launch
            }
            onResolved(resolved)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun notify(message: String) {
        _message.value = message
    }

    fun resetSettings() {
        _settings.value = OtterSettings()
        persistSettings(_settings.value)
        _message.value = "Settings restored"
    }

    private fun updateSettings(transform: OtterSettings.() -> OtterSettings) {
        _settings.value = _settings.value.transform()
        persistSettings(_settings.value)
    }

    private fun createRepository(configuration: RedditApiConfiguration): RedditRepository {
        if (BuildConfig.BENCHMARK_MODE) return InMemoryRedditRepository()
        val value = configuration.normalized()
        if (!value.isUsable) {
            return InMemoryRedditRepository(
                initialPosts = emptyList(),
                initialComments = emptyMap(),
                initialCommunities = emptyList(),
            )
        }

        val application = getApplication<Application>()
        return RedditApiRepository(
            configuration = value,
            oauthManager = RedditOAuthManager(
                configuration = value,
                store = AndroidRedditOAuthStore(application),
            ),
        )
    }

    private companion object {
        const val MIN_COMMUNITY_QUERY = 2
        const val SEARCH_SUGGESTION_LIMIT = 10
        const val COMMUNITY_QUERY_DEBOUNCE_MS = 260L
        const val FEED_CACHE_LIMIT = 8
        const val COMMENT_FLOW_CACHE_LIMIT = 16
        const val DEFAULT_LOAD_FACTOR = .75f
        const val MAX_PERSISTED_POSTS = 100
        const val MAX_ACTION_BAR_ITEMS = 5
        const val MAX_FILTERS_PER_KIND = 50
        const val POST_DRAFT_KEY = "new_post"
        const val ADULT_POOL_KEY = "adult_community_pool"

        /** Link targets the full-screen viewer should open as a still rather than a clip. */
        val LINK_IMAGE_SUFFIXES = listOf(".jpg", ".jpeg", ".png", ".webp", ".avif", ".bmp")
        val LINK_IMAGE_HOSTS = listOf("preview.redd.it", "i.redd.it", "i.imgur.com")
        val DEFAULT_FEED_ACTIONS = listOf(
            FeedAction.Search,
            FeedAction.Compose,
            FeedAction.Menu,
        )
    }

    private fun loadFeedTimeframe(): FeedTimeframe = runCatching {
        FeedTimeframe.valueOf(
            preferences.getString("feedTimeframe", FeedTimeframe.Today.name).orEmpty(),
        )
    }.getOrDefault(FeedTimeframe.Today)

    private fun loadSettings(): OtterSettings = OtterSettings(
        themeMode = runCatching {
            ThemeMode.valueOf(preferences.getString("theme", ThemeMode.Dark.name).orEmpty())
        }.getOrDefault(ThemeMode.Dark),
        feedPresentation = runCatching {
            FeedPresentation.valueOf(
                preferences.getString("presentation", FeedPresentation.Compact.name).orEmpty(),
            )
        }.getOrDefault(FeedPresentation.Compact),
        textScale = preferences.getFloat("textScale", 1f),
        thumbnailsOnRight = preferences.getBoolean("thumbnailsOnRight", true),
        swipeActions = preferences.getBoolean("swipeActions", true),
        haptics = preferences.getBoolean("haptics", true),
        dimReadPosts = preferences.getBoolean("dimReadPosts", true),
        showPostFlairs = preferences.getBoolean("showPostFlairs", true),
        alwaysShowNsfw = preferences.getBoolean("alwaysShowNsfw", false),
        webViewSignIn = preferences.getBoolean("webViewSignIn", false),
        feedActions = preferences.getString("feedActions", null)
            ?.split(',')
            ?.mapNotNull { value -> FeedAction.entries.firstOrNull { it.name == value } }
            ?.distinct()
            ?.take(MAX_ACTION_BAR_ITEMS)
            ?.ifEmpty { null }
            ?: DEFAULT_FEED_ACTIONS,
        postSwipeActions = loadSwipeActions("postSwipe", SwipeActionConfig()),
        commentSwipeActions = loadSwipeActions("commentSwipe", DefaultCommentSwipeActions),
        hideReadPosts = preferences.getBoolean("hideReadPosts", false),
        blockedKeywords = preferences.getStringSet("blockedKeywords", emptySet()).orEmpty(),
        blockedCommunities = preferences.getStringSet("blockedCommunities", emptySet()).orEmpty(),
        blockedAuthors = preferences.getStringSet("blockedAuthors", emptySet()).orEmpty(),
    )

    private fun persistSettings(settings: OtterSettings) {
        preferences.edit {
            putString("theme", settings.themeMode.name)
            putString("presentation", settings.feedPresentation.name)
            putFloat("textScale", settings.textScale)
            putBoolean("thumbnailsOnRight", settings.thumbnailsOnRight)
            putBoolean("swipeActions", settings.swipeActions)
            putBoolean("haptics", settings.haptics)
            putBoolean("dimReadPosts", settings.dimReadPosts)
            putBoolean("showPostFlairs", settings.showPostFlairs)
            putBoolean("alwaysShowNsfw", settings.alwaysShowNsfw)
            putBoolean("webViewSignIn", settings.webViewSignIn)
            putString("feedActions", settings.feedActions.joinToString(",", transform = FeedAction::name))
            putSwipeActions("postSwipe", settings.postSwipeActions)
            putSwipeActions("commentSwipe", settings.commentSwipeActions)
            putBoolean("hideReadPosts", settings.hideReadPosts)
            putStringSet("blockedKeywords", settings.blockedKeywords)
            putStringSet("blockedCommunities", settings.blockedCommunities)
            putStringSet("blockedAuthors", settings.blockedAuthors)
        }
    }

    private fun loadSwipeActions(prefix: String, fallback: SwipeActionConfig) = SwipeActionConfig(
        rightShort = loadSwipeAction("${prefix}RightShort", fallback.rightShort),
        rightLong = loadSwipeAction("${prefix}RightLong", fallback.rightLong),
        leftShort = loadSwipeAction("${prefix}LeftShort", fallback.leftShort),
        leftLong = loadSwipeAction("${prefix}LeftLong", fallback.leftLong),
    )

    private fun loadSwipeAction(key: String, fallback: SwipeAction): SwipeAction =
        enumValueOr(preferences.getString(key, fallback.name).orEmpty(), fallback)

    private fun android.content.SharedPreferences.Editor.putSwipeActions(
        prefix: String,
        value: SwipeActionConfig,
    ) {
        putString("${prefix}RightShort", value.rightShort.name)
        putString("${prefix}RightLong", value.rightLong.name)
        putString("${prefix}LeftShort", value.leftShort.name)
        putString("${prefix}LeftLong", value.leftLong.name)
    }

    private fun normalizeFilters(values: Set<String>): Set<String> = values
        .asSequence()
        .map { it.trim().lowercase() }
        .filter(String::isNotBlank)
        .take(MAX_FILTERS_PER_KIND)
        .toCollection(linkedSetOf())

    private inline fun <reified T : Enum<T>> enumValueOr(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private fun encodePostDraft(value: PostDraft): String = JSONObject()
        .put("title", value.title)
        .put("body", value.body)
        .put("community", value.community)
        .put("kind", value.kind.name)
        .put("linkUrl", value.linkUrl)
        .toString()

    private fun decodePostDraft(value: String): PostDraft? = runCatching {
        JSONObject(value).let { json ->
            PostDraft(
                title = json.optString("title"),
                body = json.optString("body"),
                community = json.optString("community"),
                // Drafts saved before link posts existed carry no kind, and a text post is what
                // they were.
                kind = enumValueOr(json.optString("kind"), SubmissionKind.TEXT),
                linkUrl = json.optString("linkUrl"),
            )
        }
    }.getOrNull()
}
