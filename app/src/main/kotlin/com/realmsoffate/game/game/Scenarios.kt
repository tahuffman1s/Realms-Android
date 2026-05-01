package com.realmsoffate.game.game

import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.MapLocation
import com.realmsoffate.game.data.content.ContentRepository
import com.realmsoffate.game.data.content.ScenarioMeta
import com.realmsoffate.game.util.Templates
import kotlin.random.Random

/**
 * Starting scenario record. The promptTemplate is a string with `{race}`,
 * `{class}`, `{loc.icon}`, `{loc.name}`, `{nearby}`, `{lore}` placeholders.
 * Use [renderPrompt] to interpolate it for a specific character + location.
 *
 * Optional [modify] tweaks the character on scenario start (e.g. starting in a
 * prison cell = lose most inventory). Backed by a code-side registry keyed on
 * scenario id; JSON only flags whether one exists via `hasModifier`.
 */
data class Scenario(
    val id: String,
    val name: String,
    val sceneHint: String,
    val promptTemplate: String,
    val modify: ((Character) -> Unit)? = null,
)

fun Scenario.renderPrompt(
    ch: Character,
    loc: MapLocation,
    nearby: String,
    lore: String,
): String = Templates.interpolate(
    promptTemplate,
    mapOf(
        "race" to ch.race,
        "class" to ch.cls,
        "loc.icon" to loc.icon,
        "loc.name" to loc.name,
        "nearby" to nearby,
        "lore" to lore,
    ),
)

object Scenarios {
    private val modifyRegistry: Map<String, (Character) -> Unit> = mapOf(
        "prison_break" to { ch ->
            ch.gold = maxOf(0, ch.gold - 15)
            ch.inventory.removeAll { it.type != "weapon" && it.type != "armor" }
        },
        "shipwreck" to { ch -> ch.gold = (ch.gold * 0.5).toInt() },
        "ambush" to { ch -> ch.hp = (ch.maxHp * 0.4).toInt().coerceAtLeast(1) },
        "heist_gone_wrong" to { ch -> ch.gold += 50 },
        "debt_collector" to { ch -> ch.gold = 0 },
    )

    val list: List<Scenario> get() = ContentRepository.scenarioMetas.map { it.toScenario() }

    fun random(rng: Random = Random.Default): Scenario = list[rng.nextInt(list.size)]

    private fun ScenarioMeta.toScenario(): Scenario {
        val mod = if (hasModifier) {
            modifyRegistry[id]
                ?: error("Scenario registry missing modifier for id='$id' (hasModifier=true in JSON)")
        } else {
            if (modifyRegistry.containsKey(id)) {
                error("Scenario registry has orphan modifier for id='$id' (hasModifier=false in JSON)")
            }
            null
        }
        return Scenario(
            id = id,
            name = name,
            sceneHint = sceneHint,
            promptTemplate = promptTemplate,
            modify = mod,
        )
    }
}
