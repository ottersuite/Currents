package app.otter.client.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedHistoryTest {
    @Test
    fun backRetracesFeedsInReverseOrderThenGivesUp() {
        val history = FeedHistory()
        history.record(previous = "Home", next = "r/android")
        history.record(previous = "r/android", next = "r/kotlin")

        assertTrue(history.canGoBack)
        assertEquals("r/android", history.back())
        assertEquals("Home", history.back())
        // Exhausted: the next back belongs to the system, which leaves the app.
        assertFalse(history.canGoBack)
        assertNull(history.back())
    }

    @Test
    fun reselectingTheCurrentFeedIsNotAMove() {
        val history = FeedHistory()

        assertFalse(history.record(previous = "r/android", next = "r/android"))
        assertFalse(history.record(previous = "r/android", next = "R/ANDROID"))
        assertFalse(history.record(previous = "", next = "r/android"))
        assertFalse(history.canGoBack)
    }

    @Test
    fun trailIsBoundedAndDropsTheOldestEntriesFirst() {
        val history = FeedHistory(limit = 3)
        listOf("one", "two", "three", "four").forEach { feed ->
            history.record(previous = feed, next = "next-$feed")
        }

        assertEquals(3, history.depth)
        // "one" fell off the front; the most recent three remain, newest first on the way back.
        assertEquals("four", history.back())
        assertEquals("three", history.back())
        assertEquals("two", history.back())
        assertNull(history.back())
    }

    @Test
    fun clearDropsTheWholeTrail() {
        val history = FeedHistory()
        history.record(previous = "Home", next = "r/android")

        history.clear()

        assertFalse(history.canGoBack)
    }
}
