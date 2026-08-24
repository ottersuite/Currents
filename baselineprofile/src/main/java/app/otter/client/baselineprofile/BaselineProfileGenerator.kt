package app.otter.client.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods worth compiling ahead of time.
 *
 * `androidx.profileinstaller` is already a dependency, but without a profile for it to install
 * it does nothing at all: the app starts fully interpreted and JITs its way through the first
 * frames of every screen. What this collects is what the installer then hands ART at first run.
 *
 * The journey deliberately goes past startup and scrolls the feed. Cold start alone would only
 * cover the activity and theme; the expensive part of a Compose feed is the first composition
 * and layout of the list items, and those classes are only reached by actually scrolling.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndFeedScroll() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        // Splits the startup classes into a separate profile ART can apply before first frame.
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        repeat(SCROLL_PASSES) {
            device.swipe(540, 1_650, 540, 500, 18)
            device.waitForIdle()
        }
    }

    private companion object {
        // The rebrand kept the shipped application ID; see app/build.gradle.kts.
        const val PACKAGE_NAME = "app.orca.client"
        const val SCROLL_PASSES = 6
    }
}
