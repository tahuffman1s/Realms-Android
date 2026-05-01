package com.realmsoffate.game.data.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class ContentRepositoryTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `parses all shipped assets and exposes non-empty pools`() {
        ContentRepository.initialize(ctx)
        assertTrue(ContentRepository.factionTypes.isNotEmpty())
        assertTrue(ContentRepository.factionAdjectives.isNotEmpty())
        assertTrue(ContentRepository.factionNouns.isNotEmpty())
        assertTrue(ContentRepository.npcFirsts.isNotEmpty())
        assertTrue(ContentRepository.npcTitles.isNotEmpty())
        assertTrue(ContentRepository.npcRoles.isNotEmpty())
        assertTrue(ContentRepository.eraLabels.isNotEmpty())
        assertTrue(ContentRepository.rumors.isNotEmpty())
        assertTrue(ContentRepository.economyStates.isNotEmpty())
        assertTrue(ContentRepository.exports.isNotEmpty())
        assertTrue(ContentRepository.imports.isNotEmpty())
        assertTrue(ContentRepository.governmentForms.isNotEmpty())
        assertTrue(ContentRepository.successionTypes.isNotEmpty())
        assertTrue(ContentRepository.rulerTraits.isNotEmpty())
        assertTrue(ContentRepository.moods.isNotEmpty())
        assertTrue(ContentRepository.goals.isNotEmpty())
        assertTrue(ContentRepository.dispositions.isNotEmpty())
        assertTrue(ContentRepository.backstoryOrigins.isNotEmpty())
        assertTrue(ContentRepository.backstoryMotivations.isNotEmpty())
        assertTrue(ContentRepository.backstoryDarkSecrets.isNotEmpty())
        assertTrue(ContentRepository.backstoryEnemyArchetypes.isNotEmpty())
        assertTrue(ContentRepository.backstoryLostItems.isNotEmpty())
        assertTrue(ContentRepository.backstoryBonds.isNotEmpty())
        assertTrue(ContentRepository.backstoryFlaws.isNotEmpty())
        assertTrue(ContentRepository.backstoryProphecies.isNotEmpty())
        assertTrue(ContentRepository.locationTemplates.isNotEmpty())
        assertTrue(ContentRepository.playerNames.isNotEmpty())
    }

    @Test
    fun `detects schema version mismatch`() {
        val open = wrapAssets(ctx) { path ->
            if (path == "content/schema-version.json") """{"version": 999}""".toStream() else null
        }
        val ex = assertThrows(ContentException.SchemaVersionMismatch::class.java) {
            ContentLoader.loadAndValidate(open)
        }
        assertEquals(CONTENT_SCHEMA_VERSION, ex.expected)
        assertEquals(999, ex.actual)
    }

    @Test
    fun `detects malformed json`() {
        val open = wrapAssets(ctx) { path ->
            if (path == "content/lore/factions.json") "{ not json".toStream() else null
        }
        val ex = assertThrows(ContentException.MalformedJson::class.java) {
            ContentLoader.loadAndValidate(open)
        }
        assertEquals("content/lore/factions.json", ex.path)
    }

    @Test
    fun `detects missing file`() {
        val open: (String) -> InputStream = { path ->
            if (path == "content/names/player.json") {
                throw FileNotFoundException(path)
            }
            ctx.assets.open(path)
        }
        val ex = assertThrows(ContentException.MissingFile::class.java) {
            ContentLoader.loadAndValidate(open)
        }
        assertEquals("content/names/player.json", ex.path)
    }

    private fun wrapAssets(ctx: Context, override: (String) -> InputStream?): (String) -> InputStream =
        { path -> override(path) ?: ctx.assets.open(path) }

    private fun String.toStream(): InputStream = ByteArrayInputStream(toByteArray(Charsets.UTF_8))
}
