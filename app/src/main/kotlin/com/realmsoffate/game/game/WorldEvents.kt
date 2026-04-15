package com.realmsoffate.game.game

import com.realmsoffate.game.data.WorldEvent
import com.realmsoffate.game.data.WorldLore
import com.realmsoffate.game.data.WorldMap
import kotlin.random.Random

/**
 * World events — 19 weighted templates across faction / NPC / location /
 * supernatural / economic / mutation-specific categories.
 *
 * Contextual triggers (travel/combat/time/quest) are surfaced by the caller
 * via a `baseChance` boost; the cooldown (min 8 turns) + +2%/turn since last
 * event ramp are built in.
 */
private data class EventTemplate(
    val weight: Int,
    val needs: (WorldLore, WorldMap) -> Boolean,
    val gen: (WorldLore, WorldMap, Int) -> Triple<String, String, String>
)

private val templates = listOf(
    // ---- FACTION (7)
    EventTemplate(3, { l, _ -> l.factions.size >= 2 }) { l, wm, _ ->
        val f = l.factions.random()
        val loc = wm.locations.filter { it.discovered }.randomOrNull() ?: wm.locations.random()
        Triple("⚔️", "${f.name} Mobilizes",
            "BREAKING: ${f.name} is mobilizing troops near ${loc.name}. NPC reactions should reflect fear, opportunity, or allegiance. Soldiers and supply wagons visible.")
    },
    EventTemplate(3, { l, _ -> l.factions.size >= 2 }) { l, _, _ ->
        val f1 = l.factions.random()
        val f2 = l.factions.filter { it.name != f1.name }.randomOrNull() ?: f1
        Triple("🤝", "Unexpected Alliance",
            "BREAKING: ${f1.name} and ${f2.name} have declared a truce. NPCs are suspicious. Alliances shift power dynamics.")
    },
    EventTemplate(2, { l, _ -> l.factions.isNotEmpty() }) { l, _, _ ->
        val f = l.factions.random()
        Triple("💀", "Ruler Assassinated",
            "BREAKING: The ruler of ${f.name} is dead — assassinated. Power vacuum. Factions scramble. NPCs react with shock, fear, or ambition. THIS CHANGES EVERYTHING: faction prices double, guards are distracted (easier stealth), and a power-grab quest emerges.")
    },
    EventTemplate(2, { l, _ -> l.factions.isNotEmpty() }) { l, _, _ ->
        val f = l.factions.random()
        Triple("🏴", "${f.name} Demands Tribute",
            "BREAKING: ${f.name} is demanding tribute from settlements. Merchants complain. Guards enforce. Resistance is brewing. GAMEPLAY EFFECT: all NPC vendors now charge a 25% surcharge on goods. Refusing to pay the tribute collector triggers combat. A resistance contact quietly offers a quest to sabotage the collection effort.")
    },
    EventTemplate(2, { l, _ -> l.factions.size >= 2 }) { l, wm, _ ->
        val f1 = l.factions.random()
        val f2 = l.factions.filter { it.name != f1.name }.randomOrNull() ?: f1
        val loc = wm.locations.filter { it.type in setOf("town", "city") }.randomOrNull() ?: wm.locations.random()
        Triple("🔥", "Border Clash near ${loc.name}",
            "BREAKING: ${f1.name} and ${f2.name} fought a skirmish near ${loc.name}. Refugees and wounded fill the roads. Pick a side or walk between. GAMEPLAY EFFECT: travel to ${loc.name} costs double in supplies. Wounded soldiers offer equipment at a discount. Siding with either faction locks out that faction's rival merchants for 6 turns.")
    },
    EventTemplate(2, { l, _ -> l.factions.isNotEmpty() }) { l, wm, _ ->
        val f = l.factions.random()
        val loc = wm.locations.filter { it.type in setOf("town", "city") }.randomOrNull() ?: wm.locations.random()
        Triple("📜", "${f.name} Issues Edict",
            "BREAKING: ${f.name} has issued a sweeping new edict affecting ${loc.name}. NPCs whisper. Some obey. Some prepare to resist.")
    },
    EventTemplate(2, { l, _ -> l.factions.isNotEmpty() }) { l, _, _ ->
        val f = l.factions.random()
        Triple("🕯️", "Heretic Burning",
            "BREAKING: ${f.name} has burned a heretic in the public square. The smoke still rises. Sympathizers gather in shadows.")
    },

    // ---- NPC (4)
    EventTemplate(2, { l, _ -> l.npcs.isNotEmpty() }) { l, _, _ ->
        val n = l.npcs.random()
        Triple("👤", "${n.name} Has Vanished",
            "BREAKING: ${n.name} the ${n.role} vanished without a trace. Their quarters show signs of a struggle. Rumors fly. GAMEPLAY EFFECT: any quest tied to ${n.name} is now blocked until they are found. A new investigation quest opens immediately. Their contacts offer a reward — and someone clearly doesn't want them found.")
    },
    EventTemplate(2, { l, _ -> l.npcs.isNotEmpty() }) { l, _, _ ->
        val n = l.npcs.random()
        Triple("🗣️", "${n.name} Speaks Out",
            "BREAKING: ${n.name} the ${n.role} has publicly denounced ${n.faction ?: "the crown"}. Consequences are coming. Pick a side.")
    },
    EventTemplate(2, { l, _ -> l.npcs.isNotEmpty() }) { l, _, _ ->
        val n = l.npcs.random()
        Triple("💍", "${n.name}'s Betrothal",
            "BREAKING: ${n.name} the ${n.role} has been betrothed. The alliance it seals shifts politics. Gifts and knives both travel fast.")
    },
    EventTemplate(2, { l, _ -> l.npcs.isNotEmpty() }) { l, _, _ ->
        val n = l.npcs.random()
        Triple("⚰️", "${n.name} Is Dead",
            "BREAKING: ${n.name} the ${n.role} has died — cause disputed. Mourners gather. Suspects flee. Inheritance or revenge drives new quests.")
    },

    // ---- LOCATION (4)
    EventTemplate(2, { _, wm -> wm.locations.isNotEmpty() }) { _, wm, _ ->
        val loc = wm.locations.random()
        Triple("🔥", "Fire in ${loc.name}",
            "BREAKING: Fire has swept part of ${loc.name}. Smoke on the horizon. Refugees move along the roads. Arson is suspected. GAMEPLAY EFFECT: damaged buildings mean fewer merchants, displaced NPCs offer desperate quests, and fire damage lingers in scene descriptions for 5+ turns.")
    },
    EventTemplate(1, { _, wm -> wm.locations.isNotEmpty() }) { _, wm, _ ->
        val loc = wm.locations.random()
        Triple("🎪", "Festival in ${loc.name}",
            "BREAKING: A festival has begun in ${loc.name}. Crowds, music, merchants, pickpockets. Rare goods and strangers in the streets.")
    },
    EventTemplate(2, { _, wm -> wm.locations.any { it.type in setOf("dungeon", "cave", "ruins") } }) { _, wm, _ ->
        val loc = wm.locations.filter { it.type in setOf("dungeon", "cave", "ruins") }.random()
        Triple("👹", "Monsters Spill From ${loc.name}",
            "BREAKING: Creatures have been sighted pouring from ${loc.name}. Hunters are gathering. Bounties rise. Something deeper stirred. GAMEPLAY EFFECT: roads near ${loc.name} trigger random encounters for the next 5 turns. Hunter NPCs pay premium for monster parts. A bounty board quest to clear the source is now active.")
    },
    EventTemplate(2, { _, wm -> wm.locations.isNotEmpty() }) { _, wm, _ ->
        val loc = wm.locations.random()
        Triple("📜", "Bounty Posted at ${loc.name}",
            "BREAKING: A substantial bounty has been posted at ${loc.name}. Hunters and opportunists move in. The target knows they are prey.")
    },

    // ---- SUPERNATURAL (3)
    EventTemplate(1, { _, _ -> true }) { _, _, _ ->
        Triple("🌪️", "Storm Sweeps the Land",
            "BREAKING: A freak storm is battering the countryside. Roads flood. Lightning strikes. Travel is treacherous.")
    },
    EventTemplate(1, { _, _ -> true }) { _, _, _ ->
        Triple("🌑", "Unnatural Darkness",
            "BREAKING: For three hours at noon, the sky went dark. Priests pray. Astrologers argue. Something beyond the Veil took notice.")
    },
    EventTemplate(1, { _, wm -> wm.locations.isNotEmpty() }) { _, wm, _ ->
        val loc = wm.locations.random()
        Triple("🌀", "Planar Rift at ${loc.name}",
            "BREAKING: A tear in reality has opened near ${loc.name}. Things slip through — most shouldn't. Mages rush to study or seal it.")
    },

    // ---- ECONOMIC (1)
    EventTemplate(2, { _, _ -> true }) { _, _, _ ->
        Triple("💰", "Trade Route Collapsed",
            "BREAKING: A key trade route has failed — raiders, rockslide, or political fiat. Prices spike. Merchants weep. Smugglers profit. GAMEPLAY EFFECT: all shop prices increase by 50% until resolved. New smuggler NPCs appear with black-market goods. A quest to reopen the route becomes available.")
    }
)

object WorldEvents {
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
        val eligible = templates.filter { it.needs(lore, wm) }
        if (eligible.isEmpty()) return null
        val total = eligible.sumOf { it.weight }
        var pick = Random.nextInt(total)
        var chosen = eligible.first()
        for (t in eligible) {
            if (pick < t.weight) { chosen = t; break } else pick -= t.weight
        }
        val (icon, title, prompt) = chosen.gen(lore, wm, currentTurn)
        return WorldEvent(icon = icon, title = title, text = prompt, prompt = prompt, turn = currentTurn)
    }
}
