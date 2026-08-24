package app.otter.client.data

import app.otter.client.data.oauth.RedditApiConfiguration
import app.otter.client.model.PostType
import app.otter.client.model.VoteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import app.otter.client.model.Community
import org.junit.Test

class InMemoryRedditRepositoryTest {
    @Test
    fun demoFeed_isDeterministicAndCoversEveryPostType() {
        val first = InMemoryRedditRepository().feed.value
        val second = InMemoryRedditRepository().feed.value

        assertEquals(first, second)
        assertEquals(8, first.size)
        assertEquals(PostType.entries.toSet(), first.map { post -> post.type }.toSet())
        assertEquals(first.size, first.map { post -> post.id }.toSet().size)
        assertTrue(first.all { post -> post.community.name.isNotBlank() })
    }

    @Test
    fun togglePostVote_updatesScoreAndDoesNotDrift() {
        val repository = InMemoryRedditRepository()
        val postId = "post_android_satellite"
        val originalScore = repository.post(postId)!!.score

        assertTrue(repository.togglePostVote(postId, VoteState.UPVOTED))
        assertEquals(VoteState.UPVOTED, repository.post(postId)!!.voteState)
        assertEquals(originalScore + 1, repository.post(postId)!!.score)

        assertTrue(repository.togglePostVote(postId, VoteState.UPVOTED))
        assertEquals(VoteState.NONE, repository.post(postId)!!.voteState)
        assertEquals(originalScore, repository.post(postId)!!.score)

        assertTrue(repository.togglePostVote(postId, VoteState.DOWNVOTED))
        assertEquals(originalScore - 1, repository.post(postId)!!.score)

        assertTrue(repository.togglePostVote(postId, VoteState.UPVOTED))
        assertEquals(VoteState.UPVOTED, repository.post(postId)!!.voteState)
        assertEquals(originalScore + 1, repository.post(postId)!!.score)
    }

    @Test
    fun markingASetOfPostsRead_publishesOneFeedUpdateForTheWholeSet() {
        val repository = InMemoryRedditRepository()
        val ids = repository.feed.value.take(5).map { post -> post.id }
        // One of the fixtures ships already read, and a post that is already in the requested
        // state is not a change: it should be neither reported nor republished.
        val expected = repository.feed.value
            .filter { post -> post.id in ids && !post.isRead }
            .map { post -> post.id }
            .toSet()
        var updates = 0
        val collector = CoroutineScope(Dispatchers.Unconfined).launch {
            repository.feed.collect { updates++ }
        }
        // The initial value counts as one emission; only what follows is the cost of marking.
        assertEquals(1, updates)

        val changed = repository.markRead(ids)

        assertEquals(expected, changed.map { post -> post.id }.toSet())
        assertTrue(expected.isNotEmpty())
        assertTrue(repository.feed.value.filter { it.id in ids }.all { it.isRead })
        // One update, not one per post: the whole point of the batch overload.
        assertEquals(2, updates)

        // Nothing changes state, so nothing is published either.
        assertTrue(repository.markRead(ids).isEmpty())
        assertEquals(2, updates)
        collector.cancel()
    }

    @Test
    fun markingASetRead_ignoresUnknownIdsAndCanUnmark() {
        val repository = InMemoryRedditRepository()
        val known = repository.feed.value.first().id

        assertEquals(listOf(known), repository.markRead(listOf(known, "no_such_post")).map { it.id })
        assertTrue(repository.post(known)!!.isRead)

        assertEquals(listOf(known), repository.markRead(listOf(known), isRead = false).map { it.id })
        assertFalse(repository.post(known)!!.isRead)
        assertTrue(repository.markRead(emptyList()).isEmpty())
    }

    @Test
    fun saveAndReadMutations_areObservableAndUnknownIdsAreSafe() {
        val repository = InMemoryRedditRepository()
        val postId = "post_android_satellite"

        assertFalse(repository.post(postId)!!.isSaved)
        assertTrue(repository.toggleSaved(postId))
        assertTrue(repository.feed.value.first { it.id == postId }.isSaved)

        assertFalse(repository.post(postId)!!.isRead)
        assertTrue(repository.markRead(postId))
        assertTrue(repository.post(postId)!!.isRead)
        assertTrue(repository.markRead(postId, isRead = false))
        assertFalse(repository.post(postId)!!.isRead)

        assertFalse(repository.toggleSaved("missing"))
        assertFalse(repository.markRead("missing"))
        assertFalse(repository.togglePostVote("missing", VoteState.UPVOTED))
        assertEquals(8, repository.feed.value.size)
    }

    @Test
    fun commentVotes_mutateOnlyTheRequestedThreadAndResetRestoresFixtures() {
        val repository = InMemoryRedditRepository()
        val postId = "post_android_satellite"
        val commentId = "comment_android_1"
        val original = repository.comments(postId).value.first { it.id == commentId }

        assertTrue(repository.toggleCommentVote(postId, commentId, VoteState.UPVOTED))
        val voted = repository.comments(postId).value.first { it.id == commentId }
        assertEquals(VoteState.UPVOTED, voted.voteState)
        assertEquals(original.score + 1, voted.score)
        assertTrue(repository.comments(postId).value.any { it.depth == 2 })

        assertFalse(repository.toggleCommentVote(postId, "missing", VoteState.UPVOTED))
        assertFalse(repository.toggleCommentVote("missing", commentId, VoteState.UPVOTED))
        assertTrue(repository.comments("missing").value.isEmpty())

        repository.reset()
        assertEquals(original, repository.comments(postId).value.first { it.id == commentId })
    }

    @Test
    fun noneCannotBeUsedAsAToggleTarget() {
        val repository = InMemoryRedditRepository()

        try {
            repository.togglePostVote("post_android_satellite", VoteState.NONE)
            fail("Expected an IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected: NONE is produced by toggling an already active direction.
        }

        assertNotNull(repository.post("post_android_satellite"))
    }

    @Test
    fun submitPost_prependsAnAutoUpvotedLocalTextPost() {
        val repository = InMemoryRedditRepository()

        val submitted = repository.submitPost(
            communityName = "r/android",
            title = "  A small offline post  ",
            body = "  Composed without a network connection.  ",
        )

        assertEquals(submitted, repository.feed.value.first())
        assertEquals("local_post_1", submitted.id)
        assertEquals("A small offline post", submitted.title)
        assertEquals("Composed without a network connection.", submitted.body)
        assertEquals(VoteState.UPVOTED, submitted.voteState)
        assertEquals(1, submitted.score)
        assertTrue(submitted.isRead)
        assertTrue(repository.comments(submitted.id).value.isEmpty())
    }

    @Test
    fun submitComment_insertsReplyAndUpdatesCount() {
        val repository = InMemoryRedditRepository()
        val postId = "post_android_satellite"
        val originalCount = repository.post(postId)!!.commentCount

        val reply = repository.submitComment(postId, "comment_android_1", "A local reply")

        assertEquals(1, reply.depth)
        assertEquals("comment_android_1", reply.parentId)
        assertEquals(VoteState.UPVOTED, reply.voteState)
        assertEquals(originalCount + 1, repository.post(postId)!!.commentCount)
        assertTrue(repository.comments(postId).value.any { it.id == reply.id })
    }

    @Test
    fun liveRepositoryStartsBlankAndRequiresAnAccountForReads() = runBlocking {
        val repository = RedditApiRepository(
            configuration = RedditApiConfiguration(
                clientId = "test-client",
                userAgent = "android:app.orca.client:test (by /u/test)",
            ),
        )

        assertTrue(repository.feed.value.isEmpty())
        assertTrue(repository.communities.value.isEmpty())
        assertTrue(repository.refresh("Home", "Best").isFailure)
        assertTrue(repository.feed.value.isEmpty())
    }

    @Test
    fun communitySearchMatchesPartialNamesAndHonoursTheLimit() = runBlocking {
        val repository = InMemoryRedditRepository()
        val known = repository.communities.value.first().name

        val exact = repository.searchCommunities(known, limit = 3).getOrThrow()
        assertEquals(known, exact.first().name)

        // A partial, differently cased fragment still matches.
        val partial = repository
            .searchCommunities(known.substring(0, 2).uppercase(), limit = 3)
            .getOrThrow()
        assertTrue(partial.isNotEmpty())
        assertTrue(partial.size <= 3)

        assertTrue(repository.searchCommunities("zzzznotacommunity", limit = 3).getOrThrow().isEmpty())
    }

    @Test
    fun visitingACommunityDoesNotPromoteItIntoTheSubscribedList() {
        val repository = MergeableRepository()
        val visited = Community(
            name = "kotlin",
            displayName = "kotlin",
            memberCount = 0,
            accentStartArgb = 0xFF102030,
            accentEndArgb = 0xFF405060,
        )

        repository.merge(listOf(visited))
        val stored = repository.communities.value.first { it.name == "kotlin" }

        assertFalse(stored.isFavorite)
    }

    @Test
    fun subscriptionFactsSurviveWhicheverOrderTheyArriveIn() {
        val seen = Community(
            name = "kotlin",
            displayName = "kotlin",
            memberCount = 0,
            accentStartArgb = 0xFF102030,
            accentEndArgb = 0xFF405060,
        )
        val subscribed = seen.copy(
            displayName = "Kotlin Programming",
            memberCount = 1_200,
            isFavorite = true,
        )

        val subscribedFirst = MergeableRepository().apply {
            merge(listOf(subscribed))
            merge(listOf(seen))
        }
        val seenFirst = MergeableRepository().apply {
            merge(listOf(seen))
            merge(listOf(subscribed))
        }

        listOf(subscribedFirst, seenFirst).forEach { repository ->
            val stored = repository.communities.value.first { it.name == "kotlin" }
            assertTrue(stored.isFavorite)
            assertEquals("Kotlin Programming", stored.displayName)
            assertEquals(1_200, stored.memberCount)
        }
    }

    /** mergeCommunities is protected for subclasses like the live repository to call. */
    private class MergeableRepository : InMemoryRedditRepository(
        initialPosts = emptyList(),
        initialComments = emptyMap(),
        initialCommunities = emptyList(),
    ) {
        fun merge(communities: List<Community>) = mergeCommunities(communities)
    }
}
