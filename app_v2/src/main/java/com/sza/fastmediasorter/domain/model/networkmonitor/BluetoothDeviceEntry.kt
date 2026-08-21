package com.sza.fastmediasorter.domain.model.networkmonitor

/**
 * S1433: one device this phone is paired with, or is connected to, or both.
 *
 * Kept out of [NetworkMonitorSnapshot] on purpose. That snapshot carries a bonded *count* and never a list,
 * and widening it would put device names in front of every screen reading it; strategic §3.1.1 item 5
 * permits the names only in the Bluetooth section and only after the grant, so the list has its own read
 * path and reaches nothing else.
 *
 * [address] exists because it is the key `BluetoothRssiSampler` needs to open a GATT connection. It is
 * never rendered and never logged - it identifies a piece of hardware a person carries around, and the
 * section already has [name] for everything it has to show.
 *
 * The two connection flags answer two different questions and must not be merged (S1853). [isConnected] is
 * "the platform reports a live link on some profile", which is what the section lists; [isChartable] is
 * "that link is a GATT connection whose RSSI can be read", which is the only kind the signal chart can
 * sample. A headset is the first without being the second.
 */
data class BluetoothDeviceEntry(
    val address: String,
    val name: String?,
    val isConnected: Boolean,
    val isChartable: Boolean,
)
