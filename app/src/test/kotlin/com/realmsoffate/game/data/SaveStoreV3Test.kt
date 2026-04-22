package com.realmsoffate.game.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SaveStoreV3Test {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private lateinit var saveDir: File

    @Before
    fun setUp() {
        SaveStore.init(ApplicationProvider.getApplicationContext<Context>())
        saveDir = File(ApplicationProvider.getApplicationContext<Context>().filesDir, "saves")
        saveDir.mkdirs()
        saveDir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        saveDir.listFiles()?.forEach { it.delete() }
    }

    private fun fixture(name: String, turns: Int = 5): SaveData = SaveData(
        version = 3,
        character = Character(name = name, race = "human", cls = "fighter"),
        morality = 0,
        factionRep = emptyMap(),
        worldMap = WorldMap(
            locations = mutableListOf(),
            roads = emptyList(), startId = 0,
            terrain = emptyList(), rivers = emptyList(), lakes = emptyList()
        ),
        currentLoc = 0,
        playerPos = null,
        worldLore = null,
        worldEvents = emptyList(),
        lastEventTurn = 0,
        npcLog = emptyList(),
        party = emptyList(),
        quests = emptyList(),
        hotbar = emptyList(),
        history = emptyList(),
        turns = turns,
        scene = "test-scene",
        savedAt = "2026-04-21T10:00:00Z"
    )

    @Test
    fun `v3 write produces rofsave zip and roundtrips`() = runTest {
        val data = fixture("Kaelis", turns = 12)
        SaveStore.write("kaelis", data)

        val rof = File(saveDir, "slot_kaelis.rofsave")
        assertTrue("rofsave should exist", rof.exists())

        val readBack = SaveStore.read("kaelis")
        assertNotNull(readBack)
        assertEquals("Kaelis", readBack!!.character.name)
        assertEquals(12, readBack.turns)
    }

    @Test
    fun `v3 write removes any legacy json for the same slot`() = runTest {
        val legacy = File(saveDir, "slot_kaelis.json")
        legacy.writeText(json.encodeToString(fixture("Kaelis")))
        assertTrue(legacy.exists())

        SaveStore.write("kaelis", fixture("Kaelis", turns = 20))

        assertFalse("legacy json should be removed after v3 write", legacy.exists())
    }

    @Test
    fun `read falls back to legacy json when no rofsave present`() = runTest {
        val legacy = File(saveDir, "slot_vera.json")
        legacy.writeText(json.encodeToString(fixture("Vera", turns = 7)))

        val readBack = SaveStore.read("vera")
        assertNotNull(readBack)
        assertEquals("Vera", readBack!!.character.name)
        assertEquals(7, readBack.turns)
    }

    @Test
    fun `listSlots surfaces both v3 and v2 entries newest first`() = runTest {
        SaveStore.write("kaelis", fixture("Kaelis", turns = 3).copy(savedAt = "2026-04-21T10:00:00Z"))
        val legacy = File(saveDir, "slot_old.json")
        legacy.writeText(json.encodeToString(fixture("OldHero", turns = 1).copy(savedAt = "2026-04-20T10:00:00Z")))

        val slots = SaveStore.listSlots()
        assertEquals(2, slots.size)
        assertEquals("kaelis", slots[0].slot) // newest first
        assertEquals("old", slots[1].slot)
    }

    @Test
    fun `delete sweeps rofsave json and v2 backup for the slot`() = runTest {
        SaveStore.write("kaelis", fixture("Kaelis"))
        File(saveDir, "slot_kaelis.v2.bak.json").writeText("legacy-backup")
        // The legacy JSON was deleted by the v3 write, but simulate a stray:
        File(saveDir, "slot_kaelis.json").writeText("legacy")

        SaveStore.delete("kaelis")

        assertFalse(File(saveDir, "slot_kaelis.rofsave").exists())
        assertFalse(File(saveDir, "slot_kaelis.json").exists())
        assertFalse(File(saveDir, "slot_kaelis.v2.bak.json").exists())
    }

    @Test
    fun `delete sweeps the autosave sibling when the character matches`() = runTest {
        SaveStore.write("kaelis", fixture("Kaelis"))
        SaveStore.write("autosave", fixture("Kaelis"))
        // A different character uses autosave-style naming — must NOT be swept.
        SaveStore.write("other", fixture("Other"))

        SaveStore.delete("kaelis")

        assertFalse(File(saveDir, "slot_kaelis.rofsave").exists())
        assertFalse("autosave should be swept — same character", File(saveDir, "slot_autosave.rofsave").exists())
        assertTrue("other character's slot should survive", File(saveDir, "slot_other.rofsave").exists())
    }

    @Test
    fun `delete autosave also removes the character-keyed slot when they match`() = runTest {
        SaveStore.write("kaelis", fixture("Kaelis"))
        SaveStore.write("autosave", fixture("Kaelis"))

        SaveStore.delete("autosave")

        assertFalse(File(saveDir, "slot_autosave.rofsave").exists())
        assertFalse("character-keyed slot should be swept too", File(saveDir, "slot_kaelis.rofsave").exists())
    }
}
