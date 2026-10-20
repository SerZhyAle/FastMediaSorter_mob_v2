package com.sza.fastmediasorter.data.remote.exchange

/** Keeps every pin the client asked to record, so a test can tell a renewal written back from none. */
class RecordingPinRecorder : ExchangePinRecorder {

    data class Recorded(val identity: String, val leaf: String, val key: String)

    val recorded = mutableListOf<Recorded>()

    override suspend fun record(identity: String, leaf: String, key: String) {
        recorded += Recorded(identity, leaf, key)
    }
}
