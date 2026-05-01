package com.realmsoffate.game.game

import com.realmsoffate.game.data.content.ContentRepository
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * World mutation record. The `prompt` field is inlined into the system context
 * so the narrator bends every scene around them. Backing list lives in JSON —
 * see `assets/content/world/mutations.json`.
 */
@Serializable
data class Mutation(
    val id: String,
    val name: String,
    val icon: String,
    val desc: String,
    val prompt: String
)

object Mutations {
    val list: List<Mutation> get() = ContentRepository.mutations

    fun find(id: String): Mutation? = list.firstOrNull { it.id == id }

    fun pickForWorld(rng: Random = Random.Default, count: Int = 2 + rng.nextInt(2)): List<Mutation> =
        list.shuffled(rng).take(count)
}
