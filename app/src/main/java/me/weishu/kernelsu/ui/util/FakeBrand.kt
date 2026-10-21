package me.weishu.kernelsu.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.fake.FakeState

/** The brand token: lab override, then the in-app name, then the app label. */
@Composable
fun brandName(): String {
    val brand = FakeState.brandName
    if (brand.isNotBlank()) return brand
    return fakeAppName()
}

/**
 * [stringResource] with the hard-coded brand replaced by [brandName].
 *
 * Only the complete word `FakeKernelSU` is substituted, and only for the
 * resource ids that call this helper — official links and the bare
 * `KernelSU` word are left untouched on purpose (D12).
 */
@Composable
fun fakeStr(@StringRes id: Int, vararg args: Any): String {
    val text = stringResource(id, *args)
    val brand = brandName()
    if (brand == stringResource(R.string.app_name)) return text
    return text.replace(LEGACY_BRAND, brand)
}

private const val LEGACY_BRAND = "FakeKernelSU"
