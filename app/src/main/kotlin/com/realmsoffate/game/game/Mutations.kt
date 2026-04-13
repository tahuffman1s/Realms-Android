package com.realmsoffate.game.game

import kotlin.random.Random

/**
 * 16 world mutations ported from realms_of_fate.html's WORLD_MUTATIONS.
 * Each world draws 2-3 at generation time. The `prompt` field is inlined
 * into the system context so the narrator bends every scene around them.
 */
data class Mutation(
    val id: String,
    val name: String,
    val icon: String,
    val desc: String,
    val prompt: String
)

object Mutations {
    val list: List<Mutation> = listOf(
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

    fun find(id: String): Mutation? = list.firstOrNull { it.id == id }

    fun pickForWorld(rng: Random = Random.Default, count: Int = 2 + rng.nextInt(2)): List<Mutation> =
        list.shuffled(rng).take(count)
}
