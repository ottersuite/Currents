# Currents setup

Currents starts with a blank feed because it does not bundle or provide a Reddit client ID. Connecting a Reddit account requires credentials for your own Reddit installed app and compliance with Reddit's current Developer and Data API terms.

## What you need

- A Reddit account
- Any Reddit approval or API access currently required for your use case
- A Reddit **installed app** Client ID
- A unique User-Agent describing your app/version and Reddit username
- A redirect URI registered exactly as entered in Currents

An installed app uses a public Client ID. Do not create, enter, or distribute a client secret for Currents.

## Register the Reddit app

1. Sign in and open [Reddit's app preferences](https://www.reddit.com/prefs/apps).
2. Create an app and choose the **installed app** type.
3. Register this default redirect URI:

   ```text
   app.orca.client://oauth/reddit
   ```

   Currents also supports another lowercase hierarchical custom scheme, such as `my.personal.client://oauth/return`, or a verified HTTPS callback with an explicit path and the required Digital Asset Links association. Plain HTTP is rejected.

4. Copy the public Client ID displayed beneath the Reddit app name.

## Configure Currents

1. Open **Settings → Advanced → Reddit API configuration**.
2. Enter:

   - **Client ID** — your public installed-app ID
   - **User-Agent** — for example, `android:app.orca.client:v1.0.1 (by /u/YOUR_USERNAME)`
   - **Redirect URI** — the exact callback registered with Reddit

3. Save the configuration.
4. Tap **Connect Reddit account** and approve access in the browser.

Currents applies new API values immediately; no rebuild is required. The returned callback base and one-time OAuth state must both match before an authorization code is accepted.

## Requested OAuth scopes

- `identity` — confirm the connected username
- `read` — load posts and comments
- `mysubreddits` — load subscribed communities
- `history` — load account history such as saved posts
- `vote` — vote on posts and comments
- `save` — save or unsave posts
- `submit` — publish posts and replies
- `edit` — edit or delete your posts and comments
- `report` — hide/unhide and report content
- `subscribe` — join or leave communities
- `account` — request user blocking where Reddit permits it

## In-app sign-in fallback

**Settings → Advanced → Sign in inside Currents** replaces the browser Auth Tab with an in-app WebView. It is off by default and should be used only when a configured redirect URI cannot return through Android's browser intent routing.

The Auth Tab is preferred because Reddit's login page remains in the browser process, where Currents cannot inspect it. Reusing another developer's Client ID may violate Reddit's terms; use your own registration whenever possible.

Both sign-in methods validate the redirect and one-time OAuth state before exchanging a code. Currents stores the resulting refresh token encrypted with Android Keystore.

## Changing or removing credentials

Changing API details disconnects the current Reddit session before activating the replacement client. Selecting **Clear API settings** removes the locally stored client details and encrypted account credential. The Client ID and User-Agent return to blank.

## Reddit policies

Review Reddit's current policies before use or distribution:

- [Data API Terms](https://redditinc.com/policies/data-api-terms)
- [Developer Terms](https://redditinc.com/policies/developer-terms)
- [Developer Platform access guidance](https://support.reddithelp.com/hc/en-us/articles/14945211791892-Developer-Platform-Accessing-Reddit-Data)
- [Reddit Data API Wiki](https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki)

[Back to the README](README.md)
