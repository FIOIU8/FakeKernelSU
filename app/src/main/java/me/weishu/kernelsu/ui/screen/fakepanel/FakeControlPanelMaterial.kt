package me.weishu.kernelsu.ui.screen.fakepanel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.fake.FakeInstallState
import me.weishu.kernelsu.fake.FakeSeccompMode
import me.weishu.kernelsu.fake.FakeSelinuxMode
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.fake.FakeSuRequestMode
import me.weishu.kernelsu.fake.LauncherAlias
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.fake.fakeStr

/**
 * Material 3 flavour of the FakeKernelSU lab.
 *
 * Layout (#3): a status overview strip on top, then five collapsible sections.
 * Both skins share [FakePanelState] so the two are feature-identical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeControlPanelMaterial(state: FakePanelState) {
    val navigator = LocalNavigator.current

    // Hiding the entry is reversible (long press the home title), but the user
    // has to know that before the entry disappears, hence the confirm step.
    val hideEntryDialog = rememberConfirmDialog(
        onConfirm = { state.updateStealthMode(true) },
        onDismiss = null,
    )
    val hideEntryTitle = stringResource(R.string.fake_panel_stealth)
    val hideEntryMessage = stringResource(R.string.fake_panel_hide_entry_message)

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
            StatusOverview(state)

            // ------------------------------------------------------ runtime
            SectionCard(
                title = stringResource(R.string.fake_panel_runtime),
                section = FakePanelSection.RUNTIME,
            ) {
                FakeInstallState.entries.forEach { entry ->
                    RadioRow(
                        selected = state.stateOverride == entry,
                        title = stringResource(entry.labelRes()),
                        summary = stringResource(entry.summaryRes()),
                        onSelect = { state.selectInstallState(entry) },
                    )
                }
                SwitchRow(
                    title = stringResource(R.string.fake_panel_root),
                    summary = stringResource(R.string.fake_panel_root_summary),
                    checked = state.rootAvailable,
                    onCheckedChange = { state.updateRootAvailable(it) },
                )
                SwitchRow(
                    title = stringResource(R.string.fake_panel_lkm_update),
                    summary = stringResource(R.string.fake_panel_lkm_update_summary),
                    checked = state.lkmUpdateAvailable,
                    onCheckedChange = { state.updateLkmUpdateAvailable(it) },
                )
                Text(
                    text = stringResource(R.string.fake_panel_su_mode),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column(modifier = Modifier.selectableGroup()) {
                    FakeSuRequestMode.entries.forEach { entry ->
                        RadioRow(
                            selected = state.suRequestMode == entry,
                            title = stringResource(entry.labelRes()),
                            summary = null,
                            onSelect = { state.selectSuRequestMode(entry) },
                        )
                    }
                }
                PanelDropdown(
                    label = stringResource(R.string.fake_panel_feature_status),
                    value = stringResource(featureStatusLabelRes(state.featureStatus)),
                    options = FEATURE_STATUS_VALUES.map { stringResource(featureStatusLabelRes(it)) },
                    onSelect = { state.selectFeatureStatus(FEATURE_STATUS_VALUES[it]) },
                    summary = stringResource(R.string.fake_panel_feature_status_summary),
                )
            }

            // ---------------------------------------------- version and mode
            SectionCard(
                title = stringResource(R.string.fake_panel_version_mode),
                section = FakePanelSection.VERSION,
            ) {
                PanelTextField(
                    value = state.ksuVersion,
                    label = stringResource(R.string.fake_panel_ksu_version),
                    placeholder = stringResource(R.string.fake_panel_ksu_version_summary),
                    numeric = true,
                    onValueChange = { state.updateKsuVersion(it) },
                )
                // The disguise itself: a release-shaped x.y.z instead of the
                // fork's git-describe build name. It drives the home page, the
                // about page and the version handed to module scripts.
                PanelTextField(
                    value = state.managerVersionName,
                    label = stringResource(R.string.fake_panel_manager_version_name),
                    placeholder = stringResource(R.string.fake_panel_manager_version_name_summary),
                    onValueChange = { state.updateManagerVersionName(it) },
                )
                PanelTextField(
                    value = state.managerVersionCode,
                    label = stringResource(R.string.fake_panel_manager_version_code),
                    placeholder = stringResource(R.string.fake_panel_manager_version_code_summary),
                    numeric = true,
                    onValueChange = { state.updateManagerVersionCode(it) },
                )
                state.effectiveManagerVersionCode?.let { compareCode ->
                    Text(
                        text = stringResource(R.string.fake_panel_compare_value, compareCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.fake_panel_mode),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column(modifier = Modifier.selectableGroup()) {
                    FakeWorkingMode.entries.forEach { mode ->
                        RadioRow(
                            selected = state.workingMode == mode,
                            title = stringResource(mode.labelRes()),
                            summary = null,
                            onSelect = { state.selectWorkingMode(mode) },
                        )
                    }
                }
            }

            // ------------------------------------------------ system info
            SectionCard(
                title = stringResource(R.string.fake_panel_system_info),
                section = FakePanelSection.SYSTEM_INFO,
            ) {
                PanelTextField(
                    value = state.deviceModel,
                    label = stringResource(R.string.home_device_model),
                    placeholder = stringResource(R.string.fake_panel_override_hint),
                    onValueChange = { state.updateDeviceModel(it) },
                )
                PanelTextField(
                    value = state.kernelVersion,
                    label = stringResource(R.string.home_kernel),
                    placeholder = stringResource(R.string.fake_panel_override_hint),
                    onValueChange = { state.updateKernelVersion(it) },
                )
                PanelTextField(
                    value = state.fingerprint,
                    label = stringResource(R.string.home_fingerprint),
                    placeholder = stringResource(R.string.fake_panel_override_hint),
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

            // ---------------------------------------------------- naming
            SectionCard(
                title = stringResource(R.string.fake_panel_naming),
                section = FakePanelSection.NAMING,
            ) {
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
                    placeholder = stringResource(R.string.fake_panel_brand_summary),
                    onValueChange = { state.updateInAppName(it) },
                )
                Text(
                    text = stringResource(R.string.fake_panel_launcher_label),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column(modifier = Modifier.selectableGroup()) {
                    LauncherAlias.entries.forEach { entry ->
                        RadioRow(
                            selected = state.launcherAlias == entry,
                            title = stringResource(entry.labelRes),
                            summary = null,
                            onSelect = { state.selectLauncherAlias(entry) },
                        )
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.fake_panel_stealth),
                    summary = stringResource(R.string.fake_panel_stealth_summary),
                    checked = state.stealthMode,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            hideEntryDialog.showConfirm(
                                title = hideEntryTitle,
                                content = hideEntryMessage,
                            )
                        } else {
                            state.updateStealthMode(false)
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.fake_panel_long_press_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ------------------------------------------------- danger zone
            SectionCard(
                title = stringResource(R.string.fake_panel_danger),
                section = FakePanelSection.DANGER,
                danger = true,
            ) {
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
                        FakeState.uninstallPermanent()
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

/** "installed / mode / version" at a glance, no scrolling needed. */
@Composable
private fun StatusOverview(state: FakePanelState) {
    val installed = when (state.stateOverride) {
        FakeInstallState.AUTO -> FakeState.installed
        FakeInstallState.INSTALLED -> true
        else -> false
    }
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusTag(
            label = stringResource(
                if (installed) R.string.fake_panel_state_installed
                else R.string.fake_panel_state_not_installed
            ),
            backgroundColor = if (installed) scheme.secondaryContainer else scheme.errorContainer,
            contentColor = if (installed) scheme.onSecondaryContainer else scheme.onErrorContainer,
        )
        StatusTag(
            label = stringResource(state.workingMode.labelRes()),
            backgroundColor = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
        )
        StatusTag(
            label = if (installed) "v${state.ksuVersion}" else "—",
            backgroundColor = scheme.surfaceVariant,
            contentColor = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    section: FakePanelSection,
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(section.name) { mutableStateOf(section.defaultExpanded) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (danger) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The whole row toggles, so the switch itself no longer handles clicks.
        Switch(checked = checked, onCheckedChange = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        keyboardOptions = KeyboardOptions(
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
    summary: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
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
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // M3 default only makes the icon tappable; the whole row selects here
        // so the label works as a hit target too.
        RadioButton(selected = selected, onClick = null)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
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

/** The three `ksud feature check` results the lab can pretend to be. */
internal val FEATURE_STATUS_VALUES = listOf(
    FakeState.FEATURE_STATUS_SUPPORTED,
    FakeState.FEATURE_STATUS_MANAGED,
    FakeState.FEATURE_STATUS_UNSUPPORTED,
)

internal fun featureStatusLabelRes(value: String): Int = when (value) {
    FakeState.FEATURE_STATUS_MANAGED -> R.string.fake_feature_status_managed
    FakeState.FEATURE_STATUS_UNSUPPORTED -> R.string.fake_feature_status_unsupported
    else -> R.string.fake_feature_status_supported
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
