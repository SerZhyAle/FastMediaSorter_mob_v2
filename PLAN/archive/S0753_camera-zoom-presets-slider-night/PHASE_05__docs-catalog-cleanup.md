# Phase 05 - Docs & catalog cleanup

**Strategic spec:** [`../S0753_camera-zoom-presets-slider-night.md`](../S0753_camera-zoom-presets-slider-night.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-27
**Completed:** 2026-06-27

---

## Objective

Record the delivered capability, regenerate the class catalog, run the string and quality gates, insert the device-test debug tag, and hand the ticket to on-device verification.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done and the project builds.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | - |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified (debug tag) | ≤ 675 |

---

## Steps

### Step 05.1 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` (consult the script header for required fields) describing the delivered capability: "In-app camera adds zoom presets 0.5-30x with a draggable zoom slider, overlay controls legible on any background, and a device-gated photo night mode." Do NOT edit `docs/FEATURES*.md` - that is `/skill-release`-owned (CLAUDE.md Rule 11). Strategic §8 holds the showcase sentence for the eventual release diff.

**Verification:**

- `Grep` - a new line in `docs/ALL_FEATURES.jsonl` mentions `zoom` and `night`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification PASS (record camera.camera-zoom-presets-zoom-slider-night-mode added via close-and-log -FuncOp ADD, flavors standard/lite/photos/legacy, spec S0753; validate.ps1 exit 0). Files: docs/ALL_FEATURES.jsonl.

---

### Step 05.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to pick up the new test class and any new helper symbols (linear-zoom, night-mode). These indexes are gitignored - regenerate, do not commit.

**Verification:**

- `Grep` - catalog regenerated via close-and-log catalog scan+render; production camera classes present (`CameraCaptureActivity` = 1 hit). Test sources (`src/test`) are not indexed by design, so `CameraRuntimeCapabilitiesTest` is intentionally absent - predicate adjusted from the plan's optimistic assumption.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification PASS (catalog_sync scan 40.6s + render 2.8s via close-and-log; CameraCaptureActivity present in app_v2.jsonl; test class not catalogued by design). Files: dev/CATALOG/app_v2.jsonl + .md (gitignored, regenerated).

---

### Step 05.3 - Run string + quality gates and dev log

**Files:** (validation only) + `dev/CHANGELOG.md` via script
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "camera_control_night"` (exit 0 required). Run `scripts/quality/assert-neuroslop.ps1` (via `post-change.ps1`) to confirm no hardcoded layout hex / lifecycle-unsafe collection slipped in. Ensure a dev-log entry exists for the ticket's logical change set via `.\scripts\add_to_dev_log.ps1` / `close-and-log.ps1 -DevLogs` (one entry per logical change, batched - not per file).

**Verification:**

- `check_strings_localized.ps1 -KeyPrefix "camera_control_night"` exits 0.
- `assert-neuroslop.ps1` reports no new violations.
- `Grep` - `dev/CHANGELOG.md` contains an `S0753`-related entry.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 3/3 PASS (check_strings_localized camera_control_night EN/RU/UK OK exit 0; assert-neuroslop all axes within baseline exit 0, layout-colors delta 0; dev/CHANGELOG.md has 13 S0753 entries). Dev log batched via close-and-log (5 logical entries).

---

### Step 05.4 - Insert the device-test debug tag and request verification

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Back up `CameraCaptureActivity.kt` to `temp/` first (Rule 5, > 500 LOC). The ticket is about to enter `BlockNeedUserTest`, so per CLAUDE.md add exactly one `Timber.d("S0753: night mode toggled enabled=$on")` at the night-toggle click handler (the single changed-flow entry). One tag, not per modified line; the `S0753:` prefix is reserved for this temporary probe - never reuse it in `Timber.i/w/e`. Do not add tags for the zoom-preset / slider flows (per-phase probes break the gate). After building, transition the ticket: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0753 -Status BlockNeedUserTest -StatusNote 'Verify on a real device (AVD insufficient for camera, per S0545): presets 0.5-30x clamp to lens with a reachable max; zoom slider drags and stays in sync with presets/pinch; overlay controls legible on white and black scenes; night-mode toggle shows only on supporting lenses (photo), brightens a dark scene, hidden in video.'`

**Verification:**

- `Grep` - exactly one `Timber.d("S0753:` line in `app_v2/src/**`.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0753 -Format json` shows `BlockNeedUserTest`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 2/2 PASS (exactly one `Timber.d("S0753:` at CameraCaptureActivity.kt:251 night-toggle entry, inserted in Phase 04 before the final build; status flipped In Progress -> BlockNeedUserTest with device-test StatusNote via close-and-log).

---

## Phase Done Criteria

- [x] Every `Step 05.*` is `[x] done`.
- [x] `docs/ALL_FEATURES.jsonl` validates (433 records); catalog regenerated; string + neuroslop gates pass.
- [x] Exactly one `Timber.d("S0753:` probe present (CameraCaptureActivity.kt:251) - matches `BlockNeedUserTest`.
- [x] Ticket status is `BlockNeedUserTest` with a device-test `-StatusNote`.

---

## Handoff Notes to Next Phase

Final phase. On-device verification follows via `/spec-test-device S0753` or `/spec-sweep`; `/spec-check` flips the strategic status to `Verified` and removes the `S0753:` probe. See INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit; remove the ALL_FEATURES line and the debug tag. Catalog indexes are regenerated, not committed.
