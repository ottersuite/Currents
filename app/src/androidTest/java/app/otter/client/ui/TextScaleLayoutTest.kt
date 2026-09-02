package app.otter.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.otter.client.model.RedditAccountState
import app.otter.client.ui.screens.SettingsScreen
import app.otter.client.ui.theme.OtterTheme
import org.junit.Rule
import org.junit.Test

class TextScaleLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsFitsCompactPhoneAt85Percent() = verifyScale(.85f)

    @Test
    fun settingsFitsCompactPhoneAt130Percent() = verifyScale(1.3f)

    private fun verifyScale(scale: Float) {
        compose.setContent {
            OtterTheme(textScale = scale) {
                Box(Modifier.width(360.dp).height(720.dp)) {
                    SettingsScreen(
                        settings = OtterSettings(textScale = scale),
                        connectionState = RedditConnectionState.SignedOut,
                        accountState = RedditAccountState.SignedOut,
                        onBack = {},
                        onOpenAdvanced = {},
                        onThemeModeChange = {},
                        onPresentationChange = {},
                        onDefaultPostSortChange = {},
                        onDefaultCommentSortChange = {},
                        onTextScaleChange = {},
                        onToggleAutoplayMedia = {},
                        onTogglePrefetchMedia = {},
                        onToggleShowThumbnails = {},
                        onMediaQualityChange = {},
                        onToggleThumbnailSide = {},
                        onToggleSwipeActions = {},
                        onToggleHaptics = {},
                        onToggleDimRead = {},
                        onToggleFlairs = {},
                        onOpenNsfw = {},
                        onReset = {},
                        onMessage = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Theme").assertIsDisplayed()
        compose.onNodeWithText("AMOLED").assertIsDisplayed()
    }
}
