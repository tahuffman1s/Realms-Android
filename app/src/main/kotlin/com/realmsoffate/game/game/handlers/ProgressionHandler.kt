package com.realmsoffate.game.game.handlers

import com.realmsoffate.game.data.deepCopy
import com.realmsoffate.game.game.Classes
import com.realmsoffate.game.game.DisplayMessage
import com.realmsoffate.game.game.Feats
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.GameViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProgressionHandler(
    private val ui: MutableStateFlow<GameUiState>,
    private val _pendingLevelUp: MutableStateFlow<Int?>,
    private val _pendingStatPoints: MutableStateFlow<Int>,
    private val _pendingFeat: MutableStateFlow<Boolean>
) {

    val pendingLevelUpFlow: StateFlow<Int?> = _pendingLevelUp.asStateFlow()
    var pendingLevelUp: Int?
        get() = _pendingLevelUp.value
        set(v) { _pendingLevelUp.value = v }

    val pendingStatPointsFlow: StateFlow<Int> = _pendingStatPoints.asStateFlow()

    val pendingFeatFlow: StateFlow<Boolean> = _pendingFeat.asStateFlow()

    fun dismissLevelUp() { _pendingLevelUp.value = null }

    fun assignStatPoint(stat: String) {
        val pts = _pendingStatPoints.value
        if (pts <= 0) return
        val s = ui.value
        val ch = s.character?.deepCopy() ?: return
        when (stat.uppercase()) {
            "STR" -> ch.abilities.str += 1
            "DEX" -> ch.abilities.dex += 1
            "CON" -> { ch.abilities.con += 1; ch.maxHp += 1; ch.hp += 1 }
            "INT" -> ch.abilities.int += 1
            "WIS" -> ch.abilities.wis += 1
            "CHA" -> ch.abilities.cha += 1
            else -> return
        }
        _pendingStatPoints.value = pts - 1
        ui.value = s.copy(character = ch)
    }

    fun selectFeat(featName: String) {
        val s = ui.value
        val ch = s.character?.deepCopy() ?: return
        val feat = Feats.find(featName) ?: return
        feat.apply(ch)
        ch.feats.add(featName)
        _pendingFeat.value = false
        ui.value = s.copy(character = ch, messages = s.messages + DisplayMessage.System("Feat acquired: $featName"))
    }

    fun dismissFeat() { _pendingFeat.value = false }

    /**
     * One-shot cheat: jump the current character from current level to 20.
     * - HP gains: class hit die + CON mod per level (deterministic max, matches
     *   [com.realmsoffate.game.game.reducers.CharacterReducer]).
     * - Stat points auto-assigned to class primary on non-feat levels (+2 per level;
     *   CON also bumps maxHp).
     * - Grants "Tough" once at the final level so its retroactive HP bonus
     *   (ch.level * 2) reflects the L20 state.
     * - XP set to level-20 threshold.
     *
     * No-ops if the character is already level 20 or if mid-combat.
     */
    fun applyOverprepared() {
        val s = ui.value
        if (s.combat != null) {
            ui.value = s.copy(messages = s.messages + DisplayMessage.System("Cannot level up during combat."))
            return
        }
        val ch = s.character?.deepCopy() ?: return
        if (ch.level >= 20) return

        val clsDef = Classes.find(ch.cls)
        val hitDie = clsDef?.hitDie ?: 8
        val conMod = ch.abilities.conMod
        val primary = clsDef?.primary ?: "STR"

        for (target in (ch.level + 1)..20) {
            ch.level = target
            val hpGain = (hitDie + conMod).coerceAtLeast(1)
            ch.maxHp += hpGain
            if (target % 4 != 0) {
                // Non-feat level: +2 stat points auto-assigned to class primary.
                when (primary.uppercase()) {
                    "STR" -> ch.abilities.str += 2
                    "DEX" -> ch.abilities.dex += 2
                    "CON" -> { ch.abilities.con += 2; ch.maxHp += 2 }
                    "INT" -> ch.abilities.int += 2
                    "WIS" -> ch.abilities.wis += 2
                    "CHA" -> ch.abilities.cha += 2
                }
            }
            // feat levels (target % 4 == 0): deferred to single grant after loop
        }
        // Grant "Tough" once at final level so its retroactive ch.level * 2 HP bonus
        // reflects the L20 state.
        if (!ch.feats.any { it.equals("Tough", ignoreCase = true) }) {
            Feats.find("Tough")?.let { feat ->
                feat.apply(ch)
                ch.feats.add("Tough")
            }
        }
        ch.hp = ch.maxHp
        ch.xp = GameViewModel.levelThreshold(20)
        _pendingLevelUp.value = null
        _pendingStatPoints.value = 0
        _pendingFeat.value = false
        ui.value = s.copy(
            character = ch,
            messages = s.messages + DisplayMessage.System("You are Overprepared.")
        )
    }
}
