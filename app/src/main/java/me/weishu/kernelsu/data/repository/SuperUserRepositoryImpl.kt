package me.weishu.kernelsu.data.repository

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.data.model.WEBVIEW_ZYGOTE_PROFILE_KEY
import me.weishu.kernelsu.data.model.WEBVIEW_ZYGOTE_UID
import me.weishu.kernelsu.fake.repository.AppCatalog
import me.weishu.kernelsu.ksuApp

/**
 * FakeKernelSU: upstream enumerates packages through a root AIDL service
 * (KsuService) to see every process on the device. The fake manager just asks
 * the PackageManager — the list of user-visible apps is what a demo needs.
 */
class SuperUserRepositoryImpl : SuperUserRepository {

    override suspend fun getAppList(): Result<Pair<List<AppInfo>, List<Int>>> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = ksuApp.packageManager
            // Same source as the su-request picker, so the picker can only ever
            // offer a superset of what this list shows (#8).
            val installed = AppCatalog.packages()

            val newApps = installed.asSequence()
                .filter {
                    val ai = it.applicationInfo ?: return@filter false
                    ai.uid != WEBVIEW_ZYGOTE_UID &&
                            (ai.flags and ApplicationInfo.FLAG_HAS_CODE) != 0
                }
                .map {
                    val appInfo = it.applicationInfo!!
                    val profile = Natives.getAppProfile(it.packageName, appInfo.uid)
                    AppInfo(
                        label = appInfo.loadLabel(pm).toString(),
                        packageInfo = it,
                        profile = profile,
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toMutableList()

            // Upstream reads the user id list from the root AIDL service. The
            // fake manager can only enumerate the current user's packages, so
            // the list is derived from the uids it actually saw instead of a
            // hardcoded `0` (a wrong list makes the multi-user filters and the
            // owner labels lie on work-profile devices).
            val userIds = newApps.asSequence()
                .mapNotNull { it.packageInfo.applicationInfo?.uid }
                .map { it / 100000 }
                .distinct()
                .sorted()
                .toList()
                .ifEmpty { listOf(0) }

            // WebView Zygote is a single system UID, not a per-user package. Reuse the system icon.
            val systemInfo = ApplicationInfo(pm.getApplicationInfo("android", 0)).apply {
                uid = WEBVIEW_ZYGOTE_UID
            }
            val placeholder = PackageInfo().apply {
                packageName = ""
                applicationInfo = systemInfo
            }
            newApps += AppInfo(
                label = "WebView Zygote",
                packageInfo = placeholder,
                profile = Natives.getAppProfile(WEBVIEW_ZYGOTE_PROFILE_KEY, WEBVIEW_ZYGOTE_UID),
                profileKey = WEBVIEW_ZYGOTE_PROFILE_KEY,
                special = true,
            )

            Pair(newApps, userIds)
        }
    }

    override suspend fun refreshProfiles(currentApps: List<AppInfo>): Result<List<AppInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            if (currentApps.isEmpty()) return@runCatching emptyList()

            currentApps.map {
                val profile = Natives.getAppProfile(it.profileKey, it.uid)
                it.copy(profile = profile)
            }
        }
    }
}
