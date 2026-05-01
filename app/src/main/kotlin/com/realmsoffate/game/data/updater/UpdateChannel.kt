package com.realmsoffate.game.data.updater

enum class UpdateChannel {
    /** Only releases with `prerelease = false` are surfaced. */
    STABLE_ONLY,
    /** All releases including alphas/betas/RCs are surfaced. */
    INCLUDE_PRERELEASES;

    fun accepts(release: ReleaseInfo): Boolean = when (this) {
        STABLE_ONLY -> !release.isPrerelease
        INCLUDE_PRERELEASES -> true
    }

    companion object {
        /** Default for pre-1.0 era. Flip to STABLE_ONLY at 1.0.0 ship time. */
        val DEFAULT: UpdateChannel = INCLUDE_PRERELEASES

        fun fromString(value: String?): UpdateChannel = when (value) {
            STABLE_ONLY.name -> STABLE_ONLY
            INCLUDE_PRERELEASES.name -> INCLUDE_PRERELEASES
            else -> DEFAULT
        }
    }
}
