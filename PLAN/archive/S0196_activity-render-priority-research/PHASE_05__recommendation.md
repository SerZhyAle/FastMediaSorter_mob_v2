# Phase 05 — recommendation

**Strategic spec:** [`../S0196_activity-render-priority-research.md`](../S0196_activity-render-priority-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Convert audits and measurements into one verdict per target surface, update the strategic spec, and decide whether child specs are required.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] All strategic §6 items in the INDEX blocker list are ready to be checked.
- [ ] `temp/S0196/04_measurement_journal.md` and supporting evidence files exist.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0196/05_recommendation.md` | New | ≤ 320 |
| `PLAN/S0196_activity-render-priority-research.md` | Modified | ≤ 420 |

---

## Steps

### Step 05.1 — Write the per-surface verdict matrix

**Files:** `temp/S0196/05_recommendation.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `temp/S0196/05_recommendation.md`. Write one verdict per target surface: actual order, desired order, conflict source, candidate mechanism (`ViewStub`, post-first-frame callback, idle handler, defer subscription, leave as-is), and priority.

**Verification:**

- `Glob` — `temp/S0196/05_recommendation.md` exists.
- `Grep` — `PlayerActivity` present in that file.
- `Grep` — `BrowseActivity` present in that file.
- `Grep` — `SettingsActivity` present in that file.
- `Grep` — `idle handler` present in that file.

**Status:** `[ ]` not done

---

### Step 05.2 — Resolve strategic §6 and §11 with evidence references

**Files:** `PLAN/S0196_activity-render-priority-research.md`, `temp/S0196/05_recommendation.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Update strategic §6 so every research item moves from `Open` to `Resolved` or `Resolved (Skipped)` and points to the relevant `temp/S0196` evidence file. Update §11 so completion criteria explicitly match the delivered evidence and recommendation outputs.

**Verification:**

- `Grep` — `**Status:** Open` returns zero hits in `PLAN/S0196_activity-render-priority-research.md`.
- `Grep` — `Resolved` present in `PLAN/S0196_activity-render-priority-research.md`.
- `Grep` — `temp/S0196/` present in `PLAN/S0196_activity-render-priority-research.md`.

**Status:** `[ ]` not done

---

### Step 05.3 — Decide whether child specs are required

**Files:** `temp/S0196/05_recommendation.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add a `Child specs:` section to `temp/S0196/05_recommendation.md`. Write either `No child specs required` or one proposed child spec title and priority per surface that needs implementation work.

**Verification:**

- `Grep` — `Child specs:` present in `temp/S0196/05_recommendation.md`.
- `Grep` — `Priority` present in `temp/S0196/05_recommendation.md`.

**Status:** `[ ]` not done

---

### Step 05.4 — Update ADR and next-step routing

**Files:** `PLAN/S0196_activity-render-priority-research.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Add one new ADR entry for the final recommendation and update strategic §12 with the next concrete command: `/spec-check S0196` if the research closes here, or the specific child spec ids if follow-up implementation is required.

**Verification:**

- `Grep` — `ADR-4` present in `PLAN/S0196_activity-render-priority-research.md`.
- `Grep` — `/spec-check S0196` or `S0` present in `PLAN/S0196_activity-render-priority-research.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Strategic spec §6, §9, §10, §11, and §12 are consistent with the research output.
- [ ] `temp/S0196/05_recommendation.md` holds the canonical per-surface verdict table.
- [ ] All INDEX blocker checkboxes may be flipped to `[x]` after this phase.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

If Phase 05 spawns child specs, Phase 06 records the ids and leaves the feature docs unchanged. If no child specs are needed, Phase 06 closes S0196 directly.

---

## Rollback Plan

Revert the strategic-spec edit and delete `temp/S0196/05_recommendation.md` — no production code or persisted app data changed.