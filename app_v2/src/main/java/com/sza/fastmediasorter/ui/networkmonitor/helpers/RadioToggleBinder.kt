package com.sza.fastmediasorter.ui.networkmonitor.helpers

import android.widget.CompoundButton
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.domain.radio.RadioKind

/**
 * S1433: what came of the user asking a section to switch its radio.
 *
 * Three cases, reconstructed at this seam rather than returned by the platform contract. `RadioControlContract`
 * answers `isToggleSupported` and a plain boolean from `toggle`, and S1441 deliberately kept it that narrow
 * (the tactical plan's "exactly one radio-control implementation" invariant), so naming the outcomes is the
 * consumer's job and the ViewModel does it.
 */
sealed interface RadioToggleOutcome {

    /** The radio actually flipped, confirmed by the observer rather than by a return value. */
    data object Switched : RadioToggleOutcome

    /** The firmware refused the direct attempt, which on Android 10+ is the ordinary path, not a fault. */
    data class NeedsSystemUi(val kind: RadioKind) : RadioToggleOutcome

    /** This build compiles no real controller, so no attempt is worth making. */
    data object Unsupported : RadioToggleOutcome
}

/**
 * S1433: the observed radio state a section renders before anybody taps anything.
 *
 * [isOn] is nullable and null means unknown, never off: a device that refuses the read must not be drawn as
 * switched off, which is the failure mode `RadioStateReader` was written to avoid.
 */
data class RadioToggleState(
    val isOn: Boolean?,
    val isSupported: Boolean,
)

/**
 * S1433: the switch row that opens every radio-bearing subscreen.
 *
 * One binder so the Wi-Fi and the Bluetooth rows cannot drift: strategic §3.2 requires the control to look
 * identical everywhere, and §11 criterion 11 forbids an error message for either refusal outcome.
 *
 * View-side only. It holds no repository, no contract and no coroutine scope - the ViewModel owns those and
 * hands this class a rendered state and a finished outcome, which is what keeps a domain dependency out of
 * the Fragment.
 */
class RadioToggleBinder(
    private val switch: CompoundButton,
    private val reasonView: TextView,
    private val onToggleRequested: () -> Unit,
) {

    /**
     * The last state the observer reported, kept so a refused attempt can be undone.
     *
     * Null until the first emission, which is also "unknown" for rendering purposes.
     */
    private var observedOn: Boolean? = null

    /** True while this class is writing the switch itself, because the listener cannot tell who moved it. */
    private var isApplyingState = false

    init {
        switch.setOnCheckedChangeListener { _, _ ->
            if (!isApplyingState) {
                onToggleRequested()
            }
        }
    }

    /** Reflects the observed state. Called on every emission, including the ones a tap caused. */
    fun render(state: RadioToggleState) {
        observedOn = state.isOn
        switch.isEnabled = state.isSupported
        setCheckedSilently(state.isOn == true)
        when {
            !state.isSupported -> showReason(R.string.network_monitor_radio_unsupported)
            state.isOn == null -> showReason(R.string.network_monitor_radio_state_unknown)
            else -> reasonView.isVisible = false
        }
    }

    /** Applies what came of a tap. */
    fun apply(outcome: RadioToggleOutcome) {
        when (outcome) {
            RadioToggleOutcome.Switched -> Unit
            is RadioToggleOutcome.NeedsSystemUi -> openSystemSurface(outcome.kind)
            RadioToggleOutcome.Unsupported -> showUnsupported()
        }
    }

    /**
     * Puts the switch back where the observer had it and opens the system surface instead.
     *
     * No message accompanies either action. A firmware ignoring `setWifiEnabled` is the documented behaviour
     * of every Android since 10, and reporting the platform doing its job as an error would teach the user to
     * distrust a screen that is working correctly. The switch is reverted rather than left where the finger
     * put it because nothing has changed yet - the observer will move it if and when the system panel does.
     */
    private fun openSystemSurface(kind: RadioKind) {
        setCheckedSilently(observedOn == true)
        val target = OsShortcutCatalog.byKey(kind.shortcutKey()) ?: return
        val context = switch.context
        val fallback = target.fallbackIntent?.invoke(context)
        val candidates = listOfNotNull(fallback, target.intent(context))
        context.startFirstAvailableSystemSurface(candidates)
    }

    private fun showUnsupported() {
        switch.isEnabled = false
        setCheckedSilently(observedOn == true)
        showReason(R.string.network_monitor_radio_unsupported)
    }

    /** The listener fires for a programmatic write too, and treating that as a tap would loop. */
    private fun setCheckedSilently(checked: Boolean) {
        isApplyingState = true
        switch.isChecked = checked
        isApplyingState = false
    }

    private fun showReason(@StringRes reasonRes: Int) {
        reasonView.setText(reasonRes)
        reasonView.isVisible = true
    }
}

/**
 * The catalog key whose target carries the system surface for this radio.
 *
 * Read from `OsShortcutCatalog` rather than built here so the Monitor lands on the same screen the launcher
 * and the app-launch panel land on - the Wi-Fi panel that floats over the app on API 29+, the full settings
 * screen for Bluetooth, which has no panel counterpart.
 */
private fun RadioKind.shortcutKey(): String = when (this) {
    RadioKind.WIFI -> OsShortcutCatalog.KEY_WIFI
    RadioKind.BLUETOOTH -> OsShortcutCatalog.KEY_BLUETOOTH
}
