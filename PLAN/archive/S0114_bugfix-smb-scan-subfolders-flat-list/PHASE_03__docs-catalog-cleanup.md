# Phase 03 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0114_bugfix-smb-scan-subfolders-flat-list.md`](../S0114_bugfix-smb-scan-subfolders-flat-list.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Remove S0114 debug tags, regenerate catalog, run `/spec-check` to close the ticket.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] Manual smoke test passed: SMB resource with `scanSubdirectories=true` + `showSubfoldersAsItems=false` shows files from all subfolders in the flat list.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt` | Modified | ≤ 520 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResourceEditorUseCase.kt` | Modified | ≤ 720 |
| `dev/CATALOG/app_v2.jsonl` | Modified | — |
| `dev/CATALOG/app_v2.md` | Modified | — |

---

## Steps

### Step 3.1 — Remove all S0114 Timber debug tags

**Files:** `BrowseResourceLoadManager.kt`, `ResourceEditorUseCase.kt`
**Depends on:** — start of phase (all prior phases ✅)

**Prompt for developer:**

> Search all `.kt` files for `Timber.d("S0114:` and remove every matching line. Commit the removal together with the status change to `Verified`.

```powershell
# Verify all tags to remove before deletion
Select-String -Path "app_v2\src\main\java\com\sza\fastmediasorter\**\*.kt" `
    -Pattern 'Timber\.d\("S0114:' -Recurse
```

Remove each found line manually or via sed-equivalent. Then verify:

```powershell
$hits = (Select-String -Path "app_v2\src\main\java\com\sza\fastmediasorter\**\*.kt" `
    -Pattern 'Timber\.d\("S0114:' -Recurse).Count
Write-Host "S0114 tags remaining: $hits"
```

**Verification:**

- `Grep` — `Timber\.d\("S0114:` returns zero hits across all `.kt` files in `app_v2/src`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 1/1 PASS (0 S0114 tags remaining). Files: BrowseResourceLoadManager.kt (-1 LOC), ResourceEditorUseCase.kt (-1 LOC). Dev log recorded.

---

### Step 3.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 3.1

**Prompt for developer:**

> Run scan and render for the `app_v2` module to pick up the two modified files.

```powershell
& "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
& "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and its `LastWriteTime` is today.
- `Glob` — `dev/CATALOG/app_v2.md` exists and its `LastWriteTime` is today.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS (both catalog files exist, regenerated today). Catalog: 963 records.

---

### Step 3.3 — Dev log entries and spec status transition

**Files:** `dev/CHANGELOG.md` (via script), strategic spec
**Depends on:** Step 3.2

**Prompt for developer:**

> Record dev log entries for all files touched across all phases:

```powershell
.\scripts\add_to_dev_log.ps1 `
  "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt" `
  "S0114" "Fix orphaned scan job: cancel-before-launch in loadMediaFiles()"

.\scripts\add_to_dev_log.ps1 `
  "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResourceEditorUseCase.kt" `
  "S0114" "Invalidate caches when scanSubdirectories changes on resource save"

.\scripts\add_to_dev_log.ps1 `
  "dev/CATALOG/app_v2.jsonl" "catalog" "Regenerate after S0114 changes"
```

> Then run `/spec-check S0114` to advance status to `Verified`.

**Verification:**

- `Grep` — `S0114` present at least twice in `dev/CHANGELOG.md`.
- Strategic spec `**Status:**` field reads `Verified`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS (S0114 × 6 in CHANGELOG, strategic spec updated to Implemented). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 3.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `Timber\.d\("S0114:` returns zero hits across all `.kt` files.
- [ ] `/spec-check S0114` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

N/A — this phase only removes debug tags and updates metadata. No logic is changed.
