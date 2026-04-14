package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Toast
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogScheduledOperationBinding
import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.TimeFilter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduledOperationDialog(
    context: Context,
    private val resources: List<MediaResource>,
    private val destinations: List<MediaResource>,
    private val existing: ScheduledOperation? = null,
    private val prefilledSourceId: Long? = null,
    private val onSave: (ScheduledOperation) -> Unit
) : Dialog(context) {

    private lateinit var b: DialogScheduledOperationBinding

    private val nextRunFormat = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = DialogScheduledOperationBinding.inflate(layoutInflater)
        setContentView(b.root)
        val dm = context.resources.displayMetrics
        val isLandscape = dm.widthPixels > dm.heightPixels
        window?.setLayout(
            (dm.widthPixels * 0.93).toInt(),
            if (isLandscape) (dm.heightPixels * 0.90).toInt()
            else android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
        setupDropdowns()
        setupNextRunPreview()
        setupConditionsCollapse()
        // Default file type mask for new operations: ALL_FILES
        if (existing == null) applyFileTypeMask(FileTypeFlags.DEFAULT)
        populateExisting()
        applyPrefilledSource()
        setupTimePicker()
        setupIntervalPicker()
        setupSaveButtonState()
        b.btnCancel.setOnClickListener { dismiss() }
        b.btnSave.setOnClickListener { trySave() }
    }

    private fun setupDropdowns() {
        // Source resources — detect read-only on selection
        val sourceNames = resources.map { it.name }
        b.actvSource.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, sourceNames))
        b.actvSource.setOnItemClickListener { _, _, pos, _ ->
            val selected = resources.getOrNull(pos)
            applyReadOnlySourceConstraint(selected)
        }

        // Operations
        val opLabels = listOf(
            context.getString(R.string.scheduled_ops_op_copy),
            context.getString(R.string.scheduled_ops_op_move),
            context.getString(R.string.scheduled_ops_op_delete)
        )
        b.actvOperation.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, opLabels))
        b.actvOperation.setOnItemClickListener { _, _, pos, _ ->
            val isDelete = pos == 2
            b.tvTargetLabel.visibility = if (isDelete) View.GONE else View.VISIBLE
            b.tilTarget.visibility = if (isDelete) View.GONE else View.VISIBLE
            b.containerOverwrite.visibility = if (isDelete) View.GONE else View.VISIBLE
        }
        b.actvOperation.setText(opLabels[0], false)

        // Target (destinations only) — auto-fill if exactly one option
        val destNames = destinations.map { it.name }
        b.actvTarget.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, destNames))
        if (destinations.size == 1 && existing == null) {
            b.actvTarget.setText(destinations[0].name, false)
        }

        // File type filters — checkboxes with ALL_FILES interlock
        updateFileTypeCheckboxVisibility()
        setupFileTypeInterlock()

        // Time filters
        val timeLabels = listOf(
            context.getString(R.string.scheduled_ops_time_all),
            context.getString(R.string.scheduled_ops_time_since_last),
            context.getString(R.string.scheduled_ops_time_last_hour),
            context.getString(R.string.scheduled_ops_time_last_day)
        )
        b.actvTimeFilter.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, timeLabels))
        b.actvTimeFilter.setText(timeLabels[0], false)
    }

    private fun setupConditionsCollapse() {
        b.headerConditions.setOnClickListener {
            val expanded = b.containerConditionsContent.visibility == View.VISIBLE
            if (expanded) {
                b.containerConditionsContent.visibility = View.GONE
                b.ivConditionsChevron.rotation = 0f
            } else {
                b.containerConditionsContent.visibility = View.VISIBLE
                b.ivConditionsChevron.rotation = 180f
            }
        }
    }

    private fun setupSaveButtonState() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateSaveButtonState()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        b.actvSource.addTextChangedListener(watcher)
        b.actvTarget.addTextChangedListener(watcher)
        b.actvOperation.addTextChangedListener(watcher)
        updateSaveButtonState()
    }

    private fun updateSaveButtonState() {
        val sourceEmpty = b.actvSource.text.isNullOrBlank()
        val isDelete = b.actvOperation.text.toString() == context.getString(R.string.scheduled_ops_op_delete)
        val targetEmpty = !isDelete && b.actvTarget.text.isNullOrBlank()

        b.tilSource.error = if (sourceEmpty) context.getString(R.string.scheduled_ops_error_source_required) else null
        if (!isDelete) {
            b.tilTarget.error = if (targetEmpty) context.getString(R.string.scheduled_ops_error_target_required) else null
        } else {
            b.tilTarget.error = null
        }

        b.btnSave.isEnabled = !sourceEmpty && !targetEmpty
    }

    private fun setupTimePicker() {
        // Pre-fill start time with current time for new operations
        if (existing == null) {
            val now = Calendar.getInstance()
            b.etStartHour.setText(now.get(Calendar.HOUR_OF_DAY).toString())
            b.etStartMinute.setText(now.get(Calendar.MINUTE).toString().padStart(2, '0'))
        }

        b.btnPickTime.setOnClickListener {
            val hour = b.etStartHour.text.toString().toIntOrNull() ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val minute = b.etStartMinute.text.toString().toIntOrNull() ?: Calendar.getInstance().get(Calendar.MINUTE)
            TimePickerDialog(context, { _, h, m ->
                b.etStartHour.setText(h.toString())
                b.etStartMinute.setText(m.toString().padStart(2, '0'))
            }, hour, minute, true).show()
        }
    }

    private fun setupIntervalPicker() {
        // Default interval for new operations: 24h 00m
        if (existing == null) {
            b.etIntervalHours.setText("24")
            b.etIntervalMinutes.setText("00")
        }

        b.btnPickInterval.setOnClickListener {
            val currentHours = b.etIntervalHours.text.toString().toIntOrNull() ?: 24
            val currentMinutes = b.etIntervalMinutes.text.toString().toIntOrNull() ?: 0

            val pickerHours = NumberPicker(context).apply {
                minValue = 0; maxValue = 1440
                value = currentHours.coerceIn(0, 1440)
                wrapSelectorWheel = false
            }
            val pickerMinutes = NumberPicker(context).apply {
                minValue = 0; maxValue = 59
                value = currentMinutes.coerceIn(0, 59)
                wrapSelectorWheel = true
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val pad = (24 * context.resources.displayMetrics.density).toInt()
                setPadding(pad, 0, pad, 0)
                addView(pickerHours, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(pickerMinutes, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }

            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.scheduled_ops_col_schedule))
                .setView(row)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    b.etIntervalHours.setText(pickerHours.value.toString())
                    b.etIntervalMinutes.setText(pickerMinutes.value.toString().padStart(2, '0'))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun updateFileTypeCheckboxVisibility() {
        b.cbFileTypeAudio.visibility = if (BuildConfig.SUPPORT_AUDIO) View.VISIBLE else View.GONE
        b.cbFileTypeVideo.visibility = if (BuildConfig.SUPPORT_VIDEO) View.VISIBLE else View.GONE
        b.cbFileTypeDocs.visibility  = if (BuildConfig.SUPPORT_DOCUMENTS) View.VISIBLE else View.GONE
        // Hidden options must not remain selected or be persisted on unsupported flavors.
        if (!BuildConfig.SUPPORT_AUDIO) b.cbFileTypeAudio.isChecked = false
        if (!BuildConfig.SUPPORT_VIDEO) b.cbFileTypeVideo.isChecked = false
        if (!BuildConfig.SUPPORT_DOCUMENTS) b.cbFileTypeDocs.isChecked = false
    }

    private fun setupFileTypeInterlock() {
        // When ALL_FILES is checked, disable and uncheck other type checkboxes
        b.cbFileTypeAllFiles.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                listOf(b.cbFileTypeImages, b.cbFileTypeAudio, b.cbFileTypeVideo, b.cbFileTypeDocs).forEach {
                    it.isChecked = false
                    it.isEnabled = false
                }
            } else {
                listOf(b.cbFileTypeImages, b.cbFileTypeAudio, b.cbFileTypeVideo, b.cbFileTypeDocs).forEach {
                    it.isEnabled = true
                }
            }
        }
    }

    /** Reads checked checkboxes and builds the bitmask. Returns 0 if nothing is selected. */
    private fun buildFileTypeMask(): Int {
        var mask = 0
        if (b.cbFileTypeAllFiles.isChecked) mask = mask or FileTypeFlags.ALL_FILES
        if (b.cbFileTypeImages.isChecked)   mask = mask or FileTypeFlags.IMAGES
        if (BuildConfig.SUPPORT_AUDIO && b.cbFileTypeAudio.isChecked)    mask = mask or FileTypeFlags.AUDIO
        if (BuildConfig.SUPPORT_VIDEO && b.cbFileTypeVideo.isChecked)    mask = mask or FileTypeFlags.VIDEO
        if (BuildConfig.SUPPORT_DOCUMENTS && b.cbFileTypeDocs.isChecked) mask = mask or FileTypeFlags.DOCUMENTS
        return mask
    }

    /** Restores checkbox state from a saved bitmask. */
    private fun applyFileTypeMask(mask: Int) {
        val normalizedMask = normalizeMaskForFlavor(mask)
        val allFiles = FileTypeFlags.isAllFiles(normalizedMask)
        b.cbFileTypeAllFiles.isChecked = allFiles
        if (allFiles) {
            // interlock will fire via setOnCheckedChangeListener
        } else {
            b.cbFileTypeImages.isChecked = FileTypeFlags.hasImages(normalizedMask)
            b.cbFileTypeAudio.isChecked  = BuildConfig.SUPPORT_AUDIO && FileTypeFlags.hasAudio(normalizedMask)
            b.cbFileTypeVideo.isChecked  = BuildConfig.SUPPORT_VIDEO && FileTypeFlags.hasVideo(normalizedMask)
            b.cbFileTypeDocs.isChecked   = BuildConfig.SUPPORT_DOCUMENTS && FileTypeFlags.hasDocuments(normalizedMask)
        }
    }

    private fun normalizeMaskForFlavor(mask: Int): Int {
        if (FileTypeFlags.isAllFiles(mask)) return FileTypeFlags.ALL_FILES
        var result = mask and FileTypeFlags.IMAGES
        if (BuildConfig.SUPPORT_AUDIO) result = result or (mask and FileTypeFlags.AUDIO)
        if (BuildConfig.SUPPORT_VIDEO) result = result or (mask and FileTypeFlags.VIDEO)
        if (BuildConfig.SUPPORT_DOCUMENTS) result = result or (mask and FileTypeFlags.DOCUMENTS)
        return result
    }

    private fun applyReadOnlySourceConstraint(source: MediaResource?) {
        val isReadOnly = source?.isReadOnly == true
        b.tvReadOnlySourceHint.visibility = if (isReadOnly) View.VISIBLE else View.GONE
        if (isReadOnly) {
            val copyLabel = context.getString(R.string.scheduled_ops_op_copy)
            b.actvOperation.setText(copyLabel, false)
            b.actvOperation.isEnabled = false
            b.tvTargetLabel.visibility = View.VISIBLE
            b.tilTarget.visibility = View.VISIBLE
        } else {
            b.actvOperation.isEnabled = true
        }
    }

    private fun setupNextRunPreview() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateNextRunPreview()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        b.etStartHour.addTextChangedListener(watcher)
        b.etStartMinute.addTextChangedListener(watcher)
        b.etIntervalHours.addTextChangedListener(watcher)
        b.etIntervalMinutes.addTextChangedListener(watcher)
    }

    private fun updateNextRunPreview() {
        val startHour = b.etStartHour.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: return
        val startMinute = b.etStartMinute.text.toString().toIntOrNull()?.coerceIn(0, 59) ?: return
        val intervalHours = b.etIntervalHours.text.toString().toIntOrNull() ?: 0
        val intervalMinutes = b.etIntervalMinutes.text.toString().toIntOrNull() ?: 0

        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                val intervalMs = (intervalHours * 3600L + intervalMinutes * 60L) * 1000L
                val effectiveInterval = if (intervalMs < 15 * 60_000L) 15 * 60_000L else intervalMs
                add(Calendar.MILLISECOND, effectiveInterval.toInt())
                if (before(now)) add(Calendar.DATE, 1)
            }
        }
        val label = context.getString(R.string.scheduled_ops_next_run)
        b.tvNextRun.text = "$label ${nextRunFormat.format(next.time)}"
        b.tvNextRun.visibility = View.VISIBLE
    }

    /** Pre-fill source dropdown when opening dialog from Browse with a pre-selected resource. */
    private fun applyPrefilledSource() {
        if (existing != null || prefilledSourceId == null) return
        val srcResource = resources.find { it.id == prefilledSourceId } ?: return
        b.actvSource.setText(srcResource.name, false)
        applyReadOnlySourceConstraint(srcResource)
    }

    private fun populateExisting() {
        val op = existing ?: return
        b.tvDialogTitle.text = context.getString(R.string.edit)

        // Source
        val srcResource = resources.find { it.id == op.sourceResourceId }
        b.actvSource.setText(srcResource?.name ?: "", false)
        applyReadOnlySourceConstraint(srcResource)

        // Operation type
        val opIdx = when (op.operationType) {
            ScheduledOpType.COPY -> 0
            ScheduledOpType.MOVE -> 1
            ScheduledOpType.DELETE -> 2
        }
        val opLabels = listOf(
            context.getString(R.string.scheduled_ops_op_copy),
            context.getString(R.string.scheduled_ops_op_move),
            context.getString(R.string.scheduled_ops_op_delete)
        )
        b.actvOperation.setText(opLabels[opIdx], false)
        if (op.operationType == ScheduledOpType.DELETE) {
            b.tvTargetLabel.visibility = View.GONE
            b.tilTarget.visibility = View.GONE
            b.containerOverwrite.visibility = View.GONE
        }

        // Target
        val destResource = destinations.find { it.id == op.targetResourceId }
        b.actvTarget.setText(destResource?.name ?: "", false)

        // Filters
        applyFileTypeMask(op.fileTypeMask)

        val timeIdx = op.timeFilter.ordinal
        val timeLabels = listOf(
            context.getString(R.string.scheduled_ops_time_all),
            context.getString(R.string.scheduled_ops_time_since_last),
            context.getString(R.string.scheduled_ops_time_last_hour),
            context.getString(R.string.scheduled_ops_time_last_day)
        )
        b.actvTimeFilter.setText(timeLabels.getOrNull(timeIdx) ?: timeLabels[0], false)

        // Time
        b.etStartHour.setText(op.startTimeHour.toString())
        b.etStartMinute.setText(op.startTimeMinute.toString().padStart(2, '0'))
        b.etIntervalHours.setText(op.intervalHours.toString())
        b.etIntervalMinutes.setText(op.intervalMinutes.toString())

        b.switchOverwrite.isChecked = op.overwrite
        b.switchSilentMode.isChecked = op.silentMode
    }

    private fun trySave() {
        val sourceIdx = resources.indexOfFirst { it.name == b.actvSource.text.toString() }
        if (sourceIdx < 0) {
            Toast.makeText(context, R.string.scheduled_ops_source, Toast.LENGTH_SHORT).show()
            return
        }
        val sourceResource = resources[sourceIdx]

        val opText = b.actvOperation.text.toString()
        val opType = when (opText) {
            context.getString(R.string.scheduled_ops_op_move) -> ScheduledOpType.MOVE
            context.getString(R.string.scheduled_ops_op_delete) -> ScheduledOpType.DELETE
            else -> ScheduledOpType.COPY
        }

        var targetId: Long? = null
        if (opType != ScheduledOpType.DELETE) {
            val destIdx = destinations.indexOfFirst { it.name == b.actvTarget.text.toString() }
            if (destIdx < 0) {
                Toast.makeText(context, R.string.scheduled_ops_target, Toast.LENGTH_SHORT).show()
                return
            }
            targetId = destinations[destIdx].id
        }

        val fileTypeMask = buildFileTypeMask()
        if (fileTypeMask == 0) {
            Toast.makeText(context, R.string.scheduled_ops_filter_select_at_least_one, Toast.LENGTH_SHORT).show()
            return
        }

        val timeText = b.actvTimeFilter.text.toString()
        val timeFilter = when (timeText) {
            context.getString(R.string.scheduled_ops_time_since_last) -> TimeFilter.SINCE_LAST
            context.getString(R.string.scheduled_ops_time_last_hour) -> TimeFilter.LAST_HOUR
            context.getString(R.string.scheduled_ops_time_last_day) -> TimeFilter.LAST_DAY
            else -> TimeFilter.ALL
        }

        val startHour = b.etStartHour.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: 0
        val startMinute = b.etStartMinute.text.toString().toIntOrNull()?.coerceIn(0, 59) ?: 0
        var intervalHours = b.etIntervalHours.text.toString().toIntOrNull() ?: 0
        var intervalMinutes = b.etIntervalMinutes.text.toString().toIntOrNull() ?: 0

        // Enforce minimum interval of 15 minutes
        if (intervalHours == 0 && intervalMinutes == 0) {
            intervalHours = 1
            Toast.makeText(context, R.string.scheduled_ops_interval_corrected_1h, Toast.LENGTH_SHORT).show()
        } else if (intervalHours == 0 && intervalMinutes < 15) {
            intervalMinutes = 15
            Toast.makeText(context, R.string.scheduled_ops_interval_corrected_15, Toast.LENGTH_SHORT).show()
        }

        val op = ScheduledOperation(
            id = existing?.id ?: 0L,
            isEnabled = existing?.isEnabled ?: true,
            sourceResourceId = sourceResource.id,
            operationType = opType,
            targetResourceId = targetId,
            fileTypeMask = fileTypeMask,
            timeFilter = timeFilter,
            startTimeHour = startHour,
            startTimeMinute = startMinute,
            intervalHours = intervalHours,
            intervalMinutes = intervalMinutes,
            overwrite = b.switchOverwrite.isChecked,
            silentMode = b.switchSilentMode.isChecked,
            lastRunAt = existing?.lastRunAt,
            lastRunStatus = existing?.lastRunStatus,
            workerId = existing?.workerId
        )
        onSave(op)
        dismiss()
    }
}
