package com.sza.fastmediasorter.ui.launcher.gadget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherStepsBinding
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveStepCountUseCase
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * S1179 / S2239: the system step counter on the desktop.
 *
 * Displays steps count relative to the last user reset timestamp or since boot if not reset.
 * Tapping the widget resets the current count and records the reset date and time.
 */
class StepsGadget @Inject constructor(
    private val availability: SensorAvailabilityRepository,
    private val observeStepCount: Lazy<ObserveStepCountUseCase>,
    private val settingsRepository: Lazy<SettingsRepository>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_STEPS
    override val defaultSpanW: Int = 1
    override val defaultSpanH: Int = 1
    override val minSpanW: Int = 1
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_steps
    override val iconRes: Int = R.drawable.ic_steps
    override val requiresResourceParam: Boolean = false

    override fun isAvailable(): Boolean = availability.isAvailable(SensorCapability.STEP_COUNTER)

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        StepsGadgetView(container.context, observeStepCount.get(), settingsRepository.get())
}

private class StepsGadgetView(
    context: Context,
    private val observeStepCount: ObserveStepCountUseCase,
    private val settingsRepository: SettingsRepository,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherStepsBinding.inflate(LayoutInflater.from(context), this)

    private var activeScope: CoroutineScope? = null
    private var latestStepsSinceBoot: Long = 0L

    init {
        binding.gadgetStepsBody.setOnClickListener { resetSteps() }
    }

    override suspend fun CoroutineScope.onActive() {
        activeScope = this
        try {
            if (!hasActivityPermission()) {
                showMessage()
                return
            }
            combine(
                observeStepCount(),
                settingsRepository.getSettings(),
            ) { reading, settings ->
                reading.stepsSinceBoot to settings
            }.collect { (stepsSinceBoot, settings) ->
                latestStepsSinceBoot = stepsSinceBoot
                showSteps(
                    stepsSinceBoot = stepsSinceBoot,
                    resetCount = settings.launcherStepsResetCount,
                    resetTimestamp = settings.launcherStepsResetTimestamp,
                )
            }
        } finally {
            activeScope = null
        }
    }

    private fun resetSteps() {
        val current = latestStepsSinceBoot
        if (current <= 0L) return
        val now = System.currentTimeMillis()
        activeScope?.launch {
            settingsRepository.updateSettings { currentSettings ->
                currentSettings.copy(
                    launcher = currentSettings.launcher.copy(
                        stepsResetCount = current,
                        stepsResetTimestamp = now,
                    ),
                )
            }
        }
    }

    private fun showSteps(
        stepsSinceBoot: Long,
        resetCount: Long,
        resetTimestamp: Long,
    ) {
        val displaySteps = maxOf(0L, stepsSinceBoot - resetCount)
        val formattedSteps = NumberFormat.getIntegerInstance(Locale.getDefault()).format(displaySteps)
        binding.gadgetStepsValue.text = formattedSteps
        binding.gadgetStepsCaption.isVisible = true
        binding.gadgetStepsMessage.isVisible = false

        if (resetTimestamp > 0L) {
            val formattedDate = DateFormat.getDateFormat(context).format(Date(resetTimestamp))
            val formattedTime = DateFormat.getTimeFormat(context).format(Date(resetTimestamp))
            val dateTimeString = "$formattedDate $formattedTime"
            binding.gadgetStepsCaption.text =
                context.getString(R.string.launcher_gadget_steps_since_date, dateTimeString)
        } else {
            binding.gadgetStepsCaption.setText(R.string.launcher_gadget_steps_since_boot)
        }

        contentDescription = context.getString(R.string.launcher_gadget_steps_description, formattedSteps)
    }

    /** The caption is hidden with the value: a denominator over an empty number says nothing. */
    private fun showMessage() {
        val message = context.getString(R.string.launcher_gadget_sensor_no_permission)
        binding.gadgetStepsMessage.text = message
        binding.gadgetStepsMessage.isVisible = true
        binding.gadgetStepsCaption.isVisible = binding.gadgetStepsValue.text.isNotEmpty()
        contentDescription = message
    }

    private fun hasActivityPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
}
