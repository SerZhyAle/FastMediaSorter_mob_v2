package com.sza.fastmediasorter.data.wear

import com.sza.fastmediasorter.domain.model.WearFileTransferMetadata
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1861: remembers what the watch announced until the channel carrying it opens.
 *
 * A `WearableListenerService` instance is created per delivery, so the message that declares the
 * file and the channel event that carries it can reach two different objects; the declaration has to
 * outlive both. Keyed by file name because the trailing path segment of the channel is the only
 * correlator the Data Layer offers.
 */
@Singleton
class WearIncomingFileRegistry @Inject constructor() {

    private val declarations = ConcurrentHashMap<String, WearFileTransferMetadata>()

    fun declare(metadata: WearFileTransferMetadata) {
        if (metadata.name.isBlank()) {
            Timber.w("Ignoring an incoming watch file declaration with no name")
        } else {
            declarations[metadata.name] = metadata
        }
    }

    /**
     * The whole declaration for [fileName], or null when it arrived undeclared. Consumed on read, so
     * a declaration that never grew a channel does not answer for the next file of the same name.
     *
     * S2142: the declaration rather than the size alone - it now also carries the receiver the watch
     * is asking this phone to hand the file to, and reading the size out here would drop that errand
     * on the floor while the transfer went on looking perfectly normal.
     */
    fun take(fileName: String): WearFileTransferMetadata? = declarations.remove(fileName)
}
