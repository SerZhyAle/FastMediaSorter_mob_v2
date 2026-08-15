# S0300 - Coverage Inventory (domain + data)

Source: `dev/CATALOG/app_v2.jsonl` (standard scan) + manual `noLegal` rows for Phase 07.

Cutoff: **in** = branching / transformation / error-handling logic; **out** = pure data holder, marker, thin delegate, Glide glue, or already covered. Phases refine per the cutoff when implementing.

Status column `tests`: `yes` = a `*Test.kt` already exists per catalog `hasTests`; `no` = to be written.


## Summary (per phase)

| Phase | Total | in-scope | in-scope already tested | out |
|-------|------:|---------:|------------------------:|----:|
| 02 | 149 | 114 | 114 | 35 |
| 03 | 113 | 29 | 29 | 84 |
| 04 | 61 | 25 | 25 | 36 |
| 05 | 114 | 58 | 58 | 56 |
| 06 | 147 | 112 | 19 | 35 |
| 07 | 9 | 7 | 0 | 2 |
| **all** | **593** | **382** | - | - |

## Phase 02 - domain use cases & core logic

In-scope: 114 (all have tests) | Out: 35 | Total classes: 149

Final batch (2026-05-29): 17 use-case classes gained `*Test.kt` coverage; 4 rows reclassified `out` (DownloadNetworkFileUseCase, ExportSettingsUseCase, ImportSettingsUseCase, SearchLyricsUseCase) - see per-row reasons. Phase 02 in-scope rows remaining: 0.

| Class | Path | LOC | tests | scope | reason |
|-------|------|----:|:-----:|:-----:|--------|
| FileNameConflictResolver | `domain/files/FileNameConflictResolver.kt` | 62 | yes | in |  |
| FileHasher | `domain/hash/FileHasher.kt` | 24 | no | out | reclassified: no testable logic (pure interface) |
| MutationJournal | `domain/mutation/MutationJournal.kt` | 46 | no | out | reclassified: no testable logic (pure interface) |
| PathNormalizer | `domain/path/PathNormalizer.kt` | 18 | no | out | reclassified: no testable logic (pure interface; impl CanonicalPathNormalizer in core/path) |
| PlaybackCompletionDetector | `domain/playback/PlaybackCompletionDetector.kt` | 31 | yes | in |  |
| PrefetchFormula | `domain/playback/PrefetchFormula.kt` | 219 | yes | in |  |
| AddResourceAsDestinationUseCase | `domain/usecase/AddResourceAsDestinationUseCase.kt` | 48 | yes | in |  |
| AddResourceUseCase | `domain/usecase/AddResourceUseCase.kt` | 97 | yes | in |  |
| AdjustImageUseCase | `domain/usecase/AdjustImageUseCase.kt` | 114 | out | out | reclassified: android-bound (Bitmap/Canvas/ColorMatrix); needs Robolectric |
| AppendToScheduledLogUseCase | `domain/usecase/AppendToScheduledLogUseCase.kt` | 43 | yes | in |  |
| ApplyImageFilterUseCase | `domain/usecase/ApplyImageFilterUseCase.kt` | 124 | out | out | reclassified: android-bound (Bitmap/Canvas/ColorMatrix); needs Robolectric |
| ApplyWatchFavoritesDeltaUseCase | `domain/usecase/ApplyWatchFavoritesDeltaUseCase.kt` | 33 | yes | in |  |
| ArchiveFilesUseCase | `domain/usecase/ArchiveFilesUseCase.kt` | 257 | yes | in | local-file + error/skip branches covered; content:// (Uri.parse) not |
| BackfillSmbCredentialShareNameUseCase | `domain/usecase/BackfillSmbCredentialShareNameUseCase.kt` | 105 | yes | in |  |
| BackupMapper | `domain/usecase/BackupMapper.kt` | 477 | yes | in |  |
| BackupToGoogleDriveUseCase | `domain/usecase/BackupToGoogleDriveUseCase.kt` | 276 | yes | in |  |
| ByteProgressCallback | `domain/usecase/ByteProgressCallback.kt` | 32 | no | out | reclassified: no testable logic (callback interface, only a constant + no-op defaults) |
| CalculateOptimalCacheSizeUseCase | `domain/usecase/CalculateOptimalCacheSizeUseCase.kt` | 71 | no | out | reclassified: branch input comes from Android StatFs/Environment, not JVM-testable without instrumentation |
| ChangeGifSpeedUseCase | `domain/usecase/ChangeGifSpeedUseCase.kt` | 172 | out | out | reclassified: android-bound (Bitmap GIF decode/encode); needs Robolectric |
| CheckPermissionStatusUseCase | `domain/usecase/CheckPermissionStatusUseCase.kt` | 54 | no | out | reclassified: every branch depends on Android Manifest/Build/ContextCompat/PowerManager; needs instrumentation/Robolectric |
| CleanupOrphanedTempFilesUseCase | `domain/usecase/CleanupOrphanedTempFilesUseCase.kt` | 126 | yes | in |  |
| CleanupTrashFoldersUseCase | `domain/usecase/CleanupTrashFoldersUseCase.kt` | 213 | yes | in |  |
| ClearResumeStateUseCase | `domain/usecase/ClearResumeStateUseCase.kt` | 13 | no | out | reclassified: no testable logic (thin delegate) |
| ClearScheduledOperationsLogUseCase | `domain/usecase/ClearScheduledOperationsLogUseCase.kt` | 17 | yes | in |  |
| ClearScheduledOperationsUseCase | `domain/usecase/ClearScheduledOperationsUseCase.kt` | 11 | no | out | reclassified: no testable logic (thin delegate) |
| ComputeFileHashUseCase | `domain/usecase/ComputeFileHashUseCase.kt` | 63 | yes | in |  |
| CreateDirectoryUseCase | `domain/usecase/CreateDirectoryUseCase.kt` | 61 | yes | in |  |
| CreateDrawingUseCase | `domain/usecase/CreateDrawingUseCase.kt` | 162 | yes | in | input-validation guards covered; success path needs Bitmap/Canvas + displayMetrics (partial) |
| CreateTextNoteUseCase | `domain/usecase/CreateTextNoteUseCase.kt` | 73 | yes | in |  |
| CredentialAuditor | `domain/usecase/CredentialAuditor.kt` | 111 | yes | in |  |
| DedupAuthAccountsUseCase | `domain/usecase/DedupAuthAccountsUseCase.kt` | 79 | yes | in |  |
| DeleteByFileSizeUseCase | `domain/usecase/DeleteByFileSizeUseCase.kt` | 94 | yes | in |  |
| DeleteDirectoriesUseCase | `domain/usecase/DeleteDirectoriesUseCase.kt` | 57 | yes | in |  |
| DeleteFilesUseCase | `domain/usecase/DeleteFilesUseCase.kt` | 26 | yes | in |  |
| DeletePathPolicy | `domain/usecase/DeletePathPolicy.kt` | 21 | yes | in |  |
| DeleteResourceUseCase | `domain/usecase/DeleteResourceUseCase.kt` | 39 | yes | in |  |
| DeleteScheduledOperationUseCase | `domain/usecase/DeleteScheduledOperationUseCase.kt` | 11 | no | out | reclassified: no testable logic (thin delegate) |
| DetectDuplicatesUseCase | `domain/usecase/DetectDuplicatesUseCase.kt` | 129 | yes | in |  |
| DiscoverNetworkResourcesUseCase | `domain/usecase/DiscoverNetworkResourcesUseCase.kt` | 212 | yes | in |  |
| DownloadNetworkFileUseCase | `domain/usecase/DownloadNetworkFileUseCase.kt` | 205 | out | out | reclassified: every protocol path goes through PathUtils.safeParseUri (android.net.Uri) immediately + MediaStoreNotifier; only the trivial unsupported-protocol branch is JVM-reachable |
| ExecuteScheduledOperationUseCase | `domain/usecase/ExecuteScheduledOperationUseCase.kt` | 340 | yes | in |  |
| ExportFavoritesUseCase | `domain/usecase/ExportFavoritesUseCase.kt` | 106 | out | out | reclassified: android-bound (Environment/Build on every write path); only catch branch JVM-reachable |
| ExportSettingsUseCase | `domain/usecase/ExportSettingsUseCase.kt` | 328 | out | out | reclassified: XML payload (only pure logic) is never returned/observable - invoke() always writes via MediaStore/Environment (writeToDownloads), which under JVM NPEs in MediaStoreNotifier or writes a stray file; escapeXml is private |
| ExtractArchiveUseCase | `domain/usecase/ExtractArchiveUseCase.kt` | 357 | yes | in | local-path extraction (FileInputStream/writeEntryLocal), sanitize/traversal, zip-bomb, cancel covered; content:// + SAF target (Uri/DocumentFile) not |
| ExtractExifMetadataUseCase | `domain/usecase/ExtractExifMetadataUseCase.kt` | 160 | out | out | reclassified: android-bound (ExifInterface/Uri); needs Robolectric |
| ExtractGifFramesUseCase | `domain/usecase/ExtractGifFramesUseCase.kt` | 263 | out | out | reclassified: android-bound (Bitmap/Canvas/Movie/MediaScannerConnection); needs Robolectric |
| ExtractVideoMetadataUseCase | `domain/usecase/ExtractVideoMetadataUseCase.kt` | 186 | out | out | reclassified: android-bound (MediaMetadataRetriever/Uri); needs Robolectric |
| FavoritesUseCase | `domain/usecase/FavoritesUseCase.kt` | 53 | yes | in |  |
| FileOperationResultExt | `domain/usecase/FileOperationResultExt.kt` | 139 | no | out | reclassified: public formatter requires Android Context.getString/R; testable logic is in private helpers only |
| FileOperationUseCase | `domain/usecase/FileOperationUseCase.kt` | 530 | yes | in | protocol routing (cloud/smb/sftp/ftp/mixed) + history accessors covered; local-FS branches route through MediaStoreNotifier and are covered by Local*OperationTests |
| FlipImageUseCase | `domain/usecase/FlipImageUseCase.kt` | 146 | out | out | reclassified: android-bound (Bitmap/Matrix/ExifInterface); needs Robolectric |
| GetDestinationsUseCase | `domain/usecase/GetDestinationsUseCase.kt` | 70 | yes | in |  |
| GetDeviceStorageUseCase | `domain/usecase/GetDeviceStorageUseCase.kt` | 24 | no | out | reclassified: success branch needs Android StatFs/Environment; only the catch branch is JVM-reachable |
| GetMediaFilesUseCase | `domain/usecase/GetMediaFilesUseCase.kt` | 474 | yes | in |  |
| MediaScanner | `domain/usecase/GetMediaFilesUseCase.kt` | 474 | yes | in |  |
| GetResourcesUseCase | `domain/usecase/GetResourcesUseCase.kt` | 47 | no | out | reclassified: no testable logic (thin pass-through delegates) |
| GetResumeStateUseCase | `domain/usecase/GetResumeStateUseCase.kt` | 14 | no | out | reclassified: no testable logic (thin delegate) |
| GetScheduledOperationsLogUseCase | `domain/usecase/GetScheduledOperationsLogUseCase.kt` | 18 | yes | in |  |
| GetScheduledOperationsUseCase | `domain/usecase/GetScheduledOperationsUseCase.kt` | 13 | no | out | reclassified: no testable logic (thin delegate) |
| ImportFavoritesUseCase | `domain/usecase/ImportFavoritesUseCase.kt` | 191 | yes | in | parse/version-check, preview, and import conflict matrix covered (contentResolver mocked) |
| ImportSettingsUseCase | `domain/usecase/ImportSettingsUseCase.kt` | 600 | out | out | reclassified: input acquisition needs Uri/MediaStore/Environment and the core parse uses org.xmlpull XmlPullParserFactory (no impl on the plain-JVM unit classpath); all mapping logic is downstream of the parser |
| ImportWatchSourcesUseCase | `domain/usecase/ImportWatchSourcesUseCase.kt` | 67 | yes | in |  |
| LocalCopyFileOperation | `domain/usecase/LocalCopyFileOperation.kt` | 143 | yes | in | plain-FS copy/skip/missing/partial covered (TemporaryFolder); content:// SAF (Uri.parse) not |
| LocalDeleteFileOperation | `domain/usecase/LocalDeleteFileOperation.kt` | 167 | yes | in | cloud-vs-local split + result passthrough covered (SDK<R skips batch path); internal local handler not exercised (partial) |
| LocalMoveFileOperation | `domain/usecase/LocalMoveFileOperation.kt` | 197 | yes | in | rename/copy-delete/skip/missing/partial covered (TemporaryFolder); content:// SAF (Uri.parse) not |
| LocalRenameFileOperation | `domain/usecase/LocalRenameFileOperation.kt` | 69 | yes | in |  |
| MarkContextualShownUseCase | `domain/usecase/MarkContextualShownUseCase.kt` | 13 | no | out | reclassified: no testable logic (thin delegate) |
| MediaScannerFactory | `domain/usecase/MediaScannerFactory.kt` | 37 | yes | in |  |
| MergeDrawOverlayUseCase | `domain/usecase/MergeDrawOverlayUseCase.kt` | 50 | out | out | reclassified: android-bound (Bitmap/Canvas composite); needs Robolectric |
| MigrateCameraResourceUseCase | `domain/usecase/MigrateCameraResourceUseCase.kt` | 28 | yes | in |  |
| MigrateS0059UseCase | `domain/usecase/MigrateS0059UseCase.kt` | 105 | out | out | reclassified: android-bound (Environment + Context.getString unavoidable on invoke path); needs Robolectric |
| NetworkImageEditUseCase | `domain/usecase/NetworkImageEditUseCase.kt` | 338 | yes | in | validation + download/edit/upload/cleanup flow covered (handlers + image use cases mocked) |
| NetworkSpeedTestUseCase | `domain/usecase/NetworkSpeedTestUseCase.kt` | 460 | yes | in | local-disk + SMB measurement flow + error emission covered; SAF/cloud (Uri) not |
| ProvisionDefaultResourcesUseCase | `domain/usecase/ProvisionDefaultResourcesUseCase.kt` | 209 | yes | in |  |
| ProvisionDownloadsDestinationUseCase | `domain/usecase/ProvisionDownloadsDestinationUseCase.kt` | 75 | out | out | reclassified: android-bound (Environment first line + Context.getString); needs Robolectric |
| PushWearSettingsUseCase | `domain/usecase/PushWearSettingsUseCase.kt` | 27 | yes | in |  |
| RecordSortSuccessUseCase | `domain/usecase/RecordSortSuccessUseCase.kt` | 77 | yes | in |  |
| RenameVirtualResourcesUseCase | `domain/usecase/RenameVirtualResourcesUseCase.kt` | 86 | out | out | reclassified: android-bound (LocaleHelper/Environment/createConfigurationContext.getString on invoke path); needs Robolectric |
| RequestContextualPermissionUseCase | `domain/usecase/RequestContextualPermissionUseCase.kt` | 27 | out | out | reclassified: android-bound (Fragment + BottomSheet UI); needs Robolectric/instrumentation |
| ResetSmbConnectionsUseCase | `domain/usecase/ResetSmbConnectionsUseCase.kt` | 21 | no | out | reclassified: no testable logic (thin delegate) |
| ResolveResourceIconUseCase | `domain/usecase/ResolveResourceIconUseCase.kt` | 82 | yes | in |  |
| ResourceEditorUseCase | `domain/usecase/ResourceEditorUseCase.kt` | 700 | yes | in | validate/buildPersistenceModel/copy-name/suggestions/existing-name+path/initialize/emptyForm covered; network testConnection + credential persistence (CryptoHelper) not |
| RestoreDeletedUseCase | `domain/usecase/RestoreDeletedUseCase.kt` | 250 | yes | in |  |
| RestoreFromGoogleDriveUseCase | `domain/usecase/RestoreFromGoogleDriveUseCase.kt` | 269 | yes | in | auth gate, no-backup, version compat, dedup (path/cloud), getBackupInfo covered (Drive client mocked) |
| RotateImageUseCase | `domain/usecase/RotateImageUseCase.kt` | 132 | out | out | reclassified: android-bound (Bitmap/Matrix/ExifInterface/MediaStoreNotifier); needs Robolectric |
| SaveDrawingUseCase | `domain/usecase/SaveDrawingUseCase.kt` | 234 | yes | in |  |
| SaveGifFirstFrameUseCase | `domain/usecase/SaveGifFirstFrameUseCase.kt` | 94 | out | out | reclassified: android-bound (Bitmap/Environment); needs Robolectric |
| SaveResumeStateUseCase | `domain/usecase/SaveResumeStateUseCase.kt` | 14 | no | out | reclassified: no testable logic (thin delegate) |
| SaveTextNoteUseCase | `domain/usecase/SaveTextNoteUseCase.kt` | 142 | yes | in |  |
| ScanLocalFoldersUseCase | `domain/usecase/ScanLocalFoldersUseCase.kt` | 259 | yes | in |  |
| ScanProgressCallback | `domain/usecase/ScanProgressCallback.kt` | 33 | no | out | reclassified: no testable logic (callback interface, only no-op defaults) |
| ScheduleNetworkSyncUseCase | `domain/usecase/ScheduleNetworkSyncUseCase.kt` | 69 | out | out | reclassified: android-bound (WorkManager.getInstance + WorkRequest builders); needs Robolectric |
| SearchAudioCoverUseCase | `domain/usecase/SearchAudioCoverUseCase.kt` | 303 | yes | in | invoke orchestration (settings gate, m4a skip, ID3 priority) + searchItunes result mapping covered; Deezer/MusicBrainz org.json parsing driven only via non-2xx responses |
| SearchLyricsUseCase | `domain/usecase/SearchLyricsUseCase.kt` | 659 | out | out | reclassified: every execute() path runs the provider loop against a non-injectable internal OkHttpClient (real network) after MediaMetadataRetriever extraction; query/script helpers are private and unreachable in isolation; providers use org.json/Jsoup |
| SearchQueryUtils | `domain/usecase/SearchQueryUtils.kt` | 78 | yes | in |  |
| SendPlaybackCommandUseCase | `domain/usecase/SendPlaybackCommandUseCase.kt` | 29 | yes | in |  |
| SendResourcesToWatchUseCase | `domain/usecase/SendResourcesToWatchUseCase.kt` | 87 | yes | in |  |
| SmbOperationsUseCase | `domain/usecase/SmbOperationsUseCase.kt` | 686 | yes | in | testConnection/listShares/scan+list mapping (detectMediaType), getConnectionInfo, checkTrash/cleanupTrash, clearAllConnectionPools covered; save*Credentials not (CryptoHelper.encrypt on every path) |
| StreamOffloadUseCase | `domain/usecase/StreamOffloadUseCase.kt` | 243 | yes | in |  |
| SyncMediaStoreUseCase | `domain/usecase/SyncMediaStoreUseCase.kt` | 132 | yes | in | non-local/virtual/invalid-dir/empty/hidden-only guard branches covered; per-file MediaStoreNotifier notify (Environment) not (partial) |
| SyncNetworkResourcesUseCase | `domain/usecase/SyncNetworkResourcesUseCase.kt` | 149 | yes | in |  |
| TestCredentialsLoader | `domain/usecase/TestCredentialsLoader.kt` | 191 | out | out | reclassified: android-bound (Environment + org.json + private parse unreachable from JVM); needs Robolectric |
| UnusedCredentialPolicy | `domain/usecase/UnusedCredentialPolicy.kt` | 63 | yes | in |  |
| UpdateResourceUseCase | `domain/usecase/UpdateResourceUseCase.kt` | 29 | yes | in |  |
| UpdateScheduledOperationUseCase | `domain/usecase/UpdateScheduledOperationUseCase.kt` | 13 | no | out | reclassified: no testable logic (thin delegate) |
| UpsertScheduledOperationUseCase | `domain/usecase/UpsertScheduledOperationUseCase.kt` | 13 | no | out | reclassified: no testable logic (thin delegate) |
| LinkAutoDownloadCoordinator | `domain/usecase/link/LinkAutoDownloadCoordinator.kt` | 776 | yes | in |  |
| LinkExtractionRegistry | `domain/usecase/link/LinkExtractionRegistry.kt` | 29 | yes | in |  |
| MediaMimeWhitelist | `domain/usecase/link/MediaMimeWhitelist.kt` | 68 | yes | in |  |
| UrlExtractionStrategy | `domain/usecase/link/UrlExtractionStrategy.kt` | 64 | no | out | reclassified: no testable logic (pure interface + sealed types) |
| YtMusicAudioOnlyContract | `domain/usecase/link/YtMusicAudioOnlyContract.kt` | 67 | yes | in |  |
| StreamingPipeline | `domain/usecase/link/streaming/StreamingPipeline.kt` | 45 | no | out | reclassified: no testable logic (pure interface + sealed types) |
| IncrementalScanStrategy | `domain/usecase/scan/IncrementalScanStrategy.kt` | 157 | yes | in |  |
| ScanDeltaDetector | `domain/usecase/scan/ScanDeltaDetector.kt` | 122 | yes | in |  |
| ScanDelta | `domain/usecase/scan/ScanDeltaDetector.kt` | 122 | yes | in |  |
| ScanDispatcher | `domain/usecase/scan/ScanDispatcher.kt` | 85 | yes | in |  |
| ScanSettings | `domain/usecase/scan/ScanSettings.kt` | 64 | yes | in |  |
| QuickVerifier | `domain/verifier/QuickVerifier.kt` | 44 | no | out | reclassified: no testable logic (pure interface) |
| Mutation | `domain/mutation/Mutation.kt` | 58 | no | out | no declared logic (data holder/marker) |
| MutationEntry | `domain/mutation/MutationJournal.kt` | 46 | no | out | no declared logic (data holder/marker) |
| AddMultipleResult | `domain/usecase/AddResourceUseCase.kt` | 97 | no | out | no declared logic (data holder/marker) |
| ArchiveProgress | `domain/usecase/ArchiveFilesUseCase.kt` | 257 | no | out | no declared logic (data holder/marker) |
| BackupPayload | `domain/usecase/BackupData.kt` | 235 | no | out | no declared logic (data holder/marker) |
| BackupSettings | `domain/usecase/BackupData.kt` | 235 | no | out | no declared logic (data holder/marker) |
| BackupResource | `domain/usecase/BackupData.kt` | 235 | no | out | no declared logic (data holder/marker) |
| BackupScheduledOperation | `domain/usecase/BackupData.kt` | 235 | no | out | no declared logic (data holder/marker) |
| BackupFavorite | `domain/usecase/BackupData.kt` | 235 | no | out | no declared logic (data holder/marker) |
| NetworkHost | `domain/usecase/DiscoverNetworkResourcesUseCase.kt` | 212 | yes | out | no declared logic (data holder/marker) |
| ScheduledExecutionResult | `domain/usecase/ExecuteScheduledOperationUseCase.kt` | 340 | no | out | no declared logic (data holder/marker) |
| ExtractProgress | `domain/usecase/ExtractArchiveUseCase.kt` | 357 | no | out | no declared logic (data holder/marker) |
| ExifMetadata | `domain/usecase/ExtractExifMetadataUseCase.kt` | 160 | no | out | no declared logic (data holder/marker) |
| VideoMetadata | `domain/usecase/ExtractVideoMetadataUseCase.kt` | 186 | no | out | no declared logic (data holder/marker) |
| FileOperation | `domain/usecase/FileOperationUseCase.kt` | 530 | no | out | no declared logic (data holder/marker) |
| FileOperationResult | `domain/usecase/FileOperationUseCase.kt` | 530 | no | out | no declared logic (data holder/marker) |
| FileOperationProgress | `domain/usecase/FileOperationUseCase.kt` | 530 | no | out | no declared logic (data holder/marker) |
| OperationHistory | `domain/usecase/FileOperationUseCase.kt` | 530 | no | out | no declared logic (data holder/marker) |
| MediaFilePage | `domain/usecase/GetMediaFilesUseCase.kt` | 474 | yes | out | no declared logic (data holder/marker) |
| SizeFilter | `domain/usecase/GetMediaFilesUseCase.kt` | 474 | yes | out | no declared logic (data holder/marker) |
| ImportWatchResult | `domain/usecase/ImportWatchSourcesUseCase.kt` | 67 | no | out | no declared logic (data holder/marker) |
| SpeedTestResult | `domain/usecase/NetworkSpeedTestUseCase.kt` | 460 | no | out | no declared logic (data holder/marker) |
| ResourceEditorSaveResult | `domain/usecase/ResourceEditorUseCase.kt` | 700 | no | out | no declared logic (data holder/marker) |
| SendResult | `domain/usecase/SendResourcesToWatchUseCase.kt` | 87 | yes | out | no declared logic (data holder/marker) |
| VirtualResourceDefaultNames | `domain/usecase/VirtualResourceDefaultNames.kt` | 49 | no | out | no declared logic (data holder/marker) |
| SiteBatchItem | `domain/usecase/link/UrlExtractionStrategy.kt` | 64 | no | out | no declared logic (data holder/marker) |
| OpenResult | `domain/usecase/link/UrlExtractionStrategy.kt` | 64 | no | out | no declared logic (data holder/marker) |
| BlockedReason | `domain/usecase/link/UrlExtractionStrategy.kt` | 64 | no | out | no declared logic (data holder/marker) |
| ProbeResult | `domain/usecase/link/UrlExtractionStrategy.kt` | 64 | no | out | no declared logic (data holder/marker) |
| PipelineOutcome | `domain/usecase/link/streaming/StreamingPipeline.kt` | 45 | no | out | no declared logic (data holder/marker) |
| QuickVerifierKey | `domain/verifier/QuickVerifier.kt` | 44 | no | out | no declared logic (data holder/marker) |

## Phase 03 - domain models, strategies, identity, input, ocr, transfer

In-scope: 29 (all have tests) | Out: 84 | Total classes: 113

Batch (2026-05-29): 27 in-scope rows (26 `*Test.kt` files - TranslationFontSize+Family share one) gained coverage,
154 test methods, all green via per-class XML. 10 in-scope rows reclassified `out`: GoogleIdentityRepository,
OcrEngineContributor, OfflineOcrEngine, ResourceStrategy, FileTransferProvider (pure interfaces);
ResetAllUseCase, ResetBindingUseCase, SetBindingUseCase (thin delegates);
PaddleOcrEngine, PaddleOcrEngineContributor (noLegal source set, off the standard test classpath).
Phase 03 in-scope rows remaining: 0.

| Class | Path | LOC | tests | scope | reason |
|-------|------|----:|:-----:|:-----:|--------|
| GoogleIdentityRepository | `domain/identity/GoogleIdentityRepository.kt` | 67 | out | out | reclassified: pure interface (Context-bound suspend contract, no logic) |
| InputTrigger | `domain/input/InputTrigger.kt` | 87 | yes | in | serialize/deserialize roundtrip + unknown-type error; KeyEvent extensions excluded (android-bound) |
| DetectConflictsUseCase | `domain/input/usecase/DetectConflictsUseCase.kt` | 32 | yes | in |  |
| ResetAllUseCase | `domain/input/usecase/ResetAllUseCase.kt` | 14 | out | out | reclassified: thin delegate (forward + log only) |
| ResetBindingUseCase | `domain/input/usecase/ResetBindingUseCase.kt` | 16 | out | out | reclassified: thin delegate (forward + log only) |
| ResetGroupUseCase | `domain/input/usecase/ResetGroupUseCase.kt` | 26 | yes | in | group→prefix mapping verified against repo |
| SetBindingUseCase | `domain/input/usecase/SetBindingUseCase.kt` | 21 | out | out | reclassified: thin delegate (forward + log only) |
| AppSettings | `domain/model/AppSettings.kt` | 239 | yes | in | getGloballyEnabledMediaTypes branches |
| FileTypeFlags | `domain/model/FileTypeFilter.kt` | 52 | yes | in |  |
| MediaExtensions | `domain/model/MediaExtensions.kt` | 48 | yes | in |  |
| FileFilter | `domain/model/Models.kt` | 304 | yes | in | isEmpty/activeFilterCount (FileFilterTest) |
| MediaResource | `domain/model/Models.kt` | 304 | yes | in | capability predicates (MediaResourceTest) |
| MediaType | `domain/model/Models.kt` | 304 | yes | in | isBinaryFile/isDocumentFile + ResourceType.isNetworkResource (MediaTypeAndResourceTypeTest) |
| PlaybackOrderMode | `domain/model/PlaybackOrderMode.kt` | 18 | yes | in |  |
| PrefetchCacheMultiplier | `domain/model/PrefetchCacheMultiplier.kt` | 24 | yes | in |  |
| ResourceFormData | `domain/model/ResourceFormData.kt` | 83 | yes | in | applyProfile extension |
| ResourceValidationResult | `domain/model/ResourceValidationResult.kt` | 66 | yes | in | valid/invalid factories |
| StereoMode | `domain/model/StereoMode.kt` | 159 | yes | in |  |
| StreamingCacheCleanupMode | `domain/model/StreamingCacheCleanupMode.kt` | 23 | yes | in |  |
| WearEventEnvelope | `domain/model/WearEventEnvelope.kt` | 30 | yes | in | custom equals/hashCode (ByteArray content) |
| MediaQualityPreference | `domain/model/link/MediaQualityPreference.kt` | 28 | yes | in | fromSettings mapping |
| TranslationFontSize | `domain/models/TranslationFontSize.kt` | 44 | yes | in | fromMultiplier (TranslationFontSizeTest) |
| TranslationFontFamily | `domain/models/TranslationFontSize.kt` | 44 | yes | in | fromTypefaceName (TranslationFontSizeTest) |
| OcrEngineContributor | `domain/ocr/OcrEngineContributor.kt` | 14 | out | out | reclassified: pure interface (no logic) |
| OfflineOcrEngine | `domain/ocr/OfflineOcrEngine.kt` | 30 | out | out | reclassified: pure interface (no logic) |
| OfflineOcrEngineProvider | `domain/ocr/OfflineOcrEngineProvider.kt` | 69 | yes | in | engine selection + default fallback + release dedup |
| PaddleOcrEngine | `domain/ocr/PaddleOcrEngine.kt` | 182 | yes | in | covered in Phase 07 (src/testNoLegal): input-validation guard + model-init-failure path; inference core is Paddle-native/Bitmap-bound (not JVM-reachable) |
| PaddleOcrEngineContributor | `domain/ocr/PaddleOcrEngineContributor.kt` | 32 | yes | in | covered in Phase 07 (src/testNoLegal): engineType + supports() engine/Cyrillic-code matrix |
| CloudResourceStrategy | `domain/strategy/CloudResourceStrategy.kt` | 76 | yes | in |  |
| FtpResourceStrategy | `domain/strategy/FtpResourceStrategy.kt` | 83 | yes | in |  |
| LocalResourceStrategy | `domain/strategy/LocalResourceStrategy.kt` | 67 | yes | in |  |
| ResourceStrategy | `domain/strategy/ResourceStrategy.kt` | 20 | out | out | reclassified: pure interface + ResourceFieldSchema holder (no logic) |
| SftpResourceStrategy | `domain/strategy/SftpResourceStrategy.kt` | 86 | yes | in |  |
| SmbResourceStrategy | `domain/strategy/SmbResourceStrategy.kt` | 81 | yes | in |  |
| FileOperationError | `domain/transfer/FileOperationError.kt` | 72 | yes | in |  |
| FileOperationErrorHandler | `domain/transfer/FileOperationErrorHandler.kt` | 200 | yes | in | translate/recoverable/suggested-action branches (Context mocked) |
| FileTransferProvider | `domain/transfer/FileTransferProvider.kt` | 122 | out | out | reclassified: pure interface + FileInfo holder (no logic) |
| ProgressTracker | `domain/transfer/ProgressTracker.kt` | 143 | yes | in | percentage/first-last/lifecycle + generateOperationId |
| TempFileManager | `domain/transfer/TempFileManager.kt` | 179 | yes | in | temp-file creation/sanitize/cleanup (TemporaryFolder cacheDir) |
| GoogleAccessToken | `domain/identity/GoogleAccessToken.kt` | 20 | no | out | no declared logic (data holder/marker) |
| GoogleScope | `domain/identity/GoogleScope.kt` | 30 | no | out | no declared logic (data holder/marker) |
| IdentitySignInResult | `domain/identity/IdentitySignInResult.kt` | 20 | no | out | no declared logic (data holder/marker) |
| PrimaryGoogleAccount | `domain/identity/PrimaryGoogleAccount.kt` | 24 | no | out | no declared logic (data holder/marker) |
| PrimaryGoogleAccountState | `domain/identity/PrimaryGoogleAccountState.kt` | 44 | no | out | no declared logic (data holder/marker) |
| NeedsResignInReason | `domain/identity/PrimaryGoogleAccountState.kt` | 44 | no | out | no declared logic (data holder/marker) |
| IdentityFailureReason | `domain/identity/PrimaryGoogleAccountState.kt` | 44 | no | out | no declared logic (data holder/marker) |
| CommandGroup | `domain/input/CommandGroup.kt` | 12 | no | out | no declared logic (data holder/marker) |
| CommandId | `domain/input/CommandId.kt` | 93 | no | out | no declared logic (data holder/marker) |
| InputBinding | `domain/input/InputBinding.kt` | 12 | no | out | no declared logic (data holder/marker) |
| BindingSource | `domain/input/InputBinding.kt` | 12 | no | out | no declared logic (data holder/marker) |
| InputSurface | `domain/input/InputBinding.kt` | 12 | no | out | no declared logic (data holder/marker) |
| AudioMetadata | `domain/model/AudioMetadata.kt` | 16 | no | out | no declared logic (data holder/marker) |
| BackgroundAudioExitBehavior | `domain/model/BackgroundAudioExitBehavior.kt` | 17 | no | out | no declared logic (data holder/marker) |
| CredentialAuditEntry | `domain/model/CredentialAuditEntry.kt` | 49 | no | out | no declared logic (data holder/marker) |
| CredentialStatus | `domain/model/CredentialAuditEntry.kt` | 49 | no | out | no declared logic (data holder/marker) |
| CredentialAuditReport | `domain/model/CredentialAuditEntry.kt` | 49 | no | out | no declared logic (data holder/marker) |
| DeviceStorageState | `domain/model/DeviceStorageState.kt` | 7 | no | out | no declared logic (data holder/marker) |
| DuplicateScanProgress | `domain/model/DuplicateModels.kt` | 26 | no | out | no declared logic (data holder/marker) |
| DuplicateGroup | `domain/model/DuplicateModels.kt` | 26 | no | out | no declared logic (data holder/marker) |
| DuplicateDetectionResult | `domain/model/DuplicateModels.kt` | 26 | no | out | no declared logic (data holder/marker) |
| ScanPhase | `domain/model/DuplicateModels.kt` | 26 | no | out | no declared logic (data holder/marker) |
| FavoritesImportPreview | `domain/model/FavoritesExportModel.kt` | 68 | no | out | no declared logic (data holder/marker) |
| FavoritesExportResult | `domain/model/FavoritesExportModel.kt` | 68 | no | out | no declared logic (data holder/marker) |
| FavoritesConflictStrategy | `domain/model/FavoritesExportModel.kt` | 68 | no | out | no declared logic (data holder/marker) |
| FavoritesExportFile | `domain/model/FavoritesExportModel.kt` | 68 | no | out | no declared logic (data holder/marker) |
| FavoritesImportDetail | `domain/model/FavoritesExportModel.kt` | 68 | no | out | no declared logic (data holder/marker) |
| FavoritesImportResult | `domain/model/FavoritesExportModel.kt` | 68 | no | out | no declared logic (data holder/marker) |
| ExportedFavorite | `domain/model/FavoritesExportModel.kt` | 68 | no | out | no declared logic (data holder/marker) |
| FavoritesImportStatus | `domain/model/FavoritesExportModel.kt` | 68 | no | out | no declared logic (data holder/marker) |
| GamepadAction | `domain/model/GamepadAction.kt` | 38 | no | out | no declared logic (data holder/marker) |
| MetadataState | `domain/model/MetadataState.kt` | 41 | no | out | no declared logic (data holder/marker) |
| DisplayMode | `domain/model/Models.kt` | 304 | no | out | no declared logic (data holder/marker) |
| SortMode | `domain/model/Models.kt` | 304 | no | out | no declared logic (data holder/marker) |
| ResourceProfile | `domain/model/Models.kt` | 304 | no | out | no declared logic (data holder/marker) |
| FileAttributes | `domain/model/Models.kt` | 304 | no | out | no declared logic (data holder/marker) |
| MediaFile | `domain/model/Models.kt` | 304 | no | out | no declared logic (data holder/marker) |
| FileOperationType | `domain/model/Models.kt` | 304 | no | out | no declared logic (data holder/marker) |
| UndoOperation | `domain/model/Models.kt` | 304 | no | out | no declared logic (data holder/marker) |
| ResourceType | `domain/model/Models.kt` | 304 | no | out | no declared logic (data holder/marker) |
| OffloadOffer | `domain/model/OffloadModels.kt` | 44 | no | out | no declared logic (data holder/marker) |
| CleanupPromptRequest | `domain/model/OffloadModels.kt` | 44 | no | out | no declared logic (data holder/marker) |
| PermissionEntry | `domain/model/PermissionEntry.kt` | 28 | no | out | no declared logic (data holder/marker) |
| PermissionGroupHeader | `domain/model/PermissionEntry.kt` | 28 | no | out | no declared logic (data holder/marker) |
| PermissionStatus | `domain/model/PermissionEntry.kt` | 28 | no | out | no declared logic (data holder/marker) |
| PermissionGroup | `domain/model/PermissionEntry.kt` | 28 | no | out | no declared logic (data holder/marker) |
| Protocol | `domain/model/PrefetchPlan.kt` | 71 | no | out | no declared logic (data holder/marker) |
| PrefetchPlan | `domain/model/PrefetchPlan.kt` | 71 | no | out | no declared logic (data holder/marker) |
| StreamViabilityState | `domain/model/PrefetchPlan.kt` | 71 | no | out | no declared logic (data holder/marker) |
| PrefetchDerivation | `domain/model/PrefetchPlan.kt` | 71 | no | out | no declared logic (data holder/marker) |
| ResourceConnectionStatus | `domain/model/ResourceConnectionTestResult.kt` | 15 | no | out | no declared logic (data holder/marker) |
| ResourceConnectionTestResult | `domain/model/ResourceConnectionTestResult.kt` | 15 | no | out | no declared logic (data holder/marker) |
| ResourceEditorMode | `domain/model/ResourceEditorMode.kt` | 7 | no | out | no declared logic (data holder/marker) |
| ResourceFieldKey | `domain/model/ResourceValidationResult.kt` | 66 | no | out | no declared logic (data holder/marker) |
| ResourceErrorCode | `domain/model/ResourceValidationResult.kt` | 66 | no | out | no declared logic (data holder/marker) |
| ResourceVerificationStatus | `domain/model/ResourceVerificationStatus.kt` | 7 | no | out | no declared logic (data holder/marker) |
| ScreenType | `domain/model/ResumeState.kt` | 26 | no | out | no declared logic (data holder/marker) |
| ResumeState | `domain/model/ResumeState.kt` | 26 | no | out | no declared logic (data holder/marker) |
| ScheduledOpType | `domain/model/ScheduledOpType.kt` | 8 | no | out | no declared logic (data holder/marker) |
| ScheduledOperation | `domain/model/ScheduledOperation.kt` | 22 | no | out | no declared logic (data holder/marker) |
| TimeFilter | `domain/model/TimeFilter.kt` | 9 | no | out | no declared logic (data holder/marker) |
| WearFavoriteDeltaItem | `domain/model/WearFavoritesPayload.kt` | 13 | no | out | no declared logic (data holder/marker) |
| WearFavoritesDeltaPayload | `domain/model/WearFavoritesPayload.kt` | 13 | no | out | no declared logic (data holder/marker) |
| WearPlaybackCommand | `domain/model/WearPlaybackCommand.kt` | 4 | no | out | no declared logic (data holder/marker) |
| WearPlaybackStatePayload | `domain/model/WearPlaybackStatePayload.kt` | 11 | no | out | no declared logic (data holder/marker) |
| WearSettingsPayload | `domain/model/WearSettingsPayload.kt` | 16 | no | out | no declared logic (data holder/marker) |
| WearSourcesExportPayload | `domain/model/WearSourcesExportPayload.kt` | 9 | no | out | no declared logic (data holder/marker) |
| WearNetworkSourcePayload | `domain/model/WearSyncPayload.kt` | 31 | no | out | no declared logic (data holder/marker) |
| WearSyncPayload | `domain/model/WearSyncPayload.kt` | 31 | no | out | no declared logic (data holder/marker) |
| StreamingManifest | `domain/model/link/StreamingManifest.kt` | 27 | no | out | no declared logic (data holder/marker) |
| TranslationSessionSettings | `domain/models/TranslationFontSize.kt` | 44 | no | out | no declared logic (data holder/marker) |
| OcrTextBlock | `domain/ocr/OcrTextBlock.kt` | 13 | no | out | no declared logic (data holder/marker) |
| ResourceFieldSchema | `domain/strategy/ResourceStrategy.kt` | 20 | no | out | no declared logic (data holder/marker) |
| FileInfo | `domain/transfer/FileTransferProvider.kt` | 122 | no | out | no declared logic (data holder/marker) |

## Phase 04 - data repositories & local persistence

In-scope: 25 (all have tests) | Out: 36 | Total classes: 61

Implementation (2026-05-29): 18 new `*Test.kt` classes added (197 methods) across data/repository, data/local/{db,preferences,staging}, data/observer, data/paging; 2 pre-existing repo tests (NetworkCredentialsRepositoryTest, PlaybackPositionRepositoryImplMarkCompletedTest) recognised. 15 rows reclassified `in`→`out` per the cutoff: thin Room-generated DAOs (covered via their repositories), android-bound classes (MediaStoreRepositoryImpl, StagingDirectoryProvider, MediaFileObserver, MediaStoreObserver), and AppDatabase migrations (instrumented-only). Phase 04 in-scope rows remaining: 0.

| Class | Path | LOC | tests | scope | reason |
|-------|------|----:|:-----:|:-----:|--------|
| LocalMediaScanner | `data/local/LocalMediaScanner.kt` | 700 | yes | in |  |
| AppDatabase | `data/local/db/AppDatabase.kt` | 778 | out | out | reclassified: schema/migrations; migration tests require room-testing MigrationTestHelper (instrumented/androidTest), not JVM unit tests |
| CachedFileListDao | `data/local/db/CachedFileListDao.kt` | 36 | out | out | reclassified: thin DAO, only Room-generated queries; CachedFileListRepositoryTest covers its use |
| CachedFileListEntity | `data/local/db/CachedFileListEntity.kt` | 77 | yes | in | hand-written equals/hashCode (CachedFileListEntityTest) |
| Converters | `data/local/db/Converters.kt` | 35 | yes | in | enum<->name round-trips + null (ConvertersTest) |
| CryptoHelper | `data/local/db/CryptoHelper.kt` | 118 | yes | in | empty/null guard branches only; AES path is AndroidKeystore-bound (CryptoHelperTest) |
| DuplicateHashCacheDao | `data/local/db/DuplicateHashCacheDao.kt` | 36 | out | out | reclassified: thin DAO, only Room-generated queries; DuplicateHashRepositoryImplTest covers its use |
| FavoritesDao | `data/local/db/FavoritesDao.kt` | 38 | out | out | reclassified: thin DAO, only Room-generated queries; FavoritesRepositoryImplTest covers its use |
| FileMetadataCacheDao | `data/local/db/FileMetadataCacheDao.kt` | 100 | yes | in | in-memory Room: lookup, FileChecksumRow projection, IN/credentials/TTL deletes (FileMetadataCacheDaoTest) |
| NetworkCredentialsDao | `data/local/db/NetworkCredentialsDao.kt` | 72 | out | out | reclassified: thin DAO, only Room-generated queries; NetworkCredentialsRepositoryTest covers its use |
| PendingRevocationDao | `data/local/db/PendingRevocationDao.kt` | 30 | out | out | reclassified: thin DAO, only Room-generated queries |
| PlaybackPositionDao | `data/local/db/PlaybackPositionDao.kt` | 52 | out | out | reclassified: thin DAO, only Room-generated queries; PlaybackPositionRepositoryImplTest covers its use |
| ResourceDao | `data/local/db/ResourceDao.kt` | 178 | yes | in | in-memory Room: FTS-sync insert/update/delete, display-order swap/batch, S0200 needs-sign-in conditional updates (ResourceDaoTest) |
| ScheduledOperationDao | `data/local/db/ScheduledOperationDao.kt` | 37 | out | out | reclassified: thin DAO, only Room-generated queries; ScheduledOperationRepositoryImplTest covers its use |
| StereoFormatOverrideDao | `data/local/db/StereoFormatOverrideDao.kt` | 18 | out | out | reclassified: thin DAO, only Room-generated queries |
| StreamingCacheDao | `data/local/db/StreamingCacheDao.kt` | 45 | out | out | reclassified: thin DAO, only Room-generated queries; StreamingCacheRepositoryImplTest covers its use |
| ThumbnailCacheDao | `data/local/db/ThumbnailCacheDao.kt` | 88 | out | out | reclassified: thin DAO, only Room-generated queries; ThumbnailCacheRepositoryImplTest covers its use |
| BrowseManualOrderPrefs | `data/local/preferences/BrowseManualOrderPrefs.kt` | 52 | yes | in | SharedPreferences round-trip, blank/missing-key nulls, clear (BrowseManualOrderPrefsTest) |
| BrowseStateDataStore | `data/local/preferences/BrowseStateDataStore.kt` | 115 | yes | in | in-memory Preferences DataStore round-trip + per-field present/absent + invalid-enum (BrowseStateDataStoreTest) |
| ReviewEligibilityDataStore | `data/local/preferences/ReviewEligibilityDataStore.kt` | 79 | yes | in | in-memory Preferences DataStore increment/snapshot/reset (ReviewEligibilityDataStoreTest) |
| SettingsManager | `data/local/preferences/SettingsManager.kt` | 291 | yes | in | in-memory Preferences DataStore: empty-store defaults, setter round-trips, nullable music-id remove branch (SettingsManagerTest) |
| LocalStagingRegistry | `data/local/staging/LocalStagingRegistry.kt` | 69 | yes | in | register/lookup/unregister/snapshot + overwrite + default location (LocalStagingRegistryTest) |
| StagingDirectoryProvider | `data/local/staging/StagingDirectoryProvider.kt` | 51 | out | out | reclassified: android-bound (Environment.getExternalStoragePublicDirectory static + File.mkdirs); only private subdirFor is pure |
| MediaFileObserver | `data/observer/MediaFileObserver.kt` | 59 | out | out | reclassified: android-bound (extends android.os.FileObserver); event dispatch is a thin map to listener, no JVM-testable branching of value |
| MediaStoreObserver | `data/observer/MediaStoreObserver.kt` | 73 | out | out | reclassified: android-framework glue (ContentObserver register/unregister), no branching logic |
| MediaFilesPagingSource | `data/paging/MediaFilesPagingSource.kt` | 106 | yes | in | load() sort-mode branches, offset/prevKey/nextKey, error path (MediaFilesPagingSourceTest) |
| AudioMetadataCacheRepository | `data/repository/AudioMetadataCacheRepository.kt` | 155 | yes | in |  |
| AuthSessionRepositoryImpl | `data/repository/AuthSessionRepositoryImpl.kt` | 296 | yes | in | blank/empty save guards, dismissed-record delegations, webview reuse-vs-new-UUID, toDomain displayName fallback + settings ordering (AuthSessionRepositoryImplTest) |
| CachedFileListRepository | `data/repository/CachedFileListRepository.kt` | 147 | yes | in | GZIP+Gson round-trip, size guard, updateFile/deleteFile found/not-found (CachedFileListRepositoryTest) |
| DuplicateHashRepositoryImpl | `data/repository/DuplicateHashRepositoryImpl.kt` | 71 | yes | in | null-resourceId coalesce, existing-copy vs new-entity branch (DuplicateHashRepositoryImplTest) |
| FavoritesRepositoryImpl | `data/repository/FavoritesRepositoryImpl.kt` | 53 | yes | in | getFavoritesForPaths empty/chunking/membership + delegates (FavoritesRepositoryImplTest) |
| MediaStoreRepositoryImpl | `data/repository/MediaStoreRepositoryImpl.kt` | 704 | out | out | reclassified: android-bound (ContentResolver/MediaStore/Cursor query building + execution); needs instrumentation |
| NetworkCredentialsRepositoryImpl | `data/repository/NetworkCredentialsRepositoryImpl.kt` | 328 | yes | in | pre-existing NetworkCredentialsRepositoryTest |
| PlaybackPositionRepositoryImpl | `data/repository/PlaybackPositionRepositoryImpl.kt` | 124 | yes | in | 95%-completion gate, isCompleted derivation, count-limit trim, error swallowing (PlaybackPositionRepositoryImplTest + ..MarkCompletedTest) |
| ResourceRepositoryImpl | `data/repository/ResourceRepositoryImpl.kt` | 555 | yes | in | entity<->domain media-type flag bitmask both ways, SMB backslash normalisation, ResourceProfile fallback, accountId enrichment, destinationOrder null->-1 (ResourceRepositoryImplTest) |
| ResumeStateRepositoryImpl | `data/repository/ResumeStateRepositoryImpl.kt` | 87 | yes | in | SharedPreferences round-trip, missing-filePath/-1 nulls, invalid-enum recovery (ResumeStateRepositoryImplTest) |
| ScheduledOperationRepositoryImpl | `data/repository/ScheduledOperationRepositoryImpl.kt` | 84 | yes | in | toDomain/toEntity enum round-trip mapping (ScheduledOperationRepositoryImplTest) |
| SettingsRepositoryImpl | `data/repository/SettingsRepositoryImpl.kt` | 695 | yes | in |  |
| StreamingCacheRepositoryImpl | `data/repository/StreamingCacheRepositoryImpl.kt` | 129 | yes | in | resolveHash determinism, delete/prune/clear file-path branches, TTL threshold (StreamingCacheRepositoryImplTest) |
| ThumbnailCacheRepositoryImpl | `data/repository/ThumbnailCacheRepositoryImpl.kt` | 239 | yes | in | file-existence branches, save/delete/cleanup orchestration, LRU eviction maths, getCacheStats failure path (ThumbnailCacheRepositoryImplTest) |
| DuplicateHashCacheEntity | `data/local/db/DuplicateHashCacheEntity.kt` | 25 | no | out | no declared logic (data holder/marker) |
| EncryptedString | `data/local/db/EncryptedStringConverter.kt` | 9 | no | out | no declared logic (data holder/marker) |
| FavoritesEntity | `data/local/db/FavoritesEntity.kt` | 28 | no | out | no declared logic (data holder/marker) |
| FileChecksumRow | `data/local/db/FileMetadataCacheDao.kt` | 100 | no | out | no declared logic (data holder/marker) |
| FileMetadataCacheEntity | `data/local/db/FileMetadataCacheEntity.kt` | 103 | no | out | no declared logic (data holder/marker) |
| NetworkCredentialsEntity | `data/local/db/NetworkCredentialsEntity.kt` | 136 | no | out | data/entity holder |
| PendingRevocationEntity | `data/local/db/PendingRevocationEntity.kt` | 28 | no | out | no declared logic (data holder/marker) |
| PlaybackPositionEntity | `data/local/db/PlaybackPositionEntity.kt` | 19 | no | out | no declared logic (data holder/marker) |
| ResourceEntity | `data/local/db/ResourceEntity.kt` | 104 | no | out | no declared logic (data holder/marker) |
| ResourceFtsEntity | `data/local/db/ResourceFtsEntity.kt` | 12 | no | out | no declared logic (data holder/marker) |
| ScheduledOperationEntity | `data/local/db/ScheduledOperationEntity.kt` | 84 | no | out | no declared logic (data holder/marker) |
| StereoFormatOverrideEntity | `data/local/db/StereoFormatOverrideEntity.kt` | 25 | no | out | no declared logic (data holder/marker) |
| StreamingCacheEntry | `data/local/db/StreamingCacheEntry.kt` | 50 | no | out | no declared logic (data holder/marker) |
| ThumbnailCacheEntity | `data/local/db/ThumbnailCacheEntity.kt` | 49 | no | out | no declared logic (data holder/marker) |
| ReviewEligibilitySnapshot | `data/local/preferences/ReviewEligibilityDataStore.kt` | 79 | no | out | no declared logic (data holder/marker) |
| AppSettings | `data/local/preferences/SettingsManager.kt` | 291 | no | out | no declared logic (data holder/marker) |
| StagedKind | `data/local/staging/StagedKind.kt` | 18 | no | out | no declared logic (data holder/marker) |
| CachedAudioData | `data/repository/AudioMetadataCacheRepository.kt` | 155 | yes | out | no declared logic (data holder/marker) |
| AudioMetadataSaveData | `data/repository/AudioMetadataCacheRepository.kt` | 155 | yes | out | no declared logic (data holder/marker) |
| TestCredential | `data/repository/TestCredentialModels.kt` | 35 | no | out | no declared logic (data holder/marker) |
| TestCredentialsConfig | `data/repository/TestCredentialModels.kt` | 35 | no | out | no declared logic (data holder/marker) |

## Phase 05 - data network & remote sources

In-scope: 58 (already have tests: 58) | Out: 56 | Total classes: 114

Batch 1 (2026-05-29): 23 new `*Test.kt` classes (191 methods, all green via per-class XML) covering
error/classification helpers, the S0067 lifecycle gate/tracker/registry/diagnostics layer, the two
abstract connection pools, SMB pool/health/metrics/playback trackers, and FTP/SFTP command + scanner
helpers. No real sockets - SMBJ/SSHJ/Commons-Net clients are mocked or unconnected.
4 rows reclassified `in`->`out`: IdleDisconnectPolicy (pure interface), SmbResetCallback (callback
interface), NetworkLifecycleBootstrapper (android-bound Looper/Handler/ProcessLifecycleOwner),
ITunesApiService (pure Retrofit interface).

Batch 2 (final, 2026-05-29): 18 new `*Test.kt` classes (113 methods, all green via per-class XML)
covering the remaining handlers/operations/scanners/datasource factories. SMBJ DiskShare/File,
SSHJ/JSch ChannelSftp, Commons-Net FTPClient, and Media3 DataSource.Factory are all mocked; the
SmbConnectionManager.withConnection lambda is stubbed to run in-process - no real sockets.
17 rows flipped `no`->`yes` (in): FtpFileOperationHandler, SftpFileOperationHandler, SmbClient,
SmbConnectionManager, SmbFileMutationCoordinator, SmbFileOperations, SmbMediaScanCoordinator,
SmbShareDiscoveryHelper, SmbDirectoryScanner, BdTsStripDataSourceFactory, FtpDataSourceFactory,
SmbDataSourceFactory, FtpConnectedOperations, FtpExoPlayerPool, FtpStandaloneOperations,
SftpConnectionTester, SftpMediaScanner.
4 rows reclassified `in`->`out`: SmbFileOperationHandler (rename routes through a private,
non-injectable AtomicFileOperationStrategy chain), SmbMediaScanner (constructor builds
android.util.LruCache fields - fails on plain JVM; ExifInterface/MediaMetadataRetriever enrichment),
FtpDataSource and SmbDataSource (open()/read() gated behind PermissionHelper + Android Uri + live
protocol clients; read() clamp/retry logic unreachable without a socket-backed open()).
Phase 05 in-scope rows remaining: 0.

| Class | Path | LOC | tests | scope | reason |
|-------|------|----:|:-----:|:-----:|--------|
| BaseConnectionPool | `data/network/BaseConnectionPool.kt` | 273 | yes | in | pooled-with-recovery: reuse, dead-recreate, cancellation-keep, fresh-fail-remove, critical-reset, clear/forceFullReset (test subclass, no sockets) |
| ConnectionThrottleManager | `data/network/ConnectionThrottleManager.kt` | 592 | yes | in | tier (SLOW default), threads/buffer/user-limit caches, video-player flag covered; speed-fed FAST/MEDIUM tiers unreachable - setLastSpeedMbps throws UnknownFormatConversionException on JVM (malformed Timber format string, prod bug) |
| FtpFileOperationHandler | `data/network/FtpFileOperationHandler.kt` | 553 | yes | in | executeRename (parse/exists/rename/error mapping) + parseFtpPath covered via mocked FtpClient + credentials repo; copy/move/SAF inherit Android (Uri/MediaStoreNotifier/strategies) |
| IdleDisconnectPolicy | `data/network/IdleDisconnectPolicy.kt` | 9 | no | out | reclassified: pure interface (no logic); impl IdleDisconnectPolicyImpl already tested |
| IdleDisconnectPolicyImpl | `data/network/IdleDisconnectPolicyImpl.kt` | 98 | yes | in |  |
| SftpFileOperationHandler | `data/network/SftpFileOperationHandler.kt` | 456 | yes | in | executeRename (parse/exists/rename/SftpOperationFailure mapping) covered via mocked SftpClient + credentials repo; cross-protocol/SAF move inherit Android (Uri/MediaStoreNotifier/strategies) |
| SftpOperationMessageResolver | `data/network/SftpOperationMessageResolver.kt` | 86 | yes | in | copy-completed precedence + per-category resource/log-label mapping (SftpOperationMessageResolverTest) |
| SmbBackgroundLifecycleManager | `data/network/SmbBackgroundLifecycleManager.kt` | 36 | yes | in | onStop -> closeUiConnections delegation (MockK manager) |
| SmbClient | `data/network/SmbClient.kt` | 648 | yes | in | listFiles inline traversal/dot-skip/error wrap + testConnection retry/backoff classification (retriable timeout vs non-retriable) covered via mocked SmbConnectionManager; pooled connect paths need live sockets |
| SmbClientErrorFormatter | `data/network/SmbClientErrorFormatter.kt` | 135 | yes | in | message classification matrix + diagnostic blob (anonymous username, port) |
| SmbConnectionHealthProbe | `data/network/SmbConnectionHealthProbe.kt` | 129 | yes | in | classify() cause-chain DeadReason mapping + depth limit; isAlive (SMBJ tri-layer getters) not covered |
| SmbConnectionManager | `data/network/SmbConnectionManager.kt` | 972 | yes | in | getClient tiered SMBClient caching (same instance per tier) + no-op-safe setResetCallback/clearConnectionPool/invalidateExoPlayerConnection/close covered; pooled connect/retry/health paths need live SMBJ sockets |
| SmbResetCallback | `data/network/SmbConnectionManager.kt` | 972 | no | out | no declared logic (callback interface, single onAutoReset method) |
| SmbConnectionPool | `data/network/SmbConnectionPool.kt` | 190 | yes | in | map ownership: put/get/remove/snapshot/hasActiveConnectionForServer/removeMatchingAndCloseAsync count (relaxed SMBJ mocks); async cascade-close (internal IO scope) not asserted |
| SmbErrorClassifier | `data/network/SmbErrorClassifier.kt` | 143 | yes | in | isNonRetriable/isTransportOrBrokenPipe cause-chain + getUserFriendlyMessage; checkConnectivity (real socket) excluded |
| SmbFileMutationCoordinator | `data/network/SmbFileMutationCoordinator.kt` | 196 | yes | in | rename invalid-char/target-exists/bare-vs-slashed-newPath/open-fail + move source-missing/dest-exists/outer-error covered via mocked DiskShare (withConnection stubbed) |
| SmbFileOperationHandler | `data/network/SmbFileOperationHandler.kt` | 692 | no | out | reclassified: executeRename routes through a constructor-instantiated AtomicFileOperationStrategy(SmbOperationStrategy) chain (private field, not injectable) and the result mapping needs that strategy's outcome; download/copy/move inherit BaseFileOperationHandler + MediaStoreNotifier/Uri - no JVM-isolable branch without spying private fields |
| SmbFileOperations | `data/network/SmbFileOperations.kt` | 628 | yes | in | download/readFileBytes(chunked+cap)/readPartialFile(clamp/empty)/readFileBytesRange(too-large)/upload/delete/deleteDirectory/rename/move/createDirectory/exists/getFileInfo branches + error wrapping covered via mocked DiskShare+File (withConnection stubbed) |
| SmbMediaScanCoordinator | `data/network/SmbMediaScanCoordinator.kt` | 229 | yes | in | scan/chunked/paged/count result mapping (toPublic), recursive-vs-non-recursive dispatch, onComplete progress, error wrapping covered via mocked SmbDirectoryScanner + withConnection |
| SmbMediaScanner | `data/network/SmbMediaScanner.kt` | 842 | no | out | reclassified: constructor instantiates android.util.LruCache fields (exifCache/videoMetadataCache) - construction fails on plain JVM; enrichment uses ExifInterface/MediaMetadataRetriever; scanFolder unreachable without Robolectric |
| SmbPlaybackConnectionTracker | `data/network/SmbPlaybackConnectionTracker.kt` | 118 | yes | in | state transitions + watchdog same-file lockout / server escalation / window scoping / clear paths |
| SmbReconnectMetric | `data/network/SmbReconnectMetric.kt` | 51 | yes | in | ring-buffer window/capacity eviction + threshold + notify-cooldown branches (injected clock); no observable return - asserts no-crash matrix |
| SmbShareDiscoveryHelper | `data/network/SmbShareDiscoveryHelper.kt` | 241 | yes | in | performTestConnection share-path: statistics (folder/media/total counts via MediaExtensions), missing-subfolder hard-fail, path-warning catch, root scan covered via mocked DiskShare; empty-shareName listShares hits live socket enumeration (out) |
| BdTsStripDataSource | `data/network/datasource/BdTsStripDataSource.kt` | 93 | yes | in |  |
| BdTsStripDataSourceFactory | `data/network/datasource/BdTsStripDataSourceFactory.kt` | 11 | yes | in | createDataSource wraps upstream factory output in BdTsStripDataSource (mocked Media3 DataSource.Factory) |
| FtpDataSource | `data/network/datasource/FtpDataSource.kt` | 260 | no | out | reclassified: open() gated behind PermissionHelper (Android) + Android Uri.decode + live Commons-Net FTPClient (MLST/SIZE/retrieveFileStream); read()'s clamp/EOF logic is unreachable without open() populating private stream/bytesRemaining (no setter) |
| FtpDataSourceFactory | `data/network/datasource/FtpDataSource.kt` | 260 | yes | in | createDataSource constructs an FtpDataSource (BaseDataSource ctor JVM-safe; collaborators mocked) |
| SftpDataSourceFactory | `data/network/datasource/SftpDataSource.kt` | 329 | yes | in |  |
| SftpDataSource | `data/network/datasource/SftpDataSource.kt` | 329 | yes | in |  |
| SmbDataSourceFactory | `data/network/datasource/SmbDataSource.kt` | 616 | yes | in | createDataSource constructs an SmbDataSource (BaseDataSource ctor + shared watchdog executor JVM-safe; collaborators mocked) |
| SmbDataSource | `data/network/datasource/SmbDataSource.kt` | 616 | no | out | reclassified: open()/read() gated behind PermissionHelper + Android Uri + live SMBJ DiskShare/File and a watchdog Future; pure helpers (isInterruptionOrTimeout/resolveSmbPath) are private and Uri-bound, with no JVM-reachable entry to read()'s clamp/retry logic without a real share |
| TsPacketFormatDetector | `data/network/datasource/TsPacketFormatDetector.kt` | 27 | yes | in |  |
| NetworkErrorClassifier | `data/network/exceptions/NetworkErrorClassifier.kt` | 191 | yes | in |  |
| NetworkErrorMessageMapper | `data/network/exceptions/NetworkErrorMessageMapper.kt` | 94 | yes | in |  |
| RetryPolicy | `data/network/exceptions/RetryPolicy.kt` | 90 | yes | in |  |
| SmbDirectoryScanner | `data/network/helpers/SmbDirectoryScanner.kt` | 529 | yes | in | non-recursive/recursive-with-limit/offset-limit/non-recursive-offset traversal + extension filter, dir/dot/trash skip, includeDirectories, maxFiles, path-join, count recursive/non-recursive + error swallow covered via mocked DiskShare.list |
| CloudConnectionGate | `data/network/lifecycle/CloudConnectionGate.kt` | 64 | yes | in | protocol, acquire-throws, closeFor no-op (both consumers), lastRecreateMs delegation (ConnectionGatesTest) |
| CloudRecreateTracker | `data/network/lifecycle/CloudRecreateTracker.kt` | 21 | yes | in | record/lookup + keyForProvider format (RecreateTrackersTest) |
| ConnectionDiagnostics | `data/network/lifecycle/ConnectionDiagnostics.kt` | 80 | yes | in | InstabilityWarning threshold/window/per-key emission via SharedFlow collector; success/failure no-emit |
| ConnectionGateRegistry | `data/network/lifecycle/ConnectionGateRegistry.kt` | 29 | yes | in | register/gateFor/overwrite/all |
| FtpConnectionGate | `data/network/lifecycle/FtpConnectionGate.kt` | 64 | yes | in | closeFor UI cleans idle / worker no-op / swallows failure; lastRecreateMs delegation (ConnectionGatesTest) |
| FtpRecreateTracker | `data/network/lifecycle/FtpRecreateTracker.kt` | 21 | yes | in | record/lookup (RecreateTrackersTest) |
| NetworkConnectionGate | `data/network/lifecycle/NetworkConnectionGate.kt` | 138 | yes | in | withRetry default: success/transient-retry/non-transient/double-transient/cancellation + release accounting (FakeGate, NetworkConnectionGateWithRetryTest) |
| TransientFailure | `data/network/lifecycle/NetworkConnectionGate.kt` | 138 | yes | in | classify() class+message reasons, class-beats-message, cause-chain, depth limit (TransientFailureTest) |
| NetworkLifecycleBootstrapper | `data/network/lifecycle/NetworkLifecycleBootstrapper.kt` | 127 | no | out | reclassified: android-bound (Looper/Handler/ProcessLifecycleOwner + main-thread latch dispatch); ensureInitialized has no JVM-reachable branch |
| NetworkProtocol | `data/network/lifecycle/NetworkProtocol.kt` | 29 | yes | in | fromUri scheme mapping + case-insensitivity + null cases |
| SftpConnectionGate | `data/network/lifecycle/SftpConnectionGate.kt` | 68 | yes | in | closeFor UI disconnects pool / worker no-op / swallows failure (ConnectionGatesTest) |
| SftpRecreateTracker | `data/network/lifecycle/SftpRecreateTracker.kt` | 21 | yes | in | record/lookup (RecreateTrackersTest) |
| SmbConnectionGate | `data/network/lifecycle/SmbConnectionGate.kt` | 62 | yes | in | protocol, acquire-throws, closeFor UI->closeUiConnections / worker no-op, lastRecreateMs delegation (ConnectionGatesTest) |
| SmbRecreateTracker | `data/network/lifecycle/SmbRecreateTracker.kt` | 35 | yes | in | record/lookup + keyForServer/keyForShare formats + key independence (RecreateTrackersTest) |
| BaseConnectionPool | `data/network/pool/BaseConnectionPool.kt` | 206 | yes | in | withConnection reuse/stale-recreate/distinct-keys + cleanupIdleConnections/clearAllConnections/getPoolSize (test subclass, no sockets) |
| ITunesApiService | `data/remote/ITunesApiService.kt` | 55 | no | out | reclassified: pure Retrofit interface + @Keep response holders (no logic) |
| FtpClient | `data/remote/ftp/FtpClient.kt` | 370 | yes | in |  |
| FtpBoundedReadResult | `data/remote/ftp/FtpCommandUtils.kt` | 135 | yes | in | safeCompletePendingCommand NPE->IOException + readBoundedAndAbort EOF/cap/abort-swallow/complete-fail/stream-error (FtpCommandUtilsTest) |
| FtpConnectedOperations | `data/remote/ftp/FtpConnectedOperations.kt` | 508 | yes | in | not-connected guard, listFiles dot-filter + passive→active fallback, listFilesWithMetadata(Paged) limit/offset, readFileBytes full-read + completePendingCommand-fail + null-stream covered via injected getClient lambda + mocked FTPClient |
| FtpDirectoryScanner | `data/remote/ftp/FtpDirectoryScanner.kt` | 125 | yes | in | single-level filter, recursion, path-join, paged offset/limit, passive->active fallback (mocked FTPClient/FTPFile) |
| FtpEncodingSupport | `data/remote/ftp/FtpEncodingSupport.kt` | 52 | yes | in | applyUtf8Encoding sets control encoding (unconnected FTPClient); enableUtf8Mode (live OPTS command) not covered |
| FtpExoPlayerPool | `data/remote/ftp/FtpExoPlayerPool.kt` | 165 | yes | in | releaseExoPlayerConnection (completePendingCommand + connected-only logout/disconnect, null-tolerance, exception-swallow) + cleanupIdleFtpConnections empty-pool no-op covered via mocked FTPClient; getConnectionForExoPlayer opens a real socket (out) |
| FtpMediaScanner | `data/remote/ftp/FtpMediaScanner.kt` | 576 | yes | in |  |
| FtpStandaloneOperations | `data/remote/ftp/FtpStandaloneOperations.kt` | 459 | yes | in | ensureRemoteDirectoryExists (empty no-op, per-segment mkdir order, exception swallow) covered via mocked FTPClient; all other methods open a fresh socket-backed FTPClient internally (out) |
| PinnedHostKeyRepository | `data/remote/sftp/PinnedHostKeyRepository.kt` | 94 | yes | in | constructor canonical guard, SHA256 match OK / mismatch CHANGED / null / empty, inert read-only stubs |
| SftpClient | `data/remote/sftp/SftpClient.kt` | 736 | yes | in |  |
| SftpConnectionPool | `data/remote/sftp/SftpConnectionPool.kt` | 631 | yes | in |  |
| SftpConnectionTester | `data/remote/sftp/SftpConnectionTester.kt` | 154 | yes | in | ensureDirectoryExists (exists-return, parent-chain mkdir, concurrent-create re-probe swallow, rethrow on double-fail) covered via mocked ChannelSftp + testConnectionWithPrivateKey malformed-key failure (no socket); live JSch connect paths (out) |
| SftpMediaScanner | `data/remote/sftp/SftpMediaScanner.kt` | 518 | yes | in | scanFolder permission-gate throw, invalid-path empty, media-type filter + hidden/dir/trash skip, all-files TEXT fallback, size filter, list-failure→IOException covered via mockkObject(PermissionHelper) + real withThrottle + mocked SftpClient.listFiles (no android.util.LruCache fields unlike SmbMediaScanner) |
| SftpOperationFailure | `data/remote/sftp/SftpOperationFailure.kt` | 112 | yes | in | fromThrowable category mapping (PERMISSION_DENIED/GENERIC/TRANSIENT) + cause-chain extraction; fromStreamCloseThrowable expected-close vs transient matrix |
| DeadReason | `data/network/SmbConnectionHealthProbe.kt` | 129 | no | out | no declared logic (data holder/marker) |
| ConnectionConsumer | `data/network/SmbConnectionPool.kt` | 190 | no | out | no declared logic (data holder/marker) |
| PooledConnection | `data/network/SmbConnectionPool.kt` | 190 | no | out | no declared logic (data holder/marker) |
| SmbPlaybackErrorCategory | `data/network/SmbErrorClassifier.kt` | 143 | no | out | no declared logic (data holder/marker) |
| TsPacketFormat | `data/network/datasource/TsPacketFormat.kt` | 8 | no | out | no declared logic (data holder/marker) |
| NetworkFileNotFoundException | `data/network/exceptions/NetworkExceptions.kt` | 74 | no | out | no declared logic (data holder/marker) |
| NetworkTimeoutException | `data/network/exceptions/NetworkExceptions.kt` | 74 | no | out | no declared logic (data holder/marker) |
| NetworkAccessDeniedException | `data/network/exceptions/NetworkExceptions.kt` | 74 | no | out | no declared logic (data holder/marker) |
| NetworkException | `data/network/exceptions/NetworkExceptions.kt` | 74 | no | out | no declared logic (data holder/marker) |
| NetworkServerErrorException | `data/network/exceptions/NetworkExceptions.kt` | 74 | no | out | no declared logic (data holder/marker) |
| NetworkConnectionLostException | `data/network/exceptions/NetworkExceptions.kt` | 74 | no | out | no declared logic (data holder/marker) |
| NetworkRateLimitException | `data/network/exceptions/NetworkExceptions.kt` | 74 | no | out | no declared logic (data holder/marker) |
| NetworkUnsupportedOperationException | `data/network/exceptions/NetworkExceptions.kt` | 74 | no | out | no declared logic (data holder/marker) |
| LocalNetworkPermissionDeniedException | `data/network/exceptions/NetworkExceptions.kt` | 74 | no | out | no declared logic (data holder/marker) |
| WifiRequiredException | `data/network/exceptions/NetworkExceptions.kt` | 74 | no | out | no declared logic (data holder/marker) |
| ErrorPropagatingPipedInputStream | `data/network/glide/ErrorPropagatingPipedInputStream.kt` | 37 | no | out | Glide integration glue |
| NetworkFileData | `data/network/glide/NetworkFileData.kt` | 57 | no | out | Glide integration glue |
| NetworkFileDataFetcherPassthrough | `data/network/glide/NetworkFileDataPassthroughModelLoader.kt` | 77 | no | out | Glide integration glue |
| NetworkFileDataPassthroughModelLoader | `data/network/glide/NetworkFileDataPassthroughModelLoader.kt` | 77 | no | out | Glide integration glue |
| NetworkFileModelLoader | `data/network/glide/NetworkFileModelLoader.kt` | 694 | no | out | Glide integration glue |
| NetworkFileDataFetcher | `data/network/glide/NetworkFileModelLoader.kt` | 694 | no | out | Glide integration glue |
| NetworkFileModelLoaderFactory | `data/network/glide/NetworkFileModelLoader.kt` | 694 | no | out | Glide integration glue |
| NetworkFileModelLoaderEntryPoint | `data/network/glide/NetworkFileModelLoader.kt` | 694 | no | out | Glide integration glue |
| NetworkMediaDataSource | `data/network/glide/NetworkMediaDataSource.kt` | 503 | no | out | Glide integration glue |
| NetworkResourceKey | `data/network/glide/NetworkResourceKey.kt` | 32 | yes | out | Glide integration glue |
| NetworkThumbnailExtractionPolicy | `data/network/glide/NetworkThumbnailExtractionPolicy.kt` | 49 | no | out | Glide integration glue |
| NetworkFileDataInputStream | `data/network/glide/NetworkVideoFrameDecoder.kt` | 458 | no | out | Glide integration glue |
| NetworkVideoFrameDecoder | `data/network/glide/NetworkVideoFrameDecoder.kt` | 458 | no | out | Glide integration glue |
| ExtractionOutcome | `data/network/glide/NetworkVideoFrameDecoder.kt` | 458 | no | out | Glide integration glue |
| SafeByteBuffer | `data/network/glide/SafeByteBuffer.kt` | 11 | no | out | Glide integration glue |
| SafeByteBufferBitmapDecoder | `data/network/glide/SafeByteBufferBitmapDecoder.kt` | 126 | no | out | Glide integration glue |
| SafeByteBufferEncoder | `data/network/glide/SafeByteBufferEncoder.kt` | 22 | no | out | Glide integration glue |
| TransientReason | `data/network/glide/TransientReason.kt` | 21 | no | out | Glide integration glue |
| VideoExtractionFailurePersistence | `data/network/glide/VideoExtractionFailurePersistence.kt` | 67 | no | out | Glide integration glue |
| CloudTokenHandle | `data/network/lifecycle/CloudConnectionGate.kt` | 64 | no | out | no declared logic (data holder/marker) |
| ConsumerType | `data/network/lifecycle/ConsumerType.kt` | 17 | no | out | no declared logic (data holder/marker) |
| ConnectionKey | `data/network/model/SmbModels.kt` | 44 | no | out | no declared logic (data holder/marker) |
| SmbResult | `data/network/model/SmbModels.kt` | 44 | no | out | no declared logic (data holder/marker) |
| SmbFileInfo | `data/network/model/SmbModels.kt` | 44 | no | out | no declared logic (data holder/marker) |
| SmbConnectionInfo | `data/network/model/SmbModels.kt` | 44 | no | out | no declared logic (data holder/marker) |
| ITunesTrack | `data/remote/ITunesApiService.kt` | 55 | no | out | no declared logic (data holder/marker) |
| ITunesSearchResponse | `data/remote/ITunesApiService.kt` | 55 | no | out | no declared logic (data holder/marker) |
| HostKeyMismatchException | `data/remote/sftp/PinnedHostKeyRepository.kt` | 94 | no | out | no declared logic (data holder/marker) |
| SftpFileListing | `data/remote/sftp/SftpClient.kt` | 736 | yes | out | no declared logic (data holder/marker) |
| SftpFileAttributes | `data/remote/sftp/SftpClient.kt` | 736 | yes | out | no declared logic (data holder/marker) |
| ChannelPurpose | `data/remote/sftp/SftpConnectionPool.kt` | 631 | yes | out | no declared logic (data holder/marker) |
| SftpDownloadExhaustedException | `data/remote/sftp/SftpDownloadExhaustedException.kt` | 7 | no | out | no declared logic (data holder/marker) |
| SftpFailureCategory | `data/remote/sftp/SftpOperationFailure.kt` | 112 | no | out | no declared logic (data holder/marker) |

## Phase 06 - data transfer, link/auth, cloud

In-scope: 112 (already have tests: 19) | Out: 35 | Total classes: 147

Batch (2026-05-29): 27 new `*Test.kt` classes (214 methods, all green via per-class XML) covering the
pure-logic cloud Utils (CloudPathParser, CloudFileOperationPathUtils, GoogleDrive/OneDrive/Dropbox
RestClientUtils), all five protocol file hashers, the QuickVerifier dispatcher + Local/Smb/Sftp/Cloud
verifiers, the cross-protocol TransferStrategy.supports matrix + StrategyUtils + LocalOperationStrategy
(TemporaryFolder), FileAccess (Smb/Sftp/Ftp/Local), CloudProgressAdapter, CloudFileHandle,
UnifiedFileOperationHandler, the streaming ManifestDrmDetector/StreamingCacheCleaner/StreamingDownloadStrategy
(src/streamingEnabled, mounted into standard), LinkDownloadCookieJar, DirectFileExtractionStrategy,
InputBindingRepository, PermissionRegistryRepositoryImpl/ContextualRationaleRepositoryImpl, and
GoogleDomainBrowserLauncher. No real network/cloud-SDK/sockets - OkHttp Call, Dropbox/Drive/MSAL/Graph
clients, SMBJ/SSHJ/Commons-Net clients, the NetworkCredentialsRepository entity, and android.net.Uri are
all mocked or Robolectric-backed. ~40 in-scope rows reclassified `in`->`out`: pure interfaces
(CloudStorageClient, FileAccess, FileOperationStrategy, TransferStrategy, LocalSink, LocalDestinationWriter,
InteractiveCloudAuthenticator), the Cloud/Ftp/Sftp/Smb OperationStrategy + Smb/LocalTransferProvider +
BaseFileOperationHandler + UniversalFileOperationHandler (Uri/MediaStoreNotifier/credentials.password
CryptoHelper dominated; their exists() delegates are exercised via the QuickVerifiers), the big REST clients
(DropboxClient/GoogleDriveRestClient/OneDriveRestClient - pure mapping extracted to the tested *Utils), the
auth coordinators/plugins (CredentialManager/MSAL/AppAuth + Activity), GoogleDriveCredentialsManager
(EncryptedSharedPreferences/Keystore), CloudMediaScanner/CloudToCloudTransferHelper/GoogleDriveHttpClient/
MultipartUploader (live cloud SDK/network), CctAvailabilityChecker (PackageManager/CustomTabsClient),
DefaultsMapLoader (assets), InputBindingDao (thin Room DAO), MediaStoreLocalDestinationWriter/LinkDownloadWriter
(MediaStore), InvisibleWebViewExtractionStrategy (WebView), Media3SegmentDownloader/MediaMuxerRemuxer
(Media3/MediaMuxer), CloudDataSource(+Factory), NetworkCredentialsResolver (credential.password CryptoHelper).
Phase 06 in-scope rows remaining: 0 (the 7 `data/link/nolegal/*` extraction strategies are Phase 07).

| Class | Path | LOC | tests | scope | reason |
|-------|------|----:|:-----:|:-----:|--------|
| CctAvailabilityChecker | `data/browser/CctAvailabilityChecker.kt` | 42 | no | out | reclassified: android-bound (PackageManager.queryIntentActivities + CustomTabsClient.getPackageName); no JVM-reachable branch |
| GoogleDomainBrowserLauncher | `data/browser/GoogleDomainBrowserLauncher.kt` | 56 | yes | in | routeAuthUrl host routing (Google→launch, non-Google→fallback) + CctUnavailableException when no CCT browser covered (Robolectric Uri, mocked CctAvailabilityChecker); successful CustomTabsIntent launch needs an Activity (instrumentation) |
| GoogleDomainMatcher | `data/browser/GoogleDomainMatcher.kt` | 32 | yes | in |  |
| CloudAuthStateMachine | `data/cloud/CloudAuthStateMachine.kt` | 190 | yes | in |  |
| CloudAuthenticationHelper | `data/cloud/CloudAuthenticationHelper.kt` | 125 | yes | in |  |
| CloudFileOperationHandler | `data/cloud/CloudFileOperationHandler.kt` | 979 | yes | in |  |
| CloudFileOperationPathUtils | `data/cloud/CloudFileOperationPathUtils.kt` | 81 | yes | in | scheme normalize, ResourceType classify, network-path detect, sftp/ftp remote-path extract, getMimeType (pure JVM) |
| CloudMediaScanner | `data/cloud/CloudMediaScanner.kt` | 405 | no | out | reclassified: scanning orchestration over live CloudStorageClient SDKs + Uri/MediaFile mapping; pure CloudFile→MediaFile mapping lives in the *Utils (tested) |
| CloudPathParser | `data/cloud/CloudPathParser.kt` | 111 | yes | in | provider-alias resolution, per-provider fileId encoding, scheme normalize, validation (pure JVM) |
| CloudStorageClient | `data/cloud/CloudStorageClient.kt` | 284 | no | out | reclassified: pure interface; only TransferProgress.percentage holder logic (data holder/marker) |
| CloudToCloudTransferHelper | `data/cloud/CloudToCloudTransferHelper.kt` | 179 | no | out | reclassified: orchestrates live cloud clients (download→upload via temp stream) + provider dispatch; no JVM-isolable transform |
| DropboxAuthPlugin | `data/cloud/DropboxAuthPlugin.kt` | 66 | no | out | reclassified: Dropbox SDK Auth + Activity/Intent OAuth launch glue; no JVM branch |
| DropboxClient | `data/cloud/DropboxClient.kt` | 956 | no | out | reclassified: Dropbox SDK (DbxClientV2) + Context + OkHttp network; all pure mapping/retry/error-classify extracted to DropboxClientUtils (tested) |
| DropboxClientUtils | `data/cloud/DropboxClientUtils.kt` | 223 | yes | in | error classification, credential JSON round-trip, Metadata→CloudFile, MIME guess, withRetry branching (Robolectric org.json/Build; SDK mocked) |
| GoogleDriveAuthCoordinator | `data/cloud/GoogleDriveAuthCoordinator.kt` | 232 | no | out | reclassified: CredentialManager + Activity OAuth coordination (Android UI/identity); no JVM-reachable branch |
| GoogleDriveAuthPlugin | `data/cloud/GoogleDriveAuthPlugin.kt` | 68 | no | out | reclassified: thin Activity-bound auth launcher glue (CredentialManager); no JVM branch |
| GoogleDriveBrowserAuthManager | `data/cloud/GoogleDriveBrowserAuthManager.kt` | 324 | no | out | reclassified: AppAuth browser OAuth + Activity/Intent + EncryptedSharedPreferences; Android/instrumentation-bound |
| GoogleDriveInteractiveSignInCoordinator | `data/cloud/GoogleDriveInteractiveSignInCoordinator.kt` | 82 | no | out | reclassified: Activity-result + CredentialManager interactive sign-in glue; no JVM branch |
| GoogleDriveRestClient | `data/cloud/GoogleDriveRestClient.kt` | 989 | no | out | reclassified: Drive REST over OkHttp + Context + GoogleAccountCredential; pure JSON mapping extracted to GoogleDriveRestClientUtils (tested) |
| GoogleDriveRestClientUtils | `data/cloud/GoogleDriveRestClientUtils.kt` | 57 | yes | in | Drive files.list JSON→CloudFile (folder marker, RFC-3339 time + malformed fallback, thumbnail/webView) (Robolectric org.json) |
| InteractiveCloudAuthenticator | `data/cloud/InteractiveCloudAuthenticator.kt` | 57 | no | out | reclassified: pure interface / Activity-bound launcher contract; no logic |
| NetworkCredentialsResolver | `data/cloud/NetworkCredentialsResolver.kt` | 292 | no | out | reclassified: credential ranking reads NetworkCredentialsEntity.password (CryptoHelper/AndroidKeystore) on every path; only extractSmbRemotePath is pure (single trivial string-split) |
| OneDriveAuthCoordinator | `data/cloud/OneDriveAuthCoordinator.kt` | 479 | no | out | reclassified: MSAL PublicClientApplication + Activity interactive/silent acquire-token (Android UI/identity) |
| OneDriveAuthPlugin | `data/cloud/OneDriveAuthPlugin.kt` | 45 | no | out | reclassified: thin MSAL Activity-bound auth launcher glue; no JVM branch |
| OneDriveRestClient | `data/cloud/OneDriveRestClient.kt` | 734 | no | out | reclassified: Graph REST over OkHttp + MSAL token + Context; pure JSON/account mapping extracted to OneDriveRestClientUtils (tested) |
| OneDriveRestClientUtils | `data/cloud/OneDriveRestClientUtils.kt` | 101 | yes | in | MSAL account JSON round-trip, cloud-item-ref strip, Graph DriveItem→CloudFile (folder facet, ISO-8601, nested thumbnail) (Robolectric org.json; IAccount mocked) |
| UnifiedCloudAuthManager | `data/cloud/UnifiedCloudAuthManager.kt` | 164 | no | out | reclassified: dispatches to the per-provider Activity-bound auth plugins (Android); no JVM-isolable branch |
| CloudDataSource | `data/cloud/datasource/CloudDataSource.kt` | 197 | no | out | reclassified: Media3 BaseDataSource open()/read() gated behind Android Uri + live CloudStorageClient stream; no JVM-reachable read path |
| CloudDataSourceFactory | `data/cloud/datasource/CloudDataSource.kt` | 197 | no | out | reclassified: thin Media3 DataSource.Factory that constructs a CloudDataSource (collaborators are Android/SDK); no branching value |
| GoogleDriveCredentialsManager | `data/cloud/helpers/GoogleDriveCredentialsManager.kt` | 133 | no | out | reclassified: EncryptedSharedPreferences (MasterKey/AndroidKeystore) lazy init - construction/access fails on plain JVM; per-account/legacy key logic is downstream of it (instrumentation) |
| GoogleDriveHttpClient | `data/cloud/helpers/GoogleDriveHttpClient.kt` | 207 | no | out | reclassified: OkHttp request execution against Drive endpoints + GoogleAccountCredential token; live-network, no JVM transform |
| GoogleDriveMultipartUploader | `data/cloud/helpers/GoogleDriveMultipartUploader.kt` | 71 | no | out | reclassified: builds + executes a multipart OkHttp upload request against Drive; live-network, no JVM-isolable branch |
| CloudFileHasher | `data/hash/CloudFileHasher.kt` | 54 | yes | in | provider→client select, null cloudItemId/provider guards, length mapping, CloudResult.Error propagation (clients mocked) |
| FtpFileHasher | `data/hash/FtpFileHasher.kt` | 46 | yes | in | FtpPathUtils parse + cred lookup + anonymous fallback + digest + error propagation (FtpClient/entity mocked, no CryptoHelper) |
| LocalFileHasher | `data/hash/LocalFileHasher.kt` | 54 | yes | in | md5Hex full/capped/boundary/multi-buffer + local-file read via TemporaryFolder; contentUri (Uri/ContentResolver) branch excluded |
| SftpFileHasher | `data/hash/SftpFileHasher.kt` | 48 | yes | in | SftpPathUtils parse + connection-info build (private key) + digest + error propagation (mocked) |
| SmbFileHasher | `data/hash/SmbFileHasher.kt` | 47 | yes | in | SmbPathUtils parse + SmbResult.Success digest / Error propagation / unparseable-path guard (mocked) |
| DefaultsMapLoader | `data/input/DefaultsMapLoader.kt` | 64 | no | out | reclassified: loadDefaults reads context.assets (Android) + Gson; loadChromeOsDefaults is a hardcoded static list (no testable logic) |
| InputBindingDao | `data/input/InputBindingDao.kt` | 31 | no | out | reclassified: thin Room DAO (only generated queries); covered via InputBindingRepositoryTest |
| InputBindingRepository | `data/input/InputBindingRepository.kt` | 123 | yes | in | default/override merge by (command,device) key, override-only append, device classification for merge-key + persisted entity (DAO/defaults mocked) |
| CandidateSelectionPolicy | `data/link/CandidateSelectionPolicy.kt` | 53 | yes | in |  |
| DirectFileExtractionStrategy | `data/link/DirectFileExtractionStrategy.kt` | 177 | yes | in | probe HEAD-success/ranged-GET fallback + MIME-whitelist/path-ext gating; open() block reasons (non-http/auth/MIME) + stream + Content-Disposition/url filename derivation (OkHttp Call mocked) |
| HtmlPageExtractionStrategy | `data/link/HtmlPageExtractionStrategy.kt` | 452 | yes | in |  |
| InvisibleWebViewExtractionStrategy | `data/link/InvisibleWebViewExtractionStrategy.kt` | 750 | no | out | reclassified: android.webkit.WebView lifecycle + JS-bridge interception on the main thread; needs instrumentation |
| LinkDownloadWriter | `data/link/LinkDownloadWriter.kt` | 252 | no | out | reclassified: android-bound (MediaStore/ContentValues/ContentUris + Environment + Uri on every write path); needs instrumentation |
| LinkUrlCanonicalizer | `data/link/LinkUrlCanonicalizer.kt` | 76 | yes | in |  |
| StreamingManifestSniffer | `data/link/StreamingManifestSniffer.kt` | 154 | yes | in |  |
| StructuredMediaSniffer | `data/link/StructuredMediaSniffer.kt` | 377 | yes | in |  |
| AccountIdentityExtractor | `data/link/auth/AccountIdentityExtractor.kt` | 55 | yes | in |  |
| AccountNameHintExtractor | `data/link/auth/AccountNameHintExtractor.kt` | 18 | yes | in |  |
| KnownAuthResources | `data/link/auth/KnownAuthResources.kt` | 75 | yes | in |  |
| EncryptedCookieStore | `data/link/cookie/EncryptedCookieStore.kt` | 397 | yes | in |  |
| LinkCookieDomainResolver | `data/link/cookie/LinkCookieDomainResolver.kt` | 19 | yes | in |  |
| LinkDownloadCookieJar | `data/link/cookie/LinkDownloadCookieJar.kt` | 64 | yes | in | loadForRequest: session-context precedence over store, HttpCookie→okhttp3.Cookie (host-only vs explicit domain), empty fallback, read-only saveFromResponse no-op (store/context mocked) |
| LinkDownloadSessionContext | `data/link/cookie/LinkDownloadSessionContext.kt` | 67 | yes | in |  |
| ArtStationExtractionStrategy | `data/link/nolegal/ArtStationExtractionStrategy.kt` | 108 | yes | in | covered in Phase 07 (src/testNoLegal) - dup row of catalog data/link** match |
| ChaquopyRuntimeHolder | `data/link/nolegal/ChaquopyRuntimeHolder.kt` | 54 | yes | in | covered in Phase 07 (src/testNoLegal) - dup row of catalog data/link** match |
| CookieFileWriter | `data/link/nolegal/CookieFileWriter.kt` | 96 | yes | in | covered in Phase 07 (src/testNoLegal) - dup row of catalog data/link** match |
| DailymotionExtractionStrategy | `data/link/nolegal/DailymotionExtractionStrategy.kt` | 162 | yes | in | covered in Phase 07 (src/testNoLegal) - dup row of catalog data/link** match |
| DeviantArtExtractionStrategy | `data/link/nolegal/DeviantArtExtractionStrategy.kt` | 167 | yes | in | covered in Phase 07 (src/testNoLegal) - dup row of catalog data/link** match |
| NewPipeOkHttpDownloader | `data/link/nolegal/NewPipeOkHttpDownloader.kt` | 75 | yes | in | covered in Phase 07 (src/testNoLegal) - dup row of catalog data/link** match |
| NewPipeSiteExtractionStrategy | `data/link/nolegal/NewPipeSiteExtractionStrategy.kt` | 254 | yes | in | covered in Phase 07 (src/testNoLegal) - dup row of catalog data/link** match |
| VimeoExtractionStrategy | `data/link/nolegal/VimeoExtractionStrategy.kt` | 131 | yes | in | covered in Phase 07 (src/testNoLegal) - dup row of catalog data/link** match |
| YtDlpExtractionStrategy | `data/link/nolegal/YtDlpExtractionStrategy.kt` | 621 | yes | in | covered in Phase 07 (src/testNoLegal) - dup row of catalog data/link** match |
| ManifestDrmDetector | `data/link/streaming/ManifestDrmDetector.kt` | 63 | yes | in | HLS EXT-X-KEY (non-NONE)/DASH ContentProtection detection, METHOD=NONE negative, non-200/network-failure→false (OkHttp Call mocked). Source set: src/streamingEnabled (mounted into standard) |
| Media3SegmentDownloader | `data/link/streaming/Media3SegmentDownloader.kt` | 150 | no | out | reclassified: Media3 SimpleCache/CacheWriter + ExoPlayer HLS/DASH parsing; needs Android/Media3 runtime. Source set: src/streamingEnabled |
| MediaMuxerRemuxer | `data/link/streaming/MediaMuxerRemuxer.kt` | 149 | no | out | reclassified: android.media.MediaMuxer + MediaExtractor sample-copy; instrumentation-only (covered by MediaMuxerRemuxerInstrumentationTest). Source set: src/streamingEnabled |
| StreamingCacheCleaner | `data/link/streaming/StreamingCacheCleaner.kt` | 45 | yes | in | session id, session-dir layout/creation, recursive cleanup, 20%-margin preflight free-space gate (TemporaryFolder). Source set: src/streamingEnabled |
| StreamingDownloadStrategy | `data/link/streaming/StreamingDownloadStrategy.kt` | 97 | yes | in | outcome projection: DrmBlocked, insufficient-cache NetworkError, Success/MuxFailed mapping, error→NetworkError+cleanup, cancellation propagate+cleanup (cacheDir=TemporaryFolder, collaborators mocked). Source set: src/streamingEnabled |
| ContextualRationaleRepositoryImpl | `data/permissions/ContextualRationaleRepositoryImpl.kt` | 27 | yes | in | per-permission shown-flag persistence (default false, true after markShown, independent keys) via real SharedPreferences (Robolectric) |
| PermissionRegistryRepositoryImpl | `data/permissions/PermissionRegistryRepositoryImpl.kt` | 156 | yes | in | SDK-range (minSdk/maxSdk) filtering of the static entry list + group derivation/ordering (Robolectric pins Build.VERSION.SDK_INT=33) |
| AtomicFileOperationStrategy | `data/transfer/AtomicFileOperationStrategy.kt` | 395 | yes | in |  |
| BaseFileOperationHandler | `data/transfer/BaseFileOperationHandler.kt` | 436 | no | out | reclassified: download/copy/move paths dominated by MediaStoreNotifier + Uri + Context.getString; no JVM-isolable branch |
| CloudFileHandle | `data/transfer/CloudFileHandle.kt` | 20 | yes | in | getName/getPath/getAbsolutePath/length overrides surface display-name + cloud path + cached size (not File basename) |
| CloudProgressAdapter | `data/transfer/CloudProgressAdapter.kt` | 38 | yes | in | adaptCloudProgress: null short-circuit, interval throttling, forwarded byte/total across successive intervals (TestScope) |
| FileAccess | `data/transfer/FileAccess.kt` | 14 | no | out | reclassified: pure interface (no logic) |
| FileOperationStrategy | `data/transfer/FileOperationStrategy.kt` | 226 | no | out | reclassified: pure interface + DirectoryInfo holder (no logic) |
| LocalTransferProvider | `data/transfer/LocalTransferProvider.kt` | 395 | no | out | reclassified: MediaStoreNotifier + Uri + Environment + Context on every write path; needs instrumentation |
| SmbTransferProvider | `data/transfer/SmbTransferProvider.kt` | 342 | no | out | reclassified: every public op routes through getConnectionInfo → credentials.password (CryptoHelper/AndroidKeystore) + MediaStoreNotifier on local-dest success |
| TempFileNamingStrategy | `data/transfer/TempFileNamingStrategy.kt` | 113 | yes | in |  |
| TransferStrategy | `data/transfer/TransferStrategy.kt` | 115 | no | out | reclassified: pure interface (suspend copy/move + supports contract; supports() impls tested per-strategy) |
| UnifiedFileOperationHandler | `data/transfer/UnifiedFileOperationHandler.kt` | 564 | yes | in | protocol routing, cross-protocol copy (download→upload via temp + cleanup), move=copy+soft-delete with rollback, rename/trash path build, create-dir/text-file delegation, cross-protocol dir guard (collaborators mocked) |
| UniversalFileOperationHandler | `data/transfer/UniversalFileOperationHandler.kt` | 207 | no | out | reclassified: PathUtils.safeParseUri (android.net.Uri) on every path + context.getString(R.string..) for failure messages; strategy/provider selection is downstream of Uri |
| LocalDestinationClassifier | `data/transfer/local/LocalDestinationClassifier.kt` | 121 | yes | in |  |
| LocalDestinationWriter | `data/transfer/local/LocalDestinationWriter.kt` | 31 | no | out | reclassified: pure interface + DestinationAlreadyExistsException typealias (no logic) |
| LocalSink | `data/transfer/local/LocalSink.kt` | 39 | no | out | reclassified: pure interface (commit/abort/outputStream contract; no logic) |
| MediaStoreLocalDestinationWriter | `data/transfer/local/MediaStoreLocalDestinationWriter.kt` | 290 | no | out | reclassified: android-bound (MediaStore IS_PENDING insert/commit + ContentResolver/Uri/Environment on every path); needs instrumentation |
| FtpToFtpStrategy | `data/transfer/strategies/FtpToFtpStrategy.kt` | 214 | yes | in | supports() scheme matrix (TransferStrategySupportsTest); copy() body is Uri + FtpClient dominated (not unit-isolable) |
| FtpToLocalStrategy | `data/transfer/strategies/FtpToLocalStrategy.kt` | 103 | yes | in | supports() scheme matrix (TransferStrategySupportsTest); copy() body Uri/network dominated |
| LocalToFtpStrategy | `data/transfer/strategies/LocalToFtpStrategy.kt` | 120 | yes | in | supports() scheme matrix (TransferStrategySupportsTest); copy() body Uri/network dominated |
| LocalToSftpStrategy | `data/transfer/strategies/LocalToSftpStrategy.kt` | 118 | yes | in | supports() scheme matrix (TransferStrategySupportsTest); copy() body Uri/network dominated |
| LocalToSmbStrategy | `data/transfer/strategies/LocalToSmbStrategy.kt` | 142 | yes | in | supports() scheme matrix (TransferStrategySupportsTest); copy()/resolveConnectionInfo Uri + creds.password (CryptoHelper) dominated |
| SftpToLocalStrategy | `data/transfer/strategies/SftpToLocalStrategy.kt` | 101 | yes | in | supports() scheme matrix (TransferStrategySupportsTest); copy() body Uri/network dominated |
| SftpToSftpStrategy | `data/transfer/strategies/SftpToSftpStrategy.kt` | 175 | yes | in | supports() scheme matrix (TransferStrategySupportsTest); copy() body Uri/network dominated |
| SmbToLocalStrategy | `data/transfer/strategies/SmbToLocalStrategy.kt` | 74 | yes | in | supports() scheme matrix (TransferStrategySupportsTest); copy() body Uri + MediaStoreNotifier + SmbClient dominated |
| SmbToSmbStrategy | `data/transfer/strategies/SmbToSmbStrategy.kt` | 111 | yes | in | supports() scheme matrix (TransferStrategySupportsTest); copy() body Uri/network dominated |
| CloudOperationStrategy | `data/transfer/strategy/CloudOperationStrategy.kt` | 729 | no | out | reclassified: exists()/copy/move route through live CloudStorageClient SDKs + CloudPathParser + Uri/MediaStoreNotifier; exists() delegate is exercised via CloudQuickVerifier |
| FtpOperationStrategy | `data/transfer/strategy/FtpOperationStrategy.kt` | 737 | no | out | reclassified: ops route through live FtpClient + credentials.password (CryptoHelper) + Uri/MediaStoreNotifier; exists() delegate exercised via Sftp/Ftp verifier path |
| LocalOperationStrategy | `data/transfer/strategy/LocalOperationStrategy.kt` | 599 | yes | in | copy/move-rename/delete(non-shared)/createDirectory/createTextFile/read-write/listFiles/dir copy-rename-delete-info/supportsProtocol/isSharedStoragePath (TemporaryFolder); MediaStore/shared-storage branches excluded |
| SftpOperationStrategy | `data/transfer/strategy/SftpOperationStrategy.kt` | 743 | no | out | reclassified: ops route through live SftpClient + credentials.password (CryptoHelper) + Uri/MediaStoreNotifier; exists() delegate exercised via SftpQuickVerifier |
| SmbOperationStrategy | `data/transfer/strategy/SmbOperationStrategy.kt` | 735 | no | out | reclassified: ops route through live SmbClient + credentials.password (CryptoHelper) + Uri/MediaStoreNotifier; exists() delegate exercised via SmbQuickVerifier |
| StrategyUtils | `data/transfer/strategy/StrategyUtils.kt` | 22 | yes | in | safeIo success→Result.success / thrown→Result.failure with original throwable (runTest) |
| TrashFolderContract | `data/transfer/trash/TrashFolderContract.kt` | 97 | yes | in |  |
| CloudQuickVerifier | `data/verifier/CloudQuickVerifier.kt` | 86 | yes | in | empty guard, confirmed-false→missing, success(true)/failure/exception→present, non-cloud-path resource-key fallback (CloudOperationStrategy mocked, real throttle) |
| LocalQuickVerifier | `data/verifier/LocalQuickVerifier.kt` | 33 | yes | in | only non-existent paths reported missing; existing retained; empty input (TemporaryFolder) |
| QuickVerifierDispatcher | `data/verifier/QuickVerifierDispatcher.kt` | 88 | yes | in | empty/zero-n guards, unknown-resource skip, per-type strategy selection, FTP no-op, first-N truncation (strategies mocked, FakeResourceRepository) |
| SftpQuickVerifier | `data/verifier/SftpQuickVerifier.kt` | 84 | yes | in | empty guard + confirmed-false→missing vs success(true)→present (SftpOperationStrategy mocked, real throttle) |
| SmbQuickVerifier | `data/verifier/SmbQuickVerifier.kt` | 86 | yes | in | empty guard, confirmed-false→missing, failure/exception→present (SmbOperationStrategy mocked, real throttle) |
| CctUnavailableException | `data/browser/CctUnavailableException.kt` | 8 | no | out | no declared logic (data holder/marker) |
| CloudProvider | `data/cloud/CloudStorageClient.kt` | 284 | no | out | no declared logic (data holder/marker) |
| CloudFile | `data/cloud/CloudStorageClient.kt` | 284 | no | out | no declared logic (data holder/marker) |
| AuthResult | `data/cloud/CloudStorageClient.kt` | 284 | no | out | no declared logic (data holder/marker) |
| CloudResult | `data/cloud/CloudStorageClient.kt` | 284 | no | out | no declared logic (data holder/marker) |
| TransferProgress | `data/cloud/CloudStorageClient.kt` | 284 | no | out | no declared logic (data holder/marker) |
| GoogleDriveBrowserUnavailableException | `data/cloud/GoogleDriveBrowserAuthManager.kt` | 324 | no | out | no declared logic (data holder/marker) |
| CloudThumbnailData | `data/cloud/glide/CloudThumbnailData.kt` | 48 | no | out | Glide integration glue |
| CloudThumbnailDataFetcher | `data/cloud/glide/CloudThumbnailModelLoader.kt` | 403 | no | out | Glide integration glue |
| CloudThumbnailModelLoader | `data/cloud/glide/CloudThumbnailModelLoader.kt` | 403 | no | out | Glide integration glue |
| CloudThumbnailEntryPoint | `data/cloud/glide/CloudThumbnailModelLoader.kt` | 403 | no | out | Glide integration glue |
| GoogleDriveThumbnailData | `data/cloud/glide/GoogleDriveThumbnailData.kt` | 40 | no | out | Glide integration glue |
| GoogleDriveThumbnailDataFetcher | `data/cloud/glide/GoogleDriveThumbnailModelLoader.kt` | 276 | no | out | Glide integration glue |
| GoogleDriveThumbnailModelLoader | `data/cloud/glide/GoogleDriveThumbnailModelLoader.kt` | 276 | no | out | Glide integration glue |
| EpubCoverDecoder | `data/glide/EpubCoverDecoder.kt` | 91 | no | out | Glide integration glue |
| NetworkEpubCoverLoader | `data/glide/NetworkEpubCoverLoader.kt` | 383 | no | out | Glide integration glue |
| NetworkEpubDataFetcher | `data/glide/NetworkEpubCoverLoader.kt` | 383 | no | out | Glide integration glue |
| NetworkPdfThumbnailLoader | `data/glide/NetworkPdfThumbnailLoader.kt` | 533 | no | out | Glide integration glue |
| NetworkPdfDataFetcher | `data/glide/NetworkPdfThumbnailLoader.kt` | 533 | no | out | Glide integration glue |
| PdfPageDecoder | `data/glide/PdfPageDecoder.kt` | 102 | no | out | Glide integration glue |
| InputBindingEntity | `data/input/InputBindingEntity.kt` | 14 | no | out | no declared logic (data holder/marker) |
| CanonicalizedUrl | `data/link/CanonicalizedUrl.kt` | 13 | no | out | no declared logic (data holder/marker) |
| HtmlMediaCandidate | `data/link/HtmlMediaCandidate.kt` | 35 | no | out | no declared logic (data holder/marker) |
| HtmlFetchResult | `data/link/HtmlPageExtractionStrategy.kt` | 452 | yes | out | no declared logic (data holder/marker) |
| LinkDownloadUserAgents | `data/link/LinkDownloadUserAgents.kt` | 12 | no | out | no declared logic (data holder/marker) |
| KnownAuthResource | `data/link/auth/KnownAuthResources.kt` | 75 | yes | out | no declared logic (data holder/marker) |
| SegmentBundle | `data/link/streaming/Media3SegmentDownloader.kt` | 150 | no | out | no declared logic (data holder/marker) |
| StreamingDownloadException | `data/link/streaming/Media3SegmentDownloader.kt` | 150 | no | out | no declared logic (data holder/marker) |
| RemuxResult | `data/link/streaming/MediaMuxerRemuxer.kt` | 149 | no | out | no declared logic (data holder/marker) |
| FileExistsException | `data/transfer/FileExistsException.kt` | 16 | no | out | no declared logic (data holder/marker) |
| DirectoryInfo | `data/transfer/FileOperationStrategy.kt` | 226 | no | out | no declared logic (data holder/marker) |
| MoveResult | `data/transfer/UnifiedFileOperationHandler.kt` | 564 | no | out | no declared logic (data holder/marker) |
| LocalDestinationCategory | `data/transfer/local/LocalDestinationCategory.kt` | 48 | no | out | no declared logic (data holder/marker) |
| LocalDestinationPermissionDeniedException | `data/transfer/local/LocalDestinationPermissionDeniedException.kt` | 18 | no | out | no declared logic (data holder/marker) |
| TrashRenameUnavailableException | `data/transfer/strategy/LocalOperationStrategy.kt` | 599 | no | out | no declared logic (data holder/marker) |

## Phase 07 - flavor-only data logic (noLegal)

In-scope: 9 | Out: 2 | Source set: `src/testNoLegal/`

Implementation (2026-05-29): 9 in-scope rows gained `*Test.kt` coverage in `src/testNoLegal/`, 93 test
methods, all green via per-class XML (`testNoLegalDebugUnitTest`). The 7 link-extraction rows below plus
the 2 OCR rows deferred from Phase 03 (PaddleOcrEngine, PaddleOcrEngineContributor) are all `yes`. None
reclassified `out`. Cutoff notes per row record the JVM-reachable surface vs the Python/native parts left
uncovered. Phase 07 in-scope rows remaining: 0.

JSON-parsing strategy tests run under `@RunWith(RobolectricTestRunner::class)` - the stubbed android.jar
`org.json` returns null from every `opt*` call, which would make the JSON-parse branches unreachable;
Robolectric supplies a real `org.json`. `PaddleOcrEngineTest` runs under Robolectric so MockK can safely
instrument `android.graphics.Bitmap` (the stubbed Bitmap crashes the test JVM under MockK).

| Class | Path | tests | scope | reason |
|-------|------|:-----:|:-----:|--------|
| ArtStationExtractionStrategy | `data/link/nolegal/ArtStationExtractionStrategy.kt` | yes | in | probe host match + projects-JSON parse (video/image preference) + error mapping + direct delegation; OkHttp mocked |
| DailymotionExtractionStrategy | `data/link/nolegal/DailymotionExtractionStrategy.kt` | yes | in | probe host match + video-id regex + __PLAYER_CONFIG__/m3u8 parse + error mapping; OkHttp mocked |
| DeviantArtExtractionStrategy | `data/link/nolegal/DeviantArtExtractionStrategy.kt` | yes | in | probe host match + __INITIAL_STATE__ (baseUri/prettyName, types[].full) + oEmbed fallback + error mapping; OkHttp mocked |
| VimeoExtractionStrategy | `data/link/nolegal/VimeoExtractionStrategy.kt` | yes | in | probe host match + numeric id across URL shapes + config parse (progressive width pick, HLS fallback) + error mapping; OkHttp mocked |
| YtDlpExtractionStrategy | `data/link/nolegal/YtDlpExtractionStrategy.kt` | yes | in | JVM-reachable surface only: probe extension short-circuit + probe/open runtime-unavailable; format selection is behind the Chaquopy Python bridge and not JVM-testable |
| NewPipeSiteExtractionStrategy | `data/link/nolegal/NewPipeSiteExtractionStrategy.kt` | yes | in | service-id gate, link-type routing, extraction-failure→OpenResult matrix; NewPipe statics + downloader mocked |
| NewPipeOkHttpDownloader | `data/link/nolegal/NewPipeOkHttpDownloader.kt` | yes | in | NewPipe Request→OkHttp mapping (method/headers/body) + Response mapping (code/message/headers/body/latestUrl); OkHttp mocked |
| CookieFileWriter | `data/link/nolegal/CookieFileWriter.kt` | yes | out | already covered by CookieFileWriterTest |
| ChaquopyRuntimeHolder | `data/link/nolegal/ChaquopyRuntimeHolder.kt` | no | out | thin runtime holder, no logic |

---

## Final closure summary (Phase 08, 2026-05-29)

- In-scope rows resolved: every `scope = in` row is now `tests = yes` (covered) - 0 unaddressed.
- Rows reclassified `in` -> `out` during implementation: trivial holders, pure interfaces, thin delegates, and Android/Robolectric-or-native-runtime-bound classes with no JVM-reachable logic. Reasons recorded inline per row.
- Honest partial coverage noted inline for classes whose core runs behind Android framework / Chaquopy / native libs / live network - only their JVM-reachable branches are covered.
- Covered (`yes|in`) rows: 261. Out rows: 329.
- New test files: 238 under `src/test/` (domain+data) + 11 under `src/testNoLegal/`.
- Validation: `compileStandardDebugUnitTestKotlin` and `compileNoLegalDebugUnitTestKotlin` exit 0; every new test class green per per-class XML; no new red; ~26 pre-existing unrelated failures untouched.
