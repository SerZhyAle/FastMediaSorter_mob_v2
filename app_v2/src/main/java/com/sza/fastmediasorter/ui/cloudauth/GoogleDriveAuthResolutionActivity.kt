package com.sza.fastmediasorter.ui.cloudauth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import com.sza.fastmediasorter.core.util.LocaleHelper
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object GoogleDriveAuthResolutionTracker {
    private val resultDeferreds = ConcurrentHashMap<String, CompletableDeferred<Intent?>>()

    fun register(id: String): CompletableDeferred<Intent?> {
        val deferred = CompletableDeferred<Intent?>()
        resultDeferreds[id] = deferred
        return deferred
    }

    /** [result] is the consent screen's result data on success, null on cancel or failure. */
    fun complete(id: String, result: Intent?) {
        resultDeferreds.remove(id)?.complete(result)
    }

    suspend fun resolvePendingIntent(context: Context, pendingIntent: PendingIntent): Boolean =
        resolveForResult(context, pendingIntent) != null

    /**
     * S4427: the result data carries the scopes Google actually granted, which a Boolean loses.
     * Returns null when the user cancelled the consent screen.
     */
    suspend fun resolveForResult(context: Context, pendingIntent: PendingIntent): Intent? {
        val id = UUID.randomUUID().toString()
        val deferred = register(id)
        val intent = Intent(context, GoogleDriveAuthResolutionActivity::class.java).apply {
            putExtra("resolution_id", id)
            putExtra("pending_intent", pendingIntent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return deferred.await()
    }
}

class GoogleDriveAuthResolutionActivity : ComponentActivity() {
    // S2930: BaseActivity is generic over a ViewBinding, so the locale wrapper is applied directly here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private var resolutionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resolutionId = intent.getStringExtra("resolution_id")
        val pendingIntent = IntentCompat.getParcelableExtra(intent, "pending_intent", PendingIntent::class.java)

        if (pendingIntent == null || resolutionId == null) {
            finish()
            return
        }

        val launcher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            // A successful result without data must still read as success, hence the empty Intent.
            val data = if (result.resultCode == Activity.RESULT_OK) result.data ?: Intent() else null
            resolutionId?.let { id -> GoogleDriveAuthResolutionTracker.complete(id, data) }
            finish()
        }

        if (savedInstanceState == null) {
            launcher.launch(IntentSenderRequest.Builder(pendingIntent).build())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            resolutionId?.let { id ->
                GoogleDriveAuthResolutionTracker.complete(id, null)
            }
        }
    }
}
