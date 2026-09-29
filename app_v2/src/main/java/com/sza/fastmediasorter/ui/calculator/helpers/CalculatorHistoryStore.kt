package com.sza.fastmediasorter.ui.calculator.helpers

import timber.log.Timber
import java.io.File

/**
 * Persistent store for the calculator's completed-history entries.
 *
 * One entry per line. No size cap - every entry is kept until [clear] (strategic S0329 ADR-2).
 * Implementations are expected to be called off the main thread by the caller.
 */
interface CalculatorHistoryStore {
    fun load(): List<String>
    fun append(entry: String)
    fun clear()
}

/** File-backed [CalculatorHistoryStore]. Takes a plain [File] so it stays unit-testable with a temp file. */
class FileCalculatorHistoryStore(private val file: File) : CalculatorHistoryStore {

    override fun load(): List<String> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        }.onFailure { Timber.w(it, "Calculator history: load failed") }
            .getOrDefault(emptyList())
    }

    override fun append(entry: String) {
        if (entry.isBlank()) return
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText("$entry\n", Charsets.UTF_8)
        }.onFailure { Timber.w(it, "Calculator history: append failed") }
    }

    override fun clear() {
        runCatching {
            if (file.exists()) file.delete()
        }.onFailure { Timber.w(it, "Calculator history: clear failed") }
    }
}
