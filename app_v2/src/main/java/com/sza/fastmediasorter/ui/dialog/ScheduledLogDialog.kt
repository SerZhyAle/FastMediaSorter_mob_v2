package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogScheduledLogBinding

class ScheduledLogDialog(
    context: Context,
    private val logContent: String,
    private val onClearLog: () -> Unit
) : Dialog(context) {

    private lateinit var b: DialogScheduledLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = DialogScheduledLogBinding.inflate(layoutInflater)
        setContentView(b.root)
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.93).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        b.tvLogContent.text = if (logContent.isBlank()) {
            context.getString(R.string.scheduled_ops_log_empty)
        } else {
            logContent
        }

        b.btnCopyLog.setOnClickListener {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", logContent))
            Toast.makeText(context, android.R.string.copy, Toast.LENGTH_SHORT).show()
        }
        b.btnCloseLog.setOnClickListener { dismiss() }
        b.btnClearLog.setOnClickListener {
            onClearLog()
            b.tvLogContent.text = context.getString(R.string.scheduled_ops_log_empty)
        }
    }
}
