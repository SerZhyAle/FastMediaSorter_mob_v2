# Phase 06 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0127_image-player-draw-crop-immersive.md`](../S0127_image-player-draw-crop-immersive.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Update the trilingual feature documentation, regenerate the class catalogue, and confirm the dev log lists every modified file.

---

## Prerequisites

- [ ] Phase 05 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (auto) | n/a |

---

## Steps

### Step 06.1 — Update trilingual FEATURES docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, locate `## 5. Image Viewer`. Append a new bullet at the end of that section:
> `- **Immersive Draw and Crop**: When you enter Draw or Crop mode, both command panels and the system bars hide automatically so the image fills the screen. Pinch-to-zoom remains available in Crop mode for precise area selection. Panels and system bars restore when you leave the mode. (S0127)`
> In `docs/FEATURES_RU.md`, locate `## 5. Просмотрщик изображений`. Append the matching Russian bullet:
> `- **Иммерсивный режим Draw и Crop**: при входе в режим рисования или кадрирования обе командные панели и системные строки скрываются автоматически, изображение занимает весь экран. Жест pinch-to-zoom остаётся доступным в режиме Crop для точного выбора области. Панели и системные строки восстанавливаются при выходе. (S0127)`
> In `docs/FEATURES_UK.md`, locate `## 5. Переглядач зображень`. Append the matching Ukrainian bullet:
> `- **Іммерсивний режим Draw і Crop**: при вході в режим малювання або кадрування обидві командні панелі та системні смуги ховаються автоматично, зображення займає весь екран. Жест pinch-to-zoom залишається доступним у режимі Crop для точного вибору ділянки. Панелі та системні смуги відновлюються при виході. (S0127)`

**Verification:**

- `Grep` — `Immersive Draw and Crop` matches once in `docs/FEATURES.md`.
- `Grep` — `Иммерсивный режим Draw и Crop` matches once in `docs/FEATURES_RU.md`.
- `Grep` — `Іммерсивний режим Draw і Crop` matches once in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS. Files: docs/FEATURES.md (+1 LOC), docs/FEATURES_RU.md (+1 LOC), docs/FEATURES_UK.md (+1 LOC). Dev log recorded.

---

### Step 06.2 — Regenerate class catalogue for `app_v2`

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run, in order:
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Then set role/status for the two new entries (`PlayerImmersiveModeManager`, `PlayerImageEditMode`) using `set.ps1` per `dev/CATALOG/README.md` if they show as `unknown`. Roles:
> - `PlayerImmersiveModeManager` — `Hides system bars and command panels for image editor immersive modes (S0127)` / status `new`.
> - `PlayerImageEditMode` — `Active image editor mode enum (NONE, DRAW, CROP) (S0127)` / status `new`.

**Verification:**

- `Grep` — `PlayerImmersiveModeManager` matches at least once in `dev/CATALOG/app_v2.md`.
- `Grep` — `PlayerImageEditMode` matches at least once in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 2/2 PASS. Catalogue regenerated; PlayerImmersiveModeManager and PlayerImageEditMode roles/status filled. Dev log recorded.

---

### Step 06.3 — Audit dev log entries

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has an entry for each modified or created file across Phases 01..05. Missing files: append via `.\scripts\add_to_dev_log.ps1 "<path>" "S0127" "<short description>"`. Do not edit `dev/CHANGELOG.md` manually.
> Required file list:
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/state/PlayerImageEditMode.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageCropManager.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImmersiveModeManager.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerObserverManager.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerImageLoadingCallbackImpl.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerCropDelegate.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/CropOverlayView.kt`
> - `docs/FEATURES.md`
> - `docs/FEATURES_RU.md`
> - `docs/FEATURES_UK.md`
> - `dev/CATALOG/app_v2.jsonl`
> - `dev/CATALOG/app_v2.md`

**Verification:**

- `Grep` — `S0127` matches at least once in `dev/CHANGELOG.md`.
- For every file path listed above: `Grep` for the file's basename in `dev/CHANGELOG.md` returns at least one hit dated `2026-05-09` or later.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 2/2 PASS. dev/CHANGELOG.md has 30 S0127 entries; every required file basename has ≥1 entry.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` BUILD SUCCESSFUL in 35s.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. After this, run `/spec-check S0127`.

---

## Rollback Plan

Documentation-only phase. Revert via git for the docs files; rerun `scan.ps1`/`render.ps1` for catalogue regeneration.
