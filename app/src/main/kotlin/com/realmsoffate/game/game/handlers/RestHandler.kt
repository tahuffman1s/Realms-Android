package com.realmsoffate.game.game.handlers

import com.realmsoffate.game.data.GraveyardEntry
import com.realmsoffate.game.data.SaveStore
import com.realmsoffate.game.data.TimelineEntry
import com.realmsoffate.game.data.deepCopy
import com.realmsoffate.game.game.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RestHandler(
    private val ui: MutableStateFlow<GameUiState>,
    private val restOverlay: MutableStateFlow<String?>,
    private val screen: MutableStateFlow<Screen>,
    private val lastDeath: MutableStateFlow<GraveyardEntry?>,
    private val logTimeline: (String, String) -> Unit,
    private val scope: CoroutineScope,
    private val refreshSlots: () -> Unit,
    private val timeline: MutableList<TimelineEntry>
) {
    val restOverlayState: StateFlow<String?> = restOverlay.asStateFlow()

    fun shortRest() {
        val s = ui.value
        val ch = s.character?.deepCopy() ?: return
        val hitDie = Classes.find(ch.cls)?.hitDie ?: 8
        val heal = Dice.d(hitDie) + ch.abilities.conMod.coerceAtLeast(0)
        ch.hp = (ch.hp + heal).coerceAtMost(ch.maxHp)
        SpellSlots.applyShortRest(ch)
        ui.value = s.copy(character = ch, messages = s.messages + DisplayMessage.System("Short rest — recovered $heal HP."))
        logTimeline("event", "Short rest — +$heal HP")
        restOverlay.value = "short:$heal"
    }

    fun longRest() {
        val s = ui.value
        val ch = s.character?.deepCopy() ?: return
        SpellSlots.applyLongRest(ch)
        // Long rest clears most conditions (per D&D 5e). Keep narrative-permanent
        // markers like "Cursed" in place — the narrator can remove them explicitly.
        val permanent = setOf("cursed", "doomed", "marked", "branded")
        ch.conditions.removeAll { it.lowercase() !in permanent }
        ui.value = s.copy(
            character = ch,
            messages = s.messages + DisplayMessage.System("Long rest — fully restored.")
        )
        logTimeline("event", "Long rest — full heal + slots restored")
        restOverlay.value = "long"
    }

    fun dismissRest() { restOverlay.value = null }

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
            worldName = s.worldMap?.locations?.firstOrNull()?.name.orEmpty(),
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
