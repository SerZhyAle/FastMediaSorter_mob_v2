package com.sza.fastmediasorter.domain.transfer

import kotlinx.coroutines.flow.Flow

/**
 * S4231: the person's choices for the Drive channel. Every flag is off until turned on - the channel
 * spends the person's Drive quota and polls the network, so it never starts on its own.
 */
interface DriveChannelSettings {

    val enabled: Flow<Boolean>

    /** Background polling at the platform's periodic-work minimum; meaningful only while [enabled]. */
    val backgroundPoll: Flow<Boolean>

    /** The clipboard via Google Drive has its own opt-in (contract DEVICE-EXCHANGE item CP). */
    val clipboardEnabled: Flow<Boolean>

    /** The largest packet this device writes (item CS `packetBytes`). */
    suspend fun packetBytes(): Long

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setBackgroundPoll(enabled: Boolean)

    suspend fun setClipboardEnabled(enabled: Boolean)

    /**
     * Untargeted packets this device already received. They stay in the queue for the other devices
     * (contract item CR), so this device remembers them instead of offering them again.
     */
    suspend fun receivedPacketIds(): Set<String>

    suspend fun markReceived(packetId: String)
}
