package com.realmsoffate.game.debug

import com.realmsoffate.game.game.CombatState
import com.realmsoffate.game.game.DeathSaveState
import com.realmsoffate.game.game.DisplayMessage
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.game.PreRollDisplay
import com.realmsoffate.game.game.Screen
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object DebugSerializer {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun serializeState(vm: GameViewModel): String {
        val state = vm.ui.value
        val screen = vm.screen.value
        return buildJsonObject {
            put("screen", screen.name)
            put("turn", state.turns)
            put("isGenerating", state.isGenerating)
            put("error", state.error)
            put("activeOverlay", activeOverlayName(state))
            put("scene", state.currentScene)
            put("sceneDesc", state.currentSceneDesc)

            // Character
            val ch = state.character
            if (ch != null) {
                putJsonObject("character") {
                    put("name", ch.name)
                    put("race", ch.race)
                    put("class", ch.cls)
                    put("level", ch.level)
                    put("hp", ch.hp)
                    put("maxHp", ch.maxHp)
                    put("ac", ch.ac)
                    put("xp", ch.xp)
                    put("gold", ch.gold)
                    put("morality", state.morality)
                    putJsonObject("abilities") {
                        put("str", ch.abilities.str)
                        put("dex", ch.abilities.dex)
                        put("con", ch.abilities.con)
                        put("int", ch.abilities.int)
                        put("wis", ch.abilities.wis)
                        put("cha", ch.abilities.cha)
                    }
                    putJsonArray("conditions") {
                        ch.conditions.forEach { add(JsonPrimitive(it)) }
                    }
                    putJsonArray("inventory") {
                        ch.inventory.forEach { item -> add(JsonPrimitive(item.name)) }
                    }
                    putJsonObject("factionRep") {
                        state.factionRep.forEach { (faction, rep) -> put(faction, rep) }
                    }
                }
            } else {
                put("character", null as String?)
            }

            // Messages
            put("totalMessages", state.messages.size)
            putJsonArray("recentMessages") {
                state.messages.takeLast(10).forEach { msg -> add(serializeMessage(msg)) }
            }

            // Choices
            putJsonArray("choices") {
                state.currentChoices.forEach { choice ->
                    add(buildJsonObject {
                        put("n", choice.n)
                        put("text", choice.text)
                        put("skill", choice.skill)
                    })
                }
            }

            // Combat
            val combat = state.combat
            if (combat != null) {
                put("combat", serializeCombat(combat))
            } else {
                put("combat", null as String?)
            }

            // NPC log (first 20)
            putJsonArray("npcLog") {
                state.npcLog.take(20).forEach { npc ->
                    add(buildJsonObject {
                        put("id", npc.id)
                        put("name", npc.name)
                        put("relationship", npc.relationship)
                        put("status", npc.status)
                    })
                }
            }

            // Quests
            putJsonArray("quests") {
                state.quests.forEach { quest ->
                    add(buildJsonObject {
                        put("id", quest.id)
                        put("title", quest.title)
                        put("status", quest.status)
                        put("type", quest.type)
                    })
                }
            }
        }.toString()
    }

    fun serializeOverlay(vm: GameViewModel): String {
        val state = vm.ui.value
        val overlayName = activeOverlayName(state)

        return buildJsonObject {
            put("activeOverlay", overlayName)

            when {
                state.preRoll != null -> {
                    val pr = state.preRoll
                    putJsonObject("preRoll") {
                        put("action", pr.action)
                        put("skill", pr.skill)
                        put("ability", pr.ability)
                        put("roll", pr.roll)
                        put("mod", pr.mod)
                        put("prof", pr.prof)
                        put("total", pr.total)
                        put("crit", pr.crit)
                    }
                    putJsonArray("availableActions") {
                        add(JsonPrimitive("continue"))
                        add(JsonPrimitive("cancel"))
                    }
                }
                state.deathSave != null -> {
                    val ds = state.deathSave
                    putJsonObject("deathSave") {
                        put("successes", ds.successes)
                        put("failures", ds.failures)
                        put("stable", ds.stable)
                        put("dead", ds.dead)
                        putJsonArray("rolls") {
                            ds.rolls.forEach { add(JsonPrimitive(it)) }
                        }
                    }
                    putJsonArray("availableActions") {
                        add(JsonPrimitive("rollDeathSave"))
                    }
                }
                state.combat != null -> {
                    put("combat", serializeCombat(state.combat))
                    putJsonArray("availableActions") {
                        add(JsonPrimitive("attack"))
                        add(JsonPrimitive("flee"))
                        add(JsonPrimitive("useItem"))
                    }
                }
                state.travelState != null -> {
                    val ts = state.travelState
                    putJsonObject("travel") {
                        put("destId", ts.destId)
                        put("destName", ts.destName)
                        put("totalLeagues", ts.totalLeagues)
                        put("leaguesTraveled", ts.leaguesTraveled)
                    }
                    putJsonArray("availableActions") {
                        add(JsonPrimitive("continueTravel"))
                        add(JsonPrimitive("camp"))
                    }
                }
                else -> {
                    putJsonArray("availableActions") {
                        state.currentChoices.forEach { choice ->
                            add(JsonPrimitive(choice.n.toString()))
                        }
                        if (state.currentChoices.isEmpty()) {
                            add(JsonPrimitive("freeform"))
                        }
                    }
                }
            }
        }.toString()
    }

    fun computeDiff(old: GameUiState, new: GameUiState): String {
        return buildJsonObject {
            // HP
            val oldHp = old.character?.hp
            val newHp = new.character?.hp
            if (oldHp != newHp) {
                putJsonObject("hp") {
                    put("old", oldHp)
                    put("new", newHp)
                    if (oldHp != null && newHp != null) put("delta", newHp - oldHp)
                }
            }

            // Gold
            val oldGold = old.character?.gold
            val newGold = new.character?.gold
            if (oldGold != newGold) {
                putJsonObject("gold") {
                    put("old", oldGold)
                    put("new", newGold)
                    if (oldGold != null && newGold != null) put("delta", newGold - oldGold)
                }
            }

            // Level
            val oldLevel = old.character?.level
            val newLevel = new.character?.level
            if (oldLevel != newLevel) {
                putJsonObject("level") {
                    put("old", oldLevel)
                    put("new", newLevel)
                }
            }

            // XP
            val oldXp = old.character?.xp
            val newXp = new.character?.xp
            if (oldXp != newXp) {
                putJsonObject("xp") {
                    put("old", oldXp)
                    put("new", newXp)
                    if (oldXp != null && newXp != null) put("delta", newXp - oldXp)
                }
            }

            // Turn
            if (old.turns != new.turns) {
                putJsonObject("turn") {
                    put("old", old.turns)
                    put("new", new.turns)
                }
            }

            // Scene
            if (old.currentScene != new.currentScene) {
                putJsonObject("scene") {
                    put("old", old.currentScene)
                    put("new", new.currentScene)
                }
            }

            // isGenerating
            if (old.isGenerating != new.isGenerating) {
                putJsonObject("isGenerating") {
                    put("old", old.isGenerating)
                    put("new", new.isGenerating)
                }
            }

            // New messages
            if (new.messages.size > old.messages.size) {
                putJsonArray("newMessages") {
                    new.messages.drop(old.messages.size).forEach { msg ->
                        add(serializeMessage(msg))
                    }
                }
            }

            // Combat state changes
            val oldCombat = old.combat
            val newCombat = new.combat
            when {
                oldCombat == null && newCombat != null -> {
                    put("combatStarted", serializeCombat(newCombat))
                }
                oldCombat != null && newCombat == null -> {
                    put("combatEnded", JsonPrimitive(true))
                }
                oldCombat != null && newCombat != null && oldCombat.round != newCombat.round -> {
                    putJsonObject("combatRound") {
                        put("old", oldCombat.round)
                        put("new", newCombat.round)
                    }
                }
            }

            // Overlay changes
            val oldOverlay = activeOverlayName(old)
            val newOverlay = activeOverlayName(new)
            if (oldOverlay != newOverlay) {
                putJsonObject("overlay") {
                    put("old", oldOverlay)
                    put("new", newOverlay)
                }
            }
        }.toString()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun activeOverlayName(state: GameUiState): String? = when {
        state.preRoll != null -> "preRoll"
        state.deathSave != null -> "deathSave"
        state.combat != null -> "combat"
        state.travelState != null -> "travel"
        else -> null
    }

    private fun serializeCombat(combat: CombatState): JsonObject = buildJsonObject {
        put("round", combat.round)
        put("activeIndex", combat.activeIndex)
        putJsonArray("order") {
            combat.order.forEach { combatant ->
                add(buildJsonObject {
                    put("name", combatant.name)
                    put("hp", combatant.hp)
                    put("maxHp", combatant.maxHp)
                    put("initiative", combatant.initiative)
                    put("isPlayer", combatant.isPlayer)
                })
            }
        }
    }

    private fun serializeMessage(msg: DisplayMessage): JsonObject = buildJsonObject {
        when (msg) {
            is DisplayMessage.Player -> {
                put("type", "player")
                put("text", msg.text)
            }
            is DisplayMessage.Narration -> {
                put("type", "narration")
                put("text", msg.text)
                put("scene", msg.scene)
                put("hpBefore", msg.hpBefore)
                put("hpAfter", msg.hpAfter)
                put("xpGained", msg.xpGained)
                put("goldBefore", msg.goldBefore)
                put("goldAfter", msg.goldAfter)
            }
            is DisplayMessage.Event -> {
                put("type", "event")
                put("icon", msg.icon)
                put("title", msg.title)
                put("text", msg.text)
            }
            is DisplayMessage.System -> {
                put("type", "system")
                put("text", msg.text)
            }
        }
    }
}
