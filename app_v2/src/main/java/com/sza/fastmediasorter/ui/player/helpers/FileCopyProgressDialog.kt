package com.sza.fastmediasorter.ui.player.helpers

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
import com.sza.fastmediasorter.databinding.DialogFileCopyProgressBinding
import kotlin.math.min

class FileCopyProgressDialog(
    context: Context,
    private val fileName: String,
    private val onCancelRequested: () -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogFileCopyProgressBinding

    init {
        setCancelable(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogFileCopyProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.fileNameText.text = fileName
        binding.cancelButton.setOnClickListener {
            onCancelRequested.invoke()
        }
    }

    fun updateProgress(bytesCopied: Long, totalBytes: Long, speedBytesPerSec: Long) {
        if (!this::binding.isInitialized) return

        if (totalBytes > 0L) {
            val progress = min(100, ((bytesCopied * 100L) / totalBytes).toInt())
            binding.progressBar.isIndeterminate = false
            binding.progressBar.progress = progress
            binding.progressText.text = "$progress%"
        } else {
            binding.progressBar.isIndeterminate = true
            binding.progressText.text = "—"
        }

        binding.speedText.text = formatSpeed(speedBytesPerSec)
    }

    fun showIndeterminate() {
        if (!this::binding.isInitialized) return
        binding.progressBar.isIndeterminate = true
        binding.progressText.text = "—"
        binding.speedText.text = ""
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0L) return ""
        val speedText = Formatter.formatShortFileSize(context, bytesPerSecond)
        return "$speedText/s"
    }
}
