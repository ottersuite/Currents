# Otter

Otter is an original Android client prototype built around the qualities that make dense, gesture-first iOS Reddit clients pleasant to use: an edge-to-edge feed, fast swipe actions, a compact action dock, a community drawer, colorful nested-comment rails, and deep appearance controls.

It deliberately uses its own name, icon, palette, copy, and Android-native implementation. It does not include Narwhal artwork, source code, proprietary fonts, or Reddit/Snoo artwork.

## What is implemented

- Compact and large-preview feed layouts
- Configurable two-threshold post and comment gestures, with a real four-slot editor
- Configurable feed action bar with search, refresh, saved, compose, mark-above-read, and menu actions
- Right-edge swipeable community drawer whose search also suggests communities from Reddit
- Native media: cached hosted video, muted-by-default GIF/video playback with sound controls, smooth backward scrubbing, and swipeable galleries
- Full-screen viewer with pinch zoom, double-tap zoom, and swipe-down to dismiss
- Sortable feed and comment views
- Post detail with inline media, nested comments, collapse state, and next-thread jump
- Account-backed voting, saving, Reddit hide/unhide, reporting, subscriptions, post creation, and threaded replies
- Edit/delete controls for the connected account's posts and comments; user blocking where Reddit permits the client ID
- Pull down to reload the feed or a post's comments
- Back at the feed level retraces the communities visited, restoring posts and scroll position
- Durable offline feed/comment cache and saved post drafts
- Read posts stay dimmed across launches, with hide-read, mark-above-read, and clear-history controls
- Case-insensitive keyword, community, and author filters
- Optional always-on NSFW reveal, with spoilers still covered
- Dark, light, and system themes
- Adjustable text size, thumbnail side, labels, read-state dimming, haptics, and gesture settings
- Phone navigation plus a classic two-pane layout at 840dp and wider
- Original adaptive app icon and no third-party brand assets
- A blank, honest signed-out state with no fabricated Reddit posts
- Personal Reddit browsing and account connection through installed-app OAuth
- Secure Android Auth Tab callbacks with runtime custom redirect schemes, plus an opt-in in-app sign-in page for redirect URIs no browser can return
- Encrypted local refresh-token storage backed by Android Keystore
- Explicit NSFW/spoiler media gates before any remote preview is requested

## Run it

Requirements:

- Android Studio 2026.1.3 or a compatible setup
- JDK 17+
- Android SDK 37

Open the project in Android Studio and run the `app` configuration, or use:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
./gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Reddit account setup

Otter starts with a blank feed. Connecting Reddit requires a registered OAuth app and compliance with Reddit's current Developer and Data API terms, even when this is a personal, sideloaded client.

To connect your account:

1. Obtain any access or approval Reddit currently requires, then open [Reddit's app preferences](https://www.reddit.com/prefs/apps) while signed in.
2. Create an app, choose the **installed app** type, and register a redirect URI. You can use Otter's default:

   ```text
   app.orca.client://oauth/reddit
   ```

   You can instead choose another lowercase hierarchical custom scheme, such as `my.personal.client://oauth/return` or `my.personal.client:/oauth/return`. A verified `https://` callback with an explicit path is also supported when its domain has the required Digital Asset Links association. Plain HTTP is not supported.

3. Copy the public client ID shown beneath the app name. An installed app must not embed or use a client secret.
4. In Otter, open **Settings → Reddit connection → Reddit API configuration** and enter:

   - **Client ID** — the public installed-app ID
   - **User-Agent** — a unique description of the platform, app/version, and your Reddit username
   - **Redirect URI** — the exact callback registered in step 2

5. Save the configuration, then tap **Connect Reddit account**. Otter applies the new values immediately; no rebuild is needed.

Otter keeps the established `app.orca.client` application ID and default callback so an installed Orca build upgrades in place without losing settings, read history, or encrypted Reddit credentials. Existing Reddit app registrations therefore do not need to change. The saved redirect remains user-configurable: Otter passes its scheme to Android's Auth Tab at runtime, and the returned callback base and one-time OAuth state are both checked before an authorization code is accepted. An authority-only URI may serialize with or without its equivalent root `/`. Use an up-to-date default browser with Auth Tab support.

### Signing in inside Otter

**Settings → Reddit connection → Sign in inside Otter** replaces the Auth Tab with an in-app WebView that watches its own navigations for the configured redirect. It is off by default and exists for one case: a client ID whose registered redirect URI belongs to an OAuth app someone else registered. A browser can only hand a callback to the app that owns the scheme, so that case never returns through an Auth Tab, while a WebView recognizes the redirect as a string and never involves Android's intent router.

The trade-off is real. The Auth Tab keeps Reddit's login page in the browser's process, where Otter cannot see it; the WebView runs that page inside Otter. Reuse of another developer's client ID is also against Reddit's Developer Terms, and the account carrying the risk is yours. Prefer using your own registration with Otter's stable default redirect URI, `app.orca.client://oauth/reddit` — Reddit still permits editing an existing app even where it no longer offers new client IDs.

Both paths converge on the same acceptance check: the callback base must match the configured redirect, the one-time OAuth state must match, and only then is the code exchanged. The in-app page keeps its Reddit cookies across an abandoned attempt so a retry is not a fresh login, and drops them once authorization completes. It requests the page with the WebView marker removed from its user agent, since Reddit serves a degraded login and consent flow to agents that identify as embedded.

For development builds, the client ID and User-Agent can still be supplied as optional defaults in the untracked `local.properties` file:

```properties
reddit.clientId=YOUR_INSTALLED_APP_CLIENT_ID
reddit.userAgent=android:app.orca.client:v1.0.0 (by /u/YOUR_REDDIT_USERNAME)
```

These defaults are used only when no in-app override has been saved. If the active configuration is incomplete—or no Reddit account is connected—the feed remains blank. Reddit redirects back through the secure Auth Tab result, Otter verifies the returned authorization, confirms the account with Reddit, and stores the refresh token encrypted for future sessions. Changing the API configuration or disconnecting removes the local account credentials.

Otter requests only these scopes:

- `identity` — confirm the connected username
- `read` — load posts and comments
- `mysubreddits` — load your communities
- `history` — load account history such as saved posts
- `vote` — vote on posts and comments
- `save` — save or unsave posts
- `submit` — publish posts and replies
- `edit` — edit or delete your posts and comments
- `report` — hide/unhide and report content
- `subscribe` — join or leave communities
- `account` — request user blocking (Reddit limits this endpoint to approved OAuth apps)

When signed in, reads, votes, saves, posts, and replies use that Reddit account. Signed-out mode does not fabricate or load posts. Otter never embeds a client secret.

Before using or distributing the client, review Reddit's current [Data API Terms](https://redditinc.com/policies/data-api-terms), [Developer Terms](https://redditinc.com/policies/developer-terms), [API access guidance](https://support.reddithelp.com/hc/en-us/articles/14945211791892-Developer-Platform-Accessing-Reddit-Data), and [Data API Wiki](https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki). Commercial or broader distribution may require a separate agreement.

## Test on an Android phone

1. On the phone, enable **Developer options** by tapping **Settings → About phone → Build number** seven times.
2. Enable **USB debugging** under **Settings → System → Developer options**.
3. Connect the phone by USB, choose a data-capable USB mode if prompted, and accept the debugging fingerprint on the phone.
4. Confirm the connection with `adb devices`.
5. Either select the phone in Android Studio and run the `app` configuration, or build and install the debug APK:

```powershell
./gradlew.bat assembleDebug
./gradlew.bat :benchmark:connectedBenchmarkAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The macrobenchmarks build the `benchmark` variant, which inherits release minification, so
they measure the shape of build a user actually installs rather than an unminified one.

Open Otter, enter your API values under **Settings → Reddit connection → Reddit API configuration**, connect Reddit, approve the browser authorization, and confirm the Auth Tab returns directly to Otter.

## Project structure

```text
app/                Android application
benchmark/          Macrobenchmarks: cold startup and feed scroll frame timing
baselineprofile/    Baseline profile generator

app/src/main/java/app/otter/client/
├── data/       Reddit API adapter plus test/preview fixtures
├── model/      Immutable post, comment, community, and vote models
├── ui/
│   ├── components/  Swipe rows, artwork, drawer, action bar, composers
│   ├── screens/     Feed, post/comments, settings, and about
│   └── theme/       Otter color and typography tokens
├── OtterViewModel.kt
└── MainActivity.kt
```

The UI consumes the `RedditRepository` contract. `InMemoryRedditRepository` is retained for deterministic tests and previews only; `RedditApiRepository` provides OAuth-backed reads and signed-in account actions.

## Verification

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleRelease
```

The tests cover deterministic fixtures, vote-score transitions, save/read state, reset behavior, local post submission, nested reply insertion, batched read-state marking and the number of feed updates it publishes, API-setting validation, callback normalization, configuration-bound OAuth state and credentials, and sign-out races against in-flight Reddit responses.

## Performance

Release builds are minified and resource-shrunk by R8; `app/proguard-rules.pro` holds the few
keeps R8 cannot infer from the code. The `benchmark` build type inherits that, so the
macrobenchmarks in `benchmark/` measure a build shaped like the one that ships.

`baselineprofile/` generates the ahead-of-time compilation profile that
`androidx.profileinstaller` installs on first run. Without it the installer has nothing to
install and the app starts fully interpreted. Generation needs a connected device or emulator:

```powershell
./gradlew.bat :app:generateBaselineProfile
```

Release builds pick the generated profile up automatically. Collection runs against the
plugin's `nonMinifiedRelease` variant, which is handed the same demo repository the benchmark
build type uses -- otherwise it would profile the signed-out empty state and never reach the
feed, which is the part worth compiling ahead of time.

Compose stability is declared in `compose_stability.conf` rather than by annotating the models,
which keeps `model/` free of any Compose dependency. To confirm a change there landed:

```powershell
./gradlew.bat assembleRelease -PcomposeMetrics
```

## Product boundaries

Otter is currently designed as a personal, sideloaded client rather than a drop-in public release. A public launch still needs Reddit approval, broader authentication and abuse-case review, rule-aware moderation UX, a privacy policy, release signing, and store assets.
