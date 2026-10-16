package com.afede.kidago.ui.products

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.compositeOver
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.afede.kidago.R
import com.afede.kidago.data.ListEntry
import com.afede.kidago.ui.components.ConfirmationController
import com.afede.kidago.ui.components.ConfirmationHost
import com.afede.kidago.ui.groupedBarcode
import com.afede.kidago.ui.theme.KidaGoColors
import com.afede.kidago.ui.theme.Manrope
import com.afede.kidago.ui.theme.KidaGoShapes
import com.afede.kidago.ui.theme.KidaGoSpacing

/**
 * Productos (FEAT-003 UX/UI): header with the total count, then the list or the empty state. Quantities cannot be
 * edited here; the only gesture is a long-press on a row, which asks for confirmation to remove it.
 */
@Composable
fun ProductosScreen(
    state: ProductosState?,
    confirmation: ConfirmationController,
    onRowLongPress: (code: String) -> Unit,
    modifier: Modifier = Modifier,
    /** The removal in progress: its row stays highlighted while the prompt is open (C13). */
    removalState: RemovalState = RemovalState.List,
) {
    // The header stays in place; the list takes the rest down to 16 px above the tab bar and is the only thing that scrolls.
    Column(modifier.fillMaxSize().testTag("screen-products").padding(bottom = KidaGoSpacing.Gap)) {
        if (state == null) return@Column // not loaded yet: show nothing rather than a wrong empty state
        Header(state.total)
        if (state.isEmpty) EmptyState(Modifier.weight(1f)) else EntryList(Modifier.weight(1f, fill = false), state.entries, (removalState as? RemovalState.ConfirmingRemoval)?.code, onRowLongPress)
    }
    ConfirmationHost(confirmation)
}

@Composable
private fun Header(total: Int) {
    // The title starts on the 32 px content margin and the badge ends on the opposite one.
    Row(Modifier.fillMaxWidth().padding(horizontal = KidaGoSpacing.Outer, vertical = KidaGoSpacing.Gap), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Productos escaneados", style = MaterialTheme.typography.titleLarge, color = KidaGoColors.Text)
        // 36x24 is the minimum pill; it grows for a multi-digit total. The tint opacity is not resolvable from the source design.
        Box(
            Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 24.dp).testTag("productos-badge")
                .background(KidaGoColors.Accent.copy(alpha = 0.15f), KidaGoShapes.Pill)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(total.toString(), style = MaterialTheme.typography.labelMedium, color = KidaGoColors.Accent, modifier = Modifier.testTag("productos-count"))
        }
    }
}

@Composable
private fun EntryList(modifier: Modifier, entries: List<ListEntry>, confirmingCode: String?, onRowLongPress: (String) -> Unit) {
    Column(modifier.padding(horizontal = 20.dp).fillMaxWidth().background(KidaGoColors.Surface, KidaGoShapes.Card).padding(vertical = KidaGoSpacing.Gap)) {
        LazyColumn(Modifier.padding(horizontal = KidaGoSpacing.Outer).testTag("productos-list")) {
            items(entries, key = { it.code }) { EntryRow(it, it.code == confirmingCode, onRowLongPress) }
        }
    }
}

@Composable
private fun EntryRow(entry: ListEntry, confirming: Boolean, onLongPress: (String) -> Unit) {
    // Long-press is the only gesture: no ripple, no visible control or hint, and a plain tap does nothing (C3, C4).
    // The semantics action lets TalkBack users reach it without anything appearing on screen.
    // Pressed feedback (C13): a very faint tint once the finger has been held HOLD_MS; a quick tap or a scroll never shows it,
    // and it stays while this row's removal prompt is open.
    var held by remember { mutableStateOf(false) }
    val tint by animateColorAsState(
        if (held || confirming) KidaGoColors.Accent.copy(alpha = 0.06f).compositeOver(KidaGoColors.Surface) else KidaGoColors.Surface,
        tween(120), label = "row-tint",
    )
    Column(
        Modifier.testTag("productos-row-${entry.code}")
            .background(tint)
            .pointerInput(entry.code) {
                detectTapGestures(
                    onPress = {
                        if (withTimeoutOrNull(HOLD_MS) { tryAwaitRelease() } == null) { // still down after the hold
                            held = true
                            try { tryAwaitRelease() } finally { held = false }
                        }
                    },
                    onLongPress = { onLongPress(entry.code) },
                )
            }
            .semantics { onLongClick(label = "Eliminar producto") { onLongPress(entry.code); true } },
    ) {
        Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(groupedBarcode(entry.code), style = RowText, color = KidaGoColors.Text) // display only: the stored code is digits
            Text(entry.quantity.toString(), style = RowText, color = KidaGoColors.Text)
        }
        HorizontalDivider(color = KidaGoColors.Border)
    }
}

private const val HOLD_MS = 150L // the platform's tap timeout

private val RowText = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 28.sp) // regular 20 px

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier.fillMaxWidth().testTag("productos-empty"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // The project's mascot outline (same artwork as the Productos tab icon).
        // 83x83 frame (FEAT-003 UI Definition); the artwork keeps its 83x53 visible rows, centred in it.
        Box(Modifier.size(83.dp), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_tab_products), contentDescription = null, modifier = Modifier.size(width = 83.dp, height = 53.dp))
        }
        Text("No hay productos escaneados", style = MaterialTheme.typography.titleLarge, color = KidaGoColors.Text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = KidaGoSpacing.Gap))
        Text(
            "Comienza a escanear para añadir productos a la lista",
            style = MaterialTheme.typography.labelLarge, // bold 14 px at 60% opacity
            color = KidaGoColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = KidaGoSpacing.Outer, end = KidaGoSpacing.Outer),
        )
    }
}
