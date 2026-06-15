package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetContributor
import dalvik.system.BaseDexClassLoader
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches a delivered native set (S0386 Phase 07). On first use of a set it re-verifies every
 * payload file against its app-pinned SHA-256/size ([PayloadIntegrityVerifier], ADR-3) and only then
 * makes the `.so` loadable.
 *
 * Why path injection and not a bare `System.load`: the de-bundled engines load their own libraries by
 * name from a static initializer that we cannot edit - `TessBaseAPI` runs
 * `System.loadLibrary("jpeg"/"pngx"/"leptonica"/"tesseract")`, `PaddleLiteInitializer` runs
 * `System.loadLibrary("paddle_lite_jni")`, and media3's `FfmpegLibrary` loads `ffmpegJNI` through its
 * own `LibraryLoader`. `System.load(absolutePath)` does not satisfy a later `System.loadLibrary(name)`
 * because the latter resolves names against the classloader's native search path, which does not
 * include `filesDir`. So we splice the delivered directory into that search path (the SoLoader /
 * ReLinker technique) before the wrapper's own loader runs, then warm-load each `.so` to fail fast.
 *
 * Scope: self-downloaded first-party / OSS `.so` only - Tesseract (+ leptonica/jpeg/pngx), PaddleOCR,
 * FFmpeg DTS, and ML Kit Translate (fallback delivery for store flavors).
 */
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
            Timber.d("DeliveredNativeLibraryLoader: set %s is bundled in base, no filesDir attach needed", set)
            loadedSets.add(set)
            return
        }

        val descriptors = buildMap { contributors.forEach { putAll(it.descriptors()) } }
        val descriptor = descriptors[set] ?: throw IOException("No descriptor found for set $set")

        val setDir = File(context.filesDir, "delivery/${set.name}")
        if (!setDir.isDirectory) {
            throw IOException("Set directory for $set is missing: ${setDir.absolutePath}")
        }

        // ADR-3: verify all payload files before any code becomes loadable.
        for (payloadFile in descriptor.files) {
            val file = File(setDir, payloadFile.fileName)
            val result = verifier.verify(file, payloadFile)
            if (result is PayloadIntegrityVerifier.Result.Failed) {
                throw IOException("Integrity check failed for ${payloadFile.fileName} in set $set: ${result.reason}")
            }
        }

        val soFiles = descriptor.files.filter { it.fileName.endsWith(".so") }
        if (soFiles.isEmpty()) {
            // Pure-resource set (e.g. Set C videos): nothing to load into the process.
            loadedSets.add(set)
            return
        }

        // Splice the delivered directory into the classloader's native library search path so the
        // engines' own `System.loadLibrary(name)` calls resolve from filesDir.
        injectNativeLibraryDirectory(setDir)

        // Warm-load each `.so` in descriptor (dependency-first) order to surface a broken payload as
        // an IOException here rather than deep inside the engine's static initializer.
        for (payloadFile in soFiles) {
            val file = File(setDir, payloadFile.fileName)
            try {
                System.load(file.absolutePath)
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "UnsatisfiedLinkError loading %s", file.absolutePath)
                throw e
            } catch (e: Exception) {
                throw IOException("Error loading library: ${file.absolutePath}", e)
            }
        }

        loadedSets.add(set)
        Timber.i("DeliveredNativeLibraryLoader: attached native set %s from %s", set, setDir.absolutePath)
    }

    /**
     * Adds [dir] to the front of the running classloader's native library directories and rebuilds
     * its path elements, so subsequent `System.loadLibrary(name)` finds delivered `.so` by soname.
     * Reflection over `DexPathList` internals; the `makePathElements` signature differs between
     * API 23-25 (3-arg static) and API 26+ (1-arg), so both are attempted.
     */
    private fun injectNativeLibraryDirectory(dir: File) {
        val classLoader = javaClass.classLoader
        if (classLoader !is BaseDexClassLoader) {
            throw IOException("Unexpected classloader ${classLoader?.javaClass?.name}; cannot attach delivered libraries")
        }
        try {
            val pathListField = BaseDexClassLoader::class.java.getDeclaredField("pathList").apply { isAccessible = true }
            val dexPathList = pathListField.get(classLoader)
                ?: throw IOException("DexPathList is null")

            val nativeLibDirsField = dexPathList.javaClass
                .getDeclaredField("nativeLibraryDirectories").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val nativeLibDirs = nativeLibDirsField.get(dexPathList) as MutableList<File>
            if (nativeLibDirs.none { it.absolutePath == dir.absolutePath }) {
                nativeLibDirs.add(0, dir)
            }

            val systemNativeLibDirsField = dexPathList.javaClass
                .getDeclaredField("systemNativeLibraryDirectories").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val systemNativeLibDirs = systemNativeLibDirsField.get(dexPathList) as List<File>

            val allDirs = ArrayList<File>(nativeLibDirs.size + systemNativeLibDirs.size).apply {
                addAll(nativeLibDirs)
                addAll(systemNativeLibDirs)
            }

            val elements = makePathElements(dexPathList, allDirs)
            val pathElementsField = dexPathList.javaClass
                .getDeclaredField("nativeLibraryPathElements").apply { isAccessible = true }
            pathElementsField.set(dexPathList, elements)
        } catch (e: Exception) {
            // Surfaced to the caller so OCR/DTS degrade to "unavailable" instead of crashing.
            throw IOException("Failed to attach delivered native library directory ${dir.absolutePath}", e)
        }
    }

    private fun makePathElements(dexPathList: Any, dirs: List<File>): Any {
        val cls = dexPathList.javaClass
        return try {
            // API 26+: makePathElements(List<File>)
            val method = cls.getDeclaredMethod("makePathElements", List::class.java).apply { isAccessible = true }
            method.invoke(dexPathList, dirs)
        } catch (_: NoSuchMethodException) {
            // API 23-25: makePathElements(List<File>, File, List<IOException>) (static)
            val method = cls.getDeclaredMethod(
                "makePathElements", List::class.java, File::class.java, List::class.java
            ).apply { isAccessible = true }
            method.invoke(null, dirs, null, ArrayList<IOException>())
        } ?: throw IOException("makePathElements returned null")
    }
}
