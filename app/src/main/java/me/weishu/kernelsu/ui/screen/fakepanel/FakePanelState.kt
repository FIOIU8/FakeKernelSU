package me.weishu.kernelsu.ui.screen.fakepanel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.weishu.kernelsu.fake.FakeInstallState
import me.weishu.kernelsu.fake.FakeManagerVersion
import me.weishu.kernelsu.fake.FakeSeccompMode
import me.weishu.kernelsu.fake.FakeSelinuxMode
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.fake.FakeSuRequestMode
import me.weishu.kernelsu.fake.LauncherAlias
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.fake.applyLauncherAlias

/**
 * The lab's mutually exclusive "how root is provided" selector. It is a UI
 * concept only: GKI/LKM map onto [FakeState.lkmMode], jailbreak additionally
 * sets [FakeState.jailbreakMode] (late-load implies LKM).
 */
enum class FakeWorkingMode {
    GKI,
    LKM,
    NONE,
    JAILBREAK,
    ;
}

/** Collapsible sections of the lab, in display order (#3). */
enum class FakePanelSection(val defaultExpanded: Boolean) {
    RUNTIME(true),
    VERSION(true),
    SYSTEM_INFO(false),
    NAMING(false),
    DANGER(false),
    ;
}

/**
 * Every value the control panel can tweak, plus the mutations that persist
 * them into [FakeState].
 *
 * Both flavours (Material / Miuix) share this model so the two screens stay
 * feature-identical.
 */
class FakePanelState {
    var stateOverride by mutableStateOf(FakeState.stateOverride)
    var rootAvailable by mutableStateOf(FakeState.rootAvailable)
    var ksuVersion by mutableStateOf(FakeState.ksuVersion.toString())
    var managerVersionCode by mutableStateOf(
        FakeState.managerVersionCodeOverride.takeIf { it > 0L }?.toString() ?: ""
    )
    var managerVersionName by mutableStateOf(FakeState.managerVersionNameOverride)
    var lkmMode by mutableStateOf(FakeState.lkmMode)
    var jailbreakMode by mutableStateOf(FakeState.jailbreakMode)
    var deviceModel by mutableStateOf(FakeState.deviceModelOverride)
    var kernelVersion by mutableStateOf(FakeState.kernelVersionOverride)
    var fingerprint by mutableStateOf(FakeState.fingerprintOverride)
    var selinuxMode by mutableStateOf(FakeState.selinuxMode)
    var seccompMode by mutableStateOf(FakeState.seccompMode)
    var launcherAlias by mutableStateOf(LauncherAlias.fromName(FakeState.launcherAlias))
    var inAppName by mutableStateOf(FakeState.inAppName)
    var brandName by mutableStateOf(FakeState.brandName)
    var suRequestMode by mutableStateOf(FakeState.suRequestMode)
    var stealthMode by mutableStateOf(FakeState.stealthMode)
    var featureStatus by mutableStateOf(FakeState.featureStatusOverride)
    var lkmUpdateAvailable by mutableStateOf(FakeState.lkmUpdateAvailable)
    var showWipeDialog by mutableStateOf(false)

    fun selectInstallState(entry: FakeInstallState) {
        stateOverride = entry
        FakeState.stateOverride = entry
    }

    fun updateRootAvailable(value: Boolean) {
        rootAvailable = value
        FakeState.rootAvailable = value
    }

    fun updateKsuVersion(input: String) {
        ksuVersion = input.filter { it.isDigit() }.take(6)
        ksuVersion.toIntOrNull()?.let {
            FakeState.ksuVersion = it.coerceIn(1, 999999)
        }
    }

    fun updateLkmMode(value: Boolean?) {
        lkmMode = value
        FakeState.lkmMode = value
    }

    val workingMode: FakeWorkingMode
        get() = when {
            jailbreakMode -> FakeWorkingMode.JAILBREAK
            lkmMode == true -> FakeWorkingMode.LKM
            lkmMode == false -> FakeWorkingMode.NONE
            else -> FakeWorkingMode.GKI
        }

    fun selectWorkingMode(mode: FakeWorkingMode) {
        if (mode == FakeWorkingMode.JAILBREAK) {
            // applyJailbreak() also answers the control panel: a forced
            // "not installed" override would otherwise survive and make the
            // home page contradict the jailbreak state.
            FakeState.applyJailbreak()
            stateOverride = FakeState.stateOverride
            jailbreakMode = true
            lkmMode = FakeState.lkmMode
            return
        }
        FakeState.clearJailbreak()
        jailbreakMode = false
        updateLkmMode(
            when (mode) {
                FakeWorkingMode.LKM -> true
                FakeWorkingMode.NONE -> false
                else -> null
            }
        )
    }

    fun updateManagerVersionCode(input: String) {
        managerVersionCode = input.filter { it.isDigit() }.take(9)
        FakeState.managerVersionCodeOverride = managerVersionCode.toLongOrNull() ?: 0L
    }

    fun updateManagerVersionName(input: String) {
        // A manager reports a release (x.y.z), not free text: keep digits and
        // single dots so whatever is typed here still reads as a version.
        val sanitized = buildString {
            for (ch in input) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '.' && isNotEmpty() && last() != '.' -> append(ch)
                }
            }
        }.take(15)
        managerVersionName = sanitized
        // The trailing dot only exists while the user is still typing "3.".
        FakeState.managerVersionNameOverride = sanitized.trim('.')
    }

    /**
     * The versionCode that follows from [managerVersionName] (`3.1.2` -> 30102),
     * or null when the field is empty / not a version yet.
     */
    val derivedManagerVersionCode: Long?
        get() = FakeManagerVersion.codeFromName(managerVersionName)

    /** What the update check compares against; null = the real build value. */
    val effectiveManagerVersionCode: Long?
        get() = managerVersionCode.toLongOrNull()?.takeIf { it > 0L } ?: derivedManagerVersionCode

    fun selectSelinuxMode(entry: FakeSelinuxMode) {
        selinuxMode = entry
        FakeState.selinuxMode = entry
    }

    fun selectSeccompMode(entry: FakeSeccompMode) {
        seccompMode = entry
        FakeState.seccompMode = entry
    }

    fun updateDeviceModel(value: String) {
        deviceModel = value
        FakeState.deviceModelOverride = value
    }

    fun updateKernelVersion(value: String) {
        kernelVersion = value
        FakeState.kernelVersionOverride = value
    }

    fun updateFingerprint(value: String) {
        fingerprint = value
        FakeState.fingerprintOverride = value
    }

    fun selectLauncherAlias(entry: LauncherAlias) {
        launcherAlias = entry
        FakeState.launcherAlias = entry.name
        applyLauncherAlias(entry)
    }

    fun updateInAppName(value: String) {
        inAppName = value
        FakeState.inAppName = value
    }

    fun updateBrandName(value: String) {
        brandName = value
        FakeState.brandName = value
    }

    fun selectSuRequestMode(entry: FakeSuRequestMode) {
        suRequestMode = entry
        FakeState.suRequestMode = entry
    }

    fun updateStealthMode(value: Boolean) {
        stealthMode = value
        FakeState.stealthMode = value
    }

    fun selectFeatureStatus(value: String) {
        featureStatus = value
        FakeState.featureStatusOverride = value
    }

    fun updateLkmUpdateAvailable(value: Boolean) {
        lkmUpdateAvailable = value
        FakeState.lkmUpdateAvailable = value
    }

    fun resetToDefaults() {
        FakeState.resetAll()
        applyLauncherAlias(LauncherAlias.FAKE)
        stateOverride = FakeInstallState.AUTO
        rootAvailable = true
        ksuVersion = FakeState.ksuVersion.toString()
        managerVersionCode = ""
        managerVersionName = ""
        lkmMode = null
        jailbreakMode = false
        deviceModel = ""
        kernelVersion = ""
        fingerprint = ""
        selinuxMode = FakeSelinuxMode.SYSTEM
        seccompMode = FakeSeccompMode.SYSTEM
        inAppName = ""
        brandName = ""
        stealthMode = false
        featureStatus = FakeState.FEATURE_STATUS_SUPPORTED
        lkmUpdateAvailable = false
        launcherAlias = LauncherAlias.FAKE
        suRequestMode = FakeSuRequestMode.PICK_APP
    }
}

@Composable
fun rememberFakePanelState(): FakePanelState = remember { FakePanelState() }

/**
 * FakeKernelSU control panel: tweaks every piece of fake state the home page,
 * superuser page and module page read from.
 *
 * The lab is never removed from the app: [FakePanelState.updateStealthMode]
 * only hides the Settings entry, and a long press on the home page title
 * always brings the lab back.
 */
@Composable
fun FakeControlPanelScreen() {
    val state = rememberFakePanelState()

    when (LocalUiMode.current) {
        UiMode.Miuix -> FakeControlPanelMiuix(state)
        UiMode.Material -> FakeControlPanelMaterial(state)
    }
}
