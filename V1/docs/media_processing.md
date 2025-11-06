# Media File Processing in FastMediaSorter Mobile

## Media Processing Overview

The application supports processing of images and video files from local storage and network SMB resources. Specialized libraries are used for efficient loading, decoding, and displaying of media content.

## Media Components Architecture

```
Media Files (Local/SMB)
    │
    ▼
MediaValidator → MediaUtils
    │
    ▼
Glide (Images) / ExoPlayer (Video)
    │
    ▼
PhotoView / PlayerView
    │
    ▼
UI Components
```

## Key Components

### MediaUtils
Helper functions for working with media files.

```kotlin
object MediaUtils {
    
    // File validation
    fun isValidMediaFile(fileName: String): Boolean
    
    // File type determination
    fun getMediaType(fileName: String): MediaType
    
    // Metadata retrieval
    fun getImageMetadata(uri: Uri): ImageMetadata?
    
    // File size formatting
    fun formatFileSize(bytes: Long): String
    
    // Video codec support checking
    fun isVideoSupported(mimeType: String): Boolean
}
```

### MediaValidator
Class for media file validation and analysis.

```kotlin
class MediaValidator {
    
    // File integrity checking
    fun validateFile(uri: Uri): ValidationResult
    
    // Supported formats analysis
    fun getSupportedFormats(): List<String>
    
    // File size checking
    fun isFileSizeAcceptable(size: Long): Boolean
    
    // Video stream validation
    fun validateVideoStream(uri: Uri): VideoValidationResult
}
```

### ImageMetadata
```kotlin
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val orientation: Int,
    val mimeType: String,
    val fileSize: Long,
    val dateTaken: Long?
)
```

## Image Processing

### Glide Integration

#### Glide Configuration
```kotlin
// AppGlideModule
@GlideModule
class AppGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setDefaultRequestOptions(
            RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565)
                .disallowHardwareConfig()
        )
    }
}
```

#### Image Loading
```kotlin
Glide.with(context)
    .load(uri)
    .placeholder(R.drawable.placeholder)
    .error(R.drawable.error)
    .transition(DrawableTransitionOptions.withCrossFade())
    .into(imageView)
```

#### SMB Specific Features
```kotlin
// Custom ModelLoader for SMB URLs
class SmbModelLoader : ModelLoader<String, InputStream> {
    override fun buildLoadData(
        model: String,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData(
            ObjectKey(model),
            SmbDataFetcher(model, smbClient)
        )
    }
}
```

### PhotoView Integration

#### Scaling Setup
```kotlin
photoView.apply {
    maximumScale = 5f
    mediumScale = 3f
    minimumScale = 1f
    scaleType = ImageView.ScaleType.FIT_CENTER
}

// Double tap handling
photoView.setOnDoubleTapListener { view, x, y ->
    // Scaling logic
}
```

#### Control Gestures
- **Pinch to zoom:** Two-finger scaling
- **Pan:** Movement when zoomed
- **Double tap:** Toggle between fit and actual size
- **Single tap:** Show/hide UI elements

## Video Processing

### ExoPlayer Integration

#### Player Configuration
```kotlin
val player = ExoPlayer.Builder(context)
    .setMediaSourceFactory(
        DefaultMediaSourceFactory(context).setDataSourceFactory(
            SmbDataSourceFactory(smbClient, config)
        )
    )
    .build()
```

#### Video Playback
```kotlin
val mediaItem = MediaItem.fromUri(uri)
player.setMediaItem(mediaItem)
player.prepare()
player.play()
```

#### State Handling
```kotlin
player.addListener(object : Player.Listener {
    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_READY -> showVideoControls()
            Player.STATE_ENDED -> onVideoEnded()
            Player.STATE_BUFFERING -> showBuffering()
        }
    }
    
    override fun onPlayerError(error: PlaybackException) {
        handleVideoError(error)
    }
})
```

### Video Controls

#### Custom Controls
```kotlin
class VideoControlsView : FrameLayout {
    private var player: Player? = null
    
    fun setPlayer(player: Player) {
        this.player = player
        setupControls()
    }
    
    private fun setupControls() {
        // Play/Pause button
        playPauseButton.setOnClickListener {
            if (player?.isPlaying == true) {
                player?.pause()
            } else {
                player?.play()
            }
        }
        
        // Progress bar
        progressBar.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                player?.seekTo(value.toLong())
            }
        }
    }
}
```

## Supported Formats

### Images
- **JPEG/JPG:** Baseline, Progressive
- **PNG:** 8-bit, 24-bit, 32-bit
- **GIF:** Animated, Static
- **WebP:** Lossy, Lossless, Animated
- **BMP:** Standard BMP formats
- **TIFF:** Basic TIFF support

### Videos
- **MP4:** H.264, H.265, MPEG-4
- **MOV:** QuickTime containers
- **AVI:** Basic AVI support
- **MKV:** Matroska containers
- **WebM:** VP8, VP9 codecs

## Performance Optimizations

### Preloading
```kotlin
class MediaPreloader {
    private var nextImageData: ByteArray? = null
    private var preloadJob: Job? = null
    
    fun preloadNext(uri: Uri) {
        preloadJob?.cancel()
        preloadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                nextImageData = loadImageData(uri)
            } catch (e: Exception) {
                // Handle preload error
            }
        }
    }
    
    fun getPreloadedData(): ByteArray? {
        val data = nextImageData
        nextImageData = null
        return data
    }
}
```

### Caching
- **Memory cache:** LruCache for active images
- **Disk cache:** Glide disk cache for persistence
- **Bitmap pool:** Bitmap object reuse

### Asynchronous Processing
```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    val bitmap = decodeBitmap(uri)
    withContext(Dispatchers.Main) {
        imageView.setImageBitmap(bitmap)
    }
}
```

## Error Handling

### Image Errors
- **Corrupted file:** Skip with logging
- **Unsupported format:** Fallback to placeholder
- **Out of memory:** Compress and retry
- **Network timeout:** For SMB files

### Video Errors
- **Codec not supported:** Fallback to image
- **Network buffering:** Show loading indicator
- **Playback error:** Diagnostics and recovery
- **DRM protected:** Not supported

### Diagnostics
```kotlin
data class MediaError(
    val fileName: String,
    val errorType: String,
    val errorMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

val errorLog = mutableListOf<MediaError>()
```

## Accessibility and UX

### Adaptive Display
- **Orientation changes:** Automatic adaptation
- **Screen sizes:** Scaling for different devices
- **Performance modes:** Quality settings for low-end devices

### Accessibility Features
- **Content descriptions:** For screen readers
- **Touch targets:** Minimum control element size
- **High contrast:** Dark theme support

## Monitoring and Analytics

### Performance Metrics
- **Load times:** Media loading times
- **Cache hit rate:** Caching efficiency
- **Error rates:** Error frequency by type

### Debug Information
- **Media info:** Resolution, size, format
- **Network stats:** Loading speed, delays
- **Memory usage:** Player memory consumption

## Future Improvements

### New Formats
- **AVIF:** Modern image format
- **HEIC/HEIF:** Apple formats
- **HDR:** High dynamic range video

### Extended Features
- **Video editing:** Basic editing
- **Image filters:** Effect application
- **Cloud integration:** Google Photos, iCloud

### Optimizations
- **Hardware acceleration:** Improved decoding
- **Adaptive streaming:** For network videos
- **Offline caching:** Preloading</content>
<parameter name="filePath">c:\GIT\FastMediaSorter_mobile\docs\media_processing.md