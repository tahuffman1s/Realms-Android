package com.realmsoffate.game.data.content

import com.realmsoffate.game.game.ClassDef
import com.realmsoffate.game.game.Mutation
import com.realmsoffate.game.game.RaceDef
import com.realmsoffate.game.game.Spell
import kotlinx.serialization.Serializable

internal const val CONTENT_SCHEMA_VERSION = 5

@Serializable
internal data class SchemaVersionDto(val version: Int)

@Serializable
internal data class FactionsDto(
    val types: List<String>,
    val adjectives: List<String>,
    val nouns: List<String>,
)

@Serializable
internal data class NpcNamesDto(
    val firsts: List<String>,
    val titles: List<String>,
    val roles: List<String>,
)

@Serializable
internal data class WorldLoreDto(
    val eraLabels: List<String>,
    val rumors: List<String>,
)

@Serializable
data class EconState(val level: String, val description: String, val wealth: Int)

@Serializable
internal data class DescriptorsDto(
    val economyStates: List<EconState>,
    val exports: List<String>,
    val imports: List<String>,
    val governmentForms: List<String>,
    val successionTypes: List<String>,
    val rulerTraits: List<String>,
    val moods: List<String>,
    val goals: List<String>,
    val dispositions: List<String>,
)

@Serializable
data class EnemyArchetype(val title: String, val motive: String)

@Serializable
data class LostItem(val item: String, val why: String)

@Serializable
data class FlawPair(val flaw: String, val trigger: String)

@Serializable
internal data class BackstoryDto(
    val origins: List<String>,
    val motivations: List<String>,
    val darkSecrets: List<String>,
    val enemyArchetypes: List<EnemyArchetype>,
    val lostItems: List<LostItem>,
    val bonds: List<String>,
    val flaws: List<FlawPair>,
    val prophecies: List<String?>,
)

@Serializable
data class LocTemplate(val type: String, val icon: String, val names: List<String>)

@Serializable
internal data class LocationTemplatesDto(val templates: List<LocTemplate>)

@Serializable
internal data class PlayerNamesDto(val names: List<String>)

@Serializable
internal data class RacesDto(val list: List<RaceDef>)

@Serializable
internal data class ClassesDto(val list: List<ClassDef>)

@Serializable
data class FeatMeta(val name: String, val description: String, val icon: String)

@Serializable
internal data class FeatsDto(val list: List<FeatMeta>)

@Serializable
internal data class SpellsDto(val list: List<Spell>)

@Serializable
internal data class MutationsDto(val list: List<Mutation>)

@Serializable
data class ScenarioMeta(
    val id: String,
    val name: String,
    val sceneHint: String,
    val promptTemplate: String,
    val hasModifier: Boolean = false,
)

@Serializable
internal data class ScenariosDto(val list: List<ScenarioMeta>)

@Serializable
data class WorldEventTemplateDto(
    val id: String,
    val weight: Int,
    val needs: String,
    val context: String,
    val icon: String,
    val title: String,
    val prompt: String,
)

@Serializable
internal data class WorldEventsDto(val list: List<WorldEventTemplateDto>)

@Serializable
data class HistoricalEvents(
    val primordial: List<String>,
    val ancient: List<String>,
    val medieval: List<String>,
    val darkAge: List<String>,
    val recent: List<String>,
)
