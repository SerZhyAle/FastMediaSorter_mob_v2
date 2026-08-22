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
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogLauncherNetworkIndicatorBinding
import com.sza.fastmediasorter.databinding.ItemSearchableOptionBinding
import com.sza.fastmediasorter.widget.networkmonitor.NetworkMonitorIndicator

/**
 * S1440: asks which network indicator a new desktop cell shows, and reports the chosen constant's
 * persisted [NetworkMonitorIndicator.key] back on the caller's request key.
 *
 * The desktop counterpart of the widget's configuration activity: a launcher cell has no widget id to
 * key a preferences row by, so the answer becomes the cell's own `target` param instead.
 *
 * No Hilt, for the same reason [LauncherResourceModePickerDialogFragment] declares none: the eight
 * options are the indicator table itself, so nothing has to be injected to build them.
 *
 * The reachability indicator needs a resource on top of the key. That second question is the host's,
 * not this dialog's - the add flow chains the shared resource picker after this result, which is what
 * keeps the cell's placement a single write.
 */
class LauncherNetworkIndicatorDialogFragment : DialogFragment() {

    private var _binding: DialogLauncherNetworkIndicatorBinding? = null
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
        _binding = DialogLauncherNetworkIndicatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnNetworkIndicatorCancel.setOnClickListener { dismiss() }
        buildRows()
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { DialogAccessibilityHelper.applyInitialFocus(it) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).also { it.setCanceledOnTouchOutside(true) }

    private fun buildRows() {
        val container = binding.containerNetworkIndicatorOptions
        val inflater = LayoutInflater.from(container.context)
        val rows = NetworkMonitorIndicator.entries.map { indicator ->
            val row = ItemSearchableOptionBinding.inflate(inflater, container, false)
            row.ivOptionIcon.setImageResource(indicator.iconRes)
            row.ivOptionIcon.isVisible = true
            row.tvOptionLabel.setText(indicator.labelRes)
            row.root.id = View.generateViewId()
            row.root.contentDescription = getString(indicator.labelRes)
            row.root.setOnClickListener { publish(indicator) }
            container.addView(row.root)
            row.root
        }
        linkFocusOrder(rows)
    }

    /**
     * D-pad order is declared rather than left to the default geometric search (CLAUDE.md Rule 16):
     * the rows are added at runtime, so no layout attribute can carry it. The chain wraps, so a remote
     * held down never lands on nothing.
     */
    private fun linkFocusOrder(rows: List<View>) {
        rows.forEachIndexed { index, row ->
            row.nextFocusUpId = rows[(index - 1 + rows.size) % rows.size].id
            row.nextFocusDownId = rows[(index + 1) % rows.size].id
        }
    }

    private fun publish(indicator: NetworkMonitorIndicator) {
        val requestKey = requireArguments().getString(ARG_REQUEST_KEY).orEmpty()
        setFragmentResult(requestKey, bundleOf(RESULT_INDICATOR_KEY to indicator.key))
        dismiss()
    }

    companion object {
        const val TAG = "LauncherNetworkIndicatorDialog"

        /** Carries [NetworkMonitorIndicator.key], the persisted form - never the enum name. */
        const val RESULT_INDICATOR_KEY = "result_indicator_key"

        private const val ARG_REQUEST_KEY = "arg_request_key"

        fun newInstance(requestKey: String): LauncherNetworkIndicatorDialogFragment =
            LauncherNetworkIndicatorDialogFragment().apply {
                arguments = bundleOf(ARG_REQUEST_KEY to requestKey)
            }
    }
}
