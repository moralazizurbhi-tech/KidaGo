package com.afede.kidago.ui.catalog

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import com.afede.kidago.data.RecordDiagnostic
import com.afede.kidago.data.SyncState
import com.afede.kidago.ui.shell.AppShell
import com.afede.kidago.ui.theme.KidaGoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CatalogStatusStripTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val state = mutableStateOf<SyncState>(SyncState.UpToDate(null))
    private var padlockTaps = 0
    private val zone = ZoneId.of("UTC")

    private fun show() = rule.setContent {
        KidaGoTheme { AppShell(headerContent = { CatalogStatusStrip(state.value, { padlockTaps++ }, zone = zone) }) }
    }

    private val error = SyncState.Error(listOf(RecordDiagnostic(7, "12345", "debe tener exactamente 13 dígitos, tiene 5")), 3)

    @Test
    fun upToDateShowsDotAndTodaysTimeWithNoTapAction() {
        state.value = SyncState.UpToDate(Instant.now())
        show()
        rule.onNodeWithText("Datos actualizados - hoy ", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Actualizado").assertIsDisplayed()
        rule.onNodeWithTag("strip-uptodate").performClick() // nothing happens
        rule.onNodeWithTag("strip-error-detail").assertDoesNotExist()
        assertEquals(0, padlockTaps)
    }

    @Test
    fun upToDateBeforeAnyImportShowsNoTime() {
        show()
        rule.onNodeWithText("Datos actualizados").assertIsDisplayed()
    }

    @Test
    fun syncingShowsSpinnerPercentAndProgressBar() {
        state.value = SyncState.Syncing(0.42f)
        show()
        rule.onNodeWithText("Sincronizando productos... 42%").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sincronizando").assertIsDisplayed()
        rule.onNodeWithTag("strip-progress").assertIsDisplayed()
        state.value = SyncState.Syncing(0.9f)
        rule.onNodeWithText("Sincronizando productos... 90%").assertIsDisplayed()
    }

    @Test
    fun errorExpandsInPlaceOnTapAndCollapsesOnTapAgain() {
        state.value = error
        show()
        rule.onNodeWithText("Error al sincronizar - toca para ver detalles").assertIsDisplayed()
        rule.onNodeWithContentDescription("Error").assertIsDisplayed()
        rule.onNodeWithTag("strip-error-detail").assertDoesNotExist()

        val before = rule.onNodeWithTag("screen-scan").getUnclippedBoundsInRoot().top
        rule.onNodeWithTag("strip-error-row").performClick()
        rule.onNodeWithTag("strip-error-detail").assertIsDisplayed()
        rule.onNodeWithText("Puede que tengas que volver a hacer Importar.").assertIsDisplayed()
        rule.onNodeWithText("Registro 7: «12345» - debe tener exactamente 13 dígitos, tiene 5").assertIsDisplayed()
        rule.onNodeWithText("y 2 más").assertIsDisplayed()
        assertTrue("content below is pushed down", rule.onNodeWithTag("screen-scan").getUnclippedBoundsInRoot().top > before)

        rule.onNodeWithTag("strip-error-row").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("strip-error-detail").assertDoesNotExist()
    }

    @Test
    fun noFileAccessShowsPadlockAndTapOpensSettingsEachTime() {
        state.value = SyncState.NoFileAccess
        show()
        rule.onNodeWithText("Falta el permiso de archivos - toca para concederlo").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sin permiso de archivos").assertIsDisplayed()
        rule.onNodeWithTag("strip-noaccess").performClick()
        rule.onNodeWithTag("strip-noaccess").performClick()
        assertEquals(2, padlockTaps)
    }

    @Test
    fun eachStateIsDistinctByIcon() {
        val icons = listOf(
            SyncState.UpToDate(null) to "Actualizado",
            SyncState.Syncing(0.1f) to "Sincronizando",
            error to "Error",
            SyncState.NoFileAccess to "Sin permiso de archivos",
        )
        show()
        icons.forEach { (s, description) ->
            state.value = s
            rule.waitForIdle()
            rule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
    }

    @Test
    fun stripShowsOnAllThreeScreensAndTheWarningNeverBlocksNavigation() {
        state.value = SyncState.NoFileAccess
        show()
        rule.onNodeWithTag("strip-noaccess").assertIsDisplayed()
        rule.onNodeWithText("Productos").performClick()
        rule.onNodeWithTag("screen-products").assertIsDisplayed()
        rule.onNodeWithTag("strip-noaccess").assertIsDisplayed()
        rule.onNodeWithContentDescription("Ajustes").performClick()
        rule.onNodeWithTag("screen-settings").assertIsDisplayed()
        rule.onNodeWithTag("strip-noaccess").assertIsDisplayed()
    }
}
