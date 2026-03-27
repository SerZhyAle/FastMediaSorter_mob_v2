package com.sza.fastmediasorter.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemScheduledOperationBinding
import com.sza.fastmediasorter.domain.model.FileTypeFilter
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.TimeFilter

class ScheduledOperationsAdapter(
    private val onToggle: (ScheduledOperation) -> Unit,
    private val onEdit: (ScheduledOperation) -> Unit,
    private val onDelete: (ScheduledOperation) -> Unit,
    private val onRunNow: (ScheduledOperation) -> Unit,
    private val resourceNameProvider: (Long?) -> String?
) : ListAdapter<ScheduledOperation, ScheduledOperationsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScheduledOperationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val b: ItemScheduledOperationBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(op: ScheduledOperation) {
            val ctx = b.root.context

            // Toggle
            b.switchEnabled.isChecked = op.isEnabled
            b.switchEnabled.setOnCheckedChangeListener(null)
            b.switchEnabled.setOnCheckedChangeListener { _, _ -> onToggle(op) }

            // Op type icon
            val iconRes = when (op.operationType) {
                ScheduledOpType.COPY -> R.drawable.ic_copy
                ScheduledOpType.MOVE -> R.drawable.ic_move
                ScheduledOpType.DELETE -> R.drawable.ic_delete
            }
            b.ivOpTypeIcon.setImageResource(iconRes)

            // Source / target names
            b.tvSourceName.text = resourceNameProvider(op.sourceResourceId)
                ?: ctx.getString(R.string.scheduled_op_unknown_resource)

            val isDelete = op.operationType == ScheduledOpType.DELETE
            b.tvArrow.isVisible = !isDelete
            b.tvTargetName.isVisible = !isDelete
            if (!isDelete) {
                b.tvTargetName.text = resourceNameProvider(op.targetResourceId)
                    ?: ctx.getString(R.string.scheduled_op_unknown_resource)
            }

            // Error badge
            b.tvErrorBadge.isVisible = op.lastRunStatus != null && op.lastRunStatus.startsWith("ERROR")

            // Schedule info
            val hh = op.startTimeHour.toString().padStart(2, '0')
            val mm = op.startTimeMinute.toString().padStart(2, '0')
            val intervalStr = buildIntervalString(ctx, op.intervalHours, op.intervalMinutes)
            b.tvScheduleInfo.text = ctx.getString(R.string.scheduled_op_schedule_summary, "$hh:$mm", intervalStr)

            // Filters
            val typeLabel = when (op.fileTypeFilter) {
                FileTypeFilter.ALL -> ctx.getString(R.string.scheduled_op_filter_all)
                FileTypeFilter.AUDIO -> ctx.getString(R.string.scheduled_op_filter_audio)
                FileTypeFilter.IMAGES -> ctx.getString(R.string.scheduled_op_filter_images)
                FileTypeFilter.VIDEO -> ctx.getString(R.string.scheduled_op_filter_video)
                FileTypeFilter.DOCUMENTS -> ctx.getString(R.string.scheduled_op_filter_docs)
            }
            val timeLabel = when (op.timeFilter) {
                TimeFilter.ALL -> ""
                TimeFilter.SINCE_LAST -> " · ${ctx.getString(R.string.scheduled_op_time_since_last)}"
                TimeFilter.LAST_HOUR -> " · ${ctx.getString(R.string.scheduled_op_time_last_hour)}"
                TimeFilter.LAST_DAY -> " · ${ctx.getString(R.string.scheduled_op_time_last_day)}"
            }
            b.tvFilters.text = typeLabel + timeLabel

            // Buttons
            b.btnRunNow.setOnClickListener { onRunNow(op) }
            b.btnEdit.setOnClickListener { onEdit(op) }
            b.btnDelete.setOnClickListener { onDelete(op) }
        }

        private fun buildIntervalString(ctx: android.content.Context, hours: Int, minutes: Int): String {
            return when {
                hours > 0 && minutes > 0 -> ctx.getString(R.string.scheduled_op_interval_hm, hours, minutes)
                hours > 0 -> ctx.getString(R.string.scheduled_op_interval_h, hours)
                else -> ctx.getString(R.string.scheduled_op_interval_m, minutes)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ScheduledOperation>() {
        override fun areItemsTheSame(oldItem: ScheduledOperation, newItem: ScheduledOperation) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ScheduledOperation, newItem: ScheduledOperation) =
            oldItem == newItem
    }
}
