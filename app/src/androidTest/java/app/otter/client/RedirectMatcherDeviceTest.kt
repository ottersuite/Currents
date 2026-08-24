package app.otter.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.client.data.oauth.RedditApiConfiguration
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android's java.net.URI is not the desktop JVM's. This pins the callback matcher to the parser
 * that actually runs on a phone, for the registry-style authority a borrowed redirect URI uses.
 */
@RunWith(AndroidJUnit4::class)
class RedirectMatcherDeviceTest {
    private val redirect = "redreader://rr_oauth_redir"

    @Test
    fun androidUriParsesAnUnderscoreAuthorityTheSameWayTheMatcherAssumes() {
        val parsed = URI("$redirect?state=abc&code=xyz")

        assertEquals("redreader", parsed.scheme)
        assertEquals("rr_oauth_redir", parsed.rawAuthority)
        assertEquals("", parsed.rawPath)
        assertEquals(-1, parsed.port)
        assertEquals(null, parsed.userInfo)
        assertEquals(null, parsed.rawFragment)
    }

    @Test
    fun matcherAcceptsTheCallbackShapeRedditActuallyReturns() {
        assertTrue(
            RedditApiConfiguration.callbackMatchesRedirect(redirect, "$redirect?state=abc&code=xyz"),
        )
        assertTrue(
            RedditApiConfiguration.callbackMatchesRedirect(
                redirect,
                "$redirect?state=abc-_9&code=xyz-_9",
            ),
        )
        assertTrue(RedditApiConfiguration.callbackMatchesRedirect(redirect, redirect))
        assertTrue(RedditApiConfiguration.callbackMatchesRedirect(redirect, "$redirect/?code=xyz"))
    }
}
