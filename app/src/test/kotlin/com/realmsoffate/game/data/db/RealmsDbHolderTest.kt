package com.realmsoffate.game.data.db

import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.data.SaveStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RealmsDbHolderTest {

    @Before
    fun setUp() { RealmsDbHolder.resetForTest() }

    @After
    fun tearDown() { RealmsDbHolder.resetForTest() }

    @Test
    fun `switchTo routes to per-slot file and reinitializes repo`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        RealmsDbHolder.init(ctx)
        RealmsDbHolder.switchTo("slot_a")
        val a = RealmsDbHolder.currentDbFile()
        RealmsDbHolder.switchTo("slot_b")
        val b = RealmsDbHolder.currentDbFile()
        assertNotEquals(a.absolutePath, b.absolutePath)
        assertTrue(a.name.contains("slot_a"))
        assertTrue(b.name.contains("slot_b"))
    }

    @Test
    fun `dbKeyForSave collapses autosave to character-keyed slot`() {
        assertEquals(
            SaveStore.slotKeyFor("Kaelis"),
            dbKeyForSave(SaveStore.AUTOSAVE_KEY, "Kaelis")
        )
    }

    @Test
    fun `dbKeyForSave passes through named slot`() {
        val named = SaveStore.slotKeyFor("Kaelis")
        assertEquals(named, dbKeyForSave(named, "Kaelis"))
    }

    @Test
    fun `dbKeyForSave returns raw slot when character name blank`() {
        assertEquals("something", dbKeyForSave("something", ""))
        assertEquals("something", dbKeyForSave("something", null))
    }

    @Test
    fun `closeSlotIfOpen nulls db when on target slot and leaves it alone otherwise`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        RealmsDbHolder.init(ctx)
        RealmsDbHolder.switchTo("alpha")
        RealmsDbHolder.closeSlotIfOpen("beta")   // different slot — no-op
        // Still readable — db is still open on alpha
        @Suppress("UNUSED_VARIABLE") val stillAlpha = RealmsDbHolder.db
        RealmsDbHolder.closeSlotIfOpen("alpha")  // target matches — closes
        // `db` getter must now throw the "not initialized" error
        try {
            RealmsDbHolder.db
            org.junit.Assert.fail("expected db getter to throw after closeSlotIfOpen")
        } catch (e: IllegalStateException) {
            // expected
        }
    }
}
