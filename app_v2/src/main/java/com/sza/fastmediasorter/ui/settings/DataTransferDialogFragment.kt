package com.sza.fastmediasorter.ui.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogDataTransferBinding
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.model.transfer.TransferMedium
import com.sza.fastmediasorter.ui.settings.helpers.DataTransferMenuManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S1565: the compact import/export menu behind the App data card entry.
 *
 * Renders state and forwards taps - it knows no file format, no Drive verb and no validation rule.
 * The two panes are described in `dialog_data_transfer.xml`.
 *
 * It inflates its own view through [onCreateView] rather than handing one to an AlertDialog builder,
 * because `collectOnLifecycle` binds to `viewLifecycleOwner`, which a dialog-only fragment has none
 * of. The title therefore lives in the layout.
 */
@AndroidEntryPoint
class DataTransferDialogFragment : DialogFragment() {

    private val viewModel: DataTransferViewModel by viewModels()

    // The streams master toggle is a user setting, not a build capability, and the settings screen
    // already holds it in this ViewModel - the same pair GeneralSettingsObserversHelper reads.
    private val settingsViewModel: SettingsViewModel by activityViewModels()

    @Inject
    lateinit var menuManager: DataTransferMenuManager

    private var binding: DialogDataTransferBinding? = null

    /** The kind whose action pane is open, and therefore the kind any launcher result belongs to. */
    private var selectedKind: TransferDataKind? = null

    // A generic type is deliberate: the contract is built once at registration while the kind - and
    // with it the MIME type - is only known when the user taps. The suggested name carries the
    // extension, which is what the picker and the receiving device actually resolve the file by.
    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument(ANY_MIME_TYPE)) { uri ->
            val kind = selectedKind
            if (uri != null && kind != null) viewModel.export(kind, TransferMedium.DEVICE_FILE, uri)
        }

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val kind = selectedKind
            if (uri != null && kind != null) viewModel.import(kind, TransferMedium.DEVICE_FILE, uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = DialogDataTransferBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bound = binding ?: return
        bindKindRows(bound)
        bindActionButtons(bound)
        applyAvailability(bound)
        collectOnLifecycle(viewModel.state) { state -> render(bound, state) }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun applyAvailability(view: DialogDataTransferBinding) {
        val kinds = menuManager.availableKinds(settingsViewModel.settings.value.enableStreams)
        view.rowKindSettings.isVisible = TransferDataKind.SETTINGS in kinds
        view.rowKindFavorites.isVisible = TransferDataKind.FAVORITES in kinds
        view.rowKindStreams.isVisible = TransferDataKind.PINNED_STREAMS in kinds
        view.rowKindResources.isVisible = TransferDataKind.RESOURCES in kinds
        val driveOffered = TransferMedium.GOOGLE_DRIVE in menuManager.availableMedia()
        view.btnExportToDrive.isVisible = driveOffered
        view.btnImportFromDrive.isVisible = driveOffered
        // The focus chain must not point at a hidden view: with Drive gone the file buttons become
        // each other's neighbours and the Back button follows the import one directly.
        if (!driveOffered) {
            view.btnExportToFile.nextFocusDownId = view.btnImportFromFile.id
            view.btnImportFromFile.nextFocusUpId = view.btnExportToFile.id
            view.btnImportFromFile.nextFocusDownId = view.btnTransferBack.id
            view.btnTransferBack.nextFocusUpId = view.btnImportFromFile.id
        }
    }

    private fun bindKindRows(view: DialogDataTransferBinding) {
        view.rowKindSettings.setOnRowClickListener { openActions(TransferDataKind.SETTINGS) }
        view.rowKindFavorites.setOnRowClickListener { openActions(TransferDataKind.FAVORITES) }
        view.rowKindStreams.setOnRowClickListener { openActions(TransferDataKind.PINNED_STREAMS) }
        view.rowKindResources.setOnRowClickListener { openActions(TransferDataKind.RESOURCES) }
    }

    private fun bindActionButtons(view: DialogDataTransferBinding) {
        view.btnExportToFile.text = actionLabel(
            R.string.data_transfer_direction_export,
            R.string.data_transfer_medium_file
        )
        view.btnExportToDrive.text = actionLabel(
            R.string.data_transfer_direction_export,
            R.string.data_transfer_medium_drive
        )
        view.btnImportFromFile.text = actionLabel(
            R.string.data_transfer_direction_import,
            R.string.data_transfer_medium_file
        )
        view.btnImportFromDrive.text = actionLabel(
            R.string.data_transfer_direction_import,
            R.string.data_transfer_medium_drive
        )
        view.btnExportToFile.setOnClickListener {
            selectedKind?.let { createDocument.launch(it.driveFileName) }
        }
        view.btnExportToDrive.setOnClickListener {
            selectedKind?.let { viewModel.export(it, TransferMedium.GOOGLE_DRIVE, null) }
        }
        view.btnImportFromFile.setOnClickListener {
            selectedKind?.let { openDocument.launch(arrayOf(it.mimeType)) }
        }
        view.btnImportFromDrive.setOnClickListener {
            selectedKind?.let { viewModel.import(it, TransferMedium.GOOGLE_DRIVE, null) }
        }
        view.btnTransferBack.setOnClickListener { closeActions(view) }
    }

    private fun actionLabel(@StringRes directionRes: Int, @StringRes mediumRes: Int): String =
        getString(directionRes) + LABEL_SEPARATOR + getString(mediumRes)

    private fun openActions(kind: TransferDataKind) {
        val view = binding ?: return
        selectedKind = kind
        view.tvSelectedKind.setText(titleOf(kind))
        view.paneKinds.visibility = View.GONE
        view.paneActions.visibility = View.VISIBLE
    }

    private fun closeActions(view: DialogDataTransferBinding) {
        selectedKind = null
        viewModel.acknowledge()
        view.paneActions.visibility = View.GONE
        view.paneKinds.visibility = View.VISIBLE
    }

    private fun render(view: DialogDataTransferBinding, state: DataTransferUiState) {
        val running = state is DataTransferUiState.InProgress
        view.progressTransfer.visibility = if (running) View.VISIBLE else View.GONE
        val message = messageOf(state)
        view.tvTransferStatus.text = message.orEmpty()
        view.layoutTransferStatus.visibility = if (message == null) View.GONE else View.VISIBLE
        // The staged document goes straight to the shipped preview flow, which is where strategic
        // 5.1 requires favorites and resources to be answered by the user.
        if (state is DataTransferUiState.NeedsPreview) handlePreview(state.kind, state.source)
    }

    private fun messageOf(state: DataTransferUiState): String? = when (state) {
        is DataTransferUiState.Idle, is DataTransferUiState.NeedsPreview -> null
        is DataTransferUiState.InProgress ->
            getString(R.string.data_transfer_progress, getString(titleOf(state.kind)))

        is DataTransferUiState.Exported ->
            getString(R.string.data_transfer_export_done, getString(titleOf(state.kind)))

        is DataTransferUiState.Success -> getString(
            R.string.data_transfer_import_done,
            state.report.created,
            state.report.updated,
            state.report.skipped
        )

        is DataTransferUiState.Failure -> failureMessage(state)
    }

    private fun failureMessage(state: DataTransferUiState.Failure): String = when (state.reason) {
        TransferFailureReason.DRIVE_FILE_MISSING -> getString(
            R.string.data_transfer_error_no_file,
            getString(titleOf(state.kind))
        )

        TransferFailureReason.INCOMPATIBLE_FILE ->
            getString(R.string.data_transfer_error_incompatible)

        TransferFailureReason.NOTHING_TO_EXPORT -> getString(
            R.string.data_transfer_error_empty,
            getString(titleOf(state.kind))
        )

        TransferFailureReason.DEVICE_FILE_UNREADABLE ->
            getString(R.string.data_transfer_error_unreadable)

        TransferFailureReason.DEVICE_FILE_UNWRITABLE ->
            getString(R.string.data_transfer_error_unwritable)

        // DRIVE_UNAVAILABLE and TRANSFER_FAILED share one message on purpose: the cloud layer
        // reports both as a CloudResult.Error carrying only text, and the message names both the
        // connection and the missing account so either cause has a step the user can take.
        else -> getString(R.string.data_transfer_error_drive)
    }

    @StringRes
    private fun titleOf(kind: TransferDataKind): Int = when (kind) {
        TransferDataKind.SETTINGS -> R.string.data_transfer_kind_settings
        TransferDataKind.FAVORITES -> R.string.data_transfer_kind_favorites
        TransferDataKind.PINNED_STREAMS -> R.string.data_transfer_kind_streams
        TransferDataKind.RESOURCES -> R.string.data_transfer_kind_resources
    }

    private fun handlePreview(kind: TransferDataKind, source: Uri) {
        viewModel.acknowledge()
        publishStagedDocument(kind, source)
        dismiss()
    }

    /**
     * Hands the staged document to the host, which owns the shipped favorites and resource import
     * flows and their previews. The dialog deliberately does not open them itself: those flows live
     * on the settings screen and answer to its own launchers.
     */
    private fun publishStagedDocument(kind: TransferDataKind, source: Uri) {
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            Bundle().apply {
                putString(RESULT_KIND, kind.name)
                putString(RESULT_URI, source.toString())
            }
        )
    }

    companion object {
        const val TAG = "DataTransferDialogFragment"

        /** Result carrying a staged document the host must open in its own preview flow. */
        const val RESULT_KEY = "s1565_staged_transfer"
        const val RESULT_KIND = "kind"
        const val RESULT_URI = "uri"

        private const val ANY_MIME_TYPE = "*/*"
        private const val LABEL_SEPARATOR = " - "
    }
}
