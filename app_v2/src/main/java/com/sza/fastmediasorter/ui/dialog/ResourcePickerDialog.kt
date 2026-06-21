package com.sza.fastmediasorter.ui.dialog

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import kotlinx.coroutines.flow.first

/**
 * Dialog for selecting a resource (e.g. slideshow music source). Thin configuration over the
 * shared [ListSelectionDialog] (S0567) - item rendering and theming live in the generic dialog.
 */
class ResourcePickerDialog(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    getResourcesUseCase: GetResourcesUseCase,
    currentSelection: Long?,
    title: String,
    allowClear: Boolean = true,
    onResourceSelected: (MediaResource?) -> Unit,
) : ListSelectionDialog<MediaResource>(
    context,
    ListSelectionConfig(
        title = title,
        lifecycleOwner = lifecycleOwner,
        loader = { getResourcesUseCase.invoke().first() },
        formatter = object : ListSelectionAdapter.ItemFormatter<MediaResource> {
            override fun getDisplayName(item: MediaResource): String = item.name
        },
        hasSelection = currentSelection != null,
        isSelected = { it.id == currentSelection },
        allowClear = allowClear,
        emptyMessageRes = R.string.no_resources_available,
        errorMessageRes = R.string.toast_error_loading_resources,
        onSelected = onResourceSelected,
    ),
)
