package com.realmsoffate.game.data.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.util.Templates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Parity tests pinning Phase 6 M5b JSON content to the historical Kotlin
 * literals it replaced — WorldEvents weighted templates and the five
 * LoreGen historical-event lists. Failures indicate JSON drift, not
 * desired changes.
 */
@RunWith(RobolectricTestRunner::class)
class M5bContentParityTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun init() {
        ContentRepository.initialize(ctx)
    }

    @Test fun `world events match historical`() {
        assertEquals(EXPECTED_WORLD_EVENTS, ContentRepository.worldEventTemplates)
    }

    @Test fun `historical events match historical literals`() {
        val he = ContentRepository.historicalEvents
        assertEquals(EXPECTED_PRIMORDIAL, he.primordial)
        assertEquals(EXPECTED_ANCIENT, he.ancient)
        assertEquals(EXPECTED_MEDIEVAL, he.medieval)
        assertEquals(EXPECTED_DARK_AGE, he.darkAge)
        assertEquals(EXPECTED_RECENT, he.recent)
    }

    @Test fun `every world event tag resolves`() {
        // We can't reach the private registries, but we can prove resolution
        // works by exercising the only public path: every tag set that ships
        // should be one of the documented sets.
        val knownNeeds = setOf("always", "factions:>=1", "factions:>=2", "npcs:>=1",
            "loc:>=1", "loc:typed-cave-dungeon-ruins")
        val knownContexts = setOf("none", "faction", "faction-pair",
            "faction-with-loc-discovered-pref", "faction-with-typed-town-city",
            "faction-pair-with-typed-town-city", "npc", "loc",
            "loc-typed-cave-dungeon-ruins")
        for (t in ContentRepository.worldEventTemplates) {
            assertTrue("unknown needs tag '${t.needs}' on id='${t.id}'", t.needs in knownNeeds)
            assertTrue("unknown context tag '${t.context}' on id='${t.id}'", t.context in knownContexts)
        }
    }

    @Test fun `world event template renders as historical lambda would`() {
        // Fixed inputs for the unexpected_alliance template (faction-pair).
        val tpl = ContentRepository.worldEventTemplates.first { it.id == "unexpected_alliance" }
        val ctxMap = mapOf("f1.name" to "The Iron Concordance", "f2.name" to "The Silent Order")
        val title = Templates.interpolate(tpl.title, ctxMap)
        val prompt = Templates.interpolate(tpl.prompt, ctxMap)
        assertEquals("Unexpected Alliance", title)
        assertEquals(
            "BREAKING: The Iron Concordance and The Silent Order have declared a truce. " +
                "NPCs are suspicious. Alliances shift power dynamics.",
            prompt
        )
    }

    @Test fun `historical event template renders as historical lambda would`() {
        val tpl = EXPECTED_ANCIENT[0] // founder vision
        val rendered = Templates.interpolate(
            tpl,
            mapOf("f" to "Iron Concordance", "n" to "Aenor Bright", "loc" to "Bastion")
        )
        assertEquals(
            "The **Iron Concordance** was founded by Aenor Bright after a divine vision at **Bastion**. " +
                "Its tenets still shape the region.",
            rendered
        )
    }

    private companion object {
        val EXPECTED_WORLD_EVENTS: List<WorldEventTemplateDto> = listOf(
            WorldEventTemplateDto("faction_mobilizes", 3, "factions:>=2", "faction-with-loc-discovered-pref",
                "⚔️", "{f.name} Mobilizes",
                "BREAKING: {f.name} is mobilizing troops near {loc.name}. NPC reactions should reflect fear, opportunity, or allegiance. Soldiers and supply wagons visible."),
            WorldEventTemplateDto("unexpected_alliance", 3, "factions:>=2", "faction-pair",
                "🤝", "Unexpected Alliance",
                "BREAKING: {f1.name} and {f2.name} have declared a truce. NPCs are suspicious. Alliances shift power dynamics."),
            WorldEventTemplateDto("ruler_assassinated", 2, "factions:>=1", "faction",
                "💀", "Ruler Assassinated",
                "BREAKING: The ruler of {f.name} is dead — assassinated. Power vacuum. Factions scramble. NPCs react with shock, fear, or ambition. THIS CHANGES EVERYTHING: faction prices double, guards are distracted (easier stealth), and a power-grab quest emerges."),
            WorldEventTemplateDto("demands_tribute", 2, "factions:>=1", "faction",
                "🏴", "{f.name} Demands Tribute",
                "BREAKING: {f.name} is demanding tribute from settlements. Merchants complain. Guards enforce. Resistance is brewing. GAMEPLAY EFFECT: all NPC vendors now charge a 25% surcharge on goods. Refusing to pay the tribute collector triggers combat. A resistance contact quietly offers a quest to sabotage the collection effort."),
            WorldEventTemplateDto("border_clash", 2, "factions:>=2", "faction-pair-with-typed-town-city",
                "🔥", "Border Clash near {loc.name}",
                "BREAKING: {f1.name} and {f2.name} fought a skirmish near {loc.name}. Refugees and wounded fill the roads. Pick a side or walk between. GAMEPLAY EFFECT: travel to {loc.name} costs double in supplies. Wounded soldiers offer equipment at a discount. Siding with either faction locks out that faction's rival merchants for 6 turns."),
            WorldEventTemplateDto("issues_edict", 2, "factions:>=1", "faction-with-typed-town-city",
                "📜", "{f.name} Issues Edict",
                "BREAKING: {f.name} has issued a sweeping new edict affecting {loc.name}. NPCs whisper. Some obey. Some prepare to resist."),
            WorldEventTemplateDto("heretic_burning", 2, "factions:>=1", "faction",
                "🕯️", "Heretic Burning",
                "BREAKING: {f.name} has burned a heretic in the public square. The smoke still rises. Sympathizers gather in shadows."),
            WorldEventTemplateDto("npc_vanished", 2, "npcs:>=1", "npc",
                "👤", "{n.name} Has Vanished",
                "BREAKING: {n.name} the {n.role} vanished without a trace. Their quarters show signs of a struggle. Rumors fly. GAMEPLAY EFFECT: any quest tied to {n.name} is now blocked until they are found. A new investigation quest opens immediately. Their contacts offer a reward — and someone clearly doesn't want them found."),
            WorldEventTemplateDto("npc_speaks_out", 2, "npcs:>=1", "npc",
                "🗣️", "{n.name} Speaks Out",
                "BREAKING: {n.name} the {n.role} has publicly denounced {n.faction}. Consequences are coming. Pick a side."),
            WorldEventTemplateDto("npc_betrothal", 2, "npcs:>=1", "npc",
                "💍", "{n.name}'s Betrothal",
                "BREAKING: {n.name} the {n.role} has been betrothed. The alliance it seals shifts politics. Gifts and knives both travel fast."),
            WorldEventTemplateDto("npc_dead", 2, "npcs:>=1", "npc",
                "⚰️", "{n.name} Is Dead",
                "BREAKING: {n.name} the {n.role} has died — cause disputed. Mourners gather. Suspects flee. Inheritance or revenge drives new quests."),
            WorldEventTemplateDto("fire_in_loc", 2, "loc:>=1", "loc",
                "🔥", "Fire in {loc.name}",
                "BREAKING: Fire has swept part of {loc.name}. Smoke on the horizon. Refugees move along the roads. Arson is suspected. GAMEPLAY EFFECT: damaged buildings mean fewer merchants, displaced NPCs offer desperate quests, and fire damage lingers in scene descriptions for 5+ turns."),
            WorldEventTemplateDto("festival", 1, "loc:>=1", "loc",
                "🎪", "Festival in {loc.name}",
                "BREAKING: A festival has begun in {loc.name}. Crowds, music, merchants, pickpockets. Rare goods and strangers in the streets."),
            WorldEventTemplateDto("monsters_spill", 2, "loc:typed-cave-dungeon-ruins", "loc-typed-cave-dungeon-ruins",
                "👹", "Monsters Spill From {loc.name}",
                "BREAKING: Creatures have been sighted pouring from {loc.name}. Hunters are gathering. Bounties rise. Something deeper stirred. GAMEPLAY EFFECT: roads near {loc.name} trigger random encounters for the next 5 turns. Hunter NPCs pay premium for monster parts. A bounty board quest to clear the source is now active."),
            WorldEventTemplateDto("bounty_posted", 2, "loc:>=1", "loc",
                "📜", "Bounty Posted at {loc.name}",
                "BREAKING: A substantial bounty has been posted at {loc.name}. Hunters and opportunists move in. The target knows they are prey."),
            WorldEventTemplateDto("storm", 1, "always", "none",
                "🌪️", "Storm Sweeps the Land",
                "BREAKING: A freak storm is battering the countryside. Roads flood. Lightning strikes. Travel is treacherous."),
            WorldEventTemplateDto("unnatural_darkness", 1, "always", "none",
                "🌑", "Unnatural Darkness",
                "BREAKING: For three hours at noon, the sky went dark. Priests pray. Astrologers argue. Something beyond the Veil took notice."),
            WorldEventTemplateDto("planar_rift", 1, "loc:>=1", "loc",
                "🌀", "Planar Rift at {loc.name}",
                "BREAKING: A tear in reality has opened near {loc.name}. Things slip through — most shouldn't. Mages rush to study or seal it."),
            WorldEventTemplateDto("trade_route", 2, "always", "none",
                "💰", "Trade Route Collapsed",
                "BREAKING: A key trade route has failed — raiders, rockslide, or political fiat. Prices spike. Merchants weep. Smugglers profit. GAMEPLAY EFFECT: all shop prices increase by 50% until resolved. New smuggler NPCs appear with black-market goods. A quest to reopen the route becomes available.")
        )

        val EXPECTED_PRIMORDIAL: List<String> = listOf(
            "The gods shaped the land around **{loc}**. The earth still hums with their residual power.",
            "A primordial titan fell near **{loc}**, its bones forming the bedrock. Its blood became the rivers.",
            "The first elves emerged from the Feywild near **{loc}**, weeping at the beauty of the mortal sky.",
            "Dragons claimed dominion over the region around **{loc}**. Their rule would last centuries.",
            "The Weave of magic was woven across **{loc}** by unknown hands. Ley lines converge here still.",
            "An ancient tree of immense power took root near **{loc}**. Druids would later name it the Worldheart."
        )

        val EXPECTED_ANCIENT: List<String> = listOf(
            "The **{f}** was founded by {n} after a divine vision at **{loc}**. Its tenets still shape the region.",
            "A dwarven kingdom was carved beneath **{loc}**. Its forges burned for a century before falling silent.",
            "{n} united the warring tribes near **{loc}** under the banner of the **{f}**, forging the first alliance.",
            "The **Great Library of {loc}** was built, housing knowledge from across the realm. The **{f}** served as its guardians.",
            "An elven city rose near **{loc}**, its towers touching the clouds. It would stand for five hundred years.",
            "{n} discovered the first deposits of mythril beneath **{loc}**, sparking a rush that transformed the region.",
            "A celestial being descended near **{loc}** and spoke a prophecy: *\"When the {f} falters, the darkness wakes.\"*",
            "The **{f}** constructed a fortress at **{loc}** to guard the border. Its walls were said to be impenetrable."
        )

        val EXPECTED_MEDIEVAL: List<String> = listOf(
            "{n} crowned themselves ruler of **{loc}** after assassinating the previous monarch. The **{f}** backed the coup.",
            "The **War of Three Banners** raged for twelve years. **{loc}** changed hands four times. The **{f}** emerged victorious — barely.",
            "A trade route was established through **{loc}**, bringing wealth and trouble in equal measure. The **{f}** taxed it heavily.",
            "{n} discovered that the ruling family of **{loc}** were secretly lycanthropes. The purge that followed was... thorough.",
            "A tournament at **{loc}** ended in bloodshed when {n} accused the **{f}** of cheating. The grudge persists to this day.",
            "The **{f}** built a cathedral at **{loc}** — ostensibly to worship, actually to conceal what lies beneath.",
            "{n} negotiated the **Treaty of {loc}**, ending the border wars. Not everyone agreed with the terms. Some never forgave.",
            "A band of adventurers destroyed a dragon's hoard near **{loc}**. The economic chaos that followed nearly toppled the **{f}**.",
            "{n} was publicly executed at **{loc}** for heresy against the **{f}**. Their followers went underground — and multiplied.",
            "The mines beneath **{loc}** broke through into something ancient. The **{f}** sealed them. {n} wants them reopened."
        )

        val EXPECTED_DARK_AGE: List<String> = listOf(
            "The **Shattering** — a magical cataclysm — devastated **{loc}**. The **{f}** was nearly wiped out. {n} led the survivors.",
            "An undead army rose from the catacombs of **{loc}**. The siege lasted seven years. {n} held the gate alone on the final night.",
            "A lich known as {n} established a domain of terror around **{loc}**. The **{f}** was formed specifically to oppose them.",
            "Famine gripped the land for a decade. Near **{loc}**, the **{f}** hoarded grain while {n} distributed it to the starving — creating a schism.",
            "The sun went dark for a month. Creatures of shadow poured from rifts near **{loc}**. {n} sealed the largest rift at the cost of their sight.",
            "A demon lord was summoned at **{loc}** by a desperate cult. {n} and the **{f}** barely contained it. The scars on the land remain.",
            "The **Red Winter** killed thousands near **{loc}**. Snow fell crimson for reasons no one could explain. The **{f}** blamed foreign sorcery.",
            "All arcane magic failed for a year near **{loc}**. The **{f}** exploited the chaos while {n} searched for the cause."
        )

        val EXPECTED_RECENT: List<String> = listOf(
            "{n} overthrew the old ruler of **{loc}**, establishing the **{f}** through blood and betrayal.",
            "A great plague swept through **{loc}**. The **{f}** rose from the ashes, promising salvation — for a price.",
            "An ancient artifact was unearthed near **{loc}**. The **{f}** claimed it, and {n} was changed by its power.",
            "{n} was exiled from **{loc}** and founded the **{f}** in the wilderness, swearing vengeance.",
            "The great fire of **{loc}** destroyed half the settlement. The **{f}** controls the reconstruction — and the debt.",
            "A portal to the Shadowfell opened near **{loc}**. The **{f}** formed to guard it. {n} has not been the same since.",
            "{n} discovered forbidden magic beneath **{loc}** and formed the **{f}** to study — or exploit — it.",
            "The harvest failed for three years near **{loc}**. The **{f}** controls the food supply. {n} decides who eats.",
            "A dragon attacked **{loc}**. {n} slew it — or so the stories say. The **{f}** was built on that legend.",
            "{n} vanished from **{loc}** three months ago. The **{f}** is searching — some say to rescue, others say to silence.",
            "Strange earthquakes have been shaking **{loc}**. The **{f}** blames {n}'s experiments. {n} blames something deeper.",
            "A child was born at **{loc}** bearing the mark of an ancient prophecy. The **{f}** and {n} both want to control the child's fate."
        )
    }
}
