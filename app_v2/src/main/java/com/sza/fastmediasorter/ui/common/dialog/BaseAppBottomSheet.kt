package com.sza.fastmediasorter.ui.common.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sza.fastmediasorter.databinding.SheetShellBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import timber.log.Timber

/**
 * Recreation-safe bottom sheet base (S3242): inflates `sheet_shell.xml` once, hands the subclass
 * its content view via [bindContent], and wires the drag handle, bottom system-bar inset and
 * keyboard contract every sheet needs, so a subclass authors only its content.
 *
 * A subclass whose dialog is not a sheet at all (an anchored panel sharing the class, S3244) sets
 * [usesShell] false and skips the shell chrome; the keyboard contract still applies.
 */
abstract class BaseAppBottomSheet : BottomSheetDialogFragment() {

    /** Layout inflated into the shell's `sheetContentFrame`, or as the whole view when bypassed. */
    @get:LayoutRes
    protected abstract val contentLayout: Int

    /** Set true to keep the shell's action-pair slot visible for a confirm/cancel sheet. */
    protected open val showActionPair: Boolean = false

    /**
     * Set false when this fragment's dialog is not a bottom sheet: [contentLayout] is inflated
     * without the shell, and the shell-only chrome - the drag handle and the bottom inset - is
     * skipped, because an anchored top panel must not carry either.
     */
    protected open val usesShell: Boolean = true

    /** Called once the content view is inflated and placed in the content frame. */
    protected abstract fun bindContent(content: View)

    /** `setFragmentResult` key this sheet delivers its result under. */
    protected abstract val requestKey: String

    private var _shellBinding: SheetShellBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Timber.d("S3244: base sheet onCreateView, usesShell=%b", usesShell)
        if (!usesShell) {
            return inflater.inflate(contentLayout, container, false)
        }
        val binding = SheetShellBinding.inflate(inflater, container, false)
        _shellBinding = binding
        val content = inflater.inflate(contentLayout, binding.sheetContentFrame, false)
        binding.sheetContentFrame.addView(content)
        binding.sheetActionContainer.visibility = if (showActionPair) View.VISIBLE else View.GONE
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val shell = _shellBinding
        if (shell == null) {
            // Bypassed shell: the subclass layout is the whole view, so it binds directly.
            bindContent(view)
            return
        }
        // S3242: sheets sit at the bottom of the window, so only the bottom edge ever needs to
        // clear the navigation bar - top/left/right stay unpadded like every other content frame.
        shell.sheetContentFrame.applySystemBarInsetPadding(
            applyLeft = false,
            applyTop = false,
            applyRight = false,
            applyBottom = true,
        )
        bindContent(shell.sheetContentFrame.getChildAt(0))
    }

    override fun onStart() {
        super.onStart()
        DialogKeyboardDelegate.applyToDialogFragment(dialog) { }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _shellBinding = null
    }

    /** Delivers [payload] to the fragment result listener registered under [requestKey]. */
    protected fun deliverResult(payload: Bundle) {
        setFragmentResult(requestKey, payload)
    }
}
