# Phase 04 - Validation

**Strategic spec:** [`../S0339_strings-thematic-split.md`](../S0339_strings-thematic-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Prove the per-locale key-union is unchanged, parity audit is green, and the target variant builds.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| (validation only - no source edits) | - | - |

---

## Steps

### Step 04.1 - Union invariant before == after

**Files:** (read-only)
**Depends on:** - start of phase

**Prompt for developer:**

> Re-run `-Action audit`, split per locale, and diff against the Phase 02 baseline files. The sorted key-union for each locale must be byte-identical (same set, no loss, no dup). Any diff line is a hard failure.

**Verification:**

- Manual: `diff` of after-vs-baseline is empty for EN, RU, UK - record `expected: 0 diff lines | actual` per locale.

**Status:** `[ ]` not done

---

### Step 04.2 - Locale parity audit

**Files:** (read-only)
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1` for the migrated key prefixes (or full run). Exit 0 required.

**Verification:**

- Manual: `check_strings_localized.ps1` exit code `0`.

**Status:** `[ ]` not done

---

### Step 04.3 - Target variant build

**Files:** (build)
**Depends on:** Step 04.2

**Prompt for developer:**

> Build `standardDebug` via the project build wrapper (`.\a.ps1 bd` / `/build`). aapt2 must merge all thematic files without duplicate-resource or missing-resource errors.

**Verification:**

- Manual: build `BUILD SUCCESSFUL`; on failure inspect with `a.ps1 bf` (not `tail`).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] All `Step 04.*` are `[x] done`.
- [ ] Zero union diff in every locale.
- [ ] `check_strings_localized.ps1` green.
- [ ] `standardDebug` builds.

---

## Handoff Notes to Next Phase

Functional equivalence proven. Phase 05 records the change and updates tool docs.

---

## Rollback Plan

If build fails on a duplicate/missing resource, `git checkout` the resource dirs (Phase 03 rollback) and re-inspect the taxonomy for a mis-attributed prefix.
