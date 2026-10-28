package dev.tidytv.app

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The activity must survive being created: 0.8.0 read filesDir in a property initializer and crashed on every launch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityLaunchTest {
    @Test fun activityStartsWithoutCrashing() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertFalse(activity.isFinishing)
    }
}
