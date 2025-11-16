# FastMediaSorter v2 - AI Coding Agent Instructions

## Project Overview
Android media management app supporting local, network (SMB/SFTP/FTP), and cloud (Google Drive) resources. Built with Clean Architecture + MVVM pattern using Kotlin, Hilt DI, Room database, and ExoPlayer.

**Active development is in `app_v2/` directory. `V1/` is legacy reference only.**

## Architecture Principles

### Three-Layer Structure
- **UI (`ui/`)**: Activities/Fragments observe `StateFlow`/`SharedFlow` from ViewModels. Zero business logic.
- **Domain (`domain/`)**: UseCases encapsulate single operations. Only depends on repository interfaces (never implementations).
- **Data (`data/`)**: Repository implementations, Room entities, network clients (SMB/SFTP/FTP/Cloud).

**Dependency Rule**: `UI` → `Domain` (via UseCases) → `Data` (via Repository interfaces).

### Key Patterns
1. **ViewModels**: Annotate with `@HiltViewModel`, inject UseCases via constructor. Expose `StateFlow` for state, `SharedFlow` for one-time events.
2. **UseCases**: Single-responsibility classes in `domain/usecase/`. Named `VerbNounUseCase` (e.g., `GetMediaFilesUseCase`, `MoveFileUseCase`).
3. **Events**: Use sealed classes for ViewModel→View communication (e.g., `sealed class BrowseEvent { data class Error(val message: String) : BrowseEvent() }`).
4. **Results**: Network operations return `Result<T>` or custom sealed class variants (`CloudResult<T>`, `SmbResult<T>`).

## Critical Terminology (See `docs/V2_TERMS.md`)
- **Resource**: Any registered media source (local folder, SMB share, SFTP path, Google Drive folder). Stored in Room `ResourceEntity`.
- **Destinations**: Up to 10 resources marked for quick copy/move operations. Flag: `isDestination = true`.
- **MediaFile**: Domain model with `path`, `name`, `size`, `date`, `type` (IMAGE/VIDEO/AUDIO/GIF), `duration`, `thumbnailUrl` (for cloud).
- **Scanners**: Protocol-specific file discovery (`LocalMediaScanner`, `SmbMediaScanner`, `CloudMediaScanner`).

## Development Workflows

### Building
```powershell
# Standard debug build
.\gradlew.bat :app_v2:assembleDebug

# Auto-versioned build (updates versionCode/versionName with timestamp)
.\build-with-version.ps1
```
Version format: `2.0.0-buildYYMMDDHHmm` (e.g., `2.0.0-build2511160117`). Script auto-reverts on build failure.

### Testing Network Protocols
- **SMB**: Server requires smbj 0.12.1 + BouncyCastle 1.78.1 (critical: avoids libpenguin.so native crash).
- **FTP**: Uses Apache Commons Net 3.10.0. **Known issue**: PASV mode timeouts handled with active mode fallback.
- **SFTP**: SSHJ 0.37.0 + EdDSA 0.3.0 for Curve25519 support.

Test connection methods (`testConnection()`) exist in each client before full operations.

### Database Migrations
Room DB version 6. Migrations in `AppDatabase.kt`:
- **5→6**: Added `cloudProvider` and `cloudFolderId` columns for cloud resources.
- Always increment version and create `Migration` object when schema changes.

### Working with TODO
Track progress in `docs/TODO_V2.md`. Section structure:
- **Current Development Tasks**: Active work (Google Drive Phase 3, Pagination testing).
- **High/Medium Priority**: Ordered by importance.
- **Recent Fixes**: Top section documents completed work with build number.

## Protocol-Specific Patterns

### SMB Operations (SMBJ)
```kotlin
// Connect with credentials from NetworkCredentialsRepository
smbClient.connect(server, shareName, username, password, domain, port)
// Operations return Result<T>
smbClient.listFiles(remotePath).onSuccess { files -> ... }
```
Files cached in Room. Use `SmbOperationsUseCase` for file ops (copy/move/delete).

### Cloud Storage (Google Drive)
```kotlin
// Authentication returns sealed class
when (val result = googleDriveClient.authenticate(activity)) {
    is AuthResult.Success -> // Use result.email
    is AuthResult.Error -> // Handle result.message
}
// File listing via CloudMediaScanner
cloudMediaScanner.scanFolder(CloudProvider.GOOGLE_DRIVE, folderId, progressCallback)
```
**OAuth Setup Required**: Create Android OAuth client in Google Cloud Console with package name + SHA-1 fingerprint.

### Pagination (Paging3)
Activated automatically when file count > `PAGINATION_THRESHOLD` (1000). `BrowseViewModel` switches between `MediaFileAdapter` (standard) and `PagingMediaFileAdapter`. All scanners implement `scanFolderPaged(offset, limit)`.

## Common Pitfalls

1. **Parallel Loading Race Conditions**: Use `Job.isActive` checks before launching coroutines (see `BrowseViewModel.loadMediaFiles()`).
2. **Async ListAdapter Timing**: Move UI state checks into `submitList { }` callback (itemCount only accurate after submission).
3. **FTP Timeouts**: Never call `completePendingCommand()` after exceptions. Use try-catch with active mode fallback.
4. **Network Image Editing**: Must download → edit temp file → upload back. Use `NetworkImageEditUseCase` pattern.

## File Operation Undo System
Soft-delete: Move to `.trash/` folder on same resource. Store original path in `FileOperationResult` for undo restoration. Trash cleanup handled by `CleanupTrashFoldersUseCase`.

## Code Style Conventions
- **Language**: All code, comments, docs in English. User-facing strings localized (en/ru/uk).
- **Sealed Classes**: Prefer for events, results, progress types over enum + data bundles.
- **Coroutines**: Use `Dispatchers.IO` for file/network ops. `viewModelScope` in ViewModels, `CoroutineScope(Dispatchers.IO)` in workers.
- **Logging**: Timber for all logs. Format: `Timber.d("Action succeeded: $details")` or `Timber.e(exception, "Context info")`.

## Key Dependencies
- **Media**: ExoPlayer (media3 1.2.1) for video/audio playback.
- **Images**: Coil 2.5.0 with video/GIF extensions. Use `memoryCacheKey(path)` + `diskCacheKey(path)` for consistent caching.
- **DI**: Hilt 2.50. Always use constructor injection in ViewModels/UseCases/Repositories.
- **Database**: Room 2.6.1 with Kotlin coroutines support.

## Current Focus Areas (Nov 2024)
1. **Google Drive Testing**: OAuth client setup blocking. Need Android OAuth client with package + SHA-1 in Cloud Console.
2. **Pagination Verification**: Test 1000+, 5000+ file scenarios across all resource types.
3. **Network Undo Testing**: Soft-delete implementation complete, needs real-world testing.

## Reference Files
- Architecture: `docs/V2_architecture_overview.md`
- Terms: `docs/V2_TERMS.md`
- Spec: `docs/V2_Specification.md` (references `V2_p1_*.md` through `V2_p3_*.md`)
- TODO: `docs/TODO_V2.md`
