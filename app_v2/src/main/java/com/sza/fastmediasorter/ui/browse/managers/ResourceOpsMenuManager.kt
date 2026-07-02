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
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.util.DrawingTargetPolicy
import com.sza.fastmediasorter.util.TextNoteTargetPolicy
import com.sza.fastmediasorter.util.VirtualPathUtils
import dagger.hilt.android.qualifiers.ActivityContext
import timber.log.Timber
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
        isDestinationsFull: Boolean = false,
        onCameraCapture: (() -> Unit)? = null,
        isCameraVisible: Boolean = false,
        // S0371: record-video command, gated independently of the camera-photo command.
        onVideoCapture: (() -> Unit)? = null,
        isVideoVisible: Boolean = false,
        // S0184: multi-window entry point, controlled by user setting + device capability default.
        allowSeparateWindow: Boolean = false,
        openBrowseInNewWindow: ((Long) -> Unit)? = null,
        // S0096: black screen for audio - shown only for audio-only libraries
        isAudioOnly: Boolean = false,
        onBlackScreenClicked: (() -> Unit)? = null,
        // S0374: adaptive overflow - top-bar commands that did not fit are surfaced here.
        isOverflowed: (Int) -> Boolean = { false },
        callbacks: BrowseButtonSetupHelper.ButtonCallbacks? = null,
        onSortClicked: (() -> Unit)? = null
    ) {
        val popup = PopupMenu(context, anchor)
        popup.inflate(R.menu.menu_resource_ops)

        // S0374: a top-bar command appears in this menu iff the overflow manager pushed it off
        // the bar. Push model - the manager is the single owner of "is this command visible".
        popup.menu.findItem(R.id.action_overflow_sort)?.isVisible = isOverflowed(R.id.btnSort)
        popup.menu.findItem(R.id.action_overflow_filter)?.isVisible = isOverflowed(R.id.btnFilter)
        popup.menu.findItem(R.id.action_overflow_refresh)?.isVisible = isOverflowed(R.id.btnRefresh)
        popup.menu.findItem(R.id.action_overflow_toggle_view)?.isVisible = isOverflowed(R.id.btnToggleView)
        popup.menu.findItem(R.id.action_overflow_select_all)?.isVisible = isOverflowed(R.id.btnSelectAll)
        popup.menu.findItem(R.id.action_overflow_deselect_all)?.isVisible = isOverflowed(R.id.btnDeselectAll)
        popup.menu.findItem(R.id.action_overflow_play)?.isVisible = isOverflowed(R.id.btnPlay)
        popup.menu.findItem(R.id.action_overflow_play_random)?.isVisible = isOverflowed(R.id.btnPlayRandom)
        popup.menu.findItem(R.id.action_overflow_mic)?.isVisible = isOverflowed(R.id.btnMicRecord)

        // Hide "Create folder" if the resource doesn't support subfolder navigation, is read-only,
        // or is a virtual resource (e.g. "All Video", "Recent") that has no real path to write to.
        val resource = viewModel.state.value.resource
        val canCreateFolder = resource != null
                && resource.showSubfoldersAsItems
                && !resource.isReadOnly
                && !VirtualPathUtils.isVirtualPath(resource.path)
        popup.menu.findItem(R.id.action_create_folder)?.isVisible =
            canCreateFolder && isOverflowed(R.id.btnCreateFolder)

        // S0189: virtual "All Documents" writes new notes to the public Documents folder.
        val canCreateTextNote = TextNoteTargetPolicy.canCreateTextNote(resource)
        popup.menu.findItem(R.id.action_create_text_file)?.isVisible =
            canCreateTextNote && isOverflowed(R.id.btnCreateTextFile)

        // S0363: drawing allowed on real image folders + the virtual "all images" / "camera" resources.
        val canCreateDrawing = DrawingTargetPolicy.canCreateDrawing(resource)
        popup.menu.findItem(R.id.action_create_drawing)?.isVisible =
            canCreateDrawing && isOverflowed(R.id.btnCreateDrawing)

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

        popup.menu.findItem(R.id.action_camera_capture)?.isVisible = isCameraVisible && onCameraCapture != null
        popup.menu.findItem(R.id.action_video_capture)?.isVisible = isVideoVisible && onVideoCapture != null
        popup.menu.findItem(R.id.action_open_in_separate_window)?.isVisible =
            allowSeparateWindow && resource != null && openBrowseInNewWindow != null

        popup.menu.findItem(R.id.action_black_screen)?.isVisible =
            isAudioOnly && onBlackScreenClicked != null

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
                R.id.action_create_text_file -> {
                    showCreateTextNoteDialog(viewModel)
                    true
                }
                R.id.action_create_drawing -> {
                    showCreateDrawingDialog(viewModel)
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
                R.id.action_camera_capture -> {
                    onCameraCapture?.invoke()
                    true
                }
                R.id.action_video_capture -> {
                    onVideoCapture?.invoke()
                    true
                }
                R.id.action_open_in_separate_window -> {
                    resource?.id?.let { openBrowseInNewWindow?.invoke(it) }
                    true
                }
                R.id.action_black_screen -> {
                    onBlackScreenClicked?.invoke()
                    true
                }
                // S0806: reach the main app settings window without going back to the home window.
                R.id.action_open_app_settings -> {
                    Timber.d("S0806: open app settings from browse overflow menu")
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                    true
                }
                // S0374: overflowed top-bar commands route to the same actions as their buttons.
                R.id.action_overflow_sort -> { onSortClicked?.invoke(); true }
                R.id.action_overflow_filter -> { callbacks?.onFilterClicked(); true }
                R.id.action_overflow_refresh -> { callbacks?.onRefreshClicked(); true }
                R.id.action_overflow_toggle_view -> { callbacks?.onToggleViewClicked(); true }
                R.id.action_overflow_select_all -> { callbacks?.onSelectAllClicked(); true }
                R.id.action_overflow_deselect_all -> { callbacks?.onDeselectAllClicked(); true }
                R.id.action_overflow_play -> { callbacks?.onPlayClicked(); true }
                R.id.action_overflow_play_random -> { callbacks?.onPlayRandomClicked(); true }
                // Press-and-hold recording is bar-only; the menu entry fires a single-tap record.
                R.id.action_overflow_mic -> { callbacks?.onMicRecordSingleTap(); true }
                else -> false
            }
        }
        popup.show()
    }

    // -------------------------------------------------------------------------
    // Delete by size - Phase 1: settings dialog
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
    // Delete by size - Phase 2: confirmation dialog (called from BrowseActivity)
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

        MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
            .setTitle(R.string.delete_by_size_preview_title)
            .setView(root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_by_size_confirm_btn) { _, _ ->
                viewModel.executeBySizeDeleteConfirmed(matchedFiles)
            }
            .show()
    }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------

    fun showCreateFolderDialog(viewModel: BrowseViewModel) {
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
            // Suppress system autofill prompts (e.g. "Sign in with Google" on devices without an account).
            // This is a file-name field - credential autofill is meaningless here.
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            }
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

    // -------------------------------------------------------------------------
    // Create text note
    // -------------------------------------------------------------------------

    fun showCreateTextNoteDialog(viewModel: BrowseViewModel) {
        // S0189: defend the keyboard-shortcut entry point with the same gate the toolbar/menu use
        // (toolbar button + popup menu hide themselves, but a bound key still reaches this method).
        val resource = viewModel.state.value.resource
        if (!TextNoteTargetPolicy.canCreateTextNote(resource)) {
            return
        }

        // S0189: no pre-creation rename dialog. The name is requested by the save-with-rename
        // dialog when the user actually commits the note (defer-creation contract). Until then
        // the file does not exist on disk, so asking for a name now is pure friction.
        val default = com.sza.fastmediasorter.util.TextNoteFileNameProvider.defaultName()
        viewModel.createTextNote(default)
    }

    fun showCreateDrawingDialog(viewModel: BrowseViewModel) {
        val resource = viewModel.state.value.resource
        if (!DrawingTargetPolicy.canCreateDrawing(resource)) {
            return
        }

        // S0191 needs real image bytes before the editor opens, so the "defer everything until
        // Save" trick from text notes does not apply. We still skip the pre-create rename dialog
        // to keep the Browse flow one-tap: the user can rename on Save inside the editor.
        val default = com.sza.fastmediasorter.core.files.FileNameDefaultProvider("jpg").defaultName()
        viewModel.createDrawing(default)
    }
}
