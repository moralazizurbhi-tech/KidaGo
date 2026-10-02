package com.afede.kidago.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.afede.kidago.ui.theme.KidaGoButton
import com.afede.kidago.ui.theme.KidaGoColors
import com.afede.kidago.ui.theme.KidaGoNeutralButton
import com.afede.kidago.ui.theme.KidaGoShapes
import com.afede.kidago.ui.theme.KidaGoSpacing

/** What a caller asks the user to confirm. Copy belongs to the caller (each Feature owns its wording). */
class ConfirmationRequest(
    val title: String,
    val confirmLabel: String,
    val declineLabel: String = "Cancelar",
    val onConfirm: () -> Unit,
    val onDecline: () -> Unit = {},
)

/**
 * Holds the one prompt that may be pending. A request made while another is pending is refused, and each
 * pending request resolves at most once, so callbacks never fire twice (double taps, dismiss + tap).
 */
class ConfirmationController {
    var current by mutableStateOf<ConfirmationRequest?>(null)
        private set

    /** @return false (and changes nothing) when a prompt is already pending. */
    fun request(request: ConfirmationRequest): Boolean {
        if (current != null) return false
        current = request
        return true
    }

    fun confirm() {
        val pending = current ?: return
        current = null // clear first: a second call finds nothing pending
        pending.onConfirm()
    }

    fun decline() {
        val pending = current ?: return
        current = null
        pending.onDecline()
    }
}

/** Renders the controller's pending request, if any. Place once, near the root of the screen using it. */
@Composable
fun ConfirmationHost(controller: ConfirmationController) {
    val request = controller.current ?: return
    DestructiveConfirmationPrompt(
        title = request.title,
        confirmLabel = request.confirmLabel,
        declineLabel = request.declineLabel,
        onConfirm = controller::confirm,
        onDecline = controller::decline, // back or tapping outside counts as declining
    )
}

/** The shared destructive confirmation prompt: white card, red confirm, neutral decline, no shadow. */
@Composable
fun DestructiveConfirmationPrompt(
    title: String,
    confirmLabel: String,
    declineLabel: String,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
) {
    Dialog(onDismissRequest = onDecline) {
        Surface(shape = KidaGoShapes.Card, color = KidaGoColors.Surface) {
            Column(Modifier.padding(KidaGoSpacing.Outer), verticalArrangement = Arrangement.spacedBy(KidaGoSpacing.Gap)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    KidaGoNeutralButton(declineLabel, onDecline)
                    KidaGoButton(confirmLabel, onConfirm)
                }
            }
        }
    }
}
