# Phase 01 - Decisions and Contracts

**Strategic spec:** [`../S0271_truth_drift_detection.md`](../S0271_truth_drift_detection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Resolve the four open research items the strategic spec defers to tactical (§6.3, §6.4, §6.5, §6.6) by writing a single `DECISIONS.md` inside the tactical folder. No PowerShell code yet. Every later phase consumes these decisions as fixed contracts.

---

## Prerequisites

- [ ] Strategic spec `Status: Approved` or later.
- [ ] Tactical folder `PLAN/S0271_truth_drift_detection/` exists.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0271_truth_drift_detection/DECISIONS.md` | New | ≤ 250 |

---

## Steps

### Step 01.1 - Resolve §6.3 - manifest format

**Files:** `PLAN/S0271_truth_drift_detection/DECISIONS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Open `DECISIONS.md` and add the section `## D-1: Manifest Format = Variant A (external PSD1)`. Lock the choice: external PowerShell data file (`*.psd1`) co-located with the checker scripts. Justify in ≤ 5 bullets why Variant A wins over Variant B (inline markers) and Variant C (hybrid name-matching) - leading reasons: (a) PSD1 is native to PowerShell 7, parsed by `Import-PowerShellDataFile` without external modules; (b) adding a new pin = one PSD1 entry, no document edits; (c) survives document refactors (markdown reshuffles, table renames) as long as the regex matchers in the entry still match; (d) zero markdown pollution; (e) explicit failure surface - if a matcher breaks, the chequer fails loudly on a specific pin rather than silently mis-extracting. Specify schema fields per pin: `name` (canonical), `gradleSource` (file + matcher), `docs` (hashtable: doc-path → matcher regex + required-flag), `policy` (`firstOnly` | `allMustMatch` - default `allMustMatch`).

**Verification:**

- `Glob` - `PLAN/S0271_truth_drift_detection/DECISIONS.md` exists.
- `Grep` - `## D-1: Manifest Format = Variant A` matches exactly once.
- `Grep` - `Import-PowerShellDataFile` mentioned (proof the native parser is named).
- `Grep` - `gradleSource` and `docs` both present (schema field names locked).

**Status:** `[~]` in progress

---

### Step 01.2 - Resolve §6.4 - missing-pin semantics

**Files:** `PLAN/S0271_truth_drift_detection/DECISIONS.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Append section `## D-2: Missing-Pin Semantics`. Lock: each pin's manifest entry declares per-document `required: $true` or `required: $false`. A pin missing from a required doc → record `MISSING` (a FAIL subclass, contributes to non-zero exit). A pin missing from a non-required doc → record `SKIP` (informational only). Rationale: `docs/TECH_STACK.md` is a short cheat-sheet and is not expected to enumerate every library; demanding full coverage there produces noise. `dev/TECH_REQUIREMENTS.md` is "Single source of truth for the full technical stack" - every Class-2 and Class-3 pin must be required there. `CLAUDE.md` requires only a handful of headline pins (Kotlin, Glide, Room, Hilt, Media3). Provide an explicit table of which document is required for which pin class.

**Verification:**

- `Grep` - `## D-2: Missing-Pin Semantics` matches exactly once.
- `Grep` - `required: \$true` and `required: \$false` both present.
- `Grep` - `MISSING` and `SKIP` both present.

**Status:** `[x] done`

---

### Step 01.3 - Resolve §6.5 - range-declaration semantics

**Files:** `PLAN/S0271_truth_drift_detection/DECISIONS.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Append section `## D-3: Range Declarations`. Lock: a matcher in the manifest may capture either an exact version (`2.2.10`) or a range marker (`1.9+`, `1.9.x`, `>=1.9`). If the captured token is a range expression and the actual Gradle version falls **inside** the range, classify as `WARN` (not PASS, not FAIL). Rationale: technically not a contradiction, but misleads agents into using stale-version knowledge. WARN does **not** flip exit code from 0 to 1 by itself - it is informational and counted under `warn` in SUMMARY. Define the range-recognition regex (e.g. `^\d+(\.\d+)*\+$` or `>=` prefix). Document the canonical case from strategic §4: `CLAUDE.md` "Kotlin 1.9+" with Gradle Kotlin 2.2.10 → WARN.

**Verification:**

- `Grep` - `## D-3: Range Declarations` matches exactly once.
- `Grep` - `WARN` mentioned with explicit "does not flip exit code" wording.
- `Grep` - example `Kotlin 1.9+` present (anchors the canonical case).

**Status:** `[x] done`

---

### Step 01.4 - Resolve §6.6 - multi-mention semantics + output contract lock

**Files:** `PLAN/S0271_truth_drift_detection/DECISIONS.md`
**Depends on:** Step 01.3

**Prompt for developer:**

> Append two final sections: `## D-4: Multi-Mention Inside One Document` and `## D-5: Output Format Contract (locked)`. D-4: per-pin `policy` field controls behavior - `allMustMatch` (default) treats divergent multiple mentions as `INCONSISTENT` (FAIL subclass); `firstOnly` uses only the first match and ignores later mentions; `exclude` regex excludes specific spans (e.g. a "Version History" section legitimately listing old values). D-5: lock the exact output grammar, copying from strategic §5.3 verbatim: `FAIL | <pin> | gradle: <X> | <doc-path>: <Y>`, `PASS | <pin> | <X>` (verbose only), `SKIP | <pin> | reason: <text>`, `WARN | <pin> | gradle: <X> | <doc-path>: <Y> (range)`, `INCONSISTENT | <pin> | <doc-path>: <Y1> vs <Y2>`, and final `SUMMARY | total: N | pass: A | fail: B | warn: C | skip: D | inconsistent: E`. Exit: `0` iff `fail + inconsistent == 0` and no `MISSING` records exist; `1` otherwise. Plain ASCII, no emoji, no ANSI by default.

**Verification:**

- `Grep` - `## D-4: Multi-Mention Inside One Document` matches exactly once.
- `Grep` - `## D-5: Output Format Contract` matches exactly once.
- `Grep` - all five record kinds present: `FAIL |`, `PASS |`, `SKIP |`, `WARN |`, `INCONSISTENT |`.
- `Grep` - `SUMMARY | total:` line present (the exact prefix downstream tools and tests will pattern-match on).
- `Grep` - `allMustMatch`, `firstOnly`, `exclude` all present (the three policy values are named).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `PLAN/S0271_truth_drift_detection/DECISIONS.md` exists and contains five `## D-N:` headings (`D-1` through `D-5`).
- [ ] No PowerShell code created yet (this is a contract-only phase).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `DECISIONS.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Five locked decisions become contracts for Phase 02-05:

- D-1 fixes the manifest schema → Phase 03 consumes it when authoring `pins.psd1`.
- D-2 fixes per-doc required/optional → Phase 04 consumes it in the comparator.
- D-3 fixes WARN class → Phase 04 output layer handles it; Phase 05 has a regression test.
- D-4 fixes multi-mention policy → Phase 03 parser respects `firstOnly`/`exclude`; Phase 04 emits `INCONSISTENT`.
- D-5 fixes output grammar → Phase 04 output layer and Phase 05 tests both pattern-match on it.

---

## Rollback Plan

Delete `PLAN/S0271_truth_drift_detection/DECISIONS.md` and revert the `INDEX.md` Pre-Implementation Blockers section if the strategic decisions need to be revisited. No code, no data migration.
