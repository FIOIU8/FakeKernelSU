package me.weishu.kernelsu.fake

import android.net.Uri
import android.util.Log
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.FlashResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Local stand-in for `ksud module ...`.
 *
 * Modules live in filesDir/adb/modules/<id>/ and are fully extracted from
 * their zip, exactly like upstream /data/adb/modules/<id>/ — minus the boot
 * time hooks that would need a real kernel.
 */
object FakeModules {

    private const val TAG = "FakeModules"

    /**
     * Limits for the extraction, and the module id rule, live in [ModuleZip]
     * so they can be unit tested without an Android runtime.
     */
    private val LIMITS = ModuleZip.Limits()

    fun moduleDir(id: String): File? {
        // The id ends up in a path, so it must never be able to escape the
        // module directory.
        if (!ModuleZip.isValidId(id)) return null
        val dir = File(FakeState.modulesDir, id)
        return if (dir.isDirectory) dir else null
    }

    /** Drop modules staged for removal — simulates the reboot upstream needs. */
    fun purgePendingRemoval() {
        val root = FakeState.modulesDir
        if (!root.isDirectory) return
        root.listFiles()?.forEach { dir ->
            if (dir.isDirectory && File(dir, "remove").exists()) {
                runCatching { dir.deleteRecursively() }
            }
        }
    }

    fun listJson(): String {
        val root = FakeState.modulesDir
        val array = JSONArray()
        if (root.isDirectory) {
            root.listFiles()?.sortedBy { it.name }?.forEach { dir ->
                if (!dir.isDirectory) return@forEach
                val prop = readProp(dir) ?: return@forEach
                val id = prop.optString("id").ifEmpty { dir.name }
                // A module.prop that was tampered with must not be able to
                // point the rest of the app outside the module directory.
                if (!ModuleZip.isValidId(id)) {
                    Log.w(TAG, "skipping module with invalid id: $id")
                    return@forEach
                }
                array.put(
                    JSONObject().apply {
                        put("id", id)
                        put("name", prop.optString("name", id))
                        put("author", prop.optString("author", "Unknown"))
                        put("version", prop.optString("version", "Unknown"))
                        put("versionCode", prop.optLong("versionCode", 0))
                        put("description", prop.optString("description"))
                        put("enabled", !File(dir, "disable").exists())
                        put("update", File(dir, "update").exists())
                        put("remove", File(dir, "remove").exists())
                        put("updateJson", prop.optString("updateJson"))
                        put("web", File(dir, "webui").isDirectory)
                        put("action", File(dir, "action.sh").exists())
                        put("metamodule", prop.optString("metamodule") == "1")
                        put("actionIcon", prop.optString("actionIcon"))
                        put("webuiIcon", prop.optString("webuiIcon"))
                        put("preinit", prop.optString("preinit"))
                    }
                )
            }
        }
        return array.toString()
    }

    fun setEnabled(id: String, enabled: Boolean): Boolean {
        val dir = moduleDir(id) ?: return false
        val disable = File(dir, "disable")
        return runCatching {
            if (enabled) disable.delete() else disable.createNewFile()
            true
        }.getOrDefault(false)
    }

    fun markUninstall(id: String): Boolean {
        val dir = moduleDir(id) ?: return false
        return runCatching { File(dir, "remove").createNewFile() }.isSuccess
    }

    fun undoUninstall(id: String): Boolean {
        val dir = moduleDir(id) ?: return false
        return runCatching { File(dir, "remove").delete() }.isSuccess
    }

    /**
     * Installs a module zip: extracts it into modules/<id>/ like ksud does.
     * Returns a FlashResult so the caller can drive the flash UI unchanged.
     */
    fun installFromZip(
        uri: Uri,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): FlashResult {
        val cache = File.createTempFile("module-", ".zip", ksuApp.cacheDir)
        try {
            val input = ksuApp.contentResolver.openInputStream(uri)
                ?: return FlashResult(1, "Cannot open $uri", false)
            input.use { copy -> cache.outputStream().use { out -> copy.copyTo(out) } }

            val zip = ZipFile(cache)
            zip.use { archive ->
                val propEntry = archive.getEntry("module.prop")
                    ?: return FlashResult(1, "This zip is not a KernelSU module (no module.prop)", false)
                val propText = archive.getInputStream(propEntry).bufferedReader().readText()
                val prop = parseProp(propText)
                val id = prop.optString("id")
                if (!ModuleZip.isValidId(id)) {
                    return FlashResult(1, "Invalid module id: '$id'", false)
                }
                onStdout("- Module id: $id")
                onStdout("- Extracting to ${FakeState.modulesDir.absolutePath}/$id")

                // Everything is validated before the first byte is written, so
                // a rejected zip does not leave a half-extracted module behind.
                val target = File(FakeState.modulesDir, id)
                val extracted = ModuleZip.extract(archive, target, LIMITS, onStdout, onStderr)
                if (extracted is ModuleZip.Result.Rejected) {
                    runCatching { target.deleteRecursively() }
                    return FlashResult(1, extracted.reason, false)
                }

                // keep the original zip for reference / re-install
                cache.copyTo(File(target, "module.zip"), overwrite = true)
                onStdout("- Module installed")
            }
            return FlashResult(0, "", false)
        } catch (e: Exception) {
            Log.w(TAG, "module install failed", e)
            return FlashResult(1, e.message ?: "Failed to read module", false)
        } finally {
            cache.delete()
        }
    }

    // -------------------------------------------------------------- templates

    private fun templatesDir(): File {
        val dir = File(FakeState.ksuDir, "templates")
        dir.mkdirs()
        return dir
    }

    private fun templateFile(id: String): File {
        val safe = id.map { c ->
            if (c.isLetterOrDigit() || c in "._-") c else '_'
        }.joinToString("")
        val name = if (safe.isEmpty() || safe.length > 64) md5(id) else safe
        return File(templatesDir(), "$name.template")
    }

    private fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun listTemplates(): List<String> {
        val dir = templatesDir()
        return dir.listFiles()?.map { it.nameWithoutExtension }?.sorted().orEmpty()
    }

    fun getTemplate(id: String): String {
        val file = templateFile(id)
        return if (file.exists()) file.readText() else ""
    }

    fun setTemplate(id: String, template: String): Boolean {
        return runCatching { templateFile(id).writeText(template) }.isSuccess
    }

    fun deleteTemplate(id: String): Boolean {
        return runCatching { templateFile(id).delete() }.isSuccess
    }

    // ------------------------------------------------------------------ utils

    private fun readProp(dir: File): JSONObject? {
        val file = File(dir, "module.prop")
        if (!file.exists()) return null
        return runCatching { parseProp(file.readText()) }.getOrNull()
    }

    /** `module.prop` as the JSON the module list speaks; parsing lives in [ModuleZip]. */
    private fun parseProp(text: String): JSONObject {
        val obj = JSONObject()
        ModuleZip.parseProp(text).forEach { (key, value) -> obj.put(key, value) }
        return obj
    }
}
