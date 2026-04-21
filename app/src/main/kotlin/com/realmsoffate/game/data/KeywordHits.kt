package com.realmsoffate.game.data

data class KeywordHits(
    val npcs: List<LogNpc>,
    val factions: List<Faction>,
    val locations: List<MapLocation>
) {
    companion object {
        val EMPTY = KeywordHits(emptyList(), emptyList(), emptyList())
    }
}
