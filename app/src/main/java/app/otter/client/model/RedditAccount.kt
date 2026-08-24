package app.otter.client.model

data class RedditAccount(
    val id: String,
    val username: String,
    val scopes: Set<String>,
)

sealed interface RedditAccountState {
    data object Unavailable : RedditAccountState

    data object SignedOut : RedditAccountState

    data object Authorizing : RedditAccountState

    data class SignedIn(val account: RedditAccount) : RedditAccountState
}

class RedditAuthenticationException(message: String) : IllegalStateException(message)
