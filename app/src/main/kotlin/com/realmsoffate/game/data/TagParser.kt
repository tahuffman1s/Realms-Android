package com.realmsoffate.game.data

/**
 * Parses the LLM narration output for mechanical tags like [DAMAGE:N], [ITEM:…],
 * [NPC_MET:…], [QUEST_START:…], [CHOICES]…[/CHOICES], etc.
 *
 * The `replyCleaned` field strips parsed tags from the display text while preserving
 * narration. The individual lists carry the side-effects for the ViewModel to apply.
 */
data class ParsedReply(
    val scene: String,
    val sceneDesc: String,
    val narration: String,
    val choices: List<Choice>,
    val damage: Int,
    val heal: Int,
    val xp: Int,
    val goldGained: Int,
    val goldLost: Int,
    val itemsGained: List<Item>,
    val checks: List<CheckResult>,
    val npcsMet: List<LogNpc>,
    val questStarts: List<Quest>,
    val questUpdates: List<Pair<String, String>>,
    val questComplete: List<String>,
    val questFails: List<String>,
    val shops: List<Pair<String, Map<String, Int>>>,
    val travelTo: String?,
    val partyJoins: List<PartyCompanion>,
    val timeOfDay: String?,
    val moralDelta: Int,
    val repDeltas: List<Pair<String, Int>>,
    val worldEventHook: String?,
    /**
     * Per-NPC dialogue lines captured from blockquote-formatted narration.
     * Keyed by NPC name, each entry is a `Pair(turn, quote)`.
     */
    val dialogues: Map<String, List<Pair<Int, String>>> = emptyMap(),
    /** [CONDITION:name] tags — apply to character's conditions list. */
    val conditionsAdded: List<String> = emptyList(),
    /** [REMOVE_CONDITION:name] tags — remove from character's conditions list. */
    val conditionsRemoved: List<String> = emptyList(),
    /** [REMOVE_ITEM:name] tags — dropped/consumed items. */
    val itemsRemoved: List<String> = emptyList()
)

data class CheckResult(
    val skill: String, val ability: String, val dc: Int,
    val passed: Boolean, val total: Int
)

object TagParser {
    private val tagPattern = Regex("\\[([A-Z_]+):([^\\[\\]]*?)\\]")
    private val scenePattern = Regex("\\[SCENE:([^|\\]]+)\\|([^\\]]+)\\]")
    private val choicesPattern = Regex("\\[CHOICES\\]([\\s\\S]*?)\\[/CHOICES\\]")
    private val choiceLinePattern = Regex("^\\s*\\d+\\.\\s*(.+?)\\s*\\[([^\\]]+)\\]\\s*$")

    // Fallback prose patterns — used when the narrator skips the mandatory tag.
    private val proseDamage = Regex(
        "(\\d+)\\s*(?:points?\\s*of\\s*)?(?:bludgeoning|slashing|piercing|fire|cold|lightning|acid|poison|psychic|necrotic|radiant|thunder|force)?\\s*damage",
        RegexOption.IGNORE_CASE
    )
    private val proseHeal = Regex(
        "(?:heal|recover|restore|regenerate)[a-z]*\\s+(\\d+)\\s*(?:hp|health|hit\\s*points)?",
        RegexOption.IGNORE_CASE
    )
    private val proseGoldGain = Regex(
        "(?:find|gain|receive|pocket|collect|earn|loot|steal)[a-z]*\\s+(\\d+)\\s*(?:gold|coins|crowns|silver|gp)",
        RegexOption.IGNORE_CASE
    )
    private val proseGoldLost = Regex(
        "(?:spend|pay|give|hand|toss|drop|bribe|lose)[a-z]*\\s+(\\d+)\\s*(?:gold|coins|crowns|silver|gp)",
        RegexOption.IGNORE_CASE
    )
    private val proseXp = Regex(
        "(?:gain|earn|receive)[a-z]*\\s+(\\d+)\\s*(?:xp|experience)",
        RegexOption.IGNORE_CASE
    )
    // NPC dialogue — **Name:** on one line followed by > "quote" on the next.
    // Works across the emoji-prefixed format: `🧔 **Greta Ironjaw:**\n> "..."`.
    private val dialoguePattern = Regex(
        """\*\*([A-Z][A-Za-z' \-]+?):\*\*\s*\n\s*>\s*["“]([^"”]+)["”]""",
        setOf(RegexOption.MULTILINE)
    )

    fun parse(raw: String, currentTurn: Int): ParsedReply {
        var scene = "default"
        var sceneDesc = ""
        scenePattern.find(raw)?.let {
            scene = it.groupValues[1].trim()
            sceneDesc = it.groupValues[2].trim()
        }

        val choices = mutableListOf<Choice>()
        choicesPattern.find(raw)?.let { m ->
            m.groupValues[1].lines().forEachIndexed { idx, line ->
                choiceLinePattern.matchEntire(line.trim())?.let {
                    choices += Choice(
                        n = choices.size + 1,
                        text = it.groupValues[1].trim(),
                        skill = it.groupValues[2].trim()
                    )
                }
            }
        }

        var damage = 0; var heal = 0; var xp = 0
        var goldGained = 0; var goldLost = 0
        val items = mutableListOf<Item>()
        val checks = mutableListOf<CheckResult>()
        val npcs = mutableListOf<LogNpc>()
        val qStarts = mutableListOf<Quest>()
        val qUpdates = mutableListOf<Pair<String, String>>()
        val qComplete = mutableListOf<String>()
        val qFails = mutableListOf<String>()
        val shops = mutableListOf<Pair<String, Map<String, Int>>>()
        var travelTo: String? = null
        val parties = mutableListOf<PartyCompanion>()
        var tod: String? = null
        var moral = 0
        val reps = mutableListOf<Pair<String, Int>>()
        val conditionsAdded = mutableListOf<String>()
        val conditionsRemoved = mutableListOf<String>()
        val itemsRemoved = mutableListOf<String>()

        for (m in tagPattern.findAll(raw)) {
            val type = m.groupValues[1]
            val body = m.groupValues[2]
            when (type) {
                "DAMAGE" -> damage += body.trim().toIntOrNull() ?: 0
                "HEAL" -> heal += body.trim().toIntOrNull() ?: 0
                "XP" -> xp += body.trim().toIntOrNull() ?: 0
                "GOLD" -> goldGained += body.trim().toIntOrNull() ?: 0
                "GOLD_LOST" -> goldLost += body.trim().toIntOrNull() ?: 0
                "ITEM" -> {
                    val p = body.split("|").map { it.trim() }
                    if (p.isNotEmpty()) items += Item(
                        name = p[0],
                        desc = p.getOrNull(1) ?: "",
                        type = p.getOrNull(2) ?: "item",
                        rarity = p.getOrNull(3) ?: "common"
                    )
                }
                "CHECK" -> {
                    val p = body.split("|").map { it.trim() }
                    if (p.size >= 5) checks += CheckResult(
                        skill = p[0], ability = p[1],
                        dc = p[2].toIntOrNull() ?: 10,
                        passed = p[3].equals("PASS", ignoreCase = true),
                        total = p[4].toIntOrNull() ?: 0
                    )
                }
                "NPC_MET" -> {
                    val p = body.split("|").map { it.trim() }
                    if (p.isNotEmpty()) npcs += LogNpc(
                        name = p[0],
                        race = p.getOrNull(1) ?: "",
                        role = p.getOrNull(2) ?: "",
                        age = p.getOrNull(3) ?: "",
                        relationship = p.getOrNull(4) ?: "neutral",
                        appearance = p.getOrNull(5) ?: "",
                        personality = p.getOrNull(6) ?: "",
                        thoughts = p.getOrNull(7) ?: "",
                        metTurn = currentTurn, lastSeenTurn = currentTurn
                    )
                }
                "QUEST_START" -> {
                    val p = body.split("|").map { it.trim() }
                    if (p.size >= 6) {
                        val objs = p[4].split(";").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                        qStarts += Quest(
                            id = "q_${System.currentTimeMillis()}_${qStarts.size}",
                            title = p[0], type = p[1], desc = p[2], giver = p[3],
                            location = "", objectives = objs, reward = p.getOrNull(5) ?: "",
                            turnStarted = currentTurn
                        )
                    }
                }
                "QUEST_UPDATE" -> {
                    val p = body.split("|").map { it.trim() }
                    if (p.size >= 2) qUpdates += p[0] to p[1]
                }
                "QUEST_COMPLETE" -> qComplete += body.trim()
                "QUEST_FAIL" -> qFails += body.trim()
                "SHOP" -> {
                    val p = body.split("|").map { it.trim() }
                    if (p.size >= 2) {
                        val items2 = p[1].split(",").mapNotNull {
                            val pp = it.split(":").map { s -> s.trim() }
                            if (pp.size == 2) pp[0] to (pp[1].toIntOrNull() ?: 0) else null
                        }.toMap()
                        shops += p[0] to items2
                    }
                }
                "TRAVEL" -> travelTo = body.trim()
                "PARTY_JOIN" -> {
                    val p = body.split("|").map { it.trim() }
                    if (p.size >= 5) parties += PartyCompanion(
                        name = p[0], race = p[1], role = p[2],
                        level = p[3].toIntOrNull() ?: 1,
                        maxHp = p[4].toIntOrNull() ?: 10,
                        hp = p[4].toIntOrNull() ?: 10,
                        appearance = p.getOrNull(5) ?: "",
                        personality = p.getOrNull(6) ?: "",
                        joinedTurn = currentTurn
                    )
                }
                "TIME" -> tod = body.trim()
                "MORAL" -> moral += body.trim().toIntOrNull() ?: 0
                "REP" -> {
                    val p = body.split("|").map { it.trim() }
                    if (p.size == 2) reps += p[0] to (p[1].toIntOrNull() ?: 0)
                }
                "CONDITION" -> {
                    val c = body.trim()
                    if (c.isNotBlank()) conditionsAdded += c
                }
                "REMOVE_CONDITION" -> {
                    val c = body.trim()
                    if (c.isNotBlank()) conditionsRemoved += c
                }
                "REMOVE_ITEM" -> {
                    val c = body.trim()
                    if (c.isNotBlank()) itemsRemoved += c
                }
            }
        }

        // Strip the mechanical tags from the narration body but keep SCENE at top as header.
        var narration = raw
        narration = scenePattern.replace(narration, "")
        narration = choicesPattern.replace(narration, "")
        narration = tagPattern.replace(narration, "")
        narration = narration.trim().replace(Regex("\\n{3,}"), "\n\n")

        // ---- Contextual prose fallbacks ----
        // Only apply when the narrator forgot the explicit tag. Caps each bucket
        // so a vivid description doesn't nuke HP twice.
        if (damage == 0) {
            damage = proseDamage.findAll(narration)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .sum()
                .coerceAtMost(50)
        }
        if (heal == 0) {
            heal = proseHeal.findAll(narration)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .sum()
                .coerceAtMost(50)
        }
        if (goldGained == 0) {
            goldGained = proseGoldGain.findAll(narration)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .sum()
                .coerceAtMost(500)
        }
        if (goldLost == 0) {
            goldLost = proseGoldLost.findAll(narration)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .sum()
                .coerceAtMost(500)
        }
        if (xp == 0) {
            xp = proseXp.findAll(narration)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .sum()
                .coerceAtMost(1000)
        }

        // ---- Dialogue extraction for the NPC journal ----
        val dialogues = mutableMapOf<String, MutableList<Pair<Int, String>>>()
        dialoguePattern.findAll(narration).forEach { m ->
            val name = m.groupValues[1].trim()
            val quote = m.groupValues[2].trim()
            if (name.isNotBlank() && quote.isNotBlank()) {
                dialogues.getOrPut(name) { mutableListOf() }.add(currentTurn to quote)
            }
        }

        return ParsedReply(
            scene, sceneDesc, narration, choices, damage, heal, xp,
            goldGained, goldLost, items, checks, npcs,
            qStarts, qUpdates, qComplete, qFails, shops,
            travelTo, parties, tod, moral, reps,
            worldEventHook = null,
            dialogues = dialogues,
            conditionsAdded = conditionsAdded,
            conditionsRemoved = conditionsRemoved,
            itemsRemoved = itemsRemoved
        )
    }
}
