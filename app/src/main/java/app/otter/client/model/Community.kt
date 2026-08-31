package app.otter.client.model

/**
 * Small, UI-ready representation of a Reddit community.
 *
 * Colors are stored as unsigned ARGB values in a [Long] so the model remains
 * independent from Android and Compose color classes.
 */
data class Community(
    val name: String,
    val displayName: String,
    val memberCount: Int,
    val isFavorite: Boolean = false,
    val accentStartArgb: Long,
    val accentEndArgb: Long,
    /**
     * The community's own icon, when Reddit has one for it.
     *
     * Null is the normal case for a community first seen in a post, where the listing carries no
     * styling. The accent colours above remain the fallback, so a missing icon is a plainer row
     * rather than an empty one.
     */
    val iconUrl: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Community name cannot be blank" }
        require(displayName.isNotBlank()) { "Community display name cannot be blank" }
        require(memberCount >= 0) { "Community member count cannot be negative" }
    }

    val path: String
        get() = "r/$name"
}
