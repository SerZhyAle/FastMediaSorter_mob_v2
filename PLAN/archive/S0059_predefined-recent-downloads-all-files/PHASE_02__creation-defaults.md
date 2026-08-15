# Phase 02 — Creation Defaults

**Strategic spec:** [`../S0059_predefined-recent-downloads-all-files.md`](../S0059_predefined-recent-downloads-all-files.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Apply `defaultAllFilesForPredefined` at every code path that constructs a Recent or Downloads `MediaResource` for the first time, so a freshly provisioned database (or freshly added Downloads row) is born with `allFiles = true`. No migration of existing rows here — that lives in Phase 03.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCase.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ScanLocalFoldersUseCase.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceVirtualCoordinator.kt` | Modified | ≤ 350 |

> All three files are well below 500 lines today; no backup step required.

---

## Steps

### Step 02.1 — Provisioning: Recent inherits classifier default

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCase.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> In `ProvisionDefaultResourcesUseCase`, find the call site that creates the Recent virtual resource (currently passes `path = LocalMediaScanner.VIRTUAL_PATH_RECENT` into `createVirtualResource`). The `createVirtualResource` helper currently hard-codes `allFiles = false`. Add a new parameter `allFiles: Boolean = false` to that helper, default `false`. At the Recent call site pass `allFiles = PredefinedResourceClassifier.defaultAllFilesForPredefined(LocalMediaScanner.VIRTUAL_PATH_RECENT, ResourceType.LOCAL) ?: false`. All other call sites (All Music / All Videos / All Images / All Documents / Camera Photos) keep their existing default — do not change them. Add the import for `PredefinedResourceClassifier`.

**Verification:**

- `Grep` — `import com.sza.fastmediasorter.util.PredefinedResourceClassifier` present in the file.
- `Grep` — `PredefinedResourceClassifier.defaultAllFilesForPredefined\(LocalMediaScanner.VIRTUAL_PATH_RECENT` present.
- `Grep` — `allFiles = false` no longer appears inside `createVirtualResource` body (helper now reads from its parameter).
- `Grep` — `allFiles: Boolean = false` matches at least once in the helper signature line.

**Status:** `[ ]` not done

---

### Step 02.2 — Local-folders scan: Recent + Downloads inherit classifier default

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ScanLocalFoldersUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `ScanLocalFoldersUseCase`, the Recent virtual currently sets `allFiles = settings.allFiles`; folders coming from `MediaStoreRepository.getStandardFolders() + getFoldersWithMedia(..)` set `allFiles = settings.allFiles` for every entry. Replace both with a classifier-aware default:
> - For Recent: use `PredefinedResourceClassifier.defaultAllFilesForPredefined(VIRTUAL_PATH_RECENT, ResourceType.LOCAL) ?: settings.allFiles`.
> - For each `folderInfo` in the merged list: use `PredefinedResourceClassifier.defaultAllFilesForPredefined(folderInfo.path, ResourceType.LOCAL) ?: settings.allFiles`.
> Do not change behavior for non-predefined folders — they still inherit `settings.allFiles`. Add the import for `PredefinedResourceClassifier`.

**Verification:**

- `Grep` — `import com.sza.fastmediasorter.util.PredefinedResourceClassifier` present.
- `Grep` — `PredefinedResourceClassifier.defaultAllFilesForPredefined` matches at least twice in the file (Recent block + folder loop).
- `Grep` — `allFiles = settings.allFiles` does **not** appear naked any more (every occurrence is wrapped via `?: settings.allFiles`).

**Status:** `[ ]` not done

---

### Step 02.3 — AddResource virtual coordinator: Recent inherits classifier default

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceVirtualCoordinator.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `AddResourceVirtualCoordinator.buildVirtualResource(..)` the final `MediaResource` is built with `allFiles = false`. Replace that literal with `PredefinedResourceClassifier.defaultAllFilesForPredefined(virtualPath, ResourceType.LOCAL) ?: false`. The manual-folder block earlier in the same file (`addManualLocalFolder` flow, line ~150) currently uses `allFiles = settings.allFiles`; replace that with `PredefinedResourceClassifier.defaultAllFilesForPredefined(path, ResourceType.LOCAL) ?: settings.allFiles` so a user adding the Downloads folder via the SAF picker also lands on `true`. Add the import for `PredefinedResourceClassifier`.

**Verification:**

- `Grep` — `import com.sza.fastmediasorter.util.PredefinedResourceClassifier` present.
- `Grep` — `PredefinedResourceClassifier.defaultAllFilesForPredefined\(virtualPath` matches once.
- `Grep` — `PredefinedResourceClassifier.defaultAllFilesForPredefined\(path` matches once (manual folder block).
- `Grep` — `allFiles = false` does not appear naked anywhere in this file (only inside `?: false`).

**Status:** `[ ]` not done

---

### Step 02.4 — Build + dev log

**Files:** all three modified above
**Depends on:** Steps 02.1, 02.2, 02.3

**Prompt for developer:**

> Run `/build` to confirm the project compiles. Then add a dev-log entry via `.\scripts\add_to_dev_log.ps1` for each of the three modified files (one entry per file, target `feature`, description `S0059 phase 02: <file>`).

**Verification:**

- Build succeeds.
- `Grep` for the substring `S0059 phase 02` in `dev/CHANGELOG.md` matches at least three times.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] On a wiped install (or after clearing app data), the freshly provisioned Recent resource has `allFiles = true`. (Manual smoke check; gated by Phase 04 release notes if the user wants it announced.)

---

## Handoff Notes to Next Phase

- All three creation paths now defer to `PredefinedResourceClassifier.defaultAllFilesForPredefined`. Any future predefined catalog gets the right default for free.
- Phase 03 must use the **same** classifier predicates so the migration definition stays in lockstep with the creation defaults.

---

## Rollback Plan

Revert phase commit(s) — no schema or data migration was introduced. New rows created while this phase was active retain their `allFiles = true` value (acceptable: rollback returns the *future* default to false, but does not corrupt existing data).
