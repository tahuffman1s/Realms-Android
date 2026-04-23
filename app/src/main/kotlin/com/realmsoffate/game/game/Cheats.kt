package com.realmsoffate.game.game

/**
 * In-memory mirror of the persisted cheat flags in [com.realmsoffate.game.data.CheatsStore].
 * Consulted by [Dice] (hot path, pure Kotlin) and by VM gold-clamp logic.
 *
 * Writes happen on the VM's viewModelScope as prefs flows emit; reads happen from
 * arbitrary threads. Fields are @Volatile for that reason.
 */
object Cheats {
    @Volatile var enabled: Boolean = false
    @Volatile var forceCrit: Boolean = false
    @Volatile var forceFail: Boolean = false
    @Volatile var infiniteGold: Boolean = false

    fun reset() {
        enabled = false
        forceCrit = false
        forceFail = false
        infiniteGold = false
    }
}
