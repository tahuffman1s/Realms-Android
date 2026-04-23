package com.realmsoffate.game.game.handlers

import com.realmsoffate.game.data.GraveyardEntry
import com.realmsoffate.game.data.SaveStore
import com.realmsoffate.game.data.TimelineEntry
import com.realmsoffate.game.data.deepCopy
import com.realmsoffate.game.game.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Death-save dice + graveyard bookkeeping, extracted from the removed
 * RestHandler so the rest feature could be retired without losing the
 * death flow.
 */
class DeathHandler(
    private val ui: MutableStateFlow<GameUiState>,
    private val screen: MutableStateFlow<Screen>,
    private val lastDeath: MutableStateFlow<GraveyardEntry?>,
    private val logTimeline: (String, String) -> Unit,
    private val scope: CoroutineScope,
    private val refreshSlots: () -> Unit,
    private val timeline: MutableList<TimelineEntry>
) {
    /**
     * Rolls a single death save. 3 successes = stabilise at 1 HP; 3 failures = die.
     * Nat 20 jumps straight to stable + 1 HP; nat 1 counts as two failures.
     */
    fun rollDeathSave() {
        val s = ui.value
        val saves = s.deathSave ?: return
        val d = Dice.d20()
        val (updated, label) = DeathSaves.roll(saves, d)
        ui.value = s.copy(
            deathSave = updated,
            messages = s.messages + DisplayMessage.System(label)
        )
        if (updated.dead) {
            logTimeline("death", "Failed third death save.")
            die("Failed the third death save.")
            return
        }
        if (updated.stable) {
            val ch = s.character?.deepCopy() ?: return
            ch.hp = 1
            ui.value = ui.value.copy(
                character = ch,
                deathSave = null,
                messages = ui.value.messages + DisplayMessage.System("Stable — 1 HP. Get away.")
            )
            logTimeline("event", "Stabilised after death saves")
        }
    }

    /** Triggered when the character's HP hits 0 — buries them and shows DeathScreen. */
    internal fun die(cause: String) {
        val s = ui.value
        val ch = s.character ?: return
        val entry = GraveyardEntry(
            characterName = ch.name,
            race = ch.race,
            cls = ch.cls,
            level = ch.level,
            turns = s.turns,
            xp = ch.xp,
            gold = ch.gold,
            morality = s.morality,
            worldName = s.worldLore?.worldName.orEmpty(),
            mutations = s.worldLore?.mutations.orEmpty(),
            companions = s.party.map { it.name },
            backstoryText = ch.backstory?.promptText,
            causeOfDeath = cause,
            timeline = timeline.toList() + TimelineEntry(s.turns, "death", cause),
            diedAt = java.time.Instant.now().toString()
        )
        lastDeath.value = entry
        scope.launch {
            SaveStore.bury(entry)
            SaveStore.delete("autosave")
            SaveStore.delete(SaveStore.slotKeyFor(ch.name))
            refreshSlots()
        }
        screen.value = Screen.Death
    }
}
