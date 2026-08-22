package com.sza.fastmediasorter.ui.networkmonitor.sections

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentNetworkMonitorInternetBinding
import com.sza.fastmediasorter.domain.usecase.networkmonitor.CgnatVerdict
import com.sza.fastmediasorter.domain.usecase.networkmonitor.SubnetScanTarget
import com.sza.fastmediasorter.ui.networkmonitor.helpers.ChartValueUnit
import com.sza.fastmediasorter.ui.networkmonitor.helpers.NetworkPathNode
import com.sza.fastmediasorter.ui.networkmonitor.helpers.SignalChartBinder
import com.sza.fastmediasorter.ui.networkmonitor.helpers.formatChartValue
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

/**
 * S1433: the Internet and resources subscreen - the path diagram, the traffic chart and the four manual
 * checks.
 *
 * Every action that spends traffic asks first and can be stopped while it runs; both are the ViewModel's
 * doing, and this class only renders the question and the stop button. The diagram carries its text
 * equivalent in `contentDescription`, because a canvas offers a screen reader nothing to walk.
 */
@AndroidEntryPoint
class InternetSectionFragment : Fragment() {

    private var _binding: FragmentNetworkMonitorInternetBinding? = null
    private val binding
        get() = requireNotNull(_binding) { "Binding is valid only between onCreateView and onDestroyView" }

    private val viewModel: InternetSectionViewModel by viewModels()

    private var chartBinder: SignalChartBinder? = null

    private var confirmationDialog: AlertDialog? = null

    /** The saved resources the picker last offered, so a tap resolves to an id rather than to a name. */
    private var pickerResources: List<InternetResource> = emptyList()

    private var pickerLabels: List<String> = emptyList()

    /** 0 is the internet itself; every later index is a saved resource. */
    private var selectedIndex: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNetworkMonitorInternetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chartBinder = SignalChartBinder(
            chart = binding.internetChart.chartSeries,
            summaryView = binding.internetChart.chartSummary,
            emptyView = binding.internetChart.chartEmpty,
            unit = ChartValueUnit.BYTES_PER_SECOND,
            resetTarget = binding.internetChart.root,
            onResetRequested = viewModel::onChartResetRequested,
        )
        binding.internetChart.chartHeading.setText(R.string.network_monitor_traffic_heading)
        bindActions()
        collectOnLifecycle(viewModel.uiState) { render(it) }
        collectOnLifecycle(viewModel.resources) { renderResources(it) }
        collectOnLifecycle(viewModel.actionState) { renderAction(it) }
        collectOnLifecycle(viewModel.deviceSubnetRange) { prefillSubnetRange(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        confirmationDialog?.dismiss()
        confirmationDialog = null
        chartBinder = null
        pickerResources = emptyList()
        pickerLabels = emptyList()
        _binding = null
    }

    /**
     * S1617: shows the range an untouched scan would use.
     *
     * The fields already meant "this network" when left blank, but two empty boxes cannot say so - the
     * owner read them as a range he had to type. Only an empty field is written: that guard covers the
     * restored-on-rotation case and the mid-edit case at once, without the screen having to know which
     * of the two put text there.
     */
    private fun prefillSubnetRange(range: SubnetScanTarget.AddressRange?) {
        if (range == null) {
            return
        }
        if (binding.internetRangeFirst.text.isNullOrBlank()) {
            binding.internetRangeFirst.setText(range.firstAddress)
        }
        if (binding.internetRangeLast.text.isNullOrBlank()) {
            binding.internetRangeLast.setText(range.lastAddress)
        }
    }

    private fun bindActions() {
        binding.internetTargetPicker.setOnItemClickListener { _, _, position, _ -> onTargetPicked(position) }
        binding.btnInternetCheckResource.setOnClickListener {
            viewModel.onActionRequested(InternetAction.RESOURCE_CHECK)
        }
        binding.btnInternetScanSubnet.setOnClickListener {
            viewModel.onSubnetRangeEntered(
                binding.internetRangeFirst.text?.toString().orEmpty(),
                binding.internetRangeLast.text?.toString().orEmpty(),
            )
            viewModel.onActionRequested(InternetAction.SUBNET_SCAN)
        }
        binding.btnInternetExternalIp.setOnClickListener {
            viewModel.onActionRequested(InternetAction.EXTERNAL_IP)
        }
        binding.btnInternetSpeedTest.setOnClickListener {
            viewModel.onActionRequested(InternetAction.SPEED_TEST)
        }
        binding.btnInternetCancel.setOnClickListener { viewModel.onCancelRequested() }
    }

    private fun render(state: InternetSectionUiState) {
        renderPath(state)
        renderRates(state)
        chartBinder?.render(state.traffic)
    }

    /**
     * The diagram states what the platform reports and nothing beyond it: research artifact 01 records that
     * Android exposes no topology past the default route, so a hop with no answer is drawn as unknown rather
     * than assumed present.
     */
    private fun renderPath(state: InternetSectionUiState) {
        val nodes = pathNodes(state)
        binding.internetPathDiagram.setNodes(nodes)
        binding.internetPathDiagram.contentDescription = nodes.joinToString(separator = ", ") { node ->
            getString(R.string.network_monitor_path_node_description, node.label, node.value ?: unknown())
        }
    }

    private fun pathNodes(state: InternetSectionUiState): List<NetworkPathNode> {
        val link = state.link
        return listOf(
            NetworkPathNode(
                label = getString(R.string.network_monitor_path_device),
                value = link?.ipv4Addresses?.firstOrNull() ?: link?.interfaceName,
                reachable = link != null,
            ),
            NetworkPathNode(
                label = getString(R.string.network_monitor_path_gateway),
                value = link?.gateway,
                reachable = link?.gateway != null,
            ),
            NetworkPathNode(
                label = getString(R.string.network_monitor_path_dns),
                value = link?.dnsServers?.firstOrNull(),
                reachable = link?.dnsServers?.isNotEmpty() == true,
            ),
            NetworkPathNode(
                label = getString(R.string.network_monitor_path_internet),
                value = getString(
                    if (state.hasInternet) {
                        R.string.network_monitor_internet_ok
                    } else {
                        R.string.network_monitor_internet_none
                    }
                ),
                reachable = state.hasInternet,
            ),
        )
    }

    private fun renderRates(state: InternetSectionUiState) {
        val rate = state.currentRate
        binding.internetTrafficDownValue.text = rate?.let { perSecond(it.receivedBytesPerSecond) } ?: unknown()
        binding.internetTrafficUpValue.text =
            rate?.let { perSecond(it.transmittedBytesPerSecond) } ?: unknown()
    }

    /**
     * The adapter is rebuilt only when the offered set actually changes, for the reason the Bluetooth picker
     * records: swapping an `ArrayAdapter` under an open dropdown makes the list impossible to tap.
     */
    private fun renderResources(resources: List<InternetResource>) {
        pickerResources = resources
        val labels = buildList {
            add(getString(R.string.network_monitor_target_internet))
            addAll(resources.map { it.name })
        }
        if (labels == pickerLabels) {
            return
        }
        pickerLabels = labels
        selectedIndex = selectedIndex.coerceIn(0, labels.lastIndex)
        binding.internetTargetPicker.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        )
        binding.internetTargetPicker.setText(labels[selectedIndex], false)
        applyTarget()
    }

    private fun onTargetPicked(position: Int) {
        selectedIndex = position
        applyTarget()
    }

    private fun applyTarget() {
        val resource = pickerResources.getOrNull(selectedIndex - 1)
        viewModel.onResourceSelected(resource?.id)
        binding.btnInternetCheckResource.isEnabled = resource != null
    }

    private fun renderAction(state: InternetActionState) {
        val running = state.running
        binding.internetProgressGroup.isVisible = running != null
        if (running != null) {
            binding.internetProgressLabel.setText(running.toRunningRes())
            val fraction = state.progressFraction
            binding.internetProgress.isIndeterminate = fraction == null
            if (fraction != null) {
                binding.internetProgress.setProgressCompat((fraction * PERCENT).toInt(), true)
            }
        }
        binding.internetResult.isVisible = state.result != null
        state.result?.let { binding.internetResult.text = resultText(it) }
        setActionsEnabled(running == null)
        renderConfirmation(state.confirmation)
    }

    private fun setActionsEnabled(enabled: Boolean) {
        binding.btnInternetScanSubnet.isEnabled = enabled
        binding.btnInternetExternalIp.isEnabled = enabled
        binding.btnInternetSpeedTest.isEnabled = enabled
        binding.btnInternetCheckResource.isEnabled = enabled && selectedIndex > 0
    }

    /**
     * A builder dialog rather than an inflated layout: it inherits the named confirm and cancel button
     * styles from `materialAlertDialogTheme`, which is what keeps this pair from drifting into a one-off.
     */
    private fun renderConfirmation(confirmation: InternetConfirmation?) {
        confirmationDialog?.dismiss()
        confirmationDialog = null
        if (confirmation == null) {
            return
        }
        confirmationDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(confirmation.toTitleRes())
            .setMessage(confirmation.toMessageRes())
            .setPositiveButton(R.string.network_monitor_action_continue) { _, _ ->
                viewModel.onConfirmationAccepted()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> viewModel.onConfirmationDismissed() }
            .setOnCancelListener { viewModel.onConfirmationDismissed() }
            .showBoundTo(this)
    }

    private fun resultText(result: InternetActionResult): String = when (result) {
        is InternetActionResult.ExternalIp -> getString(
            R.string.network_monitor_result_external_ip,
            result.address,
            getString(result.verdict.toLabelRes()),
        )
        InternetActionResult.ExternalIpUnavailable ->
            getString(R.string.network_monitor_result_external_ip_unavailable)
        is InternetActionResult.SubnetScan -> subnetText(result)
        is InternetActionResult.SubnetRefused -> refusalText(result)
        is InternetActionResult.SpeedTest -> getString(
            R.string.network_monitor_result_speed_test,
            megabits(result.downMbps),
            result.upMbps?.let { megabits(it) } ?: unknown(),
        )
        InternetActionResult.SpeedTestFailed -> getString(R.string.network_monitor_result_speed_test_failed)
        InternetActionResult.SpeedTestMetered -> getString(R.string.network_monitor_result_speed_test_metered)
        is InternetActionResult.ResourceReachable ->
            getString(R.string.network_monitor_result_resource_ok, result.detail)
        InternetActionResult.ResourceUnreachable ->
            getString(R.string.network_monitor_result_resource_failed)
        InternetActionResult.ResourceMissing -> getString(R.string.network_monitor_result_resource_missing)
    }

    private fun subnetText(result: InternetActionResult.SubnetScan): String {
        val header = getString(
            R.string.network_monitor_result_subnet_scan,
            result.hostsFound,
            result.probed,
        )
        if (result.hosts.isEmpty()) {
            return header
        }
        val lines = result.hosts.joinToString(separator = "\n") { host ->
            getString(
                R.string.network_monitor_result_subnet_host,
                host.ip,
                host.openPorts.joinToString(),
            )
        }
        return "$header\n$lines"
    }

    private fun refusalText(result: InternetActionResult.SubnetRefused): String = when (result.reason) {
        SubnetRefusal.PERMISSION -> getString(R.string.network_monitor_result_subnet_permission)
        SubnetRefusal.SUBNET_UNKNOWN -> getString(R.string.network_monitor_result_subnet_unknown)
        SubnetRefusal.RANGE_TOO_LARGE ->
            getString(R.string.network_monitor_result_subnet_too_large, result.cap ?: 0)
        SubnetRefusal.RANGE_INVALID -> getString(R.string.network_monitor_result_subnet_invalid)
    }

    private fun megabits(value: Double): String = getString(
        R.string.network_monitor_value_mbps_decimal,
        String.format(Locale.getDefault(), MBPS_FORMAT, value),
    )

    private fun perSecond(bytesPerSecond: Double): String =
        requireContext().formatChartValue(bytesPerSecond, ChartValueUnit.BYTES_PER_SECOND)

    private fun unknown(): String = getString(R.string.network_monitor_value_unknown)

    private companion object {

        const val MBPS_FORMAT = "%.1f"
        const val PERCENT = 100f
    }
}

@StringRes
private fun InternetAction.toRunningRes(): Int = when (this) {
    InternetAction.RESOURCE_CHECK -> R.string.network_monitor_running_check_resource
    InternetAction.SUBNET_SCAN -> R.string.network_monitor_running_scan_subnet
    InternetAction.EXTERNAL_IP -> R.string.network_monitor_running_external_ip
    InternetAction.SPEED_TEST -> R.string.network_monitor_running_speed_test
}

@StringRes
private fun InternetConfirmation.toTitleRes(): Int = when (this) {
    InternetConfirmation.SPEED_TEST_TRAFFIC -> R.string.network_monitor_warn_speed_test_title
    InternetConfirmation.SUBNET_SCAN_TRAFFIC -> R.string.network_monitor_warn_subnet_title
    InternetConfirmation.EXTERNAL_IP_DISCLOSURE -> R.string.network_monitor_warn_external_ip_title
    InternetConfirmation.METERED_NETWORK -> R.string.network_monitor_warn_metered_title
}

@StringRes
private fun InternetConfirmation.toMessageRes(): Int = when (this) {
    InternetConfirmation.SPEED_TEST_TRAFFIC -> R.string.network_monitor_warn_speed_test_message
    InternetConfirmation.SUBNET_SCAN_TRAFFIC -> R.string.network_monitor_warn_subnet_message
    InternetConfirmation.EXTERNAL_IP_DISCLOSURE -> R.string.network_monitor_warn_external_ip_message
    InternetConfirmation.METERED_NETWORK -> R.string.network_monitor_warn_metered_message
}

@StringRes
private fun CgnatVerdict.toLabelRes(): Int = when (this) {
    CgnatVerdict.Direct -> R.string.network_monitor_cgnat_direct
    CgnatVerdict.LikelyCgnat -> R.string.network_monitor_cgnat_likely
    CgnatVerdict.Unknown -> R.string.network_monitor_cgnat_unknown
}
