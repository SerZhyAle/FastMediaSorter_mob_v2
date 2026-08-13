# Phase 06 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0008_vr-immersive-controls-panel.md`](../S0008_vr-immersive-controls-panel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all previous phases
**Blocks:** —
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Update the trilingual FEATURES docs with the new VR panel capability. Regenerate `dev/CATALOG/app_v2.jsonl` to include the six new classes. Run `add_to_dev_log.ps1` for any files that were missed in prior phase log entries. Advance the strategic spec `Status` to `Implemented`.

---

## Prerequisites

- [ ] All phases 01–05 are ✅ Done.
- [ ] Project compiles with no errors after all prior phases — run `/build` once more before starting.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | add 1–2 bullets |
| `docs/FEATURES_RU.md` | Modified | add 1–2 bullets |
| `docs/FEATURES_UK.md` | Modified | add 1–2 bullets |
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | auto |
| `PLAN/S0008_vr-immersive-controls-panel.md` | Modified | Status field only |

---

## Steps

### Step 6.1 — Update trilingual FEATURES docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, locate the VR section (search for `VR` or `immersive`). Add a bullet:
>
> ```markdown
> - **Interactive VR control panel**: In immersive VR mode, a full control panel with seek, volume, brightness, audio track selector and stereo-format indicator is available. Controlled by controller or hand ray — no need to exit VR.
> ```
>
> In `docs/FEATURES_RU.md`, add the Russian equivalent (from strategic spec §8):
>
> ```markdown
> - **Интерактивная VR-панель управления**: В иммерсивном VR-режиме доступна полноценная панель с перемоткой, регулятором громкости, яркостью, выбором дорожки и индикатором стерео-формата. Управление осуществляется лучом контроллера или руки — без выхода из VR.
> ```
>
> In `docs/FEATURES_UK.md`, add the Ukrainian equivalent. Translate from the Russian text above — do not leave a placeholder.
>
> Do not alter any existing bullets. Place the new bullet in the same subsection as other VR features.

**Verification:**

- `Grep` — `Interactive VR control panel` (or the EN phrase) found in `docs/FEATURES.md`.
- `Grep` — `Інтерактивна VR-панель` (Ukrainian phrasing) found in `docs/FEATURES_UK.md`.
- `Grep` — `Інтерактивна` OR `VR-панель` found in `docs/FEATURES_UK.md`.
- `Grep` — `Интерактивная VR-панель` found in `docs/FEATURES_RU.md`.

**Status:** `[ ]` not done

---

### Step 6.2 — Regenerate app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 6.1

**Prompt for developer:**

> Run the catalog scanner:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> ```
>
> After the scan completes, open `dev/CATALOG/app_v2.jsonl` and verify that all six new classes appear:
>
> - `VrControllerRayManager`
> - `VrInteractivePanelRenderer`
> - `VrInteractivePanelComposer`
> - `VrInteractivePanelDriver`
> - `VrRayPanelHitTester`
> - `VrPanelHitZoneResolver`
>
> For each new class, set `role` and `status` using `dev/CATALOG/scripts/set.ps1` (see `dev/CATALOG/README.md`):
>
> | Class | Role | Status |
> |-------|------|--------|
> | `VrControllerRayManager` | NDC → MotionEvent bridge for Touch controller aim ray | Active |
> | `VrInteractivePanelRenderer` | Owns OpenXR panel swapchain and bitmap upload | Active |
> | `VrInteractivePanelComposer` | Canvas painter for interactive VR panel; defines hit zones | Active |
> | `VrInteractivePanelDriver` | State machine and auto-hide controller for GL panel | Active |
> | `VrRayPanelHitTester` | Ray-plane intersection: NDC → UV on panel texture | Active |
> | `VrPanelHitZoneResolver` | UV → zone ID and seek fraction resolver | Active |
>
> Commit the updated `app_v2.jsonl` and `app_v2.md` together.

**Verification:**

- `Grep` — `VrInteractivePanelComposer` found in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `VrRayPanelHitTester` found in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `VrPanelHitZoneResolver` found in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `VrControllerRayManager` found in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 6.3 — Run dev log entries for all touched files

**Files:** `dev/CHANGELOG.md` (via script — do not edit directly)
**Depends on:** Step 6.2

**Prompt for developer:**

> Run the following commands in sequence. These cover every file touched across all six phases (skip any that were already run at the end of a phase):
>
> ```powershell
> # Phase 01 files
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControlOverlayManager.kt" "feature" "Phase 01: extend VR control overlay with volume/brightness/speed/track/format controls"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackCommand.kt" "feature" "Phase 01: add CycleAudioTrack, SetPlaybackSpeed, CycleStereoFormat commands"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "feature" "Phase 01: add VR overlay string keys"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "feature" "Phase 01: add VR overlay string keys (RU)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "feature" "Phase 01: add VR overlay string keys (UK)"
> # Phase 02 files
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/cpp/OpenXrNative.cpp" "feature" "Phase 02: add controller aim-ray GL rendering and onControllerPointerMove JNI callback"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/XrInputCallback.kt" "feature" "Phase 02+04: add onControllerPointerMove and onControllerPanelHover default methods"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt" "feature" "Phase 02+03: expose nativeSetControllerRayEnabled and panel swapchain JNI"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControllerRayManager.kt" "feature" "Phase 02: new VrControllerRayManager (NDC → MotionEvent bridge for Touch controller)"
> # Phase 03 files
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudState.kt" "feature" "Phase 03: add panel-specific fields (brightness, speed, track, panelVisible, hoveredZoneId, seekDragFraction)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelRenderer.kt" "feature" "Phase 03: new VrInteractivePanelRenderer"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelComposer.kt" "feature" "Phase 03: new VrInteractivePanelComposer"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelDriver.kt" "feature" "Phase 03+05: new VrInteractivePanelDriver; implements VrHudSink panel methods"
> # Phase 04 files
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrRayPanelHitTester.kt" "feature" "Phase 04: new VrRayPanelHitTester"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrPanelHitZoneResolver.kt" "feature" "Phase 04: new VrPanelHitZoneResolver"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt" "feature" "Phase 04: wire hit-test into onControllerPointerMove; emit onControllerPanelHover"
> # Phase 05 files
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" "feature" "Phase 05: full zone dispatch table, seek-drag debounce, live state feed to panel"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudSink.kt" "feature" "Phase 05: add panel state feed default methods"
> # Tactical plan files
> .\scripts\add_to_dev_log.ps1 "PLAN/S0008_vr-immersive-controls-panel/INDEX.md" "spec-tech" "Tactical plan for vr-immersive-controls-panel"
> .\scripts\add_to_dev_log.ps1 "PLAN/S0008_vr-immersive-controls-panel.md" "spec-tech" "Move strategic status to Implemented after all phases complete"
> ```

**Verification:**

- `Grep` — `VrInteractivePanelComposer` found in `dev/CHANGELOG.md`.
- `Grep` — `VrRayPanelHitTester` found in `dev/CHANGELOG.md`.
- `Grep` — `VrControllerRayManager` found in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

### Step 6.4 — Advance strategic spec Status to Implemented

**Files:** `PLAN/S0008_vr-immersive-controls-panel.md`
**Depends on:** Step 6.3

**Prompt for developer:**

> Open `PLAN/S0008_vr-immersive-controls-panel.md`. Change the frontmatter field:
>
> ```markdown
> **Status:** Tactical
> ```
>
> →
>
> ```markdown
> **Status:** Implemented
> ```
>
> Also ensure the tactical plan link line is present (should have been added when the tactical plan was created):
>
> ```markdown
> **Tactical plan:** `PLAN/S0008_vr-immersive-controls-panel/INDEX.md`
> ```
>
> If the link line is absent, add it below the `**Status:**` line.

**Verification:**

- `Grep` — `Status: Implemented` in `PLAN/S0008_vr-immersive-controls-panel.md`.
- `Grep` — `Tactical plan:` link line present in `PLAN/S0008_vr-immersive-controls-panel.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 6.*` above is `[x] done`.
- [ ] `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` each contain the new VR panel bullet.
- [ ] `dev/CATALOG/app_v2.jsonl` contains all six new class entries with `role` and `status` set.
- [ ] `dev/CHANGELOG.md` contains entries for every file in the "Files Touched" tables of Phases 01–05.
- [ ] `PLAN/S0008_vr-immersive-controls-panel.md` `Status: Implemented`.
- [ ] Run `/spec-check vr-immersive-controls-panel` — result must be `Verified` or `Partial` (no `Broken`). If `Partial`, document gaps in INDEX Blockers Log.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

After `/spec-check` returns `Verified`, flip the strategic spec status to `Verified` and close the tactical INDEX (`Status: Done`, `Phases: 6/6 done`).

---

## Rollback Plan

All changes in this phase are additive (docs + catalog entries + status field). No code changes. Rollback = revert the commits; no runtime impact.

---

## Revision History

- **2026-04-26** — by `/spec-update` (`claude-sonnet-4-6`, focus: all, --tactical --apply-all)
  - ACCEPT applied: 0
  - REVIEW applied: 0
  - DISCUSS proposed: 0
  - Clean pass — no findings.
