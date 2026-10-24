package me.weishu.kernelsu.ui.fake

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Stealth unlock: long-pressing a page title opens the hidden control panel.
 *
 * A long press is deliberate — a plain tap would fire while scrolling or while
 * the bar is tapped for other reasons, and the entry is supposed to stay
 * invisible to anyone who does not already know about it. A haptic tick
 * confirms the gesture fired, since there is no visual affordance.
 *
 * Two details make this work on top app bars that handle their own touches
 * (Miuix's `TopAppBar` installs `detectTapGestures` on the whole bar and
 * consumes every press):
 *
 *  - the press is observed on [PointerEventPass.Initial], which travels from the
 *    root down to the leaves, so we see it before any child consumes it;
 *  - the hold is timed with `AwaitPointerEventScope.withTimeout`, not with
 *    `kotlinx.coroutines.withTimeout`. Compose's pointer input machinery owns
 *    that coroutine, and only its own timeout completes the await correctly.
 *
 * Nothing is consumed on purpose: the bar's own handlers keep working.
 */
@Composable
fun rememberFakeUnlockModifier(onUnlocked: () -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    val currentOnUnlocked by rememberUpdatedState(onUnlocked)
    return Modifier.pointerInput(Unit) {
        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
        val touchSlop = viewConfiguration.touchSlop
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                try {
                    // Returns early — without unlocking — as soon as the pointer
                    // lifts or wanders off, so scrolls and taps are ignored.
                    withTimeout(longPressTimeout) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@withTimeout
                            if (!change.pressed) return@withTimeout
                            if ((change.position - down.position).getDistance() > touchSlop) {
                                return@withTimeout
                            }
                        }
                    }
                } catch (_: PointerEventTimeoutCancellationException) {
                    // Held still for the full long-press window.
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnUnlocked()
                }
            }
        }
    }
}
