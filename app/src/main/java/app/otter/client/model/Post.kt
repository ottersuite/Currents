package app.otter.client.model

enum class PostType {
    TEXT,
    LINK,
    IMAGE,
    GALLERY,
    VIDEO,
}

/**
 * Deterministic visual information for an offline preview.
 *
 * A client can render a gradient using the two colors and place [label] over
 * it. [assetKey] is stable for screenshot tests and can later be mapped to a
 * bundled drawable without changing the repository contract.
 */
data class PostPreview(
    val assetKey: String,
    val label: String,
    val startColorArgb: Long,
    val endColorArgb: Long,
    val imageUrl: String? = null,
    /** A small variant for list thumbnails; [imageUrl] is the full-size version. */
    val thumbnailUrl: String? = null,
    /**
     * A screen-width variant for large feed cards. Sits between [thumbnailUrl] and [imageUrl]:
     * a card never needs the original, which Reddit often stores several thousand pixels wide.
     */
    val cardImageUrl: String? = null,
    val aspectRatio: Float = 16f / 9f,
    val altText: String = label,
) {
    init {
        require(assetKey.isNotBlank()) { "Preview asset key cannot be blank" }
        require(aspectRatio > 0f) { "Preview aspect ratio must be positive" }
    }
}

/** Immutable feed item. Mutations are emitted as updated copies by the repository. */
data class Post(
    val id: String,
    val community: Community,
    val title: String,
    val author: String,
    val type: PostType,
    val score: Int,
    val commentCount: Int,
    val createdAtEpochSeconds: Long,
    val domain: String? = null,
    /** Reddit's per-post flair, shown inline after the title. */
    val flairText: String? = null,
    val destinationUrl: String? = null,
    val body: String? = null,
    val preview: PostPreview? = null,
    /** Playable or viewable attachments; null when the post is text or a bare link. */
    val media: PostMedia? = null,
    val voteState: VoteState = VoteState.NONE,
    val isSaved: Boolean = false,
    val isRead: Boolean = false,
    val isStickied: Boolean = false,
    val isNsfw: Boolean = false,
    val isSpoiler: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Post id cannot be blank" }
        require(title.isNotBlank()) { "Post title cannot be blank" }
        require(author.isNotBlank()) { "Post author cannot be blank" }
        require(commentCount >= 0) { "Comment count cannot be negative" }
        require(createdAtEpochSeconds >= 0L) { "Created timestamp cannot be negative" }
    }
}
