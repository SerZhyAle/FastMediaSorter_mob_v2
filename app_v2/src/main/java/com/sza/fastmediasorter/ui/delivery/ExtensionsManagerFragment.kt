package com.sza.fastmediasorter.ui.delivery

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentExtensionsManagerBinding
import com.sza.fastmediasorter.databinding.ItemExtensionBinding
import com.sza.fastmediasorter.domain.delivery.ExtensionItem
import com.sza.fastmediasorter.domain.delivery.ExtensionStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Fragment displaying the Extensions Manager settings screen (S0386 Phase 08).
 * Lists all modules and language data packs, showing statuses, sizes, and providing
 * download/uninstall actions.
 */
@AndroidEntryPoint
class ExtensionsManagerFragment : Fragment() {

    companion object {
        const val TAG = "ExtensionsManagerFragment"
    }

    private var _binding: FragmentExtensionsManagerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExtensionsManagerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExtensionsManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val adapter = ExtensionsAdapter(
            lifecycleOwner = viewLifecycleOwner,
            onDownloadClick = { viewModel.download(it) },
            onUninstallClick = { confirmUninstall(it) }
        )

        binding.recyclerExtensions.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerExtensions.adapter = adapter

        adapter.submitList(viewModel.extensions)
    }

    // Explicit confirmation before deleting an installed extension (Phase 08 step 08.3). The delete
    // affordance is only shown while a set is Installed, so a download in flight cannot reach here;
    // refusing an engine that is actively loaded in the player is deferred to Phase 07 attach.
    private fun confirmUninstall(item: ExtensionItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ext_delete_confirm_title)
            .setMessage(getString(R.string.ext_delete_confirm_message, getString(item.displayNameRes)))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ocr_best_model_delete) { _, _ -> viewModel.uninstall(item) }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ExtensionsAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val onDownloadClick: (ExtensionItem) -> Unit,
    private val onUninstallClick: (ExtensionItem) -> Unit
) : ListAdapter<ExtensionItem, ExtensionsAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemExtensionBinding) : RecyclerView.ViewHolder(binding.root) {
        private var job: Job? = null

        fun bind(item: ExtensionItem) {
            job?.cancel()
            binding.tvTitle.setText(item.displayNameRes)
            binding.tvDesc.setText(item.descriptionRes)
            binding.tvSize.text = binding.root.context.getString(R.string.ext_estimated_size, item.sizeLabel)

            binding.btnDownload.setOnClickListener { onDownloadClick(item) }
            binding.btnUninstall.setOnClickListener { onUninstallClick(item) }

            job = lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    item.statusFlow.collect { status ->
                        updateStatus(status)
                    }
                }
            }
        }

        private fun updateStatus(status: ExtensionStatus) {
            val context = binding.root.context
            when (status) {
                ExtensionStatus.Installed -> {
                    binding.tvStatusTag.setText(R.string.ext_status_installed)
                    applyStatusColor(ContextCompat.getColor(context, R.color.success_color))
                    binding.btnDownload.visibility = View.GONE
                    binding.btnUninstall.visibility = View.VISIBLE
                    binding.layoutProgress.visibility = View.GONE
                }
                ExtensionStatus.NotInstalled -> {
                    binding.tvStatusTag.setText(R.string.ext_status_available)
                    applyStatusColor(ContextCompat.getColor(context, R.color.ext_status_available))
                    binding.btnDownload.visibility = View.VISIBLE
                    binding.btnUninstall.visibility = View.GONE
                    binding.layoutProgress.visibility = View.GONE
                }
                is ExtensionStatus.Downloading -> {
                    binding.tvStatusTag.setText(R.string.ext_status_downloading)
                    applyStatusColor(ContextCompat.getColor(context, R.color.ext_status_downloading))
                    binding.btnDownload.visibility = View.GONE
                    binding.btnUninstall.visibility = View.GONE
                    binding.layoutProgress.visibility = View.VISIBLE
                    binding.progressDownload.progress = status.percent
                    binding.tvProgressPercent.text =
                        context.getString(R.string.ext_downloading_progress, status.percent)
                }
                is ExtensionStatus.Failed -> {
                    binding.tvStatusTag.setText(R.string.ext_status_failed)
                    applyStatusColor(ContextCompat.getColor(context, R.color.error_color))
                    binding.btnDownload.visibility = View.VISIBLE
                    binding.btnUninstall.visibility = View.GONE
                    binding.layoutProgress.visibility = View.GONE
                }
            }
        }

        // Tints the status chip text and derives a translucent chip background from the same hue
        // (alpha 26/255 ~= 10%), so status colors live in resources, not inline hex.
        private fun applyStatusColor(color: Int) {
            binding.tvStatusTag.setTextColor(color)
            binding.tvStatusTag.backgroundTintList =
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 26))
        }

        fun onViewRecycled() {
            job?.cancel()
            job = null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExtensionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.onViewRecycled()
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ExtensionItem>() {
            override fun areItemsTheSame(oldItem: ExtensionItem, newItem: ExtensionItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ExtensionItem, newItem: ExtensionItem): Boolean =
                oldItem.id == newItem.id
        }
    }
}
