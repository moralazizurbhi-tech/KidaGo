package com.afede.kidago.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.afede.kidago.R
import com.afede.kidago.data.UsbSnapshot
import com.afede.kidago.data.UsbStatus
import com.afede.kidago.data.status
import com.afede.kidago.ui.scan.ActionPill
import com.afede.kidago.ui.theme.KidaGoButton
import com.afede.kidago.ui.theme.KidaGoColors
import com.afede.kidago.ui.theme.KidaGoNeutralButton
import com.afede.kidago.ui.theme.KidaGoShapes
import com.afede.kidago.ui.theme.KidaGoSpacing

private val steps = listOf(
    "Desenchufa el cable y espera 10 segundos.",
    "Desbloquea la PDA y enchúfala directamente al PC, sin hub.",
    "Baja las notificaciones, toca la del USB y elige «Transferencia de archivos».",
    "Si sigue en gris, prueba otro cable u otro puerto: algunos cables solo cargan.",
    "En Opciones de desarrollador, desactiva y vuelve a activar «Depuración USB».",
    "Si nada funciona, reinicia la PDA.",
)

/**
 * Conexión USB (FEAT-004 C14, C15): an inner Ajustes screen with the live USB state, the recovery guide for greyed-out
 * USB options, a shortcut to the developer options, and "Guardar registro". "Volver" and the back key return to Ajustes.
 */
@Composable
fun UsbHelpScreen(usb: UsbSnapshot, onSave: () -> String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var saveResult by remember { mutableStateOf<String?>(null) }
    Column(
        modifier.fillMaxSize().testTag("screen-usb").verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = KidaGoSpacing.Gap),
        verticalArrangement = Arrangement.spacedBy(KidaGoSpacing.Gap),
    ) {
        Column(
            Modifier.fillMaxWidth().background(KidaGoColors.Surface, KidaGoShapes.Card).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Conexión USB", style = MaterialTheme.typography.titleLarge, color = KidaGoColors.Text)
            StatusLine(usb.status())
            HorizontalDivider(color = KidaGoColors.Border)

            Text("Si las opciones USB salen en gris", style = MaterialTheme.typography.titleMedium, color = KidaGoColors.Text)
            steps.forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, color = KidaGoColors.Text)
                    Text(step, style = MaterialTheme.typography.bodyLarge, color = KidaGoColors.Text)
                }
            }
            HorizontalDivider(color = KidaGoColors.Border)

            Text("Para no elegirlo cada vez", style = MaterialTheme.typography.titleMedium, color = KidaGoColors.Text)
            Text(
                "En Opciones de desarrollador, pon «Configuración USB predeterminada» en «Transferencia de archivos». " +
                    "Si no ves esas opciones, en Información del teléfono toca 7 veces «Número de compilación».",
                style = MaterialTheme.typography.bodyLarge, color = KidaGoColors.Text,
            )
            KidaGoNeutralButton("Opciones de desarrollador", onClick = {
                val developer = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
                val action = if (developer) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_DEVICE_INFO_SETTINGS
                runCatching { context.startActivity(Intent(action)) }
            }, Modifier.fillMaxWidth().testTag("usb-developer"))
            HorizontalDivider(color = KidaGoColors.Border)

            Text("Registro", style = MaterialTheme.typography.titleMedium, color = KidaGoColors.Text)
            Text("Guarda el estado del USB y sus cambios en Descargas, para enviarlo a soporte.", style = MaterialTheme.typography.bodyLarge, color = KidaGoColors.Text)
            KidaGoButton("Guardar registro", onClick = {
                saveResult = onSave()?.let { "Guardado en Descargas: $it" } ?: "No se ha podido guardar el registro"
            }, Modifier.fillMaxWidth().testTag("usb-save"))
            saveResult?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = KidaGoColors.Text, modifier = Modifier.testTag("usb-save-result")) }
        }
        ActionPill("Volver", painterResource(R.drawable.ic_arrow_back), onBack, Modifier.fillMaxWidth().testTag("usb-back"))
    }
}

@Composable
private fun StatusLine(status: UsbStatus) {
    val (text, color) = when (status) {
        UsbStatus.FileTransfer -> "Transferencia de archivos activa" to KidaGoColors.SuccessIcon
        UsbStatus.ConnectedNoFiles -> "Conectada al PC, sin transferencia de archivos" to KidaGoColors.Accent
        UsbStatus.ChargingOnly -> "Cargando, pero el PC no recibe datos" to KidaGoColors.Accent
        UsbStatus.Unplugged -> "Sin cable USB" to KidaGoColors.TextMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (color == KidaGoColors.TextMuted) KidaGoColors.Text else color, modifier = Modifier.testTag("usb-status"))
    }
}
