package com.sza.fastmediasorter.data.network.glide

// S0066: protocol-agnostic transient-failure taxonomy used by NetworkMediaDataSource
// detectors and consumed by NetworkVideoFrameDecoder for unified classification.
enum class TransientReason {
    /** SMB only — DiskShare lifecycle race between thumbnail IO and active playback. */
    STALE_SHARE,

    /** SFTP only — SSH Channel/Session closed mid-read. */
    BROKEN_CHANNEL,

    /** FTP only — control/data channel "Broken pipe" / "Connection reset" / 421 / 426. */
    BROKEN_PIPE,

    /** Any protocol — SocketTimeoutException or message-level timeout signal. */
    TIMEOUT,

    /** SFTP / FTP — generic transport-level failure (SocketException, TransportException). */
    TRANSPORT,
}
