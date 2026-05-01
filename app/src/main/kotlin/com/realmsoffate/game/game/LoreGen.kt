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
import com.realmsoffate.game.util.Templates
import kotlin.random.Random

/**
 * Pool of playable D&D 5e races used by lore-side NPC generation. Distinct from
 * `Races.list` (player races) — this list is intentionally narrower and stable
 * for narrative variety. Stays in code; not part of the JSON content extraction.
 */
private val LORE_NPC_RACES = listOf(
    "Human", "Elf", "Dwarf", "Halfling", "Half-Elf", "Half-Orc", "Tiefling", "Dragonborn", "Gnome"
)

/**
 * Eight world-name generators with inline word lists. Out of scope for Phase 6
 * JSON extraction — different shape from the rest of the migrated content,
 * smaller writer-edit payoff. Worth a follow-up if writers want them editable.
 */
private val WORLD_NAME_PATTERNS: List<(Random) -> String> = listOf(
    { r -> "The ${listOf("Sundered", "Shattered", "Hollow", "Eternal", "Wounded", "Thirteenth").random(r)} ${listOf("Realms", "Reach", "Kingdoms", "Coast", "Expanse").random(r)}" },
    { r -> listOf("Aethelmar", "Veridonia", "Corvathia", "Dornmark", "Skalaria", "Ynderthel", "Thessarion", "Valdrith").random(r) },
    { r -> "Lands of ${ContentRepository.npcFirsts.random(r)}" },
    { r -> "${listOf("Ashen", "Iron", "Silver", "Obsidian", "Verdant", "Crimson").random(r)} ${listOf("March", "Reach", "Expanse", "Heartland", "Dominion").random(r)}" },
    { r -> "The ${listOf("Bleeding", "Drowning", "Singing", "Forgotten", "Burning", "Sleeping").random(r)} ${listOf("Marches", "Wastes", "Isles", "Frontier", "Depths").random(r)}" },
    { r -> listOf("Kharazad", "Xianyang", "Ashkhabar", "Morvenna", "Njordheim", "Takamagahara", "Aztlanara", "Wakanda").random(r) },
    { r -> "The ${listOf("Seven", "Twelve", "Thousand", "Last", "First").random(r)} ${listOf("Kingdoms", "Thrones", "Towers", "Gates", "Crowns").random(r)}" },
    { r -> "${ContentRepository.factionAdjectives.random(r)} ${listOf("Empire", "Confederacy", "Wastes", "Shores", "Wilds").random(r)}" }
)

private fun renderPrimordial(template: String, loc: String): String =
    Templates.interpolate(template, mapOf("loc" to loc))

private fun renderEra(template: String, f: String, n: String, loc: String): String =
    Templates.interpolate(template, mapOf("f" to f, "n" to n, "loc" to loc))

object LoreGen {

    fun generate(worldMap: WorldMap, seed: Long = System.currentTimeMillis()): WorldLore {
        val rand = Random(seed)
        val cr = ContentRepository
        val worldName = WORLD_NAME_PATTERNS.random(rand)(rand)
        val era = cr.eraLabels.random(rand)

        val factionCount = 2 + rand.nextInt(3)
        val factions = mutableListOf<Faction>()
        val usedBases = mutableSetOf<String>()
        val locs = worldMap.locations.filter { it.type in listOf("town", "city", "castle") }
        for (i in 0 until minOf(factionCount, locs.size)) {
            val loc = locs.filter { it.name !in usedBases }.randomOrNull(rand) ?: locs.random(rand)
            usedBases += loc.name
            val name = "The " + cr.factionAdjectives.random(rand) + " " + cr.factionNouns.random(rand)
            val type = cr.factionTypes.random(rand)
            val govForm = cr.governmentForms.random(rand)
            val ruler = cr.npcFirsts.random(rand) + " " + cr.npcTitles.random(rand)
            val econ = cr.economyStates.random(rand)
            val government = GovernmentInfo(
                form = govForm,
                ruler = ruler,
                capital = loc.name,
                rulerTrait = cr.rulerTraits.random(rand),
                yearsInPower = 1 + rand.nextInt(45),
                dynasty = if (rand.nextFloat() < 0.5f) "House " + cr.factionNouns.random(rand) else "None",
                succession = cr.successionTypes.random(rand),
                pastRulers = List(2 + rand.nextInt(3)) {
                    PastRuler(
                        name = cr.npcFirsts.random(rand) + " " + cr.npcTitles.random(rand),
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
                exports = cr.exports.shuffled(rand).take(3),
                imports = cr.imports.shuffled(rand).take(3),
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
                mood = cr.moods.random(rand),
                disposition = cr.dispositions.random(rand),
                goal = cr.goals.random(rand)
            )
        }
        val npcs = mutableListOf<LoreNpc>()
        factions.forEach { f ->
            npcs += LoreNpc(
                name = f.government?.ruler ?: cr.npcFirsts.random(rand),
                race = LORE_NPC_RACES.random(rand),
                role = cr.npcRoles.random(rand),
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
                name = cr.npcFirsts.random(rand) + " " + cr.npcTitles.random(rand),
                race = LORE_NPC_RACES.random(rand),
                role = cr.npcRoles.random(rand),
                age = (18 + rand.nextInt(70)).toString(),
                appearance = listOf("wind-burnt skin", "a quick smile", "haunted eyes", "ink-stained fingers", "a drifter's posture").random(rand),
                personality = listOf("curious", "world-weary", "mischievous", "kind", "disinterested").random(rand),
                location = home,
                faction = null
            )
        }

        // Primordial + era events
        val primordial = cr.historicalEvents.primordial.shuffled(rand).take(3).map {
            renderPrimordial(it, worldMap.locations.first().name)
        }

        val history = buildList {
            primordial.forEachIndexed { i, text ->
                add(HistoryEntry(era = "primordial", year = -2000 - i * 100, text = text))
            }
            repeat(3) {
                if (factions.isEmpty()) return@repeat
                val f = factions.random(rand)
                val n = cr.npcFirsts.random(rand) + " " + cr.npcTitles.random(rand)
                val loc = worldMap.locations.random(rand).name
                add(HistoryEntry("ancient", -800 + it * 120 + rand.nextInt(40), renderEra(cr.historicalEvents.ancient.random(rand), f.name, n, loc)))
            }
            repeat(4) {
                if (factions.isEmpty()) return@repeat
                val f = factions.random(rand)
                val n = cr.npcFirsts.random(rand) + " " + cr.npcTitles.random(rand)
                val loc = worldMap.locations.random(rand).name
                add(HistoryEntry("medieval", -400 + it * 80 + rand.nextInt(20), renderEra(cr.historicalEvents.medieval.random(rand), f.name, n, loc)))
            }
            if (rand.nextFloat() < 0.7f) {
                repeat(2) {
                    if (factions.isEmpty()) return@repeat
                    val f = factions.random(rand)
                    val n = cr.npcFirsts.random(rand) + " " + cr.npcTitles.random(rand)
                    val loc = worldMap.locations.random(rand).name
                    add(HistoryEntry("dark_age", -150 + it * 40 + rand.nextInt(10), renderEra(cr.historicalEvents.darkAge.random(rand), f.name, n, loc)))
                }
            }
            repeat(3) {
                if (factions.isEmpty()) return@repeat
                val f = factions.random(rand)
                val n = cr.npcFirsts.random(rand) + " " + cr.npcTitles.random(rand)
                val loc = worldMap.locations.random(rand).name
                add(HistoryEntry("recent", -20 + it * 8 + rand.nextInt(4), renderEra(cr.historicalEvents.recent.random(rand), f.name, n, loc)))
            }
        }.sortedBy { it.year }

        val pickedMutations = Mutations.pickForWorld(rand)
        val mutations = pickedMutations.map { "${it.icon} ${it.name} — ${it.desc}" }
        val mutationIds = pickedMutations.map { it.id }

        val rumors = cr.rumors.shuffled(rand).take(6 + rand.nextInt(4))

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
