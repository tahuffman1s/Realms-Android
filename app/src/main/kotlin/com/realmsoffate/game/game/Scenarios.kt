package com.realmsoffate.game.game

import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.MapLocation
import com.realmsoffate.game.data.WorldLore
import com.realmsoffate.game.data.WorldMap
import kotlin.random.Random

/**
 * 18 starting scenarios ported from realms_of_fate.html's STARTING_SCENARIOS.
 * Each has an opening prompt template, an optional character modifier (hp/gold/
 * inventory adjustments to reflect the scenario fiction), and a scene hint.
 */
data class Scenario(
    val id: String,
    val name: String,
    val sceneHint: String,
    val promptTemplate: (Character, MapLocation, String, String) -> String,
    val modify: ((Character) -> Unit)? = null
)

object Scenarios {
    val list: List<Scenario> = listOf(
        Scenario(
            id = "prison_break", name = "The Forgotten Prisoner", sceneHint = "dungeon",
            promptTemplate = { ch, loc, nearby, lore ->
                "I wake in a cold, dark prison cell beneath ${loc.icon} **${loc.name}**. I don't know how long I've been here. The guards haven't come in days. The lock is rusted. Something screams in the distance. I'm a ${ch.race} ${ch.cls} — and I need to escape. Nearby locations: $nearby. $lore. Open with the cell, the sounds, the desperation. Give me a way out that requires a skill check. Reference why a faction might have imprisoned me. Start a quest to discover who put me here and why."
            },
            modify = { ch ->
                ch.gold = maxOf(0, ch.gold - 15)
                ch.inventory.removeAll { it.type != "weapon" && it.type != "armor" }
            }
        ),
        Scenario(
            id = "shipwreck", name = "Wreck of the Dawn Treader", sceneHint = "ocean",
            promptTemplate = { ch, loc, nearby, lore ->
                "I wash ashore near ${loc.icon} **${loc.name}**, half-drowned, clinging to wreckage. The ship — whatever it was — is gone. My belongings are scattered. Other survivors may be nearby, or their bodies. I'm a ${ch.race} ${ch.cls}. The coast stretches in both directions. Nearby: $nearby. $lore. Open with the beach, the wreckage, the disorientation. I need to find shelter and figure out where I am. Include another survivor (NPC) who may or may not be trustworthy. Start a quest about what sank the ship."
            },
            modify = { ch -> ch.gold = (ch.gold * 0.5).toInt() }
        ),
        Scenario(
            id = "burning_village", name = "The Fire That Took Everything", sceneHint = "ruins",
            promptTemplate = { ch, loc, nearby, lore ->
                "I stumble through the smoldering ruins of ${loc.icon} **${loc.name}**. The fire came at dawn — riders, torches, slaughter. I survived. Most didn't. I'm a ${ch.race} ${ch.cls}. Smoke still rises. Bodies and secrets among the ash. Nearby: $nearby. $lore. Open with the smell, the silence, the grief. Someone is responsible and they must pay. Start a vengeance quest. One survivor NPC is nearby — traumatized, useful, or already gone mad."
            }
        ),
        Scenario(
            id = "portal_amnesia", name = "Through the Veil", sceneHint = "road",
            promptTemplate = { ch, loc, nearby, lore ->
                "I wake beside the road near ${loc.icon} **${loc.name}**, my clothes scorched, my memory thin. I came through something — a portal, a tear, a doorway — but I don't know from where. I'm a ${ch.race} ${ch.cls}. That much is sure. The rest is fog. Nearby: $nearby. $lore. Open with disorientation, the strangeness of the sky, the distant travellers who eye me warily. Start a quest to find out what I came through — and why."
            }
        ),
        Scenario(
            id = "trial", name = "The Accused", sceneHint = "town",
            promptTemplate = { ch, loc, nearby, lore ->
                "I stand before the magistrate of ${loc.icon} **${loc.name}**, accused of a crime I did not commit. The evidence is fabricated, the witnesses paid. I'm a ${ch.race} ${ch.cls}. The sentence comes at dusk. Nearby: $nearby. $lore. Open in the courtroom — cold, hostile, hungry for blood. My life depends on the next few moments. Include an NPC who might be an ally — or the one who framed me."
            }
        ),
        Scenario(
            id = "ambush", name = "Blood on the Road", sceneHint = "battle",
            promptTemplate = { ch, loc, nearby, lore ->
                "The road between ${loc.icon} **${loc.name}** and the next settlement ends in blood. Bandits. Bodies. Mine was supposed to be among them. I'm a ${ch.race} ${ch.cls} — wounded, but alive. Nearby: $nearby. $lore. Open in the aftermath — the bandits departed or dead, the wagon looted, one thing they missed. Include a choice that defines me. Start a quest that follows the trail."
            },
            modify = { ch -> ch.hp = (ch.maxHp * 0.4).toInt().coerceAtLeast(1) }
        ),
        Scenario(
            id = "feast_gone_wrong", name = "The Poisoned Feast", sceneHint = "castle",
            promptTemplate = { ch, loc, nearby, lore ->
                "The feast at ${loc.icon} **${loc.name}** was to celebrate peace. Now nobles convulse in their chairs. I'm a ${ch.race} ${ch.cls}, somehow still upright. The doors are sealed. The poisoner is among us. Nearby: $nearby. $lore. Open with the screams, the scent of almonds, the host's dead eyes. Start a locked-room mystery quest. Include 3-4 suspect NPCs."
            }
        ),
        Scenario(
            id = "gladiator", name = "The Arena", sceneHint = "battle",
            promptTemplate = { ch, loc, nearby, lore ->
                "The crowd roars as the gates of the arena at ${loc.icon} **${loc.name}** grind open. I'm a ${ch.race} ${ch.cls} — and today I fight, or today I die. Nearby: $nearby. $lore. Open in the pit — the sand, the blood, the creature on the other side of the gate. A sponsor NPC watches from the nobles' box. Start a quest to buy my freedom."
            }
        ),
        Scenario(
            id = "heist_gone_wrong", name = "The Botched Job", sceneHint = "dungeon",
            promptTemplate = { ch, loc, nearby, lore ->
                "The heist went sideways. My crew is dead or fled. I'm trapped deep beneath ${loc.icon} **${loc.name}**, with the prize and the guards converging. I'm a ${ch.race} ${ch.cls}. Nearby: $nearby. $lore. Open with the alarm bells, the closing walls, the decision of what to do with the cursed thing in my hand. Start a quest of escape — and of confronting whoever betrayed us."
            },
            modify = { ch -> ch.gold += 50 }
        ),
        Scenario(
            id = "plague_town", name = "The Quarantine", sceneHint = "town",
            promptTemplate = { ch, loc, nearby, lore ->
                "${loc.icon} **${loc.name}** is sealed. Plague inside. Archers at the walls. I'm a ${ch.race} ${ch.cls} — and I'm inside. Nearby: $nearby. $lore. Open with the stench of sickness, the coughing from shuttered windows, an NPC too lucid to be merely infected. Start a quest: find what the plague truly is, and whether anyone means to end it."
            }
        ),
        Scenario(
            id = "tavern_brawl", name = "A Drink Too Many", sceneHint = "tavern",
            promptTemplate = { ch, loc, nearby, lore ->
                "I come to under a tavern table in ${loc.icon} **${loc.name}**. There is blood on my knuckles, someone else's coin in my pocket, and an ominous symbol freshly drawn on the bar. I'm a ${ch.race} ${ch.cls} — though right now I wish I weren't. Nearby: $nearby. $lore. Open with the hangover, the barkeep's look, the thing I apparently promised last night."
            }
        ),
        Scenario(
            id = "dream_realm", name = "The Waking Nightmare", sceneHint = "town",
            promptTemplate = { ch, loc, nearby, lore ->
                "I dreamed of ${loc.icon} **${loc.name}**, and now I walk its streets. The sky is wrong. The people stand too still. I'm a ${ch.race} ${ch.cls}. Something is inside the dream with me. Nearby: $nearby. $lore. Open with the uncanny wrongness, the mirrored puddles, the first NPC who notices me noticing. Start a quest to wake — or to find out if I'm already awake."
            }
        ),
        Scenario(
            id = "caravan_guard", name = "The Last Guard Standing", sceneHint = "road",
            promptTemplate = { ch, loc, nearby, lore ->
                "The caravan I was hired to guard out of ${loc.icon} **${loc.name}** is ash. I'm a ${ch.race} ${ch.cls} — the only one left alive, hired precisely to prevent what just happened. Nearby: $nearby. $lore. Open with the burning wagons, the scattered cargo, the single survivor among the merchants' party. Start a quest to reach the next town and deliver what remains."
            }
        ),
        Scenario(
            id = "underdark_escape", name = "From the Depths", sceneHint = "cave",
            promptTemplate = { ch, loc, nearby, lore ->
                "I claw my way up through a cave mouth near ${loc.icon} **${loc.name}** — the first surface air in months, perhaps years. Below, the Underdark. Behind, something that did not want me to leave. I'm a ${ch.race} ${ch.cls}. Nearby: $nearby. $lore. Open with the sting of true sunlight, the pursuer behind, the villagers who don't know what nearly followed me up."
            }
        ),
        Scenario(
            id = "inheritance", name = "The Dead Man's Letter", sceneHint = "town",
            promptTemplate = { ch, loc, nearby, lore ->
                "A courier hands me a letter in ${loc.icon} **${loc.name}**. My uncle — whom I do not remember — has died and left me something that ought to be inherited in person. I'm a ${ch.race} ${ch.cls}. Nearby: $nearby. $lore. Open with the letter's details, the courier's evasive answers, the too-interested strangers at the edge of my vision. Start a quest of inheritance — and of who else wants what's mine."
            }
        ),
        Scenario(
            id = "bounty_target", name = "The Hunter Becomes Hunted", sceneHint = "town",
            promptTemplate = { ch, loc, nearby, lore ->
                "The wanted poster nailed to the post at ${loc.icon} **${loc.name}** has my face on it. I'm a ${ch.race} ${ch.cls}, and I haven't done the thing they say I've done. The first hunter is already in town. Nearby: $nearby. $lore. Open with the poster, the price, the hunter pretending not to see me. Start a quest to clear my name — or outlive everyone who won't let me."
            }
        ),
        Scenario(
            id = "ritual_witness", name = "The Wrong Place, Wrong Time", sceneHint = "ruins",
            promptTemplate = { ch, loc, nearby, lore ->
                "I stumbled into the ritual by accident. The ruins outside ${loc.icon} **${loc.name}** are lit by candles that do not burn wax. I'm a ${ch.race} ${ch.cls} — and I saw what I should not have seen. Nearby: $nearby. $lore. Open with the circle, the hooded figures, the silence as they realize I am here."
            }
        ),
        Scenario(
            id = "classic_tavern", name = "The Stranger in the Tavern", sceneHint = "tavern",
            promptTemplate = { ch, loc, nearby, lore ->
                "The inn at ${loc.icon} **${loc.name}** is warm and the ale is dark. A cloaked stranger at the back has been watching me since I came in. I'm a ${ch.race} ${ch.cls}, here to rest — or I was. Nearby: $nearby. $lore. Open with the firelight, the conversation that falls silent too quickly, the stranger's first words. Start a quest that could be the beginning of something — or the end of me."
            }
        ),
        Scenario(
            id = "funeral", name = "The Last Rites", sceneHint = "temple",
            promptTemplate = { ch, loc, nearby, lore ->
                "I stand at the funeral of the only person who believed in me, in the temple at ${loc.icon} **${loc.name}**. The congregation watches me with suspicion — they blame me for the death. I'm a ${ch.race} ${ch.cls}. The priest's words ring hollow. Someone in this crowd knows the truth. Nearby: $nearby. $lore. Open with the grief, the incense, the whispered accusations. Start a quest to prove my innocence — or to discover if I truly am."
            }
        ),
        Scenario(
            id = "suspicious_cargo", name = "The Long Road", sceneHint = "road",
            promptTemplate = { ch, loc, nearby, lore ->
                "Day twelve of guarding a merchant caravan approaching ${loc.icon} **${loc.name}**. The pay is awful, the company worse — but the cargo is suspicious. Locked crates that moan at night. I'm a ${ch.race} ${ch.cls}. Something is deeply wrong with this job. Nearby: $nearby. $lore. Open with the road, the caravan, the nervous merchants. Let me discover what I'm really guarding."
            }
        ),
        Scenario(
            id = "debt_collector", name = "The Debt", sceneHint = "town",
            promptTemplate = { ch, loc, nearby, lore ->
                "I owe the wrong people a great deal of gold. They've tracked me to ${loc.icon} **${loc.name}**. I have until sundown to pay or find another way. I'm a ${ch.race} ${ch.cls} with empty pockets and a talent for making things worse. Nearby: $nearby. $lore. Open with the town, the collectors watching from across the square, and the desperate options before me."
            },
            modify = { ch -> ch.gold = 0 }
        ),
        Scenario(
            id = "revolution", name = "The Uprising", sceneHint = "town",
            promptTemplate = { ch, loc, nearby, lore ->
                "The revolution in ${loc.icon} **${loc.name}** started an hour ago. Barricades in the streets. The old guard fights the new. I'm a ${ch.race} ${ch.cls} caught in the middle — and both sides want me to pick. Nearby: $nearby. $lore. Open with the chaos, the smell of smoke and blood, and the choice that will define everything."
            }
        ),
        Scenario(
            id = "contested_will", name = "The Will", sceneHint = "castle",
            promptTemplate = { ch, loc, nearby, lore ->
                "A stranger delivered a letter: I've inherited an estate near ${loc.icon} **${loc.name}** from a relative I never knew existed. The catch? Three other heirs received the same letter. I'm a ${ch.race} ${ch.cls}. The estate is magnificent, crumbling, and almost certainly haunted. Nearby: $nearby. $lore. Open with the arrival, the rival heirs, and the first sign that this inheritance comes with a price."
            }
        ),
        Scenario(
            id = "false_hero", name = "Mistaken Identity", sceneHint = "town",
            promptTemplate = { ch, loc, nearby, lore ->
                "The people of ${loc.icon} **${loc.name}** think I'm their prophesied savior. I am absolutely not. I'm a ${ch.race} ${ch.cls} who happened to arrive on the right day wearing the right colors. But they're desperate, and the real threat is very real. Nearby: $nearby. $lore. Open with the adoring crowd, my growing horror, and the monster they expect me to slay."
            }
        )
    )

    fun random(rng: Random = Random.Default): Scenario = list[rng.nextInt(list.size)]
}
