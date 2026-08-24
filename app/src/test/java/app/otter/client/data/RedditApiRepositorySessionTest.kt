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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RedditApiRepositorySessionTest {
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

    private fun signedInRepository(apiClient: OkHttpClient): RedditApiRepository {
        val store = InMemoryRedditOAuthStore()
        check(
            store.saveCredential(
                CONFIGURATION.credentialStorageKey(),
                RedditOAuthCredential(
                    refreshToken = "refresh-token",
                    accountId = "account-id",
                    username = "otter-user",
                    scopes = setOf("identity", "read", "mysubreddits"),
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
