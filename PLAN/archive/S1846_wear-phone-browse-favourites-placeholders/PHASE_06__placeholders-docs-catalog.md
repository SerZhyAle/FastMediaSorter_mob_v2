# Phase 06 - No orphaned placeholder, capability recorded, ticket closed

**Strategic spec:** [`../S1846_wear-phone-browse-favourites-placeholders.md`](../S1846_wear-phone-browse-favourites-placeholders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 05
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

Every remaining placeholder in the watch module names an open ticket, both shipped capabilities are in the inventory, the catalogs match the tree, and the ticket closes through the facade.

---

## Prerequisites

- [ ] Phases 02, 03 and 05 are ✅ Done.
- [ ] No unrelated in-flight edit from a sibling session sits in the files being closed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | +2 records |
| `dev/CATALOG/wear.jsonl` and `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Appended by script | n/a |

---

## Steps

### Step 06.1 - Prove no placeholder names a closed ticket

**Files:** none - verification only
**Depends on:** - start of phase

**Prompt for developer:**

> List every `NotYetHereScreen(ownerTicket = ..)` left in the module and resolve each named ticket through `scripts/spec_catalog/select.ps1`. Every one must be in an open status. A placeholder naming a closed ticket is repointed at the ticket that will actually replace it, or - if none exists - parked as its own ticket via `/spec-draft` and repointed at that.

**Why:**

Strategic goal 2 and criterion 3 require that no placeholder names a closed ticket, and strategic §1 explains why it matters: the `ownerTicket` contract defines the field as the ticket that will replace the screen, so naming a closed one means the screen has no owner at all - which is the defect that produced this ticket.

**Verification:**

- `Grep` - every `ownerTicket = "Sxxxx"` in `wear/src` is listed.
- For each id, `select.ps1 -Id <id> -Format json` reports a status other than `Verified` or `Archived`.
- `Grep` - `S1846` no longer appears as an `ownerTicket` value anywhere.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - `ownerTicket` values remaining in `wear/src`: S1710 only, resolved as `In Progress` - open, so those two placeholders are correct and were not touched. `S1846` no longer appears as an `ownerTicket` anywhere; both of its placeholders were replaced by real screens in Phases 02 and 05.

---

### Step 06.2 - Record the two shipped capabilities

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add two records with `scripts/all_features/add.ps1`, English only: the watch Favourites list - what was marked on the watch, opening in the watch player and unmarkable from the row - and browsing the paired phone's media by type from the watch, with a tap opening the file. Do not edit `docs/FEATURES*.md`.

**Why:**

Strategic §8 states that two dead ends become working sections, which is a user-visible capability, and CLAUDE.md §11 makes `ALL_FEATURES.jsonl` the ledger the release showcase is generated from - a capability missing from it never reaches the showcase.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - both new records mention the watch.
- `git diff --name-only` does not list `docs/FEATURES.md`, `_RU.md` or `_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Two records added via `all_features/add.ps1`, area `Wear OS`, flavors `standard,noLegal`, spec S1846: `wear.favourites-list-on-watch` and `wear.phone-media-browse-by-type`. `validate.ps1` exit 0 (769 records). `docs/FEATURES*.md` untouched.

---

### Step 06.3 - Regenerate both catalogs and fill the new roles

**Files:** `dev/CATALOG/wear.jsonl`, `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 06.2

**Prompt for developer:**

> Run `catalog_sync.ps1` once per module - `-Module wear` and `-Module app_v2` - then set `role` and `status` on every class this ticket added with `dev/CATALOG/scripts/set.ps1`.

**Why:**

CLAUDE.md requires a new class to declare its role in the catalog, which is the index every later research pass queries before grepping.

**Verification:**

- `query.ps1 -ClassMatches "FavouritesViewModel"` returns one record with a non-empty role.
- `query.ps1 -ClassMatches "WearFavoriteRecord"` returns one record with a non-empty role.
- Both catalog files are newer than the last source edit.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - `catalog_sync.ps1` run for both modules; the facade then ran the wear sync again as part of closure. `role` + `status=new` set on the three classes this ticket added to `wear` (`WearFavoriteRecord`, `FavouritesViewModel`, `FavouritesScreen`). No class was added to `app_v2` - its side of the change is fields and a filter inside existing files.

---

### Step 06.4 - Close through the facade and hand the ticket to its device test

**Files:** every file touched by Phases 01 to 05
**Depends on:** Step 06.3

**Prompt for developer:**

> Insert one `Timber.d("S1846: ..")` probe per changed flow before the final build - the filtered phone browse, the phone-file open, and the favourites list - each on ONE line and under 120 characters. Then run `post-change.ps1` once with `-Files` naming the whole changed set, `-ScopeToFile`, `-ChangeType Mixed`, and `-Module wear`; run it again with `-Module app_v2` only if the facade's own output says the phone module needs its own catalog sync.
>
> Set the status to `BlockNeedUserTest` BEFORE the closing run, not after: the probes are bound to that status and the ticket-log gate refuses them while the ticket is still In Progress. The status note names the two screens, the exact steps, and each probe as `Probe template:`.

**Why:**

CLAUDE.md's debug-tag invariant binds a `Timber.d("Sxxxx: ..")` line to the `BlockNeedUserTest` status in both directions, and strategic §3.3 fixes the validation level as build plus an on-device look at both screens - which this run cannot perform, so the note is the whole handoff.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS` or names its advisories.
- `Grep` - exactly three `Timber.d("S1846:` lines exist, each on one line and under 120 characters.
- `assert-no-ticket-logs.ps1` exits 0.
- `Grep` - `S1846` appears exactly once in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Three probes inserted before the final build, one per changed flow, each on ONE line and 71-77 characters (the 120 limit is the one that bites). Status flipped to `BlockNeedUserTest` BEFORE the closing run, which is what makes the probes legitimate; `assert-no-ticket-logs` then reported `0 forbidden log id(s), 0 missing probe(s)`. Final: `fw` 0, `fwu` 0, `fk` 0, then `post-change.ps1` over the whole 23-file set with `-ScopeToFile -ChangeType Mixed -Module wear -RegistryAck feature-inventory` -> **exit 0, `PASS WITH ADVISORIES (1)`**. The advisory is `new-lexeme-count`, repo-wide and owned by `/spec-prerelease` step 0.8 under Rule 30.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] `.\a.ps1 fw`, `.\a.ps1 fwu` and `.\a.ps1 fk` all exit 0 on the final tree, with the probes in place.
- [x] The ticket sits in `BlockNeedUserTest` with a note naming both screens, the paired-phone requirement and all three probes.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate. The device test drains through `/spec-sweep`.

---

## Rollback Plan

Inventory records, catalogs and the changelog only; revert the commit and regenerate the catalogs.

---

## Predicate correction - the changelog row count

Step 06.4 asserts `S1846` appears exactly once in `dev/CHANGELOG.md`. It appears seven times, and six of
those are correct: the spec scaffold, the placeholder repointing done when the defect was found, two quiz
transitions, the research artifact, and a status change - each a separate logical change made before this
run, each properly logged at the time. The rule CLAUDE.md states is one row per logical change, not one row
per ticket for its whole life.

The implementation IS one row, naming its 23-file set in the tail. That is the invariant the predicate meant
to protect and it holds. A future ticket writing this predicate should count rows whose target column is the
ticket id, not occurrences of the id anywhere in the file.
