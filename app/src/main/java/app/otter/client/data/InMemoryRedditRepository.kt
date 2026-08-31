package app.otter.client.data

import app.otter.client.model.Comment
import app.otter.client.model.Community
import app.otter.client.model.Post
import app.otter.client.model.PostType
import app.otter.client.model.SubmissionKind
import app.otter.client.model.VoteState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fixture repository used by tests and local previews.
 *
 * All updates are copy-on-write and synchronized, so collectors always receive
 * immutable snapshots and score changes cannot drift under concurrent input.
 */
open class InMemoryRedditRepository(
    initialPosts: List<Post> = DemoRedditContent.posts(),
    initialComments: Map<String, List<Comment>> = DemoRedditContent.commentsByPost(),
    initialCommunities: List<Community> = DemoRedditContent.communities(),
) : RedditRepository {
    private val lock = Any()
    private val originalPosts = initialPosts.toList()
    private val originalComments = initialComments.mapValues { (_, comments) -> comments.toList() }
    private var localPostSequence = 1L
    private var localCommentSequence = 1L

    private val mutableFeed = MutableStateFlow(originalPosts)
    override val feed: StateFlow<List<Post>> = mutableFeed.asStateFlow()

    private val mutableCommunities = MutableStateFlow(initialCommunities.toList())
    override val communities: StateFlow<List<Community>> = mutableCommunities.asStateFlow()

    private val commentFlows: MutableMap<String, MutableStateFlow<List<Comment>>> =
        originalComments
            .mapValuesTo(linkedMapOf()) { (_, comments) -> MutableStateFlow(comments) }

    override fun post(postId: String): Post? =
        feed.value.firstOrNull { post -> post.id == postId }

    override fun comments(postId: String): StateFlow<List<Comment>> = synchronized(lock) {
        commentFlows.getOrPut(postId) { MutableStateFlow(emptyList()) }.asStateFlow()
    }

    open override fun submitPost(
        communityName: String,
        title: String,
        body: String,
        kind: SubmissionKind,
        linkUrl: String,
    ): Post {
        val normalizedCommunity = communityName.trim().removePrefix("r/")
        val normalizedTitle = title.trim()
        val normalizedUrl = normalizeSubmissionUrl(linkUrl)
        require(normalizedCommunity.isNotEmpty()) { "Community name cannot be blank" }
        require(normalizedTitle.isNotEmpty()) { "Post title cannot be blank" }
        require(kind != SubmissionKind.LINK || normalizedUrl != null) {
            "A link post needs a web address"
        }

        synchronized(lock) {
            val community = communities.value.firstOrNull { candidate ->
                candidate.name.equals(normalizedCommunity, ignoreCase = true)
            } ?: throw IllegalArgumentException("Unknown community: $communityName")
            val sequence = localPostSequence++
            val post = Post(
                id = "local_post_$sequence",
                community = community,
                title = normalizedTitle,
                author = "you",
                type = if (kind == SubmissionKind.LINK) PostType.LINK else PostType.TEXT,
                score = 1,
                commentCount = 0,
                createdAtEpochSeconds = System.currentTimeMillis() / 1_000L,
                domain = normalizedUrl?.let(::submissionUrlHost),
                destinationUrl = normalizedUrl,
                // A link post has no self text, so the body Reddit would show is nothing at all.
                body = body.trim().takeIf { it.isNotEmpty() && kind == SubmissionKind.TEXT },
                voteState = VoteState.UPVOTED,
                isRead = true,
            )
            mutableFeed.value = listOf(post) + mutableFeed.value
            commentFlows[post.id] = MutableStateFlow(emptyList())
            return post
        }
    }

    open override fun submitComment(postId: String, parentId: String?, body: String): Comment {
        val normalizedBody = body.trim()
        require(normalizedBody.isNotEmpty()) { "Comment body cannot be blank" }

        synchronized(lock) {
            val flow = commentFlows[postId] ?: throw IllegalArgumentException("Unknown post: $postId")
            val parent = parentId?.let { id ->
                flow.value.firstOrNull { it.id == id }
                    ?: throw IllegalArgumentException("Unknown parent comment: $id")
            }
            val sequence = localCommentSequence++
            val comment = Comment(
                id = "local_comment_$sequence",
                postId = postId,
                parentId = parentId,
                depth = (parent?.depth ?: -1) + 1,
                author = "you",
                body = normalizedBody,
                score = 1,
                createdAtEpochSeconds = System.currentTimeMillis() / 1_000L,
                voteState = VoteState.UPVOTED,
                isSubmitter = post(postId)?.author == "you",
            )

            flow.value = if (parent == null) {
                listOf(comment) + flow.value
            } else {
                val parentIndex = flow.value.indexOfFirst { it.id == parent.id }
                var insertionIndex = parentIndex + 1
                while (insertionIndex < flow.value.size && flow.value[insertionIndex].depth > parent.depth) {
                    insertionIndex++
                }
                flow.value.toMutableList().apply { add(insertionIndex, comment) }
            }
            mutatePost(postId) { it.copy(commentCount = it.commentCount + 1) }
            return comment
        }
    }

    open override fun togglePostVote(postId: String, vote: VoteState): Boolean {
        require(vote != VoteState.NONE) { "Use an upvote or downvote as the toggle target" }

        return mutatePost(postId) { post ->
            val nextVote = if (post.voteState == vote) VoteState.NONE else vote
            post.copy(
                voteState = nextVote,
                score = post.score + nextVote.scoreDelta - post.voteState.scoreDelta,
            )
        }
    }

    open override fun toggleCommentVote(
        postId: String,
        commentId: String,
        vote: VoteState,
    ): Boolean {
        require(vote != VoteState.NONE) { "Use an upvote or downvote as the toggle target" }

        synchronized(lock) {
            val flow = commentFlows[postId] ?: return false
            var wasFound = false
            val updated = flow.value.map { comment ->
                if (comment.id != commentId) {
                    comment
                } else {
                    wasFound = true
                    val nextVote = if (comment.voteState == vote) VoteState.NONE else vote
                    comment.copy(
                        voteState = nextVote,
                        score = comment.score + nextVote.scoreDelta - comment.voteState.scoreDelta,
                    )
                }
            }
            if (wasFound) flow.value = updated
            return wasFound
        }
    }

    open override fun toggleSaved(postId: String): Boolean =
        mutatePost(postId) { post -> post.copy(isSaved = !post.isSaved) }

    open override fun markRead(postId: String, isRead: Boolean): Boolean =
        mutatePost(postId) { post -> post.copy(isRead = isRead) }

    /** One pass over the feed and one emission, however many posts are being marked. */
    open override fun markRead(postIds: Collection<String>, isRead: Boolean): List<Post> {
        if (postIds.isEmpty()) return emptyList()
        val wanted = postIds.toHashSet()
        synchronized(lock) {
            val changed = mutableListOf<Post>()
            val updated = mutableFeed.value.map { post ->
                if (post.id !in wanted || post.isRead == isRead) {
                    post
                } else {
                    post.copy(isRead = isRead).also(changed::add)
                }
            }
            if (changed.isNotEmpty()) mutableFeed.value = updated
            return changed
        }
    }

    /** Restores the constructor fixtures. Useful for previews and deterministic tests. */
    fun reset() {
        synchronized(lock) {
            mutableFeed.value = originalPosts
            localPostSequence = 1L
            localCommentSequence = 1L
            val allPostIds = commentFlows.keys + originalComments.keys
            allPostIds.forEach { postId ->
                commentFlows.getOrPut(postId) { MutableStateFlow(emptyList()) }.value =
                    originalComments[postId].orEmpty()
            }
        }
    }

    private inline fun mutatePost(postId: String, transform: (Post) -> Post): Boolean {
        synchronized(lock) {
            var wasFound = false
            val updated = mutableFeed.value.map { post ->
                if (post.id != postId) {
                    post
                } else {
                    wasFound = true
                    transform(post)
                }
            }
            if (wasFound) mutableFeed.value = updated
            return wasFound
        }
    }

    protected fun replaceFeed(posts: List<Post>) {
        synchronized(lock) {
            mutableFeed.value = posts.toList()
        }
    }

    protected fun replaceComments(postId: String, comments: List<Comment>) {
        synchronized(lock) {
            commentFlows.getOrPut(postId) { MutableStateFlow(emptyList()) }.value = comments.toList()
        }
    }

    protected fun clearContent() {
        synchronized(lock) {
            mutableFeed.value = emptyList()
            mutableCommunities.value = emptyList()
            commentFlows.values.forEach { comments -> comments.value = emptyList() }
        }
    }

    /** Adds newly discovered communities without discarding useful fixture metadata. */
    override fun restoreFeed(posts: List<Post>) = replaceFeed(posts)

    override fun restoreComments(postId: String, comments: List<Comment>) =
        replaceComments(postId, comments)

    /** Adds a page to the end of the feed, ignoring anything already shown. */
    protected fun appendFeed(posts: List<Post>) {
        synchronized(lock) {
            val existing = mutableFeed.value
            val known = existing.mapTo(mutableSetOf(), Post::id)
            mutableFeed.value = existing + posts.filterNot { it.id in known }
        }
    }

    protected fun mergeCommunities(communities: List<Community>) {
        synchronized(lock) {
            val merged = mutableCommunities.value.toMutableList()
            communities.forEach { community ->
                val index = merged.indexOfFirst { existing ->
                    existing.name.equals(community.name, ignoreCase = true)
                }
                if (index < 0) {
                    merged += community
                    return@forEach
                }
                // The same community arrives from two places: the subscription list, which knows
                // it is subscribed and carries its real title, and posts in a feed, which do not.
                // Whichever lands second, the subscribed facts win and are never overwritten.
                val existing = merged[index]
                merged[index] = existing.copy(
                    isFavorite = existing.isFavorite || community.isFavorite,
                    displayName = if (community.isFavorite) {
                        community.displayName
                    } else {
                        existing.displayName
                    },
                    memberCount = maxOf(existing.memberCount, community.memberCount),
                    // Only the subscription listing carries styling; a community seen in a post
                    // has no icon and must not erase one already known.
                    iconUrl = community.iconUrl ?: existing.iconUrl,
                )
            }
            mutableCommunities.value = merged
        }
    }

    protected fun removeLocalThing(fullname: String) {
        synchronized(lock) {
            when {
                fullname.startsWith("t3_") -> {
                    val id = fullname.removePrefix("t3_")
                    mutableFeed.value = mutableFeed.value.filterNot { it.id == id }
                    commentFlows.remove(id)
                }
                fullname.startsWith("t1_") -> {
                    val id = fullname.removePrefix("t1_")
                    commentFlows.values.forEach { flow ->
                        flow.value = flow.value.filterNot { it.id == id }
                    }
                }
            }
        }
    }

    protected fun editLocalThing(fullname: String, text: String) {
        synchronized(lock) {
            when {
                fullname.startsWith("t3_") -> mutatePost(fullname.removePrefix("t3_")) {
                    it.copy(body = text)
                }
                fullname.startsWith("t1_") -> {
                    val id = fullname.removePrefix("t1_")
                    commentFlows.values.forEach { flow ->
                        flow.value = flow.value.map { comment ->
                            if (comment.id == id) comment.copy(body = text, isEdited = true) else comment
                        }
                    }
                }
            }
        }
    }

    protected fun updateCommunitySubscription(name: String, subscribed: Boolean) {
        synchronized(lock) {
            mutableCommunities.value = mutableCommunities.value.map { community ->
                if (community.name.equals(name, ignoreCase = true)) {
                    community.copy(isFavorite = subscribed)
                } else {
                    community
                }
            }
        }
    }
}
