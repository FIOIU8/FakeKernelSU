package me.weishu.kernelsu.ui.screen.home

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
import me.weishu.kernelsu.fake.FakeState

@Immutable
data class ManagerVersion(
    val versionName: String,
    val versionCode: Long
)

@Immutable
data class SystemInfo(
    val kernelVersion: String,
    val managerVersion: String,
    val deviceModel: String,
    val fingerprint: String,
    val selinuxStatus: String,
    val seccompStatus: Int
)

/** The value [FakeState.ksuVersion] ships with, i.e. "never touched by the user". */
const val DEFAULT_KSU_VERSION = FakeState.DEFAULT_KSU_VERSION

/**
 * Manager version used for display and for the update check.
 *
 * Upstream this is simply the APK's versionCode/versionName. The lab can
 * override both halves independently so a user-defined version actually wins
 * over `tiann/KernelSU`'s release (which is what made the update card show up
 * no matter what was typed in).
 */
fun getManagerVersion(context: Context): ManagerVersion {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)!!
    val realVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    val realVersionName = packageInfo.versionName.orEmpty()

    val codeOverride = FakeState.managerVersionCodeOverride
    val nameOverride = FakeState.managerVersionNameOverride

    // Compatibility with the pre-#2 lab: users who only ever typed into the old
    // "KSU version" field expect that number to be the one being compared.
    val legacyCode = FakeState.ksuVersion
        .takeIf { codeOverride == 0L && it != DEFAULT_KSU_VERSION }
        ?.toLong()

    return ManagerVersion(
        versionName = nameOverride.ifBlank { realVersionName },
        versionCode = codeOverride.takeIf { it > 0L } ?: legacyCode ?: realVersionCode,
    )
}
