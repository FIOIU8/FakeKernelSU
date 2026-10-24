package me.weishu.kernelsu.ui.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Parcelable
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.fake.FakeFeatures
import me.weishu.kernelsu.fake.FakeModules
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.ksuApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * @author weishu
 * @date 2023/1/1.
 *
 * FakeKernelSU: this used to be the ksud (Rust daemon shipped as libksud.so)
 * bridge. Everything now runs locally in the app's private storage — no root,
 * no kernel. The public API is kept identical so the UI layer is untouched.
 */
private const val TAG = "KsuCli"

data class FlashResult(val code: Int, val err: String, val showReboot: Boolean)

object KsuCli {
    // `by lazy` instead of eager vals: a system image without a usable shell
    // must not take the whole app down while the KsuCli class is initialized.
    val SHELL: Shell by lazy { createRootShell() }
    val GLOBAL_MNT_SHELL: Shell by lazy { createRootShell(true) }
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
 * Creates the shell the demo uses for read-only system queries.
 *
 * FakeKernelSU must never obtain root. libsu's builder tries `su` first and
 * only falls back to a plain shell when root is unavailable (see
 * `BuilderImpl.start()` in libsu), so on a rooted device the default builder
 * would hand this app a **root** shell - which would let the bugreport
 * collector read `/data/adb` and the WebUI bridge run arbitrary commands.
 * `Shell.FLAG_NON_ROOT_SHELL` makes libsu skip both `su` attempts and go
 * straight to an unprivileged `sh`.
 *
 * @param globalMnt kept for API parity with upstream; a non-root shell has no
 *   mount namespace to switch to, so the flag is ignored.
 */
fun createRootShell(globalMnt: Boolean = false): Shell {
    Shell.enableVerboseLogging = BuildConfig.DEBUG
    val builder = Shell.Builder.create().setFlags(Shell.FLAG_NON_ROOT_SHELL)
    return try {
        builder.build("sh")
    } catch (e: Throwable) {
        Log.w(TAG, "unable to create a non-root shell: ", e)
        throw e
    }
}

/** Whether "root" is available — in the demo this is a panel switch. */
fun rootAvailable(): Boolean = FakeState.rootAvailable

// ------------------------------------------------------------------ features

private fun readFeatures(): JSONObject = FakeFeatures.read()

private fun writeFeatures(features: JSONObject) = FakeFeatures.write(features)

private fun defaultFeatureValue(feature: String): Long = FakeFeatures.defaultValue(feature)

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
    // Upstream parses `ksud feature check <name>` here. The demo has no LKM to
    // take a feature over, so every feature is user-controllable — except when
    // the lab deliberately simulates "managed"/"unsupported" to show the
    // greyed-out state.
    FakeState.featureStatusOverride
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
    return replayScript(script, onStdout, onStderr) == 0
}

/**
 * FakeKernelSU never executes code that came out of a user-provided ZIP.
 *
 * Upstream runs a module's `action.sh` (or a one-shot script) through ksud
 * with root. A demo has no business doing that: it would either need root
 * (which this app must never obtain) or it would run untrusted code with the
 * app's own privileges. The script is therefore only *replayed* - its content
 * is echoed as a simulated log and the call reports success, so the action
 * screen and the script screen still look alive.
 */
private const val MAX_REPLAYED_SCRIPT_LINES = 200

private fun replayScript(
    script: File,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): Int {
    onStdout("- FakeKernelSU does not execute scripts: it has no root and no kernel.")
    onStdout("- ${script.name} (${script.length()} bytes) would be run by ksud as root:")
    val lines = runCatching { script.readLines() }.getOrDefault(emptyList())
    lines.take(MAX_REPLAYED_SCRIPT_LINES).forEach { onStdout("  | $it") }
    if (lines.size > MAX_REPLAYED_SCRIPT_LINES) {
        onStdout("  | ... (${lines.size - MAX_REPLAYED_SCRIPT_LINES} more lines)")
    }
    onStdout("- Done (simulated).")
    return 0
}

/**
 * A prepared one-shot script command (see ScriptCommand) is only reported,
 * never executed - see [replayScript].
 */
fun runOneshotScript(
    command: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Int {
    onStdout("- Working directory: ${FakeState.ksuDir.absolutePath}")
    onStdout("- Command: $command")
    onStdout("- FakeKernelSU does not execute scripts: it has no root and no kernel.")
    onStdout("- Done (simulated).")
    return 0
}

/**
 * Removes a script's extraction directory.
 *
 * The name is kept for API parity with upstream, but nothing is ever created
 * as root any more, so this is a plain app-privileged delete.
 */
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
 *
 * Suspending rather than sleeping: the caller runs on Dispatchers.IO and the
 * screen owns the coroutine, so leaving the page now cancels the remaining
 * fake log instead of keeping a thread parked for two seconds.
 */
suspend fun installBoot(
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

    suspend fun step(line: String, delayMs: Long = 250L) {
        onStdout(line)
        delay(delayMs)
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
 * Result of probing a remote factory/OTA image for boot partitions.
 * Upstream this comes from the (deleted) image extractor.
 */
data class ProbeResult(val partitions: List<String>, val kmi: String?)

/**
 * R2: the download entry point stays as upstream ships it, but the demo never
 * parses a payload — returning no partitions makes the normal flow show
 * "no boot partition found" instead of pretending to succeed.
 */
suspend fun probeRemoteBootPartitions(url: String): ProbeResult =
    ProbeResult(partitions = emptyList(), kmi = null)





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

/**
 * The demo has no kernel, so there is no sepolicy store to write to.
 *
 * The old implementation answered `true` to everything, which made the app
 * profile page show a bogus "valid" state for input that was never applied
 * anywhere. The rules are now checked for a plausible shape and kept in the
 * local profile, so the page reflects what actually happened.
 *
 * An empty rule set is valid (upstream's `ksud sepolicy check ''` succeeds and
 * its null-check returned true): "this profile adds no rule" is the common
 * case, and rejecting it made the SELinux dialog undismissable and made
 * selecting a rule-less template silently do nothing.
 */
fun isSepolicyValid(rules: String?): Boolean {
    if (rules.isNullOrBlank()) return true
    return rules.lineSequence().all { line ->
        val trimmed = line.trim()
        trimmed.isEmpty() ||
            trimmed.startsWith("#") ||
            trimmed.split(Regex("\\s+")).size >= 2
    }
}

fun getSepolicy(pkg: String): String {
    if (pkg.isBlank()) return ""
    return Natives.getAppProfile(pkg, 0).rules
}

fun setSepolicy(pkg: String, rules: String): Boolean {
    if (pkg.isBlank() || !isSepolicyValid(rules)) return false
    val profile = Natives.getAppProfile(pkg, 0)
    return Natives.setAppProfile(profile.copy(rules = rules))
}

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
