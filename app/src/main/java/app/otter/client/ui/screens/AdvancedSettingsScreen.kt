package app.otter.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import app.otter.client.data.oauth.RedditApiConfiguration
import app.otter.client.model.RedditAccountState
import app.otter.client.ui.OtterSettings
import app.otter.client.ui.RedditConnectionState
import app.otter.client.ui.WEB_VIEW_SIGN_IN_RATIONALE
import app.otter.client.ui.theme.otterColors

/**
 * Everything about talking to Reddit: the API credentials, how sign-in happens, and the account
 * itself. Split out of the main settings list because it is set up once and then never touched,
 * while the settings above it are adjusted while reading.
 */
@Composable
fun AdvancedSettingsScreen(
    settings: OtterSettings,
    connectionState: RedditConnectionState,
    accountState: RedditAccountState,
    redditApiConfiguration: RedditApiConfiguration,
    onBack: () -> Unit,
    onConnectAccount: () -> Unit,
    onDisconnectAccount: () -> Unit,
    onSaveRedditApiConfiguration: (String, String, String) -> Boolean,
    onResetRedditApiConfiguration: () -> Boolean,
    onToggleWebViewSignIn: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.otterColors
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var showRedditApiConfiguration by rememberSaveable { mutableStateOf(false) }

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
            SettingsSectionLabel("REDDIT CONNECTION")
            SettingsGroup {
                SettingsLinkRow(
                    icon = if (connectionState == RedditConnectionState.Connected) {
                        Icons.Outlined.Key
                    } else {
                        Icons.Outlined.CloudOff
                    },
                    title = when (connectionState) {
                        RedditConnectionState.Unconfigured -> "Reddit not configured"
                        RedditConnectionState.SignedOut -> "Reddit account not connected"
                        RedditConnectionState.Connecting -> "Connecting to Reddit"
                        RedditConnectionState.Connected -> "Reddit API connected"
                        RedditConnectionState.Error -> "Reddit API unavailable"
                    },
                    subtitle = when (connectionState) {
                        RedditConnectionState.Unconfigured -> "Add your Client ID, User-Agent, and Redirect URI"
                        RedditConnectionState.SignedOut -> "Connect your account to load Reddit content"
                        RedditConnectionState.Connecting -> "Loading your account feed"
                        RedditConnectionState.Connected -> "Live listings and comments are available"
                        RedditConnectionState.Error -> "The last Reddit request failed"
                    },
                ) {
                    onMessage(
                        if (connectionState == RedditConnectionState.Connected) {
                            "Reddit browsing is connected"
                        } else {
                            "See README.md for Reddit API setup and troubleshooting"
                        },
                    )
                }
                SettingsDivider()
                SettingsLinkRow(
                    icon = Icons.Outlined.Settings,
                    title = "Reddit API configuration",
                    subtitle = "Client ID · User-Agent · Redirect URI",
                ) { showRedditApiConfiguration = true }
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Smartphone,
                    title = "Sign in inside Otter",
                    subtitle = if (settings.webViewSignIn) {
                        "In-app WebView reads the callback itself"
                    } else {
                        "Off: sign in through a secure browser Auth Tab"
                    },
                    checked = settings.webViewSignIn,
                    onToggle = {
                        onToggleWebViewSignIn()
                        if (!settings.webViewSignIn) onMessage(WEB_VIEW_SIGN_IN_RATIONALE)
                    },
                )
                SettingsDivider()
                SettingsLinkRow(
                    icon = Icons.Outlined.PersonOutline,
                    title = when (accountState) {
                        RedditAccountState.Unavailable -> "Account connection unavailable"
                        RedditAccountState.SignedOut -> "Connect Reddit account"
                        RedditAccountState.Authorizing -> "Waiting for Reddit"
                        is RedditAccountState.SignedIn -> "u/${accountState.account.username}"
                    },
                    subtitle = when (accountState) {
                        RedditAccountState.Unavailable -> "Configure a Reddit client to enable sign-in"
                        RedditAccountState.SignedOut -> if (settings.webViewSignIn) {
                            "Sign in with the in-app Reddit page"
                        } else {
                            "Sign in securely in your browser"
                        }
                        RedditAccountState.Authorizing -> if (settings.webViewSignIn) {
                            "Finish authorization in the in-app page"
                        } else {
                            "Finish authorization in the browser"
                        }
                        is RedditAccountState.SignedIn -> "Connected · tap to disconnect"
                    },
                ) {
                    when (accountState) {
                        RedditAccountState.SignedOut -> onConnectAccount()
                        is RedditAccountState.SignedIn -> onDisconnectAccount()
                        RedditAccountState.Authorizing -> onMessage(
                            if (settings.webViewSignIn) {
                                "Finish Reddit authorization in the in-app page"
                            } else {
                                "Finish Reddit authorization in the browser"
                            },
                        )
                        RedditAccountState.Unavailable -> showRedditApiConfiguration = true
                    }
                }
                SettingsDivider()
                SettingsLinkRow(
                    icon = Icons.Outlined.Lock,
                    title = "Privacy",
                    subtitle = "No analytics or ads · account token encrypted on-device",
                ) { onMessage("Your Reddit refresh token is protected by Android Keystore") }
            }
        }

        item {
            Text(
                text = "These settings decide which Reddit app Otter talks to. Nothing here " +
                    "needs to change once an account is connected.",
                color = colors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            )
        }
    }

    SettingsTopBar(title = "Advanced", onBack = onBack)

    if (showRedditApiConfiguration) {
        RedditApiConfigurationDialog(
            configuration = redditApiConfiguration,
            onDismiss = { showRedditApiConfiguration = false },
            onSave = { clientId, userAgent, redirectUri ->
                onSaveRedditApiConfiguration(clientId, userAgent, redirectUri).also { saved ->
                    if (saved) showRedditApiConfiguration = false
                }
            },
            onReset = {
                onResetRedditApiConfiguration().also { reset ->
                    if (reset) showRedditApiConfiguration = false
                }
            },
        )
    }
}
