package me.weishu.kernelsu.ui.util

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.fake.LauncherAlias
import me.weishu.kernelsu.ksuApp

/** The name shown inside the app (top bars, about page). */
@Composable
fun fakeAppName(): String {
    val override = FakeState.inAppName
    return if (override.isBlank()) stringResource(R.string.app_name) else override
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
