package app.otter.client.data.oauth

import app.otter.client.model.RedditOAuthCredential

/**
 * An access token held over from an earlier session, and the wall-clock instant it stops being
 * usable.
 *
 * Wall clock rather than elapsed time on purpose: the only reason to persist a token at all is
 * to survive process death, and a monotonic clock restarts when the process does.
 */
data class StoredAccessToken(val value: String, val expiresAtEpochMillis: Long)

/** Persists the state needed to resume a Reddit session without signing in again. */
interface RedditOAuthStore {
    /** Replaces the currently stored refresh credential. */
    fun saveCredential(configurationKey: String, credential: RedditOAuthCredential): Boolean

    /** Returns the credential for [configurationKey], or null when it cannot be recovered safely. */
    fun loadCredential(configurationKey: String): RedditOAuthCredential?

    fun clearCredential(): Boolean

    /**
     * Returns an access token saved by an earlier session, if one was kept.
     *
     * Reddit's access tokens last an hour, but a client that only holds one in memory throws it
     * away on every process death and has to spend a network round trip re-minting it before it
     * can make its first request — on a cold start, that round trip is in front of everything
     * the user is waiting to see.
     *
     * The caller is still responsible for checking the expiry, and must tolerate a token that
     * turns out to be rejected anyway: a persisted expiry is only as good as the device clock,
     * and the grant can be revoked from Reddit's side at any point. The default returns null,
     * so a store with nowhere safe to keep one simply costs the refresh it always did.
     */
    fun loadAccessToken(configurationKey: String): StoredAccessToken? = null

    /** Saves, or with a null [token] discards, the cached access token. */
    fun saveAccessToken(configurationKey: String, token: StoredAccessToken?): Boolean = false

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
