package com.realmsoffate.game.game

import kotlin.random.Random

/**
 * 13 weather types + per-terrain probability tables ported from
 * realms_of_fate.html's WEATHER_TABLE / TERRAIN_WEATHER. Night bonuses
 * and mutation modifiers are layered in during roll().
 */
data class Weather(
    val id: String,
    val label: String,
    val icon: String,
    val nightIcon: String,
    val effects: String,
    val travelMultiplier: Double
)

object WeatherSystem {
    val table: Map<String, Weather> = mapOf(
        "clear" to Weather("clear", "Clear", "☀️", "⭐", "Good visibility. No penalties.", 1.0),
        "cloudy" to Weather("cloudy", "Overcast", "☁️", "☁️", "Muted light. Slightly gloomy.", 1.0),
        "fog" to Weather("fog", "Fog", "🌫️", "🌫️", "Visibility reduced to 30ft. Stealth easier. Ranged attacks disadvantaged beyond 30ft.", 0.8),
        "rain" to Weather("rain", "Rain", "🌧️", "🌧️", "Perception checks harder. Open flames extinguished. Tracks wash away. Mud slows travel.", 0.75),
        "storm" to Weather("storm", "Storm", "⛈️", "⛈️", "Heavy rain + thunder. Disadvantage on Perception. Ranged attacks disadvantaged. Lightning risk outdoors. Very loud — Stealth easier.", 0.5),
        "snow" to Weather("snow", "Snow", "🌨️", "🌨️", "Cold damage risk without shelter. Tracks visible. Movement slowed.", 0.6),
        "blizzard" to Weather("blizzard", "Blizzard", "❄️", "❄️", "Near-zero visibility. Extreme cold. Must seek shelter or take cold damage. Travel nearly impossible.", 0.3),
        "heatwave" to Weather("heatwave", "Heat Wave", "🔥", "🌡️", "Exhaustion risk. Water consumption doubled. Metal armor burns. CON saves to avoid fatigue.", 0.7),
        "wind" to Weather("wind", "High Wind", "💨", "💨", "Ranged attacks disadvantaged. Small creatures struggle. Flying dangerous. Conversation difficult.", 0.8),
        "eclipse" to Weather("eclipse", "Eclipse", "🌑", "🌑", "Unnatural darkness at day. Undead emboldened. Animals panic. Supernatural unease.", 0.9),
        "bloodmoon" to Weather("bloodmoon", "Blood Moon", "🔴", "🔴", "Red moonlight. Undead +2 to attacks. Lycanthropes transform. Dark magic stronger.", 0.8),
        "aurora" to Weather("aurora", "Aurora", "🌌", "🌌", "Shimmering lights in sky. Magic slightly stronger. Fey creatures drawn. Hauntingly beautiful.", 1.0),
        "mist" to Weather("mist", "Mist", "🌁", "🌁", "Light fog. Atmospheric. Slight visibility reduction. Sounds carry strangely.", 0.9)
    )

    private val terrainWeights: Map<String, Map<String, Int>> = mapOf(
        "town" to mapOf("clear" to 30, "cloudy" to 25, "rain" to 20, "fog" to 10, "storm" to 5, "wind" to 5, "mist" to 5),
        "city" to mapOf("clear" to 30, "cloudy" to 25, "rain" to 20, "fog" to 10, "storm" to 5, "wind" to 5, "mist" to 5),
        "forest" to mapOf("clear" to 15, "cloudy" to 20, "rain" to 25, "fog" to 20, "mist" to 10, "storm" to 5, "wind" to 5),
        "mountain" to mapOf("clear" to 15, "cloudy" to 15, "wind" to 20, "storm" to 15, "snow" to 15, "blizzard" to 5, "fog" to 10, "mist" to 5),
        "swamp" to mapOf("clear" to 5, "cloudy" to 15, "rain" to 25, "fog" to 30, "mist" to 15, "storm" to 10),
        "desert" to mapOf("clear" to 40, "heatwave" to 30, "wind" to 15, "storm" to 5, "cloudy" to 10),
        "cave" to mapOf("clear" to 100),
        "dungeon" to mapOf("clear" to 100),
        "camp" to mapOf("clear" to 25, "cloudy" to 25, "rain" to 20, "fog" to 10, "wind" to 10, "storm" to 5, "mist" to 5),
        "ruins" to mapOf("clear" to 20, "cloudy" to 20, "rain" to 20, "fog" to 15, "wind" to 10, "mist" to 10, "storm" to 5),
        "temple" to mapOf("clear" to 30, "cloudy" to 25, "rain" to 15, "fog" to 15, "mist" to 10, "storm" to 5),
        "port" to mapOf("clear" to 20, "cloudy" to 20, "rain" to 20, "wind" to 15, "storm" to 15, "fog" to 5, "mist" to 5),
        "road" to mapOf("clear" to 25, "cloudy" to 25, "rain" to 20, "fog" to 10, "wind" to 10, "storm" to 5, "mist" to 5)
    )

    /** Rolls a weather id for a given terrain type. Underground ("cave"/"dungeon") is always "clear". */
    fun roll(terrainType: String, rng: Random = Random.Default): String {
        val weights = terrainWeights[terrainType] ?: terrainWeights["road"]!!
        val total = weights.values.sum()
        var r = rng.nextInt(total)
        for ((id, w) in weights) {
            r -= w
            if (r < 0) return id
        }
        return "clear"
    }

    /** Applies mutation overrides — e.g. Eternal Winter forces snow/blizzard. */
    fun applyMutations(base: String, mutationIds: Set<String>): String = when {
        "eternal_winter" in mutationIds && base !in setOf("blizzard", "snow") ->
            if (Random.nextBoolean()) "snow" else "blizzard"
        "shadow_eclipse" in mutationIds -> "eclipse"
        else -> base
    }

    fun iconFor(id: String, isNight: Boolean = false): String {
        val w = table[id] ?: table["clear"]!!
        return if (isNight) w.nightIcon else w.icon
    }
}
