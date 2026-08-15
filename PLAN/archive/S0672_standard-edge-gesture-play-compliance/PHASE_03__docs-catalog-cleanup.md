# Phase 03 - docs-catalog-cleanup

**Strategic spec:** [`../S0672_standard-edge-gesture-play-compliance.md`](../S0672_standard-edge-gesture-play-compliance.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-06-26
**Completed:** 2026-06-26

---

## Objective

Record the shippable capability, refresh the generated catalog/settings docs for the new tile class and the now-enable-ready gesture overlay, and confirm the noLegal silent path is untouched. No behaviour code here.

---

## Prerequisites

- [x] Phase 01 and Phase 02 are ✅ Done.
- [x] Working tree is clean or on a feature branch (DEBUG-v019).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (append) | ≤ +2 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CATALOG/app_v2.md` | Regenerated | - |
| `docs/settings/settings-manifest.json` | Regenerated (if changed) | - |
| `docs/SETTINGS_REFERENCE*.md` | Regenerated (if changed) | - |

---

## Steps

### Step 03.1 - Record the capability in `ALL_FEATURES`

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Append one capability record via `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN-only): the Play `standard` flavor can now reach the edge-gesture family - swipe-triggered screen capture via MediaProjection consent with configurable per-direction actions and an app-launch panel (invisible-strip primary path, specialUse, opt-in), plus a Quick Settings tile fallback that captures without a persistent overlay. Do NOT edit `docs/FEATURES*.md` (owned by `/skill-release`). Validate: `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - the new edge-gesture / QS-tile capability line is present in `docs/ALL_FEATURES.jsonl`.
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-26 - Verification 2/2 PASS. ALL_FEATURES record `screen-capture.standard-edge-gesture-triggers` added (area "Screen Capture", flavors: standard, spec S0672; description notes opt-in / pending Play review). `validate.ps1` EXIT 0 (429 records). `docs/FEATURES*.md` untouched.

---

### Step 03.2 - Regenerate the class catalog with the tile hint

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Regenerate the catalog so the new `ScreenshotGestureTileService` is indexed: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set its role/status and flavor isolation hint: `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1` for `ScreenshotGestureTileService` with `-Role "QS tile fallback trigger for edge-gesture screen capture"` and `-NoFlavors "lite,photos,legacy,noLegal,vr"` (standard-only). These indexes are gitignored - regenerate, do not commit.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*ScreenshotGestureTile*"` returns the class with role + `NoFlavors` set.

**Status:** `[x]` done

**Step Log:**

- 2026-06-26 - Verification PASS. Catalog scanner fixed first (Rule 13): the new `src/standardEdgeTile/java` source set was absent from `scan.ps1` `$srcRoots`, so the tile class did not index - added it. Re-scan: 2039 records, `ScreenshotGestureTileService` indexed; `set.ps1 -Role "QS tile fallback trigger.." -NoFlavors "lite,photos,legacy,noLegal,vr"`; query returns the class with role + NoFlavors.

---

### Step 03.3 - Sync settings docs if the gesture-overlay setting's standard presence changed

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`
**Depends on:** Step 03.1

**Prompt for developer:**

> The gesture-overlay setting rows (`setting_gesture_overlay_*`, `setting_screenshot_gesture_action_*`) become reachable in a `standard` build once the gate is enabled. Per Rule 22, regenerate the settings manifest + reference and update annotations if the setting's presence/behaviour in `standard` changed: run the project's settings-doc generator and `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1`. If the generator reports no delta (the rows were already documented and gating is build-flag-only), record `expected: no delta | actual: no delta` and skip the doc write. No new app setting is introduced by the QS tile (it is a system Quick Settings tile).

**Verification:**

- `scripts/quality/assert-settings-doc-sync.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-26 - Verification PASS. expected: no delta | actual: no delta. `assert-settings-doc-sync.ps1` EXIT 0. No new app setting introduced (the QS tile is a system Quick Settings tile, not an app setting; the gesture-overlay rows were already documented and the change is build-flag gating only).

---

### Step 03.4 - Confirm noLegal silent path untouched and log the change

**Files:** (no edit - verification + dev log)
**Depends on:** Step 03.2, Step 03.3

**Prompt for developer:**

> Confirm the noLegal-exclusive silent path is unchanged: `ScreenshotAccessibilityService` still exists only under `src/noLegal/` and no `isAccessibilityTool` / accessibility-capture code leaked into `src/main`, `src/standardScreenCapture`, or `src/standardEdgeTile` (strategic §2.5 / §11.5). Then add the dev-log entries for the whole ticket via `.\scripts\add_to_dev_log.ps1` (one entry per logical change, batched). The Play Console `specialUse` declaration + demo video, and the S0671-Verified dependency, are external release gates tracked in INDEX Completion Gate - NOT steps here.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*ScreenshotAccessibility*"` shows the class only in the noLegal source set.
- `Grep` - `isAccessibilityTool` does NOT appear under `src/main`, `src/standardScreenCapture`, or `src/standardEdgeTile`.
- `dev/CHANGELOG.md` has an entry covering the ticket's changed files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-26 - Verification 3/3 PASS. `ScreenshotAccessibilityService` + `ScreenshotAccessibilityServiceHolder` exist only under `src/noLegal/java`; `isAccessibilityTool` absent across all of `src/` (incl. `src/main`, `src/standardScreenCapture`, `src/standardEdgeTile`). Ticket dev-log entries recorded (Phase 01/02 batches + scanner fix; Phase 03 + status flip via close-and-log).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `docs/ALL_FEATURES.jsonl` validated (429 records, EXIT 0); `docs/FEATURES*.md` NOT touched.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated with the tile class indexed (after the `scan.ps1` source-root fix).
- [x] `dev/CHANGELOG.md` has entries for the ticket's logical changes (Phase 01/02/03 + scanner fix; status-transition entry via close-and-log).
- [x] noLegal silent path confirmed untouched (`ScreenshotAccessibilityService` noLegal-only; no `isAccessibilityTool` leak).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Remaining gates are external: S0671 -> Verified, the Play Console `specialUse` declaration + demo video, and the on-device BlockNeedUserTest sweep (strip on Android 15 with `-P fms.edgeGestureOverlay=on`; tile with `-P fms.edgeGestureTile=on`).

---

## Rollback Plan

Revert the phase commit: regenerate catalog/settings docs from the reverted source, drop the `ALL_FEATURES` line. Docs-only - no runtime impact.
