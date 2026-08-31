package app.otter.client.data

import java.net.URI

/** Matches a leading `scheme://` so a typed address can be told apart from a bare host. */
private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

/**
 * Cleans a link-post address into something Reddit will accept, or null when there is nothing
 * usable in it.
 *
 * Typing a URL on a phone rarely produces a scheme and Reddit rejects a bare `example.com`, so a
 * schemeless address is promoted to https instead of being handed over to fail. Anything that is
 * not http(s) with a host is refused here rather than at submit time, where the failure would come
 * back as an opaque Reddit error.
 */
fun normalizeSubmissionUrl(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if (SCHEME_PREFIX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    val parsed = runCatching { URI(withScheme) }.getOrNull() ?: return null
    if (parsed.scheme?.lowercase() !in setOf("http", "https")) return null
    if (parsed.host.isNullOrBlank()) return null
    return withScheme
}

/** The host a link post should be labelled with, matching how Reddit shows a post's domain. */
fun submissionUrlHost(url: String): String? =
    runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")?.takeIf(String::isNotEmpty)
