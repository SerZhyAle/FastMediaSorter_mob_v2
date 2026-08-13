# Phase 06 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0132_vr-quest3-epic-pending-verification.md`](../S0132_vr-quest3-epic-pending-verification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all previous phases
**Blocks:** none — final phase
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Regenerate the class catalog after all `.kt` changes; write complete dev-log entries for all modified files; run `/spec-check S0132` to close the epic.

---

## Prerequisites

- [ ] Phases 01, 02, 03, 04, 05 are all ✅ Done.
- [ ] All code commits from all phases are on the current branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-regenerated) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-regenerated) | — |
| `dev/CHANGELOG.md` | Modified (via script) | — |

---

## Steps

### Step 06.1 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase (all phases done)

**Prompt for developer:**

> Run the catalog scan and render for the `app_v2` module:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> For any new classes introduced by the phases (if any), set `role` and `status` via `set.ps1`.

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and `LastWriteTime` is today.
- `Glob` — `dev/CATALOG/app_v2.md` exists and `LastWriteTime` is today.

**Status:** `[ ]` not done

---

### Step 06.2 — Write dev-log entries for all modified files

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` for each file that was modified across all phases. Minimum entries (add more if additional files were touched by defect fixes):
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt" "S0132-P03" "finishAndRemoveTask: remove VrPlayerActivity from HorizonOS task switcher on exit-to-panel"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt" "S0132-P01" "VR_QUALITY_DEBUG: fisheye uniforms logging for S0041 diagnosis"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt" "S0132-P01/P05" "VR_QUALITY_DEBUG: swapchain format logging; cold-start stage metrics"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" "S0132-P05" "Cold-start stage metrics: onCreate→STAGE_SETUP_VIEWS→STAGE_FIRST_FRAME"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameExtractionPolicy.kt" "S0132-P05" "getFrameAtTime null fallback: decoder-busy/oom detection, Browse cache fallback"
> ```
> Add additional entries for any files changed in defect-fix sub-steps. If Phase 05.3b took the "Optimize now" path, add an entry for the optimization file too.

**Verification:**

- `Grep -n "S0132-P03"` in `dev/CHANGELOG.md` — matches `VrTaskTransition.kt` entry.
- `Grep -n "S0132-P01"` in `dev/CHANGELOG.md` — matches at least one Phase 01 entry.
- `Grep -n "S0132-P05"` in `dev/CHANGELOG.md` — matches at least two Phase 05 entries.

**Status:** `[ ]` not done

---

### Step 06.3 — Run /spec-check S0132 to close the epic

**Files:** strategic spec (updated by /spec-check)
**Depends on:** Steps 06.1 and 06.2

**Prompt for developer:**

> Run `/spec-check S0132`. The skill performs a full audit against all acceptance criteria from the strategic spec and all phase Done Criteria in this tactical plan. If it returns `Verified`, the epic is closed. If it returns `Partial` or `Broken`, address the listed findings before re-running.

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0132 -Format json` returns `"status":"Verified"`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` and `app_v2.md` have today's date as `LastWriteTime`.
- [ ] `/spec-check S0132` returned `Verified`.
- [ ] Strategic spec `Status:` shows `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

No code changes in this phase. If `/spec-check` returns `Broken`, re-open the relevant phase and fix the issue, then re-run `/spec-check`.
