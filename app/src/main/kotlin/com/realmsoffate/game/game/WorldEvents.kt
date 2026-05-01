package com.realmsoffate.game.game

import com.realmsoffate.game.data.WorldEvent
import com.realmsoffate.game.data.WorldLore
import com.realmsoffate.game.data.WorldMap
import com.realmsoffate.game.data.content.ContentRepository
import com.realmsoffate.game.data.content.WorldEventTemplateDto
import com.realmsoffate.game.util.Templates
import kotlin.random.Random

/**
 * World events — weighted templates loaded from `assets/content/world/world-events.json`.
 * Each JSON entry tags a `needs` predicate and a `context` picker; the registries below
 * resolve those tags to lambdas. The runtime weighted-pick + cooldown ramp is unchanged
 * from the pre-M5b implementation.
 */
object WorldEvents {

    /** Eligibility predicates keyed by the JSON `needs` tag. */
    private val needsRegistry: Map<String, (WorldLore, WorldMap) -> Boolean> = mapOf(
        "always" to { _, _ -> true },
        "factions:>=1" to { l, _ -> l.factions.isNotEmpty() },
        "factions:>=2" to { l, _ -> l.factions.size >= 2 },
        "npcs:>=1" to { l, _ -> l.npcs.isNotEmpty() },
        "loc:>=1" to { _, wm -> wm.locations.isNotEmpty() },
        "loc:typed-cave-dungeon-ruins" to { _, wm ->
            wm.locations.any { it.type in CAVE_DUNGEON_RUINS }
        },
    )

    /** Context pickers keyed by the JSON `context` tag. Each returns the
     *  interpolation map to feed [Templates.interpolate] for the title/prompt. */
    private val contextRegistry: Map<String, (WorldLore, WorldMap, Random) -> Map<String, String>> = mapOf(
        "none" to { _, _, _ -> emptyMap() },

        "faction" to { l, _, r ->
            val f = l.factions.random(r)
            mapOf("f.name" to f.name)
        },

        "faction-pair" to { l, _, r ->
            val f1 = l.factions.random(r)
            val f2 = l.factions.filter { it.name != f1.name }.randomOrNull(r) ?: f1
            mapOf("f1.name" to f1.name, "f2.name" to f2.name)
        },

        "faction-with-loc-discovered-pref" to { l, wm, r ->
            val f = l.factions.random(r)
            val loc = wm.locations.filter { it.discovered }.randomOrNull(r)
                ?: wm.locations.random(r)
            mapOf("f.name" to f.name, "loc.name" to loc.name)
        },

        "faction-with-typed-town-city" to { l, wm, r ->
            val f = l.factions.random(r)
            val loc = wm.locations.filter { it.type in TOWN_CITY }.randomOrNull(r)
                ?: wm.locations.random(r)
            mapOf("f.name" to f.name, "loc.name" to loc.name)
        },

        "faction-pair-with-typed-town-city" to { l, wm, r ->
            val f1 = l.factions.random(r)
            val f2 = l.factions.filter { it.name != f1.name }.randomOrNull(r) ?: f1
            val loc = wm.locations.filter { it.type in TOWN_CITY }.randomOrNull(r)
                ?: wm.locations.random(r)
            mapOf("f1.name" to f1.name, "f2.name" to f2.name, "loc.name" to loc.name)
        },

        "npc" to { l, _, r ->
            val n = l.npcs.random(r)
            mapOf(
                "n.name" to n.name,
                "n.role" to n.role,
                "n.faction" to (n.faction ?: "the crown"),
            )
        },

        "loc" to { _, wm, r ->
            val loc = wm.locations.random(r)
            mapOf("loc.name" to loc.name)
        },

        "loc-typed-cave-dungeon-ruins" to { _, wm, r ->
            val loc = wm.locations.filter { it.type in CAVE_DUNGEON_RUINS }.random(r)
            mapOf("loc.name" to loc.name)
        },
    )

    private val TOWN_CITY = setOf("town", "city")
    private val CAVE_DUNGEON_RUINS = setOf("dungeon", "cave", "ruins")

    /**
     * Chance ramps by +2%/turn since last event, capped by baseRate + maxBoost.
     * Minimum 8-turn cooldown.
     */
    fun maybeGenerate(lore: WorldLore?, wm: WorldMap?, currentTurn: Int, lastEventTurn: Int): WorldEvent? {
        if (lore == null || wm == null) return null
        val sinceLast = currentTurn - lastEventTurn
        if (sinceLast < 8) return null
        val baseChance = 0.05f
        val maxBoost = 0.30f
        val chance = (baseChance + sinceLast * 0.02f).coerceAtMost(baseChance + maxBoost)
        if (Random.nextFloat() > chance) return null

        val templates = ContentRepository.worldEventTemplates
        val eligible = templates.filter { resolveNeeds(it).invoke(lore, wm) }
        if (eligible.isEmpty()) return null

        val total = eligible.sumOf { it.weight }
        var pick = Random.nextInt(total)
        var chosen = eligible.first()
        for (t in eligible) {
            if (pick < t.weight) { chosen = t; break } else pick -= t.weight
        }

        val ctx = resolveContext(chosen).invoke(lore, wm, Random)
        val title = Templates.interpolate(chosen.title, ctx)
        val prompt = Templates.interpolate(chosen.prompt, ctx)
        return WorldEvent(icon = chosen.icon, title = title, text = prompt, prompt = prompt, turn = currentTurn)
    }

    private fun resolveNeeds(t: WorldEventTemplateDto): (WorldLore, WorldMap) -> Boolean =
        needsRegistry[t.needs]
            ?: error("WorldEvents: unknown needs tag '${t.needs}' for id='${t.id}'")

    private fun resolveContext(t: WorldEventTemplateDto): (WorldLore, WorldMap, Random) -> Map<String, String> =
        contextRegistry[t.context]
            ?: error("WorldEvents: unknown context tag '${t.context}' for id='${t.id}'")
}
