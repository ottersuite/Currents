package app.otter.client.data.oauth

import app.otter.client.model.RedditOAuthCredential

/** Thread-safe store for unit tests and non-persistent previews. */
class InMemoryRedditOAuthStore : RedditOAuthStore {
    private data class ScopedCredential(
        val configurationKey: String,
        val credential: RedditOAuthCredential,
    )

    private data class ScopedAccessToken(
        val configurationKey: String,
        val token: StoredAccessToken,
    )

    private var storedCredential: ScopedCredential? = null
    private var storedAccessToken: ScopedAccessToken? = null
    private var pendingStateDigest: ByteArray? = null
    private var pendingIssuedAtEpochMillis: Long? = null
    private val pendingRevocations = linkedMapOf<String, MutableList<String>>()

    @Synchronized
    override fun saveCredential(
        configurationKey: String,
        credential: RedditOAuthCredential,
    ): Boolean {
        if (!isValid(configurationKey, credential)) return false
        storedCredential = ScopedCredential(configurationKey, credential.defensiveCopy())
        return true
    }

    @Synchronized
    override fun loadCredential(configurationKey: String): RedditOAuthCredential? {
        val stored = storedCredential ?: return null
        if (configurationKey.isBlank() || stored.configurationKey != configurationKey) {
            storedCredential = null
            return null
        }
        return stored.credential.defensiveCopy()
    }

    @Synchronized
    override fun loadAccessToken(configurationKey: String): StoredAccessToken? {
        val stored = storedAccessToken ?: return null
        if (configurationKey.isBlank() || stored.configurationKey != configurationKey) {
            storedAccessToken = null
            return null
        }
        return stored.token
    }

    @Synchronized
    override fun saveAccessToken(configurationKey: String, token: StoredAccessToken?): Boolean {
        if (configurationKey.isBlank()) return false
        storedAccessToken = token
            ?.takeIf { it.value.isNotBlank() }
            ?.let { ScopedAccessToken(configurationKey, it) }
        return true
    }

    @Synchronized
    override fun clearCredential(): Boolean {
        storedCredential = null
        // A token outliving the credential it was minted from would keep a signed-out session
        // making authorized requests until it expired.
        storedAccessToken = null
        return true
    }

    @Synchronized
    override fun savePendingAuthorization(state: String, issuedAtEpochMillis: Long): Boolean {
        if (state.isBlank() || issuedAtEpochMillis < 0L) return false
        pendingStateDigest = OAuthStateVerifier.digest(state)
        pendingIssuedAtEpochMillis = issuedAtEpochMillis
        return true
    }

    @Synchronized
    override fun consumePendingAuthorization(
        returnedState: String,
        nowEpochMillis: Long,
    ): Boolean {
        val expectedDigest = pendingStateDigest
        val issuedAt = pendingIssuedAtEpochMillis

        if (expectedDigest == null || issuedAt == null) {
            pendingStateDigest = null
            pendingIssuedAtEpochMillis = null
            return false
        }
        val stateMatches = OAuthStateVerifier.matches(expectedDigest, returnedState)
        val isFresh = OAuthStateVerifier.isFresh(issuedAt, nowEpochMillis)
        if (!isFresh) {
            pendingStateDigest = null
            pendingIssuedAtEpochMillis = null
            return false
        }
        if (!stateMatches) return false
        pendingStateDigest = null
        pendingIssuedAtEpochMillis = null
        return true
    }

    @Synchronized
    override fun clearPendingAuthorization(): Boolean {
        pendingStateDigest = null
        pendingIssuedAtEpochMillis = null
        return true
    }

    @Synchronized
    override fun savePendingRevocation(configurationKey: String, refreshToken: String): Boolean {
        if (configurationKey.isBlank() || refreshToken.isBlank()) return false
        val queue = pendingRevocations.getOrPut(configurationKey) { mutableListOf() }
        if (refreshToken !in queue) queue.add(refreshToken)
        return true
    }

    @Synchronized
    override fun loadPendingRevocations(configurationKey: String): List<String> {
        if (configurationKey.isBlank()) return emptyList()
        return pendingRevocations[configurationKey].orEmpty().toList()
    }

    @Synchronized
    override fun clearPendingRevocation(configurationKey: String, refreshToken: String): Boolean {
        val queue = pendingRevocations[configurationKey] ?: return false
        val removed = queue.remove(refreshToken)
        if (queue.isEmpty()) pendingRevocations.remove(configurationKey)
        return removed
    }

    private fun RedditOAuthCredential.defensiveCopy() = copy(scopes = scopes.toSet())

    private fun isValid(configurationKey: String, credential: RedditOAuthCredential): Boolean =
        configurationKey.isNotBlank() &&
            credential.refreshToken.isNotBlank() &&
            credential.accountId.isNotBlank() &&
            credential.username.isNotBlank() &&
            credential.scopes.none(String::isBlank)
}
