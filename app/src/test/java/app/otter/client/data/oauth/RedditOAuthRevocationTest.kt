package app.otter.client.data.oauth

import app.otter.client.model.RedditAccountState
import app.otter.client.model.RedditOAuthCredential
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Disconnect destroys the only copy of the refresh token, so an unreachable Reddit used to mean
 * the grant could never be revoked. These cover the queue that keeps the retry possible.
 */
class RedditOAuthRevocationTest {
    @Test
    fun signOutSucceedsLocallyAndQueuesRevocationWhenRedditIsUnreachable() = runBlocking {
        val store = signedInStore()
        val manager = manager(store, offlineClient())

        val result = manager.disconnect()

        assertTrue(result.isFailure)
        assertTrue(manager.accountState.value is RedditAccountState.SignedOut)
        assertEquals(null, store.loadCredential(STORAGE_KEY))
        assertEquals(listOf(REFRESH_TOKEN), store.loadPendingRevocations(STORAGE_KEY))
    }

    @Test
    fun queuedRevocationIsRetiredOnTheNextRunThatReachesReddit() = runBlocking {
        val store = signedInStore()
        manager(store, offlineClient()).disconnect()
        val revoked = mutableListOf<String>()

        // A fresh manager over the same store stands in for the next app launch.
        val result = manager(store, recordingClient(revoked)).flushPendingRevocations()

        assertTrue(result.isSuccess)
        assertEquals(listOf(REFRESH_TOKEN), revoked)
        assertTrue(store.loadPendingRevocations(STORAGE_KEY).isEmpty())
    }

    @Test
    fun aTokenRedditPermanentlyRejectsStopsBeingRetried() = runBlocking {
        val store = signedInStore()
        val attempts = AtomicInteger()

        val result = manager(store, statusClient(400, attempts)).disconnect()

        assertTrue(result.isFailure)
        assertEquals(1, attempts.get())
        // Repeating a 400 cannot change the answer, so it must not outlive the attempt.
        assertTrue(store.loadPendingRevocations(STORAGE_KEY).isEmpty())
    }

    @Test
    fun aServerSideFailureStaysQueued() = runBlocking {
        val store = signedInStore()

        val result = manager(store, statusClient(503, AtomicInteger())).disconnect()

        assertTrue(result.isFailure)
        assertEquals(listOf(REFRESH_TOKEN), store.loadPendingRevocations(STORAGE_KEY))
    }

    @Test
    fun flushingAnEmptyQueueMakesNoRequests() = runBlocking {
        val result = manager(InMemoryRedditOAuthStore(), offlineClient()).flushPendingRevocations()

        assertTrue(result.isSuccess)
    }

    private fun signedInStore(): InMemoryRedditOAuthStore = InMemoryRedditOAuthStore().apply {
        check(
            saveCredential(
                STORAGE_KEY,
                RedditOAuthCredential(
                    refreshToken = REFRESH_TOKEN,
                    accountId = "account-id",
                    username = "otter-user",
                    scopes = setOf("identity", "read"),
                ),
            ),
        )
    }

    private fun manager(store: RedditOAuthStore, httpClient: OkHttpClient) = RedditOAuthManager(
        configuration = CONFIGURATION,
        store = store,
        httpClient = httpClient,
    )

    private fun offlineClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { throw IOException("offline") }
        .build()

    private fun recordingClient(revoked: MutableList<String>): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertEquals("/api/v1/revoke_token", request.url.encodedPath)
                revoked += requireNotNull(request.formValue("token"))
                assertEquals("refresh_token", request.formValue("token_type_hint"))
                emptyResponse(request, 204)
            }
            .build()

    private fun statusClient(code: Int, attempts: AtomicInteger): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                attempts.incrementAndGet()
                emptyResponse(chain.request(), code)
            }
            .build()

    private fun Request.formValue(name: String): String? {
        val body = body as? okhttp3.FormBody ?: return null
        return (0 until body.size).firstOrNull { body.name(it) == name }?.let(body::value)
    }

    companion object {
        private val CONFIGURATION = RedditApiConfiguration(
            clientId = "personal-client",
            userAgent = "android:app.orca.client:v1.0.0 (by /u/tester)",
        )
        private val STORAGE_KEY = CONFIGURATION.credentialStorageKey()
        private const val REFRESH_TOKEN = "refresh-token"

        private fun emptyResponse(request: Request, code: Int): Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code < 400) "OK" else "Error")
            .body("".toResponseBody("application/json".toMediaType()))
            .build()
    }
}
