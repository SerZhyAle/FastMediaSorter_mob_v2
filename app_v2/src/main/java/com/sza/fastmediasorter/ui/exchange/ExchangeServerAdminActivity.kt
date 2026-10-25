package com.sza.fastmediasorter.ui.exchange

import android.content.Context
import android.content.Intent
import android.view.Menu
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
import com.google.android.material.textview.MaterialTextView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityExchangeServerAdminBinding
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeAdminPolicy
import com.sza.fastmediasorter.domain.model.ExchangeAdminSharedInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminStatus
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeAdminInvitationManager
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeAdminRow
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeAdminRowAdapter
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeFailurePanel
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeServerAdminDialogManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExchangeServerAdminActivity : BaseActivity<ActivityExchangeServerAdminBinding>() {
    private val viewModel: ExchangeServerAdminViewModel by viewModels()
    private val failures by lazy { ExchangeFailurePanel(binding.exchangeAdminFailure, this) }
    private val dialogs by lazy { ExchangeServerAdminDialogManager(this, viewModel) }
    private val invitations by lazy { ExchangeAdminInvitationManager(this, viewModel) }
    private val accountRows = ExchangeAdminRowAdapter(onRowOpen = ::openAccount, onRowOverflow = ::openOverflow)
    private val invitationRows = ExchangeAdminRowAdapter(onRowOpen = ::openInvitation, onRowOverflow = ::openOverflow)
    private var renderedShared: ExchangeAdminSharedInvitation? = null
    private var renderedPolicy: ExchangeAdminPolicy? = null

    override fun getViewBinding() = ActivityExchangeServerAdminBinding.inflate(layoutInflater)

    override fun isSensitiveScreen(): Boolean = true

    override fun getMouseScrollTargetView(): View = binding.scrollExchangeAdmin

    override fun getInitialFocusView(): View = binding.editExchangeAdminPassword

    override fun setupViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setUpNavigation(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.listExchangeAdminAccounts.layoutManager = LinearLayoutManager(this)
        binding.listExchangeAdminAccounts.adapter = accountRows
        binding.listExchangeAdminInvitations.layoutManager = LinearLayoutManager(this)
        binding.listExchangeAdminInvitations.adapter = invitationRows
        binding.btnExchangeAdminSignIn.setOnClickListener {
            val editable = binding.editExchangeAdminPassword.text
            val password = CharArray(editable?.length ?: 0) { index -> editable?.get(index) ?: '\u0000' }
            editable?.clear()
            viewModel.signIn(password)
        }
        binding.btnExchangeAdminSignOut.setOnClickListener { viewModel.endSession() }
        binding.btnExchangeAdminAddAccount.setOnClickListener { dialogs.showAddAccount() }
        binding.btnExchangeAdminCreateInvitation.setOnClickListener { invitations.showCreateDialog() }
        binding.btnExchangeAdminSharedSave.setOnClickListener {
            (viewModel.state.value as? ExchangeAdminScreenState.SignedIn)?.shared?.let { shared ->
                dialogs.showSharedInvitation(shared)
            }
        }
        binding.btnExchangeAdminLimitsSave.setOnClickListener {
            (viewModel.state.value as? ExchangeAdminScreenState.SignedIn)?.policy?.let { policy ->
                dialogs.showPolicy(policy)
            }
        }
        invitations.clearSharedFiles()
        failures.hide()
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { render(it) }
        collectOnLifecycle(viewModel.replyFailures) { code -> dialogs.showReplyFailure(code) }
        collectOnLifecycle(viewModel.devicesOfAccount) { devices -> dialogs.showDevicesList(devices) }
        collectOnLifecycle(viewModel.passwordChanged) { changed -> dialogs.showPasswordSet(changed) }
        collectOnLifecycle(viewModel.createdInvitation) { created -> invitations.show(created) }
    }

    private fun render(state: ExchangeAdminScreenState) {
        binding.cardExchangeAdminSignIn.isVisible = state is ExchangeAdminScreenState.SignedOut ||
            state is ExchangeAdminScreenState.SigningIn || state is ExchangeAdminScreenState.Lapsed
        binding.cardExchangeAdminDisabled.isVisible = state is ExchangeAdminScreenState.Disabled
        binding.cardExchangeAdminStatus.isVisible = state is ExchangeAdminScreenState.SignedIn
        binding.contentExchangeAdminSections.isVisible = state is ExchangeAdminScreenState.SignedIn
        binding.btnExchangeAdminSignIn.isEnabled = state !is ExchangeAdminScreenState.SigningIn
        binding.editExchangeAdminPassword.isEnabled = state !is ExchangeAdminScreenState.SigningIn
        failures.hide()
        if (state !is ExchangeAdminScreenState.SignedIn) dialogs.dismissTransient()
        when (state) {
            is ExchangeAdminScreenState.SignedOut -> {
                binding.textExchangeAdminAddress.text = getString(R.string.exchange_admin_address, state.address)
                state.failure?.let(failures::showFailure)
            }
            ExchangeAdminScreenState.SigningIn -> failures.showProgress(getString(R.string.exchange_admin_signing_in))
            is ExchangeAdminScreenState.SignedIn -> {
                renderStatus(state.status)
                accountRows.submitList(state.accounts?.map { account -> ExchangeAdminRow.Account(account) })
                invitationRows.submitList(
                    state.invitations?.map { invitation -> ExchangeAdminRow.Invitation(invitation) },
                )
                renderShared(state.shared)
                renderPolicy(state.policy)
            }
            ExchangeAdminScreenState.Lapsed -> failures.showProgress(getString(R.string.exchange_admin_lapsed))
            ExchangeAdminScreenState.MissingEntry -> finish()
            ExchangeAdminScreenState.Disabled -> Unit
        }
    }

    private fun renderStatus(status: ExchangeAdminStatus?) {
        binding.textExchangeAdminStatus.text = status?.let {
            listOf(
                getString(R.string.exchange_admin_status_accounts, it.accounts, it.maxAccounts),
                getString(R.string.exchange_admin_status_devices, it.devices, it.online),
                getString(R.string.exchange_admin_status_resources, it.resources, it.broadcasts),
                getString(R.string.exchange_admin_status_clipboards, it.clipboards),
                getString(R.string.exchange_admin_status_version, it.version.orEmpty()),
                getString(
                    if (it.sealed) {
                        R.string.exchange_admin_status_sealed_on
                    } else {
                        R.string.exchange_admin_status_sealed_off
                    },
                ),
            ).joinToString("\n")
        } ?: getString(R.string.exchange_admin_signing_in)
    }

    private fun renderShared(shared: ExchangeAdminSharedInvitation?) {
        // Accounts, invitations, shared and policy arrive as separate emissions; the container holds the lines
        // of the last non-equal value (it starts empty, which is what null renders), so an equal one repaints nothing.
        if (shared == renderedShared) return
        renderedShared = shared
        binding.fieldsExchangeAdminShared.removeAllViews()
        shared ?: return
        val word = getString(
            if (shared.enabled) R.string.exchange_admin_shared_on else R.string.exchange_admin_shared_off,
        )
        binding.fieldsExchangeAdminShared.addView(sectionLine(word))
    }

    private fun renderPolicy(policy: ExchangeAdminPolicy?) {
        if (policy == renderedPolicy) return
        renderedPolicy = policy
        binding.fieldsExchangeAdminLimits.removeAllViews()
        policy ?: return
        listOf(
            limitLine(R.string.exchange_admin_limit_max_accounts, policy.maxAccounts),
            limitLine(R.string.exchange_admin_limit_devices, policy.defaults.devices),
            limitLine(R.string.exchange_admin_limit_resources, policy.defaults.resources),
            limitLine(R.string.exchange_admin_limit_broadcasts, policy.defaults.broadcasts),
            limitLine(R.string.exchange_admin_limit_broadcasts_per_device, policy.defaults.broadcastsPerDevice),
            limitLine(R.string.exchange_admin_limit_clipboard, policy.defaults.clipboardBytes),
        ).forEach { line -> binding.fieldsExchangeAdminLimits.addView(sectionLine(line)) }
        if (!policy.persisted) {
            binding.fieldsExchangeAdminLimits.addView(
                sectionLine(getString(R.string.exchange_admin_not_persisted)),
            )
        }
    }

    private fun limitLine(label: Int, value: Number): String = "${getString(label)}: $value"

    private fun sectionLine(text: CharSequence): TextView {
        val pad = resources.getDimensionPixelSize(R.dimen.margin_small)
        return MaterialTextView(this).apply {
            this.text = text
            setTextAppearance(this@ExchangeServerAdminActivity, TextAppearance_Material3_BodyMedium)
            setPadding(pad, pad, pad, pad)
        }
    }

    private fun openAccount(row: ExchangeAdminRow) {
        (row as? ExchangeAdminRow.Account)?.let { dialogs.showDevices(it.account.login) }
    }

    private fun openInvitation(row: ExchangeAdminRow) {
        (row as? ExchangeAdminRow.Invitation)?.let { dialogs.confirmWithdraw(it.invitation.codeHash) }
    }

    private fun openOverflow(row: ExchangeAdminRow, anchor: View) {
        when (row) {
            is ExchangeAdminRow.Account -> dialogs.showAccountMenu(row, anchor)
            is ExchangeAdminRow.Invitation -> dialogs.showInvitationMenu(row, anchor)
            is ExchangeAdminRow.Device -> Unit
        }
    }

    override fun onStop() {
        binding.editExchangeAdminPassword.text?.clear()
        if (!isChangingConfigurations) viewModel.endSession()
        super.onStop()
    }

    override fun onDestroy() {
        invitations.clearSharedFiles()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_help_action, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        fun createIntent(context: Context, entry: DeviceExchangeAccountEntry): Intent =
            Intent(context, ExchangeServerAdminActivity::class.java)
                .putExtra(ExchangeServerAdminViewModel.IDENTITY, entry.identity)
    }
}
