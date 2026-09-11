package com.sza.fastmediasorter.ui.profile

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.databinding.DialogDeviceProfilePickerBinding
import com.sza.fastmediasorter.domain.usecase.CountProfilePresetOverridesUseCase
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.util.showBoundTo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared device-profile picker. A rounded card (sized to its content, centred on screen) with a
 * grid of pressable tiles. Tapping a tile selects it immediately and closes - no confirm/cancel
 * buttons (tap-outside / Back cancels). Reused by the Welcome flow and Settings.
 *
 * For Settings ([warnOnApply] = true) a tap that actually changes the profile first shows the
 * overwrite warning (strategic §11.7); Welcome applies the tap directly.
 */
@AndroidEntryPoint
class DeviceProfilePickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var deviceProfileAvailability: DeviceProfileAvailability

    @Inject
    lateinit var countProfilePresetOverrides: CountProfilePresetOverridesUseCase

    private var _binding: DialogDeviceProfilePickerBinding? = null
    private val binding get() = _binding!!

    private var currentType: DeviceProfileType = DeviceProfileType.PERSONAL_SMARTPHONE
    private var recommended: DeviceProfileType? = null
    private var warnOnApply: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        val args = requireArguments()
        currentType = args.getString(ARG_CURRENT).toProfileOr(DeviceProfileType.PERSONAL_SMARTPHONE)
        recommended = args.getString(ARG_RECOMMENDED)?.toProfileOrNull()
        warnOnApply = args.getBoolean(ARG_WARN_ON_APPLY, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogDeviceProfilePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = DeviceProfileTileAdapter(
            profiles = deviceProfileAvailability.selectableProfiles,
            recommended = recommended,
            selected = currentType,
            onClick = ::onTileClicked,
        )
        // Info-rich horizontal tiles: fewer columns than the feature grid so the title and
        // description stay readable. Scale with the smallest width (orientation-independent).
        val swDp = resources.configuration.smallestScreenWidthDp
        val columns = when {
            swDp >= 720 -> 3
            swDp >= 480 -> 2
            else -> 1
        }
        binding.rvProfiles.layoutManager = GridLayoutManager(requireContext(), columns)
        binding.rvProfiles.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        // Size the window to its content and centre it, so wide screens show a compact centred card
        // instead of a full-screen panel with empty space. Width is capped; height wraps (the inner
        // NestedScrollView scrolls if the content is taller than the screen on small devices).
        val metrics = resources.displayMetrics
        val width = minOf((metrics.widthPixels * 0.92f).toInt(), (metrics.density * 1100f).toInt())
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
        // Pure picker: tapping a tile selects and closes, so no-op confirm only adds Esc-dismiss and
        // tile focus traversal. Initial focus is left to the tile grid.
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onTileClicked(type: DeviceProfileType) {
        val changingInSettings = warnOnApply && type != currentType && type != DeviceProfileType.OTHER
        if (!changingInSettings) {
            confirmSelection(type)
            return
        }
        // S1216: the count comes from the same override map the apply use case folds, so the
        // confirmation cannot promise a different number than the one actually written.
        viewLifecycleOwner.lifecycleScope.launch {
            val overrides = countProfilePresetOverrides(type)
            if (overrides == 0) {
                // Warning about zero overwrites is noise - the profile changes nothing here.
                confirmSelection(type)
                return@launch
            }
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(
                    resources.getQuantityString(
                        R.plurals.settings_profile_warning_count,
                        overrides,
                        overrides
                    )
                )
                .setPositiveButton(R.string.profile_picker_select) { _, _ -> confirmSelection(type) }
                .setNegativeButton(android.R.string.cancel, null)
                .showBoundTo(this@DeviceProfilePickerDialogFragment)
        }
    }

    private fun confirmSelection(type: DeviceProfileType) {
        setFragmentResult(resultRequestKey, bundleOf(RESULT_PROFILE to type.name))
        dismiss()
    }

    private val resultRequestKey: String
        get() = requireArguments().getString(ARG_REQUEST_KEY) ?: RESULT_KEY

    companion object {
        const val TAG = "DeviceProfilePickerDialog"
        const val RESULT_KEY = "device_profile_picker_result"
        const val RESULT_PROFILE = "selected_profile"

        private const val ARG_CURRENT = "arg_current"
        private const val ARG_RECOMMENDED = "arg_recommended"
        private const val ARG_WARN_ON_APPLY = "arg_warn_on_apply"
        private const val ARG_REQUEST_KEY = "arg_request_key"

        fun newInstance(
            current: DeviceProfileType,
            recommended: DeviceProfileType?,
            warnOnApply: Boolean,
            requestKey: String = RESULT_KEY,
        ): DeviceProfilePickerDialogFragment = DeviceProfilePickerDialogFragment().apply {
            arguments = bundleOf(
                ARG_CURRENT to current.name,
                ARG_RECOMMENDED to recommended?.name,
                ARG_WARN_ON_APPLY to warnOnApply,
                ARG_REQUEST_KEY to requestKey,
            )
        }

        private fun String?.toProfileOrNull(): DeviceProfileType? =
            this?.let { runCatching { DeviceProfileType.valueOf(it) }.getOrNull() }

        private fun String?.toProfileOr(fallback: DeviceProfileType): DeviceProfileType =
            toProfileOrNull() ?: fallback
    }
}
