# Phase 01 — Domain layer: create text file

**Strategic spec:** [`../S0189_browse-create-text-notes.md`](../S0189_browse-create-text-notes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 0 / 6
**Started:** —
**Completed:** —

---

## Objective

Introduce the domain UseCase, strategy methods, and shared utilities that create a new text file in the current Browse path. No UI wiring or editor changes yet. After this phase, calling `CreateTextNoteUseCase` produces a real empty `.txt` file on LOCAL and writable network/cloud resources, and exposes the default-name and conflict-suffix utilities reused by later phases.

---

## Prerequisites

- [ ] Strategic spec §6 fully resolved (verified in INDEX Pre-Implementation Blockers).
- [ ] Working tree clean or on a feature branch (`DEBUG-vNNN`).
- [ ] `dev/CATALOG/app_v2.jsonl` up to date for the affected `domain/usecase/`, `data/transfer/`, `util/` paths.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CreateTextNoteUseCase.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/TextNoteFileNameProvider.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/TextNoteNameConflictResolver.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt` | Modified | ≤ 700 (backup if projection >500) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileOperationStrategy.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/FtpOperationStrategy.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt` | Modified | ≤ 700 |

---

## Steps

### Step 01.1 — Add `TextNoteFileNameProvider`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/TextNoteFileNameProvider.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new `object TextNoteFileNameProvider` with a single function `fun defaultName(now: Long = System.currentTimeMillis(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String` returning the timestamp string `yy-MM-dd_HH-mm.txt` (note: two-digit year, two-digit month, two-digit day, underscore, two-digit hour [24h], hyphen, two-digit minute, `.txt`). Use `java.time.format.DateTimeFormatter` with pattern `yy-MM-dd_HH-mm` and append the `.txt` extension. No Hilt — pure object. Add a `Timber.d("S0189: TextNoteFileNameProvider.defaultName -> $name")` log at the entry point (must be present until ticket leaves `BlockNeedUserTest`).

**Verification:**

- Glob — `app_v2/src/main/java/com/sza/fastmediasorter/util/TextNoteFileNameProvider.kt` exists.
- Grep — `object TextNoteFileNameProvider` matches exactly once.
- Grep — `fun defaultName` matches exactly once.
- Grep — `"yy-MM-dd_HH-mm"` literal pattern present.
- Grep — `Timber.d("S0189: TextNoteFileNameProvider` present.

**Status:** `[ ]` not done

---

### Step 01.2 — Add `TextNoteNameConflictResolver`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/TextNoteNameConflictResolver.kt`
**Depends on:** —

**Prompt for developer:**

> Create `object TextNoteNameConflictResolver` with `fun applySecondsSuffix(originalName: String, now: Long = System.currentTimeMillis(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String`. Input: `note.txt`. Output: `note-37.txt` (where `37` is current `ss`, two-digit). Algorithm: split into stem + extension on the last `.`; if no extension, append `-ss` to whole name. Use `DateTimeFormatter.ofPattern("ss")`. Add `Timber.d("S0189: TextNoteNameConflictResolver -> $resolved")` log.

**Verification:**

- Glob — `app_v2/src/main/java/com/sza/fastmediasorter/util/TextNoteNameConflictResolver.kt` exists.
- Grep — `object TextNoteNameConflictResolver` matches once.
- Grep — `fun applySecondsSuffix` matches once.
- Grep — `"ss"` pattern present.
- Grep — `Timber.d("S0189: TextNoteNameConflictResolver` present.

**Status:** `[ ]` not done

---

### Step 01.3 — Extend `FileOperationStrategy` interface with `createTextFile`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/FileOperationStrategy.kt`
**Depends on:** —

**Prompt for developer:**

> Add an abstract / interface method `suspend fun createTextFile(parentPath: String, fileName: String, content: String): Result<String>` to `FileOperationStrategy` returning the absolute path of the created file on success. Default implementation is not allowed — every concrete strategy must implement it. Keep the existing method ordering; place the new method directly below `createDirectory`. KDoc: "Creates a new text file in [parentPath] with given [fileName] and UTF-8 encoded [content]. Returns the protocol-specific path of the created file. Caller is responsible for collision resolution — this method overwrites existing files of the same name only if the strategy explicitly states so in its impl docs (default: fail on conflict)."

**Verification:**

- Grep — `suspend fun createTextFile(parentPath: String, fileName: String, content: String): Result<String>` matches once in `FileOperationStrategy.kt`.
- Grep — `createTextFile` directly follows `createDirectory` (use `-A 5` `-B 0` on `createDirectory` and confirm).

**Status:** `[ ]` not done

---

### Step 01.4 — Implement `createTextFile` in `LocalOperationStrategy`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Implement `override suspend fun createTextFile(parentPath: String, fileName: String, content: String): Result<String>`. Logic:
> 1. Construct `java.io.File(parentPath, fileName)`.
> 2. If parent does not exist or is not a directory — return `Result.failure(Exception("Parent path is not a directory"))`.
> 3. If target file already exists — return `Result.failure(Exception("File already exists"))`.
> 4. Create the file via `target.createNewFile()`; if `false` returned — `Result.failure`.
> 5. Write UTF-8 content with `target.writeText(content, Charsets.UTF_8)`.
> 6. Return `Result.success(target.absolutePath)`.
> Wrap entire body in `runCatching { ... }.fold(onSuccess = { it }, onFailure = { Result.failure(it) })` or equivalent try/catch. Add `Timber.d("S0189: LocalOperationStrategy.createTextFile parent=$parentPath name=$fileName")` at entry.

**Verification:**

- Grep — `override suspend fun createTextFile` matches once in `LocalOperationStrategy.kt`.
- Grep — `Timber.d("S0189: LocalOperationStrategy.createTextFile` present.
- Grep — `writeText(content, Charsets.UTF_8)` present.
- Build: `assembleStandardDebug` compiles (no signature mismatch on the interface).

**Status:** `[ ]` not done

---

### Step 01.5 — Implement `createTextFile` in SMB / SFTP / FTP / Cloud strategies

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/FtpOperationStrategy.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt`

**Depends on:** Step 01.3

**Prompt for developer:**

> For each of the four strategies, implement `override suspend fun createTextFile(parentPath: String, fileName: String, content: String): Result<String>`. The implementation does NOT need to do remote uploads in this phase — Phase 03 introduces the Downloads-staging contract. Each impl must return `Result.failure(UnsupportedOperationException("S0189: createTextFile not yet wired for <protocol>; staged in Phase 03"))` and log `Timber.d("S0189: <Strategy>.createTextFile stub — pending Phase 03 staging")`. This keeps the interface satisfied while Phase 03 owns the network path. Replace `<Strategy>` / `<protocol>` with the concrete name (e.g., `SmbOperationStrategy` / `SMB`).

**Verification:**

- Grep — `override suspend fun createTextFile` matches exactly once in each of the four files.
- Grep — `"S0189: createTextFile not yet wired"` matches exactly four times across `data/transfer/strategy/`.
- Build: `assembleStandardDebug` compiles.

**Status:** `[ ]` not done

---

### Step 01.6 — Add `executeCreateTextFile` to `UnifiedFileOperationHandler` and `CreateTextNoteUseCase`

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CreateTextNoteUseCase.kt`

**Depends on:** Steps 01.3..01.5

**Prompt for developer:**

> 1. In `UnifiedFileOperationHandler.kt`: add `suspend fun executeCreateTextFile(parentPath: String, fileName: String, content: String = "", resourceType: ResourceType): Result<String>`. Body: dispatch to the strategy that matches `resourceType` via the existing strategy-resolution pattern used by `executeCreateDirectory`. If the file projection grows above 500 LOC, first create a timestamped backup in `temp/` per CLAUDE.md Rule 5 and split logic into a private helper before adding the new method.
> 2. Create `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CreateTextNoteUseCase.kt` as `@Inject constructor` with `private val fileOperationHandler: UnifiedFileOperationHandler`. Define `suspend operator fun invoke(resource: MediaResource, parentPath: String, fileName: String, content: String = ""): Result<String>`. Body — mirror `CreateDirectoryUseCase`:
>    - `withContext(Dispatchers.IO) { ... }`
>    - Read-only check via `resource.isReadOnly`.
>    - Trim name, reject empty.
>    - Reject names containing `/ \ : * ? " < > |` and names >255 chars.
>    - If `fileName` has no extension, append `.txt`.
>    - Dispatch via `fileOperationHandler.executeCreateTextFile(parentPath, fileName, content, resource.type)`.
> 3. Add `Timber.d("S0189: CreateTextNoteUseCase.invoke parent=$parentPath name=$fileName resource=${resource.type}")` at entry point.

**Verification:**

- Grep — `suspend fun executeCreateTextFile(` matches once in `UnifiedFileOperationHandler.kt`.
- Glob — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CreateTextNoteUseCase.kt` exists.
- Grep — `class CreateTextNoteUseCase @Inject constructor` matches once.
- Grep — `suspend operator fun invoke` matches once in `CreateTextNoteUseCase.kt`.
- Grep — `Timber.d("S0189: CreateTextNoteUseCase.invoke` present.
- Build: `assembleStandardDebug` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] `assembleStandardDebug` passes via `/build`.
- [ ] `Grep` for `TODO(phase-01)` in touched files returns zero hits.
- [ ] `add_to_dev_log.ps1` invoked for each file in "Files Touched".
- [ ] `dev/CATALOG/scripts/scan.ps1 -Module app_v2` + `render.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

- `CreateTextNoteUseCase` is the single domain entry point for note creation; UI must call it via the ViewModel.
- LOCAL path works end-to-end. Network/cloud paths are stubs that fail explicitly — Phase 03 swaps them for Downloads-staging.
- `TextNoteFileNameProvider.defaultName()` and `TextNoteNameConflictResolver.applySecondsSuffix()` are reusable in Phase 02 (initial dialog default) and Phase 06 (save-with-name).

---

## Rollback Plan

- Revert the commits for this phase. No data migration, no Room version change, no public API change to consumers — strategy interface gains a method but only internal code references it.
