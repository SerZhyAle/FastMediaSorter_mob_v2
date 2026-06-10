package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetDownloader
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import com.sza.fastmediasorter.domain.delivery.PayloadFile
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads a set's payload trying each file's ordered sources until one responds and verifies,
 * staging into a sibling `<set>.tmp` directory, then atomically promoting it to
 * `filesDir/delivery/<set>/` and flipping the install marker (S0386 strategic Pillar C, ADR-3).
 *
 * Failure is all-or-nothing: any unrecoverable source/verification failure deletes the staging
 * directory and emits [DownloadProgress.Failed] without leaving a partial payload or a marker.
 */
@Singleton
class RealDeliverableSetDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manifest: DeliveryManifestDataSource,
    private val verifier: PayloadIntegrityVerifier,
    private val markerStore: InstalledSetMarkerStore,
    private val repository: DeliverableCapabilityRepository,
    okHttpClient: OkHttpClient
) : DeliverableSetDownloader {

    // Mirror the 15 s connect/read timeouts of TesseractModelManager; no overall call timeout so
    // large payloads are not cut off mid-transfer.
    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override fun download(set: DeliverableSet): Flow<DownloadProgress> {
        if (set == DeliverableSet.TRANSLATION) {
            return downloadDfm("translate_feature")
        }

        return flow {
            emit(DownloadProgress.Queued)

            val descriptor = manifest.resolve(set)
            if (descriptor == null || descriptor.files.isEmpty()) {
                emit(DownloadProgress.Failed("no descriptor for $set"))
                return@flow
            }

            val payloadDir = markerStore.payloadDir(set)
            val stagingDir = File(payloadDir.parentFile, "${set.name}.tmp")
            stagingDir.deleteRecursively()
            if (!stagingDir.mkdirs()) {
                emit(DownloadProgress.Failed("cannot create staging dir for $set"))
                return@flow
            }

            val totalEstimate = descriptor.files.sumOf { it.minSize }.coerceAtLeast(1L)
            var completedBytes = 0L

            for (file in descriptor.files) {
                val staged = File(stagingDir, "${file.fileName}.tmp")
                val ok = downloadWithFailover(file, staged, completedBytes, totalEstimate)
                if (!ok) {
                    stagingDir.deleteRecursively()
                    emit(DownloadProgress.Failed("all sources failed for ${file.fileName}"))
                    return@flow
                }

                emit(DownloadProgress.Verifying)
                when (val result = verifier.verify(staged, file)) {
                    is PayloadIntegrityVerifier.Result.Failed -> {
                        stagingDir.deleteRecursively()
                        emit(DownloadProgress.Failed(result.reason))
                        return@flow
                    }
                    PayloadIntegrityVerifier.Result.Verified -> {
                        val finalFile = File(stagingDir, file.fileName)
                        if (!staged.renameTo(finalFile)) {
                            stagingDir.deleteRecursively()
                            emit(DownloadProgress.Failed("cannot finalize ${file.fileName}"))
                            return@flow
                        }
                        completedBytes += finalFile.length()
                    }
                }
            }

            if (!promote(stagingDir, payloadDir)) {
                stagingDir.deleteRecursively()
                emit(DownloadProgress.Failed("cannot install payload for $set"))
                return@flow
            }

            repository.markInstalled(set)
            emit(DownloadProgress.Installed)
        }.flowOn(Dispatchers.IO)
    }

    private fun downloadDfm(moduleName: String): Flow<DownloadProgress> = callbackFlow {
        val splitInstallManager = SplitInstallManagerFactory.create(context)
        
        if (splitInstallManager.installedModules.contains(moduleName)) {
            trySend(DownloadProgress.Installed)
            close()
            return@callbackFlow
        }
        
        val listener = { state: com.google.android.play.core.splitinstall.SplitInstallSessionState ->
            if (state.moduleNames().contains(moduleName)) {
                when (state.status()) {
                    com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus.PENDING -> {
                        trySend(DownloadProgress.Queued)
                    }
                    com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus.DOWNLOADING -> {
                        val total = state.totalBytesToDownload()
                        val downloaded = state.bytesDownloaded()
                        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        trySend(DownloadProgress.Running(percent, downloaded, total))
                    }
                    com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus.INSTALLING -> {
                        trySend(DownloadProgress.Verifying)
                    }
                    com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus.INSTALLED -> {
                        trySend(DownloadProgress.Installed)
                        close()
                    }
                    com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus.FAILED -> {
                        trySend(DownloadProgress.Failed("DFM install failed: code ${state.errorCode()}"))
                        close()
                    }
                    com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus.CANCELED -> {
                        trySend(DownloadProgress.Failed("DFM install canceled"))
                        close()
                    }
                }
            }
        }
        
        splitInstallManager.registerListener(listener)

        try {
            val request = com.google.android.play.core.splitinstall.SplitInstallRequest.newBuilder()
                .addModule(moduleName)
                .build()

            splitInstallManager.startInstall(request)
                .addOnFailureListener { exception ->
                    // Complete the flow with a Failed result - never close(cause), which would
                    // re-throw SplitInstallException into the collector's coroutine and crash the app.
                    // APP_NOT_OWNED (-15) is expected for sideloaded / non-Play store builds (debug):
                    // the Play dynamic-feature cannot be fetched, so translation degrades gracefully.
                    trySend(DownloadProgress.Failed(dfmFailureReason(exception)))
                    close()
                }
        } catch (t: Throwable) {
            Timber.w(t, "DFM startInstall threw synchronously for %s", moduleName)
            trySend(DownloadProgress.Failed(dfmFailureReason(t)))
            close()
        }

        awaitClose {
            splitInstallManager.unregisterListener(listener)
        }
    }

    /**
     * Human-readable reason for a Play dynamic-feature install failure. APP_NOT_OWNED (-15) means the
     * app was not acquired from Google Play (sideload / debug), so the split cannot be fetched here.
     */
    private fun dfmFailureReason(error: Throwable): String {
        val code = (error as? SplitInstallException)?.errorCode
        return when (code) {
            SplitInstallErrorCode.APP_NOT_OWNED ->
                "Translation module is delivered via Google Play and is unavailable on this build (not installed from Play)"
            null -> error.message ?: "Failed to start dynamic-feature install"
            else -> "Dynamic-feature install failed (code $code)"
        }
    }

    /** Try each source URL in order; first one that fully downloads to [dest] wins. */
    private suspend fun FlowCollector<DownloadProgress>.downloadWithFailover(
        file: PayloadFile,
        dest: File,
        baseBytes: Long,
        totalEstimate: Long
    ): Boolean {
        for (url in file.sources) {
            try {
                if (downloadOne(url, dest, baseBytes, totalEstimate)) {
                    return true
                }
                Timber.w("Delivery source returned no payload: %s", url)
            } catch (e: IOException) {
                Timber.w(e, "Delivery source failed, trying next: %s", url)
            }
            dest.delete()
        }
        return false
    }

    private suspend fun FlowCollector<DownloadProgress>.downloadOne(
        url: String,
        dest: File,
        baseBytes: Long,
        totalEstimate: Long
    ): Boolean {
        val request = Request.Builder().url(url).build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.w("Delivery source HTTP %d: %s", response.code, url)
                return false
            }
            val body = response.body ?: return false
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var fileBytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        fileBytes += read
                        val cumulative = baseBytes + fileBytes
                        val percent = ((cumulative * 100) / totalEstimate).toInt().coerceIn(0, 99)
                        emit(DownloadProgress.Running(percent, cumulative, totalEstimate))
                    }
                }
            }
        }
        return true
    }

    /** Atomically replace [payloadDir] with the verified [stagingDir]. */
    private fun promote(stagingDir: File, payloadDir: File): Boolean {
        payloadDir.deleteRecursively()
        payloadDir.parentFile?.mkdirs()
        return stagingDir.renameTo(payloadDir)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        const val BUFFER_SIZE = 8192
    }
}
