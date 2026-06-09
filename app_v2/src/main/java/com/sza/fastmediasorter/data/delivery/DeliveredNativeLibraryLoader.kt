package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetContributor
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeliveredNativeLibraryLoader @Inject constructor(
    private val context: Context,
    private val verifier: PayloadIntegrityVerifier,
    private val bundledSets: BundledDeliverableSets,
    private val contributors: Set<@JvmSuppressWildcards DeliverableSetContributor>
) {
    private val loadedSets = mutableSetOf<DeliverableSet>()

    @Synchronized
    fun load(set: DeliverableSet) {
        if (loadedSets.contains(set)) return

        if (bundledSets.contains(set)) {
            Timber.d("DeliveredNativeLibraryLoader: Set %s is bundled in base, skipping filesDir load", set)
            loadedSets.add(set)
            return
        }

        Timber.d("DeliveredNativeLibraryLoader: Loading native libraries for set $set")
        val descriptors = buildMap { contributors.forEach { putAll(it.descriptors()) } }
        val descriptor = descriptors[set] ?: throw IOException("No descriptor found for set $set")

        val setDir = File(context.filesDir, "delivery/${set.name}")
        if (!setDir.isDirectory) {
            throw IOException("Set directory for $set is missing: ${setDir.absolutePath}")
        }

        // Verify all files first
        for (payloadFile in descriptor.files) {
            val file = File(setDir, payloadFile.fileName)
            val result = verifier.verify(file, payloadFile)
            if (result is PayloadIntegrityVerifier.Result.Failed) {
                throw IOException("Integrity check failed for file ${payloadFile.fileName} in set $set: ${result.reason}")
            }
        }

        // Load files in descriptor order
        for (payloadFile in descriptor.files) {
            if (payloadFile.fileName.endsWith(".so")) {
                val file = File(setDir, payloadFile.fileName)
                try {
                    Timber.d("DeliveredNativeLibraryLoader: System.load(${file.absolutePath})")
                    System.load(file.absolutePath)
                } catch (e: UnsatisfiedLinkError) {
                    Timber.e(e, "UnsatisfiedLinkError loading library: ${file.absolutePath}")
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error loading library: ${file.absolutePath}")
                    throw IOException("Error loading library: ${file.absolutePath}", e)
                }
            }
        }

        loadedSets.add(set)
        Timber.i("DeliveredNativeLibraryLoader: Successfully loaded native libraries for set $set")
    }
}
