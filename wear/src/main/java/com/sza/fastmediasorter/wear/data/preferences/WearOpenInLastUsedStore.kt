package com.sza.fastmediasorter.wear.data.preferences

import android.content.Context
import com.sza.fastmediasorter.wear.domain.model.WearOpenInKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "wear_open_in_prefs"

/**
 * S4381: the application the wearer last opened an object in, kept per kind of object so that a PDF reader chosen
 * for a file is not put first in the list for a sentence (strategic ADR-4). Absence reads as "nothing chosen yet".
 * Both calls leave the main thread: the first read of the file blocks until it is parsed from disk.
 */
@Singleton
class WearOpenInLastUsedStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    suspend fun lastUsed(kind: WearOpenInKind): String? = withContext(Dispatchers.IO) {
        prefs.getString(keyOf(kind), null)
    }

    suspend fun remember(kind: WearOpenInKind, packageName: String) {
        withContext(Dispatchers.IO) { prefs.edit().putString(keyOf(kind), packageName).apply() }
    }

    private fun keyOf(kind: WearOpenInKind) = "last_used_${kind.name.lowercase()}"
}
