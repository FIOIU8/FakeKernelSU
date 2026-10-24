package me.weishu.kernelsu.fake

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import me.weishu.kernelsu.ksuApp
import java.io.File

/**
 * Effective display state of the fake installation.
 *
 * AUTO follows the real [installed] flag (the one written by the fake
 * "direct install"); the other values force the home screen to show a
 * specific state, which is what the control panel exposes.
 */
enum class FakeInstallState {
    AUTO,
    INSTALLED,
    NOT_INSTALLED,
    UNSUPPORTED,
    ;

    companion object {
        fun fromName(name: String?): FakeInstallState =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** Where the su request simulation picks its victim from. */
enum class FakeSuRequestMode {
    PICK_APP,
    RANDOM_APP,
}

/**
 * SELinux mode shown on the home page. [SYSTEM] keeps the real `getenforce`
 * result, everything else is a fake override.
 */
enum class FakeSelinuxMode(val rawValue: String?) {
    SYSTEM(null),
    ENFORCING("Enforcing"),
    PERMISSIVE("Permissive"),
    DISABLED("Disabled"),
    ;

    companion object {
        fun fromName(name: String?): FakeSelinuxMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Seccomp status shown on the home page; `null` = read the real prctl value. */
enum class FakeSeccompMode(val rawValue: Int?) {
    SYSTEM(null),
    NOT_SUPPORTED(-1),
    DISABLED(0),
    STRICT(1),
    FILTER(2),
    ;

    companion object {
        fun fromName(name: String?): FakeSeccompMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * Single source of truth for every piece of fake state.
 *
 * Everything lives in the app's private storage:
 *  - SharedPreferences "fake_ksu"      -> flags & overrides
 *  - filesDir/adb/ksu/                 -> stands in for /data/adb/ksu
 *  - filesDir/adb/modules/             -> stands in for /data/adb/modules
 *
 * The in-memory values are the truth while the app runs; SharedPreferences is
 * only where they are persisted. Every property is backed by a Compose
 * `MutableState`, so a read inside a composable registers a snapshot read and
 * the UI refreshes on its own when a value changes. (Before, only
 * [stealthMode] was observable; everything else had to be pushed into the UI
 * by hand through `viewModel.refresh()`.)
 */
object FakeState {

    private val prefs: SharedPreferences by lazy {
        ksuApp.getSharedPreferences("fake_ksu", Context.MODE_PRIVATE)
    }

    // ---------------------------------------------------------------- paths

    val adbRoot: File get() = File(ksuApp.filesDir, "adb")
    val ksuDir: File get() = File(adbRoot, "ksu")
    val modulesDir: File get() = File(adbRoot, "modules")
    val profilesFile: File get() = File(ksuDir, "profiles.json")

    fun ensureDirs() {
        ksuDir.mkdirs()
        modulesDir.mkdirs()
    }

    // ------------------------------------------------------- in-memory state

    private val installedState = mutableStateOf(false)
    private val installedAtState = mutableStateOf(0L)
    private val stateOverrideState = mutableStateOf(FakeInstallState.AUTO)
    private val rootAvailableState = mutableStateOf(true)
    private val suEnabledState = mutableStateOf(true)
    private val ksuVersionState = mutableStateOf(DEFAULT_KSU_VERSION)
    private val lkmModeState = mutableStateOf<Boolean?>(null)
    private val kernelUapiVersionState = mutableStateOf(DEFAULT_KERNEL_UAPI_VERSION)
    private val jailbreakModeState = mutableStateOf(false)
    private val deviceModelOverrideState = mutableStateOf("")
    private val kernelVersionOverrideState = mutableStateOf("")
    private val fingerprintOverrideState = mutableStateOf("")
    private val selinuxModeState = mutableStateOf(FakeSelinuxMode.SYSTEM)
    private val seccompModeState = mutableStateOf(FakeSeccompMode.SYSTEM)
    private val managerVersionCodeOverrideState = mutableStateOf(0L)
    private val managerVersionNameOverrideState = mutableStateOf("")
    private val launcherAliasState = mutableStateOf(LauncherAlias.FAKE.name)
    private val inAppNameState = mutableStateOf("")
    private val brandNameState = mutableStateOf("")
    private val stealthModeState = mutableStateOf(false)
    private val lkmUpdateAvailableState = mutableStateOf(false)
    private val featureStatusOverrideState = mutableStateOf(FEATURE_STATUS_SUPPORTED)
    private val suRequestModeState = mutableStateOf(FakeSuRequestMode.PICK_APP)

    /**
     * The persisted values are read once, on the first access.
     *
     * `prefs` needs the Application, so the read cannot happen while this
     * object is being constructed - the app may touch it before
     * `Application.onCreate()` ran.
     */
    private var loaded = false

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        installedState.value = prefs.getBoolean(KEY_INSTALLED, false)
        installedAtState.value = prefs.getLong(KEY_INSTALLED_AT, 0L)
        stateOverrideState.value = FakeInstallState.fromName(prefs.getString(KEY_STATE_OVERRIDE, null))
        rootAvailableState.value = prefs.getBoolean(KEY_ROOT_AVAILABLE, true)
        suEnabledState.value = prefs.getBoolean(KEY_SU_ENABLED, true)
        ksuVersionState.value = prefs.getInt(KEY_KSU_VERSION, DEFAULT_KSU_VERSION)
        lkmModeState.value = when (prefs.getString(KEY_LKM_MODE, "gki")) {
            "lkm" -> true
            "none" -> false
            else -> null
        }
        kernelUapiVersionState.value = prefs.getInt(KEY_KERNEL_UAPI, DEFAULT_KERNEL_UAPI_VERSION)
        jailbreakModeState.value = prefs.getString(KEY_JAILBREAK_MODE, JAILBREAK_OFF) == JAILBREAK_ON
        deviceModelOverrideState.value = prefs.getString(KEY_DEVICE_MODEL, "") ?: ""
        kernelVersionOverrideState.value = prefs.getString(KEY_KERNEL_VERSION, "") ?: ""
        fingerprintOverrideState.value = prefs.getString(KEY_FINGERPRINT, "") ?: ""
        selinuxModeState.value = FakeSelinuxMode.fromName(prefs.getString(KEY_SELINUX_MODE, null))
        seccompModeState.value = FakeSeccompMode.fromName(prefs.getString(KEY_SECCOMP_MODE, null))
        managerVersionCodeOverrideState.value = prefs.getLong(KEY_MANAGER_VERSION_CODE, 0L)
        managerVersionNameOverrideState.value = prefs.getString(KEY_MANAGER_VERSION_NAME, "") ?: ""
        launcherAliasState.value =
            prefs.getString(KEY_LAUNCHER_ALIAS, LauncherAlias.FAKE.name) ?: LauncherAlias.FAKE.name
        inAppNameState.value = prefs.getString(KEY_IN_APP_NAME, "") ?: ""
        brandNameState.value = prefs.getString(KEY_BRAND_NAME, "") ?: ""
        stealthModeState.value = prefs.getBoolean(KEY_STEALTH, false)
        lkmUpdateAvailableState.value = prefs.getBoolean(KEY_LKM_UPDATE, false)
        featureStatusOverrideState.value =
            prefs.getString(KEY_FEATURE_STATUS, FEATURE_STATUS_SUPPORTED) ?: FEATURE_STATUS_SUPPORTED
        suRequestModeState.value = FakeSuRequestMode.entries.firstOrNull {
            it.name == prefs.getString(KEY_SU_MODE, FakeSuRequestMode.PICK_APP.name)
        } ?: FakeSuRequestMode.PICK_APP
    }

    // ------------------------------------------------------- the "real" flag

    /** Written by the fake direct-install; this is the "installation". */
    var installed: Boolean
        get() {
            ensureLoaded()
            return installedState.value
        }
        set(value) {
            ensureLoaded()
            installedState.value = value
            prefs.edit().putBoolean(KEY_INSTALLED, value).apply()
        }

    var installedAt: Long
        get() {
            ensureLoaded()
            return installedAtState.value
        }
        set(value) {
            ensureLoaded()
            installedAtState.value = value
            prefs.edit().putLong(KEY_INSTALLED_AT, value).apply()
        }

    // ------------------------------------------------------- display state

    var stateOverride: FakeInstallState
        get() {
            ensureLoaded()
            return stateOverrideState.value
        }
        set(value) {
            ensureLoaded()
            stateOverrideState.value = value
            prefs.edit().putString(KEY_STATE_OVERRIDE, value.name).apply()
        }

    var rootAvailable: Boolean
        get() {
            ensureLoaded()
            return rootAvailableState.value
        }
        set(value) {
            ensureLoaded()
            rootAvailableState.value = value
            prefs.edit().putBoolean(KEY_ROOT_AVAILABLE, value).apply()
        }

    /**
     * The `su` compat switch (`ksud feature su_compat`, surfaced as
     * [me.weishu.kernelsu.Natives.isSuEnabled]).
     *
     * Deliberately a separate flag from [rootAvailable]: upstream, "su is
     * enabled" is a kernel-side temporary switch while `shell.isRoot` answers
     * "do we have root at all". Sharing one value meant flipping the su mode
     * in Settings also flipped [me.weishu.kernelsu.ui.util.rootAvailable],
     * which drives `Natives.isFullFeatured()` and therefore the bottom bar,
     * the pager gestures and the startup preload.
     */
    var suEnabled: Boolean
        get() {
            ensureLoaded()
            return suEnabledState.value
        }
        set(value) {
            ensureLoaded()
            suEnabledState.value = value
            prefs.edit().putBoolean(KEY_SU_ENABLED, value).apply()
        }

    var ksuVersion: Int
        get() {
            ensureLoaded()
            return ksuVersionState.value
        }
        set(value) {
            ensureLoaded()
            ksuVersionState.value = value
            prefs.edit().putInt(KEY_KSU_VERSION, value).apply()
        }

    /** null -> show "GKI"; true -> "LKM"; false -> nothing (like upstream). */
    var lkmMode: Boolean?
        get() {
            ensureLoaded()
            return lkmModeState.value
        }
        set(value) {
            ensureLoaded()
            lkmModeState.value = value
            prefs.edit().putString(
                KEY_LKM_MODE,
                when (value) {
                    true -> "lkm"
                    false -> "none"
                    null -> "gki"
                },
            ).apply()
        }

    var kernelUapiVersion: Int
        get() {
            ensureLoaded()
            return kernelUapiVersionState.value
        }
        set(value) {
            ensureLoaded()
            kernelUapiVersionState.value = value
            prefs.edit().putInt(KEY_KERNEL_UAPI, value).apply()
        }

    /**
     * Jailbreak (late-load) mode. Stored as a string like [lkmMode] so more
     * late-load methods can be added later without a migration.
     */
    var jailbreakMode: Boolean
        get() {
            ensureLoaded()
            return jailbreakModeState.value
        }
        set(value) {
            ensureLoaded()
            jailbreakModeState.value = value
            prefs.edit()
                .putString(KEY_JAILBREAK_MODE, if (value) JAILBREAK_ON else JAILBREAK_OFF)
                .apply()
        }

    // ------------------------------------------- home system info overrides

    /** Empty string -> use the real device value. */
    var deviceModelOverride: String
        get() {
            ensureLoaded()
            return deviceModelOverrideState.value
        }
        set(value) {
            ensureLoaded()
            deviceModelOverrideState.value = value
            prefs.edit().putString(KEY_DEVICE_MODEL, value).apply()
        }

    var kernelVersionOverride: String
        get() {
            ensureLoaded()
            return kernelVersionOverrideState.value
        }
        set(value) {
            ensureLoaded()
            kernelVersionOverrideState.value = value
            prefs.edit().putString(KEY_KERNEL_VERSION, value).apply()
        }

    var fingerprintOverride: String
        get() {
            ensureLoaded()
            return fingerprintOverrideState.value
        }
        set(value) {
            ensureLoaded()
            fingerprintOverrideState.value = value
            prefs.edit().putString(KEY_FINGERPRINT, value).apply()
        }

    var selinuxMode: FakeSelinuxMode
        get() {
            ensureLoaded()
            return selinuxModeState.value
        }
        set(value) {
            ensureLoaded()
            selinuxModeState.value = value
            prefs.edit().putString(KEY_SELINUX_MODE, value.name).apply()
        }

    var seccompMode: FakeSeccompMode
        get() {
            ensureLoaded()
            return seccompModeState.value
        }
        set(value) {
            ensureLoaded()
            seccompModeState.value = value
            prefs.edit().putString(KEY_SECCOMP_MODE, value.name).apply()
        }

    // ------------------------------------------------ manager version overrides

    /**
     * Update check compares against this value. `0` = use the real APK
     * versionCode.
     */
    var managerVersionCodeOverride: Long
        get() {
            ensureLoaded()
            return managerVersionCodeOverrideState.value
        }
        set(value) {
            ensureLoaded()
            managerVersionCodeOverrideState.value = value
            prefs.edit().putLong(KEY_MANAGER_VERSION_CODE, value).apply()
        }

    /** Version name shown on the home page. Empty = use the real value. */
    var managerVersionNameOverride: String
        get() {
            ensureLoaded()
            return managerVersionNameOverrideState.value
        }
        set(value) {
            ensureLoaded()
            managerVersionNameOverrideState.value = value
            prefs.edit().putString(KEY_MANAGER_VERSION_NAME, value).apply()
        }

    // ----------------------------------------------------------- app naming

    /** Launcher label preset, see LauncherAlias enum. */
    var launcherAlias: String
        get() {
            ensureLoaded()
            return launcherAliasState.value
        }
        set(value) {
            ensureLoaded()
            launcherAliasState.value = value
            prefs.edit().putString(KEY_LAUNCHER_ALIAS, value).apply()
        }

    /** Arbitrary name shown inside the app (top bars, about page). */
    var inAppName: String
        get() {
            ensureLoaded()
            return inAppNameState.value
        }
        set(value) {
            ensureLoaded()
            inAppNameState.value = value
            prefs.edit().putString(KEY_IN_APP_NAME, value).apply()
        }

    /**
     * Brand token used everywhere the app names itself in text ("X Lab",
     * "X is not installed", ...). Empty = fall back to [inAppName] / app_name.
     */
    var brandName: String
        get() {
            ensureLoaded()
            return brandNameState.value
        }
        set(value) {
            ensureLoaded()
            brandNameState.value = value
            prefs.edit().putString(KEY_BRAND_NAME, value).apply()
        }

    // ------------------------------------------------------------- stealth

    /**
     * When true, the control panel entry disappears from Settings. The lab
     * itself is never removed - a long press on the home title still opens it.
     */
    var stealthMode: Boolean
        get() {
            ensureLoaded()
            return stealthModeState.value
        }
        set(value) {
            ensureLoaded()
            stealthModeState.value = value
            prefs.edit().putBoolean(KEY_STEALTH, value).apply()
        }

    /**
     * Simulated "a newer bundled LKM is available" banner on the home page.
     *
     * Upstream derives this from `ksuVersion != managerVersionCode`, which
     * cannot work here: the demo's kernel version is a hand-made number while
     * the manager versionCode comes from the APK build, so the two never match
     * and the banner would show up permanently. The lab drives it explicitly
     * instead, defaulting to off.
     */
    var lkmUpdateAvailable: Boolean
        get() {
            ensureLoaded()
            return lkmUpdateAvailableState.value
        }
        set(value) {
            ensureLoaded()
            lkmUpdateAvailableState.value = value
            prefs.edit().putBoolean(KEY_LKM_UPDATE, value).apply()
        }

    // --------------------------------------------------------- feature status

    /**
     * Emulated result of `ksud feature check`: `supported` (the default, every
     * toggle is usable), `managed` (taken over by a module -> greyed out) or
     * `unsupported`. Only the lab changes this, to demo the greyed-out state.
     */
    var featureStatusOverride: String
        get() {
            ensureLoaded()
            return featureStatusOverrideState.value
        }
        set(value) {
            ensureLoaded()
            featureStatusOverrideState.value = value
            prefs.edit().putString(KEY_FEATURE_STATUS, value).apply()
        }

    // -------------------------------------------------------- su simulation

    var suRequestMode: FakeSuRequestMode
        get() {
            ensureLoaded()
            return suRequestModeState.value
        }
        set(value) {
            ensureLoaded()
            suRequestModeState.value = value
            prefs.edit().putString(KEY_SU_MODE, value.name).apply()
        }

    // ------------------------------------------------------------ lifecycle

    /** Perform the fake "installation". */
    fun performFakeInstall() {
        ensureDirs()
        installed = true
        installedAt = System.currentTimeMillis()
        FakeSu.logDaemonStart()
    }

    /** 临时卸载: only clears the flag. */
    fun uninstallTemporary() {
        installed = false
        jailbreakMode = false
        stateOverride = FakeInstallState.AUTO
    }

    /**
     * Enter jailbreak (late-load) mode: real devices acquire root at runtime
     * through a kernel module, so this also implies LKM mode and an install.
     */
    fun applyJailbreak() {
        if (!installed) performFakeInstall()
        // The control panel may have pinned the display state to NOT_INSTALLED
        // / UNSUPPORTED; jailbreak implies a running (LKM) install, so drop the
        // override instead of leaving the home page on "not installed".
        stateOverride = FakeInstallState.AUTO
        lkmMode = true
        jailbreakMode = true
        FakeSu.logLateLoad()
    }

    /** Leaving jailbreak mode (manual toggle or a simulated reboot). */
    fun clearJailbreak() {
        jailbreakMode = false
    }

    /** 永久卸载: clears flag + all local demo data. */
    fun uninstallPermanent() {
        installed = false
        installedAt = 0L
        stateOverride = FakeInstallState.AUTO
        runCatching { adbRoot.deleteRecursively() }
        // The documents are gone with the directory - drop the cached copies
        // so nothing serves stale data.
        FakeProfiles.clear()
        FakeFeatures.clear()
        FakeSu.clear()
    }

    /** Wipe every trace of the demo: state, files, caches. */
    fun resetAll() {
        prefs.edit().clear().apply()
        loaded = true
        installedState.value = false
        installedAtState.value = 0L
        stateOverrideState.value = FakeInstallState.AUTO
        rootAvailableState.value = true
        suEnabledState.value = true
        ksuVersionState.value = DEFAULT_KSU_VERSION
        lkmModeState.value = null
        kernelUapiVersionState.value = DEFAULT_KERNEL_UAPI_VERSION
        jailbreakModeState.value = false
        deviceModelOverrideState.value = ""
        kernelVersionOverrideState.value = ""
        fingerprintOverrideState.value = ""
        selinuxModeState.value = FakeSelinuxMode.SYSTEM
        seccompModeState.value = FakeSeccompMode.SYSTEM
        managerVersionCodeOverrideState.value = 0L
        managerVersionNameOverrideState.value = ""
        launcherAliasState.value = LauncherAlias.FAKE.name
        inAppNameState.value = ""
        brandNameState.value = ""
        stealthModeState.value = false
        lkmUpdateAvailableState.value = false
        featureStatusOverrideState.value = FEATURE_STATUS_SUPPORTED
        suRequestModeState.value = FakeSuRequestMode.PICK_APP
        runCatching { adbRoot.deleteRecursively() }
        FakeProfiles.clear()
        FakeFeatures.clear()
        FakeSu.clear()
        ensureDirs()
    }

    private const val KEY_INSTALLED = "installed"
    private const val KEY_INSTALLED_AT = "installed_at"
    private const val KEY_STATE_OVERRIDE = "state_override"
    private const val KEY_ROOT_AVAILABLE = "root_available"
    private const val KEY_SU_ENABLED = "su_enabled"
    private const val KEY_KSU_VERSION = "ksu_version"
    private const val KEY_LKM_MODE = "lkm_mode"
    private const val KEY_JAILBREAK_MODE = "jailbreak_mode"

    private const val JAILBREAK_ON = "on"
    private const val JAILBREAK_OFF = "off"
    private const val KEY_KERNEL_UAPI = "kernel_uapi"
    private const val KEY_DEVICE_MODEL = "device_model"
    private const val KEY_KERNEL_VERSION = "kernel_version"
    private const val KEY_FINGERPRINT = "fingerprint"
    private const val KEY_SELINUX_MODE = "selinux_mode"
    private const val KEY_SECCOMP_MODE = "seccomp_mode"
    private const val KEY_MANAGER_VERSION_CODE = "manager_version_code"
    private const val KEY_MANAGER_VERSION_NAME = "manager_version_name"
    private const val KEY_LAUNCHER_ALIAS = "launcher_alias"
    private const val KEY_IN_APP_NAME = "in_app_name"
    private const val KEY_BRAND_NAME = "brand_name"
    private const val KEY_STEALTH = "stealth_mode"
    private const val KEY_LKM_UPDATE = "lkm_update_available"
    private const val KEY_SU_MODE = "su_request_mode"
    private const val KEY_FEATURE_STATUS = "feature_status"

    const val FEATURE_STATUS_SUPPORTED = "supported"
    const val FEATURE_STATUS_MANAGED = "managed"
    const val FEATURE_STATUS_UNSUPPORTED = "unsupported"

    /**
     * The KernelSU version the demo pretends to run.
     *
     * Upstream computes it as `30000 + git rev-list --count HEAD` at *kernel*
     * build time (see kernel/Kbuild), so a given release has one fixed number.
     * 32680 is tiann/KernelSU v3.3.1 (`30000 + 2680 commits`), i.e. the same
     * version this fork's manager reports.
     */
    const val DEFAULT_KSU_VERSION = 32680

    /** The uapi version the demo pretends to run. */
    const val DEFAULT_KERNEL_UAPI_VERSION = 32513
}

enum class LauncherAlias(val componentSuffix: String, val labelRes: Int) {
    FAKE(".LauncherAliasFake", me.weishu.kernelsu.R.string.fake_launcher_label_fake),
    KSU(".LauncherAliasKsu", me.weishu.kernelsu.R.string.fake_launcher_label_ksu),
    MANAGER(".LauncherAliasManager", me.weishu.kernelsu.R.string.fake_launcher_label_manager),
    SHORT(".LauncherAliasShort", me.weishu.kernelsu.R.string.fake_launcher_label_short),
    ;

    companion object {
        fun fromName(name: String?): LauncherAlias =
            entries.firstOrNull { it.name == name } ?: FAKE
    }
}
