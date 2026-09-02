package app.otter.client.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun landscapeFoldableAndTabletUseTwoPanes() {
        assertTrue(shouldUseTwoPaneLayout(widthDp = 820f, heightDp = 680f, smallestWidthDp = 673))
        assertTrue(shouldUseTwoPaneLayout(widthDp = 1280f, heightDp = 800f, smallestWidthDp = 800))
    }

    @Test
    fun portraitAndCompactLandscapeStaySinglePane() {
        assertFalse(shouldUseTwoPaneLayout(widthDp = 800f, heightDp = 1_100f, smallestWidthDp = 800))
        assertFalse(shouldUseTwoPaneLayout(widthDp = 699f, heightDp = 420f, smallestWidthDp = 600))
    }

    @Test
    fun widePhoneLandscapeStaysSinglePane() {
        // Pixel 11 Pro XL: landscape is wide, but sw448dp means it is still a phone window.
        assertFalse(shouldUseTwoPaneLayout(widthDp = 997f, heightDp = 448f, smallestWidthDp = 448))
    }
}
