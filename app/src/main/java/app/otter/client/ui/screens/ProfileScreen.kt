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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otter.client.model.UserProfile
import app.otter.client.ui.components.compactNumber
import app.otter.client.ui.components.relativeAge
import app.otter.client.ui.theme.otterColors
import coil3.compose.AsyncImage

@Composable
fun ProfileScreen(
    username: String,
    profile: UserProfile?,
    loading: Boolean,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.otterColors
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    LazyColumn(
        contentPadding = PaddingValues(
            top = statusBarHeight + 64.dp,
            bottom = navigationBarHeight + 32.dp,
        ),
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(76.dp).clip(CircleShape).background(colors.surfaceRaised),
                ) {
                    if (profile?.iconUrl != null) {
                        AsyncImage(
                            model = profile.iconUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.PersonOutline,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = profile?.displayName?.takeIf { it.isNotBlank() } ?: "u/$username",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("u/${profile?.username ?: username}", color = colors.textSecondary)
                profile?.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        text = description,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (loading) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(top = 18.dp).size(24.dp),
                    )
                } else if (profile != null) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    ) {
                        ProfileStat(compactNumber(profile.totalKarma), "karma")
                        ProfileStat(compactNumber(profile.postKarma), "post")
                        ProfileStat(compactNumber(profile.commentKarma), "comment")
                        ProfileStat(
                            profile.createdAtEpochSeconds.takeIf { it > 0L }
                                ?.let(::relativeAge)
                                ?: "—",
                            "age",
                        )
                    }
                }
            }
        }

        profile?.let { value ->
            item {
                HorizontalDivider(color = colors.divider)
                Text(
                    "RECENT POSTS",
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            if (value.recentPosts.isEmpty()) {
                item {
                    Text(
                        "No recent posts",
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 28.dp),
                    )
                }
            } else {
                items(value.recentPosts, key = { it.id }) { post ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPost(post.id) }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                    ) {
                        Text(
                            post.title,
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${post.community.path} · ${compactNumber(post.score)} points · " +
                                "${compactNumber(post.commentCount)} comments",
                            color = colors.textTertiary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    HorizontalDivider(color = colors.divider, thickness = .6.dp)
                }
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(58.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.textPrimary)
        }
        Text(
            "Profile",
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    val colors = MaterialTheme.otterColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = colors.textPrimary, style = MaterialTheme.typography.labelLarge)
        Text(label, color = colors.textTertiary, style = MaterialTheme.typography.labelSmall)
    }
}
