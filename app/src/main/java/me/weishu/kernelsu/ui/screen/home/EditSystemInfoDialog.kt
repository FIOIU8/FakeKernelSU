package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.fake.FakeSeccompMode
import me.weishu.kernelsu.fake.FakeSelinuxMode
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.miuix.EditText
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * Long-press editor for the home info card (#6).
 *
 * The value is written straight into [FakeState] and the caller refreshes the
 * view model, so the row updates without leaving the page. An empty text (or
 * "follow system" for the two enums) restores the real device value.
 */
@Composable
fun EditSystemInfoDialog(
    field: SystemInfoField?,
    currentValue: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    if (field == null) return

    val title = stringResource(field.labelRes())
    var text by remember(field) { mutableStateOf(currentValue) }

    fun save(raw: String?) {
        applySystemInfoEdit(field, raw)
        onSaved()
        onDismiss()
    }

    val isEnum = field == SystemInfoField.SELINUX || field == SystemInfoField.SECCOMP

    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDialog(
            show = true,
            title = title,
            summary = stringResource(R.string.home_edit_summary),
            onDismissRequest = onDismiss,
            content = {
                if (isEnum) {
                    EnumOptions(field) { save(it) }
                } else {
                    Column {
                        EditText(
                            title = title,
                            value = text,
                            onValueChange = { text = it },
                        )
                        Text(
                            text = stringResource(R.string.home_edit_hint),
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MiuixTextButton(
                                text = stringResource(R.string.fake_edit_reset),
                                onClick = { save(null) },
                            )
                            Row {
                                MiuixTextButton(
                                    text = stringResource(android.R.string.cancel),
                                    onClick = onDismiss,
                                )
                                Spacer(Modifier.width(12.dp))
                                MiuixTextButton(
                                    text = stringResource(R.string.fake_edit_save),
                                    onClick = { save(text) },
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                            }
                        }
                    }
                }
            },
        )

        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                if (isEnum) {
                    EnumOptions(field) { save(it) }
                } else {
                    Column {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (field == SystemInfoField.MANAGER_VERSION) {
                                    KeyboardType.Number
                                } else {
                                    KeyboardType.Text
                                },
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.home_edit_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                if (!isEnum) {
                    TextButton(onClick = { save(text) }) {
                        Text(stringResource(R.string.fake_edit_save))
                    }
                }
            },
            dismissButton = {
                Row {
                    if (!isEnum) {
                        TextButton(onClick = { save(null) }) {
                            Text(stringResource(R.string.fake_edit_reset))
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            },
        )
    }
}

/** "Follow system" + every enum value; picking one saves immediately. */
@Composable
private fun EnumOptions(field: SystemInfoField, onPick: (String?) -> Unit) {
    val options: List<Pair<String, String?>> = when (field) {
        SystemInfoField.SELINUX -> FakeSelinuxMode.entries.map {
            stringResource(it.labelRes()) to it.name
        }

        SystemInfoField.SECCOMP -> FakeSeccompMode.entries.map {
            stringResource(it.labelRes()) to it.name
        }

        else -> emptyList()
    }
    Column {
        options.forEach { (label, value) ->
            Text(
                text = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(value) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun SystemInfoField.labelRes(): Int = when (this) {
    SystemInfoField.MANAGER_VERSION -> R.string.home_manager_version
    SystemInfoField.KERNEL -> R.string.home_kernel
    SystemInfoField.DEVICE_MODEL -> R.string.home_device_model
    SystemInfoField.FINGERPRINT -> R.string.home_fingerprint
    SystemInfoField.SELINUX -> R.string.home_selinux_status
    SystemInfoField.SECCOMP -> R.string.home_seccomp_status
}

private fun FakeSelinuxMode.labelRes(): Int = when (this) {
    FakeSelinuxMode.SYSTEM -> R.string.fake_follow_system
    FakeSelinuxMode.ENFORCING -> R.string.selinux_status_enforcing
    FakeSelinuxMode.PERMISSIVE -> R.string.selinux_status_permissive
    FakeSelinuxMode.DISABLED -> R.string.selinux_status_disabled
}

private fun FakeSeccompMode.labelRes(): Int = when (this) {
    FakeSeccompMode.SYSTEM -> R.string.fake_follow_system
    FakeSeccompMode.NOT_SUPPORTED -> R.string.seccomp_status_not_supported
    FakeSeccompMode.DISABLED -> R.string.seccomp_status_disabled
    FakeSeccompMode.STRICT -> R.string.seccomp_status_strict
    FakeSeccompMode.FILTER -> R.string.seccomp_status_filter
}

/**
 * Writes the edit into [FakeState]. `null` (or an empty string for text fields)
 * restores the real device value.
 */
fun applySystemInfoEdit(field: SystemInfoField, value: String?) {
    when (field) {
        SystemInfoField.MANAGER_VERSION ->
            FakeState.managerVersionCodeOverride = value?.trim()?.toLongOrNull() ?: 0L

        SystemInfoField.KERNEL -> FakeState.kernelVersionOverride = value.orEmpty()

        SystemInfoField.DEVICE_MODEL -> FakeState.deviceModelOverride = value.orEmpty()

        SystemInfoField.FINGERPRINT -> FakeState.fingerprintOverride = value.orEmpty()

        SystemInfoField.SELINUX ->
            FakeState.selinuxMode = value?.let { FakeSelinuxMode.fromName(it) } ?: FakeSelinuxMode.SYSTEM

        SystemInfoField.SECCOMP ->
            FakeState.seccompMode = value?.let { FakeSeccompMode.fromName(it) } ?: FakeSeccompMode.SYSTEM
    }
}
