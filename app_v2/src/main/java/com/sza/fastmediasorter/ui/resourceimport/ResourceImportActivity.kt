package com.sza.fastmediasorter.ui.resourceimport

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.settings.helpers.SzaResourcesImporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S0422: receives a resource share file opened via the system "open with" (ACTION_VIEW) or the
 * share sheet (ACTION_SEND) and runs the same preview -> confirm -> import flow as the settings
 * screen. Transparent host: only the dialogs are visible; the activity finishes when done.
 */
@AndroidEntryPoint
class ResourceImportActivity : AppCompatActivity() {

    @Inject
    lateinit var importer: SzaResourcesImporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = resolveUri()
        if (uri == null) {
            Timber.w("ResourceImportActivity: no URI in incoming intent (action=%s)", intent?.action)
            finish()
            return
        }
        previewAndConfirm(uri)
    }

    private fun resolveUri(): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> intent?.data
    }

    private fun previewAndConfirm(uri: Uri) {
        lifecycleScope.launch {
            Timber.d("S0422: file-association import received")
            when (val preview = importer.preview(uri)) {
                is SzaResourcesImporter.PreviewResult.Valid -> showConfirmDialog(uri, preview)
                is SzaResourcesImporter.PreviewResult.Invalid -> showResultAndFinish(
                    getString(R.string.resource_share_invalid_file)
                )
            }
        }
    }

    private fun showConfirmDialog(uri: Uri, preview: SzaResourcesImporter.PreviewResult.Valid) {
        val message = buildString {
            append(getString(R.string.resource_share_import_preview_message, preview.toCreate, preview.toUpdate))
            if (preview.containsCredentials) {
                append("\n\n")
                append(getString(R.string.resource_share_credentials_warning))
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.resource_share_import_title)
            .setMessage(message)
            .setPositiveButton(R.string.resource_share_import_action) { _, _ -> runImport(uri) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun runImport(uri: Uri) {
        lifecycleScope.launch {
            val message = when (val result = importer.importFromUri(uri)) {
                is SzaResourcesImporter.ImportResult.Success ->
                    getString(R.string.resource_share_import_success, result.imported, result.updated, result.skipped)
                is SzaResourcesImporter.ImportResult.Failure -> {
                    Timber.e(result.error, "Resource import from incoming intent failed")
                    getString(R.string.resource_share_import_failed)
                }
            }
            showResultAndFinish(message)
        }
    }

    private fun showResultAndFinish(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.resource_share_import_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
