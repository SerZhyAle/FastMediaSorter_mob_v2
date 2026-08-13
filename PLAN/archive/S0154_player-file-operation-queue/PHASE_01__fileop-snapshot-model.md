# Phase 01 — File Operation Snapshot Model

**Strategic spec:** [`../S0154_player-file-operation-queue.md`](../S0154_player-file-operation-queue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-11
**Completed:** 2026-05-11

---

## Objective

Introduce a self-contained, immutable snapshot value describing a queued player file operation (move-to-resource, move-to-path, delete, rename), capturing everything needed to execute it later without reading "current file" state. No queue, no UI changes yet.

---

## Prerequisites

- [ ] Pre-Implementation Blocker S0152 → `Verified` (see INDEX).
- [ ] Working tree is clean or on a feature branch.
- [ ] `ui/player/fileops/` package does not yet exist (this phase creates it).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperation.kt` | New | ≤ 140 |

---

## Steps

### Step 01.1 — Create `PlayerFileOperation` sealed hierarchy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperation.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create package `com.sza.fastmediasorter.ui.player.fileops`. Add a sealed class `PlayerFileOperation` with these immutable subclasses, each holding the full snapshot of the source file at enqueue time (`sourcePath: String`, `sourceName: String`, `sourceCredentialsId: Long?`) plus operation-specific params:
> - `MoveToResource(... , destination: MediaResource)`
> - `MoveToPath(... , destinationPath: String)`
> - `Delete(...)` — also carry a `softDeleteAllowed: Boolean` flag computed at enqueue time via `DeletePathPolicy.canUseSoftDelete(sourcePath)`.
> - `Rename(... , newName: String)`
> Add a stable `id: String` (e.g. `UUID.randomUUID().toString()`) and a `displayName: String` (defaults to `sourceName`) on the base class for use in progress / error messages. No Android UI imports — domain model imports only.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperation.kt` exists.
- `Grep` — `sealed class PlayerFileOperation` matches exactly once.
- `Grep` — each of `class MoveToResource`, `class MoveToPath`, `class Delete`, `class Rename` matches once.
- `Grep` — `import android.` does not match in this file.

**Status:** `[x]` done

---

### Step 01.2 — Add a `MediaFile`/`MediaResource` → `PlayerFileOperation` factory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperation.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the same file add `companion object` factory functions that build each subclass from `(currentFile: MediaFile, currentResource: MediaResource?)` plus the operation-specific argument (`destination`, `destinationPath`, or `newName`). For `Delete`, compute `softDeleteAllowed` inside the factory. These factories are the single place the snapshot is taken — callers must not construct subclasses directly elsewhere.

**Verification:**

- `Grep` — `companion object` matches once in `PlayerFileOperation.kt`.
- `Grep` — `fun moveToResource(` and `fun moveToPath(` and `fun delete(` and `fun rename(` each match once.

**Status:** `[x]` done

---

### Step 01.3 — Add a `toDomainFileOperation()` mapper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/fileops/PlayerFileOperation.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add an extension or member `fun PlayerFileOperation.toDomainFileOperation(settings: AppSettings): FileOperation` that maps the snapshot to the existing `com.sza.fastmediasorter.domain.usecase.FileOperation` (`Move` for both move variants — destination as a `MediaResource`-derived path or a raw path `File`; `Delete` with `softDelete = softDeleteAllowed`; `Rename` via the existing rename operation type). Use the same network-aware `File` wrapper logic that `FileOperationsHandler.createNetworkAwareFile` uses — extract that helper into this file or a small `ui/player/fileops/NetworkAwareFiles.kt` and have both call sites use it. Reading `settings` (overwrite flags) is allowed here since it is passed in, not pulled from state.

**Verification:**

- `Grep` — `fun PlayerFileOperation.toDomainFileOperation` or `fun toDomainFileOperation` matches once.
- `Grep -n "Log\.d\("` returns zero hits in `PlayerFileOperation.kt`.
- Project compiles — run `/build`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `PlayerFileOperation.kt` (and `NetworkAwareFiles.kt` if extracted) via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`; new classes have `role` + `status` set via `set.ps1`.

---

## Handoff Notes to Next Phase

`PlayerFileOperation` is the only currency the queue carries. Phase 02 builds a FIFO + sequential consumer over it; it must never read player "current state" — everything it needs is inside the snapshot or passed explicitly (`AppSettings`).

---

## Rollback Plan

Revert phase commit(s) — new file only, no data migration or user-facing surface changed.
