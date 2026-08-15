# Phase 03 - Sync parallel rule files

**Strategic spec:** [`../S1340_agent-rules-gate-or-compress.md`](../S1340_agent-rules-gate-or-compress.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Execute strategic §3.4's decision (already implied by CLAUDE.md's own top-of-file note: sync, do not delete): dedupe the timestamp rule between CLAUDE.md and `AGENTS.md`, and fix `.github/copilot-instructions.md:35`'s contradiction of CLAUDE.md §11 (FEATURES docs are `/skill-release`-generated only, never hand-edited per spec).

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - Phase 01 and Phase 02 must have landed their final CLAUDE.md text first, so this phase syncs against the compressed version, not the pre-S1340 one.
- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic §3.4 delivery-status note (added 2026-08-01) has been read - confirms "sync," not "delete," is the decision, and that no new mechanical parity gate is to be added (would violate strategic §5's zero-new-`assert-*` acceptance bullet).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `AGENTS.md` | Modified | stays under 71 lines (no net growth) |
| `.github/copilot-instructions.md` | Modified | stays under 71 lines (no net growth) |

---

## Steps

### Step 03.1 - Dedupe the timestamp rule in AGENTS.md

**Files:** `AGENTS.md`
**Depends on:** - start of phase (after Phase 01/02 land)

**Prompt for developer:**

> `AGENTS.md:11` restates the timestamp rule verbatim from `CLAUDE.md:10` ("Timestamps: Always accompany replies with a timestamp (HH:mm:ss based on the current local time provided in prompt metadata)."). Per repo hierarchy (`AGENTS.md`'s own import order note: "CLAUDE.md -> .github/copilot-instructions.md -> prompt; stricter wins"), CLAUDE.md is the canonical source. Replace the AGENTS.md line with a one-clause pointer: "Timestamps: see `CLAUDE.md` §1." (Grep confirmed no other in-repo file restates this exact sentence, so no further dedup target exists beyond this one line.)

**Verification:**

- `Grep "Always accompany replies with a timestamp" AGENTS.md` returns zero matches.
- `Grep "Timestamps" AGENTS.md` still matches (pointer line retained, not deleted outright).
- `Grep "Always accompany replies with a timestamp" CLAUDE.md` still matches exactly once (canonical copy untouched).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. `Grep "Always accompany replies with a timestamp" AGENTS.md` -> 0. `Grep "Timestamps" AGENTS.md` -> line 11, now a pointer. `Grep "..." CLAUDE.md` -> line 10, canonical copy untouched, exactly one match. Files: AGENTS.md. Dev log recorded via post-change.ps1.

---

### Step 03.2 - Fix the FEATURES.md contradiction in copilot-instructions.md

**Files:** `.github/copilot-instructions.md`
**Depends on:** Step 03.1 (sequential within phase - same review pass over both files)

**Prompt for developer:**

> `.github/copilot-instructions.md:35` reads "7. Feature docs: update `docs/FEATURES*.md` on new features." This contradicts CLAUDE.md §11: "`docs/FEATURES*.md`... populated ONLY by `/skill-release` from the `ALL_FEATURES` diff since the previous release - never edited per-spec." Rewrite line 35 to match: record the capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` instead (the mechanism CLAUDE.md §11 actually specifies for per-spec capability tracking); `docs/FEATURES*.md` itself is `/skill-release`-owned and must not be named as a per-change target.

**Verification:**

- `Grep "update \`docs/FEATURES" .github/copilot-instructions.md` returns zero matches.
- `Grep "ALL_FEATURES.jsonl" .github/copilot-instructions.md` matches (correct mechanism now named).
- `Grep "never edited per-spec\|skill-release" .github/copilot-instructions.md` matches at least one (ownership boundary now stated, consistent with CLAUDE.md §11).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. `Grep "update \`docs/FEATURES"` -> 0. `Grep "ALL_FEATURES.jsonl"` -> line 35 matches. `Grep "never edited per-spec|skill-release"` -> line 35 matches both. Files: `.github/copilot-instructions.md`. Dev log recorded via post-change.ps1.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - skip. No Kotlin/build-graph file touched.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for `AGENTS.md` and `.github/copilot-instructions.md` via `.\scripts\add_to_dev_log.ps1` (3 entries: step 03.1, step 03.2, phase addendum below).
- [x] If public API changed: skip - no app source touched.
- [x] Phase-boundary audit run - not applicable in the Kotlin-audit sense; ran the promised full re-read of both files (71 lines each) and found one real gap beyond the two planned steps: `AGENTS.md:33` and `.github/copilot-instructions.md:24` each independently restated the full document-registry mandate almost verbatim from CLAUDE.md's *pre-step-02.2* wording - the same duplication step 02.2 fixed for CLAUDE.md + 4 agent-persona files, missed for these two siblings during tactical planning because the original research grep was scoped to the timestamp sentence, not this one. Same subject matter as this ticket's own §3.3 scope (not an out-of-scope finding), fixed inline as a phase addendum: both compressed to the same one-line pointer used elsewhere. `Grep "query.ps1 by product area and change trigger" AGENTS.md .github/copilot-instructions.md` now returns zero matches in both; `Grep "document-registry/SKILL.md"` matches both. No other contradiction found in either file.

---

## Handoff Notes to Next Phase

Phase 04 (final) measures the aggregate byte-count delta across `CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md` against the 2026-08-01 baselines recorded in strategic §5, and runs the document-registry closing calls since `CLAUDE.md` is a registered document (`repository-rules` record).

---

## Rollback Plan

Revert phase commit(s) - text-only change to two short files, no data migration or user-facing surface changed.
