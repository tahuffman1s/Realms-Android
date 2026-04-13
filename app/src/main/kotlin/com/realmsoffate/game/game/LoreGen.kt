package com.realmsoffate.game.game

import com.realmsoffate.game.data.EconomyInfo
import com.realmsoffate.game.data.Faction
import com.realmsoffate.game.data.GovernmentInfo
import com.realmsoffate.game.data.HistoryEntry
import com.realmsoffate.game.data.LoreNpc
import com.realmsoffate.game.data.PastRuler
import com.realmsoffate.game.data.WorldLore
import com.realmsoffate.game.data.WorldMap
import kotlin.random.Random

private val FACTION_TYPES = listOf(
    "kingdom", "guild", "cult", "order", "tribe", "council", "coven", "cartel",
    "syndicate", "brotherhood", "sisterhood", "legion", "consortium", "cabal",
    "covenant", "conclave", "inquisition", "rebellion", "empire", "federation",
    "commune", "circle", "lodge", "sept", "house", "dynasty", "horde",
    "theocracy", "merchant republic"
)
private val FACTION_ADJS = listOf(
    "Iron", "Shadow", "Golden", "Crimson", "Silver", "Obsidian", "Azure", "Jade",
    "Ashen", "Radiant", "Hollow", "Dread", "Frost", "Storm", "Blood", "Moonlit",
    "Scarlet", "Ivory", "Emerald", "Onyx", "Amber", "Cobalt", "Violet", "Bone",
    "Rust", "Veiled", "Silent", "Burning", "Frozen", "Shattered", "Eternal",
    "Gilded", "Thorned", "Starless", "Pale", "Verdant", "Sable", "Weeping",
    "Prismatic", "Arcane", "Hallowed", "Profane", "Twilight", "Spectral",
    "Molten", "Abyssal"
)
private val FACTION_NOUNS = listOf(
    "Crown", "Fang", "Flame", "Pact", "Hand", "Veil", "Throne", "Circle", "Blade",
    "Rose", "Serpent", "Shield", "Eye", "Tower", "Chalice", "Oath", "Raven",
    "Wolf", "Lion", "Dragon", "Talon", "Hammer", "Thorn", "Mask", "Chain",
    "Scale", "Key", "Gate", "Arrow", "Skull", "Phoenix", "Lotus", "Gryphon",
    "Hydra", "Sphinx", "Tide", "Root", "Star", "Compass", "Herald", "Whisper",
    "Eclipse", "Abyss", "Mantis", "Wyvern"
)

private val NPC_FIRSTS = listOf(
    "Aldric", "Voss", "Elara", "Mira", "Thorne", "Kaelen", "Seraphina", "Draven",
    "Isolde", "Fenwick", "Lyra", "Balthazar", "Nyx", "Orin", "Vesper", "Corvus",
    "Morgana", "Silas", "Freya", "Zephyr", "Brynn", "Lucian", "Asha", "Grimjaw",
    "Celeste", "Tormund", "Yara", "Rook", "Dahlia", "Kael", "Amara", "Ragnar",
    "Seren", "Mordecai", "Lirien", "Hakan", "Zara", "Cassius", "Ione", "Gideon",
    "Petra", "Hadrian", "Nyssa", "Theron", "Morrigan", "Iskander", "Calista",
    "Wulfric", "Selene", "Bjorn", "Xylia", "Caspian", "Rhea", "Dorian", "Tamsin",
    "Ash", "Soraya", "Fenris", "Ophelia", "Magnus", "Kira", "Alaric", "Enya",
    "Lazarus", "Sable", "Zarek", "Imogen", "Caius", "Niamh", "Darius", "Esme",
    "Viktor", "Talia", "Orion", "Meliora", "Gareth", "Ailis", "Nero", "Wren",
    "Soren", "Livia", "Edric", "Faye", "Ronan", "Zinnia", "Kellan", "Thessa",
    "Oberon", "Nephele", "Axel", "Maren", "Xander", "Sage", "Cedric", "Astrid",
    "Eamon", "Lorelei", "Dante", "Verity", "Knox", "Juniper", "Cormac", "Iris"
)
private val NPC_TITLES = listOf(
    "the Wise", "the Cruel", "Ironhand", "Shadowbane", "the Lost", "Dawnbringer",
    "Blackheart", "the Undying", "the Wanderer", "Stormcaller", "the Exile",
    "Bloodfist", "the Silent", "Nightwhisper", "the Brave", "Ashwalker",
    "the Cunning", "Doomhammer", "the Merciful", "Spellweaver", "Halfmoon",
    "the Blighted", "Stoneheart", "the Hollow", "Wyrmtongue", "Three-Fingers",
    "the Unkind", "Duskborne", "the Branded", "Grimshaw", "the Sleepless",
    "Redmantle", "the Twice-Cursed", "Bonechill", "the Reborn", "Quicksilver",
    "the Veiled", "Greywatch", "the Unbroken", "Frostblood", "the Mad",
    "Thornfield", "the Penitent", "Nightfall", "the Last", "Deeproot",
    "the Forsaken", "Brightmore", "the Scorned", "Ashveil"
)
private val NPC_ROLES = listOf(
    "king", "queen", "warlord", "archmage", "high priest", "assassin leader",
    "merchant prince", "rebel leader", "oracle", "necromancer",
    "knight commander", "spymaster", "pirate captain", "druid elder",
    "tavern keeper", "blacksmith", "wandering bard", "bounty hunter",
    "cursed noble", "dragon slayer", "alchemist", "librarian", "smuggler",
    "arena champion", "plague doctor", "inquisitor", "ranger captain",
    "diplomat", "siege engineer", "runesmith", "blood mage", "ferryman",
    "tax collector", "revolutionary", "cartographer", "herbalist",
    "monster hunter", "fortune teller", "executioner", "architect",
    "curator of relics", "war poet", "shadow broker"
)

private val RACES = listOf("Human", "Elf", "Dwarf", "Halfling", "Half-Elf", "Half-Orc", "Tiefling", "Dragonborn", "Gnome")

private val WORLD_NAME_PATTERNS: List<(Random) -> String> = listOf(
    { r -> "The ${listOf("Sundered", "Shattered", "Hollow", "Eternal", "Wounded", "Thirteenth").random(r)} ${listOf("Realms", "Reach", "Kingdoms", "Coast", "Expanse").random(r)}" },
    { r -> listOf("Aethelmar", "Veridonia", "Corvathia", "Dornmark", "Skalaria", "Ynderthel", "Thessarion", "Valdrith").random(r) },
    { r -> "Lands of ${NPC_FIRSTS.random(r)}" },
    { r -> "${listOf("Ashen", "Iron", "Silver", "Obsidian", "Verdant", "Crimson").random(r)} ${listOf("March", "Reach", "Expanse", "Heartland", "Dominion").random(r)}" }
)

private val ERA_LABELS = listOf(
    "Age of Cinders", "Age of Reckoning", "Age of Silence",
    "Age of Banners", "Age of the Wandering Crown", "Age of Ruin",
    "Age of the Long Winter", "Age of the Broken Seal"
)

// ---- Primordial (6, as before) ----
private val PRIMORDIAL_EVENTS: List<(String) -> String> = listOf(
    { l -> "The gods shaped the land around **$l**. The earth still hums with their residual power." },
    { l -> "A primordial titan fell near **$l**, its bones forming the bedrock. Its blood became the rivers." },
    { l -> "The first elves emerged from the Feywild near **$l**, weeping at the beauty of the mortal sky." },
    { l -> "Dragons claimed dominion over the region around **$l**. Their rule would last centuries." },
    { l -> "The Weave of magic was woven across **$l** by unknown hands. Ley lines converge here still." },
    { l -> "An ancient tree of immense power took root near **$l**. Druids would later name it the Worldheart." }
)

// ---- Ancient (8) ----
private val ANCIENT_EVENTS: List<(String, String, String) -> String> = listOf(
    { f, n, loc -> "The **$f** was founded by $n after a divine vision at **$loc**. Its tenets still shape the region." },
    { f, n, loc -> "A dwarven kingdom was carved beneath **$loc**. Its forges burned for a century before falling silent." },
    { f, n, loc -> "$n united the warring tribes near **$loc** under the banner of the **$f**, forging the first alliance." },
    { f, n, loc -> "The **Great Library of $loc** was built, housing knowledge from across the realm. The **$f** served as its guardians." },
    { f, n, loc -> "An elven city rose near **$loc**, its towers touching the clouds. It would stand for five hundred years." },
    { f, n, loc -> "$n discovered the first deposits of mythril beneath **$loc**, sparking a rush that transformed the region." },
    { f, n, loc -> "A celestial being descended near **$loc** and spoke a prophecy: *\"When the $f falters, the darkness wakes.\"*" },
    { f, n, loc -> "The **$f** constructed a fortress at **$loc** to guard the border. Its walls were said to be impenetrable." }
)

// ---- Medieval (10) ----
private val MEDIEVAL_EVENTS: List<(String, String, String) -> String> = listOf(
    { f, n, loc -> "$n crowned themselves ruler of **$loc** after assassinating the previous monarch. The **$f** backed the coup." },
    { f, n, loc -> "The **War of Three Banners** raged for twelve years. **$loc** changed hands four times. The **$f** emerged victorious — barely." },
    { f, n, loc -> "A trade route was established through **$loc**, bringing wealth and trouble in equal measure. The **$f** taxed it heavily." },
    { f, n, loc -> "$n discovered that the ruling family of **$loc** were secretly lycanthropes. The purge that followed was... thorough." },
    { f, n, loc -> "A tournament at **$loc** ended in bloodshed when $n accused the **$f** of cheating. The grudge persists to this day." },
    { f, n, loc -> "The **$f** built a cathedral at **$loc** — ostensibly to worship, actually to conceal what lies beneath." },
    { f, n, loc -> "$n negotiated the **Treaty of $loc**, ending the border wars. Not everyone agreed with the terms. Some never forgave." },
    { f, n, loc -> "A band of adventurers destroyed a dragon's hoard near **$loc**. The economic chaos that followed nearly toppled the **$f**." },
    { f, n, loc -> "$n was publicly executed at **$loc** for heresy against the **$f**. Their followers went underground — and multiplied." },
    { f, n, loc -> "The mines beneath **$loc** broke through into something ancient. The **$f** sealed them. $n wants them reopened." }
)

// ---- Dark Age (8) ----
private val DARK_AGE_EVENTS: List<(String, String, String) -> String> = listOf(
    { f, n, loc -> "The **Shattering** — a magical cataclysm — devastated **$loc**. The **$f** was nearly wiped out. $n led the survivors." },
    { f, n, loc -> "An undead army rose from the catacombs of **$loc**. The siege lasted seven years. $n held the gate alone on the final night." },
    { f, n, loc -> "A lich known as $n established a domain of terror around **$loc**. The **$f** was formed specifically to oppose them." },
    { f, n, loc -> "Famine gripped the land for a decade. Near **$loc**, the **$f** hoarded grain while $n distributed it to the starving — creating a schism." },
    { f, n, loc -> "The sun went dark for a month. Creatures of shadow poured from rifts near **$loc**. $n sealed the largest rift at the cost of their sight." },
    { f, n, loc -> "A demon lord was summoned at **$loc** by a desperate cult. $n and the **$f** barely contained it. The scars on the land remain." },
    { f, n, loc -> "The **Red Winter** killed thousands near **$loc**. Snow fell crimson for reasons no one could explain. The **$f** blamed foreign sorcery." },
    { f, n, loc -> "All arcane magic failed for a year near **$loc**. The **$f** exploited the chaos while $n searched for the cause." }
)

// ---- Recent (12) ----
private val RECENT_EVENTS: List<(String, String, String) -> String> = listOf(
    { f, n, loc -> "$n overthrew the old ruler of **$loc**, establishing the **$f** through blood and betrayal." },
    { f, n, loc -> "A great plague swept through **$loc**. The **$f** rose from the ashes, promising salvation — for a price." },
    { f, n, loc -> "An ancient artifact was unearthed near **$loc**. The **$f** claimed it, and $n was changed by its power." },
    { f, n, loc -> "$n was exiled from **$loc** and founded the **$f** in the wilderness, swearing vengeance." },
    { f, n, loc -> "The great fire of **$loc** destroyed half the settlement. The **$f** controls the reconstruction — and the debt." },
    { f, n, loc -> "A portal to the Shadowfell opened near **$loc**. The **$f** formed to guard it. $n has not been the same since." },
    { f, n, loc -> "$n discovered forbidden magic beneath **$loc** and formed the **$f** to study — or exploit — it." },
    { f, n, loc -> "The harvest failed for three years near **$loc**. The **$f** controls the food supply. $n decides who eats." },
    { f, n, loc -> "A dragon attacked **$loc**. $n slew it — or so the stories say. The **$f** was built on that legend." },
    { f, n, loc -> "$n vanished from **$loc** three months ago. The **$f** is searching — some say to rescue, others say to silence." },
    { f, n, loc -> "Strange earthquakes have been shaking **$loc**. The **$f** blames $n's experiments. $n blames something deeper." },
    { f, n, loc -> "A child was born at **$loc** bearing the mark of an ancient prophecy. The **$f** and $n both want to control the child's fate." }
)

private val RUMORS = listOf(
    "They say the old baron never died — he walks the halls of his keep, looking for his sword.",
    "A merchant paid in coins that vanished by morning.",
    "The wolves in the northern woods have started hunting in daylight.",
    "Someone saw a second moon in the sky last week. Only children admit it.",
    "The cartographer's guild erased a town from the maps. No one remembers its name.",
    "A tavern keeper stopped serving ale that turns sour the moment it hits silver.",
    "The magistrate's wife has been seen at three funerals of men she never met.",
    "Children near the ruins sing a lullaby that no one taught them.",
    "A dwarven caravan passed through carrying a coffin they refused to open.",
    "The bell of the temple rings every night at the same hour — even after the bell was stolen.",
    "A farmer plows the same field each spring, burying what he finds.",
    "The bounty board has a poster that rewrites itself when you aren't looking.",
    "Someone is buying up old war banners. No one knows who.",
    "The river stopped for a heartbeat yesterday. Then it ran again.",
    "A shepherd swears a star fell near the crags but no crater was found.",
    "The old witch-woman is accepting apprentices. Three have already disappeared.",
    "A blacksmith refuses to work with iron from the southern mine.",
    "Barrow-mist rolled through the eastern villages last week. Some came back wrong.",
    "The innkeeper at the crossroads writes down every name. Every one.",
    "A traveling priest warned of 'the seventh seal' and left in a hurry.",
    "A dog dug up coins stamped with a language that hasn't been spoken in a thousand years.",
    "The last person to climb the cliff-temple came back speaking only in rhymes.",
    "A caravan of foreign silks ran empty — everyone gone, horses still walking.",
    "A beggar in the market wears a ring worth more than the whole street.",
    "The old mill's wheel turns even when the river is frozen."
)

private val ECON_STATES = listOf(
    Triple("Thriving", "Trade booms, coffers overflow, the people eat well", 5),
    Triple("Prosperous", "Steady growth, a rising merchant class", 4),
    Triple("Stable", "Neither rich nor poor — the economy holds", 3),
    Triple("Strained", "Resources stretched thin, taxes rising", 2),
    Triple("Impoverished", "Famine lurks, beggars line the streets", 1),
    Triple("Collapsed", "Currency worthless, barter economy, desperation", 0),
    Triple("Hoarded", "Vast wealth held by the few, mass poverty below", 2),
    Triple("War Economy", "Everything funneled into the military effort", 2),
    Triple("Black Market", "Official trade dead, underground networks thrive", 1),
    Triple("Boom & Bust", "Wild swings between fortune and ruin", 3)
)

private val EXPORTS = listOf(
    "grain", "iron ore", "timber", "wool", "salt", "silver", "wine", "copper",
    "leather", "cut stone", "furs", "fish", "honey", "spices", "silk",
    "enchanted trinkets", "weapon-steel", "arcane reagents", "mythril", "parchment"
)
private val IMPORTS = listOf(
    "foreign silks", "exotic spices", "ironwood", "southern fruits", "pepper",
    "dyes", "alchemical reagents", "glass", "ivory", "warhorses", "books",
    "cotton", "porcelain", "preserved fish", "tea", "cedar planks", "brass"
)
private val GOV_FORMS = listOf(
    "monarchy", "theocracy", "oligarchy", "merchant republic", "tribal council",
    "inquisition", "magocracy", "dictatorship", "federation", "matriarchy",
    "patriarchy", "warband", "commune", "dynasty"
)
private val SUCCESSIONS = listOf(
    "primogeniture", "divine election", "trial by combat", "merchant vote",
    "assassination", "popular acclamation", "ritual selection", "hereditary"
)
private val RULER_TRAITS = listOf(
    "paranoid", "just", "cruel", "scholarly", "warlike", "pious", "cunning",
    "benevolent", "reclusive", "feasted", "tyrannical", "weak", "charismatic",
    "tormented", "zealous"
)
private val MOODS = listOf(
    "restless", "content", "fearful", "defiant", "oppressed", "prosperous",
    "despairing", "fanatical", "apathetic", "hopeful"
)
private val GOALS = listOf(
    "expand territory", "defend the realm", "uncover an ancient truth",
    "enrich the treasury", "purge heretics", "seize the throne",
    "restore the old ways", "preserve the peace", "hunt a single enemy",
    "outlast their rivals"
)
private val DISPOSITIONS = listOf(
    "guarded to strangers", "hospitable to travellers", "openly hostile to outsiders",
    "suspicious of all magic", "welcoming of kindred souls"
)

object LoreGen {
    fun generate(worldMap: WorldMap, seed: Long = System.currentTimeMillis()): WorldLore {
        val rand = Random(seed)
        val worldName = WORLD_NAME_PATTERNS.random(rand)(rand)
        val era = ERA_LABELS.random(rand)

        val factionCount = 2 + rand.nextInt(3)
        val factions = mutableListOf<Faction>()
        val usedBases = mutableSetOf<String>()
        val locs = worldMap.locations.filter { it.type in listOf("town", "city", "castle") }
        for (i in 0 until minOf(factionCount, locs.size)) {
            val loc = locs.filter { it.name !in usedBases }.randomOrNull(rand) ?: locs.random(rand)
            usedBases += loc.name
            val name = "The " + FACTION_ADJS.random(rand) + " " + FACTION_NOUNS.random(rand)
            val type = FACTION_TYPES.random(rand)
            val govForm = GOV_FORMS.random(rand)
            val ruler = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand)
            val currency = listOf("gold", "silver marks", "crowns", "shards", "talons", "bones", "scales").random(rand)
            val econ = ECON_STATES.random(rand)
            val government = GovernmentInfo(
                form = govForm,
                ruler = ruler,
                capital = loc.name,
                rulerTrait = RULER_TRAITS.random(rand),
                yearsInPower = 1 + rand.nextInt(45),
                dynasty = if (rand.nextFloat() < 0.5f) "House " + FACTION_NOUNS.random(rand) else "None",
                succession = SUCCESSIONS.random(rand),
                pastRulers = List(2 + rand.nextInt(3)) {
                    PastRuler(
                        name = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand),
                        yearsAgo = (it + 1) * (10 + rand.nextInt(30)),
                        fate = listOf(
                            "died in battle", "assassinated", "abdicated",
                            "vanished without trace", "died of plague",
                            "executed for heresy", "overthrown", "died of old age",
                            "lost to madness"
                        ).random(rand)
                    )
                }
            )
            val economy = EconomyInfo(
                level = econ.first,
                wealth = econ.third,
                description = econ.second,
                exports = EXPORTS.shuffled(rand).take(3),
                imports = IMPORTS.shuffled(rand).take(3),
                tax = when (econ.first) {
                    "Thriving" -> "5% on trade, negligible head-tax"
                    "Prosperous" -> "8% on trade, small head-tax"
                    "Stable" -> "10% on trade, modest head-tax"
                    "Strained" -> "15% on trade, rising head-tax"
                    "Impoverished" -> "20% on trade, heavy head-tax"
                    "Collapsed" -> "currency worthless — levies in goods"
                    "Hoarded" -> "noble exemption, crushing commoner tax"
                    "War Economy" -> "war tithe, conscription in lieu"
                    "Black Market" -> "official 12%, nobody pays"
                    else -> "unpredictable assessment"
                }
            )
            factions += Faction(
                name = name,
                type = type,
                description = "A $type ruling from ${loc.name}. Known for ${listOf("cruelty", "honor", "secrecy", "wealth", "war", "scholarship", "zeal").random(rand)}.",
                baseLoc = loc.name,
                government = government,
                economy = economy,
                population = listOf("handful of villages", "a dozen towns", "a great city and outskirts", "scattered strongholds", "an entire kingdom").random(rand),
                mood = MOODS.random(rand),
                disposition = DISPOSITIONS.random(rand),
                goal = GOALS.random(rand),
                currency = currency
            )
        }
        val npcs = mutableListOf<LoreNpc>()
        factions.forEach { f ->
            npcs += LoreNpc(
                name = f.government?.ruler ?: NPC_FIRSTS.random(rand),
                race = RACES.random(rand),
                role = NPC_ROLES.random(rand),
                age = (20 + rand.nextInt(80)).toString(),
                appearance = listOf("scarred face", "cold eyes", "ornate robes", "calloused hands", "noble bearing", "elaborate tattoos").random(rand),
                personality = listOf("ambitious", "paranoid", "benevolent", "cruel", "melancholy", "zealous", "charismatic").random(rand),
                location = f.baseLoc,
                faction = f.name
            )
        }
        // Add 2-5 independent NPCs not attached to factions
        repeat(2 + rand.nextInt(4)) {
            val home = worldMap.locations.random(rand).name
            npcs += LoreNpc(
                name = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand),
                race = RACES.random(rand),
                role = NPC_ROLES.random(rand),
                age = (18 + rand.nextInt(70)).toString(),
                appearance = listOf("wind-burnt skin", "a quick smile", "haunted eyes", "ink-stained fingers", "a drifter's posture").random(rand),
                personality = listOf("curious", "world-weary", "mischievous", "kind", "disinterested").random(rand),
                location = home,
                faction = null
            )
        }

        // Primordial + era events
        val primordial = PRIMORDIAL_EVENTS.shuffled(rand).take(3).map { it(worldMap.locations.first().name) }

        val history = buildList {
            primordial.forEachIndexed { i, text ->
                add(HistoryEntry(era = "primordial", year = -2000 - i * 100, text = text))
            }
            repeat(3) {
                if (factions.isEmpty()) return@repeat
                val f = factions.random(rand)
                val n = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand)
                val loc = worldMap.locations.random(rand).name
                add(HistoryEntry("ancient", -800 + it * 120 + rand.nextInt(40), ANCIENT_EVENTS.random(rand)(f.name, n, loc)))
            }
            repeat(4) {
                if (factions.isEmpty()) return@repeat
                val f = factions.random(rand)
                val n = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand)
                val loc = worldMap.locations.random(rand).name
                add(HistoryEntry("medieval", -400 + it * 80 + rand.nextInt(20), MEDIEVAL_EVENTS.random(rand)(f.name, n, loc)))
            }
            if (rand.nextFloat() < 0.7f) {
                repeat(2) {
                    if (factions.isEmpty()) return@repeat
                    val f = factions.random(rand)
                    val n = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand)
                    val loc = worldMap.locations.random(rand).name
                    add(HistoryEntry("dark_age", -150 + it * 40 + rand.nextInt(10), DARK_AGE_EVENTS.random(rand)(f.name, n, loc)))
                }
            }
            repeat(3) {
                if (factions.isEmpty()) return@repeat
                val f = factions.random(rand)
                val n = NPC_FIRSTS.random(rand) + " " + NPC_TITLES.random(rand)
                val loc = worldMap.locations.random(rand).name
                add(HistoryEntry("recent", -20 + it * 8 + rand.nextInt(4), RECENT_EVENTS.random(rand)(f.name, n, loc)))
            }
        }.sortedBy { it.year }

        val pickedMutations = Mutations.pickForWorld(rand)
        val mutations = pickedMutations.map { "${it.icon} ${it.name} — ${it.desc}" }
        val mutationIds = pickedMutations.map { it.id }

        val rumors = RUMORS.shuffled(rand).take(6 + rand.nextInt(4))

        return WorldLore(
            factions = factions,
            npcs = npcs,
            primordial = primordial,
            mutations = mutations,
            mutationIds = mutationIds,
            rumors = rumors,
            worldName = worldName,
            era = era,
            history = history
        )
    }

    fun findLocalFaction(wm: WorldMap, lore: WorldLore?, locId: Int): Faction? {
        if (lore == null) return null
        val loc = wm.locations.getOrNull(locId) ?: return null
        var best: Faction? = null
        var bestDist = Double.MAX_VALUE
        for (f in lore.factions) {
            val base = wm.locations.firstOrNull { it.name == f.baseLoc } ?: continue
            val d = kotlin.math.hypot((loc.x - base.x).toDouble(), (loc.y - base.y).toDouble())
            if (d < bestDist) { bestDist = d; best = f }
        }
        return best
    }
}
