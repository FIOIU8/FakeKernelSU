package me.weishu.kernelsu.ui.screen.fakepanel

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.ui.component.AppIconImage
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Miuix flavour of the "pick an app to send a fake su request" dialog.
 */
@Composable
internal fun FakeAppPickerDialogMiuix(
    pool: List<AppInfo>,
    onPick: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        title = stringResource(R.string.fake_su_pick_title),
        show = true,
        onDismissRequest = onDismiss,
    ) {
        val dismiss = LocalDismissState.current
        Column {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(pool, key = { it.packageName }) { app ->
                    BasicComponent(
                        title = app.label,
                        summary = app.packageName,
                        startAction = {
                            AppIconImage(
                                packageInfo = app.packageInfo,
                                label = app.label,
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .size(40.dp),
                            )
                        },
                        modifier = Modifier.clickable {
                            onPick(app)
                            dismiss?.invoke()
                        },
                    )
                }
            }
            TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = {
                    onDismiss()
                    dismiss?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Miuix flavour of the fake root grant dialog.
 */
@Composable
internal fun FakeGrantDialogMiuix(
    app: AppInfo,
    rememberGrant: Boolean,
    onRememberChange: (Boolean) -> Unit,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        title = stringResource(R.string.fake_su_request_title),
        summary = stringResource(R.string.fake_su_request_message, app.label),
        show = true,
        onDismissRequest = onDismiss,
    ) {
        val dismiss = LocalDismissState.current
        Column {
            AppIconImage(
                packageInfo = app.packageInfo,
                label = app.label,
                modifier = Modifier.size(48.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    state = ToggleableState(rememberGrant),
                    onClick = { onRememberChange(!rememberGrant) },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.fake_su_remember),
                    fontSize = 15.sp,
                    color = colorScheme.onSurface,
                )
            }
            Row(modifier = Modifier.padding(top = 12.dp)) {
                TextButton(
                    text = stringResource(R.string.fake_su_deny),
                    onClick = {
                        onDeny()
                        dismiss?.invoke()
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.fake_su_allow),
                    onClick = {
                        onAllow()
                        dismiss?.invoke()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
