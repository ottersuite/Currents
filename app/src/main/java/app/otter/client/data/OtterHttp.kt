package app.otter.client.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The one OkHttp client this process uses.
 *
 * A client owns its connection pool, its dispatcher and that dispatcher's threads, so a second
 * client is a second copy of all three. The image loader built one and the Reddit API adapter
 * built another, which meant two pools that could never reuse each other's connections even
 * when a preview and an API call went to the same host.
 *
 * Built lazily: unit tests supply their own client and must not pay for this one.
 */
object OtterHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Counts image bytes so the viewer can show real progress instead of a spinner.
            // Non-image responses are passed straight through.
            .addNetworkInterceptor(MediaLoadProgress.interceptor)
            .build()
    }

    private const val CONNECT_TIMEOUT_SECONDS = 12L
    private const val READ_TIMEOUT_SECONDS = 18L
}
