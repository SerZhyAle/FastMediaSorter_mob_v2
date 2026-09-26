package com.sza.fastmediasorter.domain.model

/**
 * S3558: the families a watch face button can be pointed at, in the order the companion picker lists
 * them.
 */
enum class WearFaceSlotOptionGroup {
    WATCH_SECTIONS,
    WATCH_PROGRAMS,
    APP_DATA,
    SYSTEM,
    NONE,
}

/**
 * S3558: everything one watch face button can open or show.
 *
 * [wireId] is the value the watch receives and persists, so it is a contract with the watch module and
 * never changes once shipped - renaming a constant here is free, renaming its [wireId] strands every
 * watch that already stored the old one. A `dest:` id names a watch destination by its
 * `WearDestinationId` constant, so the watch resolves it without a second table.
 */
enum class WearFaceSlotOption(val wireId: String, val group: WearFaceSlotOptionGroup) {
    DEST_RESOURCES("dest:RESOURCES", WearFaceSlotOptionGroup.WATCH_SECTIONS),
    DEST_PHONE("dest:PHONE", WearFaceSlotOptionGroup.WATCH_SECTIONS),
    DEST_LOCAL("dest:LOCAL", WearFaceSlotOptionGroup.WATCH_SECTIONS),
    DEST_STREAMS("dest:STREAMS", WearFaceSlotOptionGroup.WATCH_SECTIONS),
    DEST_APPS("dest:APPS", WearFaceSlotOptionGroup.WATCH_SECTIONS),
    DEST_FAVOURITES("dest:FAVOURITES", WearFaceSlotOptionGroup.WATCH_SECTIONS),
    DEST_PHONE_CAMERA("dest:PHONE_CAMERA", WearFaceSlotOptionGroup.WATCH_SECTIONS),
    DEST_HOME("dest:HOME", WearFaceSlotOptionGroup.WATCH_SECTIONS),
    DEST_CALCULATOR("dest:CALCULATOR", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_NETWORK_MONITOR("dest:NETWORK_MONITOR", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_GAME("dest:GAME", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_VOICE_RECORDER("dest:VOICE_RECORDER", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_SYSTEM_INFO("dest:SYSTEM_INFO", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_WATER_FLASHLIGHT("dest:WATER_FLASHLIGHT", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_MOTION_MONITOR("dest:MOTION_MONITOR", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_BODY_SENSOR("dest:BODY_SENSOR", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_BLOOD_PRESSURE("dest:BLOOD_PRESSURE", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_BROADCAST("dest:BROADCAST", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_STOPWATCH("dest:STOPWATCH", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_TOURIST("dest:TOURIST", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_CLIPBOARD("dest:CLIPBOARD", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DEST_SOS("dest:SOS", WearFaceSlotOptionGroup.WATCH_PROGRAMS),
    DATA_FAVOURITES_COUNT("data:FAVOURITES_COUNT", WearFaceSlotOptionGroup.APP_DATA),
    DATA_LAST_RESOURCE("data:LAST_RESOURCE", WearFaceSlotOptionGroup.APP_DATA),
    DATA_NOW_PLAYING("data:NOW_PLAYING", WearFaceSlotOptionGroup.APP_DATA),
    SYS_BATTERY("sys:BATTERY", WearFaceSlotOptionGroup.SYSTEM),
    SYS_DATE("sys:DATE", WearFaceSlotOptionGroup.SYSTEM),
    SYS_NEXT_ALARM("sys:NEXT_ALARM", WearFaceSlotOptionGroup.SYSTEM),
    SYS_ALARMS("sys:ALARMS", WearFaceSlotOptionGroup.SYSTEM),
    SYS_TIMER("sys:TIMER", WearFaceSlotOptionGroup.SYSTEM),
    NONE("none", WearFaceSlotOptionGroup.NONE),
    ;

    companion object {
        private val byWireId = entries.associateBy { it.wireId }

        fun fromWireIdOrNull(wireId: String?): WearFaceSlotOption? = wireId?.let(byWireId::get)
    }
}

/**
 * S3558: the four buttons of the watch face, named by where they sit. The order is the wire order -
 * [ordinal] 0 travels as `slot1` - so a new position may only be appended.
 */
enum class WearFaceSlot(val defaultOption: WearFaceSlotOption) {
    OUTER_LEFT(WearFaceSlotOption.DATA_FAVOURITES_COUNT),
    INNER_LEFT(WearFaceSlotOption.DATA_LAST_RESOURCE),
    INNER_RIGHT(WearFaceSlotOption.DATA_NOW_PLAYING),
    OUTER_RIGHT(WearFaceSlotOption.NONE),
}

/** S3558: what each of the four watch face buttons is set to, in [WearFaceSlot] order. */
data class WearFaceSlotAssignment(val options: Map<WearFaceSlot, WearFaceSlotOption>) {

    fun optionFor(slot: WearFaceSlot): WearFaceSlotOption = options[slot] ?: slot.defaultOption

    companion object {
        val DEFAULT = WearFaceSlotAssignment(WearFaceSlot.entries.associateWith { it.defaultOption })
    }
}
