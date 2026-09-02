package app.otter.client.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class OtterSettingsTest {
    @Test
    fun nsfwDefaultsPreserveCoveredMediaAndExistingDrawerShortcut() {
        val settings = OtterSettings()

        assertFalse(settings.alwaysShowNsfw)
        assertTrue(settings.showRandomNsfwButton)
    }

    @Test
    fun dataSaverControlsDefaultToFullExperience() {
        val settings = OtterSettings()

        assertTrue(settings.autoplayMedia)
        assertTrue(settings.prefetchMedia)
        assertTrue(settings.showThumbnails)
        assertTrue(settings.openLinksInApp)
        assertEquals(MediaQuality.Auto, settings.mediaQuality)
    }
}
