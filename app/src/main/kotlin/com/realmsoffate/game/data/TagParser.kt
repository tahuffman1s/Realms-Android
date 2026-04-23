package com.realmsoffate.game.data

/**
 * Indicates which parse path was taken for a given turn response.
 * Exposed on [ParsedReply] so downstream consumers (e.g. debug logging) can report it.
 */
enum class ParseSource {
    /** Envelope decoded successfully. */
    JSON,
    /** Envelope was malformed or missing — zero effects applied this turn. */
    INVALID
}

/**
 * Ordered content segments extracted from the LLM response.
 * The UI renders each type as a distinct visual element (bubble, pill, etc.).
 * When this list is non-empty it replaces the old regex-based splitNarration() heuristic.
 */
@kotlinx.serialization.Serializable
sealed interface NarrationSegmentData {
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("prose")
    data class Prose(val text: String) : NarrationSegmentData

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("aside")
    data class Aside(val text: String) : NarrationSegmentData

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("npc_dialog")
    data class NpcDialog(val name: String, val text: String) : NarrationSegmentData

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("player_dialog")
    data class PlayerDialog(val text: String) : NarrationSegmentData

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("player_action")
    data class PlayerAction(val text: String) : NarrationSegmentData

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("npc_action")
    data class NpcAction(val name: String, val text: String) : NarrationSegmentData
}

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
    /** [CONDITION:name] tags — apply to character's conditions list. */
    val conditionsAdded: List<String> = emptyList(),
    /** [REMOVE_CONDITION:name] tags — remove from character's conditions list. */
    val conditionsRemoved: List<String> = emptyList(),
    /** [REMOVE_ITEM:name] tags — dropped/consumed items. */
    val itemsRemoved: List<String> = emptyList(),
    /** [PARTY_LEAVE:Name] tags — companions who left, died, or betrayed. */
    val partyLeaves: List<String> = emptyList(),
    /** [ENEMY:Name|HP|MaxHP] tags — enemy combatants visible in the scene. */
    val enemies: List<Triple<String, Int, Int>> = emptyList(),
    /**
     * [FACTION_UPDATE:ref|field|value] — changes to faction state.
     * ref is either a faction id (slug) or display name; resolve via ViewModel lookup.
     */
    val factionUpdates: List<Triple<String, String, String>> = emptyList(),
    /**
     * [NPC_DIED:ref] — NPCs killed this turn.
     * ref is either a stable NPC id (slug) or display name; resolve via ViewModel lookup.
     */
    val npcDeaths: List<String> = emptyList(),
    /**
     * [NPC_UPDATE:ref|field|value] — NPC field changes.
     * ref is either a stable NPC id (slug) or display name; resolve via ViewModel lookup.
     */
    val npcUpdates: List<Triple<String, String, String>> = emptyList(),
    /** [LORE:text] — new lore/history entries. */
    val loreEntries: List<String> = emptyList(),
    /**
     * [NPC_QUOTE:ref|quote] — AI-flagged memorable lines for the NPC journal.
     * ref is either a stable NPC id (slug) or display name; resolve via ViewModel lookup.
     */
    val npcQuotes: List<Pair<String, String>> = emptyList(),
    /** [NARRATOR_PROSE]...[/NARRATOR_PROSE] blocks — narrator description text. */
    val narratorProse: List<String> = emptyList(),
    /** [NARRATOR_ASIDE]...[/NARRATOR_ASIDE] blocks — narrator snarky commentary. */
    val narratorAsides: List<String> = emptyList(),
    /** [PLAYER_DIALOG]...[/PLAYER_DIALOG] blocks — player character's spoken lines. */
    val playerDialogs: List<String> = emptyList(),
    /**
     * [NPC_DIALOG:ref]...[/NPC_DIALOG] blocks — NPC spoken lines.
     * ref is either a stable NPC id (slug) or display name; resolve via ViewModel lookup.
     */
    val npcDialogs: List<Pair<String, String>> = emptyList(),
    /** [PLAYER_ACTION]...[/PLAYER_ACTION] — player character's physical actions. */
    val playerActions: List<String> = emptyList(),
    /**
     * [NPC_ACTION:ref]...[/NPC_ACTION] — NPC physical actions.
     * ref is either a stable NPC id (slug) or display name; resolve via ViewModel lookup.
     */
    val npcActions: List<Pair<String, String>> = emptyList(),
    /** Ordered content segments for structured UI rendering. */
    val segments: List<NarrationSegmentData> = emptyList(),
    /** Which parse path produced this reply — JSON block or regex fallback. */
    val source: ParseSource = ParseSource.INVALID
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
    private val metadataBlockPattern = Regex("""\[METADATA]([\s\S]*?)\[/METADATA]""", RegexOption.IGNORE_CASE)

    /** Lenient JSON decoder for [METADATA] blocks — tolerates unknown keys and coerces values. */
    private val metadataJson: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Cleans NPC dialog tag content — strips body-language prefix, emoji+name prefix,
     * blockquote markers, and surrounding quotes that the AI sometimes puts inside
     * [NPC_DIALOG:Name] tags despite being told not to.
     */
    private fun cleanDialogContent(raw: String): String {
        var t = raw.trim()
        // Strip leading *body language* italic prefix (e.g. "*She leans forward.*")
        t = t.replace(Regex("""^\*[^*]+\*\s*"""), "")
        // Strip emoji + **Name:** prefix (e.g. "🍺 **Vesper:**")
        t = t.replace(Regex("""^[^\w\s]*\s*\*\*[^*]+:\*\*\s*"""), "")
        // Strip leading > blockquote markers on each line
        t = t.replace(Regex("""^>\s*""", RegexOption.MULTILINE), "")
        // Strip surrounding quotation marks
        t = t.removeSurrounding("\"").removeSurrounding("\u201C", "\u201D")
            .removeSurrounding("'").removeSurrounding("\u2018", "\u2019")
        return t.trim()
    }

    /**
     * Strips wrapping markdown emphasis (`***...***`, `**...**`, `*...*`) from content
     * that the LLM adds for flavor — but the UI already italicizes these lines, so the
     * asterisks end up rendered literally. Removes as many layers as wrap the whole
     * string cleanly (no inner `*` in the remaining content).
     */
    private fun stripWrappingEmphasis(raw: String): String {
        var t = raw.trim()
        var changed = true
        while (changed) {
            changed = false
            for (marker in listOf("***", "**", "*")) {
                if (t.length >= marker.length * 2 &&
                    t.startsWith(marker) && t.endsWith(marker)
                ) {
                    val inner = t.substring(marker.length, t.length - marker.length)
                    if (!inner.contains('*')) {
                        t = inner.trim()
                        changed = true
                        break
                    }
                }
            }
        }
        return t
    }

    /**
     * Cleans player action tag content.
     * Player actions are written in "You ..." form per the prompt, so we only
     * strip wrapping emphasis and extract a leading `*body*` block if the LLM
     * wrapped the action in asterisks.
     */
    private fun cleanPlayerActionContent(raw: String): String {
        val t = raw.trim()
        val italicMatch = Regex("""^\s*\*+([^*]+)\*+""").find(t)
        return if (italicMatch != null) italicMatch.groupValues[1].trim()
        else stripWrappingEmphasis(t)
    }

    /**
     * Cleans and formats NPC action tag content into a natural narrator sentence
     * suitable for direct display.
     *
     * Handles LLM misuse patterns seen in debug logs:
     *   - Wrapping the action in `*...*` and leaking dialogue after the closing `*`
     *     (we take only the italicized body and discard the rest).
     *   - Starting with a subject pronoun (`He/She/They/It`) — stripped so the name
     *     can be prepended cleanly.
     *   - Starting with a possessive pronoun (`His/Her/Their/Its`) — converted to
     *     "${name}'s ..." so "His smile doesn't reach his eyes" becomes
     *     "Prosper's smile doesn't reach his eyes".
     *   - Starting with the NPC's own name or a determiner ("The Inquisitor…",
     *     "A severe woman…") — left as-is without prepending the name, so the
     *     narrator voice reads naturally.
     *
     * @return a fully-formed natural sentence describing the action, OR an empty
     *         string if there is nothing meaningful to render.
     */
    private fun formatNpcActionContent(raw: String, speakerName: String): String {
        val sanitized = raw
        if (speakerName.isBlank()) return stripWrappingEmphasis(sanitized)
        var t = sanitized.trim()

        // If the content has a leading *italicized body* block, use ONLY that.
        // The LLM often wraps the actual body-language in asterisks then dumps
        // dialogue/exposition outside the closing `*` — discard that leak.
        val italicMatch = Regex("""^\s*\*+([^*]+)\*+""").find(t)
        if (italicMatch != null) {
            t = italicMatch.groupValues[1].trim()
        } else {
            t = stripWrappingEmphasis(t)
        }
        if (t.isBlank()) return ""

        // Strip redundant leading copy of the speaker name (case-insensitive,
        // optional trailing `'s`).
        val namePattern = Regex(
            "^" + Regex.escape(speakerName) + "(?:'s)?\\s+",
            RegexOption.IGNORE_CASE
        )
        t = namePattern.replace(t, "").trim()
        if (t.isBlank()) return ""

        // Leading possessive pronoun → speaker's possessive (natural narration).
        val possessiveMatch = Regex("^(his|her|their|its)\\s+", RegexOption.IGNORE_CASE).find(t)
        if (possessiveMatch != null) {
            val rest = t.substring(possessiveMatch.range.last + 1).trimStart()
            return "${speakerName}'s $rest"
        }

        // Leading subject pronoun → strip, we'll prepend the name.
        t = Regex("^(he|she|they|it)\\s+", RegexOption.IGNORE_CASE).replace(t, "").trim()
        if (t.isBlank()) return ""

        // If content already starts with a determiner ("The guard…", "An old man…")
        // the LLM supplied its own subject — render as-is without prepending.
        if (t.matches(Regex("^(?:the|a|an)\\s+[\\s\\S]*", RegexOption.IGNORE_CASE))) {
            return t
        }

        // Otherwise the content is a bare verb phrase — prepend the speaker name
        // for natural narration: "Prosper Saltblood sets his napkin aside."
        return "$speakerName $t"
    }

    /** Cleans aside / prose content — strips wrapping markdown emphasis. */
    private fun cleanAsideContent(raw: String): String = stripWrappingEmphasis(raw)

    // =========================================================================
    // Phase B — Tokenizer + Stack-Based Segment Parser
    // =========================================================================

    /**
     * Walks the raw response once, emitting a flat list of narrative tokens.
     *
     * Skips SCENE / CHOICES / METADATA blocks cleanly (they're handled by
     * dedicated regex passes in parse()). Emits OpaqueBlock for each so the
     * parser knows something was there but doesn't try to parse the interior.
     *
     * On stray `[` that doesn't start a recognised tag, treats it as text. On
     * an unknown tag name inside `[...]`, treats the whole bracketed sequence
     * as text. No exceptions are thrown — the tokenizer is infallible.
     */
    internal fun tokenizeNarrative(raw: String): List<NarrativeToken> {
        val tokens = mutableListOf<NarrativeToken>()
        val textBuf = StringBuilder()
        var i = 0

        fun flushText() {
            if (textBuf.isNotEmpty()) {
                tokens += NarrativeToken.Text(textBuf.toString())
                textBuf.clear()
            }
        }

        while (i < raw.length) {
            if (raw[i] != '[') {
                textBuf.append(raw[i])
                i++
                continue
            }

            // raw[i] == '['
            val rest = raw.substring(i + 1) // everything after '['

            // ---- Check for opaque blocks first ----

            // SCENE: self-closing [SCENE:type|desc]
            if (rest.startsWith("SCENE:", ignoreCase = true)) {
                val closeIdx = raw.indexOf(']', i + 1)
                if (closeIdx >= 0) {
                    flushText()
                    tokens += NarrativeToken.OpaqueBlock
                    i = closeIdx + 1
                    continue
                }
            }

            // CHOICES: [CHOICES]...[/CHOICES]
            if (rest.startsWith("CHOICES]", ignoreCase = true)) {
                val closeTag = "[/CHOICES]"
                val closeIdx = raw.indexOf(closeTag, i + 1, ignoreCase = true)
                flushText()
                tokens += NarrativeToken.OpaqueBlock
                i = if (closeIdx >= 0) closeIdx + closeTag.length else raw.indexOf(']', i + 1) + 1
                continue
            }

            // METADATA: [METADATA]...[/METADATA]
            if (rest.startsWith("METADATA]", ignoreCase = true)) {
                val closeTag = "[/METADATA]"
                val closeIdx = raw.indexOf(closeTag, i + 1, ignoreCase = true)
                flushText()
                tokens += NarrativeToken.OpaqueBlock
                i = if (closeIdx >= 0) closeIdx + closeTag.length else raw.indexOf(']', i + 1) + 1
                continue
            }

            // ---- Close tag: [/TAGNAME] ----
            if (rest.startsWith("/")) {
                val closeIdx = raw.indexOf(']', i + 1)
                if (closeIdx >= 0) {
                    val tagName = raw.substring(i + 2, closeIdx).trim()
                    val tagType = NarrativeTagType.fromRaw(tagName)
                    if (tagType != null) {
                        flushText()
                        tokens += NarrativeToken.CloseTag(tagType)
                        i = closeIdx + 1
                        continue
                    }
                }
                // Unknown close tag or no ']' — treat '[' as literal text
                textBuf.append('[')
                i++
                continue
            }

            // ---- Open tag: [TAGNAME] or [TAGNAME:arg] ----
            val closeIdx = raw.indexOf(']', i + 1)
            if (closeIdx >= 0) {
                val inner = raw.substring(i + 1, closeIdx)
                val colonPos = inner.indexOf(':')
                val tagName = if (colonPos >= 0) inner.substring(0, colonPos) else inner
                val tagType = NarrativeTagType.fromRaw(tagName.trim())
                if (tagType != null) {
                    val arg = if (colonPos >= 0) {
                        inner.substring(colonPos + 1).trim().ifBlank { null }
                    } else null
                    flushText()
                    tokens += NarrativeToken.OpenTag(tagType, arg)
                    i = closeIdx + 1
                    continue
                }
            }

            // Not a recognised tag — treat '[' as literal text
            textBuf.append('[')
            i++
        }

        flushText()
        return tokens
    }

    /** Internal pending block on the parser stack. */
    private data class PendingBlock(
        val type: NarrativeTagType,
        val arg: String?,
        val body: StringBuilder
    )

    /** Maps a completed PendingBlock to a NarrationSegmentData, or null if it should be dropped. */
    private fun makeSegment(block: PendingBlock): NarrationSegmentData? {
        val bodyRaw = block.body.toString()
        return when (block.type) {
            NarrativeTagType.NARRATOR_PROSE -> {
                val t = cleanAsideContent(bodyRaw)
                if (t.isBlank()) null else NarrationSegmentData.Prose(t)
            }
            NarrativeTagType.NARRATOR_ASIDE, NarrativeTagType.SNARK -> {
                val t = cleanAsideContent(bodyRaw)
                if (t.isBlank()) null else NarrationSegmentData.Aside(t)
            }
            NarrativeTagType.PLAYER_ACTION -> {
                val t = cleanPlayerActionContent(bodyRaw)
                if (t.isBlank()) null else NarrationSegmentData.PlayerAction(t)
            }
            NarrativeTagType.PLAYER_DIALOG -> {
                val t = cleanDialogContent(bodyRaw)
                if (t.isBlank()) null else NarrationSegmentData.PlayerDialog(t)
            }
            NarrativeTagType.NPC_ACTION -> {
                val name = block.arg?.trim() ?: ""
                if (name.isBlank()) return null
                val t = formatNpcActionContent(bodyRaw, speakerName = name)
                if (t.isBlank()) null else NarrationSegmentData.NpcAction(name, t)
            }
            NarrativeTagType.NPC_DIALOG -> {
                val name = block.arg?.trim() ?: ""
                if (name.isBlank()) return null
                val t = cleanDialogContent(bodyRaw)
                if (t.isBlank()) null else NarrationSegmentData.NpcDialog(name, t)
            }
        }
    }

    /**
     * Builds a NarrationSegmentData list from a token stream with permissive recovery.
     *
     * Recovery policy:
     *   - Mismatched close tag: force-close the top-of-stack block regardless of
     *     type (the close acts as "end this block here, whatever it said").
     *   - Stray close tag (empty stack): drop silently.
     *   - Unclosed tag at EOF: auto-close with whatever body was captured so far.
     *   - Gap text (outside any open tag): becomes a Prose segment if non-blank.
     */
    internal fun buildSegments(tokens: List<NarrativeToken>): List<NarrationSegmentData> {
        val segments = mutableListOf<NarrationSegmentData>()
        val stack = ArrayDeque<PendingBlock>()
        val gapBuffer = StringBuilder()

        fun flushGap() {
            val text = cleanAsideContent(gapBuffer.toString())
            gapBuffer.clear()
            if (text.isNotBlank()) segments += NarrationSegmentData.Prose(text)
        }

        for (token in tokens) {
            when (token) {
                is NarrativeToken.OpenTag -> {
                    flushGap()
                    stack.addLast(PendingBlock(token.type, token.arg, StringBuilder()))
                }
                is NarrativeToken.CloseTag -> {
                    if (stack.isNotEmpty()) {
                        // Force-close the top of stack regardless of whether types match.
                        val block = stack.removeLast()
                        makeSegment(block)?.let { segments += it }
                    }
                    // else: stray close tag with empty stack — drop silently
                }
                is NarrativeToken.Text -> {
                    if (stack.isNotEmpty()) stack.last().body.append(token.content)
                    else gapBuffer.append(token.content)
                }
                is NarrativeToken.OpaqueBlock -> { /* skip entirely */ }
            }
        }

        // Auto-close any unclosed blocks at EOF (innermost first)
        while (stack.isNotEmpty()) {
            val block = stack.removeLast()
            makeSegment(block)?.let { segments += it }
        }
        flushGap()

        return segments
    }

    /**
     * Normalizes curly/smart quotes to their ASCII equivalents so JSON parses
     * cleanly. DeepSeek sometimes mixes typographic quotes into the [METADATA]
     * block, which the strict subset of JSON rejects, and kotlinx.serialization
     * silently drops the sections after the first smart quote even with isLenient.
     *
     *   U+201C "  U+201D "  -> "
     *   U+2018 '  U+2019 '  -> '
     *   U+00AB «  U+00BB »  -> "  (rare but seen)
     *   U+201A ‚  U+201E „  -> low-9 marks -> ' and " respectively
     */
    private fun normalizeJsonQuotes(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '\u201C', '\u201D', '\u201E', '\u00AB', '\u00BB' -> sb.append('"')
                '\u2018', '\u2019', '\u201A' -> sb.append('\'')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * A slug-style ID: lowercase letters, digits, dashes. No spaces, no capitals.
     * Used to distinguish ID-first tag formats from legacy name-first formats.
     */
    private fun isSlugId(s: String): Boolean =
        s.isNotBlank() && s.matches(Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$"))

    // New structured dialog/prose block tags.
    private val narratorProsePattern = Regex("""\[NARRATOR_PROSE]([\s\S]*?)\[/NARRATOR_PROSE]""", RegexOption.IGNORE_CASE)
    private val narratorAsidePattern = Regex("""\[NARRATOR_ASIDE]([\s\S]*?)\[/NARRATOR_ASIDE]""", RegexOption.IGNORE_CASE)
    private val playerDialogPattern = Regex("""\[PLAYER_DIALOG]([\s\S]*?)\[/PLAYER_DIALOG]""", RegexOption.IGNORE_CASE)
    private val npcDialogPattern = Regex("""\[NPC_DIALOG:([^\]]+)]([\s\S]*?)\[/NPC_DIALOG]""", RegexOption.IGNORE_CASE)
    private val playerActionPattern = Regex("""\[PLAYER_ACTION]([\s\S]*?)\[/PLAYER_ACTION]""", RegexOption.IGNORE_CASE)
    private val npcActionPattern = Regex("""\[NPC_ACTION:([^\]]+)]([\s\S]*?)\[/NPC_ACTION]""", RegexOption.IGNORE_CASE)

    fun parse(raw: String, currentTurn: Int): ParsedReply {
        // ---- Attempt JSON metadata extraction (Path A) ----
        val metadataMatch = metadataBlockPattern.find(raw)
        val metadata: TurnMetadata? = metadataMatch?.let {
            val jsonBody = normalizeJsonQuotes(it.groupValues[1].trim())
            runCatching {
                metadataJson.decodeFromString<TurnMetadata>(jsonBody)
            }.getOrNull()
        }

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
        val partyLeaves = mutableListOf<String>()
        val enemies = mutableListOf<Triple<String, Int, Int>>()
        val factionUpdates = mutableListOf<Triple<String, String, String>>()
        val npcDeaths = mutableListOf<String>()
        val npcUpdates = mutableListOf<Triple<String, String, String>>()
        val loreEntries = mutableListOf<String>()
        val npcQuotes = mutableListOf<Pair<String, String>>()

        val parseSource: ParseSource

        if (metadata != null) {
            // ---- Path A: JSON metadata block found and decoded ----
            parseSource = ParseSource.JSON

            damage = metadata.damage
            heal = metadata.heal
            xp = metadata.xp
            goldGained = metadata.goldGained
            goldLost = metadata.goldLost
            moral = metadata.moralDelta
            travelTo = metadata.travelTo
            tod = metadata.timeOfDay

            metadata.itemsGained.forEach { spec ->
                items += Item(name = spec.name, desc = spec.desc, type = spec.type, rarity = spec.rarity)
            }
            metadata.itemsRemoved.forEach { name -> if (name.isNotBlank()) itemsRemoved += name }

            metadata.conditionsAdded.forEach { c -> if (c.isNotBlank()) conditionsAdded += c }
            metadata.conditionsRemoved.forEach { c -> if (c.isNotBlank()) conditionsRemoved += c }

            metadata.npcsMet.forEach { spec ->
                npcs += LogNpc(
                    id = spec.id,
                    name = spec.name,
                    race = spec.race,
                    role = spec.role,
                    age = spec.age,
                    relationship = spec.relationship,
                    appearance = spec.appearance,
                    personality = spec.personality,
                    thoughts = spec.thoughts,
                    metTurn = currentTurn,
                    lastSeenTurn = currentTurn
                )
            }
            metadata.npcUpdates.forEach { spec ->
                npcUpdates += Triple(spec.id, spec.field, spec.value)
            }
            metadata.npcDeaths.forEach { ref -> if (ref.isNotBlank()) npcDeaths += ref }
            metadata.npcQuotes.forEach { spec ->
                if (spec.id.isNotBlank() && spec.quote.isNotBlank()) npcQuotes += spec.id to spec.quote
            }

            metadata.questStarts.forEachIndexed { idx, spec ->
                qStarts += Quest(
                    id = "q_${System.currentTimeMillis()}_$idx",
                    title = spec.title,
                    type = spec.type,
                    desc = spec.desc,
                    giver = spec.giver,
                    location = "",
                    objectives = spec.objectives.toMutableList(),
                    reward = spec.reward,
                    turnStarted = currentTurn
                )
            }
            metadata.questUpdates.forEach { spec ->
                if (spec.title.isNotBlank()) qUpdates += spec.title to spec.objective
            }
            metadata.questCompletes.forEach { ref -> if (ref.isNotBlank()) qComplete += ref }
            metadata.questFails.forEach { ref -> if (ref.isNotBlank()) qFails += ref }

            metadata.enemies.forEach { spec ->
                enemies += Triple(spec.name, spec.hp, spec.maxHp)
            }

            metadata.factionUpdates.forEach { spec ->
                factionUpdates += Triple(spec.id, spec.field, spec.value)
            }
            metadata.repDeltas.forEach { spec ->
                reps += spec.faction to spec.delta
            }
            metadata.loreEntries.forEach { entry -> if (entry.isNotBlank()) loreEntries += entry }

            metadata.check?.let { spec ->
                checks += CheckResult(
                    skill = spec.skill,
                    ability = spec.ability,
                    dc = spec.dc,
                    passed = spec.passed,
                    total = spec.total
                )
            }

            metadata.shops.forEach { spec ->
                shops += spec.merchant to spec.items
            }

            metadata.partyJoins.forEach { spec ->
                parties += PartyCompanion(
                    name = spec.name,
                    race = spec.race,
                    role = spec.role,
                    level = spec.level,
                    maxHp = spec.maxHp,
                    hp = spec.maxHp,
                    appearance = spec.appearance,
                    personality = spec.personality,
                    joinedTurn = currentTurn
                )
            }
            metadata.partyLeaves.forEach { name -> if (name.isNotBlank()) partyLeaves += name }

        } else {
            // ---- Path B: No valid JSON block — regex tag extraction (legacy) ----
            parseSource = ParseSource.INVALID

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
                        if (p.isNotEmpty()) {
                            // New ID-first format: [NPC_MET:slug-id|Display Name|Race|Role|Age|...]
                            // Legacy format:       [NPC_MET:Display Name|Race|Role|Age|...]
                            if (p.size >= 2 && isSlugId(p[0])) {
                                npcs += LogNpc(
                                    id = p[0],
                                    name = p[1],
                                    race = p.getOrNull(2) ?: "",
                                    role = p.getOrNull(3) ?: "",
                                    age = p.getOrNull(4) ?: "",
                                    relationship = p.getOrNull(5) ?: "neutral",
                                    appearance = p.getOrNull(6) ?: "",
                                    personality = p.getOrNull(7) ?: "",
                                    thoughts = p.getOrNull(8) ?: "",
                                    metTurn = currentTurn, lastSeenTurn = currentTurn
                                )
                            } else {
                                npcs += LogNpc(
                                    id = "",
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
                        }
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
                    "PARTY_LEAVE" -> {
                        val name = body.trim()
                        if (name.isNotBlank()) partyLeaves += name
                    }
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
                    "ENEMY" -> {
                        val p = body.split("|").map { it.trim() }
                        if (p.isNotEmpty()) {
                            val name = p[0]
                            val hp = p.getOrNull(1)?.toIntOrNull() ?: 10
                            val maxHp = p.getOrNull(2)?.toIntOrNull() ?: hp
                            enemies += Triple(name, hp, maxHp)
                        }
                    }
                    "FACTION_UPDATE" -> {
                        val p = body.split("|").map { it.trim() }
                        if (p.size >= 3) factionUpdates += Triple(p[0], p[1], p[2])
                    }
                    "NPC_DIED" -> {
                        val name = body.trim()
                        if (name.isNotBlank()) npcDeaths += name
                    }
                    "NPC_UPDATE" -> {
                        val p = body.split("|").map { it.trim() }
                        if (p.size >= 3) npcUpdates += Triple(p[0], p[1], p[2])
                    }
                    "LORE" -> {
                        val t = body.trim()
                        if (t.isNotBlank()) loreEntries += t
                    }
                    "NPC_QUOTE" -> {
                        // [NPC_QUOTE:Name|the memorable line] — split once so quotes
                        // containing '|' survive.
                        val sep = body.indexOf('|')
                        if (sep > 0) {
                            val name = body.substring(0, sep).trim()
                            val quote = body.substring(sep + 1).trim()
                                .removeSurrounding("\"").removeSurrounding("\u201C", "\u201D")
                                .removeSurrounding("'").removeSurrounding("\u2018", "\u2019")
                                .trim()
                            if (name.isNotBlank() && quote.isNotBlank()) {
                                npcQuotes += name to quote
                            }
                        }
                    }
                }
            }
        }

        // ---- Structured dialog/prose block extraction (Phase B tokenizer path) ----
        // Build segments first — all per-type lists derive from segments below.
        val tokens = tokenizeNarrative(raw)
        val segments = buildSegments(tokens).toMutableList()

        val narratorProse = segments.filterIsInstance<NarrationSegmentData.Prose>().map { it.text }
        val narratorAsides = segments.filterIsInstance<NarrationSegmentData.Aside>().map { it.text }
        val playerDialogs = segments.filterIsInstance<NarrationSegmentData.PlayerDialog>().map { it.text }
        val npcDialogs = segments.filterIsInstance<NarrationSegmentData.NpcDialog>().map { it.name to it.text }

        // Strip the mechanical tags from the narration body but keep SCENE at top as header.
        var narration = raw
        narration = scenePattern.replace(narration, "")
        narration = choicesPattern.replace(narration, "")
        narration = metadataBlockPattern.replace(narration, "")
        narration = narratorProsePattern.replace(narration, "$1")
        narration = narratorAsidePattern.replace(narration, "$1")
        narration = playerDialogPattern.replace(narration, "$1")
        narration = npcDialogPattern.replace(narration, "$2")
        narration = playerActionPattern.replace(narration, "$1")
        narration = npcActionPattern.replace(narration, "$2")
        narration = tagPattern.replace(narration, "")
        narration = narration.trim().replace(Regex("\\n{3,}"), "\n\n")

        // Derive per-type action lists from segments (segments already built above)
        val playerActionsExtracted = segments.filterIsInstance<NarrationSegmentData.PlayerAction>().map { it.text }
        val npcActionsExtracted = segments.filterIsInstance<NarrationSegmentData.NpcAction>().map { it.name to it.text }

        return ParsedReply(
            scene, sceneDesc, narration, choices, damage, heal, xp,
            goldGained, goldLost, items, checks, npcs,
            qStarts, qUpdates, qComplete, qFails, shops,
            travelTo, parties, tod, moral, reps,
            worldEventHook = null,
            conditionsAdded = conditionsAdded,
            conditionsRemoved = conditionsRemoved,
            itemsRemoved = itemsRemoved,
            partyLeaves = partyLeaves,
            enemies = enemies,
            factionUpdates = factionUpdates,
            npcDeaths = npcDeaths,
            npcUpdates = npcUpdates,
            loreEntries = loreEntries,
            npcQuotes = npcQuotes,
            narratorProse = narratorProse,
            narratorAsides = narratorAsides,
            playerDialogs = playerDialogs,
            npcDialogs = npcDialogs,
            playerActions = playerActionsExtracted,
            npcActions = npcActionsExtracted,
            segments = segments,
            source = parseSource
        )
    }
}
