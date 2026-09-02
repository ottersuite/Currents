package app.otter.client.data

import app.otter.client.data.oauth.InMemoryRedditOAuthStore
import app.otter.client.data.oauth.RedditApiConfiguration
import app.otter.client.data.oauth.RedditOAuthManager
import app.otter.client.data.oauth.credentialStorageKey
import app.otter.client.model.RedditOAuthCredential
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RedditApiRepositorySessionTest {
    @Test
    fun inboxLoadsRepliesAndPrivateMessagesWithUnreadState() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val repository = signedInRepository(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    requestedUrls += chain.request().url.toString()
                    jsonResponse(
                        chain.request(),
                        """
                            {
                              "data": {
                                "children": [
                                  {
                                    "kind": "t4",
                                    "data": {
                                      "id": "private-1",
                                      "name": "t4_private1",
                                      "author": "orca-friend",
                                      "subject": "Hello from Reddit",
                                      "body": "Want to compare reader setups?",
                                      "created_utc": 1700000000,
                                      "new": true,
                                      "was_comment": false
                                    }
                                  },
                                  {
                                    "kind": "t1",
                                    "data": {
                                      "id": "reply-1",
                                      "name": "t1_reply1",
                                      "author": "helpful-user",
                                      "subject": "comment reply",
                                      "body": "This worked for me too.",
                                      "created_utc": 1699999900,
                                      "new": false,
                                      "was_comment": true,
                                      "context": "/r/android/comments/post/thread/reply1"
                                    }
                                  }
                                ]
                              }
                            }
                        """.trimIndent(),
                    )
                }
                .build(),
        )

        assertTrue(repository.refreshMessages().isSuccess)

        assertEquals(2, repository.messages.value.size)
        val privateMessage = repository.messages.value.first()
        assertEquals("t4_private1", privateMessage.fullname)
        assertEquals("orca-friend", privateMessage.author)
        assertTrue(privateMessage.isUnread)
        assertFalse(privateMessage.isCommentReply)
        assertTrue(requestedUrls.single().contains("mark=false"))
    }

    @Test
    fun inboxItemsCanBeRepliedToAndMarkedRead() = runBlocking {
        val postedForms = mutableMapOf<String, FormBody>()
        val repository = signedInRepository(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val path = request.url.encodedPath
                    (request.body as? FormBody)?.let { postedForms[path] = it }
                    val payload = if (path == "/message/inbox") {
                        """{"data":{"children":[{"kind":"t4","data":{"id":"one","name":"t4_one","author":"friend","subject":"Hi","body":"Hello","created_utc":1,"new":true}}]}}"""
                    } else {
                        "{}"
                    }
                    jsonResponse(request, payload)
                }
                .build(),
        )
        assertTrue(repository.refreshMessages().isSuccess)

        assertTrue(repository.replyToMessage("t4_one", "A useful reply").isSuccess)
        assertTrue(repository.markMessagesRead(listOf("t4_one")).isSuccess)

        val reply = checkNotNull(postedForms["/api/comment"])
        assertEquals("t4_one", reply.formValue("thing_id"))
        assertEquals("A useful reply", reply.formValue("text"))
        val read = checkNotNull(postedForms["/api/read_message"])
        assertEquals("t4_one", read.formValue("id"))
        assertFalse(repository.messages.value.single().isUnread)
    }

    private fun FormBody.formValue(name: String): String =
        value((0 until size).first { index -> this.name(index) == name })

    @Test
    fun inFlightRefreshCannotRepopulateFeedAfterDisconnect() = runBlocking {
        val blockedApi = BlockingApi(
            blockedPath = "/best",
            blockedPayload = """
                {
                  "data": {
                    "children": [
                      {
                        "kind": "t3",
                        "data": {
                          "id": "late-post",
                          "title": "This response arrived after sign-out",
                          "subreddit": "android",
                          "author": "late_author",
                          "created_utc": 1
                        }
                      }
                    ]
                  }
                }
            """.trimIndent(),
        )
        val repository = signedInRepository(blockedApi.client)

        val refresh = async(Dispatchers.IO) { repository.refresh("Home", "Best") }
        if (!blockedApi.started.await(10, TimeUnit.SECONDS)) {
            val earlyFailure = if (refresh.isCompleted) {
                refresh.await().exceptionOrNull()
            } else {
                null
            }
            blockedApi.release.countDown()
            refresh.cancel()
            fail("The feed request did not reach the response gate: $earlyFailure")
        }

        val disconnect = try {
            repository.disconnectAccount()
        } finally {
            blockedApi.release.countDown()
        }
        val refreshResult = refresh.await()

        assertTrue(disconnect.isSuccess)
        assertTrue(refreshResult.isSuccess)
        assertTrue(repository.feed.value.isEmpty())
        assertTrue(repository.communities.value.isEmpty())
    }

    @Test
    fun inFlightLoadCommentsCannotRepopulateThreadAfterDisconnect() = runBlocking {
        val postId = "late-post"
        val blockedApi = BlockingApi(
            blockedPath = "/comments/$postId",
            blockedPayload = """
                [
                  {"data": {"children": []}},
                  {
                    "data": {
                      "children": [
                        {
                          "kind": "t1",
                          "data": {
                            "id": "late-comment",
                            "body": "This comment arrived after sign-out",
                            "author": "late_author",
                            "created_utc": 1
                          }
                        }
                      ]
                    }
                  }
                ]
            """.trimIndent(),
        )
        val repository = signedInRepository(blockedApi.client)
        val comments = repository.comments(postId)

        val load = async(Dispatchers.IO) { repository.loadComments(postId, "Best") }
        if (!blockedApi.started.await(10, TimeUnit.SECONDS)) {
            val earlyFailure = if (load.isCompleted) {
                load.await().exceptionOrNull()
            } else {
                null
            }
            blockedApi.release.countDown()
            load.cancel()
            fail("The comments request did not reach the response gate: $earlyFailure")
        }

        val disconnect = try {
            repository.disconnectAccount()
        } finally {
            blockedApi.release.countDown()
        }
        val loadResult = load.await()

        assertTrue(disconnect.isSuccess)
        assertTrue(loadResult.isSuccess)
        assertTrue(comments.value.isEmpty())
    }

    @Test
    fun inFlightLoadMoreCommentsCannotRepopulateThreadAfterDisconnect() = runBlocking {
        val postId = "late-post"
        val blockedApi = BlockingApi(
            blockedPath = "/api/morechildren",
            blockedPayload = """
                {
                  "json": {
                    "data": {
                      "things": [
                        {
                          "kind": "t1",
                          "data": {
                            "id": "late-reply",
                            "body": "This reply arrived after sign-out",
                            "author": "late_author",
                            "created_utc": 1,
                            "depth": 1
                          }
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
            immediatePayloads = mapOf(
                "/comments/$postId" to """
                    [
                      {"data": {"children": []}},
                      {
                        "data": {
                          "children": [
                            {
                              "kind": "t1",
                              "data": {
                                "id": "root-comment",
                                "body": "Visible before sign-out",
                                "author": "early_author",
                                "created_utc": 1,
                                "replies": {
                                  "data": {
                                    "children": [
                                      {
                                        "kind": "more",
                                        "data": {
                                          "id": "stub",
                                          "count": 1,
                                          "children": ["late-reply"]
                                        }
                                      }
                                    ]
                                  }
                                }
                              }
                            }
                          ]
                        }
                      }
                    ]
                """.trimIndent(),
            ),
        )
        val repository = signedInRepository(blockedApi.client)
        val comments = repository.comments(postId)

        assertTrue(repository.loadComments(postId, "Best").isSuccess)
        val stubId = checkNotNull(comments.value.singleOrNull { it.isMoreStub }?.id) {
            "The primed thread did not contain a more stub: ${comments.value}"
        }

        val loadMore = async(Dispatchers.IO) {
            repository.loadMoreComments(postId, stubId, "Best")
        }
        if (!blockedApi.started.await(10, TimeUnit.SECONDS)) {
            val earlyFailure = if (loadMore.isCompleted) {
                loadMore.await().exceptionOrNull()
            } else {
                null
            }
            blockedApi.release.countDown()
            loadMore.cancel()
            fail("The morechildren request did not reach the response gate: $earlyFailure")
        }

        val disconnect = try {
            repository.disconnectAccount()
        } finally {
            blockedApi.release.countDown()
        }
        val loadMoreResult = loadMore.await()

        assertTrue(disconnect.isSuccess)
        assertTrue(loadMoreResult.isSuccess)
        // The spliced list is built from a pre-disconnect snapshot, so an unguarded write would
        // restore the whole thread -- not just the page that was in flight.
        assertTrue(comments.value.isEmpty())
    }

    @Test
    fun loadingMoreBeforeTheFirstPageDoesNotReportTheListingExhausted() = runBlocking {
        val repository = signedInRepository(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    error("Unexpected Reddit API request: ${chain.request().url}")
                }
                .build(),
        )

        // No refresh has run yet, so the stored cursor belongs to no listing at all. Reaching
        // the bottom of a restored feed while its refresh is still in flight does exactly this.
        val result = repository.loadMoreFeed(feedName = "r/pics", sort = "best", timeframe = null)

        // True means "ask again", not "a page is waiting". Answering false here is what used to
        // switch paging off for the rest of the visit, because the caller latches it.
        assertEquals(true, result.getOrThrow())
    }

    @Test
    fun authorFlairIsReadFromEitherShapeRedditSendsIt() = runBlocking {
        val postId = "flair-post"
        val repository = signedInRepository(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    jsonResponse(
                        chain.request(),
                        """
                        [
                          {"data": {"children": []}},
                          {"data": {"children": [
                            {"kind": "t1", "data": {
                              "id": "plain", "body": "b", "author": "one", "created_utc": 1,
                              "author_flair_text": "  Cardiologist  "
                            }},
                            {"kind": "t1", "data": {
                              "id": "rich", "body": "b", "author": "two", "created_utc": 1,
                              "author_flair_text": "",
                              "author_flair_richtext": [
                                {"e": "text", "t": "Team"},
                                {"e": "emoji", "u": "https://example.com/e.png"},
                                {"e": "text", "t": "Seahawks"}
                              ]
                            }},
                            {"kind": "t1", "data": {
                              "id": "bare", "body": "b", "author": "three", "created_utc": 1
                            }}
                          ]}}
                        ]
                        """.trimIndent(),
                    )
                }
                .build(),
        )

        assertTrue(repository.loadComments(postId, "Best").isSuccess)

        val flairs = repository.comments(postId).value.associate { it.id to it.authorFlair }
        assertEquals("Cardiologist", flairs["plain"])
        // Richtext arrives as fragments; only the text ones carry anything showable.
        assertEquals("Team Seahawks", flairs["rich"])
        assertEquals(null, flairs["bare"])
    }

    @Test
    fun aCommunityScopedSearchAsksRedditToRestrictItToThatCommunity() = runBlocking {
        val requested = mutableListOf<String>()
        val repository = signedInRepository(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    requested += chain.request().url.toString()
                    jsonResponse(chain.request(), """{"data": {"children": [], "after": null}}""")
                }
                .build(),
        )

        assertTrue(repository.refresh("qr/pics/sunset over water", "best").isSuccess)

        val url = requested.first { it.contains("/search") }
        // Restricted to the community, and the query keeps the words that follow the first
        // slash-separated segment even though a query may contain slashes of its own.
        assertTrue(url, url.contains("/r/pics/search"))
        assertTrue(url, url.contains("restrict_sr=1"))
        assertTrue(url, url.contains("q=sunset%20over%20water") || url.contains("q=sunset+over+water"))
    }

    private fun signedInRepository(apiClient: OkHttpClient): RedditApiRepository {
        val store = InMemoryRedditOAuthStore()
        check(
            store.saveCredential(
                CONFIGURATION.credentialStorageKey(),
                RedditOAuthCredential(
                    refreshToken = "refresh-token",
                    accountId = "account-id",
                    username = "otter-user",
                    scopes = setOf("identity", "read", "mysubreddits", "privatemessages"),
                ),
            ),
        )
        val oauthManager = RedditOAuthManager(
            configuration = CONFIGURATION,
            store = store,
            httpClient = oauthClient(),
        )
        oauthManager.seedAccessToken("access-token")
        return RedditApiRepository(
            configuration = CONFIGURATION,
            httpClient = apiClient,
            oauthManager = oauthManager,
        )
    }

    private fun RedditOAuthManager.seedAccessToken(value: String) {
        val cachedTokenClass = javaClass.declaredClasses.single { nestedClass ->
            nestedClass.simpleName == "CachedToken"
        }
        val cachedToken = cachedTokenClass
            .getDeclaredConstructor(String::class.java, Long::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .newInstance(value, Long.MAX_VALUE)
        javaClass.getDeclaredField("userToken")
            .apply { isAccessible = true }
            .set(this, cachedToken)
    }

    private fun oauthClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            when (chain.request().url.encodedPath) {
                "/api/v1/access_token" -> jsonResponse(
                    request = chain.request(),
                    payload = """
                        {
                          "access_token": "access-token",
                          "token_type": "bearer",
                          "expires_in": 3600,
                          "scope": "identity read mysubreddits"
                        }
                    """.trimIndent(),
                )
                "/api/v1/revoke_token" -> jsonResponse(chain.request(), "{}")
                else -> error("Unexpected OAuth request: ${chain.request().url}")
            }
        }
        .build()

    private class BlockingApi(
        private val blockedPath: String,
        private val blockedPayload: String,
        private val immediatePayloads: Map<String, String> = emptyMap(),
    ) {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                immediatePayloads[request.url.encodedPath]?.let { payload ->
                    return@addInterceptor jsonResponse(request, payload)
                }
                when (request.url.encodedPath) {
                    blockedPath -> {
                        started.countDown()
                        check(release.await(10, TimeUnit.SECONDS)) {
                            "Timed out waiting to release ${request.url}"
                        }
                        jsonResponse(request, blockedPayload)
                    }
                    "/subreddits/mine/subscriber" -> jsonResponse(
                        request,
                        """{"data":{"children":[]}}""",
                    )
                    else -> error("Unexpected Reddit API request: ${request.url}")
                }
            }
            .build()
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private val CONFIGURATION = RedditApiConfiguration(
            clientId = "test-client",
            userAgent = "android:app.orca.client:test (by /u/test)",
        )

        private fun jsonResponse(request: Request, payload: String): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(payload.toResponseBody(JSON))
                .build()
    }
}
