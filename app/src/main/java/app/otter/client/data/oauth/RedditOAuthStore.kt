package app.otter.client.data.oauth

import app.otter.client.model.RedditOAuthCredential

/** Persists the minimum state needed to resume Reddit OAuth without retaining access tokens. */
interface RedditOAuthStore {
    /** Replaces the currently stored refresh credential. */
    fun saveCredential(configurationKey: String, credential: RedditOAuthCredential): Boolean

    /** Returns the credential for [configurationKey], or null when it cannot be recovered safely. */
    fun loadCredential(configurationKey: String): RedditOAuthCredential?

    fun clearCredential(): Boolean

    /** Stores a single outstanding OAuth state challenge, replacing any older challenge. */
    fun savePendingAuthorization(
        state: String,
        issuedAtEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean

    /**
     * Atomically consumes and validates the outstanding state challenge.
     *
     * A matching challenge is one-shot. A mismatch leaves the high-entropy challenge intact, while
     * an expired or malformed challenge is cleared.
     */
    fun consumePendingAuthorization(
        returnedState: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean

    fun clearPendingAuthorization(): Boolean

    /**
     * Records a refresh token whose server-side grant still needs revoking.
     *
     * Written before the credential is destroyed, so a disconnect that cannot reach Reddit --
     * offline, or a failing endpoint -- can still be completed on a later run instead of leaving
     * the grant alive with nothing left to revoke it with.
     */
    fun savePendingRevocation(configurationKey: String, refreshToken: String): Boolean

    /** Returns tokens still awaiting revocation for [configurationKey], oldest first. */
    fun loadPendingRevocations(configurationKey: String): List<String>

    /** Drops one token once Reddit has confirmed the grant is gone. */
    fun clearPendingRevocation(configurationKey: String, refreshToken: String): Boolean

    companion object {
        const val PENDING_AUTHORIZATION_TTL_MILLIS: Long = 10L * 60L * 1_000L
    }
}
