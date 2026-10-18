package com.sza.fastmediasorter.data.wear

import com.sza.fastmediasorter.domain.model.WatchFileLanding
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4282: remembers where each file from the watch landed until somebody asks.
 *
 * The watch's screenshot answer names only the file, and the receiver of that file is a different
 * object running a moment earlier, so the destination has to be left somewhere both can reach. Keyed
 * by the name the watch sent, the one correlator the two halves share.
 *
 * Bounded because most files from the watch are never asked about, and an unbounded map would keep a
 * name for each of them for the life of the process.
 */
@Singleton
class WatchFileLandingRegistry @Inject constructor() {

    private val landings = object : LinkedHashMap<String, WatchFileLanding>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WatchFileLanding>?): Boolean =
            size > MAX_ENTRIES
    }

    fun record(fileName: String, landing: WatchFileLanding) {
        synchronized(landings) {
            landings[fileName] = landing
        }
    }

    /**
     * The landing recorded for [fileName], or null when none was. Consumed on read, so a name that
     * arrives again later cannot answer with the place of an earlier file.
     */
    fun take(fileName: String): WatchFileLanding? = synchronized(landings) { landings.remove(fileName) }

    private companion object {
        const val MAX_ENTRIES = 8
    }
}
