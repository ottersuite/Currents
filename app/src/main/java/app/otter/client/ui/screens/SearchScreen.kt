package app.otter.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otter.client.model.Community
import app.otter.client.ui.theme.otterColors

/**
 * Reddit-wide search.
 *
 * Typing does not commit to an interpretation: the same words could be a post search, a
 * community, or a username, so all three are offered and the communities that match are listed
 * underneath as they arrive.
 */
@Composable
fun SearchScreen(
    query: String,
    suggestions: List<Community>,
    onQueryChange: (String) -> Unit,
    onSearchPosts: (String) -> Unit,
    onOpenCommunity: (String) -> Unit,
    onToggleSubscription: (Community) -> Unit,
    onOpenUser: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.otterColors
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val trimmed = query.trim()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun leaveWith(action: () -> Unit) {
        keyboard?.hide()
        action()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { leaveWith(onBack) }) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }
            Surface(
                color = colors.surfaceRaised,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search Reddit",
                                color = colors.textTertiary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.textPrimary,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (trimmed.isNotEmpty()) leaveWith { onSearchPosts(trimmed) }
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Clear search",
                            tint = colors.textTertiary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onQueryChange("") },
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
        }

        if (trimmed.isEmpty()) {
            Text(
                text = "Search posts, communities, and people across Reddit.",
                color = colors.textTertiary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Surface(
                    color = colors.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Column {
                        SearchActionRow(
                            icon = Icons.Outlined.Search,
                            label = "Search all posts for \"$trimmed\"",
                        ) { leaveWith { onSearchPosts(trimmed) } }
                        HorizontalDivider(
                            color = colors.divider,
                            modifier = Modifier.padding(start = 52.dp),
                        )
                        SearchActionRow(
                            icon = Icons.Outlined.Groups,
                            label = "View community $trimmed",
                        ) { leaveWith { onOpenCommunity(trimmed) } }
                        HorizontalDivider(
                            color = colors.divider,
                            modifier = Modifier.padding(start = 52.dp),
                        )
                        SearchActionRow(
                            icon = Icons.Outlined.PersonOutline,
                            label = "View user u/$trimmed",
                        ) { leaveWith { onOpenUser(trimmed) } }
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                item {
                    Text(
                        text = "COMMUNITIES MATCHING \"${trimmed.uppercase()}\"",
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 6.dp),
                    )
                }
                items(suggestions, key = { it.name }) { community ->
                    CommunityResultRow(
                        community = community,
                        onToggleSubscription = { onToggleSubscription(community) },
                    ) { leaveWith { onOpenCommunity(community.name) } }
                }
            }
        }
    }
}

@Composable
private fun SearchActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.otterColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForwardIos,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun CommunityResultRow(
    community: Community,
    onToggleSubscription: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = community.name,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = subscriberSummary(community.memberCount),
                color = colors.textTertiary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        IconButton(onClick = onToggleSubscription) {
            Icon(
                if (community.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (community.isFavorite) "Leave community" else "Join community",
                tint = if (community.isFavorite) colors.accent else colors.textTertiary,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForwardIos,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(13.dp),
        )
    }
}

private fun subscriberSummary(memberCount: Int): String = when {
    memberCount <= 0 -> "Community"
    memberCount >= 1_000_000 -> "%.1fM subscribers".format(memberCount / 1_000_000f)
    memberCount >= 1_000 -> "%,d subscribers".format(memberCount)
    else -> "$memberCount subscribers"
}
