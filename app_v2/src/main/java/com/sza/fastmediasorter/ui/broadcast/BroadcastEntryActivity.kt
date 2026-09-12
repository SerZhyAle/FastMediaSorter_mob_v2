package com.sza.fastmediasorter.ui.broadcast

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.ActivityBroadcastEntryBinding
import com.sza.fastmediasorter.ui.broadcast.helpers.BroadcastEntryManager
import com.sza.fastmediasorter.ui.main.helpers.MainHelperFactory
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * S2818: the confirmation screen every broadcast entry surface (launcher shortcut, Quick Settings
 * tile, home-screen widget) opens. The screen owns the permission prompts - a service cannot ask
 * for them - and the start decision stays with the user (strategic ADR-1/ADR-2).
 */
@AndroidEntryPoint
class BroadcastEntryActivity : AppCompatActivity() {
    // S2930: BaseActivity is generic over a ViewBinding, so the locale wrapper is applied directly here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject
    lateinit var controller: BroadcastSourceController

    @Inject
    lateinit var mainHelperFactory: MainHelperFactory

    private var entryManager: BroadcastEntryManager? = null

    private val requestRecordAudioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            entryManager?.onPermissionResult(Manifest.permission.RECORD_AUDIO, granted)
        }

    private val requestPostNotificationsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            entryManager?.onPermissionResult(Manifest.permission.POST_NOTIFICATIONS, granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityBroadcastEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val broadcastManager = mainHelperFactory.createBroadcastManager(
            activity = this,
            controller = controller,
            requestRecordAudioPermission = {
                requestRecordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            requestPostNotificationsPermission = {
                requestPostNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
        val manager = BroadcastEntryManager(
            broadcastManager = broadcastManager,
            controller = controller,
            finish = { finish() },
            render = { state -> render(binding, state) },
        )
        entryManager = manager
        binding.broadcastEntryStart.setOnClickListener { manager.start() }
        binding.broadcastEntryStop.setOnClickListener { manager.stop() }
        binding.broadcastEntryCancel.setOnClickListener { manager.cancel() }
        manager.bind(this)
    }

    private fun render(binding: ActivityBroadcastEntryBinding, state: BroadcastEntryManager.UiState) {
        when (state) {
            BroadcastEntryManager.UiState.Unavailable -> {
                Toast.makeText(this, R.string.broadcast_entry_unavailable, Toast.LENGTH_LONG).show()
                binding.broadcastEntryStart.visibility = View.GONE
            }
            BroadcastEntryManager.UiState.Confirm -> {
                binding.broadcastEntryState.visibility = View.GONE
                binding.broadcastEntryStop.visibility = View.GONE
                binding.broadcastEntryStart.visibility = View.VISIBLE
            }
            BroadcastEntryManager.UiState.Live -> {
                binding.broadcastEntryStart.visibility = View.GONE
                binding.broadcastEntryState.visibility = View.VISIBLE
                binding.broadcastEntryStop.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        const val ACTION_OPEN_BROADCAST_ENTRY = "com.sza.fastmediasorter.action.OPEN_BROADCAST_ENTRY"
    }
}
