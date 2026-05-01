package com.realmsoffate.game.data.updater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdatePrefsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val prefs = UpdatePrefs(ctx)

    @After fun tearDown() = runTest { prefs.clearForTest() }

    @Test fun `default channel is INCLUDE_PRERELEASES`() = runTest {
        assertEquals(UpdateChannel.INCLUDE_PRERELEASES, prefs.channel.first())
    }

    @Test fun `setChannel persists`() = runTest {
        prefs.setChannel(UpdateChannel.STABLE_ONLY)
        assertEquals(UpdateChannel.STABLE_ONLY, prefs.channel.first())
    }

    @Test fun `lastCheckedAt defaults to null`() = runTest {
        assertNull(prefs.lastCheckedAt.first())
    }

    @Test fun `setLastCheckedAt persists`() = runTest {
        prefs.setLastCheckedAt(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, prefs.lastCheckedAt.first())
    }

    @Test fun `dismissedVersions starts empty`() = runTest {
        assertTrue(prefs.dismissedVersions.first().isEmpty())
    }

    @Test fun `dismissVersion adds tag`() = runTest {
        prefs.dismissVersion("v0.2.0")
        assertEquals(setOf("v0.2.0"), prefs.dismissedVersions.first())
    }

    @Test fun `dismissedVersions bounded to 10 most recent`() = runTest {
        for (i in 1..15) prefs.dismissVersion("v0.0.$i")
        val set = prefs.dismissedVersions.first()
        assertEquals(10, set.size)
        assertFalse("oldest should have been pruned", set.contains("v0.0.1"))
        assertTrue("newest must be present", set.contains("v0.0.15"))
    }
}
