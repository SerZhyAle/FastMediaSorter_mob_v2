# Phase 06 - Docs + Catalog Cleanup (final)

**Strategic spec:** [`../S0251_cleanup-dead-vr-format-settings.md`](../S0251_cleanup-dead-vr-format-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** none (final phase)
**Steps done:** 5 / 5
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Resolve strategic §6.1 (whether the `docs/FEATURES.md` bullet about "Dual-group format override dialog" refers to the removed Settings spinners or the in-player override dialog), update FEATURES files trilingually only if needed. Regenerate the catalog. Append a functionality log entry. Move the spec into `BlockNeedUserTest` so the operator can verify the change on a vr-capable device.

---

## Prerequisites

- [ ] Phases 01-05 ✅ Done.
- [ ] No stale `Timber.d("S0251:")` tags exist in `.kt` yet - they are inserted in this phase when status flips to `BlockNeedUserTest`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Possibly modified (depends on research) | + 0 or - 1 bullet |
| `docs/FEATURES_RU.md` | Possibly modified | + 0 or - 1 bullet |
| `docs/FEATURES_UK.md` | Possibly modified | + 0 or - 1 bullet |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | machine-managed |
| `dev/CATALOG/app_v2.md` | Regenerated | machine-managed |
| `dev/FUNCTIONALITY.log` | + 1 line | append-only |
| `PLAN/S0251_cleanup-dead-vr-format-settings.md` | Status flip | machine-managed |
| `PLAN/spec-catalog.jsonl` | Status flip | machine-managed |

---

## Steps

### Step 06.1 - Resolve §6.1: identify which UI the FEATURES bullet describes

**Files:** read-only research; possibly `docs/FEATURES.md` + `_RU.md` + `_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> The strategic spec §6.1 left open whether the bullet `Dual-group format override dialog: Exposes separate flat and spherical format override selectors.` (line 155 of `docs/FEATURES.md` and parallel lines in `_RU.md` / `_UK.md`) describes:
>
> - (a) the now-removed Settings spinners (forced-format flat / spherical), in which case the bullet must be removed or rewritten; or
> - (b) the in-player Format Override dialog (still alive - the dialog that lets the user pick a stereo mode per video and writes it to the Room cache via `rememberStereoModeForCurrentFile`), in which case the bullet stays.
>
> Resolution procedure:
>
> 1. Find the in-player format-override dialog. Likely host: `PlaybackControlDialogFragment` or a sibling fragment in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/`. Grep for `StereoMode` + `Dialog` + UI binding for `SBS|OU|MONO|MONO_LEFT|MONO_RIGHT`.
> 2. Check whether that dialog exposes SEPARATE selectors for flat (SBS/OU/MONO) and spherical (VR180/360°) families, or a single unified picker.
> 3. If the dialog has two selectors (one flat, one spherical) → bullet describes the alive dialog → KEEP. Note the result in the commit message.
> 4. If the dialog has a single selector or no spherical-family branching → bullet was about the removed Settings UI → REMOVE the bullet from all three FEATURES files. Use `/doc-update` skill to do this (mirrors EN/RU/UK).
>
> Document the conclusion in the step's verification entry.

**Verification:**

- Record one of two sentences into chat trace and commit message:
  - `S0251 Phase 06.1 decision: FEATURES bullet describes alive in-player override dialog (kept).`
  - `S0251 Phase 06.1 decision: FEATURES bullet described removed Settings UI (removed from EN/RU/UK).`
- If KEEP: `Grep -n "Dual-group format override dialog"` in `docs/FEATURES.md` → 1 hit (unchanged). Parallel verification in `_RU.md`, `_UK.md` (translated phrases).
- If REMOVE: `Grep -n "Dual-group format override dialog"` in `docs/FEATURES.md` → 0 hits. Parallel verification in `_RU.md`, `_UK.md`.

**Status:** `[x]` done

---

### Step 06.2 - Regenerate catalog for `app_v2`

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run the wrapper that scans + renders in a single PowerShell process:
>
> ```powershell
> pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2
> ```
>
> Both files are gitignored by default (per project memory `build gotchas`) but they are checked-in for this repo - confirm via `git status` whether they are tracked. If tracked, commit them in the same commit as the other Phase 06 changes.

**Verification:**

- `git status` shows `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` as modified.
- `Grep -n "VrForcedFormatResolver"` in `dev/CATALOG/app_v2.jsonl` → 0 hits (if Phase 03 inlined) OR 1 hit (if kept as resolver with simplified signature).
- `Grep -n "applySettings"` in `dev/CATALOG/app_v2.jsonl` near PlayerStereoModeCoordinator → 0 hits.

**Status:** `[x]` done

---

### Step 06.3 - Append functionality log entry

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 06.2

**Prompt for developer:**

> Append a CHANGE entry via the CLI (do not edit the log directly):
>
> ```powershell
> .\scripts\add_to_functionality_log.ps1 -Id S0251 -Op CHANGE -Description "Remove obsolete VR forced-format Settings controls (Forced flat format / Forced spherical format / Remember file format) from Media settings; relocate FPS-overlay and Multi-window switches outside the VR subgroup; add help icon next to the 3D-VR card header with refreshed dialog text"
> ```

**Verification:**

- `Grep -n "S0251.*CHANGE"` in `dev/FUNCTIONALITY.log` → exactly 1 hit (the new entry).

**Status:** `[x]` done

---

### Step 06.4 - Insert `Timber.d("S0251: ..")` tags and flip spec to `BlockNeedUserTest`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt` (or wherever the changed flow entry points are)
**Depends on:** Step 06.3

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags", on transition INTO `BlockNeedUserTest` the skill must insert one `Timber.d("S0251: <description>")` at the entry point of each changed flow. The user-visible flows in this spec are:
>
> 1. `MediaSettingsFragment.setupVrHeaderHelp()` - VR help icon click flow (Phase 04).
> 2. `VideoSettingsFragment.setupViews()` - the changed video-settings UI surface (Phase 01).
>
> Add at the start of each function:
>
> ```kotlin
> Timber.d("S0251: <one-line entry-point description>")
> ```
>
> Then run:
>
> ```powershell
> pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0251 -Status BlockNeedUserTest
> ```
>
> The status flip in the spec file (`**Status:** Tactical` → `**Status:** BlockNeedUserTest`) happens via the same script's side effect, OR patch manually with `Set-Content` and verify the frontmatter.

**Verification:**

- `Grep -n 'Timber\.d\("S0251:'` repo-wide → exactly 2 hits (the two entry-point tags).
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0251 -Format json` → `"status":"BlockNeedUserTest"`.
- Frontmatter of `PLAN/S0251_cleanup-dead-vr-format-settings.md` shows `**Status:** BlockNeedUserTest`.

**Status:** `[x]` done

---

### Step 06.5 - Final dev log + handoff to operator

**Files:** dev log
**Depends on:** Step 06.4

**Prompt for developer:**

> Append dev log entries for the docs/catalog/log files touched:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0251" "Phase 06: <kept|removed> Dual-group format override dialog bullet per §6.1 research"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "S0251" "Phase 06: catalog regen after coordinator+UI cleanup"
> .\scripts\add_to_dev_log.ps1 "PLAN/S0251_cleanup-dead-vr-format-settings.md" "S0251" "Phase 06: status flip Tactical -> BlockNeedUserTest"
> ```
>
> Hand off to operator with the following test plan:
>
> 1. Install `vrDebug` build on a Meta Quest 3 or Android XR device.
> 2. Open Settings → Media → Video card. Confirm the three removed controls and the "VR" subgroup header are gone.
> 3. Confirm the two relocated switches (FPS-overlay, Multi-window) are present at the location chosen in step 01.3.
> 4. Open the 3D-VR card. Confirm the help icon is visible next to the title. Tap it. Confirm the dialog opens with the new text. Tap the title. Confirm the card collapses/expands.
> 5. Open a video. Open the format-override dialog (in-player). Pick SBS. Navigate to another video and back. Confirm the SBS choice is restored from the per-file cache.
> 6. Install a backup created on the previous build (containing `vrForcedPlatFormat` etc.). Confirm restore completes without crash and the rest of the settings are preserved.
> 7. Reboot the app. Confirm logcat shows the two `S0251:` Timber tags fire on the corresponding entry points.

**Verification:**

- `Grep -n "S0251.*Phase 06"` in `dev/CHANGELOG.md` → exactly 3 hits (FEATURES, catalog, spec).
- All blocker checkboxes in `INDEX.md` are checked (or marked Resolved with a note).
- Spec catalog status is `BlockNeedUserTest`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Spec catalog status `BlockNeedUserTest`.
- [x] Two `Timber.d("S0251:")` tags inserted in code (`MediaSettingsFragment.setupVrHeaderHelp` + `VideoSettingsFragment.setupPlayerExtras`).
- [x] Catalog files regenerated. Note: `dev/CATALOG/app_v2.{jsonl,md}` are gitignored in this repo, so they are not tracked-modified; verification done via local grep (0 hits for `VrForcedFormatResolver` and `applySettings`).
- [x] FEATURES decision recorded: bullet KEPT - describes in-player override dialog (recorded in dev_log entry).
- [x] Functionality log entry appended (2026-05-19 12:02).
- [x] Operator handoff test plan delivered (see step 06.5 below; mirrored into strategic §11a-equivalent if/when needed).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. On a successful device verification, `/spec-check S0251` will move the spec to `Verified` and remove the two `Timber.d("S0251:")` tags per CLAUDE.md tag lifecycle invariant.

---

## Rollback Plan

If device verification fails:
1. Move spec status back to `Tactical` via `update.ps1 -Status Tactical`.
2. Run `Grep -n 'Timber\.d\("S0251:'` and remove the two tags - they only belong in `BlockNeedUserTest`.
3. Identify the failing phase from the operator's report, revert only its diffs.
4. Reschedule that phase with corrected steps.
