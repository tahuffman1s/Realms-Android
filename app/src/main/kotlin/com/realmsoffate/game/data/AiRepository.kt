package com.realmsoffate.game.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
 *   - temperature 0.9, top_p 0.95 (DeepSeek creative-writing sweet spot)
 *   - frequency_penalty 0.3, presence_penalty 0.1 (cuts repetition)
 *   - max_tokens 900
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

    suspend fun generate(
        provider: AiProvider,
        apiKey: String,
        systemPrompt: String,
        history: List<ChatMsg>
    ): String = withContext(Dispatchers.IO) {
        when (provider) {
            AiProvider.GEMINI -> callGemini(apiKey, systemPrompt, history)
            AiProvider.DEEPSEEK -> callDeepSeek(apiKey, systemPrompt, history)
            AiProvider.CLAUDE -> callClaude(apiKey, systemPrompt, history)
        }
    }

    private fun callGemini(apiKey: String, sys: String, history: List<ChatMsg>): String {
        val contents = history.takeLast(20).map {
            buildJsonObject {
                put("role", if (it.role == "assistant") "model" else "user")
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", it.content) })
                }
            }
        }
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", sys) }) }
            }
            putJsonArray("contents") { contents.forEach { add(it) } }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", 800)
                put("temperature", 0.9)
            }
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val resp = client.newCall(req).execute()
        resp.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) return fallback(text)
            val root = json.parseToJsonElement(text).jsonObject
            return root["candidates"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: "The world falls silent..."
        }
    }

    private fun callDeepSeek(apiKey: String, sys: String, history: List<ChatMsg>): String {
        val trimmed = history.takeLast(20).toMutableList()
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
            put("max_tokens", 900)
            put("temperature", 0.9)
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
            return root["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: "The world falls silent..."
        }
    }

    private fun callClaude(apiKey: String, sys: String, history: List<ChatMsg>): String {
        val trimmed = history.takeLast(20)
        val messages = buildJsonArray {
            trimmed.forEach { m ->
                add(buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                })
            }
        }
        val body = buildJsonObject {
            put("model", "claude-sonnet-4-20250514")
            put("max_tokens", 600)
            put("system", sys)
            put("messages", messages)
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("anthropic-dangerous-direct-browser-access", "true")
            .addHeader("Content-Type", "application/json")
            .build()
        val resp = client.newCall(req).execute()
        resp.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) return fallback(text)
            val root = json.parseToJsonElement(text).jsonObject
            val content = root["content"]?.jsonArray
            return content?.joinToString("\n") {
                it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
            } ?: "The world falls silent..."
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

