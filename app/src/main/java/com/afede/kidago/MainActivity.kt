package com.afede.kidago

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.afede.kidago.ui.catalog.CatalogStatusStrip
import com.afede.kidago.ui.products.ProductosListViewModel
import com.afede.kidago.ui.products.ProductosScreen
import com.afede.kidago.ui.shell.AppShell
import com.afede.kidago.ui.theme.KidaGoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as KidaGoApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container // open the database at launch
        setContent {
            KidaGoTheme {
                val sync by container.catalogSync.state.collectAsState()
                val products: ProductosListViewModel = viewModel(factory = viewModelFactory {
                    initializer { ProductosListViewModel(container.todaysList.entries, container.todaysList.total, container.todaysList::remove) }
                })
                val productos by products.state.collectAsState()
                AppShell(
                    headerContent = { CatalogStatusStrip(sync, onNoFileAccessTap = container.fileAccess::openSettings) },
                    productsScreen = { ProductosScreen(productos, products.confirmation, products.removal::onLongPress) },
                )
            }
        }
    }

    // onResume runs at launch and at every return to the foreground: the catalog detection opportunities (FEAT-001 C1, C17).
    override fun onResume() {
        super.onResume()
        container.appScope.launch { container.catalogSync.onDetectionOpportunity() }
    }
}
