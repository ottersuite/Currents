package app.otter.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.otter.client.model.RedditMessage
import app.otter.client.ui.components.relativeAge
import app.otter.client.ui.theme.otterColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    messages: List<RedditMessage>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onReply: (RedditMessage) -> Unit,
    onOpenUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.otterColors
    val pullState = rememberPullToRefreshState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        SettingsTopBar(title = "Messages", onBack = onBack)
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            state = pullState,
            indicator = {
                Indicator(
                    state = pullState,
                    isRefreshing = isLoading,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = colors.surfaceRaised,
                    color = colors.accent,
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (messages.isEmpty() && !isLoading) {
                EmptyInbox(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    items(messages, key = RedditMessage::fullname) { message ->
                        MessageCard(
                            message = message,
                            onReply = { onReply(message) },
                            onOpenUser = { onOpenUser(message.author) },
                        )
                        HorizontalDivider(
                            color = colors.divider,
                            modifier = Modifier.padding(start = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: RedditMessage,
    onReply: () -> Unit,
    onOpenUser: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (message.isUnread) colors.accent.copy(alpha = .045f) else colors.canvas)
            .padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 7.dp),
    ) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.width(6.dp).padding(top = 5.dp),
        ) {
            if (message.isUnread) {
                Surface(
                    color = colors.accent,
                    shape = CircleShape,
                    modifier = Modifier.size(6.dp),
                ) {}
            }
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.subject.ifBlank {
                        if (message.isCommentReply) "Comment reply" else "Message"
                    },
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = relativeAge(message.createdAtEpochSeconds),
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "u/${message.author}",
                    color = colors.accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.clickable(onClick = onOpenUser),
                )
                Text(
                    text = if (message.isCommentReply) "  ·  reply" else "  ·  private message",
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.body,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onReply, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Reply,
                        contentDescription = "Reply to u/${message.author}",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyInbox(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.otterColors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(28.dp)) {
        Icon(
            Icons.Outlined.MailOutline,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(42.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("No messages yet", color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Replies and private messages from Reddit will appear here.",
            color = colors.textTertiary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
