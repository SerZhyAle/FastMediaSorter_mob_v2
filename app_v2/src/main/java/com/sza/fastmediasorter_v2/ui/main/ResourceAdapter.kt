package com.sza.fastmediasorter_v2.ui.main

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter_v2.databinding.ItemResourceBinding
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType

class ResourceAdapter(
    private val onItemClick: (MediaResource) -> Unit,
    private val onItemDoubleClick: (MediaResource) -> Unit,
    private val onItemLongClick: (MediaResource) -> Unit,
    private val onEditClick: (MediaResource) -> Unit,
    private val onDeleteClick: (MediaResource) -> Unit,
    private val onMoveUpClick: (MediaResource) -> Unit,
    private val onMoveDownClick: (MediaResource) -> Unit
) : ListAdapter<MediaResource, ResourceAdapter.ResourceViewHolder>(ResourceDiffCallback()) {

    private var selectedResourceId: Long? = null

    fun setSelectedResource(resourceId: Long?) {
        val previousId = selectedResourceId
        selectedResourceId = resourceId
        
        currentList.forEachIndexed { index, resource ->
            if (resource.id == previousId || resource.id == resourceId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResourceViewHolder {
        val binding = ItemResourceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ResourceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResourceViewHolder, position: Int) {
        holder.bind(getItem(position), selectedResourceId)
    }

    inner class ResourceViewHolder(
        private val binding: ItemResourceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(resource: MediaResource, selectedId: Long?) {
            binding.apply {
                tvResourceName.text = resource.name
                tvResourcePath.text = resource.path
                tvResourceType.text = resource.type.name
                tvFileCount.text = "${resource.fileCount} files"
                
                val mediaTypesText = buildString {
                    if (MediaType.IMAGE in resource.supportedMediaTypes) append("I")
                    if (MediaType.VIDEO in resource.supportedMediaTypes) append("V")
                    if (MediaType.AUDIO in resource.supportedMediaTypes) append("A")
                    if (MediaType.GIF in resource.supportedMediaTypes) append("G")
                }
                tvMediaTypes.text = mediaTypesText
                
                tvDestinationMark.text = if (resource.isDestination) "→" else ""
                
                tvWritableIndicator.visibility = if (resource.isWritable) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
                }
                
                root.isSelected = resource.id == selectedId
                
                val gestureDetector = GestureDetector(root.context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        onItemClick(resource)
                        return true
                    }
                    
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        onItemDoubleClick(resource)
                        return true
                    }
                    
                    override fun onLongPress(e: MotionEvent) {
                        onItemLongClick(resource)
                    }
                })
                
                root.setOnTouchListener { v, event ->
                    val result = gestureDetector.onTouchEvent(event)
                    // Don't consume all events - let the view handle selection state changes
                    if (!result && event.action == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    result
                }
                
                btnEdit.setOnClickListener {
                    onEditClick(resource)
                }
                
                btnDelete.setOnClickListener {
                    onDeleteClick(resource)
                }
                
                btnMoveUp.setOnClickListener {
                    onMoveUpClick(resource)
                }
                
                btnMoveDown.setOnClickListener {
                    onMoveDownClick(resource)
                }
            }
        }
    }

    private class ResourceDiffCallback : DiffUtil.ItemCallback<MediaResource>() {
        override fun areItemsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem == newItem
        }
    }
}
