package com.sza.fastmediasorter.ui.player.helpers

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.BottomSheetPlayerFilesBinding
import com.sza.fastmediasorter.databinding.DialogPlayerFileNumberBinding
import com.sza.fastmediasorter.databinding.ItemPlayerFileBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.util.showBoundTo

/** Owns file-selection windows without retaining a second live collection or changing its order. */
class PlayerFileSelectionManager(
    private val host: FragmentActivity,
    private val files: () -> List<MediaFile>,
    private val currentPath: () -> String?,
    private val selectPath: (String) -> Unit,
) {
    private var dialog: android.app.Dialog? = null

    val isShowing: Boolean get() = dialog?.isShowing == true

    fun showFiles(grid: Boolean): Boolean {
        if (!isShowing) availableFiles()?.let { showFiles(it, grid) }
        return true
    }

    private fun showFiles(snapshot: List<MediaFile>, grid: Boolean) {
        val binding = BottomSheetPlayerFilesBinding.inflate(LayoutInflater.from(host))
        val sheet = BottomSheetDialog(host)
        val title = if (grid) R.string.player_file_grid else R.string.player_file_list
        binding.title.setText(title)
        val columns = if (grid) {
            (host.resources.configuration.screenWidthDp / COLUMN_WIDTH_DP).coerceAtLeast(1)
        } else {
            1
        }
        binding.files.layoutManager = GridLayoutManager(host, columns)
        val adapter = FileAdapter(snapshot, currentPath()) { path ->
            sheet.dismiss()
            selectPath(path)
        }
        binding.files.adapter = adapter
        binding.close.setOnClickListener { sheet.dismiss() }
        val windowHeight = host.window.decorView.height.takeIf { it > 0 }
            ?: host.resources.displayMetrics.heightPixels
        binding.root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (windowHeight * SHEET_HEIGHT_FRACTION).toInt(),
        )
        sheet.setContentView(binding.root)
        applySafeInsets(binding.root)
        bindDismissal(sheet) {
            binding.files.adapter = null
            adapter.clearRequests()
        }
        sheet.showBoundTo(host) ?: return
        dialog = sheet
        (binding.root.parent as? View)?.let {
            BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
        }
        val selected = snapshot.indexOfFirst { it.path == currentPath() }.coerceAtLeast(0)
        binding.files.scrollToPosition(selected)
        binding.files.doOnNextLayout {
            binding.files.findViewHolderForAdapterPosition(selected)?.itemView?.requestFocus()
        }
    }

    fun showNumber(): Boolean {
        if (!isShowing) availableFiles()?.let(::showNumber)
        return true
    }

    private fun showNumber(snapshot: List<MediaFile>) {
        val binding = DialogPlayerFileNumberBinding.inflate(LayoutInflater.from(host))
        val numberDialog = MaterialAlertDialogBuilder(host)
            .setTitle(R.string.player_file_jump)
            .setView(binding.root)
            .create()
        binding.numberLayout.hint = host.getString(R.string.player_file_number_hint, snapshot.size)
        val current = snapshot.indexOfFirst { it.path == currentPath() }.coerceAtLeast(0) + 1
        binding.number.setText(current.toString())
        binding.number.selectAll()
        binding.cancel.setOnClickListener { numberDialog.dismiss() }
        binding.confirm.setOnClickListener {
            val index = binding.number.text?.toString()?.trim()?.toIntOrNull()?.minus(1)
            if (index == null || index !in snapshot.indices) {
                binding.numberLayout.error = host.getString(R.string.player_file_number_error, snapshot.size)
                binding.number.requestFocus()
            } else {
                numberDialog.dismiss()
                selectPath(snapshot[index].path)
            }
        }
        binding.number.setOnEditorActionListener { _, action, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            if (action == EditorInfo.IME_ACTION_DONE || enter) {
                binding.confirm.performClick()
                true
            } else {
                false
            }
        }
        applySafeInsets(binding.root)
        bindDismissal(numberDialog) {
            binding.confirm.setOnClickListener(null)
            binding.cancel.setOnClickListener(null)
            binding.number.setOnEditorActionListener(null)
        }
        numberDialog.showBoundTo(host) ?: return
        dialog = numberDialog
        binding.number.requestFocus()
    }

    private fun availableFiles(): List<MediaFile>? {
        val snapshot = files().toList()
        if (snapshot.size > 1) return snapshot
        Toast.makeText(host, R.string.player_file_single, Toast.LENGTH_SHORT).show()
        return null
    }

    private fun bindDismissal(window: android.app.Dialog, cleanup: () -> Unit = {}) {
        val previousFocus = host.currentFocus
        window.setOnKeyListener { _, key, event ->
            when (key) {
                KeyEvent.KEYCODE_ESCAPE -> {
                    if (event.action == KeyEvent.ACTION_UP) window.dismiss()
                    true
                }
                KeyEvent.KEYCODE_F1 -> {
                    if (event.action == KeyEvent.ACTION_UP) PlayerHelpEntryManager.open(host)
                    true
                }
                else -> false
            }
        }
        window.setOnDismissListener {
            cleanup()
            // The lifecycle binding retains the dialog until host destruction, so release its snapshot closure now.
            window.setOnDismissListener(null)
            if (dialog === window) dialog = null
            if (!host.isDestroyed) previousFocus?.requestFocus()
        }
    }

    private fun applySafeInsets(view: View) {
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            target.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom)
            insets
        }
    }

    private class FileAdapter(
        private val files: List<MediaFile>,
        private val selectedPath: String?,
        private val select: (String) -> Unit,
    ) : RecyclerView.Adapter<FileHolder>() {
        private val bound = mutableSetOf<FileHolder>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileHolder =
            FileHolder(ItemPlayerFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = files.size

        override fun onBindViewHolder(holder: FileHolder, position: Int) {
            bound.add(holder)
            val file = files[position]
            val label = holder.itemView.context.getString(R.string.player_file_row, position + 1, file.name)
            holder.binding.fileName.text = label
            holder.itemView.contentDescription = label
            holder.itemView.isSelected = file.path == selectedPath
            holder.binding.fileName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (file.path == selectedPath) R.drawable.ic_check else 0,
                0,
                0,
                0,
            )
            holder.itemView.setOnClickListener { select(file.path) }
            Glide.with(holder.binding.thumbnail)
                .load(file.thumbnailUrl ?: file.contentUri ?: file.path)
                .override(THUMBNAIL_SIZE_PX)
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .into(holder.binding.thumbnail)
        }

        override fun onViewRecycled(holder: FileHolder) {
            Glide.with(holder.binding.thumbnail).clear(holder.binding.thumbnail)
            bound.remove(holder)
            super.onViewRecycled(holder)
        }

        fun clearRequests() {
            bound.forEach { Glide.with(it.binding.thumbnail).clear(it.binding.thumbnail) }
            bound.clear()
        }
    }

    private class FileHolder(val binding: ItemPlayerFileBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val COLUMN_WIDTH_DP = 144
        const val THUMBNAIL_SIZE_PX = 192
        const val SHEET_HEIGHT_FRACTION = 0.85f
    }
}
