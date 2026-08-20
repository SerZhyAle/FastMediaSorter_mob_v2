package com.sza.fastmediasorter.data.local.db

/**
 * S1832: one row's play outcome, carried out of the join between `stream_sources` and the
 * identity-keyed `stream_user_state`. Exists so the repository can keep handing its callers a map
 * keyed by row id while the value itself now lives under the channel's durable identity.
 */
data class StreamRowOutcome(
    val streamId: String,
    val outcome: String
)
