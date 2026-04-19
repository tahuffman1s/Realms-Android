package com.realmsoffate.game.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.NarrationSegmentData
import com.realmsoffate.game.game.DisplayMessage
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.ui.theme.RealmsTheme
import com.realmsoffate.game.util.NarrationMarkdown

// ============================================================
// NARRATION BLOCK — master dispatcher for a full narrator turn
// ============================================================

@Composable
internal fun NarrationBlock(
    text: String,
    characterName: String? = null,
    msg: DisplayMessage.Narration? = null,
    structuredSegments: List<NarrationSegmentData> = emptyList(),
    npcLog: List<LogNpc> = emptyList(),
    isLatestTurn: Boolean = false,
    onNpcTap: (name: String) -> Unit = {},
    onNpcReply: (name: String) -> Unit = {},
    onAttackNpc: (name: String) -> Unit = {},
    onOpenJournal: (name: String) -> Unit = {},
    onOpenStats: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (structuredSegments.isNotEmpty()) {
            // ---- Structured rendering: document-order segments ----
            structuredSegments.forEach { seg ->
                when (seg) {
                    is NarrationSegmentData.Prose -> {
                        NarratorProseBubble(
                            text = seg.text,
                            isLatestTurn = isLatestTurn
                        )
                    }
                    is NarrationSegmentData.Aside -> {
                        NarratorAsideLine(text = seg.text)
                    }
                    is NarrationSegmentData.NpcDialog -> {
                        val displayName = resolveNpcDisplayName(seg.name, npcLog)
                        SwipeableMessage(
                            onSwipeLeft = if (isLatestTurn) { { onAttackNpc(displayName) } } else { {} },
                            onSwipeRight = { onOpenJournal(displayName) },
                            leftLabel = if (isLatestTurn) "Attack" else null,
                            rightLabel = "Journal",
                            leftIcon = if (isLatestTurn) Icons.Filled.GpsFixed else null,
                            rightIcon = Icons.Filled.Book
                        ) {
                            NpcDialogueBubble(
                                name = displayName,
                                quote = seg.text,
                                onTap = if (isLatestTurn) { { onNpcReply(seg.name) } } else { {} },
                                isInteractive = isLatestTurn
                            )
                        }
                    }
                    is NarrationSegmentData.NpcAction -> {
                        val displayName = resolveNpcDisplayName(seg.name, npcLog)
                        val actionText = if (seg.name.isNotBlank() && displayName != seg.name &&
                            seg.text.startsWith(seg.name, ignoreCase = true)) {
                            displayName + seg.text.substring(seg.name.length)
                        } else {
                            seg.text
                        }
                        val (accent, _) = npcColor(seg.name, RealmsTheme.colors.npcPalette)
                        NpcActionLine(text = actionText, accentColor = accent)
                    }
                    is NarrationSegmentData.PlayerAction -> {
                        PlayerActionLine(text = seg.text)
                    }
                    is NarrationSegmentData.PlayerDialog -> {
                        SwipeableMessage(
                            onSwipeLeft = {},
                            onSwipeRight = { onOpenStats() },
                            leftLabel = null,
                            rightLabel = "Stats",
                            leftIcon = null,
                            rightIcon = Icons.Filled.QueryStats
                        ) {
                            PlayerBubble(
                                text = seg.text,
                                characterName = characterName
                            )
                        }
                    }
                }
            }
            // Stat change pills at the end
            if (msg != null) StatChangePills(msg)
        } else {
            // ---- Legacy fallback: regex-based splitting for old saves ----
            val segments = splitNarration(text, characterName)
            val allProse = segments.filterIsInstance<NarrationSegment.Prose>()
                .joinToString("\n\n") { it.text }
            val nonProseSegments = segments.filter { it !is NarrationSegment.Prose }

            if (allProse.isNotBlank()) {
                NarratorProseBubble(
                    text = allProse,
                    isLatestTurn = isLatestTurn
                )
            }

            nonProseSegments.forEach { seg ->
                when (seg) {
                    is NarrationSegment.NarratorQuip -> {
                        NarratorAsideLine(text = seg.text)
                    }
                    is NarrationSegment.Dialogue -> {
                        SwipeableMessage(
                            onSwipeLeft = if (isLatestTurn) { { onAttackNpc(seg.name) } } else { {} },
                            onSwipeRight = { onOpenJournal(seg.name) },
                            leftLabel = if (isLatestTurn) "Attack" else null,
                            rightLabel = "Journal",
                            leftIcon = if (isLatestTurn) Icons.Filled.Bolt else null,
                            rightIcon = Icons.Filled.Book
                        ) {
                            NpcDialogueBubble(
                                name = seg.name,
                                quote = seg.quote,
                                onTap = if (isLatestTurn) { { onNpcReply(seg.name) } } else { {} },
                                isInteractive = isLatestTurn
                            )
                        }
                    }
                    is NarrationSegment.PlayerDialogue -> {
                        SwipeableMessage(
                            onSwipeLeft = {},
                            onSwipeRight = { onOpenStats() },
                            leftLabel = null,
                            rightLabel = "Stats",
                            leftIcon = null,
                            rightIcon = Icons.Filled.QueryStats
                        ) {
                            PlayerBubble(
                                text = seg.quote,
                                characterName = seg.name
                            )
                        }
                    }
                    is NarrationSegment.Action -> {
                        NarratorAsideLine(text = seg.text)
                    }
                    else -> {}
                }
            }

            // Stat change pills at the bottom (always shown)
            if (msg != null) {
                StatChangePills(msg)
            }
        }
    }
}

// ============================================================
// NARRATOR PROSE BUBBLE — inline collapsible full-text rendering
// ============================================================

@Composable
internal fun NarratorProseBubble(
    text: String,
    isLatestTurn: Boolean
) {
    var expanded by remember { mutableStateOf(isLatestTurn) }
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    val fontSize = (15f * LocalFontScale.current).sp
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = { expanded = !expanded },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(RealmsSpacing.m)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(RealmsSpacing.xs))
                    Text(
                        "NARRATOR",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(RealmsSpacing.xs))
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                if (expanded) {
                    Spacer(Modifier.height(RealmsSpacing.s))
                    NarrationMarkdown(
                        text = text,
                        modifier = Modifier.fillMaxWidth(),
                        baseStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize)
                    )
                }
            }
        }
    }
}

// ============================================================
// LEGACY NARRATION SUPPORT — regex-split segments + summarize
// ============================================================

private sealed class NarrationSegment {
    data class Prose(val text: String) : NarrationSegment()
    data class Dialogue(val name: String, val quote: String) : NarrationSegment()
    data class PlayerDialogue(val name: String, val quote: String) : NarrationSegment()
    data class Action(val text: String) : NarrationSegment()
    /** Narrator aside / quip — italic commentary like *I've seen this before.* */
    data class NarratorQuip(val text: String) : NarrationSegment()
}

/** Split narration text into typed segments for distinct rendering. */
private fun splitNarration(text: String, characterName: String? = null): List<NarrationSegment> {
    val segments = mutableListOf<NarrationSegment>()

    // First pass: extract [SNARK]...[/SNARK] tags and replace with placeholders
    val snarkPattern = Regex("""\[SNARK](.*?)\[/SNARK]""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val snarks = mutableListOf<String>()
    val cleaned = snarkPattern.replace(text) { match ->
        snarks.add(match.groupValues[1].trim())
        "\n@@SNARK_${snarks.size - 1}@@\n"
    }

    val lines = cleaned.lines()
    var proseBuffer = StringBuilder()
    var i = 0

    // Regex for standalone italic sentences embedded in prose (narrator asides)
    val embeddedQuip = Regex("""(?:^|\n)\s*\*([^*\n]{6,})\*\s*(?:\n|$)""")
    // Regex for dialogue embedded in prose: "Name says/said/asks/shouts, "quote""
    // or: "quote," Name says. Captures attributed speech within narrative paragraphs.
    val QQ = "\u201C\u201D\""
    val proseDialogue = Regex(
        "(?:^|\\.\\s+)\\**([A-Z][a-z]+(?:\\s[A-Z][a-z]+)?)\\**\\s+" +
        "(?:says?|said|asks?|asked|whispers?|whispered|shouts?|shouted|mutters?|muttered|growls?|growled|replies?|replied|hisses?|hissed|calls?|called|snaps?|snapped|laughs?|laughed|sighs?|sighed|screams?|screamed|barks?|barked|cries?|cried|murmurs?|murmured|exclaims?|exclaimed)" +
        "[,:]?\\s*[" + QQ + "]([^" + QQ + "]{3,}?)[" + QQ + "]",
        RegexOption.IGNORE_CASE
    )

    fun flushProse() {
        val t = proseBuffer.toString().trim()
        if (t.isNotBlank()) {
            // Split long prose into paragraph-sized chunks
            val paragraphs = t.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }
            for (para in paragraphs) {
                // First: extract any embedded quips (italic asides)
                val quipMatches = embeddedQuip.findAll(para).toList()
                // Second: extract attributed dialogue from prose ("Name says, ...")
                val dialogueMatches = proseDialogue.findAll(para).toList()

                if (quipMatches.isEmpty() && dialogueMatches.isEmpty()) {
                    segments.add(NarrationSegment.Prose(para))
                } else {
                    // Merge all extraction ranges and split the paragraph
                    data class Extraction(val range: IntRange, val segment: NarrationSegment)
                    val extractions = mutableListOf<Extraction>()
                    quipMatches.forEach { m ->
                        extractions.add(Extraction(m.range, NarrationSegment.NarratorQuip(m.groupValues[1].trim())))
                    }
                    dialogueMatches.forEach { m ->
                        val name = m.groupValues[1].trim().removeSurrounding("*")
                        val quote = m.groupValues[2].trim()
                        val isPC = characterName != null && name.equals(characterName, ignoreCase = true)
                        val seg = if (isPC) NarrationSegment.PlayerDialogue(name, quote)
                                  else NarrationSegment.Dialogue(name, quote)
                        extractions.add(Extraction(m.range, seg))
                    }
                    // Sort by position and split
                    val sorted = extractions.sortedBy { it.range.first }
                    var lastEnd = 0
                    for (ext in sorted) {
                        if (ext.range.first < lastEnd) continue // overlapping, skip
                        val before = para.substring(lastEnd, ext.range.first).trim()
                        if (before.isNotBlank()) segments.add(NarrationSegment.Prose(before))
                        segments.add(ext.segment)
                        lastEnd = ext.range.last + 1
                    }
                    val after = para.substring(lastEnd.coerceAtMost(para.length)).trim()
                    if (after.isNotBlank()) segments.add(NarrationSegment.Prose(after))
                }
            }
        }
        proseBuffer = StringBuilder()
    }

    while (i < lines.size) {
        val line = lines[i].trim()

        // Detect [SNARK] placeholder
        val snarkIdx = Regex("""^@@SNARK_(\d+)@@$""").find(line)
        if (snarkIdx != null) {
            flushProse()
            val idx2 = snarkIdx.groupValues[1].toIntOrNull()
            if (idx2 != null && idx2 in snarks.indices) {
                segments.add(NarrationSegment.NarratorQuip(snarks[idx2]))
            }
            i++
            continue
        }

        // Detect NPC/player dialogue: **Name:** or EMOJI **Name:** patterns
        // The colon MUST be inside the bold markers to avoid matching generic bold text.
        if (line.contains(":**")) {
            // Match **Name:** with optional emoji prefix and optional trailing content
            val nameMatch = Regex("""^[^\*]*\*\*([^*:]+?):\*\*(.*)$""").find(line)
            if (nameMatch != null) {
                flushProse()
                val name = nameMatch.groupValues[1].trim()
                val trailing = nameMatch.groupValues[2].trim()
                val isPlayer = characterName != null && name.equals(characterName, ignoreCase = true)

                // Strategy 1: Look ahead for blockquote on next line(s)
                val nextIdx = i + 1
                if (nextIdx < lines.size && lines[nextIdx].trim().startsWith(">")) {
                    // Collect all consecutive blockquote lines as one quote
                    val quoteLines = mutableListOf<String>()
                    var j = nextIdx
                    while (j < lines.size && lines[j].trim().startsWith(">")) {
                        quoteLines.add(lines[j].trim().removePrefix(">").trim()
                            .removeSurrounding("\"").removeSurrounding("\u201C", "\u201D"))
                        j++
                    }
                    val fullQuote = quoteLines.joinToString(" ")
                    if (isPlayer) segments.add(NarrationSegment.PlayerDialogue(name, fullQuote))
                    else segments.add(NarrationSegment.Dialogue(name, fullQuote))
                    i = j
                    continue
                }

                // Strategy 2: Inline quoted text after **Name:**
                val cleanTrailing = trailing.removeSurrounding("*").trim()
                if (cleanTrailing.contains("\"") || cleanTrailing.contains("\u201C")) {
                    val q = cleanTrailing.replace(Regex("[\"\u201C\u201D]"), "").trim()
                    if (q.isNotBlank()) {
                        if (isPlayer) segments.add(NarrationSegment.PlayerDialogue(name, q))
                        else segments.add(NarrationSegment.Dialogue(name, q))
                        i++
                        continue
                    }
                }

                // Strategy 3: Any remaining text after **Name:** is speech (no quotes)
                if (cleanTrailing.isNotBlank() && cleanTrailing.length >= 3) {
                    if (isPlayer) segments.add(NarrationSegment.PlayerDialogue(name, cleanTrailing))
                    else segments.add(NarrationSegment.Dialogue(name, cleanTrailing))
                    i++
                    continue
                }

                // Strategy 4: Name line alone — look ahead for any non-empty line as speech
                if (nextIdx < lines.size) {
                    val nextLine = lines[nextIdx].trim()
                    if (nextLine.isNotBlank() && !nextLine.startsWith("*") && !nextLine.startsWith("[")) {
                        val q = nextLine.removeSurrounding("\"").removeSurrounding("\u201C", "\u201D")
                        if (isPlayer) segments.add(NarrationSegment.PlayerDialogue(name, q))
                        else segments.add(NarrationSegment.Dialogue(name, q))
                        i = nextIdx + 1
                        continue
                    }
                }

                // Fallback: just the name, no quote found — skip it
                i++
                continue
            }
        }

        // Detect narrator asides/quips: line is *italic* or mostly italic commentary
        // Patterns: *entire line italic*, or line that is ONLY an italic phrase
        if (!line.startsWith("**") && line.length >= 8) {
            // Entire line wrapped in *...*
            if (line.startsWith("*") && line.endsWith("*") && line.count { it == '*' } == 2) {
                flushProse()
                segments.add(NarrationSegment.NarratorQuip(line.removeSurrounding("*")))
                i++
                continue
            }
            // Line starts and ends with italic markers but may have trailing punctuation
            val quipMatch = Regex("""^\*([^*]+)\*[.!?…]*$""").find(line)
            if (quipMatch != null && quipMatch.groupValues[1].length >= 6) {
                flushProse()
                segments.add(NarrationSegment.NarratorQuip(quipMatch.groupValues[1]))
                i++
                continue
            }
        }

        // Detect parenthetical actions: (something happens)
        if (line.startsWith("(") && line.endsWith(")")) {
            flushProse()
            segments.add(NarrationSegment.Action(line.removeSurrounding("(", ")")))
            i++
            continue
        }

        // Detect blockquote dialogue without a preceding name
        if (line.startsWith("> ")) {
            flushProse()
            val quote = line.removePrefix("> ").trim()
                .removeSurrounding("\"").removeSurrounding("\u201C", "\u201D")
            segments.add(NarrationSegment.Dialogue("", quote))
            i++
            continue
        }

        // Everything else is prose
        proseBuffer.appendLine(line)
        i++
    }
    flushProse()
    return segments
}

// ============================================================
// SEGMENT COMPOSABLES — distinct visual treatment per type
// ============================================================

@Composable
private fun ActionPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = accent.copy(alpha = 0.7f)
            )
            Spacer(Modifier.width(RealmsSpacing.s))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontStyle = FontStyle.Italic,
                    fontSize = 10.sp
                ),
                color = accent.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NpcActionLine(text: String, accentColor: Color) =
    ActionPill(text = text, icon = Icons.Filled.DirectionsRun, accent = accentColor)

@Composable
internal fun NarratorAsideLine(text: String) = ActionPill(
    text = text,
    icon = Icons.Filled.AutoAwesome,
    accent = MaterialTheme.colorScheme.secondary
)

@Composable
private fun PlayerActionLine(text: String) =
    ActionPill(text = text, icon = Icons.Filled.Bolt, accent = MaterialTheme.colorScheme.tertiary)

/** Extracts a 1-3 sentence summary from the full prose text. */
private fun summarizeProse(text: String): String {
    // Strip markdown formatting for the summary
    val clean = text
        .replace(Regex("#{1,3}\\s+"), "")
        .replace(Regex("\\*{1,3}"), "")
        .replace(Regex("`[^`]+`"), "")
        .replace(Regex("~~[^~]+~~"), "")
        .replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^[-*]\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("---+|\\*\\*\\*+"), "")
        .replace(Regex("\\n{2,}"), " ")
        .replace(Regex("\\n"), " ")
        .trim()

    // Take first 2-3 sentences
    val sentences = clean.split(Regex("(?<=[.!?])\\s+"))
        .filter { it.isNotBlank() }

    return when {
        sentences.size <= 2 -> clean.take(200)
        else -> sentences.take(3).joinToString(" ").take(200)
    }.let { if (it.length >= 197) "$it..." else it }
}
