package com.sza.fastmediasorter.ui.delivery

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
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
import com.sza.fastmediasorter.databinding.ItemExtensionSectionHeaderBinding
import com.sza.fastmediasorter.domain.delivery.ExtensionItem
import com.sza.fastmediasorter.domain.delivery.ExtensionSection
import com.sza.fastmediasorter.domain.delivery.ExtensionStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Fragment displaying the Extensions Manager settings screen (S0386 Phase 08, grouped in Phase 11).
 * Lists all modules and language data packs under OCR / Translation / Media Playback section headers,
 * showing statuses, sizes, and download/uninstall actions.
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

        adapter.submitList(buildRows(viewModel.extensions))
    }

    // Inserts a section header before the first item of each section (items arrive already ordered by
    // section from the inventory), so the screen reads as OCR / Translation / Media Playback groups.
    private fun buildRows(items: List<ExtensionItem>): List<ExtensionRow> {
        val rows = mutableListOf<ExtensionRow>()
        var lastSection: ExtensionSection? = null
        for (item in items) {
            if (item.section != lastSection) {
                rows.add(ExtensionRow.Header(sectionTitleRes(item.section)))
                lastSection = item.section
            }
            rows.add(ExtensionRow.Entry(item))
        }
        return rows
    }

    @StringRes
    private fun sectionTitleRes(section: ExtensionSection): Int = when (section) {
        ExtensionSection.OCR -> R.string.ext_section_ocr
        ExtensionSection.TRANSLATION -> R.string.ext_section_translation
        ExtensionSection.MEDIA_PLAYBACK -> R.string.ext_section_media_playback
    }

    // Explicit confirmation before deleting an installed extension (Phase 08 step 08.3). The delete
    // affordance is only shown while a set is Installed, so a download in flight cannot reach here.
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

/** A row in the grouped Extensions list: either a section header or a downloadable entry. */
sealed class ExtensionRow {
    data class Header(@StringRes val titleRes: Int) : ExtensionRow()
    data class Entry(val item: ExtensionItem) : ExtensionRow()
}

class ExtensionsAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val onDownloadClick: (ExtensionItem) -> Unit,
    private val onUninstallClick: (ExtensionItem) -> Unit
) : ListAdapter<ExtensionRow, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ExtensionRow.Header -> VIEW_TYPE_HEADER
        is ExtensionRow.Entry -> VIEW_TYPE_ENTRY
    }

    class HeaderViewHolder(
        private val binding: ItemExtensionSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ExtensionRow.Header) {
            binding.tvSectionTitle.setText(header.titleRes)
        }
    }

    inner class EntryViewHolder(private val binding: ItemExtensionBinding) : RecyclerView.ViewHolder(binding.root) {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemExtensionSectionHeaderBinding.inflate(inflater, parent, false))
        } else {
            EntryViewHolder(ItemExtensionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ExtensionRow.Header -> (holder as HeaderViewHolder).bind(row)
            is ExtensionRow.Entry -> (holder as EntryViewHolder).bind(row.item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is EntryViewHolder) holder.onViewRecycled()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<ExtensionRow>() {
            override fun areItemsTheSame(oldItem: ExtensionRow, newItem: ExtensionRow): Boolean =
                when {
                    oldItem is ExtensionRow.Header && newItem is ExtensionRow.Header ->
                        oldItem.titleRes == newItem.titleRes
                    oldItem is ExtensionRow.Entry && newItem is ExtensionRow.Entry ->
                        oldItem.item.id == newItem.item.id
                    else -> false
                }

            override fun areContentsTheSame(oldItem: ExtensionRow, newItem: ExtensionRow): Boolean =
                areItemsTheSame(oldItem, newItem)
        }
    }
}
