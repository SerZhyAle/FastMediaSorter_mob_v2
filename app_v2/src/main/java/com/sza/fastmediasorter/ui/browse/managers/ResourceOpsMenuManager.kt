package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.content.Intent
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import com.sza.fastmediasorter.ui.duplicates.DuplicatesActivity
import com.sza.fastmediasorter.util.VirtualPathUtils
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

class ResourceOpsMenuManager @Inject constructor(
    @ActivityContext private val context: Context
) {
    fun showMenu(
        anchor: android.view.View,
        viewModel: BrowseViewModel,
        isScheduleEnabled: Boolean = false,
        onAutomateSource: (() -> Unit)? = null,
        onAddToDestinations: (() -> Unit)? = null,
        onArchive: (() -> Unit)? = null,
        isDestinationsFull: Boolean = false
    ) {
        val popup = PopupMenu(context, anchor)
        popup.inflate(R.menu.menu_resource_ops)

        // Hide "Create folder" if the resource doesn't support subfolder navigation, is read-only,
        // or is a virtual resource (e.g. "All Video", "Recent") that has no real path to write to.
        val resource = viewModel.state.value.resource
        val canCreateFolder = resource != null
                && resource.showSubfoldersAsItems
                && !resource.isReadOnly
                && !VirtualPathUtils.isVirtualPath(resource.path)
        popup.menu.findItem(R.id.action_create_folder)?.isVisible = canCreateFolder

        // Archive item: hidden for non-local sources (matches toolbar btnArchive predicate),
        // grayed out when no files are selected so users see the action but learn it needs a selection.
        val hasSelection = viewModel.state.value.selectedFiles.isNotEmpty()
        val isLocalResource = resource?.type == ResourceType.LOCAL
        popup.menu.findItem(R.id.action_archive)?.apply {
            isVisible = isLocalResource && onArchive != null
            isEnabled = hasSelection
        }

        popup.menu.findItem(R.id.action_automate_resource)?.isVisible =
            isScheduleEnabled && onAutomateSource != null

        // Show "Add to Sort List" only when:
        // 1. Resource is not read-only
        // 2. Resource is not yet a destination
        // 3. Resource is not a virtual/predefined resource (no target folder)
        // 4. Recipients limit not reached (see GetDestinationsUseCase.isDestinationsFull)
        popup.menu.findItem(R.id.action_add_to_receivers)?.isVisible =
            resource != null && !resource.isReadOnly && !resource.isDestination &&
            !VirtualPathUtils.isVirtualPath(resource.path) && !isDestinationsFull

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_find_duplicates -> {
                    context.startActivity(
                        Intent(context, DuplicatesActivity::class.java).apply {
                            resource?.id?.let { putExtra(DuplicatesActivity.EXTRA_RESOURCE_ID, it) }
                        }
                    )
                    true
                }
                R.id.action_delete_duplicates -> {
                    context.startActivity(
                        Intent(context, DuplicatesActivity::class.java).apply {
                            resource?.id?.let { putExtra(DuplicatesActivity.EXTRA_RESOURCE_ID, it) }
                            putExtra(DuplicatesActivity.EXTRA_AUTO_DELETE, true)
                        }
                    )
                    true
                }
                R.id.action_delete_by_size -> {
                    showDeleteBySizeDialog(viewModel)
                    true
                }
                R.id.action_archive -> {
                    onArchive?.invoke()
                    true
                }
                R.id.action_create_folder -> {
                    showCreateFolderDialog(viewModel)
                    true
                }
                R.id.action_automate_resource -> {
                    onAutomateSource?.invoke()
                    true
                }
                R.id.action_add_to_receivers -> {
                    onAddToDestinations?.invoke()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // -------------------------------------------------------------------------
    // Delete by size — Phase 1: settings dialog
    // -------------------------------------------------------------------------

    private fun showDeleteBySizeDialog(viewModel: BrowseViewModel) {
        val activity = context as? BrowseActivity ?: return

        val dp8 = (8 * activity.resources.displayMetrics.density).toInt()
        val dp16 = dp8 * 2
        val dp64 = dp8 * 8

        // Root layout
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp64, dp16, dp64, dp16)
        }

        // Condition: Smaller / Larger
        val radioGroup = RadioGroup(activity).apply {
            orientation = RadioGroup.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp8 }
        }
        val rbSmaller = RadioButton(activity).apply {
            id = android.R.id.button1
            text = activity.getString(R.string.delete_by_size_smaller)
        }
        val rbLarger = RadioButton(activity).apply {
            id = android.R.id.button2
            text = activity.getString(R.string.delete_by_size_larger)
        }
        radioGroup.addView(rbSmaller)
        radioGroup.addView(rbLarger)
        rbSmaller.isChecked = true

        // Value + unit row
        val valueRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp8 }
        }
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "100"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val units = listOf(
            activity.getString(R.string.delete_by_size_unit_kb),
            activity.getString(R.string.delete_by_size_unit_mb),
            activity.getString(R.string.delete_by_size_unit_gb)
        )
        val spinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, units)
            setSelection(1) // MB by default
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = dp8; gravity = Gravity.CENTER_VERTICAL }
        }
        valueRow.addView(input)
        valueRow.addView(spinner)

        root.addView(radioGroup)
        root.addView(valueRow)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.action_delete_by_size)
            .setView(root)
            .setPositiveButton(R.string.delete_by_size_analyze) { _, _ ->
                val rawValue = input.text.toString().toFloatOrNull() ?: return@setPositiveButton
                val multiplier = when (spinner.selectedItemPosition) {
                    0 -> 1f / 1024f          // KB -> MB
                    2 -> 1024f               // GB -> MB
                    else -> 1f               // MB
                }
                val valueMb = rawValue * multiplier
                val isSmaller = rbSmaller.isChecked
                val minSize = if (!isSmaller) valueMb else null
                val maxSize = if (isSmaller) valueMb else null
                viewModel.scanBySize(minSizeMb = minSize, maxSizeMb = maxSize)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // -------------------------------------------------------------------------
    // Delete by size — Phase 2: confirmation dialog (called from BrowseActivity)
    // -------------------------------------------------------------------------

    fun showDeleteBySizeConfirm(
        viewModel: BrowseViewModel,
        count: Int,
        totalBytes: Long,
        matchedFiles: List<MediaFile>
    ) {
        val activity = context as? BrowseActivity ?: return

        val dp8 = (8 * activity.resources.displayMetrics.density).toInt()
        val dp16 = dp8 * 2

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16 * 2, dp16, dp16 * 2, dp16)
        }

        val formattedSize = com.sza.fastmediasorter.core.util.formatFileSize(totalBytes)
        val messageText = activity.getString(R.string.delete_by_size_preview_result, count, formattedSize)

        val tvMessage = TextView(activity).apply {
            text = android.text.Html.fromHtml(messageText.replace("\n", "<br>"), android.text.Html.FROM_HTML_MODE_COMPACT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp8 }
        }
        root.addView(tvMessage)

        // Warning for network/cloud resources
        val resourceType = viewModel.state.value.resource?.type
        val isNetworkOrCloud = resourceType?.isNetworkResource == true
        if (isNetworkOrCloud) {
            val tvWarning = TextView(activity).apply {
                text = activity.getString(R.string.delete_by_size_warning_no_trash)
                setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError, 0))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp8 }
            }
            root.addView(tvWarning)
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.delete_by_size_preview_title)
            .setView(root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_by_size_confirm_btn) { _, _ ->
                viewModel.executeBySizeDeleteConfirmed(matchedFiles)
            }
            .show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            ?.let { btn ->
                val errorColor = MaterialColors.getColor(btn, androidx.appcompat.R.attr.colorError, 0)
                btn.setTextColor(errorColor)
            }
    }

    // -------------------------------------------------------------------------
    // Create folder
    // -------------------------------------------------------------------------

    private fun showCreateFolderDialog(viewModel: BrowseViewModel) {
        val activity = context as? BrowseActivity ?: return

        val forbiddenChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

        val tilWrapper = TextInputLayout(activity, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint = activity.getString(R.string.create_folder_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            val dp16 = (16 * activity.resources.displayMetrics.density).toInt()
            val dp24 = (24 * activity.resources.displayMetrics.density).toInt()
            setPadding(dp24, dp16, dp24, dp16)
        }
        val inputEdit = TextInputEditText(tilWrapper.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        tilWrapper.addView(inputEdit)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.create_folder_title)
            .setView(tilWrapper)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val folderName = inputEdit.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    viewModel.createFolder(folderName)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        // Disable OK button initially and enable live validation
        val okButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        okButton?.isEnabled = false

        inputEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim() ?: ""
                when {
                    text.isEmpty() -> {
                        tilWrapper.error = null
                        okButton?.isEnabled = false
                    }
                    text.any { it in forbiddenChars } -> {
                        tilWrapper.error = activity.getString(R.string.error_invalid_folder_name)
                        okButton?.isEnabled = false
                    }
                    else -> {
                        tilWrapper.error = null
                        okButton?.isEnabled = true
                    }
                }
            }
        })
    }
}

