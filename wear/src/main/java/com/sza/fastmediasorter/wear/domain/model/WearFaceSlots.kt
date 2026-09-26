package com.sza.fastmediasorter.wear.domain.model

/** S3558: the system values and screens a face slot can show besides the app's own content. */
enum class WearFaceSystemItem {
    BATTERY,
    DATE,
    NEXT_ALARM,
    ALARMS,
    TIMER
}

/**
 * S3558: what one watch-face slot shows, as chosen on the phone.
 *
 * The wire id is the whole contract with the phone's publisher: `dest:<WearDestinationId>`,
 * `data:<WearComplicationKind>`, `sys:<WearFaceSystemItem>` or `none`. Enum names rather than ordinals,
 * so a value added on either side reads as unknown on the other instead of as a different option.
 */
sealed interface WearFaceSlotOption {
    val wireId: String

    data class Destination(val id: WearDestinationId) : WearFaceSlotOption {
        override val wireId: String get() = PREFIX_DESTINATION + id.name
    }

    data class AppData(val kind: WearComplicationKind) : WearFaceSlotOption {
        override val wireId: String get() = PREFIX_APP_DATA + kind.name
    }

    data class System(val item: WearFaceSystemItem) : WearFaceSlotOption {
        override val wireId: String get() = PREFIX_SYSTEM + item.name
    }

    data object None : WearFaceSlotOption {
        override val wireId: String get() = WIRE_NONE
    }

    companion object {
        const val PREFIX_DESTINATION = "dest:"
        const val PREFIX_APP_DATA = "data:"
        const val PREFIX_SYSTEM = "sys:"
        const val WIRE_NONE = "none"

        /** Null for an id this build does not know, so the caller picks the slot's own default. */
        fun fromWireId(wireId: String?): WearFaceSlotOption? = when {
            wireId == null -> null
            wireId == WIRE_NONE -> None
            wireId.startsWith(PREFIX_DESTINATION) ->
                enumOrNull<WearDestinationId>(wireId.removePrefix(PREFIX_DESTINATION))?.let(::Destination)
            wireId.startsWith(PREFIX_APP_DATA) ->
                enumOrNull<WearComplicationKind>(wireId.removePrefix(PREFIX_APP_DATA))?.let(::AppData)
            wireId.startsWith(PREFIX_SYSTEM) ->
                enumOrNull<WearFaceSystemItem>(wireId.removePrefix(PREFIX_SYSTEM))?.let(::System)
            else -> null
        }

        private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
            enumValues<E>().firstOrNull { it.name == name }
    }
}

/**
 * S3558: the four slot choices, slot 1 first. Always exactly [SLOT_COUNT] entries - the codec fills a
 * missing or unknown choice with that slot's default, so a reader never has to.
 */
data class WearFaceSlots(
    val options: List<WearFaceSlotOption>,
    val sentAt: Long
) {
    init {
        require(options.size == SLOT_COUNT) { "A face has exactly $SLOT_COUNT slots, got ${options.size}" }
    }

    /** [slot] is 1-based, as the face's slotId is. */
    fun optionFor(slot: Int): WearFaceSlotOption = options.getOrNull(slot - 1) ?: WearFaceSlotOption.None

    companion object {
        const val SLOT_COUNT = 4

        /** ADR-5: the face as it looked before the phone could choose - three app providers, slot 4 empty. */
        val DEFAULT_OPTIONS: List<WearFaceSlotOption> = listOf(
            WearFaceSlotOption.AppData(WearComplicationKind.FAVOURITES_COUNT),
            WearFaceSlotOption.AppData(WearComplicationKind.LAST_RESOURCE),
            WearFaceSlotOption.AppData(WearComplicationKind.NOW_PLAYING),
            WearFaceSlotOption.None
        )

        val DEFAULT = WearFaceSlots(DEFAULT_OPTIONS, sentAt = 0L)
    }
}
