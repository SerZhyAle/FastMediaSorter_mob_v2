package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.domain.model.SftpServerState
import com.sza.fastmediasorter.domain.model.SftpTunnelState
import com.sza.fastmediasorter.domain.usecase.sftpserver.ExchangeProducerLeave
import com.sza.fastmediasorter.domain.usecase.sftpserver.ExchangePublishStart
import com.sza.fastmediasorter.domain.usecase.sftpserver.ManageExchangeProducerUseCase
import com.sza.fastmediasorter.domain.usecase.sftpserver.ManageSftpServerUseCase
import com.sza.fastmediasorter.ui.common.support.MediaExchangePortalLinks
import com.sza.fastmediasorter.ui.exchange.MyDevicesActivity
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeMessageText
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeServerCardAdapter
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Binds the "Access from anywhere" part of the SFTP server card: the opt-in that lets the share's
 * address and password reach the user's Google Drive (DEVICE-EXCHANGE section 15 item X), the publish
 * switch that puts the share in the directory of the outgoing exchange entry (item AC), the share link and
 * the publication status.
 *
 * The exchange account entries themselves live on the "My devices" screen; this card only names the outgoing
 * one, lets the user change it, and opens the picker when publishing starts with none chosen.
 */
class SftpExchangeSettingsPanelManager(
    private val fragment: Fragment,
    private val binding: FragmentSettingsGeneralBinding,
    private val manageSftpServer: ManageSftpServerUseCase,
    private val producer: ManageExchangeProducerUseCase,
) {

    private val context get() = fragment.requireContext()

    // The address the status line names; the tunnel state alone does not carry it.
    private var outgoingAddress: String? = null

    fun bind() {
        binding.rowSftpDriveAccess.setOnCheckedChangeListener { checked ->
            launchInView { manageSftpServer.setDriveAccessShared(checked) }
        }
        fragment.collectOnLifecycle(manageSftpServer.driveAccessShared) { shared ->
            binding.rowSftpDriveAccess.setCheckedSilently(shared)
        }
        binding.rowSftpExchangeEnabled.setOnCheckedChangeListener { checked ->
            launchInView { if (checked) startPublishing() else leave() }
        }
        binding.btnSftpExchangeChangeOutgoing.setOnClickListener { launchInView { pickOutgoing(publishAfter = false) } }
        binding.btnSftpExchangeMyDevices.setOnClickListener { MyDevicesActivity.start(context) }
        binding.btnSftpExchangePortal.setOnClickListener {
            MediaExchangePortalLinks.open(fragment.requireActivity(), MediaExchangePortalLinks.Page.HOME_NETWORK)
        }
        binding.btnSftpExchangeNewLink.setOnClickListener {
            launchInView {
                manageSftpServer.rotateShareId()
                Toast.makeText(context, R.string.settings_sftp_exchange_new_link_done, Toast.LENGTH_LONG).show()
            }
        }
        fragment.collectOnLifecycle(
            combine(manageSftpServer.tunnelState, manageSftpServer.state, ::Pair)
        ) { (tunnel, server) -> renderStatus(tunnel, server) }
        fragment.collectOnLifecycle(producer.publishing) { publishing ->
            binding.rowSftpExchangeEnabled.setCheckedSilently(publishing)
            binding.containerSftpExchangeFields.isVisible = publishing
            renderStatus(manageSftpServer.tunnelState.value, manageSftpServer.state.value)
        }
        fragment.collectOnLifecycle(producer.outgoing) { outgoing ->
            outgoingAddress = outgoing?.let(ExchangeServerCardAdapter::address)
            binding.textSftpExchangeOutgoing.text = outgoingAddress?.let {
                context.getString(R.string.settings_sftp_exchange_outgoing, it)
            } ?: context.getString(R.string.settings_sftp_exchange_outgoing_none)
            renderStatus(manageSftpServer.tunnelState.value, manageSftpServer.state.value)
        }
    }

    private suspend fun startPublishing() {
        if (producer.startPublishing() == ExchangePublishStart.NEEDS_OUTGOING) {
            binding.rowSftpExchangeEnabled.setCheckedSilently(false)
            pickOutgoing(publishAfter = true)
        }
    }

    private suspend fun leave() {
        if (producer.leave() == ExchangeProducerLeave.LEFT_LOCALLY) {
            Toast.makeText(context, R.string.exchange_devices_left_locally, Toast.LENGTH_LONG).show()
        }
    }

    /** One choice among the held entries; with none held, the "My devices" screen is where one is added. */
    private suspend fun pickOutgoing(publishAfter: Boolean) {
        val entries = producer.entries.first()
        if (entries.isEmpty()) {
            Toast.makeText(context, R.string.exchange_devices_pick_none, Toast.LENGTH_LONG).show()
            MyDevicesActivity.start(context)
            return
        }
        val labels = entries.map(ExchangeServerCardAdapter::address).toTypedArray()
        val checked = entries.indexOfFirst(DeviceExchangeAccountEntry::outgoing)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.exchange_devices_pick_outgoing_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                launchInView { choose(entries[which], publishAfter) }
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(fragment.viewLifecycleOwner)
    }

    private suspend fun choose(entry: DeviceExchangeAccountEntry, publishAfter: Boolean) {
        if (producer.makeOutgoing(entry) == ExchangeProducerLeave.LEFT_LOCALLY) {
            Toast.makeText(context, R.string.exchange_devices_left_locally, Toast.LENGTH_LONG).show()
        }
        if (publishAfter) producer.startPublishing()
    }

    private fun renderStatus(tunnel: SftpTunnelState, server: SftpServerState) {
        binding.textSftpExchangeStatus.text = statusText(tunnel, server)
        // Only a code that carries the tunnel needs a legacy twin; a LAN-only code is already FMSSFTP1.
        binding.btnSftpServerLegacyQr.isVisible =
            server is SftpServerState.Running && binding.rowSftpExchangeEnabled.isChecked && outgoingAddress != null
    }

    private fun statusText(tunnel: SftpTunnelState, server: SftpServerState): String = when {
        !binding.rowSftpExchangeEnabled.isChecked -> context.getString(R.string.settings_sftp_exchange_status_off)
        server !is SftpServerState.Running -> context.getString(R.string.settings_sftp_exchange_status_waiting)
        else -> tunnelText(tunnel)
    }

    private fun tunnelText(tunnel: SftpTunnelState): String = when (tunnel) {
        SftpTunnelState.Off, SftpTunnelState.Connecting ->
            context.getString(R.string.settings_sftp_exchange_status_connecting)
        is SftpTunnelState.Registered -> registeredText(tunnel)
        SftpTunnelState.Unreachable -> ExchangeMessageText.twoParts(context, ExchangeMessage.UNREACHABLE_SERVER)
        SftpTunnelState.CertificateChanged ->
            ExchangeMessageText.twoParts(context, ExchangeMessage.CERTIFICATE_CHANGED)
        is SftpTunnelState.Refused ->
            ExchangeMessageText.twoParts(context, ExchangeMessage.ofWire(tunnel.reason))
    }

    private fun registeredText(tunnel: SftpTunnelState.Registered): String {
        val address = outgoingAddress
            ?: return context.getString(R.string.settings_sftp_exchange_status_connecting)
        val host = address.substringBeforeLast(':')
        return tunnel.publicPort?.let { port ->
            context.getString(R.string.settings_sftp_exchange_status_registered_port, address, "$host:$port")
        } ?: context.getString(R.string.settings_sftp_exchange_status_registered, address)
    }

    private fun launchInView(block: suspend () -> Unit): Job =
        fragment.viewLifecycleOwner.lifecycleScope.launch { block() }
}
