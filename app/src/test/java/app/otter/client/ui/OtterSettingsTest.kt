package app.otter.client.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtterSettingsTest {
    @Test
    fun nsfwDefaultsPreserveCoveredMediaAndExistingDrawerShortcut() {
        val settings = OtterSettings()

        assertFalse(settings.alwaysShowNsfw)
        assertTrue(settings.showRandomNsfwButton)
    }
}
