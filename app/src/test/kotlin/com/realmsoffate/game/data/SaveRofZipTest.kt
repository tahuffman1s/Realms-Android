package com.realmsoffate.game.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveRofZipTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `write-extract roundtrip preserves text and binary entries`() {
        val workdir = tmp.newFolder("in")
        val saveJson = File(workdir, "save.json").apply { writeText("""{"v":1}""") }
        val manifestJson = File(workdir, "manifest.json").apply { writeText("""{"version":3}""") }
        val dbFile = File(workdir, "realms.db").apply { writeBytes(ByteArray(256) { it.toByte() }) }

        val zipOut = tmp.newFile("slot.rofsave")
        SaveRofZip.write(zipOut, mapOf(
            "save.json" to saveJson,
            "manifest.json" to manifestJson,
            "realms.db" to dbFile
        ))

        val outDir = tmp.newFolder("out")
        SaveRofZip.extract(zipOut, outDir)

        assertArrayEquals(dbFile.readBytes(), File(outDir, "realms.db").readBytes())
        assertEquals("""{"v":1}""", File(outDir, "save.json").readText())
    }

    @Test
    fun `readManifest returns json without full extract`() {
        val workdir = tmp.newFolder("in")
        val manifestJson = File(workdir, "manifest.json").apply {
            writeText("""{"version":3,"savedAt":"2026-04-21"}""")
        }
        val zipOut = tmp.newFile("slot.rofsave")
        SaveRofZip.write(zipOut, mapOf("manifest.json" to manifestJson))
        val text = SaveRofZip.readManifest(zipOut)
        assertTrue(text.contains("\"version\":3"))
    }

    @Test
    fun `writeMixed supports text-only archives`() {
        val zipOut = tmp.newFile("text.rofsave")
        SaveRofZip.writeMixed(
            out = zipOut,
            textEntries = mapOf("manifest.json" to "{\"v\":3}", "save.json" to "{}")
        )
        assertEquals("{\"v\":3}", SaveRofZip.readTextEntry(zipOut, "manifest.json"))
        assertEquals("{}", SaveRofZip.readTextEntry(zipOut, "save.json"))
    }

    @Test
    fun `readTextEntry returns null for missing entry or file`() {
        val missing = tmp.newFile("empty.rofsave")
        SaveRofZip.writeMixed(missing, textEntries = mapOf("a.txt" to "a"))
        assertNull(SaveRofZip.readTextEntry(missing, "b.txt"))
        val nonexistent = File(tmp.root, "does-not-exist.rofsave")
        assertNull(SaveRofZip.readTextEntry(nonexistent, "anything"))
    }
}
