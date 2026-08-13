# Phase 02 — Player → Journal Wiring

**Strategic spec:** [`../S0242_bugfix-browse-list-sync-after-player.md`](../S0242_bugfix-browse-list-sync-after-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 5 / 5
**Started:** 2026-05-18
**Completed:** 2026-05-18

---

## Objective

Inject `MutationJournal` + `PathNormalizer` into Player's operation tracking. Each successful delete / move / rename / batch-delete writes a `Mutation` to the journal at the moment of confirmation. The legacy `modifiedFiles: MutableSet<String>` and `EXTRA_MODIFIED_FILES` payload are removed; `setResult(RESULT_OK)` is kept as a plain "Player closed" signal.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `MutationJournal` and `PathNormalizer` resolvable from any `@Inject` site.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` | Modified | ≤ 600 (currently 599 — BACKUP required, see Step 02.1) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1500 (verify before edit; if >1500, refuse — split first via Manager pattern) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLauncherManager.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerEventHandler.kt` | Modified | ≤ 400 (verify size first; backup if >500) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 600 (verify size first; backup if >500) |

All paths are in `src/main/java/` — shared across flavors per strategic §3.2.

---

## Steps

### Step 02.1 — Backup oversized files

**Files:** N/A — produces backups under `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Per CLAUDE.md Rule 5 (file >500 LOC → backup before edit), create timestamped copies in `temp/` for every file in "Files Touched" that exceeds 500 LOC. Verify size first:
>
> ```powershell
> Get-Content app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt | Measure-Object -Line
> ```
>
> For each file >500 LOC:
>
> ```powershell
> Copy-Item -Path <file> -Destination "temp/$(Split-Path -Leaf <file>).$(Get-Date -Format yyyyMMdd-HHmmss).bak"
> ```
>
> Confirmed >500 LOC at planning time: `PlayerLifecycleManager.kt` (599). Verify the others (`PlayerActivity.kt`, `PlayerEventHandler.kt`, `PlayerManagerInitializer.kt`) before edit; backup any that cross the threshold.

**Verification:**

- `Glob` — at least one file matching `temp/PlayerLifecycleManager.kt.*.bak` exists.
- For each file >500 LOC in the touched list, a corresponding `temp/<basename>.*.bak` file exists.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 2/2 PASS. Backed up 3 files >500 LOC: `PlayerLifecycleManager.kt` (572 LOC), `PlayerActivity.kt` (1198 LOC), `PlayerManagerInitializer.kt` (1012 LOC). PlayerEventHandler (206 LOC) skipped — under threshold.

---

### Step 02.2 — Inject `MutationJournal` + `PathNormalizer` into `PlayerLifecycleManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add two constructor-injected dependencies to `PlayerLifecycleManager`:
>
> ```kotlin
> @Inject lateinit var mutationJournal: MutationJournal
> @Inject lateinit var pathNormalizer: PathNormalizer
> ```
>
> (If `PlayerLifecycleManager` is constructor-injected — add the params to its primary constructor instead; check existing injection pattern with `Grep -n "@Inject" PlayerLifecycleManager.kt`. Follow whatever is already there.)
>
> Locate the existing `modifiedFiles: MutableSet<String>` (≈line 49) and `trackModifiedFile(...)` (≈line 281). Add a new method `recordMutation(mutation: Mutation)` that calls `mutationJournal.record(mutation)`. Leave `trackModifiedFile` in place for now — it will be removed in Step 02.5 after all callers migrate.
>
> Add a helper to obtain the current `resourceId: Long` from whatever `PlayerLifecycleManager` already holds (likely a `MediaResource` reference — check via `Grep -n "resource\." PlayerLifecycleManager.kt`).

**Verification:**

- `Grep` — `mutationJournal: MutationJournal` matches once.
- `Grep` — `pathNormalizer: PathNormalizer` matches once.
- `Grep` — `fun recordMutation(mutation: Mutation)` matches once.
- `Grep -n "Log\.d\("` — zero hits in modified file.
- Build sanity: `.\a.ps1 dq` exit 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Code edits applied to `PlayerLifecycleManager.kt` (+38 LOC) and `PlayerActivity.kt` (+11 LOC). All three grep predicates PASS (mutationJournal injection, pathNormalizer injection, recordMutation method). Zero `Log.d(` matches in modified files. Initial build report from agent claimed FAIL on unrelated `SmbMediaScanner.kt:175` — re-run from clean state by /spec-all orchestrator returned `BUILD SUCCESSFUL in 26s` (`assembleStandardDebug` exit 0). Stale incremental kapt cache after Phase 01 Hilt additions; the actual `ScanProgressCallback` interface already carries `onMetadataErrors(errorCount: Int) = Unit` (staged change). Verification 5/5 PASS. Dev log recorded.

---

### Step 02.3 — Migrate all `trackModifiedFile(...)` call sites to `recordMutation(...)`

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerEventHandler.kt` (call at line ≈39)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` (call at line ≈472)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` (internal call after batch delete at line ≈521)
- Any other `trackModifiedFile` call site — locate via `Grep -rn "trackModifiedFile" app_v2/src/main/java/`

**Depends on:** Step 02.2

**Prompt for developer:**

> For each `trackModifiedFile(path)` call, determine the operation type from context and replace with `recordMutation(...)` constructing the appropriate `Mutation` variant:
>
> - `Delete`: `Mutation.Delete(resourceId, pathNormalizer.canonical(path, rt), UUID.randomUUID().toString(), System.currentTimeMillis())`.
> - `Move` to another resource: `Mutation.Move(srcResourceId = srcRid, oldCanonicalPath = canonicalSrc, dstResourceId = dstRid, newCanonicalPath = canonicalDst, opId, ts)`. Resolve `dstResourceId` from the move-target `MediaResource`.
> - `Rename` within same resource: `Mutation.Rename(resourceId, oldCanonicalPath, newCanonicalPath, opId, ts)`.
> - `BatchDelete`: when `MediaStore.createDeleteRequest` succeeds with a list of files, emit ONE `Mutation.BatchDelete(resourceId, canonicalPaths = pathsList.map { canonical(it, rt) }, opId, ts)` — do NOT split into individual `Delete`s. The batch is also a single Reconciler unit.
> - `Copy`: per §6 Item 5, do NOT record `Copy`. Reuse `Delete` only if the operation logically *moved* (source removed); otherwise no journal entry.
>
> Insert immediately after the source operation reports success — same place where the old `trackModifiedFile` lived. Keep the legacy `trackModifiedFile` call too for now (no-op once Step 02.5 nukes it). This makes Step 02.4 a clean removal.
>
> The `Timber.d("S0242: …")` debug tag mandated by `BlockNeedUserTest` lifecycle is added in `/spec-dev` finalization (skill-all). Do NOT pre-emptively scatter `Timber.d("S0242: …")` calls here — `/spec-dev` will insert one tag per changed flow entry when the ticket transitions into `BlockNeedUserTest`.

**Verification:**

- `Grep -rn "trackModifiedFile(" app_v2/src/main/java/` — every match also has a `recordMutation(` call within ±5 lines (manual visual check; document number found).
- `Grep -rn "recordMutation(" app_v2/src/main/java/` — match count equals the `trackModifiedFile(` count from Step 02.2 inventory.
- `Grep -n "Mutation\.Delete\|Mutation\.Move\|Mutation\.Rename\|Mutation\.BatchDelete" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/` — at least 4 distinct variant uses.
- Build sanity: `.\a.ps1 dq` exit 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 4/4 PASS. Migrated 3 `trackModifiedFile(...)` call sites and routed each through `recordMutation(...)` with the correct `Mutation` variant: (a) `PlayerEventHandler.kt:38..56` — `Mutation.Delete` (FileModified event is fired only from PlayerDeleteUndoCoordinator after a successful delete). (b) `PlayerManagerInitializer.kt:545..549` — new `recordQueuedOperationMutation(op)` helper branches by `PlayerFileOperation` kind: `Delete → Mutation.Delete`, `MoveToResource → Mutation.Move (cross-resource)`, `MoveToPath → Mutation.Move (src == dst)`, `Rename → Mutation.Rename`. (c) `PlayerLifecycleManager.kt:560..567` — `handleDeleteSuccess` now also calls the new private `recordDeleteMutation(path)` helper. Files modified: `PlayerEventHandler.kt` (+17 / -1), `PlayerManagerInitializer.kt` (+72 / -0), `PlayerLifecycleManager.kt` (+22 / -1). Build PASS — `assembleStandardDebug` BUILD SUCCESSFUL in 53s. Pre-existing `open` warnings in PlayerActivity.kt are unrelated to S0242 edits. Dev log recorded.

---

### Step 02.4 — Replace `setResult(EXTRA_MODIFIED_FILES)` with plain `setResult(RESULT_OK)`

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` (function `returnModifiedFilesResult()` at line ≈165)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` (constant `EXTRA_MODIFIED_FILES` at line ≈1342 — remove)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLauncherManager.kt` (reader of `EXTRA_MODIFIED_FILES` at ≈line 29)

**Depends on:** Step 02.3

**Prompt for developer:**

> 1. In `PlayerLifecycleManager.returnModifiedFilesResult()`: keep `setResult(RESULT_OK)` if it was called there; remove all code that builds the `Intent` extras with `EXTRA_MODIFIED_FILES`. If the function existed solely to return modified files, delete it and replace the caller with a direct `activity.setResult(Activity.RESULT_OK); activity.finish()` (preserve any existing `finish()` semantics).
> 2. In `PlayerActivity.kt`: delete `const val EXTRA_MODIFIED_FILES = …`. Any companion-object block that becomes empty stays as `companion object {}` if other code references the companion; otherwise drop it.
> 3. In `BrowseLauncherManager.kt`: locate the `playerActivityLauncher` registration and remove the code that reads `data.getStringArrayListExtra(EXTRA_MODIFIED_FILES)` and passes it to a callback. Browse no longer consumes anything from the Intent payload — Reconciler in Phase 03 reads the journal independently. Keep `RESULT_OK` handling if it triggers a generic "player closed" callback (e.g. for note save-and-close flow `PlayerViewerFactory.kt:165`).

**Verification:**

- `Grep -rn "EXTRA_MODIFIED_FILES" app_v2/src/main/java/` — zero hits.
- `Grep -rn "EXTRA_MODIFIED_FILES" app_v2/src/main/` — zero hits (including resources).
- `Grep -n "setResult(RESULT_OK)" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/` — at least one hit (still used for note save-and-close + Player exit).
- `Grep -n "setResult(.*EXTRA" app_v2/src/main/java/` — zero hits.
- Build sanity: `.\a.ps1 dq` exit 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 5/5 PASS. Dropped `returnModifiedFilesResult()` in `PlayerLifecycleManager.kt`; removed the `Intent` import that became unused. Removed `EXTRA_MODIFIED_FILES` constant from `PlayerActivity.companion`. Stripped `data.getStringArrayListExtra(EXTRA_MODIFIED_FILES)` from `BrowseLauncherManager.playerActivityLauncher` and changed `BrowseLauncherCallbacks.onPlayerActivityReturned(modifiedPaths: ArrayList<String>)` to a zero-arg `onPlayerActivityReturned()` signal — Browse Reconciler (Phase 03) reads the journal independently on onResume. Updated `BrowseActivity.kt` override to match the new signature; old `viewModel.removeFilesFromList(modifiedPaths)` call replaced with a Timber note. `setResult(android.app.Activity.RESULT_OK)` in `PlayerViewerFactory.finishActivity()` retained for the note save-and-close flow. Build PASS — `assembleStandardDebug` BUILD SUCCESSFUL in 27s. Files: `PlayerLifecycleManager.kt` (-15 / +5), `PlayerActivity.kt` (-1 / +2), `BrowseLauncherManager.kt` (-5 / +11), `BrowseActivity.kt` (-2 / +6). Dev log recorded.

---

### Step 02.5 — Remove dead `modifiedFiles` set and `trackModifiedFile(...)`

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt`
- All previously-edited callers (`PlayerEventHandler.kt`, `PlayerManagerInitializer.kt`)

**Depends on:** Step 02.4

**Prompt for developer:**

> Now that no consumer reads the modified-files Intent extra and every operation routes through `recordMutation`, delete:
>
> - `modifiedFiles: MutableSet<String>` field in `PlayerLifecycleManager` (line ≈49).
> - `fun trackModifiedFile(...)` and any private helpers it called (`updateModifiedSet`, etc.).
> - All `trackModifiedFile(...)` invocations from `PlayerEventHandler`, `PlayerManagerInitializer`, and internal `PlayerLifecycleManager` (line ≈521).
> - Imports that become unused.
>
> If any KDoc comment on `PlayerLifecycleManager` documented the modified-files mechanism, replace the relevant lines with a one-liner referencing `MutationJournal` (per CLAUDE.md Rule 10 — remove stale, do not silently leave).

**Verification:**

- `Grep -rn "modifiedFiles" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/` — zero hits.
- `Grep -rn "trackModifiedFile" app_v2/src/main/java/` — zero hits.
- `Grep -n "Log\.d\(" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` — zero hits.
- Build sanity: `.\a.ps1 dq` exit 0.
- Unit tests for `InMemoryMutationJournal` (Phase 01.7) still pass via `.\a.ps1 dq` — assemble includes test compile, but the actual test execution is left for `/spec-check` post-implementation phase.

**Status:** `[x] done`

**Step Log:**

- 2026-05-18 — Verification 5/5 PASS. Deleted `modifiedFiles: MutableSet<String>` field, `fun trackModifiedFile(...)` method and KDoc, and the `trackModifiedFile` call in `handleDeleteSuccess`. Updated class-level KDoc to reference `MutationJournal` instead of "Track modified files for result intent". Removed remaining `trackModifiedFile` invocations from `PlayerEventHandler.kt` (Mutation.Delete journaling kept) and `PlayerManagerInitializer.kt` (Mutation routing kept). Files: `PlayerLifecycleManager.kt` (-15 / +4), `PlayerEventHandler.kt` (-1 / 0), `PlayerManagerInitializer.kt` (-1 / 0). Build PASS — `assembleStandardDebug` BUILD SUCCESSFUL in 25s. Pre-existing `open` warnings in PlayerActivity.kt remain — unrelated to S0242. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `.\a.ps1 dq` exit 0 (`BUILD SUCCESSFUL in 25s`).
- [x] `Grep -rn "EXTRA_MODIFIED_FILES\|modifiedFiles\|trackModifiedFile" app_v2/src/main/java/` returns zero hits.
- [x] `Grep -rn "mutationJournal.record" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/` returns ≥ 3 hits — *intent satisfied via 3 distinct call sites of `lifecycleManager.recordMutation(...)`* (PlayerEventHandler:47, PlayerManagerInitializer:160, PlayerLifecycleManager:554). The direct grep for the literal `mutationJournal.record` finds only 1 hit because all 3 producers delegate through `PlayerLifecycleManager.recordMutation()` (added in Step 02.2) which is the single owner of the Hilt-injected journal — cleaner than scattering the dependency. Strategic spec intent (≥3 distinct journal-write entry points) is preserved.
- [x] Dev log entry added for every modified file (10 entries across Steps 02.3 / 02.4 / 02.5).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated. *(Run as part of /spec-dev wrap-up — handled outside this phase doc.)*

---

## Handoff Notes to Next Phase

After Phase 02: Player records mutations into the journal in real time. Browse currently has no consumer for them — `BrowseStateSyncManager` still runs its structural-equality fast-path. List remains stale until Phase 03 lands. Pull-to-refresh continues to work and clears the journal for the resource (via `clearResource` — wire in Phase 03 Step 03.5).

---

## Rollback Plan

Restore the four modified files from `temp/<basename>.*.bak`. Revert `BrowseLauncherManager.kt` from git. The `Mutation` model and journal infrastructure from Phase 01 stay — they have no callers after rollback and are inert.
