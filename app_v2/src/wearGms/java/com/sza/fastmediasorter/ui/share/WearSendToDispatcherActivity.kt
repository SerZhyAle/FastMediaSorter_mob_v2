package com.sza.fastmediasorter.ui.share

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.share.helpers.WearSendToErrandManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S2142: the foreground surface the watch's «Send to..» errand is completed on.
 *
 * It exists because of one platform rule: the file arrives in a background
 * `WearableListenerService`, and a receiver is started with an intent that leaves this process,
 * which a background process may not do since Android 10. Strategic 6 question 5 settles the shape -
 * a notification offers the action, and this transparent trampoline is what its tap opens.
 *
 * Transparent and immediately finishing, so what the owner sees is the receiver, never this screen.
 */
@AndroidEntryPoint
class WearSendToDispatcherActivity : AppCompatActivity() {

    @Inject lateinit var errandManager: WearSendToErrandManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedPath = intent.getStringExtra(EXTRA_SAVED_PATH)
        val receiverId = intent.getStringExtra(EXTRA_RECEIVER_ID)
        if (savedPath.isNullOrEmpty() || receiverId.isNullOrEmpty()) {
            finish()
            return
        }
        // Guarded against a second tap of the same notification re-running a send that is already on
        // screen: the recreated instance would fire the receiver a second time with the same file.
        if (savedInstanceState != null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val sent = errandManager.run(this@WearSendToDispatcherActivity, savedPath, receiverId)
            if (!sent) {
                // The application context, because this activity is about to be gone and a toast
                // bound to it would be cancelled with it - leaving a failure the owner never saw.
                Toast.makeText(applicationContext, R.string.share_to_send_failed, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    companion object {

        private const val EXTRA_SAVED_PATH = "wear_send_to_saved_path"
        private const val EXTRA_RECEIVER_ID = "wear_send_to_receiver_id"

        /** The errand as an intent: which local file, and which receiver the watch picked for it. */
        fun intent(context: Context, savedPath: String, receiverId: String): Intent =
            Intent(context, WearSendToDispatcherActivity::class.java).apply {
                putExtra(EXTRA_SAVED_PATH, savedPath)
                putExtra(EXTRA_RECEIVER_ID, receiverId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }
}
