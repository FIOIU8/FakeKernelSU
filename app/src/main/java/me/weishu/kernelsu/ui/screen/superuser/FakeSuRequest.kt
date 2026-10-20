package me.weishu.kernelsu.ui.screen.superuser

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.fake.FakeSu
import me.weishu.kernelsu.fake.FakeSuRequestMode
import me.weishu.kernelsu.ui.component.AppIconImage
import kotlin.random.Random

/**
 * Shared "simulate an su request" host for both UI flavors.
 *
 * Each SuperUser page supplies its own trigger [row]; this composable owns the
 * whole flow: pick an app (or pick a random one per [FakeState.suRequestMode]),
 * show a high-fidelity grant dialog, then record the result through
 * [FakeSu.recordRequest] so the app profile and the sulog are updated exactly
 * like a real kernel-routed request.
 */
@Composable
fun FakeSuRequestHost(
    uiState: SuperUserUiState,
    onRefresh: () -> Unit,
    row: @Composable (onClick: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var showPicker by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<AppInfo?>(null) }
    var rememberGrant by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                .asSequence()
                .filter { it.applicationInfo != null }
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map {
                    AppInfo(
                        label = it.applicationInfo!!.loadLabel(pm).toString(),
                        packageInfo = it,
                        profile = null,
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
                .toList()
        }
    }

    val pool: List<AppInfo> = allApps.ifEmpty {
        uiState.groupedApps.flatMap { it.apps }.distinctBy { it.packageName }
    }

    fun toast(resId: Int, vararg args: Any) {
        Toast.makeText(context, context.getString(resId, *args), Toast.LENGTH_SHORT).show()
    }

    val trigger: () -> Unit = {
        if (!Natives.isManager) {
            toast(R.string.fake_su_not_installed)
        } else if (pool.isEmpty()) {
            toast(R.string.fake_su_no_apps)
        } else {
            when (FakeState.suRequestMode) {
                FakeSuRequestMode.PICK_APP -> {
                    rememberGrant = true
                    showPicker = true
                }

                FakeSuRequestMode.RANDOM_APP -> {
                    rememberGrant = true
                    pending = pool[Random.nextInt(pool.size)]
                }
            }
        }
    }

    fun decide(app: AppInfo, granted: Boolean) {
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

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.fake_su_pick_title)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(pool, key = { it.packageName }) { app ->
                        ListItem(
                            modifier = Modifier.clickable {
                                showPicker = false
                                pending = app
                            },
                            headlineContent = {
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
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    pending?.let { app ->
        AlertDialog(
            onDismissRequest = { pending = null },
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
                            onCheckedChange = { rememberGrant = it },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.fake_su_remember))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { decide(app, granted = true) }) {
                    Text(stringResource(R.string.fake_su_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { decide(app, granted = false) }) {
                    Text(stringResource(R.string.fake_su_deny))
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        row(trigger)
    }
}
