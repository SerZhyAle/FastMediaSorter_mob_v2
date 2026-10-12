package com.sza.fastmediasorter.ui.exchange

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityMyDevicesBinding
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeIntakeLaunchers
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeServerCardAdapter
import com.sza.fastmediasorter.ui.exchange.helpers.MyDevicesDialogManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * "My devices" (S4116 phase 04): one card per exchange server entry, the entry menu of S4156 strategic 6.3 and
 * "Add server". Every decision lives in [MyDevicesViewModel]; the dialogs live in [MyDevicesDialogManager].
 */
@AndroidEntryPoint
class MyDevicesActivity : BaseActivity<ActivityMyDevicesBinding>() {

    @Inject lateinit var mediaCapabilities: MediaCapabilities

    @Suppress("unused")
    private val multimodalInputSurface: UiSurface = UiSurface.SETTINGS

    private val viewModel: MyDevicesViewModel by viewModels()
    private val launchers: ExchangeIntakeLaunchers by lazy {
        ExchangeIntakeLaunchers(
            this,
            onText = { text -> dialogs.applyIntakeText(text) },
            onFile = { result -> dialogs.applyIntakeFile(result) },
            readUri = { uri -> viewModel.readConnectionFile(contentResolver, uri) },
        )
    }
    private val dialogs: MyDevicesDialogManager by lazy { MyDevicesDialogManager(this, viewModel, launchers) }
    private val adapter: ExchangeServerCardAdapter by lazy {
        ExchangeServerCardAdapter(
            dialogs::showDevices,
            ::showMenu
        )
    }

    override fun getViewBinding(): ActivityMyDevicesBinding = ActivityMyDevicesBinding.inflate(layoutInflater)

    override fun getMouseScrollTargetView(): View = binding.rvExchangeServers

    override fun setupViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setUpNavigation(this)
        binding.rvExchangeServers.layoutManager = LinearLayoutManager(this)
        binding.rvExchangeServers.adapter = adapter
        binding.btnExchangeAddServer.setOnClickListener {
            startActivity(ExchangeSignInActivity.createIntent(this))
        }
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.cards) { cards ->
            adapter.submitList(cards)
            binding.textExchangeServersEmpty.isVisible = cards.isEmpty()
        }
        collectOnLifecycle(viewModel.messages) { message ->
            Toast.makeText(this, dialogs.resolve(message), Toast.LENGTH_LONG).show()
        }
        collectOnLifecycle(viewModel.signIn) { dialogs.renderSignIn(it) }
        collectOnLifecycle(viewModel.devices) { dialogs.renderDevices(it) }
        collectOnLifecycle(viewModel.pairingCode) { dialogs.renderPairingCode(it) }
    }

    override fun getInitialFocusView(): View? =
        binding.rvExchangeServers.findViewHolderForAdapterPosition(0)?.itemView ?: binding.btnExchangeAddServer

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showMenu(anchor: View, entry: DeviceExchangeAccountEntry) {
        val popup = PopupMenu(this, anchor)
        popup.inflate(R.menu.menu_exchange_server_card)
        popup.menu.findItem(R.id.action_exchange_make_outgoing).isVisible = !entry.outgoing
        popup.menu.findItem(R.id.action_exchange_connect_watch).isVisible = mediaCapabilities.supportsWearCompanion
        popup.menu.findItem(R.id.action_exchange_auto_attach).isChecked = entry.autoAttach
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_exchange_edit_address -> dialogs.showEditAddress(entry)
                R.id.action_exchange_make_outgoing -> viewModel.makeOutgoing(entry)
                R.id.action_exchange_connect_watch -> viewModel.connectWatch(entry)
                R.id.action_exchange_share -> dialogs.showShare(entry)
                R.id.action_exchange_auto_attach -> viewModel.setAutoAttach(entry, !entry.autoAttach)
                R.id.action_exchange_remove -> dialogs.confirmRemove(entry)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, MyDevicesActivity::class.java))
        }
    }
}
