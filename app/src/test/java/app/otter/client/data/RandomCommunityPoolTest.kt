package app.otter.client.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomCommunityPoolTest {
    private var clock = 0L
    private val pool = RandomCommunityPool(ttlMillis = 1_000L, now = { clock })

    @Test
    fun anEmptyPoolAsksToBeFilledAndDrawsNothing() {
        assertTrue(pool.needsRefill)
        assertNull(pool.draw())
    }

    @Test
    fun aFilledPoolServesDrawsWithoutRefillingUntilItExpires() {
        pool.fill(listOf("one", "two", "three"))

        assertFalse(pool.needsRefill)
        repeat(10) { assertTrue(pool.draw() in listOf("one", "two", "three")) }

        clock += 1_001L
        assertTrue(pool.needsRefill)
    }

    @Test
    fun consecutiveDrawsNeverRepeat() {
        pool.fill(listOf("one", "two"))

        val drawn = (1..20).map { checkNotNull(pool.draw()) }

        drawn.zipWithNext { previous, next -> assertTrue(previous != next) }
    }

    @Test
    fun aSingleEntryPoolStillDrawsRatherThanGivingUp() {
        pool.fill(listOf("only"))

        assertEquals("only", pool.draw())
        assertEquals("only", pool.draw())
    }

    @Test
    fun drawnAndAnimatedCommunitiesNeverEnterThePool() {
        val pool = RandomCommunityPool()

        pool.fill(
            listOf(
                "photographs",
                "HentaiSubreddit",
                "nsfw_anime",
                "Rule34",
                "WaifusNSFW",
                "doujinshi",
                "realpeople",
            ),
        )

        val drawn = List(8) { pool.draw() }.toSet()
        assertEquals(setOf("photographs", "realpeople"), drawn)
    }

    @Test
    fun aPoolRestoredFromDiskIsFilteredOnTheWayBackIn() {
        val pool = RandomCommunityPool()

        // Whatever an earlier run gathered was collected before the exclusions existed, so the
        // restore path has to filter too rather than trusting what it stored.
        pool.fill(listOf("hentai_archive", "keepers"), filledAtMillis = 1L)

        assertEquals("keepers", pool.draw())
        assertEquals("keepers", pool.draw())
    }

    @Test
    fun blanksAndDuplicatesAreDiscardedAndAnEmptyFillIsIgnored() {
        pool.fill(listOf(" ", "one", "one", ""))
        assertEquals("one", pool.draw())

        pool.fill(emptyList())
        // The previous contents survive rather than being wiped by a failed search.
        assertEquals("one", pool.draw())
    }

    @Test
    fun everyCommunityIsDealtBeforeAnyIsDealtTwice() {
        val communities = (1..12).map { "c$it" }
        pool.fill(communities)

        val firstPass = (1..12).map { checkNotNull(pool.draw()) }

        // The whole point of dealing rather than picking: no repeats until the deck runs out.
        assertEquals(communities.toSet(), firstPass.toSet())
        assertEquals(12, firstPass.distinct().size)
    }

    @Test
    fun aFreshDealDoesNotOpenOnTheCommunityThatClosedTheLastOne() {
        val communities = (1..8).map { "c$it" }
        pool.fill(communities)

        // Long enough to cross several deal boundaries, which is the only place dealing alone
        // could still hand back the same community twice running.
        val drawn = (1..80).map { checkNotNull(pool.draw()) }

        drawn.zipWithNext { previous, next ->
            assertTrue("$previous repeated across a deal boundary", previous != next)
        }
    }

    @Test
    fun everyPassIsDealtInADifferentOrder() {
        pool.fill((1..12).map { "c$it" })

        val passes = (1..6).map { (1..12).map { checkNotNull(pool.draw()) } }

        // A deal that came back in the same order every time would repeat as predictably as the
        // independent picks it replaced.
        assertTrue("every pass was dealt identically", passes.distinct().size > 1)
    }

    @Test
    fun refillingRetiresWhateverIsLeftOfTheCurrentDeal() {
        pool.fill(listOf("old-a", "old-b", "old-c"))
        pool.draw()

        pool.fill(listOf("new-a", "new-b"))

        val drawn = (1..6).map { checkNotNull(pool.draw()) }
        assertTrue("a retired community was still dealt", drawn.all { it.startsWith("new-") })
    }
}
