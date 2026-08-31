package app.otter.client.data

import app.otter.client.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditVideoLinksTest {
    @Test
    fun namesTheStreamsUnderneathABareAddress() {
        val asset = checkNotNull(RedditVideoLinks.asset("https://v.redd.it/abc123"))

        assertEquals(MediaKind.VIDEO, asset.kind)
        assertEquals("https://v.redd.it/abc123/HLSPlaylist.m3u8", asset.url)
        assertEquals("https://v.redd.it/abc123/DASHPlaylist.mpd", asset.fallbackUrl)
        assertTrue(asset.hasAudio)
    }

    @Test
    fun leavesALinkThatAlreadyNamesAFileAlone() {
        // Directly playable already; a manifest would only cost an extra round trip.
        assertNull(RedditVideoLinks.asset("https://v.redd.it/abc123/DASH_720.mp4"))
        assertEquals("abc123", RedditVideoLinks.idFrom("https://v.redd.it/abc123/DASH_720.mp4"))
    }

    @Test
    fun ignoresEverythingThatIsNotRedditVideo() {
        assertNull(RedditVideoLinks.idFrom("https://i.redd.it/abc123.jpg"))
        assertNull(RedditVideoLinks.asset("https://redgifs.com/watch/abc123"))
    }

    @Test
    fun survivesATrailingSlashAndAQueryString() {
        assertEquals("abc123", RedditVideoLinks.idFrom("https://v.redd.it/abc123/?utm_source=x"))
        assertEquals(
            "https://v.redd.it/abc123/HLSPlaylist.m3u8",
            RedditVideoLinks.asset("https://v.redd.it/abc123?utm_source=x")?.url,
        )
    }
}
