package com.sza.fastmediasorter.wear.complication

import android.provider.AlarmClock
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearComplicationKind
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.wear.domain.model.WearFaceSystemItem
import com.sza.fastmediasorter.wear.tile.tileShortcutIconFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearFaceSlotContentResolverTest {

    private fun plan(wireId: String): WearFaceSlotPlan =
        WearFaceSlotContentResolver.planFor(requireNotNull(WearFaceSlotOption.fromWireId(wireId)))

    @Test
    fun `every destination id parses and becomes a shortcut wearing its tile glyph`() {
        WearDestinationId.entries.forEach { id ->
            assertEquals(
                WearFaceSlotPlan.Shortcut(tileShortcutIconFor(id), id),
                plan("dest:${id.name}")
            )
        }
    }

    @Test
    fun `a data id becomes the app content with the stand-alone provider's glyph`() {
        assertEquals(
            WearFaceSlotPlan.AppData(R.drawable.ic_complication_favourites, WearComplicationKind.FAVOURITES_COUNT),
            plan("data:FAVOURITES_COUNT")
        )
    }

    @Test
    fun `system ids map to their value, tap action and handler need`() {
        assertEquals(
            WearFaceSlotPlan.System(R.drawable.ic_complication_battery, WearFaceSystemItem.BATTERY, null, false),
            plan("sys:BATTERY")
        )
        assertEquals(
            WearFaceSlotPlan.System(
                R.drawable.ic_complication_alarm,
                WearFaceSystemItem.NEXT_ALARM,
                AlarmClock.ACTION_SHOW_ALARMS,
                false
            ),
            plan("sys:NEXT_ALARM")
        )
        assertEquals(
            WearFaceSlotPlan.System(
                R.drawable.ic_complication_timer,
                WearFaceSystemItem.TIMER,
                AlarmClock.ACTION_SHOW_TIMERS,
                true
            ),
            plan("sys:TIMER")
        )
    }

    @Test
    fun `none draws nothing`() {
        assertEquals(WearFaceSlotPlan.Blank, plan("none"))
    }

    @Test
    fun `wire ids round-trip and unknown ones parse to null`() {
        val options = WearDestinationId.entries.map { WearFaceSlotOption.Destination(it) } +
            WearComplicationKind.entries.map { WearFaceSlotOption.AppData(it) } +
            WearFaceSystemItem.entries.map { WearFaceSlotOption.System(it) } +
            WearFaceSlotOption.None
        options.forEach { option -> assertEquals(option, WearFaceSlotOption.fromWireId(option.wireId)) }

        listOf("dest:", "dest:calculator", "sys:WEATHER", "data:STEPS", "NONE", "", null).forEach { id ->
            assertNull(id, WearFaceSlotOption.fromWireId(id))
        }
    }
}
