package me.weishu.kernelsu.fake

import android.content.Context
import android.content.SharedPreferences
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

    // ------------------------------------------------------- the "real" flag

    /** Written by the fake direct-install; this is the "installation". */
    var installed: Boolean
        get() = prefs.getBoolean(KEY_INSTALLED, false)
        set(value) = prefs.edit().putBoolean(KEY_INSTALLED, value).apply()

    var installedAt: Long
        get() = prefs.getLong(KEY_INSTALLED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_INSTALLED_AT, value).apply()

    // ------------------------------------------------------- display state

    var stateOverride: FakeInstallState
        get() = FakeInstallState.fromName(prefs.getString(KEY_STATE_OVERRIDE, null))
        set(value) = prefs.edit().putString(KEY_STATE_OVERRIDE, value.name).apply()

    var rootAvailable: Boolean
        get() = prefs.getBoolean(KEY_ROOT_AVAILABLE, true)
        set(value) = prefs.edit().putBoolean(KEY_ROOT_AVAILABLE, value).apply()

    var ksuVersion: Int
        get() = prefs.getInt(KEY_KSU_VERSION, 13000)
        set(value) = prefs.edit().putInt(KEY_KSU_VERSION, value).apply()

    /** null -> show "GKI"; true -> "LKM"; false -> nothing (like upstream). */
    var lkmMode: Boolean?
        get() = when (prefs.getString(KEY_LKM_MODE, "gki")) {
            "lkm" -> true
            "none" -> false
            else -> null
        }
        set(value) = prefs.edit().putString(
            KEY_LKM_MODE,
            when (value) {
                true -> "lkm"
                false -> "none"
                null -> "gki"
            },
        ).apply()

    var kernelUapiVersion: Int
        get() = prefs.getInt(KEY_KERNEL_UAPI, 32513)
        set(value) = prefs.edit().putInt(KEY_KERNEL_UAPI, value).apply()

    // ------------------------------------------- home system info overrides

    /** Empty string -> use the real device value. */
    var deviceModelOverride: String
        get() = prefs.getString(KEY_DEVICE_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_MODEL, value).apply()

    var kernelVersionOverride: String
        get() = prefs.getString(KEY_KERNEL_VERSION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_KERNEL_VERSION, value).apply()

    var fingerprintOverride: String
        get() = prefs.getString(KEY_FINGERPRINT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FINGERPRINT, value).apply()

    var selinuxMode: FakeSelinuxMode
        get() = FakeSelinuxMode.fromName(prefs.getString(KEY_SELINUX_MODE, null))
        set(value) = prefs.edit().putString(KEY_SELINUX_MODE, value.name).apply()

    var seccompMode: FakeSeccompMode
        get() = FakeSeccompMode.fromName(prefs.getString(KEY_SECCOMP_MODE, null))
        set(value) = prefs.edit().putString(KEY_SECCOMP_MODE, value.name).apply()

    // ------------------------------------------------ manager version overrides

    /**
     * Update check compares against this value. `0` = use the real APK
     * versionCode.
     */
    var managerVersionCodeOverride: Long
        get() = prefs.getLong(KEY_MANAGER_VERSION_CODE, 0L)
        set(value) = prefs.edit().putLong(KEY_MANAGER_VERSION_CODE, value).apply()

    /** Version name shown on the home page. Empty = use the real value. */
    var managerVersionNameOverride: String
        get() = prefs.getString(KEY_MANAGER_VERSION_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MANAGER_VERSION_NAME, value).apply()

    // ----------------------------------------------------------- app naming

    /** Launcher label preset, see LauncherAlias enum. */
    var launcherAlias: String
        get() = prefs.getString(KEY_LAUNCHER_ALIAS, LauncherAlias.FAKE.name) ?: LauncherAlias.FAKE.name
        set(value) = prefs.edit().putString(KEY_LAUNCHER_ALIAS, value).apply()

    /** Arbitrary name shown inside the app (top bars, about page). */
    var inAppName: String
        get() = prefs.getString(KEY_IN_APP_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_IN_APP_NAME, value).apply()

    // -------------------------------------------------------------- stealth

    /** When true, the control panel entry disappears from Settings. */
    var stealthMode: Boolean
        get() = prefs.getBoolean(KEY_STEALTH, false)
        set(value) = prefs.edit().putBoolean(KEY_STEALTH, value).apply()

    /** Taps on the About screen title needed to open the hidden panel. */
    var unlockTapCount: Int
        get() = prefs.getInt(KEY_UNLOCK_TAPS, 7).coerceIn(3, 20)
        set(value) = prefs.edit().putInt(KEY_UNLOCK_TAPS, value.coerceIn(3, 20)).apply()

    // --------------------------------------------------------- feature status

    /**
     * Emulated result of `ksud feature check`: `supported` (the default, every
     * toggle is usable), `managed` (taken over by a module → greyed out) or
     * `unsupported`. Only the lab changes this, to demo the greyed-out state.
     */
    var featureStatusOverride: String
        get() = prefs.getString(KEY_FEATURE_STATUS, FEATURE_STATUS_SUPPORTED)
            ?: FEATURE_STATUS_SUPPORTED
        set(value) = prefs.edit().putString(KEY_FEATURE_STATUS, value).apply()

    // -------------------------------------------------------- su simulation

    var suRequestMode: FakeSuRequestMode
        get() = FakeSuRequestMode.entries.firstOrNull {
            it.name == prefs.getString(KEY_SU_MODE, FakeSuRequestMode.PICK_APP.name)
        } ?: FakeSuRequestMode.PICK_APP
        set(value) = prefs.edit().putString(KEY_SU_MODE, value.name).apply()

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
        stateOverride = FakeInstallState.AUTO
    }

    /** 永久卸载: clears flag + all local demo data. */
    fun uninstallPermanent() {
        installed = false
        installedAt = 0L
        stateOverride = FakeInstallState.AUTO
        runCatching { adbRoot.deleteRecursively() }
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        runCatching { adbRoot.deleteRecursively() }
    }

    private const val KEY_INSTALLED = "installed"
    private const val KEY_INSTALLED_AT = "installed_at"
    private const val KEY_STATE_OVERRIDE = "state_override"
    private const val KEY_ROOT_AVAILABLE = "root_available"
    private const val KEY_KSU_VERSION = "ksu_version"
    private const val KEY_LKM_MODE = "lkm_mode"
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
    private const val KEY_STEALTH = "stealth_mode"
    private const val KEY_UNLOCK_TAPS = "unlock_taps"
    private const val KEY_SU_MODE = "su_request_mode"
    private const val KEY_FEATURE_STATUS = "feature_status"

    const val FEATURE_STATUS_SUPPORTED = "supported"
    const val FEATURE_STATUS_MANAGED = "managed"
    const val FEATURE_STATUS_UNSUPPORTED = "unsupported"
}

enum class LauncherAlias(val componentSuffix: String, val labelRes: Int) {
    FAKE(".LauncherAliasFake", me.weishu.kernelsu.R.string.launcher_label_fake),
    KSU(".LauncherAliasKsu", me.weishu.kernelsu.R.string.launcher_label_ksu),
    MANAGER(".LauncherAliasManager", me.weishu.kernelsu.R.string.launcher_label_manager),
    SHORT(".LauncherAliasShort", me.weishu.kernelsu.R.string.launcher_label_short),
    ;

    companion object {
        fun fromName(name: String?): LauncherAlias =
            entries.firstOrNull { it.name == name } ?: FAKE
    }
}
