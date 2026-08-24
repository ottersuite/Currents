package app.otter.client.data

import app.otter.client.model.MediaAsset
import app.otter.client.model.MediaKind
import app.otter.client.model.PostMedia
import org.json.JSONObject

/**
 * Pulls playable assets out of a Reddit post payload.
 *
 * Reddit describes the same attachment several ways depending on where it was uploaded, so this
 * walks the shapes in order of fidelity: a gallery's own metadata, then a hosted video with its
 * adaptive streams, then the MP4 encoding Reddit generates for GIF-shaped posts, and only then
 * the raw link. A crosspost carries none of this itself and defers to the post it copied.
 */
internal object RedditMediaParser {
    private const val MAX_RATIO = 5f
    private const val MIN_RATIO = .2f

    fun parse(data: JSONObject): PostMedia? =
        gallery(data)
            ?: hostedVideo(data)
            ?: animated(data)
            ?: directLink(data)
            ?: crosspostParent(data)?.let(::parse)

    private fun crosspostParent(data: JSONObject): JSONObject? =
        data.optJSONArray("crosspost_parent_list")?.optJSONObject(0)

    private fun gallery(data: JSONObject): PostMedia? {
        val items = data.optJSONObject("gallery_data")?.optJSONArray("items") ?: return null
        val metadata = data.optJSONObject("media_metadata") ?: return null
        val assets = (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val mediaId = item.text("media_id") ?: return@mapNotNull null
            val entry = metadata.optJSONObject(mediaId) ?: return@mapNotNull null
            galleryAsset(entry, caption = item.text("caption"))
        }
        return assets.takeIf { it.isNotEmpty() }?.let(::PostMedia)
    }

    private fun galleryAsset(entry: JSONObject, caption: String?): MediaAsset? {
        if (entry.text("status")?.equals("valid", ignoreCase = true) == false) return null
        val source = entry.optJSONObject("s") ?: return null
        val ratio = ratioOf(source.optInt("x"), source.optInt("y"))
        val still = source.url("u") ?: entry.largestPreview()
        val mp4 = source.url("mp4")
        val gif = source.url("gif")

        return when {
            mp4 != null || gif != null -> MediaAsset(
                kind = MediaKind.ANIMATED,
                url = mp4 ?: gif!!,
                fallbackUrl = gif.takeIf { mp4 != null },
                previewUrl = still,
                aspectRatio = ratio,
                caption = caption,
            )

            still != null -> MediaAsset(
                kind = MediaKind.IMAGE,
                url = still,
                aspectRatio = ratio,
                caption = caption,
            )

            else -> null
        }
    }

    private fun JSONObject.largestPreview(): String? =
        optJSONArray("p")?.let { previews ->
            (previews.length() - 1 downTo 0).firstNotNullOfOrNull { index ->
                previews.optJSONObject(index)?.url("u")
            }
        }

    private fun hostedVideo(data: JSONObject): PostMedia? {
        val video = data.optJSONObject("secure_media")?.optJSONObject("reddit_video")
            ?: data.optJSONObject("media")?.optJSONObject("reddit_video")
            ?: data.optJSONObject("preview")?.optJSONObject("reddit_video_preview")
            ?: return null

        val hls = video.url("hls_url")
        val dash = video.url("dash_url")
        val fallback = video.url("fallback_url")?.substringBefore("?source=fallback")
        // Reddit's is_gif flag describes looping presentation, not reliably the absence of audio.
        val silent = video.optBoolean("is_gif")
        val hasAudio = video.optBoolean("has_audio", !silent)
        // Adaptive streams exist to carry an audio track and adjust quality; a silent loop needs
        // neither, and seeking inside one costs a network fetch per seek. The progressive file
        // is a local read once buffered, which is what makes scrubbing feel immediate.
        val primary = if (silent) {
            fallback ?: hls ?: dash ?: return null
        } else {
            hls ?: dash ?: fallback ?: return null
        }

        return PostMedia(
            listOf(
                MediaAsset(
                    kind = if (silent) MediaKind.ANIMATED else MediaKind.VIDEO,
                    url = primary,
                    fallbackUrl = fallback.takeIf { it != primary },
                    previewUrl = previewImage(data),
                    aspectRatio = ratioOf(video.optInt("width"), video.optInt("height")),
                    hasAudio = hasAudio,
                    durationSeconds = video.optInt("duration").coerceAtLeast(0),
                ),
            ),
        )
    }

    /** Reddit re-encodes GIF posts as MP4 and hides it in the preview variants. */
    private fun animated(data: JSONObject): PostMedia? {
        val image = data.optJSONObject("preview")?.optJSONArray("images")?.optJSONObject(0)
        val variants = image?.optJSONObject("variants") ?: return null
        val mp4 = variants.optJSONObject("mp4")?.optJSONObject("source")
        val gif = variants.optJSONObject("gif")?.optJSONObject("source")
        val source = mp4 ?: gif ?: return null
        val url = source.url("url") ?: return null

        return PostMedia(
            listOf(
                MediaAsset(
                    kind = MediaKind.ANIMATED,
                    url = url,
                    fallbackUrl = gif?.url("url")?.takeIf { mp4 != null },
                    previewUrl = previewImage(data),
                    aspectRatio = ratioOf(source.optInt("width"), source.optInt("height")),
                ),
            ),
        )
    }

    /** Last resort: trust the link's own extension. */
    private fun directLink(data: JSONObject): PostMedia? {
        val link = data.url("url_overridden_by_dest") ?: data.url("url") ?: return null
        val path = link.substringBefore('?').lowercase()
        val ratio = previewRatio(data)
        val kind = when {
            IMAGE_SUFFIXES.any(path::endsWith) -> MediaKind.IMAGE
            path.endsWith(".gif") -> MediaKind.ANIMATED
            VIDEO_SUFFIXES.any(path::endsWith) -> MediaKind.VIDEO
            else -> return null
        }

        return PostMedia(
            listOf(
                MediaAsset(
                    kind = kind,
                    url = link,
                    previewUrl = previewImage(data).takeIf { kind != MediaKind.IMAGE },
                    aspectRatio = ratio,
                    hasAudio = kind == MediaKind.VIDEO,
                ),
            ),
        )
    }

    private fun previewImage(data: JSONObject): String? =
        data.optJSONObject("preview")
            ?.optJSONArray("images")
            ?.optJSONObject(0)
            ?.optJSONObject("source")
            ?.url("url")

    private fun previewRatio(data: JSONObject): Float {
        val source = data.optJSONObject("preview")
            ?.optJSONArray("images")
            ?.optJSONObject(0)
            ?.optJSONObject("source")
            ?: return 16f / 9f
        return ratioOf(source.optInt("width"), source.optInt("height"))
    }

    private fun ratioOf(width: Int, height: Int): Float {
        if (width <= 0 || height <= 0) return 16f / 9f
        return (width.toFloat() / height).coerceIn(MIN_RATIO, MAX_RATIO)
    }

    private fun JSONObject.text(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf(String::isNotEmpty)
    }

    /** Reddit HTML-escapes URLs inside JSON, and only https sources are worth returning. */
    private fun JSONObject.url(key: String): String? =
        text(key)
            ?.replace("&amp;", "&")
            ?.replace("&#x2F;", "/")
            ?.takeIf { it.startsWith("https://") }

    private val IMAGE_SUFFIXES = listOf(".jpg", ".jpeg", ".png", ".webp", ".avif")
    private val VIDEO_SUFFIXES = listOf(".mp4", ".webm", ".m3u8", ".mpd")
}
