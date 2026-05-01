package com.realmsoffate.game.data.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Parity tests pinning Phase 6 M2 JSON content to the historical Kotlin literals
 * they replaced. Each expected value is a verbatim copy of the literal that lived
 * in source before the migration. Failures indicate the JSON drifted from the
 * original content (or vice versa) — they do NOT indicate a desired change.
 *
 * Update both sides together if intentional content edits land in a later phase.
 */
@RunWith(RobolectricTestRunner::class)
class M2ContentParityTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun init() {
        ContentRepository.initialize(ctx)
    }

    @Test fun `faction types match historical`() {
        assertEquals(EXPECTED_FACTION_TYPES, ContentRepository.factionTypes)
    }
    @Test fun `faction adjectives match historical`() {
        assertEquals(EXPECTED_FACTION_ADJS, ContentRepository.factionAdjectives)
    }
    @Test fun `faction nouns match historical`() {
        assertEquals(EXPECTED_FACTION_NOUNS, ContentRepository.factionNouns)
    }
    @Test fun `npc firsts match historical`() {
        assertEquals(EXPECTED_NPC_FIRSTS, ContentRepository.npcFirsts)
    }
    @Test fun `npc titles match historical`() {
        assertEquals(EXPECTED_NPC_TITLES, ContentRepository.npcTitles)
    }
    @Test fun `npc roles match historical`() {
        assertEquals(EXPECTED_NPC_ROLES, ContentRepository.npcRoles)
    }
    @Test fun `era labels match historical`() {
        assertEquals(EXPECTED_ERA_LABELS, ContentRepository.eraLabels)
    }
    @Test fun `rumors match historical`() {
        assertEquals(EXPECTED_RUMORS, ContentRepository.rumors)
    }
    @Test fun `economy states match historical`() {
        assertEquals(EXPECTED_ECON_STATES, ContentRepository.economyStates)
    }
    @Test fun `exports match historical`() {
        assertEquals(EXPECTED_EXPORTS, ContentRepository.exports)
    }
    @Test fun `imports match historical`() {
        assertEquals(EXPECTED_IMPORTS, ContentRepository.imports)
    }
    @Test fun `government forms match historical`() {
        assertEquals(EXPECTED_GOV_FORMS, ContentRepository.governmentForms)
    }
    @Test fun `succession types match historical`() {
        assertEquals(EXPECTED_SUCCESSIONS, ContentRepository.successionTypes)
    }
    @Test fun `ruler traits match historical`() {
        assertEquals(EXPECTED_RULER_TRAITS, ContentRepository.rulerTraits)
    }
    @Test fun `moods match historical`() {
        assertEquals(EXPECTED_MOODS, ContentRepository.moods)
    }
    @Test fun `goals match historical`() {
        assertEquals(EXPECTED_GOALS, ContentRepository.goals)
    }
    @Test fun `dispositions match historical`() {
        assertEquals(EXPECTED_DISPOSITIONS, ContentRepository.dispositions)
    }
    @Test fun `backstory origins match historical`() {
        assertEquals(EXPECTED_ORIGINS, ContentRepository.backstoryOrigins)
    }
    @Test fun `backstory motivations match historical`() {
        assertEquals(EXPECTED_MOTIVATIONS, ContentRepository.backstoryMotivations)
    }
    @Test fun `backstory dark secrets match historical`() {
        assertEquals(EXPECTED_DARK_SECRETS, ContentRepository.backstoryDarkSecrets)
    }
    @Test fun `backstory enemy archetypes match historical`() {
        assertEquals(EXPECTED_ENEMY_ARCHETYPES, ContentRepository.backstoryEnemyArchetypes)
    }
    @Test fun `backstory lost items match historical`() {
        assertEquals(EXPECTED_LOST_ITEMS, ContentRepository.backstoryLostItems)
    }
    @Test fun `backstory bonds match historical`() {
        assertEquals(EXPECTED_BONDS, ContentRepository.backstoryBonds)
    }
    @Test fun `backstory flaws match historical`() {
        assertEquals(EXPECTED_FLAWS, ContentRepository.backstoryFlaws)
    }
    @Test fun `backstory prophecies match historical with nulls`() {
        assertEquals(EXPECTED_PROPHECIES, ContentRepository.backstoryProphecies)
    }
    @Test fun `location templates match historical`() {
        assertEquals(EXPECTED_LOC_TEMPLATES, ContentRepository.locationTemplates)
    }
    @Test fun `player names match historical`() {
        assertEquals(EXPECTED_PLAYER_NAMES, ContentRepository.playerNames)
    }

    companion object {
        private val EXPECTED_FACTION_TYPES = listOf(
            "kingdom", "guild", "cult", "order", "tribe", "council", "coven", "cartel",
            "syndicate", "brotherhood", "sisterhood", "legion", "consortium", "cabal",
            "covenant", "conclave", "inquisition", "rebellion", "empire", "federation",
            "commune", "circle", "lodge", "sept", "house", "dynasty", "horde",
            "theocracy", "merchant republic"
        )
        private val EXPECTED_FACTION_ADJS = listOf(
            "Iron", "Shadow", "Golden", "Crimson", "Silver", "Obsidian", "Azure", "Jade",
            "Ashen", "Radiant", "Hollow", "Dread", "Frost", "Storm", "Blood", "Moonlit",
            "Scarlet", "Ivory", "Emerald", "Onyx", "Amber", "Cobalt", "Violet", "Bone",
            "Rust", "Veiled", "Silent", "Burning", "Frozen", "Shattered", "Eternal",
            "Gilded", "Thorned", "Starless", "Pale", "Verdant", "Sable", "Weeping",
            "Prismatic", "Arcane", "Hallowed", "Profane", "Twilight", "Spectral",
            "Molten", "Abyssal"
        )
        private val EXPECTED_FACTION_NOUNS = listOf(
            "Crown", "Fang", "Flame", "Pact", "Hand", "Veil", "Throne", "Circle", "Blade",
            "Rose", "Serpent", "Shield", "Eye", "Tower", "Chalice", "Oath", "Raven",
            "Wolf", "Lion", "Dragon", "Talon", "Hammer", "Thorn", "Mask", "Chain",
            "Scale", "Key", "Gate", "Arrow", "Skull", "Phoenix", "Lotus", "Gryphon",
            "Hydra", "Sphinx", "Tide", "Root", "Star", "Compass", "Herald", "Whisper",
            "Eclipse", "Abyss", "Mantis", "Wyvern"
        )
        private val EXPECTED_NPC_FIRSTS = listOf(
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
            "Eamon", "Lorelei", "Dante", "Verity", "Knox", "Juniper", "Cormac", "Iris",
            "Takeshi", "Mei-Ling", "Jiro", "Yuki", "Hana", "Kenji", "Sakura", "Ryu",
            "Kaito", "Mizuki", "Taro", "Himari", "Ren", "Akemi", "Hiroshi", "Yumi",
            "Kwame", "Zuri", "Kofi", "Nia", "Tendai", "Akua", "Jabari", "Adaeze",
            "Chidi", "Zola", "Emeka", "Afia", "Seun", "Makena", "Dayo", "Imani",
            "Farid", "Zahra", "Hassan", "Layla", "Rashid", "Noor", "Khalil", "Samira",
            "Tariq", "Yasmin", "Karim", "Leila", "Basim", "Soraya", "Faris", "Rania",
            "Priya", "Arjun", "Kavi", "Indira", "Rohan", "Meera", "Vikram", "Leela",
            "Rajan", "Ananya", "Devraj", "Kavita", "Suresh", "Nalini", "Dhruv", "Padma",
            "Lucia", "Rafael", "Esperanza", "Mateo", "Valentina", "Diego", "Camila", "Emilio",
            "Sofía", "Alejandro", "Catalina", "Rodrigo", "Isadora", "Benicio", "Llorena", "Cruz",
            "Katya", "Dmitri", "Anya", "Vladislav", "Mila", "Borislav", "Nadya", "Taras",
            "Zoya", "Stanimir", "Oksana", "Radovan", "Svetla", "Mirko", "Daria", "Bogdan",
            "Ingrid", "Sigurd", "Freyja", "Gunnar", "Helga", "Leif", "Brunhilde", "Einar",
            "Ragnhild", "Halvard", "Solveig", "Torsten", "Brynja", "Ulfr", "Astrid", "Hrolf",
            "Eirik", "Branwen", "Theron", "Lysara", "Cael", "Rowena", "Aeric", "Tamara",
            "Kiran", "Odessa", "Marcellus", "Isolde", "Daxton", "Calliope", "Leander", "Sylvaine",
            "Corwin", "Nerissa", "Tyrell", "Ondine", "Baelor", "Fiora", "Cassian", "Ellara",
            "Galen", "Maelis", "Ryker", "Thessaly", "Warrick", "Vivienne", "Bastian", "Ondina",
            "Cato", "Elspeth", "Fenn", "Grielle", "Idris", "Jessamine", "Kael", "Luthien",
            "Maddox", "Nerys", "Osric", "Phaedra", "Quinn", "Rosmund", "Stavros", "Tanith",
            "Ulric", "Vaela", "Wolfram", "Xiomara", "Yorick", "Zenith", "Ambrose", "Briar",
            "Cosimo", "Delphine", "Emeric", "Finola", "Garrick", "Hypatia", "Ivar", "Junia",
            "Kestrel", "Liora", "Mercer", "Niobe", "Osgood", "Perdita", "Quillon", "Rhiannon",
            "Severin", "Theodosia", "Umber", "Veridian", "Wynn", "Xanthia", "Yael", "Zosia",
            "Adrius", "Belphoebe", "Cyrene", "Dagny", "Erasmus", "Fenella", "Godric", "Hesper",
            "Ignatius", "Jovana", "Kellan", "Lorcan", "Morrigan", "Numa", "Oleander", "Prosper",
            "Qadira", "Remiel", "Saoirse", "Tiberius", "Ursina", "Vesper", "Wilfreda", "Xena",
            "Ysolde", "Zarael", "Anatole", "Bronwyn", "Crispin", "Desdemona", "Evander", "Flavia",
            "Grantham", "Helewise", "Illyria", "Jareth", "Katriel", "Lysander"
        )
        private val EXPECTED_NPC_TITLES = listOf(
            "the Wise", "the Cruel", "Ironhand", "Shadowbane", "the Lost", "Dawnbringer",
            "Blackheart", "the Undying", "the Wanderer", "Stormcaller", "the Exile",
            "Bloodfist", "the Silent", "Nightwhisper", "the Brave", "Ashwalker",
            "the Cunning", "Doomhammer", "the Merciful", "Spellweaver", "Halfmoon",
            "the Blighted", "Stoneheart", "the Hollow", "Wyrmtongue", "Three-Fingers",
            "the Unkind", "Duskborne", "the Branded", "Grimshaw", "the Sleepless",
            "Redmantle", "the Twice-Cursed", "Bonechill", "the Reborn", "Quicksilver",
            "the Veiled", "Greywatch", "the Unbroken", "Frostblood", "the Mad",
            "Thornfield", "the Penitent", "Nightfall", "the Last", "Deeproot",
            "the Forsaken", "Brightmore", "the Scorned", "Ashveil",
            "Oathbreaker", "the Flayed", "Soulrender", "the Famished", "Voidwalker",
            "the Unmourned", "Gorethane", "the Sewn-Shut", "Marrowdrinker", "the Pale",
            "Skullwright", "the Dreaming", "Chainborn", "the Unrepentant", "Dreadmere",
            "the Guttered", "Cinderfall", "the Sightless Oracle", "Rotweave", "the Beloved",
            "Thornbriar", "the Wretched", "Gallowsmark", "the Unclean",
            "the Hollow-Eyed", "Ashbinder", "the Twice-Dead", "Crowfeeder", "the Liar King",
            "Bonewarden", "the Candlelight", "Dustwalker", "the Many-Named", "Fleshwright",
            "the Quiet Storm", "Goretide", "the Heretic", "Ironmaiden", "the Kingmaker",
            "Lampblack", "the Nameless", "Nightsoil", "the Old Wolf", "Plaguetongue",
            "the Red-Handed", "Saltblood", "the Turncoat", "Undermire", "the Vagrant Prince",
            "Wormwood", "the Yearning", "Blackthorn", "the Deathless", "Ember-Crowned",
            "the Fevered", "Gravesong", "the Hungering", "Ironside", "the Judged",
            "Knifesmile", "the Lightless", "Mothkeeper", "the Ninth", "Oathbound"
        )
        private val EXPECTED_NPC_ROLES = listOf(
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
        private val EXPECTED_ERA_LABELS = listOf(
            "Age of Cinders", "Age of Reckoning", "Age of Silence",
            "Age of Banners", "Age of the Wandering Crown", "Age of Ruin",
            "Age of the Long Winter", "Age of the Broken Seal",
            "Age of the Shattered Sun", "Age of Thorns", "Age of the Drowning",
            "Age of Iron and Bone", "Age of the Hollow Crown", "Age of Ashes",
            "Age of the Serpent", "Age of the Red Moon", "Age of Chains",
            "Age of the First Fire", "Age of the Unmaking", "Age of Whispers"
        )
        private val EXPECTED_RUMORS = listOf(
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
            "The old mill's wheel turns even when the river is frozen.",
            "A woman walks the trade road at midnight, asking each traveler the same question — but no one can remember what it was.",
            "The garrison captain hasn't slept in three weeks. He says he doesn't need to.",
            "Someone carved a name into the cornerstone of the new bridge. It hasn't been built yet.",
            "The fish in the eastern lake have started swimming in circles. All of them. At once.",
            "A blind potter makes cups that show you things when you drink from them.",
            "Three roads meet at the crossroads and a fourth appears on foggy nights.",
            "The undertaker bought a second shovel. He says one isn't going to be enough.",
            "A crow landed on the throne during the last court session and nobody dared remove it.",
            "The old tower rings like a bell when the wind blows from the north — but there's no bell inside.",
            "A mapmaker drew a country that doesn't exist. Caravans have started heading there.",
            "The baker's bread has been rising on its own, even before she adds the yeast.",
            "A mercenary company disbanded overnight. Every member claims they can't remember why.",
            "Someone stole the shadow off the statue in the square. The statue remains.",
            "The prison is empty. Not because they freed the prisoners — the cells are simply empty.",
            "A child in the market sells prophecies for a copper each. She's never been wrong.",
            "The wells in the lower quarter taste of salt. The sea is fifty miles away.",
            "A knight returned from a quest he was sent on yesterday. He says it took him seven years.",
            "The flowers in the cemetery bloom in winter and die in spring.",
            "A traveling merchant offers a box he says must never be opened. He gives it away for free.",
            "The moon had a crack in it last night. By dawn it was whole again."
        )
        private val EXPECTED_ECON_STATES = listOf(
            EconState("Thriving", "Trade booms, coffers overflow, the people eat well", 5),
            EconState("Prosperous", "Steady growth, a rising merchant class", 4),
            EconState("Stable", "Neither rich nor poor — the economy holds", 3),
            EconState("Strained", "Resources stretched thin, taxes rising", 2),
            EconState("Impoverished", "Famine lurks, beggars line the streets", 1),
            EconState("Collapsed", "Currency worthless, barter economy, desperation", 0),
            EconState("Hoarded", "Vast wealth held by the few, mass poverty below", 2),
            EconState("War Economy", "Everything funneled into the military effort", 2),
            EconState("Black Market", "Official trade dead, underground networks thrive", 1),
            EconState("Boom & Bust", "Wild swings between fortune and ruin", 3)
        )
        private val EXPECTED_EXPORTS = listOf(
            "grain", "iron ore", "timber", "wool", "salt", "silver", "wine", "copper",
            "leather", "cut stone", "furs", "fish", "honey", "spices", "silk",
            "enchanted trinkets", "weapon-steel", "arcane reagents", "mythril", "parchment"
        )
        private val EXPECTED_IMPORTS = listOf(
            "foreign silks", "exotic spices", "ironwood", "southern fruits", "pepper",
            "dyes", "alchemical reagents", "glass", "ivory", "warhorses", "books",
            "cotton", "porcelain", "preserved fish", "tea", "cedar planks", "brass"
        )
        private val EXPECTED_GOV_FORMS = listOf(
            "monarchy", "theocracy", "oligarchy", "merchant republic", "tribal council",
            "inquisition", "magocracy", "dictatorship", "federation", "matriarchy",
            "patriarchy", "warband", "commune", "dynasty"
        )
        private val EXPECTED_SUCCESSIONS = listOf(
            "primogeniture", "divine election", "trial by combat", "merchant vote",
            "assassination", "popular acclamation", "ritual selection", "hereditary"
        )
        private val EXPECTED_RULER_TRAITS = listOf(
            "paranoid", "just", "cruel", "scholarly", "warlike", "pious", "cunning",
            "benevolent", "reclusive", "feasted", "tyrannical", "weak", "charismatic",
            "tormented", "zealous"
        )
        private val EXPECTED_MOODS = listOf(
            "restless", "content", "fearful", "defiant", "oppressed", "prosperous",
            "despairing", "fanatical", "apathetic", "hopeful"
        )
        private val EXPECTED_GOALS = listOf(
            "expand territory", "defend the realm", "uncover an ancient truth",
            "enrich the treasury", "purge heretics", "seize the throne",
            "restore the old ways", "preserve the peace", "hunt a single enemy",
            "outlast their rivals",
            "broker peace between warring neighbors", "recover a stolen relic of immense power",
            "open trade with a forbidden nation", "build a weapon of mass destruction",
            "find a cure for a spreading plague", "assassinate a rival faction's leader",
            "explore uncharted territory", "forge an alliance against a greater threat",
            "overthrow their own corrupt leadership", "summon or bind a powerful entity"
        )
        private val EXPECTED_DISPOSITIONS = listOf(
            "guarded to strangers", "hospitable to travellers", "openly hostile to outsiders",
            "suspicious of all magic", "welcoming of kindred souls",
            "pragmatic and trade-focused", "fiercely independent", "bound by ancient oaths",
            "fractured by internal politics", "united under a false prophet",
            "haunted by a past atrocity", "secretly plotting expansion",
            "zealously isolationist", "ruled by fear of the unknown",
            "torn between tradition and progress"
        )
        private val EXPECTED_ORIGINS = listOf(
            "orphan of a burned village",
            "fallen noble stripped of title",
            "escaped slave",
            "disgraced acolyte",
            "merchant's black sheep",
            "deserter soldier",
            "exiled heir",
            "hermit of the wilds",
            "tavern-born bastard"
        )
        private val EXPECTED_MOTIVATIONS = listOf(
            "redemption for a past sin",
            "revenge against one who wronged you",
            "rescue of a lost loved one",
            "recovery of a stolen heirloom",
            "discovery of a hidden truth",
            "ascension to a station denied you"
        )
        private val EXPECTED_DARK_SECRETS = listOf(
            "You killed someone in self-defense — but you're not entirely sure it was self-defense.",
            "You stole a sacred relic from a temple. It's hidden where no one will find it.",
            "You were once a member of a cult. You left, but they haven't forgotten you.",
            "You carry a cursed item you can't get rid of. It whispers to you at night.",
            "You betrayed your closest friend for gold. They don't know it was you — yet.",
            "You witnessed a murder and said nothing. The killer knows you saw.",
            "You made a deal with a devil. The first payment comes due soon.",
            "You accidentally caused a fire that killed innocents. You fled and never looked back.",
            "You have a second identity no one knows about. That person is wanted for terrible crimes.",
            "You consumed something forbidden — a monster's heart, a god's blood, a lich's phylactery dust. It changed you.",
            "You died once, briefly. What you saw on the other side haunts your every waking moment.",
            "You can hear a voice no one else hears. It knows things it shouldn't.",
            "You were exiled from your homeland for something you actually did.",
            "You owe a dangerous organization an impossible debt. Collectors are coming.",
            "You carry a letter you've never opened. It's addressed to you in your own handwriting — from the future."
        )
        private val EXPECTED_ENEMY_ARCHETYPES = listOf(
            EnemyArchetype("a former mentor", "who believes you stole their greatest discovery"),
            EnemyArchetype("a scorned lover", "who swore to destroy everything you care about"),
            EnemyArchetype("a rival adventurer", "who blames you for a mission that went catastrophically wrong"),
            EnemyArchetype("a vengeful noble", "whose family you humiliated, intentionally or not"),
            EnemyArchetype("a bounty hunter", "hired by someone from your past you can't identify"),
            EnemyArchetype("a shapeshifter", "who has been impersonating you and ruining your reputation"),
            EnemyArchetype("a debt collector for a crime lord", "and the debt doubles every month"),
            EnemyArchetype("a zealot inquisitor", "who considers your very existence heretical"),
            EnemyArchetype("your own sibling", "who wants the family inheritance — all of it"),
            EnemyArchetype("a ghost", "of someone you failed to save, and it won't let you forget"),
            EnemyArchetype("a corrupt guard captain", "who framed you and needs you dead to keep the secret"),
            EnemyArchetype("a warlock's patron", "who claims you broke a pact you never agreed to")
        )
        private val EXPECTED_LOST_ITEMS = listOf(
            LostItem("a family heirloom sword", "passed down seven generations, stolen by someone you trusted"),
            LostItem("a locket containing the only portrait of someone you loved", "lost during a desperate escape"),
            LostItem("a spellbook with a single unique spell", "taken by a rival who couldn't even cast it"),
            LostItem("a map to your ancestral homeland", "which you've never seen but dream about constantly"),
            LostItem("a ring that suppresses your nightmares", "without it, the dreams are getting worse"),
            LostItem("a letter of pardon from a king", "the only proof of your innocence, now in enemy hands"),
            LostItem("a key to a vault", "you don't know what's inside, but people have killed for it"),
            LostItem("your mentor's dying research notes", "scattered across the region after their murder"),
            LostItem("a crystallized memory", "containing something you chose to forget but now desperately need"),
            LostItem("a dragon scale given to you as a child", "by a creature that said 'you'll need this when the time comes'")
        )
        private val EXPECTED_BONDS = listOf(
            "You are searching for a missing family member who disappeared under mysterious circumstances.",
            "You made a deathbed promise to a friend that you intend to keep, no matter the cost.",
            "You are protecting someone in hiding — if they're found, they die.",
            "A child in a distant town depends on the coin you send back. Miss a payment and they suffer.",
            "You carry the ashes of a fallen companion. You swore to scatter them at a sacred place.",
            "Someone saved your life once and asked for nothing in return. You've been trying to find them ever since.",
            "You left behind a community that trusted you. Guilt drives you to become worthy of that trust.",
            "An old teacher is imprisoned somewhere. You're gathering the means to free them.",
            "You have a twin. You haven't spoken in years. Last you heard, they joined a dangerous faction.",
            "A prophecy named your bloodline. You don't want it to be true."
        )
        private val EXPECTED_FLAWS = listOf(
            FlawPair("You can't resist a gamble", "When presented with a wager or game of chance, you struggle to walk away."),
            FlawPair("You trust too easily", "Your first instinct is to believe people, even when the signs say otherwise."),
            FlawPair("You're haunted by guilt", "In quiet moments, the weight of your past threatens to overwhelm you."),
            FlawPair("You're terrified of the dark", "Underground and nighttime environments fill you with primal dread."),
            FlawPair("You have a savage temper", "When provoked or insulted, violence feels like the natural answer."),
            FlawPair("You're an addict", "Ale, moonleaf, gambling — your vice has a grip on you."),
            FlawPair("You compulsively lie", "Even when the truth would serve you better, you embellish or fabricate."),
            FlawPair("You're paralyzed by indecision", "When lives hang in the balance, you freeze."),
            FlawPair("You hoard secrets", "You keep information from allies, even when sharing would help everyone."),
            FlawPair("You never forgive a slight", "A grudge, once earned, festers until you act on it."),
            FlawPair("You're recklessly brave", "You charge into danger without thinking. Your allies call it courage. It isn't."),
            FlawPair("You crave approval", "You'll go too far to impress or please people, especially authority figures.")
        )
        private val EXPECTED_PROPHECIES = listOf<String?>(
            "When the last star falls, one of mortal blood shall stand where gods once stood.",
            "The child of no kingdom shall wear the crown of all.",
            "Beware the one who walks between — neither living nor dead, neither saved nor damned.",
            "Three times you will be betrayed. The third time, you will deserve it.",
            "The blade that slays the dreamer wakes the sleeper beneath the world.",
            "You will find what you seek in the last place you would look — your own grave.",
            "A choice is coming that no prophecy can predict. Everything depends on it.",
            "The one marked by fire shall either save the realm or burn it.",
            "Your name will be spoken in halls you've never entered, by people you've never met, long after you're gone.",
            "Two roads diverge at the mountain's peak. One leads to glory, the other to truth. You cannot have both.",
            null, null, null, null, null
        )
        private val EXPECTED_LOC_TEMPLATES = listOf(
            LocTemplate("town", "🏘️", listOf(
                "Thornwick", "Blackfen", "Oakhaven", "Greystone", "Ravenhollow", "Silvermoor", "Ironbrook", "Ashenford", "Duskvale", "Redfern", "Mosswater", "Kingsreach",
                "Willowmere", "Briarcliff", "Dunmoor", "Foxhollow", "Hearthwick", "Millhaven", "Copperfield", "Ashenmere", "Pinecross", "Stonebridge", "Highthorn", "Wychfield",
                "Barrowton", "Deepwell", "Goldcrest", "Larchford", "Mudhollow", "Rookhurst", "Salthaven", "Thistledown", "Vinterton", "Westmarch", "Elmbury", "Falstead"
            )),
            LocTemplate("city", "🏛️", listOf(
                "Silverhold", "Highport", "Stormgate", "Goldharbor", "Crownspire", "Emberwatch",
                "Vaultshore", "Ironspire", "Sunharbor", "Ashenmount", "Thronefall", "Starreach", "Blackhaven", "Frostholm", "Whitecliff", "Dawnkeep",
                "Shadowport", "Flamecrest", "Tidemark", "Crystalgate", "Copperport", "Northhold", "Grandveil", "Silkshore", "Forgepeak", "Kingsridge",
                "Nightwall", "Thornhall", "Serpentine", "Wardenmere"
            )),
            LocTemplate("dungeon", "🕳️", listOf(
                "The Blackroot Depths", "Shadowmire Crypt", "Sunken Halls", "Crimson Keep", "The Hollow Pit", "Tomb of Kings",
                "The Wailing Caverns", "Bonemarrow Pit", "Ashvault", "The Drowning Halls", "Ironfang Labyrinth", "Sepulcher of Chains",
                "The Rotwood Cellar", "Voidmaw Chasm", "Scorchdeep", "The Blighted Crypt", "Grimstone Mine", "The Sundered Vault",
                "Plaguewell", "Duskspore Caves", "The Maw of Thirst", "The Forgotten Ossuary", "Nethercoil", "Windless Depths",
                "The Carrion Pit", "Bloodstone Sanctum", "The Husk", "Thornroot Burrow", "The Shackled Deep", "Cinderhollow"
            )),
            LocTemplate("forest", "🌲", listOf(
                "Whispering Woods", "Thornvale", "Moonlit Forest", "Emerald Glade", "Duskwood", "Wildroot",
                "Briartangle", "Ashen Canopy", "Silverleaf Wood", "The Rotwood", "Misthollow", "Fernwatch",
                "Nightbriar", "Tangle Reach", "Mossdeep", "Spore Hollow", "The Verdant Maze", "Ironbark Forest",
                "Crowwood", "Dewshroud", "Gloomfern", "Wolfswood", "The Petrified Grove", "Whitebirch",
                "Thornmarsh", "Shadowfen", "Oldgrowth", "Lichenveil", "Rootdeep", "Hemlockwood"
            )),
            LocTemplate("mountain", "⛰️", listOf(
                "Stormpeak", "Grimspire", "Dragon's Tooth", "Frostfang", "Ashridge",
                "Ironjaw Peak", "Thunderhelm", "The Shattered Horn", "Bonecrag", "Cindercone", "Gloomspire", "Whitecap",
                "Razorback Ridge", "The Weeping Summit", "Stonefather", "Windshear", "Hollowpeak", "Dreadhorn",
                "Cloudpiercer", "The Giant's Tooth", "Obsidian Crest", "Frostmantle", "Serpent's Spine", "Ashfall Summit",
                "The Iron Crown", "Bleakridge", "Skullcap", "Thundertop", "Gorecrag", "Splinterpeak"
            )),
            LocTemplate("ruins", "🗿", listOf(
                "Eldarian Ruins", "Fallen Spire", "Shattered Temple", "Sunken Altar",
                "The Crumbled Citadel", "Dusthaven", "Ghostwall", "The Broken Spire", "Voidheart Shrine", "Ashfall Temple",
                "The Hollow Court", "Moonwane Priory", "Blightstone", "The Forsaken Altar", "Dawnfall", "Ironwreck",
                "The Shattered Crown", "Rotstone Abbey", "Grimwatch", "The Bone Nave", "Tideswept Fort", "Ember Basilica",
                "The Sinking Throne", "Deadlight Tower", "Riftmark", "Bleached Hall", "The Mournfield", "Cindertemple",
                "Wraithstone", "Starveil"
            )),
            LocTemplate("castle", "🏰", listOf(
                "Stonewatch Keep", "Grimhold", "Redthorn Castle",
                "Blackthorn Citadel", "Ironveil Fortress", "Stormhold", "The Crimson Bastion", "Dreadfort", "Wyvernrest",
                "Shadowmere Keep", "Frosthammer Hall", "The Bone Fortress", "Ashcrown Castle", "Thornwall", "Nightspire",
                "The Gilded Keep", "Wraithguard", "Oathstone", "Grimwatch Castle", "Hellgate Fortress", "The Sunken Bastion",
                "Wolfsgate", "Siegeholm", "The Pale Fortress", "Blackiron Keep"
            )),
            LocTemplate("camp", "⛺", listOf(
                "Hunter's Rest", "Outrider Camp", "Bandit Roost",
                "Wayward Camp", "Trapper's Hollow", "Driftwood Rest", "The Burned Clearing", "Thornbrake Camp", "Ridgeline Watch",
                "Windbreak", "The Old Bivouac", "Smokepine Rest", "Frostfire Camp", "Mudflat Stop", "The Vagrant's Eye",
                "Deadfall Camp", "Creekside Rest", "Wolftooth Camp", "The Broken Wagon", "Ashpit", "Sentinel's Rest",
                "Dew Hollow", "The Lean-To", "Moorwatch", "Heather Camp"
            ))
        )
        private val EXPECTED_PLAYER_NAMES = listOf(
            "Aric", "Bryn", "Cael", "Daria", "Eira", "Faelan", "Gareth", "Hilde",
            "Ivara", "Jorah", "Kael", "Lyra", "Mirek", "Nyssa", "Orin", "Pyra",
            "Quinn", "Rhea", "Soren", "Talya", "Ulric", "Vex", "Wynn", "Xara",
            "Yorick", "Zenna", "Brann", "Calla", "Dorian", "Elowen", "Fenris",
            "Greta", "Halric", "Isolde", "Joran", "Kira", "Loras", "Marin",
            "Niko", "Ophir", "Perrin", "Rowan", "Saela", "Theron", "Vanya"
        )
    }
}
