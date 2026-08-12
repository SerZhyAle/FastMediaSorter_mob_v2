package com.sza.fastmediasorter.ui.launcher.gadget

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherWeatherBinding
import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.model.weather.WeatherSnapshot
import com.sza.fastmediasorter.domain.model.weather.WeatherUnit
import com.sza.fastmediasorter.domain.repository.WeatherResult
import com.sza.fastmediasorter.domain.usecase.weather.GetLauncherWeatherUseCase
import com.sza.fastmediasorter.util.resolveActivityCompat
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * S0426: current conditions for one place on the launcher desktop, on keyless Open-Meteo data.
 */
class WeatherGadget @Inject constructor(
    private val getWeather: Lazy<GetLauncherWeatherUseCase>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_WEATHER
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val minSpanW: Int = 1
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_weather
    override val iconRes: Int = R.drawable.ic_weather_partly_cloudy
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        WeatherGadgetView(container.context, param, getWeather.get())
}

private class WeatherGadgetView(
    context: Context,
    param: String?,
    private val getWeather: GetLauncherWeatherUseCase,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherWeatherBinding.inflate(LayoutInflater.from(context), this)

    private val location: WeatherLocation? = WeatherLocation.decode(param)

    init {
        contentDescription = context.getString(R.string.launcher_gadget_weather_actions)
        setOnClickListener { openWeatherApp(context) }
    }

    override suspend fun CoroutineScope.onActive() {
        val place = location
        if (place == null) {
            showMessage(R.string.launcher_gadget_weather_no_location)
            return
        }
        while (isActive) {
            when (val result = getWeather(place)) {
                is WeatherResult.Fresh -> showSnapshot(result.snapshot, stale = false)
                is WeatherResult.Stale -> showSnapshot(result.snapshot, stale = true)
                WeatherResult.Unavailable -> showMessage(R.string.launcher_gadget_weather_unavailable)
            }
            delay(REFRESH_INTERVAL_MS)
        }
    }

    private fun showSnapshot(snapshot: WeatherSnapshot, stale: Boolean) {
        binding.gadgetWeatherIcon.setImageResource(iconFor(snapshot.condition, snapshot.isDay))
        binding.gadgetWeatherIcon.isVisible = true
        binding.gadgetWeatherCaption.isVisible = false
        binding.gadgetWeatherTemperature.text = formatTemperature(snapshot)
        binding.gadgetWeatherPlace.text = snapshot.location.label
        binding.gadgetWeatherPlace.isVisible = true
        binding.gadgetWeatherMessage.isVisible = stale
        if (stale) {
            binding.gadgetWeatherMessage.setText(R.string.launcher_gadget_weather_stale)
        }
    }

    /** Keeps whatever value is already on screen - an outage must not blank a readable block. */
    private fun showMessage(messageRes: Int) {
        binding.gadgetWeatherMessage.setText(messageRes)
        binding.gadgetWeatherMessage.isVisible = true
        val hasReading = binding.gadgetWeatherTemperature.text.isNotEmpty()
        binding.gadgetWeatherPlace.isVisible = hasReading
        // S1587: with no reading the icon holds a third of the card for an empty image, which is what
        // pushed the message text into the middle of a first-run card; the name takes that role instead.
        binding.gadgetWeatherIcon.isVisible = hasReading
        binding.gadgetWeatherCaption.isVisible = !hasReading
        Timber.d("S1587: weather card message state, hasReading=$hasReading")
    }

    private fun formatTemperature(snapshot: WeatherSnapshot): String {
        val suffix = if (snapshot.unit == WeatherUnit.FAHRENHEIT) FAHRENHEIT_SUFFIX else CELSIUS_SUFFIX
        return String.format(Locale.getDefault(), TEMPERATURE_FORMAT, snapshot.temperature) + suffix
    }

    /**
     * Opens whichever weather app the device has; a stock weather app is not guaranteed, so a web
     * search for the same place is the fallback and "nothing resolves" is a silent no-op rather than a
     * crash on the home screen (same contract as the clock gadget).
     */
    private fun openWeatherApp(context: Context) {
        val packageManager = context.packageManager
        val installed = WEATHER_PACKAGES
            .firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        val intent = installed ?: Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, searchQuery(context))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (installed == null && packageManager.resolveActivityCompat(intent) == null) {
            Timber.i("Launcher weather gadget: no weather app and no search app to open")
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Launcher weather gadget: weather app refused to open") }
    }

    private fun searchQuery(context: Context): String {
        val label = location?.label.orEmpty()
        val weatherWord = context.getString(R.string.launcher_gadget_weather)
        return if (label.isEmpty()) weatherWord else "$weatherWord $label"
    }

    private companion object {
        val REFRESH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(20)
        const val TEMPERATURE_FORMAT = "%.0f"
        const val CELSIUS_SUFFIX = "°"
        const val FAHRENHEIT_SUFFIX = "°F"

        /** Stock weather apps, most common first; all are optional and usually absent on AOSP. */
        val WEATHER_PACKAGES = listOf(
            "com.google.android.apps.weather",
            "com.sec.android.daemonapp",
            "com.miui.weather2",
            "com.oneplus.weather",
            "com.huawei.android.totemweather",
        )
    }
}
