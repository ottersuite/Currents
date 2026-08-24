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
}
