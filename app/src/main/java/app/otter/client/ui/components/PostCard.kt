package app.otter.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.otter.client.data.DemoRedditContent
import app.otter.client.model.MediaKind
import app.otter.client.model.Post
import app.otter.client.model.PostPreview
import app.otter.client.model.PostType
import app.otter.client.model.VoteState
import app.otter.client.ui.FeedPresentation
import app.otter.client.ui.SwipeActionConfig
import app.otter.client.ui.theme.otterColors
import coil3.compose.AsyncImage
import kotlin.math.max

@Composable
fun FeedPost(
    post: Post,
    presentation: FeedPresentation,
    thumbnailsOnRight: Boolean,
    textScale: Float,
    dimRead: Boolean,
    showFlairs: Boolean,
    swipeEnabled: Boolean,
    hapticsEnabled: Boolean,
    isSelected: Boolean,
    revealNsfw: Boolean,
    onOpen: () -> Unit,
    onVote: (VoteState) -> Unit,
    onSave: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    swipeActions: SwipeActionConfig = SwipeActionConfig(),
    onOpenMedia: () -> Unit = onOpen,
) {
    var sensitiveMediaRevealed by rememberSaveable(post.id) { mutableStateOf(false) }
    val coversNsfw = post.isNsfw && !revealNsfw
    val warningLabel = when {
        coversNsfw && post.isSpoiler -> "NSFW · SPOILER"
        coversNsfw -> "NSFW"
        post.isSpoiler -> "SPOILER"
        else -> null
    }.takeUnless { sensitiveMediaRevealed }
    SwipeActionRow(
        enabled = swipeEnabled,
        hapticsEnabled = hapticsEnabled,
        rightShortAction = swipeActions.rightShort,
        rightLongAction = swipeActions.rightLong,
        leftShortAction = swipeActions.leftShort,
        leftLongAction = swipeActions.leftLong,
        onAction = { action ->
            when (action) {
                SwipeAction.Upvote -> onVote(VoteState.UPVOTED)
                SwipeAction.Downvote -> onVote(VoteState.DOWNVOTED)
                SwipeAction.Save -> onSave()
                SwipeAction.Hide -> onHide()
                SwipeAction.Reply, SwipeAction.Collapse -> Unit
            }
        },
    ) {
        Box(modifier = modifier) {
            when (presentation) {
                FeedPresentation.Compact -> CompactPost(
                    post = post,
                    dimmed = dimRead && post.isRead,
                    thumbnailsOnRight = thumbnailsOnRight,
                    onOpenMedia = onOpenMedia,
                    textScale = textScale,
                    showFlairs = showFlairs,
                    selected = isSelected,
                    warningLabel = warningLabel,
                    onRevealMedia = { sensitiveMediaRevealed = true },
                    onOpen = onOpen,
                    onVote = onVote,
                    onSave = onSave,
                )

                FeedPresentation.LargePreview -> LargePreviewPost(
                    post = post,
                    dimmed = dimRead && post.isRead,
                    onOpenMedia = onOpenMedia,
                    textScale = textScale,
                    showFlairs = showFlairs,
                    selected = isSelected,
                    warningLabel = warningLabel,
                    onRevealMedia = { sensitiveMediaRevealed = true },
                    onOpen = onOpen,
                    onVote = onVote,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
private fun CompactPost(
    post: Post,
    dimmed: Boolean,
    thumbnailsOnRight: Boolean,
    onOpenMedia: () -> Unit,
    textScale: Float,
    showFlairs: Boolean,
    selected: Boolean,
    warningLabel: String?,
    onRevealMedia: () -> Unit,
    onOpen: () -> Unit,
    onVote: (VoteState) -> Unit,
    onSave: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    // Title and thumbnail share the top row; the metadata line runs the full width beneath both,
    // so the counts sit under the thumbnail rather than being squeezed to its left.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.accent.copy(alpha = .1f) else colors.surface)
            .clickable(role = Role.Button, onClickLabel = "Open post", onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (!thumbnailsOnRight && post.preview != null) {
                PostArtwork(
                    preview = post.preview,
                    type = post.type,
                    badge = mediaBadgeFor(post),
                    warningLabel = warningLabel,
                    onReveal = onRevealMedia,
                    onOpenMedia = onOpenMedia.takeIf { post.media != null },
                    modifier = Modifier.size(72.dp),
                    cornerRadius = 8,
                    compact = true,
                )
                Spacer(Modifier.width(11.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                TitleWithFlair(
                    post = post,
                    showFlairs = showFlairs,
                    textScale = textScale,
                    dimmed = dimmed,
                )
            }

            if (thumbnailsOnRight && post.preview != null) {
                Spacer(Modifier.width(11.dp))
                PostArtwork(
                    preview = post.preview,
                    type = post.type,
                    badge = mediaBadgeFor(post),
                    warningLabel = warningLabel,
                    onReveal = onRevealMedia,
                    onOpenMedia = onOpenMedia.takeIf { post.media != null },
                    modifier = Modifier.size(72.dp),
                    cornerRadius = 8,
                    compact = true,
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        CompactMetadata(post = post, onVote = onVote)
    }
}

@Composable
private fun LargePreviewPost(
    post: Post,
    dimmed: Boolean,
    onOpenMedia: () -> Unit,
    textScale: Float,
    showFlairs: Boolean,
    selected: Boolean,
    warningLabel: String?,
    onRevealMedia: () -> Unit,
    onOpen: () -> Unit,
    onVote: (VoteState) -> Unit,
    onSave: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.accent.copy(alpha = .1f) else colors.surface)
            .clickable(role = Role.Button, onClickLabel = "Open post", onClick = onOpen)
            .padding(top = 11.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommunityDot(post)
            Spacer(Modifier.width(6.dp))
            Text(
                text = post.community.name,
                color = colors.accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "  ·  ${relativeAge(post.createdAtEpochSeconds)}",
                color = colors.textTertiary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            text = post.title,
            color = colors.textPrimary,
            fontSize = (16f * textScale).sp,
            lineHeight = (21f * textScale).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
        )
        post.preview?.let { preview ->
            PostArtwork(
                preview = preview,
                type = post.type,
                badge = mediaBadgeFor(post),
                warningLabel = warningLabel,
                onReveal = onRevealMedia,
                onOpenMedia = onOpenMedia.takeIf { post.media != null },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(max(preview.aspectRatio, 1.25f)),
                cornerRadius = 0,
            )
        }
        PostMetadata(
            post = post,
            showFlairs = showFlairs,
            onVote = onVote,
            onSave = onSave,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun CommunityDot(post: Post) {
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(Color(post.community.accentStartArgb)),
    )
}

/**
 * Title with its flair and pin set *inside* the text, so they trail the final word instead of
 * claiming a line of their own. Each needs a measured placeholder, because inline content in
 * Compose reserves its space before it is drawn.
 */
@Composable
private fun TitleWithFlair(
    post: Post,
    showFlairs: Boolean,
    textScale: Float,
    dimmed: Boolean = false,
) {
    val colors = MaterialTheme.otterColors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val flair = post.flairText?.takeIf { showFlairs && it.isNotBlank() }
    val flairStyle = TextStyle(fontSize = (10.5f * textScale).sp)

    val flairSize = remember(flair, textScale) {
        flair?.let { measurer.measure(AnnotatedString(it), flairStyle).size }
    }

    val title = buildAnnotatedString {
        append(post.title)
        if (flair != null) {
            append(' ')
            appendInlineContent(FLAIR_SLOT, flair)
        }
        if (post.isStickied) {
            append(' ')
            appendInlineContent(PIN_SLOT, "pinned")
        }
    }

    val inlineContent = buildMap {
        if (flair != null && flairSize != null) {
            put(
                FLAIR_SLOT,
                InlineTextContent(
                    Placeholder(
                        width = with(density) { (flairSize.width.toDp() + 12.dp).toSp() },
                        height = with(density) { (flairSize.height.toDp() + 5.dp).toSp() },
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceRaised),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = flair,
                            color = colors.textTertiary,
                            style = flairStyle,
                            maxLines = 1,
                        )
                    }
                },
            )
        }
        if (post.isStickied) {
            val pin = with(density) { 13.dp.toSp() }
            put(
                PIN_SLOT,
                InlineTextContent(
                    Placeholder(pin, pin, PlaceholderVerticalAlign.TextCenter),
                ) {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = "Pinned",
                        tint = Color(0xFF5BCF8A),
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
    }

    Text(
        text = title,
        inlineContent = inlineContent,
        // Read posts recede by going grey, while the thumbnail stays at full strength.
        color = if (dimmed) colors.textTertiary else colors.textPrimary,
        fontSize = (15.2f * textScale).sp,
        lineHeight = (19.6f * textScale).sp,
        fontWeight = FontWeight.Medium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val FLAIR_SLOT = "flair"
private const val PIN_SLOT = "pin"

/** One line: where the post came from on the left, how it is doing on the right. */
@Composable
private fun CompactMetadata(post: Post, onVote: (VoteState) -> Unit) {
    val colors = MaterialTheme.otterColors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // One weighted group on the left rather than two competing weights: a shrink-to-fit
        // weight still reserves its share, which left the stats floating short of the edge by
        // however much the domain text did not use.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = post.community.name,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            sourceLabel(post)?.let { source ->
                Spacer(Modifier.width(6.dp))
                Text(
                    text = source,
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        MetadataStat(
            icon = Icons.Outlined.Schedule,
            text = relativeAge(post.createdAtEpochSeconds),
            tint = colors.textTertiary,
            label = "Posted",
        )
        Spacer(Modifier.width(10.dp))
        MetadataStat(
            icon = Icons.Outlined.ChatBubbleOutline,
            text = compactNumber(post.commentCount),
            tint = colors.textTertiary,
            label = "Comments",
        )
        Spacer(Modifier.width(10.dp))
        MetadataStat(
            icon = if (post.voteState == VoteState.UPVOTED) {
                Icons.Filled.ArrowUpward
            } else {
                Icons.Outlined.ArrowUpward
            },
            text = compactNumber(post.score),
            tint = if (post.voteState == VoteState.UPVOTED) colors.upvote else colors.textTertiary,
            label = "Upvote",
            onClick = { onVote(VoteState.UPVOTED) },
        )
    }
}

@Composable
private fun MetadataStat(
    icon: ImageVector,
    text: String,
    tint: Color,
    label: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick == null) {
            Modifier
        } else {
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClickLabel = label, onClick = onClick)
        },
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = tint, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Reddit reports a self post's domain as "self.subreddit"; only the prefix is worth showing. */
private fun sourceLabel(post: Post): String? {
    val domain = post.domain?.takeIf { it.isNotBlank() } ?: return null
    return when {
        domain.startsWith("self.", ignoreCase = true) -> "self"
        domain == "i.redd.it" || domain == "v.redd.it" -> "reddit.com"
        else -> domain
    }
}

@Composable
private fun PostMetadata(
    post: Post,
    showFlairs: Boolean,
    onVote: (VoteState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.otterColors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                showFlairs && post.isNsfw && post.isSpoiler -> "NSFW · SPOILER"
                showFlairs && post.isNsfw -> "NSFW"
                showFlairs && post.isSpoiler -> "SPOILER"
                showFlairs && post.type == PostType.GALLERY -> "GALLERY"
                showFlairs && post.type == PostType.VIDEO -> "VIDEO"
                else -> post.domain.orEmpty()
            },
            color = colors.textTertiary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MetadataButton(
            icon = if (post.voteState == VoteState.UPVOTED) Icons.Filled.ArrowUpward else Icons.Outlined.ArrowUpward,
            text = compactNumber(post.score),
            tint = if (post.voteState == VoteState.UPVOTED) colors.upvote else colors.textSecondary,
            label = "Upvote",
            onClick = { onVote(VoteState.UPVOTED) },
        )
        Spacer(Modifier.width(11.dp))
        MetadataButton(
            icon = Icons.Outlined.ChatBubbleOutline,
            text = compactNumber(post.commentCount),
            tint = colors.textSecondary,
            label = "Comments",
            onClick = null,
        )
        Spacer(Modifier.width(9.dp))
        IconButton(onClick = onSave, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (post.isSaved) "Unsave" else "Save",
                tint = if (post.isSaved) colors.saved else colors.textTertiary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun MetadataButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    label: String,
    onClick: (() -> Unit)?,
) {
    val base = Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick == null) {
            base
        } else {
            base
                .clickable(onClickLabel = label, onClick = onClick)
                .padding(horizontal = 3.dp, vertical = 7.dp)
        },
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = tint, style = MaterialTheme.typography.labelMedium)
    }
}

/** What a thumbnail says about itself beyond being a picture. */
enum class MediaBadge {
    Video,
    Gif,
    Gallery,
}

/** Reads the badge from what the post actually carries, falling back to Reddit's own post type. */
fun mediaBadgeFor(post: Post): MediaBadge? {
    post.media?.let { media ->
        if (media.isGallery) return MediaBadge.Gallery
        return when (media.first.kind) {
            MediaKind.ANIMATED -> MediaBadge.Gif
            MediaKind.VIDEO -> MediaBadge.Video
            MediaKind.IMAGE -> null
        }
    }
    return when (post.type) {
        PostType.VIDEO -> MediaBadge.Video
        PostType.GALLERY -> MediaBadge.Gallery
        else -> null
    }
}

@Composable
fun PostArtwork(
    preview: PostPreview,
    type: PostType,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 6,
    compact: Boolean = false,
    badge: MediaBadge? = null,
    warningLabel: String? = null,
    onReveal: () -> Unit = {},
    onOpenMedia: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.otterColors
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier = modifier
            .clip(shape)
            // The gate owns the tap while it is up: revealing comes before viewing.
            .then(
                if (onOpenMedia != null && warningLabel == null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "View media",
                        onClick = onOpenMedia,
                    )
                } else {
                    Modifier
                }
            )
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(preview.startColorArgb), Color(preview.endColorArgb)),
                ),
            ),
    ) {
        DemoArtwork(preview.assetKey)
        // A 72dp row shows a 320px copy and a full-bleed card a screen-width one. The original
        // is reserved for the viewer, where the extra pixels are actually visible.
        val displayUrl = if (compact) {
            preview.thumbnailUrl ?: preview.cardImageUrl ?: preview.imageUrl
        } else {
            preview.cardImageUrl ?: preview.imageUrl
        }
        displayUrl?.takeIf { warningLabel == null }?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = preview.altText,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (warningLabel != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .78f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Reveal $warningLabel media",
                        onClick = onReveal,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    warningLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Tap to reveal",
                    color = Color.White.copy(alpha = .76f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        // Only media that behaves differently from a still image earns a mark; a plain photo
        // or a link needs no caption stamped across its own thumbnail.
        badge?.let { mediaBadge ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = .55f))
                    .padding(horizontal = 3.dp, vertical = 3.dp),
            ) {
                Icon(
                    imageVector = when (mediaBadge) {
                        MediaBadge.Video -> Icons.Filled.PlayArrow
                        MediaBadge.Gif -> Icons.Outlined.Gif
                        MediaBadge.Gallery -> Icons.Outlined.Collections
                    },
                    contentDescription = when (mediaBadge) {
                        MediaBadge.Video -> "Video"
                        MediaBadge.Gif -> "GIF"
                        MediaBadge.Gallery -> "Gallery"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(if (mediaBadge == MediaBadge.Gif) 15.dp else 12.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .background(colors.accent.copy(alpha = if (type == PostType.VIDEO) .9f else 0f)),
        )
    }
}

@Composable
private fun DemoArtwork(assetKey: String) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        when {
            "satellite" in assetKey -> {
                listOf(.12f to .13f, .31f to .20f, .73f to .12f, .86f to .26f, .54f to .08f).forEach {
                    drawCircle(Color.White.copy(alpha = .75f), max(1.2f, w * .008f), Offset(w * it.first, h * it.second))
                }
                drawRoundRect(
                    color = Color(0xCC08101C),
                    topLeft = Offset(w * .38f, h * .22f),
                    size = Size(w * .25f, h * .58f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .035f),
                )
                drawCircle(Color(0xFF5CB2FF), w * .025f, Offset(w * .505f, h * .41f))
                drawArc(
                    color = Color.White.copy(alpha = .7f),
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(w * .40f, h * .27f),
                    size = Size(w * .21f, h * .27f),
                    style = Stroke(width = max(1.5f, w * .01f), cap = StrokeCap.Round),
                )
            }
            "aurora" in assetKey -> {
                listOf(.12f to .13f, .28f to .19f, .69f to .1f, .84f to .24f).forEach {
                    drawCircle(Color.White.copy(alpha = .7f), max(1.1f, w * .006f), Offset(w * it.first, h * it.second))
                }
                val ribbon = Path().apply {
                    moveTo(-w * .05f, h * .34f)
                    cubicTo(w * .2f, h * .05f, w * .45f, h * .55f, w * 1.05f, h * .18f)
                }
                drawPath(ribbon, Color(0x9957CC99), style = Stroke(width = h * .19f, cap = StrokeCap.Round))
                drawRect(Color(0x99071019), Offset(0f, h * .73f), Size(w, h * .27f))
            }
            "keycaps" in assetKey -> {
                repeat(4) { row ->
                    repeat(7) { column ->
                        drawRoundRect(
                            color = if ((row + column) % 3 == 0) Color(0xFFE9C46A) else Color(0xFFEAF4F4),
                            topLeft = Offset(w * (.08f + column * .125f), h * (.18f + row * .17f)),
                            size = Size(w * .095f, h * .12f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .012f),
                        )
                    }
                }
            }
            "watercolor" in assetKey -> {
                val hill = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, h * .62f)
                    cubicTo(w * .2f, h * .39f, w * .33f, h * .73f, w * .52f, h * .48f)
                    cubicTo(w * .7f, h * .28f, w * .82f, h * .61f, w, h * .34f)
                    lineTo(w, h)
                    close()
                }
                drawPath(hill, Color(0x99512477))
                drawCircle(Color(0x99FFD166), w * .08f, Offset(w * .76f, h * .2f))
            }
            "rainy" in assetKey -> {
                drawRoundRect(Color(0xAA172436), Offset(w * .1f, h * .1f), Size(w * .5f, h * .62f))
                drawLine(Color(0x99FFFFFF), Offset(w * .35f, h * .1f), Offset(w * .35f, h * .72f), w * .008f)
                drawLine(Color(0x99FFFFFF), Offset(w * .1f, h * .4f), Offset(w * .6f, h * .4f), w * .008f)
                drawCircle(Color(0xBBFFD08A), w * .12f, Offset(w * .78f, h * .42f))
            }
            "sleep" in assetKey -> {
                val graph = Path().apply {
                    moveTo(w * .08f, h * .64f)
                    lineTo(w * .22f, h * .56f)
                    lineTo(w * .34f, h * .68f)
                    lineTo(w * .47f, h * .31f)
                    lineTo(w * .61f, h * .45f)
                    lineTo(w * .75f, h * .24f)
                    lineTo(w * .92f, h * .34f)
                }
                drawPath(graph, Color.White.copy(alpha = .8f), style = Stroke(width = max(2f, w * .015f), cap = StrokeCap.Round))
                repeat(4) { index ->
                    drawLine(
                        Color.White.copy(alpha = .16f),
                        Offset(w * .08f, h * (.24f + index * .14f)),
                        Offset(w * .92f, h * (.24f + index * .14f)),
                        1f,
                    )
                }
            }
            else -> drawCircle(Color.White.copy(alpha = .14f), w * .27f, Offset(w * .65f, h * .35f))
        }
    }
}

fun compactNumber(value: Int): String = when {
    kotlin.math.abs(value) >= 1_000_000 -> "%.1fm".format(value / 1_000_000f).removeSuffix(".0m") + if (value % 1_000_000 == 0) "m" else ""
    kotlin.math.abs(value) >= 1_000 -> {
        val formatted = "%.1fk".format(value / 1_000f)
        formatted.replace(".0k", "k")
    }
    else -> value.toString()
}

fun relativeAge(epochSeconds: Long): String {
    val reference = max(
        DemoRedditContent.REFERENCE_TIME_EPOCH_SECONDS,
        System.currentTimeMillis() / 1000L,
    )
    val seconds = (reference - epochSeconds).coerceAtLeast(0L)
    return when {
        seconds < 60 -> "now"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        seconds < 2_592_000 -> "${seconds / 86_400}d"
        else -> "${seconds / 2_592_000}mo"
    }
}
