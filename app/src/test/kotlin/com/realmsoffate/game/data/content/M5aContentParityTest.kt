package com.realmsoffate.game.data.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.data.Abilities
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.MapLocation
import com.realmsoffate.game.game.Mutation
import com.realmsoffate.game.game.Scenarios
import com.realmsoffate.game.game.renderPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Parity tests pinning Phase 6 M5a JSON content (mutations + scenarios) to the
 * historical Kotlin literals they replaced. Scenarios additionally verify
 * promptTemplate interpolation matches the historical lambda output for a
 * fixed test input.
 */
@RunWith(RobolectricTestRunner::class)
class M5aContentParityTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun init() {
        ContentRepository.initialize(ctx)
    }

    @Test fun `mutations match historical`() {
        assertEquals(EXPECTED_MUTATIONS, ContentRepository.mutations)
    }

    @Test fun `scenario metas match historical`() {
        assertEquals(EXPECTED_SCENARIO_METAS, ContentRepository.scenarioMetas)
    }

    @Test fun `scenario list resolves all modifiers`() {
        // Forces every JSON entry's hasModifier flag to match the registry.
        val resolved = Scenarios.list
        assertEquals(EXPECTED_SCENARIO_METAS.size, resolved.size)
        EXPECTED_SCENARIO_METAS.zip(resolved).forEach { (meta, scn) ->
            assertEquals(meta.id, scn.id)
            if (meta.hasModifier) assertNotNull("expected modifier for ${meta.id}", scn.modify)
            else assertNull("unexpected modifier for ${meta.id}", scn.modify)
        }
    }

    @Test fun `renderPrompt prison_break matches historical lambda output`() {
        val ch = Character(name = "T", race = "Elf", cls = "Wizard", abilities = Abilities())
        val loc = MapLocation(
            id = 0, name = "Bastion", type = "castle", icon = "🏰", x = 0, y = 0,
            discovered = true,
        )
        val nearby = "🌲 Greenwood (5lg), 🏘️ Millfield (3lg)"
        val lore = "Local faction: The Iron Concordance (military)."

        val scn = Scenarios.list.first { it.id == "prison_break" }
        val expected = "I wake in a cold, dark prison cell beneath 🏰 **Bastion**. " +
            "I don't know how long I've been here. The guards haven't come in days. The lock is rusted. " +
            "Something screams in the distance. I'm a Elf Wizard — and I need to escape. " +
            "Nearby locations: 🌲 Greenwood (5lg), 🏘️ Millfield (3lg). " +
            "Local faction: The Iron Concordance (military).. " +
            "Open with the cell, the sounds, the desperation. Give me a way out that requires a skill check. " +
            "Reference why a faction might have imprisoned me. " +
            "Start a quest to discover who put me here and why."

        assertEquals(expected, scn.renderPrompt(ch, loc, nearby, lore))
    }

    private companion object {
        val EXPECTED_MUTATIONS: List<Mutation> = listOf(
            Mutation("dead_walk", "The Dead Walk", "🧟",
                "Undead rise from their graves with disturbing regularity. Cemeteries are guarded. Necromancy is either outlawed or state-sanctioned.",
                "WORLD MUTATION — THE DEAD WALK: Undead are a constant threat. Graveyards are fortified. Bodies are burned. Survival requires vigilance. Reference undead activity, burnt pyres, or protected tombs often."),
            Mutation("magic_dying", "Magic Is Dying", "🔮",
                "The Weave is unraveling. Spells fizzle, enchantments fade. Magical items are losing power. No one knows why.",
                "WORLD MUTATION — MAGIC IS DYING: Spells sometimes fail. Enchanted items flicker. Magic users fear what comes next. Reference failing magic, abandoned towers, or panicked arcanists."),
            Mutation("dragon_tyranny", "Dragon Tyranny", "🐉",
                "A dragon — ancient and terrible — demands tribute from every settlement. Resistance means fire.",
                "WORLD MUTATION — DRAGON TYRANNY: An ancient dragon rules the region. Tribute is demanded. Resistance is punished with fire. Reference scorch marks, tribute wagons, and whispered resistance."),
            Mutation("eternal_winter", "Eternal Winter", "❄️",
                "An unnatural winter grips the land. Rivers freeze, crops die, and something moves in the blizzards.",
                "WORLD MUTATION — ETERNAL WINTER: Perpetual cold. Snow falls year-round. Crops fail. Something hunts in the blizzards. Reference frozen breath, snow-covered everything, and cold weather gear."),
            Mutation("plague_years", "The Plague Years", "☠️",
                "A magical plague sweeps the land. The infected develop strange symptoms — glowing veins, prophetic visions, or worse.",
                "WORLD MUTATION — THE PLAGUE YEARS: Supernatural disease. Quarantines. The infected are feared or revered. Reference sealed quarters, masked healers, and glowing-veined sufferers."),
            Mutation("arcane_renaissance", "Arcane Renaissance", "✨",
                "Magic is surging. New spells manifest spontaneously. Wild magic zones appear. Magical technology is booming.",
                "WORLD MUTATION — ARCANE RENAISSANCE: Magic is ascendant. Inventions, wonders, magical colleges expand. Reference mageglow streetlamps, floating shops, and arcane wagers."),
            Mutation("civil_war", "The Sundering War", "⚔️",
                "The realm is torn by civil war. Every faction has chosen a side. Neutrality is no longer an option.",
                "WORLD MUTATION — CIVIL WAR: The realm is at war. Checkpoints, refugees, propaganda. NPCs demand to know allegiance. Reference burned villages, recruiters, and political paranoia."),
            Mutation("fey_crossing", "The Fey Crossing", "🧚",
                "The barrier between the Feywild and the Material Plane has thinned. Fey creatures wander freely, and reality bends.",
                "WORLD MUTATION — FEY CROSSING: Feywild bleeds into the material. Reality is whimsical and dangerous. Reference strange lights, shifted paths, and fey bargains."),
            Mutation("god_is_dead", "A God Has Fallen", "⛪",
                "A deity has been killed. Divine magic falters. The dead god's body crashed somewhere in the world, warping everything around it.",
                "WORLD MUTATION — A GOD HAS FALLEN: A dead god's corpse warps the land. Divine magic stutters. Reference abandoned temples, inconsistent prayers, and the warped zone."),
            Mutation("monster_surge", "The Monster Surge", "👹",
                "Monster populations have exploded. Creatures that were rare are now commonplace. Settlements are under constant siege.",
                "WORLD MUTATION — MONSTER SURGE: Monsters everywhere. Settlements fortified. Bounty boards full. Reference walls with spikes, patrols, and monster attacks."),
            Mutation("shadow_eclipse", "The Shadow Eclipse", "🌑",
                "The sun hasn't set in weeks — or hasn't risen. An unnatural eclipse or perpetual twilight blankets the land.",
                "WORLD MUTATION — SHADOW ECLIPSE: Wrong-time darkness. Perpetual twilight. Nocturnal things emboldened. Reference confused rhythms, lantern-lit noons, and shadow creatures."),
            Mutation("gold_rush", "The Great Discovery", "💎",
                "A massive vein of mythril, a dragon's hoard, or ancient ruins full of treasure have been found. Everyone is rushing to claim it.",
                "WORLD MUTATION — THE GREAT DISCOVERY: Gold rush. Prospectors everywhere. Boomtowns. Reference tent cities, claim jumpers, and inflated prices."),
            Mutation("planar_rifts", "The Shattered Veil", "🌀",
                "Portals to other planes keep opening randomly. Demons, angels, elementals, and stranger things slip through.",
                "WORLD MUTATION — THE SHATTERED VEIL: Reality is cracking. Planar rifts open randomly. Reference flickering portals, extraplanar visitors, and displaced creatures."),
            Mutation("uprising", "The People's Revolt", "✊",
                "The common folk have had enough. Revolution brews in every settlement. The old order is crumbling.",
                "WORLD MUTATION — THE PEOPLE'S REVOLT: Revolution spreading. Mobs form. Nobles hunted. Reference broken statues, red banners, and whispered meetings."),
            Mutation("wild_gods", "The Wild Gods Stir", "🦌",
                "Ancient nature spirits — primal, enormous, and alien — are awakening from millennia of slumber. They are not happy.",
                "WORLD MUTATION — THE WILD GODS STIR: Primal spirits awake. Forests shift. Weather is alive. Reference enormous footprints, hostile nature, and wild shrines."),
            Mutation("time_fractures", "Time Is Broken", "⏳",
                "Pockets of accelerated, frozen, or reversed time appear randomly. Yesterday's ghosts walk beside tomorrow's ruins.",
                "WORLD MUTATION — TIME IS BROKEN: Temporal anomalies. Frozen moments. Echoes of past and future. Reference dust-frozen scenes, aged-decades-in-minutes, and ghosts of possibility.")
        )

        // The promptTemplate strings here intentionally use {placeholder} syntax —
        // they're identical to the JSON content. Failures indicate JSON drift.
        val EXPECTED_SCENARIO_METAS: List<ScenarioMeta> = listOf(
            ScenarioMeta("prison_break", "The Forgotten Prisoner", "dungeon",
                "I wake in a cold, dark prison cell beneath {loc.icon} **{loc.name}**. I don't know how long I've been here. The guards haven't come in days. The lock is rusted. Something screams in the distance. I'm a {race} {class} — and I need to escape. Nearby locations: {nearby}. {lore}. Open with the cell, the sounds, the desperation. Give me a way out that requires a skill check. Reference why a faction might have imprisoned me. Start a quest to discover who put me here and why.",
                hasModifier = true),
            ScenarioMeta("shipwreck", "Wreck of the Dawn Treader", "ocean",
                "I wash ashore near {loc.icon} **{loc.name}**, half-drowned, clinging to wreckage. The ship — whatever it was — is gone. My belongings are scattered. Other survivors may be nearby, or their bodies. I'm a {race} {class}. The coast stretches in both directions. Nearby: {nearby}. {lore}. Open with the beach, the wreckage, the disorientation. I need to find shelter and figure out where I am. Include another survivor (NPC) who may or may not be trustworthy. Start a quest about what sank the ship.",
                hasModifier = true),
            ScenarioMeta("burning_village", "The Fire That Took Everything", "ruins",
                "I stumble through the smoldering ruins of {loc.icon} **{loc.name}**. The fire came at dawn — riders, torches, slaughter. I survived. Most didn't. I'm a {race} {class}. Smoke still rises. Bodies and secrets among the ash. Nearby: {nearby}. {lore}. Open with the smell, the silence, the grief. Someone is responsible and they must pay. Start a vengeance quest. One survivor NPC is nearby — traumatized, useful, or already gone mad."),
            ScenarioMeta("portal_amnesia", "Through the Veil", "road",
                "I wake beside the road near {loc.icon} **{loc.name}**, my clothes scorched, my memory thin. I came through something — a portal, a tear, a doorway — but I don't know from where. I'm a {race} {class}. That much is sure. The rest is fog. Nearby: {nearby}. {lore}. Open with disorientation, the strangeness of the sky, the distant travellers who eye me warily. Start a quest to find out what I came through — and why."),
            ScenarioMeta("trial", "The Accused", "town",
                "I stand before the magistrate of {loc.icon} **{loc.name}**, accused of a crime I did not commit. The evidence is fabricated, the witnesses paid. I'm a {race} {class}. The sentence comes at dusk. Nearby: {nearby}. {lore}. Open in the courtroom — cold, hostile, hungry for blood. My life depends on the next few moments. Include an NPC who might be an ally — or the one who framed me."),
            ScenarioMeta("ambush", "Blood on the Road", "battle",
                "The road between {loc.icon} **{loc.name}** and the next settlement ends in blood. Bandits. Bodies. Mine was supposed to be among them. I'm a {race} {class} — wounded, but alive. Nearby: {nearby}. {lore}. Open in the aftermath — the bandits departed or dead, the wagon looted, one thing they missed. Include a choice that defines me. Start a quest that follows the trail.",
                hasModifier = true),
            ScenarioMeta("feast_gone_wrong", "The Poisoned Feast", "castle",
                "The feast at {loc.icon} **{loc.name}** was to celebrate peace. Now nobles convulse in their chairs. I'm a {race} {class}, somehow still upright. The doors are sealed. The poisoner is among us. Nearby: {nearby}. {lore}. Open with the screams, the scent of almonds, the host's dead eyes. Start a locked-room mystery quest. Include 3-4 suspect NPCs."),
            ScenarioMeta("gladiator", "The Arena", "battle",
                "The crowd roars as the gates of the arena at {loc.icon} **{loc.name}** grind open. I'm a {race} {class} — and today I fight, or today I die. Nearby: {nearby}. {lore}. Open in the pit — the sand, the blood, the creature on the other side of the gate. A sponsor NPC watches from the nobles' box. Start a quest to buy my freedom."),
            ScenarioMeta("heist_gone_wrong", "The Botched Job", "dungeon",
                "The heist went sideways. My crew is dead or fled. I'm trapped deep beneath {loc.icon} **{loc.name}**, with the prize and the guards converging. I'm a {race} {class}. Nearby: {nearby}. {lore}. Open with the alarm bells, the closing walls, the decision of what to do with the cursed thing in my hand. Start a quest of escape — and of confronting whoever betrayed us.",
                hasModifier = true),
            ScenarioMeta("plague_town", "The Quarantine", "town",
                "{loc.icon} **{loc.name}** is sealed. Plague inside. Archers at the walls. I'm a {race} {class} — and I'm inside. Nearby: {nearby}. {lore}. Open with the stench of sickness, the coughing from shuttered windows, an NPC too lucid to be merely infected. Start a quest: find what the plague truly is, and whether anyone means to end it."),
            ScenarioMeta("tavern_brawl", "A Drink Too Many", "tavern",
                "I come to under a tavern table in {loc.icon} **{loc.name}**. There is blood on my knuckles, someone else's coin in my pocket, and an ominous symbol freshly drawn on the bar. I'm a {race} {class} — though right now I wish I weren't. Nearby: {nearby}. {lore}. Open with the hangover, the barkeep's look, the thing I apparently promised last night."),
            ScenarioMeta("dream_realm", "The Waking Nightmare", "town",
                "I dreamed of {loc.icon} **{loc.name}**, and now I walk its streets. The sky is wrong. The people stand too still. I'm a {race} {class}. Something is inside the dream with me. Nearby: {nearby}. {lore}. Open with the uncanny wrongness, the mirrored puddles, the first NPC who notices me noticing. Start a quest to wake — or to find out if I'm already awake."),
            ScenarioMeta("caravan_guard", "The Last Guard Standing", "road",
                "The caravan I was hired to guard out of {loc.icon} **{loc.name}** is ash. I'm a {race} {class} — the only one left alive, hired precisely to prevent what just happened. Nearby: {nearby}. {lore}. Open with the burning wagons, the scattered cargo, the single survivor among the merchants' party. Start a quest to reach the next town and deliver what remains."),
            ScenarioMeta("underdark_escape", "From the Depths", "cave",
                "I claw my way up through a cave mouth near {loc.icon} **{loc.name}** — the first surface air in months, perhaps years. Below, the Underdark. Behind, something that did not want me to leave. I'm a {race} {class}. Nearby: {nearby}. {lore}. Open with the sting of true sunlight, the pursuer behind, the villagers who don't know what nearly followed me up."),
            ScenarioMeta("inheritance", "The Dead Man's Letter", "town",
                "A courier hands me a letter in {loc.icon} **{loc.name}**. My uncle — whom I do not remember — has died and left me something that ought to be inherited in person. I'm a {race} {class}. Nearby: {nearby}. {lore}. Open with the letter's details, the courier's evasive answers, the too-interested strangers at the edge of my vision. Start a quest of inheritance — and of who else wants what's mine."),
            ScenarioMeta("bounty_target", "The Hunter Becomes Hunted", "town",
                "The wanted poster nailed to the post at {loc.icon} **{loc.name}** has my face on it. I'm a {race} {class}, and I haven't done the thing they say I've done. The first hunter is already in town. Nearby: {nearby}. {lore}. Open with the poster, the price, the hunter pretending not to see me. Start a quest to clear my name — or outlive everyone who won't let me."),
            ScenarioMeta("ritual_witness", "The Wrong Place, Wrong Time", "ruins",
                "I stumbled into the ritual by accident. The ruins outside {loc.icon} **{loc.name}** are lit by candles that do not burn wax. I'm a {race} {class} — and I saw what I should not have seen. Nearby: {nearby}. {lore}. Open with the circle, the hooded figures, the silence as they realize I am here."),
            ScenarioMeta("classic_tavern", "The Stranger in the Tavern", "tavern",
                "The inn at {loc.icon} **{loc.name}** is warm and the ale is dark. A cloaked stranger at the back has been watching me since I came in. I'm a {race} {class}, here to rest — or I was. Nearby: {nearby}. {lore}. Open with the firelight, the conversation that falls silent too quickly, the stranger's first words. Start a quest that could be the beginning of something — or the end of me."),
            ScenarioMeta("funeral", "The Last Rites", "temple",
                "I stand at the funeral of the only person who believed in me, in the temple at {loc.icon} **{loc.name}**. The congregation watches me with suspicion — they blame me for the death. I'm a {race} {class}. The priest's words ring hollow. Someone in this crowd knows the truth. Nearby: {nearby}. {lore}. Open with the grief, the incense, the whispered accusations. Start a quest to prove my innocence — or to discover if I truly am."),
            ScenarioMeta("suspicious_cargo", "The Long Road", "road",
                "Day twelve of guarding a merchant caravan approaching {loc.icon} **{loc.name}**. The pay is awful, the company worse — but the cargo is suspicious. Locked crates that moan at night. I'm a {race} {class}. Something is deeply wrong with this job. Nearby: {nearby}. {lore}. Open with the road, the caravan, the nervous merchants. Let me discover what I'm really guarding."),
            ScenarioMeta("debt_collector", "The Debt", "town",
                "I owe the wrong people a great deal of gold. They've tracked me to {loc.icon} **{loc.name}**. I have until sundown to pay or find another way. I'm a {race} {class} with empty pockets and a talent for making things worse. Nearby: {nearby}. {lore}. Open with the town, the collectors watching from across the square, and the desperate options before me.",
                hasModifier = true),
            ScenarioMeta("revolution", "The Uprising", "town",
                "The revolution in {loc.icon} **{loc.name}** started an hour ago. Barricades in the streets. The old guard fights the new. I'm a {race} {class} caught in the middle — and both sides want me to pick. Nearby: {nearby}. {lore}. Open with the chaos, the smell of smoke and blood, and the choice that will define everything."),
            ScenarioMeta("contested_will", "The Will", "castle",
                "A stranger delivered a letter: I've inherited an estate near {loc.icon} **{loc.name}** from a relative I never knew existed. The catch? Three other heirs received the same letter. I'm a {race} {class}. The estate is magnificent, crumbling, and almost certainly haunted. Nearby: {nearby}. {lore}. Open with the arrival, the rival heirs, and the first sign that this inheritance comes with a price."),
            ScenarioMeta("false_hero", "Mistaken Identity", "town",
                "The people of {loc.icon} **{loc.name}** think I'm their prophesied savior. I am absolutely not. I'm a {race} {class} who happened to arrive on the right day wearing the right colors. But they're desperate, and the real threat is very real. Nearby: {nearby}. {lore}. Open with the adoring crowd, my growing horror, and the monster they expect me to slay.")
        )
    }
}
