package app.otter.client.data

import app.otter.client.model.MediaAsset
import app.otter.client.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Resolves a RedGifs page link to the media behind it.
 *
 * Reddit does not host these. What it stores for a RedGifs post is `preview.reddit_video_preview`
 * — its own re-encode, which is silent and often truncated — so a client that plays what Reddit
 * hands it has nothing to unmute. RedGifs will give out the real file, with its audio track, to
 * an anonymous caller holding a temporary token.
 *
 * Everything here fails soft. A resolution that does not come back leaves the caller with the
 * silent preview it already had, which is the behaviour this replaces rather than a broken one.
 */
internal class RedGifsClient(
    private val httpClient: OkHttpClient = OtterHttp.client,
    private val baseUrl: String = "https://api.redgifs.com/v2",
    private val userAgent: String = "otter-android",
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val tokenLock = Mutex()
    private var token: String? = null
    private var tokenIssuedAtMillis = 0L

    private val cacheLock = Mutex()
    private val cache = LinkedHashMap<String, CachedAsset>()

    /**
     * Fetches the token before anything needs it.
     *
     * The first lookup of a session otherwise costs two round trips where every later one costs
     * one, and that first one is the wait somebody actually notices.
     */
    suspend fun warmUp() {
        runCatching { bearer(forceRefresh = false) }
    }

    /**
     * Resolves ahead of time so opening is instant, ignoring whether it worked.
     *
     * Called when a post is opened, on the bet that media on screen is about to be tapped. If
     * the bet is wrong it cost one request; if it is right the swap has already happened by the
     * time the viewer appears.
     */
    suspend fun prefetch(pageUrl: String) {
        resolve(pageUrl)
    }

    /** The resolved file, or null when this is not a RedGifs link or the lookup did not work. */
    suspend fun resolve(pageUrl: String): MediaAsset? = withContext(Dispatchers.IO) {
        val id = idFrom(pageUrl) ?: return@withContext null
        cached(id)?.let { return@withContext it }
        runCatching { requestGif(id, bearer(forceRefresh = false)) }
            .recoverCatching { error ->
                // A rejected token is the one failure worth a second attempt: temporary tokens
                // expire on RedGifs' schedule, not on one this client can predict.
                if (error is ExpiredTokenException) {
                    requestGif(id, bearer(forceRefresh = true))
                } else {
                    throw error
                }
            }
            .getOrNull()
            ?.also { asset -> remember(id, asset) }
    }

    private suspend fun cached(id: String): MediaAsset? = cacheLock.withLock {
        val entry = cache[id] ?: return@withLock null
        // These URLs are signed and expire. The window here is deliberately far shorter than any
        // plausible signature lifetime: a cached link that has gone stale fails at the player,
        // which is a worse outcome than simply asking again.
        if (nowMillis() - entry.resolvedAtMillis > CACHE_TTL_MILLIS) {
            cache.remove(id)
            return@withLock null
        }
        entry.asset
    }

    private suspend fun remember(id: String, asset: MediaAsset) = cacheLock.withLock {
        cache.remove(id)
        cache[id] = CachedAsset(asset, nowMillis())
        while (cache.size > MAX_CACHED) {
            cache.remove(cache.keys.first())
        }
    }

    private class CachedAsset(val asset: MediaAsset, val resolvedAtMillis: Long)

    private suspend fun bearer(forceRefresh: Boolean): String = tokenLock.withLock {
        val cached = token
        val fresh = cached != null &&
            !forceRefresh &&
            nowMillis() - tokenIssuedAtMillis < TOKEN_TTL_MILLIS
        if (fresh) return@withLock cached!!

        val request = Request.Builder()
            .url("$baseUrl/auth/temporary")
            .header("User-Agent", userAgent)
            .build()
        val issued = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("RedGifs auth ${response.code}")
            JSONObject(response.body.string()).optString("token").takeIf(String::isNotBlank)
                ?: throw IOException("RedGifs issued no token")
        }
        token = issued
        tokenIssuedAtMillis = nowMillis()
        issued
    }

    private fun requestGif(id: String, bearer: String): MediaAsset? {
        val request = Request.Builder()
            .url("$baseUrl/gifs/$id")
            .header("Authorization", "Bearer $bearer")
            .header("User-Agent", userAgent)
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (response.code == UNAUTHORIZED) throw ExpiredTokenException()
            if (!response.isSuccessful) return null
            val gif = JSONObject(response.body.string()).optJSONObject("gif") ?: return null
            gif.toAsset()
        }
    }

    private fun JSONObject.toAsset(): MediaAsset? {
        val urls = optJSONObject("urls") ?: return null
        // `hd` and `sd` both carry the audio track; `silent` deliberately does not, so it is only
        // ever a last resort and never worth preferring.
        val primary = urls.nonBlank("hd") ?: urls.nonBlank("sd") ?: return null
        val width = optInt("width")
        val height = optInt("height")
        return MediaAsset(
            kind = MediaKind.VIDEO,
            url = primary,
            fallbackUrl = urls.nonBlank("sd")?.takeIf { it != primary },
            previewUrl = urls.nonBlank("poster") ?: urls.nonBlank("thumbnail"),
            aspectRatio = if (width > 0 && height > 0) {
                (width.toFloat() / height).coerceIn(MIN_RATIO, MAX_RATIO)
            } else {
                DEFAULT_RATIO
            },
            hasAudio = optBoolean("hasAudio", true),
            durationSeconds = optDouble("duration", 0.0).toInt().coerceAtLeast(0),
        )
    }

    private fun JSONObject.nonBlank(key: String): String? =
        optString(key).takeIf(String::isNotBlank)

    private class ExpiredTokenException : IOException("RedGifs token expired")

    companion object {
        /**
         * The id inside a RedGifs link, or null when the link is not one.
         *
         * Covers the watch page, the embeddable player, and direct file links, since a Reddit
         * post can carry any of the three depending on how it was submitted.
         */
        fun idFrom(url: String): String? =
            ID_PATTERN.find(url.substringBefore('?').substringBefore('#'))
                ?.groupValues
                ?.get(1)
                ?.lowercase()
                ?.takeIf(String::isNotBlank)

        private val ID_PATTERN =
            Regex("""redgifs\.com/(?:watch|ifr|i)/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)

        private const val UNAUTHORIZED = 401

        // Temporary tokens outlive a browsing session comfortably; this only bounds how long a
        // stale one can sit unused, and a rejection is caught and retried regardless.
        private const val TOKEN_TTL_MILLIS = 6L * 60L * 60L * 1000L

        private const val CACHE_TTL_MILLIS = 25L * 60L * 1000L
        private const val MAX_CACHED = 32

        private const val MIN_RATIO = .2f
        private const val MAX_RATIO = 5f
        private const val DEFAULT_RATIO = 16f / 9f
    }
}
