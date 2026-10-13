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
import com.sza.fastmediasorter.ui.exchange.ExchangeDirectoryState
import com.sza.fastmediasorter.ui.exchange.ExchangeDirectoryViewModel
import com.sza.fastmediasorter.ui.exchange.MyDevicesActivity
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle

/**
 * The "From my devices" dialog of the add-resource wizard (S4116 strategic 6.3). It stays open across attaches so
 * the user sees each resource turn "Attached"; the directory is watched only while it is shown.
 */
class ExchangeDirectoryDialogManager(
    private val activity: AppCompatActivity,
    private val viewModel: ExchangeDirectoryViewModel,
) {

    private var dialog: AlertDialog? = null
    private var binding: DialogExchangeDirectoryBinding? = null

    init {
        activity.collectOnLifecycle(viewModel.state, block = ::render)
        activity.collectOnLifecycle(viewModel.messages) { message ->
            Toast.makeText(activity, MyDevicesDialogManager.resolve(activity, message), Toast.LENGTH_LONG).show()
        }
    }

    fun show() {
        if (dialog != null) return
        val view = DialogExchangeDirectoryBinding.inflate(LayoutInflater.from(activity))
        view.rvExchangeDirectory.layoutManager = LinearLayoutManager(activity)
        view.btnExchangeDirectoryMyDevices.setOnClickListener { MyDevicesActivity.start(activity) }
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
                viewModel.close()
            }
            .showBoundTo(activity)
        // Set after show(): a builder listener would dismiss the dialog before the attach has run.
        dialog?.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener { viewModel.attachSelected() }
        dialog?.getButton(DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener { viewModel.attachAll() }
        viewModel.open()
    }

    private fun render(state: ExchangeDirectoryState) {
        val view = binding ?: return
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
        view.rvExchangeDirectory.adapter = ExchangeDirectoryAdapter(loaded?.rows.orEmpty(), viewModel::toggle)
        val canAttach = loaded?.anyAttachable == true
        dialog?.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = canAttach
        dialog?.getButton(DialogInterface.BUTTON_NEUTRAL)?.isEnabled = canAttach
    }
}
