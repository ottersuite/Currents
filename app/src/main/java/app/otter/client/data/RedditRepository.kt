package app.otter.client.data

import app.otter.client.model.Comment
import app.otter.client.model.Community
import app.otter.client.model.Post
import app.otter.client.model.RedditAccount
import app.otter.client.model.RedditAccountState
import app.otter.client.model.VoteState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val unavailableAccountState =
    MutableStateFlow<RedditAccountState>(RedditAccountState.Unavailable).asStateFlow()

/**
 * Data boundary consumed by screen-level view models.
 *
 * Vote functions use toggle semantics: applying the active vote again clears
 * it, while applying the opposite vote switches directly to that vote.
 */
interface RedditRepository {
    val feed: StateFlow<List<Post>>
    val communities: StateFlow<List<Community>>
    val accountState: StateFlow<RedditAccountState>
        get() = unavailableAccountState
    val isLive: Boolean
        get() = false

    fun post(postId: String): Post?

    fun comments(postId: String): StateFlow<List<Comment>>

    fun submitPost(communityName: String, title: String, body: String): Post

    fun submitComment(postId: String, parentId: String?, body: String): Comment

    fun togglePostVote(postId: String, vote: VoteState): Boolean

    fun toggleCommentVote(postId: String, commentId: String, vote: VoteState): Boolean

    fun toggleSaved(postId: String): Boolean

    fun markRead(postId: String, isRead: Boolean = true): Boolean

    /**
     * Applies read state to a whole set of posts, returning the ones that actually changed.
     *
     * The single-post [markRead] publishes a new feed snapshot per call, so marking a screenful
     * one at a time costs a full-list copy, an emission and a recomposition *per post*. Any
     * implementation that owns the feed should do the set in one pass instead.
     */
    fun markRead(postIds: Collection<String>, isRead: Boolean = true): List<Post> =
        postIds.mapNotNull { postId -> if (markRead(postId, isRead)) post(postId) else null }

    /**
     * Picks an adult community at random. oauth.reddit.com does not serve the r/randnsfw
     * redirect that the website uses, so the choice is made client-side from a search pool.
     */
    suspend fun randomNsfwCommunity(): Result<String> =
        Result.failure(IllegalStateException("Connect your Reddit account to browse communities"))

    /** Publishes a previously loaded feed as-is. Used to return to a feed without refetching. */
    fun restoreFeed(posts: List<Post>) = Unit

    /** Publishes cached comments while a fresh thread is fetched in the background. */
    fun restoreComments(postId: String, comments: List<Comment>) = Unit

    suspend fun refresh(
        feedName: String,
        sort: String,
        timeframe: String? = null,
    ): Result<Unit> = Result.success(Unit)

    suspend fun loadComments(postId: String, sort: String): Result<Unit> = Result.success(Unit)

    /** Expands one truncated branch of a comment thread, replacing its placeholder. */
    suspend fun loadMoreComments(
        postId: String,
        stubId: String,
        sort: String,
    ): Result<Unit> = Result.success(Unit)

    /**
     * Appends the next page of the current feed, returning whether more pages remain after it.
     * A repository with nothing further to load simply reports false.
     */
    suspend fun loadMoreFeed(
        feedName: String,
        sort: String,
        timeframe: String? = null,
    ): Result<Boolean> = Result.success(false)

    /**
     * Communities matching a partial name, best match first. Offline repositories answer from
     * what they already hold, so callers get the same shape whether or not an account is live.
     */
    suspend fun searchCommunities(query: String, limit: Int): Result<List<Community>> =
        Result.success(
            communities.value
                .filter { it.name.contains(query.trim(), ignoreCase = true) }
                .take(limit),
        )

    fun beginAccountAuthorization(): Result<String> =
        Result.failure(IllegalStateException("Add a Reddit client ID to connect an account"))

    fun cancelAccountAuthorization() = Unit

    suspend fun completeAccountAuthorization(callbackUrl: String): Result<RedditAccount> =
        Result.failure(IllegalStateException("Reddit account connection is unavailable"))

    suspend fun disconnectAccount(): Result<Unit> = Result.success(Unit)

    /** Retries any sign-out whose token revocation could not reach Reddit at the time. */
    suspend fun retryPendingAccountRevocations(): Result<Unit> = Result.success(Unit)

    suspend fun applyPostVote(postId: String, vote: VoteState): Result<Boolean> =
        runCatching { togglePostVote(postId, vote) }

    suspend fun applyCommentVote(
        postId: String,
        commentId: String,
        vote: VoteState,
    ): Result<Boolean> = runCatching { toggleCommentVote(postId, commentId, vote) }

    suspend fun applySaved(postId: String): Result<Boolean> =
        runCatching { toggleSaved(postId) }

    suspend fun applyHidden(postId: String, hidden: Boolean): Result<Unit> =
        Result.failure(IllegalStateException("Reddit account action is unavailable"))

    suspend fun reportThing(fullname: String, reason: String): Result<Unit> =
        Result.failure(IllegalStateException("Reddit reporting is unavailable"))

    suspend fun blockUser(username: String): Result<Unit> =
        Result.failure(IllegalStateException("Reddit blocking is unavailable"))

    suspend fun deleteThing(fullname: String): Result<Unit> =
        Result.failure(IllegalStateException("Reddit editing is unavailable"))

    suspend fun editThing(fullname: String, text: String): Result<Unit> =
        Result.failure(IllegalStateException("Reddit editing is unavailable"))

    suspend fun setCommunitySubscription(communityName: String, subscribed: Boolean): Result<Unit> =
        Result.failure(IllegalStateException("Reddit subscriptions are unavailable"))

    suspend fun publishPost(communityName: String, title: String, body: String): Result<Unit> =
        runCatching { submitPost(communityName, title, body) }.map { }

    suspend fun publishComment(postId: String, parentId: String?, body: String): Result<Unit> =
        runCatching { submitComment(postId, parentId, body) }.map { }
}
