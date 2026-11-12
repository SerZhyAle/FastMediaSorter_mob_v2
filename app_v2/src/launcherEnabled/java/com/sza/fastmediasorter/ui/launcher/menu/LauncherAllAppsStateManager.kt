package com.sza.fastmediasorter.ui.launcher.menu

import android.content.SharedPreferences

/** Keeps the category drawer state independently of the dialog and its view lifecycle. */
class LauncherAllAppsStateManager(private val preferences: SharedPreferences) {
    data class Anchor(val key: String, val position: Int, val offset: Int)

    fun expandedKeys(): Set<String> = preferences.getStringSet(EXPANDED_KEYS, emptySet()).orEmpty().toSet()

    fun saveExpandedKeys(keys: Set<String>) {
        preferences.edit().putStringSet(EXPANDED_KEYS, keys.toSet()).apply()
    }

    fun anchor(): Anchor? {
        val key = preferences.getString(ANCHOR_KEY, null) ?: return null
        return Anchor(key, preferences.getInt(ANCHOR_POSITION, 0), preferences.getInt(ANCHOR_OFFSET, 0))
    }

    fun saveAnchor(anchor: Anchor) {
        preferences.edit()
            .putString(ANCHOR_KEY, anchor.key)
            .putInt(ANCHOR_POSITION, anchor.position)
            .putInt(ANCHOR_OFFSET, anchor.offset)
            .apply()
    }

    companion object {
        private const val EXPANDED_KEYS = "expanded_categories"
        private const val ANCHOR_KEY = "anchor_key"
        private const val ANCHOR_POSITION = "anchor_position"
        private const val ANCHOR_OFFSET = "anchor_offset"

        fun resolvePosition(anchor: Anchor, keys: List<String>): Int? {
            if (keys.isEmpty()) return null
            val matching = keys.indexOf(anchor.key)
            return if (matching >= 0) matching else anchor.position.coerceIn(0, keys.lastIndex)
        }
    }
}
