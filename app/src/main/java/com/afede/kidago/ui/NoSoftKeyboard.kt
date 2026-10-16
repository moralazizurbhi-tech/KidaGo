package com.afede.kidago.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import kotlinx.coroutines.awaitCancellation

/**
 * With a physical keyboard no field inside [content] opens the on-screen keyboard (FEAT-002 UX, Keyboard and focus;
 * FEAT-004 TD): the platform text-input session is never started, so the fields keep taking focus and the typed keys,
 * but nothing asks the system for the soft keyboard. Without one, input works as usual.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoSoftKeyboard(physicalKeyboard: Boolean, content: @Composable () -> Unit) {
    val suppress by rememberUpdatedState(physicalKeyboard)
    val interceptor = remember {
        PlatformTextInputInterceptor { request, nextHandler -> if (suppress) awaitCancellation() else nextHandler.startInputMethod(request) }
    }
    InterceptPlatformTextInput(interceptor, content)
}
