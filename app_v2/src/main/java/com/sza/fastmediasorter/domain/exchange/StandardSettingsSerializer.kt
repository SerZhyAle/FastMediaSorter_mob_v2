package com.sza.fastmediasorter.domain.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.model.AppSettings
import timber.log.Timber

/**
 * Serializer and merger for standard application settings exchanged with Media Exchange Server per DEVICE-EXCHANGE
 * PROPOSAL-2026-11-09-app-settings-exchange-standard.
 */
object StandardSettingsSerializer {

    const val GROUP_GENERAL = "general"
    const val GROUP_APPEARANCE = "appearance"
    const val GROUP_PLAYBACK = "playback"
    const val GROUP_SHORTCUTS = "shortcuts"
    const val GROUP_BEHAVIOR = "behavior"

    val ALL_GROUPS: Set<String> = setOf(
        GROUP_GENERAL,
        GROUP_APPEARANCE,
        GROUP_PLAYBACK,
        GROUP_SHORTCUTS,
        GROUP_BEHAVIOR
    )

    /**
     * Mandatory local exclusions that must NEVER be sent to the exchange server or overwritten.
     * Conforms to vector 07-local-exclusion-manifest.json.
     */
    val MANDATORY_LOCAL_EXCLUSIONS: Set<String> = setOf(
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
        "defaultUser",
        "defaultPassword",
        "lastUsedResourceId"
    )

    private val EXCLUDED_PREFIXES = listOf(
        "device_exchange_token_",
        "exchange_resource_pin_",
        "exchange_grant_password_",
        "network_credential_",
        "auth_session_"
    )

    fun isMandatoryExcluded(key: String): Boolean {
        if (key in MANDATORY_LOCAL_EXCLUSIONS) return true
        return EXCLUDED_PREFIXES.any { key.startsWith(it) }
    }

    /**
     * Serializes [settings] into a standard JSON payload formatVersion 1.
     */
    fun serialize(settings: AppSettings, selectedGroups: Set<String> = ALL_GROUPS): String {
        val root = JsonObject().apply {
            addProperty("version", 1)
        }
        val groupsObject = JsonObject()

        if (GROUP_GENERAL in selectedGroups) {
            groupsObject.add(GROUP_GENERAL, serializeGeneral(settings))
        }
        if (GROUP_APPEARANCE in selectedGroups) {
            groupsObject.add(GROUP_APPEARANCE, serializeAppearance(settings))
        }
        if (GROUP_PLAYBACK in selectedGroups) {
            groupsObject.add(GROUP_PLAYBACK, serializePlayback(settings))
        }
        if (GROUP_SHORTCUTS in selectedGroups) {
            groupsObject.add(GROUP_SHORTCUTS, serializeShortcuts(settings))
        }
        if (GROUP_BEHAVIOR in selectedGroups) {
            groupsObject.add(GROUP_BEHAVIOR, serializeBehavior(settings))
        }

        root.add("groups", groupsObject)
        return root.toString()
    }

    private fun serializeGeneral(settings: AppSettings): JsonObject = JsonObject().apply {
        addProperty("language", settings.language)
        addProperty("isResourceGridMode", settings.isResourceGridMode)
        addProperty("isResourceTableMode", settings.isResourceTableMode)
        addProperty("disableAnimations", settings.disableAnimations)
        addProperty("preventSleep", settings.preventSleep)
        addProperty("keepScreenOnPlayer", settings.keepScreenOnPlayer)
        addProperty("showSmallControls", settings.showSmallControls)
    }

    private fun serializeAppearance(settings: AppSettings): JsonObject = JsonObject().apply {
        addProperty("colorTheme", settings.colorTheme)
        addProperty("resourceOpsInOverflowMenu", settings.resourceOpsInOverflowMenu)
        addProperty("showProgramsPanelInMainWindow", settings.showProgramsPanelInMainWindow)
    }

    private fun serializePlayback(settings: AppSettings): JsonObject = JsonObject().apply {
        addProperty("slideshowIntervalSeconds", settings.slideshowInterval)
        addProperty("openVideoInFullscreen", settings.openVideoInFullscreen)
        addProperty("showVideoThumbnails", settings.showVideoThumbnails)
    }

    private fun serializeShortcuts(settings: AppSettings): JsonObject = JsonObject().apply {
        addProperty("stopwatchVolumeKeysControl", settings.stopwatchVolumeKeysControl)
    }

    private fun serializeBehavior(settings: AppSettings): JsonObject = JsonObject().apply {
        addProperty("enableCalculator", settings.enableCalculator)
        addProperty("enableStopwatch", settings.enableStopwatch)
        addProperty("embeddedGameEnabled", settings.embeddedGameEnabled)
        addProperty("enableSos", settings.enableSos)
        addProperty("enableFiller", settings.enableFiller)
        addProperty("mirrorEnabled", settings.mirrorEnabled)
    }

    /**
     * Deserializes [payloadJson] and merges enabled [selectedGroups] over [settings].
     */
    fun deserializeAndApply(
        settings: AppSettings,
        payloadJson: String,
        selectedGroups: Set<String> = ALL_GROUPS
    ): AppSettings {
        return try {
            val root = JsonParser.parseString(payloadJson).asJsonObject
            val groups = root.getAsJsonObject("groups") ?: return settings
            var updated = settings

            if (GROUP_GENERAL in selectedGroups && groups.has(GROUP_GENERAL)) {
                updated = applyGeneral(updated, groups.getAsJsonObject(GROUP_GENERAL))
            }
            if (GROUP_APPEARANCE in selectedGroups && groups.has(GROUP_APPEARANCE)) {
                updated = applyAppearance(updated, groups.getAsJsonObject(GROUP_APPEARANCE))
            }
            if (GROUP_PLAYBACK in selectedGroups && groups.has(GROUP_PLAYBACK)) {
                updated = applyPlayback(updated, groups.getAsJsonObject(GROUP_PLAYBACK))
            }
            if (GROUP_SHORTCUTS in selectedGroups && groups.has(GROUP_SHORTCUTS)) {
                updated = applyShortcuts(updated, groups.getAsJsonObject(GROUP_SHORTCUTS))
            }
            if (GROUP_BEHAVIOR in selectedGroups && groups.has(GROUP_BEHAVIOR)) {
                updated = applyBehavior(updated, groups.getAsJsonObject(GROUP_BEHAVIOR))
            }

            updated
        } catch (e: JsonParseException) {
            Timber.w(e, "StandardSettingsSerializer: invalid json payload")
            settings
        } catch (e: IllegalStateException) {
            Timber.w(e, "StandardSettingsSerializer: unexpected json shape")
            settings
        } catch (e: ClassCastException) {
            Timber.w(e, "StandardSettingsSerializer: unexpected type casting")
            settings
        }
    }

    private fun applyGeneral(settings: AppSettings, g: JsonObject): AppSettings = settings.copy(
        language = g.stringOrNull("language") ?: settings.language,
        isResourceGridMode = g.boolOrNull("isResourceGridMode") ?: settings.isResourceGridMode,
        isResourceTableMode = g.boolOrNull("isResourceTableMode") ?: settings.isResourceTableMode,
        disableAnimations = g.boolOrNull("disableAnimations") ?: settings.disableAnimations,
        preventSleep = g.boolOrNull("preventSleep") ?: settings.preventSleep,
        keepScreenOnPlayer = g.boolOrNull("keepScreenOnPlayer") ?: settings.keepScreenOnPlayer,
        showSmallControls = g.boolOrNull("showSmallControls") ?: settings.showSmallControls
    )

    private fun applyAppearance(settings: AppSettings, a: JsonObject): AppSettings = settings.copy(
        colorTheme = a.stringOrNull("colorTheme") ?: settings.colorTheme,
        resourceOpsInOverflowMenu = a.boolOrNull("resourceOpsInOverflowMenu")
            ?: settings.resourceOpsInOverflowMenu
    ).withPrograms {
        copy(
            showProgramsPanelInMainWindow = a.boolOrNull("showProgramsPanelInMainWindow")
                ?: settings.showProgramsPanelInMainWindow
        )
    }

    private fun applyPlayback(settings: AppSettings, p: JsonObject): AppSettings = settings.copy(
        slideshowInterval = p.intOrNull("slideshowIntervalSeconds") ?: settings.slideshowInterval,
        openVideoInFullscreen = p.boolOrNull("openVideoInFullscreen") ?: settings.openVideoInFullscreen,
        showVideoThumbnails = p.boolOrNull("showVideoThumbnails") ?: settings.showVideoThumbnails
    )

    private fun applyShortcuts(settings: AppSettings, s: JsonObject): AppSettings = settings.copy(
        stopwatchVolumeKeysControl = s.boolOrNull("stopwatchVolumeKeysControl")
            ?: settings.stopwatchVolumeKeysControl
    )

    private fun applyBehavior(settings: AppSettings, b: JsonObject): AppSettings = settings.withPrograms {
        copy(
            enableCalculator = b.boolOrNull("enableCalculator") ?: settings.enableCalculator,
            enableStopwatch = b.boolOrNull("enableStopwatch") ?: settings.enableStopwatch,
            embeddedGameEnabled = b.boolOrNull("embeddedGameEnabled") ?: settings.embeddedGameEnabled,
            enableSos = b.boolOrNull("enableSos") ?: settings.enableSos,
            enableFiller = b.boolOrNull("enableFiller") ?: settings.enableFiller,
            mirrorEnabled = b.boolOrNull("mirrorEnabled") ?: settings.mirrorEnabled
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asString

    private fun JsonObject.boolOrNull(key: String): Boolean? =
        get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asBoolean

    private fun JsonObject.intOrNull(key: String): Int? =
        get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asInt
}
