package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteDeltaItem
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.mergeFavorites
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class WearFavoritesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearFavoritesRepository {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "wear_favorites_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val keyFavorites = "wear_favorites"
    private val keyDelta = "wear_favorites_delta"

    /**
     * S1846: records live under their OWN key, beside the pre-record set rather than replacing it.
     *
     * Rewriting the old key in place would mean either dropping entries this build cannot enrich, or
     * migrating on read - and a read that writes turns listing the favourites into a way to lose them if
     * the process dies mid-write. Both keys are read; only this one is written.
     */
    private val keyRecords = "wear_favorites_records"

    override suspend fun addFavorite(sourceId: String, filePath: String) = withContext(Dispatchers.IO) {
        val key = "$sourceId:$filePath"
        val favorites = readFavorites().toMutableSet()
        if (favorites.add(key)) {
            saveFavorites(favorites)
            appendDelta(WearFavoriteDeltaItem(sourceId, filePath, true, System.currentTimeMillis()))
        }
    }

    override suspend fun addFavorite(record: WearFavoriteRecord) = withContext(Dispatchers.IO) {
        val records = readRecords().toMutableList()
        if (records.none { it.identity == record.identity }) {
            records.add(record)
            saveRecords(records)
            appendDelta(
                WearFavoriteDeltaItem(record.sourceId, record.filePath, true, System.currentTimeMillis())
            )
        }
    }

    override suspend fun getFavorites(): List<WearFavoriteRecord> = withContext(Dispatchers.IO) {
        // The merge itself is a pure function so it can be tested without an Android runtime; this store
        // only supplies the two shapes it reads. Neither read writes anything back.
        mergeFavorites(records = readRecords(), legacyKeys = readFavorites())
    }

    override suspend fun removeFavorite(sourceId: String, filePath: String) = withContext(Dispatchers.IO) {
        val key = "$sourceId:$filePath"
        // Unmarking has to reach BOTH stores: a file marked before this ticket and unmarked after it would
        // otherwise come straight back on the next read.
        val favorites = readFavorites().toMutableSet()
        val removedLegacy = favorites.remove(key)
        if (removedLegacy) {
            saveFavorites(favorites)
        }
        val records = readRecords()
        val kept = records.filterNot { it.identity == key }
        if (kept.size != records.size) {
            saveRecords(kept)
        }
        if (removedLegacy || kept.size != records.size) {
            appendDelta(WearFavoriteDeltaItem(sourceId, filePath, false, System.currentTimeMillis()))
        }
    }

    override suspend fun isFavorite(sourceId: String, filePath: String): Boolean = withContext(Dispatchers.IO) {
        val key = "$sourceId:$filePath"
        readFavorites().contains(key) || readRecords().any { it.identity == key }
    }

    override suspend fun getPendingDelta(): List<WearFavoriteDeltaItem> = withContext(Dispatchers.IO) {
        readDelta()
    }

    override suspend fun clearPendingDelta() = withContext(Dispatchers.IO) {
        prefs.edit().putString(keyDelta, gson.toJson(emptyList<WearFavoriteDeltaItem>())).apply()
    }

    private fun readFavorites(): Set<String> {
        return try {
            val json = prefs.getString(keyFavorites, null) ?: return emptySet()
            val type = TypeToken.getParameterized(Set::class.java, String::class.java).type
            gson.fromJson<Set<String>>(json, type) ?: emptySet()
        } catch (e: Exception) {
            Timber.e(e, "WearFavoritesRepositoryImpl: failed to read favorites")
            emptySet()
        }
    }

    private fun saveFavorites(favorites: Set<String>) {
        prefs.edit().putString(keyFavorites, gson.toJson(favorites)).apply()
    }

    private fun readRecords(): List<WearFavoriteRecord> {
        return try {
            val json = prefs.getString(keyRecords, null) ?: return emptyList()
            val type = TypeToken.getParameterized(List::class.java, WearFavoriteRecord::class.java).type
            gson.fromJson<List<WearFavoriteRecord>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "WearFavoritesRepositoryImpl: failed to read favorite records")
            emptyList()
        }
    }

    private fun saveRecords(records: List<WearFavoriteRecord>) {
        prefs.edit().putString(keyRecords, gson.toJson(records)).apply()
    }

    private fun readDelta(): List<WearFavoriteDeltaItem> {
        return try {
            val json = prefs.getString(keyDelta, null) ?: return emptyList()
            val type = TypeToken.getParameterized(List::class.java, WearFavoriteDeltaItem::class.java).type
            gson.fromJson<List<WearFavoriteDeltaItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "WearFavoritesRepositoryImpl: failed to read delta")
            emptyList()
        }
    }

    private fun appendDelta(item: WearFavoriteDeltaItem) {
        val delta = readDelta().toMutableList()
        delta.add(item)
        prefs.edit().putString(keyDelta, gson.toJson(delta)).apply()
    }
}
