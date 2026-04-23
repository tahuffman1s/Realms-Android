package com.realmsoffate.game.data

import kotlinx.serialization.json.Json

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
        val envelope = runCatching { json.decodeFromString<TurnEnvelope>(raw.trim()) }
            .getOrElse {
                android.util.Log.w("EnvelopeParser", "invalid envelope: ${it.message}", it)
                android.util.Log.w("EnvelopeParser", "raw payload[0..500]: ${raw.take(500)}")
                return invalidReply()
            }

        val segments: List<NarrationSegmentData> = envelope.segments
            .map { it.toSegmentData() }
            .filter(::isRenderable)
        val meta = envelope.metadata

        // Null out a partial CheckSpec where total is at its default (0) —
        // a real roll always produces a non-zero total, so total==0 means the model
        // emitted an incomplete object (e.g. missing passed/total fields).
        val check = meta.check?.takeUnless { it.total == 0 }

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
