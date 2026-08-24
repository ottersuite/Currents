package app.otter.client.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.otter.client.data.DemoRedditContent
import app.otter.client.data.oauth.RedditApiConfiguration
import app.otter.client.model.RedditAccountState
import app.otter.client.ui.screens.FeedScreen
import app.otter.client.ui.screens.PostScreen
import app.otter.client.ui.screens.AdvancedSettingsScreen
import app.otter.client.ui.screens.SettingsScreen
import app.otter.client.ui.theme.OtterTheme

@Preview(
    name = "Otter · compact feed",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Otter · large text",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.3f,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DarkFeedPreview() {
    OtterTheme(darkTheme = true) {
        FeedScreen(
            posts = DemoRedditContent.posts(),
            selectedFeed = "Home",
            sort = FeedSort.Best,
            timeframe = FeedTimeframe.Today,
            searchVisible = false,
            searchQuery = "",
            refreshing = false,
            loadingMore = false,
            connectionState = RedditConnectionState.Unconfigured,
            selectedPostId = null,
            postsAreServerSorted = false,
            hiddenPostIds = emptySet(),
            settings = OtterSettings(),
            onOpenDrawer = {},
            onOpenSearch = {},
            onToggleSearch = {},
            onSearchQueryChange = {},
            onSortChange = {},
            onTimeframeChange = {},
            onRefresh = {},
            onLoadMore = {},
            onCompose = {},
            onOpenPost = {},
            onOpenMedia = {},
            scrollTarget = null,
            onScrollChanged = { _, _ -> },
            onScrollTargetConsumed = {},
            onVote = { _, _ -> },
            onSave = {},
            onHide = {},
        )
    }
}

@Preview(
    name = "Otter · post and comments",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Otter · tablet reader pane",
    widthDp = 720,
    heightDp = 800,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostPreview() {
    val post = DemoRedditContent.posts().first()
    OtterTheme(darkTheme = true) {
        PostScreen(
            post = post,
            comments = DemoRedditContent.commentsByPost()[post.id].orEmpty(),
            commentsLoading = false,
            commentsAreServerSorted = false,
            commentSort = CommentSort.Best,
            collapsedCommentIds = emptySet(),
            settings = OtterSettings(),
            onBack = {},
            onSortChange = {},
            onPostVote = {},
            onSavePost = {},
            onCommentVote = { _, _ -> },
            onToggleCommentCollapsed = {},
            onReply = {},
            onOpenMedia = {},
            onOpenCommentMedia = {},
            onReloadComments = {},
            onOpenDrawer = {},
            expandingComments = emptySet(),
            onLoadMoreComments = {},
        )
    }
}

@Preview(
    name = "Otter · advanced settings",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AdvancedSettingsPreview() {
    OtterTheme(darkTheme = true) {
        AdvancedSettingsScreen(
            settings = OtterSettings(),
            connectionState = RedditConnectionState.Unconfigured,
            accountState = RedditAccountState.SignedOut,
            redditApiConfiguration = RedditApiConfiguration(),
            onBack = {},
            onConnectAccount = {},
            onDisconnectAccount = {},
            onSaveRedditApiConfiguration = { _, _, _ -> true },
            onResetRedditApiConfiguration = { true },
            onToggleWebViewSignIn = {},
            onMessage = {},
        )
    }
}

@Preview(
    name = "Otter · light settings",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun LightSettingsPreview() {
    OtterTheme(darkTheme = false) {
        SettingsScreen(
            settings = OtterSettings(themeMode = ThemeMode.Light),
            connectionState = RedditConnectionState.Unconfigured,
            accountState = RedditAccountState.SignedOut,
            onBack = {},
            onOpenAdvanced = {},
            onThemeModeChange = {},
            onPresentationChange = {},
            onTextScaleChange = {},
            onToggleThumbnailSide = {},
            onToggleSwipeActions = {},
            onToggleHaptics = {},
            onToggleDimRead = {},
            onToggleFlairs = {},
            onToggleAlwaysShowNsfw = {},
            onReset = {},
            onMessage = {},
        )
    }
}
