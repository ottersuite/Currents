package app.otter.client

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.otter.client.data.ReadPostStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Read state has to outlive the process, so this exercises the real SharedPreferences file. */
@RunWith(AndroidJUnit4::class)
class ReadPostStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearPreviousRun() {
        ReadPostStore(context).clear()
    }

    @Test
    fun readPostsAreStillKnownToAFreshStore() {
        ReadPostStore(context).apply {
            add("t3_one")
            add("t3_two")
        }

        // A new instance stands in for the next launch of the app.
        val reopened = ReadPostStore(context)

        assertEquals(setOf("t3_one", "t3_two"), reopened.ids())
    }

    @Test
    fun recordingTheSamePostTwiceReportsOnlyTheFirstTime() {
        val store = ReadPostStore(context)

        assertTrue(store.add("t3_one"))
        assertFalse(store.add("t3_one"))
        assertFalse(store.add("   "))
        assertEquals(setOf("t3_one"), store.ids())
    }

    @Test
    fun addAllRecordsTheWholeSetAndSurvivesRestart() {
        val store = ReadPostStore(context)

        assertTrue(store.addAll(listOf("t3_one", "t3_two", "   ")))
        assertEquals(setOf("t3_one", "t3_two"), store.ids())
        // Nothing new in the set, so nothing to write.
        assertFalse(store.addAll(listOf("t3_one", "t3_two")))
        assertFalse(store.addAll(emptyList()))

        assertEquals(setOf("t3_one", "t3_two"), ReadPostStore(context).ids())
    }

    @Test
    fun addAllEvictsOldestFirstJustLikeAdd() {
        val store = ReadPostStore(context, limit = 3)

        store.addAll(listOf("one", "two", "three", "four"))

        assertEquals(setOf("two", "three", "four"), store.ids())
        assertEquals(setOf("two", "three", "four"), ReadPostStore(context, limit = 3).ids())
    }

    @Test
    fun theTrailIsBoundedAndForgetsTheOldestPostsFirst() {
        val store = ReadPostStore(context, limit = 3)

        listOf("one", "two", "three", "four").forEach(store::add)

        assertEquals(setOf("two", "three", "four"), store.ids())
        assertEquals(setOf("two", "three", "four"), ReadPostStore(context, limit = 3).ids())
    }

    @Test
    fun clearForgetsEverythingIncludingOnDisk() {
        ReadPostStore(context).add("t3_one")

        ReadPostStore(context).clear()

        assertTrue(ReadPostStore(context).ids().isEmpty())
    }
}
