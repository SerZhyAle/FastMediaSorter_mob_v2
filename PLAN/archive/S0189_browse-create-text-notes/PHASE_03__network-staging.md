# Phase 03 — Network/cloud staging via Downloads sandbox

**Strategic spec:** [`../S0189_browse-create-text-notes.md`](../S0189_browse-create-text-notes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Not started
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

For SMB/SFTP/FTP/Cloud resources, materialise the new note as a local file in `Downloads/FastMediaSorter/notes/` first. Keep a registry of staged files so that on save (Phase 06) the manager can upload the local copy to the network destination. On successful upload the local copy is deleted; on failure the local file remains for the user to retry. After this phase, calling `CreateTextNoteUseCase` for any non-LOCAL resource produces a real local file and registers the pending upload, but does NOT attempt the upload itself — that happens in Phase 06.

---

## Prerequisites

- [ ] Phase 01 Done.
- [ ] `Downloads` directory write permission is already granted by the existing app (no new manifest permission required — verify by grepping `WRITE_EXTERNAL_STORAGE` / `MANAGE_DOCUMENTS` posture in `AndroidManifest.xml`; record actual values).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/TextNoteStagingDirectory.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/TextNoteStagingRegistry.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/TextNoteStagingModule.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt` | Modified | reuses stub from 01.5 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt` | Modified | same |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/FtpOperationStrategy.kt` | Modified | same |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt` | Modified | same |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt` | Modified | extends staging logic |

---

## Steps

### Step 03.1 — Add `TextNoteStagingDirectory` and `TextNoteStagingRegistry`

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/local/TextNoteStagingDirectory.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/local/TextNoteStagingRegistry.kt`

**Prompt for developer:**

> 1. `TextNoteStagingDirectory`: `@Singleton class TextNoteStagingDirectory @Inject constructor(@ApplicationContext private val context: Context)`. Public method `fun ensureDirectory(): java.io.File` returns the canonical staging directory: `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) / "FastMediaSorter/notes"`. Create the directory if it does not exist. If `getExternalStoragePublicDirectory` is unavailable on the running API/permission model, fall back to `context.getExternalFilesDir(null) / "notes"` (still inside the app's scoped storage). Add `Timber.d("S0189: TextNoteStagingDirectory.ensureDirectory -> $path")`.
> 2. `TextNoteStagingRegistry`: `@Singleton class TextNoteStagingRegistry @Inject constructor()`. Holds an in-memory `ConcurrentHashMap<String /*absolutePath*/, StagedNote>` where `data class StagedNote(val localFile: java.io.File, val targetResourceId: Long, val targetParentPath: String, val intendedName: String)`.
>    Public API:
>    - `fun register(file: File, targetResourceId: Long, targetParentPath: String, intendedName: String)`
>    - `fun unregister(file: File): StagedNote?`
>    - `fun lookup(file: File): StagedNote?`
>    - `fun snapshot(): List<StagedNote>`  // for diagnostics
>
>    Add `Timber.d("S0189: TextNoteStagingRegistry.register $file -> resource=$targetResourceId path=$targetParentPath")`.

**Verification:**

- Glob — both new files exist.
- Grep — `class TextNoteStagingDirectory @Inject constructor` matches once.
- Grep — `class TextNoteStagingRegistry @Inject constructor` matches once.
- Grep — `data class StagedNote` matches once.
- Grep — `Timber.d("S0189: TextNoteStagingDirectory` and `Timber.d("S0189: TextNoteStagingRegistry` both present.

**Status:** `[ ]` not done

---

### Step 03.2 — Add Hilt module `TextNoteStagingModule`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/TextNoteStagingModule.kt`

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) object TextNoteStagingModule`. Because both classes are `@Singleton class ... @Inject constructor(...)`, Hilt resolves them automatically — but add explicit `@Provides` only if the project's DI convention requires per-feature modules (mirror the convention used by `BrowseApkInstallModule` and `BrowseBinaryFileMenuActionModule` at the same `di/` path; if those classes are constructor-injected without `@Provides`, leave this module empty with a KDoc note "marker module — types are constructor-injected; module placeholder for future overrides"). If the convention requires `@Provides`, add `@Provides @Singleton fun provideStagingDirectory(...)` and `@Provides @Singleton fun provideStagingRegistry(...)`. Record decision in the file's KDoc.

**Verification:**

- Glob — `TextNoteStagingModule.kt` exists.
- Grep — `@Module` and `@InstallIn(SingletonComponent::class)` both present in the file.
- Build: `assembleStandardDebug` compiles (Hilt graph valid).

**Status:** `[ ]` not done

---

### Step 03.3 — Rewrite `createTextFile` stubs in SMB / SFTP / FTP / Cloud strategies

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/FtpOperationStrategy.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt`

**Depends on:** Step 03.1

**Prompt for developer:**

> Replace each strategy's stub `createTextFile` with the staging-only impl. Each strategy now requires injection of `TextNoteStagingDirectory` and `TextNoteStagingRegistry`. Pattern:
>
> 1. Resolve staging dir = `stagingDir.ensureDirectory()`.
> 2. Compose unique local file path: `File(stagingDir, "${resourceId}_${fileName}")` (resource id segregates concurrent notes on different resources with same filename).
> 3. Create the local file with `file.createNewFile()` and `file.writeText(content, Charsets.UTF_8)`.
> 4. Register with `stagingRegistry.register(file, resourceId, parentPath, fileName)`.
> 5. Return `Result.success(file.absolutePath)` — the local absolute path is what UI uses to open the editor.
> 6. The actual remote upload is deferred to Phase 06 save flow.
>
> Each strategy must receive `resourceId` to register correctly. Strategy methods currently only know `parentPath` — update the `FileOperationStrategy.createTextFile` signature to accept `resourceId: Long` parameter (modify Phase 01 Step 01.3 signature retroactively here; the only consumer is the new `UnifiedFileOperationHandler.executeCreateTextFile`, so the cascade is local).
>
> Add `Timber.d("S0189: <Strategy>.createTextFile staged at $localPath for resource=$resourceId path=$parentPath name=$fileName")` per strategy.

**Verification:**

- Grep — `Result.success(file.absolutePath)` in each of the four strategies.
- Grep — `stagingRegistry.register` matches exactly 4 times across `data/transfer/strategy/`.
- Grep — old stub text `"S0189: createTextFile not yet wired"` returns ZERO hits (replaced).
- Build: `assembleStandardDebug` compiles.

**Status:** `[ ]` not done

---

### Step 03.4 — Propagate `resourceId` through `UnifiedFileOperationHandler.executeCreateTextFile`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CreateTextNoteUseCase.kt`

**Prompt for developer:**

> 1. In `UnifiedFileOperationHandler.executeCreateTextFile`: add `resourceId: Long` parameter (positional, before `content`). Forward to the strategy call. LOCAL strategy ignores it; network/cloud strategies use it for staging registration.
> 2. In `CreateTextNoteUseCase.invoke`: pass `resource.id` to `executeCreateTextFile`.
> 3. Update the `Timber.d("S0189: ...")` log lines in both files to include the new parameter value.

**Verification:**

- Grep — `fun executeCreateTextFile(parentPath: String, fileName: String, resourceId: Long` in `UnifiedFileOperationHandler.kt`.
- Grep — `fileOperationHandler.executeCreateTextFile(parentPath, fileName, resource.id` in `CreateTextNoteUseCase.kt`.
- Build: `assembleStandardDebug` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] `assembleStandardDebug` passes via `/build`.
- [ ] Manual smoke (LOCAL): still works — file appears in the local folder (no staging used; LOCAL strategy ignores resourceId).
- [ ] Manual smoke (SMB or SFTP if device has one configured): the new note appears in `/storage/emulated/0/Download/FastMediaSorter/notes/` (or scoped fallback). Verify with `adb shell ls`.
- [ ] `add_to_dev_log.ps1` invoked for each touched file.
- [ ] `scan.ps1` + `render.ps1` for `app_v2`.

---

## Handoff Notes to Next Phase

- For network resources, `BrowseTextNoteCreateManager.notifyCreatedForOpen` will receive the **local staging path** (not the eventual remote URI). Phase 04 launches the editor against that local path.
- The remote upload contract is intentionally NOT executed here. It happens in Phase 06 (`save-with-name`) where the user explicitly commits the note.
- `TextNoteStagingRegistry` is consulted in Phase 06 to find the target remote destination when the user presses Save.

---

## Rollback Plan

- Revert phase commits. Staging files in `Downloads/FastMediaSorter/notes/` left on devices are harmless (no DB references). They will be ignored if the registry is empty after rollback.
