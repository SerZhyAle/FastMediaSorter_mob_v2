# Phase 04 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0073_player-copy-move-custom-path-button.md`](../S0073_player-copy-move-custom-path-button.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03 (all)
**Blocks:** —
**Steps done:** 5 / 5
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Update `docs/FEATURES.md` and its RU/UK mirrors, regenerate the human-readable catalog, and add dev-log entries for all files modified across all phases.

---

## Prerequisites

- [ ] Phases 01, 02, and 03 are ✅ Done.
- [ ] Working tree is clean (all phase commits are in).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | existing + 1 bullet |
| `docs/FEATURES_RU.md` | Modified | existing + 1 bullet |
| `docs/FEATURES_UK.md` | Modified | existing + 1 bullet |
| `dev/CATALOG/app_v2.md` | Modified | regenerated |

---

## Steps

### Step 04.1 — Update `docs/FEATURES.md`

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase (all prior phases done)

**Prompt for developer:**

> In `docs/FEATURES.md`, find the section that describes player file operations (copy/move in player). Add one bullet:
> ```
> - Player Copy/Move panels include a «..» button to pick any local folder as the destination without leaving the player.
> ```

**Verification:**

- `Grep` — `«..» button` found in `docs/FEATURES.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. Bullet added to FEATURES.md line 103. Dev log recorded.

---

### Step 04.2 — Update `docs/FEATURES_RU.md`

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `docs/FEATURES_RU.md`, find the corresponding section and add:
> ```
> - В панелях «Копировать в» и «Переместить в» плеера добавлена кнопка «..» для выбора произвольной локальной папки назначения без выхода из плеера.
> ```

**Verification:**

- `Grep` — `кнопка «..»` found in `docs/FEATURES_RU.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. Bullet added at line 103. Dev log recorded.

---

### Step 04.3 — Update `docs/FEATURES_UK.md`

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> In `docs/FEATURES_UK.md`, find the corresponding section and add:
> ```
> - У панелях «Копіювати в» та «Перемістити в» плеєра додано кнопку «..» для вибору довільної локальної папки призначення без виходу з плеєра.
> ```

**Verification:**

- `Grep` — `кнопку «..»` found in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. Bullet added at line 103. Dev log recorded.

---

### Step 04.4 — Regenerate catalog (scan + render)

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Run the catalog scan and render scripts:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> This picks up the new `PlayerFolderPickerHandler` class and any API changes to `FileOperationsHandler` and `DestinationButtonsManager`. After render, fill in `role` and `status` for `PlayerFolderPickerHandler` via `set.ps1` if the scan leaves them blank:
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class PlayerFolderPickerHandler -Role "Handles OpenDocumentTree folder-pick requests from player Copy/Move panels" -Status active
> ```

**Verification:**

- `Grep` — `PlayerFolderPickerHandler` found in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. scan.ps1 → 920 files; render.ps1 → 920 records; set.ps1 set role + status=new for PlayerFolderPickerHandler.

---

### Step 04.5 — Dev-log entries for all modified files

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 04.4

**Prompt for developer:**

> Run `add_to_dev_log.ps1` for every file modified across all phases (use the paths and descriptions below):
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt" "S0073" "Add performCopyToPath/performMoveToPath + callback methods"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt" "S0073" "Register folderPickerLauncher; implement onCopyToPathSuccess/onMoveToPathSuccess/onCustomPathPickerRequested"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt" "S0073" "Init PlayerFolderPickerHandler"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerFolderPickerHandler.kt" "S0073" "New: player folder-picker handler for custom copy/move destination"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt" "S0073" "Add «..» button to Copy/Move grids; always show panels"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0073" "Add btn_select_folder_description (EN)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0073" "Add btn_select_folder_description (RU)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0073" "Add btn_select_folder_description (UK)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0073" "Document «..» folder-picker button in player panels"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0073" "Document «..» folder-picker button in player panels (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0073" "Document «..» folder-picker button in player panels (UK)"
> ```

**Verification:**

- `Grep` — `S0073` found at least 10 times in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. 25 occurrences of S0073 found in dev/CHANGELOG.md.

---

## Phase Done Criteria

- [x] Every Step 04.* above is `[x] done`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Flip INDEX.md `Status:` to `Done` and `Phases: 4/4 done`.
- [x] Run `/spec-check S0073` to advance strategic spec to `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) — documentation-only changes; no code or data affected.
