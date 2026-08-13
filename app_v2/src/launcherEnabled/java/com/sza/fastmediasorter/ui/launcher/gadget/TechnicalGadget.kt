package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.sza.fastmediasorter.databinding.GadgetLauncherTechnicalBinding
import com.sza.fastmediasorter.domain.model.devicestatus.DeviceStatusProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber

/**
 * S1178: one technical status cell on the desktop - network, battery, storage or resources.
 *
 * One class instantiated four times rather than four classes with the same body (ADR-2): all four show a
 * value, a caption and sometimes a fill bar, and four copies would diverge at the first edit. A fifth
 * metric joins by adding an entry to
 * [TechnicalGadgetModule][com.sza.fastmediasorter.ui.launcher.gadget.di.TechnicalGadgetModule] and a
 * branch to [TechnicalGadgetFormatter] - no new view (strategic 5.3).
 */
class TechnicalGadget(
    override val key: String,
    @StringRes override val labelRes: Int,
    @DrawableRes override val iconRes: Int,
    private val provider: DeviceStatusProvider<Any>,
) : LauncherGadget {

    // Identical spans across the four are what makes them read as one set rather than four cells
    // (strategic 3.1.2); the same seed and floor the weather gadget uses.
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val minSpanW: Int = 1
    override val minSpanH: Int = 1
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        TechnicalGadgetView(container.context, key, iconRes, provider)
}

/**
 * Polls only while the cell is attached and the host is STARTED, because [LauncherGadgetView.onActive]
 * is cancelled on detach. That is the whole answer to the set's biggest risk - four gadgets polling a
 * device they are no longer visible on would make the battery gadget a reason to charge the phone.
 */
private class TechnicalGadgetView(
    context: Context,
    private val key: String,
    @DrawableRes iconRes: Int,
    private val provider: DeviceStatusProvider<Any>,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherTechnicalBinding.inflate(LayoutInflater.from(context), this)

    private val formatter = TechnicalGadgetFormatter(context)

    init {
        binding.gadgetTechnicalIcon.setImageResource(iconRes)
    }

    override suspend fun CoroutineScope.onActive() {
        while (isActive) {
            render(formatter.format(provider.read()))
            // The period belongs to the metric, not to this view: uptime moves every second while total
            // RAM never moves at all, and the view cannot know which it is drawing.
            delay(provider.refreshIntervalMs)
        }
    }

    private fun render(content: TechnicalGadgetContent) {
        binding.gadgetTechnicalValue.text = content.value
        binding.gadgetTechnicalCaption.text = content.caption
        // TalkBack gets the number AND what it means; the bar below is decoration on top of that.
        contentDescription = content.description
        val fill = content.fill
        binding.gadgetTechnicalFill.isVisible = fill != null
        if (fill != null) {
            binding.gadgetTechnicalFill.setProgressCompat((fill * PERCENT_SCALE).toInt(), true)
        }
    }

    private companion object {
        /** The bar is declared with `android:max="100"`, so a 0..1 fraction scales by a hundred. */
        const val PERCENT_SCALE = 100
    }
}
