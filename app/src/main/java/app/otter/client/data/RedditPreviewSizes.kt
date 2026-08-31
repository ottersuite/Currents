package app.otter.client.data

import org.json.JSONObject

/**
 * Chooses among the copies Reddit pre-renders for a preview image.
 *
 * Every post carries a full-size source plus a ladder of smaller renders. A list row that draws
 * a 72dp square has no use for a 1080p JPEG, and downloading fifty of them is what makes a feed
 * feel slow long after the JSON has arrived.
 */
internal object RedditPreviewSizes {
    /** The narrowest pre-rendered copy that still covers [targetWidth], if Reddit made one. */
    fun smallestCovering(image: JSONObject, targetWidth: Int): String? {
        val resolutions = image.optJSONArray("resolutions") ?: return null
        var bestWidth = Int.MAX_VALUE
        var bestUrl: String? = null

        for (index in 0 until resolutions.length()) {
            val entry = resolutions.optJSONObject(index) ?: continue
            val width = entry.optInt("width")
            if (width < targetWidth || width >= bestWidth) continue
            val url = entry.optString("url")
                .takeIf(String::isNotBlank)
                ?.replace("&amp;", "&")
                ?.replace("&#x2F;", "/")
                ?.takeIf { it.startsWith("https://") }
                ?: continue
            bestWidth = width
            bestUrl = url
        }
        return bestUrl
    }

    /**
     * The same choice, made against a gallery item's own ladder.
     *
     * A gallery describes its images in `media_metadata` rather than in the post's `preview`
     * block, and uses different keys for the same idea: `p` for the ladder, `x` for a width and
     * `u` for a URL. Without this a gallery had no ladder to read at all.
     */
    fun galleryCopyCovering(entry: JSONObject, targetWidth: Int): String? {
        val previews = entry.optJSONArray("p") ?: return null
        var bestWidth = Int.MAX_VALUE
        var bestUrl: String? = null

        for (index in 0 until previews.length()) {
            val preview = previews.optJSONObject(index) ?: continue
            val width = preview.optInt("x")
            if (width < targetWidth || width >= bestWidth) continue
            val url = preview.previewUrl() ?: continue
            bestWidth = width
            bestUrl = url
        }
        return bestUrl
    }

    /** The widest copy in a gallery item's ladder, for when none of them covers the target. */
    fun largestGalleryCopy(entry: JSONObject): String? {
        val previews = entry.optJSONArray("p") ?: return null
        return (previews.length() - 1 downTo 0).firstNotNullOfOrNull { index ->
            previews.optJSONObject(index)?.previewUrl()
        }
    }

    private fun JSONObject.previewUrl(): String? =
        optString("u")
            .takeIf(String::isNotBlank)
            ?.replace("&amp;", "&")
            ?.replace("&#x2F;", "/")
            ?.takeIf { it.startsWith("https://") }
}
