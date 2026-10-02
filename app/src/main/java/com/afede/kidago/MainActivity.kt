package com.afede.kidago

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.afede.kidago.ui.shell.AppShell
import com.afede.kidago.ui.theme.KidaGoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as KidaGoApp).container // open the database at launch
        setContent { KidaGoTheme { AppShell() } }
    }
}
