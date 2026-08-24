package app.otter.client.data

import app.otter.client.model.MediaKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditMediaParserTest {
    @Test
    fun hostedVideoPrefersTheAdaptiveStreamAndKeepsTheProgressiveFallback() {
        val media = checkNotNull(
            RedditMediaParser.parse(
                JSONObject(
                    """
                    {
                      "is_video": true,
                      "secure_media": {
                        "reddit_video": {
                          "hls_url": "https://v.redd.it/abc/HLSPlaylist.m3u8?a=1&amp;b=2",
                          "dash_url": "https://v.redd.it/abc/DASHPlaylist.mpd",
                          "fallback_url": "https://v.redd.it/abc/DASH_720.mp4?source=fallback",
                          "width": 1280, "height": 720, "duration": 42, "is_gif": false
                        }
                      }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val asset = media.first
        assertEquals(MediaKind.VIDEO, asset.kind)
        // The escaped ampersand has to survive, or the CDN rejects the signed URL.
        assertEquals("https://v.redd.it/abc/HLSPlaylist.m3u8?a=1&b=2", asset.url)
        assertEquals("https://v.redd.it/abc/DASH_720.mp4", asset.fallbackUrl)
        assertEquals(42, asset.durationSeconds)
        assertTrue(asset.hasAudio)
        assertEquals(1280f / 720f, asset.aspectRatio, .001f)
    }

    @Test
    fun hostedGifIsAnimatedRatherThanVideo() {
        val media = checkNotNull(
            RedditMediaParser.parse(
                JSONObject(
                    """
                    {
                      "is_video": true,
                      "media": {
                        "reddit_video": {
                          "fallback_url": "https://v.redd.it/xyz/DASH_480.mp4",
                          "width": 480, "height": 480, "duration": 3,
                          "is_gif": true, "has_audio": true
                        }
                      }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals(MediaKind.ANIMATED, media.first.kind)
        // is_gif controls looping presentation; it must not discard an advertised audio track.
        assertTrue(media.first.hasAudio)
    }

    @Test
    fun gifPostUsesRedditsMp4EncodingWithTheGifAsFallback() {
        val media = checkNotNull(
            RedditMediaParser.parse(
                JSONObject(
                    """
                    {
                      "preview": {
                        "images": [{
                          "source": {"url": "https://preview.redd.it/still.png", "width": 600, "height": 400},
                          "variants": {
                            "mp4": {"source": {"url": "https://preview.redd.it/clip.mp4", "width": 600, "height": 400}},
                            "gif": {"source": {"url": "https://preview.redd.it/clip.gif", "width": 600, "height": 400}}
                          }
                        }]
                      }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val asset = media.first
        assertEquals(MediaKind.ANIMATED, asset.kind)
        assertEquals("https://preview.redd.it/clip.mp4", asset.url)
        assertEquals("https://preview.redd.it/clip.gif", asset.fallbackUrl)
        assertEquals("https://preview.redd.it/still.png", asset.previewUrl)
        // The GIF fallback is not a player source; it is what the image loader animates.
        assertEquals(listOf(asset.url), asset.playbackUrls)
        assertEquals(asset.fallbackUrl, asset.animatedImageUrl)
        assertTrue(asset.needsPlayer)
    }

    @Test
    fun galleryKeepsRedditsOrderCaptionsAndPerItemKinds() {
        val media = checkNotNull(
            RedditMediaParser.parse(
                JSONObject(
                    """
                    {
                      "is_gallery": true,
                      "gallery_data": {"items": [
                        {"media_id": "one", "caption": "First frame"},
                        {"media_id": "two"},
                        {"media_id": "missing"}
                      ]},
                      "media_metadata": {
                        "one": {"status": "valid", "e": "Image", "s": {"u": "https://preview.redd.it/one.jpg", "x": 1000, "y": 500}},
                        "two": {"status": "valid", "e": "AnimatedImage", "s": {"mp4": "https://preview.redd.it/two.mp4", "gif": "https://preview.redd.it/two.gif", "x": 400, "y": 400}}
                      }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertTrue(media.isGallery)
        assertEquals(2, media.assets.size)
        assertEquals(MediaKind.IMAGE, media.assets[0].kind)
        assertEquals("First frame", media.assets[0].caption)
        assertEquals(2f, media.assets[0].aspectRatio, .001f)
        assertEquals(MediaKind.ANIMATED, media.assets[1].kind)
        assertEquals("https://preview.redd.it/two.mp4", media.assets[1].url)
        assertEquals("https://preview.redd.it/two.gif", media.assets[1].fallbackUrl)
        assertNull(media.assets[1].caption)
    }

    @Test
    fun crosspostFallsBackToTheMediaOnThePostItCopied() {
        val media = checkNotNull(
            RedditMediaParser.parse(
                JSONObject(
                    """
                    {
                      "crosspost_parent_list": [{
                        "secure_media": {"reddit_video": {
                          "hls_url": "https://v.redd.it/parent/HLSPlaylist.m3u8",
                          "width": 1920, "height": 1080, "duration": 10
                        }}
                      }]
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals("https://v.redd.it/parent/HLSPlaylist.m3u8", media.first.url)
    }

    @Test
    fun directLinksAreClassifiedByExtensionAndTextPostsCarryNoMedia() {
        fun parse(url: String) = RedditMediaParser.parse(JSONObject("""{"url": "$url"}"""))

        assertEquals(MediaKind.IMAGE, checkNotNull(parse("https://i.redd.it/a.jpg")).first.kind)
        val gif = checkNotNull(parse("https://i.redd.it/a.gif")).first
        assertEquals(MediaKind.ANIMATED, gif.kind)
        // Nothing can play it, so the viewer must fall back to an animated image surface.
        assertEquals(false, gif.needsPlayer)
        assertEquals("https://i.redd.it/a.gif", gif.animatedImageUrl)
        assertEquals(MediaKind.VIDEO, checkNotNull(parse("https://x.com/a.mp4")).first.kind)
        // Query strings must not hide the extension.
        assertEquals(
            MediaKind.IMAGE,
            checkNotNull(parse("https://i.redd.it/a.png?width=640&crop=smart")).first.kind,
        )
        assertNull(parse("https://example.com/article"))
        assertNull(RedditMediaParser.parse(JSONObject("""{"is_self": true, "selftext": "hi"}""")))
        // http sources are refused outright; the app forbids cleartext.
        assertNull(parse("http://i.redd.it/a.jpg"))
    }

    @Test
    fun redditsMp4VariantIsPlayableEvenThoughItsPathEndsInGif() {
        // Observed on device: the MP4 encoding and the GIF share a path and differ only by query.
        val media = checkNotNull(
            RedditMediaParser.parse(
                JSONObject(
                    """
                    {
                      "preview": {
                        "images": [{
                          "source": {"url": "https://preview.redd.it/abc.jpg", "width": 600, "height": 400},
                          "variants": {
                            "mp4": {"source": {"url": "https://preview.redd.it/abc.gif?format=mp4&amp;s=sig", "width": 600, "height": 400}},
                            "gif": {"source": {"url": "https://preview.redd.it/abc.gif?width=600&amp;s=other", "width": 600, "height": 400}}
                          }
                        }]
                      }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val asset = media.first
        assertTrue(asset.needsPlayer)
        assertEquals(
            listOf("https://preview.redd.it/abc.gif?format=mp4&s=sig"),
            asset.playbackUrls,
        )
        // The real GIF, which has no format=mp4, is still what an image surface would animate.
        assertEquals("https://preview.redd.it/abc.gif?width=600&s=other", asset.animatedImageUrl)
    }
}
