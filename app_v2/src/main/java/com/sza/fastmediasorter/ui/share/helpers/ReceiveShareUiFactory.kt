package com.sza.fastmediasorter.ui.share.helpers

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.ui.dialog.FileOperationDestinationDialog
import java.io.File
import javax.inject.Inject

/**
 * S1329: owns the three dependencies `ReceiveShareActivity` only ever forwarded into a constructor, and
 * builds those two collaborators itself, so the share target declares no domain type of its own (Rule 3).
 *
 * Unscoped on purpose - what it builds is Activity-scoped and must not outlive the screen that asked.
 */
class ReceiveShareUiFactory @Inject constructor(
    private val fileOperationUseCase: FileOperationUseCase,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val authSessionRepository: AuthSessionRepository,
) {

    /**
     * The share target's destination dialog. Six of the dialog's arguments are fixed for this screen and
     * are supplied here rather than threaded through the call: a shared file is always copied, never
     * moved; there is no resource context, so the resource id is the global sentinel and there is no
     * browse path or source credential; and nothing is overwritten. Detailed errors follow the build
     * type exactly as the host set them before.
     */
    fun createDestinationDialog(
        context: Context,
        sourceFiles: List<File>,
        sourceFolderName: String,
        onComplete: (UndoOperation?) -> Unit,
        onSelectFolderClicked: (FileOperationType, List<File>, String?) -> Unit,
        onOperationRequested: (MediaResource) -> Unit,
    ): FileOperationDestinationDialog = FileOperationDestinationDialog(
        context = context,
        operationType = FileOperationType.COPY,
        sourceFiles = sourceFiles,
        sourceFolderName = sourceFolderName,
        currentResourceId = NO_RESOURCE_ID,
        currentBrowsePath = null,
        sourceCredentialsId = null,
        fileOperationUseCase = fileOperationUseCase,
        getDestinationsUseCase = getDestinationsUseCase,
        overwriteFiles = false,
        showDetailedErrors = BuildConfig.DEBUG,
        onComplete = onComplete,
        onSelectFolderClicked = onSelectFolderClicked,
        onOperationRequested = onOperationRequested,
    )

    /**
     * Resolves the repository here rather than handing it to the host - the manager takes the repository
     * itself, and passing it through would be exactly the forwarding this ticket removes.
     */
    fun createAccountSelectionManager(): AccountSelectionManager =
        AccountSelectionManager(authSessionRepository)

    private companion object {
        /** The share target browses no resource, so the dialog gets the global-list sentinel. */
        const val NO_RESOURCE_ID = -1L
    }
}
