package com.sza.fastmediasorter.ui.local

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R

class LocalFolderAdapter(
    private val folders: List<LocalFolder>,
    private val onFolderClick: (LocalFolder, Boolean) -> Unit,
) : RecyclerView.Adapter<LocalFolderAdapter.ViewHolder>() {
    private var selectedPosition: Int = RecyclerView.NO_POSITION

    class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val tvFolderIcon: TextView = view.findViewById(R.id.tvFolderIcon)
        val tvFolderName: TextView = view.findViewById(R.id.tvFolderName)
        val tvFolderCount: TextView = view.findViewById(R.id.tvFolderCount)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_local_folder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val folder = folders[position]
        holder.tvFolderIcon.text = folder.icon
        holder.tvFolderName.text = folder.name
        holder.tvFolderCount.text = "(${folder.count})"

        // Highlight selected item
        val isSelected = position == selectedPosition
        holder.itemView.setBackgroundColor(
            if (isSelected) {
                holder.itemView.context.getColor(R.color.primary)
            } else {
                holder.itemView.context.getColor(android.R.color.transparent)
            },
        )

        // Change text colors for selected item
        holder.tvFolderName.setTextColor(
            if (isSelected) {
                holder.itemView.context.getColor(android.R.color.white)
            } else {
                holder.itemView.context.getColor(android.R.color.white)
            },
        )

        holder.tvFolderCount.setTextColor(
            if (isSelected) {
                holder.itemView.context.getColor(android.R.color.white)
            } else {
                holder.itemView.context.getColor(android.R.color.darker_gray)
            },
        )

        val gestureDetector =
            GestureDetector(
                holder.itemView.context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        val oldPosition = selectedPosition
                        selectedPosition = holder.bindingAdapterPosition
                        notifyItemChanged(oldPosition)
                        notifyItemChanged(selectedPosition)
                        onFolderClick(folder, false)
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        onFolderClick(folder, true)
                        return true
                    }
                },
            )

        holder.itemView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            v.performClick()
            true
        }
    }

    fun setSelectedPosition(position: Int) {
        val oldPosition = selectedPosition
        selectedPosition = position
        if (oldPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(oldPosition)
        }
        if (selectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(selectedPosition)
        }
    }

    override fun getItemCount() = folders.size
}
