package com.sza.fastmediasorter.wear.data.wear

/**
 * How long the watch waits for the phone's answer to one message before calling it unanswered.
 *
 * Declared once for every message-and-ack round trip on the bridge. The module already carried this
 * duration privately inside each client, and a per-client copy makes "the phone did not answer" mean
 * a different wait on every screen that says it - which is a difference the user reads as one screen
 * being broken and another not.
 */
const val WEAR_MESSAGE_ACK_TIMEOUT_MS = 15_000L
