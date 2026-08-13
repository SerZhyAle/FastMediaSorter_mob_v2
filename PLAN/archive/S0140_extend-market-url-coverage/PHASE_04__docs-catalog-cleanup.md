# Phase 04 — docs-catalog-cleanup

**Strategic spec:** [`../S0140_extend-market-url-coverage.md`](../S0140_extend-market-url-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01, Phase 02
**Blocks:** final verification / closure
**Steps done:** 2 / 3
**Started:** 2026-05-10
**Completed:** -

---

## Objective

Keep the public feature inventory and spec metadata aligned with the landed S0140 slices while final verification remains open.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done before final verification.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0140_extend-market-url-coverage.md` | Modified | <= 320 |
| `PLAN/S0140_extend-market-url-coverage/INDEX.md` | New | <= 220 |
| `PLAN/S0140_extend-market-url-coverage/PHASE_01__auth-share-polish.md` | New | <= 260 |
| `PLAN/S0140_extend-market-url-coverage/PHASE_02__structured-standards.md` | New | <= 260 |
| `PLAN/S0140_extend-market-url-coverage/PHASE_03__dynamic-extractor.md` | New | <= 320 |
| `PLAN/S0140_extend-market-url-coverage/PHASE_04__docs-catalog-cleanup.md` | New | <= 220 |
| `docs/FEATURES.md` | Modified | <= 520 |
| `docs/FEATURES_RU.md` | Modified | <= 520 |
| `docs/FEATURES_UK.md` | Modified | <= 520 |

---

## Steps

### Step 04.1 — Update feature inventory for the landed user-visible S0140 slice

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Phase 01, Phase 02

**Prompt for developer:**

> Add one neutral bullet in the URL-download section describing the landed S0140 slice only: structured metadata coverage (JSON-LD/oEmbed), multi-link share batching, and safer WebView auth saves. Do not mention named platforms.

**Verification:**

- `Grep` — `S0140` appears once in each of the three feature files.
- `Grep` — `JSON-LD` and `oEmbed` appear in the new EN bullet.
- `Grep` — no named platform was added by this step.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. Files: `docs/FEATURES*.md`. Dev log recorded.
- 2026-05-10 — Follow-up update. The existing S0140 feature bullets were expanded to mention the dynamic WebView pass and soft login-wall routing after the Phase 03 runtime slice landed.

---

### Step 04.2 — Materialise the tactical plan and advance the strategic status

**Files:** `PLAN/S0140_extend-market-url-coverage.md`, `PLAN/S0140_extend-market-url-coverage/INDEX.md`, `PLAN/S0140_extend-market-url-coverage/PHASE_01__auth-share-polish.md`, `PLAN/S0140_extend-market-url-coverage/PHASE_02__structured-standards.md`, `PLAN/S0140_extend-market-url-coverage/PHASE_03__dynamic-extractor.md`, `PLAN/S0140_extend-market-url-coverage/PHASE_04__docs-catalog-cleanup.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create the tactical folder, capture the landed Phase 01/02 work and the blocked Phase 03, and advance the strategic spec from `Approved` to `In Progress` with a concrete `Tactical plan:` link.

**Verification:**

- `Glob` — all tactical phase files exist.
- `Grep` — `**Status:** In Progress` exists in the strategic spec.
- `Grep` — `**Tactical plan:**` exists in the strategic spec.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. Strategic + tactical spec files created/updated. Dev log recorded.

---

### Step 04.3 — Run the final verification pass after Phase 03 lands

**Files:** `PLAN/S0140_extend-market-url-coverage/INDEX.md`, `PLAN/S0140_extend-market-url-coverage.md`
**Depends on:** Phase 03, Step 04.2

**Prompt for developer:**

> After Phase 03 is complete, rerun build / locale / catalog / spec-check verification, update the tactical counters to full completion, and advance the strategic status to `Verified` via `/spec-check`.

**Verification:**

- `/spec-check S0140` returns `Verified`.
- `INDEX.md` shows `Phases: 4 / 4 done`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated for the landed slice.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] `/spec-check S0140` returns `Verified`.

---

## Handoff Notes to Next Phase

Only the final verification/closure step remains here; it waits on the blocked dynamic extractor phase.

---

## Rollback Plan

Revert the docs/spec commit(s). No runtime data changes are introduced in this phase.