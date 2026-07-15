package com.sza.fastmediasorter.ui.companionimport

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
import com.sza.fastmediasorter.data.companion.CompanionConfigDto
import com.sza.fastmediasorter.data.companion.CompanionConfigException
import com.sza.fastmediasorter.data.companion.CompanionConfigParser
import com.sza.fastmediasorter.domain.usecase.companion.ImportCompanionConfigUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream
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

    @Inject
    lateinit var parser: CompanionConfigParser

    @Inject
    lateinit var importUseCase: ImportCompanionConfigUseCase

    // Held so a recreation (or finish) dismisses it instead of leaking the window.
    private var activeDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only start the import flow once. On recreation (or process restore) do not re-parse and
        // re-confirm - that risks a duplicate import; the user can re-open the attachment instead.
        if (savedInstanceState != null) {
            finish()
            return
        }
        val uri = resolveUri()
        if (uri == null) {
            Timber.w("CompanionConfigImportActivity: no URI in intent (action=%s)", intent?.action)
            finish()
            return
        }
        loadAndConfirm(uri)
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
            val dto = withContext(Dispatchers.IO) { readConfig(uri) }
            if (dto == null) {
                showResultAndFinish(getString(R.string.companion_import_invalid_error))
            } else {
                showConfirmDialog(dto)
            }
        }
    }

    // Broad catch is an intentional import-boundary guard: any read/parse failure rejects the file
    // (transparent host) instead of crashing. (S0988: surfaced by the diff-scoped detekt gate.)
    @Suppress("TooGenericExceptionCaught")
    private fun readConfig(uri: Uri): CompanionConfigDto? = try {
        val bytes = contentResolver.openInputStream(uri)?.use { readCapped(it) }
        if (bytes == null) null else parser.parse(bytes)
    } catch (e: CompanionConfigException) {
        Timber.w(e, "Companion config rejected: ${e.reason}")
        null
    } catch (e: Exception) {
        Timber.w(e, "Companion config read failed")
        null
    }

    /** Reads at most [MAX_CONFIG_BYTES]; returns null if the stream is larger (guards this exported entry). */
    private fun readCapped(input: InputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_CONFIG_BYTES) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
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
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
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
        dialog.show()
    }

    private fun runImport(dto: CompanionConfigDto) {
        lifecycleScope.launch {
            val message = importUseCase.import(dto).fold(
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
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()
        activeDialog = dialog
        dialog.show()
    }

    private companion object {
        const val MAX_CONFIG_BYTES = 64 * 1024
        const val READ_CHUNK_BYTES = 8 * 1024
    }
}
