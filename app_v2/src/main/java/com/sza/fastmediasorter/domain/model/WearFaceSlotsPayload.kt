package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S3558: the four watch face buttons as the watch receives them - each a [WearFaceSlotOption.wireId].
 *
 * Keys are pinned for the S1631 reason [WearClockStylePayload] states: the phone ships minified while
 * the watch keeps its own copy of this contract. Every field is nullable so a peer that omits a key
 * decodes to "keep your own value" rather than a Gson-zeroed field.
 */
data class WearFaceSlotsPayload(
    @SerializedName("slot1") val slot1: String? = null,
    @SerializedName("slot2") val slot2: String? = null,
    @SerializedName("slot3") val slot3: String? = null,
    @SerializedName("slot4") val slot4: String? = null,
    @SerializedName("sentAt") val sentAt: Long? = null,
) {
    companion object {
        fun from(assignment: WearFaceSlotAssignment) = WearFaceSlotsPayload(
            slot1 = assignment.optionFor(WearFaceSlot.OUTER_LEFT).wireId,
            slot2 = assignment.optionFor(WearFaceSlot.INNER_LEFT).wireId,
            slot3 = assignment.optionFor(WearFaceSlot.INNER_RIGHT).wireId,
            slot4 = assignment.optionFor(WearFaceSlot.OUTER_RIGHT).wireId,
        )
    }
}
