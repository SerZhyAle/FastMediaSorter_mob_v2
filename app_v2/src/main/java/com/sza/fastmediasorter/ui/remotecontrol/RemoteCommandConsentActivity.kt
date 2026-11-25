package com.sza.fastmediasorter.ui.remotecontrol

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.core.notification.RemoteCommandConsentNotifier
import com.sza.fastmediasorter.core.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Transparent, no-UI target of the consent notification's Allow and Decline (REMOTE-CONTROL rule 10). An
 * Activity because only a notification tap makes a background app visible, which a verb starting a camera or
 * microphone foreground service needs; Decline takes the same door so both answers share one path.
 */
@AndroidEntryPoint
class RemoteCommandConsentActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject lateinit var consent: RemoteCommandConsentNotifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent?.getIntExtra(EXTRA_KEY, NO_KEY) ?: NO_KEY
        if (key == NO_KEY) {
            finish()
            return
        }
        val accepted = intent.getBooleanExtra(EXTRA_ACCEPT, false)
        lifecycleScope.launch {
            consent.answer(key, accepted)
            finish()
        }
    }

    companion object {
        private const val EXTRA_KEY = "extra_remote_command_key"
        private const val EXTRA_ACCEPT = "extra_remote_command_accept"
        private const val NO_KEY = -1

        fun intent(context: Context, key: Int, accept: Boolean): Intent =
            Intent(context, RemoteCommandConsentActivity::class.java).apply {
                putExtra(EXTRA_KEY, key)
                putExtra(EXTRA_ACCEPT, accept)
            }
    }
}
