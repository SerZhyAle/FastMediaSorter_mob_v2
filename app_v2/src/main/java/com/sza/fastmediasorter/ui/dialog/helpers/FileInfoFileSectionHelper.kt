package com.sza.fastmediasorter.ui.dialog.helpers

import android.content.Context
import android.text.format.DateFormat
import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.clipboard.copyTextToClipboard
import com.sza.fastmediasorter.core.util.MediaFilePathDescriptor
import com.sza.fastmediasorter.core.util.MimeTypeResolver
import com.sza.fastmediasorter.core.util.formatFileSize
import com.sza.fastmediasorter.databinding.DialogFileInfoBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import timber.log.Timber
import java.util.Date

class FileInfoFileSectionHelper(
    private val context: Context,
    private val binding: DialogFileInfoBinding
) {

    fun render(file: MediaFile, lastModifiedMs: Long?, isReadOnly: Boolean, isHidden: Boolean) {
        try {
            val decomposition = MediaFilePathDescriptor.decompose(file.path, file.cloudDisplayPath)

            binding.tvFileName.text = context.getString(R.string.file_name_label, file.name)
            binding.tvFileSize.text = context.getString(R.string.file_size_label, formatFileSize(file.size))
            binding.tvFileDate.text = context.getString(R.string.file_date_label, formatDate(file.createdDate))
            binding.tvFileType.text = context.getString(R.string.file_type_label, file.type.name)

            if (decomposition.host != null) {
                val hostPort = decomposition.host + if (decomposition.port != null) ":${decomposition.port}" else ""
                binding.tvFileHost.text = context.getString(R.string.file_info_host_label, hostPort)
                binding.tvFileHost.isVisible = true
            } else {
                binding.tvFileHost.isVisible = false
            }

            if (decomposition.share != null) {
                binding.tvFileShare.text = context.getString(R.string.file_info_share_label, decomposition.share)
                binding.tvFileShare.isVisible = true
            } else {
                binding.tvFileShare.isVisible = false
            }

            if (decomposition.directory != null) {
                binding.tvFileDirectory.text = context.getString(R.string.file_info_directory_label, decomposition.directory)
                binding.tvFileDirectory.isVisible = true
            } else {
                binding.tvFileDirectory.isVisible = false
            }

            binding.tvFileNameLine.text = context.getString(R.string.file_info_filename_label, decomposition.filename)
            binding.tvFileNameLine.isVisible = true

            val mime = MimeTypeResolver.resolve(decomposition.extension, headBytesProvider = null)
            binding.tvFileExtensionMime.text = context.getString(
                R.string.file_info_extension_mime_label,
                decomposition.extension?.uppercase() ?: "-",
                mime
            )
            binding.tvFileExtensionMime.isVisible = true

            val effectiveLastModified = lastModifiedMs ?: if (file.lastModified > 0L) file.lastModified else null
            if (effectiveLastModified != null && effectiveLastModified != file.createdDate) {
                binding.tvFileLastModified.text = context.getString(
                    R.string.file_info_last_modified_label,
                    formatDate(effectiveLastModified)
                )
                binding.tvFileLastModified.isVisible = true
            } else {
                binding.tvFileLastModified.isVisible = false
            }

            binding.tvFileReadOnly.text = context.getString(R.string.file_info_read_only_label)
            binding.tvFileReadOnly.isVisible = isReadOnly

            binding.tvFileHidden.text = context.getString(R.string.file_info_hidden_label)
            binding.tvFileHidden.isVisible = isHidden

            binding.btnCopyPath.isVisible = true
            binding.btnCopyPath.setOnClickListener {
                context.copyTextToClipboard("path", decomposition.displayPath)
            }

            binding.tvFilePath.visibility = View.GONE
        } catch (e: Exception) {
            Timber.w(e, "FileInfoFileSectionHelper: render failed for ${file.path}")
            val readablePath = if (file.path.startsWith("cloud://") || file.path.startsWith("cloud:/")) {
                file.cloudDisplayPath?.takeIf { it.isNotBlank() } ?: file.path
            } else {
                file.path
            }
            binding.tvFilePath.text = context.getString(R.string.file_path_label, readablePath)
            binding.tvFilePath.visibility = View.VISIBLE
        }
    }

    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        val dateFormat = DateFormat.getDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        return "${dateFormat.format(date)} ${timeFormat.format(date)}"
    }
}
