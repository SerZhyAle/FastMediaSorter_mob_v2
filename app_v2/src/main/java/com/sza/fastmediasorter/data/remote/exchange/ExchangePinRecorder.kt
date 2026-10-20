package com.sza.fastmediasorter.data.remote.exchange

/**
 * Where the account client writes back what a pinned entry learned on a connect (19 item AY): the renewed leaf of
 * a same-key renewal, or the key of a pin saved before this build. The client holds no entry state of its own.
 */
interface ExchangePinRecorder {

    /** Replaces the pin of the entry of [identity] with [leaf] and [key]; an entry that pins nothing is left alone. */
    suspend fun record(identity: String, leaf: String, key: String)
}
