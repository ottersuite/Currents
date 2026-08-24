package app.otter.client.data

import android.content.Context
import app.otter.client.data.oauth.AndroidRedditApiConfigurationStore
import app.otter.client.data.oauth.AndroidRedditOAuthStore

/**
 * The preference files this app owns, and a warm-up that keeps their disk parse off the first
 * frame.
 *
 * `getSharedPreferences` hands the XML parse to a background thread and returns immediately;
 * only the first `get*` blocks until that load finishes. Naming every file at process start
 * therefore overlaps the parses with the rest of startup, instead of paying them one after
 * another inside the view model's constructor — which runs while the activity is building the
 * first frame, on the thread that has to draw it.
 *
 * Every name here predates the Otter rebrand and is load-bearing: changing one silently
 * abandons the user's settings, read history, or stored Reddit credentials.
 */
object OtterPreferences {
    const val SETTINGS = "orca_settings"

    private val ALL = listOf(
        SETTINGS,
        ReadPostStore.PREFERENCES_NAME,
        AndroidRedditOAuthStore.PREFERENCES_NAME,
        AndroidRedditApiConfigurationStore.PREFERENCES_NAME,
    )

    /** Starts the background load of every preference file. Does not block on any of them. */
    fun warm(context: Context) {
        val application = context.applicationContext
        ALL.forEach { name -> application.getSharedPreferences(name, Context.MODE_PRIVATE) }
    }
}
