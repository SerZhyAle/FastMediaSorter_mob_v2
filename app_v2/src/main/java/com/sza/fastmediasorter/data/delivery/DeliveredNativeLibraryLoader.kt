package com.sza.fastmediasorter.data.delivery

import android.content.Context
import android.os.Build
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetContributor
import dagger.hilt.android.qualifiers.ApplicationContext
import dalvik.system.BaseDexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    @param:ApplicationContext private val context: Context,
    private val verifier: PayloadIntegrityVerifier,
    private val bundledSets: BundledDeliverableSets,
    private val contributors: Set<@JvmSuppressWildcards DeliverableSetContributor>,
    private val capabilityRepository: DeliverableCapabilityRepository,
    @ApplicationScope private val recoveryScope: CoroutineScope
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
            invalidateCorruptSet(set)
            throw DeliveredPayloadCorruptException(set, "set directory missing: ${setDir.absolutePath}")
        }

        // ADR-3: verify all payload files before any code becomes loadable.
        for (payloadFile in descriptor.files) {
            val file = File(setDir, payloadFile.fileName)
            val result = verifier.verify(file, payloadFile)
            if (result is PayloadIntegrityVerifier.Result.Failed) {
                invalidateCorruptSet(set)
                throw DeliveredPayloadCorruptException(set, result.reason)
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
                // The payload already passed integrity verification (ADR-3), so the bytes are correct -
                // an UnsatisfiedLinkError here means the device cannot load this byte-correct .so (ABI
                // mismatch on an emulator / non-arm64 device, or an unsatisfiable dependency). This is an
                // expected device-capability fallback, not a corrupt delivery: log at WARN (not ERROR),
                // do not invalidate the set, and rethrow as a catchable Exception so consumers degrade
                // gracefully instead of letting the Error escape their catch (Exception) blocks uncaught.
                Timber.w("DeliveredNativeLibraryLoader: native set %s not loadable on this device (%s): %s", set, payloadFile.fileName, e.message)
                throw DeliveredNativeLibraryIncompatibleException(set, "cannot load ${payloadFile.fileName}: ${e.message}")
            } catch (e: Exception) {
                throw IOException("Error loading library: ${file.absolutePath}", e)
            }
        }

        // S0923: warm-load via System.load(absolutePath) resolves by path and therefore succeeds even when
        // injectNativeLibraryDirectory did not take effect on this device/OS. That masks the injection
        // failure: the engines' own static initializers call System.loadLibrary(soname), which resolves by
        // NAME and can fall through to a forbidden platform copy (e.g. /system/lib64/libjpeg.so) and crash
        // with an UnsatisfiedLinkError before any consumer catch runs. Assert each delivered .so is now
        // name-resolvable into setDir; if not, degrade like an incompatible payload instead of crashing later.
        for (payloadFile in soFiles) {
            if (!resolvesIntoDelivered(set, setDir, payloadFile.fileName)) {
                throw DeliveredNativeLibraryIncompatibleException(
                    set, "name resolution for ${payloadFile.fileName} did not reach ${setDir.absolutePath}",
                )
            }
        }

        loadedSets.add(set)
        Timber.i("DeliveredNativeLibraryLoader: attached native set %s from %s", set, setDir.absolutePath)
    }

    /**
     * True when `System.loadLibrary(soname)` for [fileName] would resolve to the delivered copy in
     * [setDir]. When the name cannot be verified (not a `lib*.so`, no dex classloader, or `findLibrary`
     * unavailable) the check is skipped (returns true) and the engine-boundary `LinkageError` guard is
     * left as the crash backstop; only a name that resolves to a foreign copy fails the check.
     */
    private fun resolvesIntoDelivered(set: DeliverableSet, setDir: File, fileName: String): Boolean {
        val resolved = resolvedLibraryPath(fileName) ?: return true
        val ok = File(resolved).parentFile?.absolutePath == setDir.absolutePath
        if (!ok) {
            Timber.w(
                "DeliveredNativeLibraryLoader: set %s attached but %s resolves by name to %s, not %s; " +
                    "native path injection ineffective (API %d)",
                set, fileName, resolved, setDir.absolutePath, Build.VERSION.SDK_INT,
            )
        }
        return ok
    }

    /**
     * Absolute path `System.loadLibrary` would resolve for [fileName]'s soname via the classloader's
     * native search path, or null when it cannot be determined (non `lib*.so`, no [BaseDexClassLoader],
     * or `findLibrary` blocked by hidden-API).
     */
    private fun resolvedLibraryPath(fileName: String): String? {
        val shortName = shortLibName(fileName)
        val cl = javaClass.classLoader as? BaseDexClassLoader
        if (shortName == null || cl == null) return null
        return try {
            cl.findLibrary(shortName)
        } catch (e: LinkageError) {
            Timber.w(e, "findLibrary unavailable, skipping name-resolution check for %s", fileName)
            null
        }
    }

    /** `libjpeg.so` -> `jpeg`; null for anything that is not a `lib*.so` shared object. */
    private fun shortLibName(fileName: String): String? =
        if (fileName.startsWith("lib") && fileName.endsWith(".so")) {
            fileName.removePrefix("lib").removeSuffix(".so")
        } else {
            null
        }

    /**
     * Drop the corrupt payload and clear the persisted install flag (S0432) so the set stops
     * reporting "installed" and the Extensions row reactively offers a reinstall. Runs on the
     * application scope because [load] is `@Synchronized` and cannot call the suspend uninstall
     * directly; the operation is idempotent, so a concurrent retry that re-detects corruption
     * simply re-issues the same cleanup.
     */
    private fun invalidateCorruptSet(set: DeliverableSet) {
        recoveryScope.launch { capabilityRepository.uninstall(set) }
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
