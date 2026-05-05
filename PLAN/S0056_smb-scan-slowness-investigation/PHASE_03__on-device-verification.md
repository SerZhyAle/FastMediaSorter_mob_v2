# Phase 03 — On-device verification (Quest 3 / resourceId=18)

**Strategic spec:** [`../S0056_smb-scan-slowness-investigation.md`](../S0056_smb-scan-slowness-investigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** 2026-05-03
**Completed:** —

---

## Objective

Confirm on the field device (Meta Quest 3, same SMB resourceId=18 from `logs/fastmediasorter_20260503_031217.log`) that:
1. The SLOW SCAN W-warning no longer fires for that resource after Phase 01.
2. The new W-log fields are emitted correctly when a scan still exceeds the new adaptive threshold.
3. Strategic §6 Q5 (repeatability of the 20 s timing) — record median / max across 5 runs.

This is a manual phase: developer triggers scans on device and reads logcat.

---

## Prerequisites

- [ ] Phase 01 done — adaptive threshold is live in the build under test.
- [ ] Quest 3 paired with the development host; `adb` reachable.
- [ ] SMB server `192.168.1.110:445` and resourceId=18 available on the test Wi-Fi.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0056_on_device_verification_<YYYYMMDD>.md` | New | ≤ 200 |
| `PLAN/S0056_smb-scan-slowness-investigation.md` | Modified (only the `## Last Audit` block) | unchanged file size |

---

## Steps

### Step 03.1 — Author test scenario document in `temp/`

**Files:** `temp/S0056_on_device_verification_<YYYYMMDD>.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the file at `temp/S0056_on_device_verification_<YYYYMMDD>.md` (substitute today's date, e.g. `20260504`). Sections: **Setup** (device id, build version, network), **Procedure** (5 force-refresh runs against resourceId=18 with logcat capture between runs), **Captured rows** (one row per run: `run_idx`, `duration_ms`, `slow_scan_warning_emitted` y/n, `expected_file_count`, `actual_file_count`, `effective_threshold_ms`), **Outcome** (median, max, ratio max/first). No prose beyond what is needed to fill the table. The file lives in `temp/` per CLAUDE.md rule 1 (no writes to project root).

**Verification:**

- `Glob` returns exactly one matching file under `temp/S0056_on_device_verification_*.md`.
- `Grep -n "Captured rows"` in that file returns exactly 1 hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS. Authored test scenario at temp/S0056_on_device_verification_20260503.md (Setup, Procedure, table with 5 empty rows, decision rules a/b/c). Files: temp/S0056_on_device_verification_20260503.md (new). Dev log recorded.

---

### Step 03.2 — Run 5 force-refresh scans and capture logcat

**Files:** `temp/S0056_on_device_verification_<YYYYMMDD>.md` (Captured rows table populated)
**Depends on:** Step 03.1

**Prompt for developer:**

> On the Quest 3, open the resource list, select resourceId=18, and force-refresh 5 times in sequence (wait for each scan to complete before triggering the next). Capture logcat to `logs/Oculus-Quest-3-Android-14_<timestamp>.logcat`. Extract the 5 `ScanMetrics: scan_complete` lines and any `ScanMetrics: SLOW SCAN` lines for resourceId=18. Fill the table from Step 03.1. Use the `/log-reader` skill if helpful for parsing.

**Verification:**

- `Grep -n "ScanMetrics: scan_complete .*resourceId=18"` in the captured logcat returns exactly 5 hits.
- The Captured rows table in `temp/S0056_on_device_verification_*.md` has 5 populated rows (no empty cells).

**Status:** `[~]` in progress

**Step Log:**

- 2026-05-03 — BLOCKED. Awaiting on-device execution by owner (Quest 3 + SMB resourceId=18 at 192.168.1.110:445). Spec status flipped to `BlockNeedUserTest` via `update.ps1`. Resume `/spec-dev S0056` after the table in `temp/S0056_on_device_verification_20260503.md` is filled.

---

### Step 03.3 — Decide outcome and update `## Last Audit` of S0056 strategic

**Files:** `PLAN/S0056_smb-scan-slowness-investigation.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Compute median and max over the 5 runs. Decision rules: (a) if 0 SLOW SCAN warnings emitted across all 5 runs → adaptive threshold proven sufficient; (b) if max / median > 1.5× → flag as repeatable network instability and add a `[BLOCKED]` action item under Last Audit; (c) if SLOW SCAN warnings still emitted → record `expected_file_count`, `actual_file_count`, `effective_threshold_ms` and reconsider heuristic constant `SLOW_SCAN_PER_FILE_MS`. Overwrite the `## Last Audit` block at the bottom of `PLAN/S0056_smb-scan-slowness-investigation.md` with: date, mode (`on-device`), outcome (one of a/b/c), the 5-row summary, and pointer to the `temp/` test scenario document. Do not modify any other section of the strategic spec.

**Verification:**

- `Grep -n "^## Last Audit"` in `PLAN/S0056_smb-scan-slowness-investigation.md` returns exactly 1 hit.
- `Grep -n "Mode:.*on-device"` in `PLAN/S0056_smb-scan-slowness-investigation.md` returns exactly 1 hit (under Last Audit).
- `Grep -n "temp/S0056_on_device_verification"` in `PLAN/S0056_smb-scan-slowness-investigation.md` returns ≥ 1 hit.

**Status:** `[ ]` not done

---

### Step 03.4 — If outcome (b), set spec to `BlockNeedUserTest`; otherwise leave for `/spec-check`

**Files:** none (CLI mutator only)
**Depends on:** Step 03.3

**Prompt for developer:**

> If outcome from Step 03.3 was (b) — repeatable instability beyond 1.5× — run `pwsh -File scripts/spec_catalog/update.ps1 -Id S0056 -Status BlockNeedUserTest`. Otherwise leave status untouched (Phase 04 + `/spec-check` will advance it). Either way, `update.ps1` refreshes `updated` so staleness signal is reset.

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0056 -Format json` returns a record where `updated` is today's date (YYYY-MM-DD prefix matches).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Test scenario document exists under `temp/`.
- [ ] `## Last Audit` block of S0056 strategic reflects on-device outcome.
- [ ] Dev log entry added for any modified file (strategic spec edit, test scenario doc) via `.\scripts\add_to_dev_log.ps1`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.

---

## Handoff Notes to Next Phase

If outcome is (a) — adaptive threshold is sufficient — Phase 04 may complete the spec via `/spec-check`. If outcome is (b) or (c), Phase 04 must still run for catalog regen, but the spec stays in `BlockNeedUserTest` / `Partial` until the underlying issue is addressed in a separate ticket.

---

## Rollback Plan

Phase is observation-only on device. To revert: delete the `temp/` test scenario document and revert the `## Last Audit` block of S0056 strategic to its pre-phase content. No code rollback needed.
