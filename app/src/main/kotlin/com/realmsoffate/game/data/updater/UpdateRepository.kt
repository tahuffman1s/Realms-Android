package com.realmsoffate.game.data.updater

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    private val downloader: ApkDownloaderLike? = null,
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

    fun setChannel(channel: UpdateChannel): Job =
        scope.launch {
            prefs.setChannel(channel)
            check(force = true).join()
        }

    private var downloadJob: Job? = null

    fun startDownload(release: ReleaseInfo): Job {
        val dl = downloader
        if (dl == null) {
            _state.value = UpdateState.Error("No downloader wired", recoverable = false)
            return scope.launch { /* no-op */ }
        }
        val asset = release.realmsApkAsset()
        if (asset == null) {
            _state.value = UpdateState.Error("Release missing APK", recoverable = false)
            return scope.launch { /* no-op */ }
        }
        downloadJob?.cancel()
        val job = scope.launch {
            _state.value = UpdateState.Downloading(release, progress = 0)
            dl.download(asset.downloadUrl, "realms-${release.tag.removePrefix("v")}.apk", asset.size)
                .collect { p ->
                    when (p) {
                        is ApkDownloader.Progress.Streaming ->
                            _state.value = UpdateState.Downloading(release, progress = p.percent)
                        is ApkDownloader.Progress.Done ->
                            _state.value = UpdateState.ReadyToInstall(release, p.file)
                        is ApkDownloader.Progress.Failed ->
                            _state.value = UpdateState.Available(release)
                    }
                }
        }
        downloadJob = job
        return job
    }

    fun cancelDownload() {
        val current = _state.value
        downloadJob?.cancel()
        if (current is UpdateState.Downloading) {
            _state.value = UpdateState.Available(current.release)
        }
    }

    /** UI-friendly: pass-throughs for the prefs flows. */
    fun observableChannel(): kotlinx.coroutines.flow.Flow<UpdateChannel> = prefs.channel
    fun observableLastCheckedAt(): kotlinx.coroutines.flow.Flow<Long?> = prefs.lastCheckedAt

    companion object {
        /** 6 hours. */
        const val DEFAULT_CACHE_TTL_MS: Long = 6L * 60L * 60L * 1000L
    }
}

/**
 * Process-level holder so MainActivity / Compose can grab the repository
 * without DI. Mirrors RealmsDbHolder / ContentRepository conventions.
 */
object UpdateRepositoryHolder {
    @Volatile private var repo: UpdateRepository? = null

    fun init(
        context: android.content.Context,
        currentVersionName: String,
    ) {
        if (repo != null) return
        val httpClient = GitHubReleasesClient.defaultHttpClient()
        val client = GitHubReleasesSource(
            GitHubReleasesClient(httpClient = httpClient, userAgent = "Realms/$currentVersionName")
        )
        val prefs = UpdatePrefs(context.applicationContext)
        val downloader = ApkDownloader(httpClient, java.io.File(context.cacheDir, "updates"))
        repo = UpdateRepository(
            currentVersionName = currentVersionName,
            client = client,
            prefs = prefs,
            downloader = downloader
        )
    }

    val instance: UpdateRepository
        get() = repo ?: error("UpdateRepositoryHolder not initialized")
}
