package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sza.fastmediasorter.domain.model.BackupPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S3130: the settings store travels as a loop over its entries, whatever the two sides know. */
class RawPreferencesStoreTest {

    private val flag = booleanPreferencesKey("flag")
    private val count = intPreferencesKey("count")
    private val stamp = longPreferencesKey("stamp")
    private val ratio = floatPreferencesKey("ratio")
    private val label = stringPreferencesKey("label")
    private val targets = stringSetPreferencesKey("targets")

    @Test
    fun `round trip restores every supported type`() {
        val source = mutablePreferencesOf(
            flag to true,
            count to 7,
            stamp to 9_000_000_000L,
            ratio to 0.25f,
            label to "text with true and 42",
            targets to setOf("a", "b")
        )

        val restored = mutablePreferencesOf()
        RawPreferencesStore.apply(restored, RawPreferencesStore.export(source))

        assertEquals(true, restored[flag])
        assertEquals(7, restored[count])
        assertEquals(9_000_000_000L, restored[stamp])
        assertEquals(0.25f, restored[ratio])
        assertEquals("text with true and 42", restored[label])
        assertEquals(setOf("a", "b"), restored[targets])
    }

    @Test
    fun `unknown key from another version is written as it came`() {
        val target = mutablePreferencesOf()

        RawPreferencesStore.apply(target, listOf(BackupPreference("setting_from_a_newer_build", "B", "true")))

        assertEquals(true, target[booleanPreferencesKey("setting_from_a_newer_build")])
    }

    @Test
    fun `key whose stored type differs is skipped`() {
        val target = mutablePreferencesOf(count to 7)

        RawPreferencesStore.apply(target, listOf(BackupPreference("count", "S", "seven")))

        assertEquals(7, target[count])
    }

    @Test
    fun `unreadable value is skipped`() {
        val target = mutablePreferencesOf()

        RawPreferencesStore.apply(target, listOf(BackupPreference("count", "I", "not a number")))

        assertNull(target[count])
    }

    @Test
    fun `installation-local key travels in neither direction`() {
        val firstRun = booleanPreferencesKey("is_player_first_run")
        val source = mutablePreferencesOf(firstRun to false)

        assertTrue(RawPreferencesStore.export(source).isEmpty())

        val target = mutablePreferencesOf(firstRun to true)
        RawPreferencesStore.apply(target, listOf(BackupPreference("is_player_first_run", "B", "false")))
        assertEquals(true, target[firstRun])
    }
}
