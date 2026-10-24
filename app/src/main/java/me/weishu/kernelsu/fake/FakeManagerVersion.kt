package me.weishu.kernelsu.fake

import me.weishu.kernelsu.BuildConfig

/**
 * FakeKernelSU: the manager version the app pretends to be.
 *
 * A real manager reports a plain `x.y.z` release name. This fork's build
 * reports a git-describe string (`1.0.0-fake-16-gf2c5e7c`), which would give
 * the disguise away on the about page and in the environment handed to module
 * scripts. The lab therefore lets the user type a release-shaped name, and
 * every surface reads it back through here so they cannot disagree.
 */
object FakeManagerVersion {

    /**
     * Maps `x.y.z` onto the scheme KernelSU numbers its releases with:
     * `major * 10000 + minor * 100 + patch`, so `3.1.2` is `30102`.
     *
     * An optional `v` prefix or `-suffix` is tolerated because that is what a
     * real `versionName` looks like. Returns null when the text is not a
     * version at all, which simply means "nothing to derive".
     */
    fun codeFromName(name: String): Long? {
        val core = name.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        val parts = core.split('.')
        if (parts.size !in 2..3) return null
        if (parts.any { part -> part.isEmpty() || part.length > 3 || part.any { !it.isDigit() } }) {
            return null
        }
        val major = parts[0].toLongOrNull() ?: return null
        val minor = parts[1].toLongOrNull() ?: return null
        val patch = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        return (major * 10_000L + minor * 100L + patch).takeIf { it > 0L }
    }

    /** The lab override when set, otherwise [real]. */
    fun name(real: String): String = FakeState.managerVersionNameOverride.ifBlank { real }

    /** [name] for the running build. */
    fun name(): String = name(BuildConfig.VERSION_NAME)

    /**
     * The versionCode that goes with [name], so a disguised `3.1.2` never ends
     * up paired with the fork's own build number.
     *
     * Order: the lab's explicit code override, then the code derived from the
     * disguised name, then the pre-#2 "KSU version" compat value, then [real].
     */
    fun code(real: Long): Long {
        val override = FakeState.managerVersionCodeOverride
        if (override > 0L) return override
        codeFromName(FakeState.managerVersionNameOverride)?.let { return it }
        val legacy = FakeState.ksuVersion
            .takeIf { it != FakeState.DEFAULT_KSU_VERSION }
            ?.toLong()
        return legacy ?: real
    }
}
