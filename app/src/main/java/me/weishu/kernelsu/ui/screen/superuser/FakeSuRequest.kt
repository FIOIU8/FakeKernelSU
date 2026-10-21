package me.weishu.kernelsu.ui.screen.superuser

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.data.repository.AppCatalog
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.fake.FakeSu
import me.weishu.kernelsu.fake.FakeSuRequestMode
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.util.fakeStr
import kotlin.random.Random

/**
 * Drives the "simulate an su request" flow.
 *
 * The trigger lives in the superuser page's overflow menu (#9), so the flow and
 * its dialogs are no longer rendered as the first list row: the page owns a
 * [FakeSuRequestController] through [rememberFakeSuRequestController], calls
 * [trigger] from the menu and renders [Dialogs] at the scaffold root.
 */
class FakeSuRequestController internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    /** Re-bound on every recomposition so the callback never goes stale. */
    internal var uiStateProvider: () -> SuperUserUiState = { SuperUserUiState() }
    internal var onRefresh: () -> Unit = {}

    /** Branded version of `fake_su_not_installed` (see [me.weishu.kernelsu.ui.util.fakeStr]). */
    internal var notInstalledMessage: String = ""

    private var allApps by mutableStateOf<List<AppInfo>>(emptyList())
    private var appsLoaded by mutableStateOf(false)
    private var showPicker by mutableStateOf(false)
    private var pending by mutableStateOf<AppInfo?>(null)
    private var rememberGrant by mutableStateOf(true)

    /** Launchable apps, falling back to whatever the superuser page already has. */
    private val pool: List<AppInfo>
        get() = allApps.ifEmpty {
            uiStateProvider().groupedApps.flatMap { it.apps }.distinctBy { it.packageName }
        }

    internal suspend fun loadApps() {
        if (appsLoaded) return
        allApps = AppCatalog.launchable()
        appsLoaded = true
    }

    private fun toast(resId: Int, vararg args: Any) {
        Toast.makeText(context, context.getString(resId, *args), Toast.LENGTH_SHORT).show()
    }

    /** Same behavior as the old first-row click. */
    fun trigger() {
        val apps = pool
        if (!Natives.isManager) {
            Toast.makeText(
                context,
                notInstalledMessage.ifEmpty { context.getString(R.string.fake_su_not_installed) },
                Toast.LENGTH_SHORT,
            ).show()
        } else if (apps.isEmpty()) {
            toast(R.string.fake_su_no_apps)
        } else {
            when (FakeState.suRequestMode) {
                FakeSuRequestMode.PICK_APP -> {
                    rememberGrant = true
                    showPicker = true
                }

                FakeSuRequestMode.RANDOM_APP -> {
                    rememberGrant = true
                    pending = apps[Random.nextInt(apps.size)]
                }
            }
        }
    }

    private fun decide(app: AppInfo, granted: Boolean) {
        pending = null
        FakeSu.recordRequest(
            packageName = app.packageName,
            appName = app.label,
            uid = app.uid,
            granted = granted,
            remember = rememberGrant,
        )
        toast(
            if (granted) R.string.fake_su_granted_toast else R.string.fake_su_denied_toast,
            app.label,
        )
        scope.launch { onRefresh() }
    }

    @Composable
    fun Dialogs() {
        val apps = pool
        if (showPicker) {
            when (LocalUiMode.current) {
                UiMode.Miuix -> FakeAppPickerDialogMiuix(
                    pool = apps,
                    onPick = { app ->
                        showPicker = false
                        pending = app
                    },
                    onDismiss = { showPicker = false },
                )

                UiMode.Material -> FakeAppPickerDialog(
                    pool = apps,
                    onPick = { app ->
                        showPicker = false
                        pending = app
                    },
                    onDismiss = { showPicker = false },
                )
            }
        }

        pending?.let { app ->
            when (LocalUiMode.current) {
                UiMode.Miuix -> FakeGrantDialogMiuix(
                    app = app,
                    rememberGrant = rememberGrant,
                    onRememberChange = { rememberGrant = it },
                    onAllow = { decide(app, granted = true) },
                    onDeny = { decide(app, granted = false) },
                    onDismiss = { pending = null },
                )

                UiMode.Material -> FakeGrantDialog(
                    app = app,
                    rememberGrant = rememberGrant,
                    onRememberChange = { rememberGrant = it },
                    onAllow = { decide(app, granted = true) },
                    onDeny = { decide(app, granted = false) },
                    onDismiss = { pending = null },
                )
            }
        }
    }
}

@Composable
fun rememberFakeSuRequestController(
    uiState: SuperUserUiState,
    onRefresh: () -> Unit,
): FakeSuRequestController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context, scope) { FakeSuRequestController(context, scope) }
    val latestState = rememberUpdatedState(uiState)
    val latestRefresh = rememberUpdatedState(onRefresh)
    val notInstalledMessage = fakeStr(R.string.fake_su_not_installed)
    SideEffect {
        controller.uiStateProvider = { latestState.value }
        controller.onRefresh = { latestRefresh.value() }
        controller.notInstalledMessage = notInstalledMessage
    }
    LaunchedEffect(controller) { controller.loadApps() }
    return controller
}

@Composable
private fun FakeAppPickerDialog(
    pool: List<AppInfo>,
    onPick: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fake_su_pick_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(pool, key = { it.packageName }) { app ->
                    ListItem(
                        modifier = Modifier.clickable { onPick(app) },
                        content = {
                            Text(app.label, overflow = TextOverflow.Ellipsis, maxLines = 1)
                        },
                        supportingContent = {
                            Text(
                                app.packageName,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                        },
                        leadingContent = {
                            AppIconImage(
                                packageInfo = app.packageInfo,
                                label = app.label,
                                modifier = Modifier.size(40.dp),
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun FakeGrantDialog(
    app: AppInfo,
    rememberGrant: Boolean,
    onRememberChange: (Boolean) -> Unit,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            AppIconImage(
                packageInfo = app.packageInfo,
                label = app.label,
                modifier = Modifier.size(48.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.fake_su_request_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.fake_su_request_message, app.label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = rememberGrant,
                        onCheckedChange = onRememberChange,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.fake_su_remember))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.fake_su_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text(stringResource(R.string.fake_su_deny))
            }
        },
    )
}
