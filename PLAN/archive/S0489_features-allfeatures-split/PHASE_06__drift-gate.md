# Phase 06 - Drift Gate

**Strategic spec:** [`../S0489_features-allfeatures-split.md`](../S0489_features-allfeatures-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 03
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Add a mechanical gate that keeps `docs/ALL_FEATURES.jsonl` well-formed and from silently drifting, wired into `scripts/post-change.ps1` alongside the existing quality gates.

---

## Prerequisites

- [ ] Phase 01 (validator) + Phase 03 (populated data) are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-allfeatures-sync.ps1` | New | ≤ 200 |
| `scripts/quality/allfeatures-sync-baseline.txt` | New | n/a |
| `scripts/post-change.ps1` | Modified | ≤ 30 |

---

## Steps

### Step 06.1 - Authoring the gate script

**Files:** `scripts/quality/assert-allfeatures-sync.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Author `assert-allfeatures-sync.ps1` following the repo gate pattern (mirror `assert-no-ticket-logs.ps1`: `-Gate` and `-Quiet` switches, baseline file, fail-closed exit codes). It: (a) runs `scripts/all_features/validate.ps1 -Gate` (schema + uniqueness + EN-only); (b) records the current record count in a ratchet baseline `allfeatures-sync-baseline.txt` and fails if the count drops without an explicit baseline update (guards accidental record loss). Keep the trigger narrow: only fire on changes to `docs/ALL_FEATURES*.jsonl` or the schema.

**Verification:**

- `Glob` - `scripts/quality/assert-allfeatures-sync.ps1` exists.
- Run: `pwsh -NoProfile -File scripts/quality/assert-allfeatures-sync.ps1 -Gate` exits 0 on current data.
- `Grep` - `-Gate` and `validate.ps1` present in the script.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. assert-allfeatures-sync.ps1 mirrors the repo gate pattern (-Gate/-Quiet/-UpdateBaseline); delegates to validate.ps1 -Gate and ratchets record count vs baseline. -Gate exits 0 on current data.

---

### Step 06.2 - Seed the baseline

**Files:** `scripts/quality/allfeatures-sync-baseline.txt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Generate the baseline from the populated ALL_FEATURES (record count after Phase 03). The script's `-UpdateBaseline` (or equivalent) writes it; commit the seeded baseline so the ratchet starts from the real inventory size.

**Verification:**

- `Glob` - `scripts/quality/allfeatures-sync-baseline.txt` exists and is non-empty.
- Run: `assert-allfeatures-sync.ps1 -Gate` exits 0 immediately after seeding.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. Baseline seeded to 326 (public record count after Phase 03) via -UpdateBaseline; gate exits 0.

---

### Step 06.3 - Wire into post-change

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 06.2

**Prompt for developer:**

> Register the new gate in `scripts/post-change.ps1` next to the existing `assert-*` gate invocations (same `& $pwsh -NoProfile -File ... -Gate` style), so it runs for `Doc`/`Mixed`/`Script` change types touching ALL_FEATURES. Fail-closed: a non-zero gate exit fails post-change.

**Verification:**

- `Grep` - `assert-allfeatures-sync.ps1` referenced in `scripts/post-change.ps1`.
- Run: `pwsh -NoProfile -File scripts/post-change.ps1 -File "docs/ALL_FEATURES.jsonl" -Target "all-features" -Description "gate wiring smoke" -ChangeType Doc` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. Registered $runsAllFeaturesGate (narrow path trigger) + all-features-gate Invoke-Step in post-change.ps1. Fires on docs/ALL_FEATURES*.jsonl|json (PASS), skips on other files.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `assert-allfeatures-sync.ps1 -Gate` exits 0.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The gate now protects the inventory. Phase 07 finalizes dev log, FEATURES trilingual note, and closure.

---

## Rollback Plan

Remove the gate invocation from `post-change.ps1`, delete the gate script + baseline. No data impact.
