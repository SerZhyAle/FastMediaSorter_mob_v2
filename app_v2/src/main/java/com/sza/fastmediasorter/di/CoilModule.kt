package com.sza.fastmediasorter.di

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.size.Precision
import coil.util.DebugLogger
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.coil.NetworkFileFetcher
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Dispatcher
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        smbClient: SmbClient,
        sftpClient: SftpClient,
        ftpClient: FtpClient,
        credentialsRepository: NetworkCredentialsRepository
    ): ImageLoader {
        // Configure dispatcher for parallel network requests
        val dispatcher = Dispatcher().apply {
            maxRequests = 12 // Increase from default 64 to limit parallel SMB connections
            maxRequestsPerHost = 4 // Limit per host to avoid overwhelming SMB server
        }
        
        // Calculate optimal memory cache size based on device RAM
        val memoryCache = calculateMemoryCacheSize(context)
        Timber.d("CoilModule: Configured memory cache size: $memoryCache bytes (${memoryCache / 1024 / 1024}MB)")
        
        return ImageLoader.Builder(context)
            .components {
                // Add GIF decoder for animated GIFs
                // Use ImageDecoderDecoder on Android 9+ (API 28+), fallback to GifDecoder
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                
                // Add video frame decoder for local video thumbnails
                add(VideoFrameDecoder.Factory())
                
                // Add network file fetcher for SMB/SFTP/FTP thumbnails
                add(NetworkFileFetcher.Factory(smbClient, sftpClient, ftpClient, credentialsRepository))
            }
            .crossfade(true)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED) // Enable disk cache
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED) // Enable memory cache
            .memoryCache {
                coil.memory.MemoryCache.Builder(context)
                    .maxSizeBytes(memoryCache)
                    .build()
            }
            // Enable bitmap downsampling for large images
            // This reduces memory usage by loading scaled-down bitmaps
            .precision(Precision.INEXACT) // Allow Coil to downsample images
            .allowHardware(true) // Use hardware bitmaps for better performance (Android 8+)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565) // Use RGB_565 for non-transparent images (saves 50% memory)
            .respectCacheHeaders(false) // Ignore cache headers from network files
            // Note: Coil automatically applies EXIF orientation transformations for all images
            // No additional configuration needed for EXIF auto-rotation
            .build()
    }
    
    /**
     * Calculate optimal memory cache size based on device RAM
     * Uses 15-25% of available memory depending on device class
     */
    private fun calculateMemoryCacheSize(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalMemoryMB = memoryInfo.totalMem / 1024 / 1024
        
        // Determine cache percentage based on available RAM
        val cachePercentage = when {
            totalMemoryMB >= 8192 -> 0.25 // 25% for 8GB+ devices (high-end)
            totalMemoryMB >= 4096 -> 0.20 // 20% for 4-8GB devices (mid-high)
            totalMemoryMB >= 2048 -> 0.15 // 15% for 2-4GB devices (mid-range)
            else -> 0.10 // 10% for <2GB devices (low-end)
        }
        
        val cacheSize = (memoryInfo.totalMem * cachePercentage).toInt()
        
        Timber.d("CoilModule: Device RAM: ${totalMemoryMB}MB, cache percentage: ${(cachePercentage * 100).toInt()}%")
        
        return cacheSize
    }
}
