package com.afede.kidago.ui.products

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afede.kidago.R
import com.afede.kidago.data.ListEntry
import com.afede.kidago.ui.components.ConfirmationController
import com.afede.kidago.ui.components.ConfirmationHost
import com.afede.kidago.ui.theme.KidaGoColors
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
) {
    Column(modifier.fillMaxSize().testTag("screen-products").padding(horizontal = 20.dp)) {
        if (state == null) return@Column // not loaded yet: show nothing rather than a wrong empty state
        Header(state.total)
        if (state.isEmpty) EmptyState(Modifier.weight(1f)) else EntryList(state.entries, onRowLongPress)
    }
    ConfirmationHost(confirmation)
}

@Composable
private fun Header(total: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = KidaGoSpacing.Gap), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Productos escaneados", style = MaterialTheme.typography.titleLarge, color = KidaGoColors.Text)
        // 36x24 is the minimum pill; it grows for a multi-digit total. The tint opacity is not resolvable from the source design.
        Box(
            Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 24.dp)
                .background(KidaGoColors.Accent.copy(alpha = 0.15f), KidaGoShapes.Pill)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(total.toString(), style = MaterialTheme.typography.labelLarge, color = KidaGoColors.Accent, modifier = Modifier.testTag("productos-count"))
        }
    }
}

@Composable
private fun EntryList(entries: List<ListEntry>, onRowLongPress: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(KidaGoColors.Surface, KidaGoShapes.Card).padding(vertical = KidaGoSpacing.Gap)) {
        LazyColumn(Modifier.padding(horizontal = KidaGoSpacing.Outer).testTag("productos-list")) {
            items(entries, key = { it.code }) { EntryRow(it, onRowLongPress) }
        }
    }
}

@Composable
private fun EntryRow(entry: ListEntry, onLongPress: (String) -> Unit) {
    // Long-press is the only gesture: no ripple, no visible control or hint, and a plain tap does nothing (C3, C4).
    // The semantics action lets TalkBack users reach it without anything appearing on screen.
    Column(
        Modifier.testTag("productos-row-${entry.code}")
            .pointerInput(entry.code) { detectTapGestures(onLongPress = { onLongPress(entry.code) }) }
            .semantics { onLongClick(label = "Eliminar producto") { onLongPress(entry.code); true } },
    ) {
        Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(entry.code, style = MaterialTheme.typography.bodyLarge, color = KidaGoColors.Text)
            Text(entry.quantity.toString(), style = MaterialTheme.typography.bodyLarge, color = KidaGoColors.Text)
        }
        HorizontalDivider(color = KidaGoColors.Border)
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier.fillMaxWidth().testTag("productos-empty"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // The project's mascot outline (same artwork as the Productos tab icon).
        Image(painterResource(R.drawable.ic_tab_products), contentDescription = null, modifier = Modifier.size(width = 83.dp, height = 53.dp))
        Text("No hay productos escaneados", style = MaterialTheme.typography.titleLarge, color = KidaGoColors.Text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = KidaGoSpacing.Gap))
        Text(
            "Comienza a escanear para añadir productos a la lista",
            style = MaterialTheme.typography.bodyMedium,
            color = KidaGoColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = KidaGoSpacing.Outer, end = KidaGoSpacing.Outer),
        )
    }
}
