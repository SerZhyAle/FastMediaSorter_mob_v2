package com.sza.fastmediasorter.data.network.datasource

object TsPacketFormatDetector {

    const val PROBE_BYTES = 576 // 192 × 3 - enough to check 3 consecutive BD-TS or standard-TS packets

    fun detect(probe: ByteArray): TsPacketFormat {
        val result = detectInternal(probe)
        return result
    }

    private fun detectInternal(probe: ByteArray): TsPacketFormat {
        if (probe.size < PROBE_BYTES) return TsPacketFormat.UNKNOWN
        val sync = 0x47.toByte()
        if (probe[4] == sync && probe[196] == sync && probe[388] == sync && probe[0] != sync) {
            return TsPacketFormat.BD_192
        }
        if (probe[0] == sync && probe[188] == sync && probe[376] == sync) {
            return TsPacketFormat.STANDARD_188
        }
        return TsPacketFormat.UNKNOWN
    }
}
