package com.sza.fastmediasorter.ui.browse.transfer

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowseFileTransferRequestStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) {
    private val lock = Any()
    private val rootDir: File by lazy(LazyThreadSafetyMode.NONE) {
        File(context.filesDir, "browse_file_transfer").apply { mkdirs() }
    }
    private val activeRequestFile: File by lazy(LazyThreadSafetyMode.NONE) {
        File(rootDir, "active_request.json")
    }
    private val terminalEventFile: File by lazy(LazyThreadSafetyMode.NONE) {
        File(rootDir, "terminal_event.json")
    }

    fun writeActiveRequest(request: BrowseFileTransferRequest) {
        synchronized(lock) {
            writeJson(activeRequestFile, request)
        }
    }

    fun readActiveRequest(): BrowseFileTransferRequest? = synchronized(lock) {
        readJson(activeRequestFile, BrowseFileTransferRequest::class.java)
    }

    fun clearActiveRequest() {
        synchronized(lock) {
            deleteIfExists(activeRequestFile)
        }
    }

    fun writeTerminalEvent(payload: BrowseFileTransferTerminalPayload) {
        synchronized(lock) {
            writeJson(terminalEventFile, payload)
        }
    }

    fun consumeTerminalEvent(): BrowseFileTransferTerminalPayload? = synchronized(lock) {
        val payload = readJson(terminalEventFile, BrowseFileTransferTerminalPayload::class.java)
        deleteIfExists(terminalEventFile)
        payload
    }

    fun clearTerminalEvent() {
        synchronized(lock) {
            deleteIfExists(terminalEventFile)
        }
    }

    private fun <T> writeJson(target: File, value: T) {
        runCatching {
            target.parentFile?.mkdirs()
            target.writeText(gson.toJson(value), Charsets.UTF_8)
        }.onFailure { Timber.e(it, "BrowseFileTransferRequestStore: failed writing %s", target.name) }
    }

    private fun <T> readJson(target: File, clazz: Class<T>): T? {
        if (!target.exists()) return null
        return runCatching {
            gson.fromJson(target.readText(Charsets.UTF_8), clazz)
        }.onFailure { Timber.e(it, "BrowseFileTransferRequestStore: failed reading %s", target.name) }
            .getOrNull()
    }

    private fun deleteIfExists(target: File) {
        if (!target.exists()) return
        if (!target.delete()) {
            Timber.w("BrowseFileTransferRequestStore: failed deleting %s", target.absolutePath)
        }
    }
}
