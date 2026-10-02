package com.afede.kidago.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afede.kidago.ui.components.ConfirmationController
import com.afede.kidago.ui.components.ConfirmationHost
import com.afede.kidago.ui.components.ConfirmationRequest
import com.afede.kidago.ui.shell.AppShell
import com.afede.kidago.ui.theme.KidaGoButton
import com.afede.kidago.ui.theme.KidaGoColors
import com.afede.kidago.ui.theme.KidaGoNeutralButton
import com.afede.kidago.ui.theme.KidaGoShapes
import com.afede.kidago.ui.theme.KidaGoSpacing
import com.afede.kidago.ui.theme.KidaGoSwitch
import com.afede.kidago.ui.theme.KidaGoTextField
import com.afede.kidago.ui.theme.KidaGoTheme
import com.afede.kidago.ui.theme.KidaGoWordmark

/** Debug-only: shows the theme tokens and styled controls on a device. Not part of the shipped app. */
class ThemeGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KidaGoTheme { Surface(color = MaterialTheme.colorScheme.background) { ThemeGallery() } } }
    }
}

/** Debug-only: the shell with a sample badge total, to see the tab badge. */
class ShellGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KidaGoTheme { AppShell(productsBadge = 12) } }
    }
}

@Composable
fun ThemeGallery() {
    var on by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    val prompts = remember { ConfirmationController() }
    ConfirmationHost(prompts)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(KidaGoSpacing.Outer),
        verticalArrangement = Arrangement.spacedBy(KidaGoSpacing.Gap),
    ) {
        Text("KidaGo", style = KidaGoWordmark)
        Text("Escanear producto", style = MaterialTheme.typography.headlineMedium)
        Text("Texto de cuerpo en Manrope regular", style = MaterialTheme.typography.bodyLarge)
        Column(
            Modifier
                .background(KidaGoColors.Surface, KidaGoShapes.Card)
                .border(1.dp, KidaGoColors.Border, KidaGoShapes.Card)
                .padding(KidaGoSpacing.Gap),
            verticalArrangement = Arrangement.spacedBy(KidaGoSpacing.Gap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Sonido", style = MaterialTheme.typography.titleMedium)
                KidaGoSwitch(on, { on = it })
                KidaGoSwitch(false, {})
            }
            KidaGoTextField(text, { text = it }, label = "Cantidad", placeholder = "1")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KidaGoButton("Confirmar", {})
                KidaGoNeutralButton("Cancelar", {})
            }
            KidaGoNeutralButton("Probar aviso", {
                prompts.request(ConfirmationRequest("¿Eliminar este producto?", "Eliminar", onConfirm = {}))
            })
        }
    }
}
