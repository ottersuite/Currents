package app.otter.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.core.net.toUri
import app.otter.client.data.oauth.callbackBase
import app.otter.client.ui.OtterApp
import app.otter.client.ui.OtterViewModel
import app.otter.client.ui.RedditAuthorizationRequest

class MainActivity : ComponentActivity() {
    private val otterViewModel: OtterViewModel by viewModels()
    private val authTabLauncher = AuthTabIntent.registerActivityResultLauncher(this) { result ->
        when (result.resultCode) {
            AuthTabIntent.RESULT_OK -> acceptCallback(
                callback = result.resultUri?.toString(),
                missingCallbackMessage =
                    "The browser closed Reddit sign-in without returning a callback",
            )
            AuthTabIntent.RESULT_CANCELED ->
                otterViewModel.cancelRedditSignIn("Reddit sign-in was cancelled")
            AuthTabIntent.RESULT_VERIFICATION_FAILED ->
                otterViewModel.cancelRedditSignIn("The HTTPS callback domain is not verified for Currents")
            AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT ->
                otterViewModel.cancelRedditSignIn("The HTTPS callback domain verification timed out")
            else -> otterViewModel.cancelRedditSignIn("The browser could not complete Reddit sign-in")
        }
    }

    private val webViewAuthLauncher = registerForActivityResult(
        RedditAuthWebViewActivity.Contract(),
    ) { callback ->
        acceptCallback(
            callback = callback,
            missingCallbackMessage = "Reddit sign-in was cancelled",
        )
    }

    /** One acceptance path for both sign-in surfaces; neither one gets to skip a check. */
    private fun acceptCallback(callback: String?, missingCallbackMessage: String) {
        when {
            callback == null -> otterViewModel.cancelRedditSignIn(missingCallbackMessage)

            otterViewModel.acceptsOAuthRedirect(callback) ->
                otterViewModel.handleOAuthRedirect(callback)

            // The callback can never match while the client ID / User-Agent are unset,
            // so report the missing configuration instead of blaming Reddit's response.
            !otterViewModel.isRedditApiConfigured ->
                otterViewModel.cancelRedditSignIn(
                    "Add your Reddit client ID and User-Agent in Settings, " +
                        "then try signing in again",
                )

            else ->
                otterViewModel.cancelRedditSignIn(
                    "Reddit returned " + callbackBase(callback) +
                        ", which does not match the redirect URI in Settings (" +
                        otterViewModel.configuredRedirectUri +
                        "). Both must match the URI registered for the client ID you entered " +
                        "at reddit.com/prefs/apps",
                )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            OtterApp(
                viewModel = otterViewModel,
                onLaunchRedditAuthorization = ::launchRedditAuthorization,
            )
        }
    }

    private fun launchRedditAuthorization(authorizationUrl: String, redirectUri: String) {
        if (otterViewModel.settings.value.webViewSignIn) {
            runCatching {
                webViewAuthLauncher.launch(
                    RedditAuthorizationRequest(
                        authorizationUrl = authorizationUrl,
                        redirectUri = redirectUri,
                    ),
                )
            }.onFailure {
                otterViewModel.cancelRedditSignIn("Currents could not open the in-app sign-in page")
            }
            return
        }

        val provider = findAuthTabProvider()
        if (provider == null) {
            otterViewModel.cancelRedditSignIn(
                "Update Chrome or choose a browser that supports secure Auth Tabs",
            )
            return
        }

        val authorization = authorizationUrl.toUri()
        val redirect = redirectUri.toUri()
        val scheme = redirect.scheme
        if (scheme.isNullOrBlank()) {
            otterViewModel.cancelRedditSignIn("The Reddit authorization settings are invalid")
            return
        }

        runCatching {
            val authTab = AuthTabIntent.Builder().build().also { tab ->
                tab.intent.setPackage(provider)
            }
            if (scheme == "https") {
                authTab.launch(
                    authTabLauncher,
                    authorization,
                    checkNotNull(redirect.host),
                    redirect.encodedPath.orEmpty(),
                )
            } else {
                authTab.launch(authTabLauncher, authorization, scheme)
            }
        }.onFailure {
            otterViewModel.cancelRedditSignIn("The browser could not open Reddit sign-in")
        }
    }

    private fun findAuthTabProvider(): String? {
        val defaultBrowser = runCatching {
            CustomTabsClient.getPackageName(this, null)
        }.getOrNull() ?: return null

        return defaultBrowser.takeIf { packageName ->
            runCatching { CustomTabsClient.isAuthTabSupported(this, packageName) }
                .getOrDefault(false)
        }
    }
}
