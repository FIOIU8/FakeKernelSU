package me.weishu.kernelsu.fake

import java.io.File
import java.util.zip.ZipFile

/**
 * Reading and extracting a KernelSU module zip.
 *
 * This is deliberately free of Android API: the module id rule, the
 * `module.prop` parser and the extraction limits are the parts that decide
 * whether an untrusted zip may write to the app's storage, so they are the
 * parts that need unit tests. (They used to be private members of
 * [FakeModules], which reaches for `ksuApp` and `org.json` and therefore
 * cannot run on the JVM.)
 *
 * A module zip comes from the outside world. Without limits it can fill the
 * device up (a few hundred kilobytes of highly compressible data expand to
 * gigabytes), write outside the module directory (`../../..` entry names) or
 * simply cost a fortune in extraction time. All three are bounded here.
 */
object ModuleZip {

    /**
     * Hard limits for one extraction.
     *
     * `module.prop` is not special-cased: a module that ships a 20 MiB
     * `module.prop` is broken, not unlucky.
     */
    data class Limits(
        val entrySize: Long = 16L * 1024 * 1024,
        val totalSize: Long = 128L * 1024 * 1024,
        val entryCount: Int = 4096,
    )

    sealed class Result {
        /** Every entry was written. */
        data object Ok : Result()

        /** Nothing further was written; the caller must clean [target] up. */
        data class Rejected(val reason: String) : Result()
    }

    /**
     * A module id ends up as a directory name, so it has to be a plain name:
     * no separators, no `..`, no leading dot or dash. Upstream's `ksud`
     * applies the same rule.
     */
    private val MODULE_ID_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9._-]+$")

    fun isValidId(id: String): Boolean = MODULE_ID_REGEX.matches(id)

    /**
     * Parses `key=value` lines, ignoring blanks and `#` comments.
     *
     * A line without `=` is dropped; values may contain `=` (only the first
     * one splits), which is what upstream's `module.prop` format allows.
     */
    fun parseProp(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val idx = line.indexOf('=')
            if (idx <= 0) return@forEach
            out[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
        }
        return out
    }

    /**
     * Extracts [archive] into [target], enforcing [limits].
     *
     * Entries whose name would escape [target] are skipped (and reported);
     * an archive that breaks a size limit is rejected as a whole. The caller
     * is responsible for deleting [target] when the result is [Result.Rejected].
     */
    fun extract(
        archive: ZipFile,
        target: File,
        limits: Limits = Limits(),
        onStdout: (String) -> Unit = {},
        onStderr: (String) -> Unit = {},
    ): Result {
        target.mkdirs()
        var total = 0L
        var count = 0
        val entries = archive.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            if (++count > limits.entryCount) {
                onStderr("! Too many entries in the archive")
                return Result.Rejected("The archive contains more than ${limits.entryCount} entries")
            }
            if (entry.size > limits.entrySize) {
                onStderr("! ${entry.name} is larger than ${human(limits.entrySize)}")
                return Result.Rejected("An entry is larger than the ${human(limits.entrySize)} limit")
            }
            total += entry.size
            if (total > limits.totalSize) {
                onStderr("! The archive expands to more than ${human(limits.totalSize)}")
                return Result.Rejected("The archive expands to more than ${human(limits.totalSize)}")
            }

            val file = resolveInside(target, entry.name)
            if (file == null) {
                onStderr("! Skipping unsafe entry name: ${entry.name}")
                continue
            }
            file.parentFile?.mkdirs()
            archive.getInputStream(entry).use { inStream ->
                file.outputStream().use { out -> inStream.copyTo(out) }
            }
            if (entry.name == "module.prop") onStdout("- Wrote module.prop")
        }
        return Result.Ok
    }

    /**
     * Resolves an entry name inside [target], or null when it would land
     * outside. Both `/etc/passwd` and `../../evil` are rejected, `docs/x.md`
     * is accepted.
     */
    private fun resolveInside(target: File, name: String): File? {
        if (name.isEmpty()) return null
        // canonicalFile resolves ".." and symlinks, so the containment test
        // below is done on the real path rather than on the raw entry name.
        val root = runCatching { target.canonicalFile }.getOrNull() ?: return null
        val file = runCatching { File(root, name).canonicalFile }.getOrNull() ?: return null
        if (file == root) return null
        return if (file.path.startsWith(root.path + File.separator)) file else null
    }

    /** "16 MiB" instead of "16777216" - the string ends up in a toast. */
    private fun human(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> "${bytes / (1024L * 1024)} MiB"
        bytes >= 1024L -> "${bytes / 1024L} KiB"
        else -> "$bytes B"
    }
}
