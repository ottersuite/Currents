package app.otter.client.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** Opens web content without silently weakening the privacy promised by the in-app setting. */
fun Context.openWebLink(url: String, inApp: Boolean): Boolean {
    val uri = url.toUri()
    val externalIntent = Intent(Intent.ACTION_VIEW, uri)
    return if (inApp) {
        val browserPackage = CustomTabsClient.getPackageName(this, null)
        val supportsPrivateTabs = browserPackage != null &&
            CustomTabsClient.isEphemeralBrowsingSupported(this, browserPackage)
        if (!supportsPrivateTabs) {
            Toast.makeText(
                this,
                "Your browser does not support private in-app tabs",
                Toast.LENGTH_LONG,
            ).show()
            false
        } else {
            runCatching {
                CustomTabsIntent.Builder()
                    // Ephemeral tabs use isolated cookies and do not add pages to browser history.
                    .setEphemeralBrowsingEnabled(true)
                    .setShowTitle(true)
                    .setUrlBarHidingEnabled(true)
                    .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                    .build()
                    .also { it.intent.setPackage(browserPackage) }
                    .launchUrl(this, uri)
            }.onFailure {
                Toast.makeText(this, "Could not open a private browser tab", Toast.LENGTH_LONG).show()
            }.isSuccess
        }
    } else {
        runCatching { startActivity(externalIntent) }.isSuccess
    }
}
