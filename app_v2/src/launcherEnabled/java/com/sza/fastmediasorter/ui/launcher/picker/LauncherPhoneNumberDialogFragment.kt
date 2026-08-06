package com.sza.fastmediasorter.ui.launcher.picker

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogLauncherPhoneNumberBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate

/**
 * S0428: types a phone number for a call or SMS cell, for the two cases the system picker cannot serve
 * - the number is not in the address book, or the device has no contacts at all.
 *
 * The number goes back over `FragmentResult` rather than a callback, like every other launcher picker:
 * the dialog outlives a configuration change and a lambda would not.
 */
class LauncherPhoneNumberDialogFragment : DialogFragment() {

    private var _binding: DialogLauncherPhoneNumberBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogLauncherPhoneNumberBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnPhoneNumberCancel.setOnClickListener { dismiss() }
        binding.btnPhoneNumberConfirm.setOnClickListener { confirm() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { DialogAccessibilityHelper.applyInitialFocus(it) }
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = { confirm() })
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).also { it.setCanceledOnTouchOutside(true) }

    /**
     * A digit is the whole test. Formatting varies by country far more than any pattern this app could
     * carry, and the dialler is the authority on what it can call - so the check only catches the empty
     * field and the stray word, and leaves the rest to the handler.
     */
    private fun confirm() {
        val number = binding.editPhoneNumber.text?.toString()?.trim().orEmpty()
        if (number.none(Char::isDigit)) {
            binding.tvPhoneNumberError.setText(R.string.launcher_contact_number_invalid)
            binding.tvPhoneNumberError.isVisible = true
            return
        }
        setFragmentResult(
            requireArguments().getString(ARG_REQUEST_KEY).orEmpty(),
            bundleOf(RESULT_NUMBER to number),
        )
        dismiss()
    }

    companion object {
        const val TAG = "LauncherPhoneNumberDialog"
        const val RESULT_NUMBER = "result_number"

        private const val ARG_REQUEST_KEY = "arg_request_key"

        fun newInstance(requestKey: String): LauncherPhoneNumberDialogFragment =
            LauncherPhoneNumberDialogFragment().apply {
                arguments = bundleOf(ARG_REQUEST_KEY to requestKey)
            }
    }
}
