package app.otter.client

import android.app.Application
import android.os.Build
import app.otter.client.data.OtterHttp
import app.otter.client.data.OtterPreferences
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

/**
 * Installs the shared image loader.
 *
 * Reddit serves plenty of posts whose only form is a GIF, and the default loader would render
 * one frozen frame. Registering the animated decoder makes those posts move wherever an image
 * is drawn — feed thumbnail, post header, or the full-screen viewer.
 */
class OtterApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Naming the preference files here starts their background load now, so the view model's
        // constructor is not the thing that first touches disk while the first frame is being
        // built. See OtterPreferences.
        OtterPreferences.warm(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // ImageDecoder landed in API 28; older releases fall back to Coil's own decoder.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                // Coil would otherwise build a second OkHttp client, with its own connection
                // pool and dispatcher threads, alongside the one the Reddit API adapter uses.
                add(OkHttpNetworkFetcherFactory(callFactory = { OtterHttp.client }))
            }
            // Declared rather than left to the default so the size is a decision: a feed of
            // thumbnails and previews is re-requested constantly while scrolling back over
            // posts already seen, and those bytes should not come off the network twice.
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()

    private companion object {
        const val IMAGE_DISK_CACHE_BYTES = 128L * 1024L * 1024L
    }
}
