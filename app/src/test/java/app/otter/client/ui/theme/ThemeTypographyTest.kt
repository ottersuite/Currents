package app.otter.client.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTypographyTest {
    @Test
    fun typographyScalesEveryMaterialRoleAtSupportedExtremes() {
        val small = scaledTypography(.85f)
        val large = scaledTypography(1.3f)

        assertEquals(16f * .85f, small.bodyLarge.fontSize.value, .001f)
        assertEquals(11f * .85f, small.labelMedium.fontSize.value, .001f)
        assertEquals(22f * 1.3f, large.headlineSmall.fontSize.value, .001f)
        assertEquals(16f * 1.3f, large.bodyLarge.fontSize.value, .001f)
        assertEquals(11f * 1.3f, large.labelSmall.fontSize.value, .001f)
    }
}
