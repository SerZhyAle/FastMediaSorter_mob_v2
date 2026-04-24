package com.sza.fastmediasorter.vr.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.vr.helpers.VrRecentDestinationsPrefs
import timber.log.Timber

/**
 * Immersive file-operations panel. Shown on top of the VR render surface when
 * the user presses `Y` on the left controller, `F7`, or any direct shortcut
 * (F5 copy, F6 move, F8 delete, F2 rename, F3 info).
 *
 * The manager is a thin View builder + dialog launcher — all actual file-system
 * work is delegated back to the activity via [callbacks] so this class stays
 * testable and free of Room / ViewModel dependencies.
 *
 * Playback is paused automatically when the panel opens and resumed when it
 * closes, provided the pause was applied by this manager (guarded via
 * [pausedByUs]).
 */
class VrFileOpsOverlayManager(
    private val activity: Activity,
    private val recentDestinations: VrRecentDestinationsPrefs,
    private val callbacks: Callbacks,
) {

    /** Hooks the activity must provide for the manager to dispatch operations. */
    interface Callbacks {
        /** Current file the user is acting on (null if list is empty). */
        fun currentFile(): MediaFile?
        /** True when playback is active (used to decide whether to auto-pause). */
        fun isPlaying(): Boolean
        fun pausePlayback()
        fun resumePlayback()
        fun copyFileTo(file: MediaFile, destinationPath: String)
        fun moveFileTo(file: MediaFile, destinationPath: String)
        fun deleteFile(file: MediaFile)
        fun renameFile(file: MediaFile, newName: String)
        /** Asked to surface the full folder browser (leaves immersive). */
        fun openFolderBrowserForDestination(file: MediaFile, move: Boolean)
        /** Produces a localized, multi-line info block for the "info" dialog. */
        fun buildFileInfoText(file: MediaFile): CharSequence
    }

    private var root: FrameLayout? = null
    private var pausedByUs: Boolean = false

    fun isVisible(): Boolean = root?.parent != null

    fun show() {
        val file = callbacks.currentFile() ?: run {
            Timber.w("VrFileOps: no current file — not showing panel")
            return
        }
        if (isVisible()) return

        autoPauseIfNeeded()

        val decor = activity.window?.decorView as? ViewGroup ?: return
        val overlay = buildPanel(file)
        root = overlay
        decor.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    /** Shortcut: open the panel (if not visible) and directly launch the copy flow. */
    fun showAndCopy() { show(); callbacks.currentFile()?.let { promptCopyOrMove(it, move = false) } }
    fun showAndMove() { show(); callbacks.currentFile()?.let { promptCopyOrMove(it, move = true) } }
    fun showAndDelete() { show(); callbacks.currentFile()?.let { showDeleteConfirmation(it) } }
    fun showAndRename() { show(); callbacks.currentFile()?.let { showRenameDialog(it) } }
    fun showAndInfo()   { show(); callbacks.currentFile()?.let { showFileInfo(it) } }

    fun hide() {
        val r = root ?: return
        (r.parent as? ViewGroup)?.removeView(r)
        root = null
        if (pausedByUs) {
            pausedByUs = false
            callbacks.resumePlayback()
        }
    }

    fun release() {
        hide()
    }

    // ─── Panel ─────────────────────────────────────────────────────────────

    private fun buildPanel(file: MediaFile): FrameLayout {
        val overlay = FrameLayout(activity)
        overlay.setBackgroundColor(Color.argb(160, 0, 0, 0))
        overlay.isClickable = true
        overlay.setOnClickListener { hide() }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            background = GradientDrawable().apply {
                cornerRadius = 32f
                setColor(Color.argb(240, 28, 28, 32))
                setStroke(2, Color.WHITE)
            }
            isClickable = true
            setOnClickListener { /* absorb taps — don't let them bubble up to dismiss */ }
        }

        val title = TextView(activity).apply {
            text = activity.getString(R.string.vr_fops_title)
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(0, 0, 0, 24)
        }
        card.addView(title)

        card.addView(buildAction(activity.getString(R.string.vr_fops_copy_to)) {
            promptCopyOrMove(file, move = false)
        })
        card.addView(buildAction(activity.getString(R.string.vr_fops_move_to)) {
            promptCopyOrMove(file, move = true)
        })
        card.addView(buildAction(activity.getString(R.string.vr_fops_delete)) {
            showDeleteConfirmation(file)
        })
        card.addView(buildAction(activity.getString(R.string.vr_fops_rename)) {
            showRenameDialog(file)
        })
        card.addView(buildAction(activity.getString(R.string.vr_fops_info)) {
            showFileInfo(file)
        })

        val close = Button(activity).apply {
            text = activity.getString(R.string.vr_fops_close)
            setOnClickListener { hide() }
        }
        card.addView(close)

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        overlay.addView(card, lp)
        return overlay
    }

    private fun buildAction(label: String, onClick: () -> Unit): Button = Button(activity).apply {
        text = label
        setOnClickListener { onClick() }
    }

    // ─── Copy / Move ───────────────────────────────────────────────────────

    private fun promptCopyOrMove(file: MediaFile, move: Boolean) {
        val recent = recentDestinations.getAll()
        val titleRes = if (move) R.string.vr_fops_choose_move_target else R.string.vr_fops_choose_copy_target
        val content = ScrollView(activity)
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        if (recent.isEmpty()) {
            val empty = TextView(activity).apply {
                text = activity.getString(R.string.vr_fops_no_recent)
                setTextColor(Color.LTGRAY)
                setPadding(0, 0, 0, 16)
            }
            list.addView(empty)
        } else {
            recent.forEach { path ->
                val btn = Button(activity).apply {
                    text = path
                    setOnClickListener {
                        if (move) callbacks.moveFileTo(file, path) else callbacks.copyFileTo(file, path)
                        recentDestinations.add(path)
                        hide()
                    }
                }
                list.addView(btn)
            }
        }
        val chooseOther = Button(activity).apply {
            text = activity.getString(R.string.vr_fops_choose_folder)
            setOnClickListener {
                callbacks.openFolderBrowserForDestination(file, move)
                hide()
            }
        }
        list.addView(chooseOther)
        content.addView(list)

        MaterialAlertDialogBuilder(activity)
            .setTitle(titleRes)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ─── Delete ────────────────────────────────────────────────────────────

    fun showDeleteConfirmation(file: MediaFile) {
        val size = file.size
        val sizeStr = if (size > 0) formatSize(size) else ""
        val msg = activity.getString(R.string.vr_delete_confirm_msg, file.name, sizeStr)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.vr_delete_confirm_title)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                callbacks.deleteFile(file)
                hide()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ─── Rename ────────────────────────────────────────────────────────────

    fun showRenameDialog(file: MediaFile) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(file.name)
            setSelection(file.name.substringBeforeLast('.', file.name).length)
            hint = activity.getString(R.string.vr_rename_hint)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.vr_fops_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isNotBlank() && newName != file.name) {
                    callbacks.renameFile(file, newName)
                }
                hide()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ─── Info ──────────────────────────────────────────────────────────────

    fun showFileInfo(file: MediaFile) {
        val body = callbacks.buildFileInfoText(file)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.vr_info_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ─── Auto-pause ────────────────────────────────────────────────────────

    private fun autoPauseIfNeeded() {
        if (callbacks.isPlaying()) {
            callbacks.pausePlayback()
            pausedByUs = true
        }
    }

    // ─── Utilities ─────────────────────────────────────────────────────────

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    @Suppress("unused")
    private fun absorb(v: View) { /* kept for lint completeness */ }
}
