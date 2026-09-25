package com.sza.fastmediasorter.ui.settings.helpers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.SftpServerAuthMode
import com.sza.fastmediasorter.domain.model.SftpServerConfig
import com.sza.fastmediasorter.domain.model.SftpServerFailure
import com.sza.fastmediasorter.domain.model.SftpServerState
import com.sza.fastmediasorter.domain.usecase.sftpserver.ManageSftpServerUseCase
import com.sza.fastmediasorter.service.SftpServerService
import com.sza.fastmediasorter.ui.companionimport.qr.QrCodeEncoder
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Binds the "Share over SFTP" card on the General settings tab: the on/off toggle, the shared-folder
 * list, the port, the login mode and the running status with the credentials a client needs.
 *
 * The toggle mirrors the server's real state rather than a stored wish, so a server the system
 * stopped - at the data-sync daily limit, or with the app swiped away - reads as off here too.
 */
@Suppress("TooManyFunctions")
class SftpServerSettingsPanelManager(
    private val fragment: Fragment,
    private val binding: FragmentSettingsGeneralBinding,
    private val manageSftpServer: ManageSftpServerUseCase,
    private val capabilityAvailability: CapabilityAvailability,
    private val mediaCapabilities: MediaCapabilities,
    private val launchRootPicker: (Intent) -> Unit,
) {

    private val context get() = fragment.requireContext()
    private var lastConfig: SftpServerConfig? = null

    /** Hides the card when the server cannot run here; returns whether the card is shown. */
    fun bind(): Boolean {
        val available = capabilityAvailability.isSftpServerAvailable(mediaCapabilities)
        binding.cardSftpServer.isVisible = available
        binding.headerSftpServer.isVisible = available
        if (!available) return false
        bindControls()
        fragment.collectOnLifecycle(
            combine(manageSftpServer.config, manageSftpServer.state, ::Pair)
        ) { (config, state) ->
            render(config, state)
        }
        return true
    }

    /** The system folder picker, asking for a grant that survives a restart. */
    fun buildRootPickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
    )

    fun onRootPickerResult(resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // Without a persistable grant the folder would vanish from the server at the next restart.
            Timber.w(e, "SftpServerSettingsPanelManager: folder grant not persistable")
            Toast.makeText(context, R.string.settings_sftp_server_failure_no_folders, Toast.LENGTH_LONG).show()
            return
        }
        // Roots are read per login, so a running server serves the new folder without a restart.
        launchInView { manageSftpServer.addRoot(uri.toString()) }
    }

    private fun bindControls() {
        binding.rowSftpServerEnabled.setOnCheckedChangeListener(::onToggle)
        binding.btnSftpServerAddRoot.setOnClickListener { launchRootPicker(buildRootPickerIntent()) }
        binding.etSftpServerPort.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) savePort()
            false
        }
        binding.etSftpServerPort.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) savePort() }
        binding.radioSftpServerAuth.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.radioSftpServerAuthKey) {
                SftpServerAuthMode.PUBLIC_KEY
            } else {
                SftpServerAuthMode.PASSWORD
            }
            if (mode != lastConfig?.authMode) {
                launchInView { manageSftpServer.setAuthMode(mode) }
                notifyApplyIfRunning()
            }
        }
        binding.etSftpServerAuthorizedKeys.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveAuthorizedKeys()
        }
        binding.btnSftpServerShowQr.setOnClickListener { toggleQr() }
        binding.btnSftpServerNewPassword.setOnClickListener {
            launchInView { manageSftpServer.regeneratePassword() }
            notifyApplyIfRunning()
        }
    }

    /**
     * A tap flips what the card shows, so the command comes from the server's state, not from the
     * switch's own checked value: a switch drawn off while internally checked turned every tap on an
     * "Off" card into a stop (S3532).
     */
    private fun onToggle(checked: Boolean) {
        Timber.d("S3041: server toggle %s", checked)
        val state = manageSftpServer.state.value
        val start = !state.isActive()
        if (checked != start) {
            Timber.w("SftpServerSettingsPanelManager: switch read %s against %s; following the server", checked, state)
            binding.rowSftpServerEnabled.setCheckedSilently(start)
        }
        launchInView { manageSftpServer.setEnabled(start) }
        if (start) SftpServerService.start(context) else SftpServerService.stop(context)
    }

    private fun SftpServerState.isActive(): Boolean =
        this is SftpServerState.Running || this is SftpServerState.Starting

    private fun render(config: SftpServerConfig, state: SftpServerState) {
        lastConfig = config
        binding.rowSftpServerEnabled.setCheckedSilently(state.isActive())
        binding.textSftpServerStatus.text = statusText(config, state)
        renderCredentials(state)
        renderQrAvailability(state)
        renderRoots(config.rootUris)
        renderAuth(config)
        if (!binding.etSftpServerPort.hasFocus()) binding.etSftpServerPort.setText(config.port.toString())
    }

    private fun statusText(config: SftpServerConfig, state: SftpServerState): String = when (state) {
        SftpServerState.Stopped -> context.getString(R.string.settings_sftp_server_status_off)
        SftpServerState.Starting -> context.getString(R.string.settings_sftp_server_status_starting)
        is SftpServerState.Running -> state.addresses.firstOrNull()
            ?.let { context.getString(R.string.settings_sftp_server_status_running, "$it:${state.port}") }
            ?: context.getString(R.string.settings_sftp_server_status_no_network)
        is SftpServerState.Failed -> failureText(state.reason, config.port)
    }

    private fun failureText(reason: SftpServerFailure, port: Int): String = when (reason) {
        SftpServerFailure.UNAVAILABLE -> context.getString(R.string.settings_sftp_server_failure_unavailable)
        SftpServerFailure.NO_SHARED_FOLDERS -> context.getString(R.string.settings_sftp_server_failure_no_folders)
        SftpServerFailure.NO_CREDENTIAL -> context.getString(R.string.settings_sftp_server_failure_no_credential)
        SftpServerFailure.PORT_IN_USE -> context.getString(R.string.settings_sftp_server_failure_port, port)
        SftpServerFailure.START_FAILED -> context.getString(R.string.settings_sftp_server_failure_generic)
    }

    private fun renderCredentials(state: SftpServerState) {
        val running = state is SftpServerState.Running
        binding.textSftpServerCredentials.isVisible = running
        if (!running) return
        launchInView {
            val credentials = manageSftpServer.clientCredentials()
            binding.textSftpServerCredentials.text = when (credentials.password) {
                null -> context.getString(R.string.settings_sftp_server_credentials_key, credentials.username)
                else -> context.getString(
                    R.string.settings_sftp_server_credentials_password,
                    credentials.username,
                    credentials.password,
                )
            }
        }
    }

    private fun renderQrAvailability(state: SftpServerState) {
        val running = state is SftpServerState.Running
        binding.btnSftpServerShowQr.isVisible = running
        if (!running) hideQr()
    }

    /** The code is rebuilt on every show, so it always carries the current address and password. */
    private fun toggleQr() {
        Timber.d("S3041: pairing code toggled")
        if (binding.imageSftpServerQr.isVisible) {
            hideQr()
            return
        }
        launchInView {
            val code = manageSftpServer.pairingCode() ?: return@launchInView
            val sizePx = context.resources.getDimensionPixelSize(R.dimen.sftp_server_qr_size)
            binding.imageSftpServerQr.setImageBitmap(QrCodeEncoder.encode(code, sizePx))
            binding.imageSftpServerQr.isVisible = true
            binding.btnSftpServerShowQr.setText(R.string.settings_sftp_server_hide_qr)
        }
    }

    private fun hideQr() {
        binding.imageSftpServerQr.isVisible = false
        binding.imageSftpServerQr.setImageDrawable(null)
        binding.btnSftpServerShowQr.setText(R.string.settings_sftp_server_show_qr)
    }
    private fun renderAuth(config: SftpServerConfig) {
        val keyMode = config.authMode == SftpServerAuthMode.PUBLIC_KEY
        val checkedId = if (keyMode) R.id.radioSftpServerAuthKey else R.id.radioSftpServerAuthPassword
        if (binding.radioSftpServerAuth.checkedRadioButtonId != checkedId) {
            binding.radioSftpServerAuth.check(checkedId)
        }
        binding.tilSftpServerAuthorizedKeys.isVisible = keyMode
        binding.btnSftpServerNewPassword.isVisible = !keyMode
        if (!binding.etSftpServerAuthorizedKeys.hasFocus()) {
            binding.etSftpServerAuthorizedKeys.setText(config.authorizedKeys.joinToString("\n"))
        }
    }

    private fun renderRoots(rootUris: List<String>) {
        val container = binding.containerSftpServerRoots
        container.removeAllViews()
        if (rootUris.isEmpty()) {
            container.addView(TextView(context).apply { setText(R.string.settings_sftp_server_roots_empty) })
            return
        }
        rootUris.forEach { container.addView(rootRow(it)) }
    }

    private fun rootRow(treeUri: String): LinearLayout {
        val uri = Uri.parse(treeUri)
        val name = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply { text = name },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                ImageButton(context).apply {
                    setImageResource(R.drawable.ic_remove_circle_outline)
                    setBackgroundResource(android.R.color.transparent)
                    contentDescription = context.getString(R.string.settings_sftp_server_remove_root, name)
                    isFocusable = true
                    setOnClickListener { launchInView { manageSftpServer.removeRoot(treeUri) } }
                },
            )
        }
    }

    private fun savePort() {
        val port = binding.etSftpServerPort.text?.toString()?.toIntOrNull()
        val valid = port != null && port in SftpServerConfig.MIN_PORT..SftpServerConfig.MAX_PORT
        binding.tilSftpServerPort.error =
            if (valid) null else context.getString(R.string.settings_sftp_server_port_invalid)
        if (!valid || port == lastConfig?.port) return
        launchInView { manageSftpServer.setPort(requireNotNull(port)) }
        notifyApplyIfRunning()
    }

    private fun saveAuthorizedKeys() {
        val lines = binding.etSftpServerAuthorizedKeys.text?.toString().orEmpty().lines()
        if (lines.map(String::trim).filter(String::isNotEmpty) == lastConfig?.authorizedKeys) return
        launchInView { manageSftpServer.setAuthorizedKeys(lines) }
        notifyApplyIfRunning()
    }

    /** A running server keeps the settings it started with; say so instead of restarting under a client. */
    private fun notifyApplyIfRunning() {
        if (manageSftpServer.state.value is SftpServerState.Running) {
            Toast.makeText(context, R.string.settings_sftp_server_apply_hint, Toast.LENGTH_LONG).show()
        }
    }

    private fun launchInView(block: suspend () -> Unit) {
        fragment.viewLifecycleOwner.lifecycleScope.launch { block() }
    }
}
