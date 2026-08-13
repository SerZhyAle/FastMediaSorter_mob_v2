# Phase 04 - Local file rewire

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

Retarget local file export/import onto the unified payload (JSON), keeping legacy XML import for backward compatibility.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExportSettingsUseCase.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt` | Modified | ≤ 640 |

---

## Steps

### Step 04.1 - Export unified JSON to Downloads

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExportSettingsUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the hand-rolled XML builder with: obtain `BackupPayload` from `BuildBackupPayloadUseCase`, serialize via Gson pretty-printing, write through the existing `writeToDownloads(..)` to `FastMediaSorter_backup.json` with MIME `application/json`. Keep the Android 10+/legacy write branches. Inject `BuildBackupPayloadUseCase`; drop now-unused repositories if they become dead.

**Verification:**

- `Grep` - `FastMediaSorter_backup.json` present.
- `Grep` - `buildBackupPayloadUseCase` present.
- `Grep` - `application/json` present in `ExportSettingsUseCase.kt`.
- `Grep -n "Log\.d\("` - zero hits in file.

**Status:** `[ ]` not done

---

### Step 04.2 - Import with JSON/XML auto-detection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Read the stream into text, trim leading whitespace, and branch: first non-space char `{` → parse `BackupPayload` (Gson lenient) and delegate to `ApplyBackupPayloadUseCase`; otherwise → run the existing legacy XML parser path unchanged (back-compat per research/04). For auto file lookup query `FastMediaSorter_backup%.json` first, then fall back to legacy `FastMediaSorter_export%.xml`. Keep explicit SAF-URI handling, detecting format by content. Inject `ApplyBackupPayloadUseCase`.

**Verification:**

- `Grep` - `ApplyBackupPayloadUseCase` present in `ImportSettingsUseCase.kt`.
- `Grep` - `FastMediaSorter_backup` present.
- `Grep` - `FastMediaSorterBackup` still present (legacy XML branch retained).
- `Grep -n "Log\.d\("` - acceptable only on pre-existing legacy lines; no new `Log.d` added.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for both files.

---

## Handoff Notes to Next Phase

Local path uses the unified payload. Phase 05 does the same for Drive.

---

## Rollback Plan

Revert phase commit - local export/import reverts to XML; data model and use cases stay.
