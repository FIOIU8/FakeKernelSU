package me.weishu.kernelsu.ui.util

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Stealth unlock: long-pressing the home page title opens the hidden control
 * panel.
 *
 * A long press is deliberate — a plain tap would fire while scrolling or while
 * the bar is tapped for other reasons, and the entry is supposed to stay
 * invisible to anyone who does not already know about it. A haptic tick
 * confirms the gesture fired, since there is no visual affordance.
 */
@Composable
fun rememberFakeUnlockModifier(onUnlocked: () -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    val currentOnUnlocked by rememberUpdatedState(onUnlocked)
    return Modifier.pointerInput(Unit) {
        detectTapGestures(
            onLongPress = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnUnlocked()
            },
        )
    }
}
