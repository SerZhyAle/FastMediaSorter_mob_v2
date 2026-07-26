package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.core.capability.InstallSourceProvider
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetDownloader
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import com.sza.fastmediasorter.domain.delivery.PayloadFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    private val installSourceProvider: InstallSourceProvider,
    private val bundledSets: BundledDeliverableSets,
    okHttpClient: OkHttpClient
) : DeliverableSetDownloader {

    // Mirror the 15 s connect/read timeouts of TesseractModelManager; no overall call timeout so
    // large payloads are not cut off mid-transfer.
    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override fun download(set: DeliverableSet): Flow<DownloadProgress> {
        // S0971: a bundled set (OCR/DTS/translation `.so` shipped in the APK) has nothing to download -
        // report it installed immediately so enqueue-and-enable flows complete without any network call
        // (and never hit the Play `.so`-download ban).
        if (bundledSets.contains(set)) {
            return flowOf(DownloadProgress.Queued, DownloadProgress.Installed)
        }

        // S0423: TRANSLATION is bundled (the on-demand DFM was removed), so it is never offered for
        // download. If it ever reaches here it degrades gracefully to "no descriptor" below.

        // Play policy (Device & Network Abuse) forbids fetching executable code (.so) from a non-Play
        // source. On a Play install of a store flavor the native sets must not hit the GitHub mirror;
        // the direct download stays only for non-Play contexts (sideload/debug/noLegal), which keeps
        // the existing failover path unchanged there (S0401 §6.1). Data payloads (Set C .mp4, OCR
        // traineddata) are not code and keep direct download everywhere, so the gate is .so-only.
        if (set.isNativeCodeSet() && installSourceProvider.isPlayInstall()) {
            return downloadNativeSetOnPlay(set)
        }

        return downloadFromSources(set)
    }

    /**
     * Play-compliant branch for a native (`.so`-bearing) set on a Play install. The store flavors
     * (standard/legacy) de-bundle these sets (S0386 Phase 05) and the build strips the `.so` from
     * every base artifact, so a Play install does not carry them and they cannot be re-fetched without
     * violating policy. Report a human-readable unavailable result with no network call. This covers
     * only the de-bundled native sets (OCR/DTS); translation is bundled (S0423) and never reaches here.
     */
    private fun downloadNativeSetOnPlay(set: DeliverableSet): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Queued)
        Timber.i("Native set %s is delivered via Google Play and is unavailable on this install", set)
        emit(DownloadProgress.Failed("This module is delivered via Google Play and is unavailable on this build"))
    }

    private fun downloadFromSources(set: DeliverableSet): Flow<DownloadProgress> {
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

            // `finally` deletes the staging dir on ANY non-promoted exit - failure, early return, or
            // coroutine cancellation (collector closed mid-download) - so a cancelled download never
            // leaves an orphaned `<set>.tmp` in filesDir. After a successful promote the staging dir
            // is gone (renamed), so `promoted` skips the cleanup.
            var promoted = false
            try {
                val totalEstimate = descriptor.files.sumOf { it.minSize }.coerceAtLeast(1L)
                var completedBytes = 0L

                for (file in descriptor.files) {
                    val staged = File(stagingDir, "${file.fileName}.tmp")
                    val ok = downloadWithFailover(file, staged, completedBytes, totalEstimate)
                    if (!ok) {
                        emit(DownloadProgress.Failed("all sources failed for ${file.fileName}"))
                        return@flow
                    }

                    emit(DownloadProgress.Verifying)
                    when (val result = verifier.verify(staged, file)) {
                        is PayloadIntegrityVerifier.Result.Failed -> {
                            emit(DownloadProgress.Failed(result.reason))
                            return@flow
                        }
                        PayloadIntegrityVerifier.Result.Verified -> {
                            val finalFile = File(stagingDir, file.fileName)
                            if (!staged.renameTo(finalFile)) {
                                emit(DownloadProgress.Failed("cannot finalize ${file.fileName}"))
                                return@flow
                            }
                            completedBytes += finalFile.length()
                        }
                    }
                }

                if (!promote(stagingDir, payloadDir)) {
                    emit(DownloadProgress.Failed("cannot install payload for $set"))
                    return@flow
                }
                promoted = true

                repository.markInstalled(set, descriptor.stamp)
                emit(DownloadProgress.Installed)
            } finally {
                if (!promoted) stagingDir.deleteRecursively()
            }
        }.flowOn(Dispatchers.IO)
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

    /**
     * Replace [payloadDir] with the verified [stagingDir] without destroying the prior payload until
     * the new one is in place: move the old payload aside to a `.bak`, rename staging in, then drop
     * the backup. If the final rename fails, restore the backup so a re-download keeps the previous
     * working payload instead of being left with nothing. Renames within `filesDir` are atomic.
     */
    private fun promote(stagingDir: File, payloadDir: File): Boolean {
        payloadDir.parentFile?.mkdirs()
        val backup = File(payloadDir.parentFile, "${payloadDir.name}.bak")
        backup.deleteRecursively()

        val movedOldAside = payloadDir.exists() && payloadDir.renameTo(backup)
        // If the old payload existed but could not be moved aside, remove it in place (best effort).
        if (payloadDir.exists()) payloadDir.deleteRecursively()

        if (stagingDir.renameTo(payloadDir)) {
            backup.deleteRecursively()
            return true
        }
        // Promotion failed - restore the previous payload if we had set it aside.
        if (movedOldAside) backup.renameTo(payloadDir)
        return false
    }

    // The sets whose payload is executable native code (`.so`); only these are gated on install
    // source. AUDIO_VISUALIZATIONS (.mp4) and OCR language data (.traineddata) are pure data and are
    // never gated. TRANSLATION is bundled (S0423), so it is never offered for download.
    private fun DeliverableSet.isNativeCodeSet(): Boolean =
        this == DeliverableSet.OCR_ENGINES || this == DeliverableSet.FFMPEG_DTS

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        const val BUFFER_SIZE = 8192
    }
}
