package com.realmsoffate.game.data

import kotlinx.serialization.Serializable

@Serializable
data class Abilities(
    var str: Int = 10, var dex: Int = 10, var con: Int = 10,
    var int: Int = 10, var wis: Int = 10, var cha: Int = 10
) {
    fun mod(score: Int) = (score - 10) / 2
    val strMod get() = mod(str)
    val dexMod get() = mod(dex)
    val conMod get() = mod(con)
    val intMod get() = mod(int)
    val wisMod get() = mod(wis)
    val chaMod get() = mod(cha)
    fun byName(n: String) = when (n.uppercase()) {
        "STR" -> str; "DEX" -> dex; "CON" -> con
        "INT" -> int; "WIS" -> wis; "CHA" -> cha
        else -> 10
    }
    fun modByName(n: String) = mod(byName(n))
}

@Serializable
data class Item(
    val name: String,
    val desc: String = "",
    val type: String = "item",
    val rarity: String = "common",
    var qty: Int = 1,
    var equipped: Boolean = false,
    val damage: String? = null,
    val ac: Int? = null
)

@Serializable
data class Backstory(
    val origin: String,
    val motivation: String,
    val flaw: String,
    val bond: String,
    val darkSecret: String,
    val lostItem: String,
    val personalEnemy: String,
    val prophecy: String? = null,
    val promptText: String
)

@Serializable
data class CharacterAppearance(
    /** Display name / hex of skin tone swatch (9 presets). */
    var skinTone: String = "#C49C7C",
    /** Hex of hair color (8 presets). */
    var hairColor: String = "#3A2A1A",
    /** Human-readable hair style token. */
    var hairStyle: String = "Short",
    /** Body build: Lean / Average / Muscular / Stocky / Hulking. */
    var build: String = "Average",
    /** Self-identified gender label. */
    var gender: String = "Unspecified",
    /** Life stage: Young / Adult / Mature / Elder — colors voice/skin/NPC assumptions. */
    var ageBand: String = "Adult"
)

@Serializable
data class Character(
    var name: String,
    var race: String,
    var cls: String,
    var level: Int = 1,
    var xp: Int = 0,
    var hp: Int = 10,
    var maxHp: Int = 10,
    var ac: Int = 10,
    var gold: Int = 25,
    val abilities: Abilities = Abilities(),
    val inventory: MutableList<Item> = mutableListOf(),
    val knownSpells: MutableList<String> = mutableListOf(),
    val spellSlots: MutableMap<Int, Int> = mutableMapOf(),
    val maxSpellSlots: MutableMap<Int, Int> = mutableMapOf(),
    var backstory: Backstory? = null,
    var racialPhysique: String = "",
    var appearance: CharacterAppearance = CharacterAppearance(),
    /** Per-faction local currency holdings (name → amount). Gold lives in `gold`. */
    val currencyBalances: MutableMap<String, Int> = mutableMapOf(),
    /** Active conditions — Poisoned, Blessed, Cursed, Charmed, Frightened, etc. */
    val conditions: MutableList<String> = mutableListOf()
) {
    val proficiency: Int get() = when (level) {
        in 1..4 -> 2; in 5..8 -> 3; in 9..12 -> 4; in 13..16 -> 5; else -> 6
    }
}

@Serializable
data class MapLocation(
    val id: Int,
    val name: String,
    val type: String,
    val icon: String,
    val x: Int,
    val y: Int,
    var discovered: Boolean = false
)

@Serializable
data class MapRoad(val from: Int, val to: Int, val dist: Int)

@Serializable
data class Terrain(val type: String, val x: Float, val y: Float, val size: Float, val rot: Float = 0f)

@Serializable
data class Lake(val x: Float, val y: Float, val rx: Float, val ry: Float, val rot: Float)

@Serializable
data class RiverPoint(val x: Float, val y: Float)

@Serializable
data class WorldMap(
    val locations: MutableList<MapLocation>,
    val roads: List<MapRoad>,
    val startId: Int,
    val terrain: List<Terrain>,
    val rivers: List<List<RiverPoint>>,
    val lakes: List<Lake>,
    val width: Int = 600,
    val height: Int = 500
)

@Serializable
data class PlayerPos(val x: Float, val y: Float)

@Serializable
data class PastRuler(
    val name: String,
    val yearsAgo: Int,
    val fate: String
)

@Serializable
data class GovernmentInfo(
    val form: String = "monarchy",
    val ruler: String = "",
    val capital: String = "",
    val rulerTrait: String = "",
    val yearsInPower: Int = 0,
    val dynasty: String = "",
    val succession: String = "",
    val pastRulers: List<PastRuler> = emptyList()
)

@Serializable
data class EconomyInfo(
    /** Level label: Thriving / Prosperous / Stable / Strained / Impoverished / Collapsed / Hoarded / War Economy / Black Market / Boom & Bust. */
    val level: String = "Stable",
    /** 0..5 "wealth bars" for the UI's 5-bar indicator. */
    val wealth: Int = 3,
    val description: String = "",
    val exports: List<String> = emptyList(),
    val imports: List<String> = emptyList(),
    val tax: String = ""
)

@Serializable
data class Faction(
    val name: String,
    val type: String,
    val description: String,
    val baseLoc: String,
    val color: String? = null,
    val government: GovernmentInfo? = null,
    val economy: EconomyInfo? = null,
    val population: String = "",
    val mood: String = "",
    val disposition: String = "",
    val goal: String = "",
    val currency: String = "gold"
)

@Serializable
data class LoreNpc(
    val name: String,
    val race: String,
    val role: String,
    val age: String = "",
    val appearance: String = "",
    val personality: String = "",
    val location: String = "",
    val faction: String? = null
)

@Serializable
data class HistoryEntry(
    /** One of: primordial / ancient / medieval / dark_age / recent. */
    val era: String,
    /** In-world year (approximate), descending so recent events have the largest value. */
    val year: Int,
    /** Rendered markdown text. */
    val text: String
)

@Serializable
data class WorldLore(
    val factions: List<Faction>,
    val npcs: List<LoreNpc>,
    val primordial: List<String>,
    val mutations: List<String>,
    /** Mutation ids for AI prompt lookup via Mutations.find(id). */
    val mutationIds: List<String> = emptyList(),
    /** Pre-generated rumors surfaced in the Lore panel's Rumors tab. */
    val rumors: List<String> = emptyList(),
    /** World name shown in the Lore panel header and death screen. */
    val worldName: String = "",
    /** Current era label shown under the world name. */
    val era: String = "",
    /** Chronicle of notable events across all 5 eras. */
    val history: List<HistoryEntry> = emptyList()
)

@Serializable
data class WorldEvent(
    val icon: String,
    val title: String,
    val text: String,
    val prompt: String,
    val turn: Int
)

@Serializable
data class LogNpc(
    val name: String,
    val race: String = "",
    val role: String = "",
    val age: String = "",
    var relationship: String = "neutral",
    val appearance: String = "",
    val personality: String = "",
    val thoughts: String = "",
    val faction: String? = null,
    var lastLocation: String = "",
    val metTurn: Int,
    var lastSeenTurn: Int,
    val dialogueHistory: MutableList<String> = mutableListOf(),
    var relationshipNote: String = ""
)

@Serializable
data class PartyCompanion(
    val name: String,
    val race: String = "",
    val role: String = "",
    var level: Int = 1,
    var hp: Int = 10,
    var maxHp: Int = 10,
    val appearance: String = "",
    val personality: String = "",
    val faction: String? = null,
    val homeLocation: String = "",
    val joinedTurn: Int,
    val age: String = ""
)

@Serializable
data class Quest(
    val id: String,
    val title: String,
    val type: String = "side",
    val desc: String,
    val giver: String,
    val location: String,
    val objectives: MutableList<String>,
    val completed: MutableList<Boolean> = MutableList(objectives.size) { false },
    val reward: String,
    var status: String = "active",
    val turnStarted: Int,
    var turnCompleted: Int? = null
)

@Serializable
data class ChatMsg(val role: String, val content: String)

@Serializable
data class Dice(val roll: Int, val skill: String = "", val ability: String = "")

@Serializable
data class MerchantStock(val items: MutableMap<String, Int>)

@Serializable
data class SaveData(
    val version: Int = 1,
    val character: Character,
    val morality: Int,
    val factionRep: Map<String, Int>,
    val worldMap: WorldMap,
    val currentLoc: Int,
    val playerPos: PlayerPos?,
    val worldLore: WorldLore?,
    val worldEvents: List<WorldEvent>,
    val lastEventTurn: Int,
    val npcLog: List<LogNpc>,
    val party: List<PartyCompanion>,
    val quests: List<Quest>,
    val hotbar: List<String?>,
    val timeOfDay: String,
    val history: List<ChatMsg>,
    val turns: Int,
    val scene: String,
    val savedAt: String
)

@Serializable
data class Choice(val n: Int, val text: String, val skill: String)
