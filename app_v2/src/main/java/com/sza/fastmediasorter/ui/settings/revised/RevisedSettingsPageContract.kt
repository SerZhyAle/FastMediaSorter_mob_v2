package com.sza.fastmediasorter.ui.settings.revised

import android.os.Bundle

interface RevisedSettingsPageContract {
    fun capturePageState(): Bundle? = null

    fun restorePageState(savedState: Bundle) = Unit
}