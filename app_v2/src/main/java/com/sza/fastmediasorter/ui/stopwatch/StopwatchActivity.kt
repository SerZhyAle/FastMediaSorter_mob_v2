package com.sza.fastmediasorter.ui.stopwatch

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import com.google.android.material.slider.Slider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.input.TvNavAction
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityStopwatchBinding
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.stopwatch.helpers.MediaPlayerStopwatchAudioOutput
import com.sza.fastmediasorter.ui.stopwatch.helpers.StopwatchAudioManager
import com.sza.fastmediasorter.ui.stopwatch.helpers.StopwatchCommand
import com.sza.fastmediasorter.ui.stopwatch.helpers.StopwatchInputManager
import com.sza.fastmediasorter.ui.stopwatch.helpers.StopwatchRegionBinder
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * The stopwatch screen (strategic S1411).
 *
 * It holds view wiring only: the measurement lives in [StopwatchViewModel] so it survives rotation and
 * backgrounding, the drawing lives in [StopwatchRegionBinder] so the participant count decides the
 * layout and the focus order in one place, and the key-to-command translation lives in
 * [StopwatchInputManager] (Rule 3).
 *
 * This is the only screen in the application that consumes a volume key (ADR-2), which is why the
 * escape hatch and the on-screen volume control are part of the same class as the interception.
 */
@AndroidEntryPoint
class StopwatchActivity : BaseActivity<ActivityStopwatchBinding>() {

    private val viewModel: StopwatchViewModel by viewModels()

    private lateinit var regionBinder: StopwatchRegionBinder

    private val inputManager = StopwatchInputManager(
        participantCount = { viewModel.state.value.participantCount },
    )

    private var volumeSliderListener: Slider.OnChangeListener? = null

    /**
     * The accompaniment. Created lazily rather than in a field initialiser because it reaches the
     * platform through this Activity's context, and released in [onDestroy] as Rule 18 requires.
     */
    private val audioManager: StopwatchAudioManager by lazy {
        StopwatchAudioManager(MediaPlayerStopwatchAudioOutput(this))
    }

    /** The ADR-2 escape hatch, read from the tool's own setting rather than assumed. */
    private val volumeKeysDriveMeasurement: Boolean
        get() = viewModel.volumeKeysDriveMeasurement.value

    override fun getViewBinding(): ActivityStopwatchBinding =
        ActivityStopwatchBinding.inflate(layoutInflater)

    override fun setupViews() {
        binding.root.applySystemBarInsetPadding()
        binding.toolbar.setNavigationOnClickListener { finish() }
        regionBinder = StopwatchRegionBinder(
            binding = binding,
            onStartOrLap = viewModel::startOrLap,
            onStopOrReset = ::stopOrReset,
        )
        regionBinder.attachListeners()
        binding.btnStopwatchResetAll.setOnClickListener { viewModel.resetAll() }
        binding.btnStopwatchStartAll.setOnClickListener {
            Timber.d("S2792: start-all pressed, anyRunning=${viewModel.state.value.anyRunning}")
            if (viewModel.state.value.anyRunning) viewModel.stopAll() else viewModel.startAll()
        }
        binding.btnStopwatchResult.setOnClickListener { openResultDialog() }
        setupVolumeSlider()
        binding.btnStopwatchSettings.setOnClickListener {
            StopwatchSettingsDialogFragment.newInstance()
                .show(supportFragmentManager, StopwatchSettingsDialogFragment.TAG)
        }
        binding.btnStopwatchOpenSettings.setOnClickListener { openSettings() }
    }

    override fun observeData() {
        collectOnLifecycle(
            combine(viewModel.state, viewModel.now) { state, nowMillis -> state to nowMillis },
        ) { (state, nowMillis) ->
            regionBinder.bind(state, nowMillis)
        }
        // The accompaniment is driven by the state rather than by the button handlers, so a run started
        // by a volume key or a keyboard sounds the same as one started by a tap, and four participants
        // running together still produce one player (§5.2). S2613 folds the switch into the same
        // collector: it silences a measurement still running under a screen the user has switched off,
        // and `getSettings()` is a cold DataStore read, so a second collection would re-run that
        // pipeline for a flag this one already carries.
        collectOnLifecycle(
            combine(
                appSettings.map { it.enableStopwatch },
                viewModel.state.map { it.anyRunning },
            ) { programOn, anyRunning -> programOn to anyRunning }.distinctUntilChanged(),
        ) { (programOn, anyRunning) ->
            renderAvailability(programOn)
            audioManager.syncWith(programOn && anyRunning)
        }
        collectOnLifecycle(viewModel.trackUri) { uri -> audioManager.selectTrack(uri) }
    }

    override fun getInitialFocusView(): View? =
        if (binding.stopwatchFallbackGroup.isVisible) binding.btnStopwatchOpenSettings else null

    /**
     * S2613: what a widget cell pinned before the program was switched off now opens.
     *
     * The gate sits at the destination rather than in the cell's intent because a widget
     * `PendingIntent` is baked when the cell is drawn and nothing redraws it when the switch flips -
     * `GameLaunchWidgetProvider` only survives that by having the settings screen call it back. This
     * mirrors `CalculatorActivity.renderAvailability`, which is the shape S1411 phase 08 copied from.
     */
    private fun renderAvailability(enabled: Boolean) {
        Timber.d("S2613: stopwatch screen renders enableStopwatch=$enabled")
        binding.stopwatchContentGroup.isVisible = enabled
        binding.stopwatchFallbackGroup.isVisible = !enabled
    }

    /** The Operations tab hosts `rowEnableStopwatch`, which is the switch the fallback asks for. */
    private fun openSettings() {
        Timber.d("S2613: stopwatch fallback opens settings on TAB_OPERATIONS")
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_OPERATIONS)
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val command = inputManager.handleKeyEvent(event, addressedRegion())
        return when {
            command != null -> consume(command)
            // The base pipeline hands the ACTION_DOWN of a volume key to onTvNavigation below.
            // Consuming only that half would leave the system to act on the unmatched ACTION_UP and
            // change the volume anyway, so the rest of an intercepted press is swallowed here.
            isInterceptedVolumeRelease(event) -> true
            else -> super.dispatchKeyEvent(event)
        }
    }

    /**
     * Volume up starts or laps, volume down stops. Everything else keeps its base behaviour, and with
     * the escape hatch off - or on the availability fallback - the system gets the keys back.
     *
     * The region resolves with a touch-mode default rather than requiring focus (S2792), because in
     * touch mode no view is focused and the old root-focus test dropped every press to the system.
     */
    override fun onTvNavigation(action: TvNavAction): Boolean {
        val participantId = volumeKeyRegion()
        if (!volumeKeysDriveMeasurement || participantId == StopwatchRegionBinder.NO_REGION) {
            return false
        }
        Timber.d("S2792: volume key drives participant $participantId ($action)")
        return when (action) {
            TvNavAction.VolumeUp -> consume(StopwatchCommand.StartOrLap(participantId))
            TvNavAction.VolumeDown -> consume(StopwatchCommand.Stop(participantId))
            else -> false
        }
    }

    override fun onStop() {
        // The accompaniment belongs to this screen (§6.2). The measurement keeps running on its own
        // timestamps, but music from a screen the user has left carries no visible control to silence
        // it and no notification, which is exactly what the no-foreground-service decision rules out.
        // Coming back re-emits the running state and the collector starts it again.
        audioManager.syncWith(anyRunning = false)
        super.onStop()
    }

    override fun onDestroy() {
        // Before super, which nulls the binding this listener is read off.
        volumeSliderListener?.let { binding.sliderStopwatchVolume.removeOnChangeListener(it) }
        volumeSliderListener = null
        audioManager.release()
        super.onDestroy()
    }

    /**
     * Gives the volume back on screen, because the hardware keys no longer change it (§7).
     *
     * Hidden rather than shown-but-dead on a build with no audio: there is nothing for it to change.
     */
    private fun setupVolumeSlider() {
        val maxVolume = audioManager.maxStreamVolume.toFloat()
        val available = viewModel.audioAvailable && maxVolume > 0f
        binding.stopwatchVolumeRow.isVisible = available
        if (!available) return

        val slider = binding.sliderStopwatchVolume
        slider.valueFrom = 0f
        slider.valueTo = maxVolume
        slider.value = audioManager.streamVolume.toFloat().coerceIn(0f, maxVolume)
        val listener = Slider.OnChangeListener { _, value, fromUser ->
            if (fromUser) {
                audioManager.streamVolume = value.toInt()
            }
        }
        slider.addOnChangeListener(listener)
        volumeSliderListener = listener
    }

    /** Applies [command] and reports the event as consumed, so no other handler sees it. */
    private fun consume(command: StopwatchCommand): Boolean {
        when (command) {
            is StopwatchCommand.StartOrLap -> viewModel.startOrLap(command.participantId)
            is StopwatchCommand.Stop -> viewModel.stop(command.participantId)
            is StopwatchCommand.Reset -> viewModel.reset(command.participantId)
            is StopwatchCommand.FocusRegion -> regionBinder.focusParticipant(command.participantId)
        }
        return true
    }

    /**
     * The other half of an intercepted press. It repeats the addressability test rather than trusting
     * that the press was taken, so a release whose press went to the system is not swallowed here -
     * that would let the volume move on the way down and stick on the way up.
     */
    private fun isInterceptedVolumeRelease(event: KeyEvent): Boolean =
        volumeKeysDriveMeasurement &&
            event.keyCode in VOLUME_KEYS &&
            event.action != KeyEvent.ACTION_DOWN &&
            volumeKeyRegion() != StopwatchRegionBinder.NO_REGION

    /**
     * The region a region-less centre/enter key belongs to. The volume vocabulary resolves through
     * [volumeKeyRegion] instead (S2792).
     *
     * Key dispatch can outrun [setupViews] on a cold start, so the binder is asked only once it exists.
     */
    private fun addressedRegion(): Int {
        // S2613: a region's own view stays VISIBLE under a hidden content group, and with one
        // participant the binder names it without consulting focus at all - so the fallback screen
        // would keep swallowing the volume keys and driving a measurement the user cannot see.
        if (!::regionBinder.isInitialized || !binding.stopwatchContentGroup.isVisible) {
            return StopwatchRegionBinder.NO_REGION
        }
        val participantId = regionBinder.focusedParticipantId()
        return participantId.takeIf { it in 0 until viewModel.state.value.participantCount }
            ?: StopwatchRegionBinder.NO_REGION
    }

    /**
     * The region a volume key drives: the same S2613 availability gate as [addressedRegion], but the
     * resolution carries a touch-mode default instead of requiring focus (S2792).
     */
    private fun volumeKeyRegion(): Int {
        if (!::regionBinder.isInitialized || !binding.stopwatchContentGroup.isVisible) {
            return StopwatchRegionBinder.NO_REGION
        }
        val participantId = regionBinder.volumeKeyParticipantId()
        return participantId.takeIf { it in 0 until viewModel.state.value.participantCount }
            ?: StopwatchRegionBinder.NO_REGION
    }

    /**
     * Opens the export surface, or says why there is nothing to export.
     *
     * A result assembled from four untouched participants would be a page of zeroes, which reads as a
     * broken export rather than as an empty one.
     */
    private fun openResultDialog() {
        if (viewModel.state.value.visibleParticipants.none { it.hasProgress }) {
            Toast.makeText(this, R.string.stopwatch_result_empty, Toast.LENGTH_SHORT).show()
            return
        }
        StopwatchResultDialogFragment.newInstance()
            .show(supportFragmentManager, StopwatchResultDialogFragment.TAG)
    }

    /** One secondary button for two jobs: it halts a running measurement, and clears a halted one. */
    private fun stopOrReset(participantId: Int) {
        val participant = viewModel.state.value.participants.getOrNull(participantId)
        if (participant?.running == true) {
            viewModel.stop(participantId)
        } else {
            viewModel.reset(participantId)
        }
    }

    companion object {

        private val VOLUME_KEYS = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
        )

        fun createIntent(context: Context): Intent = Intent(context, StopwatchActivity::class.java)
    }
}
