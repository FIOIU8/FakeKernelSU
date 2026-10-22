package me.weishu.kernelsu.fake.repository

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.ksuApp

/**
 * Single package source for every screen that lists apps.
 *
 * Upstream the superuser page enumerates processes through a root AIDL service
 * while other pickers use the PackageManager, so the two never line up. The
 * fake manager has no root channel, so both entries go through here instead —
 * that way the picker can only ever show a superset of what the superuser page
 * can show (#8).
 *
 * Note: "has a launcher icon" belongs to [launchable] only. Filtering on it in
 * [packages] would drop code-bearing apps without a launcher (widgets, background
 * services, some system packages) from the superuser list.
 */
object AppCatalog {

    /** Every installed package, one entry per package name, sorted by label. */
    suspend fun packages(): List<PackageInfo> = withContext(Dispatchers.IO) {
        val pm = ksuApp.packageManager
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .asSequence()
            .filter { it.applicationInfo != null }
            .distinctBy { it.packageName }
            .sortedBy { it.applicationInfo!!.loadLabel(pm).toString().lowercase() }
            .toList()
    }

    /** Packets the user can actually launch — what the su-request picker offers. */
    suspend fun launchable(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = ksuApp.packageManager
        packages()
            .asSequence()
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map {
                AppInfo(
                    label = it.applicationInfo!!.loadLabel(pm).toString(),
                    packageInfo = it,
                    profile = null,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
