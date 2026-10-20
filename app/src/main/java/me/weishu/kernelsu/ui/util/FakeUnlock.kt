package me.weishu.kernelsu.ui.util

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.fake.FakeState

/**
 * Stealth unlock: tapping the About screen title [FakeState.unlockTapCount]
 * times opens the hidden control panel.
 */
@Composable
fun rememberFakeUnlockTapModifier(onUnlocked: () -> Unit): Modifier {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    return Modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                taps += 1
                val needed = FakeState.unlockTapCount
                if (taps >= needed) {
                    taps = 0
                    onUnlocked()
                } else if (taps >= 2) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.fake_about_tap_hint, needed - taps),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        )
    }
}
