package com.sza.fastmediasorter.ui

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.ConnectionConfig

class ConnectionAdapter(
    private val onItemClick: (ConnectionConfig) -> Unit,
    private val onItemDoubleClick: (ConnectionConfig) -> Unit,
    private val onDeleteClick: (ConnectionConfig) -> Unit,
) : ListAdapter<ConnectionConfig, ConnectionAdapter.ConnectionViewHolder>(ConnectionDiffCallback()) {
    private var selectedPosition: Int = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ConnectionViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_connection, parent, false)
        return ConnectionViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ConnectionViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position), position == selectedPosition)
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

    fun clearSelection() {
        val oldPosition = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (oldPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(oldPosition)
        }
    }

    inner class ConnectionViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.connectionName)
        private val detailsText: TextView = itemView.findViewById(R.id.connectionDetails)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        private val gestureDetector =
            GestureDetector(
                itemView.context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        val position = bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            onItemClick(getItem(position))
                            setSelectedPosition(position)
                        }
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val position = bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            onItemDoubleClick(getItem(position))
                        }
                        return true
                    }
                },
            )

        fun bind(
            config: ConnectionConfig,
            isSelected: Boolean,
        ) {
            nameText.text = config.name
            detailsText.text = "${config.serverAddress}/${config.folderPath}"

            // Highlight selected item with theme-aware color
            if (isSelected) {
                val theme = itemView.context.theme
                val typedValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.colorControlHighlight, typedValue, true)
                itemView.setBackgroundColor(itemView.context.getColor(typedValue.resourceId))
            } else {
                itemView.setBackgroundColor(itemView.context.getColor(android.R.color.transparent))
            }

            // Use theme-aware colors for text
            val theme = itemView.context.theme
            val typedValue = android.util.TypedValue()

            theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            val primaryTextColor = itemView.context.getColor(typedValue.resourceId)

            theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
            val secondaryTextColor = itemView.context.getColor(typedValue.resourceId)

            nameText.setTextColor(primaryTextColor)
            detailsText.setTextColor(secondaryTextColor)

            itemView.setOnTouchListener { v, event ->
                gestureDetector.onTouchEvent(event)
                v.performClick()
                true
            }

            deleteButton.setOnClickListener {
                onDeleteClick(config)
            }
        }
    }

    class ConnectionDiffCallback : DiffUtil.ItemCallback<ConnectionConfig>() {
        override fun areItemsTheSame(
            oldItem: ConnectionConfig,
            newItem: ConnectionConfig,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: ConnectionConfig,
            newItem: ConnectionConfig,
        ): Boolean = oldItem == newItem
    }
}
