# Phase 05 - Seeding and picker

**Strategic spec:** [`../S1428_launcher-shortcut-groups.md`](../S1428_launcher-shortcut-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 04
**Blocks:** none
**Steps done:** 5 / 5

> **Unblocked 2026-08-08 and reshaped by the answer.** Strategic §6.12 resolved as option (в), so this
> phase is where it lands: the seeded order in `itemsFor` now carries two headers, and the second one is
> what bounds the first. Step 05.1 is new - the second section needs a key and a name in three locales
> before anything can seed it.
**Started:** 2026-08-08
**Completed:** 2026-08-09

---

## Objective

Seed the preset section at the top of a fresh desktop with the four launcher actions moved under it, and let a removed section be put back through the existing content picker.

---

## Anchors

- `LauncherStarterSets` - `.../core/launcher/LauncherStarterSets.kt` - `itemsFor:77`, `place:154` (sole overlap guarantor at seeding), `commonTail:186`.
- `LauncherActionCatalog.all` - `.../core/panel/LauncherActionCatalog.kt:30` - the four action keys.
- `LauncherCellContentPickerDialogFragment.categoryOptions` - `.../ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt:121` - 12 rows today.
- `seedIfEmpty` - `.../data/repository/LauncherDesktopRepositoryImpl.kt:190` - no overlap guard by design.

---

## Prerequisites

- [x] Phase 01 and Phase 04 are ✅ Done.

## Store a header at the maximum column count, not the current one

Established against the code 2026-08-08 while planning Phase 04. `LauncherCellDao.findOverlapping` is a
SQL predicate over the **stored** `spanW`; the renderer widens a header to the **live** column count in
`LauncherGridGeometry.renderSpanW`. A header stored with the span of a three-column grid therefore
leaves columns 3..N of its row free in the database while covering them on screen once the density
factor or a rotation widens the grid - a cell dragged there lands under the header. Storing the header
at the maximum supported column count reserves the whole row on every grid it can ever be rendered on,
and costs nothing: no real grid is wider, so the extra squares are unreachable.

Two call sites to get right, and both clamp today:

- `LauncherStarterSets.place` clamps `spanW` into `1..cols` for packing. The clamp must stay for the
  occupancy grid - `firstFreeAnchor` spins forever on an empty column range - so the seeded header must
  carry the full span into the entity rather than the packed one.
- `LauncherDesktopRepositoryImpl.addCellInFirstFreeSlot` clamps `spanW` to `columns` before inserting,
  which is the path a picker restore takes. A section restored through it would be stored narrow again.

`LauncherGridGeometry.MAX_COLUMNS` lives in `src/launcherEnabled` and cannot be imported from `src/main`,
which is the same constraint that made `LauncherStarterSets` duplicate the gadget keys. Mirror it the way
that duplication is already mirrored, and extend `LauncherStarterSetsParityTest` so the copy cannot
drift.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommand.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherSectionMembership.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherSectionCatalog.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt` | Modified | ≤ 400 |
| `app_v2/src/main/res/values*/strings.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | Modified | ≤ 430 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt` | Modified | ≤ 340 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 900 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt` | Modified | ≤ 400 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImplTest.kt` | Modified | ≤ 400 |
| `app_v2/src/testStandard/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsParityTest.kt` | Modified | ≤ 120 |

The four files the plan did not name were found by following the storage side of the header span, not by
scope creep: `LauncherSectionMembership` is where the mirrored constant belongs (it is already the single
home of section rules in `src/main`, so the copy exists once rather than twice), `SeedLauncherDesktopUseCase`
is what writes the packed span into the entity, `LauncherDesktopRepositoryImpl` is what the picker restore
goes through, and `LauncherSectionCatalog` is what makes a section's title data rather than a `when` branch
- required by strategic §5.3 the moment there was a second section to name.

---

## Steps

### Step 05.1 - Name the second preset section

**Files:** `.../domain/model/launcher/LauncherCellCommand.kt`, `.../domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`, `app_v2/src/main/res/values*/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a second preset section key beside `SECTION_APP_FUNCTIONS`, and resolve its title through the same label lookup the first one uses. Add the title string in EN, RU and UK with `scripts/utils/set-android-string.ps1 -Action add`, then audit with `scripts/check_strings_localized.ps1`. Name it for what it is rather than for what currently sits in it: membership is positional, so the user can drag anything under it and a descriptive title would become false.

**Why:**

Strategic §6.12 resolved the boundary of the first section as a second seeded header, and §3.2 "Локализация" now requires two section names in three locales rather than one.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_section"` exits 0.
- Unit: `ResolveLauncherCommandLabelUseCase` maps both section keys to a non-blank title.

**Status:** `[x]` done

---

### Step 05.2 - Seed both headers with the four actions between them

**Files:** `.../core/launcher/LauncherStarterSets.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Put the "app functions" header first in the starter set, move the four `LauncherAction` shortcuts out of `commonTail` to sit directly beneath it, and place the second header immediately after them so everything else in the set falls under the second one. The desktop still seeds four action cells rather than eight. Let `place` keep packing row-major - it stays the only thing preventing overlap at seeding, because `seedIfEmpty` deliberately skips the interactive intersection check.

**Why:**

Strategic §6.5 decided the four shortcuts move rather than duplicate, so the user is not asked to tell two identical pairs apart; §2.2 requires the preset section to stand above the rest of the desktop content; and §6.12 makes the second header the thing that ends the first section.

**Verification:**

- Unit: `LauncherStarterSetsTest` asserts the first placed item is the app-functions header, the next four are the action shortcuts, and the item after them is the second header, with no overlapping rectangles.
- Unit: the total count of action shortcuts in the seeded set is still four.
- Unit: no `GADGET` item is placed between the two headers.
- `.\a.ps1 fu --tests "*LauncherStarterSets*"` passes.

**Status:** `[x]` done

---

### Step 05.3 - Keep the seeded set overlap-free

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Extend the packer test so a full-width header is covered at several column counts, including the narrowest supported grid. A header spanning the whole row is the first starter item whose width equals the grid, so it is the case most likely to collide with what the packer places next.

**Why:**

Strategic §5.1.5 records the packer as the sole guarantor of non-overlap during seeding, because seeding inserts cells bypassing the interactive intersection check.

**Verification:**

- Unit: no two placed rectangles intersect at any tested column count.

**Status:** `[x]` done

---

### Step 05.4 - Add the thirteenth picker row

**Files:** `.../ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Add one category row returning a preset section, next to the existing twelve. Route its result through the same `RESULT_CATEGORY` dispatch the other rows use. Two preset sections exist now (§6.12) and the picker row must let the user say which - a single row that always returns the first one cannot restore a deleted second header.

**Why:**

Strategic §6.4 chose seeding plus a row in the existing picker so a removed section can be put back the same way S1402's action shortcuts already are, and §11.14 makes that round trip an acceptance criterion.

**Verification:**

- `Grep` - `categoryOptions` now yields 13 entries.
- Manual: strategic §11.14 - remove each section in edit mode and restore it through the picker.

**Status:** `[x]` done

---

### Step 05.5 - Place the restored section from the picker result

**Files:** `.../ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> Handle the new category in the host's picker-result path so it inserts a section cell at the chosen empty slot through the ordinary placement route, with its interactive overlap check intact.

**Why:**

Strategic §6.4's consequence is that the header must be removable and restorable, with removal already living in edit mode via `onRemoveClick`, so only the restore path is new.

**Verification:**

- Manual: the restored header spans the full row and behaves as the seeded one - collapse, accessibility and gadget refusal all still hold.

**Status:** `[x]` done

---

## What the code said back (2026-08-08)

- **The full span had to be forced at the storage layer, not only at seeding.** The plan named two call
  sites that clamp; the honest fix is one rule in `LauncherDesktopRepositoryImpl.normalized()`, which is
  the function whose whole job is "force a cell into the shape the overlap invariant assumes". A header is
  now pinned to column 0 at `HEADER_STORED_SPAN_W` on every path that writes one, and
  `addCellInFirstFreeSlot` is exempted from its own width clamp rather than re-deriving the rule.
- **The free-slot scan had to gain a separate scan width.** With the stored span wider than the grid,
  `for (col in 0..columns - candidate.spanW)` is an empty range, so a header restored through the picker
  would have been silently dropped - the exact failure step 05.5 exists to prevent. The scan now uses
  `spanW.coerceAtMost(columns)`, which is a no-op for every other kind.
- **`moveCell` had to be included.** It writes a column without going through `normalized()`, so a dragged
  header would have been stored off column 0 while still drawn across the whole row.
- **The straddle rule of Phase 04 is now defence in depth, not the first line.** Once a header is stored at
  the maximum column count, its stored rectangle covers every square it is drawn on, so plain rectangle
  intersection already refuses a gadget over a header row. The straddle rule is what still refuses one if
  `MAX_COLUMNS` is ever raised above the value already written to existing rows - which is why
  `LauncherStarterSetsParityTest` now asserts the two constants are equal.

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `check_strings_localized.ps1 -KeyPrefix "launcher_section"` exits 0 - both keys present in en/ru/uk.
- [x] Project compiles - `.\a.ps1 dq` exit 0, APK `v2.60.8082.309-DEBUG`.
- [x] `.\a.ps1 fu --tests "*Launcher*"` passes - 13 classes, 120 tests, 0 failures, `LauncherStarterSetsParityTest` 2/0 among them; read from the JUnit XML.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. See `## Phase-boundary audit` below.

**Closure note.** The five steps were implemented 2026-08-08 but the phase was never closed: the INDEX
still read `⬜ Not started 0/5`, no dev-log row existed and none of the criteria above had been run. This
session verified the phase end to end against the working tree rather than re-implementing it, then closed
it. `post-change.ps1 -ScopeToFile` over the twelve changed files: every gate PASS except `assert-detekt`,
which reports the one finding phase 03 also hit - `LargeClass:LauncherHomeActivity`, parked as **S1541**
with evidence it predates this ticket. One advisory: `LauncherStarterSets.kt:14` is a 140-char KDoc line
carried over from S0404 - lexical only, and the detekt gate itself does not flag it.

---

## Phase-boundary audit

Run 2026-08-09 against this phase's changed files (`docs/CODE_AUDIT_PROTOCOL.md` Layers 1-4). No P0/P1.

**Layer 1 - architecture.** Clean. A section's title is data in `LauncherSectionCatalog` rather than a
`when` branch, which is what strategic §5.3 asks for the moment a second section exists. The picker row
routes through the same `RESULT_CATEGORY` dispatch as the other twelve, so no second way to place a cell
was introduced.

**Layer 4 - Room and the storage invariant.** Clean, and the strongest part of the phase. The full-width
rule lives in one place - `normalized()` - rather than at each call site, so every write path stores a
header at column 0 and `HEADER_STORED_SPAN_W`; `addCellInFirstFreeSlot` exempts a header from its own
width clamp and scans with `spanW.coerceAtMost(columns)`, without which the restore path's anchor loop is
an empty range and a restored header vanishes silently; `moveCell` re-pins column 0 because it writes
outside `normalized()`. Every query runs on `Dispatchers.IO` inside a transaction, and `headerRowsFor`
skips the round trip entirely for a shortcut or a one-row cell. No schema change: `kind` is a string
column, so `SECTION` needed no migration.

**Interaction with phase 03, checked explicitly.** A header stored at span 12 and drawn at the live column
count means `findOverlapping` already covers every square the header occupies on screen, so the empty-slot
sweep cannot draw a "tap to add" square on a header row, and the phase 04 straddle rule is now defence in
depth rather than the first line - as this phase's own notes say.

---

## Handoff Notes to Next Phase

A fresh desktop seeds the section; an existing one deliberately does not, per §6.6. Testing therefore requires a fresh or reset desktop - carry that into the device-test note.

---

## Rollback Plan

Revert the phase commit. Already-seeded desktops keep whatever they were seeded with; seeding is one-shot and is not re-run on an existing desktop.
