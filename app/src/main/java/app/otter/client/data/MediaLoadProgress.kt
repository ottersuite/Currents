package app.otter.client.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/**
 * How far along each in-flight image download is, keyed by URL.
 *
 * A spinner says something is happening; it does not say whether a gallery page is a moment away
 * or a slow megabyte away. Reddit sends `Content-Length` on its image hosts, so the bytes can be
 * counted as they arrive and turned into a real fraction.
 *
 * Only image responses carrying a length are tracked. Anything else — API JSON, chunked
 * responses, video handled by its own player — passes through untouched and simply has no entry,
 * which callers should treat as "loading, length unknown".
 */
object MediaLoadProgress {
    private val _fractions = MutableStateFlow<Map<String, Float>>(emptyMap())
    val fractions: StateFlow<Map<String, Float>> = _fractions.asStateFlow()

    /**
     * Measures image response bodies as they are read.
     *
     * Installed as a *network* interceptor so it sees the bytes actually coming off the wire,
     * and a `Content-Length` that still describes them.
     */
    val interceptor: Interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val body = response.body
        val length = body.contentLength()
        if (!body.isTrackableImage(length)) {
            response
        } else {
            val url = chain.request().url.toString()
            publish(url, 0f)
            response.newBuilder()
                .body(
                    ProgressResponseBody(body) { read ->
                        if (read >= length) forget(url) else publish(url, read.toFloat() / length)
                    },
                )
                .build()
        }
    }

    private fun ResponseBody.isTrackableImage(length: Long): Boolean =
        length > 0L && contentType()?.type.equals("image", ignoreCase = true)

    private fun publish(url: String, fraction: Float) {
        _fractions.update { current ->
            // A cap in case a response is abandoned mid-flight and never reports completion.
            val trimmed = if (current.size >= MAX_TRACKED && url !in current) {
                current - current.keys.first()
            } else {
                current
            }
            trimmed + (url to fraction.coerceIn(0f, 1f))
        }
    }

    private fun forget(url: String) {
        _fractions.update { current -> if (url in current) current - url else current }
    }

    private inline fun MutableStateFlow<Map<String, Float>>.update(
        transform: (Map<String, Float>) -> Map<String, Float>,
    ) {
        while (true) {
            val current = value
            val next = transform(current)
            if (next === current || compareAndSet(current, next)) return
        }
    }

    private const val MAX_TRACKED = 24
}

/** Reports cumulative bytes read as the body is consumed. */
private class ProgressResponseBody(
    private val delegate: ResponseBody,
    private val onRead: (Long) -> Unit,
) : ResponseBody() {
    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    private val countingSource: BufferedSource by lazy {
        object : ForwardingSource(delegate.source()) {
            private var total = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read > 0L) {
                    total += read
                    onRead(total)
                }
                return read
            }
        }.buffer()
    }

    override fun source(): BufferedSource = countingSource
}
