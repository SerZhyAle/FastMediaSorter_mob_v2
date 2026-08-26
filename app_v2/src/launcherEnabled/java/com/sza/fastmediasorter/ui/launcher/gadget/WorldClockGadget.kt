package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherWorldClockBinding
import kotlinx.coroutines.CoroutineScope
import java.time.ZoneId
import javax.inject.Inject

/**
 * S1906: the time in one remote zone, beside the local clock rather than instead of it.
 *
 * A separate gadget rather than a parameter on [ClockGadget]: a gadget key is what every placed cell
 * stores in its `target`, so giving the existing clock an optional zone would change what already
 * placed clocks mean (strategic ADR-1).
 */
class WorldClockGadget @Inject constructor() : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_WORLD_CLOCK

    // Two lines - a time and a place - at the same footprint the weather cell uses for the same shape.
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_world_clock
    override val iconRes: Int = R.drawable.ic_world_clock

    // The zone is not a registered resource, so the add flow asks for it with its own picker.
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        WorldClockGadgetView(container.context, param)
}

/**
 * No polling and no receiver: [android.widget.TextClock] ticks itself and follows the 12/24-hour system
 * setting, and handing it a zone id is all this view does about time.
 */
private class WorldClockGadgetView(context: Context, param: String?) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherWorldClockBinding.inflate(LayoutInflater.from(context), this)

    private val zone: ZoneId? = LauncherTimeZoneCatalog.zoneOrNull(param)

    init {
        contentDescription = context.getString(R.string.launcher_gadget_world_clock_actions)
        val target = zone
        if (target == null) {
            // The host redirects this cell's tap to the zone picker (the S1560 contract the weather cell
            // established), so the caption is an invitation, not a dead end.
            binding.gadgetWorldClockTime.isVisible = false
            binding.gadgetWorldClockPlace.setText(R.string.launcher_gadget_world_clock_no_zone)
        } else {
            binding.gadgetWorldClockTime.timeZone = target.id
            setOnClickListener { openSystemClock(context) }
        }
    }

    /**
     * The caption is written on every activation rather than once in the constructor: the difference
     * from local time moves at a daylight-saving boundary in either zone, and a desktop left open across
     * one would otherwise keep a stale number under a clock that is already correct.
     */
    override suspend fun CoroutineScope.onActive() {
        val target = zone ?: return
        binding.gadgetWorldClockPlace.text = LauncherTimeZoneCatalog.caption(target)
    }
}
