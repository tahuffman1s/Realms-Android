package com.realmsoffate.game.data.updater

/**
 * Minimal semver per https://semver.org §11. Build metadata (`+...`) is parsed
 * but ignored for ordering. Prerelease identifiers are stored as a list and
 * compared per-identifier (numeric vs alphanumeric rules).
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String>
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        // §11.3: a release version > any prerelease at same MMP
        val a = prerelease
        val b = other.prerelease
        if (a.isEmpty() && b.isEmpty()) return 0
        if (a.isEmpty()) return 1
        if (b.isEmpty()) return -1
        // §11.4: per-identifier compare
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val c = compareIdentifiers(a[i], b[i])
            if (c != 0) return c
        }
        // §11.4.4: longer wins when prefix matches
        return a.size.compareTo(b.size)
    }

    private fun compareIdentifiers(a: String, b: String): Int {
        val ai = a.toIntOrNull()
        val bi = b.toIntOrNull()
        return when {
            ai != null && bi != null -> ai.compareTo(bi)
            ai != null && bi == null -> -1   // numeric < alphanumeric per §11.4.3
            ai == null && bi != null -> 1
            else -> a.compareTo(b)
        }
    }

    companion object {
        private val CORE = Regex("""(\d+)\.(\d+)\.(\d+)""")

        fun parse(raw: String): SemVer {
            val noV = raw.removePrefix("v").removePrefix("V")
            val noBuild = noV.substringBefore('+')
            val core = noBuild.substringBefore('-')
            val pre = noBuild.substringAfter('-', missingDelimiterValue = "")
            val m = CORE.matchEntire(core)
                ?: throw IllegalArgumentException("Bad semver: '$raw'")
            val parts = if (pre.isEmpty()) emptyList() else pre.split('.')
            for (p in parts) {
                if (p.isEmpty()) throw IllegalArgumentException("Empty prerelease segment in '$raw'")
            }
            return SemVer(
                major = m.groupValues[1].toInt(),
                minor = m.groupValues[2].toInt(),
                patch = m.groupValues[3].toInt(),
                prerelease = parts
            )
        }
    }
}
