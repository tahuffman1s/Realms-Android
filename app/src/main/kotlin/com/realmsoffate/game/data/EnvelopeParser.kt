package com.realmsoffate.game.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Decodes a DeepSeek turn response (a single JSON envelope) into ParsedReply.
 * This is the ONLY parse path post-migration. On decode failure, returns an empty
 * ParsedReply with ParseSource.INVALID so the reducer layer applies zero mechanical
 * effects.
 */
object EnvelopeParser {
    // classDiscriminator = "kind" matches the @SerialName values on Segment subclasses.
    // Auto-registration handles the sealed Segment hierarchy (verified in TurnEnvelopeTest).
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        classDiscriminator = "kind"
    }

    fun parse(raw: String, currentTurn: Int): ParsedReply {
        // Caller placeholders may pass empty string; return INVALID silently
        // so the log isn't spammed with "EOF at $" false alarms.
        if (raw.isBlank()) return invalidReply()
        val extracted = extractOutermostObject(raw.trim())
        // First attempt: strict decode. The common case succeeds here.
        val envelope = runCatching { json.decodeFromString<TurnEnvelope>(extracted) }
            .recoverCatching {
                // Second attempt: repair known DeepSeek malformations (orphan string tokens,
                // trailing commas). This salvages turns where the model hallucinated a stray
                // field — e.g. `"check": null, "" }` — without requiring a round-trip retry.
                val repaired = repairJson(extracted)
                if (repaired != extracted) {
                    android.util.Log.w("EnvelopeParser", "decoded after JSON repair pass")
                }
                json.decodeFromString<TurnEnvelope>(repaired)
            }
            .recoverCatching {
                // Final attempt: schema-free salvage. Decode as JsonObject and hand-extract
                // the bare minimum needed to render a turn (segments + choices + scene). If
                // metadata is broken we drop it; the turn renders with narrative but no
                // mechanical effects, which is strictly better than a dead bubble.
                android.util.Log.w("EnvelopeParser", "salvaging envelope from malformed JSON")
                salvageEnvelope(repairJson(extracted))
            }
            .getOrElse {
                android.util.Log.w("EnvelopeParser", "invalid envelope: ${it.message}", it)
                android.util.Log.w("EnvelopeParser", "raw payload[0..500]: ${raw.take(500)}")
                return invalidReply()
            }

        val segments: List<NarrationSegmentData> = envelope.segments
            .map { it.toSegmentData() }
            .let(::promoteContinuationQuotes)
            .filter(::isRenderable)
        val meta = envelope.metadata

        // Null out a partial CheckSpec. A real check always has: a named skill,
        // a positive DC, and a non-zero total. If any of those are absent the model
        // emitted stub/filler fields (we've seen `{skill:"",ability:"",dc:0,passed:true,total:16}`
        // render as `✓ () DC 0 — PASSED (16)`), so drop the pill entirely.
        val check = meta.check?.takeIf {
            it.skill.isNotBlank() && it.dc > 0 && it.total != 0
        }

        return ParsedReply(
            scene = envelope.scene.type,
            sceneDesc = envelope.scene.desc,
            narration = "",  // all narration is in segments; legacy field kept empty
            choices = envelope.choices
                .filter { it.text.isNotBlank() }
                .mapIndexed { i, c -> Choice(n = i + 1, text = c.text, skill = c.skill) },
            damage = meta.damage,
            heal = meta.heal,
            xp = meta.xp,
            goldGained = meta.goldGained,
            goldLost = meta.goldLost,
            itemsGained = meta.itemsGained
                .filter { it.name.isNotBlank() }
                .map { Item(name = it.name, desc = it.desc, type = it.type, rarity = it.rarity) },
            checks = check?.let {
                listOf(CheckResult(it.skill, it.ability, it.dc, it.passed, it.total))
            } ?: emptyList(),
            npcsMet = meta.npcsMet
                .filter { it.id.isNotBlank() || it.name.isNotBlank() }
                .map { spec ->
                    LogNpc(
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
                },
            questStarts = meta.questStarts
                .filter { it.title.isNotBlank() }
                .mapIndexed { i, q ->
                    Quest(
                        // TODO(Task 9+): if a retry path is added, use a content-addressed id (hash of title+currentTurn) for idempotency.
                        id = "q_${System.currentTimeMillis()}_$i",
                        title = q.title,
                        type = q.type,
                        desc = q.desc,
                        giver = q.giver,
                        location = "",
                        objectives = q.objectives.toMutableList(),
                        reward = q.reward,
                        turnStarted = currentTurn
                    )
                },
            questUpdates = meta.questUpdates
                .filter { it.title.isNotBlank() }
                .map { it.title to it.objective },
            questComplete = meta.questCompletes.filter { it.isNotBlank() },
            questFails = meta.questFails.filter { it.isNotBlank() },
            shops = meta.shops
                .filter { it.merchant.isNotBlank() }
                .map { it.merchant to it.items },
            travelTo = meta.travelTo,
            partyJoins = meta.partyJoins
                .filter { it.name.isNotBlank() }
                .map { spec ->
                    PartyCompanion(
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
                },
            timeOfDay = meta.timeOfDay,
            moralDelta = meta.moralDelta,
            repDeltas = meta.repDeltas
                .filter { it.faction.isNotBlank() }
                .map { it.faction to it.delta },
            worldEventHook = null,
            conditionsAdded = meta.conditionsAdded.filter { it.isNotBlank() },
            conditionsRemoved = meta.conditionsRemoved.filter { it.isNotBlank() },
            itemsRemoved = meta.itemsRemoved.filter { it.isNotBlank() },
            partyLeaves = meta.partyLeaves.filter { it.isNotBlank() },
            enemies = meta.enemies
                .filter { it.name.isNotBlank() }
                .map { Triple(it.name, it.hp, it.maxHp) },
            factionUpdates = meta.factionUpdates
                .filter { it.id.isNotBlank() }
                .map { Triple(it.id, it.field, it.value) },
            npcDeaths = meta.npcDeaths.filter { it.isNotBlank() },
            npcUpdates = meta.npcUpdates
                .filter { it.id.isNotBlank() }
                .map { Triple(it.id, it.field, it.value) },
            loreEntries = meta.loreEntries.filter { it.isNotBlank() },
            npcQuotes = meta.npcQuotes
                .filter { it.id.isNotBlank() && it.quote.isNotBlank() }
                .map { it.id to it.quote },
            narratorProse = segments.filterIsInstance<NarrationSegmentData.Prose>().map { it.text },
            narratorAsides = segments.filterIsInstance<NarrationSegmentData.Aside>().map { it.text },
            playerDialogs = segments.filterIsInstance<NarrationSegmentData.PlayerDialog>().map { it.text },
            npcDialogs = segments.filterIsInstance<NarrationSegmentData.NpcDialog>().map { it.name to it.text },
            playerActions = segments.filterIsInstance<NarrationSegmentData.PlayerAction>().map { it.text },
            npcActions = segments.filterIsInstance<NarrationSegmentData.NpcAction>().map { it.name to it.text },
            segments = segments,
            source = ParseSource.JSON
        )
    }

    /**
     * The model sometimes emits a speaker's continuation as a plain Prose
     * segment wrapped in single/smart quotes (e.g. the NPC was attributed in
     * a prior NpcDialog segment and a later paragraph is just a bare quote).
     * Reclassify such paragraphs as NpcDialog/PlayerDialog using the most
     * recent speaker so the UI can render them as bubbles rather than prose.
     *
     * Rules:
     *  - Only Prose segments are inspected.
     *  - Prose is split on blank-line paragraph boundaries.
     *  - A paragraph whose first and last non-trailing-punctuation chars are
     *    both quote marks (straight or curly) is treated as a bare quote.
     *  - Bare quote → NpcDialog (if last speaker was an NPC) or PlayerDialog
     *    (if last speaker was the player). If no prior speaker is known, leave
     *    as Prose.
     *  - Actions/asides/non-dialog segments don't change the current speaker.
     */
    internal fun promoteContinuationQuotes(
        segs: List<NarrationSegmentData>
    ): List<NarrationSegmentData> {
        var lastNpcName: String? = null
        var lastWasPlayer = false
        val out = mutableListOf<NarrationSegmentData>()
        for (seg in segs) {
            when (seg) {
                is NarrationSegmentData.NpcDialog -> {
                    lastNpcName = seg.name
                    lastWasPlayer = false
                    out.add(seg)
                }
                is NarrationSegmentData.PlayerDialog -> {
                    lastNpcName = null
                    lastWasPlayer = true
                    out.add(seg)
                }
                is NarrationSegmentData.Prose -> {
                    // Model separates a continuation quote from prose with a
                    // single `\n` (not a blank line), so split on any newline
                    // run — we only re-classify explicitly quoted paragraphs.
                    val paragraphs = seg.text
                        .split(Regex("\\n+"))
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    if (paragraphs.isEmpty()) {
                        out.add(seg); continue
                    }
                    for (p in paragraphs) {
                        val promoted = when {
                            !isBareQuote(p) -> null
                            lastNpcName != null -> NarrationSegmentData.NpcDialog(lastNpcName!!, p)
                            lastWasPlayer -> NarrationSegmentData.PlayerDialog(p)
                            else -> null
                        }
                        out.add(promoted ?: NarrationSegmentData.Prose(p))
                    }
                }
                else -> out.add(seg)
            }
        }
        return out
    }

    /**
     * A paragraph is a "bare quote" if its first and last characters (after
     * stripping trailing sentence punctuation) are matching quote marks. We
     * tolerate inner quote characters (contractions like "don't") since the
     * paragraph-level wrap is what signals continuation.
     */
    private fun isBareQuote(text: String): Boolean {
        val t = text.trim().trimEnd('.', ',', '!', '?', '…')
        if (t.length < 4) return false
        val opens = setOf('\'', '"', '‘', '“')
        val closes = setOf('\'', '"', '’', '”')
        return t.first() in opens && t.last() in closes
    }

    /** Segment text must have at least one "content" character to be rendered. */
    private val TRIVIAL_CHARS = Regex("""[\-=*_#~.,;:!?•·…–—…\s]""")

    /**
     * Drop segments whose text is effectively empty — pure whitespace, lone markdown
     * dividers ("--", "---", "==="), ellipses ("…", "..."), em/en dashes, or other
     * punctuation-only strings. DeepSeek occasionally emits these as placeholder
     * content (e.g. `{"kind":"aside","text":"--"}`) which renders as an empty-
     * looking pill.
     *
     * Rule: a segment must have at least one letter, digit, or non-trivial unicode
     * character AFTER stripping trivial chars. A segment with only 1-2 content chars
     * is also dropped since it's almost certainly nonsense.
     */
    private fun isRenderable(seg: NarrationSegmentData): Boolean {
        val text = when (seg) {
            is NarrationSegmentData.Prose -> seg.text
            is NarrationSegmentData.Aside -> seg.text
            is NarrationSegmentData.PlayerAction -> seg.text
            is NarrationSegmentData.PlayerDialog -> seg.text
            is NarrationSegmentData.NpcAction -> seg.text
            is NarrationSegmentData.NpcDialog -> seg.text
        }.trim()
        if (text.isEmpty()) return false
        val content = TRIVIAL_CHARS.replace(text, "")
        return content.length >= 3
    }

    private fun Segment.toSegmentData(): NarrationSegmentData = when (this) {
        is Segment.Prose -> NarrationSegmentData.Prose(cleanSegmentText(text))
        is Segment.Aside -> NarrationSegmentData.Aside(cleanSegmentText(text))
        is Segment.PlayerAction -> NarrationSegmentData.PlayerAction(cleanSegmentText(text))
        is Segment.PlayerDialog -> NarrationSegmentData.PlayerDialog(cleanSegmentText(text))
        is Segment.NpcAction -> NarrationSegmentData.NpcAction(name, cleanSegmentText(text))
        is Segment.NpcDialog -> NarrationSegmentData.NpcDialog(name, cleanSegmentText(text))
    }

    /**
     * DeepSeek occasionally wraps segment text in parens (stage-direction style),
     * asterisks (markdown italic), quotes, or brackets — even when the prompt says
     * not to. Strip wrapping delimiters iteratively as long as they enclose the
     * ENTIRE string with no inner occurrence of the same pair.
     *
     *   "(text)"        → "text"
     *   "*text*"        → "text"
     *   "**text**"      → "text"  (one pass strips 2 chars; two passes strip 4)
     *   "\"text\""     → "text"
     *   "(a (b) c)"     → "(a (b) c)"   (inner parens preserved — ambiguous, leave alone)
     */
    internal fun cleanSegmentText(raw: String): String {
        // Multi-line segments (e.g. `"(stage direction)\n'speech'"`) never match the
        // outer-wrap rules below because the first and last chars differ. Clean each
        // line independently first, then re-join. Single-line input is unaffected.
        val lines = raw.split('\n')
        if (lines.size > 1) {
            return lines.joinToString("\n") { stripWrappers(it) }.trim()
        }
        return stripWrappers(raw)
    }

    private fun stripWrappers(raw: String): String {
        var t = raw.trim()
        var changed = true
        while (changed && t.length >= 2) {
            changed = false
            val first = t.first()
            val last = t.last()
            val inner = t.substring(1, t.length - 1)
            val strip = when {
                // Structural delimiters: only strip if no inner occurrence (ambiguity guard).
                first == '(' && last == ')' && !inner.contains('(') && !inner.contains(')') -> true
                first == '[' && last == ']' && !inner.contains('[') && !inner.contains(']') -> true
                first == '"' && last == '"' && !inner.contains('"') -> true
                first == '“' && last == '”' -> true
                first == '\'' && last == '\'' && !inner.contains('\'') -> true
                first == '‘' && last == '’' -> true
                // Markdown emphasis: strip one pair at a time so "**bold**" → "*bold*" → "bold".
                first == '*' && last == '*' -> true
                first == '_' && last == '_' -> true
                else -> false
            }
            if (strip) {
                t = inner.trim()
                changed = true
            }
        }
        return t
    }

    /**
     * Return the substring from the first top-level `{` to the matching `}`. Strips
     * markdown fences and surrounding prose. Falls back to the original string if no
     * balanced object is found — the decoder will fail naturally in that case.
     */
    internal fun extractOutermostObject(raw: String): String {
        val start = raw.indexOf('{')
        if (start < 0) return raw
        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < raw.length) {
            val c = raw[i]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return raw.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        return raw.substring(start)
    }

    /**
     * Repair the two DeepSeek malformations we've observed in the wild:
     *   1. Orphan string tokens preceding a close brace/bracket (e.g.
     *      `"check": null, "" }` — the trailing `""` has no `:value`).
     *   2. Trailing commas before `}` or `]`.
     * Both patterns occur OUTSIDE quoted strings only, so we walk the string
     * state-aware (findStringEnd skips over quoted content including escapes)
     * rather than running a blind regex (regex would corrupt prose that
     * legitimately contains the same byte sequences inside strings).
     *
     * A string is classified as an orphan key ONLY when: preceded by a `,`
     * (comma in key position) AND followed by `}` or `]` (no `:value` in between).
     * This avoids misclassifying string values — `"key":"value",` — as orphans
     * just because they aren't followed by a colon.
     */
    internal fun repairJson(raw: String): String {
        if (raw.isEmpty()) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '"') {
                val strEnd = findStringEnd(raw, i)
                if (strEnd < 0) {
                    sb.append(raw, i, raw.length)
                    return sb.toString()
                }
                var k = strEnd + 1
                while (k < raw.length && raw[k].isWhitespace()) k++
                val followedByClose = k < raw.length && (raw[k] == '}' || raw[k] == ']')
                val lastNonWs = sb.indexOfLastNonWhitespace()
                val precededByComma = lastNonWs >= 0 && sb[lastNonWs] == ','
                if (followedByClose && precededByComma) {
                    // Orphan key — strip both the string token and the preceding comma.
                    sb.deleteCharAt(lastNonWs)
                    i = strEnd + 1
                    continue
                }
                sb.append(raw, i, strEnd + 1)
                i = strEnd + 1
                continue
            }
            if (c == ',') {
                var k = i + 1
                while (k < raw.length && raw[k].isWhitespace()) k++
                if (k < raw.length && (raw[k] == '}' || raw[k] == ']')) {
                    i++
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun findStringEnd(raw: String, openIdx: Int): Int {
        var j = openIdx + 1
        var esc = false
        while (j < raw.length) {
            val c = raw[j]
            if (esc) esc = false
            else if (c == '\\') esc = true
            else if (c == '"') return j
            j++
        }
        return -1
    }

    private fun StringBuilder.indexOfLastNonWhitespace(): Int {
        var i = length - 1
        while (i >= 0 && this[i].isWhitespace()) i--
        return i
    }

    /**
     * Last-resort: extract just enough to render a turn even if schema decoding fails.
     * Walks the envelope by locating each expected top-level field and extracting its
     * value substring via bracket-tracking, then parses each piece independently.
     * A broken sub-field (e.g. unquoted identifiers in metadata) no longer prevents
     * scene/segments/choices from surfacing — the turn still renders with narrative
     * even if mechanical effects are lost.
     */
    private fun salvageEnvelope(raw: String): TurnEnvelope {
        val scene = extractFieldValue(raw, "scene")?.let {
            runCatching { json.decodeFromString<SceneInfo>(it) }.getOrNull()
        } ?: SceneInfo()
        val segments = extractFieldValue(raw, "segments")?.let { arr ->
            runCatching {
                (json.parseToJsonElement(arr) as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { el -> runCatching { json.decodeFromJsonElement<Segment>(el) }.getOrNull() }
                    .orEmpty()
            }.getOrDefault(emptyList())
        } ?: emptyList()
        val choices = extractFieldValue(raw, "choices")?.let { arr ->
            runCatching {
                (json.parseToJsonElement(arr) as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { el -> runCatching { json.decodeFromJsonElement<ChoiceSpec>(el) }.getOrNull() }
                    .orEmpty()
            }.getOrDefault(emptyList())
        } ?: emptyList()
        val metadata = extractFieldValue(raw, "metadata")?.let {
            runCatching { json.decodeFromString<TurnMetadata>(it) }.getOrNull()
        } ?: TurnMetadata()
        // Salvage is only valid if we extracted SOMETHING recognizable. Plain junk input
        // (e.g. "not json") has no salvageable fields — throw so the outer recoverCatching
        // falls through to INVALID instead of silently returning an empty "valid" envelope.
        if (segments.isEmpty() && choices.isEmpty() && scene.desc.isBlank() && scene.type == "default") {
            throw IllegalStateException("no salvageable fields in envelope")
        }
        return TurnEnvelope(scene, segments, choices, metadata)
    }

    /**
     * Locate `"fieldName": <value>` at any depth in [raw] and return the value
     * substring (balanced across `{...}` / `[...]` / quoted strings). Returns
     * null if the field is absent or the value span is unterminated. Scans outside
     * string context so field names inside narrative prose don't cause false hits.
     */
    internal fun extractFieldValue(raw: String, fieldName: String): String? {
        val needle = "\"$fieldName\""
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '"') {
                val end = findStringEnd(raw, i)
                if (end < 0) return null
                // Check if this string matches the field name AND is in key position (next non-ws is `:`).
                if (end - i == needle.length - 1 && raw.regionMatches(i, needle, 0, needle.length)) {
                    var k = end + 1
                    while (k < raw.length && raw[k].isWhitespace()) k++
                    if (k < raw.length && raw[k] == ':') {
                        k++
                        while (k < raw.length && raw[k].isWhitespace()) k++
                        return extractValueSpan(raw, k)
                    }
                }
                i = end + 1
                continue
            }
            i++
        }
        return null
    }

    /** Return the substring of the JSON value starting at [start]. Handles objects,
     *  arrays, strings, and primitives (number/bool/null). Null on malformed input. */
    private fun extractValueSpan(raw: String, start: Int): String? {
        if (start >= raw.length) return null
        return when (val c = raw[start]) {
            '{', '[' -> {
                val open = c
                val close = if (c == '{') '}' else ']'
                var depth = 0
                var i = start
                while (i < raw.length) {
                    val ch = raw[i]
                    if (ch == '"') {
                        val e = findStringEnd(raw, i)
                        if (e < 0) return null
                        i = e + 1
                        continue
                    }
                    if (ch == open) depth++
                    else if (ch == close) {
                        depth--
                        if (depth == 0) return raw.substring(start, i + 1)
                    }
                    i++
                }
                null
            }
            '"' -> {
                val e = findStringEnd(raw, start)
                if (e < 0) null else raw.substring(start, e + 1)
            }
            else -> {
                // Primitive: number, true, false, null. Read until `,`, `}`, `]`, or whitespace.
                var i = start
                while (i < raw.length && raw[i] !in charArrayOf(',', '}', ']') && !raw[i].isWhitespace()) i++
                raw.substring(start, i)
            }
        }
    }

    private fun invalidReply(): ParsedReply = ParsedReply(
        scene = "default",
        sceneDesc = "",
        narration = "",
        choices = emptyList(),
        damage = 0, heal = 0, xp = 0, goldGained = 0, goldLost = 0,
        itemsGained = emptyList(), checks = emptyList(), npcsMet = emptyList(),
        questStarts = emptyList(), questUpdates = emptyList(),
        questComplete = emptyList(), questFails = emptyList(),
        shops = emptyList(),
        travelTo = null,
        partyJoins = emptyList(),
        timeOfDay = null,
        moralDelta = 0,
        repDeltas = emptyList(),
        worldEventHook = null,
        source = ParseSource.INVALID
    )
}
