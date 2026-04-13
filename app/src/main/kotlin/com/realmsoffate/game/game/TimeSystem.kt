package com.realmsoffate.game.game

/**
 * Contextual day/night accumulator. Actions add "time weight"; each threshold
 * crossed advances the time of day by one phase (dawn → day → dusk → night → dawn).
 *
 * Ported from realms_of_fate.html:
 *   travel = 4, craft/wait = 3, social/explore = 2, combat = 1, shop = 1.
 *   Threshold per phase = 6.
 */
object TimeSystem {
    private const val THRESHOLD = 6

    private val actionWeights = mapOf(
        "travel" to 4,
        "wait" to 3, "craft" to 3, "rest" to 3,
        "social" to 2, "explore" to 2, "investigate" to 2,
        "combat" to 1, "shop" to 1, "dialogue" to 1
    )

    /**
     * Advances time by weight. Returns the new phase and the residual accumulator.
     */
    data class Tick(val phase: String, val accumulator: Int)

    fun advance(phase: String, accumulator: Int, actionType: String): Tick {
        val add = actionWeights[actionType] ?: 1
        var acc = accumulator + add
        var p = phase
        while (acc >= THRESHOLD) {
            acc -= THRESHOLD
            p = nextPhase(p)
        }
        return Tick(p, acc)
    }

    /** Classifies a player action string into an action-type bucket by keyword heuristics. */
    fun classifyAction(action: String): String {
        val s = action.lowercase()
        return when {
            "travel" in s || "walk" in s || "journey" in s || "ride" in s || "head to" in s -> "travel"
            "rest" in s || "sleep" in s || "camp" in s -> "rest"
            "wait" in s || "listen" in s -> "wait"
            "craft" in s || "forge" in s || "brew" in s -> "craft"
            "search" in s || "explore" in s || "look" in s || "investigate" in s -> "explore"
            "talk" in s || "ask" in s || "tell" in s || "say" in s || "speak" in s -> "social"
            "attack" in s || "fight" in s || "strike" in s || "cast" in s -> "combat"
            "buy" in s || "sell" in s || "shop" in s -> "shop"
            else -> "dialogue"
        }
    }

    fun nextPhase(cur: String): String = when (cur) {
        "dawn" -> "day"
        "day" -> "dusk"
        "dusk" -> "night"
        "night" -> "dawn"
        else -> "day"
    }

    fun isNight(phase: String): Boolean = phase == "night"
}
