# Phase 05 - Drive rewire

**Strategic spec:** [`../S0406_unified-settings-backup.md`](../S0406_unified-settings-backup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Retarget Google Drive backup/restore onto the unified builder/applier so both storages share one payload and apply path. Drive use cases already compile in all flavors (cloud client is main-safe); no source-set split is introduced.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupToGoogleDriveUseCase.kt` | Modified | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RestoreFromGoogleDriveUseCase.kt` | Modified | ≤ 220 |

---

## Steps

### Step 05.1 - Backup builds payload via BuildBackupPayloadUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupToGoogleDriveUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace inline payload assembly (settings/resources/favorites/scheduled-ops collection + `BackupMapper.toBackupPayload`) with a single call to `BuildBackupPayloadUseCase`. Keep auth check, folder create, README upload, timestamped `backup_YYMMDD-HHmm.json` upload, and `BackupResult`. Inject `BuildBackupPayloadUseCase`; drop repositories that become unused.

**Verification:**

- `Grep` - `buildBackupPayloadUseCase` present.
- `Grep` - `BackupMapper.toBackupPayload` absent in this file.
- `Grep` - `generateFileName()` still present.

**Status:** `[ ]` not done

---

### Step 05.2 - Restore applies payload via ApplyBackupPayloadUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RestoreFromGoogleDriveUseCase.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Keep `downloadAndParseBackup()` and `getBackupInfo()`. Replace the inline apply logic (settings/resources/favorites/scheduled-ops merge) in `invoke()` with a call to `ApplyBackupPayloadUseCase(payload)`, mapping its `RestoreSummary` into the existing `RestoreResult`. Remove now-dead private merge helpers if unused.

**Verification:**

- `Grep` - `applyBackupPayloadUseCase` present.
- `Grep` - `RestoreResult(` still present (public result shape kept).
- `Grep` - `isDuplicateResource` absent if no longer referenced.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for both files.

---

## Handoff Notes to Next Phase

Both storages now read/write the identical payload through one build/apply pair. Phase 06 proves round-trip parity by test.

---

## Rollback Plan

Revert phase commit - Drive path reverts to inline assembly; use cases stay.
