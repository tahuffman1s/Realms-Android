package com.realmsoffate.game.data.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format DTO for a single GitHub release. Field names match the
 * Releases API. Use [ReleaseInfo] (a domain object derived from this) in
 * the rest of the codebase — keep wire concerns out of UpdateRepository.
 */
@Serializable
data class ReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val prerelease: Boolean = false,
    val body: String? = null,
    val assets: List<AssetDto> = emptyList()
)

@Serializable
data class AssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long
)

/**
 * Domain shape consumed by UpdateRepository. Holds the parsed [SemVer] for
 * ordering, plus only the asset(s) we actually care about.
 */
data class ReleaseInfo(
    val tag: String,
    val name: String,
    val version: SemVer,
    val isPrerelease: Boolean,
    val body: String,
    val assets: List<Asset>
) {
    data class Asset(val name: String, val downloadUrl: String, val size: Long)

    /** Find the canonical Realms APK asset for this release. */
    fun realmsApkAsset(): Asset? = assets.firstOrNull {
        it.name.startsWith("realms-") && it.name.endsWith("-release.apk")
    }

    companion object {
        fun fromDto(dto: ReleaseDto): ReleaseInfo? {
            val ver = runCatching { SemVer.parse(dto.tagName) }.getOrNull() ?: return null
            return ReleaseInfo(
                tag = dto.tagName,
                name = dto.name ?: dto.tagName,
                version = ver,
                isPrerelease = dto.prerelease,
                body = dto.body ?: "",
                assets = dto.assets.map { Asset(it.name, it.browserDownloadUrl, it.size) }
            )
        }
    }
}
