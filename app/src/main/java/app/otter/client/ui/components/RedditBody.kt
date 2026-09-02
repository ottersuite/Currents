package app.otter.client.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import app.otter.client.ui.theme.otterColors

/**
 * Reddit-authored text with its links made usable, whether that is a comment or a post body.
 *
 * Reddit stores an embedded GIF as markdown pointing at an internal id — `![gif](giphy|abc123)` —
 * which reads as noise when printed verbatim. Those become a single "View GIF" link, and ordinary
 * markdown links keep their label instead of showing a raw URL.
 */
@Composable
fun RedditBody(
    body: String,
    onOpenMedia: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val colors = MaterialTheme.otterColors
    val segments = remember(body) { parseRedditBody(body) }

    val text = buildAnnotatedString {
        segments.forEach { segment ->
            when (segment) {
                is BodySegment.Plain -> append(segment.text)
                is BodySegment.Link -> {
                    val handler = if (segment.isMedia) onOpenMedia else onOpenLink
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = segment.url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = colors.accent,
                                    fontWeight = if (segment.isMedia) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    textDecoration = if (segment.isMedia) {
                                        TextDecoration.None
                                    } else {
                                        TextDecoration.Underline
                                    },
                                ),
                            ),
                        ) { handler(segment.url) },
                    ) {
                        append(segment.label)
                    }
                }
            }
        }
    }

    Text(
        text = text,
        color = colors.textPrimary,
        style = style,
        modifier = modifier,
    )
}

internal sealed interface BodySegment {
    data class Plain(val text: String) : BodySegment

    data class Link(val label: String, val url: String, val isMedia: Boolean) : BodySegment
}

private val MARKDOWN_LINK = Regex("""(!?)\[([^\]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
private val BARE_URL = Regex("""https?://[^\s<>()\[\]]+""")
private val GIPHY_EMBED = Regex("""giphy\|([A-Za-z0-9]+)(?:\|\w+)?""")
private val EMOTE_EMBED = Regex("""emote\|[^)|]+\|\w+""")
private val MOTION_SUFFIXES = listOf(".gif", ".gifv", ".mp4", ".webm")
private val IMAGE_SUFFIXES = listOf(".jpg", ".jpeg", ".png", ".webp", ".avif", ".bmp")

/**
 * Hosts that only ever serve media.
 *
 * `preview.redd.it` is the one worth naming: it hands out resized images whose URL carries the
 * dimensions in a query string, and a suffix check alone was enough to recognise the path but
 * nothing recognised the host. Those links were leaving the app for a browser.
 */
private val MEDIA_HOSTS = listOf(
    "preview.redd.it",
    "i.redd.it",
    "v.redd.it",
    "i.imgur.com",
    "giphy.com",
    "redgifs.com",
)

/** Splits a comment into plain runs and links, resolving Reddit's internal media syntax. */
internal fun parseRedditBody(body: String): List<BodySegment> {
    val segments = mutableListOf<BodySegment>()
    var index = 0

    fun appendPlain(text: String) {
        if (text.isEmpty()) return
        val previous = segments.lastOrNull()
        if (previous is BodySegment.Plain) {
            segments[segments.lastIndex] = BodySegment.Plain(previous.text + text)
        } else {
            segments += BodySegment.Plain(text)
        }
    }

    while (index < body.length) {
        val markdown = MARKDOWN_LINK.find(body, index)
        val bare = BARE_URL.find(body, index)
        val match = listOfNotNull(markdown, bare).minByOrNull { it.range.first }
        if (match == null) {
            appendPlain(body.substring(index))
            break
        }
        appendPlain(body.substring(index, match.range.first))

        if (match === markdown) {
            val label = markdown.groupValues[2]
            val target = markdown.groupValues[3]
            when (val resolved = resolveTarget(target)) {
                null -> Unit // An emote or another internal reference with nothing to open.
                else -> segments += BodySegment.Link(
                    label = mediaLabel(resolved, label),
                    url = resolved,
                    isMedia = isMedia(resolved),
                )
            }
        } else {
            val url = bare!!.value.trimEnd('.', ',', ')')
            segments += BodySegment.Link(
                // No label was written, so let the URL name itself.
                label = mediaLabel(url, ""),
                url = url,
                isMedia = isMedia(url),
            )
        }
        index = match.range.last + 1
    }

    return segments
}

/** Turns Reddit's internal media reference into something a player can open. */
private fun resolveTarget(target: String): String? {
    GIPHY_EMBED.matchEntire(target)?.let { match ->
        return "https://i.giphy.com/media/${match.groupValues[1]}/giphy.mp4"
    }
    if (EMOTE_EMBED.matches(target)) return null
    if (target.startsWith("http://") || target.startsWith("https://")) return target
    if (target.startsWith("/r/") || target.startsWith("/u/")) return "https://www.reddit.com$target"
    return null
}

private fun isMedia(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return MOTION_SUFFIXES.any(path::endsWith) ||
        IMAGE_SUFFIXES.any(path::endsWith) ||
        MEDIA_HOSTS.any(path::contains)
}

/**
 * A media link is worth naming for what it is; anything else keeps the label the author wrote.
 * A label that is just the URL again, or Reddit's placeholder "gif", says nothing.
 */
private fun mediaLabel(url: String, label: String): String {
    if (!isMedia(url)) return label.ifBlank { shortenUrl(url) }
    val meaningful = label.isNotBlank() &&
        !label.startsWith("http", ignoreCase = true) &&
        !label.equals("gif", ignoreCase = true) &&
        !label.equals("img", ignoreCase = true)
    if (meaningful) return label
    val path = url.substringBefore('?').lowercase()
    // A giphy clip arrives as MP4 but is a GIF to anyone reading the thread.
    if (path.contains("giphy.com") || path.contains("redgifs.com")) return "View GIF"
    if (path.contains("v.redd.it")) return "View video"
    return if (path.endsWith(".mp4") || path.endsWith(".webm")) "View video" else "View GIF"
}

private fun shortenUrl(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").take(48)

/** Compose needs the annotated-string builder in scope for links; kept local for readability. */
private inline fun AnnotatedString.Builder.withLink(
    link: LinkAnnotation,
    block: AnnotatedString.Builder.() -> Unit,
) {
    val index = pushLink(link)
    block()
    pop(index)
}
