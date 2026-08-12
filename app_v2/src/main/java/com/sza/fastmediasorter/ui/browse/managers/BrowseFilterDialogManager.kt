package com.sza.fastmediasorter.ui.browse.managers

import android.app.DatePickerDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.DialogFilterBinding
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.util.showBoundToHost
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal class BrowseFilterDialogManager(
    private val activity: AppCompatActivity,
    private val callbacks: BrowseDialogHelper.DialogCallbacks,
    private val mediaCapabilities: MediaCapabilities
) {
    fun showFilterDialog(currentFilter: FileFilter?, allowedMediaTypes: Set<MediaType>? = null) {
        val dialogBinding = DialogFilterBinding.inflate(LayoutInflater.from(activity))
        dialogBinding.etFilterName.setText(currentFilter?.nameContains ?: "")

        val allowed = allowedMediaTypes ?: MediaType.entries.toSet()
        val allTypesSelected = currentFilter?.mediaTypes == null
        val typeOptions = listOf(
            MediaTypeOption(MediaType.IMAGE, dialogBinding.cbFilterImage, mediaCapabilities.supportsImages),
            MediaTypeOption(MediaType.VIDEO, dialogBinding.cbFilterVideo, mediaCapabilities.supportsVideo),
            MediaTypeOption(MediaType.AUDIO, dialogBinding.cbFilterAudio, mediaCapabilities.supportsAudio),
            MediaTypeOption(MediaType.GIF, dialogBinding.cbFilterGif, mediaCapabilities.supportsImages),
            MediaTypeOption(MediaType.TEXT, dialogBinding.cbFilterText, mediaCapabilities.supportsDocuments),
            MediaTypeOption(MediaType.PDF, dialogBinding.cbFilterPdf, mediaCapabilities.supportsDocuments),
            MediaTypeOption(MediaType.EPUB, dialogBinding.cbFilterEpub, mediaCapabilities.supportsEpub),
            MediaTypeOption(MediaType.OFFICE_DOCUMENT, dialogBinding.cbFilterOffice, mediaCapabilities.supportsDocuments)
        )

        typeOptions.forEach { option ->
            val isVisible = option.type in allowed && option.isSupported
            option.applyState(
                visible = isVisible,
                checked = isVisible && (
                    allTypesSelected || currentFilter?.mediaTypes?.contains(option.type) == true
                )
            )
        }

        var minDate = currentFilter?.minDate
        var maxDate = currentFilter?.maxDate
        if (minDate != null) {
            dialogBinding.etMinDate.setText(formatDate(minDate))
        }
        if (maxDate != null) {
            dialogBinding.etMaxDate.setText(formatDate(maxDate))
        }

        dialogBinding.etMinDate.setOnClickListener {
            showDatePicker(minDate) { selectedDate ->
                minDate = selectedDate
                dialogBinding.etMinDate.setText(formatDate(selectedDate))
            }
        }
        dialogBinding.etMaxDate.setOnClickListener {
            showDatePicker(maxDate) { selectedDate ->
                maxDate = selectedDate
                dialogBinding.etMaxDate.setText(formatDate(selectedDate))
            }
        }

        currentFilter?.minSizeMb?.let {
            dialogBinding.etMinSize.setText(activity.getString(R.string.string_format, it.toString()))
        }
        currentFilter?.maxSizeMb?.let {
            dialogBinding.etMaxSize.setText(activity.getString(R.string.string_format, it.toString()))
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.filter)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnClearFilter.setOnClickListener {
            callbacks.onFilterApplied(null)
            dialog.dismiss()
        }
        dialogBinding.btnResetTypes.setOnClickListener {
            typeOptions.forEach { option ->
                if (option.checkbox.visibility == View.VISIBLE) {
                    option.checkbox.isChecked = true
                }
            }
        }
        dialogBinding.btnCancelFilter.setOnClickListener {
            dialog.dismiss()
        }
        dialogBinding.btnApplyFilter.setOnClickListener {
            val nameFilter = dialogBinding.etFilterName.text?.toString()?.trim()
            val minSizeText = dialogBinding.etMinSize.text?.toString()?.trim()
            val maxSizeText = dialogBinding.etMaxSize.text?.toString()?.trim()
            val selectedTypes = typeOptions
                .filter { it.checkbox.isChecked && it.checkbox.visibility == View.VISIBLE }
                .mapTo(mutableSetOf()) { it.type }
            val allAllowedSelected = selectedTypes == allowed
            val filter = FileFilter(
                nameContains = nameFilter?.ifBlank { null },
                minDate = minDate,
                maxDate = maxDate,
                minSizeMb = minSizeText?.toFloatOrNull(),
                maxSizeMb = maxSizeText?.toFloatOrNull(),
                mediaTypes = if (allAllowedSelected) null else selectedTypes.ifEmpty { null }
            )
            callbacks.onFilterApplied(if (filter.isEmpty()) null else filter)
            dialog.dismiss()
        }

        dialog.showBoundToHost(activity)
        com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper.applyInitialFocus(dialog)
    }

    fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return format.format(Date(timestamp))
    }

    private fun showDatePicker(currentDate: Long?, onDateSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        if (currentDate != null) {
            calendar.timeInMillis = currentDate
        }

        DatePickerDialog(
            activity,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                onDateSelected(calendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private data class MediaTypeOption(
        val type: MediaType,
        val checkbox: CheckBox,
        val isSupported: Boolean
    ) {
        fun applyState(visible: Boolean, checked: Boolean) {
            val visibility = if (visible) View.VISIBLE else View.GONE
            (checkbox.parent as View).visibility = visibility
            checkbox.visibility = visibility
            checkbox.isChecked = checked
        }
    }
}
