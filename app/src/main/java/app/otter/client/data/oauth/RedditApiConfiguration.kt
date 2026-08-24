package app.otter.client.data.oauth

import java.net.URI
import java.util.Locale

/** Public OAuth metadata for a Reddit installed app. No client secret is used. */
data class RedditApiConfiguration(
    val clientId: String = "",
    val userAgent: String = "",
    val redirectUri: String = DEFAULT_REDIRECT_URI,
) {
    fun normalized(): RedditApiConfiguration = copy(
        clientId = clientId.trim(),
        userAgent = userAgent.trim(),
        redirectUri = redirectUri.trim(),
    )

    fun validationError(): String? {
        val value = normalized()
        if (value.clientId.isEmpty()) return "Client ID is required"
        if (value.clientId.length > MAX_CLIENT_ID_LENGTH) return "Client ID is too long"
        if (!CLIENT_ID_PATTERN.matches(value.clientId)) {
            return "Client ID may contain only letters, numbers, hyphens, and underscores"
        }
        if (value.userAgent.isEmpty()) return "User-Agent is required"
        if (value.userAgent.length > MAX_USER_AGENT_LENGTH) return "User-Agent is too long"
        if (value.userAgent.any { character -> character.code !in 0x20..0x7e }) {
            return "User-Agent must use printable characters and cannot contain line breaks"
        }
        if (value.redirectUri.isEmpty()) return "Redirect URI is required"
        if (value.redirectUri.length > MAX_REDIRECT_URI_LENGTH) return "Redirect URI is too long"
        if (value.redirectUri.any { character -> character.isWhitespace() || character.isISOControl() }) {
            return "Redirect URI cannot contain spaces or control characters"
        }
        if (value.redirectUri.any { character -> character.code !in 0x21..0x7e }) {
            return "Redirect URI must use ASCII characters; percent-encode any non-ASCII path text"
        }
        val redirect = value.parsedRedirect()
            ?: return "Redirect URI is not a valid callback URI"
        val scheme = redirect.scheme
        if (scheme.isNullOrBlank() || !SCHEME_PATTERN.matches(scheme)) {
            return "Redirect URI must use a lowercase URI scheme"
        }
        if (scheme in BLOCKED_SCHEMES) {
            return if (scheme == "http") {
                "Redirect URI must use HTTPS or a custom scheme; plain HTTP is not supported"
            } else {
                "That redirect URI scheme is not supported"
            }
        }
        if (redirect.isOpaque) {
            return "Redirect URI must use hierarchical form, such as $DEFAULT_REDIRECT_URI"
        }
        val rawPath = redirect.rawPath.orEmpty()
        if (containsDotEquivalentSegment(rawPath)) {
            return "Redirect URI path cannot contain dot segments"
        }
        if (hasNonCanonicalPercentEscape(rawPath)) {
            return "Redirect URI path must use uppercase hexadecimal percent escapes"
        }
        if (scheme == "https") {
            val host = redirect.host
            if (redirect.rawAuthority.isNullOrBlank() || host.isNullOrBlank()) {
                return "HTTPS redirect URI must include a valid domain"
            }
            if (host != host.lowercase(Locale.ROOT) || host.endsWith('.')) {
                return "HTTPS redirect URI must use a lowercase canonical domain"
            }
            if (host.all { character -> character.isDigit() || character == '.' } || ':' in host) {
                return "HTTPS redirect URI must use a verified domain, not an IP address"
            }
            if (!rawPath.startsWith('/')) {
                return "HTTPS redirect URI must include an explicit path, such as /oauth/callback"
            }
        } else if (redirect.rawAuthority.isNullOrBlank() && redirect.rawPath.isNullOrBlank()) {
            return "Redirect URI must include an authority or callback path"
        }
        if ('%' in redirect.rawAuthority.orEmpty()) {
            return "Redirect URI authority cannot use percent encoding"
        }
        if (redirect.rawUserInfo != null || '@' in redirect.rawAuthority.orEmpty()) {
            return "Redirect URI cannot include user information"
        }
        if (redirect.port != -1 || ':' in redirect.rawAuthority.orEmpty()) {
            return "Redirect URI cannot include user information or a port"
        }
        if (redirect.rawQuery != null || redirect.rawFragment != null) {
            return "Redirect URI cannot include a query or fragment"
        }
        return null
    }

    val isUsable: Boolean
        get() = validationError() == null

    /** Matches only the configured callback base; OAuth response parameters may follow it. */
    fun matchesCallback(callbackUrl: String): Boolean {
        val expected = normalized()
        if (!expected.isUsable) return false
        return callbackMatchesRedirect(expected.redirectUri, callbackUrl)
    }

    private fun parsedRedirect(): URI? = runCatching { URI(redirectUri) }.getOrNull()

    private fun containsDotEquivalentSegment(rawPath: String): Boolean =
        rawPath.split('/').any { segment ->
            val decodedDots = ENCODED_DOT.replace(segment, ".")
            decodedDots == "." || decodedDots == ".."
        }

    private fun hasNonCanonicalPercentEscape(rawPath: String): Boolean {
        var index = rawPath.indexOf('%')
        while (index >= 0) {
            if (index + 2 >= rawPath.length ||
                rawPath[index + 1] !in UPPERCASE_HEX ||
                rawPath[index + 2] !in UPPERCASE_HEX
            ) {
                return true
            }
            index = rawPath.indexOf('%', startIndex = index + 3)
        }
        return false
    }

    companion object {
        /**
         * Base comparison for a returned callback. Split out from [matchesCallback] so the
         * in-app sign-in WebView can recognize the redirect from the URI alone, before any
         * client ID or User-Agent is in scope.
         */
        internal fun callbackMatchesRedirect(redirectUri: String, callbackUrl: String): Boolean {
            if (callbackUrl.length > MAX_CALLBACK_URI_LENGTH) return false
            val expectedUri = runCatching { URI(redirectUri.trim()) }.getOrNull() ?: return false
            val callback = runCatching { URI(callbackUrl) }.getOrNull() ?: return false
            // Reddit's grant page returns the callback with a '#_' fragment appended, and
            // browsers may add a bare '#' of their own. A fragment is never sent to the token
            // endpoint and is never read here — the code and state come from the query alone —
            // so its presence says nothing about whether this is the configured callback.
            if (callback.isOpaque) return false
            if (callback.userInfo != null || callback.port != -1) return false
            return callback.scheme.equals(expectedUri.scheme, ignoreCase = true) &&
                callback.rawAuthority.equals(expectedUri.rawAuthority, ignoreCase = true) &&
                equivalentCallbackPath(callback.rawPath, expectedUri.rawPath)
        }

        /** Browsers and OAuth services may serialize an authority-only URI with a root slash. */
        private fun equivalentCallbackPath(actual: String?, expected: String?): Boolean {
            val actualPath = actual.orEmpty()
            val expectedPath = expected.orEmpty()
            return actualPath == expectedPath ||
                (actualPath.isEmpty() && expectedPath == "/") ||
                (actualPath == "/" && expectedPath.isEmpty())
        }

        // Stable across the Otter rebrand so existing Reddit app registrations keep working.
        const val DEFAULT_REDIRECT_SCHEME = "app.orca.client"
        const val DEFAULT_REDIRECT_URI = "$DEFAULT_REDIRECT_SCHEME://oauth/reddit"
        private const val MAX_CLIENT_ID_LENGTH = 128
        private const val MAX_USER_AGENT_LENGTH = 256
        private const val MAX_REDIRECT_URI_LENGTH = 512
        private const val MAX_CALLBACK_URI_LENGTH = 4_096
        private val CLIENT_ID_PATTERN = Regex("[A-Za-z0-9_-]+")
        private val SCHEME_PATTERN = Regex("[a-z][a-z0-9+.-]*")
        private val ENCODED_DOT = Regex("%2e", RegexOption.IGNORE_CASE)
        private val UPPERCASE_HEX = ('0'..'9') + ('A'..'F')
        private val BLOCKED_SCHEMES = setOf(
            "about",
            "content",
            "data",
            "file",
            "http",
            "inline",
            "intent",
            "javascript",
        )
    }
}

/** Renders only the base of a callback for diagnostics; response parameters are omitted. */
internal fun callbackBase(callbackUrl: String): String {
    val callback = runCatching { URI(callbackUrl) }.getOrNull() ?: return "an unreadable URI"
    val scheme = callback.scheme ?: return "an unreadable URI"
    return buildString {
        append(scheme)
        append(':')
        callback.rawAuthority?.let { authority ->
            append("//")
            append(authority)
        }
        append(callback.rawPath.orEmpty())
    }
}

/** Binds a persisted refresh credential to every setting that defines its OAuth client. */
internal fun RedditApiConfiguration.credentialStorageKey(): String = normalized().run {
    "v2\u0000$clientId\u0000$userAgent\u0000$redirectUri"
}
