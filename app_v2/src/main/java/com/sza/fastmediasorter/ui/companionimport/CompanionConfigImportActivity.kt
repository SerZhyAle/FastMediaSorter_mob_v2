package com.sza.fastmediasorter.ui.companionimport

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.data.companion.CompanionConfigDto
import com.sza.fastmediasorter.ui.companionimport.helpers.CompanionConfigImportManager
import com.sza.fastmediasorter.ui.streams.StreamsActivity
import com.sza.fastmediasorter.util.showBoundToHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S0984: receives a `.fmscfg` access file opened from a Telegram/email attachment (ACTION_VIEW) or
 * the share sheet (ACTION_SEND) and runs the one-tap import - the strategic core requirement. A
 * transparent host: only the confirm/result dialogs are visible; garbage is rejected by the parser.
 *
 * Kept separate from [com.sza.fastmediasorter.ui.resourceimport.ResourceImportActivity] so the
 * `.fmscfg` (JSON) and `.fmsr` (XML) contracts stay decoupled.
 */
@AndroidEntryPoint
class CompanionConfigImportActivity : AppCompatActivity() {
    // S2930: BaseActivity is generic over a ViewBinding, so the locale wrapper is applied directly here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject
    lateinit var importManager: CompanionConfigImportManager

    // Held so a recreation (or finish) dismisses it instead of leaking the window.
    private var activeDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only start the import flow once. On recreation (or process restore) do not re-parse and
        // re-confirm - that risks a duplicate import; the user can re-open the attachment instead.
        val firstStart = savedInstanceState == null
        val uri = if (firstStart) resolveUri() else null
        when {
            uri == null -> {
                if (firstStart) {
                    Timber.w("CompanionConfigImportActivity: no URI in intent (action=%s)", intent?.action)
                }
                finish()
            }
            importManager.isBroadcastDescriptor(contentResolver, uri) -> forwardToStreamsImport(uri)
            else -> loadAndConfirm(uri)
        }
    }

    /** S3052: same-uid hand-off, so the descriptor reaches the streams import instead of the config parser. */
    private fun forwardToStreamsImport(uri: Uri) {
        val forwarded = Intent(this, StreamsActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(forwarded)
        finish()
    }

    override fun onDestroy() {
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroy()
    }

    private fun resolveUri(): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> intent?.data
    }

    private fun loadAndConfirm(uri: Uri) {
        lifecycleScope.launch {
            val dto = importManager.readConfig(contentResolver, uri)
            if (dto == null) {
                showResultAndFinish(getString(R.string.companion_import_invalid_error))
            } else {
                showConfirmDialog(dto)
            }
        }
    }

    private fun showConfirmDialog(dto: CompanionConfigDto) {
        val host = dto.accessPaths?.firstOrNull()?.host.orEmpty()
        val name = dto.resourceName ?: host
        val view = layoutInflater.inflate(R.layout.dialog_companion_import_confirm, null, false)
        view.findViewById<TextView>(R.id.textImportSummary).text =
            getString(R.string.companion_import_confirm_message, name, host, dto.roots?.size ?: 0)
        if (dto.hostKeyFingerprintSha256.isNullOrBlank()) {
            view.findViewById<TextView>(R.id.textNoFingerprintWarning).visibility = View.VISIBLE
        }
        val needsPassword = dto.password.isNullOrEmpty()
        val passwordLayout = view.findViewById<TextInputLayout>(R.id.layoutImportPassword)
        val passwordField = view.findViewById<TextInputEditText>(R.id.editImportPassword)
        if (needsPassword) passwordLayout.visibility = View.VISIBLE

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.companion_import_title)
            .setView(view)
            .setPositiveButton(R.string.companion_import_action, null)
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()
        // Custom positive handler so a blank password does not dismiss the dialog and does not clobber
        // an existing stored credential with an empty one on import.
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val entered = passwordField.text?.toString().orEmpty()
                if (needsPassword && entered.isBlank()) {
                    passwordLayout.error = getString(R.string.companion_import_password_required)
                } else {
                    dialog.dismiss()
                    runImport(if (needsPassword) dto.copy(password = entered) else dto)
                }
            }
        }
        activeDialog = dialog
        dialog.showBoundToHost(this@CompanionConfigImportActivity)
    }

    private fun runImport(dto: CompanionConfigDto) {
        lifecycleScope.launch {
            val message = importManager.import(dto).fold(
                onSuccess = { result ->
                    getString(R.string.companion_import_success, result.resourceNames.joinToString(), result.host)
                },
                onFailure = { e ->
                    Timber.e(e, "Companion import from incoming intent failed")
                    getString(R.string.companion_import_failed)
                }
            )
            showResultAndFinish(message)
        }
    }

    private fun showResultAndFinish(message: String) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.companion_import_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()
        activeDialog = dialog
        dialog.showBoundToHost(this@CompanionConfigImportActivity)
    }
}
