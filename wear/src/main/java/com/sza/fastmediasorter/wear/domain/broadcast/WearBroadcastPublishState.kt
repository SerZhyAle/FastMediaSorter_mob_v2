package com.sza.fastmediasorter.wear.domain.broadcast

/**
 * Where the watch's live broadcast stands on the exchange server, in the short form of contract DEVICE-EXCHANGE
 * item DU; the details stay on the phone. [ON_SERVER] is the server's `broadcast-started` answer and nothing
 * earlier (item DT).
 */
enum class WearBroadcastPublishState { IDLE, LOCAL_ONLY, ANNOUNCING, ON_SERVER, FAILED }
