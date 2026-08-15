# Phase 04 - Docs, catalog, cleanup

**Strategic spec:** [`../S1261_bugfix-sub-1x-zoom-missing-s25fe.md`](../S1261_bugfix-sub-1x-zoom-missing-s25fe.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Catalog + dev-log closure and hand the ticket to the owner for the S25 FE pass.

---

## Prerequisites

- [ ] Phases 01-03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` + `.md` (regenerated) | Modified | n/a |
| `dev/CHANGELOG.md` (via dev-log tooling) | Modified | n/a |

---

## Steps

### Step 04.1 - Catalog regen and roles

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> `catalog_sync.ps1 -Module app_v2` once; `set.ps1` role+status for `CameraLensSelectionReporter` and `LensEquivalentCalculator`.

**Verification:**

- `Grep` - both new classes present in `dev/CATALOG/app_v2.jsonl` with non-empty roles.

**Status:** `[x]` done (18:00 catalog_sync: 2346 records; set.ps1 wrote role+status=new for
`CameraLensSelectionReporter` and `LensEquivalentCalculator` - "Updated .. role, status=new" x2)

---

### Step 04.2 - Dev logs and gates

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Batch dev-log entries (one per phase). Run `.\a.ps1 fg`; run `assert-detekt.ps1 -Gate -ChangedFiles` over every `.kt` touched by the ticket. No FEATURES/ALL_FEATURES record - strategic §8 says no changes (restoration of an S1189-declared capability).

**Verification:**

- `fg` exit 0; detekt scoped PASS.

**Status:** `[ ]` not done

---

### Step 04.3 - Park for owner device test

**Files:** catalog journal via `update.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Confirm the `S1261:` probes from Phase 03 are in place, then `update.ps1 -Id S1261 -Status BlockNeedUserTest -StatusNote '<on S25 FE: zoom row shows 0.5; tapping it gives the wide picture; tele pill reads ~3 not 1.3; screen opens on the main lens; System info -> Cameras -> App view section present - re-capture and send it>'`.

**Verification:**

- `select.ps1 -Id S1261` shows `BlockNeedUserTest` with the note.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. `/spec-check` runs after the owner's device pass.

---

## Rollback Plan

Docs-only phase - revert commits.
