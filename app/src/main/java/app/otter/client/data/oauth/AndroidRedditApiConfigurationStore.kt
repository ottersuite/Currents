package app.otter.client.data.oauth

import android.annotation.SuppressLint
import android.content.Context

/** Stores user-provided public OAuth metadata privately and independently from account tokens. */
@SuppressLint("ApplySharedPref", "UseKtx") // A successful commit gates the live client swap.
class AndroidRedditApiConfigurationStore(
    context: Context,
    private val buildDefaults: RedditApiConfiguration,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): RedditApiConfiguration {
        val hasCompleteOverride = preferences.contains(KEY_CLIENT_ID) &&
            preferences.contains(KEY_USER_AGENT) &&
            preferences.contains(KEY_REDIRECT_URI)
        if (!hasCompleteOverride) {
            if (preferences.all.isNotEmpty()) preferences.edit().clear().commit()
            return defaults()
        }

        val stored = runCatching {
            RedditApiConfiguration(
                clientId = preferences.getString(KEY_CLIENT_ID, "").orEmpty(),
                userAgent = preferences.getString(KEY_USER_AGENT, "").orEmpty(),
                redirectUri = preferences.getString(KEY_REDIRECT_URI, "").orEmpty(),
            ).normalized()
        }.getOrNull()
        if (stored?.isUsable == true) return stored

        preferences.edit().clear().commit()
        return defaults()
    }

    fun save(configuration: RedditApiConfiguration): Boolean {
        val value = configuration.normalized()
        require(value.isUsable) { value.validationError().orEmpty() }
        return preferences.edit()
            .putString(KEY_CLIENT_ID, value.clientId)
            .putString(KEY_USER_AGENT, value.userAgent)
            .putString(KEY_REDIRECT_URI, value.redirectUri)
            .commit()
    }

    fun reset(): Boolean = preferences.edit().clear().commit()

    fun defaults(): RedditApiConfiguration = buildDefaults.normalized()

    companion object {
        const val PREFERENCES_NAME = "reddit_api_configuration"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_USER_AGENT = "user_agent"
        private const val KEY_REDIRECT_URI = "redirect_uri"
    }
}
