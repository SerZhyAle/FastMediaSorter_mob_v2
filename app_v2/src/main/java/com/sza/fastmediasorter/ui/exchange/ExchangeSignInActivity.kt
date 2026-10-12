package com.sza.fastmediasorter.ui.exchange

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeIntakeLaunchers
import com.sza.fastmediasorter.ui.exchange.helpers.MyDevicesDialogManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * S4155: exported receiver for exchange connection strings - `.fmsexch` file (ACTION_VIEW) or shared text
 * (ACTION_SEND). A transparent host (like CompanionConfigImportActivity): shows the same quick sign-in dialog
 * with intake block, server summary and fields prefilled from the string.
 *
 * It only fills the form; nothing is saved without the user entering their password and tapping "Sign in".
 */
@AndroidEntryPoint
class ExchangeSignInActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val viewModel: MyDevicesViewModel by viewModels()
    private val launchers: ExchangeIntakeLaunchers by lazy {
        ExchangeIntakeLaunchers(
            this,
            onText = { text -> dialogs.applyIntakeText(text) },
            onFile = { result -> dialogs.applyIntakeFile(result) },
            readUri = { uri -> viewModel.readConnectionFile(contentResolver, uri) },
        )
    }
    private val dialogs: MyDevicesDialogManager by lazy {
        MyDevicesDialogManager(this, viewModel, launchers)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }

        collectOnLifecycle(viewModel.signIn) { state ->
            dialogs.renderSignIn(state)
            if (state is ExchangeSignInState.Done) {
                finish()
            }
        }

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> handleViewIntent(intent.data)
            Intent.ACTION_SEND -> handleSendIntent(intent.getStringExtra(Intent.EXTRA_TEXT))
            ACTION_SIGN_IN_PREFILL -> handlePrefillIntent(intent)
            else -> {
                dialogs.showSignIn(onDismiss = { finish() })
            }
        }
    }

    private fun handleViewIntent(uri: Uri?) {
        if (uri == null) {
            finish()
            return
        }
        dialogs.showSignIn(onDismiss = { finish() })
        lifecycleScope.launch {
            val result = viewModel.readConnectionFile(contentResolver, uri)
            dialogs.applyIntakeFile(result)
        }
    }

    private fun handleSendIntent(text: String?) {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            finish()
            return
        }
        val outcome = viewModel.intakeText(trimmed)
        if (outcome is ExchangeIntakeOutcome.Refused) {
            finish()
            return
        }
        dialogs.showSignIn(onDismiss = { finish() })
        dialogs.applyIntakeText(trimmed)
    }

    private fun handlePrefillIntent(intent: Intent) {
        val raw = intent.getStringExtra(EXTRA_CONNECTION_STRING)
        dialogs.showSignIn(onDismiss = { finish() })
        if (!raw.isNullOrBlank()) {
            dialogs.applyIntakeText(raw)
        }
    }

    companion object {
        const val ACTION_SIGN_IN_PREFILL = "com.sza.fastmediasorter.action.EXCHANGE_SIGN_IN"
        const val EXTRA_CONNECTION_STRING = "extra_connection_string"

        fun createIntent(context: Context, connectionString: String? = null): Intent =
            Intent(context, ExchangeSignInActivity::class.java).apply {
                if (connectionString != null) {
                    action = ACTION_SIGN_IN_PREFILL
                    putExtra(EXTRA_CONNECTION_STRING, connectionString)
                }
            }
    }
}
