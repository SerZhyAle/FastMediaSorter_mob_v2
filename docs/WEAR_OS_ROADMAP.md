---
layout: default
title: "FastMedia Wear OS - Enhancement Specification"
permalink: /docs/WEAR_OS_ROADMAP.html
---
# FastMedia Wear OS - Enhancement Specification

**Version**: 1.0  
**Date**: 2026-01-27  
**Status**: Planning Phase

---

## Overview

This document outlines planned enhancements for FastMedia Wear OS application. The goal is to add advanced features including settings management, slideshow functionality, and network storage integration while maintaining the lightweight nature suitable for wearable devices.

---

## 1. Settings Page

### 1.1 Core Settings Screen

**Objective**: Implement a settings page similar to the main application with configuration options and app information.

#### Requirements

- **Settings Navigation**: Add "⚙️ Settings" option to Home Screen
- **Settings UI**: Wear OS optimized settings screen with `ScalingLazyColumn`
- **Version Display**: Show app version name and build number
- **About Section**: App name, version, copyright information

#### UI Structure

```
Settings Screen
├── Media Types Section
│   ├── Enable Audio [Toggle]
│   ├── Enable Video [Toggle]
│   └── Enable Images [Toggle]
├── Slideshow Section
│   ├── Enable Slideshow [Toggle]
│   ├── Slideshow Interval [Selector: 3s, 5s, 10s, 15s, 30s]
│   ├── Wait for Media Finish [Toggle]
│   └── Auto-advance on Complete [Toggle]
├── Audio Settings
│   └── Download Album Art [Toggle]
├── About Section
│   ├── App Version
│   ├── Build Number
│   └── Last Updated
```

#### Data Persistence

- **Storage**: `DataStore Preferences` (already used in main app)
- **Keys**:
  - `wear_enable_audio` (Boolean, default: true)
  - `wear_enable_video` (Boolean, default: true)
  - `wear_enable_images` (Boolean, default: true)
  - `wear_slideshow_enabled` (Boolean, default: false)
  - `wear_slideshow_interval_seconds` (Int, default: 5)
  - `wear_slideshow_wait_finish` (Boolean, default: false)
  - `wear_download_album_art` (Boolean, default: false)

#### Implementation Files

- `ui/settings/SettingsScreen.kt` - UI
- `ui/settings/SettingsViewModel.kt` - ViewModel
- `ui/settings/SettingsUiState.kt` - State
- `data/preferences/WearPreferencesRepository.kt` - DataStore wrapper
- `domain/repository/WearPreferencesRepository.kt` - Interface

---

### 1.2 Media Type Filtering

**Objective**: Allow users to disable specific media types from browse screens.

#### Behavior

- When media type is disabled:
  - Hide category from Home Screen
  - Prevent navigation to disabled browse screens
  - Show "Disabled in Settings" message if accessed directly

#### Implementation

- Update `HomeScreen.kt` to filter categories based on settings
- Update `BrowseViewModel.kt` to check settings before loading
- Add settings check in navigation

---

### 1.3 Slideshow Functionality

**Objective**: Implement automatic slideshow for browsing media files.

#### Features

1. **Manual Slideshow Toggle**
   - Button in Browse Screen to start/stop slideshow
   - Indicator when slideshow is active

2. **Auto-Advance Logic**
   - **Images**: Advance after configured interval (3s, 5s, 10s, 15s, 30s)
   - **Audio**: Advance after track finishes (if "Wait for Finish" enabled)
   - **Video**: Advance after video finishes (if "Wait for Finish" enabled)

3. **Slideshow Controls**
   - Pause/Resume button
   - Skip to next/previous
   - Exit slideshow

#### Implementation

- `ui/slideshow/SlideshowController.kt` - Slideshow logic
- Update `AudioPlayerViewModel.kt` - Add auto-advance on track end
- Update `VideoPlayerViewModel.kt` - Add auto-advance on video end
- Update `ImageViewerViewModel.kt` - Add timer-based auto-advance

---

### 1.4 Album Art Download

**Objective**: Download album artwork for audio files from online sources (similar to main app).

#### Features

- **iTunes Search API Integration**: Query for album art by artist/album
- **Caching**: Store downloaded images locally
- **Fallback**: Use default placeholder if not found
- **Settings Toggle**: Enable/disable feature

#### Implementation

- Reuse code from main app:
  - `data/network/itunes/ITunesSearchApi.kt`
  - `data/network/itunes/ITunesSearchService.kt`
- Add Retrofit dependency to Wear module
- Update `AudioPlayerScreen.kt` to display downloaded art

---

## 2. Network Storage Integration

### Overview

Enable Wear OS app to connect to network storage (SMB, FTP, SFTP, Google Drive) and browse/play media files directly from remote sources.

> **⚠️ Important Consideration**: Network operations on Wear OS should be carefully managed due to:
>
> - Limited battery capacity
> - Smaller screen for credential input
> - Potential connectivity issues (Bluetooth/WiFi)

---

### 2.1 SMB (Samba/Windows Share) Support

**Objective**: Connect to SMB shares and browse media files.

#### Requirements

- **Library**: Reuse `smbj` library from main app
- **Credentials**: Server address, username, password, share name
- **Authentication**: Support for guest and authenticated access
- **Browse**: List folders and media files
- **Playback**: Stream files via SMB

#### UI Flow

```
Settings → Network Storage → Add SMB
  ├── Server Address [Text Input]
  ├── Share Name [Text Input]
  ├── Username [Text Input]
  ├── Password [Text Input]
  └── Test Connection [Button]
```

#### Implementation

- `data/network/smb/WearSmbDataSource.kt` - SMB client wrapper
- `ui/network/AddSmbScreen.kt` - Add SMB connection UI
- `ui/network/NetworkStorageListScreen.kt` - List saved connections
- Store credentials in `EncryptedSharedPreferences`

---

### 2.2 FTP Support

**Objective**: Connect to FTP servers and browse media files.

#### Requirements

- **Library**: Reuse `commons-net` from main app
- **Credentials**: Server, port, username, password
- **Browse**: List directories and files
- **Playback**: Stream files via FTP

#### Implementation

- `data/network/ftp/WearFtpDataSource.kt` - FTP client wrapper
- `ui/network/AddFtpScreen.kt` - Add FTP connection UI

---

### 2.3 SFTP Support

**Objective**: Connect to SFTP servers (secure FTP over SSH).

#### Requirements

- **Library**: Reuse `jsch` from main app
- **Credentials**: Server, port, username, password/key
- **Browse**: List directories and files
- **Playback**: Stream files via SFTP

#### Implementation

- `data/network/sftp/WearSftpDataSource.kt` - SFTP client wrapper
- `ui/network/AddSftpScreen.kt` - Add SFTP connection UI

---

### 2.4 Google Drive Support

**Objective**: Connect to Google Drive and browse media files.

#### Requirements

- **Library**: Google Drive REST API + Google Sign-In
- **Authentication**: OAuth 2.0 via Google Sign-In
- **Browse**: List folders and files
- **Playback**: Stream files via Drive API

#### Challenges on Wear OS

- **OAuth Flow**: Limited screen size for Google Sign-In
- **Solution**: Use companion app authentication (phone app authenticates, shares token with watch)
- **Alternative**: Use device-based OAuth with simplified UI

#### Implementation

- `data/cloud/drive/WearDriveDataSource.kt` - Drive API wrapper
- `ui/cloud/GoogleDriveAuthScreen.kt` - Authentication UI
- Reuse `CloudAuthenticationHelper.kt` from main app

---

## 3. Architecture Changes

### 3.1 Data Source Abstraction

Create unified interface for all media sources (local + network).

```kotlin
interface WearMediaDataSource {
    suspend fun listFiles(path: String, mediaType: MediaType): Result<List<WearMediaFile>>
    suspend fun getFileStream(file: WearMediaFile): Result<InputStream>
    fun getSourceType(): MediaSourceType
}

enum class MediaSourceType {
    LOCAL,
    SMB,
    FTP,
    SFTP,
    GOOGLE_DRIVE
}
```

### 3.2 Repository Update

Update `WearMediaRepository` to support multiple data sources.

```kotlin
interface WearMediaRepository {
    suspend fun getMediaFiles(
        mediaType: MediaType,
        source: MediaSourceType = MediaSourceType.LOCAL
    ): Flow<Result<List<WearMediaFile>>>

    suspend fun getNetworkSources(): List<NetworkSource>
    suspend fun addNetworkSource(source: NetworkSource)
    suspend fun removeNetworkSource(sourceId: String)
}
```

---

## 4. Implementation Phases

### Phase 1: Settings Foundation (Priority: High)

- [ ] Create Settings Screen UI
- [ ] Implement DataStore preferences
- [ ] Add version display
- [ ] Implement media type filtering
- [ ] Test settings persistence

**Estimated Effort**: 2-3 days

---

### Phase 2: Slideshow Feature (Priority: Medium)

- [ ] Implement slideshow controller
- [ ] Add auto-advance for images (timer-based)
- [ ] Add auto-advance for audio/video (on complete)
- [ ] Add slideshow controls UI
- [ ] Test slideshow functionality

**Estimated Effort**: 2-3 days

---

### Phase 3: Album Art Download (Priority: Low)

- [ ] Add Retrofit dependency
- [ ] Implement iTunes API integration
- [ ] Add image caching
- [ ] Update Audio Player UI
- [ ] Test album art download

**Estimated Effort**: 1-2 days

---

### Phase 4: Network Storage - SMB (Priority: High)

- [ ] Add smbj dependency
- [ ] Implement SMB data source
- [ ] Create Add SMB UI
- [ ] Implement credential storage
- [ ] Test SMB connection and playback

**Estimated Effort**: 3-4 days

---

### Phase 5: Network Storage - FTP/SFTP (Priority: Medium)

- [ ] Implement FTP data source
- [ ] Implement SFTP data source
- [ ] Create Add FTP/SFTP UI
- [ ] Test FTP/SFTP connections

**Estimated Effort**: 2-3 days

---

### Phase 6: Network Storage - Google Drive (Priority: Low)

- [ ] Implement Google Drive authentication
- [ ] Implement Drive data source
- [ ] Create Drive auth UI
- [ ] Test Drive integration

**Estimated Effort**: 3-4 days

---

## 5. Technical Considerations

### Battery Optimization

- **Network Operations**: Use WorkManager for background tasks
- **Caching**: Cache file lists and metadata locally
- **Streaming**: Use efficient buffering strategies
- **Timeout**: Set reasonable timeouts for network operations

### Security

- **Credentials**: Use `EncryptedSharedPreferences` for all passwords
- **HTTPS**: Enforce HTTPS for cloud services
- **Certificate Validation**: Validate SSL certificates for secure connections

### User Experience

- **Loading States**: Show clear loading indicators for network operations
- **Error Handling**: Provide user-friendly error messages
- **Offline Mode**: Gracefully handle network unavailability
- **Connection Management**: Allow users to test connections before saving

### Dependencies to Add

```kotlin
// Wear module build.gradle.kts additions

// Network
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// SMB
implementation("com.hierynomus:smbj:0.12.1")

// FTP
implementation("commons-net:commons-net:3.10.0")

// SFTP
implementation("com.github.mwiede:jsch:0.2.16")

// Google Drive
implementation("com.google.android.gms:play-services-auth:21.0.0")

// DataStore (already in main app)
implementation("androidx.datastore:datastore-preferences:1.0.0")

// Security
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

---

## 6. Open Questions

1. **Companion App Integration**: Should network credentials be synced from phone app to watch?
2. **Storage Limits**: Should we limit cache size on watch due to limited storage?
3. **Background Sync**: Should we implement background sync for offline playback?
4. **Multi-Source Browse**: How to display files from multiple sources in one view?

---

## 7. Success Criteria

- [ ] Settings page functional with all toggles working
- [ ] Slideshow works for all media types
- [ ] Album art downloads and displays correctly
- [ ] SMB connection established and files playable
- [ ] FTP/SFTP connections work reliably
- [ ] Google Drive authentication and playback functional
- [ ] Battery impact is acceptable (< 5% per hour during playback)
- [ ] All features tested on real Wear OS device

---

**Next Steps**: Review this specification and prioritize features for implementation.

