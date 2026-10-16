package com.afede.kidago.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.afede.kidago.ui.theme.DesignPixels
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
    // Not the platform's default width: it is sized in real dp, which on the 480 px design canvas leaves no side margin.
    // A Dialog is its own window and its own density (real dp), so re-apply the design pixels inside it.
    Dialog(onDismissRequest = onDecline, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DesignPixels {
            Surface(Modifier.fillMaxWidth().padding(horizontal = KidaGoSpacing.Outer), shape = KidaGoShapes.Card, color = KidaGoColors.Surface) {
                Column(Modifier.padding(KidaGoSpacing.Outer), verticalArrangement = Arrangement.spacedBy(KidaGoSpacing.Gap)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    ActionRow {
                        KidaGoNeutralButton(declineLabel, onDecline)
                        KidaGoButton(confirmLabel, onConfirm)
                    }
                }
            }
        }
    }
}

private val ActionHeight = 64.dp
private val ActionMinWidth = 160.dp
private val ActionGap = 16.dp

/**
 * The prompt's action row (Project UX Specification, Action row). Content is decline then confirm. Side by side
 * (decline left, confirm right) while both fit at their minimum width, never narrower than their label; otherwise
 * stacked at full width, confirm on top, so a label is never squeezed.
 */
@Composable
internal fun ActionRow(content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val (decline, confirm) = measurables
        val h = ActionHeight.roundToPx()
        val gap = ActionGap.roundToPx()
        val declineW = maxOf(ActionMinWidth.roundToPx(), decline.maxIntrinsicWidth(h))
        val confirmW = maxOf(ActionMinWidth.roundToPx(), confirm.maxIntrinsicWidth(h))
        val width = constraints.maxWidth
        if (declineW + gap + confirmW <= width) {
            val d = decline.measure(Constraints.fixed(declineW, h))
            val c = confirm.measure(Constraints.fixed(confirmW, h))
            layout(width, h) {
                c.place(width - confirmW, 0)
                d.place(width - confirmW - gap - declineW, 0)
            }
        } else {
            val c = confirm.measure(Constraints.fixed(width, h))
            val d = decline.measure(Constraints.fixed(width, h))
            layout(width, h + gap + h) {
                c.place(0, 0)
                d.place(0, h + gap)
            }
        }
    }
}
