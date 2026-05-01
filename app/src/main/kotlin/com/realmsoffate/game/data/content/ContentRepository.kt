package com.realmsoffate.game.data.content

import android.content.Context
import com.realmsoffate.game.game.ClassDef
import com.realmsoffate.game.game.Mutation
import com.realmsoffate.game.game.RaceDef
import com.realmsoffate.game.game.Spell
import java.io.File
import java.io.InputStream

object ContentRepository {

    private lateinit var bundle: ContentLoader.Bundle

    /** Filesystem root for hot-reload overrides; null when override is disabled. */
    private var overrideRoot: File? = null

    /** Per-path provenance from the most recent successful load: "asset" | "override". */
    private var sourceMap: Map<String, String> = emptyMap()

    /**
     * Load content into memory.
     *
     * When [allowOverride] is true (debug builds), each requested path is checked
     * against `<filesDir>/content/<path-without-content-prefix>` first; missing
     * override files fall through to the bundled APK assets. Per-file overlay —
     * writers can drop a single JSON without supplying the rest.
     *
     * Reload is atomic. If the new bundle fails to parse / validate, the prior
     * bundle is left untouched and the underlying [ContentException] propagates.
     */
    fun initialize(context: Context, allowOverride: Boolean = false) {
        val root = if (allowOverride) File(context.filesDir, "content") else null
        loadInto(root) { path -> context.assets.open(path) }
    }

    internal fun initializeFrom(open: (String) -> InputStream) {
        loadInto(overrideRoot = null, assetOpen = open)
    }

    @Synchronized
    private fun loadInto(overrideRoot: File?, assetOpen: (String) -> InputStream) {
        val recordedSources = mutableMapOf<String, String>()
        val opener: (String) -> InputStream = { path ->
            val rel = path.removePrefix("content/")
            val overrideFile = overrideRoot?.let { File(it, rel) }?.takeIf { it.isFile }
            if (overrideFile != null) {
                recordedSources[path] = "override"
                overrideFile.inputStream()
            } else {
                recordedSources[path] = "asset"
                assetOpen(path)
            }
        }
        // Throws ContentException on failure; bundle reference only updates on success.
        val newBundle = ContentLoader.loadAndValidate(opener)
        bundle = newBundle
        this.overrideRoot = overrideRoot
        this.sourceMap = recordedSources.toMap()
    }

    /** Snapshot of current load metadata — schema version, override status, per-file source. */
    fun info(): ContentInfo = ContentInfo(
        schemaVersion = CONTENT_SCHEMA_VERSION,
        overrideEnabled = overrideRoot != null,
        overrideRoot = overrideRoot?.absolutePath,
        sources = sourceMap,
    )

    val factionTypes: List<String> get() = bundle.factions.types
    val factionAdjectives: List<String> get() = bundle.factions.adjectives
    val factionNouns: List<String> get() = bundle.factions.nouns

    val npcFirsts: List<String> get() = bundle.npcNames.firsts
    val npcTitles: List<String> get() = bundle.npcNames.titles
    val npcRoles: List<String> get() = bundle.npcNames.roles

    val eraLabels: List<String> get() = bundle.worldLore.eraLabels
    val rumors: List<String> get() = bundle.worldLore.rumors

    val economyStates: List<EconState> get() = bundle.descriptors.economyStates
    val exports: List<String> get() = bundle.descriptors.exports
    val imports: List<String> get() = bundle.descriptors.imports
    val governmentForms: List<String> get() = bundle.descriptors.governmentForms
    val successionTypes: List<String> get() = bundle.descriptors.successionTypes
    val rulerTraits: List<String> get() = bundle.descriptors.rulerTraits
    val moods: List<String> get() = bundle.descriptors.moods
    val goals: List<String> get() = bundle.descriptors.goals
    val dispositions: List<String> get() = bundle.descriptors.dispositions

    val backstoryOrigins: List<String> get() = bundle.backstory.origins
    val backstoryMotivations: List<String> get() = bundle.backstory.motivations
    val backstoryDarkSecrets: List<String> get() = bundle.backstory.darkSecrets
    val backstoryEnemyArchetypes: List<EnemyArchetype> get() = bundle.backstory.enemyArchetypes
    val backstoryLostItems: List<LostItem> get() = bundle.backstory.lostItems
    val backstoryBonds: List<String> get() = bundle.backstory.bonds
    val backstoryFlaws: List<FlawPair> get() = bundle.backstory.flaws
    val backstoryProphecies: List<String?> get() = bundle.backstory.prophecies

    val locationTemplates: List<LocTemplate> get() = bundle.locationTemplates.templates
    val playerNames: List<String> get() = bundle.playerNames.names

    val races: List<RaceDef> get() = bundle.races.list
    val classes: List<ClassDef> get() = bundle.classes.list
    val featMetas: List<FeatMeta> get() = bundle.feats.list
    val spells: List<Spell> get() = bundle.spells.list
    val mutations: List<Mutation> get() = bundle.mutations.list
    val scenarioMetas: List<ScenarioMeta> get() = bundle.scenarios.list
    val worldEventTemplates: List<WorldEventTemplateDto> get() = bundle.worldEvents.list
    val historicalEvents: HistoricalEvents get() = bundle.historicalEvents
}
