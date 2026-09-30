package com.sza.fastmediasorter.wear.complication

import dagger.hilt.android.AndroidEntryPoint

// S3558: one component per face slot (ADR-2). The watch face binds these exact class names in its
// DefaultProviderPolicy, so renaming one silently empties that slot on every installed face.

private const val SLOT_1 = 1
private const val SLOT_2 = 2
private const val SLOT_3 = 3
private const val SLOT_4 = 4

@AndroidEntryPoint
class WearFaceSlot1ComplicationService : BaseWearFaceSlotComplicationService() {
    override val slot: Int = SLOT_1
}

@AndroidEntryPoint
class WearFaceSlot2ComplicationService : BaseWearFaceSlotComplicationService() {
    override val slot: Int = SLOT_2
}

@AndroidEntryPoint
class WearFaceSlot3ComplicationService : BaseWearFaceSlotComplicationService() {
    override val slot: Int = SLOT_3
}

@AndroidEntryPoint
class WearFaceSlot4ComplicationService : BaseWearFaceSlotComplicationService() {
    override val slot: Int = SLOT_4
}
