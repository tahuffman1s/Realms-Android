package com.realmsoffate.game.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
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

    companion object {
        /** Default history budget in tokens — leaves headroom for system + scene summaries + per-turn context. */
        const val HISTORY_TOKEN_BUDGET: Int = 8000

        /** Shared lenient parser — reused by [parseBalance] and [parseSummaryResponse] so we
         *  don't allocate a configuration object on every parse. */
        private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * Keep the largest suffix of [history] whose summed token estimate ≤ [budget].
         * Always preserves the final message even if it alone exceeds the budget
         * (a dropped final user turn would break the turn contract).
         */
        fun windowByTokenBudget(history: List<ChatMsg>, budget: Int): List<ChatMsg> {
            if (history.isEmpty()) return emptyList()
            val kept = ArrayDeque<ChatMsg>()
            var used = 0
            for (m in history.asReversed()) {
                val cost = com.realmsoffate.game.util.TokenEstimate.ofMessage(m)
                if (kept.isEmpty()) {
                    // Always keep the final message.
                    kept.addFirst(m)
                    used += cost
                    continue
                }
                if (used + cost > budget) break
                kept.addFirst(m)
                used += cost
            }
            return kept.toList()
        }

        /** Extract the USD total_balance from a DeepSeek /user/balance payload.
         *  Falls back to the first balance entry when USD is not present. Returns
         *  null on any parse failure, when the account is marked unavailable, or
         *  when balance_infos is empty. */
        fun parseBalance(raw: String): String? = runCatching {
            val root = lenientJson.parseToJsonElement(raw).jsonObject
            val available = root["is_available"]?.jsonPrimitive?.booleanOrNull ?: true
            if (!available) return@runCatching null
            val infos = root["balance_infos"]?.jsonArray ?: return@runCatching null
            if (infos.isEmpty()) return@runCatching null
            val usd = infos.firstOrNull { it.jsonObject["currency"]?.jsonPrimitive?.contentOrNull == "USD" }
            val pick = usd ?: infos.first()
            pick.jsonObject["total_balance"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()

        /**
         * Extract (summary, keyFacts) from a DeepSeek response. Tolerates code
         * fences and surrounding prose because models don't always behave.
         * Returns null if no valid JSON object with a "summary" string is found.
         */
        fun parseSummaryResponse(raw: String): Pair<String, List<String>>? {
            val stripped = raw
                .substringAfter("```json", raw)
                .substringAfter("```", raw)
                .substringBeforeLast("```", raw)
            val start = stripped.indexOf('{')
            val end = stripped.lastIndexOf('}')
            if (start < 0 || end < 0 || end <= start) return null
            val jsonSlice = stripped.substring(start, end + 1)
            return try {
                val obj = lenientJson.parseToJsonElement(jsonSlice).jsonObject
                val summary = obj["summary"]?.jsonPrimitive?.content ?: return null
                val facts = obj["keyFacts"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.content.takeIf { s -> s.isNotBlank() } }
                    ?: emptyList()
                summary to facts
            } catch (_: Exception) {
                null
            }
        }
    }
    /** Total prompt token count from the most recent DeepSeek response. Not thread-safe; called serially. */
    var lastPromptTokens: Int = 0

    suspend fun generate(
        provider: AiProvider,
        apiKey: String,
        systemPrompt: String,
        history: List<ChatMsg>,
        styleSample: String? = null
    ): String = withContext(Dispatchers.IO) {
        callDeepSeek(apiKey, systemPrompt, history, styleSample)
    }

    private fun callDeepSeek(apiKey: String, sys: String, history: List<ChatMsg>, styleSample: String? = null): String {
        val trimmed = windowByTokenBudget(history, HISTORY_TOKEN_BUDGET).toMutableList()
        if (trimmed.isNotEmpty() && trimmed.last().role == "user") {
            val last = trimmed.last()
            trimmed[trimmed.size - 1] = last.copy(content = last.content + Prompts.PER_TURN_REMINDER)
        }
        val messages = buildJsonArray {
            // Stable system message — cache target. Style block (if any) is part
            // of the stable prefix because the earliest scene summary never changes.
            add(buildJsonObject {
                put("role", "system")
                put("content", Prompts.DS_PREFIX + sys + StyleExemplar.block(styleSample))
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

    /**
     * Compress a completed scene's history into a short summary + key facts.
     * Uses the scene-summary system prompt and low temperature for consistency.
     * Returns null on network error or unparseable response — caller should
     * treat missing summaries as "better to miss one than crash the turn flow".
     */
    suspend fun summarizeScene(
        apiKey: String,
        sceneName: String,
        locationName: String,
        sceneHistory: List<ChatMsg>
    ): Pair<String, List<String>>? = withContext(Dispatchers.IO) {
        if (sceneHistory.isEmpty()) return@withContext null
        try {
            val header = "SCENE: $sceneName\nLOCATION: $locationName"
            val transcript = sceneHistory.joinToString("\n\n") { m ->
                "[${m.role.uppercase()}]\n${m.content}"
            }
            val userContent = "$header\n\n---\n\n$transcript"
            val messages = buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", Prompts.SCENE_SUMMARY_SYS)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userContent)
                })
            }
            val body = buildJsonObject {
                put("model", "deepseek-chat")
                put("max_tokens", 400)
                put("temperature", 0.2)
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
                    ?.jsonPrimitive?.content
                    ?: return@withContext null
                parseSummaryResponse(content)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * One-shot arc summarization — compresses a batch of scene summaries into a
     * single long-term arc. Mirrors [summarizeScene]'s slim call shape: no
     * per-turn reminder, no DS_PREFIX, no history windowing. Returns the raw
     * model response (expected to be `{"summary":"..."}` JSON per [Prompts.ARC_SUMMARY_SYS]);
     * [com.realmsoffate.game.game.ArcSummarizer] unwraps the envelope.
     */
    suspend fun summarizeArc(
        apiKey: String,
        sceneInputs: List<String>
    ): String = withContext(Dispatchers.IO) {
        if (sceneInputs.isEmpty() || apiKey.isBlank()) return@withContext ""
        try {
            val userContent = sceneInputs.joinToString("\n\n")
            val messages = buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", ARC_SUMMARY_SYS)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userContent)
                })
            }
            val body = buildJsonObject {
                put("model", "deepseek-chat")
                put("max_tokens", 500)
                put("temperature", 0.2)
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
                if (!it.isSuccessful) return@withContext ""
                val text = it.body?.string().orEmpty()
                val root = json.parseToJsonElement(text).jsonObject
                root["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content
                    .orEmpty()
            }
        } catch (_: Exception) {
            ""
        }
    }

    /** Fetches the current DeepSeek account USD balance as a string (e.g. "4.12").
     *  Null on network failure, auth failure, or parse failure. Blocking I/O. */
    suspend fun fetchBalance(apiKey: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val req = Request.Builder()
            .url("https://api.deepseek.com/user/balance")
            .get()
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        runCatching {
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                parseBalance(r.body?.string().orEmpty())
            }
        }.getOrNull()
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

