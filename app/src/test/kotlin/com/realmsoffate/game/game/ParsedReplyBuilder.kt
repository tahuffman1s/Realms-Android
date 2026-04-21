package com.realmsoffate.game.game

import com.realmsoffate.game.data.CheckResult
import com.realmsoffate.game.data.Choice
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.NarrationSegmentData
import com.realmsoffate.game.data.ParseSource
import com.realmsoffate.game.data.ParsedReply
import com.realmsoffate.game.data.PartyCompanion
import com.realmsoffate.game.data.Quest

/**
 * Fluent builder for [ParsedReply]. All fields default to "nothing happened"
 * values so each test only sets the fields it cares about.
 *
 * Usage:
 * ```
 * val parsed = ParsedReplyBuilder()
 *     .scene("town", "A quiet market")
 *     .narration("You enter the market.")
 *     .damage(5)
 *     .addNpcMet(LogNpc(id = "vesper", name = "Vesper", ...))
 *     .build()
 * ```
 */
class ParsedReplyBuilder {
    private var scene: String = "default"
    private var sceneDesc: String = ""
    private var narration: String = "The world turns."
    private var choices: MutableList<Choice> = mutableListOf()
    private var damage: Int = 0
    private var heal: Int = 0
    private var xp: Int = 0
    private var goldGained: Int = 0
    private var goldLost: Int = 0
    private var itemsGained: MutableList<Item> = mutableListOf()
    private var checks: MutableList<CheckResult> = mutableListOf()
    private var npcsMet: MutableList<LogNpc> = mutableListOf()
    private var questStarts: MutableList<Quest> = mutableListOf()
    private var questUpdates: MutableList<Pair<String, String>> = mutableListOf()
    private var questComplete: MutableList<String> = mutableListOf()
    private var questFails: MutableList<String> = mutableListOf()
    private var shops: MutableList<Pair<String, Map<String, Int>>> = mutableListOf()
    private var travelTo: String? = null
    private var partyJoins: MutableList<PartyCompanion> = mutableListOf()
    private var timeOfDay: String? = null
    private var moralDelta: Int = 0
    private var repDeltas: MutableList<Pair<String, Int>> = mutableListOf()
    private var worldEventHook: String? = null
    private var dialogues: Map<String, List<Pair<Int, String>>> = emptyMap()
    private var conditionsAdded: MutableList<String> = mutableListOf()
    private var conditionsRemoved: MutableList<String> = mutableListOf()
    private var itemsRemoved: MutableList<String> = mutableListOf()
    private var partyLeaves: MutableList<String> = mutableListOf()
    private var enemies: MutableList<Triple<String, Int, Int>> = mutableListOf()
    private var factionUpdates: MutableList<Triple<String, String, String>> = mutableListOf()
    private var npcDeaths: MutableList<String> = mutableListOf()
    private var npcUpdates: MutableList<Triple<String, String, String>> = mutableListOf()
    private var loreEntries: MutableList<String> = mutableListOf()
    private var npcQuotes: MutableList<Pair<String, String>> = mutableListOf()
    private var narratorProse: MutableList<String> = mutableListOf()
    private var narratorAsides: MutableList<String> = mutableListOf()
    private var playerDialogs: MutableList<String> = mutableListOf()
    private var npcDialogs: MutableList<Pair<String, String>> = mutableListOf()
    private var playerActions: MutableList<String> = mutableListOf()
    private var npcActions: MutableList<Pair<String, String>> = mutableListOf()
    private var segments: MutableList<NarrationSegmentData> = mutableListOf()
    private var source: ParseSource = ParseSource.REGEX_FALLBACK

    fun scene(scene: String, desc: String = "") = apply { this.scene = scene; this.sceneDesc = desc }
    fun narration(text: String) = apply { this.narration = text }
    fun damage(n: Int) = apply { this.damage = n }
    fun heal(n: Int) = apply { this.heal = n }
    fun xp(n: Int) = apply { this.xp = n }
    fun goldGained(n: Int) = apply { this.goldGained = n }
    fun goldLost(n: Int) = apply { this.goldLost = n }
    fun moralDelta(n: Int) = apply { this.moralDelta = n }
    fun travelTo(dest: String) = apply { this.travelTo = dest }
    fun timeOfDay(t: String) = apply { this.timeOfDay = t }
    fun worldEventHook(hook: String) = apply { this.worldEventHook = hook }

    fun addItem(item: Item) = apply { itemsGained.add(item) }
    fun removeItem(name: String) = apply { itemsRemoved.add(name) }
    fun addCondition(c: String) = apply { conditionsAdded.add(c) }
    fun removeCondition(c: String) = apply { conditionsRemoved.add(c) }
    fun addLore(entry: String) = apply { loreEntries.add(entry) }

    fun addNpcMet(npc: LogNpc) = apply { npcsMet.add(npc) }
    fun addNpcUpdate(id: String, field: String, value: String) = apply {
        npcUpdates.add(Triple(id, field, value))
    }
    fun addNpcDeath(id: String) = apply { npcDeaths.add(id) }
    fun addNpcQuote(ref: String, quote: String) = apply { npcQuotes.add(Pair(ref, quote)) }
    fun addNpcDialog(ref: String, quote: String) = apply { npcDialogs.add(Pair(ref, quote)) }
    fun addNpcAction(ref: String, action: String) = apply { npcActions.add(Pair(ref, action)) }

    fun addQuestStart(quest: Quest) = apply { questStarts.add(quest) }
    fun addQuestUpdate(title: String, objective: String) = apply {
        questUpdates.add(Pair(title, objective))
    }
    fun addQuestComplete(title: String) = apply { questComplete.add(title) }
    fun addQuestFail(title: String) = apply { questFails.add(title) }

    fun addShop(merchantName: String, items: Map<String, Int>) = apply {
        shops.add(Pair(merchantName, items))
    }

    fun addFactionUpdate(ref: String, field: String, value: String) = apply {
        factionUpdates.add(Triple(ref, field, value))
    }
    fun addRepDelta(faction: String, delta: Int) = apply { repDeltas.add(Pair(faction, delta)) }

    fun addPartyJoin(companion: PartyCompanion) = apply { partyJoins.add(companion) }
    fun addPartyLeave(name: String) = apply { partyLeaves.add(name) }

    fun addEnemy(name: String, hp: Int, maxHp: Int) = apply { enemies.add(Triple(name, hp, maxHp)) }

    fun addCheck(skill: String, ability: String, dc: Int, passed: Boolean, total: Int) = apply {
        checks.add(CheckResult(skill, ability, dc, passed, total))
    }

    fun addChoice(n: Int, text: String, skill: String) = apply {
        choices.add(Choice(n, text, skill))
    }

    fun build(): ParsedReply = ParsedReply(
        scene = scene,
        sceneDesc = sceneDesc,
        narration = narration,
        choices = choices.toList(),
        damage = damage,
        heal = heal,
        xp = xp,
        goldGained = goldGained,
        goldLost = goldLost,
        itemsGained = itemsGained.toList(),
        checks = checks.toList(),
        npcsMet = npcsMet.toList(),
        questStarts = questStarts.toList(),
        questUpdates = questUpdates.toList(),
        questComplete = questComplete.toList(),
        questFails = questFails.toList(),
        shops = shops.toList(),
        travelTo = travelTo,
        partyJoins = partyJoins.toList(),
        timeOfDay = timeOfDay,
        moralDelta = moralDelta,
        repDeltas = repDeltas.toList(),
        worldEventHook = worldEventHook,
        dialogues = dialogues,
        conditionsAdded = conditionsAdded.toList(),
        conditionsRemoved = conditionsRemoved.toList(),
        itemsRemoved = itemsRemoved.toList(),
        partyLeaves = partyLeaves.toList(),
        enemies = enemies.toList(),
        factionUpdates = factionUpdates.toList(),
        npcDeaths = npcDeaths.toList(),
        npcUpdates = npcUpdates.toList(),
        loreEntries = loreEntries.toList(),
        npcQuotes = npcQuotes.toList(),
        narratorProse = narratorProse.toList(),
        narratorAsides = narratorAsides.toList(),
        playerDialogs = playerDialogs.toList(),
        npcDialogs = npcDialogs.toList(),
        playerActions = playerActions.toList(),
        npcActions = npcActions.toList(),
        segments = segments.toList(),
        source = source
    )
}
