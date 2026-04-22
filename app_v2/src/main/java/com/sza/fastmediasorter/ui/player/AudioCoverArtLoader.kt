package com.sza.fastmediasorter.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.MemoryTier
import com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository
import com.sza.fastmediasorter.data.repository.AudioMetadataSaveData
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.AudioMetadata
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SearchAudioCoverUseCase
import com.sza.fastmediasorter.ui.player.helpers.AudioEmptyStateController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Handles audio cover art loading for PlayerActivity:
 * embedded art (MediaMetadataRetriever/ExoPlayer), local metadata cache, and online search fallback.
 */
class AudioCoverArtLoader(
    private val binding: ActivityPlayerUnifiedBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val searchAudioCoverUseCase: SearchAudioCoverUseCase,
    private val audioMetadataCacheRepository: AudioMetadataCacheRepository,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val memoryTier: MemoryTier,
    private val callback: Callback,
) {
    interface Callback {
        fun onAudioMetadataLoaded(metadata: AudioMetadata)
    }

    private var coverArtJob: Job? = null
    private var coverArtDisplayedForPath: String? = null
    private var audioEmptyStateController: AudioEmptyStateController? = null

    fun setAudioEmptyStateController(controller: AudioEmptyStateController) {
        audioEmptyStateController = controller
    }

    /** Hides the audio empty-state animation. Called by ImageLoadingManager when switching to image display. */
    fun hideEmptyState() {
        audioEmptyStateController?.hide()
    }

    /**
     * Load audio cover art. Skips redundant reload if the same file is already visible
     * (ExoPlayer re-fires STATE_READY after every seek).
     */
    fun loadAudioCoverArt(file: MediaFile) {
        if (file.path == coverArtDisplayedForPath && binding.audioCoverArtView.isVisible) {
            Timber.d("loadAudioCoverArt: cover already displayed for ${file.name}, seek re-trigger skipped")
            return
        }
        coverArtDisplayedForPath = null
        coverArtJob?.cancel()
        coverArtJob = null

        val callId = System.currentTimeMillis()
        Timber.d("loadAudioCoverArt[$callId]: file=${file.name}")
        Timber.d("loadAudioCoverArt[$callId]: audioCoverArtView.isVisible=${binding.audioCoverArtView.isVisible}")

        val isNetworkFile = file.path.startsWith("smb://") || file.path.startsWith("sftp://") || file.path.startsWith("ftp://")
        val isCloudFile = file.path.startsWith("cloud://")

        if (isNetworkFile || isCloudFile) {
            // Show empty-state immediately; hide when artwork is found
            lifecycleScope.launch {
                val mode = try { settingsRepository.getSettings().first().audioEmptyStateMode } catch (_: Exception) { AudioEmptyStateController.MODE_NONE }
                audioEmptyStateController?.show(mode)
            }
            coverArtJob = lifecycleScope.launch {
                delay(1500) // Wait for ExoPlayer to extract artwork
                val player = binding.playerView.player
                val hasExoPlayerArtwork = player?.mediaMetadata?.artworkData != null || player?.mediaMetadata?.artworkUri != null
                val exoArtist = player?.mediaMetadata?.artist?.toString()?.trim()?.takeIf { it.isNotBlank() }
                val exoTitle  = player?.mediaMetadata?.title?.toString()?.trim()?.takeIf { it.isNotBlank() }
                Timber.d("Network file artwork check: hasExoPlayerArtwork=$hasExoPlayerArtwork, exoArtist='$exoArtist', exoTitle='$exoTitle'")

                if (hasExoPlayerArtwork) {
                    val artworkData = player?.mediaMetadata?.artworkData
                    val bitmap = if (artworkData != null) {
                        withContext(Dispatchers.IO) {
                            try { android.graphics.BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size) }
                            catch (e: Exception) { Timber.w(e, "Failed to decode ExoPlayer artworkData"); null }
                        }
                    } else null

                    if (bitmap != null) {
                        Timber.d("ExoPlayer embedded artwork decoded (${artworkData?.size} bytes), displaying")
                        audioEmptyStateController?.hide()
                        binding.audioCoverArtView.setImageBitmap(bitmap)
                        binding.audioCoverArtView.isVisible = true
                        coverArtDisplayedForPath = file.path
                        pushArtworkToNotification(bitmap)
                    } else {
                        val settings = settingsRepository.getSettings().first()
                        if (!settings.searchAudioCoversOnline) {
                            withContext(Dispatchers.Main) {
                                audioEmptyStateController?.show(settings.audioEmptyStateMode)
                                    ?: run { binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note); binding.audioCoverArtView.isVisible = true }
                            }
                        } else {
                            Timber.w("ExoPlayer reports artwork but decode failed, searching online")
                            searchOnlineAndDisplayCover(file, exoArtist, exoTitle)
                        }
                    }
                } else {
                    val settings = settingsRepository.getSettings().first()
                    if (!settings.searchAudioCoversOnline) {
                        withContext(Dispatchers.Main) {
                            audioEmptyStateController?.show(settings.audioEmptyStateMode)
                                ?: run { binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note); binding.audioCoverArtView.isVisible = true }
                        }
                    } else {
                        searchOnlineAndDisplayCover(file, exoArtist, exoTitle)
                    }
                }
            }
            return
        }

        // Local file: show empty state immediately, then try embedded art
        lifecycleScope.launch {
            val mode = try { settingsRepository.getSettings().first().audioEmptyStateMode } catch (_: Exception) { AudioEmptyStateController.MODE_NONE }
            audioEmptyStateController?.show(mode)
        }
        Timber.d("loadAudioCoverArt: Starting cover art search for ${file.name}")

        coverArtJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                data class LocalEmbeddedInfo(val bitmap: android.graphics.Bitmap?, val artist: String?, val title: String?)
                val embeddedInfo = withContext(Dispatchers.IO) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        if (file.path.startsWith("content://")) {
                            retriever.setDataSource(binding.root.context, Uri.parse(file.path))
                        } else {
                            retriever.setDataSource(file.path)
                        }
                        val embeddedPicture = retriever.embeddedPicture
                        val id3Artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotBlank() }
                        val id3Title  = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotBlank() }
                        val bitmap = if (embeddedPicture != null) {
                            android.graphics.BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                        } else null
                        LocalEmbeddedInfo(bitmap, id3Artist, id3Title)
                    } catch (e: IllegalArgumentException) {
                        Timber.d("Cover art: file not accessible (${file.path}): ${e.message}")
                        LocalEmbeddedInfo(null, null, null)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to extract embedded cover art")
                        LocalEmbeddedInfo(null, null, null)
                    } finally {
                        retriever.release()
                    }
                }
                val coverBitmap = embeddedInfo.bitmap
                val id3Artist = embeddedInfo.artist
                val id3Title  = embeddedInfo.title

                withContext(Dispatchers.Main) {
                    if (coverBitmap != null) {
                        Timber.d("loadAudioCoverArt[$callId]: EMBEDDED cover found, displaying")
                        audioEmptyStateController?.hide()
                        binding.audioCoverArtView.setImageBitmap(coverBitmap)
                        binding.audioCoverArtView.isVisible = true
                        coverArtDisplayedForPath = file.path
                        pushArtworkToNotification(coverBitmap)
                    } else {
                        val settings = settingsRepository.getSettings().first()
                        if (!settings.searchAudioCoversOnline) {
                            Timber.d("loadAudioCoverArt[$callId]: No embedded cover, online search disabled")
                            audioEmptyStateController?.show(settings.audioEmptyStateMode)
                                ?: run { binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note); binding.audioCoverArtView.isVisible = true }
                            return@withContext
                        }

                        // Check local cache
                        val cached = withContext(Dispatchers.IO) { audioMetadataCacheRepository.readMetadata(file.name) }
                        if (cached != null) {
                            Timber.d("loadAudioCoverArt[$callId]: LOCAL CACHE hit for ${file.name}")
                            if (cached.coverFile != null) {
                                audioEmptyStateController?.hide()
                                Glide.with(binding.audioCoverArtView.context).load(cached.coverFile)
                                    .error(R.drawable.ic_music_note)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    .into(binding.audioCoverArtView)
                                binding.audioCoverArtView.isVisible = true
                                coverArtDisplayedForPath = file.path
                                callback.onAudioMetadataLoaded(AudioMetadata(cached.trackName, cached.artistName, cached.albumName, cached.releaseYear, cached.coverArtUrl))
                                return@withContext
                            }
                            if (cached.coverArtUrl != null) {
                                audioEmptyStateController?.hide()
                                binding.audioCoverArtView.isVisible = true
                                Glide.with(binding.audioCoverArtView.context).load(cached.coverArtUrl)
                                    .error(R.drawable.ic_music_note)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .into(binding.audioCoverArtView)
                                coverArtDisplayedForPath = file.path
                                return@withContext
                            }
                        }

                        // Online search
                        Timber.d("loadAudioCoverArt[$callId]: No embedded cover, searching online")
                        val metadata = withContext(Dispatchers.IO) { searchAudioCoverUseCase(file.name, file.path, id3Artist, id3Title) }
                        val coverUrl = metadata?.coverArtUrl
                        if (coverUrl != null) {
                            callback.onAudioMetadataLoaded(metadata)
                            // Save to local cache if enabled
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    if (!settingsRepository.getSettings().first().saveAudioMetadataLocally) return@launch
                                    val ext = if (coverUrl.contains(".png", ignoreCase = true)) "png" else "jpg"
                                    val imageBytes = downloadImageBytes(coverUrl)
                                    audioMetadataCacheRepository.saveMetadata(file.name, AudioMetadataSaveData(metadata.trackName, metadata.artistName, metadata.albumName, metadata.releaseYear, coverUrl, if (imageBytes != null) ext else null))
                                    if (imageBytes != null) audioMetadataCacheRepository.saveCover(file.name, imageBytes, ext)
                                } catch (e: Exception) { Timber.d(e, "AudioMetadataCache: save failed for %s", file.name) }
                            }
                            Timber.d("loadAudioCoverArt[$callId]: ONLINE cover found: $coverUrl")
                            audioEmptyStateController?.hide()
                            binding.audioCoverArtView.isVisible = true
                            val capturedMode = settings.audioEmptyStateMode
                            val request = Glide.with(binding.audioCoverArtView.context).load(coverUrl)
                                .error(R.drawable.ic_music_note).diskCacheStrategy(DiskCacheStrategy.ALL)
                            if (memoryTier == MemoryTier.LOW) request.format(DecodeFormat.PREFER_RGB_565).dontAnimate().override(512, 512)
                            request.listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                                    Timber.w(e, "Cover art load failed")
                                    audioEmptyStateController?.show(capturedMode) ?: binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                                    return true
                                }
                                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                                    coverArtDisplayedForPath = file.path
                                    (resource as? BitmapDrawable)?.bitmap?.let { pushArtworkToNotification(it) }
                                    return false
                                }
                            }).into(binding.audioCoverArtView)
                        } else {
                            audioEmptyStateController?.show(settingsRepository.getSettings().first().audioEmptyStateMode)
                                ?: run { binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note) }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load audio cover art for ${file.name}")
                withContext(Dispatchers.Main) {
                    val mode = try { settingsRepository.getSettings().first().audioEmptyStateMode } catch (_: Exception) { AudioEmptyStateController.MODE_NONE }
                    audioEmptyStateController?.show(mode) ?: run { binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note); binding.audioCoverArtView.isVisible = true }
                }
            }
        }
    }

    /**
     * Search for cover art online (called for network/cloud files without embedded art).
     * @param id3Artist Artist from ExoPlayer metadata tags
     * @param id3Title  Title from ExoPlayer metadata tags
     */
    private fun searchOnlineAndDisplayCover(file: MediaFile, id3Artist: String? = null, id3Title: String? = null) {
        val callId = System.currentTimeMillis()
        Timber.d("searchOnlineAndDisplayCover[$callId]: START for ${file.name}")
        lifecycleScope.launch(Dispatchers.IO) {
            val mode = try { settingsRepository.getSettings().first().audioEmptyStateMode } catch (_: Exception) { AudioEmptyStateController.MODE_NONE }
            try {
                val cached = audioMetadataCacheRepository.readMetadata(file.name)
                if (cached != null) {
                    Timber.d("searchOnlineAndDisplayCover[$callId]: LOCAL CACHE hit for ${file.name}")
                    withContext(Dispatchers.Main) {
                        if (cached.coverFile != null) {
                            audioEmptyStateController?.hide()
                            Glide.with(binding.audioCoverArtView.context).load(cached.coverFile)
                                .error(R.drawable.ic_music_note).diskCacheStrategy(DiskCacheStrategy.NONE).into(binding.audioCoverArtView)
                            binding.audioCoverArtView.isVisible = true
                            coverArtDisplayedForPath = file.path
                            callback.onAudioMetadataLoaded(AudioMetadata(cached.trackName, cached.artistName, cached.albumName, cached.releaseYear, cached.coverArtUrl))
                            return@withContext
                        }
                        if (cached.coverArtUrl != null) {
                            audioEmptyStateController?.hide()
                            binding.audioCoverArtView.isVisible = true
                            Glide.with(binding.audioCoverArtView.context).load(cached.coverArtUrl)
                                .error(R.drawable.ic_music_note).diskCacheStrategy(DiskCacheStrategy.ALL).into(binding.audioCoverArtView)
                            coverArtDisplayedForPath = file.path
                            return@withContext
                        }
                    }
                    return@launch
                }

                val metadata = searchAudioCoverUseCase(file.name, file.path, id3Artist, id3Title)
                val coverUrl = metadata?.coverArtUrl

                withContext(Dispatchers.Main) {
                    if (coverUrl != null) {
                        callback.onAudioMetadataLoaded(metadata)
                        // Save to local cache if enabled
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                if (!settingsRepository.getSettings().first().saveAudioMetadataLocally) return@launch
                                val ext = if (coverUrl.contains(".png", ignoreCase = true)) "png" else "jpg"
                                val imageBytes = downloadImageBytes(coverUrl)
                                audioMetadataCacheRepository.saveMetadata(file.name, AudioMetadataSaveData(metadata.trackName, metadata.artistName, metadata.albumName, metadata.releaseYear, coverUrl, if (imageBytes != null) ext else null))
                                if (imageBytes != null) audioMetadataCacheRepository.saveCover(file.name, imageBytes, ext)
                            } catch (e: Exception) { Timber.d(e, "AudioMetadataCache: save failed for %s", file.name) }
                        }
                        Timber.d("searchOnlineAndDisplayCover[$callId]: Found URL: $coverUrl")
                        audioEmptyStateController?.hide()
                        binding.audioCoverArtView.isVisible = true
                        val request = Glide.with(binding.audioCoverArtView.context).load(coverUrl)
                            .error(R.drawable.ic_music_note).diskCacheStrategy(DiskCacheStrategy.ALL)
                        if (memoryTier == MemoryTier.LOW) request.format(DecodeFormat.PREFER_RGB_565).dontAnimate().override(512, 512)
                        request.listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                                Timber.w(e, "searchOnlineAndDisplayCover[$callId]: Glide load FAILED")
                                audioEmptyStateController?.show(mode) ?: binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                                return true
                            }
                            override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                                Timber.d("searchOnlineAndDisplayCover[$callId]: Glide loaded from $dataSource")
                                coverArtDisplayedForPath = file.path
                                (resource as? BitmapDrawable)?.bitmap?.let { pushArtworkToNotification(it) }
                                return false
                            }
                        }).into(binding.audioCoverArtView)
                    } else {
                        Timber.d("searchOnlineAndDisplayCover[$callId]: No cover found, showing empty state")
                        audioEmptyStateController?.show(mode) ?: binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "searchOnlineAndDisplayCover[$callId]: EXCEPTION")
                withContext(Dispatchers.Main) {
                    audioEmptyStateController?.show(mode) ?: binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
                }
            }
        }
    }

    /**
     * Pushes resolved cover art to the system media notification via Player.replaceMediaItem.
     * No-op when COMMAND_CHANGE_MEDIA_ITEMS is unavailable (local ExoPlayer, no background service).
     * Bitmap is scaled to max 512×512 and JPEG-compressed at 85% to keep IPC payload small.
     */
    private fun pushArtworkToNotification(bitmap: Bitmap) {
        val player = binding.playerView.player ?: return
        if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return
        val currentIndex = player.currentMediaItemIndex
        val currentItem = player.currentMediaItem ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val maxSide = 512
                val w = bitmap.width; val h = bitmap.height
                val scaled = if (w > maxSide || h > maxSide) {
                    val scale = maxSide.toFloat() / maxOf(w, h)
                    Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
                } else bitmap
                val bytes = java.io.ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                if (scaled !== bitmap) scaled.recycle()
                val updatedItem = currentItem.buildUpon()
                    .setMediaMetadata(currentItem.mediaMetadata.buildUpon().setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build())
                    .build()
                withContext(Dispatchers.Main) {
                    if (player.currentMediaItemIndex == currentIndex) {
                        player.replaceMediaItem(currentIndex, updatedItem)
                        Timber.d("pushArtworkToNotification: pushed ${bytes.size}b at index=$currentIndex")
                    }
                }
            } catch (e: Exception) { Timber.w(e, "pushArtworkToNotification: failed") }
        }
    }

    private suspend fun downloadImageBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.bytes()
            }
        } catch (e: Exception) { Timber.d(e, "downloadImageBytes: failed for %s", url); null }
    }
}
