package app.otter.client.data

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers which posts have been opened, across launches.
 *
 * Read state is the difference between a feed you are working through and one that starts over
 * every morning, so it outlives the process. The trail is bounded and evicted oldest-first: a
 * post read a year and ten thousand posts ago is not going to be recognised in a feed today.
 */
class ReadPostStore(context: Context, private val limit: Int = DEFAULT_LIMIT) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    // Insertion-ordered, oldest first, so eviction can drop the front.
    private val ids = LinkedHashSet<String>()

    init {
        preferences.getString(KEY_IDS, null)
            ?.splitToSequence(SEPARATOR)
            ?.filter(String::isNotBlank)
            ?.take(limit)
            ?.forEach(ids::add)
    }

    fun ids(): Set<String> = ids.toSet()

    /** Records [postId], returning whether this was the first time it was seen. */
    fun add(postId: String): Boolean {
        if (!record(postId)) return false
        persist()
        return true
    }

    /**
     * Records a whole set with a single write, returning whether any of them were new.
     *
     * Calling [add] in a loop rewrites the entire trail once per post: marking a screenful of
     * 100 as read joined up to [DEFAULT_LIMIT] ids into a string 100 times and queued 100
     * separate preference writes.
     */
    fun addAll(postIds: Collection<String>): Boolean {
        var recorded = false
        postIds.forEach { postId -> if (record(postId)) recorded = true }
        if (recorded) persist()
        return recorded
    }

    private fun record(postId: String): Boolean {
        if (postId.isBlank() || !ids.add(postId)) return false
        while (ids.size > limit) {
            ids.remove(ids.first())
        }
        return true
    }

    private fun persist() {
        preferences.edit { putString(KEY_IDS, ids.joinToString(SEPARATOR)) }
    }

    fun clear() {
        ids.clear()
        preferences.edit { remove(KEY_IDS) }
    }

    companion object {
        // Retained for in-place upgrades from the Orca-branded build.
        const val PREFERENCES_NAME = "orca_read_posts"

        private const val KEY_IDS = "ids"
        private const val SEPARATOR = "\n"
        private const val DEFAULT_LIMIT = 1_500
    }
}
