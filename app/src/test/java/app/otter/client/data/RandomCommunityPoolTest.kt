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
    fun blanksAndDuplicatesAreDiscardedAndAnEmptyFillIsIgnored() {
        pool.fill(listOf(" ", "one", "one", ""))
        assertEquals("one", pool.draw())

        pool.fill(emptyList())
        // The previous contents survive rather than being wiped by a failed search.
        assertEquals("one", pool.draw())
    }
}
