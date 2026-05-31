package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import com.sza.fastmediasorter.ui.calculator.CalculatorActivity

internal class TextEditorCalculatorBridge(
    private val context: Context,
    private val launcher: ActivityResultLauncher<Intent>,
    private val textViewerManagerProvider: () -> TextViewerManager?,
) {
    fun launch(initialInput: String) {
        launcher.launch(
            CalculatorActivity.createIntent(
                context = context,
                initialInput = initialInput,
                returnResult = true,
            )
        )
    }

    fun handleResult(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) return
        val value = CalculatorActivity.readResult(result.data)?.takeIf { it.isNotBlank() } ?: return
        textViewerManagerProvider()?.insertCalculatorResult(value)
    }
}
