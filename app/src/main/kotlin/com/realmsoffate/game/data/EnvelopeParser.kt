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
        val envelope = runCatching { json.decodeFromString<TurnEnvelope>(raw.trim()) }
            .getOrElse {
                android.util.Log.w("EnvelopeParser", "invalid envelope: ${it.message}", it)
                return invalidReply()
            }

        val segments: List<NarrationSegmentData> = envelope.segments.map { it.toSegmentData() }

        return ParsedReply(
            scene = envelope.scene.type,
            sceneDesc = envelope.scene.desc,
            narration = "",  // all narration is in segments; legacy field kept empty
            choices = envelope.choices
                .filter { it.text.isNotBlank() }
                .mapIndexed { i, c -> Choice(n = i + 1, text = c.text, skill = c.skill) },
            damage = envelope.metadata.damage,
            heal = envelope.metadata.heal,
            xp = envelope.metadata.xp,
            goldGained = envelope.metadata.goldGained,
            goldLost = envelope.metadata.goldLost,
            itemsGained = envelope.metadata.itemsGained.map {
                Item(name = it.name, desc = it.desc, type = it.type, rarity = it.rarity)
            },
            checks = envelope.metadata.check?.let {
                listOf(CheckResult(it.skill, it.ability, it.dc, it.passed, it.total))
            } ?: emptyList(),
            npcsMet = envelope.metadata.npcsMet.map { spec ->
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
            questStarts = envelope.metadata.questStarts.mapIndexed { i, q ->
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
            questUpdates = envelope.metadata.questUpdates.map { it.title to it.objective },
            questComplete = envelope.metadata.questCompletes,
            questFails = envelope.metadata.questFails,
            shops = envelope.metadata.shops.map { it.merchant to it.items },
            travelTo = envelope.metadata.travelTo,
            partyJoins = envelope.metadata.partyJoins.map { spec ->
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
            timeOfDay = envelope.metadata.timeOfDay,
            moralDelta = envelope.metadata.moralDelta,
            repDeltas = envelope.metadata.repDeltas.map { it.faction to it.delta },
            worldEventHook = null,
            conditionsAdded = envelope.metadata.conditionsAdded,
            conditionsRemoved = envelope.metadata.conditionsRemoved,
            itemsRemoved = envelope.metadata.itemsRemoved,
            partyLeaves = envelope.metadata.partyLeaves,
            enemies = envelope.metadata.enemies.map { Triple(it.name, it.hp, it.maxHp) },
            factionUpdates = envelope.metadata.factionUpdates.map { Triple(it.id, it.field, it.value) },
            npcDeaths = envelope.metadata.npcDeaths,
            npcUpdates = envelope.metadata.npcUpdates.map { Triple(it.id, it.field, it.value) },
            loreEntries = envelope.metadata.loreEntries,
            npcQuotes = envelope.metadata.npcQuotes.map { it.id to it.quote },
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

    private fun Segment.toSegmentData(): NarrationSegmentData = when (this) {
        is Segment.Prose -> NarrationSegmentData.Prose(text)
        is Segment.Aside -> NarrationSegmentData.Aside(text)
        is Segment.PlayerAction -> NarrationSegmentData.PlayerAction(text)
        is Segment.PlayerDialog -> NarrationSegmentData.PlayerDialog(text)
        is Segment.NpcAction -> NarrationSegmentData.NpcAction(name, text)
        is Segment.NpcDialog -> NarrationSegmentData.NpcDialog(name, text)
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
