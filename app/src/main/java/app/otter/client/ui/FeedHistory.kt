package app.otter.client.ui

/**
 * The trail of feeds a session moved through.
 *
 * Back at the top level should retrace that trail rather than leaving the app, the way tapping
 * into r/one and then r/two makes back mean "r/one" and not "goodbye". The trail is bounded:
 * someone who has hopped through forty communities does not expect forty presses to escape.
 */
class FeedHistory(private val limit: Int = DEFAULT_LIMIT) {
    private val entries = ArrayDeque<String>()

    val canGoBack: Boolean get() = entries.isNotEmpty()

    val depth: Int get() = entries.size

    /**
     * Records a move from [previous] to [next], returning whether it was worth recording.
     * Re-selecting the feed already showing is not a move.
     */
    fun record(previous: String, next: String): Boolean {
        if (previous.isBlank() || previous.equals(next, ignoreCase = true)) return false
        entries.addLast(previous)
        while (entries.size > limit) entries.removeFirst()
        return true
    }

    /** The feed to return to, or null when the trail is exhausted and back should leave. */
    fun back(): String? = entries.removeLastOrNull()

    fun clear() = entries.clear()

    private companion object {
        const val DEFAULT_LIMIT = 12
    }
}
