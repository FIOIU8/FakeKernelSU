package me.weishu.kernelsu.ui.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.Parcelable
import android.os.SystemClock
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.fake.FakeModules
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.ksuApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.ZipFile

/**
 * @author weishu
 * @date 2023/1/1.
 *
 * FakeKernelSU: this used to be the ksud (Rust daemon shipped as libksud.so)
 * bridge. Everything now runs locally in the app's private storage — no root,
 * no kernel. The public API is kept identical so the UI layer is untouched.
 */
private const val TAG = "KsuCli"

private const val FEATURES_FILE = "features.json"

data class FlashResult(val code: Int, val err: String, val showReboot: Boolean)

object KsuCli {
    val SHELL: Shell = createRootShell()
    val GLOBAL_MNT_SHELL: Shell = createRootShell(true)
}

fun getRootShell(globalMnt: Boolean = false): Shell {
    return if (globalMnt) KsuCli.GLOBAL_MNT_SHELL else {
        KsuCli.SHELL
    }
}

inline fun <T> withNewRootShell(
    globalMnt: Boolean = false,
    block: Shell.() -> T
): T {
    return createRootShell(globalMnt).use(block)
}

fun Uri.getFileName(context: Context): String? {
    var fileName: String? = null
    val contentResolver: ContentResolver = context.contentResolver
    val cursor: Cursor? = contentResolver.query(this, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }
    return fileName
}

/**
 * Upstream builds a root shell out of libksud.so; the fake manager just runs a
 * plain unprivileged sh — good enough for reading system properties.
 */
fun createRootShell(globalMnt: Boolean = false): Shell {
    Shell.enableVerboseLogging = BuildConfig.DEBUG
    val builder = Shell.Builder.create()
    return try {
        builder.build("sh")
    } catch (e: Throwable) {
        Log.w(TAG, "sh failed: ", e)
        builder.build("sh")
    }
}

/** Whether "root" is available — in the demo this is a panel switch. */
fun rootAvailable(): Boolean = FakeState.rootAvailable

// ------------------------------------------------------------------ features

private fun featuresFile(): File {
    FakeState.ensureDirs()
    return File(FakeState.ksuDir, FEATURES_FILE)
}

private fun readFeatures(): JSONObject {
    val file = featuresFile()
    if (!file.exists()) return JSONObject()
    return runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
}

private fun writeFeatures(features: JSONObject) {
    runCatching { featuresFile().writeText(features.toString()) }
}

private fun defaultFeatureValue(feature: String): Long = when (feature) {
    "su_compat" -> 1L
    "kernel_umount" -> 1L
    "selinux_hide" -> 1L
    "sulog" -> 1L
    "adb_root" -> 0L
    "shell_su_adb_only" -> 0L
    else -> 0L
}

/**
 * Emulates `ksud feature ...`. Recognized forms:
 *  - "feature set <name> <0|1>"
 *  - "feature save"
 * Anything else is a successful no-op.
 */
fun execKsud(args: String, newShell: Boolean = false, globalMnt: Boolean = false): Boolean {
    val parts = args.trim().split(Regex("\\s+"))
    if (parts.size >= 4 && parts[0] == "feature" && parts[1] == "set") {
        val name = parts[2]
        val value = parts[3].toLongOrNull() ?: return false
        val features = readFeatures()
        features.put(name, value)
        writeFeatures(features)
        return true
    }
    Log.i(TAG, "execKsud (no-op): $args")
    return true
}

suspend fun getFeatureStatus(feature: String): String = withContext(Dispatchers.IO) {
    // "managed" is the healthy upstream status; unsupported/forced are warnings.
    "managed"
}

suspend fun getFeaturePersistValue(feature: String): Long? = withContext(Dispatchers.IO) {
    readFeatures().optLong(feature, defaultFeatureValue(feature))
}

// Current value in kernel
suspend fun getFeatureValue(feature: String): Long? = withContext(Dispatchers.IO) {
    readFeatures().optLong(feature, defaultFeatureValue(feature))
}

// ------------------------------------------------------------------- install

/** Upstream ensures ksud is present after boot; the fake manager does nothing. */
fun install() {
    Log.w(TAG, "install() is a no-op in FakeKernelSU")
}

fun listModules(): String {
    return FakeModules.listJson()
}

fun getModuleCount(): Int {
    val result = listModules()
    runCatching {
        val array = JSONArray(result)
        return array.length()
    }.getOrElse { return 0 }
}

fun getSuperuserCount(): Int {
    return Natives.getSuperuserCount()
}

fun toggleModule(id: String, enable: Boolean): Boolean {
    val ok = FakeModules.setEnabled(id, enable)
    Log.i(TAG, "module ${if (enable) "enable" else "disable"} $id result: $ok")
    return ok
}

fun undoUninstallModule(id: String): Boolean {
    val ok = FakeModules.undoUninstall(id)
    Log.i(TAG, "undo uninstall module $id result: $ok")
    return ok
}

fun uninstallModule(id: String): Boolean {
    val ok = FakeModules.markUninstall(id)
    Log.i(TAG, "uninstall module $id result: $ok")
    return ok
}

// --------------------------------------------------------------------- flash

fun flashModule(
    uri: Uri,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): FlashResult {
    onStdout("- Installing module from ${uri.getFileName(ksuApp) ?: uri.lastPathSegment}")
    val result = FakeModules.installFromZip(uri, onStdout, onStderr)
    Log.i("KernelSU", "install module $uri result: $result")
    return FlashResult(result.code, result.err, false)
}

fun runModuleAction(
    moduleId: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val dir = FakeModules.moduleDir(moduleId)
    if (dir == null) {
        onStderr("! Module $moduleId not found")
        return false
    }
    val script = File(dir, "action.sh")
    if (!script.exists()) {
        onStderr("! Module $moduleId has no action script")
        return false
    }
    onStdout("- Running action.sh of $moduleId")
    return runLocalScript(script.absolutePath, dir.absolutePath, onStdout, onStderr) == 0
}

/**
 * Runs a prepared one-shot script command (see ScriptCommand). Unlike the rest
 * of the fake daemon this really executes — as the unprivileged app user — so
 * the script screen produces genuine output.
 */
fun runOneshotScript(
    command: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Int {
    val cwd = FakeState.ksuDir.absolutePath
    return runLocalScript(command, cwd, onStdout, onStderr)
}

private fun runLocalScript(
    command: String,
    cwd: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): Int {
    return try {
        val process = ProcessBuilder("sh", "-c", command)
            .directory(File(cwd).takeIf { it.isDirectory } ?: ksuApp.filesDir)
            .redirectErrorStream(true)
            .start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            onStdout(line!!)
        }
        val code = process.waitFor()
        if (code != 0) onStderr("! Exit code: $code")
        code
    } catch (e: Exception) {
        onStderr("! ${e.message ?: "Failed to run script"}")
        -1
    }
}

/** Removes a script's extraction directory, including files the script created as root. */
fun removeAsRoot(path: String): Boolean {
    return runCatching { File(path).deleteRecursively() }.getOrDefault(false)
}

fun restoreBoot(
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): FlashResult {
    onStdout("- Looking for a backup of the stock boot image")
    onStderr("! No backup found. Nothing to restore.")
    return FlashResult(1, "No backup found", false)
}

fun uninstallPermanently(
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): FlashResult {
    onStdout("- Removing FakeKernelSU data")
    FakeState.uninstallPermanent()
    onStdout("- Done")
    return FlashResult(0, "", false)
}

@Parcelize
sealed class LkmSelection : Parcelable {
    @Parcelize
    data class LkmUri(val uri: Uri) : LkmSelection()

    @Parcelize
    data class KmiString(val value: String) : LkmSelection()

    @Parcelize
    data object KmiNone : LkmSelection()
}

/**
 * The fake "direct install": writes the install flag while playing a
 * believable ksud boot-patch log. No partition is ever touched.
 */
fun installBoot(
    bootUri: Uri?,
    lkm: LkmSelection,
    ota: Boolean,
    partition: String?,
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): FlashResult {
    val target = partition ?: "boot"
    val kmi = (lkm as? LkmSelection.KmiString)?.value
        ?: runCatching { Os.uname().release }.getOrDefault("unknown")

    fun step(line: String, delayMs: Long = 250L) {
        onStdout(line)
        try {
            Thread.sleep(delayMs)
        } catch (_: InterruptedException) {
        }
    }

    step("- Detecting device and kernel", 150)
    step("- Kernel: ${runCatching { Os.uname().release }.getOrDefault("unknown")}")
    step("- KMI: $kmi")
    if (bootUri != null) {
        step("- Using provided boot image: ${bootUri.getFileName(ksuApp)}")
    } else {
        step("- Locating $target partition")
        step("- Backing up stock boot image", 200)
    }
    step("- Patching kernel image with KernelSU", 400)
    if (allowShell) step("- --allow-shell")
    if (enableAdb) step("- --enable-adbd")
    if (forceBackup) step("- --backup")
    if (ota) step("- Flashing to inactive slot")
    step("- Flashing patched image to $target", 400)
    step("- Installing ksud to /data/adb/ksu", 200)
    step("- Writing module store", 100)

    FakeState.performFakeInstall()
    step("- Done! KernelSU is now active.")
    return FlashResult(0, "", false)
}

fun downloadBoot(
    url: String,
    partition: String,
    lkm: LkmSelection,
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): FlashResult {
    onStderr("! Downloading boot images is not available in FakeKernelSU.")
    return FlashResult(-1, "Unsupported in FakeKernelSU", false)
}




/**
 * Deliberately a no-op: a demo manager must never reboot the user's phone.
 */
fun reboot(reason: String = "") {
    Log.i(TAG, "reboot($reason) suppressed in FakeKernelSU")
}

// ------------------------------------------------------------- boot info

private fun systemProperty(key: String): String {
    return runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java)
        method.invoke(null, key) as? String
    }.getOrNull().orEmpty()
}

suspend fun getCurrentKmi(): String = withContext(Dispatchers.IO) {
    runCatching { Os.uname().release }.getOrDefault("")
}

suspend fun getSupportedKmis(): List<String> = withContext(Dispatchers.IO) {
    listOfNotNull(getCurrentKmi().takeIf { it.isNotBlank() })
}

suspend fun isAbDevice(): Boolean = withContext(Dispatchers.IO) {
    systemProperty("ro.boot.slot_suffix").isNotBlank()
}

suspend fun getDefaultPartition(): String = withContext(Dispatchers.IO) {
    if (!runCatching { Os.uname().release }.getOrDefault("").contains("android12-")) "init_boot" else "boot"
}

suspend fun getSlotSuffix(ota: Boolean): String = withContext(Dispatchers.IO) {
    val current = systemProperty("ro.boot.slot_suffix")
    if (!ota) return@withContext current
    when (current) {
        "_a" -> "_b"
        "_b" -> "_a"
        else -> current
    }
}

suspend fun getAvailablePartitions(): List<String> = withContext(Dispatchers.IO) {
    listOf(getDefaultPartition())
}

fun hasMagisk(): Boolean = false

// -------------------------------------------------------- sepolicy/templates

fun isSepolicyValid(rules: String?): Boolean = true

fun getSepolicy(pkg: String): String = ""

fun setSepolicy(pkg: String, rules: String): Boolean = true

fun listAppProfileTemplates(): List<String> = FakeModules.listTemplates()

fun getAppProfileTemplate(id: String): String = FakeModules.getTemplate(id)

fun setAppProfileTemplate(id: String, template: String): Boolean =
    FakeModules.setTemplate(id, template)

fun deleteAppProfileTemplate(id: String): Boolean = FakeModules.deleteTemplate(id)

fun forceStopApp(packageName: String, userId: Int? = null) {
    Log.i(TAG, "forceStopApp($packageName) suppressed in FakeKernelSU")
}

fun launchApp(packageName: String, userId: Int? = null) {
    Log.i(TAG, "launchApp($packageName) suppressed in FakeKernelSU")
}

fun restartApp(packageName: String, userId: Int? = null) {
    Log.i(TAG, "restartApp($packageName) suppressed in FakeKernelSU")
}
