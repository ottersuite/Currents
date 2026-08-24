package app.otter.client.data.oauth

import app.otter.client.model.RedditOAuthCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryRedditOAuthStoreTest {
    private val credential = RedditOAuthCredential(
        refreshToken = "refresh-secret",
        accountId = "t2_account",
        username = "otter_user",
        scopes = setOf("identity", "read", "vote"),
    )

    @Test
    fun credentialIsScopedToClientId() {
        val store = InMemoryRedditOAuthStore()

        assertTrue(store.saveCredential("client-a", credential))
        assertEquals(credential, store.loadCredential("client-a"))
        assertNull(store.loadCredential("client-b"))
        assertNull(store.loadCredential("client-a"))
    }

    @Test
    fun credentialValidationRejectsIncompleteSecretsOrIdentity() {
        val store = InMemoryRedditOAuthStore()

        assertFalse(store.saveCredential("", credential))
        assertFalse(store.saveCredential("client", credential.copy(refreshToken = "")))
        assertFalse(store.saveCredential("client", credential.copy(username = "")))
        assertNull(store.loadCredential("client"))
    }

    @Test
    fun validPendingStateIsConsumedExactlyOnce() {
        val store = InMemoryRedditOAuthStore()
        val issuedAt = 1_000L
        assertTrue(store.savePendingAuthorization("high-entropy-state", issuedAt))

        assertTrue(store.consumePendingAuthorization("high-entropy-state", issuedAt + 30_000L))
        assertFalse(store.consumePendingAuthorization("high-entropy-state", issuedAt + 30_001L))
    }

    @Test
    fun wrongStateDoesNotDestroyPendingAuthorization() {
        val store = InMemoryRedditOAuthStore()
        assertTrue(store.savePendingAuthorization("expected", 1_000L))

        assertFalse(store.consumePendingAuthorization("attacker", 2_000L))
        assertTrue(store.consumePendingAuthorization("expected", 2_001L))
    }

    @Test
    fun stateExpiresAfterTenMinutesAndCannotComeFromFuture() {
        val store = InMemoryRedditOAuthStore()
        val issuedAt = 5_000L
        assertTrue(store.savePendingAuthorization("state", issuedAt))
        assertTrue(
            store.consumePendingAuthorization(
                "state",
                issuedAt + RedditOAuthStore.PENDING_AUTHORIZATION_TTL_MILLIS,
            ),
        )

        assertTrue(store.savePendingAuthorization("state", issuedAt))
        assertFalse(
            store.consumePendingAuthorization(
                "state",
                issuedAt + RedditOAuthStore.PENDING_AUTHORIZATION_TTL_MILLIS + 1L,
            ),
        )

        assertTrue(store.savePendingAuthorization("state", issuedAt))
        assertFalse(store.consumePendingAuthorization("state", issuedAt - 1L))
    }
}
