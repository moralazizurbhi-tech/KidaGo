package com.afede.kidago.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.afede.kidago.ui.components.ConfirmationController
import com.afede.kidago.ui.components.ConfirmationHost
import com.afede.kidago.ui.theme.KidaGoColors
import com.afede.kidago.ui.theme.KidaGoShapes
import com.afede.kidago.ui.theme.KidaGoSpacing
import com.afede.kidago.ui.theme.KidaGoSwitch
import com.afede.kidago.ui.theme.KidaGoTextField

/**
 * Ajustes (FEAT-004 UX/UI): Sonido and Vibrar toggles, "Ruta del catálogo", Acerca de, and the manual-clear entry
 * point last and visually secondary, with its confirmation prompt hosted here.
 */
@Composable
fun AjustesScreen(
    state: AjustesState,
    onSoundToggle: () -> Unit,
    onVibrationToggle: () -> Unit,
    onFolderSubmit: (String) -> Boolean,
    confirmation: ConfirmationController,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().testTag("screen-settings").verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = KidaGoSpacing.Gap)) {
        Column(
            Modifier.fillMaxWidth().background(KidaGoColors.Surface, KidaGoShapes.Card).padding(KidaGoSpacing.Outer),
            verticalArrangement = Arrangement.spacedBy(KidaGoSpacing.Gap),
        ) {
            ToggleRow("Sonido", "Utilizar alertas de sonido al escanear", state.soundEnabled, "ajustes-sound", onSoundToggle)
            HorizontalDivider(color = KidaGoColors.Border)
            ToggleRow("Vibrar", "Utilizar alertas de vibración al escanear", state.vibrationEnabled, "ajustes-vibration", onVibrationToggle)
            HorizontalDivider(color = KidaGoColors.Border)
            FolderRow(state.catalogFolder, onFolderSubmit)
            HorizontalDivider(color = KidaGoColors.Border)
            AboutRow(state.version)
            Spacer(Modifier.height(8.dp))
            ClearRow(onClear)
        }
    }
    ConfirmationHost(confirmation)
}

@Composable
private fun RowLabel(label: String, subtext: String?) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = KidaGoColors.Text)
        if (subtext != null) Text(subtext, style = MaterialTheme.typography.bodyMedium, color = KidaGoColors.Text)
    }
}

@Composable
private fun ToggleRow(label: String, subtext: String, checked: Boolean, tag: String, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f).padding(end = KidaGoSpacing.Gap)) { RowLabel(label, subtext) }
        KidaGoSwitch(checked, { onToggle() }, Modifier.testTag(tag))
    }
}

@Composable
private fun FolderRow(saved: String, onSubmit: (String) -> Boolean) {
    // The draft is the text being typed; it resets to the saved folder whenever that changes, and when an empty
    // value is rejected (the previous folder stays, C11).
    var draft by remember(saved) { mutableStateOf(saved) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RowLabel("Ruta del catálogo", null)
        KidaGoTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth().testTag("ajustes-folder"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!onSubmit(draft)) draft = saved }),
        )
    }
}

@Composable
private fun AboutRow(version: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = KidaGoColors.Text, modifier = Modifier.size(24.dp))
        Column {
            Text("Acerca de", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = KidaGoColors.Text)
            Text("KidaGo v$version", style = MaterialTheme.typography.bodyMedium, color = KidaGoColors.Text, modifier = Modifier.testTag("ajustes-version"))
        }
    }
}

/** Secondary destructive action: red text and icon, no filled button, separated from the rows above (C12). */
@Composable
private fun ClearRow(onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClear).padding(vertical = 8.dp).testTag("ajustes-clear"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Delete, contentDescription = null, tint = KidaGoColors.Accent, modifier = Modifier.size(20.dp))
        Text("Vaciar lista", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = KidaGoColors.Accent)
    }
}
