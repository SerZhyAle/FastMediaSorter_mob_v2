package com.sza.fastmediasorter.ui.browse.managers

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogSortBinding
import com.sza.fastmediasorter.domain.model.SortMode

internal class BrowseSortDialogManager(
    private val activity: AppCompatActivity,
    private val callbacks: BrowseDialogHelper.DialogCallbacks
) {
    companion object {
        // Keep dialog order explicit so enum declaration order stays free for persistence concerns.
        internal val DIALOG_SORT_ORDER = listOf(
            SortMode.RANDOM,
            SortMode.NAME_ASC,
            SortMode.NAME_DESC,
            SortMode.DATE_ASC,
            SortMode.DATE_DESC,
            SortMode.DATE_TAKEN_ASC,
            SortMode.DATE_TAKEN_DESC,
            SortMode.SIZE_ASC,
            SortMode.SIZE_DESC,
            SortMode.MANUAL,
            SortMode.DURATION_ASC,
            SortMode.DURATION_DESC,
            SortMode.TYPE_ASC,
            SortMode.TYPE_DESC,
            SortMode.ARTIST_ASC,
            SortMode.ARTIST_DESC,
            SortMode.TITLE_ASC,
            SortMode.TITLE_DESC
        )
    }

    fun showSortDialog(currentSortMode: SortMode) {
        val dialogBinding = DialogSortBinding.inflate(LayoutInflater.from(activity))
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.sort_by_title)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialogBinding.rvSortOptions.layoutManager = GridLayoutManager(activity, 2)
        dialogBinding.rvSortOptions.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_sort_option, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val mode = DIALOG_SORT_ORDER[position]
                val button = holder.itemView as MaterialButton
                button.text = getSortModeName(mode)
                button.setIconResource(BrowseSortMenuManager.getSortModeIconRes(mode) ?: 0)

                if (mode == currentSortMode) {
                    val colorPrimaryContainer = MaterialColors.getColor(
                        button,
                        com.google.android.material.R.attr.colorPrimaryContainer
                    )
                    val colorOnPrimaryContainer = MaterialColors.getColor(
                        button,
                        com.google.android.material.R.attr.colorOnPrimaryContainer
                    )
                    button.setBackgroundColor(colorPrimaryContainer)
                    button.setTextColor(colorOnPrimaryContainer)
                    button.iconTint = ColorStateList.valueOf(colorOnPrimaryContainer)
                } else {
                    val colorOnSurface = MaterialColors.getColor(
                        button,
                        com.google.android.material.R.attr.colorOnSurface
                    )
                    button.setBackgroundColor(Color.TRANSPARENT)
                    button.setTextColor(colorOnSurface)
                    button.iconTint = ColorStateList.valueOf(colorOnSurface)
                }

                button.setOnClickListener {
                    if (mode == SortMode.RANDOM) {
                        callbacks.onRandomReshuffle()
                    } else {
                        callbacks.onSortModeSelected(mode)
                    }
                    dialog.dismiss()
                }
            }

            override fun getItemCount(): Int = DIALOG_SORT_ORDER.size
        }

        dialog.show()
        com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper.applyInitialFocus(dialog)
    }

    private fun getSortModeName(mode: SortMode): String {
        return when (mode) {
            SortMode.MANUAL -> activity.getString(R.string.sort_mode_manual)
            SortMode.NAME_ASC -> activity.getString(R.string.sort_mode_name_asc)
            SortMode.NAME_DESC -> activity.getString(R.string.sort_mode_name_desc)
            SortMode.DATE_ASC -> activity.getString(R.string.sort_mode_date_asc)
            SortMode.DATE_DESC -> activity.getString(R.string.sort_mode_date_desc)
            SortMode.SIZE_ASC -> activity.getString(R.string.sort_mode_size_asc)
            SortMode.SIZE_DESC -> activity.getString(R.string.sort_mode_size_desc)
            SortMode.TYPE_ASC -> activity.getString(R.string.sort_mode_type_asc)
            SortMode.TYPE_DESC -> activity.getString(R.string.sort_mode_type_desc)
            SortMode.ARTIST_ASC -> activity.getString(R.string.sort_mode_artist_asc)
            SortMode.ARTIST_DESC -> activity.getString(R.string.sort_mode_artist_desc)
            SortMode.TITLE_ASC -> activity.getString(R.string.sort_mode_title_asc)
            SortMode.TITLE_DESC -> activity.getString(R.string.sort_mode_title_desc)
            SortMode.DURATION_ASC -> activity.getString(R.string.sort_mode_duration_asc)
            SortMode.DURATION_DESC -> activity.getString(R.string.sort_mode_duration_desc)
            SortMode.DATE_TAKEN_ASC -> activity.getString(R.string.sort_mode_date_taken_asc)
            SortMode.DATE_TAKEN_DESC -> activity.getString(R.string.sort_mode_date_taken_desc)
            SortMode.RANDOM -> activity.getString(R.string.sort_mode_random)
        }
    }
}
