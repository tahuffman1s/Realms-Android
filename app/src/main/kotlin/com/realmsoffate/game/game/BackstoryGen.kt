package com.realmsoffate.game.game

import com.realmsoffate.game.data.Backstory
import com.realmsoffate.game.data.WorldLore
import com.realmsoffate.game.data.WorldMap
import kotlin.random.Random

/**
 * Backstory generator ported verbatim from realms_of_fate.html:
 *   15 dark secrets, 12 personal-enemy archetypes, 10 lost items, 10 bonds,
 *   12 flaw+trigger pairs, 10 prophecies + 5 null slots (~33% no prophecy).
 *
 * Origins / motivations stay from the existing port as flavour framing.
 */

private val ORIGINS = listOf(
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

private val MOTIVATIONS = listOf(
    "redemption for a past sin",
    "revenge against one who wronged you",
    "rescue of a lost loved one",
    "recovery of a stolen heirloom",
    "discovery of a hidden truth",
    "ascension to a station denied you"
)

private val DARK_SECRETS = listOf(
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

private data class EnemyArchetype(val title: String, val motive: String)
private val ENEMY_ARCHETYPES = listOf(
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

private data class LostItem(val item: String, val why: String)
private val LOST_ITEMS = listOf(
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

private val BONDS = listOf(
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

private data class FlawPair(val flaw: String, val trigger: String)
private val FLAWS = listOf(
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

private val PROPHECIES = listOf<String?>(
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
