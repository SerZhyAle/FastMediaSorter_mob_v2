package com.sza.fastmediasorter.di

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import com.sza.fastmediasorter.data.cloud.glide.GoogleDriveThumbnailData
import com.sza.fastmediasorter.data.cloud.glide.GoogleDriveThumbnailModelLoader
import com.sza.fastmediasorter.data.cloud.glide.CloudThumbnailData
import com.sza.fastmediasorter.data.cloud.glide.CloudThumbnailModelLoader
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.data.network.glide.NetworkFileModelLoaderFactory
import com.sza.fastmediasorter.data.network.glide.NetworkVideoFrameDecoder
import com.sza.fastmediasorter.data.network.glide.NetworkFileModelLoaderEntryPoint
import com.sza.fastmediasorter.data.glide.PdfPageDecoder
import com.sza.fastmediasorter.data.glide.EpubCoverDecoder
import com.sza.fastmediasorter.data.glide.NetworkPdfThumbnailLoader
import com.sza.fastmediasorter.data.glide.NetworkEpubCoverLoader
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber
import java.io.File
import java.io.InputStream

/**
 * Glide configuration module.
 * Registers custom ModelLoader for network files (SMB/SFTP/FTP).
 *
 * Memory cache: 10% of Java heap (maxMemory), capped at 64MB.
 * Disk cache: Configurable via AppSettings (default 2GB).
 * RGB_565: enabled globally on LOW-tier devices (heap < 256MB).
 */
@GlideModule
class GlideAppModule : AppGlideModule() {
    
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Set log level to ERROR to suppress verbose "Load failed for" messages
        builder.setLogLevel(android.util.Log.ERROR)
        
        // Memory cache: 10% of Java heap limit, capped at 64MB.
        // IMPORTANT: Use maxMemory() (heap limit per process), NOT availMem (system free RAM).
        // availMem * 40% was a critical bug — it reserved 160MB from a 512MB heap → 32%.
        val maxHeapBytes = Runtime.getRuntime().maxMemory()
        val memoryCacheSize = minOf((maxHeapBytes * 0.10).toLong(), 64L * 1024 * 1024)

        builder.setMemoryCache(LruResourceCache(memoryCacheSize))
        
        // Disk cache: Read from SharedPreferences (synchronous and reliable)
        val diskCacheSizeMb = try {
            val prefs = context.getSharedPreferences("glide_config", Context.MODE_PRIVATE)
            val cacheSizeMb = prefs.getInt("cache_size_mb", 2048) // Default 2GB
            Timber.i("GlideAppModule: Read cache_size_mb from SharedPreferences: ${cacheSizeMb}MB")
            cacheSizeMb
        } catch (e: Exception) {
            Timber.w(e, "Failed to read cache size from SharedPreferences, using default 2048MB")
            2048
        }.coerceIn(512, 16384)
        val diskCacheSize = diskCacheSizeMb.toLong() * 1024L * 1024L // Convert MB to bytes
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, "image_cache", diskCacheSize))
        
        // Detect device tier using heap size (after GlideModule is initialized, Context is available)
        val tier = com.sza.fastmediasorter.core.util.MemoryTier.detect(context)

        // Disable bitmap reuse to prevent IllegalArgumentException with mismatched sizes
        // especially for network sources where image dimensions may vary.
        // On LOW/STANDARD tier devices: use PREFER_RGB_565 globally (50% less memory per decoded bitmap).
        val baseOptions = com.bumptech.glide.request.RequestOptions()
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.RESOURCE)
        val defaultOptions = if (tier == com.sza.fastmediasorter.core.util.MemoryTier.LOW) {
            baseOptions.format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
        } else {
            baseOptions
        }
        builder.setDefaultRequestOptions(defaultOptions)

        Timber.i("GlideAppModule: *** CACHE CONFIGURED *** Memory=${memoryCacheSize / 1024 / 1024}MB (heap=${maxHeapBytes / 1024 / 1024}MB), Disk=${diskCacheSizeMb}MB, tier=$tier, rgb565=${tier == com.sza.fastmediasorter.core.util.MemoryTier.LOW}")
    }
    
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Get dependencies from Hilt for video decoder
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            NetworkFileModelLoaderEntryPoint::class.java
        )
        
        // Register custom ModelLoader for NetworkFileData
        // This handles SMB/SFTP/FTP image loading with buffering to prevent thread interrupt issues
        registry.prepend(
            NetworkFileData::class.java,
            InputStream::class.java,
            NetworkFileModelLoaderFactory()
        )

        // Register passthrough ModelLoader for NetworkFileData -> NetworkFileData
        // This allows NetworkVideoFrameDecoder to receive the NetworkFileData object
        registry.prepend(
            NetworkFileData::class.java,
            NetworkFileData::class.java,
            com.sza.fastmediasorter.data.network.glide.NetworkFileDataPassthroughModelLoader.Factory()
        )
        
        // Register video frame decoder for network videos
        registry.prepend(
            NetworkFileData::class.java,
            Drawable::class.java,
            NetworkVideoFrameDecoder(
                smbClient = entryPoint.smbClient(),
                sftpClient = entryPoint.sftpClient(),
                ftpClient = entryPoint.ftpClient(),
                credentialsRepository = entryPoint.credentialsRepository(),
                thumbnailCacheRepository = entryPoint.thumbnailCacheRepository(),
                bitmapPool = glide.bitmapPool
            )
        )
        
        // Register Google Drive thumbnail loader with authentication (legacy)
        registry.prepend(
            GoogleDriveThumbnailData::class.java,
            InputStream::class.java,
            GoogleDriveThumbnailModelLoader.Factory(context)
        )
        
        // Register universal cloud thumbnail loader (Google Drive, OneDrive, Dropbox)
        registry.prepend(
            CloudThumbnailData::class.java,
            InputStream::class.java,
            CloudThumbnailModelLoader.Factory(context)
        )
        
        // Register PDF page decoder for local PDF thumbnail generation
        registry.prepend(
            File::class.java,
            Bitmap::class.java,
            PdfPageDecoder()
        )
        
        // Register EPUB cover decoder for local EPUB e-book covers
        registry.prepend(
            File::class.java,
            Bitmap::class.java,
            EpubCoverDecoder()
        )
        
        // Register network PDF thumbnail loader for SMB/SFTP/FTP PDFs
        registry.prepend(
            NetworkFileData::class.java,
            Bitmap::class.java,
            NetworkPdfThumbnailLoader.Factory(
                context = context,
                smbClient = entryPoint.smbClient(),
                sftpClient = entryPoint.sftpClient(),
                ftpClient = entryPoint.ftpClient(),
                credentialsRepository = entryPoint.credentialsRepository(),
                unifiedCache = entryPoint.unifiedCache()
            )
        )
        
        // Register network EPUB cover loader for SMB/SFTP/FTP EPUBs
        registry.prepend(
            NetworkFileData::class.java,
            Bitmap::class.java,
            NetworkEpubCoverLoader.Factory(
                context = context,
                smbClient = entryPoint.smbClient(),
                sftpClient = entryPoint.sftpClient(),
                ftpClient = entryPoint.ftpClient(),
                credentialsRepository = entryPoint.credentialsRepository()
            )
        )
        
        Timber.d("GlideAppModule: Registered NetworkFileModelLoaderFactory, NetworkVideoFrameDecoder, GoogleDriveThumbnailModelLoader, PdfPageDecoder, EpubCoverDecoder, NetworkPdfThumbnailLoader, and NetworkEpubCoverLoader")
    }
    
    override fun isManifestParsingEnabled(): Boolean {
        // Disable manifest parsing for faster initialization
        return false
    }
}
