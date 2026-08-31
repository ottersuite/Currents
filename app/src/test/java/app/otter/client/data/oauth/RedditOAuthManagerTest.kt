package app.otter.client.data.oauth

import app.otter.client.model.RedditAccountState
import app.otter.client.model.RedditOAuthCredential
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditOAuthManagerTest {
    private companion object {
        const val NOW = 1_700_000_000_000L
        const val HOUR_MILLIS = 60L * 60L * 1_000L
    }

    @Test
    fun authorizationUrlUsesInstalledAppFlowAndExactRedirect() {
        val store = InMemoryRedditOAuthStore()
        val manager = manager(store)

        val first = manager.beginAuthorization().getOrThrow().toHttpUrl()
        val second = manager.beginAuthorization().getOrThrow().toHttpUrl()

        assertEquals("https", first.scheme)
        assertEquals("www.reddit.com", first.host)
        assertEquals("/api/v1/authorize.compact", first.encodedPath)
        assertEquals("personal-client", first.queryParameter("client_id"))
        assertEquals("code", first.queryParameter("response_type"))
        assertEquals("permanent", first.queryParameter("duration"))
        assertEquals(RedditApiConfiguration.DEFAULT_REDIRECT_URI, first.queryParameter("redirect_uri"))
        assertEquals(
            RedditOAuthManager.REQUESTED_SCOPES,
            first.queryParameter("scope").orEmpty().split(' ').toSet(),
        )
        assertTrue(first.queryParameter("state").orEmpty().length >= 43)
        assertNotEquals(first.queryParameter("state"), second.queryParameter("state"))
    }

    @Test
    fun pendingAuthorizationStateIsBoundToTheActiveConfiguration() {
        val store = InMemoryRedditOAuthStore()
        val url = manager(store).beginAuthorization().getOrThrow().toHttpUrl()
        val state = checkNotNull(url.queryParameter("state"))

        assertFalse(store.consumePendingAuthorization(state))
    }

    @Test
    fun callbackMustUseExactConstrainedUri() = runBlocking {
        val manager = manager(InMemoryRedditOAuthStore())
        manager.beginAuthorization().getOrThrow()

        val result = manager.completeAuthorization("otter://oauth?state=anything&code=secret")

        assertTrue(result.isFailure)
        assertTrue(manager.accountState.value is RedditAccountState.SignedOut)
    }

    @Test
    fun runtimeCustomRedirectIsUsedAndMatchedExactly() = runBlocking {
        val redirect = "personal.client://oauth_return/oauth-return"
        val store = InMemoryRedditOAuthStore()
        val manager = manager(
            store = store,
            configuration = apiConfiguration(redirectUri = redirect),
        )
        val authorization = manager.beginAuthorization().getOrThrow().toHttpUrl()
        val state = checkNotNull(authorization.queryParameter("state"))

        assertEquals(redirect, authorization.queryParameter("redirect_uri"))
        val result = manager.completeAuthorization("$redirect?state=$state&error=access_denied")
        assertTrue(result.isFailure)
        assertEquals("Reddit sign-in was cancelled", result.exceptionOrNull()?.message)
    }

    @Test
    fun pendingAuthorizationCannotMoveBetweenConfigurations() = runBlocking {
        val store = InMemoryRedditOAuthStore()
        val first = manager(store, apiConfiguration(clientId = "first-client"))
        val state = checkNotNull(
            first.beginAuthorization().getOrThrow().toHttpUrl().queryParameter("state"),
        )
        val second = manager(store, apiConfiguration(clientId = "second-client"))

        val result = second.completeAuthorization(
            "${RedditApiConfiguration.DEFAULT_REDIRECT_URI}?state=$state&error=access_denied",
        )

        assertTrue(result.isFailure)
        assertEquals(
            "This Reddit sign-in request expired or was already used",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun storedCredentialIsBoundToAllActiveApiSettings() {
        val store = InMemoryRedditOAuthStore()
        val original = apiConfiguration()
        val credential = RedditOAuthCredential(
            refreshToken = "refresh-secret",
            accountId = "account-id",
            username = "tester",
            scopes = setOf("identity", "read"),
        )
        assertTrue(store.saveCredential(original.credentialStorageKey(), credential))
        assertTrue(manager(store, original).accountState.value is RedditAccountState.SignedIn)

        val changedRedirect = original.copy(redirectUri = "personal.client://oauth_return")
        assertTrue(manager(store, changedRedirect).accountState.value is RedditAccountState.SignedOut)
        assertTrue(store.loadCredential(original.credentialStorageKey()) == null)
    }

    @Test
    fun deniedAuthorizationConsumesStateWithoutNetworkRequest() = runBlocking {
        val store = InMemoryRedditOAuthStore()
        val manager = manager(store)
        val state = checkNotNull(
            manager.beginAuthorization().getOrThrow().toHttpUrl().queryParameter("state"),
        )
        val callback = "${RedditApiConfiguration.DEFAULT_REDIRECT_URI}?state=$state&error=access_denied"

        val first = manager.completeAuthorization(callback)
        val replay = manager.completeAuthorization(callback)

        assertTrue(first.isFailure)
        assertTrue(replay.isFailure)
        assertTrue(manager.accountState.value is RedditAccountState.SignedOut)
    }

    @Test
    fun redditGrantFragmentIsAcceptedAndNeverReadForResponseValues() = runBlocking {
        val store = InMemoryRedditOAuthStore()
        val manager = manager(store)
        val state = checkNotNull(
            manager.beginAuthorization().getOrThrow().toHttpUrl().queryParameter("state"),
        )
        val redirect = RedditApiConfiguration.DEFAULT_REDIRECT_URI

        // The callback is recognized despite the trailing '#_' Reddit appends.
        val result = manager.completeAuthorization("$redirect?state=$state&error=access_denied#_")

        assertTrue(result.isFailure)
        assertEquals("Reddit sign-in was cancelled", result.exceptionOrNull()?.message)
    }

    @Test
    fun authorizationValuesInAFragmentAreNotTreatedAsAResponse() = runBlocking {
        val manager = manager(InMemoryRedditOAuthStore())
        val state = checkNotNull(
            manager.beginAuthorization().getOrThrow().toHttpUrl().queryParameter("state"),
        )
        val redirect = RedditApiConfiguration.DEFAULT_REDIRECT_URI

        // Only the query is parsed, so a fragment cannot smuggle in a state or code.
        val result = manager.completeAuthorization("$redirect#state=$state&code=secret")

        assertTrue(result.isFailure)
        assertTrue(manager.accountState.value is RedditAccountState.SignedOut)
    }

    @Test
    fun aStoredAccessTokenIsReusedWithoutContactingReddit() = runBlocking {
        val store = InMemoryRedditOAuthStore()
        val configuration = apiConfiguration()
        store.saveCredential(configuration.credentialStorageKey(), credential())
        store.saveAccessToken(
            configuration.credentialStorageKey(),
            StoredAccessToken("carried-over-token", NOW + HOUR_MILLIS),
        )

        // Any attempt to mint a new token would have to reach this host, and fail.
        val manager = offlineManager(store, configuration)

        assertEquals("carried-over-token", manager.accessToken().value)
    }

    @Test
    fun anExpiredStoredAccessTokenIsDiscardedRatherThanReplayed() = runBlocking {
        val store = InMemoryRedditOAuthStore()
        val configuration = apiConfiguration()
        store.saveCredential(configuration.credentialStorageKey(), credential())
        store.saveAccessToken(
            configuration.credentialStorageKey(),
            StoredAccessToken("stale-token", NOW - 1L),
        )

        val manager = offlineManager(store, configuration)

        // Falls through to a refresh, which cannot reach the network here.
        assertTrue(runCatching { manager.accessToken() }.isFailure)
        assertNull(store.loadAccessToken(configuration.credentialStorageKey()))
    }

    @Test
    fun signingOutDiscardsTheStoredAccessToken() = runBlocking {
        val store = InMemoryRedditOAuthStore()
        val configuration = apiConfiguration()
        store.saveCredential(configuration.credentialStorageKey(), credential())
        store.saveAccessToken(
            configuration.credentialStorageKey(),
            StoredAccessToken("carried-over-token", NOW + HOUR_MILLIS),
        )

        offlineManager(store, configuration).disconnect()

        // A token outliving its credential would keep a signed-out session authorized.
        assertNull(store.loadAccessToken(configuration.credentialStorageKey()))
        assertNull(store.loadCredential(configuration.credentialStorageKey()))
    }

    @Test
    fun aRejectedAccessTokenIsNotLeftOnDiskForTheNextLaunch() = runBlocking {
        val store = InMemoryRedditOAuthStore()
        val configuration = apiConfiguration()
        store.saveCredential(configuration.credentialStorageKey(), credential())
        store.saveAccessToken(
            configuration.credentialStorageKey(),
            StoredAccessToken("rejected-token", NOW + HOUR_MILLIS),
        )
        val manager = offlineManager(store, configuration)

        manager.invalidate(manager.accessToken())

        assertNull(store.loadAccessToken(configuration.credentialStorageKey()))
    }

    private fun credential() = RedditOAuthCredential(
        refreshToken = "refresh-token",
        accountId = "t2_account",
        username = "tester",
        scopes = RedditOAuthManager.REQUESTED_SCOPES,
    )

    /** A manager whose token endpoint is unreachable, so any refresh attempt is visible. */
    private fun offlineManager(
        store: RedditOAuthStore,
        configuration: RedditApiConfiguration = apiConfiguration(),
    ) = RedditOAuthManager(
        configuration = configuration,
        store = store,
        authBaseUrl = "https://127.0.0.1:9",
        apiBaseUrl = "https://127.0.0.1:9",
        wallClockMillis = { NOW },
    )

    private fun manager(
        store: RedditOAuthStore,
        configuration: RedditApiConfiguration = apiConfiguration(),
    ) = RedditOAuthManager(
        configuration = configuration,
        store = store,
    )

    private fun apiConfiguration(
        clientId: String = "personal-client",
        redirectUri: String = RedditApiConfiguration.DEFAULT_REDIRECT_URI,
    ) = RedditApiConfiguration(
        clientId = clientId,
        userAgent = "android:app.orca.client:v1.0.0 (by /u/tester)",
        redirectUri = redirectUri,
    )
}
