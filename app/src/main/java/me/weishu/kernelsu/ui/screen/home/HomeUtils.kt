package me.weishu.kernelsu.ui.screen.home

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
import me.weishu.kernelsu.fake.FakeManagerVersion

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

    // Both halves go through the lab so the disguised name and the code that
    // is compared against `tiann/KernelSU` releases always agree.
    return ManagerVersion(
        versionName = FakeManagerVersion.name(realVersionName),
        versionCode = FakeManagerVersion.code(realVersionCode),
    )
}
