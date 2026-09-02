package app.otter.client.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.otter.client.BuildConfig
import app.otter.client.data.oauth.RedditApiConfiguration
import app.otter.client.model.RedditAccountState
import app.otter.client.ui.OtterSettings
import app.otter.client.ui.CommentSort
import app.otter.client.ui.FeedPresentation
import app.otter.client.ui.FeedAction
import app.otter.client.ui.FeedSort
import app.otter.client.ui.MediaQuality
import app.otter.client.ui.SwipeActionConfig
import app.otter.client.ui.RedditConnectionState
import app.otter.client.ui.ThemeMode
import app.otter.client.ui.WEB_VIEW_SIGN_IN_RATIONALE
import app.otter.client.ui.openWebLink
import app.otter.client.ui.components.OtterMark
import app.otter.client.ui.components.SwipeAction
import app.otter.client.ui.theme.otterColors

@Composable
fun SettingsScreen(
    settings: OtterSettings,
    connectionState: RedditConnectionState,
    accountState: RedditAccountState,
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPresentationChange: (FeedPresentation) -> Unit,
    onDefaultPostSortChange: (FeedSort) -> Unit,
    onDefaultCommentSortChange: (CommentSort) -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onToggleAutoplayMedia: () -> Unit,
    onTogglePrefetchMedia: () -> Unit,
    onToggleShowThumbnails: () -> Unit,
    onMediaQualityChange: (MediaQuality) -> Unit,
    onToggleThumbnailSide: () -> Unit,
    onToggleSwipeActions: () -> Unit,
    onToggleHaptics: () -> Unit,
    onToggleDimRead: () -> Unit,
    onToggleFlairs: () -> Unit,
    onOpenNsfw: () -> Unit,
    onReset: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onUpdateFeedActions: (List<FeedAction>) -> Unit = {},
    onUpdatePostSwipeActions: (SwipeActionConfig) -> Unit = {},
    onUpdateCommentSwipeActions: (SwipeActionConfig) -> Unit = {},
    onToggleHideRead: () -> Unit = {},
    onUpdateFilters: (Set<String>, Set<String>, Set<String>) -> Unit = { _, _, _ -> },
    onClearReadHistory: () -> Unit = {},
    onToggleOpenLinksInApp: () -> Unit = {},
) {
    val colors = MaterialTheme.otterColors
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var showActionEditor by rememberSaveable { mutableStateOf(false) }
    var showFilterEditor by rememberSaveable { mutableStateOf(false) }
    var showClearReadHistoryConfirmation by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(top = statusBarHeight + 64.dp, bottom = navigationBarHeight + 32.dp),
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        item {
            SettingsSectionLabel("APPEARANCE")
            SettingsGroup {
                SettingsHeader(Icons.Outlined.Palette, "Theme")
                SegmentPicker(
                    choices = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = {
                        when (it) {
                            ThemeMode.System -> "System"
                            ThemeMode.Light -> "Light"
                            ThemeMode.Dark -> "Dark"
                            ThemeMode.Amoled -> "AMOLED"
                        }
                    },
                    onSelect = onThemeModeChange,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 13.dp),
                )
                SettingsDivider()
                SettingsHeader(Icons.Outlined.ViewAgenda, "Post layout")
                SegmentPicker(
                    choices = FeedPresentation.entries,
                    selected = settings.feedPresentation,
                    label = { if (it == FeedPresentation.Compact) "Compact" else "Big preview" },
                    onSelect = onPresentationChange,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 13.dp),
                )
                SettingsDivider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIcon(Icons.Outlined.FormatSize)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Text size", color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${(settings.textScale * 100).toInt()}% · The quick brown fox",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Slider(
                        value = settings.textScale,
                        onValueChange = onTextScaleChange,
                        valueRange = .85f..1.3f,
                        steps = 8,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }

        item {
            SettingsSectionLabel("DEFAULT SORTING")
            SettingsGroup {
                SettingsHeader(Icons.Outlined.SwapHoriz, "Posts")
                SegmentPicker(
                    choices = FeedSort.entries,
                    selected = settings.defaultPostSort,
                    label = FeedSort::label,
                    onSelect = onDefaultPostSortChange,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 13.dp),
                )
                SettingsDivider()
                SettingsHeader(Icons.Outlined.SwapHoriz, "Comments")
                SegmentPicker(
                    choices = CommentSort.entries,
                    selected = settings.defaultCommentSort,
                    label = CommentSort::label,
                    onSelect = onDefaultCommentSortChange,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 13.dp),
                )
            }
        }

        item {
            SettingsSectionLabel("POSTS & GESTURES")
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Image,
                    title = "Thumbnails on the right",
                    subtitle = "Turn off to move previews to the leading edge",
                    checked = settings.thumbnailsOnRight,
                    onToggle = onToggleThumbnailSide,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Gesture,
                    title = "Two-stage swipe actions",
                    subtitle = "Swipe farther for a second action",
                    checked = settings.swipeActions,
                    onToggle = onToggleSwipeActions,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Vibration,
                    title = "Threshold haptics",
                    subtitle = "A light tick when a swipe action is armed",
                    checked = settings.haptics,
                    onToggle = onToggleHaptics,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Visibility,
                    title = "Dim read posts",
                    subtitle = "Keep your place without hiding anything",
                    checked = settings.dimReadPosts,
                    onToggle = onToggleDimRead,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Visibility,
                    title = "Hide read posts",
                    subtitle = "Remove posts you have opened from feed results",
                    checked = settings.hideReadPosts,
                    onToggle = onToggleHideRead,
                )
                SettingsDivider()
                SettingsLinkRow(
                    icon = Icons.Outlined.FilterAlt,
                    title = "Content filters",
                    subtitle = filterSummary(settings),
                    onClick = { showFilterEditor = true },
                )
                SettingsDivider()
                SettingsLinkRow(
                    icon = Icons.Outlined.DoneAll,
                    title = "Clear read history",
                    subtitle = "Make locally read posts unread again",
                    onClick = { showClearReadHistoryConfirmation = true },
                )
                SettingsDivider()
                SettingsLinkRow(
                    icon = Icons.Outlined.Visibility,
                    title = "NSFW content",
                    subtitle = listOf(
                        if (settings.alwaysShowNsfw) "Media shown" else "Media covered",
                        if (settings.showRandomNsfwButton) "Random button visible" else "Random button hidden",
                    ).joinToString(" · "),
                    onClick = onOpenNsfw,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.LocalOffer,
                    title = "Post type labels",
                    subtitle = "Show gallery, video, and domain labels",
                    checked = settings.showPostFlairs,
                    onToggle = onToggleFlairs,
                )
            }
        }

        item {
            SettingsSectionLabel("BROWSING")
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Outlined.OpenInBrowser,
                    title = "Open links in app",
                    subtitle = "Private tabs with no browser history or shared cookies",
                    checked = settings.openLinksInApp,
                    onToggle = onToggleOpenLinksInApp,
                )
            }
        }

        item {
            SettingsSectionLabel("DATA SAVER")
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Outlined.CloudOff,
                    title = "Autoplay media",
                    subtitle = "Start videos and GIFs when opened or previewed",
                    checked = settings.autoplayMedia,
                    onToggle = onToggleAutoplayMedia,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Refresh,
                    title = "Prefetch media",
                    subtitle = "Load upcoming media before it is opened",
                    checked = settings.prefetchMedia,
                    onToggle = onTogglePrefetchMedia,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Image,
                    title = "Feed thumbnails",
                    subtitle = "Show image and video previews while browsing",
                    checked = settings.showThumbnails,
                    onToggle = onToggleShowThumbnails,
                )
                SettingsDivider()
                SettingsHeader(Icons.Outlined.ViewAgenda, "Media quality")
                SegmentPicker(
                    choices = MediaQuality.entries,
                    selected = settings.mediaQuality,
                    label = MediaQuality::label,
                    onSelect = onMediaQualityChange,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 13.dp),
                )
            }
        }

        item {
            SettingsSectionLabel("ACTION BAR & NAVIGATION")
            SettingsGroup {
                Column(Modifier.padding(14.dp)) {
                    Text("Feed action bar", color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Choose, order, and map the actions you use most.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp, bottom = 13.dp),
                    )
                    ActionEditorPreview(settings.feedActions) { showActionEditor = true }
                }
                SettingsDivider()
            }
        }

        item {
            SettingsSectionLabel("ACCOUNT")
            SettingsGroup {
                SettingsLinkRow(
                    icon = Icons.Outlined.Tune,
                    title = "Advanced",
                    subtitle = when (accountState) {
                        is RedditAccountState.SignedIn ->
                            "Connected as u/${accountState.account.username}"
                        RedditAccountState.Authorizing -> "Waiting for Reddit"
                        else -> when (connectionState) {
                            RedditConnectionState.Unconfigured -> "Reddit API is not configured yet"
                            RedditConnectionState.Error -> "The last Reddit request failed"
                            else -> "No Reddit account connected"
                        }
                    },
                ) { onOpenAdvanced() }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onReset) {
                    Text("Restore default settings", color = colors.upvote)
                }
            }
        }
    }

    SettingsTopBar(title = "Settings", onBack = onBack)

    if (showActionEditor) {
        ActionEditorDialog(
            settings = settings,
            onDismiss = { showActionEditor = false },
            onSave = { feed, postSwipes, commentSwipes ->
                onUpdateFeedActions(feed)
                onUpdatePostSwipeActions(postSwipes)
                onUpdateCommentSwipeActions(commentSwipes)
                showActionEditor = false
            },
        )
    }
    if (showFilterEditor) {
        ContentFilterDialog(
            settings = settings,
            onDismiss = { showFilterEditor = false },
            onSave = { keywords, communities, authors ->
                onUpdateFilters(keywords, communities, authors)
                showFilterEditor = false
            },
        )
    }
    if (showClearReadHistoryConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearReadHistoryConfirmation = false },
            title = { Text("Clear read history?") },
            text = {
                Text("All posts marked as read on this device will become unread. This can’t be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearReadHistoryConfirmation = false
                        onClearReadHistory()
                    },
                ) {
                    Text("Clear", color = colors.upvote)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearReadHistoryConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun NsfwSettingsScreen(
    settings: OtterSettings,
    onBack: () -> Unit,
    onToggleAlwaysShowNsfw: () -> Unit,
    onToggleShowRandomNsfwButton: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.otterColors
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    LazyColumn(
        contentPadding = PaddingValues(top = statusBarHeight + 64.dp, bottom = navigationBarHeight + 32.dp),
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        item {
            SettingsSectionLabel("NSFW CONTENT")
            SettingsGroup {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                    Text(
                        "Choose how adult posts appear and whether the discovery shortcut is available in the side menu.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Spoilers remain covered even when NSFW media is shown automatically.",
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Visibility,
                    title = "Always show NSFW media",
                    subtitle = "Skip the cover on adult posts",
                    checked = settings.alwaysShowNsfw,
                    onToggle = onToggleAlwaysShowNsfw,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Casino,
                    title = "Random NSFW button",
                    subtitle = "Show the adult-community shortcut in the side menu",
                    checked = settings.showRandomNsfwButton,
                    onToggle = onToggleShowRandomNsfwButton,
                )
            }
        }
    }

    SettingsTopBar(title = "NSFW content", onBack = onBack)
}

@Composable
internal fun RedditApiConfigurationDialog(
    configuration: RedditApiConfiguration,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Boolean,
    onReset: () -> Boolean,
) {
    val colors = MaterialTheme.otterColors
    var clientId by rememberSaveable(configuration.clientId) { mutableStateOf(configuration.clientId) }
    var userAgent by rememberSaveable(configuration.userAgent) { mutableStateOf(configuration.userAgent) }
    var redirectUri by rememberSaveable(configuration.redirectUri) { mutableStateOf(configuration.redirectUri) }
    var validationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val draft = RedditApiConfiguration(
        clientId = clientId,
        userAgent = userAgent,
        redirectUri = redirectUri,
    ).normalized()

    LaunchedEffect(validationMessage) {
        if (validationMessage != null) scrollState.animateScrollTo(0)
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = colors.surfaceRaised,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("Clear Reddit API settings?") },
            text = {
                Text(
                    "This removes your client details and disconnects the current Reddit session. Currents does not provide a client ID.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!onReset()) {
                            confirmReset = false
                            validationMessage = "Reddit API settings could not be cleared"
                        }
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Keep custom values")
                }
            },
        )
        return
    }

    val clientIdHasError = validationMessage?.startsWith("Client ID") == true
    val userAgentHasError = validationMessage?.startsWith("User-Agent") == true
    val redirectHasError = validationMessage?.startsWith("Redirect URI") == true

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = { Text("Reddit API configuration") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Currents does not include a Reddit client ID. Enter details for your own registered Reddit app. Changing them disconnects the current session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                validationMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
                OutlinedTextField(
                    value = clientId,
                    onValueChange = {
                        clientId = it
                        validationMessage = null
                    },
                    label = { Text("Client ID") },
                    supportingText = {
                        Text(
                            if (clientIdHasError) validationMessage.orEmpty()
                            else "The public ID for an installed Reddit app",
                        )
                    },
                    isError = clientIdHasError,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = {
                        userAgent = it
                        validationMessage = null
                    },
                    label = { Text("User-Agent") },
                    supportingText = {
                        Text(
                            if (userAgentHasError) validationMessage.orEmpty()
                            else "Example: android:app.orca.client:v1.0 (by /u/you)",
                        )
                    },
                    isError = userAgentHasError,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = redirectUri,
                    onValueChange = {
                        redirectUri = it
                        validationMessage = null
                    },
                    label = { Text("Redirect URI") },
                    supportingText = {
                        Text(
                            if (redirectHasError) validationMessage.orEmpty()
                            else "Use a custom scheme or verified HTTPS; match Reddit exactly",
                        )
                    },
                    isError = redirectHasError,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Uri,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { confirmReset = true },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text("Clear API settings")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val error = draft.validationError()
                    if (error == null) {
                        if (!onSave(draft.clientId, draft.userAgent, draft.redirectUri)) {
                            validationMessage = "Reddit API settings could not be saved"
                        }
                    } else {
                        validationMessage = error
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    openLinksInApp: Boolean = true,
) {
    val colors = MaterialTheme.otterColors
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        OtterMark(Modifier.size(92.dp))
        Spacer(Modifier.height(18.dp))
        Text("Currents", color = colors.textPrimary, style = MaterialTheme.typography.headlineSmall)
        Text("a calm, fast client for Reddit", color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.divider),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Designed for dense reading",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "Currents pairs a compact feed, nested comment rails, configurable themes, and fast two-stage gestures with an original Android-native identity.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(15.dp))
                Text(
                    "Currents is an independent prototype and is not affiliated with Reddit or Robot Swingset. Narwhal names, artwork, and branding are not included.",
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(
            color = colors.surfaceRaised,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.clickable {
                context.openWebLink(
                    "https://redditinc.com/policies/data-api-terms",
                    inApp = openLinksInApp,
                )
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Key, contentDescription = null, tint = colors.accent)
                Spacer(Modifier.width(10.dp))
                Text("Reddit Data API terms", color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Version ${BuildConfig.VERSION_NAME} · built with Jetpack Compose",
            color = colors.textTertiary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(18.dp))
    }
    SettingsTopBar(title = "About Currents", onBack = onBack)
}

@Composable
internal fun SettingsTopBar(title: String, onBack: () -> Unit) {
    val colors = MaterialTheme.otterColors
    Row(
        modifier = Modifier
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
        Text(
            title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(48.dp))
    }

}

@Composable
internal fun SettingsSectionLabel(text: String) {
    val colors = MaterialTheme.otterColors
    Text(
        text,
        color = colors.textTertiary,
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 7.dp),
    )
}

@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.otterColors
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(.7.dp, colors.divider),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsHeader(icon: ImageVector, title: String) {
    val colors = MaterialTheme.otterColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(11.dp))
        Text(title, color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    val colors = MaterialTheme.otterColors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(32.dp).background(colors.accent.copy(alpha = .13f), RoundedCornerShape(9.dp)),
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch) { onToggle() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(9.dp))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textTertiary,
                uncheckedTrackColor = colors.surfaceRaised,
                uncheckedBorderColor = colors.divider,
            ),
        )
    }
}

@Composable
internal fun SettingsLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.otterColors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary)
    }
}

@Composable
internal fun SettingsDivider() {
    val colors = MaterialTheme.otterColors
    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(start = 57.dp))
}

@Composable
private fun <T> SegmentPicker(
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.otterColors
    Row(
        modifier = modifier.fillMaxWidth().background(colors.surfaceRaised, RoundedCornerShape(11.dp)).padding(3.dp),
    ) {
        choices.forEach { choice ->
            val active = choice == selected
            Surface(
                color = if (active) colors.surfaceGlass else Color.Transparent,
                shape = RoundedCornerShape(9.dp),
                shadowElevation = if (active) 2.dp else 0.dp,
                modifier = Modifier.weight(1f).clickable(role = Role.RadioButton) { onSelect(choice) },
            ) {
                Text(
                    label(choice),
                    color = if (active) colors.accent else colors.textSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionEditorPreview(actions: List<FeedAction>, onClick: () -> Unit) {
    val colors = MaterialTheme.otterColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .horizontalScroll(rememberScrollState())
            .background(colors.surfaceRaised, RoundedCornerShape(24.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        actions.forEach { action ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(45.dp)
                    .background(if (action == FeedAction.Compose) colors.accent else Color.Transparent, CircleShape),
            ) {
                Icon(
                    feedActionIcon(action),
                    contentDescription = action.label,
                    tint = if (action == FeedAction.Compose) Color.White else colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionEditorDialog(
    settings: OtterSettings,
    onDismiss: () -> Unit,
    onSave: (List<FeedAction>, SwipeActionConfig, SwipeActionConfig) -> Unit,
) {
    var feedActions by remember(settings.feedActions) { mutableStateOf(settings.feedActions) }
    var postSwipes by remember(settings.postSwipeActions) { mutableStateOf(settings.postSwipeActions) }
    var commentSwipes by remember(settings.commentSwipeActions) { mutableStateOf(settings.commentSwipeActions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Actions & swipes") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Feed action bar", style = MaterialTheme.typography.titleSmall)
                FeedAction.entries.forEach { action ->
                    val index = feedActions.indexOf(action)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(feedActionIcon(action), contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(action.label, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        if (index >= 0) {
                            IconButton(
                                onClick = {
                                    if (index > 0) feedActions = feedActions.toMutableList().apply {
                                        add(index - 1, removeAt(index))
                                    }
                                },
                                enabled = index > 0,
                            ) { Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move up") }
                            IconButton(
                                onClick = {
                                    if (index < feedActions.lastIndex) feedActions = feedActions.toMutableList().apply {
                                        add(index + 1, removeAt(index))
                                    }
                                },
                                enabled = index < feedActions.lastIndex,
                            ) { Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move down") }
                        }
                        IconButton(
                            onClick = {
                                feedActions = if (index >= 0) {
                                    feedActions - action
                                } else if (feedActions.size < 5) {
                                    feedActions + action
                                } else {
                                    feedActions
                                }
                            },
                        ) {
                            Icon(
                                if (index >= 0) Icons.Outlined.Check else Icons.Outlined.Add,
                                contentDescription = if (index >= 0) "Remove" else "Add",
                            )
                        }
                    }
                }
                Text("Post swipes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                SwipeConfigEditor(postSwipes, POST_SWIPE_CHOICES) { postSwipes = it }
                Text("Comment swipes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                SwipeConfigEditor(commentSwipes, COMMENT_SWIPE_CHOICES) { commentSwipes = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = feedActions.isNotEmpty(),
                onClick = { onSave(feedActions, postSwipes, commentSwipes) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SwipeConfigEditor(
    config: SwipeActionConfig,
    choices: List<SwipeAction>,
    onChange: (SwipeActionConfig) -> Unit,
) {
    listOf(
        "Right · short" to config.rightShort,
        "Right · long" to config.rightLong,
        "Left · short" to config.leftShort,
        "Left · long" to config.leftLong,
    ).forEachIndexed { index, (label, action) ->
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                val next = choices[(choices.indexOf(action).coerceAtLeast(0) + 1) % choices.size]
                onChange(when (index) {
                    0 -> config.copy(rightShort = next)
                    1 -> config.copy(rightLong = next)
                    2 -> config.copy(leftShort = next)
                    else -> config.copy(leftLong = next)
                })
            }.padding(vertical = 9.dp),
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Text(action.name, color = MaterialTheme.otterColors.accent)
        }
    }
}

@Composable
private fun ContentFilterDialog(
    settings: OtterSettings,
    onDismiss: () -> Unit,
    onSave: (Set<String>, Set<String>, Set<String>) -> Unit,
) {
    var keywords by rememberSaveable { mutableStateOf(settings.blockedKeywords.joinToString(", ")) }
    var communities by rememberSaveable { mutableStateOf(settings.blockedCommunities.joinToString(", ")) }
    var authors by rememberSaveable { mutableStateOf(settings.blockedAuthors.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Content filters") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Separate entries with commas. Matching is case-insensitive.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(keywords, { keywords = it }, label = { Text("Keywords") })
                OutlinedTextField(communities, { communities = it }, label = { Text("Communities") })
                OutlinedTextField(authors, { authors = it }, label = { Text("Authors") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(keywords.csvSet(), communities.csvSet(), authors.csvSet())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun String.csvSet(): Set<String> = split(',').map(String::trim).filter(String::isNotBlank).toSet()

private fun filterSummary(settings: OtterSettings): String {
    val count = settings.blockedKeywords.size + settings.blockedCommunities.size + settings.blockedAuthors.size
    return if (count == 0) "No blocked keywords, communities, or authors" else "$count active filters"
}

private fun feedActionIcon(action: FeedAction): ImageVector = when (action) {
    FeedAction.Search -> Icons.Outlined.Search
    FeedAction.Refresh -> Icons.Outlined.Refresh
    FeedAction.Saved -> Icons.Outlined.BookmarkBorder
    FeedAction.Compose -> Icons.Outlined.Add
    FeedAction.MarkAboveRead -> Icons.Outlined.DoneAll
    FeedAction.Menu -> Icons.Outlined.Menu
}

private val POST_SWIPE_CHOICES = listOf(
    SwipeAction.Upvote,
    SwipeAction.Downvote,
    SwipeAction.Save,
    SwipeAction.Hide,
)
private val COMMENT_SWIPE_CHOICES = listOf(
    SwipeAction.Upvote,
    SwipeAction.Downvote,
    SwipeAction.Reply,
    SwipeAction.Collapse,
)
