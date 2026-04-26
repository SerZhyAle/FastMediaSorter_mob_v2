# Phase 04 — Docs + Catalog Cleanup

**Strategic spec:** [`../spec_vr-stereo-state.md`](../spec_vr-stereo-state.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03 (all)
**Blocks:** nothing — final phase
**Steps done:** 4 / 4
**Started:** 2026-04-27
**Completed:** 2026-04-27

---

## Objective

Update the three FEATURES trilingual mirrors with the stereo isolation improvement, regenerate the
app_v2 catalog, and add dev-log entries for all modified files.

---

## Prerequisites

- [ ] Phases 01, 02, and 03 are all ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | existing |
| `docs/FEATURES_RU.md` | Modified | existing |
| `docs/FEATURES_UK.md` | Modified | existing |
| `dev/CATALOG/app_v2.jsonl` | Modified | generated |
| `dev/CATALOG/app_v2.md` | Modified | generated |

---

## Steps

### Step 4.1 — Update `docs/FEATURES.md` (English)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase (all code phases done)

**Prompt for developer:**

> In `docs/FEATURES.md`, locate the existing bullet for `3D stereo detection` (or equivalent).
> Append the following clarification inline:
>
> ```
> ..correct stereo-mode isolation between files (no bleed from previous file on navigation).
> ```
>
> Also add `stereo` keyword to filename-detection list if a list of recognised tokens is present.

**Verification:**

- `Grep` — `stereo-mode isolation` present in `docs/FEATURES.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-27 — Verification 1/1 PASS. Files: docs/FEATURES.md (+1 token list, isolation note). Dev log pending.

---

### Step 4.2 — Update `docs/FEATURES_RU.md` (Russian)

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 4.1

**Prompt for developer:**

> Mirror the same update in `docs/FEATURES_RU.md`. Append to the stereo-detection bullet:
>
> ```
> ..корректная изоляция стерео-режима при переходе между файлами (нет утечки от предыдущего файла).
> ```
>
> Use `ё` where grammatically required (e.g. `стерео`, `файлов`, `режима` — no `ё` needed here;
> but check surrounding text).

**Verification:**

- `Grep` — `изоляция стерео-режима` present in `docs/FEATURES_RU.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-27 — Verification 1/1 PASS. Files: docs/FEATURES_RU.md (+isolation note). Dev log pending.

---

### Step 4.3 — Update `docs/FEATURES_UK.md` (Ukrainian)

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 4.1

**Prompt for developer:**

> Mirror the same update in `docs/FEATURES_UK.md`. Append to the stereo-detection bullet:
>
> ```
> ..коректна ізоляція стерео-режиму при переході між файлами (немає витоку від попереднього файлу).
> ```

**Verification:**

- `Grep` — `ізоляція стерео-режиму` present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-27 — Verification 1/1 PASS. Files: docs/FEATURES_UK.md (+isolation note). Dev log pending.

---

### Step 4.4 — Regenerate app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Steps 4.1–4.3

**Prompt for developer:**

> Run the catalog scan and render for the `app_v2` module:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1   -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then add dev-log entries for every file modified across all phases:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/.../StereoDetector.kt"               "spec-dev" "Phase 01: add stereo/mono filename tokens"
> .\scripts\add_to_dev_log.ps1 "app_v2/.../VideoPlayerManager.kt"            "spec-dev" "Phase 02: detection path guard in onTracksChanged"
> .\scripts\add_to_dev_log.ps1 "app_v2/.../PlayerPlaybackCallbackImpl.kt"   "spec-dev" "Phase 02: forward forFilePath to setAutoDetectedStereoMode"
> .\scripts\add_to_dev_log.ps1 "app_v2/.../PlayerViewModel.kt"              "spec-dev" "Phase 02: setAutoDetectedStereoMode accepts forFilePath"
> .\scripts\add_to_dev_log.ps1 "app_v2/.../PlayerStereoModeCoordinator.kt"  "spec-dev" "Phase 02: stale-detection guard in setAutoDetectedStereoMode"
> .\scripts\add_to_dev_log.ps1 "app_v2/.../PlayerManagerInitializer.kt"     "spec-dev" "Phase 03: filter AUTO from video GL stereoMode collector"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md"                           "spec-dev" "Phase 04: stereo isolation note"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md"                        "spec-dev" "Phase 04: stereo isolation note (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md"                        "spec-dev" "Phase 04: stereo isolation note (UK)"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl"                   "spec-dev" "Phase 04: catalog regen after vr-stereo-state"
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and was modified (check `git status`).
- `Glob` — `dev/CATALOG/app_v2.md` exists.
- `Grep` — `StereoDetector` present in `dev/CATALOG/app_v2.md` (confirms regen ran).

**Status:** `[x] done`

**Step Log:**

- 2026-04-27 — Verification 3/3 PASS. Catalog: 802 files scanned, rendered. Dev log: 10 entries added (Phases 01–04). Files: docs/FEATURES.md, docs/FEATURES_RU.md, docs/FEATURES_UK.md, dev/CATALOG/app_v2.jsonl, dev/CATALOG/app_v2.md.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] All three FEATURES files updated.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s). FEATURES files and catalog are regeneratable; no migration risk.
