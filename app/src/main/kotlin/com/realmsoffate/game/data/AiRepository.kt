package com.realmsoffate.game.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Single place for all AI provider calls. Each method returns the raw text
 * reply (still containing tags — the TagParser takes over from there).
 *
 * DeepSeek is tuned specifically here:
 *   - temperature 1.0, top_p 0.95 (DeepSeek creative-writing sweet spot)
 *   - frequency_penalty 0.3, presence_penalty 0.1 (cuts repetition)
 *   - max_tokens 1800
 *   - DS_PREFIX + SYS as a stable system message — DeepSeek auto-caches
 *     identical prefixes so this pays off across turns.
 */
class AiRepository(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Cache-hit token count from the most recent DeepSeek response. Not thread-safe; called serially. */
    var lastCacheHit: Int = 0
    /** Total prompt token count from the most recent DeepSeek response. Not thread-safe; called serially. */
    var lastPromptTokens: Int = 0

    suspend fun generate(
        provider: AiProvider,
        apiKey: String,
        systemPrompt: String,
        history: List<ChatMsg>
    ): String = withContext(Dispatchers.IO) {
        callDeepSeek(apiKey, systemPrompt, history)
    }

    private fun callDeepSeek(apiKey: String, sys: String, history: List<ChatMsg>): String {
        val trimmed = history.takeLast(40).toMutableList()
        if (trimmed.isNotEmpty() && trimmed.last().role == "user") {
            val last = trimmed.last()
            trimmed[trimmed.size - 1] = last.copy(content = last.content + Prompts.PER_TURN_REMINDER)
        }
        val messages = buildJsonArray {
            // Stable system message — cache target.
            add(buildJsonObject {
                put("role", "system")
                put("content", Prompts.DS_PREFIX + sys)
            })
            trimmed.forEach { m ->
                add(buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                })
            }
        }
        val body = buildJsonObject {
            put("model", "deepseek-chat")
            put("max_tokens", 1800)
            put("temperature", 1.0)
            put("top_p", 0.95)
            put("frequency_penalty", 0.3)
            put("presence_penalty", 0.1)
            put("messages", messages)
        }
        val req = Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()
        val resp = client.newCall(req).execute()
        resp.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) return fallback(text)
            val root = json.parseToJsonElement(text).jsonObject
            // Extract cache-hit stats from the usage object for diagnostics.
            root["usage"]?.jsonObject?.let { usage ->
                lastPromptTokens = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                lastCacheHit = usage["prompt_cache_hit_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            }
            return root["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: "The world falls silent..."
        }
    }

    /**
     * Lightweight classifier — asks DeepSeek to identify which D&D skill check
     * best fits a freeform player action. Returns a skill name like "Athletics",
     * "Persuasion", "Stealth", etc. Uses minimal tokens and no history for speed.
     * Falls back to null if the call fails or the response is garbage.
     */
    suspend fun classifyAction(apiKey: String, action: String): String? = withContext(Dispatchers.IO) {
        try {
            val sysPrompt = """You are a D&D 5e skill classifier. Given a player action, respond with ONLY the most appropriate skill name. One word. No explanation.
Skills: Athletics, Acrobatics, Sleight of Hand, Stealth, Arcana, History, Investigation, Nature, Religion, Animal Handling, Insight, Medicine, Perception, Survival, Deception, Intimidation, Performance, Persuasion, Attack
If the action is combat/attacking, respond "Attack". If purely dialogue with no manipulation, respond "none"."""

            val messages = buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", sysPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", action)
                })
            }
            val body = buildJsonObject {
                put("model", "deepseek-chat")
                put("max_tokens", 10)
                put("temperature", 0.0)
                put("messages", messages)
            }
            val req = Request.Builder()
                .url("https://api.deepseek.com/v1/chat/completions")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            val resp = client.newCall(req).execute()
            resp.use {
                if (!it.isSuccessful) return@withContext null
                val text = it.body?.string().orEmpty()
                val root = json.parseToJsonElement(text).jsonObject
                val content = root["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content?.trim()
                    ?: return@withContext null

                // Validate — DeepSeek may return bare "Stealth", quoted "\"Stealth\"",
                // prefixed "Skill: Stealth", or a full sentence. We search for any
                // known skill name anywhere in the response.
                val validSkills = listOf(
                    "sleight of hand", "animal handling",  // multi-word first
                    "athletics", "acrobatics", "stealth",
                    "arcana", "history", "investigation", "nature", "religion",
                    "insight", "medicine", "perception",
                    "survival", "deception", "intimidation", "performance",
                    "persuasion", "attack", "none"
                )
                val lower = content.lowercase()
                    .replace("\"", "").replace("'", "")
                    .trim().removeSuffix(".").removeSuffix(",")
                // First try exact match on the cleaned string
                val exact = validSkills.firstOrNull { it == lower }
                // Then try substring match (handles "The skill is Stealth" etc.)
                val found = exact ?: validSkills.firstOrNull { it in lower }
                when {
                    found == null -> null
                    found == "none" -> null
                    found == "attack" -> "Attack"
                    else -> found.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fallback(err: String): String {
        return "[SCENE:default|Mystery] *(Connection flickers...)* Try again, adventurer.\n" +
                "[CHOICES]\n" +
                "1. Try again [Athletics]\n" +
                "2. Look around [Perception]\n" +
                "3. Wait [Patience]\n" +
                "4. Pray [Religion]\n" +
                "[/CHOICES]"
    }
}

