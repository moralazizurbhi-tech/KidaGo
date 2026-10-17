package com.afede.kidago

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.afede.kidago.data.ArchiveReason
import com.afede.kidago.data.SyncState
import com.afede.kidago.ui.catalog.CatalogStatusStrip
import com.afede.kidago.ui.products.ProductosListViewModel
import com.afede.kidago.ui.products.ProductosScreen
import com.afede.kidago.ui.scan.EscanearScreen
import com.afede.kidago.ui.scan.EscanearViewModel
import com.afede.kidago.ui.settings.AjustesScreen
import com.afede.kidago.ui.settings.AjustesViewModel
import com.afede.kidago.ui.shell.AppShell
import com.afede.kidago.ui.theme.KidaGoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as KidaGoApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container // open the database at launch
        // The desktop tool can empty the list file while the app stays in the foreground, so look again every few seconds (FEAT-002 C14).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(EXPORT_CHECK_INTERVAL_MS)
                    container.listMirror.checkForExport()
                }
            }
        }
        setContent {
            KidaGoTheme {
                val sync by container.catalogSync.state.collectAsState()
                val products: ProductosListViewModel = viewModel(factory = viewModelFactory {
                    initializer { ProductosListViewModel(container.todaysList.entries, container.todaysList.total, container.todaysList::remove) }
                })
                val productos by products.state.collectAsState()
                val ajustes: AjustesViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        with(container.settings) {
                            AjustesViewModel(settings, ::setSoundEnabled, ::setVibrationEnabled, ::setCatalogFolder, appVersion(), { container.todaysList.archiveAndClear(ArchiveReason.MANUAL) })
                        }
                    }
                })
                val ajustesState by ajustes.state.collectAsState()
                val escanear: EscanearViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        with(container) {
                            EscanearViewModel(scanner, catalogSync.state, catalogQuery::lookup, todaysList.entries, todaysList::addOrAccumulate, todaysList::applyQuantity, outcomeFeedback::onOutcome)
                        }
                    }
                })
                val captureState by escanear.state.collectAsState()
                val typed by escanear.typed.collectAsState()
                val physicalKeyboard by container.keyboardProbe.present.collectAsState()
                AppShell(
                    productsBadge = productos?.total ?: 0, // the live total of the list, the same number Productos shows in its header
                    headerContent = { CatalogStatusStrip(sync, onNoFileAccessTap = container.fileAccess::openSettings) },
                    scanScreen = { EscanearScreen(captureState, typed, escanear::onTyped, escanear::onSubmit, escanear::onQuantitySubmit, syncing = sync is SyncState.Syncing, onFinish = escanear::onFinish, onQuantityEdit = escanear::onQuantityEdit, physicalKeyboard = physicalKeyboard) },
                    productsScreen = { ProductosScreen(productos, products.confirmation, products.removal::onLongPress, removalState = products.removal.state.collectAsState().value) },
                    settingsScreen = {
                        AjustesScreen(ajustesState, ajustes::onSoundToggle, ajustes::onVibrationToggle, ajustes::onFolderSubmit, ajustes.confirmation, ajustes.clear::onClearTapped, physicalKeyboard = physicalKeyboard)
                    },
                )
            }
        }
    }

    private fun appVersion() = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    // onResume runs at launch and at every return to the foreground: the catalog detection opportunities (FEAT-001 C1, C17).
    override fun onResume() {
        super.onResume()
        container.keyboardProbe.refresh() // input devices may have changed while away (FEAT-002 PhysicalKeyboardProbe)
        container.appScope.launch { container.catalogSync.onDetectionOpportunity() }
        container.appScope.launch { container.listMirror.onLaunchOrResume() } // export-reset detection, then mirroring (FEAT-002 C13, C14, C19)
    }

    private companion object {
        const val EXPORT_CHECK_INTERVAL_MS = 2_000L
    }
}
