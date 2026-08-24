package app.otter.client.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.otter.client.model.Comment
import app.otter.client.model.Post
import app.otter.client.model.VoteState
import app.otter.client.ui.OtterSettings
import app.otter.client.ui.CommentSort
import app.otter.client.ui.components.ActionBarItem
import app.otter.client.ui.components.GlassActionBar
import app.otter.client.ui.components.RedditBody
import app.otter.client.ui.components.PostArtwork
import app.otter.client.ui.components.mediaBadgeFor
import app.otter.client.ui.components.SwipeAction
import app.otter.client.ui.components.SwipeActionRow
import app.otter.client.ui.components.compactNumber
import app.otter.client.ui.components.relativeAge
import app.otter.client.ui.theme.otterColors
import kotlinx.coroutines.launch

@Composable
fun PostScreen(
    post: Post,
    comments: List<Comment>,
    commentsLoading: Boolean,
    commentsAreServerSorted: Boolean,
    commentSort: CommentSort,
    collapsedCommentIds: Set<String>,
    settings: OtterSettings,
    onBack: () -> Unit,
    onSortChange: (CommentSort) -> Unit,
    onPostVote: (VoteState) -> Unit,
    onSavePost: () -> Unit,
    onCommentVote: (String, VoteState) -> Unit,
    onToggleCommentCollapsed: (String) -> Unit,
    onReply: (String?) -> Unit,
    onOpenMedia: (Int) -> Unit,
    onOpenCommentMedia: (String) -> Unit,
    onReloadComments: () -> Unit,
    onOpenDrawer: () -> Unit,
    expandingComments: Set<String>,
    onLoadMoreComments: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenCommunity: (String) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    currentUsername: String? = null,
    postIsHidden: Boolean = false,
    onHidePost: () -> Unit = {},
    onReportPost: (String) -> Unit = {},
    onBlockUser: (String) -> Unit = {},
    onEditPost: (String) -> Unit = {},
    onDeletePost: () -> Unit = {},
    onReportComment: (String, String) -> Unit = { _, _ -> },
    onEditComment: (String, String) -> Unit = { _, _ -> },
    onDeleteComment: (String) -> Unit = {},
) {
    val colors = MaterialTheme.otterColors
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val sortedComments = remember(comments, commentSort, commentsAreServerSorted) {
        sortThreadedComments(comments, commentSort, commentsAreServerSorted)
    }
    val visibleComments = remember(sortedComments, collapsedCommentIds) {
        val byId = sortedComments.associateBy(Comment::id)
        sortedComments.filter { comment ->
            var parentId = comment.parentId
            var hidden = false
            while (parentId != null) {
                if (parentId in collapsedCommentIds) {
                    hidden = true
                    break
                }
                parentId = byId[parentId]?.parentId
            }
            !hidden
        }
    }
    val topLevelIndices = visibleComments.mapIndexedNotNull { index, comment ->
        index.takeIf { comment.depth == 0 }
    }
    var showPostActions by rememberSaveable { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var blockTarget by remember { mutableStateOf<String?>(null) }
    var selectedCommentActions by remember { mutableStateOf<Comment?>(null) }

    /** Walks between top-level comments, wrapping around at either end. */
    fun jumpToComment(forward: Boolean) {
        if (topLevelIndices.isEmpty()) return
        val current = (listState.firstVisibleItemIndex - HEADER_ITEM_COUNT).coerceAtLeast(-1)
        val target = if (forward) {
            topLevelIndices.firstOrNull { it > current } ?: topLevelIndices.first()
        } else {
            topLevelIndices.lastOrNull { it < current } ?: topLevelIndices.last()
        }
        coroutineScope.launch {
            listState.animateScrollToItem(HEADER_ITEM_COUNT + target)
        }
    }

    fun sharePost() {
        val link = post.destinationUrl ?: "https://reddit.com/comments/${post.id}"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, post.title)
            putExtra(Intent.EXTRA_TEXT, "${post.title}\n$link")
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share post"))
    }

    fun openDestination() {
        val link = post.destinationUrl ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri())) }
    }

    val pullState = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize().background(colors.canvas)) {
      PullToRefreshBox(
        isRefreshing = commentsLoading,
        onRefresh = onReloadComments,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            Indicator(
                state = pullState,
                isRefreshing = commentsLoading,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarHeight + 58.dp),
                containerColor = colors.surfaceRaised,
                color = colors.accent,
            )
        },
        state = pullState,
      ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = statusBarHeight + 58.dp,
                bottom = navigationBarHeight + 112.dp,
            ),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            item(key = "post-header") {
                PostDetailHeader(
                    post = post,
                    textScale = settings.textScale,
                    revealNsfw = settings.alwaysShowNsfw,
                    onVote = onPostVote,
                    onSave = onSavePost,
                    onOpenCommunity = { onOpenCommunity(post.community.name) },
                    onOpenUser = { onOpenUser(post.author) },
                    onOpenDestination = post.destinationUrl?.let { { openDestination() } },
                    onOpenMedia = { onOpenMedia(0) },
                    onOpenBodyMedia = onOpenCommentMedia,
                    onOpenLink = { link ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
                        }
                    },
                )
            }

            item(key = "comment-sort") {
                CommentSortBar(
                    selected = commentSort,
                    loadedCount = comments.size,
                    totalCount = post.commentCount,
                    loading = commentsLoading,
                    onSelect = onSortChange,
                )
            }

            if (visibleComments.isEmpty()) {
                item(key = "empty-comments") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 58.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (commentsLoading) {
                            CircularProgressIndicator(
                                color = colors.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(30.dp),
                            )
                        } else {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = colors.textTertiary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (commentsLoading) "Loading comments" else "No comments yet",
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (commentsLoading) "Fetching this conversation from Reddit…" else "Be the first to join the conversation.",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                itemsIndexed(visibleComments, key = { _, comment -> comment.id }) { _, comment ->
                    val hiddenReplies = if (comment.id in collapsedCommentIds) {
                        countDescendants(comment.id, sortedComments)
                    } else {
                        0
                    }
                    if (comment.isMoreStub) {
                        MoreCommentsRow(
                            comment = comment,
                            loading = comment.id in expandingComments,
                            textScale = settings.textScale,
                            onClick = { onLoadMoreComments(comment.id) },
                        )
                        HorizontalDivider(
                            color = colors.divider.copy(alpha = .7f),
                            thickness = .5.dp,
                        )
                        return@itemsIndexed
                    }

                    CommentRow(
                        comment = comment,
                        onOpenCommentMedia = onOpenCommentMedia,
                        onOpenCommentLink = { link ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
                            }
                        },
                        hiddenReplies = hiddenReplies,
                        textScale = settings.textScale,
                        swipeEnabled = settings.swipeActions,
                        hapticsEnabled = settings.haptics,
                        swipeActions = settings.commentSwipeActions,
                        onVote = { onCommentVote(comment.id, it) },
                        onCollapse = { onToggleCommentCollapsed(comment.id) },
                        onReply = { onReply(comment.id) },
                        onOpenUser = { onOpenUser(comment.author) },
                        onMore = { selectedCommentActions = comment },
                    )
                    HorizontalDivider(color = colors.divider.copy(alpha = .7f), thickness = .5.dp)
                }
            }
        }
      }

        PostTopBar(
            post = post,
            onBack = onBack,
            onSave = onSavePost,
            onShare = ::sharePost,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        GlassActionBar(
            items = listOf(
                ActionBarItem(Icons.Outlined.MoreHoriz, "More") { showPostActions = true },
                ActionBarItem(Icons.Outlined.KeyboardDoubleArrowUp, "Previous comment") {
                    jumpToComment(forward = false)
                },
                ActionBarItem(Icons.Outlined.Share, "Share", emphasized = true) { sharePost() },
                ActionBarItem(Icons.Outlined.KeyboardArrowDown, "Next comment") {
                    jumpToComment(forward = true)
                },
                ActionBarItem(Icons.Outlined.Menu, "Communities") { onOpenDrawer() },
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp),
        )

        if (showPostActions) {
            PostActionsSheet(
                post = post,
                onDismiss = { showPostActions = false },
                onVote = onPostVote,
                onSave = onSavePost,
                onReply = { onReply(null) },
                onOpenLink = {
                    val link = post.destinationUrl ?: "https://reddit.com/comments/${post.id}"
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri())) }
                },
                hidden = postIsHidden,
                isOwn = post.author.equals(currentUsername, ignoreCase = true),
                onHide = onHidePost,
                onReport = { reportTarget = "t3_${post.id}" to "Report post" },
                onBlock = { blockTarget = post.author },
                onEdit = { editTarget = "t3_${post.id}" to post.body.orEmpty() },
                onDelete = { deleteTarget = "t3_${post.id}" },
            )
        }
        selectedCommentActions?.let { comment ->
            CommentActionsSheet(
                comment = comment,
                isOwn = comment.author.equals(currentUsername, ignoreCase = true),
                onDismiss = { selectedCommentActions = null },
                onReply = { onReply(comment.id) },
                onReport = { reportTarget = "t1_${comment.id}" to "Report comment" },
                onBlock = { blockTarget = comment.author },
                onEdit = { editTarget = "t1_${comment.id}" to comment.body },
                onDelete = { deleteTarget = "t1_${comment.id}" },
            )
        }
        reportTarget?.let { (fullname, title) ->
            TextEntryDialog(
                title = title,
                label = "Reason",
                initialValue = "",
                onDismiss = { reportTarget = null },
                onConfirm = { reason ->
                    if (fullname.startsWith("t3_")) onReportPost(reason)
                    else onReportComment(fullname.removePrefix("t1_"), reason)
                    reportTarget = null
                },
            )
        }
        editTarget?.let { (fullname, body) ->
            TextEntryDialog(
                title = if (fullname.startsWith("t3_")) "Edit post" else "Edit comment",
                label = "Markdown text",
                initialValue = body,
                onDismiss = { editTarget = null },
                onConfirm = { text ->
                    if (fullname.startsWith("t3_")) onEditPost(text)
                    else onEditComment(fullname.removePrefix("t1_"), text)
                    editTarget = null
                },
            )
        }
        deleteTarget?.let { fullname ->
            ConfirmationDialog(
                title = if (fullname.startsWith("t3_")) "Delete post?" else "Delete comment?",
                body = "This permanently deletes it from Reddit.",
                confirmLabel = "Delete",
                onDismiss = { deleteTarget = null },
                onConfirm = {
                    if (fullname.startsWith("t3_")) onDeletePost()
                    else onDeleteComment(fullname.removePrefix("t1_"))
                    deleteTarget = null
                },
            )
        }
        blockTarget?.let { username ->
            ConfirmationDialog(
                title = "Block u/$username?",
                body = "Their content will no longer appear in your Reddit account.",
                confirmLabel = "Block",
                onDismiss = { blockTarget = null },
                onConfirm = {
                    onBlockUser(username)
                    blockTarget = null
                },
            )
        }
    }
}

@Composable
private fun PostDetailHeader(
    post: Post,
    textScale: Float,
    revealNsfw: Boolean,
    onVote: (VoteState) -> Unit,
    onSave: () -> Unit,
    onOpenCommunity: () -> Unit,
    onOpenUser: () -> Unit,
    onOpenDestination: (() -> Unit)?,
    onOpenMedia: () -> Unit,
    onOpenBodyMedia: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val colors = MaterialTheme.otterColors
    var sensitiveMediaRevealed by rememberSaveable(post.id) { mutableStateOf(false) }
    val coversNsfw = post.isNsfw && !revealNsfw
    val warningLabel = when {
        coversNsfw && post.isSpoiler -> "NSFW · SPOILER"
        coversNsfw -> "NSFW"
        post.isSpoiler -> "SPOILER"
        else -> null
    }.takeUnless { sensitiveMediaRevealed }
    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(post.community.accentStartArgb).copy(alpha = .16f),
                shape = CircleShape,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(
                        onClickLabel = "Open ${post.community.name}",
                        role = Role.Button,
                        onClick = onOpenCommunity,
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = post.community.name.take(1).uppercase(),
                        color = Color(post.community.accentStartArgb),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    post.community.name,
                    color = colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        onClickLabel = "Open ${post.community.name}",
                        role = Role.Button,
                        onClick = onOpenCommunity,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val authorCanOpen = post.author != "[deleted]"
                    Text(
                        "u/${post.author}",
                        color = if (authorCanOpen) colors.accent.copy(alpha = .82f) else colors.textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = if (authorCanOpen) {
                            Modifier.clickable(
                                onClickLabel = "Open u/${post.author}",
                                role = Role.Button,
                                onClick = onOpenUser,
                            )
                        } else {
                            Modifier
                        },
                    )
                    Text(
                        " · ${relativeAge(post.createdAtEpochSeconds)}",
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                text = post.domain.orEmpty(),
                color = colors.textTertiary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = post.title,
            color = colors.textPrimary,
            fontSize = (20f * textScale).sp,
            lineHeight = (25f * textScale).sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )

        post.preview?.let { preview ->
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val mediaHeight = (maxWidth / preview.aspectRatio.coerceIn(.75f, 2.2f))
                    .coerceIn(180.dp, 440.dp)
                PostArtwork(
                    preview = preview,
                    type = post.type,
                    badge = mediaBadgeFor(post),
                    cornerRadius = 0,
                    warningLabel = warningLabel,
                    onReveal = { sensitiveMediaRevealed = true },
                    onOpenMedia = onOpenMedia.takeIf { post.media != null },
                    modifier = Modifier.fillMaxWidth().height(mediaHeight),
                )
            }
        }

        post.body?.takeIf { it.isNotBlank() }?.let { body ->
            RedditBody(
                body = body,
                fontSize = (15f * textScale).sp,
                lineHeight = (22f * textScale).sp,
                onOpenMedia = onOpenBodyMedia,
                onOpenLink = onOpenLink,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }

        if (onOpenDestination != null) {
            Surface(
                color = colors.accent.copy(alpha = .12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open post link", onClick = onOpenDestination),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        post.domain ?: "Open link",
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailStat(
                icon = if (post.voteState == VoteState.UPVOTED) Icons.Filled.ArrowUpward else Icons.Outlined.ArrowUpward,
                text = compactNumber(post.score),
                tint = if (post.voteState == VoteState.UPVOTED) colors.upvote else colors.textSecondary,
                onClick = { onVote(VoteState.UPVOTED) },
            )
            Spacer(Modifier.width(18.dp))
            DetailStat(Icons.Outlined.ChatBubbleOutline, compactNumber(post.commentCount), colors.textSecondary, null)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSave, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (post.isSaved) "Unsave" else "Save",
                    tint = if (post.isSaved) colors.saved else colors.textSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
private fun DetailStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    onClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = tint, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CommentSortBar(
    selected: CommentSort,
    loadedCount: Int,
    totalCount: Int,
    loading: Boolean,
    onSelect: (CommentSort) -> Unit,
) {
    val colors = MaterialTheme.otterColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (totalCount > loadedCount) "$loadedCount LOADED · $totalCount TOTAL" else "$loadedCount COMMENTS",
            color = colors.textTertiary,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = .7.sp,
            modifier = Modifier.padding(horizontal = 5.dp),
        )
        if (loading) {
            CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 1.5.dp,
                modifier = Modifier.padding(start = 6.dp).size(14.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        CommentSort.entries.forEach { option ->
            Surface(
                color = if (option == selected) colors.accent.copy(alpha = .16f) else Color.Transparent,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.clickable(role = Role.RadioButton) { onSelect(option) },
            ) {
                Text(
                    option.label,
                    color = if (option == selected) colors.accent else colors.textSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Everything about the post that is not worth a permanent slot in the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostActionsSheet(
    post: Post,
    onDismiss: () -> Unit,
    onVote: (VoteState) -> Unit,
    onSave: () -> Unit,
    onReply: () -> Unit,
    onOpenLink: () -> Unit,
    hidden: Boolean,
    isOwn: Boolean,
    onHide: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            PostActionRow(
                icon = if (post.voteState == VoteState.UPVOTED) {
                    Icons.Filled.ArrowUpward
                } else {
                    Icons.Outlined.ArrowUpward
                },
                label = if (post.voteState == VoteState.UPVOTED) "Remove upvote" else "Upvote",
                tint = if (post.voteState == VoteState.UPVOTED) colors.upvote else colors.textPrimary,
            ) {
                onVote(VoteState.UPVOTED)
                onDismiss()
            }
            PostActionRow(
                icon = Icons.Outlined.ArrowDownward,
                label = if (post.voteState == VoteState.DOWNVOTED) "Remove downvote" else "Downvote",
                tint = if (post.voteState == VoteState.DOWNVOTED) colors.downvote else colors.textPrimary,
            ) {
                onVote(VoteState.DOWNVOTED)
                onDismiss()
            }
            PostActionRow(
                icon = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (post.isSaved) "Unsave" else "Save",
                tint = if (post.isSaved) colors.saved else colors.textPrimary,
            ) {
                onSave()
                onDismiss()
            }
            PostActionRow(
                icon = Icons.AutoMirrored.Outlined.Reply,
                label = "Reply to post",
                tint = colors.textPrimary,
            ) {
                onReply()
                onDismiss()
            }
            PostActionRow(
                icon = Icons.Outlined.OpenInBrowser,
                label = "Open in browser",
                tint = colors.textPrimary,
            ) {
                onOpenLink()
                onDismiss()
            }
            PostActionRow(
                icon = Icons.Outlined.VisibilityOff,
                label = if (hidden) "Unhide on Reddit" else "Hide on Reddit",
                tint = colors.textPrimary,
            ) {
                onHide()
                onDismiss()
            }
            PostActionRow(Icons.Outlined.Flag, "Report", colors.textPrimary) {
                onReport()
                onDismiss()
            }
            PostActionRow(Icons.Outlined.Block, "Block u/${post.author}", colors.textPrimary) {
                onBlock()
                onDismiss()
            }
            if (isOwn && post.body != null) {
                PostActionRow(Icons.Outlined.Edit, "Edit post", colors.textPrimary) {
                    onEdit()
                    onDismiss()
                }
            }
            if (isOwn) {
                PostActionRow(Icons.Outlined.DeleteOutline, "Delete post", colors.downvote) {
                    onDelete()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun PostActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentActionsSheet(
    comment: Comment,
    isOwn: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.padding(bottom = 24.dp)) {
            PostActionRow(Icons.AutoMirrored.Outlined.Reply, "Reply", colors.textPrimary) {
                onReply(); onDismiss()
            }
            PostActionRow(Icons.Outlined.Flag, "Report", colors.textPrimary) {
                onReport(); onDismiss()
            }
            PostActionRow(Icons.Outlined.Block, "Block u/${comment.author}", colors.textPrimary) {
                onBlock(); onDismiss()
            }
            if (isOwn) {
                PostActionRow(Icons.Outlined.Edit, "Edit comment", colors.textPrimary) {
                    onEdit(); onDismiss()
                }
                PostActionRow(Icons.Outlined.DeleteOutline, "Delete comment", colors.downvote) {
                    onDelete(); onDismiss()
                }
            }
        }
    }
}

@Composable
private fun TextEntryDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable(title, initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                minLines = 3,
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
                Text("Submit")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The post header and the sort bar sit above the comments in the same list. */
private const val HEADER_ITEM_COUNT = 2

/**
 * Stands in for replies Reddit did not send, at the depth they belong to. It reads as part of
 * the thread rather than as a button bolted to the end of it.
 */
@Composable
private fun MoreCommentsRow(
    comment: Comment,
    loading: Boolean,
    textScale: Float,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    val depth = comment.depth.coerceAtMost(8)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind {
                repeat(depth) { level ->
                    val x = (RAIL_INSET + RAIL_STEP * level).toPx()
                    drawLine(
                        color = commentRailColors[level % commentRailColors.size],
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
            }
            .clickable(enabled = !loading, onClick = onClick)
            .padding(
                start = RAIL_INSET + RAIL_STEP * depth + 11.dp,
                end = 13.dp,
                top = 11.dp,
                bottom = 11.dp,
            ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = if (loading) {
                "Loading replies…"
            } else {
                "Load ${compactNumber(comment.moreCount)} more " +
                    if (comment.moreCount == 1) "reply" else "replies"
            },
            color = colors.accent,
            fontSize = (14f * textScale).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private val commentRailColors = listOf(
    Color(0xFFFF6B6B),
    Color(0xFFFFA94D),
    Color(0xFFFFD43B),
    Color(0xFF69DB7C),
    Color(0xFF4DABF7),
    Color(0xFFB197FC),
)

/** How far in the first rail sits, and how much each further level steps. */
private val RAIL_INSET = 10.dp
private val RAIL_STEP = 14.dp

private fun commentScoreColor(
    comment: Comment,
    upvote: Color,
    downvote: Color,
    neutral: Color,
): Color = when (comment.voteState) {
    VoteState.UPVOTED -> upvote
    VoteState.DOWNVOTED -> downvote
    VoteState.NONE -> neutral
}

@Composable
private fun CommentRow(
    comment: Comment,
    onOpenCommentMedia: (String) -> Unit,
    onOpenCommentLink: (String) -> Unit,
    hiddenReplies: Int,
    textScale: Float,
    swipeEnabled: Boolean,
    hapticsEnabled: Boolean,
    swipeActions: app.otter.client.ui.SwipeActionConfig,
    onVote: (VoteState) -> Unit,
    onCollapse: () -> Unit,
    onReply: () -> Unit,
    onOpenUser: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    val railColors = commentRailColors
    val depth = comment.depth.coerceAtMost(8)
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
                SwipeAction.Save -> onReply()
                SwipeAction.Hide -> onCollapse()
                SwipeAction.Reply -> onReply()
                SwipeAction.Collapse -> onCollapse()
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .drawBehind {
                    // One rail per ancestor, each a step further in, so the shape of the thread
                    // is readable from the left edge alone.
                    repeat(depth) { level ->
                        val x = (RAIL_INSET + RAIL_STEP * level).toPx()
                        drawLine(
                            color = railColors[level % railColors.size],
                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                            end = androidx.compose.ui.geometry.Offset(x, size.height),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                }
                .clickable(onClickLabel = if (hiddenReplies > 0) "Expand comment" else "Collapse comment") {
                    onCollapse()
                }
                .padding(
                    // Text clears the innermost rail rather than crowding it.
                    start = RAIL_INSET + RAIL_STEP * depth + 11.dp,
                    end = 13.dp,
                    top = 9.dp,
                    bottom = 9.dp,
                ),
        ) {
            // Author on the left, age and score on the right. Nothing else earns a place on
            // the line: a comment is mostly its text.
            Row(verticalAlignment = Alignment.CenterVertically) {
                // One weighted group on the left; a shrink-to-fit weight beside a spacer would
                // keep its unused share and leave the stats short of the edge.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = comment.author,
                        color = when {
                            comment.isSubmitter -> colors.accent
                            comment.isDistinguished -> Color(0xFF57C98A)
                            else -> colors.textSecondary
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .then(
                                if (comment.author != "[deleted]") {
                                    Modifier.clickable(
                                        onClickLabel = "Open u/${comment.author}",
                                        role = Role.Button,
                                        onClick = onOpenUser,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    if (comment.isSubmitter) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "OP",
                            color = colors.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(colors.accent.copy(alpha = .13f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = relativeAge(comment.createdAtEpochSeconds),
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                Spacer(Modifier.width(9.dp))
                Icon(
                    imageVector = if (comment.voteState == VoteState.DOWNVOTED) {
                        Icons.Outlined.ArrowDownward
                    } else {
                        Icons.Outlined.ArrowUpward
                    },
                    contentDescription = null,
                    tint = commentScoreColor(comment, colors.upvote, colors.downvote, colors.textTertiary),
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = compactNumber(comment.score),
                    color = commentScoreColor(comment, colors.upvote, colors.downvote, colors.textTertiary),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                IconButton(onClick = onMore, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "Comment actions",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            if (hiddenReplies > 0) {
                Row(
                    modifier = Modifier.padding(top = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = comment.body,
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "+$hiddenReplies",
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else {
                RedditBody(
                    body = comment.body,
                    fontSize = (15f * textScale).sp,
                    lineHeight = (21f * textScale).sp,
                    onOpenMedia = onOpenCommentMedia,
                    onOpenLink = onOpenCommentLink,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PostTopBar(
    post: Post,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.otterColors
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(colors.surface.copy(alpha = .99f), colors.surface.copy(alpha = .93f)),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(58.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                post.community.name,
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Post", color = colors.textTertiary, style = MaterialTheme.typography.labelMedium)
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "More actions", tint = colors.textPrimary)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = colors.surfaceRaised,
            ) {
                DropdownMenuItem(
                    text = { Text(if (post.isSaved) "Remove from saved" else "Save post") },
                    leadingIcon = {
                        Icon(
                            if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onSave()
                        menuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    onClick = {
                        onShare()
                        menuExpanded = false
                    },
                )
            }
        }
    }
}

private fun sortThreadedComments(
    comments: List<Comment>,
    sort: CommentSort,
    preserveApiOrder: Boolean,
): List<Comment> {
    val children = comments.groupBy(Comment::parentId)
    val comparator = when (sort) {
        CommentSort.Best, CommentSort.Top -> compareByDescending<Comment> { it.score }
        CommentSort.New -> compareByDescending { it.createdAtEpochSeconds }
        CommentSort.Controversial -> compareBy { kotlin.math.abs(it.score) }
    }
    val result = mutableListOf<Comment>()
    fun append(parentId: String?) {
        val siblings = children[parentId].orEmpty().let { items ->
            if (preserveApiOrder) items else items.sortedWith(comparator)
        }
        siblings.forEach { comment ->
            result += comment
            append(comment.id)
        }
    }
    append(null)
    return result
}

private fun countDescendants(commentId: String, comments: List<Comment>): Int {
    val children = comments.groupBy(Comment::parentId)
    fun count(id: String): Int = children[id].orEmpty().sumOf { child -> 1 + count(child.id) }
    return count(commentId)
}
