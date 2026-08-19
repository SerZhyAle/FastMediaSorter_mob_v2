package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1708: JSON file-backed implementation of [WearStreamChannelRepository].
 * Stores channels atomically in `filesDir/streams/channels.json`.
 */
@Singleton
class WearStreamChannelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearStreamChannelRepository {

    private val dir: File get() = File(context.filesDir, "streams")
    private val channelsFile: File get() = File(dir, "channels.json")

    private val _channelsFlow = MutableStateFlow<List<WearStreamChannel>>(emptyList())

    init {
        val initial = readChannelsFromFile()
        _channelsFlow.value = initial
    }

    override suspend fun getAllChannels(): List<WearStreamChannel> = withContext(Dispatchers.IO) {
        val channels = readChannelsFromFile()
        _channelsFlow.value = channels
        channels
    }

    override fun observeChannels(): Flow<List<WearStreamChannel>> = _channelsFlow.asStateFlow()

    override suspend fun saveChannels(channels: List<WearStreamChannel>) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val type = object : TypeToken<List<WearStreamChannel>>() {}.type
        val json = gson.toJson(channels, type)
        writeAtomically(channelsFile, json.toByteArray(Charsets.UTF_8))
        _channelsFlow.value = channels
        Timber.d("WearStreamChannelRepository: Saved ${channels.size} channels")
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        if (channelsFile.exists()) {
            channelsFile.delete()
        }
        _channelsFlow.value = emptyList()
    }

    private fun readChannelsFromFile(): List<WearStreamChannel> {
        val file = channelsFile
        if (!file.isFile) return emptyList()
        return try {
            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<WearStreamChannel>>() {}.type
            gson.fromJson<List<WearStreamChannel>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Timber.w(e, "WearStreamChannelRepository: Failed to read channels from file")
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
