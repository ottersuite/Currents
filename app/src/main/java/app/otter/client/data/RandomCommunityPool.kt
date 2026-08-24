package app.otter.client.data

/**
 * A cached bag of communities to draw from at random.
 *
 * Searching Reddit for candidates is a network round trip, and doing it per tap means waiting
 * twice before anything appears: once to decide where to go, then again to load it. The pool of
 * candidates barely changes, so it is fetched once and drawn from until it goes stale.
 *
 * Consecutive draws never repeat: "random" that hands back the community you just left reads as
 * a bug, not as chance.
 */
internal class RandomCommunityPool(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var names: List<String> = emptyList()
    private var filledAtMillis = 0L
    private var lastDrawn: String? = null

    val needsRefill: Boolean
        get() = names.isEmpty() || now() - filledAtMillis > ttlMillis

    fun fill(candidates: List<String>) {
        val cleaned = candidates.filter(String::isNotBlank).distinct()
        if (cleaned.isEmpty()) return
        names = cleaned
        filledAtMillis = now()
    }

    /** A community that is not the one drawn last, or null when the pool is empty. */
    fun draw(): String? {
        if (names.isEmpty()) return null
        val choices = names.filterNot { it.equals(lastDrawn, ignoreCase = true) }
            .ifEmpty { names }
        return choices.random().also { lastDrawn = it }
    }

    private companion object {
        const val DEFAULT_TTL_MILLIS = 30 * 60 * 1000L
    }
}
