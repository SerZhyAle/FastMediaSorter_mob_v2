package com.sza.fastmediasorter.ui.exchange.helpers

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringCodec
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringResult
import com.sza.fastmediasorter.ui.companionimport.qr.CompanionQrScanActivity
import kotlinx.coroutines.launch

/**
 * S4155: the two system surfaces the quick sign-in form reads a connection string from - the in-app QR scanner
 * and the file picker. Registered as a property of the host activity, because a launcher must exist before the
 * activity starts; the host forwards what comes back to the open form.
 *
 * A picked file is read by its content (`ExchangeConnectionStringCodec.readFile`), never by its name or type:
 * mail and messengers deliver a `.fmsexch` with neither.
 */
class ExchangeIntakeLaunchers(
    private val activity: ComponentActivity,
    private val onText: (String) -> Unit,
    private val onFile: (ExchangeConnectionStringResult) -> Unit,
    private val readUri: suspend (android.net.Uri) -> ExchangeConnectionStringResult,
) {

    private val scan = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val payload = result.data?.getStringExtra(CompanionQrScanActivity.EXTRA_PAYLOAD)
        if (result.resultCode == Activity.RESULT_OK && payload != null) onText(payload)
    }

    private val pick = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        activity.lifecycleScope.launch {
            onFile(readUri(uri))
        }
    }

    fun scanQr() {
        scan.launch(CompanionQrScanActivity.createIntent(activity))
    }

    fun chooseFile() {
        pick.launch(arrayOf(ANY_MIME_TYPE))
    }

    private companion object {
        const val ANY_MIME_TYPE = "*/*"
    }
}
