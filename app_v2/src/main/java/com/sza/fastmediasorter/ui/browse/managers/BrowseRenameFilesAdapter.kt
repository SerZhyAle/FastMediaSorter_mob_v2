package com.sza.fastmediasorter.ui.browse.managers

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemRenameFileBinding

/**
 * RecyclerView adapter for the bulk-rename dialog in BrowseActivity.
 * Each item renders one editable filename field; changes are written back
 * into [fileNames] in place so [getFileNames] reflects the user's edits.
 */
internal class BrowseRenameFilesAdapter(
    private val fileNames: MutableList<String>
) : RecyclerView.Adapter<BrowseRenameFilesAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemRenameFileBinding) : RecyclerView.ViewHolder(binding.root) {
        private var textWatcher: TextWatcher? = null

        fun bind(fileName: String, position: Int) {
            // Remove old listener to prevent memory leaks on RecyclerView recycle.
            textWatcher?.let { binding.etFileName.removeTextChangedListener(it) }

            // Only update text if it differs to prevent cursor-jump issues.
            if (binding.etFileName.text.toString() != fileName) {
                binding.etFileName.setText(fileName)
            }

            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    fileNames[position] = s?.toString() ?: ""
                }
            }
            binding.etFileName.addTextChangedListener(textWatcher)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRenameFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(fileNames[position], position)
    }

    override fun getItemCount() = fileNames.size

    fun getFileNames() = fileNames.toList()
}
