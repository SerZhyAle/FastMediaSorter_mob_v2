package com.sza.fastmediasorter.ui.cloudauth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.data.cloud.GoogleDriveBrowserAuthManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class GoogleDriveAuthCompletionActivity : ComponentActivity() {

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