# Catalogue: app_v2

_Generated: 2026-04-29 16:43 · 819 classes_

Source of truth: [app_v2.jsonl](app_v2.jsonl). This file is auto-generated — edit JSONL, then re-render.

## Layer summary

| Layer | Files | Total LOC |
|-------|------:|----------:|
| core | 49 | 7384 |
| data | 173 | 41234 |
| di | 11 | 976 |
| domain | 152 | 18096 |
| other | 1 | 499 |
| service | 1 | 74 |
| ui | 380 | 91714 |
| utils | 28 | 3727 |
| vr | 6 | 358 |
| widget | 7 | 759 |
| worker | 11 | 1575 |

## Index

| Path | Class | Layer | LOC | Last | Status | Role |
|------|-------|-------|----:|------|--------|------|
| [com/sza/fastmediasorter/core/AppShortcutsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/AppShortcutsManager.kt) | `AppShortcutsManager` | core | 54 | 2026-03-24 | unknown | _—_ |
| [com/sza/fastmediasorter/core/AudioToggleTileService.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/AudioToggleTileService.kt) | `AudioToggleTileService` | core | 134 | 2026-03-30 | unknown | _—_ |
| [com/sza/fastmediasorter/core/cache/MediaFilesCacheManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cache/MediaFilesCacheManager.kt) | `MediaFilesCacheManager` | core | 200 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/core/cache/TranslationCacheManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cache/TranslationCacheManager.kt) | `TranslationCacheManager` | core | 74 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/core/cache/UnifiedFileCache.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cache/UnifiedFileCache.kt) | `UnifiedFileCache` | core | 212 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/core/cast/CastOptionsProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cast/CastOptionsProvider.kt) | `CastOptionsProvider` | core | 26 | 2026-03-28 | unknown | _—_ |
| [com/sza/fastmediasorter/core/cast/LocalCastProxyServer.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cast/LocalCastProxyServer.kt) | `LocalCastProxyServer` | core | 150 | 2026-03-28 | unknown | _—_ |
| [com/sza/fastmediasorter/core/constants/AppConstants.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/constants/AppConstants.kt) | `AppConstants` | core | 26 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/core/debug/DebugToolsBridge.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/debug/DebugToolsBridge.kt) | `DebugToolsBridge` | core | 41 | 2026-02-14 | unknown | _—_ |
| [com/sza/fastmediasorter/core/debug/StrictModeHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/debug/StrictModeHelper.kt) | `StrictModeHelper` | core | 101 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/core/init/AppStartupInitializer.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt) | `AppStartupInitializer` | core | 360 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/core/input/GamepadInputManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/input/GamepadInputManager.kt) | `GamepadInputManager` | core | 192 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/core/input/KeyBindingManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/input/KeyBindingManager.kt) | `KeyBindingManager` | core | 42 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/core/logging/CorrelationContext.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/logging/CorrelationContext.kt) | `CorrelationContext` | core | 93 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/core/logging/LogExportHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LogExportHelper.kt) | `LogExportHelper` | core | 127 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/core/logging/LoggingHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LoggingHelper.kt) | `LoggingHelper` | core | 369 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/core/logging/StructuredLogger.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/logging/StructuredLogger.kt) | `StructuredLogger` | core | 83 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/core/metrics/KpiAlertChecker.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/KpiAlertChecker.kt) | `KpiAlertChecker` | core | 202 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/core/metrics/MetricsExporter.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/MetricsExporter.kt) | `MetricsExporter` | core | 205 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/core/metrics/OperationMetricsRecorder.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/OperationMetricsRecorder.kt) | `OperationMetricsRecorder` | core | 132 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/core/metrics/ScanMetricsRecorder.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/ScanMetricsRecorder.kt) | `ScanMetricsRecorder` | core | 140 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/core/network/NetworkContextAnalyzer.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/network/NetworkContextAnalyzer.kt) | `NetworkContextAnalyzer` | core | 103 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/core/network/NetworkReachabilityGate.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/network/NetworkReachabilityGate.kt) | `NetworkReachabilityGate` | core | 47 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/core/network/NetworkStateMonitor.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/network/NetworkStateMonitor.kt) | `NetworkStateMonitor` | core | 221 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/core/security/SecretMasker.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/security/SecretMasker.kt) | `SecretMasker` | core | 61 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt) | `AudioMetadataLoader` | core | 575 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/CachedMediaMetadataExtractor.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/CachedMediaMetadataExtractor.kt) | `CachedMediaMetadataExtractor` | core | 194 | 2026-03-03 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/CacheStatusHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/CacheStatusHelper.kt) | `CacheStatusHelper` | core | 57 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/ColorPalette.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/ColorPalette.kt) | `ColorPalette` | core | 124 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/DestinationColors.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/DestinationColors.kt) | `DestinationColors` | core | 42 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/DeviceCapabilityProbe.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/DeviceCapabilityProbe.kt) | `DeviceCapabilityProbe` | core | 82 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/DocumentMetadataExtractor.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/DocumentMetadataExtractor.kt) | `DocumentMetadataExtractor` | core | 98 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/FileOperationErrorFormatter.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/FileOperationErrorFormatter.kt) | `FileOperationErrorFormatter` | core | 220 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/FileSize.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/FileSize.kt) | `FileSize` | core | 28 | 2026-02-13 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/GifFrameCounter.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/GifFrameCounter.kt) | `GifFrameCounter` | core | 128 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/HeifSupportUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/HeifSupportUtils.kt) | `HeifSupportUtils` | core | 42 | 2026-03-28 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/InputStreamExt.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/InputStreamExt.kt) | `InputStreamExt` | core | 81 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/LocaleHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/LocaleHelper.kt) | `LocaleHelper` | core | 228 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/MediaFormatUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/MediaFormatUtils.kt) | `MediaFormatUtils` | core | 26 | 2026-02-19 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/MediaMetadataHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/MediaMetadataHelper.kt) | `DetailedMediaInfo` | core | 412 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/MemoryTier.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/MemoryTier.kt) | `MemoryTier` | core | 75 | 2026-03-02 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/NetworkFileDownloader.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/NetworkFileDownloader.kt) | `NetworkFileDownloader` | core | 303 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/PathUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/PathUtils.kt) | `PathUtils` | core | 73 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/PdfInfoParser.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/PdfInfoParser.kt) | `PdfInfoParser` | core | 385 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/PermissionHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt) | `PermissionHelper` | core | 323 | 2026-03-24 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/SafUriExtractor.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/SafUriExtractor.kt) | `SafUriExtractor` | core | 302 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/core/util/UriPathResolver.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/UriPathResolver.kt) | `UriPathResolver` | core | 96 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/core/xr/VrPanelSizePreference.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrPanelSizePreference.kt) | `VrPanelSizePreference` | core | 61 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/core/xr/XrDeviceDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrDeviceDetector.kt) | `XrDeviceDetector` | core | 34 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/CloudAuthenticationHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudAuthenticationHelper.kt) | `CloudAuthenticationHelper` | data | 125 | 2026-02-28 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/CloudAuthStateMachine.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudAuthStateMachine.kt) | `CloudAuthStateMachine` | data | 180 | 2026-02-28 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt) | `CloudFileOperationHandler` | data | 998 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/CloudFileOperationPathUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationPathUtils.kt) | `CloudFileOperationPathUtils` | data | 81 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/CloudMediaScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudMediaScanner.kt) | `CloudMediaScanner` | data | 393 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/CloudPathParser.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudPathParser.kt) | `CloudPathParser` | data | 111 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/CloudStorageClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudStorageClient.kt) | `CloudProvider` | data | 284 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/CloudToCloudTransferHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudToCloudTransferHelper.kt) | `CloudToCloudTransferHelper` | data | 179 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/datasource/CloudDataSource.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/datasource/CloudDataSource.kt) | `CloudDataSource` | data | 197 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/DropboxAuthPlugin.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/DropboxAuthPlugin.kt) | `DropboxAuthPlugin` | data | 56 | 2026-03-24 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/DropboxClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/DropboxClient.kt) | `DropboxClient` | data | 984 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/DropboxClientUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/DropboxClientUtils.kt) | `DropboxClientUtils` | data | 223 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/glide/CloudThumbnailData.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/glide/CloudThumbnailData.kt) | `CloudThumbnailData` | data | 48 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/glide/CloudThumbnailModelLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/glide/CloudThumbnailModelLoader.kt) | `CloudThumbnailEntryPoint` | data | 406 | 2026-03-08 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/glide/GoogleDriveThumbnailData.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/glide/GoogleDriveThumbnailData.kt) | `GoogleDriveThumbnailData` | data | 40 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/glide/GoogleDriveThumbnailModelLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/glide/GoogleDriveThumbnailModelLoader.kt) | `GoogleDriveThumbnailModelLoader` | data | 249 | 2026-03-08 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/GoogleDriveAuthCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthCoordinator.kt) | `GoogleDriveAuthCoordinator` | data | 315 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/GoogleDriveAuthPlugin.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthPlugin.kt) | `GoogleDriveAuthPlugin` | data | 164 | 2026-03-20 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/GoogleDriveRestClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveRestClient.kt) | `GoogleDriveRestClient` | data | 1105 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/GoogleDriveRestClientUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveRestClientUtils.kt) | `GoogleDriveRestClientUtils` | data | 57 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveCredentialsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveCredentialsManager.kt) | `GoogleDriveCredentialsManager` | data | 143 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveHttpClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveHttpClient.kt) | `GoogleDriveHttpClient` | data | 199 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/InteractiveCloudAuthenticator.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/InteractiveCloudAuthenticator.kt) | `InteractiveCloudAuthenticator` | data | 50 | 2026-03-02 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/NetworkCredentialsResolver.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/NetworkCredentialsResolver.kt) | `NetworkCredentialsResolver` | data | 292 | 2026-03-01 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/OneDriveAuthCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveAuthCoordinator.kt) | `OneDriveAuthCoordinator` | data | 475 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/OneDriveAuthPlugin.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveAuthPlugin.kt) | `OneDriveAuthPlugin` | data | 83 | 2026-03-02 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/OneDriveRestClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveRestClient.kt) | `OneDriveRestClient` | data | 901 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/OneDriveRestClientUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveRestClientUtils.kt) | `OneDriveRestClientUtils` | data | 101 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/cloud/UnifiedCloudAuthManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/UnifiedCloudAuthManager.kt) | `UnifiedCloudAuthManager` | data | 160 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/common/MediaTypeUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/common/MediaTypeUtils.kt) | `MediaTypeUtils` | data | 128 | 2026-03-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/glide/EpubCoverDecoder.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/glide/EpubCoverDecoder.kt) | `EpubCoverDecoder` | data | 91 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/glide/NetworkEpubCoverLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/glide/NetworkEpubCoverLoader.kt) | `NetworkEpubCoverLoader` | data | 376 | 2026-02-16 | unknown | _—_ |
| [com/sza/fastmediasorter/data/glide/NetworkPdfThumbnailLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/glide/NetworkPdfThumbnailLoader.kt) | `NetworkPdfThumbnailLoader` | data | 524 | 2026-02-16 | unknown | _—_ |
| [com/sza/fastmediasorter/data/glide/PdfPageDecoder.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/glide/PdfPageDecoder.kt) | `PdfPageDecoder` | data | 96 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/hash/CloudFileHasher.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/hash/CloudFileHasher.kt) | `CloudFileHasher` | data | 54 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/data/hash/FtpFileHasher.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/hash/FtpFileHasher.kt) | `FtpFileHasher` | data | 46 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/data/hash/LocalFileHasher.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/hash/LocalFileHasher.kt) | `LocalFileHasher` | data | 54 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/data/hash/SftpFileHasher.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/hash/SftpFileHasher.kt) | `SftpFileHasher` | data | 48 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/data/hash/SmbFileHasher.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/hash/SmbFileHasher.kt) | `SmbFileHasher` | data | 47 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/data/input/DefaultsMapLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/input/DefaultsMapLoader.kt) | `DefaultsMapLoader` | data | 52 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/data/input/InputBindingDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingDao.kt) | `InputBindingDao` | data | 31 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/data/input/InputBindingEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingEntity.kt) | `InputBindingEntity` | data | 14 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/data/input/InputBindingRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingRepository.kt) | `InputBindingRepository` | data | 102 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt) | `CandidateSelectionPolicy` | data | 44 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/link/DirectFileExtractionStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/link/DirectFileExtractionStrategy.kt) | `DirectFileExtractionStrategy` | data | 154 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/link/HtmlMediaCandidate.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlMediaCandidate.kt) | `HtmlMediaCandidate` | data | 28 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt) | `HtmlPageExtractionStrategy` | data | 177 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt) | `LinkDownloadWriter` | data | 186 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/AppDatabase.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt) | `AppDatabase` | data | 719 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/CachedFileListDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/CachedFileListDao.kt) | `CachedFileListDao` | data | 36 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/CachedFileListEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/CachedFileListEntity.kt) | `CachedFileListEntity` | data | 77 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/Converters.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Converters.kt) | `Converters` | data | 35 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/CryptoHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/CryptoHelper.kt) | `CryptoHelper` | data | 118 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/DuplicateHashCacheDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/DuplicateHashCacheDao.kt) | `DuplicateHashCacheDao` | data | 36 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/DuplicateHashCacheEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/DuplicateHashCacheEntity.kt) | `DuplicateHashCacheEntity` | data | 25 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/EncryptedStringConverter.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/EncryptedStringConverter.kt) | `EncryptedString` | data | 9 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/FavoritesDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FavoritesDao.kt) | `FavoritesDao` | data | 38 | 2026-03-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/FavoritesEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FavoritesEntity.kt) | `FavoritesEntity` | data | 28 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/FileMetadataCacheDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FileMetadataCacheDao.kt) | `FileMetadataCacheDao` | data | 100 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/FileMetadataCacheEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FileMetadataCacheEntity.kt) | `FileMetadataCacheEntity` | data | 94 | 2026-03-03 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/NetworkCredentialsDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/NetworkCredentialsDao.kt) | `NetworkCredentialsDao` | data | 64 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/NetworkCredentialsEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/NetworkCredentialsEntity.kt) | `NetworkCredentialsEntity` | data | 132 | 2026-03-01 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/PendingRevocationDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/PendingRevocationDao.kt) | `PendingRevocationDao` | data | 30 | 2026-02-28 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/PendingRevocationEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/PendingRevocationEntity.kt) | `PendingRevocationEntity` | data | 28 | 2026-02-28 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/PlaybackPositionDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/PlaybackPositionDao.kt) | `PlaybackPositionDao` | data | 52 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/PlaybackPositionEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/PlaybackPositionEntity.kt) | `PlaybackPositionEntity` | data | 19 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/ResourceDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceDao.kt) | `ResourceDao` | data | 147 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/ResourceEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt) | `ResourceEntity` | data | 98 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/ResourceFtsEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceFtsEntity.kt) | `ResourceFtsEntity` | data | 12 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/ScheduledOperationDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ScheduledOperationDao.kt) | `ScheduledOperationDao` | data | 37 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/ScheduledOperationEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ScheduledOperationEntity.kt) | `ScheduledOperationEntity` | data | 84 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/StereoFormatOverrideDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StereoFormatOverrideDao.kt) | `StereoFormatOverrideDao` | data | 18 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/StereoFormatOverrideEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StereoFormatOverrideEntity.kt) | `StereoFormatOverrideEntity` | data | 25 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/StreamingCacheDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamingCacheDao.kt) | `StreamingCacheDao` | data | 45 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/StreamingCacheEntry.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamingCacheEntry.kt) | `StreamingCacheEntry` | data | 50 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/ThumbnailCacheDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ThumbnailCacheDao.kt) | `ThumbnailCacheDao` | data | 88 | 2026-02-18 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/db/ThumbnailCacheEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ThumbnailCacheEntity.kt) | `ThumbnailCacheEntity` | data | 49 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/LocalMediaScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/LocalMediaScanner.kt) | `LocalMediaScanner` | data | 786 | 2026-03-22 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/preferences/BrowseManualOrderPrefs.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/BrowseManualOrderPrefs.kt) | `BrowseManualOrderPrefs` | data | 52 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/preferences/BrowseStateDataStore.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/BrowseStateDataStore.kt) | `BrowseStateDataStore` | data | 115 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/local/preferences/SettingsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/SettingsManager.kt) | `AppSettings` | data | 291 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/data/model/TrashMetadata.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/model/TrashMetadata.kt) | `TrashMetadata` | data | 60 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/BaseConnectionPool.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/BaseConnectionPool.kt) | `BaseConnectionPool` | data | 273 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt) | `ConnectionThrottleManager` | data | 536 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/datasource/FtpDataSource.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/FtpDataSource.kt) | `FtpDataSource` | data | 249 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt) | `SftpDataSource` | data | 221 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/datasource/SmbDataSource.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SmbDataSource.kt) | `SmbDataSource` | data | 608 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt) | `NetworkErrorClassifier` | data | 174 | 2026-04-16 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/exceptions/NetworkErrorMessageMapper.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorMessageMapper.kt) | `NetworkErrorMessageMapper` | data | 72 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/exceptions/NetworkExceptions.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkExceptions.kt) | `NetworkException` | data | 54 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/exceptions/RetryPolicy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/RetryPolicy.kt) | `RetryPolicy` | data | 90 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt) | `FtpFileOperationHandler` | data | 939 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/glide/ErrorPropagatingPipedInputStream.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/ErrorPropagatingPipedInputStream.kt) | `ErrorPropagatingPipedInputStream` | data | 37 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/glide/NetworkFileData.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileData.kt) | `NetworkFileData` | data | 57 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/glide/NetworkFileDataPassthroughModelLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileDataPassthroughModelLoader.kt) | `NetworkFileDataPassthroughModelLoader` | data | 77 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt) | `NetworkFileModelLoader` | data | 759 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/glide/NetworkMediaDataSource.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkMediaDataSource.kt) | `NetworkMediaDataSource` | data | 410 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt) | `NetworkVideoFrameDecoder` | data | 367 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/glide/SafeByteBuffer.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/SafeByteBuffer.kt) | `SafeByteBuffer` | data | 11 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/glide/SafeByteBufferBitmapDecoder.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/SafeByteBufferBitmapDecoder.kt) | `SafeByteBufferBitmapDecoder` | data | 120 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/glide/SafeByteBufferEncoder.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/SafeByteBufferEncoder.kt) | `SafeByteBufferEncoder` | data | 22 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/glide/VideoExtractionFailurePersistence.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/VideoExtractionFailurePersistence.kt) | `VideoExtractionFailurePersistence` | data | 67 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/helpers/SmbDirectoryScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/helpers/SmbDirectoryScanner.kt) | `SmbDirectoryScanner` | data | 531 | 2026-03-14 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/model/SmbModels.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/model/SmbModels.kt) | `SmbConnectionInfo` | data | 44 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/pool/BaseConnectionPool.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/pool/BaseConnectionPool.kt) | `BaseConnectionPool` | data | 206 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt) | `SftpFileOperationHandler` | data | 411 | 2026-02-16 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/SmbClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbClient.kt) | `SmbClient` | data | 956 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/SmbClientErrorFormatter.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbClientErrorFormatter.kt) | `SmbClientErrorFormatter` | data | 135 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/SmbConnectionManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt) | `SmbResetCallback` | data | 1007 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/SmbErrorClassifier.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbErrorClassifier.kt) | `SmbPlaybackErrorCategory` | data | 143 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt) | `SmbFileOperationHandler` | data | 680 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/SmbFileOperations.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperations.kt) | `SmbFileOperations` | data | 617 | 2026-04-16 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/SmbMediaScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbMediaScanner.kt) | `SmbMediaScanner` | data | 754 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/SmbPlaybackConnectionTracker.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbPlaybackConnectionTracker.kt) | `SmbPlaybackConnectionTracker` | data | 70 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/data/network/SmbShareDiscoveryHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbShareDiscoveryHelper.kt) | `SmbShareDiscoveryHelper` | data | 239 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/observer/MediaFileObserver.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/observer/MediaFileObserver.kt) | `MediaFileObserver` | data | 59 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/observer/MediaStoreObserver.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/observer/MediaStoreObserver.kt) | `MediaStoreObserver` | data | 73 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/paging/MediaFilesPagingSource.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/paging/MediaFilesPagingSource.kt) | `MediaFilesPagingSource` | data | 106 | 2026-02-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/remote/ftp/FtpClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpClient.kt) | `FtpClient` | data | 911 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/remote/ftp/FtpDirectoryScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpDirectoryScanner.kt) | `FtpDirectoryScanner` | data | 125 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/remote/ftp/FtpExoPlayerPool.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpExoPlayerPool.kt) | `FtpExoPlayerPool` | data | 161 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/remote/ftp/FtpMediaScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpMediaScanner.kt) | `FtpMediaScanner` | data | 566 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/data/remote/ftp/FtpStandaloneOperations.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpStandaloneOperations.kt) | `FtpStandaloneOperations` | data | 435 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/remote/ITunesApiService.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ITunesApiService.kt) | `ITunesApiService` | data | 55 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt) | `SftpFileAttributes` | data | 635 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt) | `SftpConnectionPool` | data | 488 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/remote/sftp/SftpConnectionTester.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionTester.kt) | `SftpConnectionTester` | data | 154 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt) | `SftpMediaScanner` | data | 487 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/AudioMetadataCacheRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AudioMetadataCacheRepository.kt) | `AudioMetadataCacheRepository` | data | 150 | 2026-03-24 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/CachedFileListRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/CachedFileListRepository.kt) | `CachedFileListRepository` | data | 147 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/DuplicateHashRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/DuplicateHashRepositoryImpl.kt) | `DuplicateHashRepositoryImpl` | data | 71 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/FavoritesRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/FavoritesRepositoryImpl.kt) | `FavoritesRepositoryImpl` | data | 53 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/MediaStoreRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/MediaStoreRepositoryImpl.kt) | `MediaStoreRepositoryImpl` | data | 670 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt) | `NetworkCredentialsRepositoryImpl` | data | 274 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/PlaybackPositionRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/PlaybackPositionRepositoryImpl.kt) | `PlaybackPositionRepositoryImpl` | data | 109 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt) | `ResourceRepositoryImpl` | data | 530 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/ResumeStateRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResumeStateRepositoryImpl.kt) | `ResumeStateRepositoryImpl` | data | 93 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/ScheduledOperationRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ScheduledOperationRepositoryImpl.kt) | `ScheduledOperationRepositoryImpl` | data | 84 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt) | `SettingsRepositoryImpl` | data | 828 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/StreamingCacheRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamingCacheRepositoryImpl.kt) | `StreamingCacheRepositoryImpl` | data | 129 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/TestCredentialModels.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/TestCredentialModels.kt) | `TestCredentialsConfig` | data | 35 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/repository/ThumbnailCacheRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ThumbnailCacheRepositoryImpl.kt) | `ThumbnailCacheRepositoryImpl` | data | 239 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/access/FtpFileAccess.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/access/FtpFileAccess.kt) | `FtpFileAccess` | data | 96 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/access/LocalFileAccess.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/access/LocalFileAccess.kt) | `LocalFileAccess` | data | 53 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/access/SftpFileAccess.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/access/SftpFileAccess.kt) | `SftpFileAccess` | data | 72 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/access/SmbFileAccess.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/access/SmbFileAccess.kt) | `SmbFileAccess` | data | 30 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt) | `AtomicFileOperationStrategy` | data | 302 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/BaseFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/BaseFileOperationHandler.kt) | `BaseFileOperationHandler` | data | 940 | 2026-03-30 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/FileAccess.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileAccess.kt) | `FileAccess` | data | 14 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/FileExistsException.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileExistsException.kt) | `FileExistsException` | data | 16 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/FileOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileOperationStrategy.kt) | `FileOperationStrategy` | data | 207 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/LocalTransferProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/LocalTransferProvider.kt) | `LocalTransferProvider` | data | 395 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/SmbTransferProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/SmbTransferProvider.kt) | `SmbTransferProvider` | data | 342 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategies/FtpToFtpStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/FtpToFtpStrategy.kt) | `FtpToFtpStrategy` | data | 214 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategies/FtpToLocalStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/FtpToLocalStrategy.kt) | `FtpToLocalStrategy` | data | 103 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategies/LocalToFtpStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/LocalToFtpStrategy.kt) | `LocalToFtpStrategy` | data | 120 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategies/LocalToSftpStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/LocalToSftpStrategy.kt) | `LocalToSftpStrategy` | data | 118 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategies/LocalToSmbStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/LocalToSmbStrategy.kt) | `LocalToSmbStrategy` | data | 142 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategies/SftpToLocalStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/SftpToLocalStrategy.kt) | `SftpToLocalStrategy` | data | 101 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategies/SftpToSftpStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/SftpToSftpStrategy.kt) | `SftpToSftpStrategy` | data | 175 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategies/SmbToLocalStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/SmbToLocalStrategy.kt) | `SmbToLocalStrategy` | data | 74 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategies/SmbToSmbStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/SmbToSmbStrategy.kt) | `SmbToSmbStrategy` | data | 111 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt) | `CloudOperationStrategy` | data | 741 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategy/FtpOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/FtpOperationStrategy.kt) | `FtpOperationStrategy` | data | 708 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt) | `LocalOperationStrategy` | data | 536 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt) | `SftpOperationStrategy` | data | 712 | 2026-02-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt) | `SmbOperationStrategy` | data | 824 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/strategy/StrategyUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/StrategyUtils.kt) | `StrategyUtils` | data | 22 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/TempFileNamingStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/TempFileNamingStrategy.kt) | `TempFileNamingStrategy` | data | 113 | 2026-02-10 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/TransferStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/TransferStrategy.kt) | `TransferStrategy` | data | 115 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt) | `UnifiedFileOperationHandler` | data | 539 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/data/transfer/UniversalFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UniversalFileOperationHandler.kt) | `UniversalFileOperationHandler` | data | 207 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/data/wear/WearableDataLayerRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/wear/WearableDataLayerRepositoryImpl.kt) | `WearableDataLayerRepositoryImpl` | data | 41 | 2026-04-14 | unknown | _—_ |
| [com/sza/fastmediasorter/core/di/AppModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/di/AppModule.kt) | `IoDispatcher` | di | 174 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/core/di/DatabaseModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt) | `DatabaseModule` | di | 174 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/core/di/RepositoryModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt) | `RepositoryModule` | di | 99 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/di/DirectoryStrategyModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/DirectoryStrategyModule.kt) | `DirectoryStrategyModule` | di | 86 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/di/DuplicateHashModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/DuplicateHashModule.kt) | `DuplicateHashModule` | di | 21 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/di/GlideAppModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt) | `GlideAppModule` | di | 197 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/di/InputBindingModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/InputBindingModule.kt) | `InputBindingModule` | di | 19 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/di/LinkDownloadModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt) | `LinkDownloadModule` | di | 69 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/di/PlayerCommandOverrideModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/PlayerCommandOverrideModule.kt) | `PlayerCommandOverrideModule` | di | 27 | 2026-04-20 | unknown | _—_ |
| [com/sza/fastmediasorter/di/PlayerContractsModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/PlayerContractsModule.kt) | `PlayerContractsModule` | di | 30 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/di/TransferModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/TransferModule.kt) | `TransferModule` | di | 80 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/hash/FileHasher.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/hash/FileHasher.kt) | `FileHasher` | domain | 24 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/input/CommandGroup.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/CommandGroup.kt) | `CommandGroup` | domain | 12 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/input/CommandId.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/CommandId.kt) | `CommandId` | domain | 89 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/input/InputBinding.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/InputBinding.kt) | `InputBinding` | domain | 12 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/input/InputTrigger.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/InputTrigger.kt) | `InputTrigger` | domain | 88 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/input/usecase/DetectConflictsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/DetectConflictsUseCase.kt) | `DetectConflictsUseCase` | domain | 32 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/input/usecase/ResetAllUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/ResetAllUseCase.kt) | `ResetAllUseCase` | domain | 14 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/input/usecase/ResetBindingUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/ResetBindingUseCase.kt) | `ResetBindingUseCase` | domain | 16 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/input/usecase/ResetGroupUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/ResetGroupUseCase.kt) | `ResetGroupUseCase` | domain | 26 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/input/usecase/SetBindingUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/SetBindingUseCase.kt) | `SetBindingUseCase` | domain | 21 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/AppSettings.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt) | `AppSettings` | domain | 209 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/AudioMetadata.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AudioMetadata.kt) | `AudioMetadata` | domain | 16 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/BackgroundAudioExitBehavior.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/BackgroundAudioExitBehavior.kt) | `BackgroundAudioExitBehavior` | domain | 17 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/CredentialAuditEntry.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/CredentialAuditEntry.kt) | `CredentialAuditEntry` | domain | 49 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/DuplicateModels.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/DuplicateModels.kt) | `DuplicateGroup` | domain | 26 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/FavoritesExportModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/FavoritesExportModel.kt) | `FavoritesExportFile` | domain | 68 | 2026-03-23 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/FileTypeFilter.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/FileTypeFilter.kt) | `FileTypeFlags` | domain | 52 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/GamepadAction.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/GamepadAction.kt) | `GamepadAction` | domain | 38 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/MediaExtensions.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/MediaExtensions.kt) | `MediaExtensions` | domain | 46 | 2026-03-22 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/Models.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt) | `ResourceType` | domain | 265 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/OffloadModels.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/OffloadModels.kt) | `OffloadOffer` | domain | 44 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/PrefetchCacheMultiplier.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PrefetchCacheMultiplier.kt) | `PrefetchCacheMultiplier` | domain | 24 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/PrefetchPlan.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PrefetchPlan.kt) | `Protocol` | domain | 71 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/ResourceConnectionTestResult.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceConnectionTestResult.kt) | `ResourceConnectionStatus` | domain | 15 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/ResourceEditorMode.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceEditorMode.kt) | `ResourceEditorMode` | domain | 7 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/ResourceFormData.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceFormData.kt) | `ResourceFormData` | domain | 79 | 2026-02-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/ResourceValidationResult.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceValidationResult.kt) | `ResourceFieldKey` | domain | 66 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/ResourceVerificationStatus.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceVerificationStatus.kt) | `ResourceVerificationStatus` | domain | 7 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/ResumeState.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResumeState.kt) | `ScreenType` | domain | 26 | 2026-03-16 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/ScheduledOperation.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScheduledOperation.kt) | `ScheduledOperation` | domain | 22 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/ScheduledOpType.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScheduledOpType.kt) | `ScheduledOpType` | domain | 8 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/StereoMode.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StereoMode.kt) | `StereoMode` | domain | 159 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/StreamingCacheCleanupMode.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StreamingCacheCleanupMode.kt) | `StreamingCacheCleanupMode` | domain | 23 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/TimeFilter.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/TimeFilter.kt) | `TimeFilter` | domain | 9 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/model/WearSyncPayload.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSyncPayload.kt) | `WearNetworkSourcePayload` | domain | 31 | 2026-04-14 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/models/TranslationFontSize.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/models/TranslationFontSize.kt) | `TranslationFontSize` | domain | 44 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/playback/PlaybackCompletionDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/playback/PlaybackCompletionDetector.kt) | `PlaybackCompletionDetector` | domain | 31 | — | unknown | _—_ |
| [com/sza/fastmediasorter/domain/playback/PrefetchFormula.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/playback/PrefetchFormula.kt) | `PrefetchFormula` | domain | 219 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/repository/DuplicateHashRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/DuplicateHashRepository.kt) | `DuplicateHashRepository` | domain | 29 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/repository/FavoritesRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/FavoritesRepository.kt) | `FavoritesRepository` | domain | 15 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/repository/MediaStoreRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/MediaStoreRepository.kt) | `MediaStoreRepository` | domain | 61 | 2026-03-22 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/repository/NetworkCredentialsRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/NetworkCredentialsRepository.kt) | `NetworkCredentialsRepository` | domain | 22 | 2026-02-28 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/repository/PlaybackPositionRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PlaybackPositionRepository.kt) | `PlaybackPositionRepository` | domain | 47 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/repository/ResourceRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ResourceRepository.kt) | `ResourceRepository` | domain | 63 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/repository/ResumeStateRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ResumeStateRepository.kt) | `ResumeStateRepository` | domain | 14 | 2026-03-16 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/repository/ScheduledOperationRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ScheduledOperationRepository.kt) | `ScheduledOperationRepository` | domain | 16 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/repository/SettingsRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/SettingsRepository.kt) | `SettingsRepository` | domain | 23 | 2026-03-13 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/repository/StreamingCacheRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/StreamingCacheRepository.kt) | `StreamingCacheRepository` | domain | 61 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/repository/ThumbnailCacheRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ThumbnailCacheRepository.kt) | `ThumbnailCacheRepository` | domain | 59 | 2026-02-18 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/repository/WearableDataLayerRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/WearableDataLayerRepository.kt) | `WearableDataLayerRepository` | domain | 15 | 2026-04-14 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/strategy/CloudResourceStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/CloudResourceStrategy.kt) | `CloudResourceStrategy` | domain | 76 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/strategy/FtpResourceStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/FtpResourceStrategy.kt) | `FtpResourceStrategy` | domain | 83 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/strategy/LocalResourceStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/LocalResourceStrategy.kt) | `LocalResourceStrategy` | domain | 67 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/strategy/ResourceStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/ResourceStrategy.kt) | `ResourceFieldSchema` | domain | 20 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/strategy/SftpResourceStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/SftpResourceStrategy.kt) | `SftpResourceStrategy` | domain | 86 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/strategy/SmbResourceStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/SmbResourceStrategy.kt) | `SmbResourceStrategy` | domain | 81 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/transfer/FileOperationError.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/FileOperationError.kt) | `FileOperationError` | domain | 72 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/transfer/FileOperationErrorHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/FileOperationErrorHandler.kt) | `FileOperationErrorHandler` | domain | 200 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/transfer/FileTransferProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/FileTransferProvider.kt) | `FileTransferProvider` | domain | 122 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/transfer/ProgressTracker.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/ProgressTracker.kt) | `ProgressTracker` | domain | 143 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/transfer/TempFileManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/TempFileManager.kt) | `TempFileManager` | domain | 179 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/AddResourceAsDestinationUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/AddResourceAsDestinationUseCase.kt) | `AddResourceAsDestinationUseCase` | domain | 48 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/AddResourceUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/AddResourceUseCase.kt) | `AddMultipleResult` | domain | 97 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/AdjustImageUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/AdjustImageUseCase.kt) | `AdjustImageUseCase` | domain | 114 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/AppendToScheduledLogUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/AppendToScheduledLogUseCase.kt) | `AppendToScheduledLogUseCase` | domain | 43 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ApplyImageFilterUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyImageFilterUseCase.kt) | `ApplyImageFilterUseCase` | domain | 124 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ArchiveFilesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ArchiveFilesUseCase.kt) | `ArchiveProgress` | domain | 256 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/BackupData.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt) | `BackupPayload` | domain | 231 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/BackupMapper.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt) | `BackupMapper` | domain | 493 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/BackupToGoogleDriveUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupToGoogleDriveUseCase.kt) | `BackupToGoogleDriveUseCase` | domain | 276 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ByteProgressCallback.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ByteProgressCallback.kt) | `ByteProgressCallback` | domain | 32 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/CalculateOptimalCacheSizeUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CalculateOptimalCacheSizeUseCase.kt) | `CalculateOptimalCacheSizeUseCase` | domain | 71 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ChangeGifSpeedUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ChangeGifSpeedUseCase.kt) | `ChangeGifSpeedUseCase` | domain | 172 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/CleanupOrphanedTempFilesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupOrphanedTempFilesUseCase.kt) | `CleanupOrphanedTempFilesUseCase` | domain | 122 | 2026-03-16 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/CleanupTrashFoldersUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupTrashFoldersUseCase.kt) | `CleanupTrashFoldersUseCase` | domain | 136 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/CleanupTrashUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupTrashUseCase.kt) | `CleanupTrashUseCase` | domain | 103 | 2026-02-20 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ClearResumeStateUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ClearResumeStateUseCase.kt) | `ClearResumeStateUseCase` | domain | 13 | 2026-03-16 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ClearScheduledOperationsLogUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ClearScheduledOperationsLogUseCase.kt) | `ClearScheduledOperationsLogUseCase` | domain | 17 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ClearScheduledOperationsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ClearScheduledOperationsUseCase.kt) | `ClearScheduledOperationsUseCase` | domain | 11 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ComputeFileHashUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ComputeFileHashUseCase.kt) | `ComputeFileHashUseCase` | domain | 63 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/CreateDirectoryUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CreateDirectoryUseCase.kt) | `CreateDirectoryUseCase` | domain | 61 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/CredentialAuditor.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CredentialAuditor.kt) | `CredentialAuditor` | domain | 111 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/DeleteByFileSizeUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeleteByFileSizeUseCase.kt) | `DeleteByFileSizeUseCase` | domain | 94 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/DeleteDirectoriesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeleteDirectoriesUseCase.kt) | `DeleteDirectoriesUseCase` | domain | 57 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/DeleteFilesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeleteFilesUseCase.kt) | `DeleteFilesUseCase` | domain | 26 | 2026-03-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/DeletePathPolicy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeletePathPolicy.kt) | `DeletePathPolicy` | domain | 21 | 2026-03-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/DeleteResourceUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeleteResourceUseCase.kt) | `DeleteResourceUseCase` | domain | 39 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/DeleteScheduledOperationUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeleteScheduledOperationUseCase.kt) | `DeleteScheduledOperationUseCase` | domain | 11 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/DetectDuplicatesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DetectDuplicatesUseCase.kt) | `DetectDuplicatesUseCase` | domain | 129 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/DiscoverNetworkResourcesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DiscoverNetworkResourcesUseCase.kt) | `NetworkHost` | domain | 197 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/DownloadNetworkFileUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DownloadNetworkFileUseCase.kt) | `DownloadNetworkFileUseCase` | domain | 205 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ExecuteScheduledOperationUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExecuteScheduledOperationUseCase.kt) | `ScheduledExecutionResult` | domain | 338 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ExportFavoritesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExportFavoritesUseCase.kt) | `ExportFavoritesUseCase` | domain | 106 | 2026-03-23 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ExportSettingsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExportSettingsUseCase.kt) | `ExportSettingsUseCase` | domain | 324 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ExtractArchiveUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExtractArchiveUseCase.kt) | `ExtractProgress` | domain | 357 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ExtractExifMetadataUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExtractExifMetadataUseCase.kt) | `ExifMetadata` | domain | 160 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ExtractGifFramesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExtractGifFramesUseCase.kt) | `ExtractGifFramesUseCase` | domain | 263 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ExtractVideoMetadataUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExtractVideoMetadataUseCase.kt) | `VideoMetadata` | domain | 186 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/FavoritesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FavoritesUseCase.kt) | `FavoritesUseCase` | domain | 53 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/FileOperationResultExt.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationResultExt.kt) | `FileOperationResultExt` | domain | 139 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt) | `FileOperation` | domain | 512 | 2026-03-30 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/FlipImageUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FlipImageUseCase.kt) | `FlipImageUseCase` | domain | 146 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/GetDestinationsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetDestinationsUseCase.kt) | `GetDestinationsUseCase` | domain | 70 | 2026-04-16 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/GetMediaFilesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetMediaFilesUseCase.kt) | `SizeFilter` | domain | 449 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/GetResourcesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetResourcesUseCase.kt) | `GetResourcesUseCase` | domain | 47 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/GetResumeStateUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetResumeStateUseCase.kt) | `GetResumeStateUseCase` | domain | 14 | 2026-03-16 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/GetScheduledOperationsLogUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetScheduledOperationsLogUseCase.kt) | `GetScheduledOperationsLogUseCase` | domain | 18 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/GetScheduledOperationsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetScheduledOperationsUseCase.kt) | `GetScheduledOperationsUseCase` | domain | 13 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ImportFavoritesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportFavoritesUseCase.kt) | `ImportFavoritesUseCase` | domain | 191 | 2026-03-23 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt) | `ImportSettingsUseCase` | domain | 573 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt) | `LinkAutoDownloadCoordinator` | domain | 152 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt) | `LinkExtractionRegistry` | domain | 30 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/link/MediaMimeWhitelist.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/MediaMimeWhitelist.kt) | `MediaMimeWhitelist` | domain | 68 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt) | `UrlExtractionStrategy` | domain | 44 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/LocalCopyFileOperation.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalCopyFileOperation.kt) | `LocalCopyFileOperation` | domain | 143 | 2026-03-30 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/LocalDeleteFileOperation.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalDeleteFileOperation.kt) | `LocalDeleteFileOperation` | domain | 268 | 2026-03-30 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/LocalMoveFileOperation.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalMoveFileOperation.kt) | `LocalMoveFileOperation` | domain | 197 | 2026-03-30 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/LocalRenameFileOperation.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalRenameFileOperation.kt) | `LocalRenameFileOperation` | domain | 69 | 2026-03-30 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/MediaScannerFactory.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MediaScannerFactory.kt) | `MediaScannerFactory` | domain | 37 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/MigrateCameraResourceUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MigrateCameraResourceUseCase.kt) | `MigrateCameraResourceUseCase` | domain | 28 | 2026-03-22 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/NetworkImageEditUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/NetworkImageEditUseCase.kt) | `NetworkImageEditUseCase` | domain | 338 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/NetworkSpeedTestUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/NetworkSpeedTestUseCase.kt) | `SpeedTestResult` | domain | 439 | 2026-04-16 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCase.kt) | `ProvisionDefaultResourcesUseCase` | domain | 167 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/RenameVirtualResourcesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RenameVirtualResourcesUseCase.kt) | `RenameVirtualResourcesUseCase` | domain | 54 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ResetSmbConnectionsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResetSmbConnectionsUseCase.kt) | `ResetSmbConnectionsUseCase` | domain | 21 | 2026-02-11 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ResourceEditorUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResourceEditorUseCase.kt) | `ResourceEditorSaveResult` | domain | 731 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/RestoreDeletedUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RestoreDeletedUseCase.kt) | `RestoreDeletedUseCase` | domain | 249 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/RestoreFromGoogleDriveUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RestoreFromGoogleDriveUseCase.kt) | `RestoreFromGoogleDriveUseCase` | domain | 269 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/RotateImageUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RotateImageUseCase.kt) | `RotateImageUseCase` | domain | 132 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/SaveGifFirstFrameUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveGifFirstFrameUseCase.kt) | `SaveGifFirstFrameUseCase` | domain | 94 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/SaveResumeStateUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveResumeStateUseCase.kt) | `SaveResumeStateUseCase` | domain | 14 | 2026-03-16 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/scan/IncrementalScanStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/scan/IncrementalScanStrategy.kt) | `IncrementalScanStrategy` | domain | 157 | 2026-03-14 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/scan/ScanDeltaDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/scan/ScanDeltaDetector.kt) | `ScanDeltaDetector` | domain | 122 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/scan/ScanDispatcher.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/scan/ScanDispatcher.kt) | `ScanDispatcher` | domain | 85 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/scan/ScanSettings.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/scan/ScanSettings.kt) | `ScanSettings` | domain | 64 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ScanLocalFoldersUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ScanLocalFoldersUseCase.kt) | `ScanLocalFoldersUseCase` | domain | 256 | 2026-03-22 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ScanProgressCallback.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ScanProgressCallback.kt) | `ScanProgressCallback` | domain | 33 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/ScheduleNetworkSyncUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ScheduleNetworkSyncUseCase.kt) | `ScheduleNetworkSyncUseCase` | domain | 69 | 2026-03-24 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/SearchAudioCoverUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SearchAudioCoverUseCase.kt) | `SearchAudioCoverUseCase` | domain | 284 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/SearchLyricsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SearchLyricsUseCase.kt) | `SearchLyricsUseCase` | domain | 805 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/SearchQueryUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SearchQueryUtils.kt) | `SearchQueryUtils` | domain | 78 | 2026-03-14 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/SendResourcesToWatchUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SendResourcesToWatchUseCase.kt) | `SendResult` | domain | 87 | 2026-04-15 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt) | `SmbOperationsUseCase` | domain | 739 | 2026-04-16 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/StreamOffloadUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/StreamOffloadUseCase.kt) | `StreamOffloadUseCase` | domain | 243 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/SyncMediaStoreUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SyncMediaStoreUseCase.kt) | `SyncMediaStoreUseCase` | domain | 132 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/SyncNetworkResourcesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SyncNetworkResourcesUseCase.kt) | `SyncNetworkResourcesUseCase` | domain | 149 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/TestCredentialsLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/TestCredentialsLoader.kt) | `TestCredentialsLoader` | domain | 191 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/UnusedCredentialPolicy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/UnusedCredentialPolicy.kt) | `UnusedCredentialPolicy` | domain | 63 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/UpdateResourceUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/UpdateResourceUseCase.kt) | `UpdateResourceUseCase` | domain | 29 | 2026-02-25 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/UpdateScheduledOperationUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/UpdateScheduledOperationUseCase.kt) | `UpdateScheduledOperationUseCase` | domain | 13 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/UpsertScheduledOperationUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/UpsertScheduledOperationUseCase.kt) | `UpsertScheduledOperationUseCase` | domain | 13 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/domain/usecase/VirtualResourceDefaultNames.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/VirtualResourceDefaultNames.kt) | `VirtualResourceDefaultNames` | domain | 49 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/FastMediaSorterApp.kt](app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt) | `FastMediaSorterApp` | other | 499 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/service/PhoneWearListenerService.kt](app_v2/src/main/java/com/sza/fastmediasorter/service/PhoneWearListenerService.kt) | `PhoneWearListenerService` | service | 74 | 2026-04-14 | unknown | _—_ |
| [com/sza/fastmediasorter/core/ui/BaseActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt) | `BaseActivity` | ui | 151 | 2026-03-24 | unknown | _—_ |
| [com/sza/fastmediasorter/core/ui/BaseFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseFragment.kt) | `BaseFragment` | ui | 66 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/core/ui/BaseViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseViewModel.kt) | `BaseViewModel` | ui | 73 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/core/ui/UiEvent.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/ui/UiEvent.kt) | `UiEvent` | ui | 57 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt) | `AddResourceActivity` | ui | 419 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceBridge.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceBridge.kt) | `AddResourceBridge` | ui | 27 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt) | `AddResourceConnectionManager` | ui | 410 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceFinalizer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFinalizer.kt) | `AddResourceFinalizer` | ui | 199 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt) | `AddResourceFormManager` | ui | 413 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceHelper.kt) | `AddResourceHelper` | ui | 243 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceKeyboardDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceKeyboardDelegate.kt) | `AddResourceKeyboardDelegate` | ui | 42 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt) | `AddResourceNetworkScanCoordinator` | ui | 94 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt) | `AddResourceScanManager` | ui | 277 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceSftpFtpCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSftpFtpCoordinator.kt) | `AddResourceSftpFtpCoordinator` | ui | 287 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceSftpKeyCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSftpKeyCoordinator.kt) | `AddResourceSftpKeyCoordinator` | ui | 174 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceSmbCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSmbCoordinator.kt) | `AddResourceSmbCoordinator` | ui | 317 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt) | `AddResourceState` | ui | 555 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/AddResourceVirtualCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceVirtualCoordinator.kt) | `AddResourceVirtualCoordinator` | ui | 292 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/NetworkDiscoveryDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/NetworkDiscoveryDialog.kt) | `NetworkDiscoveryDialog` | ui | 167 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/ResourceToAddAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/ResourceToAddAdapter.kt) | `ResourceToAddAdapter` | ui | 186 | 2026-03-02 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/widgets/IpAddressEditText.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/widgets/IpAddressEditText.kt) | `IpAddressEditText` | ui | 168 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/addresource/widgets/NetworkPathEditText.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/widgets/NetworkPathEditText.kt) | `NetworkPathEditText` | ui | 195 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/AdapterDragController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterDragController.kt) | `AdapterDragController` | ui | 57 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/AdapterFileInfoFormatter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterFileInfoFormatter.kt) | `AdapterFileInfoFormatter` | ui | 99 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt) | `AdapterThumbnailLoader` | ui | 626 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/BrowseActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt) | `BrowseActivity` | ui | 393 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/BrowseEvent.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseEvent.kt) | `BrowseEvent` | ui | 47 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/BrowseState.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseState.kt) | `BrowseState` | ui | 42 | 2026-04-12 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt) | `PlaybackStatus` | ui | 708 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/cache/BrowseCacheManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/cache/BrowseCacheManager.kt) | `BrowseCacheManager` | ui | 163 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/filelist/BrowseFileListManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/filelist/BrowseFileListManager.kt) | `BrowseFileListManager` | ui | 185 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/helpers/BrowseFileDragTouchCallback.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseFileDragTouchCallback.kt) | `BrowseFileDragTouchCallback` | ui | 68 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/InlinePlaybackAnimator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/InlinePlaybackAnimator.kt) | `InlinePlaybackAnimator` | ui | 55 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/loading/BrowseLoadingManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/loading/BrowseLoadingManager.kt) | `BrowseLoadingManager` | ui | 295 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseActionBarManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseActionBarManager.kt) | `BrowseActionBarManager` | ui | 36 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveDialogManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveDialogManager.kt) | `BrowseArchiveDialogManager` | ui | 194 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveManager.kt) | `BrowseArchiveManager` | ui | 308 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseBinaryFileHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseBinaryFileHandler.kt) | `BrowseBinaryFileHandler` | ui | 128 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt) | `BrowseButtonSetupHelper` | ui | 217 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt) | `BrowseCameraCaptureManager` | ui | 394 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseCloudAuthManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCloudAuthManager.kt) | `BrowseCloudAuthManager` | ui | 193 | 2026-03-24 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseDeleteManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDeleteManager.kt) | `BrowseDeleteManager` | ui | 301 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseDialogCallbacksImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogCallbacksImpl.kt) | `BrowseDialogCallbacksImpl` | ui | 118 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt) | `BrowseDialogHelper` | ui | 734 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseDirectoryOpsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDirectoryOpsManager.kt) | `BrowseDirectoryOpsManager` | ui | 82 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseEdgeToEdgeHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEdgeToEdgeHelper.kt) | `BrowseEdgeToEdgeHelper` | ui | 72 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseErrorDisplayManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseErrorDisplayManager.kt) | `BrowseErrorDisplayManager` | ui | 162 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt) | `BrowseEventHandler` | ui | 286 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseFileListMutationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileListMutationManager.kt) | `BrowseFileListMutationManager` | ui | 172 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt) | `BrowseFileObserverManager` | ui | 228 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseFileOpenManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOpenManager.kt) | `BrowseFileOpenManager` | ui | 166 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt) | `PendingMoveOperation` | ui | 942 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseFolderPickerHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFolderPickerHandler.kt) | `BrowseFolderPickerHandler` | ui | 176 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt) | `BrowseInlineAudioManager` | ui | 345 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseLauncherManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLauncherManager.kt) | `BrowseLauncherCallbacks` | ui | 71 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleHelper.kt) | `BrowseLifecycleHelper` | ui | 108 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleSetupManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleSetupManager.kt) | `BrowseLifecycleSetupManager` | ui | 165 | 2026-04-12 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseListSubmitManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseListSubmitManager.kt) | `BrowseListSubmitManager` | ui | 188 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseLoadingAuxManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLoadingAuxManager.kt) | `BrowseLoadingAuxManager` | ui | 290 | 2026-04-18 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt) | `BrowseManagerInitializer` | ui | 885 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseManualOrderCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManualOrderCoordinator.kt) | `BrowseManualOrderCoordinator` | ui | 64 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseMediaStoreObserver.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMediaStoreObserver.kt) | `BrowseMediaStoreObserver` | ui | 56 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseNavigationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseNavigationManager.kt) | `DirectoryCacheEntry` | ui | 472 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseObserverManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseObserverManager.kt) | `BrowseObserverManager` | ui | 200 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseRecyclerViewManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRecyclerViewManager.kt) | `BrowseRecyclerViewManager` | ui | 156 | 2026-04-12 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseRefreshManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRefreshManager.kt) | `BrowseRefreshManager` | ui | 168 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt) | `BrowseResourceLoadManager` | ui | 494 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseResourceStateManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceStateManager.kt) | `BrowseResourceStateManager` | ui | 169 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseRoutingDecision.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRoutingDecision.kt) | `BrowseRoutingDecision` | ui | 41 | — | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseScrollButtonManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseScrollButtonManager.kt) | `BrowseScrollButtonManager` | ui | 100 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseScrollThumbnailListener.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseScrollThumbnailListener.kt) | `BrowseScrollThumbnailListener` | ui | 50 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseSelectionManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseSelectionManager.kt) | `BrowseSelectionManager` | ui | 37 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseShutdownCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseShutdownCoordinator.kt) | `BrowseShutdownCoordinator` | ui | 85 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseSmallControlsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseSmallControlsManager.kt) | `BrowseSmallControlsManager` | ui | 160 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseSortFilterManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseSortFilterManager.kt) | `BrowseSortFilterManager` | ui | 332 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseSortMenuManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseSortMenuManager.kt) | `BrowseSortMenuManager` | ui | 125 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseStateManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateManager.kt) | `BrowseStateManager` | ui | 68 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseStateSyncManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateSyncManager.kt) | `BrowseStateSyncManager` | ui | 162 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseStateUiUpdater.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateUiUpdater.kt) | `BrowseStateUiUpdater` | ui | 183 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/BrowseUtilityManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseUtilityManager.kt) | `BrowseUtilityManager` | ui | 161 | 2026-03-02 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/KeyboardNavigationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/KeyboardNavigationManager.kt) | `KeyboardNavigationManager` | ui | 154 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt) | `ResourceOpsMenuManager` | ui | 347 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt) | `MediaFileAdapter` | ui | 1103 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/MediaFileDiffCallback.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileDiffCallback.kt) | `MediaFileDiffCallback` | ui | 44 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/metadata/BrowseMetadataManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/metadata/BrowseMetadataManager.kt) | `BrowseMetadataManager` | ui | 63 | 2026-02-20 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/PagingLoadStateAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/PagingLoadStateAdapter.kt) | `PagingLoadStateAdapter` | ui | 54 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/PagingMediaFileAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/PagingMediaFileAdapter.kt) | `PagingMediaFileAdapter` | ui | 803 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/selection/BrowseSelectionManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/selection/BrowseSelectionManager.kt) | `BrowseSelectionManager` | ui | 162 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/browse/undo/BrowseUndoManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/undo/BrowseUndoManager.kt) | `BrowseUndoManager` | ui | 246 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/cloudfolders/CloudFolderAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/CloudFolderAdapter.kt) | `CloudFolderAdapter` | ui | 54 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/cloudfolders/CloudFolderItem.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/CloudFolderItem.kt) | `CloudFolderItem` | ui | 9 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/cloudfolders/CloudFolderItemBinding.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/CloudFolderItemBinding.kt) | `CloudFolderItemBinding` | ui | 45 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/cloudfolders/CloudFolderPickerKeyboardDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/CloudFolderPickerKeyboardDelegate.kt) | `CloudFolderPickerKeyboardDelegate` | ui | 50 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerActivity.kt) | `DropboxFolderPickerActivity` | ui | 154 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerViewModel.kt) | `DropboxFolderPickerState` | ui | 194 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerActivity.kt) | `GoogleDriveFolderPickerActivity` | ui | 179 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerViewModel.kt) | `GoogleDriveFolderPickerState` | ui | 309 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerActivity.kt) | `OneDriveFolderPickerActivity` | ui | 152 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerViewModel.kt) | `OneDriveFolderPickerState` | ui | 194 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/BreadcrumbView.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/BreadcrumbView.kt) | `BreadcrumbView` | ui | 148 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/DialogUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/DialogUtils.kt) | `DialogUtils` | ui | 104 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/ErrorDialogHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/ErrorDialogHelper.kt) | `ErrorDialogHelper` | ui | 64 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/FocusManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/FocusManager.kt) | `FocusManager` | ui | 221 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/input/FocusRingHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/FocusRingHelper.kt) | `FocusRingHelper` | ui | 80 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/input/InputAction.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputAction.kt) | `FocusDirection` | ui | 117 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/input/InputHelpDialogFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputHelpDialogFragment.kt) | `InputHelpDialogFragment` | ui | 130 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/input/InputHelpEntry.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputHelpEntry.kt) | `InputHelpEntry` | ui | 34 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/input/InputHelpLinkResolver.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputHelpLinkResolver.kt) | `InputHelpLinkResolver` | ui | 35 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/input/InputHelpRegistry.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputHelpRegistry.kt) | `InputHelpRegistry` | ui | 180 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/input/InputSurface.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputSurface.kt) | `InputSurface` | ui | 53 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/IpAddressInputFilter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/IpAddressInputFilter.kt) | `IpAddressInputFilter` | ui | 55 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/MediaGroupPalette.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/MediaGroupPalette.kt) | `MediaGroupPalette` | ui | 41 | 2026-02-16 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/common/MouseEventHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/MouseEventHandler.kt) | `MouseEventHandler` | ui | 254 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/ColorPickerDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ColorPickerDialog.kt) | `ColorPickerDialog` | ui | 165 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/DeleteDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DeleteDialog.kt) | `DeleteDialog` | ui | 183 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/DestinationAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DestinationAdapter.kt) | `DestinationAdapter` | ui | 64 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/DestinationPickerDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DestinationPickerDialog.kt) | `DestinationPickerDialog` | ui | 132 | 2026-04-18 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/DialogKeyboardDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DialogKeyboardDelegate.kt) | `DialogKeyboardDelegate` | ui | 99 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt) | `ErrorDialog` | ui | 96 | 2026-04-16 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt) | `FileInfoDialog` | ui | 947 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt) | `FileOperationDestinationDialog` | ui | 607 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt) | `FileOperationProgressDialog` | ui | 178 | 2026-03-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/GifEditorDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/GifEditorDialog.kt) | `GifEditorDialog` | ui | 364 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/helpers/GifEditorHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/GifEditorHelper.kt) | `GifEditorHelper` | ui | 153 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/ImageEditDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ImageEditDialog.kt) | `ImageEditDialog` | ui | 298 | 2026-02-16 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/MaterialProgressDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/MaterialProgressDialog.kt) | `MaterialProgressDialog` | ui | 153 | 2026-03-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/PlayerSettingsDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/PlayerSettingsDialog.kt) | `PlayerSettingsDialog` | ui | 195 | 2026-04-17 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/RenameDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/RenameDialog.kt) | `RenameDialog` | ui | 295 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/ResourcePickerDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ResourcePickerDialog.kt) | `ResourcePickerDialog` | ui | 148 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/ResourceTypeSelectorDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ResourceTypeSelectorDialog.kt) | `ResourceTypeSelectorDialog` | ui | 71 | 2026-02-20 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/ScheduledLogDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScheduledLogDialog.kt) | `ScheduledLogDialog` | ui | 47 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/ScheduledOperationDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScheduledOperationDialog.kt) | `ScheduledOperationDialog` | ui | 457 | 2026-04-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/dialog/TooltipDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/TooltipDialog.kt) | `TooltipDialog` | ui | 55 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/duplicates/DuplicateGroupAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicateGroupAdapter.kt) | `DuplicateGroupAdapter` | ui | 127 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/duplicates/DuplicatesActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesActivity.kt) | `DuplicatesActivity` | ui | 59 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/duplicates/DuplicatesFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesFragment.kt) | `DuplicatesFragment` | ui | 239 | 2026-04-12 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/duplicates/DuplicatesViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesViewModel.kt) | `DuplicatesState` | ui | 288 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/image/ImageDisplayUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/image/ImageDisplayUtils.kt) | `ImageDisplayUtils` | ui | 90 | 2026-02-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/keybinding/CaptureDialogFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/CaptureDialogFragment.kt) | `CaptureDialogFragment` | ui | 188 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/keybinding/helpers/KeybindingRowLabelFormatter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/helpers/KeybindingRowLabelFormatter.kt) | `KeybindingRowLabelFormatter` | ui | 115 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/keybinding/helpers/ResetConfirmationDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/helpers/ResetConfirmationDialog.kt) | `ResetConfirmationDialog` | ui | 32 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/keybinding/KeybindingListAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingListAdapter.kt) | `KeybindingListItem` | ui | 156 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/keybinding/KeybindingRemapActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapActivity.kt) | `KeybindingRemapActivity` | ui | 159 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/keybinding/KeybindingRemapViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapViewModel.kt) | `PendingConfirmation` | ui | 191 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/FilterResourceDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/FilterResourceDialog.kt) | `FilterResourceDialog` | ui | 241 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/helpers/KeyboardNavigationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/KeyboardNavigationHandler.kt) | `KeyboardNavigationHandler` | ui | 229 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt) | `MainLayoutChromeManager` | ui | 132 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt) | `MainResourceTabsManager` | ui | 121 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt) | `MainResumePlaybackHelper` | ui | 196 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt) | `MainStoragePermissionsHelper` | ui | 90 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/helpers/ResourceFilterManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceFilterManager.kt) | `ResourceFilterManager` | ui | 135 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/helpers/ResourceItemTouchCallback.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceItemTouchCallback.kt) | `ResourceItemTouchCallback` | ui | 87 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/helpers/ResourceNavigationCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceNavigationCoordinator.kt) | `ResourceNavigationCoordinator` | ui | 195 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/helpers/ResourceOrderManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceOrderManager.kt) | `ResourceOrderManager` | ui | 138 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/helpers/ResourcePasswordManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourcePasswordManager.kt) | `ResourcePasswordManager` | ui | 136 | 2026-03-04 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/helpers/ResourceScanCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceScanCoordinator.kt) | `ResourceScanCoordinator` | ui | 295 | 2026-03-14 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/MainActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt) | `MainActivity` | ui | 972 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/MainViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt) | `ResourceTab` | ui | 648 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/main/ResourceAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt) | `DragStartListener` | ui | 779 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt) | `AudioCoverArtLoader` | ui | 408 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt) | `AudioPlaybackService` | ui | 333 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt) | `PlayerCommandPanelCallbackImpl` | ui | 269 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/callbacks/PlayerGestureCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerGestureCallbackImpl.kt) | `PlayerGestureCallbackImpl` | ui | 130 | 2026-03-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt) | `PlayerImageLoadingCallbackImpl` | ui | 70 | 2026-03-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/callbacks/PlayerKeyboardCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerKeyboardCallbackImpl.kt) | `PlayerKeyboardCallbackImpl` | ui | 199 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt) | `PlayerPlaybackCallbackImpl` | ui | 136 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/callbacks/PlayerTouchZoneCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerTouchZoneCallbackImpl.kt) | `PlayerTouchZoneCallbackImpl` | ui | 107 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/callbacks/PlayerTranslationButtonCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerTranslationButtonCallbackImpl.kt) | `PlayerTranslationButtonCallbackImpl` | ui | 61 | 2026-03-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/callbacks/PlayerUiStateCoordinatorCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerUiStateCoordinatorCallbackImpl.kt) | `PlayerUiStateCoordinatorCallbackImpl` | ui | 151 | 2026-04-17 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/CommandPanelController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt) | `CommandPanelController` | ui | 1006 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/commands/PlayerCommandOverrides.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/commands/PlayerCommandOverrides.kt) | `FullscreenCommandOverride` | ui | 21 | 2026-04-20 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/contracts/PlaybackCommandModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackCommandModel.kt) | `PlaybackCommand` | ui | 105 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/contracts/PlaybackPreferencesFacade.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackPreferencesFacade.kt) | `PlaybackPreferencesFacade` | ui | 17 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/contracts/PlayerHostCapabilities.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlayerHostCapabilities.kt) | `PlayerHostCapabilities` | ui | 87 | 2026-04-24 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/contracts/StereoDetectionFacade.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/StereoDetectionFacade.kt) | `StereoDetectionFacade` | ui | 44 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/contracts/VideoPlayerHandle.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/VideoPlayerHandle.kt) | `VideoPlayerHandle` | ui | 43 | 2026-04-24 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/DynamicBackgroundProcessor.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/DynamicBackgroundProcessor.kt) | `DynamicBackgroundProcessor` | ui | 253 | 2026-04-16 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt) | `PlayerEntryCoordinator` | ui | 64 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt) | `VrTaskTransition` | ui | 125 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/ExifPhotoSphereReader.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ExifPhotoSphereReader.kt) | `PhotoSphereMetadataReader` | ui | 107 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt) | `FileOperationsHandler` | ui | 534 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/AnimatedImageController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AnimatedImageController.kt) | `AnimatedImageController` | ui | 104 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/AudioBackgroundPhotosManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioBackgroundPhotosManager.kt) | `AudioBackgroundPhotosManager` | ui | 334 | 2026-03-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/AudioBreathingBarsView.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioBreathingBarsView.kt) | `AudioBreathingBarsView` | ui | 225 | 2026-03-08 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt) | `AudioEmptyStateController` | ui | 337 | 2026-03-14 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/AudioFocusManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioFocusManager.kt) | `AudioFocusManager` | ui | 100 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/AudioInfoDisplayHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioInfoDisplayHelper.kt) | `AudioInfoDisplayHelper` | ui | 135 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt) | `AudioServiceController` | ui | 243 | 2026-04-16 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/AudioSlideshowPhotoModeManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioSlideshowPhotoModeManager.kt) | `AudioSlideshowPhotoModeManager` | ui | 394 | 2026-02-28 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt) | `AudioWaveParticleView` | ui | 385 | 2026-04-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt) | `BackgroundMusicManager` | ui | 556 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/BaseDocumentViewerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BaseDocumentViewerManager.kt) | `BaseDocumentViewerManager` | ui | 135 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt) | `CastMediaManager` | ui | 355 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt) | `CloudPlaybackHelper` | ui | 71 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt) | `CommandPanelLayoutPlanner` | ui | 284 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt) | `DestinationButtonsManager` | ui | 397 | 2026-03-14 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt) | `DocumentPrintManager` | ui | 304 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/DocumentSelectionActionModeCallback.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentSelectionActionModeCallback.kt) | `DocumentSelectionActionModeCallback` | ui | 91 | 2026-04-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/EpubSearchResult.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubSearchResult.kt) | `EpubSearchResult` | ui | 18 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/EpubSearchResultAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubSearchResultAdapter.kt) | `EpubSearchResultAdapter` | ui | 68 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/EpubStyleManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubStyleManager.kt) | `EpubStyleManager` | ui | 121 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/EpubTocAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubTocAdapter.kt) | `EpubTocAdapter` | ui | 70 | 2026-03-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/EpubTtsDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubTtsDelegate.kt) | `EpubTtsDelegate` | ui | 92 | 2026-04-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/EpubViewerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubViewerManager.kt) | `EpubViewerManager` | ui | 2176 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/ExoPlayerControlsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ExoPlayerControlsManager.kt) | `ExoPlayerControlsManager` | ui | 136 | 2026-04-18 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/FileCopyProgressDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FileCopyProgressDialog.kt) | `FileCopyProgressDialog` | ui | 63 | 2026-03-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/FilenameOverlayAutoHideManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FilenameOverlayAutoHideManager.kt) | `FilenameOverlayAutoHideManager` | ui | 247 | 2026-04-17 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt) | `FtpPlaybackHelper` | ui | 103 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/GoogleLensButtonsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/GoogleLensButtonsManager.kt) | `GoogleLensButtonsManager` | ui | 93 | 2026-02-13 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/GoogleLensTranslationHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/GoogleLensTranslationHelper.kt) | `GoogleLensTranslationHelper` | ui | 167 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/ImageOcrManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageOcrManager.kt) | `ImageOcrManager` | ui | 177 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/LanguageBadgeDrawable.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LanguageBadgeDrawable.kt) | `LanguageBadgeDrawable` | ui | 119 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt) | `LocalPlaybackHelper` | ui | 211 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/LyricsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LyricsManager.kt) | `LyricsManager` | ui | 208 | 2026-04-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/MediaDisplayCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/MediaDisplayCoordinator.kt) | `MediaDisplayCoordinator` | ui | 56 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/NetworkFileManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NetworkFileManager.kt) | `NetworkFileManager` | ui | 387 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt) | `NowPlayingManager` | ui | 202 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PanelStereoSingleEyeNotifier.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PanelStereoSingleEyeNotifier.kt) | `PanelStereoSingleEyeNotifier` | ui | 59 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PdfBitmapCache.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfBitmapCache.kt) | `PdfBitmapCache` | ui | 73 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PdfColorConversion.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfColorConversion.kt) | `PdfColorConversion` | ui | 80 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PdfPageAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfPageAdapter.kt) | `PdfPageAdapter` | ui | 109 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PdfRendererWrapper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfRendererWrapper.kt) | `PdfRendererWrapper` | ui | 104 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PdfTextSelectionManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfTextSelectionManager.kt) | `PdfTextSelectionManager` | ui | 201 | 2026-04-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PdfThumbnailAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfThumbnailAdapter.kt) | `PdfThumbnailAdapter` | ui | 136 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PdfTtsDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfTtsDelegate.kt) | `PdfTtsDelegate` | ui | 88 | 2026-04-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt) | `PdfViewerManager` | ui | 1641 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PictureInPictureManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PictureInPictureManager.kt) | `PictureInPictureManager` | ui | 238 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlaybackHealthHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackHealthHelper.kt) | `PlaybackHealthHelper` | ui | 212 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionHelper.kt) | `PlaybackPositionHelper` | ui | 113 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerAudioMetadataManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerAudioMetadataManager.kt) | `PlayerAudioMetadataManager` | ui | 109 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt) | `PlayerBindingSafeViews` | ui | 367 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerCompactElementsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerCompactElementsManager.kt) | `PlayerCompactElementsManager` | ui | 35 | 2026-04-12 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt) | `PlayerControlsSetupManager` | ui | 555 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerDeleteUndoCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDeleteUndoCoordinator.kt) | `PlayerDeleteUndoCoordinator` | ui | 331 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt) | `PlayerDialogAndUiStateManager` | ui | 560 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerEventHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerEventHandler.kt) | `PlayerEventHandler` | ui | 227 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerFpsMeter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerFpsMeter.kt) | `PlayerFpsMeter` | ui | 69 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerGestureManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerGestureManager.kt) | `PlayerGestureCallback` | ui | 130 | 2026-02-28 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerGestureSetupManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerGestureSetupManager.kt) | `PlayerGestureSetupManager` | ui | 474 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerImageTranslationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImageTranslationManager.kt) | `PlayerImageTranslationManager` | ui | 237 | 2026-03-28 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt) | `PlayerKeyboardHandler` | ui | 344 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt) | `PlayerLifecycleManager` | ui | 555 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt) | `PlayerMediaFilesLoader` | ui | 403 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt) | `PlayerMediaLoaderManager` | ui | 970 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerMediaViewVisibilityHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaViewVisibilityHelper.kt) | `PlayerMediaViewVisibilityHelper` | ui | 65 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationCoordinator.kt) | `PlayerNavigationCoordinator` | ui | 293 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationManager.kt) | `PlayerNavigationManager` | ui | 383 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchManager.kt) | `PlayerPrefetchManager` | ui | 219 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchOffloadCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchOffloadCoordinator.kt) | `PlayerPrefetchOffloadCoordinator` | ui | 200 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerSettingsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSettingsManager.kt) | `PlayerSettingsManager` | ui | 140 | 2026-04-17 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt) | `PlayerSetupHelper` | ui | 184 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerShareManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerShareManager.kt) | `PlayerShareManager` | ui | 142 | 2026-03-28 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt) | `PlayerStereoModeCoordinator` | ui | 254 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerTouchZoneSetupManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerTouchZoneSetupManager.kt) | `PlayerTouchZoneSetupManager` | ui | 84 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PlayerUiStateCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerUiStateCoordinator.kt) | `PlayerUiStateCoordinator` | ui | 326 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PrefetchLoadControlFactory.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchLoadControlFactory.kt) | `PrefetchLoadControlFactory` | ui | 58 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PrefetchPolicyManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchPolicyManager.kt) | `PrefetchPolicyManager` | ui | 114 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/PrefetchProgressTracker.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchProgressTracker.kt) | `PrefetchProgress` | ui | 215 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/QueueTrackAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/QueueTrackAdapter.kt) | `QueueTrackAdapter` | ui | 71 | 2026-03-30 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/SaveVideoFrameManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SaveVideoFrameManager.kt) | `SaveVideoFrameManager` | ui | 285 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/SearchControlsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SearchControlsManager.kt) | `SearchControlsManager` | ui | 247 | 2026-03-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt) | `SftpPlaybackHelper` | ui | 106 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/SleepTimerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SleepTimerManager.kt) | `SleepTimerManager` | ui | 225 | 2026-03-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt) | `SmbPlaybackHelper` | ui | 119 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt) | `StandaloneFileOperationsHandler` | ui | 374 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/StandaloneFullscreenManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFullscreenManager.kt) | `StandaloneFullscreenManager` | ui | 61 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/StandalonePlayerLifecycleManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandalonePlayerLifecycleManager.kt) | `StandalonePlayerLifecycleManager` | ui | 37 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/StandalonePlayerSettingsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandalonePlayerSettingsManager.kt) | `StandalonePlayerSettingsManager` | ui | 114 | 2026-04-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/StandaloneVideoControlsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneVideoControlsManager.kt) | `StandaloneVideoControlsManager` | ui | 38 | 2026-04-18 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/StandaloneVideoTouchDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneVideoTouchDelegate.kt) | `StandaloneVideoTouchDelegate` | ui | 230 | 2026-04-18 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt) | `StandaloneViewManager` | ui | 615 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/StreamingCacheCleanupHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamingCacheCleanupHelper.kt) | `StreamingCacheCleanupHelper` | ui | 60 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/StreamOffloadOfferDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamOffloadOfferDialog.kt) | `StreamOffloadOfferDialog` | ui | 122 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/SystemBarsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SystemBarsManager.kt) | `SystemBarsManager` | ui | 202 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt) | `TesseractManager` | ui | 344 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/TextEditorAutoSaveManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextEditorAutoSaveManager.kt) | `TextEditorAutoSaveManager` | ui | 147 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/TextFilePager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextFilePager.kt) | `TextFilePager` | ui | 250 | 2026-02-18 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/TextReaderTheme.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextReaderTheme.kt) | `TextReaderTheme` | ui | 21 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/TextUndoRedoManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextUndoRedoManager.kt) | `TextUndoRedoManager` | ui | 153 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt) | `TextViewerManager` | ui | 1824 | 2026-04-21 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/TouchZoneGestureManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TouchZoneGestureManager.kt) | `TouchZoneGestureManager` | ui | 720 | 2026-03-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/TranslationButtonManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationButtonManager.kt) | `TranslationButtonManager` | ui | 388 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/TranslationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationManager.kt) | `TranslationManager` | ui | 962 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/TranslationTextUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationTextUtils.kt) | `TranslationTextUtils` | ui | 70 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/TtsReadAloudManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TtsReadAloudManager.kt) | `TtsReadAloudManager` | ui | 186 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/UndoOperationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/UndoOperationManager.kt) | `UndoOperationManager` | ui | 68 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/VideoTouchDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoTouchDelegate.kt) | `VideoTouchDelegate` | ui | 233 | 2026-04-18 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/helpers/WindowMetricsCompat.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/WindowMetricsCompat.kt) | `WindowMetricsCompat` | ui | 70 | 2026-02-12 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/ImageLoadingDiagnostics.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingDiagnostics.kt) | `ImageLoadingDiagnostics` | ui | 89 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt) | `ImageLoadingManager` | ui | 1257 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/ImagePreloadHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImagePreloadHelper.kt) | `ImagePreloadHelper` | ui | 227 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/MediaButtonRestartReceiver.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/MediaButtonRestartReceiver.kt) | `MediaButtonRestartReceiver` | ui | 75 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/MediaNotificationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/MediaNotificationManager.kt) | `MediaNotificationManager` | ui | 73 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/model/MediaItemWithMeta.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/model/MediaItemWithMeta.kt) | `MediaItemWithMeta` | ui | 16 | 2026-03-30 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/model/TouchZoneHintType.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/model/TouchZoneHintType.kt) | `TouchZoneHintType` | ui | 15 | 2026-03-13 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/Mp4SpatialMetadataReader.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/Mp4SpatialMetadataReader.kt) | `Mp4SpatialMetadataReader` | ui | 215 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/NowPlayingBottomSheetFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/NowPlayingBottomSheetFragment.kt) | `NowPlayingBottomSheetFragment` | ui | 230 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/NowPlayingViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/NowPlayingViewModel.kt) | `NowPlayingViewModel` | ui | 214 | 2026-04-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt) | `PlaybackControlDialogFragment` | ui | 705 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/PlaybackControlPreferences.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlPreferences.kt) | `PlaybackControlPreferences` | ui | 15 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/PlayerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt) | `PlayerActivity` | ui | 916 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt) | `PlayerDialogHelper` | ui | 649 | 2026-04-20 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/PlayerGestureHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerGestureHelper.kt) | `PlayerGestureHelper` | ui | 247 | 2026-02-17 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt) | `PlayerManagerInitializer` | ui | 781 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/PlayerObserverManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerObserverManager.kt) | `PlayerObserverManager` | ui | 94 | 2026-04-18 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt) | `PlayerViewerFactory` | ui | 156 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/PlayerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt) | `PlayerViewModel` | ui | 708 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/render/DualSurfaceStaticImageRenderer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/DualSurfaceStaticImageRenderer.kt) | `DualSurfaceStaticImageRenderer` | ui | 444 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/render/LegacyPrefetchQueue.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/LegacyPrefetchQueue.kt) | `LegacyPrefetchQueue` | ui | 41 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/render/NoOpStaticImageRenderer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/NoOpStaticImageRenderer.kt) | `NoOpStaticImageRenderer` | ui | 32 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/render/PrefetchQueue.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/PrefetchQueue.kt) | `PrefetchQueueConfig` | ui | 16 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/render/PriorityPrefetchQueue.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/PriorityPrefetchQueue.kt) | `PriorityPrefetchQueue` | ui | 138 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/render/RenderTarget.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/RenderTarget.kt) | `RenderPriority` | ui | 24 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/render/StaticImageRenderer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/StaticImageRenderer.kt) | `RendererMode` | ui | 44 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/render/StereoImageCropTransformation.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/StereoImageCropTransformation.kt) | `StereoImageCropTransformation` | ui | 66 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/render/TransitionPolicy.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/TransitionPolicy.kt) | `TransitionType` | ui | 18 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/SlideshowController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/SlideshowController.kt) | `SlideshowController` | ui | 290 | 2026-03-14 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/SlideshowSettingsDialogFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/SlideshowSettingsDialogFragment.kt) | `SlideshowSettingsDialogFragment` | ui | 198 | 2026-03-10 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt) | `StandalonePlayerActivity` | ui | 1035 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt) | `StandalonePlayerViewModel` | ui | 158 | 2026-04-24 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/StereoDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt) | `StereoDetector` | ui | 354 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/StereoVideoProcessor.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoVideoProcessor.kt) | `StereoVideoProcessor` | ui | 108 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/TouchZoneConfig.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/TouchZoneConfig.kt) | `TouchZoneMap` | ui | 373 | 2026-02-16 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/TouchZoneDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/TouchZoneDetector.kt) | `TouchZoneDetector` | ui | 121 | 2026-02-16 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/TouchZoneOverlayView.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/TouchZoneOverlayView.kt) | `TouchZoneOverlayView` | ui | 102 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/VerticalSeekBar.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VerticalSeekBar.kt) | `VerticalSeekBar` | ui | 76 | 2026-04-18 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/VideoColorProcessor.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoColorProcessor.kt) | `VideoColorProcessor` | ui | 71 | 2026-04-18 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt) | `VideoPlayerManager` | ui | 905 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt) | `VideoPosterExtractor` | ui | 171 | — | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/VideoTrackSelectionManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoTrackSelectionManager.kt) | `VideoTrackSelectionManager` | ui | 212 | 2026-04-20 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/views/PrefetchOverlayView.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/PrefetchOverlayView.kt) | `PrefetchOverlayView` | ui | 314 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt) | `TranslationOverlayView` | ui | 590 | 2026-03-04 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/player/VrForcedFormatResolver.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VrForcedFormatResolver.kt) | `VrForcedFormatResolver` | ui | 44 | 2026-04-20 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt) | `ResourceEditorActivity` | ui | 106 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt) | `ResourceEditorFragment` | ui | 981 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorOutcomeRenderer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorOutcomeRenderer.kt) | `ResourceEditorOutcomeRenderer` | ui | 127 | 2026-04-23 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/resourceeditor/ResourceFormViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceFormViewModel.kt) | `ResourceFieldState` | ui | 544 | 2026-04-16 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/BackupRestoreViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/BackupRestoreViewModel.kt) | `FavoritesExportUiState` | ui | 296 | 2026-03-24 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt) | `AudioSettingsFragment` | ui | 438 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/BackupRestoreFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BackupRestoreFragment.kt) | `BackupRestoreFragment` | ui | 362 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/BaseSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BaseSettingsFragment.kt) | `BaseSettingsFragment` | ui | 102 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/DocumentsSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/DocumentsSettingsFragment.kt) | `DocumentsSettingsFragment` | ui | 144 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt) | `GeneralSettingsFragment` | ui | 219 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/ImagesSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/ImagesSettingsFragment.kt) | `ImagesSettingsFragment` | ui | 248 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt) | `MediaSettingsFragment` | ui | 259 | 2026-03-20 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/OpenSourceLicensesFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OpenSourceLicensesFragment.kt) | `OpenSourceLicensesFragment` | ui | 68 | 2026-04-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt) | `OperationsSettingsFragment` | ui | 637 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt) | `OtherMediaSettingsFragment` | ui | 450 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt) | `PlaybackSettingsFragment` | ui | 585 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt) | `VideoSettingsFragment` | ui | 347 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt) | `WearSyncSettingsFragment` | ui | 99 | 2026-04-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/BeamAnimationDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/BeamAnimationDialog.kt) | `BeamAnimationDialog` | ui | 175 | 2026-04-14 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerHelper.kt) | `DefaultPlayerHelper` | ui | 352 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerManager.kt) | `DefaultPlayerManager` | ui | 120 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsBackupHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsBackupHelper.kt) | `GeneralSettingsBackupHelper` | ui | 140 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt) | `GeneralSettingsCacheHelper` | ui | 222 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCredentialHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCredentialHelper.kt) | `GeneralSettingsCredentialHelper` | ui | 172 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsImportExportHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsImportExportHelper.kt) | `GeneralSettingsImportExportHelper` | ui | 136 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt) | `GeneralSettingsLogHelper` | ui | 142 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt) | `GeneralSettingsObserversHelper` | ui | 156 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPermissionsHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPermissionsHelper.kt) | `GeneralSettingsPermissionsHelper` | ui | 133 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPrefetchHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPrefetchHelper.kt) | `GeneralSettingsPrefetchHelper` | ui | 167 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsResetHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsResetHelper.kt) | `GeneralSettingsResetHelper` | ui | 103 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt) | `GeneralSettingsSectionsHelper` | ui | 101 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt) | `GeneralSettingsViewSetupHelper` | ui | 376 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/MediaCategoryPagerAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/MediaCategoryPagerAdapter.kt) | `MediaCategoryPagerAdapter` | ui | 52 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/ScheduledOperationsAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/ScheduledOperationsAdapter.kt) | `ScheduledOperationsAdapter` | ui | 118 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/ScheduledOperationsViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/ScheduledOperationsViewModel.kt) | `ScheduledOperationsViewModel` | ui | 81 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/SettingsActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt) | `SettingsActivity` | ui | 405 | 2026-04-26 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/SettingsKeyboardNavigationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsKeyboardNavigationManager.kt) | `SettingsKeyboardNavigationManager` | ui | 80 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/SettingsPagerAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsPagerAdapter.kt) | `SettingsPagerAdapter` | ui | 25 | 2026-03-27 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/SettingsSearchAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchAdapter.kt) | `SettingsSearchAdapter` | ui | 69 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt) | `SettingsSearchDestination` | ui | 411 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt) | `ManualNetworkSyncUiState` | ui | 658 | 2026-04-18 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/settings/WearSyncViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/WearSyncViewModel.kt) | `WearSyncUiState` | ui | 83 | 2026-04-14 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt) | `ReceiveShareActivity` | ui | 360 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/share/UrlInTextDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/share/UrlInTextDetector.kt) | `UrlInTextDetector` | ui | 35 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt) | `WelcomeActivity` | ui | 609 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt) | `WelcomePagerAdapter` | ui | 241 | 2026-03-25 | unknown | _—_ |
| [com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt) | `WelcomeViewModel` | ui | 109 | 2026-03-20 | unknown | _—_ |
| [com/sza/fastmediasorter/util/BinaryFileThumbnailGenerator.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/BinaryFileThumbnailGenerator.kt) | `BinaryFileThumbnailGenerator` | utils | 127 | 2026-02-16 | unknown | _—_ |
| [com/sza/fastmediasorter/util/BinaryFileTypeDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/BinaryFileTypeDetector.kt) | `BinaryFileTypeDetector` | utils | 78 | 2026-03-30 | unknown | _—_ |
| [com/sza/fastmediasorter/util/ConnectionErrorFormatter.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/ConnectionErrorFormatter.kt) | `ConnectionErrorFormatter` | utils | 245 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/util/ExtensionThumbnailGenerator.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/ExtensionThumbnailGenerator.kt) | `ExtensionThumbnailGenerator` | utils | 50 | 2026-02-16 | unknown | _—_ |
| [com/sza/fastmediasorter/util/FragmentExt.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/FragmentExt.kt) | `FragmentExt` | utils | 29 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/util/gif/AnimatedGifEncoder.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/gif/AnimatedGifEncoder.kt) | `AnimatedGifEncoder` | utils | 403 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/util/KeyboardShortcutHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/KeyboardShortcutHandler.kt) | `KeyboardShortcutHandler` | utils | 591 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/util/ThumbnailColorMapper.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/ThumbnailColorMapper.kt) | `ThumbnailColorMapper` | utils | 98 | 2026-03-14 | unknown | _—_ |
| [com/sza/fastmediasorter/util/ToastThrottler.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/ToastThrottler.kt) | `ToastThrottler` | utils | 75 | 2026-03-12 | unknown | _—_ |
| [com/sza/fastmediasorter/util/VirtualPathUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/VirtualPathUtils.kt) | `VirtualPathUtils` | utils | 32 | 2026-03-30 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/CharsetDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/CharsetDetector.kt) | `CharsetDetector` | utils | 162 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/ClickUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/ClickUtils.kt) | `ClickUtils` | utils | 45 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/FileExtensions.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/FileExtensions.kt) | `FileExtensions` | utils | 30 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/FtpPathUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/FtpPathUtils.kt) | `FtpPathUtils` | utils | 127 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/GlideCacheStats.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/GlideCacheStats.kt) | `GlideCacheStats` | utils | 119 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/LifecycleExtensions.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/LifecycleExtensions.kt) | `LifecycleExtensions` | utils | 44 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/MediaStoreNotifier.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/MediaStoreNotifier.kt) | `MediaStoreNotifier` | utils | 74 | 2026-04-02 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/NetworkUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/NetworkUtils.kt) | `NetworkUtils` | utils | 53 | 2026-02-13 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/PdfExportHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/PdfExportHelper.kt) | `PdfExportHelper` | utils | 142 | 2026-04-11 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/PdfHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/PdfHelper.kt) | `PdfHelper` | utils | 63 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/PdfThumbnailHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/PdfThumbnailHelper.kt) | `PdfThumbnailHelper` | utils | 69 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/PermissionChecker.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/PermissionChecker.kt) | `PermissionChecker` | utils | 40 | 2026-02-16 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/SafHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/SafHelper.kt) | `SafHelper` | utils | 224 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/SftpPathUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/SftpPathUtils.kt) | `SftpPathUtils` | utils | 120 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/SmbPathUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/SmbPathUtils.kt) | `SmbPathUtils` | utils | 194 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/SyntaxHighlighter.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/SyntaxHighlighter.kt) | `SyntaxHighlighter` | utils | 231 | 2026-02-15 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/UserActionLogger.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/UserActionLogger.kt) | `UserActionLogger` | utils | 224 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/utils/ViewExtensions.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/ViewExtensions.kt) | `ViewExtensions` | utils | 38 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt) | `DefaultVrLayerFactory` | vr | 120 | 2026-04-27 | unknown | _—_ |
| [com/sza/fastmediasorter/vr/render/VrLayerDescriptor.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/VrLayerDescriptor.kt) | `VrUvRect` | vr | 49 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/vr/render/VrLayerFactory.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/VrLayerFactory.kt) | `VrLayerFactory` | vr | 26 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/vr/render/VrLayerType.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/VrLayerType.kt) | `VrLayerType` | vr | 11 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/vr/render/VrRenderContext.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/VrRenderContext.kt) | `VrEye` | vr | 38 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/vr/render/VrRenderPlanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/VrRenderPlanner.kt) | `VrRenderPlanner` | vr | 114 | 2026-04-19 | unknown | _—_ |
| [com/sza/fastmediasorter/widget/CameraPhotosWidgetProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraPhotosWidgetProvider.kt) | `CameraPhotosWidgetProvider` | widget | 57 | 2026-03-21 | unknown | _—_ |
| [com/sza/fastmediasorter/widget/ContinueReadingWidgetProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/ContinueReadingWidgetProvider.kt) | `ContinueReadingWidgetProvider` | widget | 55 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt) | `FavoritesWidgetProvider` | widget | 85 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/widget/FavoritesWidgetService.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetService.kt) | `FavoritesWidgetService` | widget | 124 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/widget/RandomMusicWidgetProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomMusicWidgetProvider.kt) | `RandomMusicWidgetProvider` | widget | 58 | 2026-03-21 | unknown | _—_ |
| [com/sza/fastmediasorter/widget/ResourceLaunchWidgetConfigActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/ResourceLaunchWidgetConfigActivity.kt) | `ResourceLaunchWidgetConfigActivity` | widget | 218 | 2026-04-25 | unknown | _—_ |
| [com/sza/fastmediasorter/widget/ResourceLaunchWidgetProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/ResourceLaunchWidgetProvider.kt) | `ResourceLaunchWidgetProvider` | widget | 162 | 2026-03-22 | unknown | _—_ |
| [com/sza/fastmediasorter/worker/DuplicateDetectionWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/DuplicateDetectionWorker.kt) | `DuplicateDetectionWorker` | worker | 141 | 2026-04-01 | unknown | _—_ |
| [com/sza/fastmediasorter/worker/NetworkFilesSyncWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/NetworkFilesSyncWorker.kt) | `NetworkFilesSyncWorker` | worker | 180 | 2026-03-28 | unknown | _—_ |
| [com/sza/fastmediasorter/worker/OrphanCleanupWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/OrphanCleanupWorker.kt) | `OrphanCleanupWorker` | worker | 131 | 2026-03-24 | unknown | _—_ |
| [com/sza/fastmediasorter/worker/PendingRevocationWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/PendingRevocationWorker.kt) | `PendingRevocationWorker` | worker | 116 | 2026-02-28 | unknown | _—_ |
| [com/sza/fastmediasorter/worker/ScheduledOperationsBootReceiver.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/ScheduledOperationsBootReceiver.kt) | `ScheduledOperationsBootReceiver` | worker | 37 | 2026-04-13 | unknown | _—_ |
| [com/sza/fastmediasorter/worker/ScheduledOperationsWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/ScheduledOperationsWorker.kt) | `ScheduledOperationsWorker` | worker | 112 | 2026-04-29 | unknown | _—_ |
| [com/sza/fastmediasorter/worker/StreamingCacheStartupGcWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/StreamingCacheStartupGcWorker.kt) | `StreamingCacheStartupGcWorker` | worker | 79 | 2026-04-22 | unknown | _—_ |
| [com/sza/fastmediasorter/worker/ThumbnailExtractorHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/ThumbnailExtractorHelper.kt) | `ThumbnailExtractorHelper` | worker | 139 | 2026-03-28 | unknown | _—_ |
| [com/sza/fastmediasorter/worker/ThumbnailPreloadWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/ThumbnailPreloadWorker.kt) | `ThumbnailPreloadWorker` | worker | 144 | 2026-03-28 | unknown | _—_ |
| [com/sza/fastmediasorter/worker/TrashCleanupWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/TrashCleanupWorker.kt) | `TrashCleanupWorker` | worker | 64 | 2026-02-09 | unknown | _—_ |
| [com/sza/fastmediasorter/worker/WorkManagerScheduler.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/WorkManagerScheduler.kt) | `WorkManagerScheduler` | worker | 432 | 2026-04-29 | unknown | _—_ |

## Details

### `AppShortcutsManager` — [com/sza/fastmediasorter/core/AppShortcutsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/AppShortcutsManager.kt)

**Layer:** core · **LOC:** 54 · **Last:** 2026-03-24 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, ResourceDao  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `updateRecentResourceShortcuts` — _(unfilled)_
- `iconForType` — _(unfilled)_

### `AudioToggleTileService` — [com/sza/fastmediasorter/core/AudioToggleTileService.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/AudioToggleTileService.kt)

**Layer:** core · **LOC:** 134 · **Last:** 2026-03-30 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onStartListening` — _(unfilled)_
- `onStopListening` — _(unfilled)_
- `onClick` — _(unfilled)_
- `connectToSession` — _(unfilled)_
- `onIsPlayingChanged` — _(unfilled)_
- `onPlaybackStateChanged` — _(unfilled)_
- `updateTile` — _(unfilled)_
- `releaseController` — _(unfilled)_
- `onDestroy` — _(unfilled)_

### `MediaFilesCacheManager` — [com/sza/fastmediasorter/core/cache/MediaFilesCacheManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cache/MediaFilesCacheManager.kt)

**Layer:** core · **LOC:** 200 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `sizeOf` — _(unfilled)_
- `entryRemoved` — _(unfilled)_
- `setCachedList` — _(unfilled)_
- `getCachedList` — _(unfilled)_
- `updateFile` — _(unfilled)_
- `removeFile` — _(unfilled)_
- `addFile` — _(unfilled)_
- `clearCache` — _(unfilled)_
- `clearAllCaches` — _(unfilled)_
- `isCached` — _(unfilled)_
- `getCacheSize` — _(unfilled)_
- `fixCloudPaths` — _(unfilled)_

### `TranslationCacheManager` — [com/sza/fastmediasorter/core/cache/TranslationCacheManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cache/TranslationCacheManager.kt)

**Layer:** core · **LOC:** 74 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `getTranslation` — _(unfilled)_
- `getLensTranslation` — _(unfilled)_
- `putTranslation` — _(unfilled)_
- `putLensTranslation` — _(unfilled)_
- `clearAll` — _(unfilled)_
- `getCacheStats` — _(unfilled)_

### `UnifiedFileCache` — [com/sza/fastmediasorter/core/cache/UnifiedFileCache.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cache/UnifiedFileCache.kt)

**Layer:** core · **LOC:** 212 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `getCachedFile` — _(unfilled)_
- `putFile` — _(unfilled)_
- `getCacheFile` — _(unfilled)_
- `isCached` — _(unfilled)_
- `clearAll` — _(unfilled)_
- `evictIfNeeded` — _(unfilled)_
- `getCacheStats` — _(unfilled)_
- `generateCacheKey` — _(unfilled)_

### `CastOptionsProvider` — [com/sza/fastmediasorter/core/cast/CastOptionsProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cast/CastOptionsProvider.kt)

**Layer:** core · **LOC:** 26 · **Last:** 2026-03-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getCastOptions` — _(unfilled)_
- `getAdditionalSessionProviders` — _(unfilled)_

### `LocalCastProxyServer` — [com/sza/fastmediasorter/core/cast/LocalCastProxyServer.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/cast/LocalCastProxyServer.kt)

**Layer:** core · **LOC:** 150 · **Last:** 2026-03-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `serveFile` — _(unfilled)_
- `castUrl` — _(unfilled)_
- `start` — _(unfilled)_
- `stop` — _(unfilled)_
- `getLanIp` — _(unfilled)_
- `getLanIpLegacy` — _(unfilled)_
- `getLanIpApi31` — _(unfilled)_
- `serve` — _(unfilled)_

### `AppConstants` — [com/sza/fastmediasorter/core/constants/AppConstants.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/constants/AppConstants.kt)

**Layer:** core · **LOC:** 26 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** —

**Role:** _(unfilled)_

### `DebugToolsBridge` — [com/sza/fastmediasorter/core/debug/DebugToolsBridge.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/debug/DebugToolsBridge.kt)

**Layer:** core · **LOC:** 41 · **Last:** 2026-02-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `install` — _(unfilled)_
- `onCoroutineException` — _(unfilled)_
- `maybeCreateDebugMenuIntent` — _(unfilled)_
- `invokeStatic` — _(unfilled)_

### `StrictModeHelper` — [com/sza/fastmediasorter/core/debug/StrictModeHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/debug/StrictModeHelper.kt)

**Layer:** core · **LOC:** 101 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `allowDiskReads` — _(unfilled)_
- `allowDiskWrites` — _(unfilled)_
- `allowDiskIO` — _(unfilled)_

### `AppStartupInitializer` — [com/sza/fastmediasorter/core/init/AppStartupInitializer.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt)

**Layer:** core · **LOC:** 360 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `initialize` — _(unfilled)_
- `syncCacheSizeToSharedPreferences` — _(unfilled)_
- `logAllSettings` — _(unfilled)_
- `logPermissionsStatus` — _(unfilled)_
- `granted` — _(unfilled)_
- `fixCloudResourcesWritableFlag` — _(unfilled)_
- `fixLocalResourcesWritableFlag` — _(unfilled)_
- `renameVirtualResourceNames` — _(unfilled)_
- `cleanupPlaybackPositions` — _(unfilled)_
- `migrateThumbnailCache` — _(unfilled)_
- `cleanupOldThumbnails` — _(unfilled)_
- `initializeConnectionThrottleManager` — _(unfilled)_

### `GamepadInputManager` — [com/sza/fastmediasorter/core/input/GamepadInputManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/input/GamepadInputManager.kt)

**Layer:** core · **LOC:** 192 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** KeyBindingManager  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `handleKeyEvent` — _(unfilled)_
- `handleMotionEvent` — _(unfilled)_
- `mapBrowserButton` — _(unfilled)_
- `mapCommandToGamepadAction` — _(unfilled)_
- `rateLimitedTriggerSeek` — _(unfilled)_
- `rateLimitedVolume` — _(unfilled)_
- `rateLimitedAnalogSeek` — _(unfilled)_
- `isFromGamepad` — _(unfilled)_
- `isFromGamepad` — _(unfilled)_

### `KeyBindingManager` — [com/sza/fastmediasorter/core/input/KeyBindingManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/input/KeyBindingManager.kt)

**Layer:** core · **LOC:** 42 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** InputBindingRepository, CoroutineScope  
**Side effects:** —  
**Flags:** coroutines · tests

**Role:** _(unfilled)_

**Functions:**

- `resolve` — _(unfilled)_
- `resolveKeyAction` — _(unfilled)_

### `CorrelationContext` — [com/sza/fastmediasorter/core/logging/CorrelationContext.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/logging/CorrelationContext.kt)

**Layer:** core · **LOC:** 93 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `start` — _(unfilled)_
- `asContextElement` — _(unfilled)_
- `createChildContext` — _(unfilled)_

### `LogExportHelper` — [com/sza/fastmediasorter/core/logging/LogExportHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LogExportHelper.kt)

**Layer:** core · **LOC:** 127 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `exportLogs` — _(unfilled)_
- `writeZipToUri` — _(unfilled)_
- `shareZipFile` — _(unfilled)_

### `LoggingHelper` — [com/sza/fastmediasorter/core/logging/LoggingHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LoggingHelper.kt)

**Layer:** core · **LOC:** 369 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `getLogFiles` — _(unfilled)_
- `installCrashHandler` — _(unfilled)_
- `hasPreviousCrash` — _(unfilled)_
- `logRendererStateTransition` — _(unfilled)_
- `logPrefetch` — _(unfilled)_
- `logRendererFallback` — _(unfilled)_
- `initialize` — _(unfilled)_
- `log` — _(unfilled)_
- `shouldFilterLog` — _(unfilled)_
- `log` — _(unfilled)_
- `log` — _(unfilled)_
- `isUnimportantError` — _(unfilled)_
- `getCompactStackTrace` — _(unfilled)_
- `openNewLogFile` — _(unfilled)_
- `closeCurrentFile` — _(unfilled)_
- `rotateLogFilesIfNeeded` — _(unfilled)_
- `writeCrashSynchronously` — _(unfilled)_
- `hasCrashFiles` — _(unfilled)_
- `getLogDir` — _(unfilled)_
- `getLogFiles` — _(unfilled)_

### `StructuredLogger` — [com/sza/fastmediasorter/core/logging/StructuredLogger.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/logging/StructuredLogger.kt)

**Layer:** core · **LOC:** 83 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `v` — _(unfilled)_
- `d` — _(unfilled)_
- `i` — _(unfilled)_
- `w` — _(unfilled)_
- `w` — _(unfilled)_
- `e` — _(unfilled)_
- `e` — _(unfilled)_
- `log` — _(unfilled)_
- `logWithThrowable` — _(unfilled)_
- `format` — _(unfilled)_

### `KpiAlertChecker` — [com/sza/fastmediasorter/core/metrics/KpiAlertChecker.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/KpiAlertChecker.kt)

**Layer:** core · **LOC:** 202 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `checkNow` — _(unfilled)_
- `resetThresholds` — _(unfilled)_

### `MetricsExporter` — [com/sza/fastmediasorter/core/metrics/MetricsExporter.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/MetricsExporter.kt)

**Layer:** core · **LOC:** 205 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `buildReport` — _(unfilled)_
- `toJsonString` — _(unfilled)_
- `exportToLogcat` — _(unfilled)_

### `OperationMetricsRecorder` — [com/sza/fastmediasorter/core/metrics/OperationMetricsRecorder.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/OperationMetricsRecorder.kt)

**Layer:** core · **LOC:** 132 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `recordConnectionTest` — _(unfilled)_
- `recordResourceSave` — _(unfilled)_
- `snapshot` — _(unfilled)_
- `reset` — _(unfilled)_

### `ScanMetricsRecorder` — [com/sza/fastmediasorter/core/metrics/ScanMetricsRecorder.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/ScanMetricsRecorder.kt)

**Layer:** core · **LOC:** 140 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `beginScan` — _(unfilled)_
- `endScan` — _(unfilled)_
- `summary` — _(unfilled)_
- `reset` — _(unfilled)_

### `NetworkContextAnalyzer` — [com/sza/fastmediasorter/core/network/NetworkContextAnalyzer.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/network/NetworkContextAnalyzer.kt)

**Layer:** core · **LOC:** 103 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `hasAnyNetwork` — _(unfilled)_
- `hasWifi` — _(unfilled)_
- `isCellularNetwork` — _(unfilled)_
- `isPrivateIpAddress` — _(unfilled)_
- `extractHost` — _(unfilled)_

### `NetworkReachabilityGate` — [com/sza/fastmediasorter/core/network/NetworkReachabilityGate.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/network/NetworkReachabilityGate.kt)

**Layer:** core · **LOC:** 47 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** NetworkContextAnalyzer  
**Side effects:** network  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `requireAnyNetwork` — _(unfilled)_
- `requireWifi` — _(unfilled)_

### `NetworkStateMonitor` — [com/sza/fastmediasorter/core/network/NetworkStateMonitor.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/network/NetworkStateMonitor.kt)

**Layer:** core · **LOC:** 221 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onNetworkChanged` — _(unfilled)_
- `onNetworkLost` — _(unfilled)_
- `onAvailable` — _(unfilled)_
- `onLost` — _(unfilled)_
- `onCapabilitiesChanged` — _(unfilled)_
- `onLinkPropertiesChanged` — _(unfilled)_
- `registerCallback` — _(unfilled)_
- `unregisterCallback` — _(unfilled)_
- `handleNetworkChange` — _(unfilled)_
- `getNetworkId` — _(unfilled)_
- `notifyNetworkChanged` — _(unfilled)_
- `notifyNetworkLost` — _(unfilled)_
- `start` — _(unfilled)_
- `stop` — _(unfilled)_

### `SecretMasker` — [com/sza/fastmediasorter/core/security/SecretMasker.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/security/SecretMasker.kt)

**Layer:** core · **LOC:** 61 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `maskFull` — _(unfilled)_
- `mask` — _(unfilled)_
- `maskPath` — _(unfilled)_
- `sanitize` — _(unfilled)_

### `AudioMetadataLoader` — [com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt)

**Layer:** core · **LOC:** 575 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, FileMetadataCacheDao, SmbClient, SftpClient, FtpClient, NetworkCredentialsRepository  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `removeEldestEntry` — _(unfilled)_
- `getCachedMetadata` — _(unfilled)_
- `loadIfNeeded` — _(unfilled)_
- `warmMemoryCacheForResource` — _(unfilled)_
- `recordSuccess` — _(unfilled)_
- `recordFailure` — _(unfilled)_
- `readPartialBytes` — _(unfilled)_
- `readSmbPartial` — _(unfilled)_
- `readSftpPartial` — _(unfilled)_
- `readFtpPartial` — _(unfilled)_
- `extractMetadataFromBytes` — _(unfilled)_
- `extractFromMetadataEntry` — _(unfilled)_
- `resolveSmbCredentials` — _(unfilled)_
- `saveToDatabaseCache` — _(unfilled)_
- `fixCp1251Encoding` — _(unfilled)_
- `applyMetadata` — _(unfilled)_
- `hasAudioMetadata` — _(unfilled)_
- `hasAnyData` — _(unfilled)_

### `CachedMediaMetadataExtractor` — [com/sza/fastmediasorter/core/util/CachedMediaMetadataExtractor.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/CachedMediaMetadataExtractor.kt)

**Layer:** core · **LOC:** 194 · **Last:** 2026-03-03 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `logSessionDiagnostics` — _(unfilled)_
- `enrichBatch` — _(unfilled)_
- `isLocalPath` — _(unfilled)_
- `enrichInternal` — _(unfilled)_
- `enrichAudio` — _(unfilled)_
- `enrichVideo` — _(unfilled)_
- `enrichImage` — _(unfilled)_
- `mapToEntity` — _(unfilled)_
- `parseExifDateTime` — _(unfilled)_

### `CacheStatusHelper` — [com/sza/fastmediasorter/core/util/CacheStatusHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/CacheStatusHelper.kt)

**Layer:** core · **LOC:** 57 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `logGlideDiskCacheStatus` — _(unfilled)_

### `ColorPalette` — [com/sza/fastmediasorter/core/util/ColorPalette.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/ColorPalette.kt)

**Layer:** core · **LOC:** 124 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getDefaultColor` — _(unfilled)_
- `getColorName` — _(unfilled)_

### `DestinationColors` — [com/sza/fastmediasorter/core/util/DestinationColors.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/DestinationColors.kt)

**Layer:** core · **LOC:** 42 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getColorForDestination` — _(unfilled)_
- `getAllColors` — _(unfilled)_

### `DeviceCapabilityProbe` — [com/sza/fastmediasorter/core/util/DeviceCapabilityProbe.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/DeviceCapabilityProbe.kt)

**Layer:** core · **LOC:** 82 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** —  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `currentBudget` — _(unfilled)_

### `DocumentMetadataExtractor` — [com/sza/fastmediasorter/core/util/DocumentMetadataExtractor.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/DocumentMetadataExtractor.kt)

**Layer:** core · **LOC:** 98 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `extractPdfInfo` — _(unfilled)_
- `extractTextInfo` — _(unfilled)_
- `extractEpubInfo` — _(unfilled)_

### `FileOperationErrorFormatter` — [com/sza/fastmediasorter/core/util/FileOperationErrorFormatter.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/FileOperationErrorFormatter.kt)

**Layer:** core · **LOC:** 220 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `formatError` — _(unfilled)_
- `formatMultipleErrors` — _(unfilled)_
- `cleanErrorMessage` — _(unfilled)_
- `detectErrorType` — _(unfilled)_
- `getUserFriendlyReason` — _(unfilled)_
- `shortenPath` — _(unfilled)_

### `FileSize` — [com/sza/fastmediasorter/core/util/FileSize.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/FileSize.kt)

**Layer:** core · **LOC:** 28 · **Last:** 2026-02-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `formatFileSize` — _(unfilled)_

### `GifFrameCounter` — [com/sza/fastmediasorter/core/util/GifFrameCounter.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/GifFrameCounter.kt)

**Layer:** core · **LOC:** 128 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `countFrames` — _(unfilled)_
- `countFramesInternal` — _(unfilled)_

### `HeifSupportUtils` — [com/sza/fastmediasorter/core/util/HeifSupportUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/HeifSupportUtils.kt)

**Layer:** core · **LOC:** 42 · **Last:** 2026-03-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `isHeicSupported` — _(unfilled)_
- `isAvifSupported` — _(unfilled)_
- `isSupported` — _(unfilled)_
- `minimumAndroidVersion` — _(unfilled)_

### `InputStreamExt` — [com/sza/fastmediasorter/core/util/InputStreamExt.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/InputStreamExt.kt)

**Layer:** core · **LOC:** 81 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `copyToWithProgress` — _(unfilled)_

### `LocaleHelper` — [com/sza/fastmediasorter/core/util/LocaleHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/LocaleHelper.kt)

**Layer:** core · **LOC:** 228 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `detectSystemLanguage` — _(unfilled)_
- `getLanguage` — _(unfilled)_
- `saveLanguage` — _(unfilled)_
- `applyLocale` — _(unfilled)_
- `changeLanguage` — _(unfilled)_
- `markReturnToSettings` — _(unfilled)_
- `consumeReturnToSettings` — _(unfilled)_
- `restartApp` — _(unfilled)_
- `getLanguageName` — _(unfilled)_
- `getLanguageIndex` — _(unfilled)_

### `MediaFormatUtils` — [com/sza/fastmediasorter/core/util/MediaFormatUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/MediaFormatUtils.kt)

**Layer:** core · **LOC:** 26 · **Last:** 2026-02-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `formatMediaDuration` — _(unfilled)_

### `DetailedMediaInfo` — [com/sza/fastmediasorter/core/util/MediaMetadataHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/MediaMetadataHelper.kt)

**Layer:** core · **LOC:** 412 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getDetailedInfo` — _(unfilled)_
- `extractImageInfo` — _(unfilled)_
- `extractGifInfo` — _(unfilled)_
- `extractVideoAudioInfo` — _(unfilled)_

### `MemoryTier` — [com/sza/fastmediasorter/core/util/MemoryTier.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/MemoryTier.kt)

**Layer:** core · **LOC:** 75 · **Last:** 2026-03-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `detect` — _(unfilled)_

### `NetworkFileDownloader` — [com/sza/fastmediasorter/core/util/NetworkFileDownloader.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/NetworkFileDownloader.kt)

**Layer:** core · **LOC:** 303 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `downloadToTemp` — _(unfilled)_
- `downloadFromSmb` — _(unfilled)_
- `downloadFromSftp` — _(unfilled)_
- `downloadFromFtp` — _(unfilled)_

### `PathUtils` — [com/sza/fastmediasorter/core/util/PathUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/PathUtils.kt)

**Layer:** core · **LOC:** 73 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `safeParseUri` — _(unfilled)_
- `hasProblematicCharacters` — _(unfilled)_
- `getScheme` — _(unfilled)_
- `isNetworkPath` — _(unfilled)_
- `isContentUri` — _(unfilled)_
- `isLocalPath` — _(unfilled)_

### `PdfInfoParser` — [com/sza/fastmediasorter/core/util/PdfInfoParser.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/PdfInfoParser.kt)

**Layer:** core · **LOC:** 385 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `parse` — _(unfilled)_
- `parse` — _(unfilled)_
- `parseBytes` — _(unfilled)_
- `readVersionOnly` — _(unfilled)_
- `parseHeader` — _(unfilled)_
- `findInfoReference` — _(unfilled)_
- `findObjectDict` — _(unfilled)_
- `indexOfBytes` — _(unfilled)_
- `findMatchingDictEnd` — _(unfilled)_
- `skipLiteralString` — _(unfilled)_
- `parseDict` — _(unfilled)_
- `tokenizeDict` — _(unfilled)_
- `isNameTerminator` — _(unfilled)_
- `readValueEnd` — _(unfilled)_
- `decodeString` — _(unfilled)_
- `decodeLiteral` — _(unfilled)_
- `decodeHex` — _(unfilled)_
- `decodeBytes` — _(unfilled)_
- `formatPdfDate` — _(unfilled)_
- `safeSlice` — _(unfilled)_

### `PermissionHelper` — [com/sza/fastmediasorter/core/util/PermissionHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt)

**Layer:** core · **LOC:** 323 · **Last:** 2026-03-24 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `hasManageMediaPermission` — _(unfilled)_
- `hasAllFilesAccessPermission` — _(unfilled)_
- `hasStoragePermission` — _(unfilled)_
- `hasInternetPermission` — _(unfilled)_
- `requestStoragePermission` — _(unfilled)_
- `requestManageMediaPermission` — _(unfilled)_
- `shouldShowStorageRationale` — _(unfilled)_
- `getStoragePermissionMessage` — _(unfilled)_
- `getInternetPermissionMessage` — _(unfilled)_
- `getManageMediaPermissionMessage` — _(unfilled)_
- `requestAllFilesAccessPermission` — _(unfilled)_
- `getAllFilesAccessPermissionMessage` — _(unfilled)_
- `checkStoragePermissions` — _(unfilled)_
- `getStoragePermissionsArray` — _(unfilled)_
- `routeToStorageSettings` — _(unfilled)_

### `SafUriExtractor` — [com/sza/fastmediasorter/core/util/SafUriExtractor.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/SafUriExtractor.kt)

**Layer:** core · **LOC:** 302 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `extractImageInfo` — _(unfilled)_
- `extractGifInfo` — _(unfilled)_
- `extractVideoAudioInfo` — _(unfilled)_
- `extractPdfInfo` — _(unfilled)_
- `extractTextInfo` — _(unfilled)_
- `extractEpubInfo` — _(unfilled)_

### `UriPathResolver` — [com/sza/fastmediasorter/core/util/UriPathResolver.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/UriPathResolver.kt)

**Layer:** core · **LOC:** 96 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `getPath` — _(unfilled)_
- `resolveContentUri` — _(unfilled)_
- `volumePath` — _(unfilled)_

### `VrPanelSizePreference` — [com/sza/fastmediasorter/core/xr/VrPanelSizePreference.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrPanelSizePreference.kt)

**Layer:** core · **LOC:** 61 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `save` — _(unfilled)_
- `load` — _(unfilled)_
- `hasUserSize` — _(unfilled)_

### `XrDeviceDetector` — [com/sza/fastmediasorter/core/xr/XrDeviceDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrDeviceDetector.kt)

**Layer:** core · **LOC:** 34 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `isXrHeadset` — _(unfilled)_

### `CloudAuthenticationHelper` — [com/sza/fastmediasorter/data/cloud/CloudAuthenticationHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudAuthenticationHelper.kt)

**Layer:** data · **LOC:** 125 · **Last:** 2026-02-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** GoogleDriveRestClient, DropboxClient, OneDriveRestClient, CloudAuthStateMachine  
**Side effects:** —  
**Flags:** coroutines · tests

**Role:** _(unfilled)_

**Functions:**

- `getCloudClientResult` — _(unfilled)_
- `getCloudClient` — _(unfilled)_
- `executeWithAutoReauth` — _(unfilled)_

### `CloudAuthStateMachine` — [com/sza/fastmediasorter/data/cloud/CloudAuthStateMachine.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudAuthStateMachine.kt)

**Layer:** data · **LOC:** 180 · **Last:** 2026-02-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** GoogleDriveRestClient, OneDriveRestClient, DropboxClient  
**Side effects:** —  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `stateOf` — _(unfilled)_
- `authenticateOrRestore` — _(unfilled)_
- `onSignedOut` — _(unfilled)_
- `onAuthError` — _(unfilled)_
- `mutableStateFor` — _(unfilled)_

### `CloudFileOperationHandler` — [com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt)

**Layer:** data · **LOC:** 998 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, GoogleDriveRestClient, DropboxClient, OneDriveRestClient, SmbClient, SftpClient, FtpClient, NetworkCredentialsRepository, CloudPathParser, NetworkCredentialsResolver, CloudAuthenticationHelper  
**Side effects:** network, disk  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `getStrategies` — _(unfilled)_
- `getResourceType` — _(unfilled)_
- `isNetworkPath` — _(unfilled)_
- `normalizeNetworkPath` — _(unfilled)_
- `extractSftpRemotePath` — _(unfilled)_
- `extractFtpRemotePath` — _(unfilled)_
- `executeCopy` — _(unfilled)_
- `executeCopy` — _(unfilled)_
- `executeMove` — _(unfilled)_
- `executeMove` — _(unfilled)_
- `executeRename` — _(unfilled)_
- `executeDelete` — _(unfilled)_
- `downloadFromCloudTo` — _(unfilled)_
- `downloadFromCloud` — _(unfilled)_
- `uploadToCloudFromPath` — _(unfilled)_
- `uploadToCloud` — _(unfilled)_
- `deleteFromCloud` — _(unfilled)_
- `copyCloudToCloud` — _(unfilled)_
- `moveCloudToCloud` — _(unfilled)_
- `checkAuthenticationRequired` — _(unfilled)_
- `getMimeType` — _(unfilled)_

### `CloudFileOperationPathUtils` — [com/sza/fastmediasorter/data/cloud/CloudFileOperationPathUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationPathUtils.kt)

**Layer:** data · **LOC:** 81 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `normalizeNetworkPath` — _(unfilled)_
- `getResourceType` — _(unfilled)_
- `isNetworkPath` — _(unfilled)_
- `extractSftpRemotePath` — _(unfilled)_
- `extractFtpRemotePath` — _(unfilled)_
- `getMimeType` — _(unfilled)_

### `CloudMediaScanner` — [com/sza/fastmediasorter/data/cloud/CloudMediaScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudMediaScanner.kt)

**Layer:** data · **LOC:** 393 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, GoogleDriveRestClient, DropboxClient, OneDriveRestClient, ResourceRepository  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `scanFolder` — _(unfilled)_
- `scanFolderInternal` — _(unfilled)_
- `scanFolderPaged` — _(unfilled)_
- `getFileCount` — _(unfilled)_
- `listDirectoryContents` — _(unfilled)_
- `isWritable` — _(unfilled)_
- `getClient` — _(unfilled)_
- `ensureAuthenticated` — _(unfilled)_
- `listFilesRecursive` — _(unfilled)_

### `CloudPathParser` — [com/sza/fastmediasorter/data/cloud/CloudPathParser.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudPathParser.kt)

**Layer:** data · **LOC:** 111 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `parseCloudPath` — _(unfilled)_
- `normalizePath` — _(unfilled)_
- `isCloudPath` — _(unfilled)_

### `CloudProvider` — [com/sza/fastmediasorter/data/cloud/CloudStorageClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudStorageClient.kt)

**Layer:** data · **LOC:** 284 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `authenticate` — _(unfilled)_
- `initialize` — _(unfilled)_
- `isAuthenticated` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `listFolders` — _(unfilled)_
- `getFileMetadata` — _(unfilled)_
- `downloadFile` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `createFolder` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `renameFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `copyFile` — _(unfilled)_
- `fileExists` — _(unfilled)_
- `searchFiles` — _(unfilled)_
- `getThumbnail` — _(unfilled)_
- `getFileInputStream` — _(unfilled)_
- `signOut` — _(unfilled)_

### `CloudToCloudTransferHelper` — [com/sza/fastmediasorter/data/cloud/CloudToCloudTransferHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudToCloudTransferHelper.kt)

**Layer:** data · **LOC:** 179 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `deleteFromCloud` — _(unfilled)_
- `copyCloudToCloud` — _(unfilled)_
- `moveCloudToCloud` — _(unfilled)_

### `CloudDataSource` — [com/sza/fastmediasorter/data/cloud/datasource/CloudDataSource.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/datasource/CloudDataSource.kt)

**Layer:** data · **LOC:** 197 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `open` — _(unfilled)_
- `read` — _(unfilled)_
- `getUri` — _(unfilled)_
- `close` — _(unfilled)_
- `extractProvider` — _(unfilled)_
- `extractFileId` — _(unfilled)_
- `createDataSource` — _(unfilled)_

### `DropboxAuthPlugin` — [com/sza/fastmediasorter/data/cloud/DropboxAuthPlugin.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/DropboxAuthPlugin.kt)

**Layer:** data · **LOC:** 56 · **Last:** 2026-03-24 · **Status:** unknown · **NoFlavors:** —

**Injected:** DropboxClient  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `startInteractiveSignIn` — _(unfilled)_
- `processIntentResult` — _(unfilled)_
- `handleResume` — _(unfilled)_

### `DropboxClient` — [com/sza/fastmediasorter/data/cloud/DropboxClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/DropboxClient.kt)

**Layer:** data · **LOC:** 984 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `normalizeDropboxPath` — _(unfilled)_
- `saveCredentials` — _(unfilled)_
- `loadStoredCredentials` — _(unfilled)_
- `tryRestoreForAccount` — _(unfilled)_
- `clearStoredCredentials` — _(unfilled)_
- `tryRestoreFromStorage` — _(unfilled)_
- `startPkceAuthentication` — _(unfilled)_
- `authenticate` — _(unfilled)_
- `finishAuthentication` — _(unfilled)_
- `initializeWithCredential` — _(unfilled)_
- `initializeWithAccessToken` — _(unfilled)_
- `serializeAccessToken` — _(unfilled)_
- `serializeCredential` — _(unfilled)_
- `deserializeCredential` — _(unfilled)_
- `registerAccountInDatabase` — _(unfilled)_
- `buildUserFriendlyErrorMessage` — _(unfilled)_
- `logTlsDiagnostics` — _(unfilled)_
- `withRetry` — _(unfilled)_
- `initialize` — _(unfilled)_
- `isAuthenticated` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `getAccountEmail` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `listFolders` — _(unfilled)_
- `getFileMetadata` — _(unfilled)_
- `downloadFile` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `createFolder` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `renameFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `copyFile` — _(unfilled)_
- `searchFiles` — _(unfilled)_
- `getFileInputStream` — _(unfilled)_
- `getThumbnail` — _(unfilled)_
- `signOut` — _(unfilled)_
- `metadataToCloudFile` — _(unfilled)_
- `guessMimeType` — _(unfilled)_
- `fileExists` — _(unfilled)_

### `DropboxClientUtils` — [com/sza/fastmediasorter/data/cloud/DropboxClientUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/DropboxClientUtils.kt)

**Layer:** data · **LOC:** 223 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `buildUserFriendlyErrorMessage` — _(unfilled)_
- `logTlsDiagnostics` — _(unfilled)_
- `serializeAccessToken` — _(unfilled)_
- `serializeCredential` — _(unfilled)_
- `deserializeCredential` — _(unfilled)_
- `metadataToCloudFile` — _(unfilled)_
- `guessMimeType` — _(unfilled)_
- `withRetry` — _(unfilled)_

### `CloudThumbnailData` — [com/sza/fastmediasorter/data/cloud/glide/CloudThumbnailData.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/glide/CloudThumbnailData.kt)

**Layer:** data · **LOC:** 48 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `updateDiskCacheKey` — _(unfilled)_
- `equals` — _(unfilled)_
- `hashCode` — _(unfilled)_

### `CloudThumbnailEntryPoint` — [com/sza/fastmediasorter/data/cloud/glide/CloudThumbnailModelLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/glide/CloudThumbnailModelLoader.kt)

**Layer:** data · **LOC:** 406 · **Last:** 2026-03-08 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `oneDriveClient` — _(unfilled)_
- `dropboxClient` — _(unfilled)_
- `buildLoadData` — _(unfilled)_
- `handles` — _(unfilled)_
- `build` — _(unfilled)_
- `teardown` — _(unfilled)_
- `loadData` — _(unfilled)_
- `loadGoogleDriveImage` — _(unfilled)_
- `loadOneDriveImage` — _(unfilled)_
- `loadDropboxImage` — _(unfilled)_
- `downloadWithAuth` — _(unfilled)_
- `fetchFreshGoogleDriveThumbnailUrl` — _(unfilled)_
- `getGoogleAccessToken` — _(unfilled)_
- `fetchGoogleTokenWithRetry` — _(unfilled)_
- `cleanup` — _(unfilled)_
- `cancel` — _(unfilled)_
- `getDataClass` — _(unfilled)_
- `getDataSource` — _(unfilled)_
- `invalidateGoogleTokenCache` — _(unfilled)_

### `GoogleDriveThumbnailData` — [com/sza/fastmediasorter/data/cloud/glide/GoogleDriveThumbnailData.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/glide/GoogleDriveThumbnailData.kt)

**Layer:** data · **LOC:** 40 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `updateDiskCacheKey` — _(unfilled)_
- `equals` — _(unfilled)_
- `hashCode` — _(unfilled)_

### `GoogleDriveThumbnailModelLoader` — [com/sza/fastmediasorter/data/cloud/glide/GoogleDriveThumbnailModelLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/glide/GoogleDriveThumbnailModelLoader.kt)

**Layer:** data · **LOC:** 249 · **Last:** 2026-03-08 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `buildLoadData` — _(unfilled)_
- `handles` — _(unfilled)_
- `build` — _(unfilled)_
- `teardown` — _(unfilled)_
- `loadData` — _(unfilled)_
- `fetchFreshThumbnailUrl` — _(unfilled)_
- `downloadWithFreshUrl` — _(unfilled)_
- `getAccessToken` — _(unfilled)_
- `cleanup` — _(unfilled)_
- `cancel` — _(unfilled)_
- `getDataClass` — _(unfilled)_
- `getDataSource` — _(unfilled)_

### `GoogleDriveAuthCoordinator` — [com/sza/fastmediasorter/data/cloud/GoogleDriveAuthCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthCoordinator.kt)

**Layer:** data · **LOC:** 315 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `isAuthenticated` — _(unfilled)_
- `captureToken` — _(unfilled)_
- `clearAuth` — _(unfilled)_
- `buildSignInOptions` — _(unfilled)_
- `authenticate` — _(unfilled)_
- `silentSignIn` — _(unfilled)_
- `handleSignInResult` — _(unfilled)_
- `getAccessToken` — _(unfilled)_
- `shouldRefreshToken` — _(unfilled)_
- `ensureTokenFresh` — _(unfilled)_
- `initializeFromStored` — _(unfilled)_
- `makeAuthenticatedRequest` — _(unfilled)_

### `GoogleDriveAuthPlugin` — [com/sza/fastmediasorter/data/cloud/GoogleDriveAuthPlugin.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthPlugin.kt)

**Layer:** data · **LOC:** 164 · **Last:** 2026-03-20 · **Status:** unknown · **NoFlavors:** —

**Injected:** GoogleDriveRestClient  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `startInteractiveSignIn` — _(unfilled)_
- `processIntentResult` — _(unfilled)_
- `mapGoogleSignInError` — _(unfilled)_
- `handleResume` — _(unfilled)_
- `logDebugGoogleSignInEnvironment` — _(unfilled)_
- `computeSigningSha1` — _(unfilled)_

### `GoogleDriveRestClient` — [com/sza/fastmediasorter/data/cloud/GoogleDriveRestClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveRestClient.kt)

**Layer:** data · **LOC:** 1105 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, GoogleDriveCredentialsManager, GoogleDriveHttpClient, PendingRevocationDao, NetworkCredentialsRepository  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `isAuthenticated` — _(unfilled)_
- `getAccountEmail` — _(unfilled)_
- `tryRestoreFromStorage` — _(unfilled)_
- `tryRestoreForAccount` — _(unfilled)_
- `getSignInOptions` — _(unfilled)_
- `getSignInIntent` — _(unfilled)_
- `authenticate` — _(unfilled)_
- `silentSignIn` — _(unfilled)_
- `handleSignInResult` — _(unfilled)_
- `getAccessToken` — _(unfilled)_
- `ensureTokenFresh` — _(unfilled)_
- `initialize` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `listFolders` — _(unfilled)_
- `getFileMetadata` — _(unfilled)_
- `resolveFileIdFromName` — _(unfilled)_
- `parseAndResolveFileId` — _(unfilled)_
- `downloadFile` — _(unfilled)_
- `resolveFolderId` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `createFolder` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `renameFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `copyFile` — _(unfilled)_
- `fileExists` — _(unfilled)_
- `searchFiles` — _(unfilled)_
- `findFolderByName` — _(unfilled)_
- `ensureFolderExists` — _(unfilled)_
- `getThumbnail` — _(unfilled)_
- `downloadFileAsStream` — _(unfilled)_
- `signOut` — _(unfilled)_
- `getFileInputStream` — _(unfilled)_
- `getFileInputStreamInternal` — _(unfilled)_
- `makeAuthenticatedRequest` — _(unfilled)_
- `parseItems` — _(unfilled)_
- `parseItem` — _(unfilled)_

### `GoogleDriveRestClientUtils` — [com/sza/fastmediasorter/data/cloud/GoogleDriveRestClientUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveRestClientUtils.kt)

**Layer:** data · **LOC:** 57 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `parseItems` — _(unfilled)_
- `parseItem` — _(unfilled)_

### `GoogleDriveCredentialsManager` — [com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveCredentialsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveCredentialsManager.kt)

**Layer:** data · **LOC:** 143 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** prefs  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `serializeAccount` — _(unfilled)_
- `deserializeAccount` — _(unfilled)_
- `saveCredentials` — _(unfilled)_
- `loadStoredCredentials` — _(unfilled)_
- `clearStoredCredentials` — _(unfilled)_
- `hasStoredCredentials` — _(unfilled)_

### `GoogleDriveHttpClient` — [com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveHttpClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveHttpClient.kt)

**Layer:** data · **LOC:** 199 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `makeAuthenticatedRequest` — _(unfilled)_
- `getFileInputStream` — _(unfilled)_
- `downloadFileAsStream` — _(unfilled)_

### `InteractiveCloudAuthenticator` — [com/sza/fastmediasorter/data/cloud/InteractiveCloudAuthenticator.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/InteractiveCloudAuthenticator.kt)

**Layer:** data · **LOC:** 50 · **Last:** 2026-03-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `startInteractiveSignIn` — _(unfilled)_
- `processIntentResult` — _(unfilled)_
- `handleResume` — _(unfilled)_
- `consumeImmediateResult` — _(unfilled)_

### `NetworkCredentialsResolver` — [com/sza/fastmediasorter/data/cloud/NetworkCredentialsResolver.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/NetworkCredentialsResolver.kt)

**Layer:** data · **LOC:** 292 · **Last:** 2026-03-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** NetworkCredentialsRepository  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getCredentials` — _(unfilled)_
- `resolveSmb` — _(unfilled)_
- `resolveSftp` — _(unfilled)_
- `resolveFtp` — _(unfilled)_
- `extractSmbRemotePath` — _(unfilled)_
- `toSmbConnectionInfo` — _(unfilled)_
- `toSftpConnectionInfo` — _(unfilled)_
- `resolveBestCredential` — _(unfilled)_
- `pickBestValidCandidate` — _(unfilled)_
- `isCredentialUsable` — _(unfilled)_

### `OneDriveAuthCoordinator` — [com/sza/fastmediasorter/data/cloud/OneDriveAuthCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveAuthCoordinator.kt)

**Layer:** data · **LOC:** 475 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `isAuthenticated` — _(unfilled)_
- `clearAuth` — _(unfilled)_
- `captureToken` — _(unfilled)_
- `signOutLocal` — _(unfilled)_
- `onSignOut` — _(unfilled)_
- `onError` — _(unfilled)_
- `initializeMsal` — _(unfilled)_
- `onCreated` — _(unfilled)_
- `onError` — _(unfilled)_
- `authenticate` — _(unfilled)_
- `signIn` — _(unfilled)_
- `onCreated` — _(unfilled)_
- `onError` — _(unfilled)_
- `signInWithApp` — _(unfilled)_
- `onSignOut` — _(unfilled)_
- `onError` — _(unfilled)_
- `signInInternal` — _(unfilled)_
- `onSuccess` — _(unfilled)_
- `onError` — _(unfilled)_
- `onCancel` — _(unfilled)_
- `acquireTokenSilently` — _(unfilled)_
- `onSuccess` — _(unfilled)_
- `onError` — _(unfilled)_
- `onSuccess` — _(unfilled)_
- `onError` — _(unfilled)_
- `handleAuthenticationResult` — _(unfilled)_
- `initializeFromStored` — _(unfilled)_
- `shouldRefreshToken` — _(unfilled)_
- `ensureTokenFresh` — _(unfilled)_
- `makeAuthenticatedRequest` — _(unfilled)_

### `OneDriveAuthPlugin` — [com/sza/fastmediasorter/data/cloud/OneDriveAuthPlugin.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveAuthPlugin.kt)

**Layer:** data · **LOC:** 83 · **Last:** 2026-03-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** OneDriveRestClient  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `startInteractiveSignIn` — _(unfilled)_
- `consumeImmediateResult` — _(unfilled)_
- `processIntentResult` — _(unfilled)_
- `handleResume` — _(unfilled)_

### `OneDriveRestClient` — [com/sza/fastmediasorter/data/cloud/OneDriveRestClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveRestClient.kt)

**Layer:** data · **LOC:** 901 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, PendingRevocationDao, NetworkCredentialsRepository, CoroutineScope  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `isAuthenticated` — _(unfilled)_
- `initializeMsal` — _(unfilled)_
- `authenticate` — _(unfilled)_
- `signIn` — _(unfilled)_
- `handleAuthenticationResult` — _(unfilled)_
- `serializeAccount` — _(unfilled)_
- `deserializeAccount` — _(unfilled)_
- `ensureTokenFresh` — _(unfilled)_
- `initialize` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `getAccountEmail` — _(unfilled)_
- `resolveOrEnsureFolder` — _(unfilled)_
- `normalizeCloudItemReference` — _(unfilled)_
- `buildItemUrlFromReference` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `listFolders` — _(unfilled)_
- `getFileMetadata` — _(unfilled)_
- `downloadFile` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `createFolder` — _(unfilled)_
- `findFolderByName` — _(unfilled)_
- `ensureFolderExists` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `renameFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `copyFile` — _(unfilled)_
- `fileExists` — _(unfilled)_
- `searchFiles` — _(unfilled)_
- `getThumbnail` — _(unfilled)_
- `getFileInputStream` — _(unfilled)_
- `signOut` — _(unfilled)_
- `makeAuthenticatedRequest` — _(unfilled)_
- `parseItems` — _(unfilled)_
- `parseItem` — _(unfilled)_

### `OneDriveRestClientUtils` — [com/sza/fastmediasorter/data/cloud/OneDriveRestClientUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveRestClientUtils.kt)

**Layer:** data · **LOC:** 101 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `serializeAccount` — _(unfilled)_
- `deserializeAccount` — _(unfilled)_
- `normalizeCloudItemReference` — _(unfilled)_
- `parseItems` — _(unfilled)_
- `parseItem` — _(unfilled)_

### `UnifiedCloudAuthManager` — [com/sza/fastmediasorter/data/cloud/UnifiedCloudAuthManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/UnifiedCloudAuthManager.kt)

**Layer:** data · **LOC:** 160 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** CloudAuthStateMachine, GoogleDriveAuthPlugin, DropboxAuthPlugin, OneDriveAuthPlugin, ApplicationScope, CoroutineScope  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `startInteractiveSignIn` — _(unfilled)_
- `processIntentResult` — _(unfilled)_
- `handleResume` — _(unfilled)_
- `processPluginResult` — _(unfilled)_
- `handleFailedAuth` — _(unfilled)_

### `MediaTypeUtils` — [com/sza/fastmediasorter/data/common/MediaTypeUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/common/MediaTypeUtils.kt)

**Layer:** data · **LOC:** 128 · **Last:** 2026-03-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `getMediaType` — _(unfilled)_
- `getMediaTypeForAllFiles` — _(unfilled)_
- `getMediaTypeFromMime` — _(unfilled)_
- `getMediaTypeFromMimeOrExtension` — _(unfilled)_
- `isFileSizeInRange` — _(unfilled)_
- `buildExtensionsSet` — _(unfilled)_

### `EpubCoverDecoder` — [com/sza/fastmediasorter/data/glide/EpubCoverDecoder.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/glide/EpubCoverDecoder.kt)

**Layer:** data · **LOC:** 91 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `handles` — _(unfilled)_
- `decode` — _(unfilled)_

### `NetworkEpubCoverLoader` — [com/sza/fastmediasorter/data/glide/NetworkEpubCoverLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/glide/NetworkEpubCoverLoader.kt)

**Layer:** data · **LOC:** 376 · **Last:** 2026-02-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `buildLoadData` — _(unfilled)_
- `handles` — _(unfilled)_
- `build` — _(unfilled)_
- `teardown` — _(unfilled)_
- `loadData` — _(unfilled)_
- `downloadEpubToFile` — _(unfilled)_
- `downloadFromSmb` — _(unfilled)_
- `downloadFromSftp` — _(unfilled)_
- `downloadFromFtp` — _(unfilled)_
- `extractCoverImage` — _(unfilled)_
- `cleanup` — _(unfilled)_
- `cancel` — _(unfilled)_
- `getDataClass` — _(unfilled)_
- `getDataSource` — _(unfilled)_

### `NetworkPdfThumbnailLoader` — [com/sza/fastmediasorter/data/glide/NetworkPdfThumbnailLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/glide/NetworkPdfThumbnailLoader.kt)

**Layer:** data · **LOC:** 524 · **Last:** 2026-02-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `buildLoadData` — _(unfilled)_
- `handles` — _(unfilled)_
- `build` — _(unfilled)_
- `teardown` — _(unfilled)_
- `loadData` — _(unfilled)_
- `downloadPdfToFile` — _(unfilled)_
- `downloadFromSmb` — _(unfilled)_
- `downloadFromSftp` — _(unfilled)_
- `downloadFromFtp` — _(unfilled)_
- `renderPdfPage` — _(unfilled)_
- `drawPageCountBadge` — _(unfilled)_
- `cleanup` — _(unfilled)_
- `cancel` — _(unfilled)_
- `getDataClass` — _(unfilled)_
- `getDataSource` — _(unfilled)_

### `PdfPageDecoder` — [com/sza/fastmediasorter/data/glide/PdfPageDecoder.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/glide/PdfPageDecoder.kt)

**Layer:** data · **LOC:** 96 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `handles` — _(unfilled)_
- `decode` — _(unfilled)_

### `CloudFileHasher` — [com/sza/fastmediasorter/data/hash/CloudFileHasher.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/hash/CloudFileHasher.kt)

**Layer:** data · **LOC:** 54 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** GoogleDriveRestClient, DropboxClient, OneDriveRestClient  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `computeHash` — _(unfilled)_
- `getClient` — _(unfilled)_

### `FtpFileHasher` — [com/sza/fastmediasorter/data/hash/FtpFileHasher.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/hash/FtpFileHasher.kt)

**Layer:** data · **LOC:** 46 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** FtpClient, NetworkCredentialsRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `computeHash` — _(unfilled)_

### `LocalFileHasher` — [com/sza/fastmediasorter/data/hash/LocalFileHasher.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/hash/LocalFileHasher.kt)

**Layer:** data · **LOC:** 54 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** disk  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `computeHash` — _(unfilled)_
- `md5Hex` — _(unfilled)_

### `SftpFileHasher` — [com/sza/fastmediasorter/data/hash/SftpFileHasher.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/hash/SftpFileHasher.kt)

**Layer:** data · **LOC:** 48 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** SftpClient, NetworkCredentialsRepository  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `computeHash` — _(unfilled)_

### `SmbFileHasher` — [com/sza/fastmediasorter/data/hash/SmbFileHasher.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/hash/SmbFileHasher.kt)

**Layer:** data · **LOC:** 47 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** SmbFileOperations, NetworkCredentialsRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `computeHash` — _(unfilled)_

### `DefaultsMapLoader` — [com/sza/fastmediasorter/data/input/DefaultsMapLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/input/DefaultsMapLoader.kt)

**Layer:** data · **LOC:** 52 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `loadDefaults` — _(unfilled)_

### `InputBindingDao` — [com/sza/fastmediasorter/data/input/InputBindingDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingDao.kt)

**Layer:** data · **LOC:** 31 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `observeAll` — _(unfilled)_
- `upsert` — _(unfilled)_
- `deleteByCommand` — _(unfilled)_
- `deleteByCommandAndDevice` — _(unfilled)_
- `deleteByCommandPrefix` — _(unfilled)_
- `deleteAll` — _(unfilled)_

### `InputBindingEntity` — [com/sza/fastmediasorter/data/input/InputBindingEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingEntity.kt)

**Layer:** data · **LOC:** 14 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** —

**Role:** _(unfilled)_

### `InputBindingRepository` — [com/sza/fastmediasorter/data/input/InputBindingRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingRepository.kt)

**Layer:** data · **LOC:** 102 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** InputBindingDao, DefaultsMapLoader  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `observeResolvedBindings` — _(unfilled)_
- `setOverride` — _(unfilled)_
- `clearOverride` — _(unfilled)_
- `clearAllOverrides` — _(unfilled)_
- `clearAllOverridesForGroup` — _(unfilled)_
- `clearAll` — _(unfilled)_
- `merge` — _(unfilled)_
- `deviceOf` — _(unfilled)_

### `CandidateSelectionPolicy` — [com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt)

**Layer:** data · **LOC:** 44 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `choose` — _(unfilled)_
- `isHttpScheme` — _(unfilled)_

### `DirectFileExtractionStrategy` — [com/sza/fastmediasorter/data/link/DirectFileExtractionStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/link/DirectFileExtractionStrategy.kt)

**Layer:** data · **LOC:** 154 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `probe` — _(unfilled)_
- `open` — _(unfilled)_
- `deriveFileName` — _(unfilled)_
- `extractDispositionFilename` — _(unfilled)_
- `sanitise` — _(unfilled)_
- `pathHasMediaExtension` — _(unfilled)_

### `HtmlMediaCandidate` — [com/sza/fastmediasorter/data/link/HtmlMediaCandidate.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlMediaCandidate.kt)

**Layer:** data · **LOC:** 28 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `HtmlPageExtractionStrategy` — [com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt)

**Layer:** data · **LOC:** 177 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `probe` — _(unfilled)_
- `open` — _(unfilled)_
- `probeCandidates` — _(unfilled)_
- `harvestCandidates` — _(unfilled)_
- `add` — _(unfilled)_

### `LinkDownloadWriter` — [com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt)

**Layer:** data · **LOC:** 186 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, FileOperationUseCase, GetDestinationsUseCase  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `writeFromStream` — _(unfilled)_
- `saveToDownloads` — _(unfilled)_
- `uniqueFile` — _(unfilled)_
- `sanitiseFileName` — _(unfilled)_

### `AppDatabase` — [com/sza/fastmediasorter/data/local/db/AppDatabase.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt)

**Layer:** data · **LOC:** 719 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `resourceDao` — _(unfilled)_
- `networkCredentialsDao` — _(unfilled)_
- `pendingRevocationDao` — _(unfilled)_
- `favoritesDao` — _(unfilled)_
- `playbackPositionDao` — _(unfilled)_
- `thumbnailCacheDao` — _(unfilled)_
- `cachedFileListDao` — _(unfilled)_
- `fileMetadataCacheDao` — _(unfilled)_
- `stereoFormatOverrideDao` — _(unfilled)_
- `scheduledOperationDao` — _(unfilled)_
- `duplicateHashCacheDao` — _(unfilled)_
- `streamingCacheDao` — _(unfilled)_
- `inputBindingDao` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrateSchemaToV18` — _(unfilled)_
- `recreateResourcesTableWithNonNullShowSubfolders` — _(unfilled)_
- `createFinalCachedFileListsTable` — _(unfilled)_
- `createBaseFileMetadataCacheTable` — _(unfilled)_
- `createFinalFileMetadataCacheTable` — _(unfilled)_
- `createScheduledOperationsTable` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `migrate` — _(unfilled)_
- `createFinalPendingRevocationsTable` — _(unfilled)_
- `hasTable` — _(unfilled)_
- `hasIndex` — _(unfilled)_
- `ensureIndex` — _(unfilled)_
- `hasColumn` — _(unfilled)_
- `isColumnNullable` — _(unfilled)_

### `CachedFileListDao` — [com/sza/fastmediasorter/data/local/db/CachedFileListDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/CachedFileListDao.kt)

**Layer:** data · **LOC:** 36 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getByResourceId` — _(unfilled)_
- `insertOrReplace` — _(unfilled)_
- `deleteByResourceId` — _(unfilled)_
- `deleteAll` — _(unfilled)_
- `getFileCount` — _(unfilled)_
- `deleteOrphaned` — _(unfilled)_

### `CachedFileListEntity` — [com/sza/fastmediasorter/data/local/db/CachedFileListEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/CachedFileListEntity.kt)

**Layer:** data · **LOC:** 77 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `equals` — _(unfilled)_
- `hashCode` — _(unfilled)_

### `Converters` — [com/sza/fastmediasorter/data/local/db/Converters.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Converters.kt)

**Layer:** data · **LOC:** 35 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `fromResourceType` — _(unfilled)_
- `toResourceType` — _(unfilled)_
- `fromSortMode` — _(unfilled)_
- `toSortMode` — _(unfilled)_
- `fromDisplayMode` — _(unfilled)_
- `toDisplayMode` — _(unfilled)_
- `fromCloudProvider` — _(unfilled)_
- `toCloudProvider` — _(unfilled)_

### `CryptoHelper` — [com/sza/fastmediasorter/data/local/db/CryptoHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/CryptoHelper.kt)

**Layer:** data · **LOC:** 118 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `encrypt` — _(unfilled)_
- `decrypt` — _(unfilled)_
- `getOrCreateKey` — _(unfilled)_

### `DuplicateHashCacheDao` — [com/sza/fastmediasorter/data/local/db/DuplicateHashCacheDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/DuplicateHashCacheDao.kt)

**Layer:** data · **LOC:** 36 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getByKey` — _(unfilled)_
- `upsert` — _(unfilled)_
- `deleteByResourceId` — _(unfilled)_
- `deleteByResourceAndPath` — _(unfilled)_

### `DuplicateHashCacheEntity` — [com/sza/fastmediasorter/data/local/db/DuplicateHashCacheEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/DuplicateHashCacheEntity.kt)

**Layer:** data · **LOC:** 25 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** —

**Role:** _(unfilled)_

### `EncryptedString` — [com/sza/fastmediasorter/data/local/db/EncryptedStringConverter.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/EncryptedStringConverter.kt)

**Layer:** data · **LOC:** 9 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `FavoritesDao` — [com/sza/fastmediasorter/data/local/db/FavoritesDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FavoritesDao.kt)

**Layer:** data · **LOC:** 38 · **Last:** 2026-03-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `insert` — _(unfilled)_
- `deleteByUri` — _(unfilled)_
- `getAllFavorites` — _(unfilled)_
- `isFavorite` — _(unfilled)_
- `isFavoriteSync` — _(unfilled)_
- `getFavoriteUrisForPaths` — _(unfilled)_
- `deleteById` — _(unfilled)_
- `getAllFavoritesSync` — _(unfilled)_
- `getFavoritesCount` — _(unfilled)_

### `FavoritesEntity` — [com/sza/fastmediasorter/data/local/db/FavoritesEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FavoritesEntity.kt)

**Layer:** data · **LOC:** 28 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** —

**Role:** _(unfilled)_

### `FileMetadataCacheDao` — [com/sza/fastmediasorter/data/local/db/FileMetadataCacheDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FileMetadataCacheDao.kt)

**Layer:** data · **LOC:** 100 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getEntry` — _(unfilled)_
- `getChecksumSnapshot` — _(unfilled)_
- `countForResource` — _(unfilled)_
- `getAllForResource` — _(unfilled)_
- `upsert` — _(unfilled)_
- `upsertAll` — _(unfilled)_
- `deleteEntry` — _(unfilled)_
- `deleteEntries` — _(unfilled)_
- `deleteAllForResource` — _(unfilled)_
- `deleteExpired` — _(unfilled)_
- `deleteOrphaned` — _(unfilled)_
- `deleteByCredentials` — _(unfilled)_

### `FileMetadataCacheEntity` — [com/sza/fastmediasorter/data/local/db/FileMetadataCacheEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FileMetadataCacheEntity.kt)

**Layer:** data · **LOC:** 94 · **Last:** 2026-03-03 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** —

**Role:** _(unfilled)_

### `NetworkCredentialsDao` — [com/sza/fastmediasorter/data/local/db/NetworkCredentialsDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/NetworkCredentialsDao.kt)

**Layer:** data · **LOC:** 64 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getById` — _(unfilled)_
- `getCredentialsById` — _(unfilled)_
- `getByServerAndShare` — _(unfilled)_
- `getByTypeServerAndPort` — _(unfilled)_
- `getCredentialsByHost` — _(unfilled)_
- `getByTypeAndAccountId` — _(unfilled)_
- `getCredentialsByType` — _(unfilled)_
- `getAllCredentials` — _(unfilled)_
- `insert` — _(unfilled)_
- `update` — _(unfilled)_
- `deleteByCredentialId` — _(unfilled)_
- `deleteAll` — _(unfilled)_
- `getOrphanedCredentials` — _(unfilled)_

### `NetworkCredentialsEntity` — [com/sza/fastmediasorter/data/local/db/NetworkCredentialsEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/NetworkCredentialsEntity.kt)

**Layer:** data · **LOC:** 132 · **Last:** 2026-03-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `create` — _(unfilled)_

### `PendingRevocationDao` — [com/sza/fastmediasorter/data/local/db/PendingRevocationDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/PendingRevocationDao.kt)

**Layer:** data · **LOC:** 30 · **Last:** 2026-02-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `insert` — _(unfilled)_
- `getAll` — _(unfilled)_
- `deleteById` — _(unfilled)_
- `incrementAttemptCount` — _(unfilled)_
- `deleteExhausted` — _(unfilled)_

### `PendingRevocationEntity` — [com/sza/fastmediasorter/data/local/db/PendingRevocationEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/PendingRevocationEntity.kt)

**Layer:** data · **LOC:** 28 · **Last:** 2026-02-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** —

**Role:** _(unfilled)_

### `PlaybackPositionDao` — [com/sza/fastmediasorter/data/local/db/PlaybackPositionDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/PlaybackPositionDao.kt)

**Layer:** data · **LOC:** 52 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getPosition` — _(unfilled)_
- `savePosition` — _(unfilled)_
- `deletePosition` — _(unfilled)_
- `getPositionsCount` — _(unfilled)_
- `keepOnlyRecentPositions` — _(unfilled)_
- `deleteAllPositions` — _(unfilled)_
- `getAllPositions` — _(unfilled)_

### `PlaybackPositionEntity` — [com/sza/fastmediasorter/data/local/db/PlaybackPositionEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/PlaybackPositionEntity.kt)

**Layer:** data · **LOC:** 19 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db, disk  
**Flags:** —

**Role:** _(unfilled)_

### `ResourceDao` — [com/sza/fastmediasorter/data/local/db/ResourceDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceDao.kt)

**Layer:** data · **LOC:** 147 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `insertResource` — _(unfilled)_
- `updateResource` — _(unfilled)_
- `deleteResource` — _(unfilled)_
- `deleteById` — _(unfilled)_
- `deleteAllResources` — _(unfilled)_
- `insertFts` — _(unfilled)_
- `updateFts` — _(unfilled)_
- `deleteFts` — _(unfilled)_
- `deleteAllFts` — _(unfilled)_
- `insert` — _(unfilled)_
- `update` — _(unfilled)_
- `delete` — _(unfilled)_
- `deleteByIdWithFts` — _(unfilled)_
- `deleteAll` — _(unfilled)_
- `getResourceById` — _(unfilled)_
- `getResourceByIdSync` — _(unfilled)_
- `getResourcesByType` — _(unfilled)_
- `getAllResources` — _(unfilled)_
- `getAllResourcesSync` — _(unfilled)_
- `getRecentResourcesSync` — _(unfilled)_
- `getDestinations` — _(unfilled)_
- `getResourcesRaw` — _(unfilled)_
- `searchResourcesFts` — _(unfilled)_
- `swapDisplayOrders` — _(unfilled)_
- `updateDisplayOrder` — _(unfilled)_
- `updateIcon` — _(unfilled)_
- `updateAllDisplayOrders` — _(unfilled)_

### `ResourceEntity` — [com/sza/fastmediasorter/data/local/db/ResourceEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt)

**Layer:** data · **LOC:** 98 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db, disk  
**Flags:** —

**Role:** _(unfilled)_

### `ResourceFtsEntity` — [com/sza/fastmediasorter/data/local/db/ResourceFtsEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceFtsEntity.kt)

**Layer:** data · **LOC:** 12 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** —

**Role:** _(unfilled)_

### `ScheduledOperationDao` — [com/sza/fastmediasorter/data/local/db/ScheduledOperationDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ScheduledOperationDao.kt)

**Layer:** data · **LOC:** 37 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getAll` — _(unfilled)_
- `getAllEnabled` — _(unfilled)_
- `getById` — _(unfilled)_
- `upsert` — _(unfilled)_
- `update` — _(unfilled)_
- `deleteById` — _(unfilled)_
- `deleteAll` — _(unfilled)_
- `getCount` — _(unfilled)_

### `ScheduledOperationEntity` — [com/sza/fastmediasorter/data/local/db/ScheduledOperationEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ScheduledOperationEntity.kt)

**Layer:** data · **LOC:** 84 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** —

**Role:** _(unfilled)_

### `StereoFormatOverrideDao` — [com/sza/fastmediasorter/data/local/db/StereoFormatOverrideDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StereoFormatOverrideDao.kt)

**Layer:** data · **LOC:** 18 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getEntry` — _(unfilled)_
- `upsert` — _(unfilled)_
- `deleteEntry` — _(unfilled)_

### `StereoFormatOverrideEntity` — [com/sza/fastmediasorter/data/local/db/StereoFormatOverrideEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StereoFormatOverrideEntity.kt)

**Layer:** data · **LOC:** 25 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** —

**Role:** _(unfilled)_

### `StreamingCacheDao` — [com/sza/fastmediasorter/data/local/db/StreamingCacheDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamingCacheDao.kt)

**Layer:** data · **LOC:** 45 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `findByHash` — _(unfilled)_
- `getAll` — _(unfilled)_
- `observeAll` — _(unfilled)_
- `findOlderThan` — _(unfilled)_
- `upsert` — _(unfilled)_
- `touchLastPlayedAt` — _(unfilled)_
- `deleteByHash` — _(unfilled)_
- `deleteAll` — _(unfilled)_
- `totalSizeBytes` — _(unfilled)_

### `StreamingCacheEntry` — [com/sza/fastmediasorter/data/local/db/StreamingCacheEntry.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamingCacheEntry.kt)

**Layer:** data · **LOC:** 50 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** —

**Role:** _(unfilled)_

### `ThumbnailCacheDao` — [com/sza/fastmediasorter/data/local/db/ThumbnailCacheDao.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ThumbnailCacheDao.kt)

**Layer:** data · **LOC:** 88 · **Last:** 2026-02-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `updateAccessTime` — _(unfilled)_
- `getThumbnail` — _(unfilled)_
- `insertThumbnail` — _(unfilled)_
- `deleteThumbnail` — _(unfilled)_
- `getThumbnailsOlderThan` — _(unfilled)_
- `deleteOldThumbnails` — _(unfilled)_
- `getTotalCacheSize` — _(unfilled)_
- `getCacheCount` — _(unfilled)_
- `getAllThumbnails` — _(unfilled)_
- `updateThumbnailPath` — _(unfilled)_
- `getAllByLruOrder` — _(unfilled)_
- `deleteByPaths` — _(unfilled)_

### `ThumbnailCacheEntity` — [com/sza/fastmediasorter/data/local/db/ThumbnailCacheEntity.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ThumbnailCacheEntity.kt)

**Layer:** data · **LOC:** 49 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db, disk  
**Flags:** —

**Role:** _(unfilled)_

### `LocalMediaScanner` — [com/sza/fastmediasorter/data/local/LocalMediaScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/LocalMediaScanner.kt)

**Layer:** data · **LOC:** 786 · **Last:** 2026-03-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, MediaStoreRepository  
**Side effects:** disk  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `scanFolder` — _(unfilled)_
- `scanRecentFiles` — _(unfilled)_
- `scanAllByTypes` — _(unfilled)_
- `countAllByTypes` — _(unfilled)_
- `imageTypesFromSettings` — _(unfilled)_
- `docTypesFromSettings` — _(unfilled)_
- `scanFolderLegacy` — _(unfilled)_
- `scanFolderPaged` — _(unfilled)_
- `getFileCount` — _(unfilled)_
- `isWritable` — _(unfilled)_
- `listDirectoryContents` — _(unfilled)_
- `listDirectoryContentsSAF` — _(unfilled)_
- `scanFolderSAFFast` — _(unfilled)_
- `scanFolderSAF` — _(unfilled)_
- `getFileCountSAF` — _(unfilled)_
- `isWritableSAF` — _(unfilled)_
- `collectDocumentFilesRecursivelyParallel` — _(unfilled)_
- `collectFilesRecursively` — _(unfilled)_

### `BrowseManualOrderPrefs` — [com/sza/fastmediasorter/data/local/preferences/BrowseManualOrderPrefs.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/BrowseManualOrderPrefs.kt)

**Layer:** data · **LOC:** 52 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** prefs  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `saveOrder` — _(unfilled)_
- `loadOrder` — _(unfilled)_
- `clearOrder` — _(unfilled)_
- `buildKey` — _(unfilled)_

### `BrowseStateDataStore` — [com/sza/fastmediasorter/data/local/preferences/BrowseStateDataStore.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/BrowseStateDataStore.kt)

**Layer:** data · **LOC:** 115 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** DataStore  
**Side effects:** prefs  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `saveFilter` — _(unfilled)_
- `clearFilter` — _(unfilled)_

### `AppSettings` — [com/sza/fastmediasorter/data/local/preferences/SettingsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/SettingsManager.kt)

**Layer:** data · **LOC:** 291 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** DataStore  
**Side effects:** prefs  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `setConfirmDeletion` — _(unfilled)_
- `setEnableUndo` — _(unfilled)_
- `setOverwriteFiles` — _(unfilled)_
- `setShowRenameButton` — _(unfilled)_
- `setDefaultViewMode` — _(unfilled)_
- `setSlideshowInterval` — _(unfilled)_
- `setTheme` — _(unfilled)_
- `setEnableImages` — _(unfilled)_
- `setEnableVideos` — _(unfilled)_
- `setEnableAudio` — _(unfilled)_
- `setEnableGifs` — _(unfilled)_
- `setLanguage` — _(unfilled)_
- `setShowPlayerHintOnFirstRun` — _(unfilled)_
- `setCopyPanelCollapsed` — _(unfilled)_
- `setMovePanelCollapsed` — _(unfilled)_
- `setShowDetailedErrors` — _(unfilled)_
- `setEnableFavorites` — _(unfilled)_
- `setMaxRecipients` — _(unfilled)_
- `setSupportText` — _(unfilled)_
- `setSupportPdf` — _(unfilled)_
- `setTextSizeMax` — _(unfilled)_
- `setEnableTranslation` — _(unfilled)_
- `setTranslationSourceLanguage` — _(unfilled)_
- `setTranslationTargetLanguage` — _(unfilled)_
- `setTranslationLensStyle` — _(unfilled)_
- `setEnableSlideshowBackgroundMusic` — _(unfilled)_
- `setSlideshowMusicResourceId` — _(unfilled)_
- `setUseTrash` — _(unfilled)_
- `setCropImagesToFullscreen` — _(unfilled)_

### `TrashMetadata` — [com/sza/fastmediasorter/data/model/TrashMetadata.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/model/TrashMetadata.kt)

**Layer:** data · **LOC:** 60 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `toJson` — _(unfilled)_
- `fromJson` — _(unfilled)_

### `BaseConnectionPool` — [com/sza/fastmediasorter/data/network/BaseConnectionPool.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/BaseConnectionPool.kt)

**Layer:** data · **LOC:** 273 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `withConnection` — _(unfilled)_
- `isConnectionValid` — _(unfilled)_
- `removeConnection` — _(unfilled)_
- `closeAllConnections` — _(unfilled)_
- `cleanupIdleConnections` — _(unfilled)_
- `clearConnectionPool` — _(unfilled)_
- `forceFullReset` — _(unfilled)_
- `createConnection` — _(unfilled)_
- `isConnectionAlive` — _(unfilled)_
- `closeConnection` — _(unfilled)_
- `isCriticalError` — _(unfilled)_
- `resetClients` — _(unfilled)_

### `ConnectionThrottleManager` — [com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt)

**Layer:** data · **LOC:** 536 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `setUserNetworkLimit` — _(unfilled)_
- `getUserNetworkLimit` — _(unfilled)_
- `setRecommendedThreads` — _(unfilled)_
- `getRecommendedThreads` — _(unfilled)_
- `setRecommendedBufferSize` — _(unfilled)_
- `getRecommendedBufferSize` — _(unfilled)_
- `setLastSpeedMbps` — _(unfilled)_
- `getLastSpeedMbps` — _(unfilled)_
- `getSmbjClientTier` — _(unfilled)_
- `activateVideoPlayerMode` — _(unfilled)_
- `deactivateVideoPlayerMode` — _(unfilled)_
- `isVideoPlayerActive` — _(unfilled)_
- `isCongested` — _(unfilled)_
- `isAnyCongested` — _(unfilled)_
- `getMaxLimit` — _(unfilled)_
- `getMinLimit` — _(unfilled)_
- `getState` — _(unfilled)_
- `isDegraded` — _(unfilled)_
- `getSemaphoreAndLock` — _(unfilled)_
- `withThrottle` — _(unfilled)_
- `getCurrentLimit` — _(unfilled)_
- `getActiveTaskCount` — _(unfilled)_
- `forceResetConnections` — _(unfilled)_
- `resetAllSmbStates` — _(unfilled)_
- `cancelAllForResource` — _(unfilled)_
- `resetState` — _(unfilled)_

### `FtpDataSource` — [com/sza/fastmediasorter/data/network/datasource/FtpDataSource.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/FtpDataSource.kt)

**Layer:** data · **LOC:** 249 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `open` — _(unfilled)_
- `read` — _(unfilled)_
- `getUri` — _(unfilled)_
- `close` — _(unfilled)_
- `createDataSource` — _(unfilled)_

### `SftpDataSource` — [com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt)

**Layer:** data · **LOC:** 221 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `open` — _(unfilled)_
- `read` — _(unfilled)_
- `getUri` — _(unfilled)_
- `close` — _(unfilled)_
- `createDataSource` — _(unfilled)_

### `SmbDataSource` — [com/sza/fastmediasorter/data/network/datasource/SmbDataSource.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SmbDataSource.kt)

**Layer:** data · **LOC:** 608 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `isInterruptionOrTimeout` — _(unfilled)_
- `open` — _(unfilled)_
- `openInternal` — _(unfilled)_
- `read` — _(unfilled)_
- `readInternal` — _(unfilled)_
- `logProgress` — _(unfilled)_
- `reopenConnection` — _(unfilled)_
- `connectionKey` — _(unfilled)_
- `resolveSmbPath` — _(unfilled)_
- `closeQuietly` — _(unfilled)_
- `getUri` — _(unfilled)_
- `close` — _(unfilled)_
- `formatRemaining` — _(unfilled)_
- `createDataSource` — _(unfilled)_

### `NetworkErrorClassifier` — [com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt)

**Layer:** data · **LOC:** 174 · **Last:** 2026-04-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `classify` — _(unfilled)_
- `isTransient` — _(unfilled)_
- `messageContains` — _(unfilled)_
- `isSmbAccessDenied` — _(unfilled)_
- `isSmbNotFound` — _(unfilled)_
- `extractSmbStatus` — _(unfilled)_
- `extractHttpStatus` — _(unfilled)_
- `extractRetryAfter` — _(unfilled)_

### `NetworkErrorMessageMapper` — [com/sza/fastmediasorter/data/network/exceptions/NetworkErrorMessageMapper.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorMessageMapper.kt)

**Layer:** data · **LOC:** 72 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `toMessageRes` — _(unfilled)_
- `toMessageRes` — _(unfilled)_
- `toContextAwareMessage` — _(unfilled)_

### `NetworkException` — [com/sza/fastmediasorter/data/network/exceptions/NetworkExceptions.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkExceptions.kt)

**Layer:** data · **LOC:** 54 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `RetryPolicy` — [com/sza/fastmediasorter/data/network/exceptions/RetryPolicy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/RetryPolicy.kt)

**Layer:** data · **LOC:** 90 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `withRetry` — _(unfilled)_

### `FtpFileOperationHandler` — [com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt)

**Layer:** data · **LOC:** 939 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, FtpClient, SmbClient, SftpClient, NetworkCredentialsRepository  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getStrategies` — _(unfilled)_
- `executeMove` — _(unfilled)_
- `executeRename` — _(unfilled)_
- `copyFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `createTrashFolder` — _(unfilled)_
- `moveToTrash` — _(unfilled)_
- `downloadFromFtp` — _(unfilled)_
- `uploadToFtp` — _(unfilled)_
- `deleteFromFtp` — _(unfilled)_
- `copyFtpToFtp` — _(unfilled)_
- `prepareFtpDestinationForOverwrite` — _(unfilled)_
- `parseFtpPath` — _(unfilled)_
- `normalizeNetworkPath` — _(unfilled)_
- `existsAtDestination` — _(unfilled)_
- `copyFtpToSftp` — _(unfilled)_
- `copyFtpToSmb` — _(unfilled)_
- `parseSftpDestination` — _(unfilled)_
- `parseSmbDestination` — _(unfilled)_

### `ErrorPropagatingPipedInputStream` — [com/sza/fastmediasorter/data/network/glide/ErrorPropagatingPipedInputStream.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/ErrorPropagatingPipedInputStream.kt)

**Layer:** data · **LOC:** 37 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `setError` — _(unfilled)_
- `read` — _(unfilled)_
- `read` — _(unfilled)_
- `checkError` — _(unfilled)_

### `NetworkFileData` — [com/sza/fastmediasorter/data/network/glide/NetworkFileData.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileData.kt)

**Layer:** data · **LOC:** 57 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getCacheKey` — _(unfilled)_
- `updateDiskCacheKey` — _(unfilled)_
- `equals` — _(unfilled)_
- `hashCode` — _(unfilled)_

### `NetworkFileDataPassthroughModelLoader` — [com/sza/fastmediasorter/data/network/glide/NetworkFileDataPassthroughModelLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileDataPassthroughModelLoader.kt)

**Layer:** data · **LOC:** 77 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `buildLoadData` — _(unfilled)_
- `handles` — _(unfilled)_
- `build` — _(unfilled)_
- `teardown` — _(unfilled)_
- `loadData` — _(unfilled)_
- `cleanup` — _(unfilled)_
- `cancel` — _(unfilled)_
- `getDataClass` — _(unfilled)_
- `getDataSource` — _(unfilled)_

### `NetworkFileModelLoader` — [com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt)

**Layer:** data · **LOC:** 759 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `buildLoadData` — _(unfilled)_
- `handles` — _(unfilled)_
- `removeEldestEntry` — _(unfilled)_
- `ensurePersistenceLoaded` — _(unfilled)_
- `isVideoFailed` — _(unfilled)_
- `markVideoAsFailed` — _(unfilled)_
- `clearFailedVideoCache` — _(unfilled)_
- `isThumbnailFailed` — _(unfilled)_
- `markThumbnailAsFailed` — _(unfilled)_
- `loadData` — _(unfilled)_
- `fetchBytesFromSmb` — _(unfilled)_
- `fetchBytesFromSftp` — _(unfilled)_
- `fetchBytesFromFtp` — _(unfilled)_
- `determineMaxBytes` — _(unfilled)_
- `isJpegFile` — _(unfilled)_
- `isValidImageData` — _(unfilled)_
- `isValidPngData` — _(unfilled)_
- `chunkTypeEquals` — _(unfilled)_
- `readIntBigEndian` — _(unfilled)_
- `readUInt32LittleEndian` — _(unfilled)_
- `cleanup` — _(unfilled)_
- `cancel` — _(unfilled)_
- `getDataClass` — _(unfilled)_
- `getDataSource` — _(unfilled)_
- `build` — _(unfilled)_
- `teardown` — _(unfilled)_
- `smbClient` — _(unfilled)_
- `sftpClient` — _(unfilled)_
- `ftpClient` — _(unfilled)_
- `credentialsRepository` — _(unfilled)_
- `thumbnailCacheRepository` — _(unfilled)_
- `unifiedCache` — _(unfilled)_

### `NetworkMediaDataSource` — [com/sza/fastmediasorter/data/network/glide/NetworkMediaDataSource.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkMediaDataSource.kt)

**Layer:** data · **LOC:** 410 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `readAt` — _(unfilled)_
- `getSize` — _(unfilled)_
- `close` — _(unfilled)_
- `readBytesFromNetwork` — _(unfilled)_
- `readFromSmb` — _(unfilled)_
- `readFromSftp` — _(unfilled)_
- `readFromFtp` — _(unfilled)_

### `NetworkVideoFrameDecoder` — [com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt)

**Layer:** data · **LOC:** 367 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `handles` — _(unfilled)_
- `decode` — _(unfilled)_
- `loadFromThumbnailCache` — _(unfilled)_
- `extractVideoFrame` — _(unfilled)_
- `saveThumbnailToCache` — _(unfilled)_
- `getResourceClass` — _(unfilled)_
- `getSize` — _(unfilled)_
- `recycle` — _(unfilled)_
- `isExpectedFrameExtractionFailure` — _(unfilled)_
- `read` — _(unfilled)_

### `SafeByteBuffer` — [com/sza/fastmediasorter/data/network/glide/SafeByteBuffer.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/SafeByteBuffer.kt)

**Layer:** data · **LOC:** 11 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `SafeByteBufferBitmapDecoder` — [com/sza/fastmediasorter/data/network/glide/SafeByteBufferBitmapDecoder.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/SafeByteBufferBitmapDecoder.kt)

**Layer:** data · **LOC:** 120 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `handles` — _(unfilled)_
- `decode` — _(unfilled)_
- `calculateInSampleSize` — _(unfilled)_

### `SafeByteBufferEncoder` — [com/sza/fastmediasorter/data/network/glide/SafeByteBufferEncoder.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/SafeByteBufferEncoder.kt)

**Layer:** data · **LOC:** 22 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `encode` — _(unfilled)_

### `VideoExtractionFailurePersistence` — [com/sza/fastmediasorter/data/network/glide/VideoExtractionFailurePersistence.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/VideoExtractionFailurePersistence.kt)

**Layer:** data · **LOC:** 67 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `loadAll` — _(unfilled)_
- `persistFailure` — _(unfilled)_
- `clearAll` — _(unfilled)_
- `prefs` — _(unfilled)_

### `SmbDirectoryScanner` — [com/sza/fastmediasorter/data/network/helpers/SmbDirectoryScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/helpers/SmbDirectoryScanner.kt)

**Layer:** data · **LOC:** 531 · **Last:** 2026-03-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `scanDirectoryRecursive` — _(unfilled)_
- `scanDirectoryRecursiveWithLimit` — _(unfilled)_
- `scanDirectoryNonRecursive` — _(unfilled)_
- `scanDirectoryNonRecursiveWithOffset` — _(unfilled)_
- `scanDirectoryWithOffsetLimit` — _(unfilled)_
- `countDirectoryRecursive` — _(unfilled)_
- `countDirectoryNonRecursive` — _(unfilled)_

### `SmbConnectionInfo` — [com/sza/fastmediasorter/data/network/model/SmbModels.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/model/SmbModels.kt)

**Layer:** data · **LOC:** 44 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `BaseConnectionPool` — [com/sza/fastmediasorter/data/network/pool/BaseConnectionPool.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/pool/BaseConnectionPool.kt)

**Layer:** data · **LOC:** 206 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `createConnection` — _(unfilled)_
- `isConnectionValid` — _(unfilled)_
- `closeConnection` — _(unfilled)_
- `withConnection` — _(unfilled)_
- `getOrCreateConnection` — _(unfilled)_
- `invalidateConnection` — _(unfilled)_
- `cleanupIdleConnectionsQuick` — _(unfilled)_
- `cleanupIdleConnections` — _(unfilled)_
- `clearAllConnections` — _(unfilled)_
- `getPoolSize` — _(unfilled)_

### `SftpFileOperationHandler` — [com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt)

**Layer:** data · **LOC:** 411 · **Last:** 2026-02-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, SftpClient, SmbClient, FtpClient, NetworkCredentialsRepository  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getStrategies` — _(unfilled)_
- `executeCopy` — _(unfilled)_
- `executeMove` — _(unfilled)_
- `copyFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `executeDelete` — _(unfilled)_
- `executeRename` — _(unfilled)_
- `toClientInfo` — _(unfilled)_
- `parseSftpPath` — _(unfilled)_

### `SmbClient` — [com/sza/fastmediasorter/data/network/SmbClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbClient.kt)

**Layer:** data · **LOC:** 956 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** SmbConnectionManager, SmbFileOperations, SmbPlaybackConnectionTracker  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `testConnection` — _(unfilled)_
- `performTestConnection` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `scanMediaFiles` — _(unfilled)_
- `scanMediaFilesChunked` — _(unfilled)_
- `scanMediaFilesPaged` — _(unfilled)_
- `countMediaFiles` — _(unfilled)_
- `listShares` — _(unfilled)_
- `downloadFile` — _(unfilled)_
- `readFileBytes` — _(unfilled)_
- `readPartialFile` — _(unfilled)_
- `readFileBytesRange` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `deleteDirectory` — _(unfilled)_
- `renameFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `ensureSmbDirectoryExists` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `exists` — _(unfilled)_
- `getFileInfo` — _(unfilled)_
- `getUserFriendlyMessage` — _(unfilled)_
- `buildDiagnosticMessage` — _(unfilled)_
- `checkWritePermission` — _(unfilled)_
- `close` — _(unfilled)_
- `clearConnectionPool` — _(unfilled)_
- `forceFullReset` — _(unfilled)_
- `openInputStream` — _(unfilled)_
- `close` — _(unfilled)_

### `SmbClientErrorFormatter` — [com/sza/fastmediasorter/data/network/SmbClientErrorFormatter.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbClientErrorFormatter.kt)

**Layer:** data · **LOC:** 135 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `getUserFriendlyMessage` — _(unfilled)_
- `buildDiagnosticMessage` — _(unfilled)_
- `ensureSmbDirectoryExists` — _(unfilled)_

### `SmbResetCallback` — [com/sza/fastmediasorter/data/network/SmbConnectionManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt)

**Layer:** data · **LOC:** 1007 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** NetworkStateMonitor, SmbPlaybackConnectionTracker, NetworkReachabilityGate  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `onAutoReset` — _(unfilled)_
- `onNetworkChanged` — _(unfilled)_
- `onNetworkLost` — _(unfilled)_
- `getFastClient` — _(unfilled)_
- `getMediumClient` — _(unfilled)_
- `getDegradedClient` — _(unfilled)_
- `getNormalClient` — _(unfilled)_
- `getClient` — _(unfilled)_
- `withConnection` — _(unfilled)_
- `createFreshConnection` — _(unfilled)_
- `isConnectionValid` — _(unfilled)_
- `onSuccess` — _(unfilled)_
- `handleTimeout` — _(unfilled)_
- `handlePooledConnectionFailure` — _(unfilled)_
- `handleFreshConnectionFailure` — _(unfilled)_
- `removeConnection` — _(unfilled)_
- `closeConnectionAsync` — _(unfilled)_
- `closeAllConnections` — _(unfilled)_
- `isNonRetriableConnectionError` — _(unfilled)_
- `isTransportOrBrokenPipe` — _(unfilled)_
- `invalidateExoPlayerConnection` — _(unfilled)_
- `clearConnectionPool` — _(unfilled)_
- `setResetCallback` — _(unfilled)_
- `autoResetIfNeeded` — _(unfilled)_
- `resetAllConnections` — _(unfilled)_
- `resetClients` — _(unfilled)_
- `forceFullReset` — _(unfilled)_
- `checkConnectivity` — _(unfilled)_
- `getUserFriendlyMessage` — _(unfilled)_
- `getConnectionForExoPlayer` — _(unfilled)_
- `handleNetworkReconnect` — _(unfilled)_
- `handleNetworkLost` — _(unfilled)_
- `close` — _(unfilled)_

### `SmbPlaybackErrorCategory` — [com/sza/fastmediasorter/data/network/SmbErrorClassifier.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbErrorClassifier.kt)

**Layer:** data · **LOC:** 143 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `isNonRetriableConnectionError` — _(unfilled)_
- `isTransportOrBrokenPipe` — _(unfilled)_
- `getUserFriendlyMessage` — _(unfilled)_
- `checkConnectivity` — _(unfilled)_

### `SmbFileOperationHandler` — [com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt)

**Layer:** data · **LOC:** 680 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, SmbClient, NetworkCredentialsRepository  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getStrategies` — _(unfilled)_
- `copyFile` — _(unfilled)_
- `executeMove` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `executeRename` — _(unfilled)_
- `downloadFromSmb` — _(unfilled)_
- `uploadToSmb` — _(unfilled)_
- `deleteFromSmb` — _(unfilled)_
- `copySmbToSmb` — _(unfilled)_
- `parseSmbPath` — _(unfilled)_
- `resolveSmbCredentials` — _(unfilled)_

### `SmbFileOperations` — [com/sza/fastmediasorter/data/network/SmbFileOperations.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperations.kt)

**Layer:** data · **LOC:** 617 · **Last:** 2026-04-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** SmbConnectionManager  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `downloadFile` — _(unfilled)_
- `readFileBytes` — _(unfilled)_
- `readPartialFile` — _(unfilled)_
- `readFileBytesRange` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `deleteDirectory` — _(unfilled)_
- `renameFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `exists` — _(unfilled)_
- `getFileInfo` — _(unfilled)_
- `openInputStream` — _(unfilled)_
- `close` — _(unfilled)_
- `ensureSmbDirectoryExists` — _(unfilled)_

### `SmbMediaScanner` — [com/sza/fastmediasorter/data/network/SmbMediaScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbMediaScanner.kt)

**Layer:** data · **LOC:** 754 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** SmbClient, NetworkCredentialsRepository  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `scanFolder` — _(unfilled)_
- `scanFolderWithProgress` — _(unfilled)_
- `getFileByPath` — _(unfilled)_
- `scanFolderChunked` — _(unfilled)_
- `scanFolderPaged` — _(unfilled)_
- `getFileCount` — _(unfilled)_
- `listDirectoryContents` — _(unfilled)_
- `isWritable` — _(unfilled)_
- `parseSmbPath` — _(unfilled)_
- `buildFullSmbPath` — _(unfilled)_
- `extractExifMetadata` — _(unfilled)_
- `extractVideoMetadata` — _(unfilled)_
- `parseExifDateTimeMillis` — _(unfilled)_

### `SmbPlaybackConnectionTracker` — [com/sza/fastmediasorter/data/network/SmbPlaybackConnectionTracker.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbPlaybackConnectionTracker.kt)

**Layer:** data · **LOC:** 70 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onConnectionCreated` — _(unfilled)_
- `onConnectionValidated` — _(unfilled)_
- `onConnectionInvalidated` — _(unfilled)_
- `getStateName` — _(unfilled)_
- `recordWatchdog` — _(unfilled)_
- `clearWatchdog` — _(unfilled)_
- `isRecentWatchdog` — _(unfilled)_
- `clearAll` — _(unfilled)_

### `SmbShareDiscoveryHelper` — [com/sza/fastmediasorter/data/network/SmbShareDiscoveryHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbShareDiscoveryHelper.kt)

**Layer:** data · **LOC:** 239 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `listShares` — _(unfilled)_
- `performTestConnection` — _(unfilled)_

### `MediaFileObserver` — [com/sza/fastmediasorter/data/observer/MediaFileObserver.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/observer/MediaFileObserver.kt)

**Layer:** data · **LOC:** 59 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onFileDeleted` — _(unfilled)_
- `onFileCreated` — _(unfilled)_
- `onFileMoved` — _(unfilled)_
- `onFileModified` — _(unfilled)_
- `onEvent` — _(unfilled)_

### `MediaStoreObserver` — [com/sza/fastmediasorter/data/observer/MediaStoreObserver.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/observer/MediaStoreObserver.kt)

**Layer:** data · **LOC:** 73 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onChange` — _(unfilled)_
- `onChange` — _(unfilled)_
- `onChange` — _(unfilled)_
- `startWatching` — _(unfilled)_
- `stopWatching` — _(unfilled)_

### `MediaFilesPagingSource` — [com/sza/fastmediasorter/data/paging/MediaFilesPagingSource.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/paging/MediaFilesPagingSource.kt)

**Layer:** data · **LOC:** 106 · **Last:** 2026-02-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `load` — _(unfilled)_
- `getRefreshKey` — _(unfilled)_
- `sortFiles` — _(unfilled)_

### `FtpClient` — [com/sza/fastmediasorter/data/remote/ftp/FtpClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpClient.kt)

**Layer:** data · **LOC:** 911 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `getConnectionForExoPlayer` — _(unfilled)_
- `releaseExoPlayerConnection` — _(unfilled)_
- `cleanupIdleFtpConnections` — _(unfilled)_
- `connect` — _(unfilled)_
- `listFilesWithMetadata` — _(unfilled)_
- `listFilesWithMetadataPaged` — _(unfilled)_
- `listFilesWithMetadataSingleLevel` — _(unfilled)_
- `listFilesWithMetadataRecursive` — _(unfilled)_
- `listFilesWithMetadataRecursivePaged` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `readFileBytes` — _(unfilled)_
- `readFileBytesRange` — _(unfilled)_
- `downloadFile` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `deleteDirectory` — _(unfilled)_
- `renameFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `directoryExists` — _(unfilled)_
- `disconnect` — _(unfilled)_
- `isConnected` — _(unfilled)_
- `uploadFileWithNewConnection` — _(unfilled)_
- `deleteFileWithNewConnection` — _(unfilled)_
- `renameFileWithNewConnection` — _(unfilled)_
- `createDirectoryWithNewConnection` — _(unfilled)_
- `existsWithNewConnection` — _(unfilled)_
- `readFileBytesWithNewConnection` — _(unfilled)_
- `downloadFileWithNewConnection` — _(unfilled)_
- `openInputStream` — _(unfilled)_

### `FtpDirectoryScanner` — [com/sza/fastmediasorter/data/remote/ftp/FtpDirectoryScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpDirectoryScanner.kt)

**Layer:** data · **LOC:** 125 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `listFilesWithMetadataSingleLevel` — _(unfilled)_
- `listFilesWithMetadataRecursive` — _(unfilled)_
- `listFilesWithMetadataRecursivePaged` — _(unfilled)_
- `listWithPassiveActiveFallback` — _(unfilled)_
- `joinPath` — _(unfilled)_

### `FtpExoPlayerPool` — [com/sza/fastmediasorter/data/remote/ftp/FtpExoPlayerPool.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpExoPlayerPool.kt)

**Layer:** data · **LOC:** 161 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getConnectionForExoPlayer` — _(unfilled)_
- `releaseExoPlayerConnection` — _(unfilled)_
- `cleanupIdleFtpConnections` — _(unfilled)_

### `FtpMediaScanner` — [com/sza/fastmediasorter/data/remote/ftp/FtpMediaScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpMediaScanner.kt)

**Layer:** data · **LOC:** 566 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** FtpClient, NetworkCredentialsRepository  
**Side effects:** —  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `scanFolder` — _(unfilled)_
- `scanFolderPaged` — _(unfilled)_
- `getFileCount` — _(unfilled)_
- `listDirectoryContents` — _(unfilled)_
- `isWritable` — _(unfilled)_
- `parseFtpPath` — _(unfilled)_
- `buildFullFtpPath` — _(unfilled)_
- `getMediaType` — _(unfilled)_
- `toMediaFileOrNull` — _(unfilled)_

### `FtpStandaloneOperations` — [com/sza/fastmediasorter/data/remote/ftp/FtpStandaloneOperations.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpStandaloneOperations.kt)

**Layer:** data · **LOC:** 435 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `testConnection` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `renameFile` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `exists` — _(unfilled)_
- `readFileBytes` — _(unfilled)_
- `downloadFile` — _(unfilled)_
- `openInputStream` — _(unfilled)_
- `close` — _(unfilled)_
- `ensureRemoteDirectoryExists` — _(unfilled)_
- `executeWithNewConnection` — _(unfilled)_
- `applyTimeouts` — _(unfilled)_
- `disconnectQuietly` — _(unfilled)_

### `ITunesApiService` — [com/sza/fastmediasorter/data/remote/ITunesApiService.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ITunesApiService.kt)

**Layer:** data · **LOC:** 55 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `searchTracks` — _(unfilled)_

### `SftpFileAttributes` — [com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt)

**Layer:** data · **LOC:** 635 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `withConnection` — _(unfilled)_
- `getConnectionForExoPlayer` — _(unfilled)_
- `releaseExoPlayerConnection` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `listFilesSingleLevel` — _(unfilled)_
- `listFilesRecursive` — _(unfilled)_
- `readFileBytes` — _(unfilled)_
- `readFileBytesRange` — _(unfilled)_
- `downloadFile` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `stat` — _(unfilled)_
- `exists` — _(unfilled)_
- `mkdir` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `deleteDirectory` — _(unfilled)_
- `deleteRecursive` — _(unfilled)_
- `rename` — _(unfilled)_
- `renameFile` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `getFileAttributes` — _(unfilled)_
- `disconnectAll` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `testConnectionWithPrivateKey` — _(unfilled)_
- `ensureDirectoryExists` — _(unfilled)_
- `openInputStream` — _(unfilled)_

### `SftpConnectionPool` — [com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt)

**Layer:** data · **LOC:** 488 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `withConnection` — _(unfilled)_
- `getOrCreateChannel` — _(unfilled)_
- `removeChannel` — _(unfilled)_
- `getOrCreateConnection` — _(unfilled)_
- `invalidate` — _(unfilled)_
- `invalidateConnection` — _(unfilled)_
- `cleanupIdleConnections` — _(unfilled)_
- `getConnectionForExoPlayer` — _(unfilled)_
- `releaseExoPlayerConnection` — _(unfilled)_
- `openInputStream` — _(unfilled)_
- `close` — _(unfilled)_
- `close` — _(unfilled)_
- `disconnectAll` — _(unfilled)_
- `applyIdentity` — _(unfilled)_
- `applyAuth` — _(unfilled)_
- `getPassphrase` — _(unfilled)_
- `getPassword` — _(unfilled)_
- `promptPassword` — _(unfilled)_
- `promptPassphrase` — _(unfilled)_
- `promptYesNo` — _(unfilled)_
- `showMessage` — _(unfilled)_
- `getPassphrase` — _(unfilled)_
- `getPassword` — _(unfilled)_
- `promptPassword` — _(unfilled)_
- `promptPassphrase` — _(unfilled)_
- `promptYesNo` — _(unfilled)_
- `showMessage` — _(unfilled)_

### `SftpConnectionTester` — [com/sza/fastmediasorter/data/remote/sftp/SftpConnectionTester.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionTester.kt)

**Layer:** data · **LOC:** 154 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `testConnection` — _(unfilled)_
- `getPassphrase` — _(unfilled)_
- `getPassword` — _(unfilled)_
- `promptPassword` — _(unfilled)_
- `promptPassphrase` — _(unfilled)_
- `promptYesNo` — _(unfilled)_
- `showMessage` — _(unfilled)_
- `testConnectionWithPrivateKey` — _(unfilled)_
- `getPassphrase` — _(unfilled)_
- `getPassword` — _(unfilled)_
- `promptPassword` — _(unfilled)_
- `promptPassphrase` — _(unfilled)_
- `promptYesNo` — _(unfilled)_
- `showMessage` — _(unfilled)_
- `ensureDirectoryExists` — _(unfilled)_

### `SftpMediaScanner` — [com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt)

**Layer:** data · **LOC:** 487 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** SftpClient, NetworkCredentialsRepository  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `scanFolder` — _(unfilled)_
- `scanFolderPaged` — _(unfilled)_
- `getFileCount` — _(unfilled)_
- `listDirectoryContents` — _(unfilled)_
- `isWritable` — _(unfilled)_
- `parseSftpPath` — _(unfilled)_
- `getMediaType` — _(unfilled)_

### `AudioMetadataCacheRepository` — [com/sza/fastmediasorter/data/repository/AudioMetadataCacheRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AudioMetadataCacheRepository.kt)

**Layer:** data · **LOC:** 150 · **Last:** 2026-03-24 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** disk  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `readMetadata` — _(unfilled)_
- `saveMetadata` — _(unfilled)_
- `saveCover` — _(unfilled)_
- `getCacheSize` — _(unfilled)_
- `clearCache` — _(unfilled)_
- `trimIfNeeded` — _(unfilled)_
- `cleanupExpired` — _(unfilled)_
- `ensureCacheDirExists` — _(unfilled)_
- `atomicWrite` — _(unfilled)_
- `atomicWriteBytes` — _(unfilled)_

### `CachedFileListRepository` — [com/sza/fastmediasorter/data/repository/CachedFileListRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/CachedFileListRepository.kt)

**Layer:** data · **LOC:** 147 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** CachedFileListDao  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `compress` — _(unfilled)_
- `decompress` — _(unfilled)_
- `saveCachedFiles` — _(unfilled)_
- `getEntityByResourceId` — _(unfilled)_
- `getCachedFiles` — _(unfilled)_
- `deleteCachedFiles` — _(unfilled)_
- `deleteAllCachedFiles` — _(unfilled)_
- `hasCachedFiles` — _(unfilled)_
- `updateFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_

### `DuplicateHashRepositoryImpl` — [com/sza/fastmediasorter/data/repository/DuplicateHashRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/DuplicateHashRepositoryImpl.kt)

**Layer:** data · **LOC:** 71 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** DuplicateHashCacheDao  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getCachedQuickHash` — _(unfilled)_
- `getCachedFullHash` — _(unfilled)_
- `saveQuickHash` — _(unfilled)_
- `saveFullHash` — _(unfilled)_
- `deleteByResourceId` — _(unfilled)_
- `deleteHashEntry` — _(unfilled)_

### `FavoritesRepositoryImpl` — [com/sza/fastmediasorter/data/repository/FavoritesRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/FavoritesRepositoryImpl.kt)

**Layer:** data · **LOC:** 53 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** FavoritesDao  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getAllFavorites` — _(unfilled)_
- `isFavorite` — _(unfilled)_
- `isFavoriteSync` — _(unfilled)_
- `getFavoritesForPaths` — _(unfilled)_
- `addFavorite` — _(unfilled)_
- `removeFavorite` — _(unfilled)_
- `removeFavoriteById` — _(unfilled)_

### `MediaStoreRepositoryImpl` — [com/sza/fastmediasorter/data/repository/MediaStoreRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/MediaStoreRepositoryImpl.kt)

**Layer:** data · **LOC:** 670 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `isTrashPath` — _(unfilled)_
- `buildSelectionForAllowedTypes` — _(unfilled)_
- `getFoldersWithMedia` — _(unfilled)_
- `getRecentFiles` — _(unfilled)_
- `getAllFilesByTypes` — _(unfilled)_
- `countAllFilesByTypes` — _(unfilled)_
- `resolveType` — _(unfilled)_
- `build` — _(unfilled)_
- `getFilesInFolder` — _(unfilled)_
- `getStandardFolders` — _(unfilled)_
- `findCameraFolderPath` — _(unfilled)_

### `NetworkCredentialsRepositoryImpl` — [com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt)

**Layer:** data · **LOC:** 274 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, NetworkCredentialsDao, ResourceDao, SettingsRepository, ApplicationScope, CoroutineScope, Application  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `loadTestCredentials` — _(unfilled)_
- `insert` — _(unfilled)_
- `getById` — _(unfilled)_
- `getByCredentialId` — _(unfilled)_
- `getByTypeServerAndPort` — _(unfilled)_
- `getByServerAndShare` — _(unfilled)_
- `getCredentialsByHost` — _(unfilled)_
- `getByTypeAndAccountId` — _(unfilled)_
- `update` — _(unfilled)_
- `delete` — _(unfilled)_
- `getAllCredentials` — _(unfilled)_
- `getOrphanedCredentials` — _(unfilled)_
- `applyDefaultCredentialsIfNeeded` — _(unfilled)_

### `PlaybackPositionRepositoryImpl` — [com/sza/fastmediasorter/data/repository/PlaybackPositionRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/PlaybackPositionRepositoryImpl.kt)

**Layer:** data · **LOC:** 109 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** PlaybackPositionDao  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getPosition` — _(unfilled)_
- `savePosition` — _(unfilled)_
- `markAsCompleted` — _(unfilled)_
- `markPlaybackCompleted` — _(unfilled)_
- `deletePosition` — _(unfilled)_
- `cleanupOldPositions` — _(unfilled)_
- `deleteAllPositions` — _(unfilled)_

### `ResourceRepositoryImpl` — [com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt)

**Layer:** data · **LOC:** 530 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResourceDao, NetworkCredentialsRepository, SmbOperationsUseCase  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getAllResources` — _(unfilled)_
- `getAllResourcesSync` — _(unfilled)_
- `getResourceById` — _(unfilled)_
- `getResourcesByType` — _(unfilled)_
- `getDestinations` — _(unfilled)_
- `getFilteredResources` — _(unfilled)_
- `getComparator` — _(unfilled)_
- `addResource` — _(unfilled)_
- `updateResource` — _(unfilled)_
- `swapResourceDisplayOrders` — _(unfilled)_
- `updateResourcesDisplayOrder` — _(unfilled)_
- `deleteResource` — _(unfilled)_
- `deleteAllResources` — _(unfilled)_
- `updateIcon` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `testSmbConnection` — _(unfilled)_
- `testSftpConnection` — _(unfilled)_
- `testFtpConnection` — _(unfilled)_
- `toDomain` — _(unfilled)_
- `toEntity` — _(unfilled)_

### `ResumeStateRepositoryImpl` — [com/sza/fastmediasorter/data/repository/ResumeStateRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResumeStateRepositoryImpl.kt)

**Layer:** data · **LOC:** 93 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** prefs  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `saveState` — _(unfilled)_
- `getState` — _(unfilled)_
- `clearState` — _(unfilled)_
- `clearStateInternal` — _(unfilled)_

### `ScheduledOperationRepositoryImpl` — [com/sza/fastmediasorter/data/repository/ScheduledOperationRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ScheduledOperationRepositoryImpl.kt)

**Layer:** data · **LOC:** 84 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** ScheduledOperationDao  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getAll` — _(unfilled)_
- `getAllEnabled` — _(unfilled)_
- `getById` — _(unfilled)_
- `upsert` — _(unfilled)_
- `update` — _(unfilled)_
- `deleteById` — _(unfilled)_
- `deleteAll` — _(unfilled)_
- `getCount` — _(unfilled)_
- `toDomain` — _(unfilled)_
- `toEntity` — _(unfilled)_

### `SettingsRepositoryImpl` — [com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt)

**Layer:** data · **LOC:** 828 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, DataStore  
**Side effects:** prefs  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `getSettings` — _(unfilled)_
- `updateSettings` — _(unfilled)_
- `resetToDefaults` — _(unfilled)_
- `setPlayerFirstRun` — _(unfilled)_
- `isPlayerFirstRun` — _(unfilled)_
- `saveLastUsedResourceId` — _(unfilled)_
- `getLastUsedResourceId` — _(unfilled)_
- `setResourceGridMode` — _(unfilled)_
- `isTouchZoneHintShown` — _(unfilled)_
- `setTouchZoneHintShown` — _(unfilled)_
- `resetAllTouchZoneHints` — _(unfilled)_
- `encryptPassword` — _(unfilled)_
- `decryptPassword` — _(unfilled)_
- `readVrForcedPlatFormat` — _(unfilled)_
- `readVrForcedSphericalFormat` — _(unfilled)_

### `StreamingCacheRepositoryImpl` — [com/sza/fastmediasorter/data/repository/StreamingCacheRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamingCacheRepositoryImpl.kt)

**Layer:** data · **LOC:** 129 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, StreamingCacheDao  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `resolveHash` — _(unfilled)_
- `findByHash` — _(unfilled)_
- `findByOriginalUri` — _(unfilled)_
- `record` — _(unfilled)_
- `touchPlayed` — _(unfilled)_
- `delete` — _(unfilled)_
- `verifyAndPrune` — _(unfilled)_
- `findStaleByTtl` — _(unfilled)_
- `totalSizeBytes` — _(unfilled)_
- `getAll` — _(unfilled)_
- `observeAll` — _(unfilled)_
- `clearAll` — _(unfilled)_
- `localFileExists` — _(unfilled)_
- `deleteLocalFile` — _(unfilled)_

### `TestCredentialsConfig` — [com/sza/fastmediasorter/data/repository/TestCredentialModels.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/TestCredentialModels.kt)

**Layer:** data · **LOC:** 35 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `ThumbnailCacheRepositoryImpl` — [com/sza/fastmediasorter/data/repository/ThumbnailCacheRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ThumbnailCacheRepositoryImpl.kt)

**Layer:** data · **LOC:** 239 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, ThumbnailCacheDao  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getCachedThumbnail` — _(unfilled)_
- `saveThumbnail` — _(unfilled)_
- `deleteThumbnail` — _(unfilled)_
- `cleanupOldThumbnails` — _(unfilled)_
- `getCacheStats` — _(unfilled)_
- `enforceSizeLimit` — _(unfilled)_
- `migrateLegacyCache` — _(unfilled)_

### `FtpFileAccess` — [com/sza/fastmediasorter/data/transfer/access/FtpFileAccess.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/access/FtpFileAccess.kt)

**Layer:** data · **LOC:** 96 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** FtpClient, NetworkCredentialsResolver  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `exists` — _(unfilled)_
- `delete` — _(unfilled)_
- `extractRemotePath` — _(unfilled)_

### `LocalFileAccess` — [com/sza/fastmediasorter/data/transfer/access/LocalFileAccess.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/access/LocalFileAccess.kt)

**Layer:** data · **LOC:** 53 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `exists` — _(unfilled)_
- `delete` — _(unfilled)_

### `SftpFileAccess` — [com/sza/fastmediasorter/data/transfer/access/SftpFileAccess.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/access/SftpFileAccess.kt)

**Layer:** data · **LOC:** 72 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** SftpClient, NetworkCredentialsResolver  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `exists` — _(unfilled)_
- `delete` — _(unfilled)_
- `extractRemotePath` — _(unfilled)_

### `SmbFileAccess` — [com/sza/fastmediasorter/data/transfer/access/SmbFileAccess.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/access/SmbFileAccess.kt)

**Layer:** data · **LOC:** 30 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** SmbClient  
**Side effects:** network  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `exists` — _(unfilled)_
- `delete` — _(unfilled)_

### `AtomicFileOperationStrategy` — [com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt)

**Layer:** data · **LOC:** 302 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `setAtomicEnabled` — _(unfilled)_
- `copyFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `renamePath` — _(unfilled)_
- `renameLocalPath` — _(unfilled)_
- `pathExists` — _(unfilled)_
- `deletePath` — _(unfilled)_
- `isLocalPath` — _(unfilled)_
- `cleanupTempFile` — _(unfilled)_
- `getProtocolName` — _(unfilled)_

### `BaseFileOperationHandler` — [com/sza/fastmediasorter/data/transfer/BaseFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/BaseFileOperationHandler.kt)

**Layer:** data · **LOC:** 940 · **Last:** 2026-03-30 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getStrategies` — _(unfilled)_
- `getStrategyForPath` — _(unfilled)_
- `executeCopy` — _(unfilled)_
- `executeMove` — _(unfilled)_
- `executeDelete` — _(unfilled)_
- `copyFile` — _(unfilled)_
- `copyCrossProtocol` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `moveToTrash` — _(unfilled)_
- `joinPath` — _(unfilled)_
- `extractFileName` — _(unfilled)_
- `createTrashFolder` — _(unfilled)_
- `deleteWithSaf` — _(unfilled)_
- `checkBatchDeletePermissionBeforeMove` — _(unfilled)_
- `requestBatchDeletePermission` — _(unfilled)_
- `getSafePath` — _(unfilled)_
- `buildCopyResult` — _(unfilled)_
- `buildMoveResult` — _(unfilled)_
- `buildDeleteResult` — _(unfilled)_
- `listFiles` — _(unfilled)_

### `FileAccess` — [com/sza/fastmediasorter/data/transfer/FileAccess.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileAccess.kt)

**Layer:** data · **LOC:** 14 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `exists` — _(unfilled)_
- `delete` — _(unfilled)_

### `FileExistsException` — [com/sza/fastmediasorter/data/transfer/FileExistsException.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileExistsException.kt)

**Layer:** data · **LOC:** 16 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `FileOperationStrategy` — [com/sza/fastmediasorter/data/transfer/FileOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileOperationStrategy.kt)

**Layer:** data · **LOC:** 207 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `copyFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `exists` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `writeFile` — _(unfilled)_
- `readFile` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `supportsProtocol` — _(unfilled)_
- `getProtocolName` — _(unfilled)_
- `deleteDirectory` — _(unfilled)_
- `renameDirectory` — _(unfilled)_
- `copyDirectory` — _(unfilled)_
- `moveDirectory` — _(unfilled)_
- `isDirectory` — _(unfilled)_
- `getDirectoryInfo` — _(unfilled)_

### `LocalTransferProvider` — [com/sza/fastmediasorter/data/transfer/LocalTransferProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/LocalTransferProvider.kt)

**Layer:** data · **LOC:** 395 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `downloadFile` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `renameFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `exists` — _(unfilled)_
- `getFileInfo` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `isFile` — _(unfilled)_

### `SmbTransferProvider` — [com/sza/fastmediasorter/data/transfer/SmbTransferProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/SmbTransferProvider.kt)

**Layer:** data · **LOC:** 342 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, SmbClient, NetworkCredentialsRepository  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `adaptProgressCallback` — _(unfilled)_
- `onProgress` — _(unfilled)_
- `parseSmbPath` — _(unfilled)_
- `getConnectionInfo` — _(unfilled)_
- `resolveSmbCredentials` — _(unfilled)_
- `downloadFile` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `renameFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `exists` — _(unfilled)_
- `getFileInfo` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `isFile` — _(unfilled)_

### `FtpToFtpStrategy` — [com/sza/fastmediasorter/data/transfer/strategies/FtpToFtpStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/FtpToFtpStrategy.kt)

**Layer:** data · **LOC:** 214 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, FtpClient, NetworkCredentialsResolver  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `move` — _(unfilled)_
- `copy` — _(unfilled)_
- `extractRemotePath` — _(unfilled)_

### `FtpToLocalStrategy` — [com/sza/fastmediasorter/data/transfer/strategies/FtpToLocalStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/FtpToLocalStrategy.kt)

**Layer:** data · **LOC:** 103 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, FtpClient, NetworkCredentialsResolver  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `copy` — _(unfilled)_
- `extractRemotePath` — _(unfilled)_

### `LocalToFtpStrategy` — [com/sza/fastmediasorter/data/transfer/strategies/LocalToFtpStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/LocalToFtpStrategy.kt)

**Layer:** data · **LOC:** 120 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, FtpClient, NetworkCredentialsResolver  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `copy` — _(unfilled)_
- `extractRemotePath` — _(unfilled)_

### `LocalToSftpStrategy` — [com/sza/fastmediasorter/data/transfer/strategies/LocalToSftpStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/LocalToSftpStrategy.kt)

**Layer:** data · **LOC:** 118 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, SftpClient, NetworkCredentialsResolver  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `copy` — _(unfilled)_
- `extractRemotePath` — _(unfilled)_

### `LocalToSmbStrategy` — [com/sza/fastmediasorter/data/transfer/strategies/LocalToSmbStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/LocalToSmbStrategy.kt)

**Layer:** data · **LOC:** 142 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, SmbClient  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `copy` — _(unfilled)_
- `resolveConnectionInfo` — _(unfilled)_

### `SftpToLocalStrategy` — [com/sza/fastmediasorter/data/transfer/strategies/SftpToLocalStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/SftpToLocalStrategy.kt)

**Layer:** data · **LOC:** 101 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, SftpClient, NetworkCredentialsResolver  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `copy` — _(unfilled)_
- `extractRemotePath` — _(unfilled)_

### `SftpToSftpStrategy` — [com/sza/fastmediasorter/data/transfer/strategies/SftpToSftpStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/SftpToSftpStrategy.kt)

**Layer:** data · **LOC:** 175 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, SftpClient, NetworkCredentialsResolver  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `move` — _(unfilled)_
- `copy` — _(unfilled)_
- `extractRemotePath` — _(unfilled)_
- `toSftpConnectionInfo` — _(unfilled)_

### `SmbToLocalStrategy` — [com/sza/fastmediasorter/data/transfer/strategies/SmbToLocalStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/SmbToLocalStrategy.kt)

**Layer:** data · **LOC:** 74 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, SmbClient  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `copy` — _(unfilled)_

### `SmbToSmbStrategy` — [com/sza/fastmediasorter/data/transfer/strategies/SmbToSmbStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategies/SmbToSmbStrategy.kt)

**Layer:** data · **LOC:** 111 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** SmbClient  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `supports` — _(unfilled)_
- `copy` — _(unfilled)_
- `move` — _(unfilled)_

### `CloudOperationStrategy` — [com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt)

**Layer:** data · **LOC:** 741 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `copyFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `exists` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `writeFile` — _(unfilled)_
- `readFile` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `supportsProtocol` — _(unfilled)_
- `getProtocolName` — _(unfilled)_
- `downloadCloudToLocal` — _(unfilled)_
- `uploadLocalToCloud` — _(unfilled)_
- `copyCloudToCloud` — _(unfilled)_
- `getClientOrThrow` — _(unfilled)_
- `getClient` — _(unfilled)_
- `parseCloudUri` — _(unfilled)_
- `splitParentAndName` — _(unfilled)_
- `guessMimeType` — _(unfilled)_
- `deleteDirectory` — _(unfilled)_
- `collectCloudFiles` — _(unfilled)_
- `renameDirectory` — _(unfilled)_
- `copyDirectory` — _(unfilled)_
- `collectCloudFilesWithPath` — _(unfilled)_
- `isDirectory` — _(unfilled)_
- `getDirectoryInfo` — _(unfilled)_

### `FtpOperationStrategy` — [com/sza/fastmediasorter/data/transfer/strategy/FtpOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/FtpOperationStrategy.kt)

**Layer:** data · **LOC:** 708 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `copyFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `exists` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `writeFile` — _(unfilled)_
- `readFile` — _(unfilled)_
- `supportsProtocol` — _(unfilled)_
- `getProtocolName` — _(unfilled)_
- `parseFtpPath` — _(unfilled)_
- `ensureConnected` — _(unfilled)_
- `ensureFtpDirectoryExists` — _(unfilled)_
- `copyFtpToFtp` — _(unfilled)_
- `downloadFromFtp` — _(unfilled)_
- `uploadToFtp` — _(unfilled)_
- `deleteDirectory` — _(unfilled)_
- `collectFtpFiles` — _(unfilled)_
- `renameDirectory` — _(unfilled)_
- `copyDirectory` — _(unfilled)_
- `collectFtpFilesOnly` — _(unfilled)_
- `isDirectory` — _(unfilled)_
- `getDirectoryInfo` — _(unfilled)_

### `LocalOperationStrategy` — [com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt)

**Layer:** data · **LOC:** 536 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `copyFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `deleteViaMediaStore` — _(unfilled)_
- `exists` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `writeFile` — _(unfilled)_
- `readFile` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `supportsProtocol` — _(unfilled)_
- `getProtocolName` — _(unfilled)_
- `deleteDirectory` — _(unfilled)_
- `collectAllFiles` — _(unfilled)_
- `renameDirectory` — _(unfilled)_
- `copyDirectory` — _(unfilled)_
- `isDirectory` — _(unfilled)_
- `getDirectoryInfo` — _(unfilled)_

### `SftpOperationStrategy` — [com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt)

**Layer:** data · **LOC:** 712 · **Last:** 2026-02-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `copyFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `exists` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `writeFile` — _(unfilled)_
- `readFile` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `supportsProtocol` — _(unfilled)_
- `getProtocolName` — _(unfilled)_
- `parseSftpPath` — _(unfilled)_
- `getConnectionInfo` — _(unfilled)_
- `copySftpToSftp` — _(unfilled)_
- `downloadFromSftp` — _(unfilled)_
- `uploadToSftp` — _(unfilled)_
- `deleteDirectory` — _(unfilled)_
- `collectSftpFiles` — _(unfilled)_
- `renameDirectory` — _(unfilled)_
- `copyDirectory` — _(unfilled)_
- `collectSftpFilesOnly` — _(unfilled)_
- `isDirectory` — _(unfilled)_
- `getDirectoryInfo` — _(unfilled)_

### `SmbOperationStrategy` — [com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt)

**Layer:** data · **LOC:** 824 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `copyFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `writeFile` — _(unfilled)_
- `readFile` — _(unfilled)_
- `exists` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `supportsProtocol` — _(unfilled)_
- `getProtocolName` — _(unfilled)_
- `parseAndFixUri` — _(unfilled)_
- `downloadFromSmb` — _(unfilled)_
- `uploadToSmb` — _(unfilled)_
- `copySmbToSmb` — _(unfilled)_
- `getConnectionInfo` — _(unfilled)_
- `resolveSmbCredentials` — _(unfilled)_
- `deleteDirectory` — _(unfilled)_
- `collectSmbFiles` — _(unfilled)_
- `renameDirectory` — _(unfilled)_
- `copyDirectory` — _(unfilled)_
- `collectSmbFilesOnly` — _(unfilled)_
- `isDirectory` — _(unfilled)_
- `getDirectoryInfo` — _(unfilled)_

### `StrategyUtils` — [com/sza/fastmediasorter/data/transfer/strategy/StrategyUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/StrategyUtils.kt)

**Layer:** data · **LOC:** 22 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `safeIo` — _(unfilled)_

### `TempFileNamingStrategy` — [com/sza/fastmediasorter/data/transfer/TempFileNamingStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/TempFileNamingStrategy.kt)

**Layer:** data · **LOC:** 113 · **Last:** 2026-02-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `getTempPath` — _(unfilled)_
- `getOriginalPath` — _(unfilled)_
- `isTempFile` — _(unfilled)_
- `getFileName` — _(unfilled)_
- `getDirectoryPath` — _(unfilled)_
- `getTempPathInSameDir` — _(unfilled)_

### `TransferStrategy` — [com/sza/fastmediasorter/data/transfer/TransferStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/TransferStrategy.kt)

**Layer:** data · **LOC:** 115 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `canHandle` — _(unfilled)_
- `copy` — _(unfilled)_
- `copy` — _(unfilled)_
- `move` — _(unfilled)_
- `move` — _(unfilled)_
- `supports` — _(unfilled)_

### `UnifiedFileOperationHandler` — [com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt)

**Layer:** data · **LOC:** 539 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** LocalTransferProvider, TempFileManager, ProgressTracker, FileOperationErrorHandler, Map  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `registerProvider` — _(unfilled)_
- `executeCopy` — _(unfilled)_
- `executeMove` — _(unfilled)_
- `executeRename` — _(unfilled)_
- `executeDelete` — _(unfilled)_
- `executeCreateDirectory` — _(unfilled)_
- `executeSoftDelete` — _(unfilled)_
- `executeSameProtocolCopy` — _(unfilled)_
- `executeCrossProtocolCopy` — _(unfilled)_
- `getProvider` — _(unfilled)_
- `generateDestinationPath` — _(unfilled)_
- `executeDeleteDirectory` — _(unfilled)_
- `executeRenameDirectory` — _(unfilled)_
- `executeCopyDirectory` — _(unfilled)_
- `executeMoveDirectory` — _(unfilled)_
- `getStrategy` — _(unfilled)_
- `getProtocolKey` — _(unfilled)_

### `UniversalFileOperationHandler` — [com/sza/fastmediasorter/data/transfer/UniversalFileOperationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UniversalFileOperationHandler.kt)

**Layer:** data · **LOC:** 207 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, Set  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `executeCopy` — _(unfilled)_
- `executeMove` — _(unfilled)_
- `executeDelete` — _(unfilled)_
- `performTransfer` — _(unfilled)_
- `processResult` — _(unfilled)_
- `buildFinalResult` — _(unfilled)_
- `buildDestUri` — _(unfilled)_

### `WearableDataLayerRepositoryImpl` — [com/sza/fastmediasorter/data/wear/WearableDataLayerRepositoryImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/wear/WearableDataLayerRepositoryImpl.kt)

**Layer:** data · **LOC:** 41 · **Last:** 2026-04-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getConnectedNodes` — _(unfilled)_
- `putDataItem` — _(unfilled)_
- `sendMessage` — _(unfilled)_

### `IoDispatcher` — [com/sza/fastmediasorter/core/di/AppModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/di/AppModule.kt)

**Layer:** di · **LOC:** 174 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, prefs  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `provideApplicationContext` — _(unfilled)_
- `provideIoDispatcher` — _(unfilled)_
- `provideMainDispatcher` — _(unfilled)_
- `provideDefaultDispatcher` — _(unfilled)_
- `provideApplicationScope` — _(unfilled)_
- `provideDataStore` — _(unfilled)_
- `provideMediaFilesCacheManager` — _(unfilled)_
- `provideUnifiedFileCache` — _(unfilled)_
- `provideOkHttpClient` — _(unfilled)_
- `provideRetrofit` — _(unfilled)_
- `provideITunesApiService` — _(unfilled)_

### `DatabaseModule` — [com/sza/fastmediasorter/core/di/DatabaseModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt)

**Layer:** di · **LOC:** 174 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** db  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `provideAppDatabase` — _(unfilled)_
- `buildDatabase` — _(unfilled)_
- `provideResourceDao` — _(unfilled)_
- `provideNetworkCredentialsDao` — _(unfilled)_
- `provideFavoritesDao` — _(unfilled)_
- `providePlaybackPositionDao` — _(unfilled)_
- `provideThumbnailCacheDao` — _(unfilled)_
- `provideCachedFileListDao` — _(unfilled)_
- `provideFileMetadataCacheDao` — _(unfilled)_
- `provideStereoFormatOverrideDao` — _(unfilled)_
- `providePendingRevocationDao` — _(unfilled)_
- `provideScheduledOperationDao` — _(unfilled)_
- `provideDuplicateHashCacheDao` — _(unfilled)_
- `provideStreamingCacheDao` — _(unfilled)_
- `provideCachedMediaMetadataExtractor` — _(unfilled)_

### `RepositoryModule` — [com/sza/fastmediasorter/core/di/RepositoryModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt)

**Layer:** di · **LOC:** 99 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `bindResourceRepository` — _(unfilled)_
- `bindSettingsRepository` — _(unfilled)_
- `bindNetworkCredentialsRepository` — _(unfilled)_
- `bindFavoritesRepository` — _(unfilled)_
- `bindPlaybackPositionRepository` — _(unfilled)_
- `bindThumbnailCacheRepository` — _(unfilled)_
- `bindMediaStoreRepository` — _(unfilled)_
- `bindResumeStateRepository` — _(unfilled)_
- `bindScheduledOperationRepository` — _(unfilled)_
- `bindWearableDataLayerRepository` — _(unfilled)_
- `bindStreamingCacheRepository` — _(unfilled)_

### `DirectoryStrategyModule` — [com/sza/fastmediasorter/di/DirectoryStrategyModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/DirectoryStrategyModule.kt)

**Layer:** di · **LOC:** 86 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `provideLocalDirectoryStrategy` — _(unfilled)_
- `provideSmbDirectoryStrategy` — _(unfilled)_
- `provideSftpDirectoryStrategy` — _(unfilled)_
- `provideFtpDirectoryStrategy` — _(unfilled)_
- `provideCloudDirectoryStrategy` — _(unfilled)_

### `DuplicateHashModule` — [com/sza/fastmediasorter/di/DuplicateHashModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/DuplicateHashModule.kt)

**Layer:** di · **LOC:** 21 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `bindDuplicateHashRepository` — _(unfilled)_

### `GlideAppModule` — [com/sza/fastmediasorter/di/GlideAppModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt)

**Layer:** di · **LOC:** 197 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk, prefs  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `applyOptions` — _(unfilled)_
- `registerComponents` — _(unfilled)_
- `isManifestParsingEnabled` — _(unfilled)_

### `InputBindingModule` — [com/sza/fastmediasorter/di/InputBindingModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/InputBindingModule.kt)

**Layer:** di · **LOC:** 19 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `provideInputBindingDao` — _(unfilled)_

### `LinkDownloadModule` — [com/sza/fastmediasorter/di/LinkDownloadModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt)

**Layer:** di · **LOC:** 69 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `provideOkHttpClient` — _(unfilled)_
- `intercept` — _(unfilled)_
- `bindDirect` — _(unfilled)_
- `bindHtml` — _(unfilled)_

### `PlayerCommandOverrideModule` — [com/sza/fastmediasorter/di/PlayerCommandOverrideModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/PlayerCommandOverrideModule.kt)

**Layer:** di · **LOC:** 27 · **Last:** 2026-04-20 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `bindFullscreenCommandOverride` — _(unfilled)_
- `bindSaveFrameCommandOverride` — _(unfilled)_
- `bindSystemUiCommandOverride` — _(unfilled)_

### `PlayerContractsModule` — [com/sza/fastmediasorter/di/PlayerContractsModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/PlayerContractsModule.kt)

**Layer:** di · **LOC:** 30 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `bindStereoDetectionFacade` — _(unfilled)_
- `bindPlayerEntryCoordinator` — _(unfilled)_

### `TransferModule` — [com/sza/fastmediasorter/di/TransferModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/TransferModule.kt)

**Layer:** di · **LOC:** 80 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `bindLocalToSmbStrategy` — _(unfilled)_
- `bindSmbToLocalStrategy` — _(unfilled)_
- `bindSmbToSmbStrategy` — _(unfilled)_
- `bindLocalFileAccess` — _(unfilled)_
- `bindSmbFileAccess` — _(unfilled)_
- `bindLocalToSftpStrategy` — _(unfilled)_
- `bindSftpToLocalStrategy` — _(unfilled)_
- `bindSftpToSftpStrategy` — _(unfilled)_
- `bindSftpFileAccess` — _(unfilled)_
- `bindLocalToFtpStrategy` — _(unfilled)_
- `bindFtpToLocalStrategy` — _(unfilled)_
- `bindFtpToFtpStrategy` — _(unfilled)_
- `bindFtpFileAccess` — _(unfilled)_

### `FileHasher` — [com/sza/fastmediasorter/domain/hash/FileHasher.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/hash/FileHasher.kt)

**Layer:** domain · **LOC:** 24 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `computeHash` — _(unfilled)_

### `CommandGroup` — [com/sza/fastmediasorter/domain/input/CommandGroup.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/CommandGroup.kt)

**Layer:** domain · **LOC:** 12 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `CommandId` — [com/sza/fastmediasorter/domain/input/CommandId.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/CommandId.kt)

**Layer:** domain · **LOC:** 89 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `InputBinding` — [com/sza/fastmediasorter/domain/input/InputBinding.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/InputBinding.kt)

**Layer:** domain · **LOC:** 12 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `InputTrigger` — [com/sza/fastmediasorter/domain/input/InputTrigger.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/InputTrigger.kt)

**Layer:** domain · **LOC:** 88 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `serialize` — _(unfilled)_
- `serialize` — _(unfilled)_
- `serialize` — _(unfilled)_
- `serialize` — _(unfilled)_
- `serialize` — _(unfilled)_
- `serialize` — _(unfilled)_
- `deserialize` — _(unfilled)_
- `extractModifiers` — _(unfilled)_
- `fromKeyEvent` — _(unfilled)_
- `fromGamepadButton` — _(unfilled)_
- `fromGamepadAxis` — _(unfilled)_
- `fromXrInputEvent` — _(unfilled)_

### `DetectConflictsUseCase` — [com/sza/fastmediasorter/domain/input/usecase/DetectConflictsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/DetectConflictsUseCase.kt)

**Layer:** domain · **LOC:** 32 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `deviceOf` — _(unfilled)_

### `ResetAllUseCase` — [com/sza/fastmediasorter/domain/input/usecase/ResetAllUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/ResetAllUseCase.kt)

**Layer:** domain · **LOC:** 14 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** InputBindingRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `ResetBindingUseCase` — [com/sza/fastmediasorter/domain/input/usecase/ResetBindingUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/ResetBindingUseCase.kt)

**Layer:** domain · **LOC:** 16 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** InputBindingRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `ResetGroupUseCase` — [com/sza/fastmediasorter/domain/input/usecase/ResetGroupUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/ResetGroupUseCase.kt)

**Layer:** domain · **LOC:** 26 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** InputBindingRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `prefixForGroup` — _(unfilled)_

### `SetBindingUseCase` — [com/sza/fastmediasorter/domain/input/usecase/SetBindingUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/SetBindingUseCase.kt)

**Layer:** domain · **LOC:** 21 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** InputBindingRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `AppSettings` — [com/sza/fastmediasorter/domain/model/AppSettings.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt)

**Layer:** domain · **LOC:** 209 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getGloballyEnabledMediaTypes` — _(unfilled)_

### `AudioMetadata` — [com/sza/fastmediasorter/domain/model/AudioMetadata.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AudioMetadata.kt)

**Layer:** domain · **LOC:** 16 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `BackgroundAudioExitBehavior` — [com/sza/fastmediasorter/domain/model/BackgroundAudioExitBehavior.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/BackgroundAudioExitBehavior.kt)

**Layer:** domain · **LOC:** 17 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** —

**Role:** _(unfilled)_

### `CredentialAuditEntry` — [com/sza/fastmediasorter/domain/model/CredentialAuditEntry.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/CredentialAuditEntry.kt)

**Layer:** domain · **LOC:** 49 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `DuplicateGroup` — [com/sza/fastmediasorter/domain/model/DuplicateModels.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/DuplicateModels.kt)

**Layer:** domain · **LOC:** 26 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** —

**Role:** _(unfilled)_

### `FavoritesExportFile` — [com/sza/fastmediasorter/domain/model/FavoritesExportModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/FavoritesExportModel.kt)

**Layer:** domain · **LOC:** 68 · **Last:** 2026-03-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `FileTypeFlags` — [com/sza/fastmediasorter/domain/model/FileTypeFilter.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/FileTypeFilter.kt)

**Layer:** domain · **LOC:** 52 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `isAllFiles` — _(unfilled)_
- `hasImages` — _(unfilled)_
- `hasAudio` — _(unfilled)_
- `hasVideo` — _(unfilled)_
- `hasDocuments` — _(unfilled)_
- `fromLegacyName` — _(unfilled)_

### `GamepadAction` — [com/sza/fastmediasorter/domain/model/GamepadAction.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/GamepadAction.kt)

**Layer:** domain · **LOC:** 38 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `MediaExtensions` — [com/sza/fastmediasorter/domain/model/MediaExtensions.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/MediaExtensions.kt)

**Layer:** domain · **LOC:** 46 · **Last:** 2026-03-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `isImage` — _(unfilled)_
- `isVideo` — _(unfilled)_
- `isAudio` — _(unfilled)_
- `isText` — _(unfilled)_
- `isPdf` — _(unfilled)_
- `isEpub` — _(unfilled)_
- `getMediaType` — _(unfilled)_

### `ResourceType` — [com/sza/fastmediasorter/domain/model/Models.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt)

**Layer:** domain · **LOC:** 265 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `isBinaryFile` — _(unfilled)_
- `isEmpty` — _(unfilled)_
- `activeFilterCount` — _(unfilled)_
- `isAudioOnly` — _(unfilled)_
- `isOnlyImage` — _(unfilled)_
- `isVideoOnly` — _(unfilled)_

### `OffloadOffer` — [com/sza/fastmediasorter/domain/model/OffloadModels.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/OffloadModels.kt)

**Layer:** domain · **LOC:** 44 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `PrefetchCacheMultiplier` — [com/sza/fastmediasorter/domain/model/PrefetchCacheMultiplier.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PrefetchCacheMultiplier.kt)

**Layer:** domain · **LOC:** 24 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `fromName` — _(unfilled)_

### `Protocol` — [com/sza/fastmediasorter/domain/model/PrefetchPlan.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PrefetchPlan.kt)

**Layer:** domain · **LOC:** 71 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `ResourceConnectionStatus` — [com/sza/fastmediasorter/domain/model/ResourceConnectionTestResult.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceConnectionTestResult.kt)

**Layer:** domain · **LOC:** 15 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `ResourceEditorMode` — [com/sza/fastmediasorter/domain/model/ResourceEditorMode.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceEditorMode.kt)

**Layer:** domain · **LOC:** 7 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `ResourceFormData` — [com/sza/fastmediasorter/domain/model/ResourceFormData.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceFormData.kt)

**Layer:** domain · **LOC:** 79 · **Last:** 2026-02-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `applyProfile` — _(unfilled)_

### `ResourceFieldKey` — [com/sza/fastmediasorter/domain/model/ResourceValidationResult.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceValidationResult.kt)

**Layer:** domain · **LOC:** 66 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `valid` — _(unfilled)_
- `invalid` — _(unfilled)_

### `ResourceVerificationStatus` — [com/sza/fastmediasorter/domain/model/ResourceVerificationStatus.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceVerificationStatus.kt)

**Layer:** domain · **LOC:** 7 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `ScreenType` — [com/sza/fastmediasorter/domain/model/ResumeState.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResumeState.kt)

**Layer:** domain · **LOC:** 26 · **Last:** 2026-03-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `ScheduledOperation` — [com/sza/fastmediasorter/domain/model/ScheduledOperation.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScheduledOperation.kt)

**Layer:** domain · **LOC:** 22 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `ScheduledOpType` — [com/sza/fastmediasorter/domain/model/ScheduledOpType.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScheduledOpType.kt)

**Layer:** domain · **LOC:** 8 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `StereoMode` — [com/sza/fastmediasorter/domain/model/StereoMode.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StereoMode.kt)

**Layer:** domain · **LOC:** 159 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `isSpherical` — _(unfilled)_
- `isStereoscopic` — _(unfilled)_
- `is180Only` — _(unfilled)_
- `fromKey` — _(unfilled)_

### `StreamingCacheCleanupMode` — [com/sza/fastmediasorter/domain/model/StreamingCacheCleanupMode.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StreamingCacheCleanupMode.kt)

**Layer:** domain · **LOC:** 23 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `fromName` — _(unfilled)_

### `TimeFilter` — [com/sza/fastmediasorter/domain/model/TimeFilter.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/TimeFilter.kt)

**Layer:** domain · **LOC:** 9 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `WearNetworkSourcePayload` — [com/sza/fastmediasorter/domain/model/WearSyncPayload.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSyncPayload.kt)

**Layer:** domain · **LOC:** 31 · **Last:** 2026-04-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `TranslationFontSize` — [com/sza/fastmediasorter/domain/models/TranslationFontSize.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/models/TranslationFontSize.kt)

**Layer:** domain · **LOC:** 44 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `fromMultiplier` — _(unfilled)_
- `fromTypefaceName` — _(unfilled)_

### `PlaybackCompletionDetector` — [com/sza/fastmediasorter/domain/playback/PlaybackCompletionDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/playback/PlaybackCompletionDetector.kt)

**Layer:** domain · **LOC:** 31 · **Last:** — · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `nearEndThresholdMs` — _(unfilled)_
- `isNearEnd` — _(unfilled)_

### `PrefetchFormula` — [com/sza/fastmediasorter/domain/playback/PrefetchFormula.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/playback/PrefetchFormula.kt)

**Layer:** domain · **LOC:** 219 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `compute` — _(unfilled)_
- `classifyViability` — _(unfilled)_
- `ratioOf` — _(unfilled)_
- `bytesToSeconds` — _(unfilled)_
- `protocolSafetyMargin` — _(unfilled)_
- `baselineMbps` — _(unfilled)_
- `baselineRatio` — _(unfilled)_
- `baselineBitrateKbps` — _(unfilled)_
- `label` — _(unfilled)_

### `DuplicateHashRepository` — [com/sza/fastmediasorter/domain/repository/DuplicateHashRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/DuplicateHashRepository.kt)

**Layer:** domain · **LOC:** 29 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getCachedQuickHash` — _(unfilled)_
- `getCachedFullHash` — _(unfilled)_
- `saveQuickHash` — _(unfilled)_
- `saveFullHash` — _(unfilled)_
- `deleteByResourceId` — _(unfilled)_
- `deleteHashEntry` — _(unfilled)_

### `FavoritesRepository` — [com/sza/fastmediasorter/domain/repository/FavoritesRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/FavoritesRepository.kt)

**Layer:** domain · **LOC:** 15 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getAllFavorites` — _(unfilled)_
- `isFavorite` — _(unfilled)_
- `isFavoriteSync` — _(unfilled)_
- `getFavoritesForPaths` — _(unfilled)_
- `addFavorite` — _(unfilled)_
- `removeFavorite` — _(unfilled)_
- `removeFavoriteById` — _(unfilled)_

### `MediaStoreRepository` — [com/sza/fastmediasorter/domain/repository/MediaStoreRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/MediaStoreRepository.kt)

**Layer:** domain · **LOC:** 61 · **Last:** 2026-03-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getFoldersWithMedia` — _(unfilled)_
- `getFilesInFolder` — _(unfilled)_
- `getRecentFiles` — _(unfilled)_
- `getAllFilesByTypes` — _(unfilled)_
- `countAllFilesByTypes` — _(unfilled)_
- `getStandardFolders` — _(unfilled)_
- `findCameraFolderPath` — _(unfilled)_

### `NetworkCredentialsRepository` — [com/sza/fastmediasorter/domain/repository/NetworkCredentialsRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/NetworkCredentialsRepository.kt)

**Layer:** domain · **LOC:** 22 · **Last:** 2026-02-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `insert` — _(unfilled)_
- `getById` — _(unfilled)_
- `getByCredentialId` — _(unfilled)_
- `getByTypeServerAndPort` — _(unfilled)_
- `getByServerAndShare` — _(unfilled)_
- `getCredentialsByHost` — _(unfilled)_
- `getByTypeAndAccountId` — _(unfilled)_
- `update` — _(unfilled)_
- `delete` — _(unfilled)_
- `getAllCredentials` — _(unfilled)_
- `getOrphanedCredentials` — _(unfilled)_

### `PlaybackPositionRepository` — [com/sza/fastmediasorter/domain/repository/PlaybackPositionRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PlaybackPositionRepository.kt)

**Layer:** domain · **LOC:** 47 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getPosition` — _(unfilled)_
- `savePosition` — _(unfilled)_
- `markAsCompleted` — _(unfilled)_
- `deletePosition` — _(unfilled)_
- `markPlaybackCompleted` — _(unfilled)_
- `cleanupOldPositions` — _(unfilled)_
- `deleteAllPositions` — _(unfilled)_

### `ResourceRepository` — [com/sza/fastmediasorter/domain/repository/ResourceRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ResourceRepository.kt)

**Layer:** domain · **LOC:** 63 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getAllResources` — _(unfilled)_
- `getAllResourcesSync` — _(unfilled)_
- `getResourceById` — _(unfilled)_
- `getResourcesByType` — _(unfilled)_
- `getDestinations` — _(unfilled)_
- `getFilteredResources` — _(unfilled)_
- `addResource` — _(unfilled)_
- `updateResource` — _(unfilled)_
- `swapResourceDisplayOrders` — _(unfilled)_
- `updateResourcesDisplayOrder` — _(unfilled)_
- `deleteResource` — _(unfilled)_
- `deleteAllResources` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `updateIcon` — _(unfilled)_

### `ResumeStateRepository` — [com/sza/fastmediasorter/domain/repository/ResumeStateRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ResumeStateRepository.kt)

**Layer:** domain · **LOC:** 14 · **Last:** 2026-03-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `saveState` — _(unfilled)_
- `getState` — _(unfilled)_
- `clearState` — _(unfilled)_

### `ScheduledOperationRepository` — [com/sza/fastmediasorter/domain/repository/ScheduledOperationRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ScheduledOperationRepository.kt)

**Layer:** domain · **LOC:** 16 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getAll` — _(unfilled)_
- `getAllEnabled` — _(unfilled)_
- `getById` — _(unfilled)_
- `upsert` — _(unfilled)_
- `update` — _(unfilled)_
- `deleteById` — _(unfilled)_
- `deleteAll` — _(unfilled)_
- `getCount` — _(unfilled)_

### `SettingsRepository` — [com/sza/fastmediasorter/domain/repository/SettingsRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/SettingsRepository.kt)

**Layer:** domain · **LOC:** 23 · **Last:** 2026-03-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getSettings` — _(unfilled)_
- `updateSettings` — _(unfilled)_
- `resetToDefaults` — _(unfilled)_
- `setPlayerFirstRun` — _(unfilled)_
- `isPlayerFirstRun` — _(unfilled)_
- `saveLastUsedResourceId` — _(unfilled)_
- `getLastUsedResourceId` — _(unfilled)_
- `setResourceGridMode` — _(unfilled)_
- `isTouchZoneHintShown` — _(unfilled)_
- `setTouchZoneHintShown` — _(unfilled)_
- `resetAllTouchZoneHints` — _(unfilled)_

### `StreamingCacheRepository` — [com/sza/fastmediasorter/domain/repository/StreamingCacheRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/StreamingCacheRepository.kt)

**Layer:** domain · **LOC:** 61 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `resolveHash` — _(unfilled)_
- `findByHash` — _(unfilled)_
- `findByOriginalUri` — _(unfilled)_
- `record` — _(unfilled)_
- `touchPlayed` — _(unfilled)_
- `delete` — _(unfilled)_
- `verifyAndPrune` — _(unfilled)_
- `findStaleByTtl` — _(unfilled)_
- `totalSizeBytes` — _(unfilled)_
- `getAll` — _(unfilled)_
- `observeAll` — _(unfilled)_
- `clearAll` — _(unfilled)_

### `ThumbnailCacheRepository` — [com/sza/fastmediasorter/domain/repository/ThumbnailCacheRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ThumbnailCacheRepository.kt)

**Layer:** domain · **LOC:** 59 · **Last:** 2026-02-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getCachedThumbnail` — _(unfilled)_
- `saveThumbnail` — _(unfilled)_
- `deleteThumbnail` — _(unfilled)_
- `cleanupOldThumbnails` — _(unfilled)_
- `getCacheStats` — _(unfilled)_
- `enforceSizeLimit` — _(unfilled)_

### `WearableDataLayerRepository` — [com/sza/fastmediasorter/domain/repository/WearableDataLayerRepository.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/WearableDataLayerRepository.kt)

**Layer:** domain · **LOC:** 15 · **Last:** 2026-04-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getConnectedNodes` — _(unfilled)_
- `putDataItem` — _(unfilled)_
- `sendMessage` — _(unfilled)_

### `CloudResourceStrategy` — [com/sza/fastmediasorter/domain/strategy/CloudResourceStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/CloudResourceStrategy.kt)

**Layer:** domain · **LOC:** 76 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `validate` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `normalizeBeforeSave` — _(unfilled)_
- `fieldSchema` — _(unfilled)_

### `FtpResourceStrategy` — [com/sza/fastmediasorter/domain/strategy/FtpResourceStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/FtpResourceStrategy.kt)

**Layer:** domain · **LOC:** 83 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `validate` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `normalizeBeforeSave` — _(unfilled)_
- `fieldSchema` — _(unfilled)_
- `normalizeNetworkPath` — _(unfilled)_

### `LocalResourceStrategy` — [com/sza/fastmediasorter/domain/strategy/LocalResourceStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/LocalResourceStrategy.kt)

**Layer:** domain · **LOC:** 67 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `validate` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `normalizeBeforeSave` — _(unfilled)_
- `fieldSchema` — _(unfilled)_

### `ResourceFieldSchema` — [com/sza/fastmediasorter/domain/strategy/ResourceStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/ResourceStrategy.kt)

**Layer:** domain · **LOC:** 20 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `validate` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `normalizeBeforeSave` — _(unfilled)_
- `fieldSchema` — _(unfilled)_

### `SftpResourceStrategy` — [com/sza/fastmediasorter/domain/strategy/SftpResourceStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/SftpResourceStrategy.kt)

**Layer:** domain · **LOC:** 86 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `validate` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `normalizeBeforeSave` — _(unfilled)_
- `fieldSchema` — _(unfilled)_
- `normalizeNetworkPath` — _(unfilled)_

### `SmbResourceStrategy` — [com/sza/fastmediasorter/domain/strategy/SmbResourceStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/SmbResourceStrategy.kt)

**Layer:** domain · **LOC:** 81 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `validate` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `normalizeBeforeSave` — _(unfilled)_
- `fieldSchema` — _(unfilled)_

### `FileOperationError` — [com/sza/fastmediasorter/domain/transfer/FileOperationError.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/FileOperationError.kt)

**Layer:** domain · **LOC:** 72 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `formatTransferError` — _(unfilled)_
- `formatDeleteError` — _(unfilled)_
- `extractErrorMessage` — _(unfilled)_

### `FileOperationErrorHandler` — [com/sza/fastmediasorter/domain/transfer/FileOperationErrorHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/FileOperationErrorHandler.kt)

**Layer:** domain · **LOC:** 200 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `handleError` — _(unfilled)_
- `translateException` — _(unfilled)_
- `extractSmbError` — _(unfilled)_
- `buildContextMessage` — _(unfilled)_
- `isRecoverableError` — _(unfilled)_
- `getSuggestedAction` — _(unfilled)_

### `FileTransferProvider` — [com/sza/fastmediasorter/domain/transfer/FileTransferProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/FileTransferProvider.kt)

**Layer:** domain · **LOC:** 122 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `downloadFile` — _(unfilled)_
- `uploadFile` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `renameFile` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `exists` — _(unfilled)_
- `getFileInfo` — _(unfilled)_
- `createDirectory` — _(unfilled)_
- `isFile` — _(unfilled)_

### `ProgressTracker` — [com/sza/fastmediasorter/domain/transfer/ProgressTracker.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/ProgressTracker.kt)

**Layer:** domain · **LOC:** 143 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `reportProgress` — _(unfilled)_
- `reportProgressBytes` — _(unfilled)_
- `clearOperation` — _(unfilled)_
- `clearAll` — _(unfilled)_
- `getTrackedOperationCount` — _(unfilled)_
- `generateOperationId` — _(unfilled)_

### `TempFileManager` — [com/sza/fastmediasorter/domain/transfer/TempFileManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/TempFileManager.kt)

**Layer:** domain · **LOC:** 179 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `createTempFile` — _(unfilled)_
- `createTempFileFromName` — _(unfilled)_
- `cleanupTempFile` — _(unfilled)_
- `cleanupAllTempFiles` — _(unfilled)_
- `cleanupOldTempFiles` — _(unfilled)_
- `getTotalTempFileSize` — _(unfilled)_
- `getActiveTempFileCount` — _(unfilled)_
- `hasAvailableSpace` — _(unfilled)_

### `AddResourceAsDestinationUseCase` — [com/sza/fastmediasorter/domain/usecase/AddResourceAsDestinationUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/AddResourceAsDestinationUseCase.kt)

**Layer:** domain · **LOC:** 48 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** GetDestinationsUseCase, UpdateResourceUseCase  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `AddMultipleResult` — [com/sza/fastmediasorter/domain/usecase/AddResourceUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/AddResourceUseCase.kt)

**Layer:** domain · **LOC:** 97 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResourceRepository  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `addMultiple` — _(unfilled)_

### `AdjustImageUseCase` — [com/sza/fastmediasorter/domain/usecase/AdjustImageUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/AdjustImageUseCase.kt)

**Layer:** domain · **LOC:** 114 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_
- `applyAdjustments` — _(unfilled)_

### `AppendToScheduledLogUseCase` — [com/sza/fastmediasorter/domain/usecase/AppendToScheduledLogUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/AppendToScheduledLogUseCase.kt)

**Layer:** domain · **LOC:** 43 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `trimLog` — _(unfilled)_

### `ApplyImageFilterUseCase` — [com/sza/fastmediasorter/domain/usecase/ApplyImageFilterUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyImageFilterUseCase.kt)

**Layer:** domain · **LOC:** 124 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_
- `applyGrayscale` — _(unfilled)_
- `applySepia` — _(unfilled)_
- `applyNegative` — _(unfilled)_

### `ArchiveProgress` — [com/sza/fastmediasorter/domain/usecase/ArchiveFilesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ArchiveFilesUseCase.kt)

**Layer:** domain · **LOC:** 256 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `resolveFile` — _(unfilled)_
- `resolveContentUriName` — _(unfilled)_
- `resolveEntryName` — _(unfilled)_
- `generateUniqueFile` — _(unfilled)_
- `silentClose` — _(unfilled)_
- `deletePartialArchive` — _(unfilled)_

### `BackupPayload` — [com/sza/fastmediasorter/domain/usecase/BackupData.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt)

**Layer:** domain · **LOC:** 231 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `BackupMapper` — [com/sza/fastmediasorter/domain/usecase/BackupMapper.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt)

**Layer:** domain · **LOC:** 493 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `toBackupPayload` — _(unfilled)_
- `toBackupScheduledOperation` — _(unfilled)_
- `toScheduledOperation` — _(unfilled)_
- `toBackupSettings` — _(unfilled)_
- `toBackupResource` — _(unfilled)_
- `toAppSettings` — _(unfilled)_
- `toMediaResource` — _(unfilled)_
- `gsonSafe` — _(unfilled)_
- `gsonSafeList` — _(unfilled)_
- `safeParseSortMode` — _(unfilled)_
- `safeParseResourceType` — _(unfilled)_
- `safeParseCloudProvider` — _(unfilled)_
- `safeParseDisplayMode` — _(unfilled)_
- `safeParseMediaType` — _(unfilled)_
- `safeParseResourceProfile` — _(unfilled)_
- `toBackupFavorites` — _(unfilled)_
- `toFavoritesEntity` — _(unfilled)_

### `BackupToGoogleDriveUseCase` — [com/sza/fastmediasorter/domain/usecase/BackupToGoogleDriveUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupToGoogleDriveUseCase.kt)

**Layer:** domain · **LOC:** 276 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, SettingsRepository, ResourceRepository, GoogleDriveRestClient, FavoritesDao, ScheduledOperationRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `generateFileName` — _(unfilled)_
- `invoke` — _(unfilled)_
- `findOrCreateFolder` — _(unfilled)_
- `uploadReadme` — _(unfilled)_
- `getReadmeContent` — _(unfilled)_

### `ByteProgressCallback` — [com/sza/fastmediasorter/domain/usecase/ByteProgressCallback.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ByteProgressCallback.kt)

**Layer:** domain · **LOC:** 32 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `onProgress` — _(unfilled)_
- `onFileStarted` — _(unfilled)_

### `CalculateOptimalCacheSizeUseCase` — [com/sza/fastmediasorter/domain/usecase/CalculateOptimalCacheSizeUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CalculateOptimalCacheSizeUseCase.kt)

**Layer:** domain · **LOC:** 71 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `getAvailableInternalStorageGB` — _(unfilled)_
- `getTotalInternalStorageGB` — _(unfilled)_
- `getStorageInfo` — _(unfilled)_

### `ChangeGifSpeedUseCase` — [com/sza/fastmediasorter/domain/usecase/ChangeGifSpeedUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ChangeGifSpeedUseCase.kt)

**Layer:** domain · **LOC:** 172 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_

### `CleanupOrphanedTempFilesUseCase` — [com/sza/fastmediasorter/domain/usecase/CleanupOrphanedTempFilesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupOrphanedTempFilesUseCase.kt)

**Layer:** domain · **LOC:** 122 · **Last:** 2026-03-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** SmbFileOperationHandler, CloudFileOperationHandler  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `CleanupTrashFoldersUseCase` — [com/sza/fastmediasorter/domain/usecase/CleanupTrashFoldersUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupTrashFoldersUseCase.kt)

**Layer:** domain · **LOC:** 136 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `cleanup` — _(unfilled)_
- `cleanupTrashFoldersRecursive` — _(unfilled)_
- `deleteRecursively` — _(unfilled)_

### `CleanupTrashUseCase` — [com/sza/fastmediasorter/domain/usecase/CleanupTrashUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupTrashUseCase.kt)

**Layer:** domain · **LOC:** 103 · **Last:** 2026-02-20 · **Status:** unknown · **NoFlavors:** —

**Injected:** LocalOperationStrategy, SmbOperationStrategy, SftpOperationStrategy, FtpOperationStrategy, CloudOperationStrategy  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `deleteDirectory` — _(unfilled)_

### `ClearResumeStateUseCase` — [com/sza/fastmediasorter/domain/usecase/ClearResumeStateUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ClearResumeStateUseCase.kt)

**Layer:** domain · **LOC:** 13 · **Last:** 2026-03-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResumeStateRepository  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `ClearScheduledOperationsLogUseCase` — [com/sza/fastmediasorter/domain/usecase/ClearScheduledOperationsLogUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ClearScheduledOperationsLogUseCase.kt)

**Layer:** domain · **LOC:** 17 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `ClearScheduledOperationsUseCase` — [com/sza/fastmediasorter/domain/usecase/ClearScheduledOperationsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ClearScheduledOperationsUseCase.kt)

**Layer:** domain · **LOC:** 11 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** ScheduledOperationRepository  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `ComputeFileHashUseCase` — [com/sza/fastmediasorter/domain/usecase/ComputeFileHashUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ComputeFileHashUseCase.kt)

**Layer:** domain · **LOC:** 63 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** LocalFileHasher, SmbFileHasher, SftpFileHasher, FtpFileHasher, CloudFileHasher, DuplicateHashRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `compute` — _(unfilled)_

### `CreateDirectoryUseCase` — [com/sza/fastmediasorter/domain/usecase/CreateDirectoryUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CreateDirectoryUseCase.kt)

**Layer:** domain · **LOC:** 61 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** UnifiedFileOperationHandler  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `CredentialAuditor` — [com/sza/fastmediasorter/domain/usecase/CredentialAuditor.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CredentialAuditor.kt)

**Layer:** domain · **LOC:** 111 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** NetworkCredentialsRepository, UnusedCredentialPolicy  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `audit` — _(unfilled)_
- `auditAsFlow` — _(unfilled)_
- `buildReport` — _(unfilled)_
- `logReport` — _(unfilled)_

### `DeleteByFileSizeUseCase` — [com/sza/fastmediasorter/domain/usecase/DeleteByFileSizeUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeleteByFileSizeUseCase.kt)

**Layer:** domain · **LOC:** 94 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** GetMediaFilesUseCase, DeleteFilesUseCase  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `scan` — _(unfilled)_
- `execute` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `invoke` — _(unfilled)_

### `DeleteDirectoriesUseCase` — [com/sza/fastmediasorter/domain/usecase/DeleteDirectoriesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeleteDirectoriesUseCase.kt)

**Layer:** domain · **LOC:** 57 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** UnifiedFileOperationHandler  
**Side effects:** —  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `DeleteFilesUseCase` — [com/sza/fastmediasorter/domain/usecase/DeleteFilesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeleteFilesUseCase.kt)

**Layer:** domain · **LOC:** 26 · **Last:** 2026-03-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** SettingsRepository, FileOperationUseCase  
**Side effects:** disk  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `DeletePathPolicy` — [com/sza/fastmediasorter/domain/usecase/DeletePathPolicy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeletePathPolicy.kt)

**Layer:** domain · **LOC:** 21 · **Last:** 2026-03-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `canUseSoftDelete` — _(unfilled)_
- `canUseSoftDelete` — _(unfilled)_

### `DeleteResourceUseCase` — [com/sza/fastmediasorter/domain/usecase/DeleteResourceUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeleteResourceUseCase.kt)

**Layer:** domain · **LOC:** 39 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResourceRepository, ScheduledOperationRepository, WorkManagerScheduler  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `DeleteScheduledOperationUseCase` — [com/sza/fastmediasorter/domain/usecase/DeleteScheduledOperationUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DeleteScheduledOperationUseCase.kt)

**Layer:** domain · **LOC:** 11 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** ScheduledOperationRepository  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `DetectDuplicatesUseCase` — [com/sza/fastmediasorter/domain/usecase/DetectDuplicatesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DetectDuplicatesUseCase.kt)

**Layer:** domain · **LOC:** 129 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** GetMediaFilesUseCase, ComputeFileHashUseCase  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `detect` — _(unfilled)_

### `NetworkHost` — [com/sza/fastmediasorter/domain/usecase/DiscoverNetworkResourcesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DiscoverNetworkResourcesUseCase.kt)

**Layer:** domain · **LOC:** 197 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_
- `scanSubnet` — _(unfilled)_
- `probePorts` — _(unfilled)_
- `isTcpPortOpen` — _(unfilled)_
- `resolveHost` — _(unfilled)_
- `extractSubnet` — _(unfilled)_
- `getLocalIpAddress` — _(unfilled)_

### `DownloadNetworkFileUseCase` — [com/sza/fastmediasorter/domain/usecase/DownloadNetworkFileUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DownloadNetworkFileUseCase.kt)

**Layer:** domain · **LOC:** 205 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** SmbClient, SftpClient, FtpClient, NetworkCredentialsRepository, ApplicationContext, Context  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_
- `downloadSmbFile` — _(unfilled)_
- `onProgress` — _(unfilled)_
- `downloadSftpFile` — _(unfilled)_
- `onProgress` — _(unfilled)_
- `downloadFtpFile` — _(unfilled)_

### `ScheduledExecutionResult` — [com/sza/fastmediasorter/domain/usecase/ExecuteScheduledOperationUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExecuteScheduledOperationUseCase.kt)

**Layer:** domain · **LOC:** 338 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** ScheduledOperationRepository, ResourceRepository, GetMediaFilesUseCase, FileOperationUseCase, AppendToScheduledLogUseCase  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `checkTargetReachability` — _(unfilled)_
- `buildEffectiveResource` — _(unfilled)_
- `applyFilters` — _(unfilled)_
- `matchesTypeMask` — _(unfilled)_
- `matchesTimeFilter` — _(unfilled)_
- `logOp` — _(unfilled)_

### `ExportFavoritesUseCase` — [com/sza/fastmediasorter/domain/usecase/ExportFavoritesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExportFavoritesUseCase.kt)

**Layer:** domain · **LOC:** 106 · **Last:** 2026-03-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, FavoritesDao, ResourceDao  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `writeToFile` — _(unfilled)_

### `ExportSettingsUseCase` — [com/sza/fastmediasorter/domain/usecase/ExportSettingsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExportSettingsUseCase.kt)

**Layer:** domain · **LOC:** 324 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, SettingsRepository, ResourceRepository, NetworkCredentialsRepository, ScheduledOperationRepository  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `writeToDownloads` — _(unfilled)_
- `escapeXml` — _(unfilled)_

### `ExtractProgress` — [com/sza/fastmediasorter/domain/usecase/ExtractArchiveUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExtractArchiveUseCase.kt)

**Layer:** domain · **LOC:** 357 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `countEntries` — _(unfilled)_
- `withZipInputStream` — _(unfilled)_
- `openArchiveInputStream` — _(unfilled)_
- `sanitizeEntryPath` — _(unfilled)_
- `ensureDirectory` — _(unfilled)_
- `writeEntry` — _(unfilled)_
- `writeEntryLocal` — _(unfilled)_
- `writeEntrySaf` — _(unfilled)_
- `getTargetDirectoryDocument` — _(unfilled)_
- `createDirectoriesSaf` — _(unfilled)_
- `ensureInsideTarget` — _(unfilled)_
- `detectMimeType` — _(unfilled)_
- `isNoSpaceError` — _(unfilled)_
- `isCharsetRelatedError` — _(unfilled)_

### `ExifMetadata` — [com/sza/fastmediasorter/domain/usecase/ExtractExifMetadataUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExtractExifMetadataUseCase.kt)

**Layer:** domain · **LOC:** 160 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `extractFromFile` — _(unfilled)_
- `extractFromStream` — _(unfilled)_
- `extractFromUri` — _(unfilled)_
- `extractMetadata` — _(unfilled)_
- `supportsExif` — _(unfilled)_
- `supportsExif` — _(unfilled)_

### `ExtractGifFramesUseCase` — [com/sza/fastmediasorter/domain/usecase/ExtractGifFramesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExtractGifFramesUseCase.kt)

**Layer:** domain · **LOC:** 263 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_
- `isExtractionSupported` — _(unfilled)_
- `resolveOutputDirectory` — _(unfilled)_
- `extractGifFrames` — _(unfilled)_
- `extractMovieFrames` — _(unfilled)_
- `scanFiles` — _(unfilled)_
- `cleanupFiles` — _(unfilled)_

### `VideoMetadata` — [com/sza/fastmediasorter/domain/usecase/ExtractVideoMetadataUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExtractVideoMetadataUseCase.kt)

**Layer:** domain · **LOC:** 186 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `extractFromFile` — _(unfilled)_
- `extractFromUri` — _(unfilled)_
- `extractFromDataSource` — _(unfilled)_
- `extractMetadata` — _(unfilled)_
- `supportsVideoMetadata` — _(unfilled)_
- `supportsVideoMetadata` — _(unfilled)_

### `FavoritesUseCase` — [com/sza/fastmediasorter/domain/usecase/FavoritesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FavoritesUseCase.kt)

**Layer:** domain · **LOC:** 53 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** FavoritesRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getAllFavorites` — _(unfilled)_
- `isFavorite` — _(unfilled)_
- `isFavoriteSync` — _(unfilled)_
- `getFavoritesForPaths` — _(unfilled)_
- `toggleFavorite` — _(unfilled)_

### `FileOperationResultExt` — [com/sza/fastmediasorter/domain/usecase/FileOperationResultExt.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationResultExt.kt)

**Layer:** domain · **LOC:** 139 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `formatUserFriendlyMessage` — _(unfilled)_
- `extractFileNameFromError` — _(unfilled)_
- `cleanErrorMessage` — _(unfilled)_

### `FileOperation` — [com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt)

**Layer:** domain · **LOC:** 512 · **Last:** 2026-03-30 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, SmbFileOperationHandler, SftpFileOperationHandler, FtpFileOperationHandler, CloudFileOperationHandler  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `scanNewFile` — _(unfilled)_
- `executeWithProgress` — _(unfilled)_
- `onProgress` — _(unfilled)_
- `onFileStarted` — _(unfilled)_
- `executeInternal` — _(unfilled)_
- `isNetworkPath` — _(unfilled)_
- `execute` — _(unfilled)_
- `getLastOperation` — _(unfilled)_
- `clearHistory` — _(unfilled)_
- `canUndo` — _(unfilled)_
- `undo` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `exists` — _(unfilled)_

### `FlipImageUseCase` — [com/sza/fastmediasorter/domain/usecase/FlipImageUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FlipImageUseCase.kt)

**Layer:** domain · **LOC:** 146 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_

### `GetDestinationsUseCase` — [com/sza/fastmediasorter/domain/usecase/GetDestinationsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetDestinationsUseCase.kt)

**Layer:** domain · **LOC:** 70 · **Last:** 2026-04-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResourceRepository  
**Side effects:** —  
**Flags:** coroutines · tests

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `getDestinationsExcluding` — _(unfilled)_
- `getDestinationCount` — _(unfilled)_
- `isDestinationsFull` — _(unfilled)_
- `getNextAvailableOrder` — _(unfilled)_

### `SizeFilter` — [com/sza/fastmediasorter/domain/usecase/GetMediaFilesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetMediaFilesUseCase.kt)

**Layer:** domain · **LOC:** 449 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** MediaScannerFactory, FavoritesRepository, CachedFileListRepository, ScanDispatcher, CachedMediaMetadataExtractor  
**Side effects:** disk  
**Flags:** coroutines · tests

**Role:** _(unfilled)_

**Functions:**

- `scanFolder` — _(unfilled)_
- `listDirectoryContents` — _(unfilled)_
- `scanFolderPaged` — _(unfilled)_
- `getFileCount` — _(unfilled)_
- `isWritable` — _(unfilled)_
- `invoke` — _(unfilled)_
- `needsMetadataForSort` — _(unfilled)_
- `sortFiles` — _(unfilled)_
- `applyFlavorMediaTypeRestrictions` — _(unfilled)_

### `GetResourcesUseCase` — [com/sza/fastmediasorter/domain/usecase/GetResourcesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetResourcesUseCase.kt)

**Layer:** domain · **LOC:** 47 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResourceRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `getByType` — _(unfilled)_
- `getById` — _(unfilled)_
- `getFiltered` — _(unfilled)_

### `GetResumeStateUseCase` — [com/sza/fastmediasorter/domain/usecase/GetResumeStateUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetResumeStateUseCase.kt)

**Layer:** domain · **LOC:** 14 · **Last:** 2026-03-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResumeStateRepository  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `GetScheduledOperationsLogUseCase` — [com/sza/fastmediasorter/domain/usecase/GetScheduledOperationsLogUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetScheduledOperationsLogUseCase.kt)

**Layer:** domain · **LOC:** 18 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `GetScheduledOperationsUseCase` — [com/sza/fastmediasorter/domain/usecase/GetScheduledOperationsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetScheduledOperationsUseCase.kt)

**Layer:** domain · **LOC:** 13 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** ScheduledOperationRepository  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `ImportFavoritesUseCase` — [com/sza/fastmediasorter/domain/usecase/ImportFavoritesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportFavoritesUseCase.kt)

**Layer:** domain · **LOC:** 191 · **Last:** 2026-03-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, FavoritesDao, ResourceDao  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `preview` — _(unfilled)_
- `invoke` — _(unfilled)_
- `readAndParse` — _(unfilled)_
- `toEntity` — _(unfilled)_

### `ImportSettingsUseCase` — [com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt)

**Layer:** domain · **LOC:** 573 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, SettingsRepository, ResourceRepository, ScheduledOperationRepository, WorkManagerScheduler  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `getFileInputStream` — _(unfilled)_

### `LinkAutoDownloadCoordinator` — [com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt)

**Layer:** domain · **LOC:** 152 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** SettingsRepository, LinkExtractionRegistry, LinkDownloadWriter  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `handle` — _(unfilled)_
- `mapIoError` — _(unfilled)_
- `onProgress` — _(unfilled)_

### `LinkExtractionRegistry` — [com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt)

**Layer:** domain · **LOC:** 30 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** Set  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `ordered` — _(unfilled)_

### `MediaMimeWhitelist` — [com/sza/fastmediasorter/domain/usecase/link/MediaMimeWhitelist.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/MediaMimeWhitelist.kt)

**Layer:** domain · **LOC:** 68 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `isAllowed` — _(unfilled)_
- `extensionFor` — _(unfilled)_
- `mimeForExtension` — _(unfilled)_

### `UrlExtractionStrategy` — [com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt)

**Layer:** domain · **LOC:** 44 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `probe` — _(unfilled)_
- `open` — _(unfilled)_

### `LocalCopyFileOperation` — [com/sza/fastmediasorter/domain/usecase/LocalCopyFileOperation.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalCopyFileOperation.kt)

**Layer:** domain · **LOC:** 143 · **Last:** 2026-03-30 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_

### `LocalDeleteFileOperation` — [com/sza/fastmediasorter/domain/usecase/LocalDeleteFileOperation.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalDeleteFileOperation.kt)

**Layer:** domain · **LOC:** 268 · **Last:** 2026-03-30 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `isSharedStorage` — _(unfilled)_
- `deleteViaMediaStore` — _(unfilled)_
- `collectMediaStoreUris` — _(unfilled)_
- `execute` — _(unfilled)_

### `LocalMoveFileOperation` — [com/sza/fastmediasorter/domain/usecase/LocalMoveFileOperation.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalMoveFileOperation.kt)

**Layer:** domain · **LOC:** 197 · **Last:** 2026-03-30 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_

### `LocalRenameFileOperation` — [com/sza/fastmediasorter/domain/usecase/LocalRenameFileOperation.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalRenameFileOperation.kt)

**Layer:** domain · **LOC:** 69 · **Last:** 2026-03-30 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_

### `MediaScannerFactory` — [com/sza/fastmediasorter/domain/usecase/MediaScannerFactory.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MediaScannerFactory.kt)

**Layer:** domain · **LOC:** 37 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** LocalMediaScanner, SmbMediaScanner, SftpMediaScanner, FtpMediaScanner, CloudMediaScanner  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getScanner` — _(unfilled)_

### `MigrateCameraResourceUseCase` — [com/sza/fastmediasorter/domain/usecase/MigrateCameraResourceUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MigrateCameraResourceUseCase.kt)

**Layer:** domain · **LOC:** 28 · **Last:** 2026-03-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResourceRepository  
**Side effects:** —  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `NetworkImageEditUseCase` — [com/sza/fastmediasorter/domain/usecase/NetworkImageEditUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/NetworkImageEditUseCase.kt)

**Layer:** domain · **LOC:** 338 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, RotateImageUseCase, FlipImageUseCase, ApplyImageFilterUseCase, AdjustImageUseCase, SmbFileOperationHandler, SftpFileOperationHandler, FtpFileOperationHandler  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_
- `rotateImage` — _(unfilled)_
- `flipImage` — _(unfilled)_
- `applyFilter` — _(unfilled)_
- `applyAdjustments` — _(unfilled)_
- `isNetworkPath` — _(unfilled)_
- `downloadToTemp` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `uploadFromTemp` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_

### `SpeedTestResult` — [com/sza/fastmediasorter/domain/usecase/NetworkSpeedTestUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/NetworkSpeedTestUseCase.kt)

**Layer:** domain · **LOC:** 439 · **Last:** 2026-04-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, SmbClient, SftpClient, FtpClient, GoogleDriveRestClient, NetworkCredentialsRepository, ResourceRepository, SmbOperationsUseCase, IoDispatcher, CoroutineDispatcher  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `runSpeedTest` — _(unfilled)_
- `testSmbSpeed` — _(unfilled)_
- `testSftpSpeed` — _(unfilled)_
- `testFtpSpeed` — _(unfilled)_
- `testCloudSpeed` — _(unfilled)_
- `measureTime` — _(unfilled)_
- `calculateSpeed` — _(unfilled)_
- `calculateThreads` — _(unfilled)_
- `calculateBufferSize` — _(unfilled)_
- `testLocalSpeed` — _(unfilled)_
- `testSafSpeed` — _(unfilled)_
- `read` — _(unfilled)_
- `read` — _(unfilled)_
- `write` — _(unfilled)_
- `write` — _(unfilled)_
- `write` — _(unfilled)_

### `ProvisionDefaultResourcesUseCase` — [com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCase.kt)

**Layer:** domain · **LOC:** 167 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, ResourceRepository, SettingsRepository  
**Side effects:** —  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `createVirtualResource` — _(unfilled)_

### `RenameVirtualResourcesUseCase` — [com/sza/fastmediasorter/domain/usecase/RenameVirtualResourcesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RenameVirtualResourcesUseCase.kt)

**Layer:** domain · **LOC:** 54 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, ResourceRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `ResetSmbConnectionsUseCase` — [com/sza/fastmediasorter/domain/usecase/ResetSmbConnectionsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResetSmbConnectionsUseCase.kt)

**Layer:** domain · **LOC:** 21 · **Last:** 2026-02-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** SmbConnectionManager  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `ResourceEditorSaveResult` — [com/sza/fastmediasorter/domain/usecase/ResourceEditorUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResourceEditorUseCase.kt)

**Layer:** domain · **LOC:** 731 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResourceRepository, SettingsRepository, CachedFileListRepository, AddResourceUseCase, UpdateResourceUseCase, SmbOperationsUseCase, NetworkCredentialsRepository, IoDispatcher, CoroutineDispatcher  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `initialize` — _(unfilled)_
- `validate` — _(unfilled)_
- `testConnection` — _(unfilled)_
- `fieldSchema` — _(unfilled)_
- `buildPersistenceModel` — _(unfilled)_
- `save` — _(unfilled)_
- `strategyFor` — _(unfilled)_
- `persistNetworkCredentials` — _(unfilled)_
- `updateCredentialInPlace` — _(unfilled)_
- `ensureDestinationMetadata` — _(unfilled)_
- `normalizeForStrategy` — _(unfilled)_
- `buildResourcePath` — _(unfilled)_
- `launchPostSaveVerification` — _(unfilled)_
- `updateVerificationStatus` — _(unfilled)_
- `toFormData` — _(unfilled)_
- `emptyForm` — _(unfilled)_
- `getExistingResourceNames` — _(unfilled)_
- `generateUniqueCopyName` — _(unfilled)_
- `buildNameSuggestions` — _(unfilled)_
- `getExistingPathKeys` — _(unfilled)_
- `getResourceStatistics` — _(unfilled)_
- `inferSubfolderCount` — _(unfilled)_
- `normalizePath` — _(unfilled)_
- `extractParent` — _(unfilled)_

### `RestoreDeletedUseCase` — [com/sza/fastmediasorter/domain/usecase/RestoreDeletedUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RestoreDeletedUseCase.kt)

**Layer:** domain · **LOC:** 249 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, LocalOperationStrategy, SmbOperationStrategy, SftpOperationStrategy, FtpOperationStrategy, CloudOperationStrategy  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `findLatestTrashFolder` — _(unfilled)_
- `getNameFromPath` — _(unfilled)_
- `readMetadata` — _(unfilled)_
- `moveFile` — _(unfilled)_
- `deleteDirectory` — _(unfilled)_

### `RestoreFromGoogleDriveUseCase` — [com/sza/fastmediasorter/domain/usecase/RestoreFromGoogleDriveUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RestoreFromGoogleDriveUseCase.kt)

**Layer:** domain · **LOC:** 269 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, SettingsRepository, ResourceRepository, GoogleDriveRestClient, FavoritesDao, ScheduledOperationRepository, WorkManagerScheduler  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getBackupInfo` — _(unfilled)_
- `invoke` — _(unfilled)_
- `downloadAndParseBackup` — _(unfilled)_
- `isDuplicateResource` — _(unfilled)_

### `RotateImageUseCase` — [com/sza/fastmediasorter/domain/usecase/RotateImageUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RotateImageUseCase.kt)

**Layer:** domain · **LOC:** 132 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_

### `SaveGifFirstFrameUseCase` — [com/sza/fastmediasorter/domain/usecase/SaveGifFirstFrameUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveGifFirstFrameUseCase.kt)

**Layer:** domain · **LOC:** 94 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_

### `SaveResumeStateUseCase` — [com/sza/fastmediasorter/domain/usecase/SaveResumeStateUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveResumeStateUseCase.kt)

**Layer:** domain · **LOC:** 14 · **Last:** 2026-03-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResumeStateRepository  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `IncrementalScanStrategy` — [com/sza/fastmediasorter/domain/usecase/scan/IncrementalScanStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/scan/IncrementalScanStrategy.kt)

**Layer:** domain · **LOC:** 157 · **Last:** 2026-03-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `canSkipScan` — _(unfilled)_
- `currentFolderMtime` — _(unfilled)_
- `reset` — _(unfilled)_

### `ScanDeltaDetector` — [com/sza/fastmediasorter/domain/usecase/scan/ScanDeltaDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/scan/ScanDeltaDetector.kt)

**Layer:** domain · **LOC:** 122 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `compute` — _(unfilled)_
- `isModified` — _(unfilled)_
- `toString` — _(unfilled)_

### `ScanDispatcher` — [com/sza/fastmediasorter/domain/usecase/scan/ScanDispatcher.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/scan/ScanDispatcher.kt)

**Layer:** domain · **LOC:** 85 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** ScanSettings  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `withScanPermit` — _(unfilled)_
- `availablePermits` — _(unfilled)_
- `reset` — _(unfilled)_
- `semaphoreFor` — _(unfilled)_

### `ScanSettings` — [com/sza/fastmediasorter/domain/usecase/scan/ScanSettings.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/scan/ScanSettings.kt)

**Layer:** domain · **LOC:** 64 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `limitFor` — _(unfilled)_

### `ScanLocalFoldersUseCase` — [com/sza/fastmediasorter/domain/usecase/ScanLocalFoldersUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ScanLocalFoldersUseCase.kt)

**Layer:** domain · **LOC:** 256 · **Last:** 2026-03-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, ResourceRepository, SettingsRepository, MediaStoreRepository  
**Side effects:** —  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `ScanProgressCallback` — [com/sza/fastmediasorter/domain/usecase/ScanProgressCallback.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ScanProgressCallback.kt)

**Layer:** domain · **LOC:** 33 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `onProgress` — _(unfilled)_
- `onComplete` — _(unfilled)_
- `shouldStop` — _(unfilled)_
- `onMetadataErrors` — _(unfilled)_

### `ScheduleNetworkSyncUseCase` — [com/sza/fastmediasorter/domain/usecase/ScheduleNetworkSyncUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ScheduleNetworkSyncUseCase.kt)

**Layer:** domain · **LOC:** 69 · **Last:** 2026-03-24 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `cancel` — _(unfilled)_

### `SearchAudioCoverUseCase` — [com/sza/fastmediasorter/domain/usecase/SearchAudioCoverUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SearchAudioCoverUseCase.kt)

**Layer:** domain · **LOC:** 284 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, ITunesApiService, SettingsRepository, OkHttpClient  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `searchItunes` — _(unfilled)_
- `searchDeezer` — _(unfilled)_
- `searchMusicBrainz` — _(unfilled)_
- `parseArtistFromPath` — _(unfilled)_
- `isWiFiConnected` — _(unfilled)_
- `prepareSearchQuery` — _(unfilled)_

### `SearchLyricsUseCase` — [com/sza/fastmediasorter/domain/usecase/SearchLyricsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SearchLyricsUseCase.kt)

**Layer:** domain · **LOC:** 805 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, SmbClient, SftpClient, FtpClient, NetworkCredentialsRepository, UnifiedFileCache  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_
- `extractMetadataWithCache` — _(unfilled)_
- `extractMetadata` — _(unfilled)_
- `fixEncoding` — _(unfilled)_
- `getLocalFile` — _(unfilled)_
- `downloadFromSmb` — _(unfilled)_
- `downloadFromSftp` — _(unfilled)_
- `downloadFromFtp` — _(unfilled)_
- `buildSearchQueries` — _(unfilled)_
- `parseFilename` — _(unfilled)_
- `parseArtistFromPath` — _(unfilled)_
- `hasCyrillic` — _(unfilled)_
- `normalizeText` — _(unfilled)_
- `searchLyricsOnline` — _(unfilled)_
- `searchMusixmatch` — _(unfilled)_
- `searchGeniusApi` — _(unfilled)_
- `fetchGeniusLyrics` — _(unfilled)_
- `searchAZLyrics` — _(unfilled)_

### `SearchQueryUtils` — [com/sza/fastmediasorter/domain/usecase/SearchQueryUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SearchQueryUtils.kt)

**Layer:** domain · **LOC:** 78 · **Last:** 2026-03-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `prepareSearchQuery` — _(unfilled)_
- `isPlaceholderValue` — _(unfilled)_
- `filterPlaceholder` — _(unfilled)_
- `cleanForSearch` — _(unfilled)_

### `SendResult` — [com/sza/fastmediasorter/domain/usecase/SendResourcesToWatchUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SendResourcesToWatchUseCase.kt)

**Layer:** domain · **LOC:** 87 · **Last:** 2026-04-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResourceRepository, NetworkCredentialsRepository, WearableDataLayerRepository  
**Side effects:** —  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `SmbOperationsUseCase` — [com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt)

**Layer:** domain · **LOC:** 739 · **Last:** 2026-04-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** SmbClient, SftpClient, FtpClient, NetworkCredentialsRepository, IoDispatcher, CoroutineDispatcher  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `testConnection` — _(unfilled)_
- `listShares` — _(unfilled)_
- `scanMediaFiles` — _(unfilled)_
- `saveCredentials` — _(unfilled)_
- `getConnectionInfo` — _(unfilled)_
- `listFiles` — _(unfilled)_
- `detectMediaType` — _(unfilled)_
- `testSftpConnection` — _(unfilled)_
- `saveSftpCredentials` — _(unfilled)_
- `testFtpConnection` — _(unfilled)_
- `saveFtpCredentials` — _(unfilled)_
- `getSftpCredentials` — _(unfilled)_
- `getFtpCredentials` — _(unfilled)_
- `listSftpFiles` — _(unfilled)_
- `listSftpFilesWithCredentials` — _(unfilled)_
- `checkTrashFolders` — _(unfilled)_
- `cleanupTrash` — _(unfilled)_
- `clearAllConnectionPools` — _(unfilled)_

### `StreamOffloadUseCase` — [com/sza/fastmediasorter/domain/usecase/StreamOffloadUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/StreamOffloadUseCase.kt)

**Layer:** domain · **LOC:** 243 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, SmbToLocalStrategy, SftpToLocalStrategy, FtpToLocalStrategy, StreamingCacheRepository  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `run` — _(unfilled)_
- `onProgress` — _(unfilled)_
- `hasFreeSpace` — _(unfilled)_
- `resolveDestinationFile` — _(unfilled)_
- `estimateDownloadSec` — _(unfilled)_
- `dispatchDownload` — _(unfilled)_
- `externalStreamingRoot` — _(unfilled)_
- `cleanupPartial` — _(unfilled)_
- `sanitizeFilename` — _(unfilled)_

### `SyncMediaStoreUseCase` — [com/sza/fastmediasorter/domain/usecase/SyncMediaStoreUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SyncMediaStoreUseCase.kt)

**Layer:** domain · **LOC:** 132 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_
- `collectFiles` — _(unfilled)_

### `SyncNetworkResourcesUseCase` — [com/sza/fastmediasorter/domain/usecase/SyncNetworkResourcesUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SyncNetworkResourcesUseCase.kt)

**Layer:** domain · **LOC:** 149 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResourceRepository, SettingsRepository, MediaScannerFactory  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `syncAll` — _(unfilled)_
- `syncSingle` — _(unfilled)_

### `TestCredentialsLoader` — [com/sza/fastmediasorter/domain/usecase/TestCredentialsLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/TestCredentialsLoader.kt)

**Layer:** domain · **LOC:** 191 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `loadCredentials` — _(unfilled)_
- `tryLoadFromExternalStorage` — _(unfilled)_
- `tryLoadFromAssets` — _(unfilled)_
- `parseCredentials` — _(unfilled)_
- `getCredential` — _(unfilled)_
- `getCloudCredential` — _(unfilled)_
- `getAvailableCloudProviders` — _(unfilled)_

### `UnusedCredentialPolicy` — [com/sza/fastmediasorter/domain/usecase/UnusedCredentialPolicy.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/UnusedCredentialPolicy.kt)

**Layer:** domain · **LOC:** 63 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `isEligibleForCleanup` — _(unfilled)_
- `filterEligible` — _(unfilled)_

### `UpdateResourceUseCase` — [com/sza/fastmediasorter/domain/usecase/UpdateResourceUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/UpdateResourceUseCase.kt)

**Layer:** domain · **LOC:** 29 · **Last:** 2026-02-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResourceRepository  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `UpdateScheduledOperationUseCase` — [com/sza/fastmediasorter/domain/usecase/UpdateScheduledOperationUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/UpdateScheduledOperationUseCase.kt)

**Layer:** domain · **LOC:** 13 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** ScheduledOperationRepository  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `UpsertScheduledOperationUseCase` — [com/sza/fastmediasorter/domain/usecase/UpsertScheduledOperationUseCase.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/UpsertScheduledOperationUseCase.kt)

**Layer:** domain · **LOC:** 13 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** ScheduledOperationRepository  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `invoke` — _(unfilled)_

### `VirtualResourceDefaultNames` — [com/sza/fastmediasorter/domain/usecase/VirtualResourceDefaultNames.kt](app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/VirtualResourceDefaultNames.kt)

**Layer:** domain · **LOC:** 49 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `FastMediaSorterApp` — [com/sza/fastmediasorter/FastMediaSorterApp.kt](app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt)

**Layer:** other · **LOC:** 499 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `onStop` — _(unfilled)_
- `onStart` — _(unfilled)_
- `setupDebugStrictMode` — _(unfilled)_
- `setupSmbAutoReset` — _(unfilled)_
- `onAutoReset` — _(unfilled)_
- `onAppBackgrounded` — _(unfilled)_
- `onAppForegrounded` — _(unfilled)_
- `attachBaseContext` — _(unfilled)_
- `onTrimMemory` — _(unfilled)_
- `logAppStartupInfo` — _(unfilled)_
- `logSettingsInfo` — _(unfilled)_

### `PhoneWearListenerService` — [com/sza/fastmediasorter/service/PhoneWearListenerService.kt](app_v2/src/main/java/com/sza/fastmediasorter/service/PhoneWearListenerService.kt)

**Layer:** service · **LOC:** 74 · **Last:** 2026-04-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `onMessageReceived` — _(unfilled)_
- `handleSyncRequest` — _(unfilled)_
- `handleAck` — _(unfilled)_
- `onDestroy` — _(unfilled)_

### `BaseActivity` — [com/sza/fastmediasorter/core/ui/BaseActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt)

**Layer:** ui · **LOC:** 151 · **Last:** 2026-03-24 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `getViewBinding` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `observeData` — _(unfilled)_
- `attachBaseContext` — _(unfilled)_
- `shouldEnableEdgeToEdge` — _(unfilled)_
- `onCreate` — _(unfilled)_
- `onResume` — _(unfilled)_
- `onDestroy` — _(unfilled)_
- `shouldKeepScreenAwake` — _(unfilled)_
- `onLayoutConfigurationChanged` — _(unfilled)_
- `onConfigurationChanged` — _(unfilled)_
- `dispatchTouchEvent` — _(unfilled)_
- `applyKeepScreenAwake` — _(unfilled)_

### `BaseFragment` — [com/sza/fastmediasorter/core/ui/BaseFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseFragment.kt)

**Layer:** ui · **LOC:** 66 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `getViewBinding` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `safeShowDialog` — _(unfilled)_

### `BaseViewModel` — [com/sza/fastmediasorter/core/ui/BaseViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseViewModel.kt)

**Layer:** ui · **LOC:** 73 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getInitialState` — _(unfilled)_
- `updateState` — _(unfilled)_
- `sendEvent` — _(unfilled)_
- `setLoading` — _(unfilled)_
- `setError` — _(unfilled)_
- `handleError` — _(unfilled)_
- `clearError` — _(unfilled)_

### `UiEvent` — [com/sza/fastmediasorter/core/ui/UiEvent.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/ui/UiEvent.kt)

**Layer:** ui · **LOC:** 57 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

### `AddResourceActivity` — [com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt)

**Layer:** ui · **LOC:** 419 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `navigateBack` — _(unfilled)_
- `showHelp` — _(unfilled)_
- `activateFocused` — _(unfilled)_
- `moveFocus` — _(unfilled)_
- `getViewBinding` — _(unfilled)_
- `onSaveInstanceState` — _(unfilled)_
- `onCreate` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onResume` — _(unfilled)_
- `showLocalFolderOptions` — _(unfilled)_
- `showSmbFolderOptions` — _(unfilled)_
- `showSftpFolderOptions` — _(unfilled)_
- `showCloudStorageOptions` — _(unfilled)_
- `onActivityResult` — _(unfilled)_
- `onKeyDown` — _(unfilled)_
- `createIntent` — _(unfilled)_

### `AddResourceBridge` — [com/sza/fastmediasorter/ui/addresource/AddResourceBridge.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceBridge.kt)

**Layer:** ui · **LOC:** 27 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `mutate` — _(unfilled)_
- `emit` — _(unfilled)_
- `markLoading` — _(unfilled)_
- `reportError` — _(unfilled)_
- `supportedMediaTypes` — _(unfilled)_
- `runSpeedTest` — _(unfilled)_

### `AddResourceConnectionManager` — [com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt)

**Layer:** ui · **LOC:** 410 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `observeAuthEvents` — _(unfilled)_
- `handleResume` — _(unfilled)_
- `updateCloudStorageStatus` — _(unfilled)_
- `startGoogleDriveAuth` — _(unfilled)_
- `handleGoogleSignInResult` — _(unfilled)_
- `showGoogleDriveSignedInOptions` — _(unfilled)_
- `signOutGoogleDrive` — _(unfilled)_
- `navigateToGoogleDriveFolderPicker` — _(unfilled)_
- `startDropboxAuth` — _(unfilled)_
- `showDropboxSignedInOptions` — _(unfilled)_
- `signOutDropbox` — _(unfilled)_
- `navigateToDropboxFolderPicker` — _(unfilled)_
- `startOneDriveAuth` — _(unfilled)_
- `showOneDriveSignedInOptions` — _(unfilled)_
- `signOutOneDrive` — _(unfilled)_
- `navigateToOneDriveFolderPicker` — _(unfilled)_
- `showAccountPicker` — _(unfilled)_
- `testSmbConnection` — _(unfilled)_
- `testSftpConnection` — _(unfilled)_
- `getSelectedProtocol` — _(unfilled)_
- `showSharePickerDialog` — _(unfilled)_
- `showError` — _(unfilled)_
- `showTestResultDialog` — _(unfilled)_
- `showRememberFileListHelpDialog` — _(unfilled)_
- `showDetailedErrorDialog` — _(unfilled)_

### `AddResourceFinalizer` — [com/sza/fastmediasorter/ui/addresource/AddResourceFinalizer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFinalizer.kt)

**Layer:** ui · **LOC:** 199 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `scanInsertedResource` — _(unfilled)_
- `scanInsertedResources` — _(unfilled)_
- `allocateDestinationSlot` — _(unfilled)_

### `AddResourceFormManager` — [com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt)

**Layer:** ui · **LOC:** 413 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback

**Role:** _(unfilled)_

**Functions:**

- `applyEdgeToEdgeInsets` — _(unfilled)_
- `updateResourceTypeGridColumns` — _(unfilled)_
- `applyFlavorRestrictions` — _(unfilled)_
- `setupCheckboxInteractions` — _(unfilled)_
- `updateMediaTypeCheckboxes` — _(unfilled)_
- `setupIpAddressField` — _(unfilled)_
- `setupCollapsibleSections` — _(unfilled)_
- `sectionKey` — _(unfilled)_
- `setupHeader` — _(unfilled)_
- `initSmbMediaTypes` — _(unfilled)_
- `initSftpMediaTypes` — _(unfilled)_
- `applyMediaTypeCheckboxes` — _(unfilled)_
- `showProfilePresetDialog` — _(unfilled)_
- `getProfileLabelResId` — _(unfilled)_
- `applyProfilePresetToSmb` — _(unfilled)_
- `applyProfilePresetToSftp` — _(unfilled)_
- `addSmbResourceManually` — _(unfilled)_
- `addSftpResource` — _(unfilled)_
- `getSelectedProtocol` — _(unfilled)_
- `getSmbSupportedTypes` — _(unfilled)_
- `getSftpSupportedTypes` — _(unfilled)_

### `AddResourceHelper` — [com/sza/fastmediasorter/ui/addresource/AddResourceHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceHelper.kt)

**Layer:** ui · **LOC:** 243 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `preFillResourceData` — _(unfilled)_

### `AddResourceKeyboardDelegate` — [com/sza/fastmediasorter/ui/addresource/AddResourceKeyboardDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceKeyboardDelegate.kt)

**Layer:** ui · **LOC:** 42 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `navigateBack` — _(unfilled)_
- `showHelp` — _(unfilled)_
- `activateFocused` — _(unfilled)_
- `moveFocus` — _(unfilled)_
- `handleKeyDown` — _(unfilled)_
- `dispatchAction` — _(unfilled)_

### `AddResourceNetworkScanCoordinator` — [com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt)

**Layer:** ui · **LOC:** 94 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `scanNetwork` — _(unfilled)_
- `stopScan` — _(unfilled)_
- `scanShares` — _(unfilled)_

### `AddResourceScanManager` — [com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt)

**Layer:** ui · **LOC:** 277 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `loadSshKeyFromFile` — _(unfilled)_
- `handleSelectedFolderUri` — _(unfilled)_
- `showFolderSelectionDialog` — _(unfilled)_
- `applyVirtualButtonStates` — _(unfilled)_
- `selectFolderByPath` — _(unfilled)_
- `showFolderBrowserDialog` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `getItemCount` — _(unfilled)_
- `loadFolders` — _(unfilled)_
- `showAllFilesAccessPermissionDialog` — _(unfilled)_

### `AddResourceSftpFtpCoordinator` — [com/sza/fastmediasorter/ui/addresource/AddResourceSftpFtpCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSftpFtpCoordinator.kt)

**Layer:** ui · **LOC:** 287 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `testSftpFtpConnection` — _(unfilled)_
- `testSftpConnection` — _(unfilled)_
- `addSftpFtpResource` — _(unfilled)_
- `addSftpResource` — _(unfilled)_

### `AddResourceSftpKeyCoordinator` — [com/sza/fastmediasorter/ui/addresource/AddResourceSftpKeyCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSftpKeyCoordinator.kt)

**Layer:** ui · **LOC:** 174 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `testSftpConnectionWithKey` — _(unfilled)_
- `addSftpResourceWithKey` — _(unfilled)_

### `AddResourceSmbCoordinator` — [com/sza/fastmediasorter/ui/addresource/AddResourceSmbCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSmbCoordinator.kt)

**Layer:** ui · **LOC:** 317 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `testSmbConnection` — _(unfilled)_
- `scanSmbShares` — _(unfilled)_
- `addSmbResources` — _(unfilled)_
- `addSmbResourceManually` — _(unfilled)_

### `AddResourceState` — [com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt)

**Layer:** ui · **LOC:** 555 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, ScanLocalFoldersUseCase, AddResourceUseCase, MediaScannerFactory, SmbOperationsUseCase, DiscoverNetworkResourcesUseCase, NetworkCredentialsRepository, SettingsRepository, ResourceRepository, NetworkSpeedTestUseCase, ApplicationScope, CoroutineScope, IoDispatcher, CoroutineDispatcher  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `getInitialState` — _(unfilled)_
- `getSupportedMediaTypes` — _(unfilled)_
- `getSettings` — _(unfilled)_
- `loadResourceForCopy` — _(unfilled)_
- `loadCloudAccounts` — _(unfilled)_
- `scanLocalFolders` — _(unfilled)_
- `addVirtualResource` — _(unfilled)_
- `getExistingVirtualPaths` — _(unfilled)_
- `addManualFolder` — _(unfilled)_
- `addManualFolder` — _(unfilled)_
- `scanNetwork` — _(unfilled)_
- `stopScan` — _(unfilled)_
- `scanShares` — _(unfilled)_
- `toggleResourceSelection` — _(unfilled)_
- `updateResourceName` — _(unfilled)_
- `toggleDestination` — _(unfilled)_
- `toggleScanSubdirectories` — _(unfilled)_
- `toggleReadOnlyMode` — _(unfilled)_
- `toggleMediaType` — _(unfilled)_
- `toggleAllFiles` — _(unfilled)_
- `addSelectedResources` — _(unfilled)_
- `addManualResource` — _(unfilled)_
- `testSmbConnection` — _(unfilled)_
- `scanSmbShares` — _(unfilled)_
- `addSmbResources` — _(unfilled)_
- `addSmbResourceManually` — _(unfilled)_
- `testSftpFtpConnection` — _(unfilled)_
- `testSftpConnection` — _(unfilled)_
- `addSftpFtpResource` — _(unfilled)_
- `addSftpResource` — _(unfilled)_
- `testSftpConnectionWithKey` — _(unfilled)_
- `addSftpResourceWithKey` — _(unfilled)_
- `triggerSpeedTest` — _(unfilled)_
- `mutate` — _(unfilled)_
- `emit` — _(unfilled)_
- `markLoading` — _(unfilled)_
- `reportError` — _(unfilled)_
- `supportedMediaTypes` — _(unfilled)_
- `runSpeedTest` — _(unfilled)_

### `AddResourceVirtualCoordinator` — [com/sza/fastmediasorter/ui/addresource/AddResourceVirtualCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceVirtualCoordinator.kt)

**Layer:** ui · **LOC:** 292 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `scanLocalFolders` — _(unfilled)_
- `addVirtualResource` — _(unfilled)_
- `getExistingVirtualPaths` — _(unfilled)_
- `addManualFolder` — _(unfilled)_
- `buildVirtualResource` — _(unfilled)_
- `suggestLocalResourceName` — _(unfilled)_

### `NetworkDiscoveryDialog` — [com/sza/fastmediasorter/ui/addresource/NetworkDiscoveryDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/NetworkDiscoveryDialog.kt)

**Layer:** ui · **LOC:** 167 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

**Functions:**

- `onCreateDialog` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onStart` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `newInstance` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `bind` — _(unfilled)_
- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_

### `ResourceToAddAdapter` — [com/sza/fastmediasorter/ui/addresource/ResourceToAddAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/ResourceToAddAdapter.kt)

**Layer:** ui · **LOC:** 186 · **Last:** 2026-03-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `setSelectedPaths` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `bind` — _(unfilled)_
- `setupMediaTypeButton` — _(unfilled)_
- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_

### `IpAddressEditText` — [com/sza/fastmediasorter/ui/addresource/widgets/IpAddressEditText.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/widgets/IpAddressEditText.kt)

**Layer:** ui · **LOC:** 168 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `setValidationEnabled` — _(unfilled)_
- `validateAndHighlight` — _(unfilled)_
- `isValidIpOrHostname` — _(unfilled)_
- `isValidIpv4` — _(unfilled)_
- `isValidHostname` — _(unfilled)_
- `applyErrorHighlight` — _(unfilled)_
- `clearValidationHighlight` — _(unfilled)_
- `isValid` — _(unfilled)_

### `NetworkPathEditText` — [com/sza/fastmediasorter/ui/addresource/widgets/NetworkPathEditText.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/widgets/NetworkPathEditText.kt)

**Layer:** ui · **LOC:** 195 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `setAutoCorrectEnabled` — _(unfilled)_
- `autoCorrectPath` — _(unfilled)_
- `removeDuplicateSlashes` — _(unfilled)_
- `getNormalizedPath` — _(unfilled)_
- `filter` — _(unfilled)_

### `AdapterDragController` — [com/sza/fastmediasorter/ui/browse/AdapterDragController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterDragController.kt)

**Layer:** ui · **LOC:** 57 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onStartDrag` — _(unfilled)_
- `setShowHandles` — _(unfilled)_
- `moveItem` — _(unfilled)_
- `syncWithCurrentList` — _(unfilled)_
- `getOrderedPaths` — _(unfilled)_

### `AdapterFileInfoFormatter` — [com/sza/fastmediasorter/ui/browse/AdapterFileInfoFormatter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterFileInfoFormatter.kt)

**Layer:** ui · **LOC:** 99 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `buildAudioDisplayName` — _(unfilled)_
- `buildAudioDetailLine` — _(unfilled)_
- `buildFileInfo` — _(unfilled)_
- `buildLegacyFileInfo` — _(unfilled)_
- `formatDuration` — _(unfilled)_

### `AdapterThumbnailLoader` — [com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt)

**Layer:** ui · **LOC:** 626 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `load` — _(unfilled)_
- `applyPlaceholderStyle` — _(unfilled)_
- `resetThumbnailStyle` — _(unfilled)_
- `createExtensionBitmap` — _(unfilled)_
- `createPlaceholderDrawable` — _(unfilled)_
- `showGeneratedPlaceholder` — _(unfilled)_
- `getPlaceholderExtension` — _(unfilled)_
- `checkFileExists` — _(unfilled)_
- `detectCloudProvider` — _(unfilled)_
- `extractCloudFileId` — _(unfilled)_
- `loadEpub` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `onResourceReady` — _(unfilled)_
- `loadPdf` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `onResourceReady` — _(unfilled)_
- `loadImage` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `onResourceReady` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `onResourceReady` — _(unfilled)_
- `loadVideo` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `onResourceReady` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `onResourceReady` — _(unfilled)_
- `isVideoDecoderException` — _(unfilled)_

### `BrowseActivity` — [com/sza/fastmediasorter/ui/browse/BrowseActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt)

**Layer:** ui · **LOC:** 393 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `handleGoogleSignInResult` — _(unfilled)_
- `onPlayerActivityReturned` — _(unfilled)_
- `onEditResourceReturned` — _(unfilled)_
- `onDeletePermissionGranted` — _(unfilled)_
- `onPermissionDenied` — _(unfilled)_
- `clearPendingMoveOperation` — _(unfilled)_
- `onFolderPicked` — _(unfilled)_
- `getViewBinding` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `handleOnBackPressed` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onLayoutConfigurationChanged` — _(unfilled)_
- `onKeyDown` — _(unfilled)_
- `dispatchKeyEvent` — _(unfilled)_
- `routeBrowserGamepadAction` — _(unfilled)_
- `onGenericMotionEvent` — _(unfilled)_
- `onResume` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onStop` — _(unfilled)_
- `onDestroy` — _(unfilled)_
- `onSaveInstanceState` — _(unfilled)_
- `onCameraCaptureClicked` — _(unfilled)_
- `onCapturedFileSaved` — _(unfilled)_
- `createIntent` — _(unfilled)_

### `BrowseEvent` — [com/sza/fastmediasorter/ui/browse/BrowseEvent.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseEvent.kt)

**Layer:** ui · **LOC:** 47 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

### `BrowseState` — [com/sza/fastmediasorter/ui/browse/BrowseState.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseState.kt)

**Layer:** ui · **LOC:** 42 · **Last:** 2026-04-12 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `PlaybackStatus` — [com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt)

**Layer:** ui · **LOC:** 708 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, GetResourcesUseCase, GetMediaFilesUseCase, MediaScannerFactory, SettingsRepository, CachedFileListRepository, UpdateResourceUseCase, FileOperationUseCase, SmbOperationsUseCase, CachedMediaMetadataExtractor, IoDispatcher, CoroutineDispatcher, SavedStateHandle  
**Side effects:** network, disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `addFilesToList` — _(unfilled)_
- `reloadFileList` — _(unfilled)_
- `createMediaFileFromFile` — _(unfilled)_
- `showMessage` — _(unfilled)_
- `showUndoToast` — _(unfilled)_
- `showError` — _(unfilled)_
- `markListAsSubmitted` — _(unfilled)_
- `getInitialState` — _(unfilled)_
- `getSettings` — _(unfilled)_
- `cancelBackgroundThumbnailLoading` — _(unfilled)_
- `inlinePlayToggle` — _(unfilled)_
- `inlineStop` — _(unfilled)_
- `onCleared` — _(unfilled)_
- `launchSubfolderReload` — _(unfilled)_
- `removeFiles` — _(unfilled)_
- `addFiles` — _(unfilled)_
- `updateFile` — _(unfilled)_
- `createFolder` — _(unfilled)_
- `renameDirectory` — _(unfilled)_
- `reloadFiles` — _(unfilled)_
- `scrollToFileAfterRefresh` — _(unfilled)_
- `refreshResourceMetadata` — _(unfilled)_
- `cancelScan` — _(unfilled)_
- `navigateToFolder` — _(unfilled)_
- `navigateBack` — _(unfilled)_
- `getCurrentBreadcrumb` — _(unfilled)_
- `enableSubfolderMode` — _(unfilled)_
- `disableSubfolderMode` — _(unfilled)_
- `loadResource` — _(unfilled)_
- `loadMediaFiles` — _(unfilled)_
- `setSortMode` — _(unfilled)_
- `reshuffleRandom` — _(unfilled)_
- `toggleDisplayMode` — _(unfilled)_
- `selectFile` — _(unfilled)_
- `currentSelectedPaths` — _(unfilled)_
- `selectFileRange` — _(unfilled)_
- `clearSelection` — _(unfilled)_
- `selectAll` — _(unfilled)_
- `openFile` — _(unfilled)_
- `deleteSelectedFiles` — _(unfilled)_
- `onDeletePermissionGranted` — _(unfilled)_
- `toggleFavorite` — _(unfilled)_
- `saveUndoOperation` — _(unfilled)_
- `undoLastOperation` — _(unfilled)_
- `clearExpiredUndoOperation` — _(unfilled)_
- `setFilter` — _(unfilled)_
- `applyFilter` — _(unfilled)_
- `applyFilterToList` — _(unfilled)_
- `sortFiles` — _(unfilled)_
- `saveManualOrder` — _(unfilled)_
- `saveLastViewedFile` — _(unfilled)_
- `saveScrollPosition` — _(unfilled)_
- `clearResumeState` — _(unfilled)_
- `createMediaFileFromFile` — _(unfilled)_
- `syncWithCache` — _(unfilled)_
- `checkAndReloadIfResourceChanged` — _(unfilled)_
- `removeFilesFromList` — _(unfilled)_
- `onFileMissingFromDisk` — _(unfilled)_
- `setIgnoringFileChanges` — _(unfilled)_
- `isIgnoringFileChanges` — _(unfilled)_
- `isSubfolderModeEnabled` — _(unfilled)_
- `navigateToFolder` — _(unfilled)_
- `canNavigateUp` — _(unfilled)_
- `navigateUp` — _(unfilled)_
- `resetToRoot` — _(unfilled)_
- `getCurrentFolderName` — _(unfilled)_
- `getBreadcrumbPath` — _(unfilled)_
- `getBreadcrumbParts` — _(unfilled)_
- `navigateToDepth` — _(unfilled)_
- `deleteBySize` — _(unfilled)_
- `scanBySize` — _(unfilled)_
- `executeBySizeDeleteConfirmed` — _(unfilled)_
- `archiveSelectedFiles` — _(unfilled)_
- `cancelArchive` — _(unfilled)_
- `prepareExtraction` — _(unfilled)_
- `extractArchive` — _(unfilled)_
- `cancelExtraction` — _(unfilled)_
- `addCurrentResourceAsDestination` — _(unfilled)_

### `BrowseCacheManager` — [com/sza/fastmediasorter/ui/browse/cache/BrowseCacheManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/cache/BrowseCacheManager.kt)

**Layer:** ui · **LOC:** 163 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `checkCache` — _(unfilled)_
- `applyFilter` — _(unfilled)_

### `BrowseFileListManager` — [com/sza/fastmediasorter/ui/browse/filelist/BrowseFileListManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/filelist/BrowseFileListManager.kt)

**Layer:** ui · **LOC:** 185 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `removeFiles` — _(unfilled)_
- `addFiles` — _(unfilled)_
- `updateFile` — _(unfilled)_
- `sortFiles` — _(unfilled)_
- `syncWithCache` — _(unfilled)_

### `BrowseFileDragTouchCallback` — [com/sza/fastmediasorter/ui/browse/helpers/BrowseFileDragTouchCallback.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseFileDragTouchCallback.kt)

**Layer:** ui · **LOC:** 68 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `getMovementFlags` — _(unfilled)_
- `onMove` — _(unfilled)_
- `onSwiped` — _(unfilled)_
- `isLongPressDragEnabled` — _(unfilled)_
- `onSelectedChanged` — _(unfilled)_
- `clearView` — _(unfilled)_

### `InlinePlaybackAnimator` — [com/sza/fastmediasorter/ui/browse/InlinePlaybackAnimator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/InlinePlaybackAnimator.kt)

**Layer:** ui · **LOC:** 55 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `startNote` — _(unfilled)_
- `stopNote` — _(unfilled)_
- `startDownload` — _(unfilled)_
- `stopDownload` — _(unfilled)_
- `stopAll` — _(unfilled)_

### `BrowseLoadingManager` — [com/sza/fastmediasorter/ui/browse/loading/BrowseLoadingManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/loading/BrowseLoadingManager.kt)

**Layer:** ui · **LOC:** 295 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `updateLoadingProgress` — _(unfilled)_
- `updateState` — _(unfilled)_
- `setLoading` — _(unfilled)_
- `handleLoadingError` — _(unfilled)_
- `updateResourceMetadata` — _(unfilled)_
- `onFilesLoaded` — _(unfilled)_
- `startFileObserver` — _(unfilled)_
- `sortFiles` — _(unfilled)_
- `onScanMetadataErrors` — _(unfilled)_
- `setupPagination` — _(unfilled)_
- `loadFilesStandard` — _(unfilled)_
- `onProgress` — _(unfilled)_
- `onComplete` — _(unfilled)_
- `shouldStop` — _(unfilled)_
- `onMetadataErrors` — _(unfilled)_

### `BrowseActionBarManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseActionBarManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseActionBarManager.kt)

**Layer:** ui · **LOC:** 36 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onSearchQueryChanged` — _(unfilled)_
- `onSearchClosed` — _(unfilled)_
- `onFilterClicked` — _(unfilled)_
- `onSortClicked` — _(unfilled)_
- `onRefreshClicked` — _(unfilled)_
- `onSelectAllClicked` — _(unfilled)_
- `onCopyClicked` — _(unfilled)_
- `onMoveClicked` — _(unfilled)_
- `onDeleteClicked` — _(unfilled)_
- `onShareClicked` — _(unfilled)_
- `initialize` — _(unfilled)_
- `cleanup` — _(unfilled)_

### `BrowseArchiveDialogManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveDialogManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveDialogManager.kt)

**Layer:** ui · **LOC:** 194 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showArchiveConfigurationDialog` — _(unfilled)_
- `showArchiveProgressDialog` — _(unfilled)_
- `dismissArchiveProgressDialog` — _(unfilled)_
- `updateArchiveProgress` — _(unfilled)_
- `showUnarchiveConfirmDialog` — _(unfilled)_
- `showExtractProgressDialog` — _(unfilled)_
- `updateExtractProgress` — _(unfilled)_
- `dismissExtractProgressDialog` — _(unfilled)_
- `onArchiveSuccess` — _(unfilled)_
- `onArchiveError` — _(unfilled)_
- `onExtractionSuccess` — _(unfilled)_
- `onExtractionFailed` — _(unfilled)_

### `BrowseArchiveManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveManager.kt)

**Layer:** ui · **LOC:** 308 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `archiveSelectedFiles` — _(unfilled)_
- `cancelArchive` — _(unfilled)_
- `prepareExtraction` — _(unfilled)_
- `extractArchive` — _(unfilled)_
- `cancelExtraction` — _(unfilled)_
- `resolveUniqueExtractionDirName` — _(unfilled)_
- `folderExists` — _(unfilled)_
- `isZipArchive` — _(unfilled)_

### `BrowseBinaryFileHandler` — [com/sza/fastmediasorter/ui/browse/managers/BrowseBinaryFileHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseBinaryFileHandler.kt)

**Layer:** ui · **LOC:** 128 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showBinaryFileMenu` — _(unfilled)_
- `openWithDefaultApp` — _(unfilled)_
- `shareFile` — _(unfilled)_
- `getMimeTypeForFile` — _(unfilled)_

### `BrowseButtonSetupHelper` — [com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt)

**Layer:** ui · **LOC:** 217 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onFilterClicked` — _(unfilled)_
- `onRefreshClicked` — _(unfilled)_
- `onToggleViewClicked` — _(unfilled)_
- `onSelectAllClicked` — _(unfilled)_
- `onDeselectAllClicked` — _(unfilled)_
- `onCopyClicked` — _(unfilled)_
- `onMoveClicked` — _(unfilled)_
- `onRenameClicked` — _(unfilled)_
- `onDeleteClicked` — _(unfilled)_
- `onUndoClicked` — _(unfilled)_
- `onShareClicked` — _(unfilled)_
- `onArchiveClicked` — _(unfilled)_
- `onPlayClicked` — _(unfilled)_
- `onPlayRandomClicked` — _(unfilled)_
- `onResourceOpsClicked` — _(unfilled)_
- `onRetryClicked` — _(unfilled)_
- `onStopScanClicked` — _(unfilled)_
- `isAudioOnlyResource` — _(unfilled)_
- `setupAllButtons` — _(unfilled)_
- `setupScrollButtons` — _(unfilled)_
- `updateToolbarButtonLabels` — _(unfilled)_

### `BrowseCameraCaptureManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt)

**Layer:** ui · **LOC:** 394 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `launch` — _(unfilled)_
- `saveState` — _(unfilled)_
- `restoreState` — _(unfilled)_
- `handleResult` — _(unfilled)_
- `showNameDialog` — _(unfilled)_
- `save` — _(unfilled)_
- `saveToDcim` — _(unfilled)_
- `saveLocal` — _(unfilled)_
- `showSnackbar` — _(unfilled)_
- `showSnackbar` — _(unfilled)_
- `createTemp` — _(unfilled)_
- `withExt` — _(unfilled)_
- `hasCameraHandler` — _(unfilled)_

### `BrowseCloudAuthManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseCloudAuthManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCloudAuthManager.kt)

**Layer:** ui · **LOC:** 193 · **Last:** 2026-03-24 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onAuthenticationSuccess` — _(unfilled)_
- `onAuthenticationFailure` — _(unfilled)_
- `launchGoogleSignIn` — _(unfilled)_
- `handleGoogleSignInResult` — _(unfilled)_
- `launchDropboxSignIn` — _(unfilled)_
- `launchOneDriveSignIn` — _(unfilled)_
- `onResume` — _(unfilled)_

### `BrowseDeleteManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseDeleteManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDeleteManager.kt)

**Layer:** ui · **LOC:** 301 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `deleteSelectedFiles` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `onDeletePermissionGranted` — _(unfilled)_
- `deleteBySize` — _(unfilled)_
- `scanBySize` — _(unfilled)_
- `executeBySizeDeleteConfirmed` — _(unfilled)_

### `BrowseDialogCallbacksImpl` — [com/sza/fastmediasorter/ui/browse/managers/BrowseDialogCallbacksImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogCallbacksImpl.kt)

**Layer:** ui · **LOC:** 118 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

**Functions:**

- `onFilterApplied` — _(unfilled)_
- `onSortModeSelected` — _(unfilled)_
- `onRandomReshuffle` — _(unfilled)_
- `onRenameConfirmed` — _(unfilled)_
- `onRenameMultipleConfirmed` — _(unfilled)_
- `onDirectoryRenameConfirmed` — _(unfilled)_
- `onCopyDestinationSelected` — _(unfilled)_
- `onMoveDestinationSelected` — _(unfilled)_
- `onDeleteConfirmed` — _(unfilled)_
- `onCloudSignInRequested` — _(unfilled)_
- `saveUndoOperation` — _(unfilled)_
- `reloadFiles` — _(unfilled)_
- `updateFile` — _(unfilled)_
- `setIgnoringFileChanges` — _(unfilled)_
- `createMediaFileFromFile` — _(unfilled)_
- `getFileOperationUseCase` — _(unfilled)_
- `getResourceName` — _(unfilled)_
- `getLifecycleOwner` — _(unfilled)_

### `BrowseDialogHelper` — [com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt)

**Layer:** ui · **LOC:** 734 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** user-feedback · tests

**Role:** _(unfilled)_

**Functions:**

- `onFilterApplied` — _(unfilled)_
- `onSortModeSelected` — _(unfilled)_
- `onRandomReshuffle` — _(unfilled)_
- `onRenameConfirmed` — _(unfilled)_
- `onRenameMultipleConfirmed` — _(unfilled)_
- `onDirectoryRenameConfirmed` — _(unfilled)_
- `onCopyDestinationSelected` — _(unfilled)_
- `onMoveDestinationSelected` — _(unfilled)_
- `onDeleteConfirmed` — _(unfilled)_
- `onCloudSignInRequested` — _(unfilled)_
- `saveUndoOperation` — _(unfilled)_
- `reloadFiles` — _(unfilled)_
- `updateFile` — _(unfilled)_
- `setIgnoringFileChanges` — _(unfilled)_
- `createMediaFileFromFile` — _(unfilled)_
- `getFileOperationUseCase` — _(unfilled)_
- `getResourceName` — _(unfilled)_
- `getLifecycleOwner` — _(unfilled)_
- `initialize` — _(unfilled)_
- `cleanup` — _(unfilled)_
- `showFilterDialog` — _(unfilled)_
- `showDatePicker` — _(unfilled)_
- `formatDate` — _(unfilled)_
- `showSortDialog` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `getItemCount` — _(unfilled)_
- `getSortModeName` — _(unfilled)_
- `showDeleteConfirmation` — _(unfilled)_
- `showNetworkDeleteConfirmation` — _(unfilled)_
- `showErrorDialog` — _(unfilled)_
- `showErrorDetailsDialog` — _(unfilled)_
- `showCloudAuthenticationDialog` — _(unfilled)_
- `copyToClipboard` — _(unfilled)_
- `showRenameDialog` — _(unfilled)_
- `showRenameDirectoryDialog` — _(unfilled)_
- `showRenameSingleDialog` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `showRenameMultipleDialog` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `bind` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `getItemCount` — _(unfilled)_
- `getFileNames` — _(unfilled)_

### `BrowseDirectoryOpsManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseDirectoryOpsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDirectoryOpsManager.kt)

**Layer:** ui · **LOC:** 82 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `createFolder` — _(unfilled)_
- `renameDirectory` — _(unfilled)_

### `BrowseEdgeToEdgeHelper` — [com/sza/fastmediasorter/ui/browse/managers/BrowseEdgeToEdgeHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEdgeToEdgeHelper.kt)

**Layer:** ui · **LOC:** 72 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `apply` — _(unfilled)_

### `BrowseErrorDisplayManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseErrorDisplayManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseErrorDisplayManager.kt)

**Layer:** ui · **LOC:** 162 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showError` — _(unfilled)_
- `isNonCriticalNetworkImageError` — _(unfilled)_
- `showUndoSnackbar` — _(unfilled)_

### `BrowseEventHandler` — [com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt)

**Layer:** ui · **LOC:** 286 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `handleEvent` — _(unfilled)_
- `launchPermissionRequest` — _(unfilled)_
- `shouldLaunchStandardPlayer` — _(unfilled)_
- `createStandardPlayerIntent` — _(unfilled)_
- `detectStereoForLaunch` — _(unfilled)_
- `showAddedAsDestinationSnackbar` — _(unfilled)_

### `BrowseFileListMutationManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseFileListMutationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileListMutationManager.kt)

**Layer:** ui · **LOC:** 172 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `removeFiles` — _(unfilled)_
- `addFiles` — _(unfilled)_
- `updateFile` — _(unfilled)_
- `removeFilesFromList` — _(unfilled)_
- `onFileMissingFromDisk` — _(unfilled)_
- `createMediaFileFromFile` — _(unfilled)_

### `BrowseFileObserverManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt)

**Layer:** ui · **LOC:** 228 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `start` — _(unfilled)_
- `onFileDeleted` — _(unfilled)_
- `onFileCreated` — _(unfilled)_
- `onFileMoved` — _(unfilled)_
- `onFileModified` — _(unfilled)_
- `stop` — _(unfilled)_
- `setIgnoringFileChanges` — _(unfilled)_
- `scheduleReload` — _(unfilled)_
- `handleFileRename` — _(unfilled)_

### `BrowseFileOpenManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseFileOpenManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOpenManager.kt)

**Layer:** ui · **LOC:** 166 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `openFile` — _(unfilled)_
- `tryResolveMissingFile` — _(unfilled)_
- `resolveMissingSmbFile` — _(unfilled)_
- `mergeResolvedFileAndOpen` — _(unfilled)_

### `PendingMoveOperation` — [com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt)

**Layer:** ui · **LOC:** 942 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onOperationCompleted` — _(unfilled)_
- `saveUndoOperation` — _(unfilled)_
- `clearSelection` — _(unfilled)_
- `getCacheDir` — _(unfilled)_
- `getExternalCacheDir` — _(unfilled)_
- `onAuthRequest` — _(unfilled)_
- `onPermissionRequired` — _(unfilled)_
- `onShowMessage` — _(unfilled)_
- `onShowError` — _(unfilled)_
- `onFolderPickerRequested` — _(unfilled)_
- `hasPendingMoveOperation` — _(unfilled)_
- `retryPendingMoveOperation` — _(unfilled)_
- `executeMoveDirectly` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `length` — _(unfilled)_
- `clearPendingMoveOperation` — _(unfilled)_
- `executeOperationToPath` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `showCopyDialog` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `length` — _(unfilled)_
- `showMoveDialog` — _(unfilled)_
- `showMoveDialogInternal` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `length` — _(unfilled)_
- `shareSelectedFiles` — _(unfilled)_
- `downloadNetworkFileToCacheWithProgress` — _(unfilled)_
- `cleanupOldShareTempFiles` — _(unfilled)_
- `createNetworkAwareFile` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getName` — _(unfilled)_
- `length` — _(unfilled)_
- `downloadNetworkFileToCache` — _(unfilled)_
- `downloadSmbFile` — _(unfilled)_
- `downloadSftpFile` — _(unfilled)_
- `downloadFtpFile` — _(unfilled)_
- `cleanup` — _(unfilled)_

### `BrowseFolderPickerHandler` — [com/sza/fastmediasorter/ui/browse/managers/BrowseFolderPickerHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFolderPickerHandler.kt)

**Layer:** ui · **LOC:** 176 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `requestFolderPick` — _(unfilled)_
- `onFolderPicked` — _(unfilled)_

### `BrowseInlineAudioManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt)

**Layer:** ui · **LOC:** 345 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `inlinePlayToggle` — _(unfilled)_
- `inlineStop` — _(unfilled)_
- `attemptResumeInlinePlayback` — _(unfilled)_
- `inlineStart` — _(unfilled)_
- `inlinePlayNext` — _(unfilled)_
- `prefetchNextInlineAudio` — _(unfilled)_
- `resolveLocalPath` — _(unfilled)_
- `getInlineAudioCacheFile` — _(unfilled)_
- `downloadSmbAudioToCache` — _(unfilled)_
- `onProgress` — _(unfilled)_
- `resetDownloadProgress` — _(unfilled)_
- `saveResumeState` — _(unfilled)_

### `BrowseLauncherCallbacks` — [com/sza/fastmediasorter/ui/browse/managers/BrowseLauncherManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLauncherManager.kt)

**Layer:** ui · **LOC:** 71 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `handleGoogleSignInResult` — _(unfilled)_
- `onPlayerActivityReturned` — _(unfilled)_
- `onEditResourceReturned` — _(unfilled)_
- `onDeletePermissionGranted` — _(unfilled)_
- `onPermissionDenied` — _(unfilled)_
- `clearPendingMoveOperation` — _(unfilled)_
- `onFolderPicked` — _(unfilled)_

### `BrowseLifecycleHelper` — [com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleHelper.kt)

**Layer:** ui · **LOC:** 108 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `restoreScrollOnResume` — _(unfilled)_
- `checkAndRequestStoragePermission` — _(unfilled)_

### `BrowseLifecycleSetupManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleSetupManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleSetupManager.kt)

**Layer:** ui · **LOC:** 165 · **Last:** 2026-04-12 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `initialize` — _(unfilled)_
- `getSettings` — _(unfilled)_
- `clearPdfThumbnailCache` — _(unfilled)_
- `checkResumeStateOnInit` — _(unfilled)_
- `loadSettings` — _(unfilled)_
- `restoreFilterState` — _(unfilled)_
- `observeSelectionChanges` — _(unfilled)_
- `observeUndoChanges` — _(unfilled)_

### `BrowseListSubmitManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseListSubmitManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseListSubmitManager.kt)

**Layer:** ui · **LOC:** 188 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onListSubmitted` — _(unfilled)_
- `triggerDeferredThumbnails` — _(unfilled)_
- `triggerThumbnailsImmediate` — _(unfilled)_
- `retryOnFirstChildAttach` — _(unfilled)_
- `onChildViewAttachedToWindow` — _(unfilled)_
- `onChildViewDetachedFromWindow` — _(unfilled)_
- `getVisibleRange` — _(unfilled)_
- `updateEmptyState` — _(unfilled)_
- `restoreScrollPosition` — _(unfilled)_
- `scrollToPosition` — _(unfilled)_

### `BrowseLoadingAuxManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseLoadingAuxManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLoadingAuxManager.kt)

**Layer:** ui · **LOC:** 290 · **Last:** 2026-04-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `handleLoadingError` — _(unfilled)_
- `enrichAudioMetadataInBackground` — _(unfilled)_
- `schedulePlayerWarmupIfEligible` — _(unfilled)_
- `cancelPlayerWarmup` — _(unfilled)_
- `cancelAll` — _(unfilled)_

### `BrowseManagerInitializer` — [com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt)

**Layer:** ui · **LOC:** 885 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `initialize` — _(unfilled)_
- `onFilterApplied` — _(unfilled)_
- `onSortModeSelected` — _(unfilled)_
- `onRandomReshuffle` — _(unfilled)_
- `onRenameConfirmed` — _(unfilled)_
- `onRenameMultipleConfirmed` — _(unfilled)_
- `onDirectoryRenameConfirmed` — _(unfilled)_
- `onCopyDestinationSelected` — _(unfilled)_
- `onMoveDestinationSelected` — _(unfilled)_
- `onDeleteConfirmed` — _(unfilled)_
- `onCloudSignInRequested` — _(unfilled)_
- `saveUndoOperation` — _(unfilled)_
- `reloadFiles` — _(unfilled)_
- `updateFile` — _(unfilled)_
- `setIgnoringFileChanges` — _(unfilled)_
- `createMediaFileFromFile` — _(unfilled)_
- `getFileOperationUseCase` — _(unfilled)_
- `getResourceName` — _(unfilled)_
- `getLifecycleOwner` — _(unfilled)_
- `onMediaStoreChanged` — _(unfilled)_
- `onDisplayModeChanged` — _(unfilled)_
- `updateToggleButtonIcon` — _(unfilled)_
- `saveLastViewedFile` — _(unfilled)_
- `saveScrollPosition` — _(unfilled)_
- `getCurrentFocusPosition` — _(unfilled)_
- `getMediaFilesCount` — _(unfilled)_
- `getSelectedFilesCount` — _(unfilled)_
- `toggleCurrentItemSelection` — _(unfilled)_
- `playCurrentOrSelected` — _(unfilled)_
- `onBackPressed` — _(unfilled)_
- `showDeleteConfirmation` — _(unfilled)_
- `showCopyDialog` — _(unfilled)_
- `showMoveDialog` — _(unfilled)_
- `selectAllFiles` — _(unfilled)_
- `showRenameDialog` — _(unfilled)_
- `refreshFiles` — _(unfilled)_
- `clearSelection` — _(unfilled)_
- `navigateUp` — _(unfilled)_
- `showCreateFolderDialog` — _(unfilled)_
- `showHelp` — _(unfilled)_
- `showContextMenu` — _(unfilled)_
- `extendSelectionUp` — _(unfilled)_
- `extendSelectionDown` — _(unfilled)_
- `undoLastOperation` — _(unfilled)_
- `playRandomFiles` — _(unfilled)_
- `onAuthenticationSuccess` — _(unfilled)_
- `onAuthenticationFailure` — _(unfilled)_
- `onOperationCompleted` — _(unfilled)_
- `saveUndoOperation` — _(unfilled)_
- `clearSelection` — _(unfilled)_
- `getCacheDir` — _(unfilled)_
- `getExternalCacheDir` — _(unfilled)_
- `onAuthRequest` — _(unfilled)_
- `onPermissionRequired` — _(unfilled)_
- `onShowMessage` — _(unfilled)_
- `onShowError` — _(unfilled)_
- `onFolderPickerRequested` — _(unfilled)_
- `onFilterClicked` — _(unfilled)_
- `onRefreshClicked` — _(unfilled)_
- `onToggleViewClicked` — _(unfilled)_
- `onSelectAllClicked` — _(unfilled)_
- `onDeselectAllClicked` — _(unfilled)_
- `onCopyClicked` — _(unfilled)_
- `onMoveClicked` — _(unfilled)_
- `onRenameClicked` — _(unfilled)_
- `onDeleteClicked` — _(unfilled)_
- `onUndoClicked` — _(unfilled)_
- `onShareClicked` — _(unfilled)_
- `onArchiveClicked` — _(unfilled)_
- `onPlayClicked` — _(unfilled)_
- `onPlayRandomClicked` — _(unfilled)_
- `onRetryClicked` — _(unfilled)_
- `onStopScanClicked` — _(unfilled)_
- `isAudioOnlyResource` — _(unfilled)_
- `onResourceOpsClicked` — _(unfilled)_
- `setupDragToReorder` — _(unfilled)_
- `onStartDrag` — _(unfilled)_
- `updateSortButton` — _(unfilled)_
- `updateDragHandleVisibility` — _(unfilled)_
- `performSelectAllWithToast` — _(unfilled)_
- `showRenameDialog` — _(unfilled)_
- `showDeleteConfirmation` — _(unfilled)_
- `showCopyDialog` — _(unfilled)_
- `showMoveDialog` — _(unfilled)_
- `showArchiveDestinationPicker` — _(unfilled)_
- `startSlideshow` — _(unfilled)_
- `startRandomPlay` — _(unfilled)_
- `toggleCurrentItemSelection` — _(unfilled)_
- `playCurrentOrSelected` — _(unfilled)_
- `updateDisplayMode` — _(unfilled)_
- `updateToggleViewAvailability` — _(unfilled)_
- `updateBreadcrumb` — _(unfilled)_
- `launchEditResource` — _(unfilled)_
- `forceUpdateDisplayMode` — _(unfilled)_

### `BrowseManualOrderCoordinator` — [com/sza/fastmediasorter/ui/browse/managers/BrowseManualOrderCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManualOrderCoordinator.kt)

**Layer:** ui · **LOC:** 64 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `currentDirPath` — _(unfilled)_
- `sortFiles` — _(unfilled)_
- `saveManualOrder` — _(unfilled)_
- `applyManualOrder` — _(unfilled)_

### `BrowseMediaStoreObserver` — [com/sza/fastmediasorter/ui/browse/managers/BrowseMediaStoreObserver.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMediaStoreObserver.kt)

**Layer:** ui · **LOC:** 56 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onMediaStoreChanged` — _(unfilled)_
- `start` — _(unfilled)_
- `stop` — _(unfilled)_
- `cleanup` — _(unfilled)_

### `DirectoryCacheEntry` — [com/sza/fastmediasorter/ui/browse/managers/BrowseNavigationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseNavigationManager.kt)

**Layer:** ui · **LOC:** 472 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `navigateToFolder` — _(unfilled)_
- `navigateToFolder` — _(unfilled)_
- `navigateBack` — _(unfilled)_
- `canNavigateUp` — _(unfilled)_
- `navigateUp` — _(unfilled)_
- `resetToRoot` — _(unfilled)_
- `enableSubfolderMode` — _(unfilled)_
- `disableSubfolderMode` — _(unfilled)_
- `isSubfolderModeEnabled` — _(unfilled)_
- `navigateToDepth` — _(unfilled)_
- `getCurrentBreadcrumb` — _(unfilled)_
- `getCurrentFolderName` — _(unfilled)_
- `getBreadcrumbPath` — _(unfilled)_
- `getBreadcrumbParts` — _(unfilled)_
- `invalidateDirectoryCache` — _(unfilled)_
- `clearDirectoryCache` — _(unfilled)_
- `reloadCurrentSubfolder` — _(unfilled)_
- `loadDirectoryContents` — _(unfilled)_
- `computeDirectoryHash` — _(unfilled)_
- `computeContentHash` — _(unfilled)_
- `mix64` — _(unfilled)_

### `BrowseObserverManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseObserverManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseObserverManager.kt)

**Layer:** ui · **LOC:** 200 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `startAll` — _(unfilled)_
- `observeInlinePlayer` — _(unfilled)_
- `observeFavoritesSetting` — _(unfilled)_
- `observeHideGridActionButtons` — _(unfilled)_
- `observeLoadingProgress` — _(unfilled)_
- `observeSettings` — _(unfilled)_
- `observeError` — _(unfilled)_

### `BrowseRecyclerViewManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseRecyclerViewManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRecyclerViewManager.kt)

**Layer:** ui · **LOC:** 156 · **Last:** 2026-04-12 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onDisplayModeChanged` — _(unfilled)_
- `updateToggleButtonIcon` — _(unfilled)_
- `initialize` — _(unfilled)_
- `updateDisplayMode` — _(unfilled)_
- `cleanup` — _(unfilled)_
- `calculateListSpanCount` — _(unfilled)_

### `BrowseRefreshManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseRefreshManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRefreshManager.kt)

**Layer:** ui · **LOC:** 168 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `launchReload` — _(unfilled)_
- `cleanupTrashOnBackground` — _(unfilled)_

### `BrowseResourceLoadManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt)

**Layer:** ui · **LOC:** 494 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `loadResource` — _(unfilled)_
- `loadMediaFiles` — _(unfilled)_
- `cancelLoad` — _(unfilled)_
- `checkCloudAuthBeforeScan` — _(unfilled)_
- `checkGoogleDriveAuth` — _(unfilled)_
- `checkDropboxAuth` — _(unfilled)_
- `checkOneDriveAuth` — _(unfilled)_
- `isAuthError` — _(unfilled)_
- `loadMediaFilesStandard` — _(unfilled)_
- `updateLoadingProgress` — _(unfilled)_
- `updateState` — _(unfilled)_
- `setLoading` — _(unfilled)_
- `handleLoadingError` — _(unfilled)_
- `updateResourceMetadata` — _(unfilled)_
- `onFilesLoaded` — _(unfilled)_
- `startFileObserver` — _(unfilled)_
- `sortFiles` — _(unfilled)_
- `onScanMetadataErrors` — _(unfilled)_
- `loadMediaFilesWithPagination` — _(unfilled)_

### `BrowseResourceStateManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseResourceStateManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceStateManager.kt)

**Layer:** ui · **LOC:** 169 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `toggleFavorite` — _(unfilled)_
- `saveLastViewedFile` — _(unfilled)_
- `saveScrollPosition` — _(unfilled)_
- `clearResumeState` — _(unfilled)_
- `refreshResourceMetadata` — _(unfilled)_
- `addCurrentResourceAsDestination` — _(unfilled)_

### `BrowseRoutingDecision` — [com/sza/fastmediasorter/ui/browse/managers/BrowseRoutingDecision.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRoutingDecision.kt)

**Layer:** ui · **LOC:** 41 · **Last:** — · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `decide` — _(unfilled)_

### `BrowseScrollButtonManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseScrollButtonManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseScrollButtonManager.kt)

**Layer:** ui · **LOC:** 100 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `updateScrollButtonsVisibility` — _(unfilled)_
- `notifyItemRangeChangedSafely` — _(unfilled)_

### `BrowseScrollThumbnailListener` — [com/sza/fastmediasorter/ui/browse/managers/BrowseScrollThumbnailListener.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseScrollThumbnailListener.kt)

**Layer:** ui · **LOC:** 50 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onScrollStateChanged` — _(unfilled)_
- `onScrolled` — _(unfilled)_

### `BrowseSelectionManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseSelectionManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseSelectionManager.kt)

**Layer:** ui · **LOC:** 37 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onSelectionChanged` — _(unfilled)_
- `onFileClicked` — _(unfilled)_
- `onFileLongClicked` — _(unfilled)_
- `onActionModeStarted` — _(unfilled)_
- `onActionModeFinished` — _(unfilled)_
- `initialize` — _(unfilled)_
- `cleanup` — _(unfilled)_

### `BrowseShutdownCoordinator` — [com/sza/fastmediasorter/ui/browse/managers/BrowseShutdownCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseShutdownCoordinator.kt)

**Layer:** ui · **LOC:** 85 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `buildNetworkResourceKey` — _(unfilled)_
- `cancelBackgroundThumbnailLoading` — _(unfilled)_
- `onShutdown` — _(unfilled)_
- `launchPostShutdownCleanup` — _(unfilled)_

### `BrowseSmallControlsManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseSmallControlsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseSmallControlsManager.kt)

**Layer:** ui · **LOC:** 160 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `applySmallControlsIfNeeded` — _(unfilled)_
- `restoreCommandButtonHeightsIfNeeded` — _(unfilled)_
- `commandPanelButtons` — _(unfilled)_
- `resolveOriginalButtonHeight` — _(unfilled)_

### `BrowseSortFilterManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseSortFilterManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseSortFilterManager.kt)

**Layer:** ui · **LOC:** 332 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk, prefs  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `setSortMode` — _(unfilled)_
- `reshuffleRandom` — _(unfilled)_
- `toggleDisplayMode` — _(unfilled)_
- `setFilter` — _(unfilled)_
- `applyFilter` — _(unfilled)_
- `applyFilterToList` — _(unfilled)_
- `sortFiles` — _(unfilled)_
- `cachedFilesMissingMetadataForSort` — _(unfilled)_
- `reshuffleVisibleFiles` — _(unfilled)_
- `refreshRandomShuffleSeed` — _(unfilled)_

### `BrowseSortMenuManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseSortMenuManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseSortMenuManager.kt)

**Layer:** ui · **LOC:** 125 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getSortModeIconRes` — _(unfilled)_
- `getRelevantSortModes` — _(unfilled)_
- `showSortPopupMenu` — _(unfilled)_
- `getSortModeShortName` — _(unfilled)_
- `getSortModeName` — _(unfilled)_

### `BrowseStateManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseStateManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateManager.kt)

**Layer:** ui · **LOC:** 68 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `saveLastViewedFile` — _(unfilled)_
- `saveScrollPosition` — _(unfilled)_
- `getCurrentFocusPosition` — _(unfilled)_
- `saveLastViewedFile` — _(unfilled)_
- `saveScrollPosition` — _(unfilled)_

### `BrowseStateSyncManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseStateSyncManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateSyncManager.kt)

**Layer:** ui · **LOC:** 162 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `loadFavorites` — _(unfilled)_
- `syncWithCache` — _(unfilled)_
- `checkAndReloadIfResourceChanged` — _(unfilled)_

### `BrowseStateUiUpdater` — [com/sza/fastmediasorter/ui/browse/managers/BrowseStateUiUpdater.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateUiUpdater.kt)

**Layer:** ui · **LOC:** 183 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `onStateChanged` — _(unfilled)_
- `updateFilterBadge` — _(unfilled)_
- `updateSelectionPanel` — _(unfilled)_
- `updatePlayRandomButtonVisibility` — _(unfilled)_
- `updateDisplayModeIfNeeded` — _(unfilled)_
- `applySmallControls` — _(unfilled)_
- `updateResourceActionButton` — _(unfilled)_
- `isCameraCaptureVisible` — _(unfilled)_

### `BrowseUtilityManager` — [com/sza/fastmediasorter/ui/browse/managers/BrowseUtilityManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseUtilityManager.kt)

**Layer:** ui · **LOC:** 161 · **Last:** 2026-03-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `buildResourceInfo` — _(unfilled)_
- `buildRootPathDisplay` — _(unfilled)_
- `buildBreadcrumb` — _(unfilled)_
- `buildFilterDescription` — _(unfilled)_
- `formatDate` — _(unfilled)_

### `KeyboardNavigationManager` — [com/sza/fastmediasorter/ui/browse/managers/KeyboardNavigationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/KeyboardNavigationManager.kt)

**Layer:** ui · **LOC:** 154 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `getCurrentFocusPosition` — _(unfilled)_
- `getMediaFilesCount` — _(unfilled)_
- `getSelectedFilesCount` — _(unfilled)_
- `toggleCurrentItemSelection` — _(unfilled)_
- `playCurrentOrSelected` — _(unfilled)_
- `onBackPressed` — _(unfilled)_
- `showDeleteConfirmation` — _(unfilled)_
- `showCopyDialog` — _(unfilled)_
- `showMoveDialog` — _(unfilled)_
- `showRenameDialog` — _(unfilled)_
- `selectAllFiles` — _(unfilled)_
- `clearSelection` — _(unfilled)_
- `refreshFiles` — _(unfilled)_
- `navigateUp` — _(unfilled)_
- `showCreateFolderDialog` — _(unfilled)_
- `showHelp` — _(unfilled)_
- `showContextMenu` — _(unfilled)_
- `extendSelectionUp` — _(unfilled)_
- `extendSelectionDown` — _(unfilled)_
- `undoLastOperation` — _(unfilled)_
- `playRandomFiles` — _(unfilled)_
- `handleKeyDown` — _(unfilled)_
- `dispatchAction` — _(unfilled)_
- `handleMoveFocus` — _(unfilled)_
- `movePosition` — _(unfilled)_
- `scrollPage` — _(unfilled)_
- `scrollToPosition` — _(unfilled)_

### `ResourceOpsMenuManager` — [com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt)

**Layer:** ui · **LOC:** 347 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** —  
**Flags:** user-feedback · tests

**Role:** _(unfilled)_

**Functions:**

- `showMenu` — _(unfilled)_
- `showDeleteBySizeDialog` — _(unfilled)_
- `showDeleteBySizeConfirm` — _(unfilled)_
- `showCreateFolderDialog` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_

### `MediaFileAdapter` — [com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt)

**Layer:** ui · **LOC:** 1103 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `setDragStartListener` — _(unfilled)_
- `showDragHandles` — _(unfilled)_
- `moveItem` — _(unfilled)_
- `getOrderedPaths` — _(unfilled)_
- `updateInlinePlayerState` — _(unfilled)_
- `setBinaryThumbnailGenerator` — _(unfilled)_
- `setAudioMetadataLoader` — _(unfilled)_
- `loadVisibleAudioMetadata` — _(unfilled)_
- `shouldDisableDocumentPreviews` — _(unfilled)_
- `incrementRefreshVersion` — _(unfilled)_
- `setScrolling` — _(unfilled)_
- `loadVisibleThumbnails` — _(unfilled)_
- `setCredentialsId` — _(unfilled)_
- `setShowFavoriteButton` — _(unfilled)_
- `setHideGridActionButtons` — _(unfilled)_
- `setResourcePermissions` — _(unfilled)_
- `setDisableThumbnails` — _(unfilled)_
- `setAudioOnlyMode` — _(unfilled)_
- `setUseCompactElements` — _(unfilled)_
- `setSkipInitialThumbnailLoad` — _(unfilled)_
- `getSkipInitialThumbnailLoad` — _(unfilled)_
- `resolveAudioMetadata` — _(unfilled)_
- `setGridMode` — _(unfilled)_
- `setSelectedPaths` — _(unfilled)_
- `getItemViewType` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `onCurrentListChanged` — _(unfilled)_
- `onViewRecycled` — _(unfilled)_
- `getItemByPosition` — _(unfilled)_
- `clearImage` — _(unfilled)_
- `updatePlaybackState` — _(unfilled)_
- `updateAudioMetadataText` — _(unfilled)_
- `stopPlaybackAnimations` — _(unfilled)_
- `applyInlineHighlight` — _(unfilled)_
- `loadThumbnailOnly` — _(unfilled)_
- `bind` — _(unfilled)_
- `loadThumbnail` — _(unfilled)_
- `getItemByPosition` — _(unfilled)_
- `ensureOperationsInflated` — _(unfilled)_
- `clearImage` — _(unfilled)_
- `loadThumbnailOnly` — _(unfilled)_
- `bind` — _(unfilled)_
- `loadThumbnail` — _(unfilled)_

### `MediaFileDiffCallback` — [com/sza/fastmediasorter/ui/browse/MediaFileDiffCallback.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileDiffCallback.kt)

**Layer:** ui · **LOC:** 44 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_
- `getChangePayload` — _(unfilled)_

### `BrowseMetadataManager` — [com/sza/fastmediasorter/ui/browse/metadata/BrowseMetadataManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/metadata/BrowseMetadataManager.kt)

**Layer:** ui · **LOC:** 63 · **Last:** 2026-02-20 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `updateMetadata` — _(unfilled)_

### `PagingLoadStateAdapter` — [com/sza/fastmediasorter/ui/browse/PagingLoadStateAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/PagingLoadStateAdapter.kt)

**Layer:** ui · **LOC:** 54 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `bind` — _(unfilled)_

### `PagingMediaFileAdapter` — [com/sza/fastmediasorter/ui/browse/PagingMediaFileAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/PagingMediaFileAdapter.kt)

**Layer:** ui · **LOC:** 803 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `setCredentialsId` — _(unfilled)_
- `setUseCompactElements` — _(unfilled)_
- `setGridMode` — _(unfilled)_
- `setSelectedPaths` — _(unfilled)_
- `getItemViewType` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `onViewRecycled` — _(unfilled)_
- `clearImage` — _(unfilled)_
- `bind` — _(unfilled)_
- `loadThumbnail` — _(unfilled)_
- `createExtensionBitmap` — _(unfilled)_
- `getPlaceholderExtension` — _(unfilled)_
- `createPlaceholderBitmap` — _(unfilled)_
- `createPlaceholderDrawable` — _(unfilled)_
- `showGeneratedPlaceholder` — _(unfilled)_
- `buildFileInfo` — _(unfilled)_
- `formatFileSize` — _(unfilled)_
- `clearImage` — _(unfilled)_
- `bind` — _(unfilled)_
- `loadThumbnail` — _(unfilled)_
- `createExtensionBitmap` — _(unfilled)_
- `getPlaceholderExtension` — _(unfilled)_
- `createPlaceholderBitmap` — _(unfilled)_
- `createPlaceholderDrawable` — _(unfilled)_
- `showGeneratedPlaceholder` — _(unfilled)_

### `BrowseSelectionManager` — [com/sza/fastmediasorter/ui/browse/selection/BrowseSelectionManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/selection/BrowseSelectionManager.kt)

**Layer:** ui · **LOC:** 162 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `toggleSelection` — _(unfilled)_
- `selectRange` — _(unfilled)_
- `clearSelection` — _(unfilled)_
- `selectAll` — _(unfilled)_
- `onFilesRemoved` — _(unfilled)_
- `onFilePathChanged` — _(unfilled)_
- `isSelected` — _(unfilled)_
- `getSelectionCount` — _(unfilled)_

### `BrowseUndoManager` — [com/sza/fastmediasorter/ui/browse/undo/BrowseUndoManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/undo/BrowseUndoManager.kt)

**Layer:** ui · **LOC:** 246 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `addFilesToList` — _(unfilled)_
- `reloadFileList` — _(unfilled)_
- `createMediaFileFromFile` — _(unfilled)_
- `showMessage` — _(unfilled)_
- `showUndoToast` — _(unfilled)_
- `showError` — _(unfilled)_
- `saveOperation` — _(unfilled)_
- `undoLastOperation` — _(unfilled)_
- `undoCopyOperation` — _(unfilled)_
- `undoMoveOperation` — _(unfilled)_
- `undoDeleteOperation` — _(unfilled)_
- `undoRenameOperation` — _(unfilled)_
- `clearIfExpired` — _(unfilled)_
- `isUndoAvailable` — _(unfilled)_

### `CloudFolderAdapter` — [com/sza/fastmediasorter/ui/cloudfolders/CloudFolderAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/CloudFolderAdapter.kt)

**Layer:** ui · **LOC:** 54 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `bind` — _(unfilled)_
- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_

### `CloudFolderItem` — [com/sza/fastmediasorter/ui/cloudfolders/CloudFolderItem.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/CloudFolderItem.kt)

**Layer:** ui · **LOC:** 9 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `CloudFolderItemBinding` — [com/sza/fastmediasorter/ui/cloudfolders/CloudFolderItemBinding.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/CloudFolderItemBinding.kt)

**Layer:** ui · **LOC:** 45 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `CloudFolderPickerKeyboardDelegate` — [com/sza/fastmediasorter/ui/cloudfolders/CloudFolderPickerKeyboardDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/CloudFolderPickerKeyboardDelegate.kt)

**Layer:** ui · **LOC:** 50 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `activateFocused` — _(unfilled)_
- `navigateUp` — _(unfilled)_
- `refresh` — _(unfilled)_
- `cancel` — _(unfilled)_
- `showHelp` — _(unfilled)_
- `handleKeyDown` — _(unfilled)_
- `dispatchAction` — _(unfilled)_

### `DropboxFolderPickerActivity` — [com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerActivity.kt)

**Layer:** ui · **LOC:** 154 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `activateFocused` — _(unfilled)_
- `navigateUp` — _(unfilled)_
- `refresh` — _(unfilled)_
- `cancel` — _(unfilled)_
- `showHelp` — _(unfilled)_
- `getViewBinding` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `handleOnBackPressed` — _(unfilled)_
- `applyEdgeToEdgeInsets` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onKeyDown` — _(unfilled)_
- `handleBackNavigation` — _(unfilled)_

### `DropboxFolderPickerState` — [com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/DropboxFolderPickerViewModel.kt)

**Layer:** ui · **LOC:** 194 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, DropboxClient, ResourceRepository, SettingsRepository, AddResourceUseCase, SavedStateHandle  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `loadFolders` — _(unfilled)_
- `toggleDestinationFlag` — _(unfilled)_
- `toggleScanSubdirectoriesFlag` — _(unfilled)_
- `selectFolder` — _(unfilled)_
- `navigateIntoFolder` — _(unfilled)_
- `navigateBack` — _(unfilled)_

### `GoogleDriveFolderPickerActivity` — [com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerActivity.kt)

**Layer:** ui · **LOC:** 179 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `activateFocused` — _(unfilled)_
- `navigateUp` — _(unfilled)_
- `refresh` — _(unfilled)_
- `cancel` — _(unfilled)_
- `showHelp` — _(unfilled)_
- `getViewBinding` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `handleOnBackPressed` — _(unfilled)_
- `applyEdgeToEdgeInsets` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onKeyDown` — _(unfilled)_
- `handleBackNavigation` — _(unfilled)_

### `GoogleDriveFolderPickerState` — [com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/GoogleDriveFolderPickerViewModel.kt)

**Layer:** ui · **LOC:** 309 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, GoogleDriveRestClient, ResourceRepository, AddResourceUseCase, SavedStateHandle  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `loadFolders` — _(unfilled)_
- `toggleDestinationFlag` — _(unfilled)_
- `toggleScanSubdirectoriesFlag` — _(unfilled)_
- `selectFolder` — _(unfilled)_
- `navigateIntoFolder` — _(unfilled)_
- `navigateBack` — _(unfilled)_

### `OneDriveFolderPickerActivity` — [com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerActivity.kt)

**Layer:** ui · **LOC:** 152 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `activateFocused` — _(unfilled)_
- `navigateUp` — _(unfilled)_
- `refresh` — _(unfilled)_
- `cancel` — _(unfilled)_
- `showHelp` — _(unfilled)_
- `getViewBinding` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `handleOnBackPressed` — _(unfilled)_
- `applyEdgeToEdgeInsets` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onKeyDown` — _(unfilled)_
- `handleBackNavigation` — _(unfilled)_

### `OneDriveFolderPickerState` — [com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudfolders/OneDriveFolderPickerViewModel.kt)

**Layer:** ui · **LOC:** 194 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, OneDriveRestClient, ResourceRepository, SettingsRepository, AddResourceUseCase, SavedStateHandle  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `loadFolders` — _(unfilled)_
- `toggleDestinationFlag` — _(unfilled)_
- `toggleScanSubdirectoriesFlag` — _(unfilled)_
- `selectFolder` — _(unfilled)_
- `navigateIntoFolder` — _(unfilled)_
- `navigateBack` — _(unfilled)_

### `BreadcrumbView` — [com/sza/fastmediasorter/ui/common/BreadcrumbView.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/BreadcrumbView.kt)

**Layer:** ui · **LOC:** 148 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `setPath` — _(unfilled)_
- `setOnSegmentClickListener` — _(unfilled)_
- `addSegment` — _(unfilled)_
- `addSeparator` — _(unfilled)_
- `clear` — _(unfilled)_
- `getDepth` — _(unfilled)_

### `DialogUtils` — [com/sza/fastmediasorter/ui/common/DialogUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/DialogUtils.kt)

**Layer:** ui · **LOC:** 104 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showScrollableDialog` — _(unfilled)_
- `showScrollableDialog` — _(unfilled)_

### `ErrorDialogHelper` — [com/sza/fastmediasorter/ui/common/ErrorDialogHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/ErrorDialogHelper.kt)

**Layer:** ui · **LOC:** 64 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `showSimpleError` — _(unfilled)_
- `showDetailedError` — _(unfilled)_
- `showDetailedError` — _(unfilled)_

### `FocusManager` — [com/sza/fastmediasorter/ui/common/FocusManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/FocusManager.kt)

**Layer:** ui · **LOC:** 221 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `onItemFocused` — _(unfilled)_
- `onItemSelected` — _(unfilled)_
- `getItemCount` — _(unfilled)_
- `onRangeExtended` — _(unfilled)_
- `handleArrowKey` — _(unfilled)_
- `applyAction` — _(unfilled)_
- `getSpanCount` — _(unfilled)_
- `moveFocus` — _(unfilled)_
- `extendRange` — _(unfilled)_
- `selectCurrentItem` — _(unfilled)_
- `getCurrentPosition` — _(unfilled)_
- `setPosition` — _(unfilled)_
- `reset` — _(unfilled)_
- `setFocusHighlightEnabled` — _(unfilled)_
- `hasFocus` — _(unfilled)_
- `ensureFocus` — _(unfilled)_

### `FocusRingHelper` — [com/sza/fastmediasorter/ui/common/input/FocusRingHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/FocusRingHelper.kt)

**Layer:** ui · **LOC:** 80 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `setFocused` — _(unfilled)_
- `attach` — _(unfilled)_
- `buildRing` — _(unfilled)_

### `FocusDirection` — [com/sza/fastmediasorter/ui/common/input/InputAction.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputAction.kt)

**Layer:** ui · **LOC:** 117 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** —

**Role:** _(unfilled)_

### `InputHelpDialogFragment` — [com/sza/fastmediasorter/ui/common/input/InputHelpDialogFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputHelpDialogFragment.kt)

**Layer:** ui · **LOC:** 130 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

**Functions:**

- `onCreateDialog` — _(unfilled)_
- `buildRow` — _(unfilled)_
- `show` — _(unfilled)_

### `InputHelpEntry` — [com/sza/fastmediasorter/ui/common/input/InputHelpEntry.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputHelpEntry.kt)

**Layer:** ui · **LOC:** 34 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `InputHelpLinkResolver` — [com/sza/fastmediasorter/ui/common/input/InputHelpLinkResolver.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputHelpLinkResolver.kt)

**Layer:** ui · **LOC:** 35 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `urlFor` — _(unfilled)_

### `InputHelpRegistry` — [com/sza/fastmediasorter/ui/common/input/InputHelpRegistry.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputHelpRegistry.kt)

**Layer:** ui · **LOC:** 180 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `get` — _(unfilled)_
- `entry` — _(unfilled)_

### `InputSurface` — [com/sza/fastmediasorter/ui/common/input/InputSurface.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputSurface.kt)

**Layer:** ui · **LOC:** 53 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `IpAddressInputFilter` — [com/sza/fastmediasorter/ui/common/IpAddressInputFilter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/IpAddressInputFilter.kt)

**Layer:** ui · **LOC:** 55 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `filter` — _(unfilled)_

### `MediaGroupPalette` — [com/sza/fastmediasorter/ui/common/MediaGroupPalette.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/MediaGroupPalette.kt)

**Layer:** ui · **LOC:** 41 · **Last:** 2026-02-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `colorForType` — _(unfilled)_
- `colorForSingleCategory` — _(unfilled)_

### `MouseEventHandler` — [com/sza/fastmediasorter/ui/common/MouseEventHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/common/MouseEventHandler.kt)

**Layer:** ui · **LOC:** 254 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `onSingleClick` — _(unfilled)_
- `onDoubleClick` — _(unfilled)_
- `onRightClick` — _(unfilled)_
- `onMiddleClick` — _(unfilled)_
- `onScrollWheel` — _(unfilled)_
- `onHoverEnter` — _(unfilled)_
- `onHoverExit` — _(unfilled)_
- `onNavigateBack` — _(unfilled)_
- `onNavigateForward` — _(unfilled)_
- `onInputAction` — _(unfilled)_
- `handleMotionEvent` — _(unfilled)_
- `handleGenericMotionEvent` — _(unfilled)_
- `isMouseEvent` — _(unfilled)_
- `handleMouseDown` — _(unfilled)_
- `handleMouseUp` — _(unfilled)_
- `handleButtonPress` — _(unfilled)_
- `dispatchSecondaryButton` — _(unfilled)_
- `handleScroll` — _(unfilled)_
- `reset` — _(unfilled)_
- `createTouchListener` — _(unfilled)_
- `createGenericMotionListener` — _(unfilled)_

### `ColorPickerDialog` — [com/sza/fastmediasorter/ui/dialog/ColorPickerDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ColorPickerDialog.kt)

**Layer:** ui · **LOC:** 165 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

**Functions:**

- `onCreateDialog` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `updateColorPreview` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `newInstance` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `getItemCount` — _(unfilled)_
- `updateSelection` — _(unfilled)_
- `bind` — _(unfilled)_

### `DeleteDialog` — [com/sza/fastmediasorter/ui/dialog/DeleteDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DeleteDialog.kt)

**Layer:** ui · **LOC:** 183 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `setupUI` — _(unfilled)_
- `deleteFiles` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `handleDeleteResult` — _(unfilled)_

### `DestinationAdapter` — [com/sza/fastmediasorter/ui/dialog/DestinationAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DestinationAdapter.kt)

**Layer:** ui · **LOC:** 64 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `bind` — _(unfilled)_
- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_

### `DestinationPickerDialog` — [com/sza/fastmediasorter/ui/dialog/DestinationPickerDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DestinationPickerDialog.kt)

**Layer:** ui · **LOC:** 132 · **Last:** 2026-04-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `setupUI` — _(unfilled)_
- `loadDestinations` — _(unfilled)_
- `createResourceButtons` — _(unfilled)_

### `DialogKeyboardDelegate` — [com/sza/fastmediasorter/ui/dialog/DialogKeyboardDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DialogKeyboardDelegate.kt)

**Layer:** ui · **LOC:** 99 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `applyTo` — _(unfilled)_
- `applyToDialogFragment` — _(unfilled)_
- `showHelp` — _(unfilled)_
- `toggleFocusedControl` — _(unfilled)_
- `moveFocus` — _(unfilled)_

### `ErrorDialog` — [com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt)

**Layer:** ui · **LOC:** 96 · **Last:** 2026-04-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `show` — _(unfilled)_
- `show` — _(unfilled)_
- `copyToClipboard` — _(unfilled)_

### `FileInfoDialog` — [com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt)

**Layer:** ui · **LOC:** 947 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `onStop` — _(unfilled)_
- `setupDialog` — _(unfilled)_
- `isLocalFile` — _(unfilled)_
- `isCloudFile` — _(unfilled)_
- `buildPathInfoText` — _(unfilled)_
- `openInExternalPlayer` — _(unfilled)_
- `displayFileInfo` — _(unfilled)_
- `displayExifInfo` — _(unfilled)_
- `displayAudioInfo` — _(unfilled)_
- `displayVideoInfo` — _(unfilled)_
- `displayDocumentInfo` — _(unfilled)_
- `updateDetailedInfo` — _(unfilled)_
- `updateDocumentInfo` — _(unfilled)_
- `downloadAndOpenFile` — _(unfilled)_
- `openDownloadedFile` — _(unfilled)_
- `formatDate` — _(unfilled)_
- `formatDuration` — _(unfilled)_
- `formatOrientation` — _(unfilled)_
- `formatGPS` — _(unfilled)_
- `formatBitrate` — _(unfilled)_
- `gcd` — _(unfilled)_

### `FileOperationDestinationDialog` — [com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt)

**Layer:** ui · **LOC:** 607 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onDetachedFromWindow` — _(unfilled)_
- `onCreate` — _(unfilled)_
- `setupUI` — _(unfilled)_
- `loadDestinations` — _(unfilled)_
- `createDestinationButtons` — _(unfilled)_
- `performOperation` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `handleOperationResult` — _(unfilled)_
- `showOperationError` — _(unfilled)_
- `showCloudAuthenticationError` — _(unfilled)_

### `FileOperationProgressDialog` — [com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt)

**Layer:** ui · **LOC:** 178 · **Last:** 2026-03-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `startShowTimer` — _(unfilled)_
- `dismiss` — _(unfilled)_
- `updateProgress` — _(unfilled)_
- `applyProgressToUI` — _(unfilled)_
- `onStart` — _(unfilled)_
- `formatSpeed` — _(unfilled)_
- `show` — _(unfilled)_

### `GifEditorDialog` — [com/sza/fastmediasorter/ui/dialog/GifEditorDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/GifEditorDialog.kt)

**Layer:** ui · **LOC:** 364 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `setupUI` — _(unfilled)_
- `onProgressChanged` — _(unfilled)_
- `onStartTrackingTouch` — _(unfilled)_
- `onStopTrackingTouch` — _(unfilled)_
- `updateSpeedLabel` — _(unfilled)_
- `showHelpDialog` — _(unfilled)_
- `performExtractFrames` — _(unfilled)_
- `performChangeSpeed` — _(unfilled)_
- `performSaveFirstFrame` — _(unfilled)_
- `setButtonsEnabled` — _(unfilled)_
- `showProgress` — _(unfilled)_
- `hideProgress` — _(unfilled)_
- `onDetachedFromWindow` — _(unfilled)_

### `GifEditorHelper` — [com/sza/fastmediasorter/ui/dialog/helpers/GifEditorHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/GifEditorHelper.kt)

**Layer:** ui · **LOC:** 153 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `isNetworkPath` — _(unfilled)_
- `prepareGifFile` — _(unfilled)_
- `cleanup` — _(unfilled)_
- `getSuccessMessage` — _(unfilled)_
- `getPreparingMessage` — _(unfilled)_

### `ImageEditDialog` — [com/sza/fastmediasorter/ui/dialog/ImageEditDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ImageEditDialog.kt)

**Layer:** ui · **LOC:** 298 · **Last:** 2026-02-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `setupUI` — _(unfilled)_
- `performFilter` — _(unfilled)_
- `performAdjustments` — _(unfilled)_
- `performRotation` — _(unfilled)_
- `performFlip` — _(unfilled)_
- `setButtonsEnabled` — _(unfilled)_
- `showProgress` — _(unfilled)_
- `hideProgress` — _(unfilled)_
- `onDetachedFromWindow` — _(unfilled)_

### `MaterialProgressDialog` — [com/sza/fastmediasorter/ui/dialog/MaterialProgressDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/MaterialProgressDialog.kt)

**Layer:** ui · **LOC:** 153 · **Last:** 2026-03-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `setTitle` — _(unfilled)_
- `setTitle` — _(unfilled)_
- `setMessage` — _(unfilled)_
- `setMessage` — _(unfilled)_
- `setProgressStyle` — _(unfilled)_
- `show` — _(unfilled)_

### `PlayerSettingsDialog` — [com/sza/fastmediasorter/ui/dialog/PlayerSettingsDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/PlayerSettingsDialog.kt)

**Layer:** ui · **LOC:** 195 · **Last:** 2026-04-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `fromCode` — _(unfilled)_
- `onCreate` — _(unfilled)_
- `setupUI` — _(unfilled)_
- `setupStereoSection` — _(unfilled)_
- `setupLanguageSpinner` — _(unfilled)_
- `loadCurrentSettings` — _(unfilled)_
- `collectSettings` — _(unfilled)_

### `RenameDialog` — [com/sza/fastmediasorter/ui/dialog/RenameDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/RenameDialog.kt)

**Layer:** ui · **LOC:** 295 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `setupUI` — _(unfilled)_
- `renameFiles` — _(unfilled)_
- `renameSingleFile` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getName` — _(unfilled)_
- `renameMultipleFiles` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getName` — _(unfilled)_
- `bind` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `getItemCount` — _(unfilled)_
- `getFileNames` — _(unfilled)_

### `ResourcePickerDialog` — [com/sza/fastmediasorter/ui/dialog/ResourcePickerDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ResourcePickerDialog.kt)

**Layer:** ui · **LOC:** 148 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `setupUI` — _(unfilled)_
- `loadResources` — _(unfilled)_
- `createResourceButtons` — _(unfilled)_

### `ResourceTypeSelectorDialog` — [com/sza/fastmediasorter/ui/dialog/ResourceTypeSelectorDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ResourceTypeSelectorDialog.kt)

**Layer:** ui · **LOC:** 71 · **Last:** 2026-02-20 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `newInstance` — _(unfilled)_

### `ScheduledLogDialog` — [com/sza/fastmediasorter/ui/dialog/ScheduledLogDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScheduledLogDialog.kt)

**Layer:** ui · **LOC:** 47 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_

### `ScheduledOperationDialog` — [com/sza/fastmediasorter/ui/dialog/ScheduledOperationDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScheduledOperationDialog.kt)

**Layer:** ui · **LOC:** 457 · **Last:** 2026-04-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `setupDropdowns` — _(unfilled)_
- `setupConditionsCollapse` — _(unfilled)_
- `setupSaveButtonState` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `updateSaveButtonState` — _(unfilled)_
- `setupTimePicker` — _(unfilled)_
- `setupIntervalPicker` — _(unfilled)_
- `updateFileTypeCheckboxVisibility` — _(unfilled)_
- `setupFileTypeInterlock` — _(unfilled)_
- `buildFileTypeMask` — _(unfilled)_
- `applyFileTypeMask` — _(unfilled)_
- `normalizeMaskForFlavor` — _(unfilled)_
- `applyReadOnlySourceConstraint` — _(unfilled)_
- `setupNextRunPreview` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `updateNextRunPreview` — _(unfilled)_
- `applyPrefilledSource` — _(unfilled)_
- `populateExisting` — _(unfilled)_
- `trySave` — _(unfilled)_

### `TooltipDialog` — [com/sza/fastmediasorter/ui/dialog/TooltipDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/TooltipDialog.kt)

**Layer:** ui · **LOC:** 55 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `show` — _(unfilled)_
- `show` — _(unfilled)_

### `DuplicateGroupAdapter` — [com/sza/fastmediasorter/ui/duplicates/DuplicateGroupAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicateGroupAdapter.kt)

**Layer:** ui · **LOC:** 127 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `bind` — _(unfilled)_
- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `bind` — _(unfilled)_
- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_

### `DuplicatesActivity` — [com/sza/fastmediasorter/ui/duplicates/DuplicatesActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesActivity.kt)

**Layer:** ui · **LOC:** 59 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onKeyDown` — _(unfilled)_
- `onCreate` — _(unfilled)_

### `DuplicatesFragment` — [com/sza/fastmediasorter/ui/duplicates/DuplicatesFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesFragment.kt)

**Layer:** ui · **LOC:** 239 · **Last:** 2026-04-12 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `applyEdgeToEdgeInsets` — _(unfilled)_
- `setupRecyclerView` — _(unfilled)_
- `setupListeners` — _(unfilled)_
- `observeViewModel` — _(unfilled)_
- `updateUi` — _(unfilled)_
- `buildResourceChips` — _(unfilled)_
- `handleEvent` — _(unfilled)_
- `onDestroyView` — _(unfilled)_

### `DuplicatesState` — [com/sza/fastmediasorter/ui/duplicates/DuplicatesViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesViewModel.kt)

**Layer:** ui · **LOC:** 288 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, ResourceRepository, DetectDuplicatesUseCase, DeleteFilesUseCase, DuplicateHashRepository  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `initWithResource` — _(unfilled)_
- `applyPinnedResource` — _(unfilled)_
- `loadResources` — _(unfilled)_
- `toggleResourceSelection` — _(unfilled)_
- `startScan` — _(unfilled)_
- `startScanConfirmed` — _(unfilled)_
- `doStartScan` — _(unfilled)_
- `cancelScan` — _(unfilled)_
- `toggleFileSelection` — _(unfilled)_
- `deleteSelectedFiles` — _(unfilled)_
- `deleteFile` — _(unfilled)_
- `setResult` — _(unfilled)_

### `ImageDisplayUtils` — [com/sza/fastmediasorter/ui/image/ImageDisplayUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/image/ImageDisplayUtils.kt)

**Layer:** ui · **LOC:** 90 · **Last:** 2026-02-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `isOrientationMatch` — _(unfilled)_
- `determineImageScaleType` — _(unfilled)_

### `CaptureDialogFragment` — [com/sza/fastmediasorter/ui/keybinding/CaptureDialogFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/CaptureDialogFragment.kt)

**Layer:** ui · **LOC:** 188 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `setupInputListeners` — _(unfilled)_
- `captureAxisFrom` — _(unfilled)_
- `onTriggerCaptured` — _(unfilled)_
- `isGamepadSource` — _(unfilled)_
- `startCountdown` — _(unfilled)_
- `onTick` — _(unfilled)_
- `onFinish` — _(unfilled)_

### `KeybindingRowLabelFormatter` — [com/sza/fastmediasorter/ui/keybinding/helpers/KeybindingRowLabelFormatter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/helpers/KeybindingRowLabelFormatter.kt)

**Layer:** ui · **LOC:** 115 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `format` — _(unfilled)_
- `resolveCommandLabel` — _(unfilled)_
- `resolveGroupLabel` — _(unfilled)_
- `formatKey` — _(unfilled)_
- `keyLabel` — _(unfilled)_
- `formatGamepadButton` — _(unfilled)_
- `formatMouseButton` — _(unfilled)_
- `formatAxis` — _(unfilled)_
- `formatVrEvent` — _(unfilled)_

### `ResetConfirmationDialog` — [com/sza/fastmediasorter/ui/keybinding/helpers/ResetConfirmationDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/helpers/ResetConfirmationDialog.kt)

**Layer:** ui · **LOC:** 32 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

**Functions:**

- `showForGroup` — _(unfilled)_
- `showForAll` — _(unfilled)_

### `KeybindingListItem` — [com/sza/fastmediasorter/ui/keybinding/KeybindingListAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingListAdapter.kt)

**Layer:** ui · **LOC:** 156 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getItemViewType` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `bind` — _(unfilled)_
- `bind` — _(unfilled)_
- `formatBindings` — _(unfilled)_
- `createHeaderView` — _(unfilled)_
- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_

### `KeybindingRemapActivity` — [com/sza/fastmediasorter/ui/keybinding/KeybindingRemapActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapActivity.kt)

**Layer:** ui · **LOC:** 159 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `getViewBinding` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `observeData` — _(unfilled)_
- `setupAdapter` — _(unfilled)_
- `getSpanSize` — _(unfilled)_
- `setupSearch` — _(unfilled)_
- `onQueryTextSubmit` — _(unfilled)_
- `onQueryTextChange` — _(unfilled)_
- `setupFragmentResults` — _(unfilled)_
- `buildDisplayItems` — _(unfilled)_
- `showCaptureDialog` — _(unfilled)_
- `dismissCaptureDialog` — _(unfilled)_
- `handlePendingConfirmation` — _(unfilled)_

### `PendingConfirmation` — [com/sza/fastmediasorter/ui/keybinding/KeybindingRemapViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapViewModel.kt)

**Layer:** ui · **LOC:** 191 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** InputBindingRepository, SetBindingUseCase, ResetBindingUseCase, ResetGroupUseCase, ResetAllUseCase, DetectConflictsUseCase, KeybindingRowLabelFormatter  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `onFilterChanged` — _(unfilled)_
- `onGroupToggle` — _(unfilled)_
- `onRemapRequested` — _(unfilled)_
- `onCaptureCompleted` — _(unfilled)_
- `onCaptureCancelled` — _(unfilled)_
- `onResetRowRequested` — _(unfilled)_
- `onResetGroupRequested` — _(unfilled)_
- `onResetAllRequested` — _(unfilled)_
- `onConfirmReset` — _(unfilled)_
- `onCancelReset` — _(unfilled)_
- `refreshState` — _(unfilled)_
- `applyFilter` — _(unfilled)_
- `buildRows` — _(unfilled)_
- `commandGroupOf` — _(unfilled)_
- `deviceOf` — _(unfilled)_

### `FilterResourceDialog` — [com/sza/fastmediasorter/ui/main/FilterResourceDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/FilterResourceDialog.kt)

**Layer:** ui · **LOC:** 241 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `setupSortSpinner` — _(unfilled)_
- `onItemSelected` — _(unfilled)_
- `onNothingSelected` — _(unfilled)_
- `setupResourceTypeChips` — _(unfilled)_
- `setupMediaTypeChips` — _(unfilled)_
- `applyFilters` — _(unfilled)_
- `clearFilters` — _(unfilled)_
- `onStart` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `newInstance` — _(unfilled)_

### `KeyboardNavigationHandler` — [com/sza/fastmediasorter/ui/main/helpers/KeyboardNavigationHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/KeyboardNavigationHandler.kt)

**Layer:** ui · **LOC:** 229 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onItemFocused` — _(unfilled)_
- `onItemSelected` — _(unfilled)_
- `getItemCount` — _(unfilled)_
- `handleKeyDown` — _(unfilled)_
- `dispatchSharedAction` — _(unfilled)_
- `ensureFocus` — _(unfilled)_
- `clearFocus` — _(unfilled)_
- `getCurrentFocusPosition` — _(unfilled)_
- `getCurrentResource` — _(unfilled)_
- `navigateUp` — _(unfilled)_
- `navigateDown` — _(unfilled)_
- `scrollToPosition` — _(unfilled)_
- `scrollPage` — _(unfilled)_
- `selectResourceAt` — _(unfilled)_

### `MainLayoutChromeManager` — [com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt)

**Layer:** ui · **LOC:** 132 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `updateToolbarButtonLabels` — _(unfilled)_
- `updateLayoutManagerForScreenSize` — _(unfilled)_
- `applyCompactToolbar` — _(unfilled)_
- `refreshGridSpacing` — _(unfilled)_
- `getItemOffsets` — _(unfilled)_

### `MainResourceTabsManager` — [com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt)

**Layer:** ui · **LOC:** 121 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `createTabs` — _(unfilled)_
- `setupListener` — _(unfilled)_
- `onTabSelected` — _(unfilled)_
- `onTabUnselected` — _(unfilled)_
- `onTabReselected` — _(unfilled)_
- `getTabIndexForResourceTab` — _(unfilled)_
- `getResourceTabForIndex` — _(unfilled)_

### `MainResumePlaybackHelper` — [com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt)

**Layer:** ui · **LOC:** 196 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `shouldAttemptResume` — _(unfilled)_
- `attemptResumePlayback` — _(unfilled)_
- `checkResourceAvailability` — _(unfilled)_
- `dismissResumeLoading` — _(unfilled)_

### `MainStoragePermissionsHelper` — [com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt)

**Layer:** ui · **LOC:** 90 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback

**Role:** _(unfilled)_

**Functions:**

- `hasFullLocalPermissions` — _(unfilled)_
- `checkLocalPermissionsOnStartup` — _(unfilled)_
- `wasStoragePermissionRequested` — _(unfilled)_
- `markStoragePermissionRequested` — _(unfilled)_
- `showStoragePermissionRequestDialog` — _(unfilled)_
- `launchStoragePermissionFlow` — _(unfilled)_

### `ResourceFilterManager` — [com/sza/fastmediasorter/ui/main/helpers/ResourceFilterManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceFilterManager.kt)

**Layer:** ui · **LOC:** 135 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `applyFiltersAndSorting` — _(unfilled)_
- `applyTabFilter` — _(unfilled)_
- `applySorting` — _(unfilled)_
- `getEffectiveTypeFilter` — _(unfilled)_

### `ResourceItemTouchCallback` — [com/sza/fastmediasorter/ui/main/helpers/ResourceItemTouchCallback.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceItemTouchCallback.kt)

**Layer:** ui · **LOC:** 87 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `isLongPressDragEnabled` — _(unfilled)_
- `isItemViewSwipeEnabled` — _(unfilled)_
- `getMovementFlags` — _(unfilled)_
- `onMove` — _(unfilled)_
- `onSwiped` — _(unfilled)_
- `onSelectedChanged` — _(unfilled)_
- `clearView` — _(unfilled)_

### `ResourceNavigationCoordinator` — [com/sza/fastmediasorter/ui/main/helpers/ResourceNavigationCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceNavigationCoordinator.kt)

**Layer:** ui · **LOC:** 195 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `validateAndNavigate` — _(unfilled)_
- `testConnectionAndNavigate` — _(unfilled)_
- `createNavigationResult` — _(unfilled)_
- `parseSubfolderCount` — _(unfilled)_

### `ResourceOrderManager` — [com/sza/fastmediasorter/ui/main/helpers/ResourceOrderManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceOrderManager.kt)

**Layer:** ui · **LOC:** 138 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `moveResourceUp` — _(unfilled)_
- `moveResourceDown` — _(unfilled)_
- `getRecommendedSortMode` — _(unfilled)_
- `saveResourceOrder` — _(unfilled)_

### `ResourcePasswordManager` — [com/sza/fastmediasorter/ui/main/helpers/ResourcePasswordManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourcePasswordManager.kt)

**Layer:** ui · **LOC:** 136 · **Last:** 2026-03-04 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `checkResourcePin` — _(unfilled)_
- `checkResourcePassword` — _(unfilled)_
- `checkResourcePinForEdit` — _(unfilled)_
- `showPinDialog` — _(unfilled)_

### `ResourceScanCoordinator` — [com/sza/fastmediasorter/ui/main/helpers/ResourceScanCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceScanCoordinator.kt)

**Layer:** ui · **LOC:** 295 · **Last:** 2026-03-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getSummaryMessageResId` — _(unfilled)_
- `getSummaryMessageArgs` — _(unfilled)_
- `hasAggregateVirtualResources` — _(unfilled)_
- `scanAllResources` — _(unfilled)_
- `scanSingleResource` — _(unfilled)_
- `processVirtualResource` — _(unfilled)_
- `processAvailableResource` — _(unfilled)_
- `getFileCount` — _(unfilled)_
- `parseSubfolderCount` — _(unfilled)_

### `MainActivity` — [com/sza/fastmediasorter/ui/main/MainActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt)

**Layer:** ui · **LOC:** 972 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `getViewBinding` — _(unfilled)_
- `onCreate` — _(unfilled)_
- `onNewIntent` — _(unfilled)_
- `openAudioPlayerFromNotification` — _(unfilled)_
- `onResume` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onConfigurationChanged` — _(unfilled)_
- `onDestroy` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `applyEdgeToEdgeInsets` — _(unfilled)_
- `observeData` — _(unfilled)_
- `updateFilterWarning` — _(unfilled)_
- `onLayoutConfigurationChanged` — _(unfilled)_
- `showError` — _(unfilled)_
- `showInfo` — _(unfilled)_
- `showDeleteConfirmation` — _(unfilled)_
- `onKeyDown` — _(unfilled)_
- `dispatchKeyEvent` — _(unfilled)_
- `routeBrowserGamepadAction` — _(unfilled)_
- `setupResourceTypeTabs` — _(unfilled)_
- `getTabIndexForResourceTab` — _(unfilled)_
- `onGenericMotionEvent` — _(unfilled)_
- `stopAudioPlaybackService` — _(unfilled)_

### `ResourceTab` — [com/sza/fastmediasorter/ui/main/MainViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt)

**Layer:** ui · **LOC:** 648 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, GetResourcesUseCase, AddResourceUseCase, UpdateResourceUseCase, DeleteResourceUseCase, ResourceRepository, MediaScannerFactory, SettingsRepository, SmbOperationsUseCase, ProvisionDefaultResourcesUseCase, MigrateCameraResourceUseCase, IoDispatcher, CoroutineDispatcher  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `equals` — _(unfilled)_
- `hashCode` — _(unfilled)_
- `getInitialState` — _(unfilled)_
- `observeResourcesFromDatabase` — _(unfilled)_
- `applyFiltersAndSorting` — _(unfilled)_
- `loadResources` — _(unfilled)_
- `selectResource` — _(unfilled)_
- `openBrowse` — _(unfilled)_
- `startPlayer` — _(unfilled)_
- `startSlideshowFor` — _(unfilled)_
- `startRandomMusicPlayback` — _(unfilled)_
- `openCameraPhotos` — _(unfilled)_
- `saveLastUsedResourceId` — _(unfilled)_
- `validateAndOpenResource` — _(unfilled)_
- `proceedAfterPasswordCheck` — _(unfilled)_
- `addResource` — _(unfilled)_
- `deleteResource` — _(unfilled)_
- `moveResourceUp` — _(unfilled)_
- `moveResourceDown` — _(unfilled)_
- `saveResourceOrder` — _(unfilled)_
- `setSortMode` — _(unfilled)_
- `setFilterByType` — _(unfilled)_
- `setFilterByMediaType` — _(unfilled)_
- `setFilterByName` — _(unfilled)_
- `clearFilters` — _(unfilled)_
- `setActiveTab` — _(unfilled)_
- `openFavorites` — _(unfilled)_
- `openResourceDirect` — _(unfilled)_
- `restorePreviousTab` — _(unfilled)_
- `copySelectedResource` — _(unfilled)_
- `generateCopyName` — _(unfilled)_
- `toggleResourceViewMode` — _(unfilled)_
- `refreshResources` — _(unfilled)_
- `scanAllResources` — _(unfilled)_
- `forceRescanAllResources` — _(unfilled)_
- `performScanAllResources` — _(unfilled)_

### `DragStartListener` — [com/sza/fastmediasorter/ui/main/ResourceAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt)

**Layer:** ui · **LOC:** 779 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onStartDrag` — _(unfilled)_
- `formatMediaTypes` — _(unfilled)_
- `isQuickSlideshowEligible` — _(unfilled)_
- `createIconSpan` — _(unfilled)_
- `submitList` — _(unfilled)_
- `moveItem` — _(unfilled)_
- `getDragOrderedList` — _(unfilled)_
- `setSelectedResource` — _(unfilled)_
- `setViewMode` — _(unfilled)_
- `setUseCompactElements` — _(unfilled)_
- `getItemViewType` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `bind` — _(unfilled)_
- `bind` — _(unfilled)_
- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_

### `AudioCoverArtLoader` — [com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt)

**Layer:** ui · **LOC:** 408 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `onAudioMetadataLoaded` — _(unfilled)_
- `setAudioEmptyStateController` — _(unfilled)_
- `hideEmptyState` — _(unfilled)_
- `loadAudioCoverArt` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `onResourceReady` — _(unfilled)_
- `searchOnlineAndDisplayCover` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `onResourceReady` — _(unfilled)_
- `pushArtworkToNotification` — _(unfilled)_
- `downloadImageBytes` — _(unfilled)_

### `AudioPlaybackService` — [com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt)

**Layer:** ui · **LOC:** 333 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `onPlaybackStateChanged` — _(unfilled)_
- `onPlayerError` — _(unfilled)_
- `seekToNext` — _(unfilled)_
- `seekToPrevious` — _(unfilled)_
- `isCommandAvailable` — _(unfilled)_
- `getAvailableCommands` — _(unfilled)_
- `onGetSession` — _(unfilled)_
- `onTaskRemoved` — _(unfilled)_
- `onDestroy` — _(unfilled)_
- `dispatchCommand` — _(unfilled)_
- `playAudio` — _(unfilled)_
- `playAudioPlaylist` — _(unfilled)_
- `onConnect` — _(unfilled)_
- `onCustomCommand` — _(unfilled)_
- `onBind` — _(unfilled)_

### `PlayerCommandPanelCallbackImpl` — [com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt)

**Layer:** ui · **LOC:** 269 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onBackClicked` — _(unfilled)_
- `onPreviousClicked` — _(unfilled)_
- `onRandomClicked` — _(unfilled)_
- `onNextClicked` — _(unfilled)_
- `onRenameClicked` — _(unfilled)_
- `onDeleteClicked` — _(unfilled)_
- `onShareClicked` — _(unfilled)_
- `onEditClicked` — _(unfilled)_
- `onUndoClicked` — _(unfilled)_
- `onFullscreenClicked` — _(unfilled)_
- `onSlideshowClicked` — _(unfilled)_
- `onCopyPanelHeaderClicked` — _(unfilled)_
- `onMovePanelHeaderClicked` — _(unfilled)_
- `onInfoClicked` — _(unfilled)_
- `onLyricsClicked` — _(unfilled)_
- `onSearchYoutubeMusicClicked` — _(unfilled)_
- `onCastClicked` — _(unfilled)_
- `onFavoriteClicked` — _(unfilled)_
- `onSearchClicked` — _(unfilled)_
- `onTranslateClicked` — _(unfilled)_
- `onOcrClicked` — _(unfilled)_
- `onGoogleLensClicked` — _(unfilled)_
- `onCopyTextClicked` — _(unfilled)_
- `onEditTextClicked` — _(unfilled)_
- `onOcrSettingsClicked` — _(unfilled)_
- `onTranslationSettingsClicked` — _(unfilled)_
- `onSleepTimerClicked` — _(unfilled)_
- `onReopenEncodingClicked` — _(unfilled)_
- `onToggleMarkdownClicked` — _(unfilled)_
- `onReaderSettingsClicked` — _(unfilled)_
- `onReadAloudClicked` — _(unfilled)_
- `onPdfScrollModeClicked` — _(unfilled)_
- `onPdfColorModeClicked` — _(unfilled)_
- `onPdfThumbnailsClicked` — _(unfilled)_
- `onEpubReaderSettingsClicked` — _(unfilled)_
- `onEpubSearchAllClicked` — _(unfilled)_
- `onPrintClicked` — _(unfilled)_
- `onSaveFrameClicked` — _(unfilled)_
- `on3dVrToggleClicked` — _(unfilled)_

### `PlayerGestureCallbackImpl` — [com/sza/fastmediasorter/ui/player/callbacks/PlayerGestureCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerGestureCallbackImpl.kt)

**Layer:** ui · **LOC:** 130 · **Last:** 2026-03-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onSwipeLeft` — _(unfilled)_
- `onSwipeRight` — _(unfilled)_
- `onSwipeUp` — _(unfilled)_
- `onSwipeDown` — _(unfilled)_
- `onDoubleTap` — _(unfilled)_
- `onLongPress` — _(unfilled)_
- `onTouchZone` — _(unfilled)_
- `setPhotoViewZoom` — _(unfilled)_

### `PlayerImageLoadingCallbackImpl` — [com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt)

**Layer:** ui · **LOC:** 70 · **Last:** 2026-03-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

**Functions:**

- `isFinishing` — _(unfilled)_
- `isDestroyed` — _(unfilled)_
- `releasePlayer` — _(unfilled)_
- `showError` — _(unfilled)_
- `showToast` — _(unfilled)_
- `getWindowManager` — _(unfilled)_
- `onAudioMetadataLoaded` — _(unfilled)_
- `updateSlideShow` — _(unfilled)_
- `getAdjacentFiles` — _(unfilled)_
- `getCurrentFile` — _(unfilled)_
- `getCurrentResource` — _(unfilled)_
- `getExoPlayer` — _(unfilled)_
- `getString` — _(unfilled)_
- `isShowingCommandPanel` — _(unfilled)_
- `isSlideshowActive` — _(unfilled)_
- `setAnimatedBadgeVisible` — _(unfilled)_

### `PlayerKeyboardCallbackImpl` — [com/sza/fastmediasorter/ui/player/callbacks/PlayerKeyboardCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerKeyboardCallbackImpl.kt)

**Layer:** ui · **LOC:** 199 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onDeleteFile` — _(unfilled)_
- `onExitPlayer` — _(unfilled)_
- `onToggleSlideshow` — _(unfilled)_
- `onShowRenameDialog` — _(unfilled)_
- `onShowFileInfo` — _(unfilled)_
- `onToggleCommandPanel` — _(unfilled)_
- `onToggleCopyPanel` — _(unfilled)_
- `canCopyCurrent` — _(unfilled)_
- `onToggleMovePanel` — _(unfilled)_
- `canMoveCurrent` — _(unfilled)_
- `onShowEditDialog` — _(unfilled)_
- `getActivePlayer` — _(unfilled)_
- `getCurrentMediaType` — _(unfilled)_
- `onPdfNextPage` — _(unfilled)_
- `onPdfPreviousPage` — _(unfilled)_
- `onPdfHome` — _(unfilled)_
- `onPdfEnd` — _(unfilled)_
- `onEpubNextPage` — _(unfilled)_
- `onEpubPreviousPage` — _(unfilled)_
- `onEpubHome` — _(unfilled)_
- `onEpubEnd` — _(unfilled)_
- `onTextScrollDown` — _(unfilled)_
- `onTextScrollUp` — _(unfilled)_
- `onTextHome` — _(unfilled)_
- `onTextEnd` — _(unfilled)_
- `onSeekForward` — _(unfilled)_
- `onSeekBackward` — _(unfilled)_
- `onEpubScrollDelta` — _(unfilled)_
- `onNavigationScroll` — _(unfilled)_
- `onToggleMute` — _(unfilled)_
- `onToggleFullscreen` — _(unfilled)_
- `onChangeVolume` — _(unfilled)_
- `onShowHelp` — _(unfilled)_
- `onDocumentSearch` — _(unfilled)_
- `onSaveCurrent` — _(unfilled)_
- `onShowContextMenu` — _(unfilled)_
- `onNextFile` — _(unfilled)_
- `onPreviousFile` — _(unfilled)_
- `onToggleFavourite` — _(unfilled)_
- `onUndoOperation` — _(unfilled)_

### `PlayerPlaybackCallbackImpl` — [com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt)

**Layer:** ui · **LOC:** 136 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onPlaybackReady` — _(unfilled)_
- `onPlaybackError` — _(unfilled)_
- `onBuffering` — _(unfilled)_
- `onPlaybackStateChanged` — _(unfilled)_
- `onPlaybackEnded` — _(unfilled)_
- `onAudioFormatChanged` — _(unfilled)_
- `showError` — _(unfilled)_
- `showFileNotFound` — _(unfilled)_
- `isActivityDestroyed` — _(unfilled)_
- `showUnsupportedFormatError` — _(unfilled)_
- `onBeforeVideoLoad` — _(unfilled)_
- `onStereoDetected` — _(unfilled)_

### `PlayerTouchZoneCallbackImpl` — [com/sza/fastmediasorter/ui/player/callbacks/PlayerTouchZoneCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerTouchZoneCallbackImpl.kt)

**Layer:** ui · **LOC:** 107 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `isOverlayBlocking` — _(unfilled)_
- `getTouchZonesEnabled` — _(unfilled)_
- `getLoadFullSizeImages` — _(unfilled)_
- `onBack` — _(unfilled)_
- `onCopy` — _(unfilled)_
- `onRename` — _(unfilled)_
- `onPrevious` — _(unfilled)_
- `onMove` — _(unfilled)_
- `onNext` — _(unfilled)_
- `onSwitchToCommandPanel` — _(unfilled)_
- `onToggleSlideshow` — _(unfilled)_
- `onDelete` — _(unfilled)_
- `onPauseResume` — _(unfilled)_
- `onSeekToStart` — _(unfilled)_
- `onSeekToEnd` — _(unfilled)_
- `onZoomIn` — _(unfilled)_
- `onZoomOut` — _(unfilled)_
- `onPageUp` — _(unfilled)_
- `onPageDown` — _(unfilled)_
- `showSlideshowEnabledMessage` — _(unfilled)_
- `updateSlideShowButton` — _(unfilled)_
- `updateSlideShow` — _(unfilled)_
- `setPhotoViewZoom` — _(unfilled)_

### `PlayerTranslationButtonCallbackImpl` — [com/sza/fastmediasorter/ui/player/callbacks/PlayerTranslationButtonCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerTranslationButtonCallbackImpl.kt)

**Layer:** ui · **LOC:** 61 · **Last:** 2026-03-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getTranslationSessionSettings` — _(unfilled)_
- `setTranslationSessionSettings` — _(unfilled)_
- `getCurrentFileType` — _(unfilled)_
- `translateCurrentImage` — _(unfilled)_
- `updateTextViewerTranslationButtonIcon` — _(unfilled)_
- `applyTextViewerFontSettings` — _(unfilled)_
- `applyTranslationManagerFontSettings` — _(unfilled)_
- `applyEpubFontSettings` — _(unfilled)_
- `forceTranslatePdf` — _(unfilled)_
- `forceTranslateText` — _(unfilled)_
- `forceTranslateEpub` — _(unfilled)_

### `PlayerUiStateCoordinatorCallbackImpl` — [com/sza/fastmediasorter/ui/player/callbacks/PlayerUiStateCoordinatorCallbackImpl.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerUiStateCoordinatorCallbackImpl.kt)

**Layer:** ui · **LOC:** 151 · **Last:** 2026-04-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `isActivityAlive` — _(unfilled)_
- `getCurrentSettings` — _(unfilled)_
- `setCurrentSettings` — _(unfilled)_
- `getCurrentFilePath` — _(unfilled)_
- `setCurrentFilePath` — _(unfilled)_
- `isImageVisible` — _(unfilled)_
- `hasImageDrawable` — _(unfilled)_
- `isSlideshowModeRequested` — _(unfilled)_
- `clearSlideshowModeRequested` — _(unfilled)_
- `hasShownHintType` — _(unfilled)_
- `markHintTypeShown` — _(unfilled)_
- `getUseTouchZones` — _(unfilled)_
- `displayImage` — _(unfilled)_
- `playVideo` — _(unfilled)_
- `displayText` — _(unfilled)_
- `displayPdf` — _(unfilled)_
- `displayEpub` — _(unfilled)_
- `adjustTouchZonesForVideo` — _(unfilled)_
- `updatePanelVisibility` — _(unfilled)_
- `updateCommandAvailability` — _(unfilled)_
- `updatePlayPauseButton` — _(unfilled)_
- `updateSlideShowButton` — _(unfilled)_
- `updateVolumeButtonsVisibility` — _(unfilled)_
- `showTouchZoneHintOverlay` — _(unfilled)_
- `showSlideshowEnabledMessage` — _(unfilled)_
- `toggleSlideShow` — _(unfilled)_
- `startSlideshow` — _(unfilled)_
- `getLatestState` — _(unfilled)_
- `forceStateUpdate` — _(unfilled)_
- `enterAudioSlideshowPhotoModeIfNeeded` — _(unfilled)_
- `updateTouchZonesHelpButtonVisibility` — _(unfilled)_
- `onFilenameOverlayFileShown` — _(unfilled)_
- `onFilenameOverlayPauseInteraction` — _(unfilled)_

### `CommandPanelController` — [com/sza/fastmediasorter/ui/player/CommandPanelController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt)

**Layer:** ui · **LOC:** 1006 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `onBackClicked` — _(unfilled)_
- `onPreviousClicked` — _(unfilled)_
- `onRandomClicked` — _(unfilled)_
- `onNextClicked` — _(unfilled)_
- `onRenameClicked` — _(unfilled)_
- `onDeleteClicked` — _(unfilled)_
- `onShareClicked` — _(unfilled)_
- `onEditClicked` — _(unfilled)_
- `onUndoClicked` — _(unfilled)_
- `onFullscreenClicked` — _(unfilled)_
- `onSlideshowClicked` — _(unfilled)_
- `onCopyPanelHeaderClicked` — _(unfilled)_
- `onMovePanelHeaderClicked` — _(unfilled)_
- `onInfoClicked` — _(unfilled)_
- `onLyricsClicked` — _(unfilled)_
- `onSearchYoutubeMusicClicked` — _(unfilled)_
- `onCastClicked` — _(unfilled)_
- `onFavoriteClicked` — _(unfilled)_
- `onSearchClicked` — _(unfilled)_
- `onTranslateClicked` — _(unfilled)_
- `onOcrClicked` — _(unfilled)_
- `onGoogleLensClicked` — _(unfilled)_
- `onCopyTextClicked` — _(unfilled)_
- `onEditTextClicked` — _(unfilled)_
- `onOcrSettingsClicked` — _(unfilled)_
- `onTranslationSettingsClicked` — _(unfilled)_
- `onSleepTimerClicked` — _(unfilled)_
- `onReopenEncodingClicked` — _(unfilled)_
- `onToggleMarkdownClicked` — _(unfilled)_
- `onReaderSettingsClicked` — _(unfilled)_
- `onReadAloudClicked` — _(unfilled)_
- `onPdfScrollModeClicked` — _(unfilled)_
- `onPdfColorModeClicked` — _(unfilled)_
- `onPdfThumbnailsClicked` — _(unfilled)_
- `onEpubReaderSettingsClicked` — _(unfilled)_
- `onEpubSearchAllClicked` — _(unfilled)_
- `onPrintClicked` — _(unfilled)_
- `onSaveFrameClicked` — _(unfilled)_
- `on3dVrToggleClicked` — _(unfilled)_
- `setupCommandPanelControls` — _(unfilled)_
- `updateCommandAvailability` — _(unfilled)_
- `logPanelGeometrySnapshot` — _(unfilled)_
- `updateSlideshowButtonColor` — _(unfilled)_
- `applySmallControlsIfNeeded` — _(unfilled)_
- `restoreCommandButtonHeightsIfNeeded` — _(unfilled)_
- `commandPanelButtons` — _(unfilled)_
- `resolveOriginalButtonHeight` — _(unfilled)_
- `updateOrientation` — _(unfilled)_
- `showOverflowMenu` — _(unfilled)_
- `getOverflowableButtons` — _(unfilled)_
- `barViewForCommand` — _(unfilled)_
- `countVisibleRightGroupButtons` — _(unfilled)_
- `shouldShowRandomNavigation` — _(unfilled)_
- `resolveAvailableCenterWidthPx` — _(unfilled)_
- `isWifiConnected` — _(unfilled)_

### `FullscreenCommandOverride` — [com/sza/fastmediasorter/ui/player/commands/PlayerCommandOverrides.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/commands/PlayerCommandOverrides.kt)

**Layer:** ui · **LOC:** 21 · **Last:** 2026-04-20 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `execute` — _(unfilled)_
- `execute` — _(unfilled)_
- `execute` — _(unfilled)_

### `PlaybackCommand` — [com/sza/fastmediasorter/ui/player/contracts/PlaybackCommandModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackCommandModel.kt)

**Layer:** ui · **LOC:** 105 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `forVrPlayback` — _(unfilled)_
- `forStandardPlayback` — _(unfilled)_

### `PlaybackPreferencesFacade` — [com/sza/fastmediasorter/ui/player/contracts/PlaybackPreferencesFacade.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackPreferencesFacade.kt)

**Layer:** ui · **LOC:** 17 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getPlaybackSpeed` — _(unfilled)_
- `getResumePosition` — _(unfilled)_
- `saveResumePosition` — _(unfilled)_
- `getPreferredSubtitleTrackIndex` — _(unfilled)_
- `isStereoPlaybackEnabled` — _(unfilled)_

### `PlayerHostCapabilities` — [com/sza/fastmediasorter/ui/player/contracts/PlayerHostCapabilities.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlayerHostCapabilities.kt)

**Layer:** ui · **LOC:** 87 · **Last:** 2026-04-24 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

**Functions:**

- `setStereoMode` — _(unfilled)_
- `rememberStereoModeIfEnabled` — _(unfilled)_
- `showMessage` — _(unfilled)_
- `requestFinishAfterDelete` — _(unfilled)_

### `StereoDetectionFacade` — [com/sza/fastmediasorter/ui/player/contracts/StereoDetectionFacade.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/StereoDetectionFacade.kt)

**Layer:** ui · **LOC:** 44 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** StereoDetector  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `detectFromDimensions` — _(unfilled)_
- `isStereoContent` — _(unfilled)_
- `detectFromDimensions` — _(unfilled)_
- `isStereoContent` — _(unfilled)_

### `VideoPlayerHandle` — [com/sza/fastmediasorter/ui/player/contracts/VideoPlayerHandle.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/VideoPlayerHandle.kt)

**Layer:** ui · **LOC:** 43 · **Last:** 2026-04-24 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getAvailableAudioTracks` — _(unfilled)_
- `selectAudioTrack` — _(unfilled)_
- `getAvailableSubtitleTracks` — _(unfilled)_
- `selectSubtitleTrack` — _(unfilled)_
- `getHueAdjustmentDegrees` — _(unfilled)_
- `setHueAdjustmentDegrees` — _(unfilled)_
- `getBrightnessProgress` — _(unfilled)_
- `setBrightnessProgress` — _(unfilled)_
- `getBrightnessPercentOffset` — _(unfilled)_
- `getPlaybackSpeed` — _(unfilled)_
- `setPlaybackSpeed` — _(unfilled)_

### `DynamicBackgroundProcessor` — [com/sza/fastmediasorter/ui/player/DynamicBackgroundProcessor.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/DynamicBackgroundProcessor.kt)

**Layer:** ui · **LOC:** 253 · **Last:** 2026-04-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `process` — _(unfilled)_
- `clear` — _(unfilled)_
- `drawableToBitmap` — _(unfilled)_
- `buildBackgroundBitmap` — _(unfilled)_
- `smoothColorArray` — _(unfilled)_
- `processFromBitmap` — _(unfilled)_
- `applyBackground` — _(unfilled)_

### `PlayerEntryCoordinator` — [com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt)

**Layer:** ui · **LOC:** 64 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `resolveEntry` — _(unfilled)_
- `resolveEntry` — _(unfilled)_

### `VrTaskTransition` — [com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt)

**Layer:** ui · **LOC:** 125 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `shouldEnterImmersiveTask` — _(unfilled)_
- `enterImmersive` — _(unfilled)_
- `exitImmersiveToPanel` — _(unfilled)_
- `exitImmersiveToFlatPlayer` — _(unfilled)_

### `PhotoSphereMetadataReader` — [com/sza/fastmediasorter/ui/player/ExifPhotoSphereReader.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ExifPhotoSphereReader.kt)

**Layer:** ui · **LOC:** 107 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `read` — _(unfilled)_
- `is180Projection` — _(unfilled)_
- `read` — _(unfilled)_
- `parseXmp` — _(unfilled)_
- `containsProjectionTypeMarker` — _(unfilled)_
- `readEmbeddedXmp` — _(unfilled)_
- `extractIntValue` — _(unfilled)_

### `FileOperationsHandler` — [com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt)

**Layer:** ui · **LOC:** 534 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCopySuccess` — _(unfilled)_
- `onMoveSuccess` — _(unfilled)_
- `onDeleteSuccess` — _(unfilled)_
- `onOperationError` — _(unfilled)_
- `onAuthenticationRequired` — _(unfilled)_
- `onBatchDeletePermissionRequired` — _(unfilled)_
- `getCurrentFile` — _(unfilled)_
- `getCurrentResource` — _(unfilled)_
- `performCopy` — _(unfilled)_
- `performMove` — _(unfilled)_
- `performDelete` — _(unfilled)_
- `performShare` — _(unfilled)_
- `shareNetworkFileWithProgress` — _(unfilled)_
- `cleanupOldShareTempFiles` — _(unfilled)_
- `checkSmbDestinationReachability` — _(unfilled)_
- `shareLocalFile` — _(unfilled)_
- `deleteCurrentFile` — _(unfilled)_
- `createNetworkAwareFile` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getName` — _(unfilled)_

### `AnimatedImageController` — [com/sza/fastmediasorter/ui/player/helpers/AnimatedImageController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AnimatedImageController.kt)

**Layer:** ui · **LOC:** 104 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `isAnimatedContent` — _(unfilled)_
- `onDrawableLoaded` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onResume` — _(unfilled)_
- `prepareForNewContent` — _(unfilled)_
- `hasAnimatedDrawable` — _(unfilled)_
- `isPlaybackPaused` — _(unfilled)_
- `togglePlayback` — _(unfilled)_
- `release` — _(unfilled)_
- `clearCurrentAnimation` — _(unfilled)_

### `AudioBackgroundPhotosManager` — [com/sza/fastmediasorter/ui/player/helpers/AudioBackgroundPhotosManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioBackgroundPhotosManager.kt)

**Layer:** ui · **LOC:** 334 · **Last:** 2026-03-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, ResourceRepository, GetMediaFilesUseCase  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `setOnPhotoChangedListener` — _(unfilled)_
- `setOnErrorListener` — _(unfilled)_
- `initialize` — _(unfilled)_
- `updateState` — _(unfilled)_
- `loadPhotosPlaylist` — _(unfilled)_
- `onProgress` — _(unfilled)_
- `onComplete` — _(unfilled)_
- `shouldStop` — _(unfilled)_
- `getCurrentPhoto` — _(unfilled)_
- `getNextPhoto` — _(unfilled)_
- `advanceToNextPhoto` — _(unfilled)_
- `deactivate` — _(unfilled)_
- `release` — _(unfilled)_
- `getPhotoResourceType` — _(unfilled)_
- `getPhotoCredentialsId` — _(unfilled)_

### `AudioBreathingBarsView` — [com/sza/fastmediasorter/ui/player/helpers/AudioBreathingBarsView.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioBreathingBarsView.kt)

**Layer:** ui · **LOC:** 225 · **Last:** 2026-03-08 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `startAnimation` — _(unfilled)_
- `pauseAnimation` — _(unfilled)_
- `stopAndReset` — _(unfilled)_
- `onDraw` — _(unfilled)_
- `drawBars` — _(unfilled)_
- `drawRings` — _(unfilled)_
- `regenerateRingColors` — _(unfilled)_
- `onDetachedFromWindow` — _(unfilled)_

### `AudioEmptyStateController` — [com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt)

**Layer:** ui · **LOC:** 337 · **Last:** 2026-03-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `show` — _(unfilled)_
- `hide` — _(unfilled)_
- `onIsPlayingChanged` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onResume` — _(unfilled)_
- `release` — _(unfilled)_
- `hideAll` — _(unfilled)_
- `showStaticNote` — _(unfilled)_
- `showPulseNote` — _(unfilled)_
- `showBars` — _(unfilled)_
- `showWaves` — _(unfilled)_
- `showVideo` — _(unfilled)_
- `onSurfaceTextureAvailable` — _(unfilled)_
- `onSurfaceTextureSizeChanged` — _(unfilled)_
- `onSurfaceTextureDestroyed` — _(unfilled)_
- `onSurfaceTextureUpdated` — _(unfilled)_
- `startMediaPlayer` — _(unfilled)_
- `applyCenterCropTransform` — _(unfilled)_
- `releaseMediaPlayer` — _(unfilled)_
- `stopBars` — _(unfilled)_
- `stopWaves` — _(unfilled)_

### `AudioFocusManager` — [com/sza/fastmediasorter/ui/player/helpers/AudioFocusManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioFocusManager.kt)

**Layer:** ui · **LOC:** 100 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `requestFocus` — _(unfilled)_
- `releaseFocus` — _(unfilled)_
- `requestFocusApi26` — _(unfilled)_
- `requestFocusLegacy` — _(unfilled)_

### `AudioInfoDisplayHelper` — [com/sza/fastmediasorter/ui/player/helpers/AudioInfoDisplayHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioInfoDisplayHelper.kt)

**Layer:** ui · **LOC:** 135 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getString` — _(unfilled)_
- `getExoPlayer` — _(unfilled)_
- `showAudioFileInfo` — _(unfilled)_
- `updateAudioFormatInfo` — _(unfilled)_
- `buildAudioInfoLine` — _(unfilled)_
- `parseArtistFromPath` — _(unfilled)_
- `formatDuration` — _(unfilled)_

### `AudioServiceController` — [com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt)

**Layer:** ui · **LOC:** 243 · **Last:** 2026-04-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `connect` — _(unfilled)_
- `playAudio` — _(unfilled)_
- `playAudioWithMetadata` — _(unfilled)_
- `playAudioPlaylist` — _(unfilled)_
- `playAudioPlaylistWithMetadata` — _(unfilled)_
- `connectForStatus` — _(unfilled)_
- `release` — _(unfilled)_

### `AudioSlideshowPhotoModeManager` — [com/sza/fastmediasorter/ui/player/helpers/AudioSlideshowPhotoModeManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioSlideshowPhotoModeManager.kt)

**Layer:** ui · **LOC:** 394 · **Last:** 2026-02-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `updateSlideShowButton` — _(unfilled)_
- `updateSystemBarsForPlayer` — _(unfilled)_
- `toggleSlideshow` — _(unfilled)_
- `updateSlideshowState` — _(unfilled)_
- `getSupportActionBar` — _(unfilled)_
- `enter` — _(unfilled)_
- `exit` — _(unfilled)_
- `enforceUI` — _(unfilled)_
- `loadBackgroundPhoto` — _(unfilled)_
- `onResourceReady` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `preloadNextPhoto` — _(unfilled)_
- `advancePhoto` — _(unfilled)_
- `updatePhotoLabel` — _(unfilled)_
- `updateCurrentSongLabel` — _(unfilled)_

### `AudioWaveParticleView` — [com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt)

**Layer:** ui · **LOC:** 385 · **Last:** 2026-04-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onSizeChanged` — _(unfilled)_
- `randomizeParams` — _(unfilled)_
- `initParticles` — _(unfilled)_
- `onDraw` — _(unfilled)_
- `onDetachedFromWindow` — _(unfilled)_
- `startAnimation` — _(unfilled)_
- `pauseAnimation` — _(unfilled)_
- `stopAndReset` — _(unfilled)_
- `tick` — _(unfilled)_
- `hslToArgb` — _(unfilled)_

### `BackgroundMusicManager` — [com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt)

**Layer:** ui · **LOC:** 556 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, ResourceRepository, GetMediaFilesUseCase, DownloadNetworkFileUseCase, UnifiedFileCache  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `setOnTrackChangedListener` — _(unfilled)_
- `setOnMusicErrorListener` — _(unfilled)_
- `initialize` — _(unfilled)_
- `initializeInternal` — _(unfilled)_
- `onPlaybackStateChanged` — _(unfilled)_
- `onPlayerError` — _(unfilled)_
- `updateState` — _(unfilled)_
- `loadAndSetPlaylist` — _(unfilled)_
- `skipToNextRandomTrack` — _(unfilled)_
- `prepareMediaItem` — _(unfilled)_
- `startHealthCheck` — _(unfilled)_
- `release` — _(unfilled)_
- `setVolume` — _(unfilled)_

### `BaseDocumentViewerManager` — [com/sza/fastmediasorter/ui/player/helpers/BaseDocumentViewerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BaseDocumentViewerManager.kt)

**Layer:** ui · **LOC:** 135 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `handleTouchZones` — _(unfilled)_
- `handleFullscreenTouchZones` — _(unfilled)_
- `handleNormalTouchZones` — _(unfilled)_
- `onPreviousPageRequest` — _(unfilled)_
- `onNextPageRequest` — _(unfilled)_
- `onExitFullscreenRequest` — _(unfilled)_
- `isInFullscreenMode` — _(unfilled)_

### `CastMediaManager` — [com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt)

**Layer:** ui · **LOC:** 355 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onSessionStarted` — _(unfilled)_
- `onSessionEnded` — _(unfilled)_
- `onSessionSuspended` — _(unfilled)_
- `onSessionResumed` — _(unfilled)_
- `onSessionStartFailed` — _(unfilled)_
- `onSessionResumeFailed` — _(unfilled)_
- `onSessionStarting` — _(unfilled)_
- `onSessionEnding` — _(unfilled)_
- `onSessionResuming` — _(unfilled)_
- `init` — _(unfilled)_
- `release` — _(unfilled)_
- `showCastDialog` — _(unfilled)_
- `sendCurrentMedia` — _(unfilled)_
- `resolveAndSend` — _(unfilled)_
- `loadMediaOnReceiver` — _(unfilled)_
- `downloadToTemp` — _(unfilled)_
- `openRemoteInputStream` — _(unfilled)_
- `handleSessionEnd` — _(unfilled)_
- `deleteTempFile` — _(unfilled)_
- `isWifiConnected` — _(unfilled)_

### `CloudPlaybackHelper` — [com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt)

**Layer:** ui · **LOC:** 71 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `playCloudVideo` — _(unfilled)_

### `CommandPanelLayoutPlanner` — [com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt)

**Layer:** ui · **LOC:** 284 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `buildActiveCommands` — _(unfilled)_
- `planLayout` — _(unfilled)_

### `DestinationButtonsManager` — [com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt)

**Layer:** ui · **LOC:** 397 · **Last:** 2026-03-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCopyClicked` — _(unfilled)_
- `onMoveClicked` — _(unfilled)_
- `getCurrentResourceId` — _(unfilled)_
- `onUpdateCommandAvailability` — _(unfilled)_
- `isCommandPanelVisible` — _(unfilled)_
- `populateDestinationButtons` — _(unfilled)_
- `createButtonRow` — _(unfilled)_
- `clearSettingsCache` — _(unfilled)_
- `createDestinationButton` — _(unfilled)_
- `toggleCopyPanel` — _(unfilled)_
- `toggleMovePanel` — _(unfilled)_
- `updateCopyPanelVisibility` — _(unfilled)_
- `updateMovePanelVisibility` — _(unfilled)_
- `updateContainerOrientation` — _(unfilled)_

### `DocumentPrintManager` — [com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt)

**Layer:** ui · **LOC:** 304 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `printCurrentFile` — _(unfilled)_
- `dispatchPrint` — _(unfilled)_
- `prepareLocalFile` — _(unfilled)_
- `printPdf` — _(unfilled)_
- `printImage` — _(unfilled)_
- `printText` — _(unfilled)_
- `onPageFinished` — _(unfilled)_
- `showSnackbar` — _(unfilled)_
- `onLayout` — _(unfilled)_
- `onWrite` — _(unfilled)_
- `onFinish` — _(unfilled)_

### `DocumentSelectionActionModeCallback` — [com/sza/fastmediasorter/ui/player/helpers/DocumentSelectionActionModeCallback.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentSelectionActionModeCallback.kt)

**Layer:** ui · **LOC:** 91 · **Last:** 2026-04-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onCreateActionMode` — _(unfilled)_
- `onPrepareActionMode` — _(unfilled)_
- `onActionItemClicked` — _(unfilled)_
- `onDestroyActionMode` — _(unfilled)_
- `openGoogleSearch` — _(unfilled)_

### `EpubSearchResult` — [com/sza/fastmediasorter/ui/player/helpers/EpubSearchResult.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubSearchResult.kt)

**Layer:** ui · **LOC:** 18 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `EpubSearchResultAdapter` — [com/sza/fastmediasorter/ui/player/helpers/EpubSearchResultAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubSearchResultAdapter.kt)

**Layer:** ui · **LOC:** 68 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `getItemCount` — _(unfilled)_

### `EpubStyleManager` — [com/sza/fastmediasorter/ui/player/helpers/EpubStyleManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubStyleManager.kt)

**Layer:** ui · **LOC:** 121 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `fromName` — _(unfilled)_
- `generateCss` — _(unfilled)_

### `EpubTocAdapter` — [com/sza/fastmediasorter/ui/player/helpers/EpubTocAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubTocAdapter.kt)

**Layer:** ui · **LOC:** 70 · **Last:** 2026-03-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `getItemCount` — _(unfilled)_
- `findCurrentChapterPosition` — _(unfilled)_

### `EpubTtsDelegate` — [com/sza/fastmediasorter/ui/player/helpers/EpubTtsDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubTtsDelegate.kt)

**Layer:** ui · **LOC:** 92 · **Last:** 2026-04-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `toggle` — _(unfilled)_
- `stop` — _(unfilled)_
- `speakText` — _(unfilled)_
- `release` — _(unfilled)_
- `isReading` — _(unfilled)_
- `ensureManager` — _(unfilled)_
- `cleanJsString` — _(unfilled)_

### `EpubViewerManager` — [com/sza/fastmediasorter/ui/player/helpers/EpubViewerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubViewerManager.kt)

**Layer:** ui · **LOC:** 2176 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk, prefs  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showError` — _(unfilled)_
- `displayTranslatedText` — _(unfilled)_
- `onEnterFullscreenMode` — _(unfilled)_
- `onExitFullscreenMode` — _(unfilled)_
- `onSelectionChanged` — _(unfilled)_
- `onDown` — _(unfilled)_
- `onFling` — _(unfilled)_
- `setupTranslationOverlayGestures` — _(unfilled)_
- `onFling` — _(unfilled)_
- `onSingleTapConfirmed` — _(unfilled)_
- `closeTranslationOverlay` — _(unfilled)_
- `increaseTranslationFontSize` — _(unfilled)_
- `decreaseTranslationFontSize` — _(unfilled)_
- `applyTranslationFontSize` — _(unfilled)_
- `saveTranslationFontSize` — _(unfilled)_
- `displayEpub` — _(unfilled)_
- `showChapter` — _(unfilled)_
- `preprocessHtml` — _(unfilled)_
- `convertResourceToDataUri` — _(unfilled)_
- `findImageResource` — _(unfilled)_
- `findImageResourceByPath` — _(unfilled)_
- `resolveResourcePath` — _(unfilled)_
- `updateChapterIndicator` — _(unfilled)_
- `showGoToChapterDialog` — _(unfilled)_
- `showPreviousChapter` — _(unfilled)_
- `showNextChapter` — _(unfilled)_
- `showFirstChapter` — _(unfilled)_
- `onPreviousPageRequest` — _(unfilled)_
- `onNextPageRequest` — _(unfilled)_
- `onExitFullscreenRequest` — _(unfilled)_
- `isInFullscreenMode` — _(unfilled)_
- `enterFullscreenMode` — _(unfilled)_
- `exitFullscreenMode` — _(unfilled)_
- `getOrCreateWebView` — _(unfilled)_
- `configureWebView` — _(unfilled)_
- `onPageFinished` — _(unfilled)_
- `shouldInterceptRequest` — _(unfilled)_
- `closeEpubBook` — _(unfilled)_
- `toggleReadAloud` — _(unfilled)_
- `stopTtsOnChapterChange` — _(unfilled)_
- `release` — _(unfilled)_
- `getCurrentProgress` — _(unfilled)_
- `increaseFontSize` — _(unfilled)_
- `decreaseFontSize` — _(unfilled)_
- `getCurrentFontSize` — _(unfilled)_
- `saveFontSize` — _(unfilled)_
- `reloadCurrentChapter` — _(unfilled)_
- `showReaderSettingsDialog` — _(unfilled)_
- `saveReaderSettings` — _(unfilled)_
- `showTableOfContents` — _(unfilled)_
- `flattenToc` — _(unfilled)_
- `findSpineIndexForResource` — _(unfilled)_
- `showSpineBasedToc` — _(unfilled)_
- `searchInEpub` — _(unfilled)_
- `nextSearchMatch` — _(unfilled)_
- `previousSearchMatch` — _(unfilled)_
- `showCrossChapterSearch` — _(unfilled)_
- `performCrossChapterSearch` — _(unfilled)_
- `clearSearch` — _(unfilled)_
- `extractTextFromCurrentChapter` — _(unfilled)_
- `toggleTranslation` — _(unfilled)_
- `forceTranslate` — _(unfilled)_
- `handleTranslateSelection` — _(unfilled)_
- `translateCurrentChapter` — _(unfilled)_
- `updateTranslateButtonIcon` — _(unfilled)_
- `checkAndHideControlsAtBottom` — _(unfilled)_
- `checkAndExitFullscreenAtBottom` — _(unfilled)_
- `applyFontSettings` — _(unfilled)_
- `scrollToHome` — _(unfilled)_
- `scrollToEnd` — _(unfilled)_
- `getSelectionActionModeCallback` — _(unfilled)_

### `ExoPlayerControlsManager` — [com/sza/fastmediasorter/ui/player/helpers/ExoPlayerControlsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ExoPlayerControlsManager.kt)

**Layer:** ui · **LOC:** 136 · **Last:** 2026-04-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onPreviousFile` — _(unfilled)_
- `onNextFile` — _(unfilled)_
- `showPlaybackControlDialog` — _(unfilled)_
- `setupExoPlayerNavigationButtons` — _(unfilled)_
- `updateRepeatButtonIcon` — _(unfilled)_
- `updateTrackButtonsVisibility` — _(unfilled)_

### `FileCopyProgressDialog` — [com/sza/fastmediasorter/ui/player/helpers/FileCopyProgressDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FileCopyProgressDialog.kt)

**Layer:** ui · **LOC:** 63 · **Last:** 2026-03-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `updateProgress` — _(unfilled)_
- `showIndeterminate` — _(unfilled)_
- `formatSpeed` — _(unfilled)_

### `FilenameOverlayAutoHideManager` — [com/sza/fastmediasorter/ui/player/helpers/FilenameOverlayAutoHideManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FilenameOverlayAutoHideManager.kt)

**Layer:** ui · **LOC:** 247 · **Last:** 2026-04-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `onFileShown` — _(unfilled)_
- `onPauseInteraction` — _(unfilled)_
- `onZoomInteraction` — _(unfilled)_
- `onEnterCommandPanelMode` — _(unfilled)_
- `onEnterFullscreenMode` — _(unfilled)_
- `onHostPause` — _(unfilled)_
- `onHostResume` — _(unfilled)_
- `cancel` — _(unfilled)_
- `timeoutForType` — _(unfilled)_
- `triggerReShowOrExtend` — _(unfilled)_
- `scheduleHide` — _(unfilled)_
- `animateShow` — _(unfilled)_
- `animateHide` — _(unfilled)_

### `FtpPlaybackHelper` — [com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt)

**Layer:** ui · **LOC:** 103 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `playFtpVideo` — _(unfilled)_

### `GoogleLensButtonsManager` — [com/sza/fastmediasorter/ui/player/helpers/GoogleLensButtonsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/GoogleLensButtonsManager.kt)

**Layer:** ui · **LOC:** 93 · **Last:** 2026-02-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `setupButtons` — _(unfilled)_

### `GoogleLensTranslationHelper` — [com/sza/fastmediasorter/ui/player/helpers/GoogleLensTranslationHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/GoogleLensTranslationHelper.kt)

**Layer:** ui · **LOC:** 167 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `translateBitmap` — _(unfilled)_
- `hide` — _(unfilled)_
- `isVisible` — _(unfilled)_

### `ImageOcrManager` — [com/sza/fastmediasorter/ui/player/helpers/ImageOcrManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageOcrManager.kt)

**Layer:** ui · **LOC:** 177 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `showError` — _(unfilled)_
- `getString` — _(unfilled)_
- `getString` — _(unfilled)_
- `extractTextFromCurrentImage` — _(unfilled)_
- `extractBitmapFromImageView` — _(unfilled)_
- `extractBitmapFromDrawable` — _(unfilled)_

### `LanguageBadgeDrawable` — [com/sza/fastmediasorter/ui/player/helpers/LanguageBadgeDrawable.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LanguageBadgeDrawable.kt)

**Layer:** ui · **LOC:** 119 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `resolveThemeColor` — _(unfilled)_
- `updateLanguages` — _(unfilled)_
- `draw` — _(unfilled)_
- `setAlpha` — _(unfilled)_
- `setColorFilter` — _(unfilled)_
- `getOpacity` — _(unfilled)_
- `getIntrinsicWidth` — _(unfilled)_
- `getIntrinsicHeight` — _(unfilled)_

### `LocalPlaybackHelper` — [com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt)

**Layer:** ui · **LOC:** 211 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `playLocalVideo` — _(unfilled)_
- `playLocalVideoInternal` — _(unfilled)_
- `resolveContentUriForPath` — _(unfilled)_
- `normalizeLocalPath` — _(unfilled)_
- `getMimeTypeFromPath` — _(unfilled)_
- `createMediaItem` — _(unfilled)_

### `LyricsManager` — [com/sza/fastmediasorter/ui/player/helpers/LyricsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LyricsManager.kt)

**Layer:** ui · **LOC:** 208 · **Last:** 2026-04-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `searchAndShowLyrics` — _(unfilled)_
- `showLyricsViewer` — _(unfilled)_
- `hideLyricsViewer` — _(unfilled)_
- `ensureTtsManager` — _(unfilled)_

### `MediaDisplayCoordinator` — [com/sza/fastmediasorter/ui/player/helpers/MediaDisplayCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/MediaDisplayCoordinator.kt)

**Layer:** ui · **LOC:** 56 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `displayImage` — _(unfilled)_
- `playVideo` — _(unfilled)_
- `displayText` — _(unfilled)_
- `displayPdf` — _(unfilled)_
- `displayEpub` — _(unfilled)_
- `display` — _(unfilled)_

### `NetworkFileManager` — [com/sza/fastmediasorter/ui/player/helpers/NetworkFileManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NetworkFileManager.kt)

**Layer:** ui · **LOC:** 387 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getCurrentResource` — _(unfilled)_
- `showError` — _(unfilled)_
- `prepareFileForRead` — _(unfilled)_
- `copyUriToTemp` — _(unfilled)_
- `copyFileToTemp` — _(unfilled)_
- `copyContentUriToTemp` — _(unfilled)_
- `prepareFileForWrite` — _(unfilled)_
- `uploadEditedFile` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `clearEditingCache` — _(unfilled)_
- `downloadNetworkFileForRead` — _(unfilled)_
- `downloadNetworkFileForWrite` — _(unfilled)_
- `downloadSmbFileForRead` — _(unfilled)_
- `downloadSftpFileForRead` — _(unfilled)_
- `downloadFtpFileForRead` — _(unfilled)_
- `downloadCloudFileForRead` — _(unfilled)_

### `NowPlayingManager` — [com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt)

**Layer:** ui · **LOC:** 202 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `startPlayback` — _(unfilled)_
- `buildPlaybackUri` — _(unfilled)_
- `onStart` — _(unfilled)_
- `updateBarVisibility` — _(unfilled)_
- `updateMiniPlayPauseIcon` — _(unfilled)_
- `showBottomSheet` — _(unfilled)_

### `PanelStereoSingleEyeNotifier` — [com/sza/fastmediasorter/ui/player/helpers/PanelStereoSingleEyeNotifier.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PanelStereoSingleEyeNotifier.kt)

**Layer:** ui · **LOC:** 59 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `notifyIfFirstThisSession` — _(unfilled)_
- `resetForNewSession` — _(unfilled)_

### `PdfBitmapCache` — [com/sza/fastmediasorter/ui/player/helpers/PdfBitmapCache.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfBitmapCache.kt)

**Layer:** ui · **LOC:** 73 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `entryRemoved` — _(unfilled)_
- `sizeOf` — _(unfilled)_
- `get` — _(unfilled)_
- `put` — _(unfilled)_
- `remove` — _(unfilled)_
- `clear` — _(unfilled)_
- `size` — _(unfilled)_

### `PdfColorConversion` — [com/sza/fastmediasorter/ui/player/helpers/PdfColorConversion.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfColorConversion.kt)

**Layer:** ui · **LOC:** 80 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getColorFilter` — _(unfilled)_
- `nextMode` — _(unfilled)_
- `fromName` — _(unfilled)_

### `PdfPageAdapter` — [com/sza/fastmediasorter/ui/player/helpers/PdfPageAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfPageAdapter.kt)

**Layer:** ui · **LOC:** 109 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `onViewRecycled` — _(unfilled)_
- `getItemCount` — _(unfilled)_
- `setColorFilter` — _(unfilled)_
- `invalidateCache` — _(unfilled)_

### `PdfRendererWrapper` — [com/sza/fastmediasorter/ui/player/helpers/PdfRendererWrapper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfRendererWrapper.kt)

**Layer:** ui · **LOC:** 104 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `renderPage` — _(unfilled)_
- `getPageDimensions` — _(unfilled)_
- `close` — _(unfilled)_

### `PdfTextSelectionManager` — [com/sza/fastmediasorter/ui/player/helpers/PdfTextSelectionManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfTextSelectionManager.kt)

**Layer:** ui · **LOC:** 201 · **Last:** 2026-04-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `isInTextSelectionMode` — _(unfilled)_
- `enterTextSelectionMode` — _(unfilled)_
- `exitTextSelectionMode` — _(unfilled)_
- `extractPageTextForTts` — _(unfilled)_
- `extractPageText` — _(unfilled)_
- `extractTextNative` — _(unfilled)_
- `extractTextOcr` — _(unfilled)_

### `PdfThumbnailAdapter` — [com/sza/fastmediasorter/ui/player/helpers/PdfThumbnailAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfThumbnailAdapter.kt)

**Layer:** ui · **LOC:** 136 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `entryRemoved` — _(unfilled)_
- `sizeOf` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `onViewRecycled` — _(unfilled)_
- `getItemCount` — _(unfilled)_
- `setCurrentPage` — _(unfilled)_
- `clearCache` — _(unfilled)_

### `PdfTtsDelegate` — [com/sza/fastmediasorter/ui/player/helpers/PdfTtsDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfTtsDelegate.kt)

**Layer:** ui · **LOC:** 88 · **Last:** 2026-04-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `toggle` — _(unfilled)_
- `speakText` — _(unfilled)_
- `stop` — _(unfilled)_
- `release` — _(unfilled)_
- `isReading` — _(unfilled)_
- `ensureManager` — _(unfilled)_

### `PdfViewerManager` — [com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt)

**Layer:** ui · **LOC:** 1641 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showError` — _(unfilled)_
- `onEnterFullscreenMode` — _(unfilled)_
- `onExitFullscreenMode` — _(unfilled)_
- `displayOcrText` — _(unfilled)_
- `displayTranslatedText` — _(unfilled)_
- `shareFileToGoogleLens` — _(unfilled)_
- `isLandscapeMode` — _(unfilled)_
- `getCurrentPageBitmap` — _(unfilled)_
- `toggleTranslationOverlaySize` — _(unfilled)_
- `showGoToPageDialog` — _(unfilled)_
- `displayPdf` — _(unfilled)_
- `showPreviousPage` — _(unfilled)_
- `showNextPage` — _(unfilled)_
- `showFirstPage` — _(unfilled)_
- `setupPageMode` — _(unfilled)_
- `setupScrollMode` — _(unfilled)_
- `onScrolled` — _(unfilled)_
- `toggleScrollMode` — _(unfilled)_
- `isInScrollMode` — _(unfilled)_
- `toggleColorMode` — _(unfilled)_
- `getCurrentColorModeName` — _(unfilled)_
- `showThumbnailNavigation` — _(unfilled)_
- `toggleTranslation` — _(unfilled)_
- `forceTranslate` — _(unfilled)_
- `extractTextFromCurrentPage` — _(unfilled)_
- `copyPageTextToClipboard` — _(unfilled)_
- `translateCurrentPage` — _(unfilled)_
- `translateCurrentPageOverlay` — _(unfilled)_
- `translateCurrentPageLensStyle` — _(unfilled)_
- `toggleReadAloud` — _(unfilled)_
- `speakText` — _(unfilled)_
- `stopTtsOnPageChange` — _(unfilled)_
- `releaseTts` — _(unfilled)_
- `close` — _(unfilled)_
- `saveCurrentPagePosition` — _(unfilled)_
- `showPdfPage` — _(unfilled)_
- `enterFullscreenMode` — _(unfilled)_
- `exitFullscreenMode` — _(unfilled)_
- `isInFullscreenMode` — _(unfilled)_
- `isPdfActive` — _(unfilled)_
- `handlePdfFling` — _(unfilled)_
- `handlePdfLongPress` — _(unfilled)_
- `onPreviousPageRequest` — _(unfilled)_
- `onNextPageRequest` — _(unfilled)_
- `onExitFullscreenRequest` — _(unfilled)_
- `clearTranslationOverlays` — _(unfilled)_
- `updateButtonVisibility` — _(unfilled)_
- `closePdfRenderer` — _(unfilled)_
- `searchInPdf` — _(unfilled)_
- `nextSearchResult` — _(unfilled)_
- `previousSearchResult` — _(unfilled)_
- `getSearchState` — _(unfilled)_
- `extractLinksNative` — _(unfilled)_
- `handlePdfTap` — _(unfilled)_
- `openUrlInBrowser` — _(unfilled)_
- `shareCurrentPageToGoogleLens` — _(unfilled)_

### `PictureInPictureManager` — [com/sza/fastmediasorter/ui/player/helpers/PictureInPictureManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PictureInPictureManager.kt)

**Layer:** ui · **LOC:** 238 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `setupPipButton` — _(unfilled)_
- `enterPictureInPicture` — _(unfilled)_
- `onUserLeaveHint` — _(unfilled)_
- `onPictureInPictureModeChanged` — _(unfilled)_
- `updatePipActions` — _(unfilled)_
- `release` — _(unfilled)_
- `enterPipApi31` — _(unfilled)_
- `buildPipParams` — _(unfilled)_
- `createRemoteAction` — _(unfilled)_
- `registerPipReceiver` — _(unfilled)_
- `onReceive` — _(unfilled)_
- `unregisterPipReceiver` — _(unfilled)_

### `PlaybackHealthHelper` — [com/sza/fastmediasorter/ui/player/helpers/PlaybackHealthHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackHealthHelper.kt)

**Layer:** ui · **LOC:** 212 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `startPlaybackHealthCheck` — _(unfilled)_
- `checkPlaybackHealth` — _(unfilled)_
- `cancelPlaybackHealthCheck` — _(unfilled)_
- `playWithMediaPlayer` — _(unfilled)_
- `releaseMediaPlayer` — _(unfilled)_

### `PlaybackPositionHelper` — [com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionHelper.kt)

**Layer:** ui · **LOC:** 113 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `startPositionSaving` — _(unfilled)_
- `run` — _(unfilled)_
- `stopPositionSaving` — _(unfilled)_
- `saveCurrentPosition` — _(unfilled)_
- `seekForward` — _(unfilled)_
- `seekBackward` — _(unfilled)_
- `formatTime` — _(unfilled)_

### `PlayerAudioMetadataManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerAudioMetadataManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerAudioMetadataManager.kt)

**Layer:** ui · **LOC:** 109 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `getCachedMetadataFor` — _(unfilled)_
- `onMetadataLoaded` — _(unfilled)_
- `searchInYoutubeMusic` — _(unfilled)_

### `PlayerBindingSafeViews` — [com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt)

**Layer:** ui · **LOC:** 367 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `required` — _(unfilled)_
- `required` — _(unfilled)_
- `ensureLyricsInflated` — _(unfilled)_
- `requiredFromRoot` — _(unfilled)_

### `PlayerCompactElementsManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerCompactElementsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerCompactElementsManager.kt)

**Layer:** ui · **LOC:** 35 · **Last:** 2026-04-12 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `apply` — _(unfilled)_
- `restore` — _(unfilled)_

### `PlayerControlsSetupManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt)

**Layer:** ui · **LOC:** 555 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `setupAllControls` — _(unfilled)_
- `setupNavigationControls` — _(unfilled)_
- `setupPlaybackControls` — _(unfilled)_
- `setupSlideshowControls` — _(unfilled)_
- `setupCommandButtons` — _(unfilled)_
- `setupPdfControls` — _(unfilled)_
- `setupEpubControls` — _(unfilled)_
- `setupTranslationControls` — _(unfilled)_
- `setupLyricsViewerControls` — _(unfilled)_
- `setupTextViewerControls` — _(unfilled)_
- `setupSearchControls` — _(unfilled)_
- `setupExoPlayerControls` — _(unfilled)_
- `setupDocumentFullscreenExitButton` — _(unfilled)_
- `updateDocumentFullscreenExitButtonVisibility` — _(unfilled)_
- `setupTouchZonesHelpButton` — _(unfilled)_
- `setupToolbar` — _(unfilled)_

### `PlayerDeleteUndoCoordinator` — [com/sza/fastmediasorter/ui/player/helpers/PlayerDeleteUndoCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDeleteUndoCoordinator.kt)

**Layer:** ui · **LOC:** 331 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `saveResumeState` — _(unfilled)_
- `reloadFiles` — _(unfilled)_
- `deleteCurrentFile` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `reloadAfterRename` — _(unfilled)_
- `saveUndoOperation` — _(unfilled)_
- `undoLastOperation` — _(unfilled)_
- `clearExpiredUndoOperation` — _(unfilled)_
- `restoreLocalFile` — _(unfilled)_
- `restoreNetworkFile` — _(unfilled)_

### `PlayerDialogAndUiStateManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt)

**Layer:** ui · **LOC:** 560 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showRenameDialog` — _(unfilled)_
- `showImageEditDialog` — _(unfilled)_
- `showGifEditDialog` — _(unfilled)_
- `showPdfEditDialog` — _(unfilled)_
- `exportPdfToJpg` — _(unfilled)_
- `showCopyDialog` — _(unfilled)_
- `showMoveDialog` — _(unfilled)_
- `updatePanelVisibility` — _(unfilled)_
- `applySmallControlsIfNeeded` — _(unfilled)_
- `restoreCommandButtonHeightsIfNeeded` — _(unfilled)_
- `toggleCopyPanel` — _(unfilled)_
- `toggleMovePanel` — _(unfilled)_
- `updateVolumeButtonsVisibility` — _(unfilled)_
- `updateAudioTouchZonesVisibility` — _(unfilled)_
- `updateSlideShowButton` — _(unfilled)_
- `updateCountdownDisplay` — _(unfilled)_
- `updateBackgroundMusicTrackDisplay` — _(unfilled)_
- `showFileInfo` — _(unfilled)_
- `clearUiOverlayForAnimatedPause` — _(unfilled)_
- `formatDuration` — _(unfilled)_

### `PlayerEventHandler` — [com/sza/fastmediasorter/ui/player/helpers/PlayerEventHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerEventHandler.kt)

**Layer:** ui · **LOC:** 227 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onDestroy` — _(unfilled)_
- `handleEvent` — _(unfilled)_
- `showError` — _(unfilled)_
- `showFileNotFound` — _(unfilled)_
- `showCloudAuthenticationError` — _(unfilled)_
- `showUnsupportedFormatError` — _(unfilled)_
- `showVrInstallCtaDialog` — _(unfilled)_

### `PlayerFpsMeter` — [com/sza/fastmediasorter/ui/player/helpers/PlayerFpsMeter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerFpsMeter.kt)

**Layer:** ui · **LOC:** 69 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `doFrame` — _(unfilled)_
- `start` — _(unfilled)_
- `stop` — _(unfilled)_

### `PlayerGestureCallback` — [com/sza/fastmediasorter/ui/player/helpers/PlayerGestureManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerGestureManager.kt)

**Layer:** ui · **LOC:** 130 · **Last:** 2026-02-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onSwipeLeft` — _(unfilled)_
- `onSwipeRight` — _(unfilled)_
- `onSwipeUp` — _(unfilled)_
- `onSwipeDown` — _(unfilled)_
- `onSingleTap` — _(unfilled)_
- `onDoubleTap` — _(unfilled)_
- `onLongPress` — _(unfilled)_
- `onPinch` — _(unfilled)_
- `onZoomRequested` — _(unfilled)_
- `getCurrentScale` — _(unfilled)_
- `getMinScale` — _(unfilled)_
- `getMaxScale` — _(unfilled)_
- `canHandleGestures` — _(unfilled)_
- `onTouchEvent` — _(unfilled)_
- `onDown` — _(unfilled)_
- `onSingleTapConfirmed` — _(unfilled)_
- `onDoubleTap` — _(unfilled)_
- `onLongPress` — _(unfilled)_
- `onFling` — _(unfilled)_
- `onScaleBegin` — _(unfilled)_
- `onScale` — _(unfilled)_
- `onScaleEnd` — _(unfilled)_

### `PlayerGestureSetupManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerGestureSetupManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerGestureSetupManager.kt)

**Layer:** ui · **LOC:** 474 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `isAnyPhotoViewActive` — _(unfilled)_
- `getVisiblePhotoView` — _(unfilled)_
- `isOverlayBlocking` — _(unfilled)_
- `setupGestureDetector` — _(unfilled)_
- `setupRootTouchListener` — _(unfilled)_
- `setupPlayerViewTouchListener` — _(unfilled)_
- `setupPhotoViewTouchListener` — _(unfilled)_
- `configurePhotoViewGestures` — _(unfilled)_
- `onSingleTapConfirmed` — _(unfilled)_
- `onDoubleTap` — _(unfilled)_
- `onDoubleTapEvent` — _(unfilled)_
- `toRootCoordinatesEvent` — _(unfilled)_
- `setupImageViewTouchListener` — _(unfilled)_

### `PlayerImageTranslationManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerImageTranslationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImageTranslationManager.kt)

**Layer:** ui · **LOC:** 237 · **Last:** 2026-03-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `stopTranslation` — _(unfilled)_
- `translateCurrentImage` — _(unfilled)_
- `extractBitmapFromDrawable` — _(unfilled)_

### `PlayerKeyboardHandler` — [com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt)

**Layer:** ui · **LOC:** 344 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onDeleteFile` — _(unfilled)_
- `onExitPlayer` — _(unfilled)_
- `onToggleSlideshow` — _(unfilled)_
- `onShowRenameDialog` — _(unfilled)_
- `onShowFileInfo` — _(unfilled)_
- `onToggleCommandPanel` — _(unfilled)_
- `onToggleCopyPanel` — _(unfilled)_
- `onToggleMovePanel` — _(unfilled)_
- `onShowEditDialog` — _(unfilled)_
- `getActivePlayer` — _(unfilled)_
- `getCurrentMediaType` — _(unfilled)_
- `onNextFile` — _(unfilled)_
- `onPreviousFile` — _(unfilled)_
- `onSeekForward` — _(unfilled)_
- `onSeekBackward` — _(unfilled)_
- `onPdfNextPage` — _(unfilled)_
- `onPdfPreviousPage` — _(unfilled)_
- `onPdfHome` — _(unfilled)_
- `onPdfEnd` — _(unfilled)_
- `onEpubNextPage` — _(unfilled)_
- `onEpubPreviousPage` — _(unfilled)_
- `onEpubHome` — _(unfilled)_
- `onEpubEnd` — _(unfilled)_
- `onTextScrollDown` — _(unfilled)_
- `onTextScrollUp` — _(unfilled)_
- `onTextHome` — _(unfilled)_
- `onTextEnd` — _(unfilled)_
- `onEpubScrollDelta` — _(unfilled)_
- `onNavigationScroll` — _(unfilled)_
- `onToggleMute` — _(unfilled)_
- `onToggleFullscreen` — _(unfilled)_
- `onChangeVolume` — _(unfilled)_
- `onShowHelp` — _(unfilled)_
- `onDocumentSearch` — _(unfilled)_
- `onSaveCurrent` — _(unfilled)_
- `onShowContextMenu` — _(unfilled)_
- `onToggleFavourite` — _(unfilled)_
- `onUndoOperation` — _(unfilled)_
- `canCopyCurrent` — _(unfilled)_
- `canMoveCurrent` — _(unfilled)_
- `onRightClick` — _(unfilled)_
- `onMiddleClick` — _(unfilled)_
- `onScrollWheel` — _(unfilled)_
- `onNavigateBack` — _(unfilled)_
- `onNavigateForward` — _(unfilled)_
- `handleKeyDown` — _(unfilled)_
- `handleCommand` — _(unfilled)_
- `handlePointerEvent` — _(unfilled)_
- `handleWheelScroll` — _(unfilled)_
- `supportsDocumentSearch` — _(unfilled)_
- `scanCodeToCommandId` — _(unfilled)_
- `needsMediaButtonDebounce` — _(unfilled)_
- `isMediaButtonDebounced` — _(unfilled)_
- `handlePlayPause` — _(unfilled)_

### `PlayerLifecycleManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt)

**Layer:** ui · **LOC:** 555 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `isSlideshowModeRequested` — _(unfilled)_
- `onResume` — _(unfilled)_
- `updateButtonVisibility` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onDestroy` — _(unfilled)_
- `releaseResources` — _(unfilled)_
- `returnModifiedFilesResult` — _(unfilled)_
- `trackModifiedFile` — _(unfilled)_
- `setActiveResourceKey` — _(unfilled)_
- `addPreloadJob` — _(unfilled)_
- `removePreloadJob` — _(unfilled)_
- `saveCurrentPlaybackPosition` — _(unfilled)_
- `setupBackPressHandler` — _(unfilled)_
- `handleOnBackPressed` — _(unfilled)_
- `exitPlayerWithAudioCheck` — _(unfilled)_
- `showExitAudioDialog` — _(unfilled)_
- `doFinish` — _(unfilled)_
- `setSlideshowKeepAwake` — _(unfilled)_
- `handleDeleteSuccess` — _(unfilled)_
- `handleBatchDeleteResult` — _(unfilled)_
- `handleDeletePermissionResult` — _(unfilled)_
- `stopVideoPlayback` — _(unfilled)_
- `clearImageMemoryCache` — _(unfilled)_

### `PlayerMediaFilesLoader` — [com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt)

**Layer:** ui · **LOC:** 403 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `loadSettings` — _(unfilled)_
- `loadMediaFiles` — _(unfilled)_
- `reloadFiles` — _(unfilled)_
- `cancelLoading` — _(unfilled)_
- `isPlayerSupportedType` — _(unfilled)_
- `isPlayerBrowsableFile` — _(unfilled)_
- `normalizePath` — _(unfilled)_

### `PlayerMediaLoaderManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt)

**Layer:** ui · **LOC:** 970 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `onIsPlayingChanged` — _(unfilled)_
- `onPlaybackStateChanged` — _(unfilled)_
- `onMediaItemTransition` — _(unfilled)_
- `onPlayerError` — _(unfilled)_
- `displayImage` — _(unfilled)_
- `isCurrentAnimatedContent` — _(unfilled)_
- `isAnimatedPlaybackPaused` — _(unfilled)_
- `toggleAnimatedPlayback` — _(unfilled)_
- `displayText` — _(unfilled)_
- `playVideo` — _(unfilled)_
- `playAudioViaService` — _(unfilled)_
- `playLocalAudioViaService` — _(unfilled)_
- `preCacheNetworkAudio` — _(unfilled)_
- `downloadSmbFull` — _(unfilled)_
- `downloadSftpFull` — _(unfilled)_
- `downloadFtpFull` — _(unfilled)_
- `preCacheCloudAudio` — _(unfilled)_
- `buildLocalUri` — _(unfilled)_
- `buildUriForMediaFile` — _(unfilled)_
- `bindServicePlayerToView` — _(unfilled)_
- `reattachServicePlayerToView` — _(unfilled)_
- `bindServicePlaybackListener` — _(unfilled)_
- `unbindServicePlaybackListener` — _(unfilled)_
- `playLocalVideo` — _(unfilled)_
- `reloadCurrentImage` — _(unfilled)_
- `preloadNextImageIfNeeded` — _(unfilled)_
- `prefetchNextAudio` — _(unfilled)_
- `cancelAudioPrefetch` — _(unfilled)_
- `showAudioFileInfo` — _(unfilled)_
- `updateAudioTouchZonesVisibility` — _(unfilled)_
- `updateVolumeButtonsVisibility` — _(unfilled)_
- `adjustTouchZonesForVideo` — _(unfilled)_
- `hideImageViews` — _(unfilled)_
- `hideTextViewerControls` — _(unfilled)_
- `hidePdfViewerControls` — _(unfilled)_
- `hideEpubViewerControls` — _(unfilled)_
- `configurePlayerViewForMediaType` — _(unfilled)_
- `determineResourceType` — _(unfilled)_
- `playVideoWithResourceType` — _(unfilled)_

### `PlayerMediaViewVisibilityHelper` — [com/sza/fastmediasorter/ui/player/helpers/PlayerMediaViewVisibilityHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaViewVisibilityHelper.kt)

**Layer:** ui · **LOC:** 65 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `hideImageViews` — _(unfilled)_
- `hideTextViewerControls` — _(unfilled)_
- `hidePdfViewerControls` — _(unfilled)_
- `hideEpubViewerControls` — _(unfilled)_
- `determineResourceType` — _(unfilled)_

### `PlayerNavigationCoordinator` — [com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationCoordinator.kt)

**Layer:** ui · **LOC:** 293 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `syncAudioServiceIndex` — _(unfilled)_
- `jumpToIndex` — _(unfilled)_
- `nextFile` — _(unfilled)_
- `previousFile` — _(unfilled)_
- `getLookaheadTargets` — _(unfilled)_
- `getAdjacentFiles` — _(unfilled)_
- `getNextAudioFile` — _(unfilled)_
- `saveLastViewedFileDebounced` — _(unfilled)_
- `saveLastViewedFile` — _(unfilled)_

### `PlayerNavigationManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationManager.kt)

**Layer:** ui · **LOC:** 383 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `initializeSlideshowController` — _(unfilled)_
- `onSlideAdvance` — _(unfilled)_
- `onSlideshowStateChanged` — _(unfilled)_
- `onCountdownTick` — _(unfilled)_
- `onMemoryCacheClear` — _(unfilled)_
- `setKeepScreenAwake` — _(unfilled)_
- `shouldSuppressTimer` — _(unfilled)_
- `handleMouseWheelScroll` — _(unfilled)_
- `handleTouchZoneNavigation` — _(unfilled)_
- `navigatePreviousFromButton` — _(unfilled)_
- `navigateNextFromButton` — _(unfilled)_
- `navigateRandomFromButton` — _(unfilled)_
- `navigatePreviousFromGesture` — _(unfilled)_
- `navigateNextFromGesture` — _(unfilled)_
- `navigatePrevious` — _(unfilled)_
- `navigateNext` — _(unfilled)_
- `handleSlideshowControl` — _(unfilled)_
- `updateSlideshowInterval` — _(unfilled)_
- `handlePlaybackStateChange` — _(unfilled)_
- `isSlideshowActive` — _(unfilled)_
- `getSlideshowController` — _(unfilled)_
- `navigateNextAfterOperation` — _(unfilled)_
- `navigatePreviousFromTouchZone` — _(unfilled)_
- `navigateNextFromTouchZone` — _(unfilled)_
- `navigatePreviousFromControl` — _(unfilled)_
- `navigateNextFromControl` — _(unfilled)_
- `navigatePreviousFromMouseScroll` — _(unfilled)_
- `navigateNextFromMouseScroll` — _(unfilled)_
- `toggleSlideshow` — _(unfilled)_
- `syncMediaLibraryMode` — _(unfilled)_
- `updateSlideshowState` — _(unfilled)_

### `PlayerPrefetchManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchManager.kt)

**Layer:** ui · **LOC:** 219 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `setup` — _(unfilled)_
- `mountOverlay` — _(unfilled)_
- `startObserving` — _(unfilled)_
- `showOffloadOffer` — _(unfilled)_
- `handleOffloadProgress` — _(unfilled)_
- `showCleanupPrompt` — _(unfilled)_
- `onNewMediaSession` — _(unfilled)_
- `onPlaybackReady` — _(unfilled)_
- `dpToPx` — _(unfilled)_

### `PlayerPrefetchOffloadCoordinator` — [com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchOffloadCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrefetchOffloadCoordinator.kt)

**Layer:** ui · **LOC:** 200 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `updatePrefetchPlan` — _(unfilled)_
- `bindPrefetchTracker` — _(unfilled)_
- `unbindPrefetchTracker` — _(unfilled)_
- `emitOffloadOffer` — _(unfilled)_
- `acceptOffload` — _(unfilled)_
- `declineOffload` — _(unfilled)_
- `cancelOffload` — _(unfilled)_
- `switchToLocalFile` — _(unfilled)_
- `requestCleanupIfNeeded` — _(unfilled)_
- `deleteLocalCopy` — _(unfilled)_

### `PlayerSettingsManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerSettingsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSettingsManager.kt)

**Layer:** ui · **LOC:** 140 · **Last:** 2026-04-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showPlayerSettingsDialog` — _(unfilled)_
- `applyPlayerSettings` — _(unfilled)_
- `applySubtitleStyling` — _(unfilled)_
- `showPlaybackSpeedDialog` — _(unfilled)_
- `release` — _(unfilled)_

### `PlayerSetupHelper` — [com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt)

**Layer:** ui · **LOC:** 184 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `createPlayer` — _(unfilled)_
- `applyConfiguredVideoEffects` — _(unfilled)_
- `brightnessProgressToAdjustment` — _(unfilled)_
- `brightnessAdjustmentToProgress` — _(unfilled)_
- `logMemoryStats` — _(unfilled)_

### `PlayerShareManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerShareManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerShareManager.kt)

**Layer:** ui · **LOC:** 142 · **Last:** 2026-03-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `openInExternalPlayer` — _(unfilled)_
- `shareCurrentFileToGoogleLens` — _(unfilled)_
- `shareFileToGoogleLens` — _(unfilled)_

### `PlayerStereoModeCoordinator` — [com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt)

**Layer:** ui · **LOC:** 254 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `setStereoMode` — _(unfilled)_
- `setAutoDetectedStereoMode` — _(unfilled)_
- `resetStereoModeForNewFile` — _(unfilled)_
- `rememberStereoModeIfEnabled` — _(unfilled)_
- `applySettings` — _(unfilled)_
- `publishEffective` — _(unfilled)_
- `resolveAutoStereoMode` — _(unfilled)_
- `resolveForcedStereoMode` — _(unfilled)_

### `PlayerTouchZoneSetupManager` — [com/sza/fastmediasorter/ui/player/helpers/PlayerTouchZoneSetupManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerTouchZoneSetupManager.kt)

**Layer:** ui · **LOC:** 84 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `setupLegacyTouchZoneListeners` — _(unfilled)_
- `showHintOverlay` — _(unfilled)_

### `PlayerUiStateCoordinator` — [com/sza/fastmediasorter/ui/player/helpers/PlayerUiStateCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerUiStateCoordinator.kt)

**Layer:** ui · **LOC:** 326 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `displayImage` — _(unfilled)_
- `playVideo` — _(unfilled)_
- `displayText` — _(unfilled)_
- `displayPdf` — _(unfilled)_
- `displayEpub` — _(unfilled)_
- `isActivityAlive` — _(unfilled)_
- `getCurrentSettings` — _(unfilled)_
- `setCurrentSettings` — _(unfilled)_
- `getCurrentFilePath` — _(unfilled)_
- `setCurrentFilePath` — _(unfilled)_
- `isImageVisible` — _(unfilled)_
- `hasImageDrawable` — _(unfilled)_
- `isSlideshowModeRequested` — _(unfilled)_
- `clearSlideshowModeRequested` — _(unfilled)_
- `hasShownHintType` — _(unfilled)_
- `markHintTypeShown` — _(unfilled)_
- `getUseTouchZones` — _(unfilled)_
- `displayImage` — _(unfilled)_
- `playVideo` — _(unfilled)_
- `displayText` — _(unfilled)_
- `displayPdf` — _(unfilled)_
- `displayEpub` — _(unfilled)_
- `adjustTouchZonesForVideo` — _(unfilled)_
- `updatePanelVisibility` — _(unfilled)_
- `updateCommandAvailability` — _(unfilled)_
- `updatePlayPauseButton` — _(unfilled)_
- `updateSlideShowButton` — _(unfilled)_
- `updateVolumeButtonsVisibility` — _(unfilled)_
- `showTouchZoneHintOverlay` — _(unfilled)_
- `showSlideshowEnabledMessage` — _(unfilled)_
- `toggleSlideShow` — _(unfilled)_
- `startSlideshow` — _(unfilled)_
- `getLatestState` — _(unfilled)_
- `forceStateUpdate` — _(unfilled)_
- `enterAudioSlideshowPhotoModeIfNeeded` — _(unfilled)_
- `updateTouchZonesHelpButtonVisibility` — _(unfilled)_
- `onFilenameOverlayFileShown` — _(unfilled)_
- `onFilenameOverlayPauseInteraction` — _(unfilled)_
- `determineTouchZoneHintType` — _(unfilled)_
- `updateUI` — _(unfilled)_
- `getCurrentHintType` — _(unfilled)_

### `PrefetchLoadControlFactory` — [com/sza/fastmediasorter/ui/player/helpers/PrefetchLoadControlFactory.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchLoadControlFactory.kt)

**Layer:** ui · **LOC:** 58 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `build` — _(unfilled)_

### `PrefetchPolicyManager` — [com/sza/fastmediasorter/ui/player/helpers/PrefetchPolicyManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchPolicyManager.kt)

**Layer:** ui · **LOC:** 114 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** DeviceCapabilityProbe, SettingsRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `computePlan` — _(unfilled)_
- `computePlanWithDefaults` — _(unfilled)_
- `detectProtocol` — _(unfilled)_

### `PrefetchProgress` — [com/sza/fastmediasorter/ui/player/helpers/PrefetchProgressTracker.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchProgressTracker.kt)

**Layer:** ui · **LOC:** 215 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `attach` — _(unfilled)_
- `updatePlan` — _(unfilled)_
- `detach` — _(unfilled)_
- `markReady` — _(unfilled)_
- `recordStall` — _(unfilled)_
- `maybeEscalate` — _(unfilled)_
- `startPolling` — _(unfilled)_
- `onPlaybackStateChanged` — _(unfilled)_

### `QueueTrackAdapter` — [com/sza/fastmediasorter/ui/player/helpers/QueueTrackAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/QueueTrackAdapter.kt)

**Layer:** ui · **LOC:** 71 · **Last:** 2026-03-30 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `setCurrentIndex` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `bind` — _(unfilled)_
- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_

### `SaveVideoFrameManager` — [com/sza/fastmediasorter/ui/player/helpers/SaveVideoFrameManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SaveVideoFrameManager.kt)

**Layer:** ui · **LOC:** 285 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `saveCurrentFrame` — _(unfilled)_
- `captureFrame` — _(unfilled)_
- `findTextureView` — _(unfilled)_
- `writeTempFile` — _(unfilled)_
- `trySaveToResource` — _(unfilled)_
- `saveToDownloads` — _(unfilled)_
- `buildFileName` — _(unfilled)_
- `showSnackbar` — _(unfilled)_

### `SearchControlsManager` — [com/sza/fastmediasorter/ui/player/helpers/SearchControlsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SearchControlsManager.kt)

**Layer:** ui · **LOC:** 247 · **Last:** 2026-03-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getCurrentMediaFile` — _(unfilled)_
- `scheduleHideControls` — _(unfilled)_
- `onEpubTranslate` — _(unfilled)_
- `showTranslationSettingsDialog` — _(unfilled)_
- `setupSearchControls` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `showSearchPanel` — _(unfilled)_
- `hideSearchPanel` — _(unfilled)_
- `performSearch` — _(unfilled)_
- `performSearchNavigation` — _(unfilled)_
- `updateSearchCounter` — _(unfilled)_
- `clearSearch` — _(unfilled)_

### `SftpPlaybackHelper` — [com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt)

**Layer:** ui · **LOC:** 106 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `playSftpVideo` — _(unfilled)_

### `SleepTimerManager` — [com/sza/fastmediasorter/ui/player/helpers/SleepTimerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SleepTimerManager.kt)

**Layer:** ui · **LOC:** 225 · **Last:** 2026-03-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `startVinylAnimation` — _(unfilled)_
- `pauseVinylAnimation` — _(unfilled)_
- `stopVinylAnimation` — _(unfilled)_
- `updateVinylState` — _(unfilled)_
- `startSleepTimer` — _(unfilled)_
- `onTick` — _(unfilled)_
- `onFinish` — _(unfilled)_
- `cancelSleepTimer` — _(unfilled)_
- `fadeOutAndPause` — _(unfilled)_
- `onAnimationEnd` — _(unfilled)_
- `updateBadgeText` — _(unfilled)_
- `release` — _(unfilled)_

### `SmbPlaybackHelper` — [com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt)

**Layer:** ui · **LOC:** 119 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `playSmbVideo` — _(unfilled)_

### `StandaloneFileOperationsHandler` — [com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt)

**Layer:** ui · **LOC:** 374 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `deleteCurrentFile` — _(unfilled)_
- `performDelete` — _(unfilled)_
- `onDeleteSuccess` — _(unfilled)_
- `toastDeleteFailed` — _(unfilled)_
- `retryDeleteAfterPermission` — _(unfilled)_
- `handleBatchDeleteResult` — _(unfilled)_
- `handleRecoverableDeleteResult` — _(unfilled)_
- `shareCurrentFile` — _(unfilled)_
- `openInFms` — _(unfilled)_
- `launchMainActivity` — _(unfilled)_
- `resolveToLocalPath` — _(unfilled)_
- `updateRenameButtonVisibility` — _(unfilled)_
- `canRenameUri` — _(unfilled)_
- `showStandaloneRenameDialog` — _(unfilled)_
- `performStandaloneRename` — _(unfilled)_

### `StandaloneFullscreenManager` — [com/sza/fastmediasorter/ui/player/helpers/StandaloneFullscreenManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFullscreenManager.kt)

**Layer:** ui · **LOC:** 61 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `toggleFullscreen` — _(unfilled)_
- `enterFullscreen` — _(unfilled)_
- `exitFullscreen` — _(unfilled)_

### `StandalonePlayerLifecycleManager` — [com/sza/fastmediasorter/ui/player/helpers/StandalonePlayerLifecycleManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandalonePlayerLifecycleManager.kt)

**Layer:** ui · **LOC:** 37 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `onResume` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onDestroy` — _(unfilled)_

### `StandalonePlayerSettingsManager` — [com/sza/fastmediasorter/ui/player/helpers/StandalonePlayerSettingsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandalonePlayerSettingsManager.kt)

**Layer:** ui · **LOC:** 114 · **Last:** 2026-04-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showPlaybackSpeedDialog` — _(unfilled)_
- `showAudioTrackDialog` — _(unfilled)_
- `showSubtitleTrackDialog` — _(unfilled)_
- `applySubtitleStyling` — _(unfilled)_

### `StandaloneVideoControlsManager` — [com/sza/fastmediasorter/ui/player/helpers/StandaloneVideoControlsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneVideoControlsManager.kt)

**Layer:** ui · **LOC:** 38 · **Last:** 2026-04-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `showPlaybackControlDialog` — _(unfilled)_
- `setupVideoControls` — _(unfilled)_
- `updateTrackButtonsVisibility` — _(unfilled)_

### `StandaloneVideoTouchDelegate` — [com/sza/fastmediasorter/ui/player/helpers/StandaloneVideoTouchDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneVideoTouchDelegate.kt)

**Layer:** ui · **LOC:** 230 · **Last:** 2026-04-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onDown` — _(unfilled)_
- `onSingleTapConfirmed` — _(unfilled)_
- `onDoubleTap` — _(unfilled)_
- `onScroll` — _(unfilled)_
- `attachIndicator` — _(unfilled)_
- `handleTouchEvent` — _(unfilled)_
- `isVideoGestureArea` — _(unfilled)_
- `toggleController` — _(unfilled)_
- `applyBrightnessProgress` — _(unfilled)_
- `showIndicator` — _(unfilled)_
- `scheduleIndicatorHide` — _(unfilled)_

### `StandaloneViewManager` — [com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt)

**Layer:** ui · **LOC:** 615 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `getCurrentResource` — _(unfilled)_
- `showError` — _(unfilled)_
- `showError` — _(unfilled)_
- `showModelDownloadPrompt` — _(unfilled)_
- `getExoPlayer` — _(unfilled)_
- `getPlayer` — _(unfilled)_
- `isVideoPlaying` — _(unfilled)_
- `setHueAdjustmentDegrees` — _(unfilled)_
- `getHueAdjustmentDegrees` — _(unfilled)_
- `setBrightnessProgress` — _(unfilled)_
- `getBrightnessProgress` — _(unfilled)_
- `getBrightnessPercentOffset` — _(unfilled)_
- `getPlaybackSpeed` — _(unfilled)_
- `setPlaybackSpeed` — _(unfilled)_
- `isMediaPlaying` — _(unfilled)_
- `play` — _(unfilled)_
- `pause` — _(unfilled)_
- `activePlayer` — _(unfilled)_
- `show` — _(unfilled)_
- `onResume` — _(unfilled)_
- `onPause` — _(unfilled)_
- `release` — _(unfilled)_
- `showImage` — _(unfilled)_
- `showGif` — _(unfilled)_
- `playVideo` — _(unfilled)_
- `applyVideoColorEffects` — _(unfilled)_
- `brightnessProgressToAdjustment` — _(unfilled)_
- `brightnessAdjustmentToProgress` — _(unfilled)_
- `playAudio` — _(unfilled)_
- `showPdf` — _(unfilled)_
- `showPdfPreviousPage` — _(unfilled)_
- `showPdfNextPage` — _(unfilled)_
- `showPdfFirstPage` — _(unfilled)_
- `showEpub` — _(unfilled)_
- `showEpubPreviousChapter` — _(unfilled)_
- `showEpubNextChapter` — _(unfilled)_
- `showEpubFirstChapter` — _(unfilled)_
- `showEpubTableOfContents` — _(unfilled)_
- `decreaseEpubFontSize` — _(unfilled)_
- `increaseEpubFontSize` — _(unfilled)_
- `showEpubReaderSettings` — _(unfilled)_
- `showEpubCrossSearch` — _(unfilled)_
- `exitEpubFullscreen` — _(unfilled)_
- `toggleEpubTranslation` — _(unfilled)_
- `togglePdfTranslation` — _(unfilled)_
- `epubViewerManagerProvider` — _(unfilled)_
- `pdfViewerManagerProvider` — _(unfilled)_
- `textViewerManagerProvider` — _(unfilled)_
- `isEpubActive` — _(unfilled)_
- `getEpubSelectionActionModeCallback` — _(unfilled)_
- `updateAudioMediaItem` — _(unfilled)_
- `showText` — _(unfilled)_
- `hidePhotoAndPlayerViews` — _(unfilled)_
- `showToastError` — _(unfilled)_
- `showTranslatedTextDialog` — _(unfilled)_
- `acquireWakeLock` — _(unfilled)_
- `releaseWakeLock` — _(unfilled)_
- `createPlayerErrorListener` — _(unfilled)_
- `onPlayerError` — _(unfilled)_
- `onVideoSizeChanged` — _(unfilled)_
- `restorePlaybackPosition` — _(unfilled)_
- `startPositionAutoSave` — _(unfilled)_
- `stopPositionAutoSave` — _(unfilled)_
- `saveCurrentPosition` — _(unfilled)_
- `createPdfViewerManager` — _(unfilled)_
- `showError` — _(unfilled)_
- `displayOcrText` — _(unfilled)_
- `displayTranslatedText` — _(unfilled)_
- `shareFileToGoogleLens` — _(unfilled)_
- `isLandscapeMode` — _(unfilled)_
- `onEnterFullscreenMode` — _(unfilled)_
- `onExitFullscreenMode` — _(unfilled)_
- `createEpubViewerManager` — _(unfilled)_
- `showError` — _(unfilled)_
- `displayTranslatedText` — _(unfilled)_
- `onEnterFullscreenMode` — _(unfilled)_
- `onExitFullscreenMode` — _(unfilled)_
- `createTextViewerManager` — _(unfilled)_
- `showError` — _(unfilled)_
- `showTranslationSettingsDialog` — _(unfilled)_
- `exitFullscreenMode` — _(unfilled)_
- `setTouchZonesEnabled` — _(unfilled)_
- `showEncodingDialog` — _(unfilled)_

### `StreamingCacheCleanupHelper` — [com/sza/fastmediasorter/ui/player/helpers/StreamingCacheCleanupHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamingCacheCleanupHelper.kt)

**Layer:** ui · **LOC:** 60 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showPrompt` — _(unfilled)_

### `StreamOffloadOfferDialog` — [com/sza/fastmediasorter/ui/player/helpers/StreamOffloadOfferDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamOffloadOfferDialog.kt)

**Layer:** ui · **LOC:** 122 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `bindOffer` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `newInstance` — _(unfilled)_

### `SystemBarsManager` — [com/sza/fastmediasorter/ui/player/helpers/SystemBarsManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SystemBarsManager.kt)

**Layer:** ui · **LOC:** 202 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `enterFullscreenMode` — _(unfilled)_
- `exitFullscreenMode` — _(unfilled)_
- `isInFullscreenMode` — _(unfilled)_
- `updateSystemBarsVisibility` — _(unfilled)_
- `updateForPlayerState` — _(unfilled)_

### `TesseractManager` — [com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt)

**Layer:** ui · **LOC:** 344 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `init` — _(unfilled)_
- `checkAndDownloadData` — _(unfilled)_
- `recognizeText` — _(unfilled)_
- `recognizeTextBlocks` — _(unfilled)_
- `prepareBitmapForTesseract` — _(unfilled)_
- `isNonCriticalBitmapReadError` — _(unfilled)_
- `release` — _(unfilled)_
- `filterDuplicateAndOverlappingBlocks` — _(unfilled)_
- `calculateTextSimilarity` — _(unfilled)_
- `calculateOverlapPercentage` — _(unfilled)_

### `TextEditorAutoSaveManager` — [com/sza/fastmediasorter/ui/player/helpers/TextEditorAutoSaveManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextEditorAutoSaveManager.kt)

**Layer:** ui · **LOC:** 147 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `startAutoSave` — _(unfilled)_
- `stopAutoSave` — _(unfilled)_
- `hasDraft` — _(unfilled)_
- `restoreDraft` — _(unfilled)_
- `forceSave` — _(unfilled)_
- `deleteDraft` — _(unfilled)_
- `saveDraft` — _(unfilled)_
- `getDraftFile` — _(unfilled)_

### `TextFilePager` — [com/sza/fastmediasorter/ui/player/helpers/TextFilePager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextFilePager.kt)

**Layer:** ui · **LOC:** 250 · **Last:** 2026-02-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `open` — _(unfilled)_
- `close` — _(unfilled)_
- `readPage` — _(unfilled)_
- `getEstimatedPageCount` — _(unfilled)_
- `isFullyIndexed` — _(unfilled)_
- `isSinglePage` — _(unfilled)_
- `hasNextPage` — _(unfilled)_
- `hasPreviousPage` — _(unfilled)_
- `getStartLineNumber` — _(unfilled)_
- `ensureIndexedUpTo` — _(unfilled)_
- `findPageEnd` — _(unfilled)_
- `adjustForCharsetBoundary` — _(unfilled)_

### `TextReaderTheme` — [com/sza/fastmediasorter/ui/player/helpers/TextReaderTheme.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextReaderTheme.kt)

**Layer:** ui · **LOC:** 21 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `fromName` — _(unfilled)_

### `TextUndoRedoManager` — [com/sza/fastmediasorter/ui/player/helpers/TextUndoRedoManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextUndoRedoManager.kt)

**Layer:** ui · **LOC:** 153 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `attach` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `detach` — _(unfilled)_
- `undo` — _(unfilled)_
- `redo` — _(unfilled)_
- `canUndo` — _(unfilled)_
- `canRedo` — _(unfilled)_
- `clear` — _(unfilled)_
- `pushAction` — _(unfilled)_
- `notifyStateChanged` — _(unfilled)_

### `TextViewerManager` — [com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt)

**Layer:** ui · **LOC:** 1824 · **Last:** 2026-04-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showError` — _(unfilled)_
- `showTranslationSettingsDialog` — _(unfilled)_
- `exitFullscreenMode` — _(unfilled)_
- `setTouchZonesEnabled` — _(unfilled)_
- `showEncodingDialog` — _(unfilled)_
- `setupControls` — _(unfilled)_
- `setupGestureDetectors` — _(unfilled)_
- `onFling` — _(unfilled)_
- `onFling` — _(unfilled)_
- `onSingleTapConfirmed` — _(unfilled)_
- `applyFontSettings` — _(unfilled)_
- `increaseTextFontSize` — _(unfilled)_
- `decreaseTextFontSize` — _(unfilled)_
- `applyTextFontSize` — _(unfilled)_
- `increaseTranslationFontSize` — _(unfilled)_
- `decreaseTranslationFontSize` — _(unfilled)_
- `applyTranslationFontSize` — _(unfilled)_
- `applyTranslationFontSizeForImageTranslation` — _(unfilled)_
- `showFontSizeToast` — _(unfilled)_
- `displayText` — _(unfilled)_
- `applyLineNumbers` — _(unfilled)_
- `nextPage` — _(unfilled)_
- `previousPage` — _(unfilled)_
- `reopenWithEncoding` — _(unfilled)_
- `getSupportedCharsets` — _(unfilled)_
- `getCurrentCharsetName` — _(unfilled)_
- `updatePageIndicator` — _(unfilled)_
- `closePager` — _(unfilled)_
- `release` — _(unfilled)_
- `closeTextViewerFromBackPress` — _(unfilled)_
- `toggleMarkdownRendering` — _(unfilled)_
- `applyReaderTheme` — _(unfilled)_
- `getCurrentTheme` — _(unfilled)_
- `resolveTheme` — _(unfilled)_
- `toggleReadAloud` — _(unfilled)_
- `isMarkdownFile` — _(unfilled)_
- `getFileExtension` — _(unfilled)_
- `applyThemeToViews` — _(unfilled)_
- `renderPageContent` — _(unfilled)_
- `reloadCurrentPage` — _(unfilled)_
- `setupEditorToolbar` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `setupCursorPositionTracking` — _(unfilled)_
- `updateCursorPosition` — _(unfilled)_
- `showFindPanel` — _(unfilled)_
- `closeFindPanel` — _(unfilled)_
- `performFindInEditor` — _(unfilled)_
- `navigateFind` — _(unfilled)_
- `highlightFindMatch` — _(unfilled)_
- `updateFindCounter` — _(unfilled)_
- `replaceCurrent` — _(unfilled)_
- `replaceAll` — _(unfilled)_
- `clearEditorHighlights` — _(unfilled)_
- `enterEditMode` — _(unfilled)_
- `exitEditMode` — _(unfilled)_
- `saveEditedText` — _(unfilled)_
- `scrollDown` — _(unfilled)_
- `scrollUp` — _(unfilled)_
- `handleMouseWheelScroll` — _(unfilled)_
- `forceTranslate` — _(unfilled)_
- `toggleTranslation` — _(unfilled)_
- `hideTranslationOverlay` — _(unfilled)_
- `toggleTranslationOverlaySize` — _(unfilled)_
- `translateCurrentText` — _(unfilled)_
- `translateSelectedText` — _(unfilled)_
- `updateCloseButtonVisibility` — _(unfilled)_
- `updateTranslateButtonTint` — _(unfilled)_
- `updateTranslationButtonIcon` — _(unfilled)_
- `displayOcrText` — _(unfilled)_
- `hideOcrText` — _(unfilled)_
- `displayTranslatedText` — _(unfilled)_
- `searchText` — _(unfilled)_
- `highlightSearchMatch` — _(unfilled)_
- `clearSearch` — _(unfilled)_
- `scrollToTop` — _(unfilled)_
- `scrollToBottom` — _(unfilled)_

### `TouchZoneGestureManager` — [com/sza/fastmediasorter/ui/player/helpers/TouchZoneGestureManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TouchZoneGestureManager.kt)

**Layer:** ui · **LOC:** 720 · **Last:** 2026-03-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `isAnyPhotoViewVisible` — _(unfilled)_
- `getVisiblePhotoView` — _(unfilled)_
- `getCurrentPhotoViewScale` — _(unfilled)_
- `resolveHorizontalZone` — _(unfilled)_
- `isAnyImageSurfaceVisible` — _(unfilled)_
- `isOverlayBlocking` — _(unfilled)_
- `getTouchZonesEnabled` — _(unfilled)_
- `getLoadFullSizeImages` — _(unfilled)_
- `onBack` — _(unfilled)_
- `onPrevious` — _(unfilled)_
- `onNext` — _(unfilled)_
- `onCopy` — _(unfilled)_
- `onRename` — _(unfilled)_
- `onMove` — _(unfilled)_
- `onDelete` — _(unfilled)_
- `onSwitchToCommandPanel` — _(unfilled)_
- `onToggleSlideshow` — _(unfilled)_
- `onPauseResume` — _(unfilled)_
- `onSeekToStart` — _(unfilled)_
- `onSeekToEnd` — _(unfilled)_
- `onZoomIn` — _(unfilled)_
- `onZoomOut` — _(unfilled)_
- `setPhotoViewZoom` — _(unfilled)_
- `onPageUp` — _(unfilled)_
- `onPageDown` — _(unfilled)_
- `showSlideshowEnabledMessage` — _(unfilled)_
- `updateSlideShowButton` — _(unfilled)_
- `updateSlideShow` — _(unfilled)_
- `onUp` — _(unfilled)_
- `createImageTouchGestureDetector` — _(unfilled)_
- `onDown` — _(unfilled)_
- `onSingleTapConfirmed` — _(unfilled)_
- `onDoubleTap` — _(unfilled)_
- `onLongPress` — _(unfilled)_
- `onFling` — _(unfilled)_
- `executeSwipeAction` — _(unfilled)_
- `handleImageSingleTap` — _(unfilled)_
- `handleImageDoubleTap` — _(unfilled)_
- `handleImageFling` — _(unfilled)_
- `handleImageLongPress` — _(unfilled)_
- `createGestureDetector` — _(unfilled)_
- `onDown` — _(unfilled)_
- `onSingleTapConfirmed` — _(unfilled)_
- `handleTouchZone` — _(unfilled)_
- `handleCommandPanelTouchZones` — _(unfilled)_

### `TranslationButtonManager` — [com/sza/fastmediasorter/ui/player/helpers/TranslationButtonManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationButtonManager.kt)

**Layer:** ui · **LOC:** 388 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `getTranslationSessionSettings` — _(unfilled)_
- `setTranslationSessionSettings` — _(unfilled)_
- `getCurrentFileType` — _(unfilled)_
- `translateCurrentImage` — _(unfilled)_
- `updateTextViewerTranslationButtonIcon` — _(unfilled)_
- `applyTextViewerFontSettings` — _(unfilled)_
- `applyTranslationManagerFontSettings` — _(unfilled)_
- `applyEpubFontSettings` — _(unfilled)_
- `forceTranslatePdf` — _(unfilled)_
- `forceTranslateText` — _(unfilled)_
- `forceTranslateEpub` — _(unfilled)_
- `setupTranslationDefaults` — _(unfilled)_
- `setupTranslationButtonIcons` — _(unfilled)_
- `showTranslationSettingsDialog` — _(unfilled)_
- `applyFontSettingsToOverlay` — _(unfilled)_

### `TranslationManager` — [com/sza/fastmediasorter/ui/player/helpers/TranslationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationManager.kt)

**Layer:** ui · **LOC:** 962 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `applyFontSettings` — _(unfilled)_
- `getTargetLanguageCode` — _(unfilled)_
- `mlKitToTesseractLang` — _(unfilled)_
- `languageCodeToMLKit` — _(unfilled)_
- `getEnglishLanguageName` — _(unfilled)_
- `getNativeLanguageName` — _(unfilled)_
- `buildSourceLanguageList` — _(unfilled)_
- `buildTargetLanguageList` — _(unfilled)_
- `convertLatinToCyrillic` — _(unfilled)_
- `showError` — _(unfilled)_
- `showModelDownloadPrompt` — _(unfilled)_
- `getTextRecognizer` — _(unfilled)_
- `detectLanguage` — _(unfilled)_
- `isDirectTranslationSupported` — _(unfilled)_
- `extractTextOnly` — _(unfilled)_
- `translate` — _(unfilled)_
- `translateDirect` — _(unfilled)_
- `cleanOcrText` — _(unfilled)_
- `recognizeText` — _(unfilled)_
- `recognizeAndTranslate` — _(unfilled)_
- `recognizeAndTranslateBlocks` — _(unfilled)_
- `getLanguageName` — _(unfilled)_
- `release` — _(unfilled)_

### `TranslationTextUtils` — [com/sza/fastmediasorter/ui/player/helpers/TranslationTextUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationTextUtils.kt)

**Layer:** ui · **LOC:** 70 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `cleanOcrText` — _(unfilled)_
- `getLanguageName` — _(unfilled)_

### `TtsReadAloudManager` — [com/sza/fastmediasorter/ui/player/helpers/TtsReadAloudManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TtsReadAloudManager.kt)

**Layer:** ui · **LOC:** 186 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onInit` — _(unfilled)_
- `onStart` — _(unfilled)_
- `onDone` — _(unfilled)_
- `onError` — _(unfilled)_
- `startReading` — _(unfilled)_
- `stop` — _(unfilled)_
- `toggle` — _(unfilled)_
- `release` — _(unfilled)_
- `isReading` — _(unfilled)_
- `getState` — _(unfilled)_
- `speakInternal` — _(unfilled)_
- `splitTextAtBoundaries` — _(unfilled)_
- `updateState` — _(unfilled)_

### `UndoOperationManager` — [com/sza/fastmediasorter/ui/player/helpers/UndoOperationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/UndoOperationManager.kt)

**Layer:** ui · **LOC:** 68 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

**Functions:**

- `isActivityAlive` — _(unfilled)_
- `getUndoActionText` — _(unfilled)_
- `onUndoRequested` — _(unfilled)_
- `showUndoSnackbar` — _(unfilled)_
- `getOperationDescription` — _(unfilled)_
- `defaultUndoActionText` — _(unfilled)_

### `VideoTouchDelegate` — [com/sza/fastmediasorter/ui/player/helpers/VideoTouchDelegate.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoTouchDelegate.kt)

**Layer:** ui · **LOC:** 233 · **Last:** 2026-04-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onDown` — _(unfilled)_
- `onSingleTapConfirmed` — _(unfilled)_
- `onDoubleTap` — _(unfilled)_
- `onScroll` — _(unfilled)_
- `handleTouchEvent` — _(unfilled)_
- `isVideoGestureArea` — _(unfilled)_
- `togglePlayerController` — _(unfilled)_
- `getCurrentBrightnessProgress` — _(unfilled)_
- `applyBrightnessProgress` — _(unfilled)_
- `showIndicator` — _(unfilled)_
- `scheduleIndicatorHide` — _(unfilled)_

### `WindowMetricsCompat` — [com/sza/fastmediasorter/ui/player/helpers/WindowMetricsCompat.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/WindowMetricsCompat.kt)

**Layer:** ui · **LOC:** 70 · **Last:** 2026-02-12 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getScreenWidth` — _(unfilled)_
- `getScreenHeight` — _(unfilled)_
- `getScreenSize` — _(unfilled)_

### `ImageLoadingDiagnostics` — [com/sza/fastmediasorter/ui/player/ImageLoadingDiagnostics.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingDiagnostics.kt)

**Layer:** ui · **LOC:** 89 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `isNonCriticalNetworkImageError` — _(unfilled)_
- `logMemoryStats` — _(unfilled)_

### `ImageLoadingManager` — [com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt)

**Layer:** ui · **LOC:** 1257 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `isFinishing` — _(unfilled)_
- `isDestroyed` — _(unfilled)_
- `releasePlayer` — _(unfilled)_
- `showError` — _(unfilled)_
- `showToast` — _(unfilled)_
- `getWindowManager` — _(unfilled)_
- `onAudioMetadataLoaded` — _(unfilled)_
- `updateSlideShow` — _(unfilled)_
- `getAdjacentFiles` — _(unfilled)_
- `getCurrentFile` — _(unfilled)_
- `getCurrentResource` — _(unfilled)_
- `getExoPlayer` — _(unfilled)_
- `getString` — _(unfilled)_
- `isShowingCommandPanel` — _(unfilled)_
- `isSlideshowActive` — _(unfilled)_
- `setAnimatedBadgeVisible` — _(unfilled)_
- `getAdjacentFiles` — _(unfilled)_
- `getCurrentResource` — _(unfilled)_
- `getString` — _(unfilled)_
- `getExoPlayer` — _(unfilled)_
- `onAudioMetadataLoaded` — _(unfilled)_
- `setAudioEmptyStateController` — _(unfilled)_
- `setSlideshowBias` — _(unfilled)_
- `setStereoMode` — _(unfilled)_
- `setPanelStereoSingleEyeEnabled` — _(unfilled)_
- `setDynamicBackgroundEnabled` — _(unfilled)_
- `clearDynamicBackground` — _(unfilled)_
- `clearForVideoTransition` — _(unfilled)_
- `triggerVideoBackground` — _(unfilled)_
- `cleanup` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onResume` — _(unfilled)_
- `isCurrentAnimatedContent` — _(unfilled)_
- `isAnimatedPlaybackPaused` — _(unfilled)_
- `toggleAnimatedPlayback` — _(unfilled)_
- `hideAnimatedBadge` — _(unfilled)_
- `reEvaluateScaleTypeOnRotation` — _(unfilled)_
- `displayImage` — _(unfilled)_
- `loadCloudImage` — _(unfilled)_
- `loadNetworkImage` — _(unfilled)_
- `loadLocalImage` — _(unfilled)_
- `createGlideListener` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `onResourceReady` — _(unfilled)_
- `createGifGlideListener` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `onResourceReady` — _(unfilled)_
- `isNonCriticalNetworkImageError` — _(unfilled)_
- `preloadNextImageIfNeeded` — _(unfilled)_
- `showAudioFileInfo` — _(unfilled)_
- `updateAudioFormatInfo` — _(unfilled)_
- `loadAudioCoverArt` — _(unfilled)_
- `updateButtonVisibility` — _(unfilled)_
- `logMemoryStats` — _(unfilled)_
- `clearMemoryCache` — _(unfilled)_

### `ImagePreloadHelper` — [com/sza/fastmediasorter/ui/player/ImagePreloadHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImagePreloadHelper.kt)

**Layer:** ui · **LOC:** 227 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getAdjacentFiles` — _(unfilled)_
- `getCurrentResource` — _(unfilled)_
- `setSlideshowBias` — _(unfilled)_
- `cancelStaleJobsForPaths` — _(unfilled)_
- `preloadNextImageIfNeeded` — _(unfilled)_
- `cleanup` — _(unfilled)_
- `getResourceKey` — _(unfilled)_
- `preloadAdjacentTarget` — _(unfilled)_
- `preloadNetworkFile` — _(unfilled)_
- `preloadCloudFile` — _(unfilled)_
- `preloadLocalFile` — _(unfilled)_

### `MediaButtonRestartReceiver` — [com/sza/fastmediasorter/ui/player/MediaButtonRestartReceiver.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/MediaButtonRestartReceiver.kt)

**Layer:** ui · **LOC:** 75 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onReceive` — _(unfilled)_

### `MediaNotificationManager` — [com/sza/fastmediasorter/ui/player/MediaNotificationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/MediaNotificationManager.kt)

**Layer:** ui · **LOC:** 73 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `createNotificationChannel` — _(unfilled)_
- `createNotificationProvider` — _(unfilled)_

### `MediaItemWithMeta` — [com/sza/fastmediasorter/ui/player/model/MediaItemWithMeta.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/model/MediaItemWithMeta.kt)

**Layer:** ui · **LOC:** 16 · **Last:** 2026-03-30 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `TouchZoneHintType` — [com/sza/fastmediasorter/ui/player/model/TouchZoneHintType.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/model/TouchZoneHintType.kt)

**Layer:** ui · **LOC:** 15 · **Last:** 2026-03-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** —

**Role:** _(unfilled)_

### `Mp4SpatialMetadataReader` — [com/sza/fastmediasorter/ui/player/Mp4SpatialMetadataReader.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/Mp4SpatialMetadataReader.kt)

**Layer:** ui · **LOC:** 215 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `detectStereoMode` — _(unfilled)_
- `openLocalChannel` — _(unfilled)_
- `isRemotePath` — _(unfilled)_
- `parseBoxes` — _(unfilled)_
- `parseStsd` — _(unfilled)_
- `readSt3dStereoLayout` — _(unfilled)_
- `readHeader` — _(unfilled)_
- `readFully` — _(unfilled)_
- `isComplete` — _(unfilled)_
- `toStereoMode` — _(unfilled)_

### `NowPlayingBottomSheetFragment` — [com/sza/fastmediasorter/ui/player/NowPlayingBottomSheetFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/NowPlayingBottomSheetFragment.kt)

**Layer:** ui · **LOC:** 230 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `bindViews` — _(unfilled)_
- `setupQueueRecycler` — _(unfilled)_
- `setupClickListeners` — _(unfilled)_
- `setupSeekBar` — _(unfilled)_
- `onStartTrackingTouch` — _(unfilled)_
- `onStopTrackingTouch` — _(unfilled)_
- `onProgressChanged` — _(unfilled)_
- `observeState` — _(unfilled)_
- `updateNowPlayingPanel` — _(unfilled)_
- `updateQueuePanel` — _(unfilled)_
- `showQueuePanel` — _(unfilled)_
- `showNowPlayingPanel` — _(unfilled)_
- `formatMs` — _(unfilled)_
- `newInstance` — _(unfilled)_

### `NowPlayingViewModel` — [com/sza/fastmediasorter/ui/player/NowPlayingViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/NowPlayingViewModel.kt)

**Layer:** ui · **LOC:** 214 · **Last:** 2026-04-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `onIsPlayingChanged` — _(unfilled)_
- `onPlaybackStateChanged` — _(unfilled)_
- `onMediaItemTransition` — _(unfilled)_
- `onTimelineChanged` — _(unfilled)_
- `connect` — _(unfilled)_
- `disconnect` — _(unfilled)_
- `togglePlayPause` — _(unfilled)_
- `seekToNext` — _(unfilled)_
- `seekToPrevious` — _(unfilled)_
- `seekTo` — _(unfilled)_
- `jumpToQueueItem` — _(unfilled)_
- `startPositionPoll` — _(unfilled)_
- `stopPositionPoll` — _(unfilled)_
- `refreshQueueItems` — _(unfilled)_
- `onCleared` — _(unfilled)_

### `PlaybackControlDialogFragment` — [com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt)

**Layer:** ui · **LOC:** 705 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `onStart` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `onSaveInstanceState` — _(unfilled)_
- `setupSectionNavigation` — _(unfilled)_
- `selectedSectionIndex` — _(unfilled)_
- `updateVisibleSection` — _(unfilled)_
- `setupVolumeTab` — _(unfilled)_
- `onProgressChanged` — _(unfilled)_
- `onStartTrackingTouch` — _(unfilled)_
- `onStopTrackingTouch` — _(unfilled)_
- `applyVolumePreset` — _(unfilled)_
- `syncMuteToggleUi` — _(unfilled)_
- `setupAudioTab` — _(unfilled)_
- `refreshAudioTab` — _(unfilled)_
- `setupSubtitleTab` — _(unfilled)_
- `refreshSubtitleTab` — _(unfilled)_
- `setupStereoSection` — _(unfilled)_
- `setupVrStereoControls` — _(unfilled)_
- `onProgressChanged` — _(unfilled)_
- `onStartTrackingTouch` — _(unfilled)_
- `onStopTrackingTouch` — _(unfilled)_
- `updateIpdLabel` — _(unfilled)_
- `bindStereoMode` — _(unfilled)_
- `handleStereoModeSelection` — _(unfilled)_
- `updateStereoDetectedLabel` — _(unfilled)_
- `updateStereoFamilyAvailability` — _(unfilled)_
- `setGroupEnabled` — _(unfilled)_
- `resolveStereoFamily` — _(unfilled)_
- `flatRadioIdFor` — _(unfilled)_
- `sphericalRadioIdFor` — _(unfilled)_
- `stereoModeLabel` — _(unfilled)_
- `setupHueTab` — _(unfilled)_
- `onProgressChanged` — _(unfilled)_
- `onStartTrackingTouch` — _(unfilled)_
- `onStopTrackingTouch` — _(unfilled)_
- `setupBrightnessTab` — _(unfilled)_
- `onProgressChanged` — _(unfilled)_
- `onStartTrackingTouch` — _(unfilled)_
- `onStopTrackingTouch` — _(unfilled)_
- `setupSpeedTab` — _(unfilled)_
- `onProgressChanged` — _(unfilled)_
- `onStartTrackingTouch` — _(unfilled)_
- `onStopTrackingTouch` — _(unfilled)_
- `updateVolumeLabel` — _(unfilled)_
- `updateBrightnessLabel` — _(unfilled)_
- `updateHueLabel` — _(unfilled)_
- `updateSpeedLabel` — _(unfilled)_
- `hueToProgress` — _(unfilled)_
- `progressToHue` — _(unfilled)_
- `resetHue` — _(unfilled)_
- `resetBrightness` — _(unfilled)_
- `resetSpeed` — _(unfilled)_
- `host` — _(unfilled)_
- `newInstance` — _(unfilled)_

### `PlaybackControlPreferences` — [com/sza/fastmediasorter/ui/player/PlaybackControlPreferences.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlPreferences.kt)

**Layer:** ui · **LOC:** 15 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `PlayerActivity` — [com/sza/fastmediasorter/ui/player/PlayerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt)

**Layer:** ui · **LOC:** 916 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `getViewBinding` — _(unfilled)_
- `isPdfActive` — _(unfilled)_
- `shouldEnableEdgeToEdge` — _(unfilled)_
- `onCreate` — _(unfilled)_
- `initializeManagers` — _(unfilled)_
- `onConfigurationChanged` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `observeData` — _(unfilled)_
- `exitPlayerWithAudioCheck` — _(unfilled)_
- `setupGestureDetector` — _(unfilled)_
- `setupToolbar` — _(unfilled)_
- `setupControls` — _(unfilled)_
- `setupCommandPanelControls` — _(unfilled)_
- `setupTouchZones` — _(unfilled)_
- `showTouchZoneHintOverlay` — _(unfilled)_
- `showTouchZonesHelpOverlay` — _(unfilled)_
- `adjustTouchZonesForVideo` — _(unfilled)_
- `updateUI` — _(unfilled)_
- `updatePanelVisibility` — _(unfilled)_
- `updateSystemBarsForPlayer` — _(unfilled)_
- `tryHandleFullscreenCommandOverride` — _(unfilled)_
- `tryHandleSaveFrameCommandOverride` — _(unfilled)_
- `tryHandleSystemUiCommandOverride` — _(unfilled)_
- `updateCommandAvailability` — _(unfilled)_
- `displayText` — _(unfilled)_
- `displayImage` — _(unfilled)_
- `playVideo` — _(unfilled)_
- `updateSlideShow` — _(unfilled)_
- `updatePlayPauseButton` — _(unfilled)_
- `isCurrentAnimatedContent` — _(unfilled)_
- `toggleAnimatedPlayback` — _(unfilled)_
- `clearUiOverlayForAnimatedPause` — _(unfilled)_
- `clearImageMemoryCache` — _(unfilled)_
- `setSlideshowKeepAwake` — _(unfilled)_
- `adjustVolume` — _(unfilled)_
- `scheduleHideControls` — _(unfilled)_
- `updateAudioTouchZonesVisibility` — _(unfilled)_
- `showFileInfo` — _(unfilled)_
- `isAnimatedImagePath` — _(unfilled)_
- `searchAndShowLyrics` — _(unfilled)_
- `searchInYoutubeMusic` — _(unfilled)_
- `castCurrentMedia` — _(unfilled)_
- `hideLyricsViewer` — _(unfilled)_
- `saveCurrentFrame` — _(unfilled)_
- `handle3dVrToggleClicked` — _(unfilled)_
- `launchImmersiveOnCurrentFile` — _(unfilled)_
- `handleDeleteSuccess` — _(unfilled)_
- `handleEvent` — _(unfilled)_
- `showError` — _(unfilled)_
- `showFileNotFound` — _(unfilled)_
- `showCloudAuthenticationError` — _(unfilled)_
- `showUnsupportedFormatError` — _(unfilled)_
- `showSlideshowEnabledMessage` — _(unfilled)_
- `populateDestinationButtons` — _(unfilled)_
- `performCopyOperation` — _(unfilled)_
- `performMoveOperation` — _(unfilled)_
- `showAudioFileInfo` — _(unfilled)_
- `updateAudioFormatInfo` — _(unfilled)_
- `updateTrackButtonsVisibility` — _(unfilled)_
- `prefetchNextAudio` — _(unfilled)_
- `handleMediaLoadErrorAndSkip` — _(unfilled)_
- `releasePlayer` — _(unfilled)_
- `stopVideoPlayback` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onUserLeaveHint` — _(unfilled)_
- `onPictureInPictureModeChanged` — _(unfilled)_
- `shareCurrentFile` — _(unfilled)_
- `stopTranslation` — _(unfilled)_
- `translateCurrentImage` — _(unfilled)_
- `extractTextFromCurrentImage` — _(unfilled)_
- `onResume` — _(unfilled)_
- `updatePlayerFpsOverlay` — _(unfilled)_
- `onKeyDown` — _(unfilled)_
- `dispatchKeyEvent` — _(unfilled)_
- `dispatchGenericMotionEvent` — _(unfilled)_
- `routePlayerGamepadAction` — _(unfilled)_
- `advanceAudioBackgroundPhoto` — _(unfilled)_
- `updateAudioSlideshowCurrentSongLabel` — _(unfilled)_
- `onDestroy` — _(unfilled)_
- `setupGoogleLensButtons` — _(unfilled)_
- `shareCurrentFileToGoogleLens` — _(unfilled)_
- `isOverlayBlocking` — _(unfilled)_
- `getTouchZonesEnabled` — _(unfilled)_
- `isInAudioSlideshowPhotoMode` — _(unfilled)_
- `onAudioMetadataLoaded` — _(unfilled)_
- `setStereoMode` — _(unfilled)_
- `rememberStereoModeIfEnabled` — _(unfilled)_
- `showMessage` — _(unfilled)_
- `requestFinishAfterDelete` — _(unfilled)_
- `getAvailableAudioTracks` — _(unfilled)_
- `selectAudioTrack` — _(unfilled)_
- `getAvailableSubtitleTracks` — _(unfilled)_
- `selectSubtitleTrack` — _(unfilled)_
- `getHueAdjustmentDegrees` — _(unfilled)_
- `setHueAdjustmentDegrees` — _(unfilled)_
- `getBrightnessProgress` — _(unfilled)_
- `setBrightnessProgress` — _(unfilled)_
- `getBrightnessPercentOffset` — _(unfilled)_
- `getPlaybackSpeed` — _(unfilled)_
- `setPlaybackSpeed` — _(unfilled)_
- `createIntent` — _(unfilled)_

### `PlayerDialogHelper` — [com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt)

**Layer:** ui · **LOC:** 649 · **Last:** 2026-04-20 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk, prefs  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `setAuthCallback` — _(unfilled)_
- `safeShow` — _(unfilled)_
- `dismissAll` — _(unfilled)_
- `showPlaybackControlDialog` — _(unfilled)_
- `onImageEditComplete` — _(unfilled)_
- `onGifEditComplete` — _(unfilled)_
- `onRenameComplete` — _(unfilled)_
- `showCopyDialog` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getName` — _(unfilled)_
- `length` — _(unfilled)_
- `showMoveDialog` — _(unfilled)_
- `showMoveDialogInternal` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getName` — _(unfilled)_
- `length` — _(unfilled)_
- `showRenameDialog` — _(unfilled)_
- `getAbsolutePath` — _(unfilled)_
- `getPath` — _(unfilled)_
- `getName` — _(unfilled)_
- `length` — _(unfilled)_
- `showFileInfo` — _(unfilled)_
- `showImageEditDialog` — _(unfilled)_
- `showGifEditDialog` — _(unfilled)_
- `isAnimatedImagePath` — _(unfilled)_
- `showPlayerSettingsDialog` — _(unfilled)_
- `showCloudAuthError` — _(unfilled)_
- `showPdfEditDialog` — _(unfilled)_
- `showEncodingDialog` — _(unfilled)_
- `showReaderSettingsDialog` — _(unfilled)_
- `showSleepTimerDialog` — _(unfilled)_
- `showAudioTrackDialog` — _(unfilled)_
- `showSubtitleTrackDialog` — _(unfilled)_
- `exportPdfToJpg` — _(unfilled)_

### `PlayerGestureHelper` — [com/sza/fastmediasorter/ui/player/PlayerGestureHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerGestureHelper.kt)

**Layer:** ui · **LOC:** 247 · **Last:** 2026-02-17 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onSwipeLeft` — _(unfilled)_
- `onSwipeRight` — _(unfilled)_
- `onSwipeUp` — _(unfilled)_
- `onSwipeDown` — _(unfilled)_
- `onDoubleTap` — _(unfilled)_
- `onLongPress` — _(unfilled)_
- `onTouchZone` — _(unfilled)_
- `setupGestureDetector` — _(unfilled)_
- `onFling` — _(unfilled)_
- `onDoubleTap` — _(unfilled)_
- `onLongPress` — _(unfilled)_
- `handleTouch` — _(unfilled)_
- `detectTouchZone` — _(unfilled)_
- `handleCommandPanelTouch` — _(unfilled)_
- `showFirstRunHintOverlay` — _(unfilled)_
- `cleanup` — _(unfilled)_

### `PlayerManagerInitializer` — [com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt)

**Layer:** ui · **LOC:** 781 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `initialize` — _(unfilled)_
- `initPrefetchManager` — _(unfilled)_
- `initBackgroundMedia` — _(unfilled)_
- `initCoreCoordination` — _(unfilled)_
- `onAuthenticationSuccess` — _(unfilled)_
- `onAuthenticationFailure` — _(unfilled)_
- `isActivityAlive` — _(unfilled)_
- `getUndoActionText` — _(unfilled)_
- `onUndoRequested` — _(unfilled)_
- `initDialogHelper` — _(unfilled)_
- `onImageEditComplete` — _(unfilled)_
- `onGifEditComplete` — _(unfilled)_
- `onRenameComplete` — _(unfilled)_
- `initFileOps` — _(unfilled)_
- `onCopySuccess` — _(unfilled)_
- `onMoveSuccess` — _(unfilled)_
- `onDeleteSuccess` — _(unfilled)_
- `onOperationError` — _(unfilled)_
- `onAuthenticationRequired` — _(unfilled)_
- `onBatchDeletePermissionRequired` — _(unfilled)_
- `getCurrentFile` — _(unfilled)_
- `getCurrentResource` — _(unfilled)_
- `onCopyClicked` — _(unfilled)_
- `onMoveClicked` — _(unfilled)_
- `getCurrentResourceId` — _(unfilled)_
- `onUpdateCommandAvailability` — _(unfilled)_
- `isCommandPanelVisible` — _(unfilled)_
- `initCommandPanelAndImageLoading` — _(unfilled)_
- `initNetworkAndTranslation` — _(unfilled)_
- `getCurrentResource` — _(unfilled)_
- `showError` — _(unfilled)_
- `showError` — _(unfilled)_
- `showModelDownloadPrompt` — _(unfilled)_
- `initPlayerControlsAndOcr` — _(unfilled)_
- `onPreviousFile` — _(unfilled)_
- `onNextFile` — _(unfilled)_
- `showPlaybackControlDialog` — _(unfilled)_
- `getCurrentMediaFile` — _(unfilled)_
- `scheduleHideControls` — _(unfilled)_
- `onEpubTranslate` — _(unfilled)_
- `showTranslationSettingsDialog` — _(unfilled)_
- `showError` — _(unfilled)_
- `getString` — _(unfilled)_
- `getString` — _(unfilled)_
- `initAudioAndMediaServices` — _(unfilled)_
- `initUiCoordinators` — _(unfilled)_
- `updateSlideShowButton` — _(unfilled)_
- `updateSystemBarsForPlayer` — _(unfilled)_
- `toggleSlideshow` — _(unfilled)_
- `updateSlideshowState` — _(unfilled)_
- `getSupportActionBar` — _(unfilled)_
- `initSetupManagers` — _(unfilled)_

### `PlayerObserverManager` — [com/sza/fastmediasorter/ui/player/PlayerObserverManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerObserverManager.kt)

**Layer:** ui · **LOC:** 94 · **Last:** 2026-04-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `startObserving` — _(unfilled)_
- `updateUI` — _(unfilled)_

### `PlayerViewerFactory` — [com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt)

**Layer:** ui · **LOC:** 156 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `createVideoPlayerManager` — _(unfilled)_
- `createPdfViewerManager` — _(unfilled)_
- `showError` — _(unfilled)_
- `displayOcrText` — _(unfilled)_
- `displayTranslatedText` — _(unfilled)_
- `shareFileToGoogleLens` — _(unfilled)_
- `isLandscapeMode` — _(unfilled)_
- `onEnterFullscreenMode` — _(unfilled)_
- `onExitFullscreenMode` — _(unfilled)_
- `createEpubViewerManager` — _(unfilled)_
- `showError` — _(unfilled)_
- `displayTranslatedText` — _(unfilled)_
- `onEnterFullscreenMode` — _(unfilled)_
- `onExitFullscreenMode` — _(unfilled)_
- `createTextViewerManager` — _(unfilled)_
- `showError` — _(unfilled)_
- `showTranslationSettingsDialog` — _(unfilled)_
- `exitFullscreenMode` — _(unfilled)_
- `setTouchZonesEnabled` — _(unfilled)_
- `showEncodingDialog` — _(unfilled)_

### `PlayerViewModel` — [com/sza/fastmediasorter/ui/player/PlayerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt)

**Layer:** ui · **LOC:** 708 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** SavedStateHandle, GetResourcesUseCase, GetMediaFilesUseCase, FileOperationUseCase, GetDestinationsUseCase, SettingsRepository, StereoFormatOverrideDao, CachedFileListRepository, PrefetchPolicyManager, StreamOffloadUseCase, StreamingCacheRepository  
**Side effects:** network, disk, prefs  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `getInitialState` — _(unfilled)_
- `saveResumeState` — _(unfilled)_
- `reloadFiles` — _(unfilled)_
- `setStereoMode` — _(unfilled)_
- `setAutoDetectedStereoMode` — _(unfilled)_
- `resetStereoModeForNewFile` — _(unfilled)_
- `rememberStereoModeIfEnabled` — _(unfilled)_
- `showMessage` — _(unfilled)_
- `showVrInstallCta` — _(unfilled)_
- `updatePrefetchPlan` — _(unfilled)_
- `bindPrefetchTracker` — _(unfilled)_
- `unbindPrefetchTracker` — _(unfilled)_
- `emitOffloadOffer` — _(unfilled)_
- `acceptOffload` — _(unfilled)_
- `declineOffload` — _(unfilled)_
- `cancelOffload` — _(unfilled)_
- `switchToLocalFile` — _(unfilled)_
- `requestCleanupIfNeeded` — _(unfilled)_
- `deleteLocalCopy` — _(unfilled)_
- `reloadFiles` — _(unfilled)_
- `updateCastState` — _(unfilled)_
- `loadSettings` — _(unfilled)_
- `loadMediaFiles` — _(unfilled)_
- `getLookaheadTargets` — _(unfilled)_
- `getCredentialsIdForResource` — _(unfilled)_
- `syncAudioServiceIndex` — _(unfilled)_
- `jumpToIndex` — _(unfilled)_
- `nextFile` — _(unfilled)_
- `previousFile` — _(unfilled)_
- `cancelLoading` — _(unfilled)_
- `clearResumeState` — _(unfilled)_
- `saveResumeState` — _(unfilled)_
- `toggleSlideShow` — _(unfilled)_
- `forceStateUpdate` — _(unfilled)_
- `setSlideShowActive` — _(unfilled)_
- `setSlideShowInterval` — _(unfilled)_
- `setPlayToEndInSlideshow` — _(unfilled)_
- `setSlideshowMusic` — _(unfilled)_
- `saveSlideshowSettings` — _(unfilled)_
- `updateExitBehavior` — _(unfilled)_
- `toggleControls` — _(unfilled)_
- `togglePause` — _(unfilled)_
- `setPaused` — _(unfilled)_
- `toggleCommandPanel` — _(unfilled)_
- `enterFullscreenMode` — _(unfilled)_
- `enterCommandPanelMode` — _(unfilled)_
- `deleteCurrentFile` — _(unfilled)_
- `reloadAfterRename` — _(unfilled)_
- `saveUndoOperation` — _(unfilled)_
- `undoLastOperation` — _(unfilled)_
- `clearExpiredUndoOperation` — _(unfilled)_
- `getSettings` — _(unfilled)_
- `getAdjacentFiles` — _(unfilled)_
- `getNextAudioFile` — _(unfilled)_
- `saveLastViewedFile` — _(unfilled)_
- `onFileMoved` — _(unfilled)_
- `refreshCurrentFileInfo` — _(unfilled)_
- `removeMovedFile` — _(unfilled)_
- `removeDeletedFile` — _(unfilled)_
- `removeFileFromList` — _(unfilled)_
- `toggleFavorite` — _(unfilled)_

### `DualSurfaceStaticImageRenderer` — [com/sza/fastmediasorter/ui/player/render/DualSurfaceStaticImageRenderer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/DualSurfaceStaticImageRenderer.kt)

**Layer:** ui · **LOC:** 444 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk, prefs  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `render` — _(unfilled)_
- `prefetch` — _(unfilled)_
- `setMode` — _(unfilled)_
- `setStereoMode` — _(unfilled)_
- `setPanelStereoSingleEyeEnabled` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onResume` — _(unfilled)_
- `release` — _(unfilled)_
- `getActiveSurfaceView` — _(unfilled)_
- `getInactiveSurface` — _(unfilled)_
- `swapSurfaces` — _(unfilled)_
- `renderToSingleSurface` — _(unfilled)_
- `loadImageIntoSurface` — _(unfilled)_
- `loadWithGlide` — _(unfilled)_
- `onLoadFailed` — _(unfilled)_
- `onResourceReady` — _(unfilled)_
- `resolveGlideModel` — _(unfilled)_
- `resolveCloudProvider` — _(unfilled)_
- `resolvePriority` — _(unfilled)_
- `prefetchTarget` — _(unfilled)_
- `getTransitionPolicy` — _(unfilled)_
- `updateState` — _(unfilled)_
- `create` — _(unfilled)_

### `LegacyPrefetchQueue` — [com/sza/fastmediasorter/ui/player/render/LegacyPrefetchQueue.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/LegacyPrefetchQueue.kt)

**Layer:** ui · **LOC:** 41 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `offer` — _(unfilled)_
- `offerAll` — _(unfilled)_
- `pollNext` — _(unfilled)_
- `clear` — _(unfilled)_
- `size` — _(unfilled)_
- `updateConfig` — _(unfilled)_

### `NoOpStaticImageRenderer` — [com/sza/fastmediasorter/ui/player/render/NoOpStaticImageRenderer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/NoOpStaticImageRenderer.kt)

**Layer:** ui · **LOC:** 32 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `render` — _(unfilled)_
- `prefetch` — _(unfilled)_
- `setMode` — _(unfilled)_
- `setStereoMode` — _(unfilled)_
- `setPanelStereoSingleEyeEnabled` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onResume` — _(unfilled)_
- `release` — _(unfilled)_

### `PrefetchQueueConfig` — [com/sza/fastmediasorter/ui/player/render/PrefetchQueue.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/PrefetchQueue.kt)

**Layer:** ui · **LOC:** 16 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `offer` — _(unfilled)_
- `offerAll` — _(unfilled)_
- `pollNext` — _(unfilled)_
- `clear` — _(unfilled)_
- `size` — _(unfilled)_
- `updateConfig` — _(unfilled)_

### `PriorityPrefetchQueue` — [com/sza/fastmediasorter/ui/player/render/PriorityPrefetchQueue.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/PriorityPrefetchQueue.kt)

**Layer:** ui · **LOC:** 138 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `offer` — _(unfilled)_
- `offerAll` — _(unfilled)_
- `pollNext` — _(unfilled)_
- `clear` — _(unfilled)_
- `size` — _(unfilled)_
- `updateConfig` — _(unfilled)_
- `computePriority` — _(unfilled)_

### `RenderPriority` — [com/sza/fastmediasorter/ui/player/render/RenderTarget.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/RenderTarget.kt)

**Layer:** ui · **LOC:** 24 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `RendererMode` — [com/sza/fastmediasorter/ui/player/render/StaticImageRenderer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/StaticImageRenderer.kt)

**Layer:** ui · **LOC:** 44 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `render` — _(unfilled)_
- `prefetch` — _(unfilled)_
- `setMode` — _(unfilled)_
- `setStereoMode` — _(unfilled)_
- `setPanelStereoSingleEyeEnabled` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onResume` — _(unfilled)_
- `release` — _(unfilled)_

### `StereoImageCropTransformation` — [com/sza/fastmediasorter/ui/player/render/StereoImageCropTransformation.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/StereoImageCropTransformation.kt)

**Layer:** ui · **LOC:** 66 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `transform` — _(unfilled)_
- `equals` — _(unfilled)_
- `hashCode` — _(unfilled)_
- `updateDiskCacheKey` — _(unfilled)_

### `TransitionType` — [com/sza/fastmediasorter/ui/player/render/TransitionPolicy.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/TransitionPolicy.kt)

**Layer:** ui · **LOC:** 18 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `SlideshowController` — [com/sza/fastmediasorter/ui/player/SlideshowController.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/SlideshowController.kt)

**Layer:** ui · **LOC:** 290 · **Last:** 2026-03-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onSlideAdvance` — _(unfilled)_
- `onSlideshowStateChanged` — _(unfilled)_
- `onCountdownTick` — _(unfilled)_
- `onMemoryCacheClear` — _(unfilled)_
- `isRendererReady` — _(unfilled)_
- `setKeepScreenAwake` — _(unfilled)_
- `shouldSuppressTimer` — _(unfilled)_
- `run` — _(unfilled)_
- `run` — _(unfilled)_
- `startSlideshow` — _(unfilled)_
- `pauseSlideshow` — _(unfilled)_
- `resumeSlideshow` — _(unfilled)_
- `stopSlideshow` — _(unfilled)_
- `updateInterval` — _(unfilled)_
- `isActive` — _(unfilled)_
- `isPaused` — _(unfilled)_
- `restartTimer` — _(unfilled)_
- `scheduleNextSlide` — _(unfilled)_
- `showCountdown` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onResume` — _(unfilled)_
- `onDestroy` — _(unfilled)_
- `cleanup` — _(unfilled)_

### `SlideshowSettingsDialogFragment` — [com/sza/fastmediasorter/ui/player/SlideshowSettingsDialogFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/SlideshowSettingsDialogFragment.kt)

**Layer:** ui · **LOC:** 198 · **Last:** 2026-03-10 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreateDialog` — _(unfilled)_
- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `setupUI` — _(unfilled)_
- `onStartTrackingTouch` — _(unfilled)_
- `onStopTrackingTouch` — _(unfilled)_
- `observeViewModel` — _(unfilled)_
- `openMusicPicker` — _(unfilled)_
- `handleMusicSelection` — _(unfilled)_
- `releaseUriPermissions` — _(unfilled)_
- `getFileName` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `onStart` — _(unfilled)_

### `StandalonePlayerActivity` — [com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt)

**Layer:** ui · **LOC:** 1035 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, disk  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `startActionMode` — _(unfilled)_
- `onCreateActionMode` — _(unfilled)_
- `onPrepareActionMode` — _(unfilled)_
- `onActionItemClicked` — _(unfilled)_
- `onDestroyActionMode` — _(unfilled)_
- `onGetContentRect` — _(unfilled)_
- `getViewBinding` — _(unfilled)_
- `shouldEnableEdgeToEdge` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `setupKeyboardHandler` — _(unfilled)_
- `onDeleteFile` — _(unfilled)_
- `onExitPlayer` — _(unfilled)_
- `onToggleSlideshow` — _(unfilled)_
- `onShowRenameDialog` — _(unfilled)_
- `onShowFileInfo` — _(unfilled)_
- `onToggleCommandPanel` — _(unfilled)_
- `onToggleCopyPanel` — _(unfilled)_
- `onToggleMovePanel` — _(unfilled)_
- `onShowEditDialog` — _(unfilled)_
- `getActivePlayer` — _(unfilled)_
- `getCurrentMediaType` — _(unfilled)_
- `onPdfNextPage` — _(unfilled)_
- `onPdfPreviousPage` — _(unfilled)_
- `onPdfHome` — _(unfilled)_
- `onPdfEnd` — _(unfilled)_
- `onEpubNextPage` — _(unfilled)_
- `onEpubPreviousPage` — _(unfilled)_
- `onEpubHome` — _(unfilled)_
- `onEpubEnd` — _(unfilled)_
- `onTextScrollDown` — _(unfilled)_
- `onTextScrollUp` — _(unfilled)_
- `onTextHome` — _(unfilled)_
- `onTextEnd` — _(unfilled)_
- `onSeekForward` — _(unfilled)_
- `onSeekBackward` — _(unfilled)_
- `onEpubScrollDelta` — _(unfilled)_
- `onNavigationScroll` — _(unfilled)_
- `onToggleMute` — _(unfilled)_
- `onToggleFullscreen` — _(unfilled)_
- `onChangeVolume` — _(unfilled)_
- `onShowHelp` — _(unfilled)_
- `onDocumentSearch` — _(unfilled)_
- `onSaveCurrent` — _(unfilled)_
- `onShowContextMenu` — _(unfilled)_
- `onNextFile` — _(unfilled)_
- `onPreviousFile` — _(unfilled)_
- `onToggleFavourite` — _(unfilled)_
- `onUndoOperation` — _(unfilled)_
- `onKeyDown` — _(unfilled)_
- `dispatchGenericMotionEvent` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onResume` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onDestroy` — _(unfilled)_
- `standaloneViewManager` — _(unfilled)_
- `standaloneTrackSelectionManager` — _(unfilled)_
- `currentMediaType` — _(unfilled)_
- `showPlaybackControlDialog` — _(unfilled)_
- `onUserLeaveHint` — _(unfilled)_
- `onConfigurationChanged` — _(unfilled)_
- `onPictureInPictureModeChanged` — _(unfilled)_
- `parseIncomingIntent` — _(unfilled)_
- `debugLogLaunchConditions` — _(unfilled)_
- `setupWindowAndInsets` — _(unfilled)_
- `setupCloseButton` — _(unfilled)_
- `setupPdfButtons` — _(unfilled)_
- `setupEpubButtons` — _(unfilled)_
- `setupSearchControls` — _(unfilled)_
- `getCurrentMediaFile` — _(unfilled)_
- `scheduleHideControls` — _(unfilled)_
- `onEpubTranslate` — _(unfilled)_
- `showTranslationSettingsDialog` — _(unfilled)_
- `setupBackPressHandler` — _(unfilled)_
- `handleOnBackPressed` — _(unfilled)_
- `hidePlaylistControls` — _(unfilled)_
- `setupFileOperationButtons` — _(unfilled)_
- `deleteCurrentFile` — _(unfilled)_
- `shareCurrentFile` — _(unfilled)_
- `openInFms` — _(unfilled)_
- `observeFavoriteState` — _(unfilled)_
- `setupPlaybackControls` — _(unfilled)_
- `showPlaybackControlDialog` — _(unfilled)_
- `setupVideoControls` — _(unfilled)_
- `onTracksChanged` — _(unfilled)_
- `observeViewModelState` — _(unfilled)_
- `observeViewModelEvents` — _(unfilled)_
- `updateEpubTranslatorVisibility` — _(unfilled)_
- `observeTranslationSettings` — _(unfilled)_
- `observePipSettings` — _(unfilled)_
- `updateRenameButtonVisibility` — _(unfilled)_
- `showStandaloneRenameDialog` — _(unfilled)_
- `observeMessages` — _(unfilled)_
- `setStereoMode` — _(unfilled)_
- `rememberStereoModeIfEnabled` — _(unfilled)_
- `showMessage` — _(unfilled)_
- `requestFinishAfterDelete` — _(unfilled)_
- `getAvailableAudioTracks` — _(unfilled)_
- `selectAudioTrack` — _(unfilled)_
- `getAvailableSubtitleTracks` — _(unfilled)_
- `selectSubtitleTrack` — _(unfilled)_
- `getHueAdjustmentDegrees` — _(unfilled)_
- `setHueAdjustmentDegrees` — _(unfilled)_
- `getBrightnessProgress` — _(unfilled)_
- `setBrightnessProgress` — _(unfilled)_
- `getBrightnessPercentOffset` — _(unfilled)_
- `getPlaybackSpeed` — _(unfilled)_
- `setPlaybackSpeed` — _(unfilled)_

### `StandalonePlayerViewModel` — [com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt)

**Layer:** ui · **LOC:** 158 · **Last:** 2026-04-24 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, FavoritesUseCase, ResourceRepository, StereoFormatOverrideDao  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber · tests

**Role:** _(unfilled)_

**Functions:**

- `setStereoMode` — _(unfilled)_
- `rememberStereoModeIfEnabled` — _(unfilled)_
- `setAutoDetectedStereoMode` — _(unfilled)_
- `resetStereoModeForNewFile` — _(unfilled)_
- `showMessage` — _(unfilled)_
- `getInitialState` — _(unfilled)_
- `loadFromUri` — _(unfilled)_
- `checkFavoriteStatus` — _(unfilled)_
- `toggleFavorite` — _(unfilled)_
- `onRenameComplete` — _(unfilled)_
- `findResourceForPath` — _(unfilled)_

### `StereoDetector` — [com/sza/fastmediasorter/ui/player/StereoDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt)

**Layer:** ui · **LOC:** 354 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `detectFromFilename` — _(unfilled)_
- `detectFromMp4Path` — _(unfilled)_
- `detectForVideo` — _(unfilled)_
- `detectForImage` — _(unfilled)_
- `detectFromFormat` — _(unfilled)_
- `detectFromDimensions` — _(unfilled)_
- `detectFromMatroskaTag` — _(unfilled)_
- `extractCustomDataBundle` — _(unfilled)_
- `containsToken` — _(unfilled)_
- `detectFromAspectRatio` — _(unfilled)_
- `isNear` — _(unfilled)_
- `logMatch` — _(unfilled)_

### `StereoVideoProcessor` — [com/sza/fastmediasorter/ui/player/StereoVideoProcessor.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoVideoProcessor.kt)

**Layer:** ui · **LOC:** 108 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `setStereoMode` — _(unfilled)_
- `getCurrentMode` — _(unfilled)_
- `buildGlEffect` — _(unfilled)_
- `release` — _(unfilled)_

### `TouchZoneMap` — [com/sza/fastmediasorter/ui/player/TouchZoneConfig.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/TouchZoneConfig.kt)

**Layer:** ui · **LOC:** 373 · **Last:** 2026-02-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getConfiguration` — _(unfilled)_
- `getZoneMapForMediaType` — _(unfilled)_
- `get9ZoneTapAction` — _(unfilled)_
- `get3ZoneImageTapAction` — _(unfilled)_
- `get3ZoneVideoTapAction` — _(unfilled)_
- `getSwipeAction` — _(unfilled)_
- `isDestructiveAction` — _(unfilled)_
- `getActionDescription` — _(unfilled)_

### `TouchZoneDetector` — [com/sza/fastmediasorter/ui/player/TouchZoneDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/TouchZoneDetector.kt)

**Layer:** ui · **LOC:** 121 · **Last:** 2026-02-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `detectAction` — _(unfilled)_
- `calculateColumn` — _(unfilled)_
- `calculateRow` — _(unfilled)_
- `shouldIgnoreSwipe` — _(unfilled)_

### `TouchZoneOverlayView` — [com/sza/fastmediasorter/ui/player/TouchZoneOverlayView.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/TouchZoneOverlayView.kt)

**Layer:** ui · **LOC:** 102 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onDraw` — _(unfilled)_

### `VerticalSeekBar` — [com/sza/fastmediasorter/ui/player/VerticalSeekBar.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VerticalSeekBar.kt)

**Layer:** ui · **LOC:** 76 · **Last:** 2026-04-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onSizeChanged` — _(unfilled)_
- `onMeasure` — _(unfilled)_
- `onDraw` — _(unfilled)_
- `onTouchEvent` — _(unfilled)_

### `VideoColorProcessor` — [com/sza/fastmediasorter/ui/player/VideoColorProcessor.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoColorProcessor.kt)

**Layer:** ui · **LOC:** 71 · **Last:** 2026-04-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `setHueAdjustmentDegrees` — _(unfilled)_
- `getHueAdjustmentDegrees` — _(unfilled)_
- `setBrightnessAdjustment` — _(unfilled)_
- `getBrightnessAdjustment` — _(unfilled)_
- `buildHueEffect` — _(unfilled)_
- `buildBrightnessEffect` — _(unfilled)_
- `release` — _(unfilled)_
- `normalizeHue` — _(unfilled)_
- `normalizeBrightness` — _(unfilled)_

### `VideoPlayerManager` — [com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt)

**Layer:** ui · **LOC:** 905 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network, prefs  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onPlaybackReady` — _(unfilled)_
- `onPlaybackError` — _(unfilled)_
- `onBuffering` — _(unfilled)_
- `onPlaybackStateChanged` — _(unfilled)_
- `onPlaybackEnded` — _(unfilled)_
- `onAudioFormatChanged` — _(unfilled)_
- `showError` — _(unfilled)_
- `showFileNotFound` — _(unfilled)_
- `isActivityDestroyed` — _(unfilled)_
- `showUnsupportedFormatError` — _(unfilled)_
- `onBeforeVideoLoad` — _(unfilled)_
- `onStereoDetected` — _(unfilled)_
- `setPrefetchPlan` — _(unfilled)_
- `setVrImmersiveActive` — _(unfilled)_
- `onPlaybackStateChanged` — _(unfilled)_
- `onIsPlayingChanged` — _(unfilled)_
- `onPlayerError` — _(unfilled)_
- `onRenderedFirstFrame` — _(unfilled)_
- `onTracksChanged` — _(unfilled)_
- `onVideoSizeChanged` — _(unfilled)_
- `setPlayerView` — _(unfilled)_
- `applyStereoEffect` — _(unfilled)_
- `setHueAdjustmentDegrees` — _(unfilled)_
- `getHueAdjustmentDegrees` — _(unfilled)_
- `setBrightnessAdjustment` — _(unfilled)_
- `getBrightnessAdjustment` — _(unfilled)_
- `setBrightnessProgress` — _(unfilled)_
- `getBrightnessProgress` — _(unfilled)_
- `getBrightnessPercentOffset` — _(unfilled)_
- `playVideo` — _(unfilled)_
- `getPlayer` — _(unfilled)_
- `setRepeatMode` — _(unfilled)_
- `pause` — _(unfilled)_
- `play` — _(unfilled)_
- `setPlaybackSpeed` — _(unfilled)_
- `applyPlayerSettings` — _(unfilled)_
- `applySubtitleStyle` — _(unfilled)_
- `getAvailableAudioTracks` — _(unfilled)_
- `getAvailableSubtitleTracks` — _(unfilled)_
- `selectAudioTrack` — _(unfilled)_
- `selectSubtitleTrack` — _(unfilled)_
- `hasMultipleAudioTracks` — _(unfilled)_
- `hasSubtitleTracks` — _(unfilled)_
- `getAudioFormat` — _(unfilled)_
- `retryPlayback` — _(unfilled)_
- `releasePlayer` — _(unfilled)_
- `onPause` — _(unfilled)_
- `onResume` — _(unfilled)_
- `onDestroy` — _(unfilled)_

### `VideoPosterExtractor` — [com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt)

**Layer:** ui · **LOC:** 171 · **Last:** — · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `rememberDelivered` — _(unfilled)_
- `reset` — _(unfilled)_
- `extract` — _(unfilled)_
- `runFallback` — _(unfilled)_
- `logFallback` — _(unfilled)_
- `buildPlaceholder` — _(unfilled)_
- `tryGlideMemoryCache` — _(unfilled)_
- `classifyNullReason` — _(unfilled)_

### `VideoTrackSelectionManager` — [com/sza/fastmediasorter/ui/player/VideoTrackSelectionManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoTrackSelectionManager.kt)

**Layer:** ui · **LOC:** 212 · **Last:** 2026-04-20 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `applyTrackSelection` — _(unfilled)_
- `applySubtitleStyle` — _(unfilled)_
- `getAvailableAudioTracks` — _(unfilled)_
- `getAvailableSubtitleTracks` — _(unfilled)_
- `selectAudioTrack` — _(unfilled)_
- `selectSubtitleTrack` — _(unfilled)_
- `hasMultipleAudioTracks` — _(unfilled)_
- `hasSubtitleTracks` — _(unfilled)_

### `PrefetchOverlayView` — [com/sza/fastmediasorter/ui/player/views/PrefetchOverlayView.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/PrefetchOverlayView.kt)

**Layer:** ui · **LOC:** 314 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `dp` — _(unfilled)_
- `sp` — _(unfilled)_
- `update` — _(unfilled)_
- `showLocalCopyMode` — _(unfilled)_
- `markPlaybackReady` — _(unfilled)_
- `setUserDismissed` — _(unfilled)_
- `reset` — _(unfilled)_
- `performClick` — _(unfilled)_
- `onTouchEvent` — _(unfilled)_
- `onMeasure` — _(unfilled)_
- `onDraw` — _(unfilled)_
- `scheduleDismiss` — _(unfilled)_
- `updateA11yDescription` — _(unfilled)_

### `TranslationOverlayView` — [com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt)

**Layer:** ui · **LOC:** 590 · **Last:** 2026-03-04 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onFling` — _(unfilled)_
- `onSingleTapConfirmed` — _(unfilled)_
- `spToPx` — _(unfilled)_
- `loadFontSizeMultiplierAsync` — _(unfilled)_
- `increaseFontSize` — _(unfilled)_
- `decreaseFontSize` — _(unfilled)_
- `getFontSizeMultiplier` — _(unfilled)_
- `saveFontSize` — _(unfilled)_
- `createStaticLayout` — _(unfilled)_
- `setOriginalImageSize` — _(unfilled)_
- `updateImageDisplayRect` — _(unfilled)_
- `setScale` — _(unfilled)_
- `setSourceBitmap` — _(unfilled)_
- `sampleBackgroundColor` — _(unfilled)_
- `getContrastTextColor` — _(unfilled)_
- `setTranslatedBlocks` — _(unfilled)_
- `clear` — _(unfilled)_
- `performClick` — _(unfilled)_
- `onTouchEvent` — _(unfilled)_
- `onDraw` — _(unfilled)_

### `VrForcedFormatResolver` — [com/sza/fastmediasorter/ui/player/VrForcedFormatResolver.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VrForcedFormatResolver.kt)

**Layer:** ui · **LOC:** 44 · **Last:** 2026-04-20 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `resolve` — _(unfilled)_
- `mapPlatSetting` — _(unfilled)_
- `mapSphericalSetting` — _(unfilled)_

### `ResourceEditorActivity` — [com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt)

**Layer:** ui · **LOC:** 106 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getViewBinding` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onKeyDown` — _(unfilled)_
- `onCreate` — _(unfilled)_
- `createAddIntent` — _(unfilled)_
- `createEditIntent` — _(unfilled)_
- `createCopyIntent` — _(unfilled)_

### `ResourceEditorFragment` — [com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt)

**Layer:** ui · **LOC:** 981 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreate` — _(unfilled)_
- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `onResume` — _(unfilled)_
- `setupToolbar` — _(unfilled)_
- `updateToolbarTypeSubtitle` — _(unfilled)_
- `sectionStateKey` — _(unfilled)_
- `setupCollapsibleSections` — _(unfilled)_
- `reorderScanningAboveMediaTypes` — _(unfilled)_
- `setupCollapsibleHeader` — _(unfilled)_
- `setSectionExpanded` — _(unfilled)_
- `setupFieldListeners` — _(unfilled)_
- `updateMediaTypes` — _(unfilled)_
- `performSave` — _(unfilled)_
- `setupButtons` — _(unfilled)_
- `shouldCheckMediaPermissionBeforeSave` — _(unfilled)_
- `showPermissionRequiredDialog` — _(unfilled)_
- `requestMediaPermissions` — _(unfilled)_
- `showProfileSelectorDialog` — _(unfilled)_
- `pinWidgetForCurrentResource` — _(unfilled)_
- `getProfileLabelResId` — _(unfilled)_
- `observeUiState` — _(unfilled)_
- `renderNameCollision` — _(unfilled)_
- `renderCredentialChoice` — _(unfilled)_
- `renderEditActions` — _(unfilled)_
- `renderContextWarnings` — _(unfilled)_
- `showWarningDialog` — _(unfilled)_
- `renderFormData` — _(unfilled)_
- `updateMediaTypesSectionVisibility` — _(unfilled)_
- `updateConnectionSectionVisibility` — _(unfilled)_
- `renderFieldSchema` — _(unfilled)_
- `renderFieldStates` — _(unfilled)_
- `getInputLayoutForField` — _(unfilled)_
- `showRememberFileListHelpDialog` — _(unfilled)_
- `getErrorMessage` — _(unfilled)_
- `renderConnectionResult` — _(unfilled)_
- `renderLoadingStates` — _(unfilled)_
- `renderSaveButton` — _(unfilled)_
- `observeEvents` — _(unfilled)_
- `renderStatistics` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `newInstance` — _(unfilled)_

### `ResourceEditorOutcomeRenderer` — [com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorOutcomeRenderer.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorOutcomeRenderer.kt)

**Layer:** ui · **LOC:** 127 · **Last:** 2026-04-23 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `renderConnectionResult` — _(unfilled)_
- `renderLoadingStates` — _(unfilled)_
- `renderSaveButton` — _(unfilled)_
- `getErrorMessage` — _(unfilled)_
- `renderStatistics` — _(unfilled)_

### `ResourceFieldState` — [com/sza/fastmediasorter/ui/resourceeditor/ResourceFormViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceFormViewModel.kt)

**Layer:** ui · **LOC:** 544 · **Last:** 2026-04-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** ResourceEditorUseCase  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `initialize` — _(unfilled)_
- `onFieldChanged` — _(unfilled)_
- `onUseNameSuggestion` — _(unfilled)_
- `onProfileSelected` — _(unfilled)_
- `onCredentialBehaviorSelected` — _(unfilled)_
- `onTestConnection` — _(unfilled)_
- `refreshStatistics` — _(unfilled)_
- `onSave` — _(unfilled)_
- `onSaveAsCopy` — _(unfilled)_
- `onResetChanges` — _(unfilled)_
- `onRetry` — _(unfilled)_
- `applyValidation` — _(unfilled)_
- `recalculateState` — _(unfilled)_
- `buildWarnings` — _(unfilled)_
- `extractMediaTypes` — _(unfilled)_

### `FavoritesExportUiState` — [com/sza/fastmediasorter/ui/settings/BackupRestoreViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/BackupRestoreViewModel.kt)

**Layer:** ui · **LOC:** 296 · **Last:** 2026-03-24 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, BackupToGoogleDriveUseCase, RestoreFromGoogleDriveUseCase, GoogleDriveRestClient, GoogleDriveCredentialsManager, ExportFavoritesUseCase, ImportFavoritesUseCase  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `getSignInIntent` — _(unfilled)_
- `isAuthenticated` — _(unfilled)_
- `getAccountEmail` — _(unfilled)_
- `startBackup` — _(unfilled)_
- `startRestore` — _(unfilled)_
- `confirmRestore` — _(unfilled)_
- `handleSignInResult` — _(unfilled)_
- `needsSignIn` — _(unfilled)_
- `resetState` — _(unfilled)_
- `performBackup` — _(unfilled)_
- `fetchBackupInfo` — _(unfilled)_
- `performRestore` — _(unfilled)_
- `exportFavorites` — _(unfilled)_
- `resetExportFavState` — _(unfilled)_
- `previewFavoritesImport` — _(unfilled)_
- `confirmFavoritesImport` — _(unfilled)_
- `resetImportFavState` — _(unfilled)_

### `AudioSettingsFragment` — [com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt)

**Layer:** ui · **LOC:** 438 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** coroutines · user-feedback

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `observeData` — _(unfilled)_
- `setupBackgroundAudioSection` — _(unfilled)_
- `setupExitBehaviorSection` — _(unfilled)_
- `updateExitBehaviorVisibility` — _(unfilled)_
- `isNotificationPermissionGranted` — _(unfilled)_
- `updateNotificationPermissionButtonVisibility` — _(unfilled)_
- `showBatteryOptimizationHintIfNeeded` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `onResume` — _(unfilled)_
- `setupDefaultPlayerButton` — _(unfilled)_

### `BackupRestoreFragment` — [com/sza/fastmediasorter/ui/settings/fragments/BackupRestoreFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BackupRestoreFragment.kt)

**Layer:** ui · **LOC:** 362 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `setupButtons` — _(unfilled)_
- `observeState` — _(unfilled)_
- `observeFavoritesState` — _(unfilled)_
- `handleExportFavState` — _(unfilled)_
- `handleImportFavState` — _(unfilled)_
- `showExportSuccessDialog` — _(unfilled)_
- `showImportPreviewDialog` — _(unfilled)_
- `showImportResultDialog` — _(unfilled)_
- `shareFile` — _(unfilled)_
- `handleState` — _(unfilled)_
- `launchSignIn` — _(unfilled)_
- `showRestoreConfirmDialog` — _(unfilled)_
- `showRestoreSuccessMessage` — _(unfilled)_
- `updateAccountInfo` — _(unfilled)_
- `showSnackbar` — _(unfilled)_
- `onDestroyView` — _(unfilled)_

### `BaseSettingsFragment` — [com/sza/fastmediasorter/ui/settings/fragments/BaseSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BaseSettingsFragment.kt)

**Layer:** ui · **LOC:** 102 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `withSettingsUpdate` — _(unfilled)_
- `bindSwitch` — _(unfilled)_
- `setSwitchChecked` — _(unfilled)_
- `bindSpinner` — _(unfilled)_
- `onItemSelected` — _(unfilled)_
- `onNothingSelected` — _(unfilled)_
- `setSpinnerSelection` — _(unfilled)_
- `bindInputField` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `setInputText` — _(unfilled)_

### `DocumentsSettingsFragment` — [com/sza/fastmediasorter/ui/settings/fragments/DocumentsSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/DocumentsSettingsFragment.kt)

**Layer:** ui · **LOC:** 144 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `applyFlavorRestrictions` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `onResume` — _(unfilled)_
- `setupDefaultPlayerButton` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onDestroyView` — _(unfilled)_

### `GeneralSettingsFragment` — [com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt)

**Layer:** ui · **LOC:** 219 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `onConfigurationChanged` — _(unfilled)_
- `onResume` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `setupGeneralLayouts` — _(unfilled)_
- `updateLayoutParams` — _(unfilled)_

### `ImagesSettingsFragment` — [com/sza/fastmediasorter/ui/settings/fragments/ImagesSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/ImagesSettingsFragment.kt)

**Layer:** ui · **LOC:** 248 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `onResume` — _(unfilled)_
- `setupDefaultPlayerButton` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onDestroyView` — _(unfilled)_

### `MediaSettingsFragment` — [com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt)

**Layer:** ui · **LOC:** 259 · **Last:** 2026-03-20 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** user-feedback

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `onResume` — _(unfilled)_
- `setupDefaultPlayerButton` — _(unfilled)_
- `setupResetSection` — _(unfilled)_
- `setupSectionTitles` — _(unfilled)_
- `attachChildFragments` — _(unfilled)_
- `setupExpandableSections` — _(unfilled)_
- `ensureSectionExpanded` — _(unfilled)_
- `expandSection` — _(unfilled)_
- `bindSectionToggle` — _(unfilled)_
- `updateHeader` — _(unfilled)_
- `getSavedSectionStates` — _(unfilled)_
- `saveSectionState` — _(unfilled)_
- `onDestroyView` — _(unfilled)_

### `OpenSourceLicensesFragment` — [com/sza/fastmediasorter/ui/settings/fragments/OpenSourceLicensesFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OpenSourceLicensesFragment.kt)

**Layer:** ui · **LOC:** 68 · **Last:** 2026-04-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `openUrl` — _(unfilled)_
- `onDestroyView` — _(unfilled)_

### `OperationsSettingsFragment` — [com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt)

**Layer:** ui · **LOC:** 637 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `onResume` — _(unfilled)_
- `checkAndOpenAutomateDialog` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `showAddDestinationDialog` — _(unfilled)_
- `showColorPicker` — _(unfilled)_
- `observeData` — _(unfilled)_
- `updateCopyOptionsVisibility` — _(unfilled)_
- `updateMoveOptionsVisibility` — _(unfilled)_
- `setupDestinationsLayoutManager` — _(unfilled)_
- `setupExpandableSections` — _(unfilled)_
- `setupScheduledSection` — _(unfilled)_
- `showScheduledOperationDialog` — _(unfilled)_
- `confirmDeleteScheduledOp` — _(unfilled)_
- `bindSectionToggle` — _(unfilled)_
- `updateHeader` — _(unfilled)_
- `onConfigurationChanged` — _(unfilled)_
- `updateAddDestinationVisibility` — _(unfilled)_
- `moveDestination` — _(unfilled)_
- `deleteDestination` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `bind` — _(unfilled)_
- `updateScheduledNotificationPermissionButton` — _(unfilled)_
- `checkAndRequestScheduledPermissions` — _(unfilled)_
- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_

### `OtherMediaSettingsFragment` — [com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt)

**Layer:** ui · **LOC:** 450 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `applyDeviceCapabilityRestrictions` — _(unfilled)_
- `applyFlavorRestrictions` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `setupLanguageSpinners` — _(unfilled)_
- `onItemSelected` — _(unfilled)_
- `onNothingSelected` — _(unfilled)_
- `onItemSelected` — _(unfilled)_
- `onNothingSelected` — _(unfilled)_
- `getLanguageCode` — _(unfilled)_
- `getLanguagePosition` — _(unfilled)_
- `updateTranslationVisibility` — _(unfilled)_
- `setupOcrFontSpinners` — _(unfilled)_
- `onItemSelected` — _(unfilled)_
- `onNothingSelected` — _(unfilled)_
- `onItemSelected` — _(unfilled)_
- `onNothingSelected` — _(unfilled)_
- `updateOcrVisibility` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onDestroyView` — _(unfilled)_

### `PlaybackSettingsFragment` — [com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt)

**Layer:** ui · **LOC:** 585 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `onDestroyView` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `observeData` — _(unfilled)_
- `setupExpandableSections` — _(unfilled)_
- `bindSectionToggle` — _(unfilled)_
- `updateHeader` — _(unfilled)_
- `getSavedSectionStates` — _(unfilled)_
- `saveSectionState` — _(unfilled)_
- `getSortModeName` — _(unfilled)_

### `VideoSettingsFragment` — [com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt)

**Layer:** ui · **LOC:** 347 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `onViewCreated` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `beforeTextChanged` — _(unfilled)_
- `onTextChanged` — _(unfilled)_
- `afterTextChanged` — _(unfilled)_
- `onResume` — _(unfilled)_
- `setupDefaultPlayerButton` — _(unfilled)_
- `setupSnapshotResourcePicker` — _(unfilled)_
- `setupVrSettings` — _(unfilled)_
- `onItemSelected` — _(unfilled)_
- `onNothingSelected` — _(unfilled)_
- `onItemSelected` — _(unfilled)_
- `onNothingSelected` — _(unfilled)_
- `onItemSelected` — _(unfilled)_
- `onNothingSelected` — _(unfilled)_
- `observeData` — _(unfilled)_
- `onDestroyView` — _(unfilled)_

### `WearSyncSettingsFragment` — [com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt)

**Layer:** ui · **LOC:** 99 · **Last:** 2026-04-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreateView` — _(unfilled)_
- `launchBeamDialog` — _(unfilled)_
- `WearSyncScreen` — _(unfilled)_

### `BeamAnimationDialog` — [com/sza/fastmediasorter/ui/settings/helpers/BeamAnimationDialog.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/BeamAnimationDialog.kt)

**Layer:** ui · **LOC:** 175 · **Last:** 2026-04-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback

**Role:** _(unfilled)_

**Functions:**

- `onCreateDialog` — _(unfilled)_
- `onResume` — _(unfilled)_
- `BeamDialogContent` — _(unfilled)_
- `PulsingBeamAnimation` — _(unfilled)_

### `DefaultPlayerHelper` — [com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerHelper.kt)

**Layer:** ui · **LOC:** 352 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `isAlreadyDefaultPlayer` — _(unfilled)_
- `applyButtonState` — _(unfilled)_
- `showSetDefaultDialogForType` — _(unfilled)_
- `showSetDefaultDialog` — _(unfilled)_
- `openChooserOrFallbackFromActivity` — _(unfilled)_
- `openChooserOrFallback` — _(unfilled)_
- `findSampleFile` — _(unfilled)_
- `tryOpenProbeChooser` — _(unfilled)_
- `tryOpenProbeChooser` — _(unfilled)_
- `createProbeUri` — _(unfilled)_
- `grantReadPermissionToResolvers` — _(unfilled)_
- `canQueryMediaStore` — _(unfilled)_
- `queryCollection` — _(unfilled)_
- `queryFilesWithMime` — _(unfilled)_
- `openDefaultAppsSettings` — _(unfilled)_
- `openDefaultAppsSettingsFromActivity` — _(unfilled)_

### `DefaultPlayerManager` — [com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerManager.kt)

**Layer:** ui · **LOC:** 120 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `viewAliasesForFlavor` — _(unfilled)_
- `sendAliasesForFlavor` — _(unfilled)_
- `applyPrimaryPlayerState` — _(unfilled)_
- `applyShareReceiverState` — _(unfilled)_
- `setComponentState` — _(unfilled)_

### `GeneralSettingsBackupHelper` — [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsBackupHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsBackupHelper.kt)

**Layer:** ui · **LOC:** 140 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `setupWearCompanionButton` — _(unfilled)_
- `setupBackupButtons` — _(unfilled)_
- `observeBackupState` — _(unfilled)_
- `updateBackupAccountInfo` — _(unfilled)_
- `handleBackupState` — _(unfilled)_
- `launchBackupSignIn` — _(unfilled)_
- `showRestoreConfirmDialog` — _(unfilled)_
- `showRestoreSuccessMessage` — _(unfilled)_
- `showBackupSnackbar` — _(unfilled)_

### `GeneralSettingsCacheHelper` — [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt)

**Layer:** ui · **LOC:** 222 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `checkAndSuggestOptimalCacheSize` — _(unfilled)_
- `showCacheSizeRestartDialog` — _(unfilled)_
- `updateCacheSize` — _(unfilled)_
- `autoCalculateCacheSize` — _(unfilled)_
- `clearCache` — _(unfilled)_
- `showOptimalCacheSizeSuggestion` — _(unfilled)_
- `showAudioCacheSizeWarning` — _(unfilled)_
- `calculateDirectorySize` — _(unfilled)_
- `formatFileSize` — _(unfilled)_
- `deleteRecursive` — _(unfilled)_

### `GeneralSettingsCredentialHelper` — [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCredentialHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCredentialHelper.kt)

**Layer:** ui · **LOC:** 172 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `importTestCredentials` — _(unfilled)_
- `importCredentialsFromUri` — _(unfilled)_

### `GeneralSettingsImportExportHelper` — [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsImportExportHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsImportExportHelper.kt)

**Layer:** ui · **LOC:** 136 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showExportSettingsConfirmation` — _(unfilled)_
- `showImportSettingsConfirmation` — _(unfilled)_
- `importSettingsFromUri` — _(unfilled)_
- `exportSettings` — _(unfilled)_
- `importSettings` — _(unfilled)_
- `importSettingsAuto` — _(unfilled)_
- `showRestartAfterImportDialog` — _(unfilled)_

### `GeneralSettingsLogHelper` — [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt)

**Layer:** ui · **LOC:** 142 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `setupVersionInfo` — _(unfilled)_
- `setupButtons` — _(unfilled)_
- `shareLogs` — _(unfilled)_
- `launchSaveLogs` — _(unfilled)_
- `showLogDialog` — _(unfilled)_
- `showSaveLogsNotSupportedDialog` — _(unfilled)_
- `openEmailClient` — _(unfilled)_
- `getFullLog` — _(unfilled)_
- `getSessionLog` — _(unfilled)_

### `GeneralSettingsObserversHelper` — [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt)

**Layer:** ui · **LOC:** 156 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `observeData` — _(unfilled)_
- `observeManualNetworkSyncState` — _(unfilled)_
- `refreshLastSyncStatus` — _(unfilled)_
- `dismissManualSyncProgressDialog` — _(unfilled)_
- `showOrUpdateManualSyncProgressDialog` — _(unfilled)_

### `GeneralSettingsPermissionsHelper` — [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPermissionsHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPermissionsHelper.kt)

**Layer:** ui · **LOC:** 133 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `updatePermissionButtonsState` — _(unfilled)_
- `handleLocalFilesPermissionAction` — _(unfilled)_
- `requestMediaPermissions` — _(unfilled)_
- `openAppSettings` — _(unfilled)_
- `requestManageMediaPermission` — _(unfilled)_

### `GeneralSettingsPrefetchHelper` — [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPrefetchHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPrefetchHelper.kt)

**Layer:** ui · **LOC:** 167 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `setup` — _(unfilled)_
- `updateFromSettings` — _(unfilled)_
- `setupPrefetchCacheDropdown` — _(unfilled)_
- `prefetchCacheLabel` — _(unfilled)_
- `setupCleanupModeDropdown` — _(unfilled)_
- `cleanupModeLabel` — _(unfilled)_
- `setupTtlDropdown` — _(unfilled)_
- `ttlLabel` — _(unfilled)_
- `setupClearButton` — _(unfilled)_
- `showClearDialog` — _(unfilled)_
- `saveSettings` — _(unfilled)_

### `GeneralSettingsResetHelper` — [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsResetHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsResetHelper.kt)

**Layer:** ui · **LOC:** 103 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showRememberFileListHelpDialog` — _(unfilled)_
- `showResetSettingsConfirmation` — _(unfilled)_
- `showResetGeneralSectionConfirmation` — _(unfilled)_
- `resetSmbConnections` — _(unfilled)_
- `resetGeneralSection` — _(unfilled)_
- `resetSettingsToDefaults` — _(unfilled)_

### `GeneralSettingsSectionsHelper` — [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt)

**Layer:** ui · **LOC:** 101 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `setup` — _(unfilled)_
- `bindSectionToggle` — _(unfilled)_
- `updateHeader` — _(unfilled)_
- `getSavedSectionStates` — _(unfilled)_
- `saveSectionState` — _(unfilled)_

### `GeneralSettingsViewSetupHelper` — [com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt)

**Layer:** ui · **LOC:** 376 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `setup` — _(unfilled)_
- `setupLanguageSpinner` — _(unfilled)_
- `onItemSelected` — _(unfilled)_
- `onNothingSelected` — _(unfilled)_
- `setupSwitches` — _(unfilled)_
- `setupTooltips` — _(unfilled)_
- `setupNetworkParallelism` — _(unfilled)_
- `setupCacheSizeInput` — _(unfilled)_
- `setupSyncSection` — _(unfilled)_
- `setupDefaultCredentials` — _(unfilled)_
- `setupLinkButtons` — _(unfilled)_
- `setupActionButtons` — _(unfilled)_
- `showRestartDialog` — _(unfilled)_
- `openUrl` — _(unfilled)_

### `MediaCategoryPagerAdapter` — [com/sza/fastmediasorter/ui/settings/MediaCategoryPagerAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/MediaCategoryPagerAdapter.kt)

**Layer:** ui · **LOC:** 52 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getItemCount` — _(unfilled)_
- `createFragment` — _(unfilled)_
- `getTabTitle` — _(unfilled)_

### `ScheduledOperationsAdapter` — [com/sza/fastmediasorter/ui/settings/ScheduledOperationsAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/ScheduledOperationsAdapter.kt)

**Layer:** ui · **LOC:** 118 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `bind` — _(unfilled)_
- `buildFileTypeMaskLabel` — _(unfilled)_
- `buildIntervalString` — _(unfilled)_
- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_

### `ScheduledOperationsViewModel` — [com/sza/fastmediasorter/ui/settings/ScheduledOperationsViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/ScheduledOperationsViewModel.kt)

**Layer:** ui · **LOC:** 81 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** GetScheduledOperationsUseCase, UpsertScheduledOperationUseCase, UpdateScheduledOperationUseCase, DeleteScheduledOperationUseCase, ClearScheduledOperationsUseCase, GetScheduledOperationsLogUseCase, ClearScheduledOperationsLogUseCase, WorkManagerScheduler  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `upsert` — _(unfilled)_
- `toggleEnabled` — _(unfilled)_
- `delete` — _(unfilled)_
- `runNow` — _(unfilled)_
- `getLog` — _(unfilled)_
- `clearLog` — _(unfilled)_

### `SettingsActivity` — [com/sza/fastmediasorter/ui/settings/SettingsActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt)

**Layer:** ui · **LOC:** 405 · **Last:** 2026-04-26 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `switchTab` — _(unfilled)_
- `tabCount` — _(unfilled)_
- `currentTab` — _(unfilled)_
- `openSearchOverlay` — _(unfilled)_
- `closeSearchOverlay` — _(unfilled)_
- `isSearchVisible` — _(unfilled)_
- `navigateBack` — _(unfilled)_
- `showHelp` — _(unfilled)_
- `activateFocused` — _(unfilled)_
- `moveFocus` — _(unfilled)_
- `openKeybindingRemap` — _(unfilled)_
- `getViewBinding` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `elapsed` — _(unfilled)_
- `onPageSelected` — _(unfilled)_
- `observeData` — _(unfilled)_
- `applyEdgeToEdgeInsets` — _(unfilled)_
- `applyWindowInsets` — _(unfilled)_
- `applyCompactToolbar` — _(unfilled)_
- `updateLandscapeToolbarHeight` — _(unfilled)_
- `onLayoutConfigurationChanged` — _(unfilled)_
- `onKeyDown` — _(unfilled)_
- `setupGlobalSearch` — _(unfilled)_
- `handleOnBackPressed` — _(unfilled)_
- `openSearchOverlay` — _(unfilled)_
- `closeSearchOverlay` — _(unfilled)_
- `updateSearchResults` — _(unfilled)_
- `onSearchResultSelected` — _(unfilled)_
- `navigateToTarget` — _(unfilled)_
- `highlightView` — _(unfilled)_
- `getSettingsFragment` — _(unfilled)_
- `getLastTabPosition` — _(unfilled)_
- `saveLastTabPosition` — _(unfilled)_

### `SettingsKeyboardNavigationManager` — [com/sza/fastmediasorter/ui/settings/SettingsKeyboardNavigationManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsKeyboardNavigationManager.kt)

**Layer:** ui · **LOC:** 80 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `switchTab` — _(unfilled)_
- `tabCount` — _(unfilled)_
- `currentTab` — _(unfilled)_
- `openSearchOverlay` — _(unfilled)_
- `closeSearchOverlay` — _(unfilled)_
- `isSearchVisible` — _(unfilled)_
- `navigateBack` — _(unfilled)_
- `showHelp` — _(unfilled)_
- `activateFocused` — _(unfilled)_
- `moveFocus` — _(unfilled)_
- `handleKeyDown` — _(unfilled)_
- `dispatchAction` — _(unfilled)_

### `SettingsPagerAdapter` — [com/sza/fastmediasorter/ui/settings/SettingsPagerAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsPagerAdapter.kt)

**Layer:** ui · **LOC:** 25 · **Last:** 2026-03-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getItemCount` — _(unfilled)_
- `createFragment` — _(unfilled)_

### `SettingsSearchAdapter` — [com/sza/fastmediasorter/ui/settings/SettingsSearchAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchAdapter.kt)

**Layer:** ui · **LOC:** 69 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `bind` — _(unfilled)_
- `formatSection` — _(unfilled)_
- `areItemsTheSame` — _(unfilled)_
- `areContentsTheSame` — _(unfilled)_

### `SettingsSearchDestination` — [com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt)

**Layer:** ui · **LOC:** 411 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `isEntryAvailable` — _(unfilled)_
- `search` — _(unfilled)_

### `ManualNetworkSyncUiState` — [com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt)

**Layer:** ui · **LOC:** 658 · **Last:** 2026-04-18 · **Status:** unknown · **NoFlavors:** —

**Injected:** SettingsRepository, ResourceRepository, NetworkCredentialsRepository, GetDestinationsUseCase, GetResourcesUseCase, UpdateResourceUseCase, ExportSettingsUseCase, ImportSettingsUseCase, ResetSmbConnectionsUseCase, SyncNetworkResourcesUseCase, WorkManagerScheduler  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `updateSettings` — _(unfilled)_
- `applyScheduledOperationsToggle` — _(unfilled)_
- `applyBackgroundSyncSchedule` — _(unfilled)_
- `resetToDefaults` — _(unfilled)_
- `resetGeneralSection` — _(unfilled)_
- `resetMediaSection` — _(unfilled)_
- `resetPlaybackSection` — _(unfilled)_
- `resetDestinationsSection` — _(unfilled)_
- `resetPlayerFirstRun` — _(unfilled)_
- `moveDestination` — _(unfilled)_
- `removeDestination` — _(unfilled)_
- `getWritableNonDestinationResources` — _(unfilled)_
- `getLastNetworkSyncTimestamp` — _(unfilled)_
- `addDestination` — _(unfilled)_
- `startManualNetworkSync` — _(unfilled)_
- `cancelManualNetworkSync` — _(unfilled)_
- `clearManualNetworkSyncTerminalState` — _(unfilled)_
- `updateDestinationColor` — _(unfilled)_
- `addCredentials` — _(unfilled)_
- `getAllCredentials` — _(unfilled)_
- `addResourceDirectly` — _(unfilled)_
- `importSzaResources` — _(unfilled)_

### `WearSyncUiState` — [com/sza/fastmediasorter/ui/settings/WearSyncViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/WearSyncViewModel.kt)

**Layer:** ui · **LOC:** 83 · **Last:** 2026-04-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, SendResourcesToWatchUseCase  
**Side effects:** prefs  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `startPush` — _(unfilled)_
- `reset` — _(unfilled)_
- `parseSentCount` — _(unfilled)_

### `ReceiveShareActivity` — [com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt)

**Layer:** ui · **LOC:** 360 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `attachBaseContext` — _(unfilled)_
- `onCreate` — _(unfilled)_
- `processIntent` — _(unfilled)_
- `extractAndCacheFiles` — _(unfilled)_
- `processLinkAutoDownload` — _(unfilled)_
- `onProgress` — _(unfilled)_
- `handleLinkAutoDownloadResult` — _(unfilled)_
- `extractStreams` — _(unfilled)_
- `cacheStreams` — _(unfilled)_
- `resolveFileName` — _(unfilled)_
- `createTextFile` — _(unfilled)_
- `showDestinationDialog` — _(unfilled)_
- `copyToSafFolder` — _(unfilled)_
- `showLoadingDialog` — _(unfilled)_
- `cleanupAndFinish` — _(unfilled)_
- `onKeyDown` — _(unfilled)_
- `onDestroy` — _(unfilled)_

### `UrlInTextDetector` — [com/sza/fastmediasorter/ui/share/UrlInTextDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/share/UrlInTextDetector.kt)

**Layer:** ui · **LOC:** 35 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `firstHttpUrl` — _(unfilled)_
- `stripTrailingPunctuation` — _(unfilled)_

### `WelcomeActivity` — [com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt)

**Layer:** ui · **LOC:** 609 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** prefs  
**Flags:** coroutines · user-feedback

**Role:** _(unfilled)_

**Functions:**

- `getViewBinding` — _(unfilled)_
- `setupViews` — _(unfilled)_
- `observeData` — _(unfilled)_
- `applyEdgeToEdgeInsets` — _(unfilled)_
- `applyWindowInsets` — _(unfilled)_
- `setupViewPager` — _(unfilled)_
- `onPageSelected` — _(unfilled)_
- `setupIndicators` — _(unfilled)_
- `updateIndicators` — _(unfilled)_
- `animateIndicatorWidth` — _(unfilled)_
- `setupButtons` — _(unfilled)_
- `updateUI` — _(unfilled)_
- `applyPageBackground` — _(unfilled)_
- `finishWelcome` — _(unfilled)_
- `requestPermissions` — _(unfilled)_
- `onRuntimePermissionsProcessed` — _(unfilled)_
- `continueSpecialPermissionsFlowOrComplete` — _(unfilled)_
- `requestManageMediaPermissionIfNeeded` — _(unfilled)_
- `requestAllFilesAccessPermissionIfNeeded` — _(unfilled)_
- `requestBatteryOptimizationIfNeeded` — _(unfilled)_
- `requestNotificationsPermissionIfNeeded` — _(unfilled)_
- `showPermissionDeniedDialog` — _(unfilled)_
- `completeWelcomeFlow` — _(unfilled)_
- `goToMainActivity` — _(unfilled)_
- `getRequiredMediaPermissions` — _(unfilled)_
- `hasRequiredMediaPermissions` — _(unfilled)_

### `WelcomePagerAdapter` — [com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt)

**Layer:** ui · **LOC:** 241 · **Last:** 2026-03-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getItemViewType` — _(unfilled)_
- `onCreateViewHolder` — _(unfilled)_
- `onBindViewHolder` — _(unfilled)_
- `getItemCount` — _(unfilled)_
- `bind` — _(unfilled)_
- `bind` — _(unfilled)_
- `bind` — _(unfilled)_
- `bind` — _(unfilled)_
- `bind` — _(unfilled)_
- `animateEntrance` — _(unfilled)_

### `WelcomeViewModel` — [com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt)

**Layer:** ui · **LOC:** 109 · **Last:** 2026-03-20 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, SettingsRepository  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `getInitialState` — _(unfilled)_
- `setWelcomeCompleted` — _(unfilled)_
- `isWelcomeCompleted` — _(unfilled)_
- `isFirstRunAfterWelcome` — _(unfilled)_
- `setFirstRunCompleted` — _(unfilled)_
- `isDefaultPlayerOnboardingShown` — _(unfilled)_
- `markDefaultPlayerOnboardingShown` — _(unfilled)_
- `enablePrimaryMediaPlayer` — _(unfilled)_
- `setMediaPermissionsGranted` — _(unfilled)_

### `BinaryFileThumbnailGenerator` — [com/sza/fastmediasorter/util/BinaryFileThumbnailGenerator.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/BinaryFileThumbnailGenerator.kt)

**Layer:** utils · **LOC:** 127 · **Last:** 2026-02-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `generateThumbnail` — _(unfilled)_
- `drawTypeIndicator` — _(unfilled)_
- `clearCache` — _(unfilled)_
- `getCacheSize` — _(unfilled)_

### `BinaryFileTypeDetector` — [com/sza/fastmediasorter/util/BinaryFileTypeDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/BinaryFileTypeDetector.kt)

**Layer:** utils · **LOC:** 78 · **Last:** 2026-03-30 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `detectType` — _(unfilled)_
- `isBinaryExtension` — _(unfilled)_
- `getColorForType` — _(unfilled)_

### `ConnectionErrorFormatter` — [com/sza/fastmediasorter/util/ConnectionErrorFormatter.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/ConnectionErrorFormatter.kt)

**Layer:** utils · **LOC:** 245 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `formatConnectionError` — _(unfilled)_
- `buildTechnicalDetails` — _(unfilled)_
- `formatResourceType` — _(unfilled)_
- `extractServer` — _(unfilled)_
- `extractPort` — _(unfilled)_
- `extractTimeout` — _(unfilled)_
- `cleanErrorMessage` — _(unfilled)_

### `ExtensionThumbnailGenerator` — [com/sza/fastmediasorter/util/ExtensionThumbnailGenerator.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/ExtensionThumbnailGenerator.kt)

**Layer:** utils · **LOC:** 50 · **Last:** 2026-02-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `generate` — _(unfilled)_
- `clearCache` — _(unfilled)_

### `FragmentExt` — [com/sza/fastmediasorter/util/FragmentExt.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/FragmentExt.kt)

**Layer:** utils · **LOC:** 29 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `safeShowDialog` — _(unfilled)_
- `canShowDialog` — _(unfilled)_

### `AnimatedGifEncoder` — [com/sza/fastmediasorter/util/gif/AnimatedGifEncoder.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/gif/AnimatedGifEncoder.kt)

**Layer:** utils · **LOC:** 403 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `start` — _(unfilled)_
- `setDelay` — _(unfilled)_
- `setRepeat` — _(unfilled)_
- `addFrame` — _(unfilled)_
- `finish` — _(unfilled)_
- `analyzePixels` — _(unfilled)_
- `findClosestColor` — _(unfilled)_
- `writeHeader` — _(unfilled)_
- `writeLSD` — _(unfilled)_
- `writePalette` — _(unfilled)_
- `writeNetscapeExt` — _(unfilled)_
- `writeGraphicCtrlExt` — _(unfilled)_
- `writeImageDesc` — _(unfilled)_
- `writePixels` — _(unfilled)_
- `writeShort` — _(unfilled)_
- `encode` — _(unfilled)_
- `compress` — _(unfilled)_
- `output` — _(unfilled)_
- `maxCode` — _(unfilled)_
- `charOut` — _(unfilled)_
- `flushChar` — _(unfilled)_
- `clBlock` — _(unfilled)_
- `clHash` — _(unfilled)_
- `nextPixel` — _(unfilled)_

### `KeyboardShortcutHandler` — [com/sza/fastmediasorter/util/KeyboardShortcutHandler.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/KeyboardShortcutHandler.kt)

**Layer:** utils · **LOC:** 591 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber · tests

**Role:** _(unfilled)_

**Functions:**

- `onSelectAll` — _(unfilled)_
- `onCopy` — _(unfilled)_
- `onCut` — _(unfilled)_
- `onDelete` — _(unfilled)_
- `onRename` — _(unfilled)_
- `onRefresh` — _(unfilled)_
- `onBack` — _(unfilled)_
- `onEscape` — _(unfilled)_
- `onSpace` — _(unfilled)_
- `onEnter` — _(unfilled)_
- `dispatch` — _(unfilled)_
- `handleKeyEvent` — _(unfilled)_
- `isPlayerSurface` — _(unfilled)_
- `commandIdToAction` — _(unfilled)_
- `mapToAction` — _(unfilled)_
- `mapMain` — _(unfilled)_
- `mapBrowse` — _(unfilled)_
- `mapGlobal` — _(unfilled)_
- `mapFileList` — _(unfilled)_
- `mapPlayer` — _(unfilled)_
- `mapSettings` — _(unfilled)_
- `mapDialog` — _(unfilled)_
- `mapCloudPicker` — _(unfilled)_
- `mapAddResource` — _(unfilled)_
- `mapNavigation` — _(unfilled)_
- `dispatch` — _(unfilled)_

### `ThumbnailColorMapper` — [com/sza/fastmediasorter/util/ThumbnailColorMapper.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/ThumbnailColorMapper.kt)

**Layer:** utils · **LOC:** 98 · **Last:** 2026-03-14 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getColorForExtension` — _(unfilled)_
- `getColorForFile` — _(unfilled)_
- `getColorForMediaType` — _(unfilled)_
- `getContrastingTextColor` — _(unfilled)_
- `darken` — _(unfilled)_
- `lighten` — _(unfilled)_

### `ToastThrottler` — [com/sza/fastmediasorter/util/ToastThrottler.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/ToastThrottler.kt)

**Layer:** utils · **LOC:** 75 · **Last:** 2026-03-12 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `showNetworkError` — _(unfilled)_
- `showThrottled` — _(unfilled)_
- `reset` — _(unfilled)_

### `VirtualPathUtils` — [com/sza/fastmediasorter/util/VirtualPathUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/util/VirtualPathUtils.kt)

**Layer:** utils · **LOC:** 32 · **Last:** 2026-03-30 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** tests

**Role:** _(unfilled)_

**Functions:**

- `isVirtualPath` — _(unfilled)_
- `isAggregateVirtualPath` — _(unfilled)_

### `CharsetDetector` — [com/sza/fastmediasorter/utils/CharsetDetector.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/CharsetDetector.kt)

**Layer:** utils · **LOC:** 162 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `detect` — _(unfilled)_
- `detectFromBom` — _(unfilled)_
- `detectByHeuristic` — _(unfilled)_
- `isValidUtf8` — _(unfilled)_
- `looksLikeWindows1251` — _(unfilled)_

### `ClickUtils` — [com/sza/fastmediasorter/utils/ClickUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/ClickUtils.kt)

**Layer:** utils · **LOC:** 45 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `setOnClickListenerDebounced` — _(unfilled)_
- `onClick` — _(unfilled)_
- `setOnLongClickListenerDebounced` — _(unfilled)_
- `onLongClick` — _(unfilled)_

### `FileExtensions` — [com/sza/fastmediasorter/utils/FileExtensions.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/FileExtensions.kt)

**Layer:** utils · **LOC:** 30 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** —

**Role:** _(unfilled)_

### `FtpPathUtils` — [com/sza/fastmediasorter/utils/FtpPathUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/FtpPathUtils.kt)

**Layer:** utils · **LOC:** 127 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `parseFtpPath` — _(unfilled)_
- `buildFtpPath` — _(unfilled)_
- `normalizeFtpPath` — _(unfilled)_

### `GlideCacheStats` — [com/sza/fastmediasorter/utils/GlideCacheStats.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/GlideCacheStats.kt)

**Layer:** utils · **LOC:** 119 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `recordLoad` — _(unfilled)_
- `recordThumbnailRepoHit` — _(unfilled)_
- `reset` — _(unfilled)_
- `logStats` — _(unfilled)_
- `getSummary` — _(unfilled)_

### `LifecycleExtensions` — [com/sza/fastmediasorter/utils/LifecycleExtensions.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/LifecycleExtensions.kt)

**Layer:** utils · **LOC:** 44 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `collectOnLifecycle` — _(unfilled)_
- `collectOnLifecycle` — _(unfilled)_

### `MediaStoreNotifier` — [com/sza/fastmediasorter/utils/MediaStoreNotifier.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/MediaStoreNotifier.kt)

**Layer:** utils · **LOC:** 74 · **Last:** 2026-04-02 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `notifyFile` — _(unfilled)_
- `notifyFileAwait` — _(unfilled)_
- `isSharedStoragePath` — _(unfilled)_

### `NetworkUtils` — [com/sza/fastmediasorter/utils/NetworkUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/NetworkUtils.kt)

**Layer:** utils · **LOC:** 53 · **Last:** 2026-02-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `getLocalIpAddress` — _(unfilled)_

### `PdfExportHelper` — [com/sza/fastmediasorter/utils/PdfExportHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/PdfExportHelper.kt)

**Layer:** utils · **LOC:** 142 · **Last:** 2026-04-11 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `exportPdfPagesToJpg` — _(unfilled)_
- `saveBitmapToDownloads` — _(unfilled)_
- `saveBitmapToDownloadsApi29` — _(unfilled)_
- `saveBitmapToDownloadsLegacy` — _(unfilled)_

### `PdfHelper` — [com/sza/fastmediasorter/utils/PdfHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/PdfHelper.kt)

**Layer:** utils · **LOC:** 63 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `loadPdfThumbnail` — _(unfilled)_

### `PdfThumbnailHelper` — [com/sza/fastmediasorter/utils/PdfThumbnailHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/PdfThumbnailHelper.kt)

**Layer:** utils · **LOC:** 69 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `generateThumbnail` — _(unfilled)_

### `PermissionChecker` — [com/sza/fastmediasorter/utils/PermissionChecker.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/PermissionChecker.kt)

**Layer:** utils · **LOC:** 40 · **Last:** 2026-02-16 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `getRequiredMediaPermissions` — _(unfilled)_
- `hasMediaPermissions` — _(unfilled)_

### `SafHelper` — [com/sza/fastmediasorter/utils/SafHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/SafHelper.kt)

**Layer:** utils · **LOC:** 224 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `deleteContentUri` — _(unfilled)_
- `isContentUri` — _(unfilled)_
- `normalizeContentUri` — _(unfilled)_
- `parseUri` — _(unfilled)_
- `getDisplayName` — _(unfilled)_
- `getFileSize` — _(unfilled)_
- `getDocumentFileFromUri` — _(unfilled)_

### `SftpPathUtils` — [com/sza/fastmediasorter/utils/SftpPathUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/SftpPathUtils.kt)

**Layer:** utils · **LOC:** 120 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `parseSftpPath` — _(unfilled)_
- `buildSftpPath` — _(unfilled)_
- `normalizeSftpPath` — _(unfilled)_

### `SmbPathUtils` — [com/sza/fastmediasorter/utils/SmbPathUtils.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/SmbPathUtils.kt)

**Layer:** utils · **LOC:** 194 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `parseSmbPath` — _(unfilled)_
- `buildSmbPath` — _(unfilled)_
- `extractServer` — _(unfilled)_
- `extractShare` — _(unfilled)_
- `extractRemotePath` — _(unfilled)_
- `isSameShare` — _(unfilled)_
- `getParentPath` — _(unfilled)_
- `getFileName` — _(unfilled)_

### `SyntaxHighlighter` — [com/sza/fastmediasorter/utils/SyntaxHighlighter.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/SyntaxHighlighter.kt)

**Layer:** utils · **LOC:** 231 · **Last:** 2026-02-15 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `highlight` — _(unfilled)_
- `isSupported` — _(unfilled)_
- `highlightKotlin` — _(unfilled)_
- `highlightPython` — _(unfilled)_
- `highlightJavaScript` — _(unfilled)_
- `highlightJson` — _(unfilled)_
- `highlightXml` — _(unfilled)_
- `highlightCss` — _(unfilled)_
- `highlightPattern` — _(unfilled)_
- `highlightKeywords` — _(unfilled)_

### `UserActionLogger` — [com/sza/fastmediasorter/utils/UserActionLogger.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/UserActionLogger.kt)

**Layer:** utils · **LOC:** 224 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `logButtonClick` — _(unfilled)_
- `logItemClick` — _(unfilled)_
- `logItemLongClick` — _(unfilled)_
- `logScroll` — _(unfilled)_
- `logRecyclerViewScroll` — _(unfilled)_
- `logTouch` — _(unfilled)_
- `logGesture` — _(unfilled)_
- `logKey` — _(unfilled)_
- `logNavigation` — _(unfilled)_
- `logDialog` — _(unfilled)_
- `logTextInput` — _(unfilled)_
- `logSelection` — _(unfilled)_
- `wrapClickListener` — _(unfilled)_
- `createScrollListener` — _(unfilled)_
- `onScrolled` — _(unfilled)_
- `onScrollStateChanged` — _(unfilled)_
- `motionEventActionToString` — _(unfilled)_

### `ViewExtensions` — [com/sza/fastmediasorter/utils/ViewExtensions.kt](app_v2/src/main/java/com/sza/fastmediasorter/utils/ViewExtensions.kt)

**Layer:** utils · **LOC:** 38 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `setBadgeText` — _(unfilled)_
- `clearBadge` — _(unfilled)_

### `DefaultVrLayerFactory` — [com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt)

**Layer:** vr · **LOC:** 120 · **Last:** 2026-04-27 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `describe` — _(unfilled)_
- `projectionDescriptor` — _(unfilled)_
- `quadCinemaDescriptor` — _(unfilled)_
- `equirectDescriptor` — _(unfilled)_
- `cylinderDescriptor` — _(unfilled)_
- `leftEyeUv` — _(unfilled)_
- `rightEyeUv` — _(unfilled)_

### `VrUvRect` — [com/sza/fastmediasorter/vr/render/VrLayerDescriptor.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/VrLayerDescriptor.kt)

**Layer:** vr · **LOC:** 49 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `VrLayerFactory` — [com/sza/fastmediasorter/vr/render/VrLayerFactory.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/VrLayerFactory.kt)

**Layer:** vr · **LOC:** 26 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `describe` — _(unfilled)_
- `fromPreferenceValue` — _(unfilled)_

### `VrLayerType` — [com/sza/fastmediasorter/vr/render/VrLayerType.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/VrLayerType.kt)

**Layer:** vr · **LOC:** 11 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

### `VrEye` — [com/sza/fastmediasorter/vr/render/VrRenderContext.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/VrRenderContext.kt)

**Layer:** vr · **LOC:** 38 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `fromNativeIndex` — _(unfilled)_

### `VrRenderPlanner` — [com/sza/fastmediasorter/vr/render/VrRenderPlanner.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/VrRenderPlanner.kt)

**Layer:** vr · **LOC:** 114 · **Last:** 2026-04-19 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `buildRenderPlan` — _(unfilled)_
- `calculateUvParams` — _(unfilled)_
- `calculateCinemaViewport` — _(unfilled)_

### `CameraPhotosWidgetProvider` — [com/sza/fastmediasorter/widget/CameraPhotosWidgetProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraPhotosWidgetProvider.kt)

**Layer:** widget · **LOC:** 57 · **Last:** 2026-03-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onUpdate` — _(unfilled)_
- `updateAppWidget` — _(unfilled)_

### `ContinueReadingWidgetProvider` — [com/sza/fastmediasorter/widget/ContinueReadingWidgetProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/ContinueReadingWidgetProvider.kt)

**Layer:** widget · **LOC:** 55 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onUpdate` — _(unfilled)_
- `updateAppWidget` — _(unfilled)_

### `FavoritesWidgetProvider` — [com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt)

**Layer:** widget · **LOC:** 85 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onUpdate` — _(unfilled)_
- `onEnabled` — _(unfilled)_
- `onDisabled` — _(unfilled)_
- `updateAppWidget` — _(unfilled)_

### `FavoritesWidgetService` — [com/sza/fastmediasorter/widget/FavoritesWidgetService.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetService.kt)

**Layer:** widget · **LOC:** 124 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** timber

**Role:** _(unfilled)_

**Functions:**

- `onGetViewFactory` — _(unfilled)_
- `database` — _(unfilled)_
- `onCreate` — _(unfilled)_
- `onDataSetChanged` — _(unfilled)_
- `loadFavorites` — _(unfilled)_
- `onDestroy` — _(unfilled)_
- `getCount` — _(unfilled)_
- `getViewAt` — _(unfilled)_
- `getLoadingView` — _(unfilled)_
- `getViewTypeCount` — _(unfilled)_
- `getItemId` — _(unfilled)_
- `hasStableIds` — _(unfilled)_

### `RandomMusicWidgetProvider` — [com/sza/fastmediasorter/widget/RandomMusicWidgetProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomMusicWidgetProvider.kt)

**Layer:** widget · **LOC:** 58 · **Last:** 2026-03-21 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onUpdate` — _(unfilled)_
- `updateAppWidget` — _(unfilled)_

### `ResourceLaunchWidgetConfigActivity` — [com/sza/fastmediasorter/widget/ResourceLaunchWidgetConfigActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/ResourceLaunchWidgetConfigActivity.kt)

**Layer:** widget · **LOC:** 218 · **Last:** 2026-04-25 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines

**Role:** _(unfilled)_

**Functions:**

- `database` — _(unfilled)_
- `onKeyDown` — _(unfilled)_
- `onCreate` — _(unfilled)_
- `saveWidgetConfig` — _(unfilled)_
- `updateWidgetAndFinish` — _(unfilled)_
- `ResourceSelectionScreen` — _(unfilled)_
- `ResourceItem` — _(unfilled)_

### `ResourceLaunchWidgetProvider` — [com/sza/fastmediasorter/widget/ResourceLaunchWidgetProvider.kt](app_v2/src/main/java/com/sza/fastmediasorter/widget/ResourceLaunchWidgetProvider.kt)

**Layer:** widget · **LOC:** 162 · **Last:** 2026-03-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** —

**Role:** _(unfilled)_

**Functions:**

- `onUpdate` — _(unfilled)_
- `onReceive` — _(unfilled)_
- `onDeleted` — _(unfilled)_
- `onEnabled` — _(unfilled)_
- `onDisabled` — _(unfilled)_
- `updateAppWidget` — _(unfilled)_
- `resolveIcon` — _(unfilled)_

### `DuplicateDetectionWorker` — [com/sza/fastmediasorter/worker/DuplicateDetectionWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/DuplicateDetectionWorker.kt)

**Layer:** worker · **LOC:** 141 · **Last:** 2026-04-01 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `doWork` — _(unfilled)_
- `createForegroundInfo` — _(unfilled)_
- `buildNotification` — _(unfilled)_
- `createNotificationChannel` — _(unfilled)_

### `NetworkFilesSyncWorker` — [com/sza/fastmediasorter/worker/NetworkFilesSyncWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/NetworkFilesSyncWorker.kt)

**Layer:** worker · **LOC:** 180 · **Last:** 2026-03-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `doWork` — _(unfilled)_
- `createForegroundInfo` — _(unfilled)_
- `buildNotification` — _(unfilled)_
- `createNotificationChannel` — _(unfilled)_

### `OrphanCleanupWorker` — [com/sza/fastmediasorter/worker/OrphanCleanupWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/OrphanCleanupWorker.kt)

**Layer:** worker · **LOC:** 131 · **Last:** 2026-03-24 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `doWork` — _(unfilled)_
- `cleanOrphanedCaches` — _(unfilled)_
- `cleanMetadataCache` — _(unfilled)_
- `cleanAudioMetadataCache` — _(unfilled)_
- `auditOrphanedCredentials` — _(unfilled)_

### `PendingRevocationWorker` — [com/sza/fastmediasorter/worker/PendingRevocationWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/PendingRevocationWorker.kt)

**Layer:** worker · **LOC:** 116 · **Last:** 2026-02-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** network  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `doWork` — _(unfilled)_
- `revokeToken` — _(unfilled)_

### `ScheduledOperationsBootReceiver` — [com/sza/fastmediasorter/worker/ScheduledOperationsBootReceiver.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/ScheduledOperationsBootReceiver.kt)

**Layer:** worker · **LOC:** 37 · **Last:** 2026-04-13 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `onReceive` — _(unfilled)_

### `ScheduledOperationsWorker` — [com/sza/fastmediasorter/worker/ScheduledOperationsWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/ScheduledOperationsWorker.kt)

**Layer:** worker · **LOC:** 112 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · user-feedback · timber

**Role:** _(unfilled)_

**Functions:**

- `getForegroundInfo` — _(unfilled)_
- `doWork` — _(unfilled)_
- `calculateNextRunAt` — _(unfilled)_
- `createForegroundInfo` — _(unfilled)_
- `buildNotification` — _(unfilled)_
- `createNotificationChannel` — _(unfilled)_

### `StreamingCacheStartupGcWorker` — [com/sza/fastmediasorter/worker/StreamingCacheStartupGcWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/StreamingCacheStartupGcWorker.kt)

**Layer:** worker · **LOC:** 79 · **Last:** 2026-04-22 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `doWork` — _(unfilled)_

### `ThumbnailExtractorHelper` — [com/sza/fastmediasorter/worker/ThumbnailExtractorHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/ThumbnailExtractorHelper.kt)

**Layer:** worker · **LOC:** 139 · **Last:** 2026-03-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** Context, SmbClient, SftpClient, FtpClient, NetworkCredentialsRepository, UnifiedFileCache  
**Side effects:** network, disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `extract` — _(unfilled)_
- `extractVideoThumbnail` — _(unfilled)_
- `extractPdfThumbnail` — _(unfilled)_

### `ThumbnailPreloadWorker` — [com/sza/fastmediasorter/worker/ThumbnailPreloadWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/ThumbnailPreloadWorker.kt)

**Layer:** worker · **LOC:** 144 · **Last:** 2026-03-28 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `workName` — _(unfilled)_
- `doWork` — _(unfilled)_

### `TrashCleanupWorker` — [com/sza/fastmediasorter/worker/TrashCleanupWorker.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/TrashCleanupWorker.kt)

**Layer:** worker · **LOC:** 64 · **Last:** 2026-02-09 · **Status:** unknown · **NoFlavors:** —

**Injected:** —  
**Side effects:** disk  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `doWork` — _(unfilled)_

### `WorkManagerScheduler` — [com/sza/fastmediasorter/worker/WorkManagerScheduler.kt](app_v2/src/main/java/com/sza/fastmediasorter/worker/WorkManagerScheduler.kt)

**Layer:** worker · **LOC:** 432 · **Last:** 2026-04-29 · **Status:** unknown · **NoFlavors:** —

**Injected:** ApplicationContext, Context, ScheduledOperationRepository  
**Side effects:** —  
**Flags:** coroutines · timber

**Role:** _(unfilled)_

**Functions:**

- `scheduleTrashCleanup` — _(unfilled)_
- `cancelTrashCleanup` — _(unfilled)_
- `scheduleResourcesSync` — _(unfilled)_
- `cancelResourcesSync` — _(unfilled)_
- `scheduleOrphanCleanup` — _(unfilled)_
- `scheduleOperation` — _(unfilled)_
- `runNow` — _(unfilled)_
- `cancelOperation` — _(unfilled)_
- `cancelAllScheduledOperations` — _(unfilled)_
- `rescheduleAll` — _(unfilled)_
- `enqueueDuplicateScan` — _(unfilled)_
- `cancelDuplicateScan` — _(unfilled)_
- `scheduleThumbnailPreload` — _(unfilled)_
- `cancelThumbnailPreload` — _(unfilled)_
- `cancelAllThumbnailPreloads` — _(unfilled)_
- `observeAndReschedule` — _(unfilled)_
- `scheduleStreamingCacheGc` — _(unfilled)_
- `schedulePendingRevocation` — _(unfilled)_

