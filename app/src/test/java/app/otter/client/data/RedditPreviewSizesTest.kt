package app.otter.client.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RedditPreviewSizesTest {
    private fun image(vararg widths: Int): JSONObject {
        val entries = widths.joinToString(",") { width ->
            """{"url": "https://preview.redd.it/w$width.jpg?s=sig&amp;a=1", "width": $width, "height": 100}"""
        }
        return JSONObject("""{"resolutions": [$entries]}""")
    }

    @Test
    fun picksTheNarrowestCopyThatStillCoversTheTarget() {
        val chosen = RedditPreviewSizes.smallestCovering(image(108, 216, 320, 640, 1080), 320)

        assertEquals("https://preview.redd.it/w320.jpg?s=sig&a=1", chosen)
    }

    @Test
    fun ordersByWidthRatherThanByPositionInTheList() {
        // Reddit lists these ascending, but nothing in the payload guarantees it.
        val chosen = RedditPreviewSizes.smallestCovering(image(1080, 640, 360), 320)

        assertEquals("https://preview.redd.it/w360.jpg?s=sig&a=1", chosen)
    }

    @Test
    fun fallsBackToNothingWhenEveryCopyIsTooSmallOrMissing() {
        assertNull(RedditPreviewSizes.smallestCovering(image(108, 216), 320))
        assertNull(RedditPreviewSizes.smallestCovering(JSONObject("{}"), 320))
        assertNull(
            RedditPreviewSizes.smallestCovering(
                JSONObject("""{"resolutions": [{"url": "http://insecure/w640.jpg", "width": 640}]}"""),
                320,
            ),
        )
    }
}
