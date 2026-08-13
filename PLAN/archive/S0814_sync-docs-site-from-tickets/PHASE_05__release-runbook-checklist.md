# Phase 05 - Release-runbook narrative-guide review anchor

**Strategic spec:** [`../S0814_sync-docs-site-from-tickets.md`](../S0814_sync-docs-site-from-tickets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent process-anchor phase
**Blocks:** Phase 06
**Steps done:** 1 / 1
**Started:** 2026-07-05
**Completed:** 2026-07-05

---

## Objective

Institutionalize the doc-review habit as a lightweight checklist step in the release runbook (`.claude/commands/release.md`) - no keyword gate. Satisfies strategic §2 goal 3 and §11 criterion 3.

---

## Prerequisites

- [ ] Read `.claude/commands/release.md` "Step 4 - Ready the documentation" and the "Checklist - nothing forgotten" section.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/commands/release.md` | Modified | +10 |

> The `/release` campaign runbook is the durable home for a repeatable review step. This is a process/runbook edit, not `PLAN/**` text, so it passes the real-work filter.

---

## Steps

### Step 05.1 - Add the narrative-guide freshness review step + checklist line

**Files:** `.claude/commands/release.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In "Step 4 - Ready the documentation", add a bullet: before publishing, review the narrative guides (README, QUICK_START, HOW_TO, FAQ, LIMITATIONS in EN/RU/UK) against the `docs/ALL_FEATURES.jsonl` diff since the last release, and reflect any newly user-visible capability that a Verified ticket shipped. In the same bullet, record which doc surfaces are already gated and therefore automatic (`SETTINGS_REFERENCE*`, `ICON_LEGEND*`, version-pins, `FEATURES*`) versus the narrative guides, which have no gate and rely on this manual review step. Then add one matching line to the "Checklist - nothing forgotten" section (e.g. "Narrative guides reviewed against ALL_FEATURES diff; new visible capabilities reflected in EN/RU/UK"). No keyword gate, no `assert-*` script.

**Verification:**

- `Grep -n "ALL_FEATURES"` matches a new bullet inside Step 4 of `release.md`.
- `Grep -n "Narrative guides"` (or the exact checklist wording) matches once in the "Checklist - nothing forgotten" section.
- `Grep -n "SETTINGS_REFERENCE"` present in the new Step 4 bullet (the gated-vs-narrative distinction that satisfies §11 criterion 3).

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS (content pre-existed from a prior untracked session). `.claude/commands/release.md` L79: "Narrative guides drift review (S0814)" bullet in Step 4 - five guides vs `ALL_FEATURES.jsonl` diff, gated surfaces (`FEATURES*` / `SETTINGS_REFERENCE*` / `ICON_LEGEND*`) explicitly excluded as owned/generated; L124: matching checklist line in "Checklist - nothing forgotten". No new gate script introduced.

---

## Phase Done Criteria

- [x] `Step 05.1` is `[x] done`.
- [x] `release.md` Step 4 has the narrative-review bullet; the checklist has the matching line.
- [x] No new `assert-*` gate or keyword-matching script was introduced (owner chose checklist, not a gate).
- [x] `Grep` for `TODO(phase-05)` returns zero hits (expected 0 | actual 0).
- [x] Dev log entry added (batched at Phase 06 acceptable) - batched to Phase 06.

---

## Handoff Notes to Next Phase

The review habit is now anchored in the release runbook. Strategic §11 criterion 3 (gated-vs-process surfaces recorded) is satisfied by the Step 4 bullet. Only cleanup (Phase 06) remains.

---

## Rollback Plan

Revert the `release.md` edit - a runbook checklist addition with no code or user-facing impact.
