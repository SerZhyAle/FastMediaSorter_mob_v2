package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.wear.domain.model.WearStreamCollection
import com.sza.fastmediasorter.wear.domain.repository.WearStreamCollectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2669: JSON file-backed implementation of [WearStreamCollectionRepository], a neighbour of the
 * channel file in `filesDir/streams/`. The watch has no database and no schema version; the write
 * mirrors [WearStreamChannelRepositoryImpl]'s atomic `.tmp` plus rename with a direct-write
 * fallback, because that is what keeps a half-written file from surviving a crash mid-import.
 */
@Singleton
class WearStreamCollectionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearStreamCollectionRepository {

    private val dir: File get() = File(context.filesDir, "streams")
    private val collectionsFile: File get() = File(dir, "collections.json")

    private val _collectionsFlow = MutableStateFlow<List<WearStreamCollection>>(emptyList())

    init {
        _collectionsFlow.value = readFromFile()
    }

    override fun observeCollections(): Flow<List<WearStreamCollection>> = _collectionsFlow.asStateFlow()

    override suspend fun saveAll(collections: List<WearStreamCollection>) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val type = object : TypeToken<List<WearStreamCollection>>() {}.type
        val json = gson.toJson(collections, type)
        writeAtomically(collectionsFile, json.toByteArray(Charsets.UTF_8))
        _collectionsFlow.value = collections
        Timber.d("WearStreamCollectionRepository: Saved ${collections.size} collections")
    }

    private fun readFromFile(): List<WearStreamCollection> {
        val file = collectionsFile
        if (!file.isFile) return emptyList()
        return try {
            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<WearStreamCollection>>() {}.type
            gson.fromJson<List<WearStreamCollection>>(json, type) ?: emptyList()
        } catch (e: IOException) {
            Timber.w(e, "WearStreamCollectionRepository: Failed to read collections from file")
            emptyList()
        } catch (e: JsonSyntaxException) {
            Timber.w(e, "WearStreamCollectionRepository: Failed to read collections from file")
            emptyList()
        }
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
    }
}
