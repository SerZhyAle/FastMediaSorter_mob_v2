package com.sza.fastmediasorter.ui.browse.managers

import android.util.LruCache
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.MainDispatcher
import com.sza.fastmediasorter.domain.model.MediaFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VrApkClassificationCache @Inject constructor(
    private val archiveResolver: VrApkArchiveResolver,
    private val classifier: VrApkClassifier,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {

    fun peek(mediaFile: MediaFile): VrApkClassification? {
        val key = CacheKey.from(mediaFile) ?: return null
        synchronized(memoryCache) {
            return memoryCache.get(key)
        }
    }

    fun requestClassification(
        mediaFile: MediaFile,
        onResult: (VrApkClassification) -> Unit,
    ) {
        val key = CacheKey.from(mediaFile)
        if (key == null) {
            onResult(VrApkClassification.NOT_VR)
            return
        }

        peek(mediaFile)?.let {
            onResult(it)
            return
        }

        if (diskFullThisSession) {
            // Cache volume filled earlier this session: skip the download entirely and degrade.
            // This is the session-wide stop that ends the repeated-error cascade; the flag lives in
            // memory only, so classification resumes after a process restart frees the volume.
            onResult(VrApkClassification.NOT_VR)
            return
        }

        applicationScope.launch {
            var cached: VrApkClassification? = null
            val deferred = inFlightMutex.withLock {
                synchronized(memoryCache) {
                    memoryCache.get(key)
                }?.also { resolved ->
                    cached = resolved
                }

                if (cached != null) {
                    null
                } else {
                    inFlight[key] ?: applicationScope.async {
                        concurrencyLimiter.withPermit {
                            classifyForCache(mediaFile)
                        }
                    }.also { created ->
                        inFlight[key] = created
                    }
                }
            }

            cached?.let {
                withContext(mainDispatcher) {
                    onResult(it)
                }
                return@launch
            }

            val result = deferred?.await() ?: return@launch
            inFlightMutex.withLock {
                inFlight.remove(key, deferred)
            }
            if (result.cacheable) {
                synchronized(memoryCache) {
                    memoryCache.put(key, result.classification)
                }
            }
            withContext(mainDispatcher) {
                onResult(result.classification)
            }
        }
    }

    private suspend fun classifyForCache(mediaFile: MediaFile): ClassificationTaskResult {
        return try {
            when (val resolution = archiveResolver.resolve(mediaFile)) {
                is VrArchiveResolution.Available -> try {
                    ClassificationTaskResult(
                        classification = classifier.classify(resolution.file),
                        cacheable = true,
                    )
                } finally {
                    // Release the transient copy as soon as the manifest is read; it never persists.
                    resolution.cleanup()
                }

                VrArchiveResolution.OutOfSpace -> {
                    diskFullThisSession = true
                    Timber.w("classifyForCache: cache volume full, stopping VR APK classification for this session")
                    ClassificationTaskResult(
                        classification = VrApkClassification.NOT_VR,
                        cacheable = false,
                    )
                }

                VrArchiveResolution.Unavailable -> ClassificationTaskResult(
                    classification = VrApkClassification.NOT_VR,
                    cacheable = false,
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "classifyForCache: APK classification failed; degrading to NOT_VR")
            ClassificationTaskResult(
                classification = VrApkClassification.NOT_VR,
                cacheable = false,
            )
        }
    }

    private data class ClassificationTaskResult(
        val classification: VrApkClassification,
        val cacheable: Boolean,
    )

    private data class CacheKey(
        val path: String,
        val size: Long,
        val lastModified: Long,
    ) {
        companion object {
            fun from(mediaFile: MediaFile): CacheKey? {
                if (mediaFile.path.isBlank()) return null
                // Browse scanners already normalize lastModified to zero when unavailable, which
                // keeps the cache key stable while still invalidating on real mtime changes.
                return CacheKey(
                    path = mediaFile.path,
                    size = mediaFile.size,
                    lastModified = mediaFile.lastModified,
                )
            }
        }
    }

    private val memoryCache = object : LruCache<CacheKey, VrApkClassification>(CACHE_CAPACITY) {}
    private val inFlight = mutableMapOf<CacheKey, Deferred<ClassificationTaskResult>>()
    private val inFlightMutex = Mutex()
    private val concurrencyLimiter = Semaphore(MAX_CONCURRENT_CLASSIFICATIONS)

    // S0388: once the cache volume fills, stop classifying for the rest of the session so the same
    // failure is not retried (and re-logged) on every list re-bind. Resets on process restart.
    @Volatile
    private var diskFullThisSession = false

    private companion object {
        private const val CACHE_CAPACITY = 256
        private const val MAX_CONCURRENT_CLASSIFICATIONS = 4
    }
}