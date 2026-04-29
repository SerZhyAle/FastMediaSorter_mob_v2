# Phase 07 — Docs & Catalog Cleanup

**Strategic spec:** [`../spec_vr-immersive-hud-gl.md`](../spec_vr-immersive-hud-gl.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Blocks:** none — final phase before `/spec-check`.
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Close the feature: refresh trilingual `docs/FEATURES*.md` per strategic §8, regenerate `dev/CATALOG/app_v2.jsonl` for the newly added VR render/ui classes, backfill any missing dev log entries, and hand off to `/spec-check`.

---

## Prerequisites

Check each before starting Step 1:

- [ ] Phase 06 is `✅ Done`.
- [ ] On-device acceptance test of all strategic §11 criteria passed.
- [ ] `dev/CATALOG/README.md` has been skimmed so the developer knows how to fill `role` + `status` for new classes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified (append ~1 paragraph to VR section) | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | — |
| `dev/CATALOG/app_v2.md` | Regenerated | — |
| `dev/CHANGELOG.md` | Appended via `add_to_dev_log.ps1` (not edited directly) | — |

---

## Steps

### Step 7.1 — Update `docs/FEATURES.md` (English) per strategic §8

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Locate the existing bullet `- **VR Immersive Controls**:` in `docs/FEATURES.md`. Append (or inline, editorial choice) a sentence block in the same style as the surrounding entries:
>
> > Immersive sessions now surface a floating HUD with a progress bar (current position / buffer / duration), plus pop-up indicators for pause, seek, volume, zoom, file, recenter, immersive-mode toggle, and repeat mode. The HUD appears on any controller action and auto-dismisses a few seconds later. Full file-operations and playback-control panels remain in panel-layout mode — exit immersive to open them.
>
> Do NOT create a new top-level bullet. Do NOT duplicate content; merge the new copy with the existing VR paragraph. Wording obeys author style: no `...`, two-dot ellipsis only.

**Verification:**

- `Grep` — pattern `floating HUD` or `pop-up indicators` (author choice) in `docs/FEATURES.md` returns exactly one hit inside the VR section.
- No duplicate VR Immersive Controls bullets.

**Status:** `[x]` done

---

### Step 7.2 — Mirror the update in `docs/FEATURES_RU.md`

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 7.1

**Prompt for developer:**

> Translate the exact same block into Russian and splice it into the VR Immersive Controls paragraph. Use `ё` / `Ё` where grammatically correct. Use `..` in place of any ellipsis.

**Verification:**

- `Grep` — pattern `всплывающий HUD` (or the translator's equivalent phrase picked) in `docs/FEATURES_RU.md` returns exactly one hit.
- `Grep` — pattern `...` (three dots) inside the block returns zero hits.
- `Grep` — pattern `ё` or `Ё` in the block returns at least one hit if the Russian wording would normally contain `ё` (e.g., «всё», «ещё»).

**Status:** `[x]` done

---

### Step 7.3 — Mirror the update in `docs/FEATURES_UK.md`

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 7.2

**Prompt for developer:**

> Translate the same block into Ukrainian. Author style rules (no `...`) apply as in the EN / RU mirrors.

**Verification:**

- `Grep` — pattern (Ukrainian keyword the translator chose for "floating HUD", e.g. `спливаючий HUD`) in `docs/FEATURES_UK.md` returns exactly one hit.
- `Grep` — pattern `...` inside the block returns zero hits.

**Status:** `[x]` done

---

### Step 7.4 — Regenerate the catalog and annotate new classes

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 7.3

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` from the repo root. The scanner picks up the new Kotlin classes:
>
> - `VrHudRenderer` (role: render)
> - `VrHudSceneComposer` (role: render)
> - `VrHudSceneDriver` (role: ui)
> - `VrHudSink` (role: ui)
> - `VrHudState` / `RepeatMode` / `ActionBadge` (role: domain)
>
> For each new class, run `pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class <Fqcn> -Role <role> -Status active` with the matching role label. Consult `dev/CATALOG/README.md` for valid role tokens. Commit the updated `app_v2.jsonl` and `app_v2.md` together with the other phase files.

**Verification:**

- `Grep` — pattern `"VrHudRenderer"` in `dev/CATALOG/app_v2.jsonl` returns exactly one hit.
- `Grep` — pattern `"VrHudSceneComposer"` returns exactly one hit.
- `Grep` — pattern `"VrHudSceneDriver"` returns exactly one hit.
- `Grep` — pattern `"VrHudSink"` returns exactly one hit.
- `Grep` — pattern `"VrHudState"` returns exactly one hit.
- `dev/CATALOG/app_v2.md` regenerated; diff is limited to the new entries (no spurious reordering).

**Status:** `[x]` done

---

### Step 7.5 — Backfill `dev/CHANGELOG.md` entries for every modified file

**Files:** `dev/CHANGELOG.md` (appended via `add_to_dev_log.ps1`)
**Depends on:** Step 7.4

**Prompt for developer:**

> Review the phase-by-phase "Dev log entry added for every file in Files Touched" criteria. Any file that was modified during Phases 01–06 but did not yet get a `dev/CHANGELOG.md` entry — add one now via:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "<path>" "spec-vr-immersive-hud-gl" "<one-line description>"
> ```
>
> Do not edit `dev/CHANGELOG.md` directly.
>
> Also append the tactical-plan authoring entries for this folder:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "PLAN/spec_vr-immersive-hud-gl/INDEX.md" "spec-tech" "Create tactical plan for vr-immersive-hud-gl"
> .\scripts\add_to_dev_log.ps1 "PLAN/spec_vr-immersive-hud-gl/PHASE_01__foundations.md" "spec-tech" "Phase 01: foundations"
> .\scripts\add_to_dev_log.ps1 "PLAN/spec_vr-immersive-hud-gl/PHASE_02__composition-layer.md" "spec-tech" "Phase 02: composition-layer"
> .\scripts\add_to_dev_log.ps1 "PLAN/spec_vr-immersive-hud-gl/PHASE_03__bitmap-upload.md" "spec-tech" "Phase 03: bitmap-upload"
> .\scripts\add_to_dev_log.ps1 "PLAN/spec_vr-immersive-hud-gl/PHASE_04__scene-composer.md" "spec-tech" "Phase 04: scene-composer"
> .\scripts\add_to_dev_log.ps1 "PLAN/spec_vr-immersive-hud-gl/PHASE_05__event-routing.md" "spec-tech" "Phase 05: event-routing"
> .\scripts\add_to_dev_log.ps1 "PLAN/spec_vr-immersive-hud-gl/PHASE_06__transitional-guard.md" "spec-tech" "Phase 06: transitional-guard"
> .\scripts\add_to_dev_log.ps1 "PLAN/spec_vr-immersive-hud-gl/PHASE_07__docs-catalog-cleanup.md" "spec-tech" "Phase 07: docs-catalog-cleanup"
> ```
>
> (The `spec-tech` skill run itself ran these eight lines already when it created the folder — Step 7.5 only deals with file-edit entries from Phases 01–06 that may have been missed.)

**Verification:**

- `Grep` on the latest block of `dev/CHANGELOG.md` — every file path from Phase 01..06 "Files Touched" tables appears at least once.
- No direct edits to `dev/CHANGELOG.md` detected (`git diff` shows only script-appended lines).

**Status:** `[x]` done

---

## Phase Done Criteria

All of the following must hold for this phase to flip to `✅ Done`:

- [ ] Every `Step 7.*` above is `[x] done`.
- [ ] `/spec-check vr-immersive-hud-gl` returns `Verified` (or `Partial` with an explicit triage note).
- [ ] Strategic spec `Status:` advanced by `/spec-check` to `Verified`.
- [ ] Tactical INDEX `Status:` flipped to `Done` and counter reads `7 / 7`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit(s). Code is already merged through Phase 06 and keeps working; only documentation and catalog revert to the pre-phase state. No runtime impact.
