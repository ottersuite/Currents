# Currents FAQ

## Why is the feed blank after installation?

Currents intentionally ships without a Reddit client ID and does not fabricate posts. Follow the [setup guide](SETUP.md), then connect your Reddit account.

## Does Currents provide a shared client ID?

No. Every user must provide their own Reddit installed-app Client ID, User-Agent, and redirect URI. Currents has no credential proxy or shared backend.

## Do I need a client secret?

No. Currents uses Reddit's installed-app OAuth flow. Do not enter or distribute a client secret.

## Why does the package ID still say `app.orca.client`?

The application ID and default OAuth callback remain stable so earlier installations can upgrade without losing settings, read history, cached content, or encrypted credentials. Currents' branding and implementation are original.

## Where are the NSFW controls?

Open **Settings → NSFW content**. You can independently control whether NSFW media is automatically revealed and whether the **Random NSFW** shortcut appears in the side menu. Spoilers remain covered.

## How do I open the community menu?

Swipe inward from anywhere along the right edge while viewing a feed or post. On gesture-navigation devices, Currents uses Android's right-edge back gesture; left-edge and hardware back continue to navigate backward.

## Can I update without losing my data?

Yes. Install the newer GitHub APK over the existing app. Do not uninstall first. Android preserves app data when the package ID and signing certificate match.

## Where are credentials stored?

API configuration is kept in the app's private preferences. OAuth refresh tokens are encrypted with Android Keystore. Clearing API settings or disconnecting removes local account credentials.

## Is Currents affiliated with Reddit?

No. Currents is an independent open-source project and is not affiliated with, authorized by, maintained by, or endorsed by Reddit.

## How do I report a problem or request a feature?

[Open a GitHub issue](https://github.com/ottersuite/Currents/issues/new) with the Android version, Currents version, steps to reproduce, and what you expected to happen. Do not include Client IDs, OAuth codes, tokens, or other private credentials.

[Back to the README](README.md)
