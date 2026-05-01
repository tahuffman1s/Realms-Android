package com.realmsoffate.game.game

import com.realmsoffate.game.data.PlayerNamePool
import kotlin.random.Random

internal data class RandomizedCharacter(
    val name: String,
    val gender: String,
    val ageBand: String,
    val skinTone: String,
    val hairColor: String,
    val hairStyle: String,
    val build: String,
    val race: String,
    val cls: String,
    val baseStats: IntArray,
    val primaryBonus: Int,
    val secondaryBonus: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RandomizedCharacter) return false
        return name == other.name && gender == other.gender && ageBand == other.ageBand &&
            skinTone == other.skinTone && hairColor == other.hairColor &&
            hairStyle == other.hairStyle && build == other.build &&
            race == other.race && cls == other.cls &&
            baseStats.contentEquals(other.baseStats) &&
            primaryBonus == other.primaryBonus && secondaryBonus == other.secondaryBonus
    }

    override fun hashCode(): Int {
        var h = name.hashCode()
        h = 31 * h + gender.hashCode()
        h = 31 * h + ageBand.hashCode()
        h = 31 * h + skinTone.hashCode()
        h = 31 * h + hairColor.hashCode()
        h = 31 * h + hairStyle.hashCode()
        h = 31 * h + build.hashCode()
        h = 31 * h + race.hashCode()
        h = 31 * h + cls.hashCode()
        h = 31 * h + baseStats.contentHashCode()
        h = 31 * h + primaryBonus
        h = 31 * h + secondaryBonus
        return h
    }
}

internal object RandomCharacter {

    private val GENDERS = listOf("Male", "Female", "Non-binary", "Unspecified")
    private val AGE_BANDS = listOf("Young", "Adult", "Mature", "Elder")
    private val SKIN_TONES = listOf(
        "#F7D4B9", "#E8BC95", "#D19A72", "#B67B4F",
        "#935531", "#6B3A1A", "#4A2509", "#8C5A3C", "#C49C7C"
    )
    private val HAIR_COLORS = listOf(
        "#2A1E10", "#5A4330", "#8C6A44", "#B89066",
        "#D9B070", "#E8D8B0", "#9E9E9E", "#C0392B"
    )
    private val HAIR_STYLES = listOf(
        "Short", "Long", "Braided", "Shaved", "Bun", "Ponytail", "Tousled", "Flowing"
    )
    private val BUILDS = listOf("Lean", "Average", "Muscular", "Stocky", "Hulking")

    fun generate(rng: Random = Random.Default): RandomizedCharacter {
        val race = Races.list.random(rng)
        val cls = Classes.list.random(rng)
        val (primary, secondary) = race.defaultBonusIndices()
        val safeSecondary = if (primary == secondary) {
            (0..5).filter { it != primary }.random(rng)
        } else {
            secondary
        }
        return RandomizedCharacter(
            name = PlayerNamePool.NAMES.random(rng),
            gender = GENDERS.random(rng),
            ageBand = AGE_BANDS.random(rng),
            skinTone = SKIN_TONES.random(rng),
            hairColor = HAIR_COLORS.random(rng),
            hairStyle = HAIR_STYLES.random(rng),
            build = BUILDS.random(rng),
            race = race.name,
            cls = cls.name,
            baseStats = cls.recommended.toIntArray(),
            primaryBonus = primary,
            secondaryBonus = safeSecondary
        )
    }
}
