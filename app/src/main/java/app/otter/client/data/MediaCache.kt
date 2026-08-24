package app.otter.client.data

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.concurrent.Executors

/** One process-wide media cache shared by every player and the lightweight prefetcher. */
@UnstableApi
object MediaCache {
    private val lock = Any()
    private val prefetchExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "otter-media-prefetch").apply { isDaemon = true }
    }
    @Volatile private var cache: SimpleCache? = null

    fun dataSourceFactory(context: Context): DataSource.Factory {
        val upstream = DefaultDataSource.Factory(
            context.applicationContext,
            DefaultHttpDataSource.Factory()
                .setUserAgent("Otter Android media")
                .setConnectTimeoutMs(12_000)
                .setReadTimeoutMs(20_000)
                .setAllowCrossProtocolRedirects(true),
        )
        return CacheDataSource.Factory()
            .setCache(instance(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Warms only the first few megabytes of direct media; adaptive manifests load on demand. */
    fun prefetch(context: Context, urls: Iterable<String>) {
        urls.asSequence()
            .filter(::isDirectMedia)
            .distinct()
            .take(PREFETCH_ITEM_LIMIT)
            .forEach { url ->
                prefetchExecutor.execute {
                    runCatching {
                        CacheWriter(
                            CacheDataSource.Factory()
                                .setCache(instance(context))
                                .setUpstreamDataSourceFactory(
                                    DefaultHttpDataSource.Factory()
                                        .setUserAgent("Otter Android media")
                                        .setAllowCrossProtocolRedirects(true),
                                )
                                .createDataSource(),
                            DataSpec.Builder()
                                .setUri(url)
                                .setLength(PREFETCH_BYTES)
                                .build(),
                            null,
                            null,
                        ).cache()
                    }
                }
            }
    }

    private fun instance(context: Context): SimpleCache = cache ?: synchronized(lock) {
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }

    private fun isDirectMedia(url: String): Boolean {
        val lower = url.lowercase()
        val path = lower.substringBefore('?')
        return path.endsWith(".mp4") || path.endsWith(".webm") ||
            lower.contains("format=mp4") || lower.contains("v.redd.it") && !path.endsWith(".mpd")
    }

    private const val MAX_CACHE_BYTES = 256L * 1024L * 1024L
    private const val PREFETCH_BYTES = 4L * 1024L * 1024L
    private const val PREFETCH_ITEM_LIMIT = 3
}
