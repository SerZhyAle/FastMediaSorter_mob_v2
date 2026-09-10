package com.sza.fastmediasorter.domain.model.transfer

/**
 * Outcome counts of one import, in the shape the resource-share import already reports (S1565).
 */
data class TransferReport(
    val kind: TransferDataKind,
    val created: Int,
    val updated: Int,
    val skipped: Int
) {
    companion object {
        fun empty(kind: TransferDataKind): TransferReport = TransferReport(kind, 0, 0, 0)
    }
}
