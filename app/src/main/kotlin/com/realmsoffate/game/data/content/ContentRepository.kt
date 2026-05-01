package com.realmsoffate.game.data.content

import android.content.Context

object ContentRepository {

    private lateinit var bundle: ContentLoader.Bundle

    fun initialize(context: Context) {
        bundle = ContentLoader.loadAndValidate { path -> context.assets.open(path) }
    }

    internal fun initializeFrom(open: (String) -> java.io.InputStream) {
        bundle = ContentLoader.loadAndValidate(open)
    }

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
}
