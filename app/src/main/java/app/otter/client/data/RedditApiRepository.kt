package app.otter.client.data

import app.otter.client.data.oauth.InMemoryRedditOAuthStore
import app.otter.client.data.oauth.RedditApiConfiguration
import app.otter.client.data.oauth.RedditAccessToken
import app.otter.client.data.oauth.RedditOAuthManager
import app.otter.client.BuildConfig
import app.otter.client.model.Comment
import app.otter.client.model.Community
import app.otter.client.model.Post
import app.otter.client.model.PostPreview
import app.otter.client.model.PostType
import app.otter.client.model.SubmissionKind
import app.otter.client.model.RedditAccount
import app.otter.client.model.RedditAccountState
import app.otter.client.model.RedditAuthenticationException
import app.otter.client.model.VoteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Reddit Data API adapter for an installed-app client ID.
 * Reads and account mutations use the user's authorization-code/refresh-token session.
 */
class RedditApiRepository(
    configuration: RedditApiConfiguration,
    private val httpClient: OkHttpClient = OtterHttp.client,
    private val oauthManager: RedditOAuthManager = RedditOAuthManager(
        configuration = configuration,
        store = InMemoryRedditOAuthStore(),
        httpClient = httpClient,
    ),
) : InMemoryRedditRepository(
    initialPosts = emptyList(),
    initialComments = emptyMap(),
    initialCommunities = emptyList(),
) {
    private val configuration = configuration.normalized()
    private val userAgent: String
        get() = configuration.userAgent

    override val isLive: Boolean = true
    override val accountState = oauthManager.accountState

    /** Keeps optimistic account mutations stable across subsequent listing responses. */
    private val localStateLock = Any()
    private val localPostFlags = mutableMapOf<String, LocalPostFlags>()
    private val localPostDrafts = linkedMapOf<String, Post>()
    private val savedPostSnapshots = linkedMapOf<String, Post>()
    private val localCommentVotes = mutableMapOf<String, MutableMap<String, VoteState>>()
    private val localReplies = mutableMapOf<String, LinkedHashMap<String, Comment>>()
    private var contentSessionGeneration = 0L
    private var subscriptionsLoadedAtMillis = 0L
    private var paginationKey: String? = null
    private var afterCursor: String? = null
    private var loadedPostCount = 0
    private val nsfwPool = RandomCommunityPool()

    init {
        require(configuration.isUsable) { configuration.validationError().orEmpty() }
    }

    override fun beginAccountAuthorization(): Result<String> =
        oauthManager.beginAuthorization()

    override fun cancelAccountAuthorization() = oauthManager.cancelAuthorization()

    override suspend fun completeAccountAuthorization(callbackUrl: String): Result<RedditAccount> =
        oauthManager.completeAuthorization(callbackUrl)

    override suspend fun warmAccountSession(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { oauthManager.accessToken() }.map { }
    }

    override suspend fun retryPendingAccountRevocations(): Result<Unit> =
        oauthManager.flushPendingRevocations()

    override suspend fun disconnectAccount(): Result<Unit> {
        synchronized(localStateLock) {
            invalidateContentSessionLocked()
        }
        val result = try {
            oauthManager.disconnect()
        } finally {
            // A request can begin while token revocation is in flight. Advance the generation a
            // second time and clear again so neither pre-disconnect nor mid-disconnect work can
            // publish account data after sign-out.
            synchronized(localStateLock) {
                invalidateContentSessionLocked()
            }
        }
        return result
    }

    override fun submitPost(
        communityName: String,
        title: String,
        body: String,
        kind: SubmissionKind,
        linkUrl: String,
    ): Post =
        synchronized(localStateLock) {
            super.submitPost(communityName, title, body, kind, linkUrl).also { post ->
                localPostDrafts[post.id] = post
                rememberPostSnapshot(post)
            }
        }

    override fun submitComment(postId: String, parentId: String?, body: String): Comment =
        synchronized(localStateLock) {
            super.submitComment(postId, parentId, body).also { comment ->
                localReplies.getOrPut(postId) { linkedMapOf() }[comment.id] = comment
                post(postId)?.let(::rememberPostSnapshot)
            }
        }

    override fun togglePostVote(postId: String, vote: VoteState): Boolean =
        synchronized(localStateLock) {
            super.togglePostVote(postId, vote).also { changed ->
                if (changed) {
                    val post = checkNotNull(post(postId))
                    localPostFlags[postId] = localPostFlags[postId].orEmpty().copy(
                        voteState = post.voteState,
                    )
                    rememberPostSnapshot(post)
                }
            }
        }

    override fun toggleCommentVote(
        postId: String,
        commentId: String,
        vote: VoteState,
    ): Boolean = synchronized(localStateLock) {
        super.toggleCommentVote(postId, commentId, vote).also { changed ->
            if (changed) {
                val comment = checkNotNull(
                    comments(postId).value.firstOrNull { candidate -> candidate.id == commentId },
                )
                localCommentVotes.getOrPut(postId) { mutableMapOf() }[commentId] = comment.voteState
                localReplies[postId]?.takeIf { replies -> commentId in replies }?.set(commentId, comment)
            }
        }
    }

    override fun toggleSaved(postId: String): Boolean = synchronized(localStateLock) {
        super.toggleSaved(postId).also { changed ->
            if (changed) {
                val post = checkNotNull(post(postId))
                localPostFlags[postId] = localPostFlags[postId].orEmpty().copy(
                    isSaved = post.isSaved,
                )
                rememberPostSnapshot(post)
            }
        }
    }

    override fun markRead(postId: String, isRead: Boolean): Boolean =
        synchronized(localStateLock) {
            super.markRead(postId, isRead).also { changed ->
                if (changed) {
                    val post = checkNotNull(post(postId))
                    localPostFlags[postId] = localPostFlags[postId].orEmpty().copy(
                        isRead = post.isRead,
                    )
                    rememberPostSnapshot(post)
                }
            }
        }

    override fun markRead(postIds: Collection<String>, isRead: Boolean): List<Post> =
        synchronized(localStateLock) {
            super.markRead(postIds, isRead).onEach { post ->
                localPostFlags[post.id] = localPostFlags[post.id].orEmpty().copy(
                    isRead = post.isRead,
                )
                rememberPostSnapshot(post)
            }
        }

    override suspend fun applyPostVote(postId: String, vote: VoteState): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(vote != VoteState.NONE) { "Choose an upvote or downvote" }
                val current = post(postId) ?: throw IllegalArgumentException("Post is no longer available")
                val next = if (current.voteState == vote) VoteState.NONE else vote
                authorizedPost(
                    path = "/api/vote",
                    body = FormBody.Builder()
                        .add("id", "t3_$postId")
                        .add("dir", next.redditDirection.toString())
                        .build(),
                    retryOnUnauthorized = true,
                )
                currentCoroutineContext().ensureActive()
                togglePostVote(postId, vote)
            }
        }

    override suspend fun applyCommentVote(
        postId: String,
        commentId: String,
        vote: VoteState,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            require(vote != VoteState.NONE) { "Choose an upvote or downvote" }
            val current = comments(postId).value.firstOrNull { it.id == commentId }
                ?: throw IllegalArgumentException("Comment is no longer available")
            val next = if (current.voteState == vote) VoteState.NONE else vote
            authorizedPost(
                path = "/api/vote",
                body = FormBody.Builder()
                    .add("id", "t1_$commentId")
                    .add("dir", next.redditDirection.toString())
                    .build(),
                retryOnUnauthorized = true,
            )
            currentCoroutineContext().ensureActive()
            toggleCommentVote(postId, commentId, vote)
        }
    }

    override suspend fun applySaved(postId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val current = post(postId) ?: throw IllegalArgumentException("Post is no longer available")
                authorizedPost(
                    path = if (current.isSaved) "/api/unsave" else "/api/save",
                    body = FormBody.Builder().add("id", "t3_$postId").build(),
                    retryOnUnauthorized = true,
                )
                currentCoroutineContext().ensureActive()
                toggleSaved(postId)
            }
        }

    override suspend fun applyHidden(postId: String, hidden: Boolean): Result<Unit> =
        simpleAccountAction(
            path = if (hidden) "/api/hide" else "/api/unhide",
            body = FormBody.Builder().add("id", "t3_$postId").build(),
        )

    override suspend fun reportThing(fullname: String, reason: String): Result<Unit> {
        val normalizedReason = reason.trim().take(100)
        return simpleAccountAction(
            path = "/api/report",
            body = FormBody.Builder()
                .add("api_type", "json")
                .add("thing_id", fullname)
                .add("reason", "other")
                .add("other_reason", normalizedReason)
                .build(),
        )
    }

    override suspend fun blockUser(username: String): Result<Unit> = simpleAccountAction(
        path = "/api/block_user",
        body = FormBody.Builder()
            .add("api_type", "json")
            .add("name", username.trim().removePrefix("u/"))
            .build(),
    )

    override suspend fun deleteThing(fullname: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authorizedPost(
                "/api/del",
                FormBody.Builder().add("id", fullname).build(),
                retryOnUnauthorized = true,
            )
            currentCoroutineContext().ensureActive()
            removeLocalThing(fullname)
        }
    }

    override suspend fun editThing(fullname: String, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(text.isNotBlank()) { "Text cannot be blank" }
                authorizedPost(
                    "/api/editusertext",
                    FormBody.Builder()
                        .add("api_type", "json")
                        .add("thing_id", fullname)
                        .add("text", text.trim())
                        .add("raw_json", "1")
                        .build(),
                    retryOnUnauthorized = true,
                )
                currentCoroutineContext().ensureActive()
                editLocalThing(fullname, text.trim())
            }
        }

    override suspend fun setCommunitySubscription(
        communityName: String,
        subscribed: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val name = communityName.trim().removePrefix("r/")
            authorizedPost(
                "/api/subscribe",
                FormBody.Builder()
                    .add("action", if (subscribed) "sub" else "unsub")
                    .add("sr_name", name)
                    .apply { if (subscribed) add("skip_initial_defaults", "true") }
                    .build(),
                retryOnUnauthorized = true,
            )
            currentCoroutineContext().ensureActive()
            updateCommunitySubscription(name, subscribed)
            subscriptionsLoadedAtMillis = 0L
        }
    }

    private suspend fun simpleAccountAction(path: String, body: FormBody): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                authorizedPost(path, body, retryOnUnauthorized = true)
                Unit
            }
        }

    override suspend fun publishPost(
        communityName: String,
        title: String,
        body: String,
        kind: SubmissionKind,
        linkUrl: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val community = communityName.trim().removePrefix("r/")
            require(community.isNotEmpty()) { "Community name cannot be blank" }
            require(title.isNotBlank()) { "Post title cannot be blank" }
            val form = FormBody.Builder()
                .add("api_type", "json")
                .add("kind", kind.formValue)
                .add("sr", community)
                .add("title", title.trim())
                .add("raw_json", "1")
                .add("resubmit", "true")
                .add("sendreplies", "true")
            // Reddit reads `text` only for a self post and `url` only for a link post, and
            // rejects the submission outright if the field its kind needs is missing.
            when (kind) {
                SubmissionKind.TEXT -> form.add("text", body.trim())
                SubmissionKind.LINK -> {
                    val url = normalizeSubmissionUrl(linkUrl)
                    requireNotNull(url) { "A link post needs a web address" }
                    form.add("url", url)
                }
            }
            authorizedPost(
                path = "/api/submit",
                body = form.build(),
                retryOnUnauthorized = false,
            )
            Unit
        }
    }

    override suspend fun publishComment(
        postId: String,
        parentId: String?,
        body: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(body.isNotBlank()) { "Reply cannot be blank" }
            authorizedPost(
                path = "/api/comment",
                body = FormBody.Builder()
                    .add("api_type", "json")
                    .add("thing_id", parentId?.let { "t1_$it" } ?: "t3_$postId")
                    .add("text", body.trim())
                    .add("raw_json", "1")
                    .build(),
                retryOnUnauthorized = false,
            )
            Unit
        }
    }

    override suspend fun refresh(
        feedName: String,
        sort: String,
        timeframe: String?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestSessionGeneration = synchronized(localStateLock) {
                    contentSessionGeneration
                }
                val signedInAccount = (accountState.value as? RedditAccountState.SignedIn)?.account
                    ?: throw RedditAuthenticationException("Connect your Reddit account first")

                val token = oauthManager.accessToken()
                val url = feedUrl(feedName, sort, timeframe, signedInAccount.username)
                val json = authorizedGet(url, token)
                if (!isCurrentContentSession(requestSessionGeneration)) return@runCatching
                val root = JSONObject(json)
                val loaded = parsePosts(root)
                // Remember where this page ended so the next one can continue from it.
                synchronized(localStateLock) {
                    paginationKey = pageKey(feedName, sort, timeframe)
                    afterCursor = root.optJSONObject("data")?.nullableString("after")
                    loadedPostCount = loaded.size
                }

                currentCoroutineContext().ensureActive()
                // Publish before anything else is fetched: the posts are already in hand, and
                // making them wait on a second request is what a slow feed actually feels like.
                synchronized(localStateLock) {
                    if (requestSessionGeneration == contentSessionGeneration) {
                        mergeCommunities(loaded.map(Post::community))
                        val remotePosts = loaded.map(::mergeRemotePost)
                        val drafts = draftsForFeed(feedName)
                        val draftIds = drafts.mapTo(mutableSetOf(), Post::id)
                        replaceFeed(drafts + remotePosts.filterNot { post -> post.id in draftIds })
                    }
                }

                // Subscriptions change rarely, so they do not belong in every refresh. The
                // drawer updates a moment after the feed rather than holding it up.
                if (shouldRefreshSubscriptions()) {
                    runCatching {
                        val subscribed = parseSubscribedCommunities(
                            JSONObject(
                                authorizedGet(
                                    "https://oauth.reddit.com/subreddits/mine/subscriber" +
                                        "?limit=100&raw_json=1",
                                    token,
                                ),
                            ),
                        )
                        synchronized(localStateLock) {
                            if (requestSessionGeneration == contentSessionGeneration) {
                                mergeCommunities(subscribed)
                                subscriptionsLoadedAtMillis = System.currentTimeMillis()
                            }
                        }
                    }
                }
            }
        }

    override suspend fun loadMoreFeed(
        feedName: String,
        sort: String,
        timeframe: String?,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val requestSessionGeneration = synchronized(localStateLock) { contentSessionGeneration }
            val signedInAccount = (accountState.value as? RedditAccountState.SignedIn)?.account
                ?: return@runCatching false

            val (key, cursor, alreadyLoaded) = synchronized(localStateLock) {
                Triple(paginationKey, afterCursor, loadedPostCount)
            }
            // A cursor only means anything for the listing it came from. When it belongs to a
            // different one, this feed simply has not had its first page yet -- the refresh is
            // still in flight -- and nothing is known about whether further pages exist.
            //
            // That is not the same as being finished, and the difference matters: the caller
            // latches a false as "this listing is exhausted" and stops asking for the rest of
            // the visit. Reaching the bottom of a restored feed before its refresh lands is
            // exactly how a community used to end up unable to page at all.
            if (key != pageKey(feedName, sort, timeframe)) {
                return@runCatching true
            }
            // Reddit signals the real end of a listing by returning no cursor at all.
            if (cursor.isNullOrBlank()) {
                return@runCatching false
            }

            val token = oauthManager.accessToken()
            val url = feedUrl(feedName, sort, timeframe, signedInAccount.username) +
                "&after=$cursor&count=$alreadyLoaded"
            val root = JSONObject(authorizedGet(url, token))
            if (!isCurrentContentSession(requestSessionGeneration)) return@runCatching false

            val page = parsePosts(root)
            val nextCursor = root.optJSONObject("data")?.nullableString("after")

            synchronized(localStateLock) {
                if (requestSessionGeneration != contentSessionGeneration) return@runCatching false
                if (paginationKey != key) return@runCatching false
                mergeCommunities(page.map(Post::community))
                appendFeed(page.map(::mergeRemotePost))
                afterCursor = nextCursor
                loadedPostCount = alreadyLoaded + page.size
            }

            !nextCursor.isNullOrBlank() && page.isNotEmpty()
        }
    }

    /** Saved lives under the account rather than in the listing namespace. */
    private fun feedUrl(
        feedName: String,
        sort: String,
        timeframe: String?,
        username: String,
    ): String = if (feedName.equals("Saved", ignoreCase = true)) {
        "https://oauth.reddit.com/user/$username/saved?limit=$FEED_PAGE_LIMIT&raw_json=1"
    } else {
        listingUrl(feedName, sort, timeframe)
    }

    /**
     * The author's flair, from whichever of the two shapes Reddit used.
     *
     * `author_flair_text` is the plain form. A flair built from the richtext editor arrives as a
     * list of fragments instead, where only the text ones carry anything a label can show — the
     * rest are emoji references, which this has no way to render.
     */
    private fun JSONObject.authorFlair(): String? {
        nullableString("author_flair_text")?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
        val fragments = optJSONArray("author_flair_richtext") ?: return null
        return (0 until fragments.length())
            .mapNotNull { index -> fragments.optJSONObject(index)?.nullableString("t") }
            .joinToString(" ")
            .trim()
            .takeIf(String::isNotEmpty)
    }

    private fun pageKey(feedName: String, sort: String, timeframe: String?): String =
        "$feedName|$sort|${timeframe.orEmpty()}"

    override suspend fun loadComments(postId: String, sort: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestSessionGeneration = synchronized(localStateLock) {
                    contentSessionGeneration
                }
                if (synchronized(localStateLock) { postId in localPostDrafts }) {
                    return@runCatching
                }

                val token = oauthManager.accessToken()
                val normalizedSort = sort.lowercase(Locale.ROOT)
                    .let { if (it == "best") "confidence" else it }
                // Sized for the first screen, not the whole thread. A 200-comment, 12-deep
                // response is a large download and a large parse before a single row can be
                // drawn, and almost none of it is on screen when the post opens. Truncated
                // branches come back as "load more" rows, which already exist.
                val json = authorizedGet(
                    "https://oauth.reddit.com/comments/$postId" +
                        "?limit=$COMMENT_PAGE_LIMIT&depth=$COMMENT_PAGE_DEPTH" +
                        "&sort=$normalizedSort&raw_json=1",
                    token,
                )
                if (!isCurrentContentSession(requestSessionGeneration)) return@runCatching
                val root = JSONArray(json)
                val listing = root.optJSONObject(1) ?: throw IOException("Missing comment listing")
                val parsed = mutableListOf<Comment>()
                parseCommentListing(
                    listing = listing,
                    postId = postId,
                    depth = 0,
                    output = parsed,
                )
                currentCoroutineContext().ensureActive()
                synchronized(localStateLock) {
                    if (requestSessionGeneration == contentSessionGeneration) {
                        replaceComments(postId, mergeRemoteComments(postId, parsed))
                    }
                }
            }
        }

    private fun invalidateContentSessionLocked() {
        contentSessionGeneration++
        localPostFlags.clear()
        localPostDrafts.clear()
        savedPostSnapshots.clear()
        localCommentVotes.clear()
        localReplies.clear()
        clearContent()
    }

    private fun isCurrentContentSession(generation: Long): Boolean =
        synchronized(localStateLock) {
            generation == contentSessionGeneration
        }

    private fun mergeRemotePost(post: Post): Post {
        val flags = localPostFlags[post.id].orEmpty()
        val voteState = flags.voteState ?: post.voteState
        val merged = post.copy(
            score = post.score + voteState.scoreDelta - post.voteState.scoreDelta,
            commentCount = post.commentCount + localReplies[post.id].orEmpty().size,
            voteState = voteState,
            isSaved = flags.isSaved ?: post.isSaved,
            isRead = flags.isRead ?: post.isRead,
        )
        rememberPostSnapshot(merged)
        return merged
    }

    private fun mergeRemoteComments(postId: String, comments: List<Comment>): List<Comment> {
        val votes = localCommentVotes[postId].orEmpty()
        val merged = comments.map { comment ->
            val voteState = votes[comment.id] ?: return@map comment
            comment.copy(
                score = comment.score + voteState.scoreDelta - comment.voteState.scoreDelta,
                voteState = voteState,
            )
        }.toMutableList()

        localReplies[postId].orEmpty().values.forEach { storedReply ->
            if (merged.any { comment -> comment.id == storedReply.id }) return@forEach
            val parentIndex = storedReply.parentId?.let { parentId ->
                merged.indexOfFirst { comment -> comment.id == parentId }.takeIf { it >= 0 }
            }
            if (storedReply.parentId == null) {
                merged.add(0, storedReply.copy(depth = 0))
            } else if (parentIndex == null) {
                // Keep the reply visible even if Reddit omitted its parent at this depth/sort.
                merged += storedReply.copy(parentId = null, depth = 0)
            } else {
                val parent = merged[parentIndex]
                var insertionIndex = parentIndex + 1
                while (insertionIndex < merged.size && merged[insertionIndex].depth > parent.depth) {
                    insertionIndex++
                }
                merged.add(insertionIndex, storedReply.copy(depth = parent.depth + 1))
            }
        }
        return merged
    }

    private fun draftsForFeed(feedName: String): List<Post> {
        val normalized = feedName.removePrefix("r/")
        return localPostDrafts.values
            .filter { post ->
                feedName.equals("Home", ignoreCase = true) ||
                    feedName.equals("All", ignoreCase = true) ||
                    post.community.name.equals(normalized, ignoreCase = true)
            }
            .toList()
            .asReversed()
    }

    private fun rememberPostSnapshot(post: Post) {
        if (post.id in localPostDrafts) localPostDrafts[post.id] = post
        if (post.isSaved) {
            savedPostSnapshots[post.id] = post
        } else {
            savedPostSnapshots.remove(post.id)
        }
    }

    private data class LocalPostFlags(
        val voteState: VoteState? = null,
        val isSaved: Boolean? = null,
        val isRead: Boolean? = null,
    )

    private fun LocalPostFlags?.orEmpty(): LocalPostFlags = this ?: LocalPostFlags()

    override fun seedRandomCommunityPool(names: List<String>, harvestedAtMillis: Long) {
        nsfwPool.fill(names, harvestedAtMillis)
    }

    override fun randomCommunityPoolSnapshot(): List<String> = nsfwPool.snapshot()

    override suspend fun randomNsfwCommunity(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // A cached pool turns the second and later taps into no network call at all: the
            // only wait left is loading the community itself.
            if (nsfwPool.needsRefill) {
                nsfwPool.fill(buildAdultPool(oauthManager.accessToken()))
            }

            nsfwPool.draw() ?: throw IOException("No adult communities came back from Reddit")
        }
    }

    /**
     * Builds the pool to draw from: communities Reddit knows about, not ones already followed.
     *
     * The point of the button is to land somewhere new, so subscriptions are deliberately not
     * consulted. Every seed contributes and each is followed past its first page, because one
     * relevance-ranked page is a few dozen communities once the floor has taken its cut — a bag
     * that small is why the button kept returning to the same handful.
     *
     * The harvest runs in two rounds because this endpoint matches names and descriptions, so a
     * fixed word list can only ever reach communities that describe themselves in those words.
     * Round two searches for the names round one found: adult communities cross-reference each
     * other constantly, so their own names are the queries that reach their neighbours — and
     * that keeps working as the communities Reddit surfaces change, which a word list does not.
     *
     * Searches run together within a round, so the cost is roughly two round trips rather than
     * twenty. It is paid on the first tap and then once per pool lifetime.
     */
    private suspend fun buildAdultPool(token: RedditAccessToken): List<String> = coroutineScope {
        val firstRound = NSFW_SEED_QUERIES
            .map { seed -> async { adultSeedResults(seed, token) } }
            .awaitAll()
            .flatten()

        // Sampled rather than taken from the top: the largest communities are the aggregators,
        // and their neighbours are the ones round one already found. Mid-sized names are what
        // reach a corner of the space the word list never named.
        val crossReferences = firstRound
            .filter { it.second >= FALLBACK_RANDOM_SUBSCRIBERS }
            .map { it.first }
            .distinct()
            .shuffled()
            .take(CROSS_REFERENCE_SEEDS)
            .map { seed -> async { adultSeedResults(seed, token, pages = 1) } }
            .awaitAll()
            .flatten()

        // Seeds overlap heavily, so the same community arrives several times over. Keyed on a
        // folded name, the first sighting wins and keeps the casing Reddit displays it with.
        val candidates = linkedMapOf<String, Pair<String, Int>>()
        (firstRound + crossReferences).forEach { candidate ->
            candidates.getOrPut(candidate.first.lowercase(Locale.ROOT)) { candidate }
        }
        if (BuildConfig.DEBUG) {
            // Round two costs a second wait, so it should be visible whether it earns one.
            val fromSeeds = firstRound.map { it.first.lowercase(Locale.ROOT) }.distinct().size
            android.util.Log.d(
                "OtterApi",
                "adult harvest: $fromSeeds from seeds, " +
                    "${candidates.size - fromSeeds} more from cross-references",
            )
        }
        rankedAdultPool(candidates.values)
    }

    /** Walks one seed's search results, following Reddit's cursor until the pages run out. */
    private suspend fun adultSeedResults(
        seed: String,
        token: RedditAccessToken,
        pages: Int = ADULT_POOL_PAGES,
    ): List<Pair<String, Int>> {
        val results = mutableListOf<Pair<String, Int>>()
        var after: String? = null
        repeat(pages) {
            currentCoroutineContext().ensureActive()
            val cursor = after?.let { "&after=${it.urlEncoded()}" }.orEmpty()
            val payload = runCatching {
                JSONObject(
                    authorizedGet(
                        "https://oauth.reddit.com/subreddits/search" +
                            "?q=${seed.urlEncoded()}&limit=100&include_over_18=on" +
                            "&sort=relevance&raw_json=1$cursor",
                        token,
                    ),
                )
            }.getOrNull() ?: return results

            results += adultCommunities(payload)
            after = payload.optJSONObject("data")?.nullableString("after") ?: return results
        }
        return results
    }

    /**
     * Prefers communities anyone would recognise, but does not insist on it.
     *
     * The high floor is what keeps a draw from landing on a community of four hundred people.
     * Applying it to a thin harvest is the other half of what made the button repeat, so it
     * holds only while it can still fill a deck worth dealing.
     */
    private fun rankedAdultPool(candidates: Collection<Pair<String, Int>>): List<String> {
        fun namesAbove(floor: Int) = candidates.filter { it.second >= floor }.map { it.first }

        val preferred = namesAbove(MIN_RANDOM_SUBSCRIBERS)
        val chosen = if (preferred.size >= MIN_RANDOM_POOL) {
            preferred
        } else {
            namesAbove(FALLBACK_RANDOM_SUBSCRIBERS).ifEmpty { preferred }
        }
        if (BuildConfig.DEBUG) {
            // The floors are a judgement about how active a community is; this is the only way
            // to check that judgement against what Reddit actually returns.
            android.util.Log.d(
                "OtterApi",
                "adult pool: ${candidates.size} found, " +
                    "${preferred.size} over $MIN_RANDOM_SUBSCRIBERS, " +
                    "dealing ${chosen.size}",
            )
        }
        return chosen
    }

    private fun adultCommunities(payload: JSONObject): List<Pair<String, Int>> {
        val children = payload.optJSONObject("data")?.optJSONArray("children") ?: return emptyList()
        return (0 until children.length()).mapNotNull { index ->
            val data = children.optJSONObject(index)?.optJSONObject("data") ?: return@mapNotNull null
            val name = data.nullableString("display_name") ?: return@mapNotNull null
            // Public only: a private or restricted community would load into an error page.
            val reachable = data.nullableString("subreddit_type")
                ?.equals("public", ignoreCase = true) != false
            // Quarantined communities are still typed public but answer 403 until the account
            // has opted into them on the web, so landing on one is the same dead end.
            val gated = data.optBoolean("quarantine")
            if (!data.optBoolean("over18") || !reachable || gated) return@mapNotNull null
            name to data.optInt("subscribers").coerceAtLeast(0)
        }
    }

    override suspend fun searchCommunities(query: String, limit: Int): Result<List<Community>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = query.trim()
                if (trimmed.isEmpty()) return@runCatching emptyList()
                val token = oauthManager.accessToken()
                // The endpoint Reddit's own search box uses: prefix matches, ranked by relevance.
                val json = authorizedGet(
                    "https://oauth.reddit.com/api/subreddit_autocomplete_v2" +
                        "?query=${trimmed.urlEncoded()}" +
                        "&limit=${limit.coerceIn(1, 10)}" +
                        "&include_over_18=true&include_profiles=false&typeahead_active=true" +
                        "&raw_json=1",
                    token,
                )
                parseCommunitySuggestions(JSONObject(json), limit)
            }
        }

    private fun parseCommunitySuggestions(payload: JSONObject, limit: Int): List<Community> {
        val children = payload.optJSONObject("data")?.optJSONArray("children") ?: return emptyList()
        return (0 until children.length())
            .mapNotNull { index ->
                val data = children.optJSONObject(index)?.optJSONObject("data") ?: return@mapNotNull null
                val name = data.nullableString("display_name") ?: return@mapNotNull null
                generatedCommunity(name).copy(
                    memberCount = data.optInt("subscribers").coerceAtLeast(0),
                )
            }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .take(limit)
    }

    private fun String.urlEncoded(): String =
        java.net.URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private suspend fun authorizedGet(
        url: String,
        token: RedditAccessToken,
        retryOnUnauthorized: Boolean = true,
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "bearer ${token.value}")
            .header("User-Agent", userAgent)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body.string()
            if (response.code == 401 && retryOnUnauthorized) {
                oauthManager.invalidate(token)
                return authorizedGet(
                    url = url,
                    token = oauthManager.accessToken(),
                    retryOnUnauthorized = false,
                )
            }
            if (response.code == 429) throw rateLimitException(response.header("Retry-After"))
            if (!response.isSuccessful) {
                if (BuildConfig.DEBUG) {
                    // Path only: the token is in a header and never belongs in a log.
                    android.util.Log.d(
                        "OtterApi",
                        "GET ${request.url.encodedPath} -> ${response.code} " +
                            "(after redirects: ${response.request.url.encodedPath})",
                    )
                }
                throw IOException("Reddit request failed (${response.code})")
            }
            return payload
        }
    }

    private suspend fun authorizedPost(
        path: String,
        body: FormBody,
        retryOnUnauthorized: Boolean,
    ): String {
        val token = oauthManager.accessToken()
        val request = Request.Builder()
            .url("https://oauth.reddit.com$path")
            .header("Authorization", "bearer ${token.value}")
            .header("User-Agent", userAgent)
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body.string()
            if (response.code == 401 && retryOnUnauthorized) {
                oauthManager.invalidate(token)
                return authorizedPost(path, body, retryOnUnauthorized = false)
            }
            if (response.code == 429) throw rateLimitException(response.header("Retry-After"))
            if (!response.isSuccessful) throw IOException("Reddit request failed (${response.code})")
            throwIfRedditApiErrors(payload)
            return payload
        }
    }

    private fun throwIfRedditApiErrors(payload: String) {
        val root = payload.trim().takeIf { it.startsWith('{') }?.let(::JSONObject) ?: return
        val errors = root.optJSONObject("json")?.optJSONArray("errors") ?: return
        if (errors.length() == 0) return
        val messages = buildList {
            for (index in 0 until errors.length()) {
                val entry = errors.optJSONArray(index)
                add(entry?.optString(1)?.takeIf(String::isNotBlank) ?: "Reddit rejected the action")
            }
        }
        throw IOException(messages.distinct().joinToString(" · "))
    }

    private fun rateLimitException(retryAfter: String?): IOException {
        val wait = retryAfter?.trim()?.takeIf(String::isNotEmpty)
        return IOException(
            wait?.let {
                it.toLongOrNull()?.let { seconds ->
                    "Reddit rate limit reached; try again in $seconds seconds"
                } ?: "Reddit rate limit reached; Retry-After is $it"
            }
                ?: "Reddit rate limit reached; try again shortly",
        )
    }

    private fun shouldRefreshSubscriptions(): Boolean {
        if (communities.value.none(Community::isFavorite)) return true
        return System.currentTimeMillis() - subscriptionsLoadedAtMillis > SUBSCRIPTION_TTL_MS
    }

    /**
     * The full listing URL for a feed name.
     *
     * Beyond the named feeds and communities, two prefixes address things Reddit exposes as
     * listings of their own: `q/` for a site-wide post search and `u/` for a user's submissions.
     */
    private fun listingUrl(feedName: String, requestedSort: String, timeframe: String?): String {
        val sort = requestedSort.lowercase(Locale.ROOT)
        val communitySort = if (sort == "best") "hot" else sort
        val window = timeframe?.let { "&t=$it" }.orEmpty()
        val base = "https://oauth.reddit.com"
        // Search has its own sort vocabulary; the feed's sort is mapped onto it.
        val searchSort = when (sort) {
            "best" -> "relevance"
            "rising" -> "hot"
            else -> sort
        }

        return when {
            // A search confined to one community. The name cannot contain a slash, so the first
            // one separates it from a query that may well contain several.
            feedName.startsWith(COMMUNITY_SEARCH_PREFIX, ignoreCase = true) -> {
                val scoped = feedName.substring(COMMUNITY_SEARCH_PREFIX.length)
                val community = scoped.substringBefore('/')
                val query = scoped.substringAfter('/', "")
                "$base/r/${community.urlEncoded()}/search?q=${query.urlEncoded()}" +
                    "&restrict_sr=1&type=link&sort=$searchSort" +
                    "&include_over_18=on&limit=$FEED_PAGE_LIMIT&raw_json=1" +
                    (window.ifEmpty { "&t=all" })
            }

            feedName.startsWith(SEARCH_FEED_PREFIX, ignoreCase = true) -> {
                val query = feedName.substring(SEARCH_FEED_PREFIX.length)
                "$base/search?q=${query.urlEncoded()}&type=link&sort=$searchSort" +
                    "&include_over_18=on&limit=$FEED_PAGE_LIMIT&raw_json=1" +
                    (window.ifEmpty { "&t=all" })
            }

            feedName.startsWith(USER_FEED_PREFIX, ignoreCase = true) -> {
                val user = feedName.substring(USER_FEED_PREFIX.length)
                "$base/user/${user.urlEncoded()}/submitted" +
                    "?sort=${if (sort == "best") "new" else sort}" +
                        "&limit=$FEED_PAGE_LIMIT&raw_json=1$window"
            }

            else -> {
                val path = when {
                    feedName.equals("Home", ignoreCase = true) ->
                        if (sort == "best") "/best" else "/$sort"
                    feedName.equals("Popular", ignoreCase = true) -> "/r/popular/$communitySort"
                    feedName.equals("All", ignoreCase = true) -> "/r/all/$communitySort"
                    feedName.startsWith("r/", ignoreCase = true) ->
                        "/${feedName.lowercase(Locale.ROOT)}/$communitySort"
                    else -> "/best"
                }
                "$base$path?limit=$FEED_PAGE_LIMIT&raw_json=1$window"
            }
        }
    }

    private fun parsePosts(root: JSONObject): List<Post> {
        val children = root.optJSONObject("data")?.optJSONArray("children") ?: return emptyList()
        return buildList {
            for (index in 0 until children.length()) {
                val data = children.optJSONObject(index)?.optJSONObject("data") ?: continue
                parsePost(data)?.let(::add)
            }
        }
    }

    private fun parseSubscribedCommunities(root: JSONObject): List<Community> {
        val children = root.optJSONObject("data")?.optJSONArray("children") ?: return emptyList()
        return buildList {
            for (index in 0 until children.length()) {
                val data = children.optJSONObject(index)?.optJSONObject("data") ?: continue
                val name = data.nullableString("display_name") ?: continue
                val generated = generatedCommunity(name)
                add(
                    generated.copy(
                        displayName = data.nullableString("title") ?: generated.displayName,
                        // `community_icon` is the styled one a subreddit picks; `icon_img` is
                        // the older square. Either can be present, empty, or absent.
                        iconUrl = data.nullableString("community_icon")
                            ?.substringBefore('?')
                            ?: data.nullableString("icon_img")?.substringBefore('?'),
                        memberCount = data.optLong("subscribers")
                            .coerceIn(0L, Int.MAX_VALUE.toLong())
                            .toInt(),
                        // Subscribed is what the drawer means by a favourite; merely visiting a
                        // community elsewhere in the app must not earn it a place in that list.
                        isFavorite = true,
                    ),
                )
            }
        }
    }

    private fun parsePost(data: JSONObject): Post? {
        val id = data.nullableString("id") ?: return null
        val title = data.nullableString("title") ?: return null

        val communityName = data.nullableString("subreddit") ?: "reddit"
        val community = communities.value.firstOrNull {
            it.name.equals(communityName, ignoreCase = true)
        } ?: generatedCommunity(communityName)
        val destination = data.nullableString("url_overridden_by_dest")
        val hint = data.nullableString("post_hint")
        val isVideo = data.optBoolean("is_video") || hint == "hosted:video"
        val isGallery = data.optBoolean("is_gallery")
        val isSelf = data.optBoolean("is_self")
        val type = when {
            isVideo -> PostType.VIDEO
            isGallery -> PostType.GALLERY
            isSelf -> PostType.TEXT
            hint == "image" -> PostType.IMAGE
            else -> PostType.LINK
        }
        val image = extractImage(data, type)
        val voteState = (data.opt("likes") as? Boolean)?.let { liked ->
            if (liked) VoteState.UPVOTED else VoteState.DOWNVOTED
        } ?: VoteState.NONE

        return Post(
            id = id,
            community = community,
            title = title,
            author = data.nullableString("author") ?: "[deleted]",
            type = type,
            score = data.optInt("score"),
            commentCount = data.optInt("num_comments").coerceAtLeast(0),
            createdAtEpochSeconds = data.optLong("created_utc").coerceAtLeast(0L),
            domain = data.nullableString("domain"),
            flairText = data.nullableString("link_flair_text")?.take(40),
            destinationUrl = destination,
            body = data.nullableString("selftext"),
            preview = image?.let { preview ->
                PostPreview(
                    assetKey = "reddit_$id",
                    label = when (type) {
                        PostType.VIDEO -> "VIDEO"
                        PostType.GALLERY -> "GALLERY"
                        else -> community.displayName.uppercase(Locale.ROOT).take(14)
                    },
                    startColorArgb = community.accentStartArgb,
                    endColorArgb = community.accentEndArgb,
                    imageUrl = preview.url,
                    thumbnailUrl = preview.thumbnailUrl,
                    cardImageUrl = preview.cardImageUrl,
                    aspectRatio = preview.aspectRatio,
                    altText = title,
                )
            },
            media = RedditMediaParser.parse(data),
            voteState = voteState,
            isSaved = data.optBoolean("saved"),
            isStickied = data.optBoolean("stickied"),
            isNsfw = data.optBoolean("over_18"),
            isSpoiler = data.optBoolean("spoiler"),
        )
    }

    private data class PreviewImage(
        val url: String,
        val thumbnailUrl: String?,
        val cardImageUrl: String?,
        val aspectRatio: Float,
    )

    private fun extractImage(data: JSONObject, type: PostType): PreviewImage? {
        val image = data.optJSONObject("preview")?.optJSONArray("images")?.optJSONObject(0)
        val source = image?.optJSONObject("source")
        val sourceUrl = source?.nullableString("url")?.decodeRedditUrl().orEmpty()
        if (sourceUrl.startsWith("https://")) {
            val width = source?.optInt("width", 16)?.coerceAtLeast(1) ?: 16
            val height = source?.optInt("height", 9)?.coerceAtLeast(1) ?: 9
            return PreviewImage(
                url = sourceUrl,
                // Reddit pre-renders smaller copies; a list row does not need the original.
                thumbnailUrl = image?.let {
                    RedditPreviewSizes.smallestCovering(it, THUMBNAIL_TARGET_WIDTH)
                },
                // A full-bleed card is at most screen-width, which the ladder already covers.
                cardImageUrl = image?.let {
                    RedditPreviewSizes.smallestCovering(it, CARD_TARGET_WIDTH)
                },
                aspectRatio = (width.toFloat() / height).coerceIn(.7f, 2.2f),
            )
        }

        // A gallery usually carries no `preview` block at all: its images live in
        // `media_metadata`, each with its own resolution ladder. Without reading that, a gallery
        // fell through to `thumbnail` below -- a ~140px square, which a full-bleed card then
        // scaled across the whole screen. That is the blur, not the decoder.
        galleryPreview(data)?.let { return it }

        val thumbnail = data.nullableString("thumbnail")?.decodeRedditUrl().orEmpty()
        if (thumbnail.startsWith("https://")) {
            return PreviewImage(thumbnail, thumbnail, thumbnail, 4f / 3f)
        }

        val destination = data.nullableString("url_overridden_by_dest")?.decodeRedditUrl().orEmpty()
        if (type == PostType.IMAGE && destination.startsWith("https://")) {
            return PreviewImage(destination, null, null, 4f / 3f)
        }
        return null
    }


    /** The first usable image of a gallery, at the sizes the feed actually draws it. */
    private fun galleryPreview(data: JSONObject): PreviewImage? {
        val items = data.optJSONObject("gallery_data")?.optJSONArray("items") ?: return null
        val metadata = data.optJSONObject("media_metadata") ?: return null
        // Reddit leaves removed or still-processing items in the list; the card should lead with
        // the first one that has something to show rather than with a hole.
        val entry = (0 until items.length()).firstNotNullOfOrNull { index ->
            items.optJSONObject(index)
                ?.nullableString("media_id")
                ?.let(metadata::optJSONObject)
                ?.takeIf { it.nullableString("status")?.equals("valid", ignoreCase = true) != false }
        } ?: return null

        val source = entry.optJSONObject("s")
        // An animated item stores `gif`/`mp4` under `s` and no still at all, so the ladder is
        // the only place a frame to show exists.
        val full = source?.nullableString("u")?.decodeRedditUrl()?.takeIf { it.startsWith("https://") }
            ?: RedditPreviewSizes.largestGalleryCopy(entry)
            ?: return null
        val width = source?.optInt("x", 0) ?: 0
        val height = source?.optInt("y", 0) ?: 0

        return PreviewImage(
            url = full,
            thumbnailUrl = RedditPreviewSizes.galleryCopyCovering(entry, THUMBNAIL_TARGET_WIDTH),
            cardImageUrl = RedditPreviewSizes.galleryCopyCovering(entry, CARD_TARGET_WIDTH),
            aspectRatio = if (width > 0 && height > 0) {
                (width.toFloat() / height).coerceIn(.7f, 2.2f)
            } else {
                4f / 3f
            },
        )
    }

    /**
     * Reddit truncates deep or long threads and leaves a `more` marker where the rest belongs.
     * A marker with no children is its "continue this thread" form, which needs a different
     * request than this one, so it is dropped rather than shown as an button that cannot work.
     */
    private fun moreStub(data: JSONObject?, postId: String, depth: Int): Comment? {
        val payload = data ?: return null
        val id = payload.nullableString("id") ?: return null
        val childIds = payload.optJSONArray("children")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        if (childIds.isEmpty()) return null

        return Comment(
            id = "more_$id",
            postId = postId,
            parentId = payload.nullableString("parent_id")
                ?.takeIf { it.startsWith("t1_") }
                ?.removePrefix("t1_"),
            depth = depth,
            author = "reddit",
            body = "more",
            score = 0,
            createdAtEpochSeconds = 0L,
            isMoreStub = true,
            moreChildren = childIds,
            moreCount = payload.optInt("count", childIds.size).coerceAtLeast(childIds.size),
        )
    }

    override suspend fun loadMoreComments(
        postId: String,
        stubId: String,
        sort: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestSessionGeneration = synchronized(localStateLock) {
                    contentSessionGeneration
                }
                val existing = comments(postId).value
                val stub = existing.firstOrNull { it.id == stubId && it.isMoreStub }
                    ?: return@runCatching

                // Reddit accepts a bounded batch per call; whatever is left stays behind as a
                // smaller marker so a thousand-reply thread opens in pages instead of one wait.
                val batch = stub.moreChildren.take(MORE_CHILDREN_BATCH)
                val remaining = stub.moreChildren.drop(MORE_CHILDREN_BATCH)

                val body = FormBody.Builder()
                    .add("api_type", "json")
                    .add("link_id", "t3_$postId")
                    .add("children", batch.joinToString(","))
                    .add("sort", commentSortFor(sort))
                    .add("limit_children", "false")
                    .build()
                val json = authorizedPost("/api/morechildren", body, retryOnUnauthorized = true)
                // A disconnect while this was in flight cleared the thread; `existing` is a
                // pre-logout snapshot, so writing it back would restore the whole tree along
                // with the previous account's vote state.
                if (!isCurrentContentSession(requestSessionGeneration)) return@runCatching

                val loaded = parseMoreChildren(JSONObject(json), postId)
                val replacement = if (remaining.isEmpty()) {
                    loaded
                } else {
                    loaded + stub.copy(moreChildren = remaining, moreCount = remaining.size)
                }

                val spliced = existing.flatMap { comment ->
                    if (comment.id == stubId) replacement else listOf(comment)
                }
                currentCoroutineContext().ensureActive()
                synchronized(localStateLock) {
                    if (requestSessionGeneration == contentSessionGeneration) {
                        replaceComments(postId, spliced)
                    }
                }
            }
        }

    /** morechildren answers with a flat list; each entry carries its own depth. */
    /** Reddit calls the default comment sort "confidence" everywhere except in the UI. */
    private fun commentSortFor(sort: String): String = sort.lowercase(Locale.ROOT)
        .let { if (it == "best") "confidence" else it }

    private fun parseMoreChildren(payload: JSONObject, postId: String): List<Comment> {
        val things = payload.optJSONObject("json")
            ?.optJSONObject("data")
            ?.optJSONArray("things")
            ?: return emptyList()

        return (0 until things.length()).mapNotNull { index ->
            val thing = things.optJSONObject(index) ?: return@mapNotNull null
            val data = thing.optJSONObject("data") ?: return@mapNotNull null
            val depth = data.optInt("depth").coerceAtLeast(0)
            when (thing.nullableString("kind")) {
                "more" -> moreStub(data, postId, depth)
                "t1" -> {
                    val body = data.nullableString("body") ?: return@mapNotNull null
                    val id = data.nullableString("id") ?: return@mapNotNull null
                    val voteState = (data.opt("likes") as? Boolean)?.let { liked ->
                        if (liked) VoteState.UPVOTED else VoteState.DOWNVOTED
                    } ?: VoteState.NONE
                    Comment(
                        id = id,
                        postId = postId,
                        parentId = data.nullableString("parent_id")
                            ?.takeIf { it.startsWith("t1_") }
                            ?.removePrefix("t1_"),
                        depth = depth,
                        author = data.nullableString("author") ?: "[deleted]",
                        body = body,
                        score = data.optInt("score"),
                        createdAtEpochSeconds = data.optLong("created_utc").coerceAtLeast(0L),
                        voteState = voteState,
                        authorFlair = data.authorFlair(),
                        isSubmitter = data.optBoolean("is_submitter"),
                        isDistinguished = data.nullableString("distinguished") != null,
                        isEdited = data.opt("edited")
                            .let { it != null && it !== JSONObject.NULL && it != false },
                    )
                }
                else -> null
            }
        }
    }

    private fun parseCommentListing(
        listing: JSONObject,
        postId: String,
        depth: Int,
        output: MutableList<Comment>,
    ) {
        val children = listing.optJSONObject("data")?.optJSONArray("children") ?: return
        for (index in 0 until children.length()) {
            val child = children.optJSONObject(index) ?: continue
            val kind = child.nullableString("kind")
            if (kind == "more") {
                moreStub(child.optJSONObject("data"), postId, depth)?.let(output::add)
                continue
            }
            if (kind != "t1") continue
            val data = child.optJSONObject("data") ?: continue
            val body = data.nullableString("body") ?: continue
            val id = data.nullableString("id") ?: continue
            val parent = data.nullableString("parent_id")
                ?.takeIf { it.startsWith("t1_") }
                ?.removePrefix("t1_")
            val voteState = (data.opt("likes") as? Boolean)?.let { liked ->
                if (liked) VoteState.UPVOTED else VoteState.DOWNVOTED
            } ?: VoteState.NONE
            output += Comment(
                id = id,
                postId = postId,
                parentId = parent,
                depth = depth,
                author = data.nullableString("author") ?: "[deleted]",
                body = body,
                score = data.optInt("score"),
                createdAtEpochSeconds = data.optLong("created_utc").coerceAtLeast(0L),
                voteState = voteState,
                authorFlair = data.authorFlair(),
                isSubmitter = data.optBoolean("is_submitter"),
                isDistinguished = data.nullableString("distinguished") != null,
                isEdited = data.opt("edited").let { it != null && it !== JSONObject.NULL && it != false },
            )
            val replies = data.opt("replies")
            if (replies is JSONObject) {
                parseCommentListing(replies, postId, depth + 1, output)
            }
        }
    }

    private fun generatedCommunity(name: String): Community {
        val palette = COMMUNITY_PALETTE
        val index = Math.floorMod(name.lowercase(Locale.ROOT).hashCode(), palette.size)
        val colors = palette[index]
        return Community(
            name = name,
            displayName = name.replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase(Locale.ROOT) else character.toString()
            },
            memberCount = 0,
            accentStartArgb = colors.first,
            accentEndArgb = colors.second,
        )
    }

    private fun String.decodeRedditUrl(): String =
        replace("&amp;", "&").replace("&#x2F;", "/")

    private val VoteState.redditDirection: Int
        get() = when (this) {
            VoteState.UPVOTED -> 1
            VoteState.NONE -> 0
            VoteState.DOWNVOTED -> -1
        }

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf(String::isNotEmpty)
    }

    private companion object {
        /** Posts per listing request. Reddit caps this at 100. */
        const val FEED_PAGE_LIMIT = 75

        /**
         * How much of a thread the first request asks for; the rest expands on demand.
         *
         * A balance rather than a maximum. The original 200/12 was a large download and a large
         * parse before a single comment could be drawn; 60/8 was quick but ran out too early on
         * a busy post. Truncated branches still come back as "load more" rows.
         */
        const val COMMENT_PAGE_LIMIT = 120
        const val COMMENT_PAGE_DEPTH = 10

        /**
         * The floor's job is to skip communities that are effectively dead — a handful of posts
         * a year, nothing loaded when you arrive — not to keep the draw famous. Set too high it
         * works against the button: the largest adult communities are the aggregators everyone
         * has already seen, so a strict floor makes "random" land on the familiar. Activity is
         * what matters and subscriber count is the only proxy the search endpoint offers.
         */
        const val MIN_RANDOM_SUBSCRIBERS = 25_000

        /** Relaxed floor, used only when the preferred one cannot fill a deck. */
        const val FALLBACK_RANDOM_SUBSCRIBERS = 5_000

        /** Below this the deal comes back around fast enough to notice. */
        const val MIN_RANDOM_POOL = 40

        /** Two pages a seed: past that, relevance ranking has stopped being about the seed. */
        const val ADULT_POOL_PAGES = 2
        const val MORE_CHILDREN_BATCH = 100
        const val SUBSCRIPTION_TTL_MS = 15 * 60 * 1000L
        const val SEARCH_FEED_PREFIX = "q/"

        /** `qr/<community>/<query>` — a search restricted to one community. */
        const val COMMUNITY_SEARCH_PREFIX = "qr/"
        const val USER_FEED_PREFIX = "u/"
        /** Wide enough for a list thumbnail on a dense screen, far short of a full-size image. */
        const val THUMBNAIL_TARGET_WIDTH = 320

        /** Reddit's preview ladder tops out at 1080, so this takes the widest rung it offers. */
        const val CARD_TARGET_WIDTH = 1080

        /**
         * Round-one queries.
         *
         * Each word finds a different slice rather than a different ranking of one slice, since
         * the endpoint matches names and descriptions: a community calling itself "amateur" and
         * one calling itself "cosplay" do not turn up in each other's results. Four generic
         * words returned four orderings of the same corner of Reddit.
         *
         * Terms that would bias the crawl toward content sexualising minors are deliberately
         * absent and should stay absent — the omission is the filter, and adding one back to
         * "widen the pool" would defeat it.
         */
        val NSFW_SEED_QUERIES = listOf(
            "nsfw",
            "adult",
            "over18",
            "porn",
            "gonewild",
            "amateur",
            "cosplay",
        )

        /** How many of round one's communities are searched for in round two. */
        const val CROSS_REFERENCE_SEEDS = 6

        val COMMUNITY_PALETTE = listOf(
            0xFF4EA7F5L to 0xFF154A72L,
            0xFF5BCF8AL to 0xFF1C6040L,
            0xFFFF8B5CL to 0xFF7D3822L,
            0xFF9B8AFBL to 0xFF40347DL,
            0xFFE7B84BL to 0xFF70571CL,
        )
    }
}
