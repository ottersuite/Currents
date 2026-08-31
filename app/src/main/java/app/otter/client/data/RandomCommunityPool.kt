package app.otter.client.data

/**
 * A cached bag of communities to draw from at random.
 *
 * Searching Reddit for candidates is a network round trip, and doing it per tap means waiting
 * twice before anything appears: once to decide where to go, then again to load it. The pool of
 * candidates barely changes, so it is fetched once and drawn from until it goes stale.
 *
 * Draws deal from a shuffled deck rather than picking independently each time. Independent picks
 * collide far sooner than they feel like they should — from a pool of twenty, two of the first
 * six draws repeat more often than not — and landing back somewhere just visited reads as a
 * broken button rather than as chance. Dealing guarantees every community in the pool comes up
 * once before any of them comes up twice.
 */
internal class RandomCommunityPool(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var names: List<String> = emptyList()
    private var filledAtMillis = 0L
    private var deck: List<String> = emptyList()
    private var dealt = 0
    private var lastDrawn: String? = null

    val needsRefill: Boolean
        get() = names.isEmpty() || now() - filledAtMillis > ttlMillis

    /** Everything currently in the bag, so it can be written down and restored later. */
    fun snapshot(): List<String> = names

    /**
     * Replaces the pool.
     *
     * [filledAtMillis] carries the age of a pool restored from disk, so a stored bag expires on
     * the same schedule as one just fetched rather than looking brand new at every launch.
     */
    fun fill(candidates: List<String>, filledAtMillis: Long = now()) {
        val cleaned = candidates
            .filter(String::isNotBlank)
            .filterNot(::isExcluded)
            .distinct()
        if (cleaned.isEmpty()) return
        names = cleaned
        this.filledAtMillis = filledAtMillis
        // New contents retire whatever is left of the old deal rather than finishing it.
        deck = emptyList()
        dealt = 0
    }

    /** The next community in the deal, or null when the pool is empty. */
    fun draw(): String? {
        if (names.isEmpty()) return null
        if (dealt >= deck.size) reshuffle()
        return deck[dealt++].also { lastDrawn = it }
    }

    /**
     * Shuffles the pool into a fresh deal, keeping the new deck from opening on the community
     * that closed the last one — the one repeat dealing cannot rule out on its own. A pool with
     * a single entry has nothing else to offer and repeats rather than giving up.
     */
    private fun reshuffle() {
        val shuffled = names.shuffled()
        deck = if (shuffled.size > 1 && shuffled.first() == lastDrawn) {
            shuffled.drop(1) + shuffled.first()
        } else {
            shuffled
        }
        dealt = 0
    }

    /** True when the name marks a community as drawn rather than photographed. */
    private fun isExcluded(name: String): Boolean {
        val folded = name.lowercase()
        return EXCLUDED_FRAGMENTS.any { fragment -> fragment in folded }
    }

    private companion object {
        /**
         * Substrings that exclude a community from the draw.
         *
         * Filtering happens here, in the one place every pool passes through, rather than at the
         * point of harvest -- a pool restored from disk was collected by an earlier run and has
         * to be filtered on the way back in, or it would go on serving what it gathered before
         * this list existed.
         *
         * Matched against the community's own name, which is all the pool holds. Loose matching
         * is the right trade for a random button: excluding a community that merely reads like
         * one of these costs a name nobody notices missing, while letting one through is the
         * failure the reader actually sees.
         */
        val EXCLUDED_FRAGMENTS = listOf(
            "hentai",
            "anime",
            "manga",
            "doujin",
            "ecchi",
            "ahegao",
            "waifu",
            "weeb",
            "rule34",
            "r34",
            "toon",
            "yaoi",
            "yuri",
            "futa",
            "loli",
            "shota",
        )

        // The pool is a set of communities, not their contents, and that barely moves. It used
        // to expire in half an hour because it only ever lived as long as the process; now that
        // it is written to disk, re-harvesting every session is the thing worth avoiding.
        const val DEFAULT_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L
    }
}
