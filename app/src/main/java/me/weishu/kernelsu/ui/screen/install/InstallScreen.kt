package me.weishu.kernelsu.ui.screen.install

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.ui.util.LkmSelection

/**
 * FakeKernelSU install screen.
 *
 * Upstream offers select-boot-image / download / direct-install / inactive-slot
 * flows. The fake manager keeps exactly one: "Direct install", which patches
 * nothing and simply writes the install flag (see [installBoot]).
 *
 * The screen is rendered with the skin the user picked in the settings, so both
 * the Material and the Miuix flavour live in this one APK.
 */
@Composable
fun InstallScreen() {
    val navigator = LocalNavigator.current
    var selected by remember { mutableStateOf(0) }

    fun goNext() {
        navigator.push(
            Route.Flash(
                FlashIt.FlashBoot(
                    boot = null,
                    lkm = LkmSelection.KmiNone,
                    ota = false,
                    partition = "boot",
                    allowShell = false,
                    enableAdb = false,
                    backup = false,
                )
            )
        )
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> InstallScreenMiuix(selected = selected, onSelect = { selected = it }, onNext = ::goNext)
        UiMode.Material -> InstallScreenMaterial(selected = selected, onSelect = { selected = it }, onNext = ::goNext)
    }
}
