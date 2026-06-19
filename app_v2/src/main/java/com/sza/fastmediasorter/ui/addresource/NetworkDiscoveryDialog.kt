package com.sza.fastmediasorter.ui.addresource

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.NetworkHost
import com.sza.fastmediasorter.databinding.DialogNetworkDiscoveryBinding
import com.sza.fastmediasorter.databinding.ItemNetworkHostBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import kotlinx.coroutines.launch


class NetworkDiscoveryDialog : DialogFragment() {

    private val viewModel: AddResourceViewModel by activityViewModels()
    private var _binding: DialogNetworkDiscoveryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NetworkHostAdapter

    var onHostSelected: ((NetworkHost) -> Unit)? = null

    // onCreateDialog inflates the view and sets it via setView() so the dialog has
    // visible content. viewLifecycleOwner is NOT available in this path - observation
    // uses lifecycleScope + repeatOnLifecycle(STARTED) instead (STARTED = dialog visible).
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogNetworkDiscoveryBinding.inflate(LayoutInflater.from(requireContext()))
        setupViews()
        observeData()
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun setupViews() {
        adapter = NetworkHostAdapter { host ->
            onHostSelected?.invoke(host)
            dismiss()
        }
        binding.rvHosts.adapter = adapter

        // tvStatus announces scan state changes to screen readers. Use View property
        // directly - ViewCompat.setAccessibilityLiveRegion is deprecated (minSdk 26).
        binding.tvStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE

        binding.btnStopScan.setOnClickListener {
            if (viewModel.state.value.isScanning) {
                // Stop the active scan without closing the dialog.
                viewModel.stopScan()
            } else {
                // Restart a new scan when none is active.
                viewModel.scanNetwork()
            }
        }

        binding.btnCancel.setOnClickListener {
            viewModel.stopScan() // ensure any active scan is cancelled on dismiss
            dismiss()
        }
    }

    private fun observeData() {
        // DialogFragment via onCreateDialog+setView has no view lifecycle -
        // use fragment lifecycle (STARTED = dialog is visible and interactive).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Update progress and status text.
                    binding.progressBar.isVisible = state.isScanning
                    binding.tvStatus.text = when {
                        state.isScanning -> {
                            val p = state.scanProgress
                            // Show per-subnet progress counter while scan is running.
                            if (p != null) "${p.subnet}.x (${p.done}/${p.total})"
                            else getString(R.string.msg_scanning_subnet)
                        }
                        else -> getString(R.string.msg_scan_complete)
                    }

                    // Stop button label switches based on scanning state.
                    binding.btnStopScan.text = if (state.isScanning) {
                        getString(R.string.stop)
                    } else {
                        getString(R.string.msg_scan_again)
                    }

                    // Hosts list.
                    adapter.submitList(state.foundNetworkHosts)
                    binding.tvEmpty.isVisible = !state.isScanning && state.foundNetworkHosts.isEmpty()
                    binding.rvHosts.isVisible = state.foundNetworkHosts.isNotEmpty()
                }
            }
        }
    }

    // Start scan automatically when the dialog opens and no scan is in progress.
    override fun onStart() {
        super.onStart()
        if (viewModel.state.value.foundNetworkHosts.isEmpty() && !viewModel.state.value.isScanning) {
            viewModel.scanNetwork()
        }
        // The action is picking a discovered host from the list (Stop/Cancel are auxiliary), so
        // there is no positive confirm - no-op confirm only adds Esc-dismiss and focus traversal.
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "NetworkDiscoveryDialog"
        fun newInstance() = NetworkDiscoveryDialog()
    }
}

class NetworkHostAdapter(
    private val onItemClick: (NetworkHost) -> Unit
) : ListAdapter<NetworkHost, NetworkHostAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNetworkHostBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemNetworkHostBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(host: NetworkHost) {
            binding.tvHostname.text = host.hostname
            binding.tvIp.text = host.ip

            val services = host.openPorts.joinToString(", ") { port ->
                when(port) {
                    445 -> "SMB"
                    21 -> "FTP"
                    22 -> "SFTP"
                    else -> port.toString()
                }
            }
            binding.tvServices.text = services
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<NetworkHost>() {
        override fun areItemsTheSame(oldItem: NetworkHost, newItem: NetworkHost) = oldItem.ip == newItem.ip
        override fun areContentsTheSame(oldItem: NetworkHost, newItem: NetworkHost) = oldItem == newItem
    }
}
