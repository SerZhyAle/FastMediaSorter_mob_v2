package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherWorldClockBinding
import com.sza.fastmediasorter.domain.model.UnitScale
import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import dagger.Lazy
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
class WorldClockGadget @Inject constructor(
    private val unitSystemProvider: Lazy<UnitSystemProvider>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_WORLD_CLOCK

    // Two lines - a time and a place - at the same footprint the weather cell uses for the same shape.
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_world_clock
    override val iconRes: Int = R.drawable.ic_world_clock

    // S2062: ic_world_clock fills white and is invisible on the picker's light surface without a tint.
    override val iconTintable: Boolean = true

    // The zone is not a registered resource, so the add flow asks for it with its own picker.
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        WorldClockGadgetView(container.context, param, unitSystemProvider.get())
}

/**
 * No polling and no receiver: [android.widget.TextClock] ticks itself, so handing it a zone id and a
 * pattern is all this view does about time. The pattern is the only lifecycle work (S2795) - the app's
 * unit system, not the device's 12/24-hour switch, decides the clock length.
 */
private class WorldClockGadgetView(
    context: Context,
    param: String?,
    private val unitSystemProvider: UnitSystemProvider,
) : LauncherGadgetView(context) {

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
            // Applied here and not only from the collection below, so the clock never draws one frame
            // in the platform's default pattern before the first emission arrives.
            applyPattern(unitSystemProvider.value)
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
        unitSystemProvider.current.collect { applyPattern(it) }
    }

    /**
     * Exactly one attribute carries the pattern and the other is null, because TextClock falls back to
     * whichever one is set when the one matching the DEVICE's 12/24-hour switch is missing - the same
     * rule the local clock gadget applies. Setting both, as this layout used to, hands the choice back
     * to the device. The imperial pattern keeps its meridiem: a bare "2:39" for Tokyo inverts day and
     * night, which is the one thing a remote clock exists to tell.
     */
    private fun applyPattern(system: UnitSystem) {
        val pattern = UnitScale.timePattern(system)
        val imperial = system == UnitSystem.IMPERIAL
        binding.gadgetWorldClockTime.format12Hour = pattern.takeIf { imperial }
        binding.gadgetWorldClockTime.format24Hour = pattern.takeIf { !imperial }
    }
}
