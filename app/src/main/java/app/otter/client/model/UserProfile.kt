package app.otter.client.model

/** Reddit account metadata plus a small, independently loaded submitted-post listing. */
data class UserProfile(
    val username: String,
    val displayName: String = username,
    val description: String = "",
    val iconUrl: String? = null,
    val createdAtEpochSeconds: Long = 0L,
    val totalKarma: Int = 0,
    val postKarma: Int = 0,
    val commentKarma: Int = 0,
    val isGold: Boolean = false,
    val isEmployee: Boolean = false,
    val recentPosts: List<Post> = emptyList(),
) {
    init {
        require(username.isNotBlank()) { "Profile username cannot be blank" }
        require(createdAtEpochSeconds >= 0L) { "Profile creation time cannot be negative" }
    }
}
