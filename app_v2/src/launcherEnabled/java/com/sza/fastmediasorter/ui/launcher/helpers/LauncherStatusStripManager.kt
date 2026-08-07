package com.sza.fastmediasorter.ui.launcher.helpers

import android.graphics.Rect
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.databinding.LauncherStatusStripBinding
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignal
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalListBottomSheet
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalRegistry
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
    ) {
        this.binding = binding
        this.fragmentManager = fragmentManager
        this.lifecycleOwner = lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(this)
        applySafeArea(binding)
        observeCutout(binding)
        binding.launcherSignalRow.setOnOverflowTap(::showSignalList)
        lifecycleOwner.collectOnLifecycle(replaceSystemStatusArea) { replace ->
            // The band exists only while the launcher owns the status area; with the Android bar left in
            // place there is no freed space to draw in.
            Timber.d("S1421: status strip visible=%s", replace)
            this.binding?.root?.isVisible = replace
            // The inset the band must respect moves with the bar, so a stale one leaves either a gap or
            // content under the cutout (Rule 17). Re-dispatch rather than re-applying the padding helper,
            // which would compound onto the padding it already added.
            this.binding?.root?.let(ViewCompat::requestApplyInsets)
        }
        lifecycleOwner.collectOnLifecycle(signalRegistry.observe()) { current ->
            Timber.d("S1421: signals=%d kinds=%s", current.size, current.map { it.kind })
            _signals.value = current
            this.binding?.launcherSignalRow?.submit(
                signals = current,
                canOpen = { signalRegistry.open(it) != null },
                onTap = ::openSignal,
            )
        }
        lifecycleOwner.collectOnLifecycle(cutoutBounds) { bounds ->
            Timber.d("S1421: cutout bounds=%s", bounds)
            this.binding?.launcherSignalRow?.setCutoutBounds(bounds)
        }
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
        Timber.d("S1421: overflow tapped, listing %d signal(s)", _signals.value.size)
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
        Timber.d("S1421: signal tapped id=%s kind=%s", signal.id, signal.kind)
        val target = signalRegistry.open(signal) ?: return
        val host = binding?.root?.context ?: return
        runCatching { host.startActivity(target) }
            .onFailure { Timber.w(it, "Launcher status strip could not open signal %s", signal.id) }
    }

    private companion object {
        const val SIGNAL_LIST_TAG = "launcher_signal_list"
    }
}
