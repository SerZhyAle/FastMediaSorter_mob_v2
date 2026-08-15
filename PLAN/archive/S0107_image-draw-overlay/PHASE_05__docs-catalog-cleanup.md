# Phase 05 — Docs, Catalog, Cleanup

**Strategic spec:** [`../S0107_image-draw-overlay.md`](../S0107_image-draw-overlay.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** all previous phases
**Blocks:** —
**Steps done:** 1 / 4
**Started:** 2026-05-09
**Completed:** —

---

## Objective

Update user-facing feature documentation (EN/RU/UK), regenerate the class catalog, remove all `Timber.d("S0107:` debug tags, and advance the spec to `Verified`.

---

## Prerequisites

- [ ] Phases 01–04 are all ✅ Done.
- [ ] Manual test completed: enter Draw Mode → draw with brush + rectangle + eraser → save → confirm new file appears in player and original is intact.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | +3 lines |
| `docs/FEATURES_RU.md` | Modified | +3 lines |
| `docs/FEATURES_UK.md` | Modified | +3 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/...` (all S0107 Timber tags) | Modified | — |

---

## Steps

### Step 5.1 — Update `docs/FEATURES.md` + `_RU.md` + `_UK.md`

**Files:**
- `docs/FEATURES.md`
- `docs/FEATURES_RU.md`
- `docs/FEATURES_UK.md`

**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, under section **5. Image Viewer**, add a bullet:
>
> `- **Draw annotations**: Activate "Draw" from the image overflow menu to enter draw mode. Select from a brush, rectangle, or eraser tool with a 7-color palette (white, black, gray, red, blue, green, yellow). Tap "Save as new file" to merge annotations onto the image and save it as a new file (original untouched). The new file is named `<original>_draw_YYMMDD_hhmm.<ext>` and the player navigates to it automatically. For read-only sources, the file is saved to the device Downloads folder.`
>
> In `docs/FEATURES_RU.md`, under the corresponding section, add the equivalent Russian bullet. In `docs/FEATURES_UK.md`, add the Ukrainian bullet.

**Verification:**

- `Grep` — `Draw annotations` present in `docs/FEATURES.md`.
- `Grep` — `draw_overlay` or `Рисування` or `Малювання` present in `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md` respectively (or the translated equivalent).

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS. Files: FEATURES.md, FEATURES_RU.md, FEATURES_UK.md. Dev logs recorded.

---

### Step 5.2 — Remove all `Timber.d("S0107:` debug tags

**Files:** all `.kt` files touched in Phases 01–04

**Depends on:** Step 5.1

**Prompt for developer:**

> Run: `grep -rn 'Timber.d("S0107:' app_v2/src/` to locate all debug tags. Remove every matching line. Confirm with a second `grep` that zero hits remain. Commit the removal together with Step 5.1 in the same commit or as an adjacent commit tagged `chore(S0107): remove debug tags`.

**Verification:**

- `Grep` — `Timber\.d\("S0107:` returns zero hits across all `.kt` files under `app_v2/src/`.

**Status:** `[ ]` not done

> ⚠️ BLOCKED until on-device manual test is completed. Timber.d tags were restored — do not remove them before the user confirms the feature works on device.

---

### Step 5.3 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`

**Depends on:** Step 5.2

**Prompt for developer:**

> Run:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> For the two new files (`ImageDrawOverlayManager.kt`, `MergeDrawOverlayUseCase.kt`), set `role` and `status` via `set.ps1`:
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class ImageDrawOverlayManager -Role "draw-overlay ui manager" -Status active
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class MergeDrawOverlayUseCase -Role "image merge use case" -Status active
> ```
> Commit the updated `app_v2.jsonl` and `app_v2.md`.

**Verification:**

- `Grep` — `ImageDrawOverlayManager` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `MergeDrawOverlayUseCase` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[~] in progress`

---

### Step 5.4 — Advance spec status and run string locale audit

**Files:** `PLAN/S0107_image-draw-overlay.md`, `PLAN/spec-catalog.jsonl` (via script only)

**Depends on:** Steps 5.1–5.3

**Prompt for developer:**

> Run the string locale audit for the new S0107 keys:
> ```powershell
> pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_overlay"
> ```
> Fix any missing translations before proceeding. Exit code 0 required.
>
> Then advance the spec:
> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0107 -Status Implemented
> ```
> Update `**Status:**` in `PLAN/S0107_image-draw-overlay.md` to `Implemented`.
>
> Run `/spec-check S0107` to trigger the final audit. If it returns `Verified`, the status will be set automatically; otherwise address the findings.

**Verification:**

- `Grep` — `pwsh -File scripts/check_strings_localized.ps1` exit code 0 (no missing keys).
- `Grep` — `"status":"Implemented"` or `"status":"Verified"` returned by `select.ps1 -Id S0107 -Format json`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 5.*` above is `[x] done`.
- [ ] `Timber.d("S0107:` tags: zero hits.
- [ ] String locale audit: exit code 0.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [ ] `dev/CHANGELOG.md` has entries for every file modified in Phases 01–05 (via `add_to_dev_log.ps1`).
- [ ] `/spec-check S0107` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Doc-only changes. No data migration. Revert the FEATURES.md edits and the catalog jsonl update if needed.
