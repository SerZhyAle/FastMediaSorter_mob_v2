# Phase 01 — cache-scope-and-stale-path

**Strategic spec:** [`../S0146_bugfix-player-stale-initial-file-path-reload.md`](../S0146_bugfix-player-stale-initial-file-path-reload.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Fix the spurious "cache scope mismatch" re-read: extend the scope check to also accept `currentFilePath` being in cache, add an `initialFilePathIsStale` guard so a once-absent initial path no longer re-triggers mismatch on future reloads.

---

## Prerequisites

- [ ] Strategic §6 research items are all Resolved (see INDEX.md).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt` | Modified | <= 400 |

---

## Steps

### Step 01.1 — Add `initialFilePathIsStale` guard field

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** start of phase

**Prompt for developer:**

> In `PlayerMediaFilesLoader`, right after the line `private var loadingJob: Job? = null`, add the field:
>
> ```kotlin
>     // Tracks whether the launch-time initialFilePath was found to be absent from the
>     // filesystem. Once set, excludes the path from cache-scope checks and prevents
>     // repeated "cache scope mismatch" cycles within the same ViewModel session.
>     private var initialFilePathIsStale = false
> ```

**Verification:**

- [x] Field is a `private var Boolean`, default `false`, placed directly after `private var loadingJob: Job? = null`.
- [x] No existing logic changed in this step.

---

### Step 01.2 — Move `normalizedCurrentPath` declaration early; extend scope check

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `loadMediaFiles()`, find the block that computes `normalizedInitialPath` and `cacheMatchesInitialFile` (around the cache fast/slow-path decision). Replace it as follows:
>
> **Old block** (three lines):
> ```kotlin
>                 val normalizedInitialPath = initialFilePath?.let { normalizePath(it) }
>                 val cacheMatchesInitialFile = normalizedInitialPath == null ||
>                     (cachedFiles != null && cachedFiles.any { normalizePath(it.path) == normalizedInitialPath })
> ```
>
> **New block:**
> ```kotlin
>                 val normalizedInitialPath = initialFilePath?.let { normalizePath(it) }
>                 // Declared early so the scope check and the position-restore section share one instance.
>                 val normalizedCurrentPath = currentFilePath?.let { normalizePath(it) }
>                 // Scope check accepts the cache when the current (actively-playing) file is present —
>                 // avoids a full re-read just because the original launch file was sorted away.
>                 val cacheMatchesCurrentFile = normalizedCurrentPath != null &&
>                     cachedFiles != null && cachedFiles.any { normalizePath(it.path) == normalizedCurrentPath }
>                 // Exclude stale initial path from scope check (already confirmed absent in prior reload).
>                 val effectiveInitialPath = if (initialFilePathIsStale) null else normalizedInitialPath
>                 val cacheMatchesInitialFile = cacheMatchesCurrentFile ||
>                     effectiveInitialPath == null ||
>                     (cachedFiles != null && cachedFiles.any { normalizePath(it.path) == effectiveInitialPath })
> ```
>
> Then, further down in the same function, inside the `else {` block that begins `// Priority order for determining index:`, **remove** the now-duplicate `val normalizedCurrentPath = currentFilePath?.let { normalizePath(it) }` line (the `val safeIndex = if (normalizedCurrentPath != null)` line immediately below it must stay — it now references the early-declared variable).

**Verification:**

- [x] `normalizedCurrentPath` is declared exactly once inside `loadMediaFiles()`.
- [x] `cacheMatchesInitialFile` uses `cacheMatchesCurrentFile || effectiveInitialPath == null || ...`.
- [x] `effectiveInitialPath` is `null` when `initialFilePathIsStale` is `true`.
- [x] Compile passes: `./gradlew.bat :app_v2:compileStandardDebugKotlin --console=plain --no-daemon`.

---

### Step 01.3 — Set `initialFilePathIsStale` after source-list check

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `loadMediaFiles()`, find the existing `initialFileExistsInUnfiltered` computation:
>
> ```kotlin
>                 val initialFileExistsInUnfiltered = normalizedInitialPath?.let { normalizedTarget ->
>                     allFiles.any { file -> !file.isDirectory && normalizePath(file.path) == normalizedTarget }
>                 } ?: false
> ```
>
> Immediately after the closing `?: false`, add:
>
> ```kotlin
>                 // If the initial path is absent from the full source list, mark it stale so
>                 // subsequent reloadFiles() calls skip the scope check for this path.
>                 if (normalizedInitialPath != null && !initialFileExistsInUnfiltered) {
>                     initialFilePathIsStale = true
>                 }
> ```

**Verification:**

- [x] `initialFilePathIsStale = true` is set exactly when `normalizedInitialPath != null && !initialFileExistsInUnfiltered`.
- [x] The existing cleanup block (`MediaFilesCacheManager.removeFile` + `cachedFileListRepository.deleteFile`) is unchanged.
- [x] Compile passes.

---

### Step 01.4 — Verify fast/slow-path decision integrity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Read the fast/slow-path `if` condition that uses `cacheMatchesInitialFile`:
>
> ```kotlin
>                 val allFiles = if (cachedFiles != null && cachedFiles.isNotEmpty() && !cacheHasOnlyDirectories && cacheMatchesInitialFile) {
>                     cachedFiles
>                 } else {
> ```
>
> Confirm the condition is exactly as above (no other variable was affected). Also confirm the slow-path log block still says `cacheMatchesInitialFile` in its warning (update the warning message to `effectiveInitialPath` for accuracy):
>
> ```kotlin
>                         Timber.w("PlayerMediaFilesLoader: cache scope mismatch — cached ${cachedFiles.size} files do not contain initialFilePath=$initialFilePath, reloading from $initialFileDir")
> ```
> Update the warning line's variable reference from `initialFilePath` to reflect what was actually checked:
> ```kotlin
>                         Timber.w("PlayerMediaFilesLoader: cache scope mismatch — cached ${cachedFiles.size} files contain neither current ($currentFilePath) nor initial ($initialFilePath), reloading from $initialFileDir")
> ```
> This is documentation only — no logic change.

**Verification:**

- [x] Fast-path `if` condition unchanged structurally — only `cacheMatchesInitialFile` value semantics changed by Step 01.2.
- [x] Slow-path `Timber.w` for scope mismatch now mentions both `currentFilePath` and `initialFilePath`.
- [x] Compile passes.
- [x] Full build passes: `./gradlew.bat assembleStandardDebug --console=plain --no-daemon`.

---

## Phase Done Criteria

- [x] All four steps `[x]` done.
- [x] `PlayerMediaFilesLoader.kt` compiles without warnings in the modified section.
- [x] `initialFilePathIsStale` field is present and the scope check uses it via `effectiveInitialPath`.
- [x] `normalizedCurrentPath` is declared exactly once inside `loadMediaFiles()`.
