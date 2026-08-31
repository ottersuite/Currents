package app.otter.client.ui.components

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.otter.client.model.Community
import app.otter.client.model.SubmissionKind
import app.otter.client.ui.theme.otterColors
import app.otter.client.ui.PostDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostComposerSheet(
    communities: List<Community>,
    initialDraft: PostDraft = PostDraft(),
    onDraftChange: (PostDraft) -> Unit = {},
    onDismiss: () -> Unit,
    onSubmit: (PostDraft) -> Unit,
) {
    val colors = MaterialTheme.otterColors
    // Where the sheet opens: whatever seeded the draft, normally the feed being read, and only
    // then the first subscription, which is a guess rather than an intention.
    val initialCommunity = remember(initialDraft.community, communities) {
        initialDraft.community.ifBlank { communities.firstOrNull()?.path ?: "r/android" }
    }
    var title by rememberSaveable(initialDraft.title) { mutableStateOf(initialDraft.title) }
    var body by rememberSaveable(initialDraft.body) { mutableStateOf(initialDraft.body) }
    var linkUrl by rememberSaveable(initialDraft.linkUrl) { mutableStateOf(initialDraft.linkUrl) }
    var kind by rememberSaveable(initialDraft.kind) { mutableStateOf(initialDraft.kind) }
    var selectedCommunity by rememberSaveable(initialCommunity) { mutableStateOf(initialCommunity) }
    // That default leads the row. It is not always a subscription -- posting to a community you
    // only browse is ordinary -- and even when it is, leaving it thirty chips deep in a horizontal
    // scroller hides where the post is about to go. Keyed on the opening default rather than on
    // the selection, so tapping a chip does not reshuffle the row under the finger.
    val targets = remember(communities, initialCommunity) {
        val subscribed = communities.map { ComposerTarget(it.path, it.name) }
        val lead = subscribed.firstOrNull { it.path.equals(initialCommunity, ignoreCase = true) }
            ?: ComposerTarget(initialCommunity, initialCommunity.removePrefix("r/"))
        listOf(lead) + subscribed.filterNot { it.path.equals(lead.path, ignoreCase = true) }
    }

    // Both kinds' contents are held at once, so switching kind to look at the other field and
    // switching back does not discard what was already typed.
    fun draft() = PostDraft(
        title = title,
        body = body,
        community = selectedCommunity,
        kind = kind,
        linkUrl = linkUrl,
    )

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
                targets.forEach { target ->
                    ComposerChip(
                        label = target.label,
                        selected = selectedCommunity.equals(target.path, ignoreCase = true),
                        onClick = {
                            selectedCommunity = target.path
                            onDraftChange(draft())
                        },
                    )
                    Spacer(Modifier.width(7.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
            ) {
                SubmissionKind.entries.forEach { option ->
                    ComposerChip(
                        label = option.composerLabel,
                        selected = kind == option,
                        onClick = {
                            kind = option
                            onDraftChange(draft())
                        },
                    )
                    Spacer(Modifier.width(7.dp))
                }
            }
            ComposerTextField(
                value = title,
                onValueChange = {
                    title = it.take(300)
                    onDraftChange(draft())
                },
                label = "Title",
                singleLine = false,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            when (kind) {
                SubmissionKind.TEXT -> ComposerTextField(
                    value = body,
                    onValueChange = {
                        body = it
                        onDraftChange(draft())
                    },
                    label = "Text (optional)",
                    singleLine = false,
                    minLines = 4,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )

                SubmissionKind.LINK -> ComposerTextField(
                    value = linkUrl,
                    onValueChange = {
                        linkUrl = it
                        onDraftChange(draft())
                    },
                    label = "Link",
                    singleLine = true,
                    // A web address is not prose. Sentence casing and spelling correction would
                    // corrupt it, which is exactly what they are here to do to the other fields.
                    keyboardOptions = URL_KEYBOARD,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${kind.composerLabel.uppercase()} POST · SAVED DRAFT",
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSubmit(draft()) },
                    enabled = title.isNotBlank() &&
                        (kind != SubmissionKind.LINK || linkUrl.isNotBlank()),
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

/** A selectable pill: one community destination, or one submission kind. */
@Composable
private fun ComposerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.otterColors
    Surface(
        color = if (selected) colors.accent.copy(alpha = .17f) else colors.surfaceRaised,
        shape = RoundedCornerShape(11.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (selected) colors.accent else colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
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
    keyboardOptions: KeyboardOptions = PROSE_KEYBOARD,
) {
    val colors = MaterialTheme.otterColors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
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

/**
 * The default for every field in these sheets, all of which hold prose.
 *
 * Left at Compose's defaults the keyboard treats them as neutral text -- no sentence casing, no
 * spelling correction -- so titles, bodies and replies went out lowercase and uncorrected.
 */
private val PROSE_KEYBOARD = KeyboardOptions(
    capitalization = KeyboardCapitalization.Sentences,
    autoCorrectEnabled = true,
    keyboardType = KeyboardType.Text,
)

/** The one composer field that is not prose. */
private val URL_KEYBOARD = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Uri,
)

/** One destination chip in the post composer's community row. */
private data class ComposerTarget(val path: String, val label: String)

/** How a submission kind reads on its chip and in the sheet's footer. */
private val SubmissionKind.composerLabel: String
    get() = when (this) {
        SubmissionKind.TEXT -> "Text"
        SubmissionKind.LINK -> "Link"
    }
