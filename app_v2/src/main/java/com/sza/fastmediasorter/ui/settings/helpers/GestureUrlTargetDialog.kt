package com.sza.fastmediasorter.ui.settings.helpers

import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.showBoundTo

/**
 * S2256: the address prompt for an open-URL gesture, shared by the edge-gesture slots and the launcher
 * desktop swipes.
 *
 * Both surfaces had their own copy of this dialog, which is how the same question ends up phrased two
 * ways. Clearing is deliberately not here: it belongs to the target row's reset control
 * ([GestureTargetResetControl]), so a target is cleared the same way whichever kind it is.
 */
object GestureUrlTargetDialog {

    private const val PADDING_DP = 24

    fun show(
        host: Fragment,
        current: String,
        onSave: (String) -> Unit,
    ) {
        val context = host.requireContext()
        val input = EditText(context).apply {
            setText(current)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setHint(R.string.gesture_url_input_hint)
        }
        val padding = (PADDING_DP * context.resources.displayMetrics.density).toInt()
        val container = FrameLayout(context).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.gesture_url_input_title)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                onSave(input.text?.toString()?.trim().orEmpty())
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(host)
    }
}
