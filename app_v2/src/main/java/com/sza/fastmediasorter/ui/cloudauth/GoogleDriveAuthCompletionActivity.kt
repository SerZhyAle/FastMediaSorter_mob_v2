package com.sza.fastmediasorter.ui.cloudauth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.data.cloud.GoogleDriveBrowserAuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GoogleDriveAuthCompletionActivity : ComponentActivity() {
    // S2930: BaseActivity is generic over a ViewBinding, so the locale wrapper is applied directly here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject
    lateinit var browserAuthManager: GoogleDriveBrowserAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val safeIntent = intent ?: run {
            finish()
            return
        }

        // Keep redirect parsing in one dedicated activity so host activities only need onResume.
        lifecycleScope.launch {
            runCatching {
                browserAuthManager.completeAuthorizationIntent(safeIntent)
            }.onFailure {
                Timber.e(it, "GoogleDriveAuthCompletionActivity failed to finalize browser auth")
            }
            finish()
        }
    }
}
