# Phase 04 - DOCS_MAP date reconciliation (category C)

**Strategic spec:** [`../S0814_sync-docs-site-from-tickets.md`](../S0814_sync-docs-site-from-tickets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03 (dates must reflect the edits made there)
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-07-05
**Completed:** 2026-07-05

---

## Objective

Fix the stale "Last Updated" dates in `docs/DOCS_MAP.md` for the guides edited in Phases 01-03, and resolve the two-table inconsistency the reconciliation report flagged (category C).

---

## Prerequisites

- [ ] Phases 01, 02, 03 are ✅ Done (so the dates reflect real edits).
- [ ] Read `research/01__doc-freshness-reconciliation.md` section C.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DOCS_MAP.md` | Modified | ±12 |

> Do NOT touch: `WHATS_NEW.md` date/text (release-owned, `/skill-release`), `SETTINGS_REFERENCE*` date (generated + gated), any Wear/Legal table rows. Scope strictly to the narrative guides actually edited plus the inconsistency note.

---

## Steps

### Step 04.1 - Update stale "Last Updated" dates for edited guides

**Files:** `docs/DOCS_MAP.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In the "User Guides & Manuals" table, change the `Last Updated` cell from `2026-06-19` to `2026-07-05` for the rows that were edited in Phases 01-03: README, Quick Start, How-To, FAQ. Leave Troubleshooting (`2026-06-07`) unchanged - it is out of this ticket's edit scope (flag only, not edited). Leave `SETTINGS_REFERENCE` (`2026-06-19`) unchanged - it is generated/gated. Do not edit the What's New row (release-owned).

**Verification:**

- `Grep -n "2026-06-19"` in `DOCS_MAP.md` no longer matches the README / Quick Start / How-To / FAQ rows (they now read `2026-07-05`).
- `Grep -n "2026-07-05"` matches four rows (README, Quick Start, How-To, FAQ) in the User Guides & Manuals table.
- `Grep -n "2026-06-07"` still matches the Troubleshooting row (unchanged).

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS. Four guide rows -> 2026-07-05; remaining 2026-06-19 is only the SETTINGS_REFERENCE row (generated/gated, deliberately untouched); Troubleshooting 2026-06-07 and What's New row untouched.

---

### Step 04.2 - Resolve the two-table freshness inconsistency

**Files:** `docs/DOCS_MAP.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> The same five guides appear in both the "User Guides & Manuals" table (explicit dates) and the "Current public docs" table (`Current`). Reconcile so a reader does not see contradictory freshness for one doc: add a one-line convention note clarifying that the "Current public docs" table tracks publish status while "User Guides & Manuals" carries the historical last-edit date, OR align the two. Keep the explicit dates as the authoritative last-edit signal. No new stale date introduced.

**Verification:**

- `Grep` - a convention/clarification note exists between or under the two tables explaining the date-vs-status distinction (or the tables now agree).
- `Grep -n "Current"` in the "Current public docs" table is unchanged unless deliberately aligned; no row shows two conflicting explicit dates.

**Status:** `[x]` done

**Step Log:**

- 2026-07-05 - Verification PASS. "Date convention" blockquote added directly under the "Current public docs" heading: explicit dates in User Guides & Manuals are the authoritative last-edit signal, `Current` = publish status only. Table rows untouched; no row carries two explicit dates.

---

## Phase Done Criteria

- [x] Both `Step 04.*` are `[x] done`.
- [x] Edited-guide rows read `2026-07-05`; Troubleshooting/SETTINGS_REFERENCE/What's New rows untouched (verified: expected 4x 2026-07-05, 1x 2026-06-07, 1x 2026-06-19 (Settings Reference) | actual matches).
- [x] Two-table inconsistency resolved (note added or tables aligned) - convention note added.
- [x] `Grep` for `TODO(phase-04)` returns zero hits (expected 0 | actual 0).
- [x] Dev log entry added (batched at Phase 06 acceptable) - batched to Phase 06.

---

## Handoff Notes to Next Phase

DOCS_MAP freshness now matches the actual edits. Only the release-runbook anchor (Phase 05) and cleanup (Phase 06) remain.

---

## Rollback Plan

Revert the DOCS_MAP.md edit - purely an index date change, no downstream impact.
