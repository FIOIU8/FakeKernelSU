package me.weishu.kernelsu.ui.screen.fakepanel

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.fake.FakeInstallState
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.fake.FakeSuRequestMode
import me.weishu.kernelsu.fake.LauncherAlias
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.util.applyLauncherAlias

/**
 * FakeKernelSU control panel: tweaks every piece of fake state the home page,
 * superuser page and module page read from. Hidden behind the stealth mode
 * (Settings entry disappears; the About title tap-count unlocks it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeControlPanelScreen() {
    val navigator = LocalNavigator.current

    var stateOverride by remember { mutableStateOf(FakeState.stateOverride) }
    var rootAvailable by remember { mutableStateOf(FakeState.rootAvailable) }
    var ksuVersion by remember { mutableStateOf(FakeState.ksuVersion.toString()) }
    var lkmMode by remember { mutableStateOf(FakeState.lkmMode) }
    var deviceModel by remember { mutableStateOf(FakeState.deviceModelOverride) }
    var kernelVersion by remember { mutableStateOf(FakeState.kernelVersionOverride) }
    var fingerprint by remember { mutableStateOf(FakeState.fingerprintOverride) }
    var launcherAlias by remember { mutableStateOf(LauncherAlias.fromName(FakeState.launcherAlias)) }
    var inAppName by remember { mutableStateOf(FakeState.inAppName) }
    var suRequestMode by remember { mutableStateOf(FakeState.suRequestMode) }
    var stealthMode by remember { mutableStateOf(FakeState.stealthMode) }
    var unlockTaps by remember { mutableIntStateOf(FakeState.unlockTapCount) }
    var showWipeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fake_panel_title)) },
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
                        selected = stateOverride == entry,
                        title = stringResource(entry.labelRes()),
                        summary = stringResource(entry.summaryRes()),
                        onSelect = {
                            stateOverride = entry
                            FakeState.stateOverride = entry
                        },
                    )
                }
            }

            // ------------------------------------------------------ root state
            PanelCard(title = stringResource(R.string.fake_panel_root)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = rootAvailable,
                        onCheckedChange = {
                            rootAvailable = it
                            FakeState.rootAvailable = it
                        },
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
                    value = ksuVersion,
                    onValueChange = { input ->
                        ksuVersion = input.filter { it.isDigit() }.take(6)
                        ksuVersion.toIntOrNull()?.let {
                            FakeState.ksuVersion = it.coerceIn(1, 999999)
                        }
                    },
                    label = { Text(stringResource(R.string.fake_panel_version_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.fake_panel_lkm),
                    style = MaterialTheme.typography.titleSmall,
                )
                LkmModeRow(
                    selected = lkmMode,
                    onSelect = {
                        lkmMode = it
                        FakeState.lkmMode = it
                    },
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
                    value = deviceModel,
                    label = stringResource(R.string.home_device_model),
                    onValueChange = {
                        deviceModel = it
                        FakeState.deviceModelOverride = it
                    },
                )
                PanelTextField(
                    value = kernelVersion,
                    label = stringResource(R.string.home_kernel),
                    onValueChange = {
                        kernelVersion = it
                        FakeState.kernelVersionOverride = it
                    },
                )
                PanelTextField(
                    value = fingerprint,
                    label = stringResource(R.string.home_fingerprint),
                    onValueChange = {
                        fingerprint = it
                        FakeState.fingerprintOverride = it
                    },
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
                        selected = launcherAlias == entry,
                        title = stringResource(entry.labelRes),
                        summary = null,
                        onSelect = {
                            launcherAlias = entry
                            FakeState.launcherAlias = entry.name
                            applyLauncherAlias(entry)
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                PanelTextField(
                    value = inAppName,
                    label = stringResource(R.string.fake_panel_in_app_name),
                    onValueChange = {
                        inAppName = it
                        FakeState.inAppName = it
                    },
                )
            }

            // ------------------------------------------------------ su simulate
            PanelCard(title = stringResource(R.string.fake_panel_su_mode)) {
                FakeSuRequestMode.entries.forEach { entry ->
                    RadioRow(
                        selected = suRequestMode == entry,
                        title = stringResource(entry.labelRes()),
                        summary = null,
                        onSelect = {
                            suRequestMode = entry
                            FakeState.suRequestMode = entry
                        },
                    )
                }
            }

            // ---------------------------------------------------------- stealth
            PanelCard(title = stringResource(R.string.fake_panel_stealth)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = stealthMode,
                        onCheckedChange = {
                            stealthMode = it
                            FakeState.stealthMode = it
                        },
                    )
                    Text(
                        text = stringResource(R.string.fake_panel_stealth_summary, unlockTaps),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.fake_panel_tap_count, unlockTaps),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = unlockTaps.toFloat(),
                    onValueChange = {
                        unlockTaps = it.toInt()
                        FakeState.unlockTapCount = it.toInt()
                    },
                    valueRange = 3f..20f,
                    steps = 16,
                )
            }

            // ------------------------------------------------------- danger zone
            PanelCard(title = stringResource(R.string.fake_panel_danger)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            FakeState.resetAll()
                            applyLauncherAlias(LauncherAlias.FAKE)
                            stateOverride = FakeInstallState.AUTO
                            rootAvailable = true
                            ksuVersion = FakeState.ksuVersion.toString()
                            lkmMode = null
                            deviceModel = ""
                            kernelVersion = ""
                            fingerprint = ""
                            inAppName = ""
                            stealthMode = false
                            unlockTaps = 7
                            launcherAlias = LauncherAlias.FAKE
                            suRequestMode = FakeSuRequestMode.PICK_APP
                        },
                    ) {
                        Text(stringResource(R.string.fake_panel_reset))
                    }
                    Button(
                        onClick = { showWipeDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.fake_panel_wipe))
                    }
                }
            }

            Text(
                text = stringResource(R.string.fake_panel_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text(stringResource(R.string.fake_panel_wipe)) },
            text = { Text(stringResource(R.string.fake_panel_wipe_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        FakeState.uninstallPermanent()
                        showWipeDialog = false
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
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
private fun LkmModeRow(selected: Boolean?, onSelect: (Boolean?) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        RadioRow(
            selected = selected == null,
            title = stringResource(R.string.fake_panel_lkm_gki),
            summary = null,
            onSelect = { onSelect(null) },
        )
        RadioRow(
            selected = selected == true,
            title = stringResource(R.string.fake_panel_lkm_lkm),
            summary = null,
            onSelect = { onSelect(true) },
        )
        RadioRow(
            selected = selected == false,
            title = stringResource(R.string.fake_panel_lkm_none),
            summary = null,
            onSelect = { onSelect(false) },
        )
    }
}

private fun FakeInstallState.labelRes(): Int = when (this) {
    FakeInstallState.AUTO -> R.string.fake_panel_state_auto
    FakeInstallState.INSTALLED -> R.string.fake_panel_state_installed
    FakeInstallState.NOT_INSTALLED -> R.string.fake_panel_state_not_installed
    FakeInstallState.UNSUPPORTED -> R.string.fake_panel_state_unsupported
}

private fun FakeInstallState.summaryRes(): Int = when (this) {
    FakeInstallState.AUTO -> R.string.fake_panel_state_auto_summary
    FakeInstallState.INSTALLED -> R.string.fake_panel_state_installed_summary
    FakeInstallState.NOT_INSTALLED -> R.string.fake_panel_state_not_installed_summary
    FakeInstallState.UNSUPPORTED -> R.string.fake_panel_state_unsupported_summary
}

private fun FakeSuRequestMode.labelRes(): Int = when (this) {
    FakeSuRequestMode.PICK_APP -> R.string.fake_panel_su_pick
    FakeSuRequestMode.RANDOM_APP -> R.string.fake_panel_su_random
}
