# Phase 05 — docs-catalog-cleanup

**Strategic spec:** [`../S0081_tv-remote-key-coverage.md`](../S0081_tv-remote-key-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01–04
**Blocks:** —
**Steps done:** 2 / 2
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Update trilingual feature docs with the new TV remote remapping and DPAD acceleration capabilities; record dev log entries for all files touched across the entire spec.

---

## Prerequisites

- [ ] All prior phases (01–04) are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | +2 lines |
| `docs/FEATURES_RU.md` | Modified | +2 lines |
| `docs/FEATURES_UK.md` | Modified | +2 lines |

---

## Steps

### Step 5.1 — Update trilingual FEATURES docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, locate section **§2 Media Browsing** (or the closest relevant section covering keyboard/gamepad input). Add two bullets:
>
> - **TV remote key remapping**: Color buttons (Red/Green/Yellow/Blue) and Channel Up/Down on Android TV remotes can be reassigned to any command in Settings → Key bindings, just like keyboard shortcuts. Default actions (Delete/Copy/Move/Rename and Next/Previous file) apply when no custom binding is set.
> - **DPAD hold-to-scroll acceleration**: Holding the DPAD Up or Down on a remote or gamepad switches from single-step to page-jump after a brief delay, making navigation through long file lists significantly faster.
>
> Mirror both bullets in `docs/FEATURES_RU.md` (Russian) and `docs/FEATURES_UK.md` (Ukrainian), in the same section, with `..` (two dots) for any ellipsis in Russian/Ukrainian text and correct `ё`/`Ё` in Russian.

**Verification:**

- `Grep` — "TV remote" (or "ТВ-пульт" / "ТВ-пульт") present in each of the three FEATURES files.
- `Grep` — "DPAD" (or "DPAD" / "d-pad") present in each of the three FEATURES files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: FEATURES.md (+2 bullets), FEATURES_RU.md (+2 bullets), FEATURES_UK.md (+2 bullets). Dev log pending end-of-phase.

---

### Step 5.2 — Dev log entries for all modified files

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 5.1

**Prompt for developer:**

> Run the following commands (one per modified file across all phases of S0081):
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/AndroidManifest.xml" "AndroidManifest.xml" "S0081 Phase 01: add android.software.leanback required=false"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/assets/input/default_bindings.json" "default_bindings.json" "S0081 Phase 02: add TV remote key triggers (PROG_RED/GREEN/YELLOW/BLUE, CHANNEL_UP/DOWN)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt" "BrowseActivity" "S0081 Phase 02: pre-check KeyBindingManager for TV keys before hardcoded fallback"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt" "MainActivity" "S0081 Phase 02: pre-check KeyBindingManager for TV keys before hardcoded fallback"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/common/FocusManager.kt" "FocusManager" "S0081 Phase 03+04: boundary escape + DPAD hold acceleration"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/custom_player_controls.xml" "custom_player_controls.xml" "S0081 Phase 03: close nextFocusLeft/Right loop in player controls"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "FEATURES" "S0081: TV remote remapping and DPAD acceleration"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "FEATURES_RU" "S0081: TV remote remapping and DPAD acceleration (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "FEATURES_UK" "S0081: TV remote remapping and DPAD acceleration (UK)"
> ```

**Verification:**

- `Grep` — `S0081` present at least 9 times in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS (28 S0081 entries in CHANGELOG.md). Files: dev/CHANGELOG.md (+11 entries). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every Step 5.* above is `[x] done`.
- [ ] `/spec-check S0081` returns `Verified` or is scheduled for next run.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s). Feature docs and dev log changes only — no code changes.
