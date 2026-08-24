package app.otter.client.model

/**
 * The durable portion of a signed-in Reddit session.
 *
 * Access tokens are deliberately absent: callers should retain them only in memory and obtain a
 * new one from [refreshToken] after a process restart.
 */
data class RedditOAuthCredential(
    val refreshToken: String,
    val accountId: String,
    val username: String,
    val scopes: Set<String>,
)

