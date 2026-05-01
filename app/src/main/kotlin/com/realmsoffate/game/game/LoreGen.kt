package com.realmsoffate.game.game

import com.realmsoffate.game.data.EconomyInfo
import com.realmsoffate.game.data.Faction
import com.realmsoffate.game.data.GovernmentInfo
import com.realmsoffate.game.data.HistoryEntry
import com.realmsoffate.game.data.LoreNpc
import com.realmsoffate.game.data.PastRuler
import com.realmsoffate.game.data.WorldLore
import com.realmsoffate.game.data.WorldMap
import com.realmsoffate.game.data.content.ContentRepository
import com.realmsoffate.game.data.content.EconState
import com.realmsoffate.game.util.Templates
import kotlin.random.Random

private val FACTION_TYPES: List<String> get() = ContentRepository.factionTypes
private val FACTION_ADJS: List<String> get() = ContentRepository.factionAdjectives
private val FACTION_NOUNS: List<String> get() = ContentRepository.factionNouns

private val NPC_FIRSTS: List<String> get() = ContentRepository.npcFirsts
private val NPC_TITLES: List<String> get() = ContentRepository.npcTitles
private val NPC_ROLES: List<String> get() = ContentRepository.npcRoles

private val RACES = listOf("Human", "Elf", "Dwarf", "Halfling", "Half-Elf", "Half-Orc", "Tiefling", "Dragonborn", "Gnome")

private val WORLD_NAME_PATTERNS: List<(Random) -> String> = listOf(
    { r -> "The ${listOf("Sundered", "Shattered", "Hollow", "Eternal", "Wounded", "Thirteenth").random(r)} ${listOf("Realms", "Reach", "Kingdoms", "Coast", "Expanse").random(r)}" },
    { r -> listOf("Aethelmar", "Veridonia", "Corvathia", "Dornmark", "Skalaria", "Ynderthel", "Thessarion", "Valdrith").random(r) },
    { r -> "Lands of ${NPC_FIRSTS.random(r)}" },
    { r -> "${listOf("Ashen", "Iron", "Silver", "Obsidian", "Verdant", "Crimson").random(r)} ${listOf("March", "Reach", "Expanse", "Heartland", "Dominion").random(r)}" },
    { r -> "The ${listOf("Bleeding", "Drowning", "Singing", "Forgotten", "Burning", "Sleeping").random(r)} ${listOf("Marches", "Wastes", "Isles", "Frontier", "Depths").random(r)}" },
    { r -> listOf("Kharazad", "Xianyang", "Ashkhabar", "Morvenna", "Njordheim", "Takamagahara", "Aztlanara", "Wakanda").random(r) },
    { r -> "The ${listOf("Seven", "Twelve", "Thousand", "Last", "First").random(r)} ${listOf("Kingdoms", "Thrones", "Towers", "Gates", "Crowns").random(r)}" },
    { r -> "${FACTION_ADJS.random(r)} ${listOf("Empire", "Confederacy", "Wastes", "Shores", "Wilds").random(r)}" }
)

private val ERA_LABELS: List<String> get() = ContentRepository.eraLabels

private val PRIMORDIAL_EVENTS: List<String> get() = ContentRepository.historicalEvents.primordial
private val ANCIENT_EVENTS: List<String> get() = ContentRepository.historicalEvents.ancient
private val MEDIEVAL_EVENTS: List<String> get() = ContentRepository.historicalEvents.medieval
private val DARK_AGE_EVENTS: List<String> get() = ContentRepository.historicalEvents.darkAge
private val RECENT_EVENTS: List<String> get() = ContentRepository.historicalEvents.recent

private fun renderPrimordial(template: String, loc: String): String =
    Templates.interpolate(template, mapOf("loc" to loc))

private fun renderEra(template: String, f: String, n: String, loc: String): String =
    Templates.interpolate(template, mapOf("f" to f, "n" to n, "loc" to loc))

private val RUMORS: List<String> get() = ContentRepository.rumors
private val ECON_STATES: List<EconState> get() = ContentRepository.economyStates
private val EXPORTS: List<String> get() = ContentRepository.exports
private val IMPORTS: List<String> get() = ContentRepository.imports
private val GOV_FORMS: List<String> get() = ContentRepository.governmentForms
private val SUCCESSIONS: List<String> get() = ContentRepository.successionTypes
private val RULER_TRAITS: List<String> get() = ContentRepository.rulerTraits
private val MOODS: List<String> get() = ContentRepository.moods
private val GOALS: List<String> get() = ContentRepository.goals
private val DISPOSITIONS: List<String> get() = ContentRepository.dispositions

object LoreGen {
    // ---- Exposed pools for per-turn DeepSeek name/lore hints ----
    fun npcFirstNames(): List<String> = NPC_FIRSTS
    fun npcTitles(): List<String> = NPC_TITLES
    fun npcRoles(): List<String> = NPC_ROLES
    fun factionAdjs(): List<String> = FACTION_ADJS
    fun factionNouns(): List<String> = FACTION_NOUNS
    fun factionTypes(): List<String> = FACTION_TYPES
    fun rulerTraits(): List<String> = RULER_TRAITS
    fun moods(): List<String> = MOODS
    fun rumors(): List<String> = RUMORS
    fun govForms(): List<String> = GOV_FORMS
    fun successions(): List<String> = SUCCESSIONS
    fun exports(): List<String> = EXPORTS
    fun imports(): List<String> = IMPORTS
    fun goals(): List<String> = GOALS
    fun dispositions(): List<String> = DISPOSITIONS

    fun generate(worldMap: WorldMap, seed: Long = System.currentTimeMillis()): WorldLore {
        val rand = Random(seed)
        val worldName = WORLD_NAME_PATTERNS.random(rand)(rand)
        val era = ERA_LABELS.random(rand)

        val factionCount = 2 + rand.nextInt(3)
        val factions = mutableListOf<Faction>()
        val usedBases = mutableSetOf<String>()
        val locs = worldMap.locations.filter { it.type in listOf("town", "city", "castle") }
        for (i in 0 until minOf(factionCount, locs.size)) {
            val loc = locs.filter { it.name !in usedBases }.randomOrNull(rand) ?: locs.random(rand)
            usedBases += loc.name
            val name = "The " + FACTION_ADJS.random(rand) + " " + FACTION_NOUNS.random(rand)
            val type = FACTION_TYPES.random(rand)
            val govForm = GOV_FORMS.random(rand)
            val ruler = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand)
            val econ = ECON_STATES.random(rand)
            val government = GovernmentInfo(
                form = govForm,
                ruler = ruler,
                capital = loc.name,
                rulerTrait = RULER_TRAITS.random(rand),
                yearsInPower = 1 + rand.nextInt(45),
                dynasty = if (rand.nextFloat() < 0.5f) "House " + FACTION_NOUNS.random(rand) else "None",
                succession = SUCCESSIONS.random(rand),
                pastRulers = List(2 + rand.nextInt(3)) {
                    PastRuler(
                        name = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand),
                        yearsAgo = (it + 1) * (10 + rand.nextInt(30)),
                        fate = listOf(
                            "died in battle", "assassinated", "abdicated",
                            "vanished without trace", "died of plague",
                            "executed for heresy", "overthrown", "died of old age",
                            "lost to madness"
                        ).random(rand)
                    )
                }
            )
            val economy = EconomyInfo(
                level = econ.level,
                wealth = econ.wealth,
                description = econ.description,
                exports = EXPORTS.shuffled(rand).take(3),
                imports = IMPORTS.shuffled(rand).take(3),
                tax = when (econ.level) {
                    "Thriving" -> "5% on trade, negligible head-tax"
                    "Prosperous" -> "8% on trade, small head-tax"
                    "Stable" -> "10% on trade, modest head-tax"
                    "Strained" -> "15% on trade, rising head-tax"
                    "Impoverished" -> "20% on trade, heavy head-tax"
                    "Collapsed" -> "currency worthless — levies in goods"
                    "Hoarded" -> "noble exemption, crushing commoner tax"
                    "War Economy" -> "war tithe, conscription in lieu"
                    "Black Market" -> "official 12%, nobody pays"
                    else -> "unpredictable assessment"
                }
            )
            factions += Faction(
                name = name,
                type = type,
                description = "A $type ruling from ${loc.name}. Known for ${listOf("cruelty", "honor", "secrecy", "wealth", "war", "scholarship", "zeal").random(rand)}.",
                baseLoc = loc.name,
                government = government,
                economy = economy,
                population = listOf("handful of villages", "a dozen towns", "a great city and outskirts", "scattered strongholds", "an entire kingdom").random(rand),
                mood = MOODS.random(rand),
                disposition = DISPOSITIONS.random(rand),
                goal = GOALS.random(rand)
            )
        }
        val npcs = mutableListOf<LoreNpc>()
        factions.forEach { f ->
            npcs += LoreNpc(
                name = f.government?.ruler ?: NPC_FIRSTS.random(rand),
                race = RACES.random(rand),
                role = NPC_ROLES.random(rand),
                age = (20 + rand.nextInt(80)).toString(),
                appearance = listOf("scarred face", "cold eyes", "ornate robes", "calloused hands", "noble bearing", "elaborate tattoos").random(rand),
                personality = listOf("ambitious", "paranoid", "benevolent", "cruel", "melancholy", "zealous", "charismatic").random(rand),
                location = f.baseLoc,
                faction = f.name
            )
        }
        // Add 2-5 independent NPCs not attached to factions
        repeat(2 + rand.nextInt(4)) {
            val home = worldMap.locations.random(rand).name
            npcs += LoreNpc(
                name = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand),
                race = RACES.random(rand),
                role = NPC_ROLES.random(rand),
                age = (18 + rand.nextInt(70)).toString(),
                appearance = listOf("wind-burnt skin", "a quick smile", "haunted eyes", "ink-stained fingers", "a drifter's posture").random(rand),
                personality = listOf("curious", "world-weary", "mischievous", "kind", "disinterested").random(rand),
                location = home,
                faction = null
            )
        }

        // Primordial + era events
        val primordial = PRIMORDIAL_EVENTS.shuffled(rand).take(3).map {
            renderPrimordial(it, worldMap.locations.first().name)
        }

        val history = buildList {
            primordial.forEachIndexed { i, text ->
                add(HistoryEntry(era = "primordial", year = -2000 - i * 100, text = text))
            }
            repeat(3) {
                if (factions.isEmpty()) return@repeat
                val f = factions.random(rand)
                val n = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand)
                val loc = worldMap.locations.random(rand).name
                add(HistoryEntry("ancient", -800 + it * 120 + rand.nextInt(40), renderEra(ANCIENT_EVENTS.random(rand), f.name, n, loc)))
            }
            repeat(4) {
                if (factions.isEmpty()) return@repeat
                val f = factions.random(rand)
                val n = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand)
                val loc = worldMap.locations.random(rand).name
                add(HistoryEntry("medieval", -400 + it * 80 + rand.nextInt(20), renderEra(MEDIEVAL_EVENTS.random(rand), f.name, n, loc)))
            }
            if (rand.nextFloat() < 0.7f) {
                repeat(2) {
                    if (factions.isEmpty()) return@repeat
                    val f = factions.random(rand)
                    val n = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand)
                    val loc = worldMap.locations.random(rand).name
                    add(HistoryEntry("dark_age", -150 + it * 40 + rand.nextInt(10), renderEra(DARK_AGE_EVENTS.random(rand), f.name, n, loc)))
                }
            }
            repeat(3) {
                if (factions.isEmpty()) return@repeat
                val f = factions.random(rand)
                val n = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand)
                val loc = worldMap.locations.random(rand).name
                add(HistoryEntry("recent", -20 + it * 8 + rand.nextInt(4), renderEra(RECENT_EVENTS.random(rand), f.name, n, loc)))
            }
        }.sortedBy { it.year }

        val pickedMutations = Mutations.pickForWorld(rand)
        val mutations = pickedMutations.map { "${it.icon} ${it.name} — ${it.desc}" }
        val mutationIds = pickedMutations.map { it.id }

        val rumors = RUMORS.shuffled(rand).take(6 + rand.nextInt(4))

        return WorldLore(
            factions = factions,
            npcs = npcs,
            primordial = primordial,
            mutations = mutations,
            mutationIds = mutationIds,
            rumors = rumors,
            worldName = worldName,
            era = era,
            history = history
        )
    }

    fun findLocalFaction(wm: WorldMap, lore: WorldLore?, locId: Int): Faction? {
        if (lore == null) return null
        val loc = wm.locations.getOrNull(locId) ?: return null
        var best: Faction? = null
        var bestDist = Double.MAX_VALUE
        for (f in lore.factions) {
            val base = wm.locations.firstOrNull { it.name == f.baseLoc } ?: continue
            val d = kotlin.math.hypot((loc.x - base.x).toDouble(), (loc.y - base.y).toDouble())
            if (d < bestDist) { bestDist = d; best = f }
        }
        return best
    }
}
