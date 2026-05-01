package com.realmsoffate.game.data.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Phase 6 M6 — verifies the debug-only `filesDir/content/` overlay short-circuits
 * the bundled assets per-file, propagates schema mismatches without dropping the
 * prior bundle, and is a no-op when [ContentRepository.initialize] is called with
 * `allowOverride = false`.
 */
@RunWith(RobolectricTestRunner::class)
class HotReloadTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val overrideRoot: File = File(ctx.filesDir, "content")

    @After
    fun tearDown() {
        // Clear any test-written override files between cases and restore default load.
        if (overrideRoot.exists()) overrideRoot.deleteRecursively()
        ContentRepository.initialize(ctx, allowOverride = false)
    }

    @Test
    fun `override file wins over bundled asset`() {
        writeOverride(
            "world/mutations.json",
            """{"list":[{"id":"hot_test","name":"Hot Test","icon":"🔁","desc":"override","prompt":"OVERRIDE PROMPT"}]}"""
        )

        ContentRepository.initialize(ctx, allowOverride = true)

        assertEquals(1, ContentRepository.mutations.size)
        assertEquals("hot_test", ContentRepository.mutations[0].id)
        assertEquals("override", ContentRepository.info().sources["content/world/mutations.json"])
    }

    @Test
    fun `missing override files fall through to bundled assets`() {
        // Override directory exists but no files inside.
        overrideRoot.mkdirs()

        ContentRepository.initialize(ctx, allowOverride = true)

        assertTrue(ContentRepository.mutations.size >= 16) // bundled count is 16
        val info = ContentRepository.info()
        assertTrue(info.overrideEnabled)
        assertTrue(info.sources.values.all { it == "asset" })
    }

    @Test
    fun `override schema mismatch raises and preserves prior bundle`() {
        // Establish a known-good bundle first.
        ContentRepository.initialize(ctx, allowOverride = false)
        val priorMutationCount = ContentRepository.mutations.size

        writeOverride("schema-version.json", """{"version": 999}""")

        assertThrows(ContentException.SchemaVersionMismatch::class.java) {
            ContentRepository.initialize(ctx, allowOverride = true)
        }

        // Prior bundle stays queryable — atomic reload contract.
        assertEquals(priorMutationCount, ContentRepository.mutations.size)
    }

    @Test
    fun `disabled override ignores filesDir contents`() {
        writeOverride(
            "world/mutations.json",
            """{"list":[{"id":"should_be_ignored","name":"x","icon":"x","desc":"x","prompt":"x"}]}"""
        )

        ContentRepository.initialize(ctx, allowOverride = false)

        assertNotEquals("should_be_ignored", ContentRepository.mutations.first().id)
        assertTrue(ContentRepository.mutations.size >= 16)
        val info = ContentRepository.info()
        assertEquals(false, info.overrideEnabled)
        assertEquals(null, info.overrideRoot)
    }

    @Test
    fun `info reports per-file override source`() {
        writeOverride(
            "world/mutations.json",
            """{"list":[{"id":"x","name":"x","icon":"x","desc":"x","prompt":"x"}]}"""
        )

        ContentRepository.initialize(ctx, allowOverride = true)

        val sources = ContentRepository.info().sources
        assertEquals("override", sources["content/world/mutations.json"])
        // Adjacent files in the same folder should still be coming from the asset.
        assertEquals("asset", sources["content/world/scenarios.json"])
        // Schema file too.
        assertEquals("asset", sources["content/schema-version.json"])
    }

    private fun writeOverride(relativePath: String, body: String) {
        val target = File(overrideRoot, relativePath)
        target.parentFile?.mkdirs()
        target.writeText(body, Charsets.UTF_8)
        assertNotNull(target.parentFile)
    }
}
