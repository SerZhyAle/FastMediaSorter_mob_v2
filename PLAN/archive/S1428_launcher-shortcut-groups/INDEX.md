# Tactical Plan: S1428 - launcher-shortcut-groups

**Strategic spec:** [`../S1428_launcher-shortcut-groups.md`](../S1428_launcher-shortcut-groups.md)
**Research inputs:** [`research/01__launcher-desktop-map.md`](research/01__launcher-desktop-map.md) - full code survey with file:line citations for the cell model, storage, renderer, render pipeline, edit mode, seeding, the long-press/accessibility seam, the existing collapsible header and current test coverage. The per-phase "Anchors" blocks are a subset of it.
**Feature:** Launcher desktop
**Tier:** 4 - Large (ad-hoc)
**Priority:** 40
**Status:** BlockNeedUserTest
**Phases:** 6 / 6 done
**Last updated:** 2026-08-09

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | section-cell-domain | - | ✅ Done | 5/5 | [PHASE_01__section-cell-domain.md](PHASE_01__section-cell-domain.md) |
| 02 | header-rendering | 01 | ✅ Done | 5/5 | [PHASE_02__header-rendering.md](PHASE_02__header-rendering.md) |
| 03 | collapse-state | 02, 04 | ✅ Done | 6/6 | [PHASE_03__collapse-state.md](PHASE_03__collapse-state.md) |
| 04 | placement-rules | 01 | ✅ Done | 3/3 | [PHASE_04__placement-rules.md](PHASE_04__placement-rules.md) |
| 05 | seeding-and-picker | 01, 04 | ✅ Done | 5/5 | [PHASE_05__seeding-and-picker.md](PHASE_05__seeding-and-picker.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All twelve strategic §6 research items are Resolved and owner sign-off was recorded 2026-08-08 (§3.3).

---

## Three facts this plan must not lose

All three were established against the code, and each contradicts a natural reading of the strategic spec.

- **The guard already compares four values, not three, and collapsed-state is the fifth.** Strategic §3.2 and §5.1.6 call collapsed-state "the fourth member" of the comparison key; the code is one ahead of that. `LauncherCellViewBinder` holds `lastBound: Triple<List<LauncherCellUi>, Int, Boolean>` (line 46) **and** a separate `lastRows: Int` (line 48), and the guard reads `if (lastBound == Triple(cells, columns, editMode) && lastRows == rows) return` (line 71). The row count already had to live outside the `Triple` because the `Triple` was full - its KDoc at lines 68-70 explains why rows joined the guard at all. So collapsed-state is the fifth compared value, and adding it as a third loose field would leave three parallel fields to keep in sync. Phase 03 collapses all five into one data-class key instead.
- **`src/launcherEnabled` has no test source set, so pure logic placed there cannot be unit-tested.** `app_v2/build.gradle.kts` adds `src/launcherEnabled/java` only to `standard` (line 646) and `noLegal` (line 677); a test in the shared `src/test` referencing a class from it fails to compile for `lite`, `photos`, `legacy` and `vr`. That is why `LauncherGridGeometry`, `LauncherCellViewBinder`, `LauncherEditModeManager` and `LauncherResizeManager` all have zero coverage today. Any Android-free arithmetic this ticket adds - membership ranges, collapse row mapping, the straddle predicate - must live under `src/main` to be testable. Phase 04's `LauncherSectionMembership` already satisfies this; Phase 03's collapse geometry must not quietly break it by putting the row math in `LauncherGridGeometry`.
- **The accessibility long-press action is attached inside the shortcut binding path only.** Strategic §5.1.4 says it is attached "to every cell before the command kind is parsed". True for command kind, but the call sits in `bindShortcut`, and `bindGadget` never makes it. So a section header inherits the action only if it reuses the shortcut path - which is exactly the suppression point §6.8 needs.

---

## Accepted consequence, flagged for the device test

Moving the four action shortcuts to the top (strategic §3.1.1, §6.5) reverses an ordering S1402 chose
deliberately: its `commonTail()` KDoc (`LauncherStarterSets.kt`, lines 181-185) states the four sit last
precisely so they do not "push the clock and the lists below the fold". The section costs one full-width
header row plus one row of actions at four or more columns, and two action rows at three columns - so the
clock and the resource lists move down two or three rows on a freshly seeded desktop, and the narrow-grid
case is the worst one.

The owner ruled twice and explicitly, so the plan implements it. If it reads badly on the device the
reversal is one ordering change in `LauncherStarterSets.itemsFor` - S1402's own KDoc says so. Phase 05
should pin the bound with a unit assertion (the clock still lands within the first four rendered rows at
four columns) rather than leaving it to be noticed on hardware.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not edited here; the `docs/ALL_FEATURES.jsonl` record `launcher.titled-sections-on-the-desktop` was written in Phase 06 and the showcase is left to `/skill-release`.
- [x] `dev/CHANGELOG.md` has an entry per phase (01, 02, 04 from the earlier sessions; 03, 05, 06 from this one).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `LauncherSectionCatalog`, `LauncherSectionMembership` and `LauncherSectionCollapseManager` carry `role` + `status=new`.
- [ ] `/spec-check S1428` returns `Verified` - blocked on the device test, which is the only validation available for a launcher home screen (strategic §3.3).
- [ ] Strategic spec `Status:` advanced by `/spec-check` - currently `BlockNeedUserTest`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1428`.

---

## Blockers Log

- 2026-08-08 - **Phases 03 and 04 blocked on strategic §6.12 (new, Open).** The single preset section is
  first on the desktop and membership is positional "down to the next header", but there is no next
  header - so the section owns the entire desktop. Verified against code, not inferred: the clock seeds
  as a `GADGET` and lands below the only header. Two owner rulings then break: §6.11's gadget refusal
  (phase 04.2) would refuse every gadget move, resize and insert anywhere on the desktop, and §6.8's
  collapse (phase 03.3/03.4) would hide the clock and the lists rather than the four functions. Four
  candidate resolutions are costed in §6.12; each is an owner decision, so the pipeline parked the
  ticket at `BlockQuestions` instead of guessing.
- 2026-08-08 - **Resolved by the owner: §6.12 option (в), a second preset header.** Seeding places a
  second full-width header immediately after the four functions, so the first section is bounded by a
  real next header and §6.7 applies literally - no special rule for a trailing section is needed.
  Consequences the plan now carries: two localized section names instead of one, the gadget refusal only
  ever fires inside the (gadget-free) first section, and the clock drops one further row on a freshly
  seeded desktop. Phases 03, 04 and 05 unblocked.
- Phases 01 and 02 are complete and safe to leave in the tree: nothing seeds a `SECTION` cell and the
  picker offers no row for one, so no user can produce a header. The code is inert until phase 05.

---

## Change Log

- 2026-08-08 - Initial tactical plan authored by `/spec-all` Stage F2.
- 2026-08-08 - Phases 01 and 02 implemented by `/spec-dev`. Three plan corrections recorded in the phase
  files: a third exhaustive `when` the survey missed (`ExecuteLauncherCommandUseCase`), the header layout
  and bind branch pulled forward from 02.1/02.3 because phase 01 could not otherwise compile, and the
  full-width span routed through a shared `renderSpanW` because `footprintOf` alone does not reach the
  layout path. Phase 04 should run before phase 03 when work resumes - step 04.1's own prompt says the
  collapse geometry consumes `LauncherSectionMembership`, and the INDEX fact about `src/launcherEnabled`
  having no test source set means that arithmetic has to live in `src/main` to be testable at all.
- 2026-08-08 - Parked at `BlockQuestions` on strategic §6.12; see Blockers Log.
- 2026-08-08 - Unparked: owner ruled §6.12 option (в). Phase 05 gains a fifth step for the second header
  and its trilingual name; phase 04's membership function needs no trailing-section rule; phase 03's
  collapse geometry consumes that same bounded membership. Phase 04 runs before phase 03.
- 2026-08-08 - Three plan corrections found by reading the code before writing it, all recorded in the
  phase files rather than here:
  - §6.11's gadget refusal had to be re-ruled by the owner: with two headers the whole desktop is inside
    a section, so the literal "no gadget inside a section" would have refused every gadget everywhere.
    It is now "a gadget may not cover a header row".
  - The occupied-slot refusal this phase must imitate is **silent** - `LauncherHomeViewModel` documents
    add as doing nothing, move as snapping back and resize as keeping the last valid size. Step 04.3 is
    therefore about *not* adding a Toast, the opposite of how it read.
  - A header must be stored at the maximum column count, not the current one: `findOverlapping` reads the
    stored span while the renderer widens to the live one, so a header stored narrow leaves the rest of
    its row free in the database. Phase 05 note carries the two call sites that clamp today.
- 2026-08-09 - Phases 03, 05 and 06 closed; ticket parked at `BlockNeedUserTest` with five probes. Phase 05
  turned out to be already implemented (2026-08-08) but never closed - the INDEX read `0/5` while all five
  steps were `[x]` - so this session verified it against the working tree rather than re-implementing it.
  Phase 03 shipped the fold: one named render key replacing the `Triple` plus its parallel `lastRows`,
  `LauncherSectionMembership.renderRowFor` projecting stored rows onto drawn ones, a
  `LauncherSectionCollapseManager` holding folded state per orientation, and the collapsed condition
  announced to TalkBack. Two findings the phase introduced were fixed (`TooManyFunctions`,
  `MultiLineIfElse`); a third pre-existing one was fixed in passing (`CyclomaticComplexMethod`, half of
  S1541). One residual: `LargeClass:LauncherHomeActivity`, the other half of S1541, which fails the scoped
  detekt gate for any ticket that opens that file.
- 2026-08-08 - Phase 04 implemented and closed. `post-change` PASS (Kotlin, scoped to the four changed
  files); `LauncherSectionMembershipTest` 13 tests, 0 failures, read from the JUnit XML rather than the
  task exit code. The phase-boundary audit found and fixed one defect before closure: `findFreeAnchor`
  queried the header rows once per probed row, inside the same transaction as the insert, where they
  cannot change - hoisted to a single read, and skipped entirely for a shortcut or a one-row cell, so
  the common placement pays no round trip at all.
