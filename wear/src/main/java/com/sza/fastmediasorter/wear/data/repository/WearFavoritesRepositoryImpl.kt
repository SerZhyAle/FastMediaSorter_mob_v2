package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteDeltaItem
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

    override suspend fun addFavorite(sourceId: String, filePath: String) = withContext(Dispatchers.IO) {
        val key = "$sourceId:$filePath"
        val favorites = readFavorites().toMutableSet()
        if (favorites.add(key)) {
            saveFavorites(favorites)
            appendDelta(WearFavoriteDeltaItem(sourceId, filePath, true, System.currentTimeMillis()))
        }
    }

    override suspend fun removeFavorite(sourceId: String, filePath: String) = withContext(Dispatchers.IO) {
        val key = "$sourceId:$filePath"
        val favorites = readFavorites().toMutableSet()
        if (favorites.remove(key)) {
            saveFavorites(favorites)
            appendDelta(WearFavoriteDeltaItem(sourceId, filePath, false, System.currentTimeMillis()))
        }
    }

    override suspend fun isFavorite(sourceId: String, filePath: String): Boolean = withContext(Dispatchers.IO) {
        readFavorites().contains("$sourceId:$filePath")
    }

    override suspend fun hasAnyFavorite(): Boolean = withContext(Dispatchers.IO) {
        readFavorites().isNotEmpty()
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
