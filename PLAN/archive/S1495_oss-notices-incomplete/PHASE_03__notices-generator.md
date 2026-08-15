# Phase 03 - Notices generator

**Strategic spec:** [`../S1495_oss-notices-incomplete.md`](../S1495_oss-notices-incomplete.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-10
**Completed:** 2026-08-10

---

## Objective

Render the machine snapshot and the three localized OSS notice pages from the parser and the licence manifest, replacing the hand-authored two-library page.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/docs/generate-oss-notices.ps1` | New | ≤ 420 |
| `docs/legal/oss-notices.json` | New (generated) | n/a |
| `docs/OPEN_SOURCE.md` | Modified (becomes generated) | n/a |
| `docs/OPEN_SOURCE.ru.md` | New (generated) | n/a |
| `docs/OPEN_SOURCE.uk.md` | New (generated) | n/a |

---

## Steps

### Step 03.1 - Build the snapshot

**Files:** `scripts/docs/generate-oss-notices.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/docs/generate-oss-notices.ps1` following the shape of `scripts/docs/generate-flavor-matrix.ps1`: a comment-based help block naming every exit code, a `-Check` switch that renders into memory and compares against disk without writing, and a `-Quiet` switch. Dot-source `OssDependencyParser.ps1`, read both modules, keep the shipping coordinates, merge in the transitive manifest entries, join each to its licence, and compute a `ShippedIn` list of flavors per coordinate: a plain `implementation` yields all six flavors, a `<flavor>Implementation` yields that flavor alone. Emit `docs/legal/oss-notices.json` sorted by coordinate. Exit 2 when a shipping coordinate has no manifest entry, naming the coordinate.

**Why:**

Strategic ADR-3 makes an unknown coordinate a fatal condition precisely so a newly added dependency cannot slip past unnoticed, which is the mechanism §2.5 requires for the list to stay true after the dependency set changes.

**Verification:**

- `Glob` - `scripts/docs/generate-oss-notices.ps1` and `docs/legal/oss-notices.json` exist.
- `Grep` - `ShippedIn` present in both the script and the JSON.
- Run the generator - exit 0.
- Run it with a temporary manifest lacking one coordinate - exit 2, the missing coordinate named in the output.

**Status:** `[x] done`

---

### Step 03.2 - Render the English page

**Files:** `scripts/docs/generate-oss-notices.ps1`, `docs/OPEN_SOURCE.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Render `docs/OPEN_SOURCE.md` from the snapshot, preserving the existing Jekyll front matter (`layout`, `title`, `permalink: /docs/OPEN_SOURCE.html`) and the trailing navigation line. Emit a generated-file banner naming this script, then one table with columns Library, Coordinate, License, Shipped in, Source. Keep the full LGPL-2.1 paragraph already on the page and add the equivalent paragraph for GPL-3.0, each rendered only while a coordinate carries that licence. Mark a transitive row with its `Via` coordinate.

**Why:**

Strategic §2.2 requires the GPL component to be named together with its terms and its `noLegal` scope, and §11.2 makes that an observable criterion - the licence paragraph is what carries the terms onto the published page.

**Verification:**

- `Grep` - `NewPipeExtractor` matches in `docs/OPEN_SOURCE.md`.
- `Grep` - `noLegal` matches in `docs/OPEN_SOURCE.md`.
- `Grep` - `permalink: /docs/OPEN_SOURCE.html` still matches exactly once.
- `Grep` - `Shipped in` matches once.
- Run the generator twice - the second run reports no change, exit 0.

**Status:** `[x] done`

---

### Step 03.3 - Render the Russian and Ukrainian pages

**Files:** `scripts/docs/generate-oss-notices.ps1`, `docs/OPEN_SOURCE.ru.md`, `docs/OPEN_SOURCE.uk.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Render `docs/OPEN_SOURCE.ru.md` and `docs/OPEN_SOURCE.uk.md` from the same snapshot with permalinks `/docs/OPEN_SOURCE.ru.html` and `/docs/OPEN_SOURCE.uk.html`. Hold the framing prose - page title, the introductory sentence, the table headers, the licence paragraph headings - in a per-locale table inside the generator. Do not translate library names, SPDX identifiers, coordinates or URLs. Check the introductory sentence against `docs/COMMUNICATION_POLICY.md` §2 for message formula and §6 for tone.

**Why:**

Strategic §6.3 records the owner's ruling that this page joins the trilingual row its registry neighbours already form, and ADR-5 fixes the split: prose is localized, the table is not.

**Verification:**

- `Glob` - `docs/OPEN_SOURCE.ru.md` and `docs/OPEN_SOURCE.uk.md` exist.
- `Grep` - `permalink: /docs/OPEN_SOURCE.ru.html` matches once in the RU page.
- `Grep` - `NewPipeExtractor` matches in all three pages.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- Run the generator twice - the second run reports no change, exit 0.

**Status:** `[x] done`

---

### Step 03.4 - Prove idempotence and drift detection

**Files:** `scripts/docs/oss-notices.tests/Run-Tests.ps1`
**Depends on:** Step 03.3

**Prompt for developer:**

> Extend the test suite with a case asserting that `-Check` exits 0 on a freshly generated tree, exits 1 after a single character is altered in `docs/OPEN_SOURCE.md`, and restores the file afterwards. Assert that the generator exits 2 on a fixture coordinate absent from the manifest.

**Why:**

Strategic §11.5 and §11.6 state idempotent regeneration and the fatal-unknown-coordinate behaviour as completion criteria, and both are claims about the script that only a test can hold true as the script changes.

**Verification:**

- Run `pwsh -NoProfile -File scripts/docs/oss-notices.tests/Run-Tests.ps1` - exit 0.
- `Grep` - `-Check` referenced in the test suite.

**Status:** `[x] done`

---

## Step Log

- 2026-08-10 - Steps 03.1 to 03.4 done. Generator emits 99 entries into the snapshot and three pages; regeneration is byte-identical, `-Check` reports drift on a single altered character, and an unlisted shipping coordinate exits 2 naming the coordinate.
- 2026-08-10 - Two PowerShell traps hit and fixed in place: a parameter/local case collision (`$locale` is the `$Locale` parameter), and `$list.Add('fmt' -f $a, $b)` where the commas are read as further method arguments unless the whole expression is parenthesised.
- 2026-08-10 - Editorial correction before publishing: the SMBJ and epub4j notes first described our own past error. A published legal page states what the licence is; the correction record belongs in the ticket. Rewritten to explain the licence and the reason the two are commonly conflated.
- 2026-08-10 - Framing prose checked against `docs/COMMUNICATION_POLICY.md` §2 and §6: informative, no raw exception text, no confirmation phrasing, no empty state.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - not applicable, no compiled source touched.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG` regeneration - not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The three pages are render targets from this phase on, never hand-edited. Phase 04 enforces that.

---

## Rollback Plan

Restore `docs/OPEN_SOURCE.md` to its pre-phase content, delete the two new locale pages, the snapshot and the generator - the published page returns to its current two-library state.
