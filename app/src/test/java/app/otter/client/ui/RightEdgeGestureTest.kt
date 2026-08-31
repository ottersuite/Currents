package app.otter.client.ui

import androidx.activity.BackEventCompat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RightEdgeGestureTest {
    @Test
    fun onlyRightEdgeBackGesturesOpenTheDrawer() {
        assertTrue(isRightEdgeBackGesture(BackEventCompat.EDGE_RIGHT))
        assertFalse(isRightEdgeBackGesture(BackEventCompat.EDGE_LEFT))
        assertFalse(isRightEdgeBackGesture(BackEventCompat.EDGE_NONE))
    }
}
