package me.weishu.kernelsu.ui.screen.fakepanel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.fake.FakeInstallState
import me.weishu.kernelsu.fake.FakeSeccompMode
import me.weishu.kernelsu.fake.FakeSelinuxMode
import me.weishu.kernelsu.fake.FakeSuRequestMode
import me.weishu.kernelsu.fake.LauncherAlias
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.util.fakeStr

/**
 * Material 3 flavour of the FakeKernelSU control panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeControlPanelMaterial(state: FakePanelState) {
    val navigator = LocalNavigator.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fakeStr(R.string.fake_panel_title)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            // ---------------------------------------------------- install state
            PanelCard(title = stringResource(R.string.fake_panel_state)) {
                FakeInstallState.entries.forEach { entry ->
                    RadioRow(
                        selected = state.stateOverride == entry,
                        title = stringResource(entry.labelRes()),
                        summary = stringResource(entry.summaryRes()),
                        onSelect = { state.selectInstallState(entry) },
                    )
                }
            }

            // ------------------------------------------------------ root state
            PanelCard(title = stringResource(R.string.fake_panel_root)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.rootAvailable,
                        onCheckedChange = { state.updateRootAvailable(it) },
                    )
                    Text(
                        text = stringResource(R.string.fake_panel_root_summary),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            // ----------------------------------------------------------- version
            PanelCard(title = stringResource(R.string.fake_panel_version)) {
                OutlinedTextField(
                    value = state.ksuVersion,
                    onValueChange = { state.updateKsuVersion(it) },
                    label = { Text(stringResource(R.string.fake_panel_version_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                PanelTextField(
                    value = state.managerVersionCode,
                    label = stringResource(R.string.fake_panel_manager_version_code),
                    placeholder = stringResource(R.string.fake_panel_manager_version_code_summary),
                    numeric = true,
                    onValueChange = { state.updateManagerVersionCode(it) },
                )
                PanelTextField(
                    value = state.managerVersionName,
                    label = stringResource(R.string.fake_panel_manager_version_name),
                    placeholder = stringResource(R.string.fake_panel_manager_version_name_summary),
                    onValueChange = { state.updateManagerVersionName(it) },
                )
                if (state.managerVersionCode.isNotBlank()) {
                    Text(
                        text = stringResource(
                            R.string.fake_panel_compare_value,
                            state.managerVersionCode.toLongOrNull() ?: 0L,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.fake_panel_lkm),
                    style = MaterialTheme.typography.titleSmall,
                )
                LkmModeRow(
                    selected = state.workingMode,
                    onSelect = { state.selectWorkingMode(it) },
                )
            }

            // -------------------------------------------------- home overrides
            PanelCard(title = stringResource(R.string.fake_panel_home_overrides)) {
                Text(
                    text = stringResource(R.string.fake_panel_override_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                PanelTextField(
                    value = state.deviceModel,
                    label = stringResource(R.string.home_device_model),
                    onValueChange = { state.updateDeviceModel(it) },
                )
                PanelTextField(
                    value = state.kernelVersion,
                    label = stringResource(R.string.home_kernel),
                    onValueChange = { state.updateKernelVersion(it) },
                )
                PanelTextField(
                    value = state.fingerprint,
                    label = stringResource(R.string.home_fingerprint),
                    onValueChange = { state.updateFingerprint(it) },
                )
                PanelDropdown(
                    label = stringResource(R.string.fake_panel_selinux_mode),
                    value = stringResource(state.selinuxMode.labelRes()),
                    options = FakeSelinuxMode.entries.map { stringResource(it.labelRes()) },
                    onSelect = { state.selectSelinuxMode(FakeSelinuxMode.entries[it]) },
                )
                PanelDropdown(
                    label = stringResource(R.string.fake_panel_seccomp_status),
                    value = stringResource(state.seccompMode.labelRes()),
                    options = FakeSeccompMode.entries.map { stringResource(it.labelRes()) },
                    onSelect = { state.selectSeccompMode(FakeSeccompMode.entries[it]) },
                )
            }

            // ------------------------------------------------------- app naming
            PanelCard(title = stringResource(R.string.fake_panel_app_name)) {
                Text(
                    text = stringResource(R.string.fake_panel_launcher_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                LauncherAlias.entries.forEach { entry ->
                    RadioRow(
                        selected = state.launcherAlias == entry,
                        title = stringResource(entry.labelRes),
                        summary = null,
                        onSelect = { state.selectLauncherAlias(entry) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                PanelTextField(
                    value = state.brandName,
                    label = stringResource(R.string.fake_panel_brand),
                    placeholder = stringResource(R.string.fake_panel_brand_summary),
                    onValueChange = { state.updateBrandName(it) },
                )
                Text(
                    text = stringResource(
                        R.string.fake_panel_brand_in_use,
                        state.brandName.ifBlank {
                            state.inAppName.ifBlank { stringResource(R.string.app_name) }
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PanelTextField(
                    value = state.inAppName,
                    label = stringResource(R.string.fake_panel_in_app_name),
                    onValueChange = { state.updateInAppName(it) },
                )
            }

            // ------------------------------------------------------ su simulate
            PanelCard(title = stringResource(R.string.fake_panel_su_mode)) {
                FakeSuRequestMode.entries.forEach { entry ->
                    RadioRow(
                        selected = state.suRequestMode == entry,
                        title = stringResource(entry.labelRes()),
                        summary = null,
                        onSelect = { state.selectSuRequestMode(entry) },
                    )
                }
            }

            // ---------------------------------------------------------- stealth
            PanelCard(title = stringResource(R.string.fake_panel_stealth)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.stealthMode,
                        onCheckedChange = { state.updateStealthMode(it) },
                    )
                    Text(
                        text = stringResource(R.string.fake_panel_stealth_summary, state.unlockTaps),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.fake_panel_tap_count, state.unlockTaps),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // rememberSliderState(value, steps, valueRange)
                val sliderState = rememberSliderState(
                    state.sliderValue,
                    16,
                    3f..20f,
                )
                LaunchedEffect(state.sliderValue) {
                    // Keep the thumb in sync when the value is changed elsewhere
                    // (e.g. "reset simulation state").
                    if (sliderState.value != state.sliderValue) {
                        sliderState.value = state.sliderValue
                    }
                }
                Slider(
                    state = sliderState,
                    onValueChange = { state.setUnlockTaps(it) },
                )
            }

            // ------------------------------------------------------- danger zone
            PanelCard(title = stringResource(R.string.fake_panel_danger)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { state.resetToDefaults() }) {
                        Text(stringResource(R.string.fake_panel_reset))
                    }
                    Button(
                        onClick = { state.showWipeDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.fake_panel_wipe))
                    }
                }
            }

            Text(
                text = fakeStr(R.string.fake_panel_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.showWipeDialog) {
        AlertDialog(
            onDismissRequest = { state.showWipeDialog = false },
            title = { Text(stringResource(R.string.fake_panel_wipe)) },
            text = { Text(stringResource(R.string.fake_panel_wipe_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        me.weishu.kernelsu.fake.FakeState.uninstallPermanent()
                        state.showWipeDialog = false
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { state.showWipeDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PanelCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun PanelTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun PanelDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
        // Transparent overlay so tapping anywhere on the field opens the list.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RadioRow(
    selected: Boolean,
    title: String,
    summary: String?,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LkmModeRow(selected: FakeWorkingMode, onSelect: (FakeWorkingMode) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        FakeWorkingMode.entries.forEach { mode ->
            RadioRow(
                selected = selected == mode,
                title = stringResource(mode.labelRes()),
                summary = null,
                onSelect = { onSelect(mode) },
            )
        }
    }
}

internal fun FakeInstallState.labelRes(): Int = when (this) {
    FakeInstallState.AUTO -> R.string.fake_panel_state_auto
    FakeInstallState.INSTALLED -> R.string.fake_panel_state_installed
    FakeInstallState.NOT_INSTALLED -> R.string.fake_panel_state_not_installed
    FakeInstallState.UNSUPPORTED -> R.string.fake_panel_state_unsupported
}

internal fun FakeInstallState.summaryRes(): Int = when (this) {
    FakeInstallState.AUTO -> R.string.fake_panel_state_auto_summary
    FakeInstallState.INSTALLED -> R.string.fake_panel_state_installed_summary
    FakeInstallState.NOT_INSTALLED -> R.string.fake_panel_state_not_installed_summary
    FakeInstallState.UNSUPPORTED -> R.string.fake_panel_state_unsupported_summary
}

internal fun FakeSuRequestMode.labelRes(): Int = when (this) {
    FakeSuRequestMode.PICK_APP -> R.string.fake_panel_su_pick
    FakeSuRequestMode.RANDOM_APP -> R.string.fake_panel_su_random
}

internal fun FakeSelinuxMode.labelRes(): Int = when (this) {
    FakeSelinuxMode.SYSTEM -> R.string.fake_follow_system
    FakeSelinuxMode.ENFORCING -> R.string.selinux_status_enforcing
    FakeSelinuxMode.PERMISSIVE -> R.string.selinux_status_permissive
    FakeSelinuxMode.DISABLED -> R.string.selinux_status_disabled
}

internal fun FakeSeccompMode.labelRes(): Int = when (this) {
    FakeSeccompMode.SYSTEM -> R.string.fake_follow_system
    FakeSeccompMode.NOT_SUPPORTED -> R.string.seccomp_status_not_supported
    FakeSeccompMode.DISABLED -> R.string.seccomp_status_disabled
    FakeSeccompMode.STRICT -> R.string.seccomp_status_strict
    FakeSeccompMode.FILTER -> R.string.seccomp_status_filter
}

internal fun FakeWorkingMode.labelRes(): Int = when (this) {
    FakeWorkingMode.GKI -> R.string.fake_panel_lkm_gki
    FakeWorkingMode.LKM -> R.string.fake_panel_lkm_lkm
    FakeWorkingMode.NONE -> R.string.fake_panel_lkm_none
    FakeWorkingMode.JAILBREAK -> R.string.jailbreak_mode
}
