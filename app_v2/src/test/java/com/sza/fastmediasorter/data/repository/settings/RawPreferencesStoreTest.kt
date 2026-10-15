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

    @Test
    fun `S4238 exchange secret families are exported by no name and restored by none`() {
        val secrets = listOf(
            "device_exchange_token_0123456789abcdef",
            "exchange_resource_pin_0123456789abcdef_fedcba9876543210",
            "exchange_grant_password_guest.one",
            "exchange_grants_v1",
            "device_exchange_account_entries"
        )
        val source = mutablePreferencesOf(label to "kept")
        secrets.forEach { source[stringPreferencesKey(it)] = "cipher-or-json" }

        assertEquals(listOf("label"), RawPreferencesStore.export(source).map { it.key })

        val target = mutablePreferencesOf()
        RawPreferencesStore.apply(
            target,
            secrets.map { BackupPreference(it, "S", "cipher-or-json") } + BackupPreference("label", "S", "kept")
        )
        assertEquals("kept", target[label])
        secrets.forEach { assertNull(target[stringPreferencesKey(it)]) }
    }

    @Test
    fun `S4238 a key that only resembles a secret family is an ordinary setting`() {
        val source = mutablePreferencesOf(stringPreferencesKey("exchange_resource_note") to "x")

        assertEquals(listOf("exchange_resource_note"), RawPreferencesStore.export(source).map { it.key })
    }
}
