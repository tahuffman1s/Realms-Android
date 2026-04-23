package com.realmsoffate.game.data

/**
 * Bundle of ground-truth entity records to inject into the user prompt.
 * The AI is instructed not to contradict these.
 */
data class CanonicalFacts(
    val npcs: List<LogNpc>,
    val factions: List<Faction>,
    val locations: List<MapLocation>,
    val currentLocationId: Int? = null,
    val adjacencies: Map<Int, List<Int>> = emptyMap()
) {
    val isEmpty: Boolean
        get() = npcs.isEmpty() && factions.isEmpty() && locations.isEmpty() && currentLocationId == null

    fun render(): String {
        if (isEmpty) return ""
        val sb = StringBuilder()
        sb.appendLine("# CANONICAL FACTS (ground truth — do not contradict)")
        renderCurrentLocation(sb)
        if (npcs.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## NPCs")
            for (n in npcs) sb.appendLine("- ${renderNpc(n)}")
        }
        if (factions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Factions")
            for (f in factions) sb.appendLine("- ${renderFaction(f)}")
        }
        val otherLocations = locations.filter { it.id != currentLocationId }
        if (otherLocations.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Locations")
            for (l in otherLocations) sb.appendLine("- ${renderLocation(l)}")
        }
        return sb.toString().trimEnd()
    }

    private fun renderCurrentLocation(sb: StringBuilder) {
        val cid = currentLocationId ?: return
        val current = locations.firstOrNull { it.id == cid } ?: return
        val adjIds = adjacencies[cid].orEmpty()
        val adjNames = adjIds.mapNotNull { id -> locations.firstOrNull { it.id == id }?.name }
        sb.appendLine()
        sb.appendLine("## Current location")
        sb.append("- ").appendLine(renderLocation(current))
        if (adjNames.isNotEmpty()) sb.appendLine("  Adjacent: ${adjNames.joinToString(", ")}")
    }

    private fun renderNpc(n: LogNpc): String {
        val parts = mutableListOf<String>()
        parts += n.name
        val details = buildString {
            val bits = mutableListOf<String>()
            if (n.race.isNotBlank()) bits += n.race
            if (n.role.isNotBlank()) bits += n.role
            if (n.faction != null) bits += "faction=${n.faction}"
            bits += "status=${n.status}"
            if (n.relationship.isNotBlank()) bits += "relationship=${n.relationship}"
            if (n.lastLocation.isNotBlank()) bits += "last seen turn ${n.lastSeenTurn} at ${n.lastLocation}"
            append(bits.joinToString(", "))
        }
        parts += "($details)"
        if (n.thoughts.isNotBlank()) parts += "thoughts: \"${n.thoughts}\""
        return parts.joinToString(" ")
    }

    private fun renderFaction(f: Faction): String = buildString {
        append(f.name)
        val bits = mutableListOf<String>()
        if (f.type.isNotBlank()) bits += f.type
        if (f.ruler.isNotBlank()) bits += "ruler: ${f.ruler}"
        if (f.disposition.isNotBlank()) bits += "disposition: ${f.disposition}"
        if (f.goal.isNotBlank()) bits += "goal: ${f.goal}"
        if (bits.isNotEmpty()) append(" (").append(bits.joinToString(", ")).append(")")
    }

    private fun renderLocation(l: MapLocation): String = buildString {
        append(l.name)
        val bits = mutableListOf<String>()
        if (l.type.isNotBlank()) bits += l.type
        if (l.discovered) bits += "discovered"
        if (bits.isNotEmpty()) append(" (").append(bits.joinToString(", ")).append(")")
    }
}
