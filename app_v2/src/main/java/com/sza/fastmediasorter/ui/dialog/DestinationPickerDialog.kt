package com.sza.fastmediasorter.ui.dialog

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.ui.settings.helpers.LocalFolderDestinationPickerManager
import kotlinx.coroutines.flow.first

/**
 * Dialog for selecting a destination resource (frame save target, etc.). Shows only resources
 * marked as destinations (the use case excludes virtual/read-only). Thin configuration over the
 * shared [ListSelectionDialog] (S0567).
 *
 * S1010: [includeLocalFolder] is opt-in - only a persisted write-receiver setting sets it, routing
 * [onResourceSelected] through [LocalFolderDestinationPickerManager.wrapOnSelected], so the ephemeral
 * "save log to resource" call site keeps its original list and behavior.
 */
class DestinationPickerDialog(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    getDestinationsUseCase: GetDestinationsUseCase,
    currentSelection: Long?,
    title: String,
    allowClear: Boolean = true,
    includeLocalFolder: Boolean = false,
    onResourceSelected: (MediaResource?) -> Unit,
) : ListSelectionDialog<MediaResource>(
    context,
    ListSelectionConfig(
        title = title,
        lifecycleOwner = lifecycleOwner,
        loader = {
            val base = getDestinationsUseCase.invoke().first()
            if (includeLocalFolder) {
                listOf(LocalFolderDestinationPickerManager.sentinelItem(context)) + base
            } else {
                base
            }
        },
        formatter = object : ListSelectionAdapter.ItemFormatter<MediaResource> {
            override fun getDisplayName(item: MediaResource): String = item.name
        },
        hasSelection = currentSelection != null,
        isSelected = { it.id == currentSelection },
        allowClear = allowClear,
        emptyMessageRes = R.string.no_destinations_available,
        errorMessageRes = R.string.save_frame_error,
        onSelected = onResourceSelected,
    ),
)
