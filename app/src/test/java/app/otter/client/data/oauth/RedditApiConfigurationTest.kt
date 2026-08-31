package app.otter.client.data.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditApiConfigurationTest {
    @Test
    fun defaultsContainNoClientIdentity() {
        val configuration = RedditApiConfiguration()

        assertEquals("", configuration.clientId)
        assertEquals("", configuration.userAgent)
        assertEquals(RedditApiConfiguration.DEFAULT_REDIRECT_URI, configuration.redirectUri)
        assertFalse(configuration.isUsable)
    }

    @Test
    fun normalizedValidConfigurationIsUsable() {
        val configuration = RedditApiConfiguration(
            clientId = "  personal-client_1  ",
            userAgent = "  android:app.orca.client:v1.0.0 (by /u/tester)  ",
            redirectUri = "  app.orca.client://callback/reddit  ",
        ).normalized()

        assertEquals("personal-client_1", configuration.clientId)
        assertEquals("app.orca.client://callback/reddit", configuration.redirectUri)
        assertNull(configuration.validationError())
        assertTrue(configuration.isUsable)
    }

    @Test
    fun clientIdRejectsBlankWhitespaceAndPunctuation() {
        assertFalse(configuration(clientId = "").isUsable)
        assertFalse(configuration(clientId = "has space").isUsable)
        assertFalse(configuration(clientId = "client:secret").isUsable)
    }

    @Test
    fun userAgentRejectsHeaderInjectionAndControlCharacters() {
        assertFalse(configuration(userAgent = "otter\r\nInjected: true").isUsable)
        assertFalse(configuration(userAgent = "otter\u0000agent").isUsable)
    }

    @Test
    fun redirectSupportsRuntimeCustomSchemesAndVerifiedHttps() {
        assertTrue(configuration(redirectUri = "other.app://oauth/return").isUsable)
        assertTrue(configuration(redirectUri = "personal.client://oauth_return").isUsable)
        assertTrue(configuration(redirectUri = "personal.client:/oauth/return").isUsable)
        assertTrue(configuration(redirectUri = "https://example.com/callback").isUsable)
        assertFalse(configuration(redirectUri = "http://example.com/callback").isUsable)
        assertFalse(configuration(redirectUri = "app.orca.client:callback").isUsable)
        assertFalse(configuration(redirectUri = "OTHER.APP://oauth/return").isUsable)
        assertFalse(configuration(redirectUri = "inline://oauth/return").isUsable)
        assertFalse(configuration(redirectUri = "https://example.com").isUsable)
        assertFalse(configuration(redirectUri = "https://EXAMPLE.com/callback").isUsable)
        assertFalse(configuration(redirectUri = "https://127.000.000.001/callback").isUsable)
        assertFalse(configuration(redirectUri = "https://127.1/callback").isUsable)
        assertFalse(configuration(redirectUri = "https://[::1]/callback").isUsable)
        assertFalse(configuration(redirectUri = "https://example.com/a/../callback").isUsable)
        assertFalse(configuration(redirectUri = "https://example.com/../callback").isUsable)
        assertFalse(configuration(redirectUri = "https://example.com/a/%2e%2e/callback").isUsable)
        assertFalse(configuration(redirectUri = "personal.client:/a/.%2E/callback").isUsable)
        assertFalse(configuration(redirectUri = "personal.client:/café/callback").isUsable)
        assertFalse(configuration(redirectUri = "personal.client:/oauth/%2freturn").isUsable)
        assertTrue(configuration(redirectUri = "personal.client:/oauth/%2Freturn").isUsable)
    }

    @Test
    fun redirectRejectsAmbiguousOrNonBaseComponents() {
        assertFalse(configuration(redirectUri = "app.orca.client://user@oauth/reddit").isUsable)
        assertFalse(configuration(redirectUri = "app.orca.client://oauth:123/reddit").isUsable)
        assertFalse(configuration(redirectUri = "app.orca.client://oauth/reddit?mode=test").isUsable)
        assertFalse(configuration(redirectUri = "app.orca.client://oauth/reddit#fragment").isUsable)
    }

    @Test
    fun callbackMatchesExactConfiguredBaseAndAllowsOAuthQuery() {
        val custom = configuration(redirectUri = "personal.client://oauth_return/oauth/reddit")

        assertTrue(
            custom.matchesCallback(
                "personal.client://oauth_return/oauth/reddit?state=one&code=two",
            ),
        )
        assertFalse(custom.matchesCallback("personal.client://oauth_return/oauth/reddit/"))
        assertFalse(custom.matchesCallback("personal.client://other/oauth/reddit?state=one"))
        assertTrue(custom.matchesCallback("PERSONAL.CLIENT://oauth_return/oauth/reddit?state=one"))
        assertTrue(custom.matchesCallback("personal.client://oauth_return/oauth/reddit#state=one"))

        val https = configuration(redirectUri = "https://example.com/oauth/reddit")
        assertTrue(https.matchesCallback("https://example.com/oauth/reddit?state=one&code=two"))
    }

    @Test
    fun callbackTreatsAuthorityCaseAndOptionalRootSlashAsEquivalent() {
        val withoutSlash = configuration(redirectUri = "personal.client://oauth_return")
        val withSlash = configuration(redirectUri = "personal.client://oauth_return/")

        assertTrue(
            withoutSlash.matchesCallback(
                "PERSONAL.CLIENT://OAUTH_RETURN/?state=one&code=two",
            ),
        )
        assertTrue(
            withSlash.matchesCallback(
                "personal.client://oauth_return?state=one&code=two",
            ),
        )
        assertFalse(withSlash.matchesCallback("personal.client://oauth_return/other?state=one"))
    }

    @Test
    fun callbackMatchesAuthoritylessHierarchicalCustomRedirect() {
        val custom = configuration(redirectUri = "personal.client:/oauth/return")

        assertTrue(custom.matchesCallback("personal.client:/oauth/return?state=one&code=two"))
        assertFalse(custom.matchesCallback("personal.client://oauth/return?state=one&code=two"))
        assertFalse(custom.matchesCallback("personal.client:/oauth/other?state=one&code=two"))
    }

    @Test
    fun callbackIgnoresTheFragmentRedditAppendsToItsGrantRedirect() {
        val custom = configuration(redirectUri = "personal.client://oauth_return/oauth/reddit")

        // Reddit's own redirect is '...?state=..&code=..#_'; the base is what identifies it.
        assertTrue(
            custom.matchesCallback("personal.client://oauth_return/oauth/reddit?code=two#_"),
        )
        assertTrue(custom.matchesCallback("personal.client://oauth_return/oauth/reddit?code=two#"))
        assertTrue(
            custom.matchesCallback("personal.client://oauth_return/oauth/reddit?code=two#token=x"),
        )
        // A fragment still cannot stand in for a base that does not match.
        assertFalse(custom.matchesCallback("personal.client://elsewhere/oauth/reddit#code=two"))
    }

    @Test
    fun callbackBaseOmitsResponseParameters() {
        assertEquals(
            "personal.client://oauth_return/oauth/reddit",
            callbackBase("personal.client://oauth_return/oauth/reddit?state=one&code=two"),
        )
        assertEquals(
            "personal.client:/oauth/return",
            callbackBase("personal.client:/oauth/return?code=two"),
        )
    }

    @Test
    fun redirectMatcherAcceptsAnyRegisteredCallbackBaseWithoutAClientId() {
        // The in-app sign-in WebView matches on the redirect alone, before a config exists.
        val redirect = "redreader://rr_oauth_redir"

        assertTrue(
            RedditApiConfiguration.callbackMatchesRedirect(
                redirect,
                "redreader://rr_oauth_redir?state=one&code=two",
            ),
        )
        assertTrue(
            RedditApiConfiguration.callbackMatchesRedirect(
                redirect,
                "redreader://rr_oauth_redir/?error=access_denied",
            ),
        )
        assertFalse(
            RedditApiConfiguration.callbackMatchesRedirect(
                redirect,
                "https://www.reddit.com/api/v1/authorize.compact?client_id=x",
            ),
        )
        assertFalse(
            RedditApiConfiguration.callbackMatchesRedirect(
                redirect,
                "redreader://rr_oauth_redir_evil?code=two",
            ),
        )
        assertFalse(RedditApiConfiguration.callbackMatchesRedirect(redirect, "not a uri at all"))
    }

    private fun configuration(
        clientId: String = "personal-client",
        userAgent: String = "android:app.orca.client:v1.0.0 (by /u/tester)",
        redirectUri: String = RedditApiConfiguration.DEFAULT_REDIRECT_URI,
    ) = RedditApiConfiguration(clientId, userAgent, redirectUri)
}
