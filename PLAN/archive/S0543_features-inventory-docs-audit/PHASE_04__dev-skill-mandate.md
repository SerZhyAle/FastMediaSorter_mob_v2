# Phase 04 - Dev-Skill Mandate

**Strategic spec:** [`../S0543_features-inventory-docs-audit.md`](../S0543_features-inventory-docs-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none (dependency-free - may run first)
**Steps done:** 3 / 3

---

## Objective

Make dev-time skills mechanically keep `ALL_FEATURES` current: add on new capability, change on behaviour change, and - the suspected gap - mark `removed` on removal/archival. Close the "user-visible spec lands with zero inventory record" hole.

---

## Audit outcome (summary)

The mandate was **already largely in place** - the durability gap is enforcement, not duties:

- `/spec-dev` and `/spec-check` already drive `ALL_FEATURES` via `close-and-log.ps1 -FuncOp ADD|CHANGE`.
- `close-and-log.ps1` engine already supports `-FuncOp DELETE` -> `add.ps1 -Status removed` (engine line ~133).
- `/spec-arc --removes-functionality` already marks records `removed` on archival.
- Real gap found + fixed: the **dev-time removal** path (a spec removes a shipped capability without archiving) was not surfaced - `/spec-dev`/`/spec-check` only documented `ADD|CHANGE`.

Why the inventory still shows 0 `removed` and gaps: duties are opt-in/human-driven, the bulk of records were migrated from the old `FUNCTIONALITY.log` and never reconciled, and there is no mechanical drift gate. The data backlog is Phases 01-03/06; the mechanical enforcement is Phase 07.

---

## Steps

### Step 04.1 - Confirm and tighten the add/change mandate

**Verification:**

- `Grep` `.claude/commands/spec-dev.md` - `FuncOp` ADD/CHANGE mandate present. PASS.
- `Grep` `.claude/commands/spec-check.md` - inventory fallback on `Verified` present (line 110). PASS.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verified both skills mandate inventory writes. `post-change.ps1` keeps inventory decisions out of per-file closure by design (spec-dev.md line 72). No tightening needed on add/change.

---

### Step 04.2 - Add removed lifecycle (removal + archival)

**Files:** `.claude/commands/spec-dev.md`

**Verification:**

- `/spec-arc` removed-on-archival duty present (`--removes-functionality` -> `-FuncOp DELETE`). PASS (already existed).
- `/spec-dev` now documents `-FuncOp DELETE` for dev-time removal. PASS (added this phase).

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - `/spec-arc --removes-functionality` already wired (spec-arc.md line 18). Added `DELETE` to `/spec-dev` `-FuncOp` set + a DELETE bullet (when a spec removes a shipped capability without archiving). spec-check fallback left as-is (rare edge; covered by spec-dev/spec-arc) - noted, not edited, to avoid over-editing.

---

### Step 04.3 - Mechanical "missing/stale inventory" enforcement decision

**Verification:**

- Decision recorded; existing nets identified.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Decision: do NOT add a new dev-time gate yet. Two nets already exist: `/skill-release` Step 12b `[INVENTORY MISSED]` and `/spec-all` final-report grep. The high-value durability fix is a single inventory-drift gate (re-scan vs inventory) analogous to S0440's `assert-settings-doc-sync.ps1` - deferred to Phase 07 §6 Q1, decided after Phase 02/03 reveal the noise level. Avoids a redundant/noisy gate.

---

## Phase Done Criteria

- [x] Steps 04.1-04.3 are `[x]`.
- [x] Removed lifecycle documented for both removal (spec-dev) and archival (spec-arc).
- [x] One dev-log entry for the skill changes.
