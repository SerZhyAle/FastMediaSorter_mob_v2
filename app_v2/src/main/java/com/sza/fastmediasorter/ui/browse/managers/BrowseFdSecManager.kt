package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.FdSecResult
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.usecase.LocalizeFdSecContainerUseCase
import com.sza.fastmediasorter.domain.usecase.SecureFileToFdSecUseCase
import com.sza.fastmediasorter.domain.usecase.UnsecureFdSecFileUseCase
import com.sza.fastmediasorter.domain.usecase.WriteFdSecBesideRemoteFileUseCase
import com.sza.fastmediasorter.ui.player.dispatch.StandalonePlayerDispatcherActivity
import com.sza.fastmediasorter.util.showBoundToHost
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * S3382: the Browse-side surface of the FileDO container - the two menu operations and the
 * open-a-container-as-media path.
 *
 * Every user-visible message here is the contract's, not a paraphrase: the three refusal classes
 * stay distinct, the first one names all three of its causes and chooses none, and a plain unlink
 * of an original is never described as erasure.
 */
@ActivityScoped
class BrowseFdSecManager @Inject constructor(
    @ActivityContext private val context: Context,
    private val secureFile: SecureFileToFdSecUseCase,
    private val unsecureFile: UnsecureFdSecFileUseCase,
    private val localizeContainer: LocalizeFdSecContainerUseCase,
    private val besideRemote: WriteFdSecBesideRemoteFileUseCase,
    private val passwordDialog: FdSecPasswordDialogManager,
) {

    private var copyHandedToViewer = false

    // A document id from a SAF cloud provider is opaque, so there only the display name carries the
    // extension.
    fun isContainer(file: MediaFile): Boolean =
        secureFile.isContainer(file.name) || secureFile.isContainer(file.path)

    /**
     * S3408: a file on a document tree or a network share is packed from a private copy and the
     * container travels back beside it; [currentFolder] is the folder the list shows, the fallback for a
     * document whose provider cannot name its folder.
     */
    fun encrypt(scope: CoroutineScope, file: MediaFile, currentFolder: String?, onFinished: () -> Unit) {
        passwordDialog.ask(FdSecPasswordDialogManager.Direction.ENCRYPT) { credential, _ ->
            scope.launch {
                val outcome = try {
                    if (isLocal(file)) {
                        secureFile(File(file.path), credential)
                    } else {
                        besideRemote.encrypt(file, currentFolder, credential)
                    }
                } finally {
                    credential.fill('\u0000')
                }
                report(outcome, R.string.filedo_encrypted_ok)
                onFinished()
            }
        }
    }

    fun decrypt(scope: CoroutineScope, file: MediaFile, currentFolder: String?, onFinished: () -> Unit) {
        passwordDialog.ask(FdSecPasswordDialogManager.Direction.DECRYPT) { credential, _ ->
            scope.launch {
                val outcome = try {
                    if (isLocal(file)) {
                        unsecureFile.restoreBeside(File(file.path), credential)
                    } else {
                        besideRemote.decrypt(file, currentFolder, credential)
                    }
                } finally {
                    credential.fill('\u0000')
                }
                report(outcome, R.string.filedo_decrypted_ok)
                onFinished()
            }
        }
    }

    private fun isLocal(file: MediaFile): Boolean = file.path.startsWith(LOCAL_ROOT)

    /**
     * Opens a container as an ordinary media file: password, decrypt into the app's private cache,
     * then the one-file viewer over the recovered copy. The copy never leaves the private cache. It
     * goes to the viewer alone because it is no member of the list it was tapped in, so the list's
     * own open path would report it missing.
     *
     * S3397: a remembered credential is tried first and the dialog appears only when there is none
     * or it did not fit - in which case it has already been forgotten and the owner is told so.
     */
    fun openAsMedia(scope: CoroutineScope, file: MediaFile) {
        scope.launch {
            val source = File(openWorkspace(), System.nanoTime().toString() + SOURCE_SUFFIX)
            val container = localizeContainer(file.path, source)
            if (container == null) {
                source.deleteRecursively()
                reportFailure("the container could not be read where it is")
                return@launch
            }
            val workspace = newWorkspace()
            when (val remembered = unsecureFile.materializeWithRemembered(container, workspace)) {
                null -> {
                    workspace.deleteRecursively()
                    askAndOpen(scope, container, source)
                }
                is FdSecResult.WrongCredentialOrTamper -> {
                    workspace.deleteRecursively()
                    Toast.makeText(context, R.string.filedo_remembered_password_forgotten, Toast.LENGTH_LONG).show()
                    askAndOpen(scope, container, source)
                }
                else -> {
                    source.deleteRecursively()
                    handleOpened(remembered, workspace)
                }
            }
        }
    }

    /** [source] holds the fetched copy of a container that was not a local file; it goes once read. */
    private fun askAndOpen(scope: CoroutineScope, container: File, source: File) {
        passwordDialog.ask(FdSecPasswordDialogManager.Direction.OPEN) { credential, remember ->
            scope.launch {
                val workspace = newWorkspace()
                val outcome = unsecureFile.materialize(container, workspace, credential)
                source.deleteRecursively()
                if (remember && outcome is FdSecResult.Restored) {
                    unsecureFile.rememberCredential(credential)
                }
                credential.fill('\u0000')
                handleOpened(outcome, workspace)
            }
        }
    }

    private fun newWorkspace(): File = File(openWorkspace(), System.nanoTime().toString())

    /**
     * Sweeps decrypted copies and S3408 staging copies a killed process left behind. The only backstop
     * after a power loss.
     */
    fun sweepWorkspace() {
        sweepOpenedCopies()
        besideRemote.sweepStaging()
    }

    private fun sweepOpenedCopies() {
        openWorkspace().listFiles()?.forEach { it.deleteRecursively() }
    }

    /**
     * Browse is back on screen, so the viewer a recovered copy went to has closed and the copy goes.
     * Only after a hand-off: an open still deriving its key must keep its workspace. The staging of an
     * encrypt or decrypt still running beside a remote file is not touched here.
     */
    fun dropViewedCopies() {
        if (!copyHandedToViewer) return
        copyHandedToViewer = false
        sweepOpenedCopies()
    }

    private fun handleOpened(outcome: FdSecResult, workspace: File) {
        if (outcome !is FdSecResult.Restored) {
            workspace.deleteRecursively()
            report(outcome, R.string.filedo_decrypted_ok)
            return
        }
        if (isRefusedType(outcome.file.name)) {
            // Never launch an executable or a script recovered from a container: the sealed name is
            // attacker-controlled the moment somebody else made the file.
            workspace.deleteRecursively()
            Toast.makeText(context, R.string.filedo_open_refused_executable, Toast.LENGTH_LONG).show()
            return
        }
        launchViewer(outcome.file, workspace)
    }

    private fun launchViewer(recovered: File, workspace: File) {
        val uri = try {
            FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, recovered)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "fdsec: the recovered copy is outside the shared cache")
            workspace.deleteRecursively()
            reportFailure("the recovered file could not be handed to the viewer")
            return
        }
        val intent = Intent(context, StandalonePlayerDispatcherActivity::class.java)
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        copyHandedToViewer = true
        context.startActivity(intent)
    }

    private fun openWorkspace(): File = File(context.cacheDir, WORKSPACE_DIRECTORY).apply { mkdirs() }

    private fun isRefusedType(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in REFUSED_EXTENSIONS

    private fun report(outcome: FdSecResult, successMessage: Int) {
        // The outcome class is safe to log; the credential and the sealed true name never are.
        Timber.i("fdsec: operation ended as %s", outcome::class.java.simpleName)
        val refusal = when (outcome) {
            is FdSecResult.Packed, is FdSecResult.Placed, is FdSecResult.Restored -> null
            is FdSecResult.WrongCredentialOrTamper -> context.getString(R.string.filedo_outcome_wrong_credential)
            is FdSecResult.Damaged -> context.getString(R.string.filedo_outcome_damaged)
            is FdSecResult.Unsupported -> context.getString(R.string.filedo_outcome_unsupported)
            is FdSecResult.Failed -> context.getString(R.string.filedo_outcome_failed, outcome.detail)
        }
        if (refusal == null) {
            Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show()
            return
        }
        // A toast is cut to two lines since API 31, which drops the causes the wrong-credential
        // refusal must name together; a dialog shows the whole sentence.
        MaterialAlertDialogBuilder(context)
            .setMessage(refusal)
            .setPositiveButton(R.string.ok, null)
            .showBoundToHost(context)
    }

    private fun reportFailure(detail: String) = report(FdSecResult.Failed(detail), R.string.filedo_decrypted_ok)

    private companion object {
        const val WORKSPACE_DIRECTORY = "fdsec-open"
        const val LOCAL_ROOT = "/"
        const val SOURCE_SUFFIX = "-source"
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"

        /**
         * Deliberately wider than what Android executes - the list guards what is handed to a
         * viewer, and a type this platform cannot run today may be runnable by an app installed
         * tomorrow.
         */
        val REFUSED_EXTENSIONS = setOf(
            "apk", "apks", "xapk", "dex", "jar", "so", "aar",
            "sh", "bash", "zsh", "bin", "run", "elf",
            "exe", "dll", "msi", "com", "scr", "cpl", "bat", "cmd", "ps1", "psm1", "vbs", "vbe",
            "js", "jse", "wsf", "wsh", "hta", "lnk", "reg", "scf", "url",
            "py", "pyc", "rb", "pl", "php", "jsp", "class",
        )
    }
}
