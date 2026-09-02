---
layout: default
title: "FastMedia Wear OS - Detailed Development Steps"
permalink: /docs/WEAR_OS_IMPLEMENTATION_STEPS.html
---
# FastMedia Wear OS - Detailed Development Steps

**Version**: 1.0  
**Date**: 2026-01-27  
**Type**: Step-by-Step Implementation Guide

> **Superseded in part (S1839).** Every `slideshowWaitForFinish` / `toggleWaitForFinish` /
> `wait_for_finish` element in the steps and code listings below was removed from the watch. Do not
> re-add it: the watch already plays a file to its end in all three players, so the switch had
> nothing to turn off. The slideshow steps still apply minus that field.

---

## How to Use This Document

Each step below represents an **atomic development task** that should be:

1. Implemented completely
2. Built successfully (`.\scripts\builders\build-wear-debug.PS1`)
3. Tested on emulator/device
4. Committed to git with meaningful message

**Format**:

- **Step ID**: Unique identifier
- **Task Description**: Detailed prompt for implementation
- **Files to Create/Modify**: List of affected files
- **Build Target**: Gradle task to verify
- **Commit Message Template**: Suggested commit message

---

# PHASE 1: Settings Foundation

## Step 1.1: Create DataStore Preferences Repository Interface

**Task Description**:
Create a domain-layer interface for Wear OS preferences management. This interface will define methods to read and write application settings using Kotlin Coroutines Flow.

Define the following settings keys:

- Media type toggles (audio, video, images)
- Slideshow configuration (enabled, interval; the "wait for finish" key was removed in S1839)
- Album art download toggle

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearPreferencesRepository.kt`

**Implementation Details**:

```kotlin
interface WearPreferencesRepository {
    // Media type toggles
    val isAudioEnabled: Flow<Boolean>
    val isVideoEnabled: Flow<Boolean>
    val isImagesEnabled: Flow<Boolean>

    suspend fun setAudioEnabled(enabled: Boolean)
    suspend fun setVideoEnabled(enabled: Boolean)
    suspend fun setImagesEnabled(enabled: Boolean)

    // Slideshow settings
    val isSlideshowEnabled: Flow<Boolean>
    val slideshowIntervalSeconds: Flow<Int>
    val slideshowWaitForFinish: Flow<Boolean>

    suspend fun setSlideshowEnabled(enabled: Boolean)
    suspend fun setSlideshowIntervalSeconds(seconds: Int)
    suspend fun setSlideshowWaitForFinish(wait: Boolean)

    // Album art settings
    val downloadAlbumArt: Flow<Boolean>
    suspend fun setDownloadAlbumArt(enabled: Boolean)
}
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add WearPreferencesRepository interface for settings management`

---

## Step 1.2: Implement DataStore Preferences Repository

**Task Description**:
Implement the WearPreferencesRepository interface using AndroidX DataStore. Create a concrete implementation that persists settings to DataStore Preferences.

Add DataStore dependency to `wear/build.gradle.kts` if not present:

```kotlin
implementation("androidx.datastore:datastore-preferences:1.1.7")
```

Create PreferencesKeys object with all settings keys and default values.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/data/preferences/WearPreferencesRepositoryImpl.kt`

**Implementation Details**:

- Use `preferencesDataStore` delegate for DataStore instance
- Implement all interface methods using `dataStore.data.map { }` for reads
- Implement all interface methods using `dataStore.edit { }` for writes
- Set default values: audio=true, video=true, images=true, slideshow=false, interval=5, wait=false, albumArt=false

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Implement WearPreferencesRepositoryImpl with DataStore`

---

## Step 1.3: Add Preferences Repository to Hilt DI Module

**Task Description**:
Register WearPreferencesRepository in Hilt dependency injection module to make it available throughout the app.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/di/WearAppModule.kt`

**Implementation Details**:

```kotlin
@Provides
@Singleton
fun provideWearPreferencesRepository(
    @ApplicationContext context: Context
): WearPreferencesRepository {
    return WearPreferencesRepositoryImpl(context)
}
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add WearPreferencesRepository to Hilt DI module`

---

## Step 1.4: Create Settings UI State Data Class

**Task Description**:
Create a data class to represent the UI state of the Settings screen. This will hold all current setting values and loading state.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsUiState.kt`

**Implementation Details**:

```kotlin
data class SettingsUiState(
    val isLoading: Boolean = true,

    // Media types
    val isAudioEnabled: Boolean = true,
    val isVideoEnabled: Boolean = true,
    val isImagesEnabled: Boolean = true,

    // Slideshow
    val isSlideshowEnabled: Boolean = false,
    val slideshowIntervalSeconds: Int = 5,
    val slideshowWaitForFinish: Boolean = false,

    // Album art
    val downloadAlbumArt: Boolean = false,

    // App info
    val appVersion: String = "",
    val buildNumber: String = ""
)
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add SettingsUiState data class`

---

## Step 1.5: Create Settings ViewModel

**Task Description**:
Create a ViewModel for the Settings screen that loads current settings from repository and provides methods to update them. Use StateFlow to expose UI state.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsViewModel.kt`

**Implementation Details**:

- Inject WearPreferencesRepository via Hilt
- Inject application Context to read app version from BuildConfig
- Combine all settings Flows into single SettingsUiState
- Provide methods: `toggleAudio()`, `toggleVideo()`, `toggleImages()`, `toggleSlideshow()`, `setSlideshowInterval(Int)`, `toggleWaitForFinish()`, `toggleAlbumArt()`
- Use `viewModelScope.launch` for write operations
- Mark as `@HiltViewModel`

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add SettingsViewModel with settings management logic`

---

## Step 1.6: Create Settings Screen UI

**Task Description**:
Create a Wear OS optimized settings screen using ScalingLazyColumn with toggles for all settings and app version display.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsScreen.kt`

**Implementation Details**:
Use `ScalingLazyColumn` with the following items:

1. Header: "Settings" (title2 style)
2. Section: "Media Types"
   - ToggleChip: "Enable Audio"
   - ToggleChip: "Enable Video"
   - ToggleChip: "Enable Images"
3. Section: "Slideshow"
   - ToggleChip: "Enable Slideshow"
   - Chip: "Interval: Xs" (shows picker dialog)
   - ToggleChip: "Wait for Finish"
4. Section: "Audio"
   - ToggleChip: "Download Album Art"
5. Section: "About"
   - Text: "Version: X.X.X"
   - Text: "Build: XXXXX"

Use `hiltViewModel()` to obtain ViewModel and `collectAsState()` for state observation.

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add Settings screen UI with all toggles and app info`

---

## Step 1.7: Add Settings Navigation Route

**Task Description**:
Add "settings" navigation route to MainActivity and add Settings button to Home Screen.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/MainActivity.kt`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeScreen.kt`

**Implementation Details**:

1. In MainActivity, add navigation composable:

```kotlin
composable("settings") {
    SettingsScreen(navController = navController)
}
```

1. In HomeScreen, add Settings category after existing categories:

```kotlin
MediaCategory("⚙️ Settings", "settings")
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add Settings navigation and Home screen button`

---

## Step 1.8: Add String Resources for Settings

**Task Description**:
Add all required string resources for Settings screen to strings.xml.

**Files to Modify**:

- `wear/src/main/res/values/strings.xml`

**Implementation Details**:
Add the following strings:

- `settings` = "Settings"
- `media_types` = "Media Types"
- `enable_audio` = "Enable Audio"
- `enable_video` = "Enable Video"
- `enable_images` = "Enable Images"
- `slideshow_settings` = "Slideshow"
- `enable_slideshow` = "Enable Slideshow"
- `slideshow_interval` = "Interval: %ds"
- `wait_for_finish` = "Wait for Finish"
- `audio_settings` = "Audio Settings"
- `download_album_art` = "Download Album Art"
- `about` = "About"
- `version` = "Version: %s"
- `build_number` = "Build: %s"

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add string resources for Settings screen`

---

## Step 1.9: Implement Media Type Filtering in Browse

**Task Description**:
Update BrowseViewModel to check settings before loading media files. If media type is disabled, show appropriate message.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/browse/BrowseViewModel.kt`

**Implementation Details**:

- Inject WearPreferencesRepository
- Before calling `mediaRepository.getMediaFiles()`, check if media type is enabled
- If disabled, emit `BrowseUiState.Empty("This media type is disabled in settings")`
- Combine settings Flow with media files Flow

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add media type filtering in BrowseViewModel based on settings`

---

## Step 1.10: Filter Home Screen Categories by Settings

**Task Description**:
Update HomeScreen to hide disabled media type categories based on settings.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeScreen.kt`

**Implementation Details**:

- Add parameter `settingsViewModel: SettingsViewModel = hiltViewModel()`
- Collect settings state: `val settings by settingsViewModel.uiState.collectAsState()`
- Filter `mediaCategories` list based on enabled flags:

  ```kotlin
  val filteredCategories = mediaCategories.filter { category ->
      when (category.route) {
          "music" -> settings.isAudioEnabled
          "videos" -> settings.isVideoEnabled
          "photos" -> settings.isImagesEnabled
          else -> true
      }
  }
  ```

- Use `filteredCategories` in ScalingLazyColumn

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Filter Home screen categories based on settings`

---

# PHASE 2: Slideshow Feature

## Step 2.1: Create Slideshow Controller Interface

**Task Description**:
Create a controller interface that manages slideshow state and auto-advance logic for all media types.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/slideshow/SlideshowController.kt`

**Implementation Details**:

```kotlin
interface SlideshowController {
    val isActive: StateFlow<Boolean>
    val currentIndex: StateFlow<Int>

    fun start()
    fun stop()
    fun pause()
    fun resume()
    fun next()
    fun previous()
}
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add SlideshowController interface`

---

## Step 2.2: Implement Image Slideshow Controller

**Task Description**:
Implement slideshow controller for images with timer-based auto-advance.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/slideshow/ImageSlideshowController.kt`

**Implementation Details**:

- Use CoroutineScope with delay() for timer
- Read interval from WearPreferencesRepository
- Emit currentIndex updates
- Implement start/stop/pause/resume logic
- Support manual next/previous

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Implement ImageSlideshowController with timer-based advance`

---

## Step 2.3: Update ImageViewerViewModel for Slideshow

**Task Description**:
Integrate slideshow controller into ImageViewerViewModel to support auto-advance.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/image/ImageViewerViewModel.kt`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/image/ImageViewerUiState.kt`

**Implementation Details**:

- Inject WearPreferencesRepository and ImageSlideshowController
- Add `isSlideshowActive: Boolean` to ImageViewerUiState
- Add methods: `startSlideshow()`, `stopSlideshow()`, `toggleSlideshow()`
- Observe slideshow currentIndex and navigate automatically
- Check settings.isSlideshowEnabled before starting

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add slideshow support to ImageViewerViewModel`

---

## Step 2.4: Add Slideshow Controls to ImageViewerScreen

**Task Description**:
Add UI controls to start/stop slideshow in ImageViewerScreen.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/image/ImageViewerScreen.kt`

**Implementation Details**:

- Add slideshow toggle button (play/pause icon)
- Show indicator when slideshow is active
- Add slideshow status text ("Slideshow: ON/OFF")
- Button calls `viewModel.toggleSlideshow()`

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add slideshow controls to ImageViewerScreen UI`

---

## Step 2.5: Add Auto-Advance on Audio Track Complete

**Task Description**:
Update AudioPlayerViewModel to automatically advance to next track when current track finishes (if slideshow enabled and "wait for finish" is on).

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerViewModel.kt`

**Implementation Details**:

- Inject WearPreferencesRepository
- In `onPlaybackStateChanged()`, when `Player.STATE_ENDED`:
  - Check if `settings.isSlideshowEnabled && settings.slideshowWaitForFinish`
  - If true, navigate to next track automatically
  - Need to pass navController or create navigation callback

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add auto-advance to next track in AudioPlayerViewModel`

---

## Step 2.6: Add Auto-Advance on Video Complete

**Task Description**:
Update VideoPlayerViewModel to automatically advance to next video when current video finishes (if slideshow enabled and "wait for finish" is on).

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerViewModel.kt`

**Implementation Details**:

- Inject WearPreferencesRepository
- In `onPlaybackStateChanged()`, when `Player.STATE_ENDED`:
  - Check if `settings.isSlideshowEnabled && settings.slideshowWaitForFinish`
  - If true, navigate to next video automatically

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add auto-advance to next video in VideoPlayerViewModel`

---

## Step 2.7: Add Slideshow String Resources

**Task Description**:
Add string resources for slideshow feature.

**Files to Modify**:

- `wear/src/main/res/values/strings.xml`

**Implementation Details**:
Add strings:

- `slideshow_active` = "Slideshow Active"
- `slideshow_paused` = "Slideshow Paused"
- `start_slideshow` = "Start Slideshow"
- `stop_slideshow` = "Stop Slideshow"

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add slideshow string resources`

---

# PHASE 3: Album Art Download

## Step 3.1: Add Retrofit Dependencies

**Task Description**:
Add Retrofit and OkHttp dependencies to wear module build.gradle.kts for iTunes API integration.

**Files to Modify**:

- `wear/build.gradle.kts`

**Implementation Details**:
Add to dependencies block:

```kotlin
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `build(wear): Add Retrofit dependencies for album art API`

---

## Step 3.2: Create iTunes API Data Models

**Task Description**:
Create data classes for iTunes Search API response.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/itunes/ITunesSearchResponse.kt`

**Implementation Details**:

```kotlin
data class ITunesSearchResponse(
    val resultCount: Int,
    val results: List<ITunesTrack>
)

data class ITunesTrack(
    val artistName: String?,
    val collectionName: String?,
    val artworkUrl100: String?,
    val artworkUrl60: String?
) {
    fun getHighResArtworkUrl(): String? {
        return artworkUrl100?.replace("100x100", "600x600")
    }
}
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add iTunes API data models`

---

## Step 3.3: Create iTunes API Service Interface

**Task Description**:
Create Retrofit service interface for iTunes Search API.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/itunes/ITunesApiService.kt`

**Implementation Details**:

```kotlin
interface ITunesApiService {
    @GET("search")
    suspend fun searchAlbumArt(
        @Query("term") searchTerm: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 1
    ): ITunesSearchResponse
}
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add iTunes API service interface`

---

## Step 3.4: Add iTunes API to Hilt Module

**Task Description**:
Provide Retrofit and iTunes API service in Hilt module.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/di/WearAppModule.kt`

**Implementation Details**:

```kotlin
@Provides
@Singleton
fun provideRetrofit(): Retrofit {
    return Retrofit.Builder()
        .baseUrl("https://itunes.apple.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

@Provides
@Singleton
fun provideITunesApiService(retrofit: Retrofit): ITunesApiService {
    return retrofit.create(ITunesApiService::class.java)
}
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add iTunes API service to Hilt DI`

---

## Step 3.5: Create Album Art Repository

**Task Description**:
Create a repository for fetching and caching album artwork.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/data/albumart/AlbumArtRepository.kt`

**Implementation Details**:

- Inject ITunesApiService
- Inject Context for file caching
- Method: `suspend fun getAlbumArt(artist: String, album: String): Result<String?>`
- Cache downloaded URLs in SharedPreferences
- Return cached URL if available, otherwise query iTunes API

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add AlbumArtRepository for artwork fetching and caching`

---

## Step 3.6: Update AudioPlayerViewModel with Album Art

**Task Description**:
Integrate album art loading into AudioPlayerViewModel.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerViewModel.kt`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerUiState.kt`

**Implementation Details**:

- Inject AlbumArtRepository and WearPreferencesRepository
- Add `albumArtUrl: String?` to AudioPlayerUiState
- In `loadMediaFile()`, check if `settings.downloadAlbumArt == true`
- If true, call `albumArtRepository.getAlbumArt()` with artist/album from metadata
- Update UI state with artwork URL

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add album art loading to AudioPlayerViewModel`

---

## Step 3.7: Update AudioPlayerScreen to Display Album Art

**Task Description**:
Update AudioPlayerScreen UI to display downloaded album artwork using Coil.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerScreen.kt`

**Implementation Details**:

- Use `AsyncImage` from Coil to load `uiState.albumArtUrl`
- Show placeholder if URL is null
- Add error handling for failed loads
- Size: circular image, 80.dp diameter

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Display album art in AudioPlayerScreen UI`

---

# PHASE 4: Network Storage - SMB

## Step 4.1: Add SMB Dependency

**Task Description**:
Add SMBJ library dependency to wear module.

**Files to Modify**:

- `wear/build.gradle.kts`

**Implementation Details**:

```kotlin
implementation("com.hierynomus:smbj:0.12.1")
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `build(wear): Add SMBJ dependency for SMB support`

---

## Step 4.2: Create Network Source Model

**Task Description**:
Create data models for network storage sources.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/NetworkSource.kt`

**Implementation Details**:

```kotlin
enum class NetworkSourceType {
    SMB, FTP, SFTP, GOOGLE_DRIVE
}

data class NetworkSource(
    val id: String = UUID.randomUUID().toString(),
    val type: NetworkSourceType,
    val name: String,
    val server: String,
    val port: Int = when(type) {
        NetworkSourceType.SMB -> 445
        NetworkSourceType.FTP -> 21
        NetworkSourceType.SFTP -> 22
        NetworkSourceType.GOOGLE_DRIVE -> 443
    },
    val username: String,
    val password: String, // Will be encrypted
    val shareName: String? = null, // For SMB
    val basePath: String = "/"
)
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add NetworkSource data models`

---

## Step 4.3: Create SMB Data Source

**Task Description**:
Create SMB data source implementation using SMBJ library.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/smb/SmbDataSource.kt`

**Implementation Details**:

- Class with methods: `connect()`, `disconnect()`, `listFiles()`, `getFileStream()`
- Use SMBClient from smbj library
- Implement connection with authentication
- Handle SMB connection lifecycle
- Return Result<> for all operations

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add SmbDataSource for SMB file operations`

---

## Step 4.4: Create Network Source Repository

**Task Description**:
Create repository for managing network storage connections.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/NetworkSourceRepository.kt`
- `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/NetworkSourceRepositoryImpl.kt`

**Implementation Details**:
Interface:

```kotlin
interface NetworkSourceRepository {
    suspend fun getAllSources(): List<NetworkSource>
    suspend fun getSourceById(id: String): NetworkSource?
    suspend fun addSource(source: NetworkSource)
    suspend fun updateSource(source: NetworkSource)
    suspend fun deleteSource(id: String)
    suspend fun testConnection(source: NetworkSource): Result<Boolean>
}
```

Implementation:

- Store sources in EncryptedSharedPreferences (serialize to JSON)
- Inject EncryptedSharedPreferences via Hilt

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add NetworkSourceRepository for network storage management`

---

## Step 4.5: Add EncryptedSharedPreferences to Hilt

**Task Description**:
Provide EncryptedSharedPreferences in Hilt module for secure credential storage.

**Files to Modify**:

- `wear/build.gradle.kts` (add security-crypto dependency)
- `wear/src/main/java/com/sza/fastmediasorter/wear/di/WearAppModule.kt`

**Implementation Details**:

1. Add dependency:

```kotlin
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

1. Provide in Hilt:

```kotlin
@Provides
@Singleton
fun provideEncryptedSharedPreferences(
    @ApplicationContext context: Context
): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        "wear_network_sources",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add EncryptedSharedPreferences for secure credential storage`

---

## Step 4.6: Create Add SMB Connection UI State

**Task Description**:
Create UI state for Add SMB connection screen.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/AddSmbUiState.kt`

**Implementation Details**:

```kotlin
data class AddSmbUiState(
    val server: String = "",
    val shareName: String = "",
    val username: String = "",
    val password: String = "",
    val isTesting: Boolean = false,
    val testResult: ConnectionTestResult? = null,
    val isSaving: Boolean = false,
    val error: String? = null
)

sealed class ConnectionTestResult {
    object Success : ConnectionTestResult()
    data class Failure(val message: String) : ConnectionTestResult()
}
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add AddSmbUiState data classes`

---

## Step 4.7: Create Add SMB ViewModel

**Task Description**:
Create ViewModel for Add SMB connection screen.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/AddSmbViewModel.kt`

**Implementation Details**:

- Inject NetworkSourceRepository and SmbDataSource
- StateFlow<AddSmbUiState>
- Methods: `updateServer()`, `updateShareName()`, `updateUsername()`, `updatePassword()`, `testConnection()`, `saveConnection()`
- `testConnection()` calls SmbDataSource to verify connection
- `saveConnection()` creates NetworkSource and saves via repository

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add AddSmbViewModel for SMB connection management`

---

## Step 4.8: Create Add SMB Screen UI

**Task Description**:
Create Add SMB connection screen with input fields and test/save buttons.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/AddSmbScreen.kt`

**Implementation Details**:
Use ScalingLazyColumn with:

1. Title: "Add SMB Share"
2. Text input chips for: Server, Share Name, Username, Password
3. Button: "Test Connection"
4. Show test result (success/failure)
5. Button: "Save" (enabled only after successful test)
6. Use hiltViewModel() and collectAsState()

Note: Text input on Wear OS is limited - use `rememberLauncherForActivityResult` with `RemoteInput.getResultsFromIntent()` for text entry.

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add AddSmbScreen UI for SMB connection setup`

---

## Step 4.9: Add Network Storage Navigation

**Task Description**:
Add navigation routes for network storage screens.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/MainActivity.kt`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsScreen.kt`

**Implementation Details**:

1. Add navigation routes:
   - "network/sources" -> NetworkSourceListScreen
   - "network/add_smb" -> AddSmbScreen

2. Add "Network Storage" button in Settings screen that navigates to "network/sources"

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add network storage navigation routes`

---

## Step 4.10: Create Network Source List Screen

**Task Description**:
Create a screen to list all saved network storage connections.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/NetworkSourceListScreen.kt`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/NetworkSourceListViewModel.kt`

**Implementation Details**:

- ViewModel loads all sources from repository
- Screen displays list with:
  - Source name and type icon (SMB/FTP/SFTP/Drive)
  - Tap to browse files
  - Long press to delete
- FAB to add new connection (navigate to add screen)

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add NetworkSourceListScreen to manage connections`

---

## Step 4.11: Update WearMediaRepository for Network Sources

**Task Description**:
Extend WearMediaRepository to support loading files from network sources.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearMediaRepository.kt`
- `wear/src/main/java/com/sza/fastmediasorter/wear/data/repository/WearMediaRepositoryImpl.kt`

**Implementation Details**:
Add method to interface:

```kotlin
suspend fun getNetworkMediaFiles(
    sourceId: String,
    mediaType: MediaType
): Flow<Result<List<WearMediaFile>>>
```

Implementation: Query appropriate data source based on NetworkSource type.

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Extend WearMediaRepository to support network sources`

---

## Step 4.12: Create Network Browse Screen

**Task Description**:
Create a browse screen for network storage sources.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/NetworkBrowseScreen.kt`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/NetworkBrowseViewModel.kt`

**Implementation Details**:

- Similar to BrowseScreen but loads from network source
- Show loading indicator during network operations
- Handle connection errors gracefully
- Support playing files directly from network

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add NetworkBrowseScreen for browsing network storage`

---

## Step 4.13: Update ExoPlayer for Network Streaming

**Task Description**:
Configure ExoPlayer to support streaming from SMB sources.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/di/WearAppModule.kt`

**Implementation Details**:

- Create custom DataSource.Factory for SMB
- Configure ExoPlayer with network data source
- Add appropriate buffer size for network streaming
- Handle connection timeouts

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Configure ExoPlayer for SMB network streaming`

---

## Step 4.14: Add Network Storage String Resources

**Task Description**:
Add all required string resources for network storage features.

**Files to Modify**:

- `wear/src/main/res/values/strings.xml`

**Implementation Details**:
Add strings for:

- Network storage screens
- Connection setup forms
- Error messages
- Connection status

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add network storage string resources`

---

# PHASE 5: FTP/SFTP Support

## Step 5.1: Add FTP/SFTP Dependencies

**Task Description**:
Add Apache Commons Net (FTP) and JSch (SFTP) dependencies.

**Files to Modify**:

- `wear/build.gradle.kts`

**Implementation Details**:

```kotlin
implementation("commons-net:commons-net:3.10.0")
implementation("com.github.mwiede:jsch:0.2.16")
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `build(wear): Add FTP and SFTP dependencies`

---

## Step 5.2: Create FTP Data Source

**Task Description**:
Create FTP data source implementation using Apache Commons Net.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/ftp/FtpDataSource.kt`

**Implementation Details**:
Similar to SmbDataSource but using FTPClient from commons-net.

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add FtpDataSource for FTP file operations`

---

## Step 5.3: Create SFTP Data Source

**Task Description**:
Create SFTP data source implementation using JSch.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/sftp/SftpDataSource.kt`

**Implementation Details**:
Similar to SmbDataSource but using JSch library for SFTP.

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add SftpDataSource for SFTP file operations`

---

## Step 5.4: Create Add FTP Screen

**Task Description**:
Create Add FTP connection screen (similar to Add SMB).

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/AddFtpScreen.kt`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/AddFtpViewModel.kt`

**Implementation Details**:
Form fields: Server, Port, Username, Password, Base Path

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add FTP connection setup screen`

---

## Step 5.5: Create Add SFTP Screen

**Task Description**:
Create Add SFTP connection screen.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/AddSftpScreen.kt`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/AddSftpViewModel.kt`

**Implementation Details**:
Form fields: Server, Port, Username, Password, Base Path

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add SFTP connection setup screen`

---

## Step 5.6: Update NetworkSourceRepository for FTP/SFTP

**Task Description**:
Update repository implementation to handle FTP and SFTP sources.

**Files to Modify**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/NetworkSourceRepositoryImpl.kt`

**Implementation Details**:
Add handling for FTP and SFTP connection testing using respective data sources.

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add FTP/SFTP support to NetworkSourceRepository`

---

# PHASE 6: Google Drive Support

## Step 6.1: Add Google Play Services Auth Dependency

**Task Description**:
Add Google Play Services Auth dependency for Google Sign-In.

**Files to Modify**:

- `wear/build.gradle.kts`

**Implementation Details**:

```kotlin
implementation("com.google.android.gms:play-services-auth:21.0.0")
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `build(wear): Add Google Play Services Auth dependency`

---

## Step 6.2: Create Google Drive API Models

**Task Description**:
Create data models for Google Drive API responses.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/data/cloud/drive/DriveFileModels.kt`

**Implementation Details**:
Data classes for Drive files list, file metadata, etc.

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add Google Drive API data models`

---

## Step 6.3: Create Google Drive Data Source

**Task Description**:
Create Google Drive data source using Drive REST API.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/data/cloud/drive/GoogleDriveDataSource.kt`

**Implementation Details**:

- Use Google Sign-In for authentication
- Use Drive REST API for file operations
- Store access token securely

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add GoogleDriveDataSource for Drive file operations`

---

## Step 6.4: Create Google Sign-In Screen

**Task Description**:
Create authentication screen for Google Drive.

**Files to Create**:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/cloud/GoogleDriveAuthScreen.kt`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/cloud/GoogleDriveAuthViewModel.kt`

**Implementation Details**:

- Use ActivityResultContract for sign-in
- Handle OAuth flow
- Save access/refresh tokens

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add Google Drive authentication screen`

---

## Step 6.5: Update Network Storage for Google Drive

**Task Description**:
Integrate Google Drive into network storage system.

**Files to Modify**:

- Multiple files to add Drive support throughout network storage flow

**Implementation Details**:
Add "Google Drive" option to network source types and handle accordingly.

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Integrate Google Drive into network storage system`

---

# Final Steps

## Step F.1: Add INTERNET Permission

**Task Description**:
Add INTERNET permission to AndroidManifest.xml for network operations.

**Files to Modify**:

- `wear/src/main/AndroidManifest.xml`

**Implementation Details**:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

**Build Target**: `:wear:assembleStandardDebug`

**Commit Message**: `feat(wear): Add INTERNET permission for network operations`

---

## Step F.2: Update README Documentation

**Task Description**:
Update Wear OS documentation with all new features.

**Files to Modify**:

- `docs/WEAR_OS_STATUS.md`

**Implementation Details**:
Document all implemented features in Phase 1-6.

**Build Target**: N/A

**Commit Message**: `docs: Update Wear OS documentation with new features`

---

## Step F.3: Final Testing and Polish

**Task Description**:
Comprehensive testing of all features on real device and emulator.

**Testing Checklist**:

- [ ] Settings page functional
- [ ] All toggles work correctly
- [ ] Media filtering works
- [ ] Slideshow works for all media types
- [ ] Album art downloads successfully
- [ ] SMB connection works
- [ ] FTP connection works
- [ ] SFTP connection works
- [ ] Google Drive connection works
- [ ] Network streaming performance acceptable

**Build Target**: `:wear:assembleRelease`

**Commit Message**: `test: Final testing and polish for Wear OS enhancements`

---

**Total Steps**: ~50 atomic development steps
**Estimated Total Time**: 15-20 days
**Priority Order**: Phase 1 → Phase 4 → Phase 2 → Phase 5 → Phase 3 → Phase 6
