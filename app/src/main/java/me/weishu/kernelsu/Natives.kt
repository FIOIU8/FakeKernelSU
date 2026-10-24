package me.weishu.kernelsu

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import me.weishu.kernelsu.fake.FakeFeatures
import me.weishu.kernelsu.fake.FakeInstallState
import me.weishu.kernelsu.fake.FakeProfiles
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.Natives.Profile.RootProfileFlag
import me.weishu.kernelsu.ui.util.rootAvailable

/**
 * Fake stand-in for the upstream JNI bridge (libkernelsu.so).
 *
 * Upstream, every value here comes from an ioctl() on the kernel-side
 * [ksu_driver] descriptor. In FakeKernelSU nothing touches the kernel:
 * everything is served from [FakeState] / [FakeProfiles] in app-private
 * storage. The API surface is intentionally identical so the rest of the
 * app compiles and behaves unchanged.
 *
 * @author weishu
 * @date 2022/12/8.
 */
object Natives {
    // minimal supported kernel version
    // 10915: allowlist breaking change, add app profile
    // 10931: app profile struct add 'version' field
    // 10946: add capabilities
    // 10977: change groups_count and groups to avoid overflow write
    // 11071: Fix the issue of failing to set a custom SELinux type.
    // 12143: breaking: new supercall impl
    // 32310: new get_allow_list ioctl
    // 32336: new set_sepolicy ioctl
    // 32377: add set_init_pgrp ioctl
    // 32513: add uapi version
    const val MINIMAL_SUPPORTED_KERNEL = 32513

    const val KERNEL_SU_DOMAIN = "u:r:ksu:s0"

    const val ROOT_UID = 0
    const val ROOT_GID = 0

    /** Android encodes a uid as `userId * PER_USER_RANGE + appId`. */
    private const val PER_USER_RANGE = 100000

    /** App ids below this one are system apps, not installed packages. */
    private const val FIRST_APPLICATION_UID = 10000

    /** Effective display state, honoring the control-panel override. */
    val effectiveState: FakeInstallState
        get() = when (FakeState.stateOverride) {
            FakeInstallState.AUTO ->
                if (FakeState.installed) FakeInstallState.INSTALLED else FakeInstallState.NOT_INSTALLED
            else -> FakeState.stateOverride
        }

    /** "Is KernelSU installed" — the flag the fake direct-install writes. */
    val isManager: Boolean
        get() = effectiveState == FakeInstallState.INSTALLED

    val version: Int
        get() = if (isManager) FakeState.ksuVersion else 0

    val isSafeMode: Boolean
        get() = false

    val isLkmMode: Boolean
        get() = isManager && FakeState.lkmMode == true

    val isLkmBundled: Boolean
        get() = true

    /** Late-load / jailbreak: only meaningful when the manager is "installed". */
    val isLateLoadMode: Boolean
        get() = isManager && FakeState.jailbreakMode

    val isPrBuild: Boolean
        get() = false

    fun uidShouldUmount(uid: Int): Boolean {
        // Mirrors ksu_uid_should_umount() in kernel/policy/allowlist.c:
        //  - never unmount for the manager itself;
        //  - a granted app (allowSu) keeps its modules;
        //  - otherwise the profile's non-root config applies, falling back to
        //    the default non-root profile.
        // Answering "just the default" for every uid made the per-app
        // "umount modules" state in the superuser list meaningless.
        if (uid == ksuApp.applicationInfo.uid) return false
        // The kernel keys profiles by uid; here they are keyed by package
        // name, so several packages of one shared uid can disagree.
        val profiles = FakeProfiles.all().values.filter { it.currentUid == uid }
        // Prefer a profile that carries an explicit non-root config; a plain
        // "use default" one looks the same as no profile at all.
        val profile = profiles.firstOrNull { !it.nonRootUseDefault } ?: profiles.firstOrNull()
        return when {
            profile == null -> isDefaultUmountModules()
            profiles.any { it.allowSu } -> false
            profile.nonRootUseDefault -> isDefaultUmountModules()
            else -> profile.umountModules
        }
    }

    /**
     * Get the profile of the given package.
     * @param key usually the package name
     * @return return null if failed.
     */
    fun getAppProfile(key: String?, uid: Int): Profile {
        if (key == null) return Profile()
        return FakeProfiles.get(key, uid)
    }

    fun setAppProfile(profile: Profile?): Boolean {
        if (profile == null) return false
        FakeProfiles.set(profile)
        return true
    }

    /**
     * `su` compat mode can be disabled temporarily.
     *  0: disabled
     *  1: enabled
     *  negative : error
     */
    fun isSuEnabled(): Boolean {
        return FakeState.suEnabled
    }

    fun setSuEnabled(enabled: Boolean): Boolean {
        FakeState.suEnabled = enabled
        return true
    }

    /**
     * Kernel module umount can be disabled temporarily.
     *  0: disabled
     *  1: enabled
     *  negative : error
     *
     * FakeKernelSU: backed by its own `kernel_umount` feature entry, so it no
     * longer shares storage with [isDefaultUmountModules].
     */
    fun isKernelUmountEnabled(): Boolean {
        return FakeFeatures.isEnabled(FEATURE_KERNEL_UMOUNT)
    }

    fun setKernelUmountEnabled(enabled: Boolean): Boolean {
        return FakeFeatures.setEnabled(FEATURE_KERNEL_UMOUNT, enabled)
    }

    /**
     * SELinux hide can be disabled temporarily.
     *  0: disabled
     *  1: enabled
     *  negative : error
     */
    fun isSelinuxHideEnabled(): Boolean {
        return FakeFeatures.isEnabled(FEATURE_SELINUX_HIDE)
    }

    /** Returns 0 on success, like the upstream ioctl. */
    fun setSelinuxHideEnabled(enabled: Boolean): Int {
        return if (FakeFeatures.setEnabled(FEATURE_SELINUX_HIDE, enabled)) 0 else -1
    }

    /**
     * The Android user name of [uid], like upstream's `getpwuid()` wrapper.
     *
     * Android encodes a uid as `userId * 100000 + appId`, and the app id
     * itself is `10000 + appIndex` for a normal app. Upstream asks the
     * platform passwd table, which answers "root" for 0, "system" for 1000
     * and "shell" for 2000; the demo only needs the same names for the three
     * fixed uids and the `u<user>_a<index>` shape for apps.
     */
    fun getUserName(uid: Int): String? {
        if (uid < 0) return null
        when (uid) {
            ROOT_UID -> return "root"
            1000 -> return "system"
            2000 -> return "shell"
        }
        val userId = uid / PER_USER_RANGE
        val appId = uid % PER_USER_RANGE
        return if (appId < FIRST_APPLICATION_UID) {
            "u${userId}_system${appId}"
        } else {
            "u${userId}_a${appId - FIRST_APPLICATION_UID}"
        }
    }

    fun getSuperuserCount(): Int {
        if (!isManager) return 0
        return FakeProfiles.grantedCount()
    }

    private const val NON_ROOT_DEFAULT_PROFILE_KEY = "$"
    private const val NOBODY_UID = 9999

    private const val FEATURE_KERNEL_UMOUNT = "kernel_umount"
    private const val FEATURE_SELINUX_HIDE = "selinux_hide"

    fun setDefaultUmountModules(umountModules: Boolean): Boolean {
        Profile(
            NON_ROOT_DEFAULT_PROFILE_KEY,
            NOBODY_UID,
            false,
            umountModules = umountModules
        ).let {
            return setAppProfile(it)
        }
    }

    fun isDefaultUmountModules(): Boolean {
        getAppProfile(NON_ROOT_DEFAULT_PROFILE_KEY, NOBODY_UID).let {
            return it.umountModules
        }
    }

    val kernelUAPIVersion: Int
        get() = if (isManager) FakeState.kernelUapiVersion else 0

    val managerUAPIVersion: Int
        get() = MINIMAL_SUPPORTED_KERNEL

    fun isFullFeatured(): Boolean {
        return isManager && kernelUAPIVersion == managerUAPIVersion && rootAvailable()
    }

    @Keep
    @Immutable
    @Parcelize
    @Serializable
    data class Profile(
        // and there is a default profile for root and non-root
        val name: String,
        // current uid for the package, this is convinent for kernel to check
        // if the package name doesn't match uid, then it should be invalidated.
        val currentUid: Int = 0,

        // if this is true, kernel will grant root permission to this package
        val allowSu: Boolean = false,

        // these are used for root profile
        val rootUseDefault: Boolean = true,
        val rootTemplate: String? = null,
        val uid: Int = ROOT_UID,
        val gid: Int = ROOT_GID,
        val groups: List<Int> = mutableListOf(),
        val capabilities: List<Int> = mutableListOf(),
        val context: String = KERNEL_SU_DOMAIN,
        val namespace: Int = Namespace.INHERITED.ordinal,

        val nonRootUseDefault: Boolean = true,
        val umountModules: Boolean = true,
        // The sepolicy rules of this profile. Upstream stores them in ksud;
        // the demo keeps them in the local profile document.
        var rules: String = "",
        val flags: Long = FLAG_KSU_NO_NEW_PRIVS,
    ) : Parcelable {
        @Keep
        enum class RootProfileFlag(val display: String, val desc: Int) {
            NO_NEW_PRIVS(
                "NO_NEW_PRIVS",
                R.string.profile_flags_desc_no_new_privs
            )
        }

        enum class Namespace {
            INHERITED,
            GLOBAL,
            INDIVIDUAL,
        }

        constructor() : this("")
    }

    const val FLAG_KSU_NO_NEW_PRIVS = 1L
}

fun List<RootProfileFlag>.toRawFlags(): Long =
    fold(0L) { acc, flag -> acc.or(1L.shl(flag.ordinal)) }

fun List<RootProfileFlag>.toOrdinalList(): List<Int> =
    map { it.ordinal }

fun Long.toRootProfileFlags(): List<RootProfileFlag> =
    RootProfileFlag.entries.filter { 1L.shl(it.ordinal).and(this) != 0L }
