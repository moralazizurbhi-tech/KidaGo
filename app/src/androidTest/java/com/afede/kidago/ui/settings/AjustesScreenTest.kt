package com.afede.kidago.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.afede.kidago.data.AppDatabase
import com.afede.kidago.data.ArchiveReason
import com.afede.kidago.data.SettingsStore
import com.afede.kidago.data.TodaysListEntryStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import com.afede.kidago.ui.theme.KidaGoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Ajustes against the real SettingsStore (SharedPreferences) and a real ViewModel, on the device. */
class AjustesScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefsName = "ajustes-test"
    private val defaultFolder = "/storage/test/afede/kidago/"
    private fun newStore() = SettingsStore(context, defaultFolder, prefsName)
    private lateinit var store: SettingsStore
    private lateinit var db: AppDatabase
    private lateinit var list: TodaysListEntryStore

    @Before
    fun setUp() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        store = newStore()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        list = TodaysListEntryStore(db)
        rule.setContent {
            KidaGoTheme {
                val vm = viewModel { AjustesViewModel(store.settings, store::setSoundEnabled, store::setVibrationEnabled, store::setCatalogFolder, "9.8.7") { list.archiveAndClear(ArchiveReason.MANUAL) } }
                val state by vm.state.collectAsState()
                AjustesScreen(state, vm::onSoundToggle, vm::onVibrationToggle, vm::onFolderSubmit, vm.confirmation, vm.clear::onClearTapped)
            }
        }
    }

    @After
    fun tearDown() = db.close()

    private fun scan(vararg codes: String) = runBlocking { codes.forEach { list.addOrAccumulate(it) } }
    private fun entries() = runBlocking { db.todaysListDao().entries() }
    private fun history() = runBlocking { list.history() }
    private fun askToClear() {
        rule.onNodeWithTag("ajustes-clear").performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test
    fun showsDefaultsVersionAndFolder() {
        rule.onNodeWithTag("ajustes-sound").assertIsOn()
        rule.onNodeWithTag("ajustes-vibration").assertIsOn()
        rule.onNodeWithTag("ajustes-folder").assertTextEquals(defaultFolder)
        rule.onNodeWithTag("ajustes-version").assertTextEquals("KidaGo v9.8.7")
        rule.onNodeWithText("Utilizar alertas de sonido al escanear").assertIsDisplayed()
        rule.onNodeWithText("Utilizar alertas de vibración al escanear").assertIsDisplayed()
    }

    @Test
    fun toggleWritesImmediatelyAndOnlyItsOwnFlag() {
        rule.onNodeWithTag("ajustes-sound").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ajustes-sound").assertIsOff()
        rule.onNodeWithTag("ajustes-vibration").assertIsOn()
        assertFalse(newStore().settings.value.soundEnabled) // already durable: a fresh store reads it back
        assertEquals(true, newStore().settings.value.vibrationEnabled)

        rule.onNodeWithTag("ajustes-vibration").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ajustes-sound").assertIsOff()
        rule.onNodeWithTag("ajustes-vibration").assertIsOff()

        rule.onNodeWithTag("ajustes-sound").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ajustes-sound").assertIsOn()
        rule.onNodeWithTag("ajustes-vibration").assertIsOff()
    }

    @Test
    fun submittedFolderIsSavedAndShown() {
        rule.onNodeWithTag("ajustes-folder").performTextReplacement("/storage/emulated/0/otra/")
        rule.onNodeWithTag("ajustes-folder").performImeAction()
        rule.waitForIdle()
        assertEquals("/storage/emulated/0/otra/", newStore().settings.value.catalogFolder)
        rule.onNodeWithTag("ajustes-folder").assertTextEquals("/storage/emulated/0/otra/")
    }

    @Test
    fun emptyFolderIsRejectedAndThePreviousOneStays() {
        rule.onNodeWithTag("ajustes-folder").performTextReplacement("")
        rule.onNodeWithTag("ajustes-folder").performImeAction()
        rule.waitForIdle()
        assertEquals(defaultFolder, newStore().settings.value.catalogFolder)
        rule.onNodeWithTag("ajustes-folder").assertTextEquals(defaultFolder)
    }

    @Test
    fun clearOnlyAsksFirstAndWritesNothing() {
        scan("1111111111111", "2222222222222")
        askToClear()
        rule.onNodeWithText("¿Vaciar la lista actual?").assertIsDisplayed()
        rule.onNodeWithText("Vaciar").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").assertIsDisplayed()
        assertEquals(2, entries().size)
        assertTrue(history().isEmpty())
    }

    @Test
    fun confirmArchivesTheWholeListThenEmptiesIt() {
        scan("1111111111111", "2222222222222", "2222222222222", "3333333333333", "4444444444444")
        askToClear()
        rule.onNodeWithText("Vaciar").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("¿Vaciar la lista actual?").assertDoesNotExist()
        assertTrue(entries().isEmpty())
        val record = history().single()
        assertEquals(ArchiveReason.MANUAL, record.reason)
        assertEquals(4, runBlocking { list.historyEntries(record.id) }.size)
    }

    @Test
    fun declineLeavesTheListUnchanged() {
        scan("1111111111111", "1111111111111")
        askToClear()
        rule.onNodeWithText("Cancelar").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("¿Vaciar la lista actual?").assertDoesNotExist()
        assertEquals(2, entries().single().quantity)
        assertTrue(history().isEmpty())
    }

    @Test
    fun clearingAnEmptyListIsANoOp() {
        askToClear()
        rule.onNodeWithText("Vaciar").performClick()
        rule.waitForIdle()
        assertTrue(entries().isEmpty())
        assertTrue(history().isEmpty())
    }

    @Test
    fun noOtherActionProceedsWhileTheClearIsPending() {
        askToClear()
        rule.onNodeWithTag("ajustes-sound").performClick() // the prompt covers it; the ViewModel also refuses
        rule.waitForIdle()
        assertTrue(newStore().settings.value.soundEnabled)
        rule.onNodeWithText("Cancelar").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ajustes-sound").performClick()
        rule.waitForIdle()
        assertFalse(newStore().settings.value.soundEnabled) // works again after the answer
    }
}
