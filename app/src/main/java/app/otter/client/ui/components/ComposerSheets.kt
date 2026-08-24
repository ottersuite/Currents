package app.otter.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.otter.client.model.Community
import app.otter.client.ui.theme.otterColors
import app.otter.client.ui.PostDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostComposerSheet(
    communities: List<Community>,
    initialDraft: PostDraft = PostDraft(),
    onDraftChange: (PostDraft) -> Unit = {},
    onDismiss: () -> Unit,
    onSubmit: (title: String, body: String, community: String) -> Unit,
) {
    val colors = MaterialTheme.otterColors
    var title by rememberSaveable(initialDraft.title) { mutableStateOf(initialDraft.title) }
    var body by rememberSaveable(initialDraft.body) { mutableStateOf(initialDraft.body) }
    var selectedCommunity by rememberSaveable(initialDraft.community) {
        mutableStateOf(initialDraft.community.ifBlank { communities.firstOrNull()?.path ?: "r/android" })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(bottom = 18.dp),
        ) {
            ComposerHeader(title = "Create a post", onDismiss = onDismiss)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                communities.forEach { community ->
                    val selected = selectedCommunity == community.path
                    Surface(
                        color = if (selected) colors.accent.copy(alpha = .17f) else colors.surfaceRaised,
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.clickable {
                            selectedCommunity = community.path
                            onDraftChange(PostDraft(title, body, selectedCommunity))
                        },
                    ) {
                        Text(
                            community.name,
                            color = if (selected) colors.accent else colors.textSecondary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                }
            }
            ComposerTextField(
                value = title,
                onValueChange = {
                    title = it.take(300)
                    onDraftChange(PostDraft(title, body, selectedCommunity))
                },
                label = "Title",
                singleLine = false,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            ComposerTextField(
                value = body,
                onValueChange = {
                    body = it
                    onDraftChange(PostDraft(title, body, selectedCommunity))
                },
                label = "Text (optional)",
                singleLine = false,
                minLines = 4,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "TEXT POST · SAVED DRAFT",
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSubmit(title, body, selectedCommunity) },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("Post", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplyComposerSheet(
    replyingTo: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val colors = MaterialTheme.otterColors
    var body by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(bottom = 18.dp),
        ) {
            ComposerHeader(
                title = if (replyingTo == null) "Add a comment" else "Reply to u/$replyingTo",
                onDismiss = onDismiss,
            )
            ComposerTextField(
                value = body,
                onValueChange = { body = it },
                label = "What are your thoughts?",
                singleLine = false,
                minLines = 5,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Button(
                onClick = { onSubmit(body) },
                enabled = body.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("Reply", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ComposerHeader(title: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.otterColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 7.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = colors.accent.copy(alpha = .16f), shape = CircleShape) {
            OtterMark(Modifier.padding(5.dp).height(28.dp).width(28.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = colors.textSecondary)
        }
    }
}

@Composable
private fun ComposerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
) {
    val colors = MaterialTheme.otterColors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(13.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedContainerColor = colors.surfaceRaised.copy(alpha = .5f),
            unfocusedContainerColor = colors.surfaceRaised.copy(alpha = .5f),
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.divider,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textTertiary,
            cursorColor = colors.accent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
