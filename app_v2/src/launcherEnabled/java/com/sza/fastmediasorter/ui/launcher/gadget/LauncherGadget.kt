package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S0404: one kind of interactive block the user can place on the desktop (ADR-5 - our own views only,
 * never third-party AppWidgets).
 *
 * A gadget never sizes itself: [LauncherDesktopLayout][com.sza.fastmediasorter.ui.launcher.grid.LauncherDesktopLayout]
 * measures its cell EXACTLY at `cellSize x spanW/spanH`, so the view simply fills the container it is
 * handed. Setting `layoutParams.height` from a gadget fights the grid (ADR-9).
 */
interface LauncherGadget {

    /** Stable id persisted inside a GADGET cell's `target` - never localise or renumber it. */
    val key: String

    val defaultSpanW: Int
    val defaultSpanH: Int

    /** The resize floor (S1093). Defaults to the seed size, so a gadget wanting a bigger seed than its
     *  floor - like the clock - overrides these; every other gadget keeps min = default. */
    val minSpanW: Int get() = defaultSpanW
    val minSpanH: Int get() = defaultSpanH

    @get:StringRes
    val labelRes: Int

    @get:DrawableRes
    val iconRes: Int

    /**
     * True when [iconRes] is a flat single-colour glyph meant to inherit the surrounding theme's colour
     * (S2062), as opposed to a branded or multi-colour icon (a YouTube logo, an `_accent` widget glyph)
     * that a tint would flatten to one colour. Defaults to false so every gadget stays untinted unless
     * its own drawable is confirmed a plain white/constant-filled vector.
     */
    val iconTintable: Boolean get() = false

    /** True when the gadget is meaningless without a resource picked at add time (Phase 07). */
    val requiresResourceParam: Boolean

    /**
     * S1179: whether this device has the hardware the gadget needs, and nothing else. A refused
     * runtime permission is a separate axis and must not be folded in here - a denied grant would
     * then delete the gadget from the picker instead of leaving it on the desktop in an honest empty
     * state, which is what strategic §11.5 requires.
     */
    fun isAvailable(): Boolean = true

    fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View
}

/**
 * What a gadget is allowed to ask of the desktop.
 *
 * Deliberately NOT `suspend fun execute(command): Boolean` as the phase file first sketched: the
 * desktop, both taskbar strips and the Start menu already share ONE launch guard and one "cannot open"
 * message inside `LauncherHomeViewModel.run`. Handing gadgets a Boolean would invite them to build a
 * fifth failure path around the guard - the exact duplication Phase 05 collapsed.
 *
 * No `lifecycleOwner` either: a gadget view is removed and re-created on every desktop rebind, while
 * the host's lifecycle outlives it, so anything scoped to the host leaks one collector per rebind.
 * Gadgets scope their work to the view instead - see [LauncherGadgetView].
 */
interface LauncherGadgetHost {
    fun run(command: LauncherCellCommand)
}

/**
 * Base for a gadget's view: does work only while the view is attached AND the host is STARTED, and
 * stops the moment either ends.
 *
 * This is the single place the "no gadget outlives its cell" rule is enforced. The desktop binder
 * rebuilds every cell view on each emission and there is no `onViewRecycled` to hook (the grid is not
 * a RecyclerView - ADR-9), so a gadget that started a Flow or a receiver has exactly one honest place
 * to stop it: its own detach.
 */
abstract class LauncherGadgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var activeJob: Job? = null

    /** The renderer replaces this cell with its failed-gadget state after asynchronous work fails. */
    var onFailure: (() -> Unit)? = null

    /**
     * Runs while attached and STARTED; cancelled on detach or stop, and restarted on the next
     * attach/start. Loading here (rather than in the constructor) is what keeps Home cheap - a gadget
     * scrolled out of the desktop, or a desktop the user left, costs nothing.
     */
    protected open suspend fun CoroutineScope.onActive() = Unit

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Null before the view is in a lifecycle-owning tree; the next attach will find one.
        val owner = findViewTreeLifecycleOwner() ?: return
        activeJob = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // A failing gadget may cost its own cell and nothing more (S2208). This view is part of
                // the home screen, so an exception escaping here kills the process the system restarts
                // as HOME, which crash-loops the whole device until the desktop is edited - and the
                // desktop cannot be edited while the launcher keeps dying (S2207 did exactly that).
                try {
                    onActive()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                    // Deliberately every throwable: an isolation boundary that enumerated exception
                    // types would let the one it did not list through, which is the crash-loop again.
                } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
                    Timber.e(failure, "Gadget ${javaClass.simpleName} failed; cell left inert")
                    Timber.d("S2208: ${javaClass.simpleName} active work failed")
                    onFailure?.invoke()
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        activeJob?.cancel()
        activeJob = null
        super.onDetachedFromWindow()
    }
}
