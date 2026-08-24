package app.otter.client.model

/** How an asset is played back, which decides the surface it needs. */
enum class MediaKind {
    /** A still frame. */
    IMAGE,

    /** Looping and started without being asked — some MP4-backed GIFs may still carry audio. */
    ANIMATED,

    /** A clip with a timeline, and usually sound. */
    VIDEO,
}

/**
 * One playable or viewable thing attached to a post.
 *
 * [url] is what should be opened first and [fallbackUrl] is the same content in a form more
 * players accept — an adaptive stream falling back to a progressive MP4, or an MP4 encoding of
 * a GIF falling back to the GIF itself. [previewUrl] is a still frame worth showing while the
 * real asset loads.
 */
data class MediaAsset(
    val kind: MediaKind,
    val url: String,
    val fallbackUrl: String? = null,
    val previewUrl: String? = null,
    val aspectRatio: Float = 16f / 9f,
    val caption: String? = null,
    val hasAudio: Boolean = false,
    val durationSeconds: Int = 0,
) {
    init {
        require(url.isNotBlank()) { "Media asset url cannot be blank" }
        require(aspectRatio > 0f) { "Media asset aspect ratio must be positive" }
        require(durationSeconds >= 0) { "Media asset duration cannot be negative" }
    }

    /** True when the asset moves, whether a player or an animated decoder drives it. */
    val isPlayable: Boolean get() = kind != MediaKind.IMAGE

    /**
     * Sources a video player can actually open, in order; it should fall through them on failure.
     * GIF sources are excluded — no ExoPlayer extractor reads them, so they belong on an image
     * surface with an animated decoder instead.
     */
    val playbackUrls: List<String>
        get() = listOfNotNull(url, fallbackUrl).distinct().filterNot(::isGifSource)

    /** The animated image to hand to the image loader when no player-friendly source exists. */
    val animatedImageUrl: String?
        get() = listOfNotNull(url, fallbackUrl).firstOrNull(::isGifSource)

    /** True when this asset needs a player rather than an image surface. */
    val needsPlayer: Boolean get() = isPlayable && playbackUrls.isNotEmpty()

    /**
     * Reddit serves the MP4 re-encoding of a GIF from the *same* `.gif` path as the GIF itself,
     * distinguished only by `format=mp4` in the query. Judging by the path alone sends video to
     * the image decoder, which renders nothing at all.
     */
    private fun isGifSource(candidate: String): Boolean {
        val path = candidate.substringBefore('?')
        val query = candidate.substringAfter('?', "")
        if (query.contains("format=mp4", ignoreCase = true)) return false
        return path.endsWith(".gif", ignoreCase = true)
    }
}

/** Every asset a post carries, in the order Reddit lists them. */
data class PostMedia(val assets: List<MediaAsset>) {
    init {
        require(assets.isNotEmpty()) { "Post media cannot be empty" }
    }

    val isGallery: Boolean get() = assets.size > 1
    val first: MediaAsset get() = assets.first()

    /** True when opening this post should start a player rather than an image view. */
    val leadsWithPlayback: Boolean get() = first.isPlayable
}
