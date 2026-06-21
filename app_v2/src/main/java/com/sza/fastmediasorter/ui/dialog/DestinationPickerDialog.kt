package com.sza.fastmediasorter.ui.dialog

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import kotlinx.coroutines.flow.first

/**
 * Dialog for selecting a destination resource (frame save target, etc.). Shows only resources
 * marked as destinations (the use case excludes virtual/read-only). Thin configuration over the
 * shared [ListSelectionDialog] (S0567).
 */
class DestinationPickerDialog(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    getDestinationsUseCase: GetDestinationsUseCase,
    currentSelection: Long?,
    title: String,
    allowClear: Boolean = true,
    onResourceSelected: (MediaResource?) -> Unit,
) : ListSelectionDialog<MediaResource>(
    context,
    ListSelectionConfig(
        title = title,
        lifecycleOwner = lifecycleOwner,
        loader = { getDestinationsUseCase.invoke().first() },
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
