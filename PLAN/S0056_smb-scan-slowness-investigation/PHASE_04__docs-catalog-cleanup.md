# Phase 04 — Docs / catalog cleanup

**Strategic spec:** [`../S0056_smb-scan-slowness-investigation.md`](../S0056_smb-scan-slowness-investigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** —
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Final mandatory phase. Regenerate `dev/CATALOG/app_v2.{jsonl,md}` (public API of `ScanMetricsRecorder` changed in Phase 01), confirm `dev/CHANGELOG.md` covers every modified file, and advance the spec lifecycle via `/spec-check`. No `docs/FEATURES*.md` update — strategic §8 confirms the change is internal-only.

---

## Prerequisites

- [ ] Phases 01, 02, 03 are ✅ Done.
- [ ] Working tree contains uncommitted changes from earlier phases (or they are committed but the catalog has not yet been regenerated).
- [ ] `dev/CATALOG/scripts/scan.ps1` and `render.ps1` available under PowerShell 7.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-regenerated) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (auto-regenerated) | n/a |
| `dev/CHANGELOG.md` | Modified (via `add_to_dev_log.ps1`, never edited by hand) | n/a |

---

## Steps

### Step 04.1 — Regenerate catalog jsonl

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`. This refreshes auto-fields for every `.kt` file in `app_v2`. Manual `role`/`status` annotations are preserved. Inspect the diff for `core/metrics/ScanMetricsRecorder.kt` — the public API signature (`beginScan` arity) should be reflected.

**Verification:**

- `Grep -n "ScanMetricsRecorder"` in `dev/CATALOG/app_v2.jsonl` returns ≥ 1 hit.
- `git diff dev/CATALOG/app_v2.jsonl` shows non-empty diff for the `ScanMetricsRecorder` record (or no diff if signature was already documented — both are acceptable, the run itself is the gate).

**Status:** `[ ]` not done

---

### Step 04.2 — Regenerate human-readable catalog markdown

**Files:** `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. (Reminder: `render.ps1` is PS7-only — use the full path `"/c/Program Files/PowerShell/7/pwsh.exe"` if invoking from bash.)

**Verification:**

- `Grep -n "ScanMetricsRecorder"` in `dev/CATALOG/app_v2.md` returns ≥ 1 hit.
- File `dev/CATALOG/app_v2.md` mtime is today.

**Status:** `[ ]` not done

---

### Step 04.3 — Confirm dev changelog coverage for all modified files

**Files:** none (verification only)
**Depends on:** Step 04.2

**Prompt for developer:**

> Verify that `dev/CHANGELOG.md` contains an entry for each file modified across Phases 01–04: `core/metrics/ScanMetricsRecorder.kt`, `domain/usecase/GetMediaFilesUseCase.kt`, the new `Sxxxx_scan-progress-indicator.md` from Phase 02, the `temp/` test scenario doc from Phase 03, and the catalog regeneration outputs from 04.1–04.2. If any are missing, run `.\scripts\add_to_dev_log.ps1 "<path>" "spec-dev" "<short description>"` for each.

**Verification:**

- `Grep -n "ScanMetricsRecorder.kt"` in `dev/CHANGELOG.md` returns ≥ 1 hit dated today (2026-05-03 or later).
- `Grep -n "GetMediaFilesUseCase.kt"` in `dev/CHANGELOG.md` returns ≥ 1 hit dated today.
- `Grep -n "scan-progress-indicator"` in `dev/CHANGELOG.md` returns ≥ 1 hit (from Phase 02).
- `Grep -n "S0056_on_device_verification"` in `dev/CHANGELOG.md` returns ≥ 1 hit (from Phase 03).

**Status:** `[ ]` not done

---

### Step 04.4 — Run `/spec-check S0056` to advance lifecycle

**Files:** `PLAN/S0056_smb-scan-slowness-investigation.md` (only the `## Last Audit` block, by `/spec-check`)
**Depends on:** Step 04.3

**Prompt for developer:**

> Invoke `/spec-check S0056`. Expected outcome depends on Phase 03 result:
> - Phase 03 outcome (a) → `/spec-check` flips status to `Verified`.
> - Phase 03 outcome (b) → spec already in `BlockNeedUserTest`; `/spec-check` should leave it there.
> - Phase 03 outcome (c) → `/spec-check` may flip to `Partial`.
>
> Do not edit the journal directly; only the skill or `update.ps1` may mutate status.

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0056 -Format json` returns `status` ∈ {`Verified`, `Partial`, `BlockNeedUserTest`}.
- `Grep -n "^## Last Audit"` in `PLAN/S0056_smb-scan-slowness-investigation.md` returns exactly 1 hit, with the `Date:` line matching today.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` and `app_v2.md` regenerated and committed alongside Phase 01 code change.
- [ ] `dev/CHANGELOG.md` covers every modified file from this spec.
- [ ] Spec status is one of `Verified` / `Partial` / `BlockNeedUserTest` (no longer `In Progress` / `Tactical`).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Catalog regen and changelog appends are append-only operations; revert the catalog files to their pre-phase committed versions if needed. No code rollback.
