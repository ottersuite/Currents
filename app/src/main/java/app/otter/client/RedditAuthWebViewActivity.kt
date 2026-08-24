package app.otter.client

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.net.URI
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.otter.client.data.oauth.RedditApiConfiguration
import app.otter.client.ui.RedditAuthorizationRequest

/**
 * In-app sign-in surface for redirect URIs that no browser can hand back to Otter — the case
 * when the configured client ID belongs to an OAuth app registered by someone else.
 *
 * The WebView compares each navigation against the configured redirect itself, so the callback
 * never has to reach Android's intent router. That is strictly weaker than the Auth Tab, which
 * keeps Reddit's login page in the browser's process where this app cannot observe it, so the
 * Auth Tab stays the default and this path is opt-in. The returned callback is validated by the
 * same configuration and one-time-state checks either way.
 */
class RedditAuthWebViewActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var redirectUri: String = ""
    private var settled = false
    private var delivered = false

    @SuppressLint("SetJavaScriptEnabled") // Reddit's login page is script- and XHR-driven.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authorizationUrl = intent?.getStringExtra(EXTRA_AUTHORIZATION_URL).orEmpty()
        redirectUri = intent?.getStringExtra(EXTRA_REDIRECT_URI).orEmpty()
        if (authorizationUrl.isEmpty() || redirectUri.isEmpty()) {
            settle(null)
            return
        }

        val view = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = true
            // Honour the page's viewport tag and start zoomed to fit, or Reddit's consent
            // buttons lay out past the edge of a 980px default viewport and cannot be tapped.
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // Reddit degrades its login and consent pages for the WebView-marked user agent.
            settings.userAgentString = browserUserAgent(context)
            webViewClient = authorizationClient()
            webChromeClient = loggingChromeClient()
        }
        webView = view
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)

        val container = FrameLayout(this).apply {
            addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        ViewCompat.setOnApplyWindowInsetsListener(container) { padded, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
            )
            padded.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(container)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val active = webView
                    if (active != null && active.canGoBack()) active.goBack() else settle(null)
                }
            },
        )

        if (savedInstanceState == null) view.loadUrl(authorizationUrl)
    }

    private fun authorizationClient(): WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val url = request.url.toString()
            // Anything on the redirect's own scheme is Reddit answering, so hand it back even
            // when the base looks wrong: swallowing it strands the flow on a dead page, while
            // the shared acceptance check can reject it and say exactly what did not line up.
            if (isRedirectResponse(url)) {
                if (!matchesRedirect(url)) traceMismatch(url)
                settle(url)
                return true
            }
            // Custom schemes beyond that are app hand-offs that cannot carry the authorization
            // back, so they are dropped rather than routed out to another app.
            val blocked = request.url.scheme?.lowercase() !in BROWSABLE_SCHEMES
            trace(if (blocked) "blocked" else "navigate", url)
            return blocked
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            // Server-side redirect chains can land on the callback without a navigation request.
            trace("start", url)
            if (isRedirectResponse(url)) settle(url)
        }

        override fun onPageFinished(view: WebView, url: String) = trace("finish", url)

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (request.isForMainFrame) {
                trace("http ${errorResponse.statusCode}", request.url.toString())
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // A custom-scheme callback has nothing to load; the URL itself is the response.
            val url = request.url.toString()
            if (!request.isForMainFrame) return
            trace("error ${error.errorCode}", url)
            if (isRedirectResponse(url)) settle(url)
        }
    }

    /** Present so script-driven consent buttons that open dialogs or windows still work. */
    private fun loggingChromeClient(): WebChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "console ${message.messageLevel()}: ${message.message()}")
            }
            return true
        }
    }

    /** Debug-only breadcrumbs; the query string is dropped so no code or state is logged. */
    private fun trace(event: String, url: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "$event: ${url.substringBefore('?')}")
    }

    /**
     * Explains why a callback-shaped URL was not recognized. Only the shape is logged: parameter
     * names without their values, and the length of a fragment rather than its contents.
     */
    private fun traceMismatch(url: String) {
        if (!BuildConfig.DEBUG) return
        val expected = runCatching { URI(redirectUri.trim()) }.getOrNull()
        val actual = runCatching { URI(url) }.getOrNull()
        if (actual == null) {
            Log.d(TAG, "mismatch: callback did not parse as a URI (length ${url.length})")
            return
        }
        val keys = actual.rawQuery.orEmpty()
            .split('&')
            .filter(String::isNotBlank)
            .joinToString(",") { it.substringBefore('=') }
        Log.d(
            TAG,
            "mismatch: expected[scheme=${expected?.scheme} authority=${expected?.rawAuthority} " +
                "path='${expected?.rawPath}'] actual[scheme=${actual.scheme} " +
                "authority=${actual.rawAuthority} path='${actual.rawPath}' opaque=${actual.isOpaque} " +
                "userInfo=${actual.userInfo != null} port=${actual.port} " +
                "fragmentLength=${actual.rawFragment?.length ?: -1} queryKeys=[$keys]]",
        )
    }

    private fun matchesRedirect(url: String): Boolean =
        RedditApiConfiguration.callbackMatchesRedirect(redirectUri, url)

    /**
     * True when a navigation is Reddit answering the authorization rather than more web page.
     * An exact base match qualifies; so does the redirect's own scheme, which nothing else in
     * this flow uses. Validation of the callback itself stays with the caller.
     */
    private fun isRedirectResponse(url: String): Boolean {
        if (matchesRedirect(url)) return true
        val expectedScheme = runCatching { URI(redirectUri.trim()).scheme }.getOrNull()
            ?: return false
        val actualScheme = runCatching { URI(url).scheme }.getOrNull() ?: return false
        // An https redirect shares its scheme with the login pages, so only a custom one counts.
        return !expectedScheme.equals("https", ignoreCase = true) &&
            actualScheme.equals(expectedScheme, ignoreCase = true)
    }

    private fun settle(callbackUrl: String?) {
        if (settled) return
        settled = true
        webView?.stopLoading()
        if (callbackUrl == null) {
            setResult(RESULT_CANCELED)
        } else {
            delivered = true
            setResult(RESULT_OK, Intent().putExtra(EXTRA_CALLBACK_URL, callbackUrl))
        }
        finish()
    }

    override fun onDestroy() {
        // Once the account is authorized it lives on the refresh token, so the Reddit web
        // session is dropped. An abandoned attempt keeps it, so a retry is not a fresh login.
        if (delivered) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        webView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        webView = null
        super.onDestroy()
    }

    /** Launches the in-app flow and returns the raw callback URL, or null if it was abandoned. */
    class Contract : ActivityResultContract<RedditAuthorizationRequest, String?>() {
        override fun createIntent(context: Context, input: RedditAuthorizationRequest): Intent =
            Intent(context, RedditAuthWebViewActivity::class.java)
                .putExtra(EXTRA_AUTHORIZATION_URL, input.authorizationUrl)
                .putExtra(EXTRA_REDIRECT_URI, input.redirectUri)

        override fun parseResult(resultCode: Int, intent: Intent?): String? =
            intent?.getStringExtra(EXTRA_CALLBACK_URL)?.takeIf { resultCode == Activity.RESULT_OK }
    }

    /** The stock WebView agent advertises itself with a "wv" token that Reddit serves around. */
    private fun browserUserAgent(context: Context): String =
        WebSettings.getDefaultUserAgent(context)
            .replace("; wv)", ")")
            .replace(" Version/4.0", "")

    private companion object {
        const val TAG = "OtterAuth"
        const val EXTRA_AUTHORIZATION_URL = "app.otter.client.extra.AUTHORIZATION_URL"
        const val EXTRA_REDIRECT_URI = "app.otter.client.extra.REDIRECT_URI"
        const val EXTRA_CALLBACK_URL = "app.otter.client.extra.CALLBACK_URL"
        val BROWSABLE_SCHEMES = setOf("https")
    }
}
