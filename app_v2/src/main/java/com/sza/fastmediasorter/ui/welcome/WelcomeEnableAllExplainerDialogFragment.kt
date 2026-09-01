package com.sza.fastmediasorter.ui.welcome

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.DialogEnableAllExplainerBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S2322: prepares the user for the "Enable all" sequence, which otherwise hands them to a chain of
 * system screens with no narration - the permission dialogs, one settings page per special
 * permission, and finally the OS "Open with / Always" sheet raised over one of the user's own media
 * files.
 *
 * Two modes share this one class and layout: [Mode.OVERVIEW] runs before the sequence starts and
 * lists every stage, [Mode.DEFAULT_APP] runs immediately before the default-app stage and repeats
 * only that stage's two actions. They are one class because both show the same "pick the app, tap
 * Always, press Back" row; two copies of it would diverge at the first wording change.
 *
 * The outcome is delivered through [setFragmentResult] so it survives recreation - a caller that
 * rotates the device while this dialog is up still gets its answer.
 */
@AndroidEntryPoint
class WelcomeEnableAllExplainerDialogFragment : DialogFragment() {

    /** Gates the default-app row in [Mode.OVERVIEW]: builds without default-player support skip that
     *  stage entirely, so announcing it there would describe something that never happens. */
    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

    private var _binding: DialogEnableAllExplainerBinding? = null
    private val binding: DialogEnableAllExplainerBinding
        get() = requireNotNull(_binding) { "binding read outside the dialog's view lifetime" }

    private var mode: Mode = Mode.OVERVIEW
    private var requestKey: String = ""

    enum class Mode { OVERVIEW, DEFAULT_APP }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        mode = args.getString(ARG_MODE)?.let { name ->
            runCatching { Mode.valueOf(name) }.getOrNull()
        } ?: Mode.OVERVIEW
        requestKey = args.getString(ARG_REQUEST_KEY).orEmpty()

        _binding = DialogEnableAllExplainerBinding.inflate(LayoutInflater.from(requireContext()))
        render()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes())
            .setView(binding.root)
            .create()
    }

    private fun titleRes(): Int = when (mode) {
        Mode.OVERVIEW -> R.string.welcome_enable_all_explainer_title
        Mode.DEFAULT_APP -> R.string.welcome_enable_all_default_app_prompt_title
    }

    private fun render() {
        val overview = mode == Mode.OVERVIEW
        binding.tvExplainerIntro.setText(
            if (overview) {
                R.string.welcome_enable_all_explainer_intro
            } else {
                R.string.welcome_enable_all_default_app_prompt_intro
            }
        )
        binding.rowStepPermissions.isVisible = overview
        // In DEFAULT_APP the stage is already running, so the row always applies; in OVERVIEW it is
        // announced only where the stage will actually run.
        binding.rowStepDefaultApp.isVisible = !overview || mediaCapabilities.supportsDefaultPlayer
        binding.btnExplainerConfirm.setText(
            if (overview) {
                R.string.welcome_enable_all_explainer_continue
            } else {
                R.string.welcome_enable_all_default_app_prompt_continue
            }
        )
        binding.btnExplainerCancel.setText(
            if (overview) {
                android.R.string.cancel
            } else {
                R.string.welcome_enable_all_default_app_prompt_skip
            }
        )
        binding.btnExplainerConfirm.setOnClickListener { finishWith(proceed = true) }
        binding.btnExplainerCancel.setOnClickListener { finishWith(proceed = false) }
    }

    private fun finishWith(proceed: Boolean) {
        setFragmentResult(requestKey, bundleOf(RESULT_PROCEED to proceed))
        dismiss()
    }

    // Back and tap-outside never reach the buttons, and the caller must still learn that the user
    // declined - without this the sequence would wait for a result that never arrives.
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        setFragmentResult(requestKey, bundleOf(RESULT_PROCEED to false))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_PROCEED = "welcome_enable_all_explainer_proceed"

        private const val ARG_MODE = "welcome_enable_all_explainer_mode"
        private const val ARG_REQUEST_KEY = "welcome_enable_all_explainer_request_key"

        fun newInstance(mode: Mode, requestKey: String): WelcomeEnableAllExplainerDialogFragment =
            WelcomeEnableAllExplainerDialogFragment().apply {
                arguments = bundleOf(ARG_MODE to mode.name, ARG_REQUEST_KEY to requestKey)
            }
    }
}
