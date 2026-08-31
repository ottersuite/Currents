package app.otter.client.data

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedGifsClientTest {
    @Test
    fun linksAreRecognisedInEveryShapeRedditCarriesThem() {
        assertEquals("somename", RedGifsClient.idFrom("https://www.redgifs.com/watch/somename"))
        assertEquals("somename", RedGifsClient.idFrom("https://redgifs.com/ifr/somename"))
        assertEquals("somename", RedGifsClient.idFrom("https://i.redgifs.com/i/somename.mp4"))
        assertEquals("somename", RedGifsClient.idFrom("https://v3.redgifs.com/watch/SomeName"))
        assertEquals(
            "somename",
            RedGifsClient.idFrom("https://www.redgifs.com/watch/somename?ref=share#t=3"),
        )

        assertNull(RedGifsClient.idFrom("https://v.redd.it/abc/DASH_480.mp4"))
        assertNull(RedGifsClient.idFrom("https://example.com/watch/somename"))
    }

    @Test
    fun aResolvedGifLeadsWithTheStreamThatCarriesItsAudio() = runBlocking {
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/auth/temporary") -> json("""{"token":"t"}""")
                else -> json(
                    """
                    {"gif": {
                      "urls": {
                        "hd": "https://media.redgifs.com/name.mp4",
                        "sd": "https://media.redgifs.com/name-mobile.mp4",
                        "silent": "https://media.redgifs.com/name-silent.mp4",
                        "poster": "https://media.redgifs.com/name-poster.jpg"
                      },
                      "hasAudio": true, "duration": 12.5, "width": 1280, "height": 720
                    }}
                    """.trimIndent(),
                )
            }
        }

        val asset = checkNotNull(client.resolve("https://www.redgifs.com/watch/name"))

        assertEquals("https://media.redgifs.com/name.mp4", asset.url)
        assertTrue(asset.hasAudio)
        assertEquals(12, asset.durationSeconds)
        // The silent variant exists and is deliberately never chosen; it is the whole problem.
        assertTrue(asset.playbackUrls.none { url -> url.contains("silent") })
    }

    @Test
    fun anExpiredTokenIsReplacedAndTheLookupRetriedOnce() = runBlocking {
        var tokensIssued = 0
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/auth/temporary") -> {
                    tokensIssued++
                    json("""{"token":"token-$tokensIssued"}""")
                }
                request.header("Authorization") == "Bearer token-1" ->
                    json("""{"message":"expired"}""", code = 401)
                else -> json(
                    """{"gif": {"urls": {"hd": "https://media.redgifs.com/n.mp4"},
                       "hasAudio": true, "width": 100, "height": 100}}""",
                )
            }
        }

        val asset = checkNotNull(client.resolve("https://www.redgifs.com/watch/name"))

        assertEquals("https://media.redgifs.com/n.mp4", asset.url)
        assertEquals(2, tokensIssued)
    }

    @Test
    fun afailedLookupResolvesToNothingRatherThanThrowing() = runBlocking {
        val notFound = client { request ->
            if (request.url.encodedPath.endsWith("/auth/temporary")) {
                json("""{"token":"t"}""")
            } else {
                json("""{"message":"gone"}""", code = 404)
            }
        }
        assertNull(notFound.resolve("https://www.redgifs.com/watch/name"))

        // The caller keeps whatever it already had, so a transport failure must not propagate.
        val offline = client { error("network is down") }
        assertNull(offline.resolve("https://www.redgifs.com/watch/name"))

        // Not a RedGifs link at all: answered without any request being made.
        val untouched = client { error("should not be called") }
        assertNull(untouched.resolve("https://v.redd.it/abc/DASH_480.mp4"))
    }

    @Test
    fun aSecondLookupOfTheSameGifIsServedWithoutAnotherRequest() = runBlocking {
        var gifRequests = 0
        val client = client { request ->
            if (request.url.encodedPath.endsWith("/auth/temporary")) {
                json("""{"token":"t"}""")
            } else {
                gifRequests++
                json(
                    """{"gif": {"urls": {"hd": "https://media.redgifs.com/n.mp4"},
                       "hasAudio": true, "width": 100, "height": 100}}""",
                )
            }
        }

        // Prefetching on post open is what makes the second call free; without the cache the
        // viewer would pay the round trip all over again a moment later.
        client.prefetch("https://www.redgifs.com/watch/name")
        val asset = checkNotNull(client.resolve("https://www.redgifs.com/watch/NAME"))

        assertEquals("https://media.redgifs.com/n.mp4", asset.url)
        assertEquals(1, gifRequests)
    }

    @Test
    fun aCachedLinkIsAbandonedBeforeItsSignatureCouldHaveExpired() = runBlocking {
        var gifRequests = 0
        var clock = 0L
        val client = RedGifsClient(
            httpClient = OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        val request = chain.request()
                        if (request.url.encodedPath.endsWith("/auth/temporary")) {
                            json("""{"token":"t"}""")
                        } else {
                            gifRequests++
                            json(
                                """{"gif": {"urls": {"hd": "https://media.redgifs.com/n.mp4"},
                                   "hasAudio": true, "width": 100, "height": 100}}""",
                            )
                        }
                    },
                )
                .build(),
            nowMillis = { clock },
        )

        client.resolve("https://www.redgifs.com/watch/name")
        clock += 60L * 60L * 1000L
        client.resolve("https://www.redgifs.com/watch/name")

        // A stale signed URL fails at the player, which is worse than simply asking again.
        assertEquals(2, gifRequests)
    }

    private fun client(handler: (Request) -> Response) = RedGifsClient(
        httpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build(),
    )

    private fun json(payload: String, code: Int = 200): Response = Response.Builder()
        .request(Request.Builder().url("https://api.redgifs.com/v2/stub").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code == 200) "OK" else "Error")
        .body(payload.toResponseBody("application/json".toMediaType()))
        .build()
}
