package me.weishu.kernelsu.ui.screen.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.dialog.rememberLoadingDialog
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.fakepanel.EditSystemInfoDialog
import me.weishu.kernelsu.ui.viewmodel.HomeViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun HomePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val loadingDialog = rememberLoadingDialog()
    val scope = rememberCoroutineScope()
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<SystemInfoField?>(null) }

    var hasActivated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage && !hasActivated) {
            hasActivated = true
            viewModel.refresh()
        }
    }

    LifecycleResumeEffect(Unit) {
        if (initialResumeHandled.value && latestIsCurrentPage) {
            viewModel.refresh()
        }
        initialResumeHandled.value = true
        onPauseOrDispose { }
    }

    val jailbreakConfirm = rememberConfirmDialog(
        onConfirm = {
            FakeState.applyJailbreak()
            viewModel.refresh()
            Toast.makeText(context, R.string.fake_jailbreak_enabled, Toast.LENGTH_SHORT).show()
        },
    )
    val jailbreakTitle = stringResource(R.string.jailbreak_mode)
    val jailbreakMessage = stringResource(R.string.fake_jailbreak_confirm)

    val actions = HomeActions(
        onInstallClick = { navigator.push(Route.Install) },
        onOpenUrl = uriHandler::openUri,
        onJailbreakClick = {
            // Simulated late-load: enter jailbreak mode instead of pretending
            // the action is unsupported.
            jailbreakConfirm.showConfirm(title = jailbreakTitle, content = jailbreakMessage)
        },
        // Stealth-friendly entry to the control panel: the bottom bar is hidden
        // until the fake install happens, so without this the lab would be
        // unreachable on a fresh install.
        onUnlockPanel = {
            if (navigator.current() !is Route.FakePanel) navigator.push(Route.FakePanel)
        },
        onEditField = { editingField = it },
    )

    val editField = editingField
    if (editField != null) {
        EditSystemInfoDialog(
            field = editField,
            currentValue = when (editField) {
                SystemInfoField.MANAGER_VERSION -> uiState.currentManagerVersionCode.toString()
                SystemInfoField.KERNEL -> uiState.systemInfo.kernelVersion
                SystemInfoField.DEVICE_MODEL -> uiState.systemInfo.deviceModel
                SystemInfoField.FINGERPRINT -> uiState.systemInfo.fingerprint
                SystemInfoField.SELINUX -> uiState.systemInfo.selinuxStatus
                SystemInfoField.SECCOMP -> uiState.systemInfo.seccompStatus.toString()
            },
            onDismiss = { editingField = null },
            onSaved = { viewModel.refresh() },
        )
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> HomePagerMiuix(
            state = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> HomePagerMaterial(
            state = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}
