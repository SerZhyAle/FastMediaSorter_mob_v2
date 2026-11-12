package com.sza.fastmediasorter.domain.exchange

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The local-isolation half of contract DEVICE-EXCHANGE proposal app-settings-exchange-standard: vector 07 lists
 * the keys that never leave the device and the groups that may, and a read must change only the selected groups.
 */
class StandardSettingsSerializerTest {

    @Test
    fun `every vector 07 exclusion is a mandatory local exclusion`() {
        VECTOR_07_EXCLUSIONS.forEach { key ->
            assertTrue(key, StandardSettingsSerializer.isMandatoryExcluded(key))
        }
    }

    @Test
    fun `prefixed device secrets are excluded`() {
        listOf(
            "device_exchange_token_abc",
            "exchange_resource_pin_42",
            "exchange_grant_password_7",
            "network_credential_smb",
            "auth_session_1",
        ).forEach { key -> assertTrue(key, StandardSettingsSerializer.isMandatoryExcluded(key)) }
        assertFalse(StandardSettingsSerializer.isMandatoryExcluded("colorTheme"))
    }

    @Test
    fun `exchangeable groups match vector 07`() {
        assertEquals(VECTOR_07_GROUPS, StandardSettingsSerializer.ALL_GROUPS)
    }

    @Test
    fun `serialized payload carries no excluded key at any depth`() {
        val payload = JsonParser.parseString(StandardSettingsSerializer.serialize(AppSettings())).asJsonObject

        collectKeys(payload).forEach { key ->
            assertFalse(key, StandardSettingsSerializer.isMandatoryExcluded(key))
        }
        assertEquals(1, payload.get("version").asInt)
        assertEquals(VECTOR_07_GROUPS, payload.getAsJsonObject("groups").keySet())
    }

    @Test
    fun `serialize writes only the selected groups`() {
        val selected = setOf(StandardSettingsSerializer.GROUP_APPEARANCE, StandardSettingsSerializer.GROUP_PLAYBACK)
        val payload = JsonParser.parseString(StandardSettingsSerializer.serialize(AppSettings(), selected))
            .asJsonObject

        assertEquals(selected, payload.getAsJsonObject("groups").keySet())
    }

    @Test
    fun `round trip restores every exchangeable value`() {
        val source = changedSettings()
        val payload = StandardSettingsSerializer.serialize(source)

        val applied = StandardSettingsSerializer.deserializeAndApply(AppSettings(), payload)

        assertEquals(source, applied)
    }

    @Test
    fun `apply leaves unselected groups untouched`() {
        val payload = StandardSettingsSerializer.serialize(changedSettings())
        val local = AppSettings()

        val applied = StandardSettingsSerializer.deserializeAndApply(
            local,
            payload,
            setOf(StandardSettingsSerializer.GROUP_APPEARANCE)
        )

        assertEquals(CHANGED_THEME, applied.colorTheme)
        assertEquals(local.language, applied.language)
        assertEquals(local.slideshowInterval, applied.slideshowInterval)
        assertEquals(local.enableCalculator, applied.enableCalculator)
        assertEquals(local.stopwatchVolumeKeysControl, applied.stopwatchVolumeKeysControl)
    }

    @Test
    fun `excluded keys in a server payload never reach local settings`() {
        val local = AppSettings()
        val hostile = """{"version":1,"groups":{"general":{"defaultUser":"intruder",""" +
            """"defaultPassword":"secret","lastUsedResourceId":99,"language":"$CHANGED_LANGUAGE"}}}"""

        val applied = StandardSettingsSerializer.deserializeAndApply(local, hostile)

        assertEquals(local.copy(language = CHANGED_LANGUAGE), applied)
    }

    @Test
    fun `malformed payload keeps local settings`() {
        val local = changedSettings()

        assertEquals(local, StandardSettingsSerializer.deserializeAndApply(local, "not json"))
        assertEquals(local, StandardSettingsSerializer.deserializeAndApply(local, """{"version":1}"""))
    }

    private fun collectKeys(json: JsonObject): List<String> = json.entrySet().flatMap { (key, value) ->
        listOf(key) + if (value.isJsonObject) collectKeys(value.asJsonObject) else emptyList()
    }

    private fun changedSettings(): AppSettings {
        val defaults = AppSettings()
        return defaults.copy(
            language = CHANGED_LANGUAGE,
            isResourceGridMode = !defaults.isResourceGridMode,
            disableAnimations = !defaults.disableAnimations,
            colorTheme = CHANGED_THEME,
            slideshowInterval = CHANGED_INTERVAL,
            openVideoInFullscreen = !defaults.openVideoInFullscreen,
            stopwatchVolumeKeysControl = !defaults.stopwatchVolumeKeysControl,
        ).withPrograms {
            copy(
                showProgramsPanelInMainWindow = !defaults.showProgramsPanelInMainWindow,
                enableCalculator = !defaults.enableCalculator,
                mirrorEnabled = !defaults.mirrorEnabled,
            )
        }
    }

    private companion object {
        const val CHANGED_LANGUAGE = "uk"
        const val CHANGED_THEME = "DARK"
        const val CHANGED_INTERVAL = 42

        val VECTOR_07_EXCLUSIONS = listOf(
            "device_key",
            "private_key",
            "auth_tokens",
            "passwords",
            "hardware_paths",
            "drive_letters",
            "port_bindings",
            "loopback_ip",
            "window_bounds",
            "window_state",
            "screen_dpi",
        )

        val VECTOR_07_GROUPS = setOf("general", "appearance", "playback", "shortcuts", "behavior")
    }
}
