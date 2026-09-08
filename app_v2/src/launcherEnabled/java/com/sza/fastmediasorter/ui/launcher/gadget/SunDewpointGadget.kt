package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherSunDewpointBinding
import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.model.weather.WeatherSnapshot
import com.sza.fastmediasorter.domain.model.weather.WeatherUnit
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.WeatherResult
import com.sza.fastmediasorter.domain.usecase.weather.GetLauncherWeatherUseCase
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * S1907: today's sunrise and sunset plus the dew point, for one place on the launcher desktop.
 *
 * Reads the same [WeatherSnapshot] the weather cell does rather than a model of its own (strategic
 * ADR-1): both ask one provider about one city, so a second path would mean a second request and a
 * second cache policy where one is needed.
 */
class SunDewpointGadget @Inject constructor(
    private val getWeather: Lazy<GetLauncherWeatherUseCase>,
    private val settingsRepository: Lazy<SettingsRepository>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_SUN_DEWPOINT

    // Three captioned lines at the footprint the weather and world-clock cells already use.
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_sun_dewpoint
    override val iconRes: Int = R.drawable.ic_sun_dewpoint

    // Flat single-fill vector, so it is invisible on the picker's light surface without a tint (S2062).
    override val iconTintable: Boolean = true

    // The place is not a registered resource, so the add flow asks for it with the weather city picker.
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        SunDewpointGadgetView(container.context, param, getWeather.get(), settingsRepository.get())
}

private class SunDewpointGadgetView(
    context: Context,
    param: String?,
    private val getWeather: GetLauncherWeatherUseCase,
    private val settingsRepository: SettingsRepository,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherSunDewpointBinding.inflate(LayoutInflater.from(context), this)

    // The same codec the weather cell reads, so one picker serves both (strategic ADR-2).
    private val location: WeatherLocation? = WeatherLocation.decode(param)

    init {
        contentDescription = context.getString(R.string.launcher_gadget_sun_dewpoint_actions)
        // No stock "sun app" exists to hand the tap to, unlike the weather cell, so a tap refreshes.
        setOnClickListener { refreshOnTap() }
    }

    private fun refreshOnTap() {
        val place = location ?: return
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            render(getWeather(place, forceRefresh = true))
        }
    }

    override suspend fun CoroutineScope.onActive() {
        val place = location
        if (place == null) {
            showMessage(R.string.launcher_gadget_sun_dewpoint_no_location)
            return
        }
        // The weather cell's cadence, deliberately not a second one: two cells on the same city must
        // land on the same cached reading rather than each keeping the other's entry warm.
        // S2716: restarted by a unit-system change for the reason the weather cell states - the dew
        // point rides the same snapshot, so both cards must switch scale in the same moment.
        settingsRepository.getSettings()
            .map { it.unitSystem }
            .distinctUntilChanged()
            .collectLatest {
                while (currentCoroutineContext().isActive) {
                    render(getWeather(place))
                    delay(REFRESH_INTERVAL_MS)
                }
            }
    }

    private fun render(result: WeatherResult) {
        when (result) {
            is WeatherResult.Fresh -> showSnapshot(result.snapshot, stale = false)
            is WeatherResult.Stale -> showSnapshot(result.snapshot, stale = true)
            WeatherResult.Unavailable -> showMessage(R.string.launcher_gadget_sun_dewpoint_unavailable)
        }
    }

    private fun showSnapshot(snapshot: WeatherSnapshot, stale: Boolean) {
        binding.gadgetSunDewpointIcon.isVisible = true
        binding.gadgetSunDewpointCaption.isVisible = false
        // Per value, not per card: one field the provider skipped this cycle is not the same failure as
        // no snapshot at all, and blanking three readable lines over one gap would say that it is.
        binding.gadgetSunDewpointSunrise.text =
            captioned(R.string.launcher_gadget_sun_dewpoint_sunrise, snapshot.sunrise?.let(::formatTime))
        binding.gadgetSunDewpointSunset.text =
            captioned(R.string.launcher_gadget_sun_dewpoint_sunset, snapshot.sunset?.let(::formatTime))
        binding.gadgetSunDewpointDew.text = captioned(
            R.string.launcher_gadget_sun_dewpoint_dew,
            snapshot.dewPoint?.let { formatDewPoint(it, snapshot.unit) },
        )
        binding.gadgetSunDewpointPlace.text = snapshot.location.label
        binding.gadgetSunDewpointPlace.isVisible = true
        binding.gadgetSunDewpointMessage.isVisible = stale
        if (stale) {
            binding.gadgetSunDewpointMessage.setText(R.string.launcher_gadget_weather_stale)
        }
        describeForScreenReader(snapshot.location.label)
    }

    /**
     * Strategic §3.2: a screen reader gets the values, not just the gadget's name - the three lines are
     * separate views, so without this the card announces only whichever one has focus.
     */
    private fun describeForScreenReader(placeLabel: String) {
        val values = listOf(
            binding.gadgetSunDewpointSunrise.text,
            binding.gadgetSunDewpointSunset.text,
            binding.gadgetSunDewpointDew.text,
            placeLabel,
        ).joinToString(VALUE_SEPARATOR)
        val actions = context.getString(R.string.launcher_gadget_sun_dewpoint_actions)
        contentDescription = values + VALUE_SEPARATOR + actions
    }

    /** Keeps whatever is already on screen - an outage must not blank a readable card (S1587). */
    private fun showMessage(messageRes: Int) {
        binding.gadgetSunDewpointMessage.setText(messageRes)
        binding.gadgetSunDewpointMessage.isVisible = true
        val hasReading = binding.gadgetSunDewpointSunrise.text.isNotEmpty()
        binding.gadgetSunDewpointPlace.isVisible = hasReading
        binding.gadgetSunDewpointIcon.isVisible = hasReading
        binding.gadgetSunDewpointCaption.isVisible = !hasReading
    }

    /** "Sunrise 06:12", and the caption survives a value the provider did not send this cycle. */
    private fun captioned(@StringRes captionRes: Int, value: String?): String {
        val shown = value ?: context.getString(R.string.launcher_gadget_sun_dewpoint_value_missing)
        return context.getString(captionRes) + VALUE_SEPARATOR_SHORT + shown
    }

    /**
     * The digits are the PLACE's own wall-clock time, so they are planted into a local calendar and read
     * back by a local formatter: both halves use one zone, which is what makes the printed hour survive a
     * device sitting in a different one. Formatting the instant instead would shift it (research/01 §4).
     */
    private fun formatTime(time: LocalTime): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return DateFormat.getTimeFormat(context).format(calendar.time)
    }

    /** The weather cell's own formatting, keyed off the same unit - one desktop, one kind of degree. */
    private fun formatDewPoint(value: Double, unit: WeatherUnit): String {
        val suffix = if (unit == WeatherUnit.FAHRENHEIT) FAHRENHEIT_SUFFIX else CELSIUS_SUFFIX
        return String.format(Locale.getDefault(), TEMPERATURE_FORMAT, value) + suffix
    }

    private companion object {
        val REFRESH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(20)
        const val TEMPERATURE_FORMAT = "%.0f"
        const val CELSIUS_SUFFIX = "°"
        const val FAHRENHEIT_SUFFIX = "°F"
        const val VALUE_SEPARATOR = ", "
        const val VALUE_SEPARATOR_SHORT = " "
    }
}
