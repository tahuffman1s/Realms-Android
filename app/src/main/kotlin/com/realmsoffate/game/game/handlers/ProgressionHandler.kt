package com.realmsoffate.game.game.handlers

import com.realmsoffate.game.game.DisplayMessage
import com.realmsoffate.game.game.Feats
import com.realmsoffate.game.game.GameUiState
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
        val ch = s.character ?: return
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
        val ch = s.character ?: return
        val feat = Feats.find(featName) ?: return
        feat.apply(ch)
        ch.feats.add(featName)
        _pendingFeat.value = false
        ui.value = s.copy(character = ch, messages = s.messages + DisplayMessage.System("Feat acquired: $featName"))
    }

    fun dismissFeat() { _pendingFeat.value = false }
}
