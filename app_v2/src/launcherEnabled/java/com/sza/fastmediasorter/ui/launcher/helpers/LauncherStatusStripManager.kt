package com.sza.fastmediasorter.ui.launcher.helpers

import android.graphics.Rect
import android.view.LayoutInflater
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.databinding.LauncherStatusClockBinding
import com.sza.fastmediasorter.databinding.LauncherStatusIndicatorsBinding
import com.sza.fastmediasorter.databinding.LauncherStatusStripBinding
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignal
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalListBottomSheet
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalRegistry
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayCallbacks
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayComposition
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * S1421 ADR-2: the one node that decides what the freed system status band shows.
 *
 * Two tickets writing into the same band independently would make the applied order accidental and push the
 * conflict into a third, so this class is the only place allowed to set the strip's visibility or to add a
 * child to its content slot. The band's height never changes with its content - that is an owner ruling, not
 * a layout convenience.
 *
 * What occupies the band while no signal is active is the one decision still open in strategic S1421 §5.2.
 * Until it is answered the slot stays empty at full height; nothing here guesses content for it.
 */
class LauncherStatusStripManager @Inject constructor(
    private val signalRegistry: LauncherSignalRegistry,
) : DefaultLifecycleObserver {

    private var binding: LauncherStatusStripBinding? = null

    private var fragmentManager: FragmentManager? = null

    /** Held only so [unbind] can unregister this observer on the same edge [bind] registered it. */
    private var lifecycleOwner: LifecycleOwner? = null

    /**
     * S1431: the strip's own copies of the two shared layouts. Inflated once per [bind] and pinned or
     * unpinned as the mode changes - re-inflating them on every toggle would mean a new renderer over new
     * views each time, and the old one would keep collecting into a detached hierarchy.
     */
    private var stripClock: LauncherStatusClockBinding? = null

    private var stripIndicators: LauncherStatusIndicatorsBinding? = null

    /** The one renderer (ADR-1) drawing the strip's indicators; the taskbar tray has its own instance. */
    private var stripTray: LauncherTrayManager? = null

    private val _signals = MutableStateFlow<List<LauncherSignal>>(emptyList())

    /** What the strip currently has to show. The row phase renders this; nothing else reads the sources. */
    val signals: StateFlow<List<LauncherSignal>> = _signals.asStateFlow()

    private val _cutoutBounds = MutableStateFlow(Rect())

    /**
     * Horizontal span of the top display cutout in window coordinates, empty when the device has none.
     *
     * The row splits around this rather than around a per-device constant (S1421 ADR-3), and it comes from
     * the cutout's bounding rect rather than from `displayCutout()` insets: a camera island in the middle of
     * the top edge produces a top inset and no horizontal one at all, so the insets alone cannot say where
     * the hole is.
     */
    val cutoutBounds: StateFlow<Rect> = _cutoutBounds.asStateFlow()

    /**
     * Follow the replacement policy and the signal list for as long as [lifecycleOwner] is started. Call once
     * from the host; the strip keeps itself in sync from there.
     */
    fun bind(
        binding: LauncherStatusStripBinding,
        lifecycleOwner: LifecycleOwner,
        fragmentManager: FragmentManager,
        replaceSystemStatusArea: Flow<Boolean>,
        topStatusStripMode: Flow<Boolean>,
        trayComposition: Flow<LauncherTrayComposition>,
        callbacks: LauncherTrayCallbacks,
    ) {
        this.binding = binding
        this.fragmentManager = fragmentManager
        this.lifecycleOwner = lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(this)
        applySafeArea(binding)
        observeCutout(binding)
        binding.launcherSignalRow.setOnOverflowTap(::showSignalList)
        prepareStripContent(
            binding = binding,
            lifecycleOwner = lifecycleOwner,
            topStatusStripMode = topStatusStripMode,
            trayComposition = trayComposition,
            callbacks = callbacks,
        )
        lifecycleOwner.collectOnLifecycle(topStatusStripMode) { enabled ->
            applyStripMode(enabled)
        }
        lifecycleOwner.collectOnLifecycle(replaceSystemStatusArea) { replace ->
            // The band exists only while the launcher owns the status area; with the Android bar left in
            // place there is no freed space to draw in.
            this.binding?.root?.isVisible = replace
            // The inset the band must respect moves with the bar, so a stale one leaves either a gap or
            // content under the cutout (Rule 17). A re-dispatch is all it takes - the listener the padding
            // helper registered in bind() is still on this view and recomputes from the same base.
            this.binding?.root?.let(ViewCompat::requestApplyInsets)
        }
        lifecycleOwner.collectOnLifecycle(signalRegistry.observe()) { current ->
            _signals.value = current
            this.binding?.launcherSignalRow?.submit(
                signals = current,
                canOpen = { signalRegistry.open(it) != null },
                onTap = ::openSignal,
            )
        }
        lifecycleOwner.collectOnLifecycle(cutoutBounds) { bounds ->
            this.binding?.launcherSignalRow?.setCutoutBounds(bounds)
        }
    }

    /**
     * S1431: inflates the strip's clock and indicator row once and puts the shared renderer behind them.
     *
     * The renderer is [LauncherTrayManager], the same class the taskbar tray uses (ADR-1) - one set of
     * subscription, permission and ordering rules serves both placements, so the six toggles cannot mean
     * different things in the two places. It is handed the mode flow as its visibility gate, which is what
     * releases the battery receiver and the network callback when the mode goes off; the views are merely
     * unpinned, not destroyed.
     */
    private fun prepareStripContent(
        binding: LauncherStatusStripBinding,
        lifecycleOwner: LifecycleOwner,
        topStatusStripMode: Flow<Boolean>,
        trayComposition: Flow<LauncherTrayComposition>,
        callbacks: LauncherTrayCallbacks,
    ) {
        val row = binding.launcherSignalRow
        val inflater = LayoutInflater.from(row.context)
        val clock = LauncherStatusClockBinding.inflate(inflater, row, false)
        val indicators = LauncherStatusIndicatorsBinding.inflate(inflater, row, false)
        stripClock = clock
        stripIndicators = indicators
        applyStripClockFormat(clock)
        stripTray = LauncherTrayManager(
            lifecycleOwner = lifecycleOwner,
            clock = clock,
            indicators = indicators,
            callbacks = callbacks,
        ).apply { bind(topStatusStripMode, trayComposition) }
    }

    /**
     * The owner asked for seconds on this placement specifically (strategic §0); the taskbar clock keeps
     * the system default and is not touched here.
     *
     * No ticker, handler or time-tick receiver is introduced: `TextClock` declares
     * `onVisibilityAggregated(boolean)` and stops its own per-second tick when the launcher stops being
     * visible (research 01 §2), which is exactly the cost bound strategic §3.2 sets.
     */
    private fun applyStripClockFormat(clock: LauncherStatusClockBinding) {
        clock.root.format12Hour = CLOCK_FORMAT_12H
        clock.root.format24Hour = CLOCK_FORMAT_24H
    }

    /**
     * Attaching and detaching the two pinned views is the whole of the mode as far as the band is concerned
     * - the row decides where they sit (S1431 ADR-3) and the renderer decides what they say.
     */
    private fun applyStripMode(enabled: Boolean) {
        val row = binding?.launcherSignalRow ?: return
        row.setPinnedStart(if (enabled) stripClock?.root else null)
        row.setPinnedEnd(if (enabled) stripIndicators?.root else null)
    }

    /**
     * Horizontal edges only. Padding the top would push a band the height of the status bar out of the very
     * space it was created to occupy - the row belongs beside the cutout, not below it - and the bottom edge
     * is nowhere near this strip. `useStatusBarHeightFallback` is off for the reason the helper documents:
     * this surface controls the bar's visibility itself.
     */
    private fun applySafeArea(binding: LauncherStatusStripBinding) {
        binding.root.applySystemBarInsetPadding(
            applyTop = false,
            applyBottom = false,
            useStatusBarHeightFallback = false,
        )
    }

    /**
     * Listens on the content slot rather than on the root: the padding helper already owns the root's
     * inset listener, and a second listener on one view replaces the first. Insets are dispatched to both
     * and passed through here unconsumed.
     */
    private fun observeCutout(binding: LauncherStatusStripBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.launcherStatusStripContent) { _, insets ->
            _cutoutBounds.value = insets.topCutoutBounds()
            insets
        }
    }

    /**
     * `DisplayCutoutCompat` exposes only the full list of bounding rects - there is no `boundingRectTop` on
     * the compat type - so the top-edge ones are selected here and unioned, which also covers a device with
     * two punch-holes along the same edge.
     */
    private fun WindowInsetsCompat.topCutoutBounds(): Rect {
        val topRects = displayCutout?.boundingRects?.filter { it.top <= 0 }.orEmpty()
        if (topRects.isEmpty()) {
            return Rect()
        }
        return Rect(
            topRects.minOf { it.left },
            0,
            topRects.maxOf { it.right },
            topRects.maxOf { it.bottom },
        )
    }

    /**
     * Symmetric with the shortcut and cell-action menus this surface already dismisses on stop: a sheet
     * opened over the home screen must not survive it leaving the foreground and reappear over whatever the
     * user opened next.
     */
    override fun onStop(owner: LifecycleOwner) {
        (fragmentManager?.findFragmentByTag(SIGNAL_LIST_TAG) as? LauncherSignalListBottomSheet)?.dismiss()
    }

    /**
     * Undoes [bind] in the same order it registered: the inset listener comes off the view before the view
     * reference goes, and the lifecycle observer comes off before its owner does. Nothing here relies on the
     * manager being short-lived - a teardown that only works because the object happens to die with the
     * window is the kind that breaks the day it is scoped differently.
     */
    fun unbind() {
        binding?.let { ViewCompat.setOnApplyWindowInsetsListener(it.launcherStatusStripContent, null) }
        applyStripMode(enabled = false)
        stripClock = null
        stripIndicators = null
        stripTray = null
        lifecycleOwner?.lifecycle?.removeObserver(this)
        lifecycleOwner = null
        binding = null
        fragmentManager = null
    }

    /**
     * Shows every active signal, not only the hidden ones: the counter stands for the overflow, but the list
     * behind it is the whole picture, and taps route through the same path a chip uses so there is one way
     * to open a signal rather than two.
     */
    private fun showSignalList() {
        val manager = fragmentManager ?: return
        if (manager.findFragmentByTag(SIGNAL_LIST_TAG) != null) {
            return
        }
        LauncherSignalListBottomSheet().apply {
            signals = _signals.value
            onTap = ::openSignal
        }.show(manager, SIGNAL_LIST_TAG)
    }

    /**
     * Opens the screen the signal belongs to (strategic §4.4). Guarded because the intent is built from state
     * that can go stale between the render and the tap - a transfer that just finished, a screen a later
     * build no longer exports - and a home surface must not die because one chip pointed at nothing.
     */
    private fun openSignal(signal: LauncherSignal) {
        val target = signalRegistry.open(signal) ?: return
        val host = binding?.root?.context ?: return
        runCatching { host.startActivity(target) }
            .onFailure { Timber.w(it, "Launcher status strip could not open signal %s", signal.id) }
    }

    private companion object {
        const val SIGNAL_LIST_TAG = "launcher_signal_list"

        // Mirrors gadget_launcher_clock.xml, so the two clocks the launcher can show read alike.
        const val CLOCK_FORMAT_12H = "h:mm:ss"
        const val CLOCK_FORMAT_24H = "H:mm:ss"
    }
}
