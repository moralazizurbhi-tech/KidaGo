package com.afede.kidago.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefsName = "settings-test"
    private val default = "/storage/emulated/0/afede/kidago/"

    private fun store() = SettingsStore(context, default, prefsName)

    @Before
    @After
    fun clean() {
        context.deleteSharedPreferences(prefsName)
    }

    @Test
    fun defaultsHoldBeforeAnyWrite() {
        assertEquals(Settings(true, true, default), store().settings.value)
    }

    @Test
    fun realDefaultFolderIsSharedRootPlusAfedeKidago() {
        assertTrue(SettingsStore.defaultCatalogFolder().endsWith("/afede/kidago/"))
    }

    @Test
    fun flagsAreIndependent() {
        val s = store()
        s.setSoundEnabled(false)
        assertEquals(Settings(false, true, default), s.settings.value)
        s.setSoundEnabled(true)
        s.setVibrationEnabled(false)
        assertEquals(Settings(true, false, default), s.settings.value)
    }

    @Test
    fun valuesSurviveRestart() {
        store().apply {
            setSoundEnabled(false)
            setVibrationEnabled(false)
            setCatalogFolder("/storage/emulated/0/other/")
        }
        assertEquals(Settings(false, false, "/storage/emulated/0/other/"), store().settings.value)
    }

    @Test
    fun emptyFolderIsRejectedAndKeepsPreviousValue() {
        val s = store()
        assertFalse(s.setCatalogFolder(""))
        assertFalse(s.setCatalogFolder("   "))
        assertEquals(default, s.settings.value.catalogFolder)
        assertTrue(s.setCatalogFolder("/x/"))
        assertFalse(s.setCatalogFolder(""))
        assertEquals("/x/", store().settings.value.catalogFolder)
    }
}
