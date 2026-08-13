package com.sza.fastmediasorter.ui.launcher.gadget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherStepsBinding
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveStepCountUseCase
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

/**
 * S1179: the system step counter on the desktop.
 *
 * The tile shows what `TYPE_STEP_COUNTER` reports - steps since the last reboot - and computes
 * nothing on top of it. A daily or since-reset total would need a stored starting point this ticket
 * never specifies, and strategic §5.1 says a gadget reads its source rather than deriving from it.
 */
class StepsGadget @Inject constructor(
    private val availability: SensorAvailabilityRepository,
    private val observeStepCount: Lazy<ObserveStepCountUseCase>,
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
        StepsGadgetView(container.context, observeStepCount.get())
}

private class StepsGadgetView(
    context: Context,
    private val observeStepCount: ObserveStepCountUseCase,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherStepsBinding.inflate(LayoutInflater.from(context), this)

    override suspend fun CoroutineScope.onActive() {
        if (!hasActivityPermission()) {
            showMessage()
            return
        }
        observeStepCount().collect { reading -> showSteps(reading.stepsSinceBoot) }
    }

    private fun showSteps(steps: Long) {
        val formatted = NumberFormat.getIntegerInstance(Locale.getDefault()).format(steps)
        binding.gadgetStepsValue.text = formatted
        binding.gadgetStepsCaption.isVisible = true
        binding.gadgetStepsMessage.isVisible = false
        contentDescription = context.getString(R.string.launcher_gadget_steps_description, formatted)
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
