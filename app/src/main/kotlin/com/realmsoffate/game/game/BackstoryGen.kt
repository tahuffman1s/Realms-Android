package com.realmsoffate.game.game

import com.realmsoffate.game.data.Backstory
import com.realmsoffate.game.data.WorldLore
import com.realmsoffate.game.data.WorldMap
import com.realmsoffate.game.data.content.ContentRepository
import com.realmsoffate.game.data.content.EnemyArchetype
import com.realmsoffate.game.data.content.FlawPair
import com.realmsoffate.game.data.content.LostItem
import kotlin.random.Random

/**
 * Backstory generator ported verbatim from realms_of_fate.html:
 *   15 dark secrets, 12 personal-enemy archetypes, 10 lost items, 10 bonds,
 *   12 flaw+trigger pairs, 10 prophecies + 5 null slots (~33% no prophecy).
 *
 * Origins / motivations stay from the existing port as flavour framing.
 */

private val ORIGINS: List<String> get() = ContentRepository.backstoryOrigins
private val MOTIVATIONS: List<String> get() = ContentRepository.backstoryMotivations
private val DARK_SECRETS: List<String> get() = ContentRepository.backstoryDarkSecrets
private val ENEMY_ARCHETYPES: List<EnemyArchetype> get() = ContentRepository.backstoryEnemyArchetypes
private val LOST_ITEMS: List<LostItem> get() = ContentRepository.backstoryLostItems
private val BONDS: List<String> get() = ContentRepository.backstoryBonds
private val FLAWS: List<FlawPair> get() = ContentRepository.backstoryFlaws
private val PROPHECIES: List<String?> get() = ContentRepository.backstoryProphecies

object BackstoryGen {
    fun generate(race: String, cls: String, lore: WorldLore?, worldMap: WorldMap?): Backstory {
        val rand = Random(System.currentTimeMillis())
        val origin = ORIGINS.random(rand)
        val motivation = MOTIVATIONS.random(rand)
        val flawPair = FLAWS.random(rand)
        val bond = BONDS.random(rand)
        val dark = DARK_SECRETS.random(rand)
        val lostPair = LOST_ITEMS.random(rand)
        val prophecy = PROPHECIES.random(rand)
        val enemyArc = ENEMY_ARCHETYPES.random(rand)
        val enemyFaction = lore?.factions?.randomOrNull(rand)
        val enemyFlavor = enemyFaction?.let { " Their hand reaches out from ${it.name}." } ?: ""
        val enemyDesc = "${enemyArc.title} ${enemyArc.motive}.$enemyFlavor"
        val flaw = "${flawPair.flaw}. ${flawPair.trigger}"
        val lostItem = "${lostPair.item} — ${lostPair.why}"

        val prompt = buildString {
            append("You are a $race $cls, $origin. Your driving motive: $motivation.\n")
            append("BOND: $bond\n")
            append("FLAW: $flaw\n")
            append("DARK SECRET: $dark\n")
            append("LOST ITEM: $lostItem\n")
            append("PERSONAL ENEMY: $enemyDesc")
            if (prophecy != null) append("\nPROPHECY: \"$prophecy\"")
        }
        return Backstory(
            origin = origin, motivation = motivation, flaw = flaw, bond = bond,
            darkSecret = dark, lostItem = lostItem, personalEnemy = enemyDesc,
            prophecy = prophecy, promptText = prompt
        )
    }
}
