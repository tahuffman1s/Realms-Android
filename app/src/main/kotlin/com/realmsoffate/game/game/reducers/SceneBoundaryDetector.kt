package com.realmsoffate.game.game.reducers

/**
 * Pure function that compares two game state snapshots and emits a boundary
 * reason if the scene has meaningfully changed. The scene-summarizer runs
 * whenever this returns non-null.
 *
 * Precedence: LOCATION_CHANGED > COMBAT_* > SCENE_TAG_CHANGED. Location is
 * the strongest signal; a combat transition inside one location still earns
 * a summary (so post-combat notes don't leak into the next fight).
 */
object SceneBoundaryDetector {
    enum class Reason {
        LOCATION_CHANGED,
        SCENE_TAG_CHANGED,
        COMBAT_STARTED,
        COMBAT_ENDED
    }

    data class Snapshot(
        val sceneTag: String,
        val locationName: String,
        val inCombat: Boolean
    )

    fun detect(prev: Snapshot, cur: Snapshot): Reason? {
        if (prev.locationName != cur.locationName && cur.locationName.isNotBlank()) {
            return Reason.LOCATION_CHANGED
        }
        if (prev.inCombat != cur.inCombat) {
            return if (cur.inCombat) Reason.COMBAT_STARTED else Reason.COMBAT_ENDED
        }
        if (prev.sceneTag != cur.sceneTag) {
            return Reason.SCENE_TAG_CHANGED
        }
        return null
    }
}
