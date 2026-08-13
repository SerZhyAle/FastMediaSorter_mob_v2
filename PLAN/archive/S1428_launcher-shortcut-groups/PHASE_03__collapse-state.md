# Phase 03 - Collapse state

**Strategic spec:** [`../S1428_launcher-shortcut-groups.md`](../S1428_launcher-shortcut-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 04
**Blocks:** none
**Steps done:** 6 / 6

> **Unblocked 2026-08-08.** Strategic §6.12 resolved as option (в): seeding places a second header, so
> "the rows the section owns, up to the next header" is a bounded range on a freshly seeded desktop and
> collapsing the first section hides the four functions, not the clock and the lists.
>
> **Run Phase 04 first** - already reflected in `Depends on`. Step 04.1's prompt makes the collapse
> geometry a consumer of `LauncherSectionMembership`, and the INDEX's second "fact this plan must not
> lose" forbids putting that arithmetic in `LauncherGridGeometry` - `src/launcherEnabled` has no test
> source set, so a `src/test` case referencing it fails to compile on the four launcher-less flavors.
> Steps 03.3/03.4 point at that file for the *view* work only; the row math itself stays in the
> `src/main` membership helper and is called from there.
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Make a tap on the header collapse and expand its rows without moving a single stored position, persist that per orientation, and get collapsed-state into the renderer's comparison key without destroying gadget views.

---

## Anchors

- `LauncherCellViewBinder.lastBound` - `.../grid/LauncherCellViewBinder.kt:46` - `Triple<List<LauncherCellUi>, Int, Boolean>`; guard at `:71`, reassignment at `:72`; KDoc `:50-58` explains why the guard is load-bearing.
- `CollapsibleSectionStore` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionStore.kt:12` - caller-supplied `<screen>__<section>` keys over one `SharedPreferences` namespace.
- `LauncherHomeViewModel` - `.../ui/launcher/LauncherHomeViewModel.kt`.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] Re-read `LauncherCellViewBinder` KDoc lines 50-58 before touching the guard - it is a written requirement, not a comment (CLAUDE.md Rule 8).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` | Modified | ≤ 700 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometry.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherSectionMembership.kt` | Modified | ≤ 200 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 780 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 1000 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/launcher/LauncherSectionCollapseTest.kt` | New | ≤ 250 |

**Plan corrections applied 2026-08-08, before the first step ran:**

- `LauncherSectionMembership.kt` was missing from this table although the phase header already
  requires the row arithmetic to live there ("the row math itself stays in the `src/main` membership
  helper"). Added.
- `LauncherHomeActivity.kt` was missing although step 03.2 changes `bind`'s signature and this is its
  only call site (`renderDesktop()`, line 861), and the collapsed-state `StateFlow` needs a collector
  next to the two that already re-render. Added.
- The ViewModel's budget read `≤ 600` against a file that was already 683 lines before this phase
  started, so it was unmeetable as written. Raised to `≤ 780`; CLAUDE.md Rule 2's 1500-line limit is
  the binding one.

---

## Steps

### Step 03.1 - Replace the `Triple` guard with a named render key

**Files:** `.../grid/LauncherCellViewBinder.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace `lastBound: Triple<List<LauncherCellUi>, Int, Boolean>` **and the separate `lastRows` field** with one private `data class` holding cells, column count, edit mode, the row count and the collapsed-section set, and compare that instead. `kotlin.Triple` holds exactly three elements, so collapsed-state cannot simply be appended. Keep the early-return semantics identical - only the key's shape changes in this step.

**Why:**

Strategic §3.2 "Производительность" and §5.1.6 require collapsed-state to enter the renderer's comparison key, and the key is a stdlib `Triple` whose arity is fixed, so the type has to change before the value can join it.

**Verification:**

- `Grep` - `Triple<` no longer appears in `LauncherCellViewBinder`.
- `Grep` - the new key type carries exactly five properties, and no `lastRows` field survives beside it.
- `.\a.ps1 fk` passes.

**Plan correction applied 2026-08-08 (five, not four).** As written this step said four properties and
left `lastRows` beside the key, which is precisely what the INDEX's first "fact this plan must not
lose" forbids: the row count already lives outside the `Triple` (`LauncherCellViewBinder.kt:48`,
guard at `:72`), so a four-property key plus `lastRows` plus the collapsed set is three parallel
fields to keep in sync. The INDEX's instruction - "Phase 03 collapses all five into one data-class
key instead" - is followed here, and the Handoff Note below is corrected the same way.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 3/3 PASS. `RenderKey` data class (cells, columns, editMode, rows, collapsedSections) replaces `lastBound: Triple` + `lastRows`; no `Triple` and no `lastRows` field remain in the file. `.\a.ps1 fk` exit 0. Files: `grid/LauncherCellViewBinder.kt` (+11 LOC).

---

### Step 03.2 - Feed collapsed-state into the key

**Files:** `.../grid/LauncherCellViewBinder.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Pass the collapsed-section set into `bind` and include it in the key built and compared at the guard. A change in collapsed-state must make the guard miss and rebind; nothing else may.

**Why:**

Strategic §7 rates a collapsed-state omitted from the key a high-probability risk with two failure modes - collapsing never repaints, or every Home visit rebuilds the gadget views and kills the work they had just started.

**Verification:**

- Manual: strategic §11.12 - collapse and expand while a gadget outside the section is mid-work; its work is not interrupted.
- `Grep` - the collapsed set is read in the same expression that builds the key.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 1/1 mechanical PASS (`RenderKey(cells, columns, editMode, rows, foldedSections)` - the key-building expression reads the collapsed set). `.\a.ps1 fk` exit 0. §11.12 is on-device and stays MANUAL-REQUIRED. Plan correction: the set fed to the key is the collapsed set **folded for edit mode** - `if (editMode) emptySet() else collapsedSections`. Two reasons, both from code rather than the spec: a drop maps a pixel row straight to a stored row (`LauncherEditModeManager` -> `LauncherDesktopLayout.cellAt` -> `moveCell`), so arranging over folded rows would land every dragged cell that many rows off; and this step's own requirement - "nothing else may make the guard miss" - is violated if a toggle made while editing rebuilds an identical tree and destroys every gadget view with it. Files: `grid/LauncherCellViewBinder.kt` (+7 LOC).

---

### Step 03.3 - Collapse by layout, never by moving stored positions

**Files:** `.../grid/LauncherGridGeometry.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Collapsing a section means not drawing the rows it owns and lifting everything below by exactly that height at layout time. Do not write a single row or column back to storage while collapsing or expanding.

**Why:**

Strategic §5.1.6 states that expanding would otherwise have to restore someone else's arrangement, which nothing can do, and §7 lists a collapse implemented by shifting stored positions as a defect whose consequence is a desktop that creeps upward irreversibly.

**Verification:**

- Manual: strategic §11.9 - after collapse then expand, every cell sits at the same row and column as before.
- `Grep` - no repository write call appears on the collapse path.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 1/1 mechanical PASS (no repository, DAO, update, insert or persist call in `LauncherSectionMembership` or `LauncherGridGeometry` - the whole collapse path). `.\a.ps1 fk` exit 0. §11.9 is on-device and stays MANUAL-REQUIRED. Implementation: `LauncherSectionMembership.renderRowFor` maps a stored row to the drawn row (null = inside a folded section), `LauncherGridGeometry.renderPlan/rowsForRendered/footprintOfRendered` project a cell list through it, and the binder lays out, sizes the canvas and sweeps empty slots off that projection. Storage is read, never written. Files: `domain/model/launcher/LauncherSectionMembership.kt` (+28 LOC), `grid/LauncherGridGeometry.kt` (+47 LOC), `grid/LauncherCellViewBinder.kt` (+8 LOC).

---

### Step 03.4 - Bound the lift by the same rule that defines membership

**Files:** `.../grid/LauncherGridGeometry.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> The rows a section owns run from its header down to the next header, and the lift applied on collapse must be measured against that same boundary - never past the next header.

**Why:**

Strategic §6.7 defines membership positionally as everything below the header up to the next header, and §6.8 requires that a collapsed section never hide a cell it does not own - §7 rates that a medium-probability defect where a foreign shortcut vanishes until the section is expanded again.

**Verification:**

- Manual: strategic §11.10 - a collapsed section hides nothing below the next header.
- Unit: a geometry case with two headers proves the lift stops at the second.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 1/1 mechanical PASS. `LauncherSectionCollapseTest`, 10 tests, 0 failures, read from `TEST-...LauncherSectionCollapseTest.xml` rather than the task exit code. The two-header case is `the lift stops at the next header`: folding the section headed at 0 lifts row 5 to 1 and row 9 to 5, never further. The boundary rule itself is one expression inside `renderRowFor` and was therefore written in step 03.3's edit - splitting it across two edits would have meant shipping an unbounded lift in between. First run failed on the two-section case: the test asserted a four-row lift where the code folds five (each header stays on screen, so only the rows *between* headers fold) - the test expectation was wrong, the implementation was not. Files: `domain/model/launcher/LauncherSectionCollapseTest.kt` (new, 89 LOC).

**Plan correction applied 2026-08-09 (test path).** `Files Touched` puts the test under
`src/test/java/com/sza/fastmediasorter/ui/launcher/`, but the class it exercises is
`LauncherSectionMembership` in `domain.model.launcher`, and its sibling `LauncherSectionMembershipTest`
already lives beside it. The test was written to `src/test/java/com/sza/fastmediasorter/domain/model/launcher/`
so the package matches its directory and the two tests of one object sit together.

---

### Step 03.5 - Persist collapsed-state per orientation

**Files:** `.../ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Store collapsed-state through the existing `CollapsibleSectionStore`, keying it by orientation so portrait and landscape keep separate values, and default a section to expanded. Reuse the store rather than adding a column - it takes caller-supplied string keys and already has its own namespace.

**Why:**

Strategic §6.8 requires collapsed-state to be per orientation and to survive rotation and restart, and §6.3 establishes the two orientations as fully independent layouts; §3.2 "Совместимость данных" forbids the schema migration a new column would cost.

**Verification:**

- Manual: strategic §11.11 - collapse in portrait, rotate, and landscape is unaffected; restart preserves both.
- `Grep` - the persistence key includes the orientation.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 1/1 mechanical PASS - `collapseKey(orientation, target)` builds `launcher_desktop__<ORIENTATION>__<target>`, the store's own `<screen>__<section>` shape with the orientation folded into the screen half. `.\a.ps1 fk` exit 0. §11.11 is on-device and stays MANUAL-REQUIRED. Implementation: `LauncherHomeViewModel` gains `@ApplicationContext appContext` (the same `@Inject constructor` shape `KeybindingRemapViewModel` already uses - no new Hilt scope or qualifier), a `SharedPreferencesCollapsibleSectionStore`, a derived `collapsedSections: StateFlow<Set<String>>` and `toggleSection(cell)`. Sections are addressed by encoded target, not row, so dragging a header elsewhere carries its folded state with it. The header's tap listener is the binder's new `onSectionClick`, wired in `LauncherHomeActivity` alongside a fourth render collector. Files: `LauncherHomeViewModel.kt` (+52 LOC), `grid/LauncherCellViewBinder.kt` (+4 LOC), `LauncherHomeActivity.kt` (+8 LOC).

---

### Step 03.6 - Announce collapsed-state to TalkBack

**Files:** `.../grid/LauncherCellViewBinder.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> Expose the header's expanded or collapsed condition as accessibility state so a screen reader says which it is. This is state on a heading, not a long-press action - step 02.4's suppression stays in force.

**Why:**

Strategic §3.2 "Доступность" requires collapsed-state to be announced so a screen reader reports whether the section is collapsed or expanded.

**Verification:**

- Manual: TalkBack announces the collapsed condition and its change on tap.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification: the step's only predicate is on-device and stays MANUAL-REQUIRED; `.\a.ps1 fk` exit 0. `announceSectionState` puts the folded condition on the header root as a state description above API 30 and folds it into the content description below it - the split `CollapsibleSectionHeader` already makes, because TalkBack does not read a state description before API 30 and the launcher ships to minSdk 26. No new strings: `collapsible_section_state_expanded` / `_collapsed` already exist in all three locales. Nothing announces an action, so step 02.4's long-press suppression is intact. Files: `grid/LauncherCellViewBinder.kt` (+24 LOC).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, APK `v2.60.8082.309-DEBUG`.
- [x] `.\a.ps1 fu --tests "*LauncherSection*"` passes - `LauncherSectionCollapseTest` 10/0 and `LauncherSectionMembershipTest` 13/0, read from the JUnit XML.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. See `## Phase-boundary audit` below.

**Closure note - one gate FAILs on another ticket's debt.** `post-change.ps1 -ScopeToFile` over the seven
changed files: every gate PASS except `assert-detekt`, which reports one finding -
`LargeClass:LauncherHomeActivity` (class ~900 LOC, threshold 600). That is **S1541**, parked earlier the
same day with a recorded experiment proving the debt predates any current ticket: the edit was reverted,
the gate re-run, and both findings stayed. This phase fixed the other half of S1541 in passing - the
thirteen-way content-category branch moved out of `registerAddFlowListeners` into `openPickerForCategory`,
so `CyclomaticComplexMethod` is gone. Clearing `LargeClass` needs the manager extraction S1541 is written
about (~300 LOC out of the Activity) and is not this ticket's work. Because the facade aborts before its
mutating steps, the dev-log row and the catalog sync were run individually; both exit 0.

Two findings this phase **did** introduce and did fix: `TooManyFunctions` on `LauncherHomeViewModel` (39
functions before, threshold fires at 40) and `MultiLineIfElse` in the binder.

**Plan corrections applied 2026-08-09 (line budgets).** `LauncherHomeActivity.kt` ended at 1001 lines
against a `≤ 1000` budget - one line over a plan-local figure; CLAUDE.md Rule 2's 1500-line limit is the
binding one. `LauncherHomeViewModel.kt` came in at 738, under its raised `≤ 780`, because the collapse
state moved out to its own manager rather than growing the ViewModel.

---

## Phase-boundary audit

Run 2026-08-09 against this phase's changed files (`docs/CODE_AUDIT_PROTOCOL.md` Layers 1-3; Layer 4 not
applicable - no Room surface touched).

**Layer 1 - architecture.** Clean. The row arithmetic sits in `src/main` where a shared unit test can
reach it, the projection plumbing stays in `src/launcherEnabled`, and the Activity gained one lambda and
one collector - no logic (Rule 3). `LauncherSectionCollapseManager` follows `NounVerbManager` and keeps
the folded-state concern, its persistence and its toggle in one 88-line class instead of a ViewModel that
was already at detekt's function ceiling.

**Layer 2 - coroutines and shared state.** One P2, no P0/P1.

- **P2 - the first render after process start can draw a folded section expanded.** `collapsed` is a
  `stateIn(WhileSubscribed)` flow seeded with `emptySet()`, and the desktop's own collector renders first,
  so the cold-start sequence is: cells arrive -> render with `collapsed.value` still empty -> `collapsed`
  computes -> render again. Cost is one extra desktop rebuild and a possible one-frame flash, **at cold
  start only**: `stateIn` retains its value, so every later Home visit already holds the right set and the
  render guard absorbs the other three collectors. Not fixed here because the fix that actually removes it
  - emitting cells and folded state as one snapshot so they can never disagree - reshapes the desktop
  stream and belongs with the render-model work, not with a `[~]`-to-`[x]` step. Recorded for the device
  test, which exercises §11.9-§11.12 on that exact path.
- Checked and clean: the toggle's write-then-read is safe (`SharedPreferences.edit().apply()` updates the
  in-memory map synchronously, so the `revision` bump that follows re-reads the new value); the fourth
  collector costs nothing per Home visit because the render key now carries the folded set and the guard
  returns early; edit mode renders unfolded, so a drop coordinate still maps to the stored row it always
  mapped to.

**Layer 3 - ownership and leaks.** Clean. The manager takes the application context and the store passes
it through `applicationContext`, so no Activity is retained. Header click listeners live on views the
binder rebuilds each pass and die with them.

---

## Handoff Notes to Next Phase

Collapse is a render-time concern only; storage never learns about it. The render key is now a named type carrying all five compared values, so any future input joins by adding a property rather than by changing an arity or growing a parallel field beside it.

---

## Rollback Plan

Revert the phase commit. Persisted collapsed-state remains in the shared preferences namespace and is simply not read by the reverted build - no stored desktop position was ever written by this phase.
