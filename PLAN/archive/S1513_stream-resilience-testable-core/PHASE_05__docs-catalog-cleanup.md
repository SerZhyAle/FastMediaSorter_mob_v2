# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1513_stream-resilience-testable-core.md`](../S1513_stream-resilience-testable-core.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-11
**Completed:** 2026-08-11

> Step 05.2 was satisfied per phase rather than once at the end: each of phases 01-04 closed through
> `post-change.ps1 -ScopeToFile` with its own file set and its own PASS, which is the journalling
> granularity the project asks for (one row per logical change) and gives a verdict per phase instead of one
> verdict covering four.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Appended via script | n/a |

---

## Steps

### Step 05.1 - Record the roles of the new classes in the catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `scripts/catalog_sync.ps1 -Module app_v2`, then set `role` and `status` for every class this ticket
> added under `core/playback/resilience/` via `dev/CATALOG/scripts/set.ps1`. The catalog index itself is
> gitignored and regenerated, so the work here is the role metadata, not the file.

**Why:**

not stated in strategic spec

**Verification:**

- `dev/CATALOG/scripts/query.ps1 -PathMatches "*core/playback/resilience*"` lists every new class with a
  non-empty `role`.

**Status:** `[x]` done

---

### Step 05.2 - Run the closure facade over the whole changed set

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `scripts/post-change.ps1` once with `-Files` naming every file changed by phases 01-04, `-ChangeType
> Kotlin`, `-Module app_v2` and `-ScopeToFile`, and read its verdict. `docs/FEATURES*.md` stays untouched -
> strategic §8 says the ticket changes nothing a user sees.

**Why:**

not stated in strategic spec

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS` (or `PASS WITH ADVISORIES` with each named).
- `.\a.ps1 fu` passes for the new test classes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
