package com.sza.fastmediasorter_v2.ui.addresource

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter_v2.databinding.ItemResourceToAddBinding
import com.sza.fastmediasorter_v2.domain.model.MediaResource

class ResourceToAddAdapter(
    private val onSelectionChanged: (MediaResource, Boolean) -> Unit,
    private val onNameChanged: (MediaResource, String) -> Unit,
    private val onDestinationChanged: (MediaResource, Boolean) -> Unit
) : ListAdapter<MediaResource, ResourceToAddAdapter.ViewHolder>(ResourceDiffCallback()) {

    private var selectedPaths: Set<String> = emptySet()

    fun setSelectedPaths(paths: Set<String>) {
        selectedPaths = paths
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResourceToAddBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemResourceToAddBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentResource: MediaResource? = null
        private val nameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentResource?.let { resource ->
                    val newName = s.toString()
                    if (newName != resource.name) {
                        onNameChanged(resource, newName)
                    }
                }
            }
        }

        init {
            binding.etName.addTextChangedListener(nameWatcher)
        }

        fun bind(resource: MediaResource) {
            currentResource = resource
            
            binding.apply {
                cbAdd.setOnCheckedChangeListener(null)
                cbAdd.isChecked = resource.path in selectedPaths
                cbAdd.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChanged(resource, isChecked)
                }
                
                etName.removeTextChangedListener(nameWatcher)
                etName.setText(resource.name)
                etName.addTextChangedListener(nameWatcher)
                
                tvPath.text = resource.path
                tvFileCount.text = "${resource.fileCount} files"
                
                cbDestination.isVisible = resource.isWritable
                cbDestination.setOnCheckedChangeListener(null)
                cbDestination.isChecked = resource.isDestination
                cbDestination.setOnCheckedChangeListener { _, isChecked ->
                    onDestinationChanged(resource, isChecked)
                }
            }
        }
    }

    private class ResourceDiffCallback : DiffUtil.ItemCallback<MediaResource>() {
        override fun areItemsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem == newItem
        }
    }
}
