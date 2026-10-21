package me.weishu.kernelsu.ui.screen.fakepanel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
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
import me.weishu.kernelsu.ui.component.miuix.EditText
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
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
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix flavour of the FakeKernelSU control panel.
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

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.fake_panel_title),
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
                    // -------------------------------------------- install state
                    PanelCard(title = stringResource(R.string.fake_panel_state)) {
                        FakeInstallState.entries.forEach { entry ->
                            RadioButtonPreference(
                                title = stringResource(entry.labelRes()),
                                summary = stringResource(entry.summaryRes()),
                                selected = state.stateOverride == entry,
                                onClick = { state.selectInstallState(entry) },
                            )
                        }
                    }

                    // ------------------------------------------------ root state
                    PanelCard(title = stringResource(R.string.fake_panel_root)) {
                        SwitchPreference(
                            title = stringResource(R.string.fake_panel_root),
                            summary = stringResource(R.string.fake_panel_root_summary),
                            checked = state.rootAvailable,
                            onCheckedChange = { state.updateRootAvailable(it) },
                        )
                    }

                    // --------------------------------------------------- version
                    PanelCard(title = stringResource(R.string.fake_panel_version)) {
                        EditText(
                            title = stringResource(R.string.fake_panel_version_hint),
                            value = state.ksuVersion,
                            onValueChange = { state.updateKsuVersion(it) },
                            textHint = "13000",
                        )
                        EditText(
                            title = stringResource(R.string.fake_panel_manager_version_code),
                            value = state.managerVersionCode,
                            onValueChange = { state.updateManagerVersionCode(it) },
                        )
                        EditText(
                            title = stringResource(R.string.fake_panel_manager_version_name),
                            value = state.managerVersionName,
                            onValueChange = { state.updateManagerVersionName(it) },
                        )
                        if (state.managerVersionCode.isNotBlank()) {
                            Text(
                                text = stringResource(
                                    R.string.fake_panel_compare_value,
                                    state.managerVersionCode.toLongOrNull() ?: 0L,
                                ),
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.fake_panel_lkm),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                        RadioButtonPreference(
                            title = stringResource(R.string.fake_panel_lkm_gki),
                            selected = state.lkmMode == null,
                            onClick = { state.updateLkmMode(null) },
                        )
                        RadioButtonPreference(
                            title = stringResource(R.string.fake_panel_lkm_lkm),
                            selected = state.lkmMode == true,
                            onClick = { state.updateLkmMode(true) },
                        )
                        RadioButtonPreference(
                            title = stringResource(R.string.fake_panel_lkm_none),
                            selected = state.lkmMode == false,
                            onClick = { state.updateLkmMode(false) },
                        )
                    }

                    // ------------------------------------------ home overrides
                    PanelCard(title = stringResource(R.string.fake_panel_home_overrides)) {
                        Text(
                            text = stringResource(R.string.fake_panel_override_hint),
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        )
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

                    // ----------------------------------------------- app naming
                    PanelCard(title = stringResource(R.string.fake_panel_app_name)) {
                        Text(
                            text = stringResource(R.string.fake_panel_launcher_label),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                        LauncherAlias.entries.forEach { entry ->
                            RadioButtonPreference(
                                title = stringResource(entry.labelRes),
                                selected = state.launcherAlias == entry,
                                onClick = { state.selectLauncherAlias(entry) },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        EditText(
                            title = stringResource(R.string.fake_panel_in_app_name),
                            value = state.inAppName,
                            onValueChange = { state.updateInAppName(it) },
                        )
                    }

                    // ----------------------------------------------- su simulate
                    PanelCard(title = stringResource(R.string.fake_panel_su_mode)) {
                        FakeSuRequestMode.entries.forEach { entry ->
                            RadioButtonPreference(
                                title = stringResource(entry.labelRes()),
                                selected = state.suRequestMode == entry,
                                onClick = { state.selectSuRequestMode(entry) },
                            )
                        }
                    }

                    // -------------------------------------------------- stealth
                    PanelCard(title = stringResource(R.string.fake_panel_stealth)) {
                        SwitchPreference(
                            title = stringResource(R.string.fake_panel_stealth),
                            summary = stringResource(R.string.fake_panel_stealth_summary, state.unlockTaps),
                            checked = state.stealthMode,
                            onCheckedChange = { state.updateStealthMode(it) },
                        )
                        SliderPreference(
                            value = state.sliderValue,
                            onValueChange = { state.setUnlockTaps(it) },
                            title = stringResource(R.string.fake_panel_tap_count, state.unlockTaps),
                            valueRange = 3f..20f,
                            steps = 16,
                        )
                    }

                    // -------------------------------------------- danger zone
                    PanelCard(title = stringResource(R.string.fake_panel_danger)) {
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
                        text = stringResource(R.string.fake_panel_disclaimer),
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

@Composable
private fun PanelCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}
