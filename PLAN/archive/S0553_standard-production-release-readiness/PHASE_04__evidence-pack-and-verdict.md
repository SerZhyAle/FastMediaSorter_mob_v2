# Phase 04 - Evidence pack and verdict

**Strategic spec:** [`../S0553_standard-production-release-readiness.md`](../S0553_standard-production-release-readiness.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-20
**Completed:** 2026-06-20

## Step Log

- 2026-06-20 - 04.1 PASS: `store_assets/release_waivers/TEMPLATE.md` + `README.md` created (loss-class/author/date/review-by fields; one-file-per-versionName, owner-only).
- 2026-06-20 - 04.2 PASS: Operator evidence pack section filled (required artifacts + storage, PLAY_CONSOLE cross-ref, mapping.txt retention, Play gate strictness).
- 2026-06-20 - 04.3 PASS: `standard-release-gate.ps1` created; folds surface+smoke+coverage+waiver; verified FAIL (no waiver, exit 1) and WAIVED (waiver present, exit 3).
- 2026-06-20 - 04.4 PASS: Verdict contract section filled (PASS/FAIL/WAIVED, tiered blocker policy, waiver authority); zero `Filled in Phase` placeholders remain in the gate doc.

---

## Objective

Produce the operator evidence pack (waiver template + storage convention), the single PASS/FAIL/WAIVED verdict aggregator script, and the verdict-contract + evidence-pack sections of the gate document.

---

## Prerequisites

- [ ] Phase 01, 02, 03 Done (snapshot tool, release smoke, coverage manifest exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `store_assets/release_waivers/TEMPLATE.md` | New | ≤ 80 |
| `store_assets/release_waivers/README.md` | New | ≤ 60 |
| `scripts/release/standard-release-gate.ps1` | New | ≤ 260 |
| `docs/RELEASE_READINESS_STANDARD.md` | Modified | ≤ 400 |

---

## Steps

### Step 04.1 - Author the waiver template + storage convention

**Files:** `store_assets/release_waivers/TEMPLATE.md`, `store_assets/release_waivers/README.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `store_assets/release_waivers/TEMPLATE.md`: a per-waiver entry block with fields `Loss class` (ref to §5.x), `Subject`, `Reason`, `Author`, `Date`, `Review-by`, `Evidence link`. Create `store_assets/release_waivers/README.md` documenting the convention from §3.3: one file per release named `<versionName>.md`, each holds the list of approved deviations for that release; a waiver requires author + date + loss-class reference; only the owner may approve a waiver.

**Verification:**

- `Glob` - both files exist under `store_assets/release_waivers/`.
- `Grep` - `Loss class`, `Author`, `Review-by` present in TEMPLATE.md.
- `Grep` - `<versionName>.md` and `owner` present in README.md.

**Status:** `[x]` done

---

### Step 04.2 - Fill the Operator evidence pack section of the gate document

**Files:** `docs/RELEASE_READINESS_STANDARD.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Replace the `Operator evidence pack` placeholder with the required-artifacts list (release smoke verdict JSON, surface snapshot, coverage manifest, screenshots, Pre-launch report link, Data Safety review note, signing fingerprint confirmation, `mapping.txt` + native symbols per `versionCode`, waiver file) and their storage locations (`temp/` for run artifacts, `store_assets/release_waivers/` for waivers). Cross-reference `store_assets/PLAY_CONSOLE_CHECKLIST.md` as the operator-side Play checklist. State the Play gate strictness from §3.3 (clean Pre-launch report; known non-user-facing warnings only with a recorded waiver).

**Verification:**

- `Grep` - `## Operator evidence pack` no longer contains a `Filled in Phase` placeholder.
- `Grep` - `PLAY_CONSOLE_CHECKLIST.md` and `release_waivers` both referenced.
- `Grep` - `mapping.txt` referenced in the evidence list.

**Status:** `[x]` done

---

### Step 04.3 - Author the single verdict aggregator

**Files:** `scripts/release/standard-release-gate.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `scripts/release/standard-release-gate.ps1` modelled on `scripts/devtest/prerelease-verdict.ps1`. It folds four signals into one verdict: surface regression check (`standard-surface-snapshot.ps1 -CheckRegressions`), release smoke (`standard-release-smoke.ps1 -Json`, treat exit 2 / no-device as a recorded coverage gap not a FAIL), coverage manifest (`docs/release/standard-coverage-matrix.json` parses and has no `not`-covered group lacking a waiver), and waiver file presence for any waiver-eligible gap. Apply the tiered blocker policy (§3.3): hard-stop classes -> FAIL; waiver-eligible classes with a matching waiver entry -> WAIVED; all clear -> PASS. Emit `-Json` `{ verdict:"PASS|FAIL|WAIVED", breakdown:{...} }`. Exit 0 = PASS, 1 = FAIL, 3 = WAIVED, 2 = infra abort.

**Verification:**

- `Glob` - `scripts/release/standard-release-gate.ps1` exists.
- `Grep` - all four signal sources referenced: `standard-surface-snapshot.ps1`, `standard-release-smoke.ps1`, `standard-coverage-matrix.json`, `release_waivers`.
- `Grep` - `PASS`, `FAIL`, `WAIVED` all present.
- Run: `pwsh -NoProfile -File scripts/release/standard-release-gate.ps1 -Json` exits with 0/1/2/3 and prints a verdict object with a `verdict` field.

**Status:** `[x]` done

---

### Step 04.4 - Fill the Verdict contract section of the gate document

**Files:** `docs/RELEASE_READINESS_STANDARD.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Replace the `Verdict contract` placeholder with the single PASS/FAIL/WAIVED definition produced by `standard-release-gate.ps1`, the tiered blocker policy from §3.3 (which loss classes hard-stop vs are waiver-eligible), and the waiver authority (only the owner may waive; a sufficient waiver record = author + date + loss-class reference in the per-release waiver file). Reference the aggregator script as the mechanical gate.

**Verification:**

- `Grep` - `## Verdict contract` no longer contains a `Filled in Phase` placeholder.
- `Grep` - `PASS`, `FAIL`, `WAIVED` defined in the section.
- `Grep` - `standard-release-gate.ps1` referenced.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `standard-release-gate.ps1` runs and emits a verdict (exit 0/1/2/3) - record exit code.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] No `Filled in Phase` placeholder remains anywhere in `docs/RELEASE_READINESS_STANDARD.md`.
- [ ] Dev log entry added (or batched in Phase 05).

---

## Handoff Notes to Next Phase

The gate is complete: contract doc fully filled, surface/smoke/coverage tools wired into one verdict aggregator, waiver pack in place. Phase 05 cross-links and runs post-change closure.

---

## Rollback Plan

Revert phase commit(s) - new template/script + doc sections, no runtime change.
