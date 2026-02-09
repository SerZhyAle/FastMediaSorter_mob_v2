package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import com.sza.fastmediasorter.R

/**
 * Material Design replacement for deprecated android.app.ProgressDialog
 * 
 * Supports two styles:
 * - Spinner (indeterminate circular progress)
 * - Horizontal (progress bar with percentage)
 * 
 * Usage:
 * ```
 * val dialog = MaterialProgressDialog(context)
 * dialog.setTitle("Processing...")
 * dialog.setMessage("Please wait")
 * dialog.setProgressStyle(MaterialProgressDialog.STYLE_HORIZONTAL)
 * dialog.max = 100
 * dialog.show()
 * 
 * // Update progress
 * dialog.progress = 50
 * 
 * // Dismiss
 * dialog.dismiss()
 * ```
 */
class MaterialProgressDialog(context: Context) : Dialog(context) {

    private lateinit var tvTitle: TextView
    private lateinit var tvMessage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView

    private var progressStyle = STYLE_SPINNER
    private var titleText: String? = null
    private var messageText: String? = null
    
    var max: Int = 100
        set(value) {
            field = value
            if (::progressBar.isInitialized) {
                progressBar.max = value
            }
        }
    
    var progress: Int = 0
        set(value) {
            field = value
            if (::progressBar.isInitialized && ::tvProgress.isInitialized) {
                progressBar.progress = value
                if (progressStyle == STYLE_HORIZONTAL && max > 0) {
                    val percentage = (value * 100) / max
                    tvProgress.text = "$percentage%"
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layoutId = when (progressStyle) {
            STYLE_HORIZONTAL -> R.layout.dialog_material_progress_horizontal
            else -> R.layout.dialog_material_progress_spinner
        }
        
        val view = LayoutInflater.from(context).inflate(layoutId, null, false)
        
        tvTitle = view.findViewById(R.id.tvProgressTitle)
        tvMessage = view.findViewById(R.id.tvProgressMessage)
        progressBar = view.findViewById(R.id.progressBar)
        
        if (progressStyle == STYLE_HORIZONTAL) {
            tvProgress = view.findViewById(R.id.tvProgress)
            progressBar.max = max
            progressBar.progress = progress
            if (max > 0) {
                val percentage = (progress * 100) / max
                tvProgress.text = "$percentage%"
            }
        }
        
        titleText?.let { tvTitle.text = it }
        messageText?.let { tvMessage.text = it }
        
        setContentView(view)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }
    
    override fun setTitle(title: CharSequence?) {
        titleText = title?.toString()
        if (::tvTitle.isInitialized) {
            if (titleText.isNullOrEmpty()) {
                tvTitle.visibility = android.view.View.GONE
            } else {
                tvTitle.visibility = android.view.View.VISIBLE
                tvTitle.text = titleText
            }
        }
    }
    
    override fun setTitle(titleId: Int) {
        setTitle(context.getString(titleId))
    }
    
    fun setMessage(message: CharSequence?) {
        messageText = message?.toString()
        if (::tvMessage.isInitialized) {
            tvMessage.text = messageText
        }
    }
    
    fun setMessage(messageId: Int) {
        setMessage(context.getString(messageId))
    }
    
    fun setProgressStyle(style: Int) {
        progressStyle = style
    }

    companion object {
        const val STYLE_SPINNER = 0
        const val STYLE_HORIZONTAL = 1
        
        /**
         * Quick static show method for simple spinner dialogs
         */
        fun show(
            context: Context,
            title: CharSequence?,
            message: CharSequence?,
            indeterminate: Boolean = true,
            cancelable: Boolean = false
        ): MaterialProgressDialog {
            return MaterialProgressDialog(context).apply {
                setTitle(title)
                setMessage(message)
                setProgressStyle(if (indeterminate) STYLE_SPINNER else STYLE_HORIZONTAL)
                setCancelable(cancelable)
                show()
            }
        }
    }
}
