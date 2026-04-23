package com.realmsoffate.game.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheatsStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val store = CheatsStore(ctx)

    @After fun tearDown() = runTest { store.disable() }

    @Test fun `unlock sets enabled true`() = runTest {
        store.unlock()
        assertTrue(store.enabled.first())
    }

    @Test fun `disable clears enabled and all four flags`() = runTest {
        store.unlock()
        store.setUnnaturalTwenty(true)
        store.setInfiniteGold(true)
        store.disable()
        assertFalse(store.enabled.first())
        assertFalse(store.unnaturalTwenty.first())
        assertFalse(store.loser.first())
        assertFalse(store.infiniteGold.first())
    }

    @Test fun `setUnnaturalTwenty true clears loser`() = runTest {
        store.setLoser(true)
        store.setUnnaturalTwenty(true)
        assertTrue(store.unnaturalTwenty.first())
        assertFalse(store.loser.first())
    }

    @Test fun `setLoser true clears unnaturalTwenty`() = runTest {
        store.setUnnaturalTwenty(true)
        store.setLoser(true)
        assertTrue(store.loser.first())
        assertFalse(store.unnaturalTwenty.first())
    }

    @Test fun `setInfiniteGold does not affect other cheats`() = runTest {
        store.setUnnaturalTwenty(true)
        store.setInfiniteGold(true)
        assertTrue(store.unnaturalTwenty.first())
        assertTrue(store.infiniteGold.first())
    }
}
