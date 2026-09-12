package com.sza.fastmediasorter.ui.browse

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
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
@Suppress("LargeClass", "LongMethod")
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

        /**
         * True when [file] is drawn with the generated extension tile rather than a picture of its own.
         *
         * Mirrors the branches of [load] that return before any Glide request is issued, and lives
         * here for that reason: a type that later gains a real preview then moves this answer and the
         * loading path together instead of leaving a second copy of the list to drift (S2177).
         *
         * Deliberately synchronous and type-only. An image whose decode fails also ends on the
         * extension tile, but only after the cell is already on screen, and flipping the caption then
         * would reflow the row under a scrolling finger.
         */
        fun rendersGroupIcon(file: MediaFile): Boolean {
            val hasOwnPicture = file.resourceId == SyntheticResourceIds.STREAM || file.isDirectory
            return !hasOwnPicture &&
                (
                    file.type == MediaType.AUDIO ||
                        file.type == MediaType.TEXT ||
                        file.type == MediaType.OFFICE_DOCUMENT ||
                        file.type.isBinaryFile()
                    )
        }
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
            applyPlaceholderStyle(imageView)
            Timber.v("loadThumbnail: AUDIO extension bitmap for ${file.name} ($ext)")
            return newKey
        }

        // TEXT and Office documents: extension bitmap
        if (file.type == MediaType.TEXT || file.type == MediaType.OFFICE_DOCUMENT) {
            val ext = file.name.substringAfterLast('.', "").uppercase()
            imageView.setImageBitmap(createExtensionBitmap(ext))
            applyPlaceholderStyle(imageView)
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
                applyPlaceholderStyle(imageView)
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

        // S1569: no existence probe here. It ran inside RecyclerView bind on the main thread - eight
        // StrictMode disk reads of 25-68 ms in one frame - and bought nothing: every local branch below
        // already ends in .error(generatedPlaceholder), so a missing file paints the same placeholder,
        // decided by Glide on its own thread.
        when (file.type) {
            MediaType.EPUB -> loadEpub(
                imageView,
                file,
                context,
                isNetworkPath,
                isCloudPath,
                generatedPlaceholder,
                isListMode,
                isScrolling
            )
            MediaType.PDF -> loadPdf(
                imageView,
                file,
                context,
                isNetworkPath,
                isCloudPath,
                generatedPlaceholder,
                isListMode,
                isScrolling
            )
            MediaType.IMAGE, MediaType.GIF -> loadImage(
                imageView,
                file,
                context,
                isNetworkPath,
                isCloudPath,
                generatedPlaceholder,
                isScrolling
            )
            MediaType.VIDEO -> loadVideo(
                imageView,
                file,
                context,
                isNetworkPath,
                isCloudPath,
                generatedPlaceholder,
                isListMode,
                isScrolling
            )
            MediaType.AUDIO -> {
                val ext = file.name.substringAfterLast('.', "").uppercase()
                imageView.setImageBitmap(createExtensionBitmap(ext))
                applyPlaceholderStyle(imageView)
            }
            MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
            MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> {
                val ext = file.name.substringAfterLast('.', "").uppercase()
                imageView.setImageBitmap(getBinaryGenerator()?.generateThumbnail(ext, file.type))
                applyPlaceholderStyle(imageView)
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
        applyPlaceholderStyle(imageView)
        val scope = faviconScope ?: return
        val index = faviconResolver(file.path) ?: return
        faviconJobs[imageView] = scope.launch {
            val tile = faviconTileLoader(index) ?: return@launch
            resetThumbnailStyle(imageView)
            imageView.setImageBitmap(tile)
        }
    }

    // ─── Style utilities (formerly companion object / ViewHolder statics) ─────

    fun applyPlaceholderStyle(imageView: ImageView) {
        val typedValue = android.util.TypedValue()
        val theme = imageView.context.theme
        val color = if (theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceVariant,
                typedValue,
                true
            )
        ) {
            typedValue.data
        } else {
            ContextCompat.getColor(imageView.context, R.color.color_media_other)
        }
        imageView.setBackgroundColor(color)
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
        applyPlaceholderStyle(imageView)
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

    /**
     * S1569: what Glide should load, decided without touching the disk.
     *
     * The previous form asked `File(path).canRead()` first and fell back to the content URI. That probe ran
     * during RecyclerView bind, on the main thread. A row that carries a content URI carries it because the
     * item is MediaStore-backed, which is also the handle that survives scoped storage, so it leads here and
     * the raw path becomes the fallback - see [thumbnailFallbackModel]. Where both resolve they are the same
     * bytes, and the Glide signature is keyed on path and size either way, so the cache does not split.
     */
    private fun thumbnailModel(file: MediaFile): Any = when {
        file.path.startsWith("content://") -> Uri.parse(file.path)
        !file.contentUri.isNullOrEmpty() -> Uri.parse(file.contentUri)
        else -> File(file.path)
    }

    /**
     * S1569: the second attempt, or null when there is nothing else to try. Glide runs it off the main
     * thread only after the primary model fails, which is what preserves the old behaviour of using the raw
     * path whenever it is readable - without asking the disk during bind whether it is.
     */
    private fun thumbnailFallbackModel(file: MediaFile): Any? = when {
        file.path.startsWith("content://") -> null
        file.contentUri.isNullOrEmpty() -> null
        else -> File(file.path)
    }

    /** S1569: [thumbnailFallbackModel] wrapped as the request Glide runs when the primary model fails. */
    private fun thumbnailErrorRequest(
        context: Context,
        file: MediaFile,
        generatedPlaceholder: BitmapDrawable,
    ): RequestBuilder<android.graphics.drawable.Drawable>? {
        val fallback = thumbnailFallbackModel(file) ?: return null
        return Glide.with(context)
            .load(fallback)
            .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE)
            .centerCrop()
            // S1968: the fallback runs precisely when the primary request already failed, so it is the
            // arm most likely to meet an unmeasurable source - it produced the last 3 OOMs after the
            // primary was bounded. Same reason as the primary: MUST follow centerCrop to override it.
            .downsample(DownsampleStrategy.AT_MOST)
            .dontAnimate()
            .error(generatedPlaceholder)
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
        imageView: ImageView,
        file: MediaFile,
        context: Context,
        isNetworkPath: Boolean,
        isCloudPath: Boolean,
        generatedPlaceholder: BitmapDrawable,
        isListMode: Boolean,
        isScrolling: Boolean
    ) {
        if (!isCloudPath && !isNetworkPath) {
            // S1569: no exists() probe - it ran on the main thread during bind (the same defect the
            // image and video branches were already cleared of). The .error placeholder below is the
            // same bitmap the removed else-branch painted, chosen by Glide off the main thread.
            Glide.with(context)
                .asBitmap()
                .load(File(file.path))
                .format(decodeFormatResolver.decodeFormat())
                .signature(ObjectKey("${file.path}_${file.size}"))
                .placeholder(generatedPlaceholder)
                .error(generatedPlaceholder)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .onlyRetrieveFromCache(isScrolling)
                .dontAnimate()
                .into(imageView)
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
                val networkData = NetworkFileData(
                    path = file.path,
                    size = file.size,
                    credentialsId = getCredentialsId()
                )
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
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Bitmap>,
                            isFirstResource: Boolean
                        ): Boolean {
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
                        override fun onResourceReady(
                            resource: Bitmap,
                            model: Any,
                            target: Target<Bitmap>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
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
        imageView: ImageView,
        file: MediaFile,
        context: Context,
        isNetworkPath: Boolean,
        isCloudPath: Boolean,
        generatedPlaceholder: BitmapDrawable,
        isListMode: Boolean,
        isScrolling: Boolean
    ) {
        val largePdfThumbnails = getShowPdfThumbnails()

        if (!isCloudPath && !isNetworkPath) {
            // S1569: no exists() probe - it cost 18 ms on the main thread during bind. Both arms of
            // the removed else-branch painted the PDF extension bitmap, which is exactly what
            // .error(generatedPlaceholder) paints, so a missing file still shows the same tile.
            Glide.with(context)
                .asBitmap()
                .load(File(file.path))
                .format(decodeFormatResolver.decodeFormat())
                .signature(ObjectKey("${file.path}_${file.size}"))
                .placeholder(generatedPlaceholder)
                .error(generatedPlaceholder)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .onlyRetrieveFromCache(isScrolling)
                .dontAnimate()
                .into(imageView)
        } else if (isNetworkPath) {
            val isSmbPath = file.path.startsWith("smb://")
            val maxSize = if (largePdfThumbnails) {
                if (isSmbPath) SMB_PDF_LARGE_MAX_SIZE else NETWORK_PDF_LARGE_MAX_SIZE
            } else {
                if (isSmbPath) SMB_PDF_NORMAL_MAX_SIZE else NETWORK_PDF_NORMAL_MAX_SIZE
            }
            if (file.size > maxSize) {
                if (isListMode) {
                    showGeneratedPlaceholder(imageView, file)
                } else {
                    imageView.setImageBitmap(createExtensionBitmap("PDF"))
                }
            } else {
                if (isListMode && NetworkFileDataFetcher.isThumbnailFailed(file.path)) {
                    Timber.v("Skipping PDF thumbnail load for ${file.name} (cached as failed)")
                    showGeneratedPlaceholder(imageView, file)
                    return
                }
                val builder = Glide.with(context)
                    .asBitmap()
                    .load(
                        NetworkFileData(
                            path = file.path,
                            credentialsId = getCredentialsId(),
                            loadFullImage = false,
                            size = file.size,
                            createdDate = file.createdDate
                        )
                    )
                    .format(decodeFormatResolver.decodeFormat())
                    .apply(
                        RequestOptions().set(
                            com.sza.fastmediasorter.data.glide.NetworkPdfThumbnailLoader.OPTION_FULL_PDF_DOWNLOAD,
                            largePdfThumbnails
                        )
                    )
                    .placeholder(generatedPlaceholder)
                    .error(generatedPlaceholder)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .onlyRetrieveFromCache(isScrolling)
                    .dontAnimate()
                // S0110: skip failure-marking listener during scroll - cache miss is expected, not a real error
                if (isListMode && !isScrolling) {
                    builder.listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Bitmap>,
                            isFirstResource: Boolean
                        ): Boolean {
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
                        override fun onResourceReady(
                            resource: Bitmap,
                            model: Any,
                            target: Target<Bitmap>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
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
                if (isListMode) {
                    showGeneratedPlaceholder(imageView, file)
                } else {
                    imageView.setImageBitmap(createExtensionBitmap("PDF"))
                }
            } else {
                if (isListMode) {
                    showGeneratedPlaceholder(imageView, file)
                } else {
                    imageView.setImageBitmap(createExtensionBitmap("PDF"))
                }
            }
        }
    }

    private fun loadImage(
        imageView: ImageView,
        file: MediaFile,
        context: Context,
        isNetworkPath: Boolean,
        isCloudPath: Boolean,
        generatedPlaceholder: BitmapDrawable,
        isScrolling: Boolean
    ) {
        val fileExt = file.name.substringAfterLast('.', "").lowercase()
        if (!HeifSupportUtils.isSupported(fileExt)) {
            Timber.w(
                "loadThumbnail: ${fileExt.uppercase()} not supported on this device - " +
                    "showing placeholder for ${file.name}"
            )
            showGeneratedPlaceholder(imageView, file)
            return
        }
        when {
            isCloudPath -> {
                val provider = detectCloudProvider(file.path)
                val fileId = extractCloudFileId(file.path, provider)
                Glide.with(context)
                    .load(
                        CloudThumbnailData(
                            thumbnailUrl = file.thumbnailUrl ?: "",
                            fileId = fileId,
                            loadFullImage = false,
                            cloudProvider = provider
                        )
                    )
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
                    .load(
                        NetworkFileData(
                            path = file.path,
                            credentialsId = getCredentialsId(),
                            loadFullImage = false,
                            size = file.size,
                            createdDate = file.createdDate
                        )
                    )
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
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<android.graphics.drawable.Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            if (isVideoPriorityThumbnailSuspension(e)) {
                                Timber.v("Network image load suspended by video priority: ${file.name}")
                            } else if (isDecodeCapabilityFailure(e)) {
                                Timber.w("Network image load failed due to decode capability: ${file.name}")
                            } else if (e != null) {
                                Timber.w("Network image load failed: ${file.name}, ${e.message}")
                                NetworkFileDataFetcher.markThumbnailAsFailed(file.path)
                            }
                            applyPlaceholderStyle(imageView)
                            return false
                        }
                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable,
                            model: Any,
                            target: Target<android.graphics.drawable.Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            GlideCacheStats.recordLoad(dataSource)
                            resetThumbnailStyle(imageView)
                            return false
                        }
                    }).into(imageView)
                } else {
                    imageBuilder.into(imageView)
                }
            }
            else -> loadLocalImage(imageView, file, context, generatedPlaceholder, isScrolling)
        }
    }

    /**
     * S1968: the local-image arm, lifted out of `loadImage` so its guard has somewhere to return to.
     *
     * `loadImage` sat exactly at the two-return limit, so the refusal could not be expressed there
     * without a third return or wrapping the whole branch in an else. Extracting the branch is the
     * smaller change and it shortens `loadImage` rather than nesting it deeper.
     */
    private fun loadLocalImage(
        imageView: ImageView,
        file: MediaFile,
        context: Context,
        generatedPlaceholder: BitmapDrawable,
        isScrolling: Boolean,
    ) {
        if (skipLocalImageThumbnail(file)) {
            showGeneratedPlaceholder(imageView, file)
            return
        }
        val localImageBuilder = Glide.with(context)
            .load(thumbnailModel(file))
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
            .error(thumbnailErrorRequest(context, file, generatedPlaceholder))
        // S0110: no error/stats listener during scroll - cache miss triggers .error() placeholder naturally
        if (!isScrolling) {
            localImageBuilder.listener(object : RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    if (e != null) Timber.w("Local image load failed: ${file.name}, ${e.message}")
                    applyPlaceholderStyle(imageView)
                    return false
                }
                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    model: Any,
                    target: Target<android.graphics.drawable.Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    GlideCacheStats.recordLoad(dataSource)
                    resetThumbnailStyle(imageView)
                    return false
                }
            }).into(imageView)
        } else {
            localImageBuilder.into(imageView)
        }
    }

    /**
     * S1968: whether the local image arm should show a placeholder instead of asking Glide.
     *
     * Two reasons folded into one predicate. Either the file is already known to have failed - the
     * negative cache the network arms three branches up already consult, which this arm never did,
     * so the same doomed request was reissued on every rebind (254 identical allocation failures in
     * one sweep) - or its declared size makes the centerCrop target unallocatable, checked before
     * the request is issued and remembered so it is not asked again this session.
     */
    private fun skipLocalImageThumbnail(file: MediaFile): Boolean {
        if (NetworkFileDataFetcher.isThumbnailFailed(file.path)) {
            Timber.v("Skipping local image thumbnail for ${file.name} (cached as failed)")
            return true
        }
        val overBudget = exceedsDecodeBudget(file)
        if (overBudget) {
            Timber.w("Thumbnail target over the decode budget for %s - placeholder, not retried", file.name)
            NetworkFileDataFetcher.markThumbnailAsFailed(file.path)
        }
        return overBudget
    }

    /**
     * True when this file's declared size would make the browse thumbnail unallocatable.
     *
     * Only the header is read (`inJustDecodeBounds`), so nothing is allocated to find out. A file we
     * cannot measure - unreadable, or a header that reports nothing - returns false: the budget has no
     * opinion there, and the ordinary decode path is still allowed to try and fail normally.
     */
    private fun exceedsDecodeBudget(file: MediaFile): Boolean {
        val path = file.path
        if (path.isBlank() || path.contains("://")) return false
        return runCatching {
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, options)
            ThumbnailDecodeBudget.exceedsBudget(
                sourceWidth = options.outWidth,
                sourceHeight = options.outHeight,
                target = CACHED_THUMBNAIL_SIZE,
            )
        }.getOrDefault(false)
    }

    private fun loadVideo(
        imageView: ImageView,
        file: MediaFile,
        context: Context,
        isNetworkPath: Boolean,
        isCloudPath: Boolean,
        generatedPlaceholder: BitmapDrawable,
        isListMode: Boolean,
        isScrolling: Boolean
    ) {
        when {
            isCloudPath -> {
                val provider = detectCloudProvider(file.path)
                val fileId = extractCloudFileId(file.path, provider)
                Glide.with(context)
                    .load(
                        CloudThumbnailData(
                            thumbnailUrl = file.thumbnailUrl ?: "",
                            fileId = fileId,
                            loadFullImage = false,
                            cloudProvider = provider
                        )
                    )
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
                        .shouldSkipNetworkExtraction(ext)
                ) {
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
                    .load(
                        NetworkFileData(
                            path = file.path,
                            credentialsId = getCredentialsId(),
                            loadFullImage = false,
                            size = file.size,
                            createdDate = file.createdDate
                        )
                    )
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
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<android.graphics.drawable.Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            if (isVideoPriorityThumbnailSuspension(e)) {
                                Timber.v("Video thumbnail load suspended by video priority: ${file.name}")
                            } else if (isVideoDecoderException(e)) {
                                NetworkFileDataFetcher.markVideoAsFailed(file.path)
                                Timber.v("Thumbnail load failed: ${file.name} (decoder error, cached)")
                            } else if (e != null) {
                                Timber.w("Thumbnail load failed: ${file.name}, ${e.message}")
                            }
                            if (isListMode) applyPlaceholderStyle(imageView)
                            return false
                        }
                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable,
                            model: Any,
                            target: Target<android.graphics.drawable.Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            GlideCacheStats.recordLoad(dataSource)
                            resetThumbnailStyle(imageView)
                            return false
                        }
                    }).into(imageView)
                } else {
                    videoBuilder.into(imageView)
                }
            }
            else -> loadLocalVideo(imageView, file, context, generatedPlaceholder, isListMode, isScrolling)
        }
    }

    /**
     * S1968: the local-video arm, lifted out of `loadVideo` so its guard has somewhere to return to.
     *
     * Extracted for the same reason the image arm was: `loadVideo` was already at its return budget,
     * so the skip could not be expressed inline without nesting the whole branch in an else.
     */
    private fun loadLocalVideo(
        imageView: ImageView,
        file: MediaFile,
        context: Context,
        generatedPlaceholder: BitmapDrawable,
        isListMode: Boolean,
        isScrolling: Boolean,
    ) {
        if (isListMode && !getShowVideoThumbnails()) {
            showGeneratedPlaceholder(imageView, file)
            return
        }
        // S1968: the local arm never consulted the negative cache the network arm three branches up
        // already used, so a doomed frame extraction was reissued on every rebind.
        if (NetworkFileDataFetcher.isVideoFailed(file.path)) {
            Timber.v("Skipping local video thumbnail for ${file.name} (cached as failed)")
            showGeneratedPlaceholder(imageView, file)
            return
        }
        val localVideoBuilder = Glide.with(context)
            .load(thumbnailModel(file))
            .format(decodeFormatResolver.decodeFormat())
            .signature(ObjectKey("${file.path}_${file.size}"))
            .priority(Priority.NORMAL)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE)
            .centerCrop()
            // S1968: MUST follow centerCrop, which itself sets CENTER_OUTSIDE. That strategy scales to
            // COVER the box, so a container reporting a degenerate frame size derives a target with
            // nothing bounding it - an MPEG-2 file MediaStore cannot measure at all (width and height
            // both NULL) produced 300 x 1970400, ~2.4 GB, 318 times in one sweep. AT_MOST never
            // upscales, so the decode cannot exceed the box no matter what the source claims; the
            // CenterCrop transformation still fills the cell afterwards.
            .downsample(DownsampleStrategy.AT_MOST)
            .onlyRetrieveFromCache(isScrolling)
            .dontAnimate()
            .placeholder(generatedPlaceholder)
            .error(generatedPlaceholder)
            .error(thumbnailErrorRequest(context, file, generatedPlaceholder))
        // S0110: no failure-marking listener during scroll - cache miss triggers .error() placeholder naturally
        if (!isScrolling) {
            localVideoBuilder.listener(object : RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    // S1968: remember it, so a frame this device cannot extract is attempted once a
                    // session rather than once a rebind. Back-pressure cancellations are not failures.
                    if (!isVideoPriorityThumbnailSuspension(e)) {
                        NetworkFileDataFetcher.markVideoAsFailed(file.path)
                        Timber.v("Local video thumbnail failed: ${file.name} (cached, not retried)")
                    }
                    if (isListMode) applyPlaceholderStyle(imageView)
                    return false
                }
                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    model: Any,
                    target: Target<android.graphics.drawable.Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    GlideCacheStats.recordLoad(dataSource)
                    resetThumbnailStyle(imageView)
                    return false
                }
            }).into(imageView)
        } else {
            localVideoBuilder.into(imageView)
        }
    }

    private fun isVideoPriorityThumbnailSuspension(e: GlideException?): Boolean {
        if (e == null) return false

        // ConnectionThrottleManager uses this cancellation as back-pressure; treating it as a
        // failure would poison the persisted failed-thumbnail cache and suppress a later retry.
        if (e.rootCauses.any { cause ->
                cause is CancellationException &&
                    cause.message?.contains(VIDEO_PRIORITY_THUMBNAIL_SUSPEND_MESSAGE) == true
            }
        ) {
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
            }
        ) {
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
            ) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }
}
