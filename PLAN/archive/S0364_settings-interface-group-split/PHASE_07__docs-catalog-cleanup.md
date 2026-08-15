# Phase 07 - Docs, catalog cleanup

**Strategic spec:** [`../S0364_settings-interface-group-split.md`](../S0364_settings-interface-group-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** all phases
**Blocks:** none
**Steps done:** 2 / 3
**Started:** 2026-06-05
**Completed:** - (07.3 manual on-device verification pending; ticket in BlockNeedUserTest)

---

## Objective

Finalize the mechanical closure: catalog regen, functionality log, and full-screen verification of both new groups in both orientations.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/FUNCTIONALITY.log` | Appended | n/a |

> `dev/CATALOG/app_v2.jsonl` + `.md` are local gitignored indexes - regenerate, do not commit.

---

## Steps

### Step 07.1 - Catalog sync

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Regenerate the app_v2 catalog after the Kotlin change in Phase 02: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

**Verification:**

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 1/1 PASS. catalog_sync.ps1 -Module app_v2 exit 0 (1333 files, 1640 records scanned + rendered).

---

### Step 07.2 - Functionality log

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 07.1

**Prompt for developer:**

> Append one functionality-log line: `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1 -Id S0364 -Op CHANGE -Description "Split interface settings into two collapsible groups (general + file browser) and adopt 'браузер файлов' terminology"`.

**Verification:**

- `Grep` - `S0364` present in `dev/FUNCTIONALITY.log`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 1/1 PASS. FUNCTIONALITY.log has S0364 CHANGE line ×1 (English-only; corrected an initial CLI-arg Cyrillic mojibake by rewriting the line via UTF-8 script).

---

### Step 07.3 - Full verification, both orientations

**Files:** -
**Depends on:** Step 07.2

**Prompt for developer:**

> Build and drive the settings screen: confirm two collapsible groups ("Общие настройки интерфейса", "Интерфейс браузера файлов") appear in portrait AND landscape; every pre-split row is present and functional; both groups remember collapse state across app restart; settings-search jumps into and expands the correct group. Insert one `Timber.d("S0364: settings interface groups split shown")` at the settings-fragment view-setup entry per CLAUDE.md Debug Verification Tags (ticket enters BlockNeedUserTest).

**Verification:**

- `/build` standardDebug compiles.
- `Grep` - `Timber.d("S0364:` present exactly once in the settings fragment/helper.
- Manual on-device: both groups visible, collapsible, state persists, no row lost (portrait + landscape).

**Status:** `[~] in progress`

**Step Log:**

- 2026-06-05 - Build + tag PASS. `.\a.ps1 dq` standardDebug BUILD SUCCESSFUL. `Timber.d("S0364: settings interface groups split shown")` inserted ×1 at GeneralSettingsFragment.onViewCreated entry (before sectionsHelper.setup()). Manual on-device verification PENDING - ticket flipped to BlockNeedUserTest for device test.

---

## Phase Done Criteria

- [~] Every `Step 07.*` above is `[x] done`. (07.1/07.2 done; 07.3 build+tag done, manual device test pending)
- [x] `dev/CHANGELOG.md` has an entry for every modified file (via `add_to_dev_log.ps1` across phases).
- [x] `docs/FEATURES*.md` wording aligned (Phase 06).
- [x] Spec ticket advanced to `BlockNeedUserTest` for device verification, then `/spec-check S0364`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Catalog regen and functionality log are non-destructive. Revert the S0364 debug tag together with any status change out of BlockNeedUserTest.
