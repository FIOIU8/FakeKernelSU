package me.weishu.kernelsu.ui.util

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.fake.LauncherAlias
import me.weishu.kernelsu.ksuApp

/**
 * The name shown inside the app (top bars, about page).
 *
 * Priority: explicit in-app name -> brand name -> app label. Keeping the
 * in-app name first preserves the previous behavior, while a brand-only
 * override (#12) still reaches the title.
 */
@Composable
fun fakeAppName(): String {
    val override = FakeState.inAppName
    if (override.isNotBlank()) return override
    val brand = FakeState.brandName
    if (brand.isNotBlank()) return brand
    return stringResource(R.string.app_name)
}

/**
 * Switches the launcher-visible app name by enabling exactly one
 * activity-alias (declared in AndroidManifest.xml).
 */
fun applyLauncherAlias(alias: LauncherAlias) {
    val pm = ksuApp.packageManager
    LauncherAlias.entries.forEach { entry ->
        val component = ComponentName(
            ksuApp.packageName,
            "me.weishu.kernelsu" + entry.componentSuffix,
        )
        val state = if (entry == alias) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching {
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}

/** Ensures the persisted alias choice matches the manifest state on cold start. */
fun syncLauncherAlias() {
    applyLauncherAlias(LauncherAlias.fromName(FakeState.launcherAlias))
}
