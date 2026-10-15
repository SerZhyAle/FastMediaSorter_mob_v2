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
import com.sza.fastmediasorter.ui.common.support.MediaExchangePortalLinks
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeAccountDialogManager
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeFailureDialogManager
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

    // Eager on purpose: BaseActivity runs setupViews() after the first frame, when the activity is already
    // RESUMED, and registerForActivityResult throws once the lifecycle has reached STARTED. The lambdas below
    // read `dialogs` and `viewModel` only when a result arrives, so construction order does not matter.
    private val launchers: ExchangeIntakeLaunchers = ExchangeIntakeLaunchers(
        this,
        onText = { text -> dialogs.applyIntakeText(text) },
        onFile = { result -> dialogs.applyIntakeFile(result) },
        readUri = { uri -> viewModel.readConnectionFile(contentResolver, uri) },
    )
    private val dialogs: MyDevicesDialogManager by lazy { MyDevicesDialogManager(this, viewModel, launchers) }
    private val accountDialogs: ExchangeAccountDialogManager by lazy { ExchangeAccountDialogManager(this, viewModel) }
    private val failureDialogs: ExchangeFailureDialogManager by lazy { ExchangeFailureDialogManager(this) }
    private val adapter: ExchangeServerCardAdapter by lazy {
        ExchangeServerCardAdapter(
            dialogs::showDevices,
            ::showMenu
        )
    }

    override fun getViewBinding(): ActivityMyDevicesBinding = ActivityMyDevicesBinding.inflate(layoutInflater)

    override fun getMouseScrollTargetView(): View = binding.scrollExchangeServers

    override fun setupViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setUpNavigation(this)
        binding.rvExchangeServers.layoutManager = LinearLayoutManager(this)
        binding.rvExchangeServers.adapter = adapter
        binding.btnExchangeAddServer.setOnClickListener {
            startActivity(ExchangeSignInActivity.createIntent(this))
        }
        binding.btnExchangePortalAddServer.setOnClickListener {
            MediaExchangePortalLinks.open(this, MediaExchangePortalLinks.Page.MY_DEVICES)
        }
        binding.btnExchangePortalServer.setOnClickListener {
            MediaExchangePortalLinks.open(this, MediaExchangePortalLinks.Page.EXCHANGE_SERVER)
        }
        // The same condition as the Connect watch menu item: the page is about that action.
        binding.btnExchangePortalWatch.isVisible = mediaCapabilities.supportsWearCompanion
        binding.btnExchangePortalWatch.setOnClickListener {
            MediaExchangePortalLinks.open(this, MediaExchangePortalLinks.Page.WATCH_EXCHANGE)
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
        collectOnLifecycle(viewModel.failures) { failureDialogs.show(it) }
        collectOnLifecycle(viewModel.signIn) { dialogs.renderSignIn(it) }
        collectOnLifecycle(viewModel.devices) { dialogs.renderDevices(it) }
        collectOnLifecycle(viewModel.pairingCode) { dialogs.renderPairingCode(it) }
        collectOnLifecycle(viewModel.accountAction) { accountDialogs.render(it) }
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
                R.id.action_exchange_sign_in_again -> signInAgain(entry)
                R.id.action_exchange_change_password -> accountDialogs.showChangePassword(entry)
                R.id.action_exchange_remove -> dialogs.confirmRemove(entry)
                R.id.action_exchange_delete_account -> accountDialogs.showDeleteAccount(entry)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    // The held address, pin and login refill the form, so the same identity re-enrolls its own deviceId.
    private fun signInAgain(entry: DeviceExchangeAccountEntry) {
        dialogs.showSignIn()
        viewModel.signInAgainString(entry)?.let(dialogs::applyIntakeText)
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, MyDevicesActivity::class.java))
        }
    }
}
