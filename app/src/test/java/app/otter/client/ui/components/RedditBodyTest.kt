package app.otter.client.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditBodyTest {
    @Test
    fun redditsGiphyEmbedBecomesAViewableGifLink() {
        val segments = parseRedditBody("that's me ![gif](giphy|3o7TKz2eMXx7dn95FS) exactly")

        val link = segments.filterIsInstance<BodySegment.Link>().single()
        assertEquals("View GIF", link.label)
        assertEquals("https://i.giphy.com/media/3o7TKz2eMXx7dn95FS/giphy.mp4", link.url)
        assertTrue(link.isMedia)
        // The words around it survive untouched.
        assertEquals(
            listOf("that's me ", " exactly"),
            segments.filterIsInstance<BodySegment.Plain>().map { it.text },
        )
    }

    @Test
    fun giphyEmbedWithASizeSuffixStillResolves() {
        val segments = parseRedditBody("![gif](giphy|BpGWitbFZflfSUYuZ9|downsized)")

        val link = segments.filterIsInstance<BodySegment.Link>().single()
        assertEquals("https://i.giphy.com/media/BpGWitbFZflfSUYuZ9/giphy.mp4", link.url)
    }

    @Test
    fun markdownLinksKeepTheLabelTheAuthorWrote() {
        val segments = parseRedditBody("see [the docs](https://example.com/guide) for more")

        val link = segments.filterIsInstance<BodySegment.Link>().single()
        assertEquals("the docs", link.label)
        assertEquals("https://example.com/guide", link.url)
        assertEquals(false, link.isMedia)
    }

    @Test
    fun aBareGifUrlIsNamedRatherThanPrinted() {
        val segments = parseRedditBody("proof: https://i.imgur.com/abc123.gif")

        val link = segments.filterIsInstance<BodySegment.Link>().single()
        assertEquals("View GIF", link.label)
        assertEquals("https://i.imgur.com/abc123.gif", link.url)
        assertTrue(link.isMedia)
    }

    @Test
    fun videoLinksSayVideoAndOrdinaryUrlsAreShortened() {
        val clip = parseRedditBody("https://v.redd.it/xyz/DASH_720.mp4")
            .filterIsInstance<BodySegment.Link>()
            .single()
        assertEquals("View video", clip.label)

        val article = parseRedditBody("https://www.example.com/a/very/long/article/path")
            .filterIsInstance<BodySegment.Link>()
            .single()
        assertEquals("example.com/a/very/long/article/path", article.label)
    }

    @Test
    fun emotesLeaveNothingBehindAndPlainTextIsUnchanged() {
        val emote = parseRedditBody("nice ![img](emote|t5_2th52|4271) one")
        assertTrue(emote.filterIsInstance<BodySegment.Link>().isEmpty())
        assertEquals(
            "nice  one",
            emote.filterIsInstance<BodySegment.Plain>().joinToString("") { it.text },
        )

        val plain = parseRedditBody("just words, no links here")
        assertEquals(listOf(BodySegment.Plain("just words, no links here")), plain)
    }
}
