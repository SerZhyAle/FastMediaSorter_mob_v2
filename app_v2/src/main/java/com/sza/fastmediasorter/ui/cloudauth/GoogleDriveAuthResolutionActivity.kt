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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

object GoogleDriveAuthResolutionTracker {
    private val resultDeferreds = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    fun register(id: String): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        resultDeferreds[id] = deferred
        return deferred
    }

    fun complete(id: String, success: Boolean) {
        resultDeferreds.remove(id)?.complete(success)
    }

    suspend fun resolvePendingIntent(context: Context, pendingIntent: PendingIntent): Boolean {
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
            val success = result.resultCode == Activity.RESULT_OK
            GoogleDriveAuthResolutionTracker.complete(resolutionId!!, success)
            finish()
        }

        launcher.launch(IntentSenderRequest.Builder(pendingIntent).build())
    }

    override fun onDestroy() {
        super.onDestroy()
        resolutionId?.let { id ->
            GoogleDriveAuthResolutionTracker.complete(id, false)
        }
    }
}
