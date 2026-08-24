package app.otter.client.data.oauth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object OAuthStateVerifier {
    fun digest(state: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(state.toByteArray(StandardCharsets.UTF_8))

    fun matches(expectedDigest: ByteArray, returnedState: String): Boolean =
        MessageDigest.isEqual(expectedDigest, digest(returnedState))

    fun isFresh(issuedAtEpochMillis: Long, nowEpochMillis: Long): Boolean {
        if (issuedAtEpochMillis < 0L || nowEpochMillis < issuedAtEpochMillis) return false
        return nowEpochMillis - issuedAtEpochMillis <=
            RedditOAuthStore.PENDING_AUTHORIZATION_TTL_MILLIS
    }
}

