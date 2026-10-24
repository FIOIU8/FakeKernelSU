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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.fake.FakeInstallState
import me.weishu.kernelsu.fake.FakeSeccompMode
import me.weishu.kernelsu.fake.FakeSelinuxMode
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.fake.FakeSuRequestMode
import me.weishu.kernelsu.fake.LauncherAlias
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.miuix.EditText
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.fake.fakeStr
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix flavour of the FakeKernelSU lab.
 *
 * Same sections and order as the Material flavour (#3); only the containers
 * differ.
 */
@Composable
fun FakeControlPanelMiuix(state: FakePanelState) {
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            FakeState.uninstallPermanent()
            state.showWipeDialog = false
        },
        onDismiss = {
            state.showWipeDialog = false
        },
    )

    val wipeLabel = stringResource(R.string.fake_panel_wipe)
    val wipeConfirmLabel = stringResource(R.string.fake_panel_wipe_confirm)

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
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = fakeStr(R.string.fake_panel_title),
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    StatusOverview(state)

                    // ------------------------------------------------- runtime
                    SectionCard(
                        title = stringResource(R.string.fake_panel_runtime),
                        section = FakePanelSection.RUNTIME,
                    ) {
                        FakeInstallState.entries.forEach { entry ->
                            RadioButtonPreference(
                                title = stringResource(entry.labelRes()),
                                summary = stringResource(entry.summaryRes()),
                                selected = state.stateOverride == entry,
                                onClick = { state.selectInstallState(entry) },
                            )
                        }
                        SwitchPreference(
                            title = stringResource(R.string.fake_panel_root),
                            summary = stringResource(R.string.fake_panel_root_summary),
                            checked = state.rootAvailable,
                            onCheckedChange = { state.updateRootAvailable(it) },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.fake_panel_lkm_update),
                            summary = stringResource(R.string.fake_panel_lkm_update_summary),
                            checked = state.lkmUpdateAvailable,
                            onCheckedChange = { state.updateLkmUpdateAvailable(it) },
                        )
                        SectionLabel(stringResource(R.string.fake_panel_su_mode))
                        FakeSuRequestMode.entries.forEach { entry ->
                            RadioButtonPreference(
                                title = stringResource(entry.labelRes()),
                                selected = state.suRequestMode == entry,
                                onClick = { state.selectSuRequestMode(entry) },
                            )
                        }
                        OverlayDropdownPreference(
                            title = stringResource(R.string.fake_panel_feature_status),
                            summary = stringResource(featureStatusLabelRes(state.featureStatus)),
                            items = FEATURE_STATUS_VALUES.map { stringResource(featureStatusLabelRes(it)) },
                            selectedIndex = FEATURE_STATUS_VALUES.indexOf(state.featureStatus)
                                .coerceAtLeast(0),
                            onSelectedIndexChange = {
                                state.selectFeatureStatus(FEATURE_STATUS_VALUES[it])
                            },
                        )
                    }

                    // ------------------------------------------ version and mode
                    SectionCard(
                        title = stringResource(R.string.fake_panel_version_mode),
                        section = FakePanelSection.VERSION,
                    ) {
                        EditText(
                            title = stringResource(R.string.fake_panel_ksu_version),
                            value = state.ksuVersion,
                            onValueChange = { state.updateKsuVersion(it) },
                            textHint = "13000",
                        )
                        // The disguise itself: a release-shaped x.y.z instead
                        // of the fork's git-describe build name.
                        EditText(
                            title = stringResource(R.string.fake_panel_manager_version_name),
                            value = state.managerVersionName,
                            onValueChange = { state.updateManagerVersionName(it) },
                        )
                        EditText(
                            title = stringResource(R.string.fake_panel_manager_version_code),
                            value = state.managerVersionCode,
                            onValueChange = { state.updateManagerVersionCode(it) },
                        )
                        state.effectiveManagerVersionCode?.let { compareCode ->
                            HintText(
                                stringResource(
                                    R.string.fake_panel_compare_value,
                                    compareCode,
                                )
                            )
                        }
                        SectionLabel(stringResource(R.string.fake_panel_mode))
                        FakeWorkingMode.entries.forEach { mode ->
                            RadioButtonPreference(
                                title = stringResource(mode.labelRes()),
                                selected = state.workingMode == mode,
                                onClick = { state.selectWorkingMode(mode) },
                            )
                        }
                    }

                    // -------------------------------------------- system info
                    SectionCard(
                        title = stringResource(R.string.fake_panel_system_info),
                        section = FakePanelSection.SYSTEM_INFO,
                    ) {
                        EditText(
                            title = stringResource(R.string.home_device_model),
                            value = state.deviceModel,
                            onValueChange = { state.updateDeviceModel(it) },
                        )
                        EditText(
                            title = stringResource(R.string.home_kernel),
                            value = state.kernelVersion,
                            onValueChange = { state.updateKernelVersion(it) },
                        )
                        EditText(
                            title = stringResource(R.string.home_fingerprint),
                            value = state.fingerprint,
                            onValueChange = { state.updateFingerprint(it) },
                        )
                        OverlayDropdownPreference(
                            title = stringResource(R.string.fake_panel_selinux_mode),
                            summary = stringResource(state.selinuxMode.labelRes()),
                            items = FakeSelinuxMode.entries.map { stringResource(it.labelRes()) },
                            selectedIndex = FakeSelinuxMode.entries.indexOf(state.selinuxMode),
                            onSelectedIndexChange = {
                                state.selectSelinuxMode(FakeSelinuxMode.entries[it])
                            },
                        )
                        OverlayDropdownPreference(
                            title = stringResource(R.string.fake_panel_seccomp_status),
                            summary = stringResource(state.seccompMode.labelRes()),
                            items = FakeSeccompMode.entries.map { stringResource(it.labelRes()) },
                            selectedIndex = FakeSeccompMode.entries.indexOf(state.seccompMode),
                            onSelectedIndexChange = {
                                state.selectSeccompMode(FakeSeccompMode.entries[it])
                            },
                        )
                    }

                    // ------------------------------------------------ naming
                    SectionCard(
                        title = stringResource(R.string.fake_panel_naming),
                        section = FakePanelSection.NAMING,
                    ) {
                        EditText(
                            title = stringResource(R.string.fake_panel_brand),
                            value = state.brandName,
                            onValueChange = { state.updateBrandName(it) },
                        )
                        HintText(
                            stringResource(
                                R.string.fake_panel_brand_in_use,
                                state.brandName.ifBlank {
                                    state.inAppName.ifBlank { stringResource(R.string.app_name) }
                                },
                            )
                        )
                        EditText(
                            title = stringResource(R.string.fake_panel_in_app_name),
                            value = state.inAppName,
                            onValueChange = { state.updateInAppName(it) },
                        )
                        SectionLabel(stringResource(R.string.fake_panel_launcher_label))
                        LauncherAlias.entries.forEach { entry ->
                            RadioButtonPreference(
                                title = stringResource(entry.labelRes),
                                selected = state.launcherAlias == entry,
                                onClick = { state.selectLauncherAlias(entry) },
                            )
                        }
                        SwitchPreference(
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
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
                        )
                    }

                    // --------------------------------------------- danger zone
                    SectionCard(
                        title = stringResource(R.string.fake_panel_danger),
                        section = FakePanelSection.DANGER,
                        danger = true,
                    ) {
                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            text = stringResource(R.string.fake_panel_reset),
                            onClick = { state.resetToDefaults() },
                        )
                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            text = wipeLabel,
                            onClick = {
                                confirmDialog.showConfirm(
                                    title = wipeLabel,
                                    content = wipeConfirmLabel,
                                )
                            },
                        )
                    }

                    Text(
                        text = fakeStr(R.string.fake_panel_disclaimer),
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusTag(
            label = stringResource(
                if (installed) R.string.fake_panel_state_installed
                else R.string.fake_panel_state_not_installed
            ),
            backgroundColor = if (installed) colorScheme.secondaryContainer else colorScheme.errorContainer,
            contentColor = if (installed) colorScheme.onSecondaryContainer else colorScheme.onErrorContainer,
        )
        StatusTag(
            label = stringResource(state.workingMode.labelRes()),
            backgroundColor = colorScheme.tertiaryContainer,
            contentColor = colorScheme.onTertiaryContainer,
        )
        StatusTag(
            label = if (installed) "v${state.ksuVersion}" else "—",
            backgroundColor = colorScheme.surfaceContainerHigh,
            contentColor = colorScheme.onSurfaceVariantSummary,
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (danger) colorScheme.error else colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariantSummary,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}
