package com.afede.kidago.ui.scan

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.afede.kidago.data.SyncState
import com.afede.kidago.ui.catalog.CatalogStatusStrip
import com.afede.kidago.ui.shell.AppShell
import com.afede.kidago.ui.theme.KidaGoTheme
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Escanear's cross-Feature composition (Project UX Specification, Screen Composition): strip above, surface disabled while Syncing. */
class EscanearCompositionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val sync = mutableStateOf<SyncState>(SyncState.UpToDate(Instant.now()))

    private fun show() = rule.setContent {
        KidaGoTheme {
            val state = sync.value
            AppShell(
                headerContent = { CatalogStatusStrip(state, {}) },
                scanScreen = { EscanearScreen(CaptureState.Idle, "", {}, {}, {}, syncing = state is SyncState.Syncing) },
            )
        }
    }

    @Test
    fun whileSyncingTheSurfaceIsReplacedByTheMascotAndTheUpdatingMessage() {
        sync.value = SyncState.Syncing(0.4f)
        show()
        rule.onNodeWithTag("scan-disabled").assertIsDisplayed()
        rule.onNodeWithText("La base de datos se está actualizando. Esto puede tardar unos minutos.").assertIsDisplayed()
    }

    @Test
    fun whileSyncingThereIsNoInputToTypeOrScanIntoNotMerelyADimmedOne() {
        sync.value = SyncState.Syncing(0.4f)
        show()
        rule.onNodeWithTag("scan-input").assertDoesNotExist()
        rule.onNodeWithTag("scan-hero").assertDoesNotExist()
    }

    @Test
    fun theStripSitsAboveTheSurfaceAndShowsTheProgress() {
        sync.value = SyncState.Syncing(0.4f)
        show()
        rule.onNodeWithText("Sincronizando productos", substring = true).assertIsDisplayed()
        val strip = rule.onNodeWithText("Sincronizando productos", substring = true).getUnclippedBoundsInRoot()
        val surface = rule.onNodeWithTag("scan-disabled").getUnclippedBoundsInRoot()
        assertTrue("strip above the disabled surface", strip.bottom <= surface.top)
    }

    @Test
    fun whenTheSyncEndsTheInputComesBackAndTakesFocus() {
        sync.value = SyncState.Syncing(0.9f)
        show()
        rule.onNodeWithTag("scan-input").assertDoesNotExist()

        sync.value = SyncState.UpToDate(Instant.now())
        rule.waitForIdle()
        rule.onNodeWithTag("scan-disabled").assertDoesNotExist()
        rule.onNodeWithTag("scan-input").assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun withoutASyncTheSurfaceIsTheNormalScanningOne() {
        show()
        rule.onNodeWithTag("scan-input").assertIsDisplayed()
        rule.onNodeWithTag("scan-disabled").assertDoesNotExist()
    }

    @Test
    fun theDisabledSurfaceShowsTheMascotAt83x83AndAMessageNoWiderThan248() {
        sync.value = SyncState.Syncing(0.4f)
        show()
        val mascot = rule.onNodeWithTag("scan-mascot").getUnclippedBoundsInRoot()
        assertEquals(83f, mascot.width.value, 0.5f)
        assertEquals(83f, mascot.height.value, 0.5f)
        val message = rule.onNodeWithText("La base de datos se está actualizando. Esto puede tardar unos minutos.").getUnclippedBoundsInRoot()
        assertTrue("message at most 248 wide", message.width.value <= 248.5f)
    }
}
