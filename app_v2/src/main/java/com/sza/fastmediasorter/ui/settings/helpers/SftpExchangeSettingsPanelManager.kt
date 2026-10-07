package com.sza.fastmediasorter.ui.settings.helpers

import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.SftpExchangeConfig
import com.sza.fastmediasorter.domain.model.SftpExchangePasswordPolicy
import com.sza.fastmediasorter.domain.model.SftpServerState
import com.sza.fastmediasorter.domain.model.SftpTunnelState
import com.sza.fastmediasorter.domain.usecase.sftpserver.ManageSftpServerUseCase
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Binds the "Access from anywhere" part of the SFTP server card: the exchange server the embedded
 * server registers on (contract ANYWHERE-ACCESS section 5), its credential, the share link and the
 * registration status.
 *
 * Off by default, and with the toggle off the card is the LAN-only server it always was. The password
 * field is never filled back from storage: it is write-only, and an empty field keeps the stored one.
 */
class SftpExchangeSettingsPanelManager(
    private val fragment: Fragment,
    private val binding: FragmentSettingsGeneralBinding,
    private val manageSftpServer: ManageSftpServerUseCase,
) {

    private val context get() = fragment.requireContext()

    fun bind() {
        binding.rowSftpExchangeEnabled.setOnCheckedChangeListener { checked ->
            launchInView { manageSftpServer.setExchangeEnabled(checked) }
        }
        binding.btnSftpExchangeSave.setOnClickListener { save() }
        binding.etSftpExchangePassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) save()
            false
        }
        binding.btnSftpExchangeNewLink.setOnClickListener {
            launchInView {
                manageSftpServer.rotateShareId()
                Toast.makeText(context, R.string.settings_sftp_exchange_new_link_done, Toast.LENGTH_LONG).show()
            }
        }
        binding.btnSftpExchangeTrustAgain.setOnClickListener {
            launchInView { manageSftpServer.trustExchangeServerAgain() }
        }
        fragment.collectOnLifecycle(
            combine(manageSftpServer.exchangeConfig, manageSftpServer.tunnelState, manageSftpServer.state, ::Triple)
        ) { (config, tunnel, server) ->
            render(config, tunnel, server)
        }
    }

    private fun render(config: SftpExchangeConfig, tunnel: SftpTunnelState, server: SftpServerState) {
        binding.rowSftpExchangeEnabled.setCheckedSilently(config.enabled)
        binding.containerSftpExchangeFields.isVisible = config.enabled
        binding.textSftpExchangeStatus.text = statusText(config, tunnel, server)
        binding.btnSftpExchangeTrustAgain.isVisible = tunnel is SftpTunnelState.CertificateChanged
        // Only a code that carries the tunnel needs a legacy twin; a LAN-only code is already FMSSFTP1.
        binding.btnSftpServerLegacyQr.isVisible = server is SftpServerState.Running && config.isUsable
        if (!binding.etSftpExchangeHost.hasFocus()) binding.etSftpExchangeHost.setText(config.host)
        if (!binding.etSftpExchangePort.hasFocus()) binding.etSftpExchangePort.setText(config.port.toString())
    }

    private fun statusText(config: SftpExchangeConfig, tunnel: SftpTunnelState, server: SftpServerState): String =
        when {
            !config.enabled -> context.getString(R.string.settings_sftp_exchange_status_off)
            server !is SftpServerState.Running -> context.getString(R.string.settings_sftp_exchange_status_waiting)
            tunnel is SftpTunnelState.Off -> incompleteText(config)
            else -> tunnelText(config, tunnel)
        }

    private fun incompleteText(config: SftpExchangeConfig): String =
        if (SftpExchangePasswordPolicy.accepts(config.password)) {
            context.getString(R.string.settings_sftp_exchange_server_invalid)
        } else {
            context.getString(R.string.settings_sftp_exchange_password_rule)
        }

    private fun tunnelText(config: SftpExchangeConfig, tunnel: SftpTunnelState): String = when (tunnel) {
        SftpTunnelState.Off, SftpTunnelState.Connecting ->
            context.getString(R.string.settings_sftp_exchange_status_connecting)
        is SftpTunnelState.Registered -> tunnel.publicPort?.let { port ->
            val thirdParty = "${config.host}:$port"
            context.getString(R.string.settings_sftp_exchange_status_registered_port, config.endpoint(), thirdParty)
        } ?: context.getString(R.string.settings_sftp_exchange_status_registered, config.endpoint())
        SftpTunnelState.Unreachable -> context.getString(R.string.settings_sftp_exchange_status_unreachable)
        SftpTunnelState.CertificateChanged -> context.getString(R.string.settings_sftp_exchange_status_certificate)
        is SftpTunnelState.Refused -> if (tunnel.reason == REASON_BAD_PASSWORD) {
            context.getString(R.string.settings_sftp_exchange_status_bad_password)
        } else {
            context.getString(R.string.settings_sftp_exchange_status_refused, tunnel.reason)
        }
    }

    private fun save() {
        Timber.d("S4094: exchange settings save")
        val host = binding.etSftpExchangeHost.text?.toString()?.trim().orEmpty()
        val port = binding.etSftpExchangePort.text?.toString()?.toIntOrNull()
        val password = binding.etSftpExchangePassword.text?.toString().orEmpty()
        val serverValid = host.isNotEmpty() && port != null && port in 1..SftpExchangeConfig.MAX_PORT
        // An empty field keeps the stored password; a typed one must meet the floor before anything is saved.
        val passwordValid = password.isEmpty() || SftpExchangePasswordPolicy.accepts(password)
        binding.tilSftpExchangePort.error =
            if (serverValid) null else context.getString(R.string.settings_sftp_exchange_server_invalid)
        binding.tilSftpExchangePassword.error =
            if (passwordValid) null else context.getString(R.string.settings_sftp_exchange_password_rule)
        if (!serverValid || !passwordValid) return
        launchInView {
            manageSftpServer.setExchangeServer(host, requireNotNull(port))
            if (password.isNotEmpty()) manageSftpServer.setExchangePassword(password)
            binding.etSftpExchangePassword.text = null
            Toast.makeText(context, R.string.settings_sftp_exchange_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchInView(block: suspend () -> Unit): Job =
        fragment.viewLifecycleOwner.lifecycleScope.launch { block() }

    private companion object {
        // The contract section 6.3 reason a user can act on in this card; every other reason is shown as sent.
        const val REASON_BAD_PASSWORD = "bad-password"
    }
}
