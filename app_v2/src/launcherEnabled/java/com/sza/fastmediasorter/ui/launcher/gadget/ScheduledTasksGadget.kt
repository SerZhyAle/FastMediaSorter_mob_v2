package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.databinding.GadgetLauncherListBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * S1170: the scheduled-tasks widget as a desktop cell.
 *
 * Running an operation can copy, move or DELETE files, so a row tap does not fire it - it runs
 * [LauncherCellCommand.ScheduledOp], which the launcher answers with the same confirmation dialog a
 * scheduled-operation shortcut cell already uses. Going through the command model rather than calling
 * the execute use case here is what buys that: the confirmation, the launch guard and the journal entry
 * all live on that one path.
 */
class ScheduledTasksGadget @Inject constructor(
    private val scheduledOperationRepository: ScheduledOperationRepository,
) : LauncherGadget {

    override val key: String = KEY
    override val defaultSpanW: Int = SPAN_W
    override val defaultSpanH: Int = SPAN_H
    override val labelRes: Int = R.string.widget_scheduled_tasks_label
    override val iconRes: Int = R.drawable.ic_widget_scheduled_tasks
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        ScheduledTasksGadgetView(container.context, host, scheduledOperationRepository)

    private companion object {
        const val KEY = "scheduled_tasks"

        /** The widget's own declared `targetCellWidth` / `targetCellHeight`; the cell resizes. */
        const val SPAN_W = 2
        const val SPAN_H = 1
    }
}

private class ScheduledTasksGadgetView(
    context: Context,
    private val host: LauncherGadgetHost,
    private val scheduledOperationRepository: ScheduledOperationRepository,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherListBinding.inflate(LayoutInflater.from(context), this)

    private val rowAdapter = LauncherGadgetRowAdapter { row ->
        row.id.toLongOrNull()?.let { host.run(LauncherCellCommand.ScheduledOp(it)) }
    }

    init {
        binding.gadgetListTitle.setText(R.string.widget_scheduled_tasks_label)
        binding.gadgetListItems.layoutManager = LinearLayoutManager(context)
        binding.gadgetListItems.adapter = rowAdapter
        binding.gadgetListMessage.setOnClickListener {
            host.run(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_SCHEDULED_TASKS))
        }
    }

    override suspend fun CoroutineScope.onActive() {
        scheduledOperationRepository.getAll().collect { operations ->
            // Disabled operations are listed by neither the widget nor the settings summary - a row that
            // cannot run would just be a dead tap target.
            val visible = operations.filter { it.isEnabled }.take(OPERATION_LIMIT)
            binding.gadgetListMessage.isVisible = visible.isEmpty()
            binding.gadgetListItems.isVisible = visible.isNotEmpty()
            if (visible.isEmpty()) {
                binding.gadgetListMessage.setText(R.string.launcher_gadget_scheduled_empty)
                binding.gadgetListMessage.isClickable = true
                binding.gadgetListMessage.isFocusable = true
                return@collect
            }
            rowAdapter.submitList(
                visible.map { operation ->
                    LauncherGadgetRow(
                        id = operation.id.toString(),
                        // Same wording the widget's row factory shows, so the two never disagree.
                        title = operation.operationType.name,
                        iconRes = R.drawable.ic_widget_scheduled_tasks,
                        tintIcon = true,
                    )
                }
            )
        }
    }

    private companion object {
        const val OPERATION_LIMIT = 10
    }
}
