package com.sza.fastmediasorter.data.preset

import android.content.Context
import com.sza.fastmediasorter.data.model.DeviceProfileType
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the owner-authored device-profile preset matrix shipped as an asset
 * (`assets/device_profile_presets.csv`) and exposes it as a per-profile map of
 * field-name → override-value, keeping ONLY the non-empty cells.
 *
 * CSV layout: the first column header is `option` (an [com.sza.fastmediasorter.domain.model.AppSettings]
 * field name); each remaining column header is a profile key. One data row per AppSettings field;
 * each cell is the override value for that profile, an empty cell means "no override".
 *
 * The parse is performed once and cached for the process lifetime — the asset is immutable at runtime.
 */
@Singleton
class DeviceProfilePresetCsvDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val ASSET_PATH = "device_profile_presets.csv"

        /** Maps a CSV profile-column header to its [DeviceProfileType]. */
        private val COLUMN_TO_PROFILE: Map<String, DeviceProfileType> = mapOf(
            "personal_smartphone" to DeviceProfileType.PERSONAL_SMARTPHONE,
            "home_tablet" to DeviceProfileType.HOME_TABLET,
            "tv_media_box" to DeviceProfileType.TV_MEDIA_BOX,
            "car_head_unit" to DeviceProfileType.CAR_HEAD_UNIT,
            "media_player" to DeviceProfileType.MEDIA_PLAYER,
            "photo_frame" to DeviceProfileType.PHOTO_FRAME,
            "video_player" to DeviceProfileType.VIDEO_PLAYER,
            "audio_player" to DeviceProfileType.AUDIO_PLAYER,
            "ebook_reader" to DeviceProfileType.EBOOK_READER,
            "vr_headset" to DeviceProfileType.VR_HEADSET,
            "Other" to DeviceProfileType.OTHER
        )
    }

    // Cached parse result; computed lazily on first access (thread-safe via lazy default mode).
    private val parsed: Map<DeviceProfileType, Map<String, String>> by lazy { parse() }

    /**
     * Returns the per-profile override map. Each inner map holds only non-empty cells,
     * keyed by the AppSettings field name. Profiles with no overrides map to an empty map.
     */
    fun load(): Map<DeviceProfileType, Map<String, String>> = parsed

    private fun parse(): Map<DeviceProfileType, Map<String, String>> {
        val result = linkedMapOf<DeviceProfileType, MutableMap<String, String>>()
        DeviceProfileType.entries.forEach { result[it] = linkedMapOf() }

        try {
            context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).useLines { lines ->
                val iterator = lines.iterator()
                if (!iterator.hasNext()) {
                    Timber.w("Device profile preset CSV is empty: $ASSET_PATH")
                    return result
                }

                // Header row: column index → DeviceProfileType (skip the leading `option` column).
                val header = splitCsvLine(iterator.next())
                val columnProfiles = arrayOfNulls<DeviceProfileType>(header.size)
                for (i in 1 until header.size) {
                    columnProfiles[i] = COLUMN_TO_PROFILE[header[i].trim()]
                }

                while (iterator.hasNext()) {
                    val cells = splitCsvLine(iterator.next())
                    if (cells.isEmpty()) continue
                    val field = cells[0].trim()
                    if (field.isEmpty()) continue

                    for (i in 1 until cells.size) {
                        val profile = columnProfiles.getOrNull(i) ?: continue
                        val raw = cells[i].trim()
                        if (raw.isNotEmpty()) {
                            result.getValue(profile)[field] = raw
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Asset missing or unreadable — return empty overrides so callers fall back to defaults.
            Timber.e(e, "Failed to read device profile preset CSV: $ASSET_PATH")
        }

        return result
    }

    /**
     * Splits a single CSV line into fields (RFC 4180-ish). Handles both quoted and unquoted exports:
     * surrounding double quotes are stripped, a doubled quote inside a quoted field (`""`) becomes a
     * literal `"`, commas inside quotes are preserved, and trailing empty cells are kept. Spreadsheet
     * exports (Excel / Google Sheets) quote every field, so plain splitting on commas is not enough.
     */
    private fun splitCsvLine(line: String): List<String> {
        // Strip a UTF-8 BOM that spreadsheet exports prepend to the first line, and a trailing CR.
        val clean = line.removePrefix("﻿").trimEnd('\r')
        val fields = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < clean.length) {
            val c = clean[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < clean.length && clean[i + 1] == '"' -> { sb.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> sb.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { fields.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        fields.add(sb.toString())
        return fields
    }
}
