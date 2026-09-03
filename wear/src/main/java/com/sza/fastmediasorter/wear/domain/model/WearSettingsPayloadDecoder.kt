package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * S2462: reads a settings payload off the wire while telling apart "the peer did not send this key",
 * "the peer sent it as something else" and "the peer sent a key this build does not know".
 *
 * A model rather than a use case, on the precedent of [WearSettingsMergeResolver]: it holds a policy
 * and touches nothing outside its own arguments, so Rule 6's `*UseCase` suffix would name it something
 * it is not.
 *
 * Mirrored verbatim from the phone copy in
 * `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSettingsPayloadDecoder.kt`, including
 * the order of the contract table below - the two modules share no source artifact, so a table that
 * differed between the sides would make the two ends disagree about which payloads are well formed.
 *
 * Two properties of the shipped exchange make this class necessary, both measured on 2026-09-03 and
 * written up in `PLAN/S2462_wear-companion-version-skew-sync/research/01__wire-protocol-skew-behaviour.md`:
 *
 *  - Deserializing straight into [WearSettingsPayload] cannot report absence. Gson constructs the class
 *    reflectively without running the Kotlin constructor, so an omitted `audioEnabled` arrives as the
 *    JVM default `false` and is indistinguishable from a peer that switched audio off.
 *  - A single key of the wrong type aborts the whole payload. `Gson.fromJson` throws for the entire
 *    object, and the caller's outer catch then drops the event, so one incompatible field silences the
 *    channel between two versions rather than costing only itself.
 *
 * This class lives under `domain.model` so it inherits the existing
 * `-keep class com.sza.fastmediasorter.wear.domain.model.** { *; }` rule in `wear/proguard-rules.pro`;
 * a different package would let R8 rename the very field names the wire contract is built on.
 */
class WearSettingsPayloadDecoder(private val gson: Gson = Gson()) {

    /** The JSON shape one contract key must arrive as. */
    private enum class JsonKind { BOOLEAN, NUMBER, STRING, OBJECT }

    /**
     * Reads [json] and reports what of it is usable.
     *
     * Never throws: a payload that is not a JSON object at all comes back with a null payload and an
     * empty [WearSettingsDecodeResult.presentFields], which every consumer already reads as "apply
     * nothing".
     */
    fun decode(json: String): WearSettingsDecodeResult {
        val root = runCatching { JsonParser.parseString(json) }.getOrNull()
        if (root == null || !root.isJsonObject) {
            return WearSettingsDecodeResult(
                payload = null,
                presentFields = emptySet(),
                divergences = listOf(WearSettingsDivergence(ROOT, WearSettingsFieldIssue.WRONG_TYPE))
            )
        }
        return decodeObject(root.asJsonObject)
    }

    private fun decodeObject(incoming: JsonObject): WearSettingsDecodeResult {
        val accepted = JsonObject()
        val present = linkedSetOf<String>()
        val divergences = mutableListOf<WearSettingsDivergence>()
        EXPECTED.forEach { (name, kind) ->
            val element = incoming.get(name)
            when {
                element == null || element.isJsonNull ->
                    divergences += WearSettingsDivergence(name, WearSettingsFieldIssue.MISSING)
                matches(element, kind) -> {
                    accepted.add(name, element)
                    present += name
                }
                else -> divergences += WearSettingsDivergence(name, WearSettingsFieldIssue.WRONG_TYPE)
            }
        }
        incoming.keySet()
            .filterNot { EXPECTED.containsKey(it) }
            .forEach { divergences += WearSettingsDivergence(it, WearSettingsFieldIssue.UNKNOWN_KEY) }
        val payload = runCatching { gson.fromJson(accepted, WearSettingsPayload::class.java) }.getOrNull()
        // A subset that still fails to deserialize leaves nothing trustworthy behind, so the present
        // set is dropped with it rather than pointing at fields of an object that does not exist.
        return if (payload == null) {
            val rootIssue = WearSettingsDivergence(ROOT, WearSettingsFieldIssue.WRONG_TYPE)
            WearSettingsDecodeResult(null, emptySet(), divergences + rootIssue)
        } else {
            WearSettingsDecodeResult(payload, present, divergences)
        }
    }

    private fun matches(element: JsonElement, kind: JsonKind): Boolean = when (kind) {
        JsonKind.OBJECT -> element.isJsonObject
        JsonKind.BOOLEAN -> element.isJsonPrimitive && element.asJsonPrimitive.isBoolean
        JsonKind.NUMBER -> element.isJsonPrimitive && element.asJsonPrimitive.isNumber
        JsonKind.STRING -> element.isJsonPrimitive && element.asJsonPrimitive.isString
    }

    companion object {
        /** Stands for the payload as a whole when the failure is not attributable to one key. */
        const val ROOT = "<root>"

        /**
         * The wire contract of the settings exchange: every key [WearSettingsPayload] carries, and the
         * JSON kind it must arrive as.
         *
         * This table IS the contract. A field added to [WearSettingsPayload] without a row here is
         * reported as [WearSettingsFieldIssue.UNKNOWN_KEY] and dropped, and the phone copy of this file
         * must gain the same row in the same change - the two modules share no artifact.
         */
        private val EXPECTED: Map<String, JsonKind> = linkedMapOf(
            "audioEnabled" to JsonKind.BOOLEAN,
            "videoEnabled" to JsonKind.BOOLEAN,
            "imagesEnabled" to JsonKind.BOOLEAN,
            "slideshowEnabled" to JsonKind.BOOLEAN,
            "slideshowIntervalSeconds" to JsonKind.NUMBER,
            "downloadAlbumArt" to JsonKind.BOOLEAN,
            "viewMode" to JsonKind.STRING,
            "keepScreenAwakeOutsidePlayers" to JsonKind.BOOLEAN,
            "fileListViewMode" to JsonKind.STRING,
            "appLanguage" to JsonKind.STRING,
            "backgroundMode" to JsonKind.STRING,
            "streamsSectionEnabled" to JsonKind.BOOLEAN,
            "documentsEnabled" to JsonKind.BOOLEAN,
            "disableAnimations" to JsonKind.BOOLEAN,
            "backgroundPlaybackEnabled" to JsonKind.BOOLEAN,
            // S2461, added 2026-09-03 while this ticket was in flight: metadata about the sender, not a
            // setting. It is listed here for the reason the KDoc above gives - a key missing from this
            // table is stripped as unknown, so omitting it would have made the field decode as null and
            // silently broken the version display it exists for.
            "appVersionName" to JsonKind.STRING,
            "fieldTimestamps" to JsonKind.OBJECT,
            "capabilities" to JsonKind.OBJECT
        )

        /** Every contract key, for a caller that has no decode result and must assume the old behaviour. */
        val CONTRACT_FIELDS: Set<String> = EXPECTED.keys
    }
}
