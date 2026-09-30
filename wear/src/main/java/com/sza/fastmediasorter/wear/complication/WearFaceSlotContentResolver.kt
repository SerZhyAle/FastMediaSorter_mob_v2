package com.sza.fastmediasorter.wear.complication

import android.provider.AlarmClock
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.catalog.HomeSectionCatalog
import com.sza.fastmediasorter.wear.domain.catalog.WearAppCatalog
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.domain.model.WearComplicationKind
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.wear.domain.model.WearFaceSystemItem
import com.sza.fastmediasorter.wear.domain.model.destinationFor
import com.sza.fastmediasorter.wear.tile.tileShortcutIconFor

/** S3558: what a face slot draws, decided before anything is read from the device. */
sealed interface WearFaceSlotPlan {

    /** Draws nothing - the `none` choice. */
    data object Blank : WearFaceSlotPlan

    /** A glyph and no value; the tap opens [destination] inside this app. */
    data class Shortcut(@DrawableRes val icon: Int, val destination: WearDestinationId) : WearFaceSlotPlan

    /** One of the app's own data complications, drawn exactly as its stand-alone provider draws it. */
    data class AppData(@DrawableRes val icon: Int, val kind: WearComplicationKind) : WearFaceSlotPlan

    /**
     * A system value or screen. [tapAction] is an implicit activity action, or null for no tap;
     * [needsHandler] means the slot has nothing to offer at all when no activity answers that action.
     */
    data class System(
        @DrawableRes val icon: Int,
        val item: WearFaceSystemItem,
        val tapAction: String?,
        val needsHandler: Boolean
    ) : WearFaceSlotPlan
}

/**
 * S3558: maps a slot choice to a [WearFaceSlotPlan]. Pure - the Android reads (battery, alarm, the
 * activity resolve) happen in the service, so the whole id-to-content table is unit-testable.
 */
object WearFaceSlotContentResolver {

    fun planFor(option: WearFaceSlotOption): WearFaceSlotPlan = when (option) {
        WearFaceSlotOption.None -> WearFaceSlotPlan.Blank
        is WearFaceSlotOption.Destination -> WearFaceSlotPlan.Shortcut(tileShortcutIconFor(option.id), option.id)
        is WearFaceSlotOption.AppData -> WearFaceSlotPlan.AppData(complicationIconFor(option.kind), option.kind)
        is WearFaceSlotOption.System -> systemPlanFor(option.item)
    }

    /** The glyph each app data complication wears, on its own provider and in a face slot alike. */
    @DrawableRes
    fun complicationIconFor(kind: WearComplicationKind): Int = when (kind) {
        WearComplicationKind.LAST_RESOURCE -> R.drawable.ic_complication_last_resource
        WearComplicationKind.FAVOURITES_COUNT -> R.drawable.ic_complication_favourites
        WearComplicationKind.NOW_PLAYING -> R.drawable.ic_complication_now_playing
    }

    // NEXT_ALARM keeps its value when no alarm screen answers - only the tap goes, while the two
    // screen-only items have nothing left to draw without one (strategic section 7).
    private fun systemPlanFor(item: WearFaceSystemItem): WearFaceSlotPlan.System = when (item) {
        WearFaceSystemItem.BATTERY ->
            WearFaceSlotPlan.System(R.drawable.ic_complication_battery, item, tapAction = null, needsHandler = false)
        WearFaceSystemItem.DATE ->
            WearFaceSlotPlan.System(R.drawable.ic_complication_date, item, tapAction = null, needsHandler = false)
        WearFaceSystemItem.NEXT_ALARM -> WearFaceSlotPlan.System(
            R.drawable.ic_complication_alarm,
            item,
            tapAction = AlarmClock.ACTION_SHOW_ALARMS,
            needsHandler = false
        )
        WearFaceSystemItem.ALARMS -> WearFaceSlotPlan.System(
            R.drawable.ic_complication_alarm,
            item,
            tapAction = AlarmClock.ACTION_SHOW_ALARMS,
            needsHandler = true
        )
        WearFaceSystemItem.TIMER -> WearFaceSlotPlan.System(
            R.drawable.ic_complication_timer,
            item,
            tapAction = AlarmClock.ACTION_SHOW_TIMERS,
            needsHandler = true
        )
    }

    /**
     * The name a destination is read aloud by, taken from the catalogs the Apps and Home screens draw,
     * so a slot never carries a caption of its own. Null for [WearDestinationId.HOME] and for a program
     * this edition withholds - the caller falls back to the app's name.
     */
    @StringRes
    fun labelResFor(destination: WearDestinationId, capabilities: WearRestrictedCapabilities): Int? {
        val program = WearAppCatalog.apps(capabilities).firstOrNull { destinationFor(it.id) == destination }
        if (program != null) return program.labelRes
        val visibility = HomeSectionVisibility(
            streamsEnabled = true,
            offersMediaAccess = capabilities.offersMediaAccess,
            offersRemoteSources = capabilities.offersRemoteSources,
            offersContentTransfer = capabilities.offersContentTransfer,
            offersVoiceRecording = capabilities.offersVoiceRecording
        )
        return HomeSectionCatalog.sectionsFor(visibility)
            .firstOrNull { destinationFor(it.id) == destination }
            ?.labelRes
    }
}
