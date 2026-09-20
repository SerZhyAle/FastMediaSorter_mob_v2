package com.sza.fastmediasorter.ui.sos

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import com.google.android.material.button.MaterialButtonToggleGroup
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivitySosBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.sos.SosMode
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * S3216: the phone's distress signal - the window that starts it, reshapes it and stops it.
 *
 * The signal itself lives in [SosService] and not here, so it survives this window being destroyed
 * (strategic §6, Resolved). What the activity owns is the second lamp: the root background flashes on the
 * same Morse beat as the torch, which is what makes a phone with no flash unit still a light.
 *
 * Reachable three ways - the programs surfaces, the Tourist dashboard, and an incoming start from the
 * paired watch - and the third is why [EXTRA_MODE] exists: a signal started on the watch must come up
 * here in the same mode rather than in whatever this phone last used.
 */
@AndroidEntryPoint
class SosActivity : BaseActivity<ActivitySosBinding>() {

    private val viewModel: SosViewModel by viewModels()

    override fun getViewBinding(): ActivitySosBinding = ActivitySosBinding.inflate(layoutInflater)

    /**
     * The window is half the signal, so it may not go dark while the signal runs. The flag lives on this
     * window, so [BaseActivity] drops it with the window and the hold cannot outlive the program.
     */
    override fun keepScreenAwakeFor(settings: AppSettings): Boolean = true

    /**
     * Asked for here rather than in the manifest: both attributes exist only from API 27 and the
     * `legacy` flavor builds against 23, so a manifest declaration would be a lint failure on it. A
     * start arriving from the watch has to reach the screen of a phone lying locked in a pack.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    /**
     * Held as a field so [onDestroy] can hand the same instance back: `addOnButtonCheckedListener` has a
     * matching `remove`, and a group that outlives this window - it does not, but the symmetry is what
     * the listener gate reads - would keep calling into a dead view model.
     */
    private val modeCheckedListener =
        MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                viewModel.select(modeOf(checkedId))
            }
        }

    /** The stop button finishes the window, so onDestroy must not send a second stop behind it. */
    private var stopRequested = false

    override fun setupViews() {
        binding.btnSosStop.setOnClickListener { stopAndLeave() }
        binding.groupSosMode.addOnButtonCheckedListener(modeCheckedListener)
        viewModel.resolveInitialMode(intent?.getStringExtra(EXTRA_MODE)?.let(SosMode::fromNameOrDefault))
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.mode) { mode -> mode?.let(::applyMode) }
        collectOnLifecycle(viewModel.isLit) { lit -> renderStrobe(lit) }
    }

    /** Leaving by the system back gesture must end the signal too - a hidden siren is not a paused one. */
    override fun onDestroy() {
        binding.groupSosMode.removeOnButtonCheckedListener(modeCheckedListener)
        binding.btnSosStop.setOnClickListener(null)
        applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        if (isFinishing && !stopRequested) {
            viewModel.requestStopEverywhere()
            SosService.stop(this)
        }
        super.onDestroy()
    }

    /**
     * Sends the mode to the service and reflects it in the chip group.
     *
     * The service is re-started rather than asked to change: both halves of the signal are idempotent,
     * so one entry point both starts and reshapes it, and there is no second command to keep in step.
     */
    private fun applyMode(mode: SosMode) {
        val target = buttonIdOf(mode)
        if (binding.groupSosMode.checkedButtonId != target) {
            binding.groupSosMode.check(target)
        }
        // A mode without light leaves the window at its resting colour rather than at whatever phase
        // the previous mode was in when it was switched.
        if (!mode.engagesLight) {
            renderStrobe(lit = false)
        }
        applyWindowBrightness(
            if (mode.engagesLight) MAX_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        )
        SosService.start(this, mode)
    }

    /**
     * S3333: the strobe is only as bright as the window it flashes in, and a phone that dimmed itself
     * before the signal started flashes at that dimmed level - which is what a daylight rescuer does not
     * see. Only this window's attribute is touched, never the device's own brightness setting, so leaving
     * the window is the whole of the restore.
     */
    private fun applyWindowBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }

    private fun renderStrobe(lit: Boolean) {
        binding.viewSosFlash.visibility = if (lit) View.VISIBLE else View.GONE
    }

    private fun stopAndLeave() {
        stopRequested = true
        viewModel.requestStopEverywhere()
        SosService.stop(this)
        finish()
    }

    private fun modeOf(buttonId: Int): SosMode = when (buttonId) {
        R.id.btnSosModeSoundOnly -> SosMode.SOUND_ONLY
        R.id.btnSosModeLightOnly -> SosMode.LIGHT_ONLY
        else -> SosMode.ALL
    }

    private fun buttonIdOf(mode: SosMode): Int = when (mode) {
        SosMode.ALL -> R.id.btnSosModeAll
        SosMode.SOUND_ONLY -> R.id.btnSosModeSoundOnly
        SosMode.LIGHT_ONLY -> R.id.btnSosModeLightOnly
    }

    companion object {

        private const val MAX_BRIGHTNESS = 1.0f

        const val EXTRA_MODE = "sos_mode"

        fun createIntent(context: Context): Intent = Intent(context, SosActivity::class.java)

        /** The window opened in a named mode, which is how a start from the paired watch arrives. */
        fun createIntent(context: Context, mode: SosMode): Intent =
            createIntent(context).putExtra(EXTRA_MODE, mode.name)
    }
}
