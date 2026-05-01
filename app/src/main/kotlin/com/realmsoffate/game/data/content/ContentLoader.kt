package com.realmsoffate.game.data.content

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

internal object ContentLoader {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    data class Bundle(
        val factions: FactionsDto,
        val npcNames: NpcNamesDto,
        val worldLore: WorldLoreDto,
        val descriptors: DescriptorsDto,
        val backstory: BackstoryDto,
        val locationTemplates: LocationTemplatesDto,
        val playerNames: PlayerNamesDto,
        val races: RacesDto,
        val classes: ClassesDto,
        val feats: FeatsDto,
    )

    fun loadAndValidate(open: (String) -> InputStream): Bundle {
        val version = parse<SchemaVersionDto>("content/schema-version.json", open).version
        if (version != CONTENT_SCHEMA_VERSION) {
            throw ContentException.SchemaVersionMismatch(CONTENT_SCHEMA_VERSION, version)
        }
        return Bundle(
            factions = parse("content/lore/factions.json", open),
            npcNames = parse("content/lore/npc-names.json", open),
            worldLore = parse("content/lore/world.json", open),
            descriptors = parse("content/lore/descriptors.json", open),
            backstory = parse("content/backstory/fragments.json", open),
            locationTemplates = parse("content/world/location-templates.json", open),
            playerNames = parse("content/names/player.json", open),
            races = parse("content/characters/races.json", open),
            classes = parse("content/characters/classes.json", open),
            feats = parse("content/characters/feats.json", open),
        )
    }

    private inline fun <reified T> parse(path: String, open: (String) -> InputStream): T {
        val text = try {
            open(path).use { it.bufferedReader().readText() }
        } catch (e: FileNotFoundException) {
            throw ContentException.MissingFile(path, e)
        } catch (e: IOException) {
            throw ContentException.MissingFile(path, e)
        }
        return try {
            json.decodeFromString<T>(text)
        } catch (e: SerializationException) {
            throw ContentException.MalformedJson(path, e)
        } catch (e: IllegalArgumentException) {
            throw ContentException.MalformedJson(path, e)
        }
    }
}
