# Phase 03 — docs-catalog-cleanup

**Strategic spec:** [`../S0126_image-editor-output-autoname.md`](../S0126_image-editor-output-autoname.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Update the trilingual feature docs to reflect the unified naming formula, regenerate the class catalog, and close the dev log.

---

## Prerequisites

- [ ] Phases 01 and 02 are ✅ Done.
- [ ] Working tree is clean or on the same feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |

Landscape parity: no layout XML touched.

---

## Steps

### Step 3.1 — Update docs/FEATURES.md §5 Image Viewer

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, §5 Image Viewer, find the bullet that describes Crop, Crop to file, and Compressed copy:
>
> ```
> - Crop (overwrite original), Crop to file (save fragment as new file), and Compressed copy ...
> ```
>
> Also find the Draw annotations bullet:
>
> ```
> The new file is named `<original>_draw_YYMMDD_hhmm.<ext>` ...
> ```
>
> Apply the following two changes:
>
> 1. In the crop/compress bullet — append a sentence at the end of the bullet:
>    `Output files for Crop to file and Compressed copy are named \`<original>_crop-YYMMDD-hhmm.<ext>\` and \`<original>_compress-YYMMDD-hhmm.<ext>\` respectively.`
>
> 2. In the Draw annotations bullet — replace `\`<original>_draw_YYMMDD_hhmm.<ext>\`` with `\`<original>_draw-YYMMDD-hhmm.<ext>\`` (hyphen instead of underscores between operation and timestamp, and between date and time).

**Verification:**

- `Grep` — `_draw-YYMMDD-hhmm` matches in `docs/FEATURES.md`.
- `Grep` — `_draw_YYMMDD_hhmm` returns zero hits in `docs/FEATURES.md`.
- `Grep` — `_compress-YYMMDD-hhmm` matches in `docs/FEATURES.md`.
- `Grep` — `_crop-YYMMDD-hhmm` matches in `docs/FEATURES.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. Files: docs/FEATURES.md (line 125-126 updated). Dev log pending Step 3.4.

---

### Step 3.2 — Update docs/FEATURES_RU.md §5

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 3.1

**Prompt for developer:**

> In `docs/FEATURES_RU.md`, §5, locate the equivalent bullets for Crop/Compress and Draw. Apply the same naming-formula updates as Step 3.1 — the formula text (`_crop-`, `_compress-`, `_draw-`, `YYMMDD-hhmm`) is identical across all three files; surrounding descriptive text is in Russian.

**Verification:**

- `Grep` — `_draw-YYMMDD-hhmm` matches in `docs/FEATURES_RU.md`.
- `Grep` — `_draw_YYMMDD_hhmm` returns zero hits in `docs/FEATURES_RU.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 2/2 PASS. Files: docs/FEATURES_RU.md (line 122-123 updated). Dev log pending Step 3.4.

---

### Step 3.3 — Update docs/FEATURES_UK.md §5

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 3.2

**Prompt for developer:**

> In `docs/FEATURES_UK.md`, §5, apply the same naming-formula updates as Steps 3.1–3.2. Surrounding text is in Ukrainian.

**Verification:**

- `Grep` — `_draw-YYMMDD-hhmm` matches in `docs/FEATURES_UK.md`.
- `Grep` — `_draw_YYMMDD_hhmm` returns zero hits in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 2/2 PASS. Files: docs/FEATURES_UK.md (line 122-123 updated). Dev log pending Step 3.4.

---

### Step 3.4 — Catalog regen and dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Steps 3.1–3.3

**Prompt for developer:**

> 1. Regenerate the catalog:
>    ```powershell
>    pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
>    pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
>    ```
> 2. Set role and status for the new class via:
>    ```powershell
>    pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class ImageEditorFileNamer -Role "utility" -Status "active"
>    ```
> 3. Add dev log entries for every file modified across all phases:
>    ```powershell
>    .\scripts\add_to_dev_log.ps1 "app_v2/.../ImageEditorFileNamer.kt"         "S0126" "Phase 01: add shared filename generator"
>    .\scripts\add_to_dev_log.ps1 "app_v2/.../ImageCropManager.kt"             "S0126" "Phase 02: use ImageEditorFileNamer for crop/compress naming"
>    .\scripts\add_to_dev_log.ps1 "app_v2/.../ImageDrawOverlayManager.kt"      "S0126" "Phase 02: use ImageEditorFileNamer for draw naming"
>    .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md"                           "S0126" "Phase 03: update output filename examples"
>    .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md"                        "S0126" "Phase 03: update output filename examples (RU)"
>    .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md"                        "S0126" "Phase 03: update output filename examples (UK)"
>    ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` modified timestamp is today.
- `Grep` — `ImageEditorFileNamer` matches in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 2/2 PASS. Catalog regenerated via `scan.ps1` + `render.ps1` (991 records). Role/status set on `ImageEditorFileNamer` (utility / new) via `set.ps1`. Dev log entries added for FEATURES.md (EN/RU/UK) and catalog files. Code-file dev log entries from Phases 01–02 already present.

---

## Phase Done Criteria

- [x] Steps 3.1–3.4 above are `[x] done`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] All trilingual feature docs contain `_draw-YYMMDD-hhmm` and `_compress-YYMMDD-hhmm`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Run `/spec-check S0126` after all phases are done.

---

## Rollback Plan

Revert phase commit(s) — docs-only changes, no code or data impact.
