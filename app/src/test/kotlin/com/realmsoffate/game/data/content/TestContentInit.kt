package com.realmsoffate.game.data.content

import java.io.File

/**
 * JVM-test helper that seeds [ContentRepository] from the project's
 * `src/main/assets/` tree without requiring an Android `Context`.
 *
 * Use this in plain-JUnit (non-Robolectric) tests that exercise code which now
 * reads from [ContentRepository] (M3+: `Races`, `Classes`, `Feats`).
 * Robolectric tests get init for free via `RealmsApp.onCreate`.
 *
 * Idempotent: safe to call multiple times.
 */
internal object TestContentInit {

    private val assetsRoot: File by lazy {
        // gradle test working dir is the app module; src/main/assets is the canonical asset root.
        // Fall back to the rooted-from-repo path so it works no matter where the test is launched from.
        listOf(
            File("src/main/assets"),
            File("app/src/main/assets")
        ).firstOrNull { it.isDirectory }
            ?: error("Could not find content assets root (looked in src/main/assets and app/src/main/assets)")
    }

    @Volatile private var initialized = false

    fun ensureLoaded() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            ContentRepository.initializeFrom { path ->
                File(assetsRoot, path).also { f ->
                    if (!f.exists()) error("Test asset not found: $f")
                }.inputStream()
            }
            initialized = true
        }
    }
}
