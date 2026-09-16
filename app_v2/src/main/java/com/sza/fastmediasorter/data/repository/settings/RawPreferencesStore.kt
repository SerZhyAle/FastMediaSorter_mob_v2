package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sza.fastmediasorter.domain.model.BackupPreference
import timber.log.Timber

/**
 * S3130: reads and writes the settings store as a loop over its stored entries, so a setting added
 * later travels in a backup without any edit here. The typed [com.sza.fastmediasorter.domain.usecase.BackupSettings]
 * section stays beside it and keeps the compatibility rules for backups taken before this existed.
 */
object RawPreferencesStore {

    private const val TYPE_BOOLEAN = "B"
    private const val TYPE_INT = "I"
    private const val TYPE_LONG = "L"
    private const val TYPE_FLOAT = "F"
    private const val TYPE_DOUBLE = "D"
    private const val TYPE_STRING = "S"
    private const val TYPE_STRING_SET = "SS"

    /** Unit separator - never part of a stored string-set member, unlike a comma or a space. */
    private const val SET_SEPARATOR = "\u001F"

    /**
     * Keys describing the state of THIS installation rather than a user choice. Restoring them on
     * another device would replay a one-shot decision the receiving install has its own answer for.
     */
    private val EXCLUDED_KEYS = setOf("is_player_first_run")

    /** Every stored entry of [preferences], excluded keys and unsupported value types apart. */
    fun export(preferences: Preferences): List<BackupPreference> =
        preferences.asMap().mapNotNull { (key, value) ->
            val name = key.name
            when {
                name in EXCLUDED_KEYS -> null
                else -> encode(value)?.let { (type, encoded) -> BackupPreference(name, type, encoded) }
            }
        }.sortedBy { it.key }

    /**
     * Writes [values] into [target] by preference name. A name the receiver does not know is written
     * as it came - the sender's newer or older setting set is the case this exists for; a name whose
     * stored type differs from the incoming one is skipped rather than written, because the reader of
     * that setting would throw on the next read.
     */
    fun apply(target: MutablePreferences, values: List<BackupPreference>) {
        val existing = target.asMap().entries.associate { it.key.name to it.value }
        values.forEach { entry ->
            if (entry.key in EXCLUDED_KEYS) {
                Timber.w("raw settings: %s excluded from restore", entry.key)
                return@forEach
            }
            val decoded = decode(entry)
            if (decoded == null) {
                Timber.w("raw settings: %s skipped, unreadable type '%s'", entry.key, entry.type)
                return@forEach
            }
            // Compare the type TAG, not the runtime class: a stored string set and a decoded one
            // are both Set yet arrive as different Set implementations.
            val stored = existing[entry.key]
            if (stored != null && encode(stored)?.first != encode(decoded)?.first) {
                Timber.w("raw settings: %s skipped, type differs from the stored value", entry.key)
                return@forEach
            }
            write(target, entry.key, decoded)
        }
    }

    private fun encode(value: Any): Pair<String, String>? = when (value) {
        is Boolean -> TYPE_BOOLEAN to value.toString()
        is Int -> TYPE_INT to value.toString()
        is Long -> TYPE_LONG to value.toString()
        is Float -> TYPE_FLOAT to value.toString()
        is Double -> TYPE_DOUBLE to value.toString()
        is String -> TYPE_STRING to value
        is Set<*> -> TYPE_STRING_SET to value.filterIsInstance<String>().joinToString(SET_SEPARATOR)
        else -> null
    }

    private fun decode(entry: BackupPreference): Any? = when (entry.type) {
        TYPE_BOOLEAN -> entry.value.toBooleanStrictOrNull()
        TYPE_INT -> entry.value.toIntOrNull()
        TYPE_LONG -> entry.value.toLongOrNull()
        TYPE_FLOAT -> entry.value.toFloatOrNull()
        TYPE_DOUBLE -> entry.value.toDoubleOrNull()
        TYPE_STRING -> entry.value
        TYPE_STRING_SET -> decodeStringSet(entry.value)
        else -> null
    }

    private fun decodeStringSet(value: String): Set<String> =
        if (value.isEmpty()) emptySet() else value.split(SET_SEPARATOR).toSet()

    private fun write(target: MutablePreferences, key: String, value: Any) {
        when (value) {
            is Boolean -> target[booleanPreferencesKey(key)] = value
            is Int -> target[intPreferencesKey(key)] = value
            is Long -> target[longPreferencesKey(key)] = value
            is Float -> target[floatPreferencesKey(key)] = value
            is Double -> target[doublePreferencesKey(key)] = value
            is String -> target[stringPreferencesKey(key)] = value
            is Set<*> -> target[stringSetPreferencesKey(key)] = value.filterIsInstance<String>().toSet()
        }
    }
}
