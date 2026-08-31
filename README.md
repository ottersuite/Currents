<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_artwork.png" width="168" alt="Currents app icon">
</p>

<h1 align="center">Currents</h1>

<p align="center">
  <strong>A polished, gesture-first Reddit client for Android.</strong><br>
  Dense when you want it, calm when you need it, and designed for one-handed reading.
</p>

<p align="center">
  <a href="https://github.com/ottersuite/Currents/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/ottersuite/Currents?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/ottersuite/Currents/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/ottersuite/Currents/total?style=for-the-badge&logo=android"></a>
  <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/github/license/ottersuite/Currents?style=for-the-badge"></a>
  <a href="https://discord.gg/7hmp2mt3v2"><img alt="Join the Currents Discord" src="https://img.shields.io/badge/Discord-Join%20the%20community-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <img alt="Android 8.0 or newer" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white">
</p>

<p align="center">
  <a href="https://github.com/ottersuite/Currents/releases/latest/download/Currents.apk"><strong>Download APK</strong></a>
  · <a href="https://github.com/ottersuite/Currents/releases">Releases</a>
  · <a href="SETUP.md">Setup</a>
  · <a href="FAQ.md">FAQ</a>
  · <a href="CHANGELOG.md">Changelog</a>
  · <a href="https://discord.gg/7hmp2mt3v2">Discord</a>
  · <a href="https://github.com/ottersuite/Currents/issues">Issues</a>
</p>

---

## About the project

I came to Android from iOS and missed having a Reddit client that felt polished, information-dense, quick, and comfortable in one hand. Currents is the app I wanted to use: edge-to-edge content, predictable gestures, a compact action dock, a community drawer, colorful comment rails, native media, and enough appearance controls to make the experience your own.

Currents is an original Android-native project built with Kotlin and Jetpack Compose. It has its own name, icon, palette, interaction design, and copy. It contains no advertising or fabricated Reddit content.

> [!IMPORTANT]
> Currents does **not** ship with a Reddit client ID, and I do not provide one. The Client ID field starts blank. Every user must supply their own Reddit installed-app Client ID, User-Agent, and redirect URI, subject to Reddit's current API terms and approval requirements. See the [setup guide](SETUP.md).

## Installation

1. [Download the latest `Currents.apk`](https://github.com/ottersuite/Currents/releases/latest/download/Currents.apk).
2. Open the downloaded file on an Android 8.0 or newer device.
3. If Android asks, allow installs from the browser or file manager that opened the APK.
4. Open Currents and follow the [Reddit account setup guide](SETUP.md).

Installing a newer GitHub release over an existing Currents installation preserves settings, read history, cached content, and encrypted account credentials when the signing certificate matches.

### Verify the download

- Package ID: `app.orca.client`
- Currents 1.0.1 APK SHA-256: `30E4E2AAE093876EEC2A611729124495C1678CACC3398D4A2DECD8A64039B394`

On Windows, verify the file with:

```powershell
Get-FileHash .\Currents.apk -Algorithm SHA256
```

The current release checksum is shown above and should be refreshed with every APK release. Only download Currents from the [OtterSuite repository](https://github.com/ottersuite/Currents/releases).

## What makes Currents different

- **Gesture-first navigation** — configurable two-threshold post/comment actions and a full-height right-edge community drawer gesture.
- **Dense or visual feeds** — switch between compact rows and large previews, then tune text size, thumbnail position, labels, and read-state dimming.
- **Native media** — hosted video, GIF playback, audio controls, galleries, smooth scrubbing, pinch/double-tap zoom, swipe-down dismissal, and gallery saving.
- **Readable conversations** — colorful nested-comment rails, collapse state, sort controls, threaded replies, and next-thread navigation.
- **Personal control** — keyword/community/author filters, hide-read mode, persistent read history, themes, haptics, and configurable action bars.
- **Dedicated NSFW controls** — independently choose whether adult media is covered and whether the Random NSFW shortcut appears in the side menu; spoilers remain covered.
- **Phone and tablet layouts** — a focused phone experience plus a responsive two-pane reader at 840dp and wider.
- **Honest signed-out state** — no bundled credentials, shared proxy, fake feed, ads, or tracking service.

## Features

- Browse Home, Popular, All, saved posts, communities, user profiles, and search results
- Sort feeds and comments; pull to refresh and page through results
- Vote, save, hide, report, subscribe, reply, and create text or link posts
- Edit or delete the connected account's posts and comments
- Configurable feed action bar and four-slot swipe-action editor
- Right-edge community drawer with favorites and Reddit community suggestions
- Durable offline feed/comment cache and saved post drafts
- Read-post history with dim, hide, mark-above-read, and clear-history controls
- Secure installed-app OAuth with runtime redirect schemes and callback/state validation
- Encrypted refresh-token storage backed by Android Keystore
- Optional in-app sign-in fallback for redirect URIs a browser cannot return
- Explicit NSFW/spoiler gates before remote media previews are requested
- Baseline profiles, R8 shrinking, and macrobenchmarks for release performance

See the [changelog](CHANGELOG.md) for release-by-release changes.

## Setup and FAQ

- [Set up your Reddit client ID and account](SETUP.md)
- [Read frequently asked questions](FAQ.md)
- [View all releases](https://github.com/ottersuite/Currents/releases)

Currents never reads credentials from `local.properties`, compiles them into the APK, or supplies a shared client ID. User-provided values are stored in the app's private storage and can be cleared under **Settings → Advanced → Reddit API configuration**.

## Build from source

Requirements:

- Android Studio 2026.1.3 or a compatible setup
- JDK 17+
- Android SDK 37

Clone the repository, open it in Android Studio, and run the `app` configuration, or use:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Verification

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

The test suite covers repository behavior, media parsing, voting and saving transitions, post creation, nested replies, filters, OAuth configuration/callback validation, credential lifecycle, sign-out races, NSFW defaults, and right-edge gesture routing.

<details>
<summary><strong>Project structure and performance tooling</strong></summary>

```text
app/                Android application
benchmark/          Cold-start and feed-scroll macrobenchmarks
baselineprofile/    Baseline profile generator

app/src/main/java/app/otter/client/
├── data/       Reddit API, OAuth, caching, media, and test fixtures
├── model/      Immutable post, comment, community, and vote models
└── ui/
    ├── components/  Gestures, media, drawer, action bar, and composers
    ├── screens/     Feed, post, search, settings, and about
    └── theme/       Currents color and typography tokens
```

Release builds are minified and resource-shrunk with R8. The `benchmark` build inherits that release shape, while `baselineprofile/` generates ahead-of-time compilation rules for startup and feed scrolling. Compose stability rules live in `compose_stability.conf`.

</details>

## Contributing

Bug reports, focused feature proposals, documentation improvements, and pull requests are welcome. Join the [Currents Discord](https://discord.gg/7hmp2mt3v2) to talk with the community.

1. [Open an issue](https://github.com/ottersuite/Currents/issues/new) describing the bug or proposed change.
2. Fork the repository and create a focused branch from `main`.
3. Keep the project credential-free and preserve the blank first-run Client ID state.
4. Add or update tests for behavior changes.
5. Run the verification commands above.
6. Open a pull request against `main`.

## Privacy and independence

Currents talks directly to Reddit and to media hosts needed for content you choose to view. It has no bundled client ID and no service that lends credentials to users. OAuth refresh tokens are encrypted locally with Android Keystore; clearing the API configuration or disconnecting removes local account credentials.

Currents is an independent, community-built client. It is not affiliated with, authorized by, maintained by, or endorsed by Reddit. Reddit is a trademark of Reddit, Inc. API access, endpoints, and account actions can change, and some features may require Reddit approval.

## License

Currents is distributed under the [MIT License](LICENSE).

## Project links

- Repository: [github.com/ottersuite/Currents](https://github.com/ottersuite/Currents)
- Releases: [github.com/ottersuite/Currents/releases](https://github.com/ottersuite/Currents/releases)
- Community: [Currents Discord](https://discord.gg/7hmp2mt3v2)
- Bug reports and feature requests: [GitHub Issues](https://github.com/ottersuite/Currents/issues)
