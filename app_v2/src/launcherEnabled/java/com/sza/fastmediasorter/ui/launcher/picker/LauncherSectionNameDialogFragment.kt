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
import com.sza.fastmediasorter.databinding.DialogLauncherSectionNameBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate

/**
 * S1742: names a section the user is creating.
 *
 * The name goes back over `FragmentResult` rather than a callback, like every other launcher picker: the
 * dialog outlives a configuration change and a lambda would not.
 *
 * Shaped after [LauncherPhoneNumberDialogFragment] deliberately - the two are the launcher's only
 * single-field prompts, and a second layout idiom for the same job would be one more thing to keep in
 * step.
 */
class LauncherSectionNameDialogFragment : DialogFragment() {

    private var _binding: DialogLauncherSectionNameBinding? = null
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
        _binding = DialogLauncherSectionNameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.editSectionName.setText(requireArguments().getString(ARG_INITIAL_NAME).orEmpty())
        binding.btnSectionNameCancel.setOnClickListener { dismiss() }
        binding.btnSectionNameConfirm.setOnClickListener { confirm() }
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
     * A blank name is the only refusal: the name is the whole of a user section's identity on screen, so
     * an empty one would leave a header nobody can tell from the next.
     */
    private fun confirm() {
        val name = binding.editSectionName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.tvSectionNameError.setText(R.string.launcher_section_name_empty)
            binding.tvSectionNameError.isVisible = true
            return
        }
        setFragmentResult(
            requireArguments().getString(ARG_REQUEST_KEY).orEmpty(),
            bundleOf(RESULT_NAME to name),
        )
        dismiss()
    }

    companion object {
        const val TAG = "LauncherSectionNameDialog"
        const val RESULT_NAME = "result_section_name"

        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_INITIAL_NAME = "arg_initial_name"

        fun newInstance(requestKey: String, initialName: String = ""): LauncherSectionNameDialogFragment =
            LauncherSectionNameDialogFragment().apply {
                arguments = bundleOf(ARG_REQUEST_KEY to requestKey, ARG_INITIAL_NAME to initialName)
            }
    }
}
