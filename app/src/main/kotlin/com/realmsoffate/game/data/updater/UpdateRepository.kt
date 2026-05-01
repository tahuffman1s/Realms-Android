package com.realmsoffate.game.data.updater

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Anything that can return a list of releases. Production: GitHubReleasesClient.
 * Tests: in-memory fake. Lets the repository remain agnostic to OkHttp.
 */
interface ReleasesSource {
    suspend fun fetchReleases(): GitHubReleasesClient.Result
}

/** Production adapter so the existing GitHubReleasesClient implements ReleasesSource. */
class GitHubReleasesSource(private val delegate: GitHubReleasesClient) : ReleasesSource {
    override suspend fun fetchReleases() = delegate.fetchReleases()
}

/**
 * Process-level singleton (initialized in RealmsApp). Reduces network responses
 * + user actions into [UpdateState]. Cache is in-memory (TTL on lastCheckedAt);
 * channel + dismissals + last-known-tag persist in [UpdatePrefs].
 */
class UpdateRepository(
    private val currentVersionName: String,
    private val client: ReleasesSource,
    private val prefs: UpdatePrefs,
    private val clock: () -> Long = System::currentTimeMillis,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checkJob: Job? = null

    /**
     * Trigger a release check. If [force] is false and the cache is fresh (<TTL),
     * no-op except for re-emitting the current state. Cancels any in-flight check.
     * Returns the launched [Job] so callers (notably tests) can await completion.
     */
    fun check(force: Boolean = false): Job {
        checkJob?.cancel()
        val job = scope.launch {
            if (!force) {
                val last = prefs.lastCheckedAt.first()
                if (last != null && clock() - last < cacheTtlMs) {
                    return@launch
                }
            }
            _state.value = UpdateState.Checking
            val channel = prefs.channel.first()
            when (val r = client.fetchReleases()) {
                is GitHubReleasesClient.Result.Ok -> {
                    prefs.setLastCheckedAt(clock())
                    val current = runCatching { SemVer.parse(currentVersionName) }.getOrNull()
                        ?: run {
                            _state.value = UpdateState.Error("Bad app version", recoverable = false)
                            return@launch
                        }
                    val candidate = r.releases
                        .filter(channel::accepts)
                        .filter { it.version > current }
                        .maxByOrNull { it.version }
                    _state.value = when {
                        candidate == null -> UpdateState.UpToDate
                        else -> {
                            prefs.setLastKnownReleaseTag(candidate.tag)
                            UpdateState.Available(candidate)
                        }
                    }
                }
                is GitHubReleasesClient.Result.NetworkError ->
                    _state.value = UpdateState.Error("No connection", recoverable = true)
                is GitHubReleasesClient.Result.RateLimited ->
                    _state.value = UpdateState.Error("Rate-limited, try again later", recoverable = true)
                is GitHubReleasesClient.Result.HttpError ->
                    _state.value = UpdateState.Error("Couldn't reach GitHub (${r.code})", recoverable = true)
                is GitHubReleasesClient.Result.ParseError ->
                    _state.value = UpdateState.Error("Couldn't parse release", recoverable = false)
            }
        }
        checkJob = job
        return job
    }

    /** UI helper: should the title-screen banner show for this release? */
    suspend fun bannerVisibleFor(release: ReleaseInfo): Boolean =
        release.tag !in prefs.dismissedVersions.first()

    fun dismissBannerForVersion(tag: String) {
        scope.launch { prefs.dismissVersion(tag) }
    }

    fun setChannel(channel: UpdateChannel) {
        scope.launch {
            prefs.setChannel(channel)
            check(force = true)
        }
    }

    companion object {
        /** 6 hours. */
        const val DEFAULT_CACHE_TTL_MS: Long = 6L * 60L * 60L * 1000L
    }
}
