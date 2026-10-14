package com.sza.fastmediasorter.ui.exchange

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.core.clipboard.ExchangeClipboardReader
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeClipboardDialogManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The translucent window behind "send the clipboard to the server" and "get the clipboard from the server" on
 * every surface: a menu item, a panel tile, a launcher cell or an app-icon shortcut. It exists because from
 * Android 10 the clipboard answers only the window holding input focus (contract DEVICE-EXCHANGE 22 item CB), and
 * focus arrives after resume - so the clip is read in [onWindowFocusChanged], never in [onCreate].
 */
@AndroidEntryPoint
class ExchangeClipboardActivity : AppCompatActivity() {
    // S2930: BaseActivity is generic over a ViewBinding, so the locale wrapper is applied directly here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject lateinit var reader: ExchangeClipboardReader

    private val viewModel: ExchangeClipboardViewModel by viewModels()
    private lateinit var dialogs: ExchangeClipboardDialogManager
    private val sending: Boolean get() = intent?.action == ACTION_SEND_CLIPBOARD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialogs = ExchangeClipboardDialogManager(this, sending, ::finish, viewModel::cancel, ::finish)
        if (!sending) viewModel.startGet()
        collectOnLifecycle(viewModel.state) { dialogs.render(it) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && sending && !viewModel.started) {
            viewModel.startSend(reader.read(getSystemService(ClipboardManager::class.java).primaryClip))
        }
    }

    override fun onDestroy() {
        dialogs.dismiss()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SEND_CLIPBOARD = "com.sza.fastmediasorter.action.SEND_CLIPBOARD_TO_SERVER"
        const val ACTION_GET_CLIPBOARD = "com.sza.fastmediasorter.action.GET_CLIPBOARD_FROM_SERVER"

        /**
         * NEW_TASK because a pinned shortcut, a launcher cell and the panel start it from outside an activity task
         * of ours; the action is required, since a shortcut intent without one fails to build (S1471).
         */
        fun createIntent(context: Context, send: Boolean): Intent =
            Intent(context, ExchangeClipboardActivity::class.java).apply {
                action = if (send) ACTION_SEND_CLIPBOARD else ACTION_GET_CLIPBOARD
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}
