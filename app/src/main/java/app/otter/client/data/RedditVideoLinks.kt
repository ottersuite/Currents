package app.otter.client.data

import app.otter.client.model.MediaAsset
import app.otter.client.model.MediaKind

/**
 * Turns a bare `v.redd.it` address into something a player can actually open.
 *
 * Reddit hosts its own video at `https://v.redd.it/<id>`, but that address serves a web page —
 * the streams sit underneath it at fixed names. A post Reddit expanded carries them in its
 * `reddit_video` block, so this never comes up. What does not carry them is everything else that
 * points at the same video: a link post Reddit never expanded, a crosspost whose parent was not
 * included in the listing, and a link written into a comment. Those were leaving for a browser,
 * or opening a viewer holding a URL no extractor could read.
 *
 * Both manifests are offered because networks differ in which one they will carry, and the
 * player falls through its sources in order.
 */
internal object RedditVideoLinks {
    /** The video id in a `v.redd.it` address, or null when the link is not one. */
    fun idFrom(url: String): String? =
        ID.find(url.substringBefore('?'))?.groupValues?.get(1)

    /**
     * A playable asset for a `v.redd.it` link, or null when there is nothing to add.
     *
     * A link that already names a file — `DASH_720.mp4` and the like — is left alone: it is
     * directly playable, and the ordinary link handling opens it with fewer round trips than a
     * manifest costs.
     */
    fun asset(
        url: String,
        previewUrl: String? = null,
        aspectRatio: Float = 16f / 9f,
    ): MediaAsset? {
        val id = idFrom(url) ?: return null
        if (namesAFile(url)) return null
        return MediaAsset(
            kind = MediaKind.VIDEO,
            url = "https://v.redd.it/$id/HLSPlaylist.m3u8",
            fallbackUrl = "https://v.redd.it/$id/DASHPlaylist.mpd",
            previewUrl = previewUrl,
            aspectRatio = aspectRatio,
            // The progressive files Reddit generates are video-only; the audio track exists but
            // only these manifests combine it, so a clip opened this way is worth unmuting.
            hasAudio = true,
        )
    }

    private fun namesAFile(url: String): Boolean =
        url.substringBefore('?').substringAfterLast('/').contains('.')

    private val ID = Regex("""v\.redd\.it/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
}
