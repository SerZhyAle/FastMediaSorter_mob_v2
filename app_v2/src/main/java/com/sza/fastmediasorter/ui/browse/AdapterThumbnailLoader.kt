package com.sza.fastmediasorter.ui.browse

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.HeifSupportUtils
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.glide.CloudThumbnailData
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
import com.sza.fastmediasorter.di.memoryPressureDecodeFormatResolver
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SyntheticResourceIds
import com.sza.fastmediasorter.util.BinaryFileThumbnailGenerator
import com.sza.fastmediasorter.util.ExtensionThumbnailGenerator
import com.sza.fastmediasorter.utils.GlideCacheStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Handles all thumbnail loading for [MediaFileAdapter] ViewHolders.
 * Encapsulates Glide requests for local, network, and cloud files across all media types.
 *
 * Extracted from MediaFileAdapter.ListViewHolder and GridViewHolder (Wave 3 - IV.1).
 * Both ViewHolders share this single implementation parameterised by [isListMode].
 */
class AdapterThumbnailLoader(
    private val getIsScrolling: () -> Boolean,
    private val getDisableThumbnails: () -> Boolean,
    private val getRefreshVersion: () -> Int,
    private val getCredentialsId: () -> String?,
    private val getShowVideoThumbnails: () -> Boolean,
    private val getShowPdfThumbnails: () -> Boolean,
    private val getBinaryGenerator: () -> BinaryFileThumbnailGenerator?,
    // S0783: favicon sprite-atlas plumbing for STREAM favorites rows (live channels in the Favorites
    // list). Mirrors the streams catalog (StreamSourceAdapter.bindFavicon): [faviconResolver] maps a
    // channel url -> its atlas tile index, [faviconTileLoader] decodes that index into a bitmap off the
    // main thread. The no-op defaults render the generic kind placeholder, so non-favorites screens that
    // build this loader without an atlas are unaffected.
    private val faviconResolver: (String) -> Int? = { null },
    private val faviconTileLoader: suspend (Int) -> Bitmap? = { null },
    private val faviconScope: CoroutineScope? = null
) {

    // Main-thread-only map of the in-flight favicon decode per target ImageView, so a rebind/recycle can
    // cancel the stale decode before it paints a previous channel's logo (the adapter binds on main).
    private val faviconJobs = HashMap<ImageView, Job>()

    private val decodeFormatResolver by lazy {
        com.sza.fastmediasorter.FastMediaSorterApp.appContext.memoryPressureDecodeFormatResolver()
    }

    companion object {
        const val CACHED_THUMBNAIL_SIZE = 300
        private const val VIDEO_PRIORITY_THUMBNAIL_SUSPEND_MESSAGE = "Video player priority - thumbnail loading suspended"
        // PDF thumbnail size limits for network resources when "Large PDF Thumbnails" is ENABLED (bytes)
        private const val SMB_PDF_LARGE_MAX_SIZE = 50 * 1024 * 1024L
        private const val NETWORK_PDF_LARGE_MAX_SIZE = 10 * 1024 * 1024L
        // PDF thumbnail size limits when "Large PDF Thumbnails" is DISABLED
        private const val SMB_PDF_NORMAL_MAX_SIZE = 3 * 1024 * 1024L
        private const val NETWORK_PDF_NORMAL_MAX_SIZE = 1 * 1024 * 1024L
        // EPUB cover size limits for network resources
        private const val SMB_EPUB_MAX_SIZE = 50 * 1024 * 1024L
        private const val NETWORK_EPUB_MAX_SIZE = 10 * 1024 * 1024L
        // S1317: decode-capability failure fragment - Glide's required-transform throw on an
        // AnimatedImageDrawable it cannot convert, not a broken source file.
        private const val BITMAP_CONVERSION_FRAGMENT = "to a bitmap"
    }

    // ─── Public entry point ───────────────────────────────────────────────────

    /**
     * Load thumbnail into [imageView] for [file].
     * Returns new cache key when a Glide request was started; null if skipped.
     * Caller stores the returned key: `lastKey = load(...) ?: lastKey`
     *
     * [binaryGeneratorSizePx] - pixel size passed to [BinaryFileThumbnailGenerator]; null uses [CACHED_THUMBNAIL_SIZE].
     *   Glide image/video overrides always use [CACHED_THUMBNAIL_SIZE] regardless of this value.
     * [isListMode] - true for ListViewHolder: checks SAF URI existence, adds PDF/EPUB load listeners.
     */
    fun load(
        imageView: ImageView,
        file: MediaFile,
        lastLoadedKey: String?,
        binaryGeneratorSizePx: Int? = null,
        isListMode: Boolean = false
    ): String? {
        val binarySizePx = binaryGeneratorSizePx ?: CACHED_THUMBNAIL_SIZE
        val context = imageView.context
        val isScrolling = getIsScrolling()

        // S0783: a rebind/recycle of this view cancels any in-flight favicon decode targeting it.
        cancelFavicon(imageView)
        // STREAM favorites (live channels in the Favorites list) render the channel's favicon-atlas tile
        // like the streams catalog - their url is not a decodable file path. Checked before the AUDIO
        // branch so audio channels also get the favicon rather than an extension bitmap.
        if (file.resourceId == SyntheticResourceIds.STREAM) {
            loadStreamFavicon(imageView, file)
            return null
        }

        if (file.isDirectory) return null

        val newKey = "${file.path}_${file.size}_${getDisableThumbnails()}_${getShowVideoThumbnails()}_${getShowPdfThumbnails()}_${getRefreshVersion()}"
        if (newKey == lastLoadedKey) return null

        // AUDIO: extension bitmap only; cover art loads exclusively in Player
        if (file.type == MediaType.AUDIO) {
            val ext = file.name.substringAfterLast('.', "").uppercase()
            imageView.setImageBitmap(createExtensionBitmap(ext))
            applyPlaceholderStyle(imageView, file.type)
            Timber.v("loadThumbnail: AUDIO extension bitmap for ${file.name} ($ext)")
            return newKey
        }

        // TEXT and Office documents: extension bitmap
        if (file.type == MediaType.TEXT || file.type == MediaType.OFFICE_DOCUMENT) {
            val ext = file.name.substringAfterLast('.', "").uppercase()
            imageView.setImageBitmap(createExtensionBitmap(ext))
            applyPlaceholderStyle(imageView, file.type)
            return newKey
        }

        // Binary files: custom generator or extension bitmap
        if (file.type.isBinaryFile()) {
            val ext = file.name.substringAfterLast('.', "").ifEmpty { "BIN" }
            getBinaryGenerator()?.let { gen ->
                imageView.setImageBitmap(gen.generateThumbnail(ext, file.type, binarySizePx))
                resetThumbnailStyle(imageView)
                Timber.v("Binary file thumbnail generated for ${file.name}")
            } ?: run {
                imageView.setImageBitmap(createExtensionBitmap(ext.uppercase()))
                applyPlaceholderStyle(imageView, file.type)
            }
            return newKey
        }

        val generatedPlaceholder = createPlaceholderDrawable(file, context.resources)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        // During scroll assign correct placeholder immediately - prevents stale extension
        // from previous ViewHolder occupant; Glide will then attempt a synchronous cache-only hit.
        if (isScrolling) {
            showGeneratedPlaceholder(imageView, file)
        }

        if (getDisableThumbnails()) {
            showGeneratedPlaceholder(imageView, file)
            return newKey
        }

        val isCloudPath = file.path.startsWith("cloud://")
        val isNetworkPath = file.path.startsWith("smb://") ||
            file.path.startsWith("sftp://") || file.path.startsWith("ftp://")

        if (!isNetworkPath && !isCloudPath) {
            if (!checkFileExists(file, context, isListMode)) {
                Timber.w("File no longer exists: ${file.path}")
                showGeneratedPlaceholder(imageView, file)
                return newKey
            }
        }

        when (file.type) {
            MediaType.EPUB -> loadEpub(imageView, file, context, isNetworkPath, isCloudPath, generatedPlaceholder, isListMode, isScrolling)
            MediaType.PDF -> loadPdf(imageView, file, context, isNetworkPath, isCloudPath, generatedPlaceholder, isListMode, isScrolling)
            MediaType.IMAGE, MediaType.GIF -> loadImage(imageView, file, context, isNetworkPath, isCloudPath, generatedPlaceholder, isScrolling)
            MediaType.VIDEO -> loadVideo(imageView, file, context, isNetworkPath, isCloudPath, generatedPlaceholder, isListMode, isScrolling)
            MediaType.AUDIO -> {
                val ext = file.name.substringAfterLast('.', "").uppercase()
                imageView.setImageBitmap(createExtensionBitmap(ext))
                applyPlaceholderStyle(imageView, file.type)
            }
            MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
            MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> {
                val ext = file.name.substringAfterLast('.', "").uppercase()
                imageView.setImageBitmap(getBinaryGenerator()?.generateThumbnail(ext, file.type))
                applyPlaceholderStyle(imageView, file.type)
            }
            else -> {} // TEXT handled above
        }
        // S0110: return null during scroll so lastLoadedKey stays unset → full reload fires on scroll stop
        return if (isScrolling) null else newKey
    }

    // ─── Stream favicon (S0783) ───────────────────────────────────────────────

    /** S0783: cancel the in-flight favicon decode (if any) targeting [imageView]. */
    fun cancelFavicon(imageView: ImageView) {
        faviconJobs.remove(imageView)?.cancel()
    }

    /**
     * S0783: render a STREAM favorite's leading thumbnail from the favicon sprite-atlas, mirroring
     * [com.sza.fastmediasorter.ui.streams.StreamSourceAdapter] bindFavicon. A generic kind icon shows
     * immediately; the atlas tile replaces it when the async decode resolves. Rebind-safety comes from
     * [cancelFavicon] at the top of [load]: the prior job is cancelled before the next bind launches, so a
     * cancelled decode never paints a stale channel logo onto a recycled row.
     */
    private fun loadStreamFavicon(imageView: ImageView, file: MediaFile) {
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val kindIcon = if (file.type == MediaType.AUDIO) R.drawable.ic_audio else R.drawable.ic_video
        imageView.setImageResource(kindIcon)
        applyPlaceholderStyle(imageView, file.type)
        val scope = faviconScope ?: return
        val index = faviconResolver(file.path) ?: return
        faviconJobs[imageView] = scope.launch {
            val tile = faviconTileLoader(index) ?: return@launch
            resetThumbnailStyle(imageView)
            imageView.setImageBitmap(tile)
        }
    }

    // ─── Style utilities (formerly companion object / ViewHolder statics) ─────

    fun applyPlaceholderStyle(imageView: ImageView, type: MediaType) {
        val colorRes = when (type) {
            MediaType.VIDEO -> R.color.thumbnail_video_bg
            MediaType.AUDIO -> R.color.thumbnail_audio_bg
            MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT -> R.color.thumbnail_doc_bg
            else -> R.color.thumbnail_image_bg
        }
        imageView.setBackgroundColor(ContextCompat.getColor(imageView.context, colorRes))
    }

    fun resetThumbnailStyle(imageView: ImageView) {
        imageView.background = null
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    fun createExtensionBitmap(extension: String): Bitmap =
        ExtensionThumbnailGenerator.generate(extension)

    fun createPlaceholderDrawable(file: MediaFile, resources: Resources): BitmapDrawable =
        BitmapDrawable(resources, createExtensionBitmap(getPlaceholderExtension(file)))

    fun showGeneratedPlaceholder(imageView: ImageView, file: MediaFile) {
        imageView.setImageBitmap(createExtensionBitmap(getPlaceholderExtension(file)))
        applyPlaceholderStyle(imageView, file.type)
    }

    private fun getPlaceholderExtension(file: MediaFile): String {
        val ext = file.name.substringAfterLast('.', "").uppercase()
        if (ext.isNotBlank()) return ext
        return when (file.type) {
            MediaType.IMAGE, MediaType.GIF -> "IMG"
            MediaType.VIDEO -> "VID"
            MediaType.PDF -> "PDF"
            MediaType.EPUB -> "EPUB"
            MediaType.OFFICE_DOCUMENT -> "DOC"
            MediaType.AUDIO -> "AUD"
            MediaType.TEXT -> "TXT"
            MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
            MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> "BIN"
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun checkFileExists(file: MediaFile, context: Context, isListMode: Boolean): Boolean {
        if (file.path.startsWith("content://")) {
            if (!isListMode) return true // Grid skips SAF check
            return try {
                val uri = Uri.parse(file.path)
                DocumentFile.fromSingleUri(context, uri)?.exists() == true
            } catch (e: Exception) {
                Timber.w(e, "Failed to check SAF URI existence: ${file.path}")
                false
            }
        }
        return File(file.path).exists()
    }

    private fun detectCloudProvider(path: String): CloudProvider = when {
        path.startsWith("cloud://googledrive", ignoreCase = true) ||
            path.startsWith("cloud://google_drive", ignoreCase = true) -> CloudProvider.GOOGLE_DRIVE
        path.startsWith("cloud://onedrive", ignoreCase = true) -> CloudProvider.ONEDRIVE
        path.startsWith("cloud://dropbox", ignoreCase = true) -> CloudProvider.DROPBOX
        else -> CloudProvider.GOOGLE_DRIVE
    }

    private fun extractCloudFileId(path: String, provider: CloudProvider): String = when (provider) {
        CloudProvider.DROPBOX -> {
            val dropboxPath = path.substringAfter("cloud://dropbox")
            if (dropboxPath.startsWith("/")) dropboxPath else "/$dropboxPath"
        }
        else -> path.substringAfterLast("/")
    }

    private fun loadEpub(
        imageView: ImageView, file: MediaFile, context: Context,
        isNetworkPath: Boolean, isCloudPath: Boolean,
        generatedPlaceholder: BitmapDrawable, isListMode: Boolean,
        isScrolling: Boolean
    ) {
        if (!isCloudPath && !isNetworkPath) {
            val epubFile = File(file.path)
            if (epubFile.exists()) {
                Glide.with(context)
                    .asBitmap()
                    .load(epubFile)
                    .format(decodeFormatResolver.decodeFormat())
                    .signature(ObjectKey("${file.path}_${file.size}"))
                    .placeholder(generatedPlaceholder)
                    .error(generatedPlaceholder)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .onlyRetrieveFromCache(isScrolling)
                    .dontAnimate()
                    .into(imageView)
            } else {
                showGeneratedPlaceholder(imageView, file)
            }
        } else if (isNetworkPath) {
            val isSmbPath = file.path.startsWith("smb://")
            val maxSize = if (isSmbPath) SMB_EPUB_MAX_SIZE else NETWORK_EPUB_MAX_SIZE
            if (file.size > maxSize) {
                showGeneratedPlaceholder(imageView, file)
            } else {
                if (isListMode && NetworkFileDataFetcher.isThumbnailFailed(file.path)) {
                    Timber.v("Skipping EPUB cover load for ${file.name} (cached as failed)")
                    showGeneratedPlaceholder(imageView, file)
                    return
                }
                val networkData = NetworkFileData(path = file.path, size = file.size, credentialsId = getCredentialsId())
                val builder = Glide.with(context)
                    .asBitmap()
                    .load(networkData)
                    .format(decodeFormatResolver.decodeFormat())
                    .signature(ObjectKey("${file.path}_${file.size}"))
                    .placeholder(generatedPlaceholder)
                    .error(generatedPlaceholder)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .onlyRetrieveFromCache(isScrolling)
                    .dontAnimate()
                // S0110: skip failure-marking listener during scroll - cache miss is expected, not a real error
                if (isListMode && !isScrolling) {
                    builder.listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                            if (isVideoPriorityThumbnailSuspension(e)) {
                                Timber.v("EPUB cover load suspended by video priority: ${file.name}")
                            } else if (isDecodeCapabilityFailure(e)) {
                                Timber.w("EPUB cover load failed due to decode capability: ${file.name}")
                            } else if (e != null) {
                                Timber.w("EPUB cover load failed: ${file.name}, ${e.message}")
                                NetworkFileDataFetcher.markThumbnailAsFailed(file.path)
                            }
                            return false
                        }
                        override fun onResourceReady(resource: Bitmap, model: Any, target: Target<Bitmap>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            resetThumbnailStyle(imageView)
                            return false
                        }
                    }).into(imageView)
                } else {
                    builder.into(imageView)
                }
            }
        } else {
            // Cloud EPUB
            if (file.size > NETWORK_EPUB_MAX_SIZE) {
                showGeneratedPlaceholder(imageView, file)
            } else {
                showGeneratedPlaceholder(imageView, file)
            }
        }
    }

    private fun loadPdf(
        imageView: ImageView, file: MediaFile, context: Context,
        isNetworkPath: Boolean, isCloudPath: Boolean,
        generatedPlaceholder: BitmapDrawable, isListMode: Boolean,
        isScrolling: Boolean
    ) {
        val largePdfThumbnails = getShowPdfThumbnails()

        if (!isCloudPath && !isNetworkPath) {
            val pdfFile = File(file.path)
            if (pdfFile.exists()) {
                Glide.with(context)
                    .asBitmap()
                    .load(pdfFile)
                    .format(decodeFormatResolver.decodeFormat())
                    .signature(ObjectKey("${file.path}_${file.size}"))
                    .placeholder(generatedPlaceholder)
                    .error(generatedPlaceholder)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .onlyRetrieveFromCache(isScrolling)
                    .dontAnimate()
                    .into(imageView)
            } else {
                if (isListMode) showGeneratedPlaceholder(imageView, file)
                else imageView.setImageBitmap(createExtensionBitmap("PDF"))
            }
        } else if (isNetworkPath) {
            val isSmbPath = file.path.startsWith("smb://")
            val maxSize = if (largePdfThumbnails) {
                if (isSmbPath) SMB_PDF_LARGE_MAX_SIZE else NETWORK_PDF_LARGE_MAX_SIZE
            } else {
                if (isSmbPath) SMB_PDF_NORMAL_MAX_SIZE else NETWORK_PDF_NORMAL_MAX_SIZE
            }
            if (file.size > maxSize) {
                if (isListMode) showGeneratedPlaceholder(imageView, file)
                else imageView.setImageBitmap(createExtensionBitmap("PDF"))
            } else {
                if (isListMode && NetworkFileDataFetcher.isThumbnailFailed(file.path)) {
                    Timber.v("Skipping PDF thumbnail load for ${file.name} (cached as failed)")
                    showGeneratedPlaceholder(imageView, file)
                    return
                }
                val builder = Glide.with(context)
                    .asBitmap()
                    .load(NetworkFileData(
                        path = file.path,
                        credentialsId = getCredentialsId(),
                        loadFullImage = false,
                        size = file.size,
                        createdDate = file.createdDate
                    ))
                    .format(decodeFormatResolver.decodeFormat())
                    .apply(RequestOptions().set(
                        com.sza.fastmediasorter.data.glide.NetworkPdfThumbnailLoader.OPTION_FULL_PDF_DOWNLOAD,
                        largePdfThumbnails
                    ))
                    .placeholder(generatedPlaceholder)
                    .error(generatedPlaceholder)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .onlyRetrieveFromCache(isScrolling)
                    .dontAnimate()
                // S0110: skip failure-marking listener during scroll - cache miss is expected, not a real error
                if (isListMode && !isScrolling) {
                    builder.listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                            if (isVideoPriorityThumbnailSuspension(e)) {
                                Timber.v("PDF thumbnail load suspended by video priority: ${file.name}")
                            } else if (isDecodeCapabilityFailure(e)) {
                                Timber.w("PDF thumbnail load failed due to decode capability: ${file.name}")
                            } else if (e != null) {
                                Timber.w("PDF thumbnail load failed: ${file.name}, ${e.message}")
                                NetworkFileDataFetcher.markThumbnailAsFailed(file.path)
                            }
                            return false
                        }
                        override fun onResourceReady(resource: Bitmap, model: Any, target: Target<Bitmap>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            resetThumbnailStyle(imageView)
                            return false
                        }
                    }).into(imageView)
                } else {
                    builder.into(imageView)
                }
            }
        } else {
            // Cloud PDF
            val maxSize = if (largePdfThumbnails) NETWORK_PDF_LARGE_MAX_SIZE else NETWORK_PDF_NORMAL_MAX_SIZE
            if (file.size > maxSize) {
                if (isListMode) showGeneratedPlaceholder(imageView, file)
                else imageView.setImageBitmap(createExtensionBitmap("PDF"))
            } else {
                if (isListMode) showGeneratedPlaceholder(imageView, file)
                else imageView.setImageBitmap(createExtensionBitmap("PDF"))
            }
        }
    }

    private fun loadImage(
        imageView: ImageView, file: MediaFile, context: Context,
        isNetworkPath: Boolean, isCloudPath: Boolean,
        generatedPlaceholder: BitmapDrawable,
        isScrolling: Boolean
    ) {
        val fileExt = file.name.substringAfterLast('.', "").lowercase()
        if (!HeifSupportUtils.isSupported(fileExt)) {
            Timber.w("loadThumbnail: ${fileExt.uppercase()} not supported on this device - showing placeholder for ${file.name}")
            showGeneratedPlaceholder(imageView, file)
            return
        }
        when {
            isCloudPath -> {
                val provider = detectCloudProvider(file.path)
                val fileId = extractCloudFileId(file.path, provider)
                Glide.with(context)
                    .load(CloudThumbnailData(
                        thumbnailUrl = file.thumbnailUrl ?: "",
                        fileId = fileId,
                        loadFullImage = false,
                        cloudProvider = provider
                    ))
                    .format(decodeFormatResolver.decodeFormat())
                    .priority(Priority.HIGH)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .onlyRetrieveFromCache(isScrolling)
                    .dontAnimate()
                    .placeholder(generatedPlaceholder)
                    .error(generatedPlaceholder)
                    .into(imageView)
            }
            isNetworkPath -> {
                if (NetworkFileDataFetcher.isThumbnailFailed(file.path)) {
                    Timber.v("Skipping thumbnail load for ${file.name} (cached as failed)")
                    showGeneratedPlaceholder(imageView, file)
                    return
                }
                val imageBuilder = Glide.with(context)
                    .load(NetworkFileData(
                        path = file.path,
                        credentialsId = getCredentialsId(),
                        loadFullImage = false,
                        size = file.size,
                        createdDate = file.createdDate
                    ))
                    .format(decodeFormatResolver.decodeFormat())
                    .priority(Priority.HIGH)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE)
                    .centerCrop()
                    .onlyRetrieveFromCache(isScrolling)
                    .dontAnimate()
                    .placeholder(generatedPlaceholder)
                    .error(generatedPlaceholder)
                // S0110: skip failure-marking listener during scroll - cache miss is expected, not a real error
                if (!isScrolling) {
                    imageBuilder.listener(object : RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean {
                            if (isVideoPriorityThumbnailSuspension(e)) {
                                Timber.v("Network image load suspended by video priority: ${file.name}")
                            } else if (isDecodeCapabilityFailure(e)) {
                                Timber.d("S1317: skip failed-thumb mark for ${file.name}")
                                Timber.w("Network image load failed due to decode capability: ${file.name}")
                            } else if (e != null) {
                                Timber.w("Network image load failed: ${file.name}, ${e.message}")
                                NetworkFileDataFetcher.markThumbnailAsFailed(file.path)
                            }
                            applyPlaceholderStyle(imageView, file.type)
                            return false
                        }
                        override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: Target<android.graphics.drawable.Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            GlideCacheStats.recordLoad(dataSource)
                            resetThumbnailStyle(imageView)
                            return false
                        }
                    }).into(imageView)
                } else {
                    imageBuilder.into(imageView)
                }
            }
            else -> {
                val data: Any = when {
                    file.path.startsWith("content://") -> Uri.parse(file.path)
                    !File(file.path).canRead() && !file.contentUri.isNullOrEmpty() -> Uri.parse(file.contentUri)
                    else -> File(file.path)
                }
                val localImageBuilder = Glide.with(context)
                    .load(data)
                    .format(decodeFormatResolver.decodeFormat())
                    .signature(ObjectKey("${file.path}_${file.size}"))
                    .priority(Priority.HIGH)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE)
                    .centerCrop()
                    .onlyRetrieveFromCache(isScrolling)
                    .dontAnimate()
                    .placeholder(generatedPlaceholder)
                    .error(generatedPlaceholder)
                // S0110: no error/stats listener during scroll - cache miss triggers .error() placeholder naturally
                if (!isScrolling) {
                    localImageBuilder.listener(object : RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean {
                            if (e != null) Timber.w("Local image load failed: ${file.name}, ${e.message}")
                            applyPlaceholderStyle(imageView, file.type)
                            return false
                        }
                        override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: Target<android.graphics.drawable.Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            GlideCacheStats.recordLoad(dataSource)
                            resetThumbnailStyle(imageView)
                            return false
                        }
                    }).into(imageView)
                } else {
                    localImageBuilder.into(imageView)
                }
            }
        }
    }

    private fun loadVideo(
        imageView: ImageView, file: MediaFile, context: Context,
        isNetworkPath: Boolean, isCloudPath: Boolean,
        generatedPlaceholder: BitmapDrawable, isListMode: Boolean,
        isScrolling: Boolean
    ) {
        when {
            isCloudPath -> {
                val provider = detectCloudProvider(file.path)
                val fileId = extractCloudFileId(file.path, provider)
                Glide.with(context)
                    .load(CloudThumbnailData(
                        thumbnailUrl = file.thumbnailUrl ?: "",
                        fileId = fileId,
                        loadFullImage = false,
                        cloudProvider = provider
                    ))
                    .format(decodeFormatResolver.decodeFormat())
                    .priority(Priority.NORMAL)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .onlyRetrieveFromCache(isScrolling)
                    .dontAnimate()
                    .placeholder(generatedPlaceholder)
                    .error(generatedPlaceholder)
                    .into(imageView)
            }
            isNetworkPath -> {
                // ListMode respects video thumbnail setting; Grid always loads
                if (isListMode && !getShowVideoThumbnails()) {
                    showGeneratedPlaceholder(imageView, file)
                    return
                }
                // S0063: skip extraction immediately for formats known to fail on network streams.
                // Avoids wasting a 10-second MediaMetadataRetriever timeout slot per file.
                val ext = file.name.substringAfterLast('.', "").lowercase()
                if (com.sza.fastmediasorter.data.network.glide.NetworkThumbnailExtractionPolicy
                        .shouldSkipNetworkExtraction(ext)) {
                    Timber.d("[scope=thumbnail] Blocked network format '$ext' - showing placeholder: ${file.name}")
                    showGeneratedPlaceholder(imageView, file)
                    imageView.contentDescription = context.getString(
                        R.string.thumbnail_unavailable_network_format,
                        ext.uppercase()
                    )
                    return
                }
                if (NetworkFileDataFetcher.isVideoFailed(file.path)) {
                    Timber.v("Skipping video thumbnail load for ${file.name} (cached as failed)")
                    showGeneratedPlaceholder(imageView, file)
                    return
                }
                val videoBuilder = Glide.with(context)
                    .load(NetworkFileData(
                        path = file.path,
                        credentialsId = getCredentialsId(),
                        loadFullImage = false,
                        size = file.size,
                        createdDate = file.createdDate
                    ))
                    .format(decodeFormatResolver.decodeFormat())
                    .priority(Priority.NORMAL)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE)
                    .centerCrop()
                    .onlyRetrieveFromCache(isScrolling)
                    .dontAnimate()
                    .placeholder(generatedPlaceholder)
                    .error(generatedPlaceholder)
                // S0110: skip failure-marking listener during scroll - cache miss is expected, not a real error
                if (!isScrolling) {
                    videoBuilder.listener(object : RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean {
                            if (isVideoPriorityThumbnailSuspension(e)) {
                                Timber.v("Video thumbnail load suspended by video priority: ${file.name}")
                            } else if (isVideoDecoderException(e)) {
                                NetworkFileDataFetcher.markVideoAsFailed(file.path)
                                Timber.v("Thumbnail load failed: ${file.name} (decoder error, cached)")
                            } else if (e != null) {
                                Timber.w("Thumbnail load failed: ${file.name}, ${e.message}")
                            }
                            if (isListMode) applyPlaceholderStyle(imageView, file.type)
                            return false
                        }
                        override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: Target<android.graphics.drawable.Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            GlideCacheStats.recordLoad(dataSource)
                            resetThumbnailStyle(imageView)
                            return false
                        }
                    }).into(imageView)
                } else {
                    videoBuilder.into(imageView)
                }
            }
            else -> {
                if (isListMode && !getShowVideoThumbnails()) {
                    showGeneratedPlaceholder(imageView, file)
                    return
                }
                val data: Any = when {
                    file.path.startsWith("content://") -> Uri.parse(file.path)
                    !File(file.path).canRead() && !file.contentUri.isNullOrEmpty() -> Uri.parse(file.contentUri)
                    else -> File(file.path)
                }
                val localVideoBuilder = Glide.with(context)
                    .load(data)
                    .format(decodeFormatResolver.decodeFormat())
                    .signature(ObjectKey("${file.path}_${file.size}"))
                    .priority(Priority.NORMAL)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE)
                    .centerCrop()
                    .onlyRetrieveFromCache(isScrolling)
                    .dontAnimate()
                    .placeholder(generatedPlaceholder)
                    .error(generatedPlaceholder)
                // S0110: no failure-marking listener during scroll - cache miss triggers .error() placeholder naturally
                if (!isScrolling) {
                    localVideoBuilder.listener(object : RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean {
                            if (isListMode) applyPlaceholderStyle(imageView, file.type)
                            return false
                        }
                        override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: Target<android.graphics.drawable.Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            GlideCacheStats.recordLoad(dataSource)
                            resetThumbnailStyle(imageView)
                            return false
                        }
                    }).into(imageView)
                } else {
                    localVideoBuilder.into(imageView)
                }
            }
        }
    }

    private fun isVideoPriorityThumbnailSuspension(e: GlideException?): Boolean {
        if (e == null) return false

        // ConnectionThrottleManager uses this cancellation as back-pressure; treating it as a
        // failure would poison the persisted failed-thumbnail cache and suppress a later retry.
        if (e.rootCauses.any { cause ->
                cause is CancellationException &&
                    cause.message?.contains(VIDEO_PRIORITY_THUMBNAIL_SUSPEND_MESSAGE) == true
            }) {
            return true
        }

        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 10) {
            if (current is CancellationException &&
                current.message?.contains(VIDEO_PRIORITY_THUMBNAIL_SUSPEND_MESSAGE) == true
            ) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    private fun isDecodeCapabilityFailure(e: GlideException?): Boolean {
        if (e == null) return false

        if (e.rootCauses.any { cause ->
                cause is IllegalArgumentException &&
                    cause.message?.lowercase()?.contains(BITMAP_CONVERSION_FRAGMENT) == true
            }) {
            return true
        }

        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 10) {
            val msg = current.message?.lowercase() ?: ""
            val className = current.javaClass.simpleName.lowercase()
            if (current is IllegalArgumentException && msg.contains(BITMAP_CONVERSION_FRAGMENT)) {
                return true
            }
            if (className.contains("illegalargumentexception") && msg.contains(BITMAP_CONVERSION_FRAGMENT)) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    private fun isVideoDecoderException(e: GlideException?): Boolean {
        if (e == null) return false
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 10) {
            val msg = current.message?.lowercase() ?: ""
            val className = current.javaClass.simpleName.lowercase()
            if (className.contains("videodecoder") || className.contains("videodecoderexception") ||
                msg.contains("mediametadataretriever") || msg.contains("failed to retrieve a frame")
            ) return true
            current = current.cause
            depth++
        }
        return false
    }
}
