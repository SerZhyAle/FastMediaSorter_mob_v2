package com.sza.fastmediasorter.ui.exchange.helpers

import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogExchangeDirectoryBinding
import com.sza.fastmediasorter.ui.common.support.MediaExchangePortalLinks
import com.sza.fastmediasorter.ui.exchange.ExchangeDirectoryState
import com.sza.fastmediasorter.ui.exchange.ExchangeDirectoryViewModel
import com.sza.fastmediasorter.ui.exchange.MyDevicesActivity
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import timber.log.Timber

/**
 * The "From my devices" dialog of the add-resource wizard. A completed batch returns to the resource list;
 * failed or skipped attachments keep the directory open so the user can retry.
 */
class ExchangeDirectoryDialogManager(
    private val activity: AppCompatActivity,
    private val viewModel: ExchangeDirectoryViewModel,
) {

    private var dialog: AlertDialog? = null
    private var binding: DialogExchangeDirectoryBinding? = null
    private var directoryAdapter: ExchangeDirectoryAdapter? = null
    private val pinPrompt = ExchangePinPromptManager(activity, viewModel::submitPin, viewModel::skipPin)

    init {
        activity.collectOnLifecycle(viewModel.state, block = ::render)
        activity.collectOnLifecycle(viewModel.pinPrompt, block = pinPrompt::render)
        activity.collectOnLifecycle(viewModel.attachmentsCompleted) {
            Timber.d("S4402: exchange import completed; closing resource type selection")
            dialog?.dismiss()
            activity.finish()
        }
        activity.collectOnLifecycle(viewModel.messages) { message ->
            Toast.makeText(activity, MyDevicesDialogManager.resolve(activity, message), Toast.LENGTH_LONG).show()
        }
    }

    fun show() {
        if (dialog != null) return
        val view = DialogExchangeDirectoryBinding.inflate(LayoutInflater.from(activity))
        view.rvExchangeDirectory.layoutManager = LinearLayoutManager(activity)
        view.rvExchangeDirectory.adapter = ExchangeDirectoryAdapter(
            onToggle = viewModel::toggle,
            onPinNeeded = viewModel::askPin,
            driveChannelOn = viewModel.driveChannelOn.value,
        ).also { directoryAdapter = it }
        view.btnExchangeDirectoryMyDevices.setOnClickListener { MyDevicesActivity.start(activity) }
        view.btnExchangePortalDirectory.setOnClickListener {
            MediaExchangePortalLinks.open(activity, MediaExchangePortalLinks.Page.FOLDERS_EXCHANGE_SERVER)
        }
        binding = view
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.add_resource_my_devices)
            .setView(view.root)
            .setPositiveButton(R.string.exchange_directory_attach, null)
            .setNeutralButton(R.string.exchange_directory_attach_all, null)
            .setNegativeButton(R.string.close, null)
            .setOnDismissListener {
                dialog = null
                binding = null
                directoryAdapter = null
                viewModel.close()
            }
            .showBoundTo(activity)
        // Set after show(): a builder listener would dismiss the dialog before the attach has run.
        dialog?.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener { viewModel.attachSelected() }
        dialog?.getButton(DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener { viewModel.attachAll() }
        viewModel.open()
        Timber.d("S4348: directory dialog shown")
    }

    private fun render(state: ExchangeDirectoryState) {
        val view = binding ?: return
        Timber.d("S4348: directory dialog render")
        val loaded = state as? ExchangeDirectoryState.Loaded
        val status = when {
            loaded == null -> R.string.exchange_devices_list_loading
            loaded.unreachable > 0 -> R.string.exchange_directory_some_unreachable
            loaded.pending -> R.string.exchange_devices_list_loading
            loaded.rows.isEmpty() -> R.string.exchange_directory_empty
            else -> null
        }
        view.textExchangeDirectoryStatus.isVisible = status != null
        status?.let(view.textExchangeDirectoryStatus::setText)
        directoryAdapter?.submitList(loaded?.rows.orEmpty())
        val canAttach = loaded?.anyAttachable == true
        dialog?.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = canAttach
        dialog?.getButton(DialogInterface.BUTTON_NEUTRAL)?.isEnabled = canAttach
    }
}
