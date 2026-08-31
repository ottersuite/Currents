package app.otter.client.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubmissionUrlTest {
    @Test
    fun normalize_promotesASchemelessAddressToHttps() {
        assertEquals("https://example.com/story", normalizeSubmissionUrl("  example.com/story  "))
    }

    @Test
    fun normalize_keepsAnAddressThatAlreadyHasAScheme() {
        assertEquals("http://example.com", normalizeSubmissionUrl("http://example.com"))
        assertEquals("https://example.com/a?b=c", normalizeSubmissionUrl("https://example.com/a?b=c"))
    }

    @Test
    fun normalize_rejectsWhatRedditCouldNotUse() {
        assertNull(normalizeSubmissionUrl(""))
        assertNull(normalizeSubmissionUrl("   "))
        // Prose typed into the link field: promoting it to https still leaves nothing parseable.
        assertNull(normalizeSubmissionUrl("look at this thing"))
        // A link post is a web address; other schemes are not something Reddit will accept.
        assertNull(normalizeSubmissionUrl("ftp://example.com/file"))
    }

    @Test
    fun host_matchesHowRedditLabelsAPostsDomain() {
        assertEquals("example.com", submissionUrlHost("https://www.example.com/a/b"))
        assertEquals("news.example.co.uk", submissionUrlHost("https://news.example.co.uk"))
        assertNull(submissionUrlHost("not a url"))
    }
}
