# Phase 05 — Consolidate MediaStore delete path

**Strategic spec:** [`../S0209_deletion-trash-overhaul.md`](../S0209_deletion-trash-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — independent technical-debt cleanup (can run in parallel with Phase 03/04 in different commits, but must merge after Phase 02)
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Implement ADR-3: keep one MediaStore-aware hard-delete implementation (in `LocalOperationStrategy`) and remove the duplicate inside `LocalDeleteFileOperation`. Stop routing local deletes through `SmbFileOperationHandler.executeDelete`. Local-path deletes go directly to `LocalOperationStrategy`.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (the contract is in place).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalDeleteFileOperation.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt` | Modified | ≤ 540 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt` | Modified | ≤ 600 |

> `FileOperationUseCase.kt` is ~526 lines — Grep before edit to confirm headroom. `LocalOperationStrategy.kt` may be near the 500-line threshold after Phase 04 — back up before edit.

---

## Steps

### Step 05.1 — Remove duplicate `deleteViaMediaStore` from `LocalDeleteFileOperation`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalDeleteFileOperation.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Read both files. `LocalDeleteFileOperation.deleteViaMediaStore` and `LocalOperationStrategy.deleteViaMediaStore` implement nearly the same logic. Decision: keep the strategy version (it already lives where deletion strategies are dispatched). Delete the operation-level version and any helper methods exclusive to it (e.g. `collectMediaStoreUris` if it duplicates strategy logic).
> `LocalDeleteFileOperation.execute` currently calls `MediaStore.createDeleteRequest` directly and throws `BatchDeletePermissionRequiredException`. Move this responsibility into `LocalOperationStrategy.deleteFile` (the strategy version already throws the same exception — verify identical exception class and re-routing in UI).
> `FileOperationUseCase` constructs `LocalDeleteFileOperation` with `deleteViaMediaStore` lambda for `LocalMoveFileOperation` (line ~101): re-route this lambda to `LocalOperationStrategy.deleteViaMediaStore` directly (inject the strategy into `FileOperationUseCase` or expose the helper as a free function in the strategy).

**Verification:**

- `Grep -c "fun deleteViaMediaStore" app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalDeleteFileOperation.kt` — `expected: 0 | actual: <observed>`.
- `Grep -c "fun deleteViaMediaStore" app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt` — `expected: 1 | actual: <observed>`.
- Target variant compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Removed the duplicate `deleteViaMediaStore` implementation from `LocalDeleteFileOperation` and re-routed `FileOperationUseCase` / move-delete wiring to the single `LocalOperationStrategy.deleteViaMediaStore` implementation.

---

### Step 05.2 — Stop routing local deletes through `SmbFileOperationHandler.executeDelete`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalDeleteFileOperation.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> In `LocalDeleteFileOperation.execute`, the existing `smbFileOperationHandler.executeDelete(otherFiles)` call (current line ~222) is the SMB-handler transit for local paths. Replace it with a direct call into `LocalOperationStrategy` (inject via constructor). The behaviour parity must be preserved: soft-delete branch builds `.trash/<ts>/` per `BaseFileOperationHandler.executeDelete` (already inherited via the strategy chain), hard-delete branch hits `deleteFile`. The phrase "SmbFileOperationHandler" must not appear in this file after the edit.

**Verification:**

- `Grep -c "smbFileOperationHandler" app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/LocalDeleteFileOperation.kt` — `expected: 0 | actual: <observed>`.
- `Grep -n` — `LocalOperationStrategy` constructor injection visible.
- Target variant compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Replaced the SMB-handler transit for local deletes with a local-only handler backed by `LocalOperationStrategy`. Local-path deletes now stay on the local strategy path while preserving batch URI pre-checks and soft-delete result semantics.

---

### Step 05.3 — Refresh catalog and regression scope notes

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Steps 05.1, 05.2

**Prompt for developer:**

> Run `scan.ps1 -Module app_v2` and `render.ps1 -Module app_v2`. For `LocalDeleteFileOperation` set `role` to `domain-operation-leaf` (or equivalent existing convention). Add a brief commit message line listing the test scenarios the team should re-check manually: (a) single hard delete from DCIM, (b) batch hard delete with system dialog, (c) single soft delete with MANAGE_MEDIA granted, (d) batch soft delete after device-rotation.

**Verification:**

- `Grep -n "LocalDeleteFileOperation" dev/CATALOG/app_v2.jsonl` returns ≥ 1 match.
- `expected: scan.ps1 exit 0 | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Catalog refresh completed with `scan.ps1 -Module app_v2` + `render.ps1 -Module app_v2` after the LocalDeleteFileOperation consolidation.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `Grep -rn "smbFileOperationHandler" app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- Local deletes are now isolated to local code paths only. Trash naming + final cleanup are unified. Phase 06 handles the manual "Clear Trash" button + restore to use the single contract.

---

## Rollback Plan

- Revert the phase commits. Routing reverts to the previous SMB-handler transit; functional impact is none, but the dead-code split returns.
