package me.weishu.kernelsu.ui.component.rebootlistpopup

import android.content.Context
import android.os.PowerManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.fake.FakeModules
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.util.reboot

data class RebootListOption(
    @param:StringRes val labelRes: Int,
    val reason: String,
)

@Composable
fun getRebootListOption(): List<RebootListOption> {
    val pm = LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?

    @Suppress("DEPRECATION")
    val isRebootingUserspaceSupported = pm?.isRebootingUserspaceSupported == true

    return buildList {
        add(RebootListOption(R.string.reboot, ""))
        if (isRebootingUserspaceSupported) {
            add(RebootListOption(R.string.reboot_userspace, "userspace"))
        }
        add(RebootListOption(R.string.reboot_soft, "soft_reboot"))
        add(RebootListOption(R.string.reboot_recovery, "recovery"))
        add(RebootListOption(R.string.reboot_bootloader, "bootloader"))
        add(RebootListOption(R.string.reboot_download, "download"))
        add(RebootListOption(R.string.reboot_edl, "edl"))
    }
}

/** Reboots on selection, but confirms first in jailbreak mode where a plain reboot drops root. */
@Composable
fun rememberRebootAction(): (String) -> Unit {
    val context = LocalContext.current
    val title = stringResource(R.string.reboot)
    val message = stringResource(R.string.jailbreak_reboot_warning)
    val rebootedMessage = stringResource(R.string.fake_jailbreak_rebooted)

    // FakeKernelSU: reboot() is a no-op, so simulate "root is gone after reboot"
    // by leaving jailbreak mode and telling the user.
    val doReboot: (String) -> Unit = { reason ->
        val wasJailbreak = FakeState.jailbreakMode
        reboot(reason)
        // Upstream applies the staged module removals on the next boot; the
        // demo has no boot, so the simulated reboot does it.
        FakeModules.purgePendingRemoval()
        if (wasJailbreak) {
            FakeState.clearJailbreak()
            Toast.makeText(context, rebootedMessage, Toast.LENGTH_LONG).show()
        }
    }

    val confirmDialog = rememberConfirmDialog(onConfirm = { doReboot("") })

    return remember(title, message, rebootedMessage, confirmDialog) {
        { reason ->
            if (Natives.isLateLoadMode && reason.isEmpty()) {
                confirmDialog.showConfirm(title = title, content = message)
            } else {
                doReboot(reason)
            }
        }
    }
}

@Composable
fun RebootListPopup() {
    when (LocalUiMode.current) {
        UiMode.Miuix -> RebootListPopupMiuix()
        UiMode.Material -> RebootListPopupMaterial()
    }
}
