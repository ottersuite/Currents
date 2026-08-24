package app.otter.client.data.oauth

import app.otter.client.model.RedditAccountState
import app.otter.client.model.RedditOAuthCredential
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditOAuthManagerTest {
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
