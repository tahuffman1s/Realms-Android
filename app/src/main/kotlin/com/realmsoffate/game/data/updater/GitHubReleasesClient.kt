package com.realmsoffate.game.data.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper over GitHub's public Releases API. Returns a [Result] sealed
 * class so callers can distinguish recoverable network/rate-limit errors
 * from permanent parse errors without exception-catching gymnastics.
 *
 * Anonymous (no auth) — public repo, 60 req/hour limit per IP. The repository
 * caches results for 6h so this limit is comfortably out of reach.
 */
class GitHubReleasesClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://api.github.com/",
    private val userAgent: String,
    private val owner: String = "tahuffman1s",
    private val repo: String = "Realms-Android"
) {
    private val json = Json { ignoreUnknownKeys = true }

    sealed class Result {
        data class Ok(val releases: List<ReleaseInfo>) : Result()
        data class NetworkError(val message: String) : Result()
        data class RateLimited(val resetEpochSec: Long?) : Result()
        data class ParseError(val message: String) : Result()
        data class HttpError(val code: Int, val message: String) : Result()
    }

    suspend fun fetchReleases(perPage: Int = 10): Result = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/repos/$owner/$repo/releases?per_page=$perPage"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            return@withContext Result.NetworkError(e.message ?: "I/O error")
        }
        response.use { resp ->
            if (resp.code == 403 && resp.header("X-RateLimit-Remaining") == "0") {
                val reset = resp.header("X-RateLimit-Reset")?.toLongOrNull()
                return@withContext Result.RateLimited(reset)
            }
            if (!resp.isSuccessful) {
                return@withContext Result.HttpError(resp.code, resp.message)
            }
            val body = resp.body?.string() ?: return@withContext Result.ParseError("empty body")
            return@withContext try {
                val dtos = json.decodeFromString<List<ReleaseDto>>(body)
                Result.Ok(dtos.mapNotNull(ReleaseInfo::fromDto))
            } catch (e: Exception) {
                Result.ParseError(e.message ?: "parse failure")
            }
        }
    }

    companion object {
        /** Default OkHttp client tuned for short, latency-sensitive metadata calls. */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
