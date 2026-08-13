# Phase 01 - Section cell domain

**Strategic spec:** [`../S1428_launcher-shortcut-groups.md`](../S1428_launcher-shortcut-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Teach the domain that a desktop cell can be a section header: one new `LauncherCellKind`, one new `LauncherCellCommand` variant with its own prefix, and a resolved label - with no schema migration.

---

## Anchors

- `LauncherCellKind` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCell.kt:9` - today exactly `SHORTCUT`, `GADGET`.
- `LauncherCellCommand` - `.../launcher/LauncherCellCommand.kt:29`, `decode` at `:146`, `PREFIX_*` constants at `:115`.
- `ResolveLauncherCommandLabelUseCase` - `.../domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt:116` - second exhaustive `when`.
- `LauncherCellEntity` - `.../data/local/db/LauncherCellEntity.kt:18` - `kind` and `target` are strings by design.

---

## Prerequisites

- [x] Strategic §6 items all Resolved (they are).
- [x] `LauncherCellCommand.kt` and `LauncherCellViewBinder.kt` are both over 500 LOC - back each up under `temp/S1428/` before editing (CLAUDE.md Rule 5). Backed up both anyway; the premise is wrong - measured 259 and 381 LOC, so Rule 5's 500-LOC threshold was never crossed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCell.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommand.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt` | Modified | ≤ 200 |
| `app_v2/src/main/res/values/strings.xml` + `values-ru` + `values-uk` | Modified | n/a |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommandTest.kt` | Modified | ≤ 400 |

---

## Steps

### Step 01.1 - Add the `SECTION` cell kind

**Files:** `.../domain/model/launcher/LauncherCell.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `SECTION` to `LauncherCellKind` alongside `SHORTCUT` and `GADGET`. Adding it breaks two exhaustive `when` expressions on purpose - `LauncherCellViewBinder.bind` and `ResolveLauncherCommandLabelUseCase` - and those breaks are the compile-time proof that every consumer was visited. Do not add an `else` branch to either to silence them.

**Why:**

Strategic ADR-1 puts the section in the existing cell storage using the same kind and target fields, because a second entity beside the desktop would need its own synchronisation at rotation, edit, reset and seeding - four places where two models drift apart silently.

**Verification:**

- `Grep` - `SECTION` appears in the `LauncherCellKind` declaration exactly once.
- `.\a.ps1 fk` fails with exactly the two expected non-exhaustive-`when` errors before step 01.2 and 01.4 land, and passes after.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 1\2 PASS (`SECTION` declared once in `LauncherCellKind`). Files: `domain/model/launcher/LauncherCell.kt` (+7 LOC). The compile half of the predicate is evaluated at step 01.4, where the same predicate reads "passes after" - the plan wrote one predicate spanning 01.1..01.4. Plan inaccuracy noted: only **one** exhaustive `when` breaks on `LauncherCellKind` (`LauncherCellViewBinder.bind`); `ResolveLauncherCommandLabelUseCase` switches on `LauncherCellCommand`, so its break arrives with step 01.2, not 01.1.

---

### Step 01.2 - Add the `Section` command and its prefix

**Files:** `.../domain/model/launcher/LauncherCellCommand.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a `Section` variant to the `LauncherCellCommand` sealed interface carrying the section's title key, with a `PREFIX_SECTION` constant next to the existing ten prefixes, plus encode and `decode` support. Keep `decode` tolerant exactly as it is today: an unknown prefix, an empty payload or an unparseable key must still return `null` rather than throw.

**Why:**

Strategic §5.3 requires the section to behave like every other command under a build that does not know it - a cell written by a newer build renders as unavailable on an older one instead of bringing the desktop down.

**Verification:**

- `Grep` - `PREFIX_SECTION` declared once in the companion object.
- `Grep` - a `Section` branch exists inside `decode`.
- `LauncherCellCommandTest` gains a round-trip case and a malformed-payload case, and `.\a.ps1 fu --tests "*LauncherCellCommandTest*"` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. `PREFIX_SECTION = "sec:"` declared once; `decode` gained the tolerant `Section` branch (empty payload -> null). `check-standard-fast.ps1 -Mode Unit -Tests "*LauncherCellCommandTest*"` exit 0; result XML `TEST-..LauncherCellCommandTest.xml` (13:39:20) reads tests=8 failures=0 and names both new cases (`section survives a round trip`, `empty section payload decodes to null`). Files: `LauncherCellCommand.kt` (+14 LOC), `LauncherCellCommandTest.kt` (+13 LOC).
- Constant added beyond the prompt's named symbols: `SECTION_APP_FUNCTIONS = "app_functions"` in the same companion. The plan names no home for the preset key, and steps 01.4/05.x both need it; it sits next to the prefixes rather than in a new catalog so no class name was invented.

---

### Step 01.3 - Add the section title string in three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add exactly one key for the preset section's title - «Функции приложения» - through `scripts/utils/set-android-string.ps1 -Action add -En .. -Ru .. -Uk ..`, which enforces parity across the three strict locales in one call. Add no empty-state string.

**Why:**

Strategic §3.2 "Локализация" fixes the count at exactly one new string and §6.10 closed the empty section as a bare header, so no empty-state text is needed.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<the new key>"` exits 0.
- `Grep` - the key exists in all three `strings.xml` files.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. Key `launcher_section_app_functions` added through `set-android-string.ps1 -Action add` (EN "App functions" / RU «Функции приложения» / UK «Функції застосунку»); `check_strings_localized.ps1 -KeyPrefix "launcher_section_app_functions"` exit 0, "all 1 key(s) present in en/ru/uk". No empty-state string added, per §6.10.

---

### Step 01.4 - Resolve the section's label

**Files:** `.../domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add the `Section` branch to the exhaustive `when` so the header resolves to its title string off the main thread, the same way every other command resolves its label. An unresolvable section key degrades to the same "unavailable" label the other commands use, not to a blank header.

**Why:**

Strategic §5.2 routes the header through the same path as any other cell - label and icon resolved off the main thread, an unresolvable target degrading honestly - because the header is an ordinary cell rather than a separate render branch.

**Verification:**

- `Grep` - a `Section` branch exists in the `when` at the use case.
- `.\a.ps1 fk` passes - both exhaustive `when` expressions are satisfied again.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. `sectionVisual` resolves through `sectionLabelRes`, returning null for an unknown key exactly like `launcherActionVisual`, which the binder already renders as `launcher_home_cell_unavailable`. `.\a.ps1 fk` exit 0. Files: `ResolveLauncherCommandLabelUseCase.kt` (+18 LOC).
- **Plan correction - a third exhaustive `when`.** The plan names two (`LauncherCellViewBinder.bind`, this resolver). The first `fk` failed on a third the plan never surveyed: `ExecuteLauncherCommandUseCase.launch:43`, which switches over `LauncherCellCommand`. Added `is LauncherCellCommand.Section -> false` beside the existing `ScheduledOp` and `LauncherAction` refusals - a header is a caption, never an Intent target, and returning false also keeps it out of the launch journal. File not in this phase's `Files Touched`; recorded rather than deferred, because the phase cannot compile without it.

---

### Step 01.5 - Name the section title as data, not a constant in code

**Files:** `.../domain/model/launcher/LauncherCellCommand.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Ensure the section's title travels in the command payload rather than being hard-coded at the render site, so a second section would be a second instance of the same kind and not a code change.

**Why:**

Strategic §5.3 requires that the user-defined groups deferred by §6.2 become a second instance of this same cell kind rather than a rewrite, which holds only if the section name is data.

**Verification:**

- `Grep` - the render path contains no literal reference to the preset section's string key; the key reaches it through the decoded command.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 1\1 PASS. `LauncherCellViewBinder` contains zero occurrences of `SECTION_APP_FUNCTIONS` or `launcher_section_app_functions`; `bindSection` reads only `item.visual?.label`, which the resolver produced from the decoded command's `sectionKey`. Satisfied by step 01.2's design (payload carries the key), so no further edit was required.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0 (the run also merged resources and generated the new binding class, so the pulled-forward layout is proven to parse).
- [x] `.\a.ps1 fu --tests "*Launcher*"` passes - no schema test touched, since no schema changed.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

### Steps pulled forward from Phase 02

Phase 01's own Handoff Notes require a `SECTION` branch in `LauncherCellViewBinder` "that must exist to
compile", but the plan assigned that branch to step 02.3 and the header layout to 02.1 - so as written
this phase could neither compile nor run a unit test. Both were executed here instead of stubbing and
rewriting: `item_launcher_section_header.xml` (02.1) and the `bindSection` branch (02.3). Phase 02
re-verifies them against its own predicates and continues at 02.2.

One design fact found while doing it, which changed the layout: `decorateForEdit` returns early on any
root that is not a `FrameLayout`, so a `LinearLayout` root would have silently cost the header its
edit-mode remove badge - the very affordance strategic §6.4 and §11.14 rely on for removing the section.
The root is a `FrameLayout` for that reason.

---

## Phase-boundary audit - 2026-08-08

Layers 1-3 of `docs/CODE_AUDIT_PROTOCOL.md`. Layer 4 (Room) not applicable - no schema, DAO or query
changed, which is the phase's whole premise.

- **Layer 1 (architecture)** - clean. The domain model gained no Android dependency; the two use cases
  gained one branch and two private functions; the binder's new path holds no business logic. The
  `decode` split was forced by the cyclomatic ceiling (21 > 20) rather than chosen, and the unit tests
  re-ran green after it, so it is behaviour-preserving.
- **Layer 2 (lifecycle / coroutines)** - clean. `sectionVisual` runs inside the resolver's existing
  `withContext(Dispatchers.IO)` and only reads a string resource. Data path confirmed end to end:
  `ResolveLauncherDesktopUseCase.toUi` decodes every non-`GADGET` target, so a `SECTION` cell resolves
  its visual through the same route as any shortcut and needs no branch of its own there.
- **Layer 3 (listener ownership)** - one P2, deferred by design. `item_launcher_section_header.xml`
  declares `android:clickable="true"` while `bindSection` attaches no click listener, so right now the
  header advertises a tap that does nothing - the same shape strategic §7 flags for long press. Phase 03
  attaches the collapse listener, which resolves it. **If Phase 03 changes course, this must be fixed
  there instead of shipped:** drop `clickable`/`focusable` from the root.
- No P0 or P1 findings.

---

## Handoff Notes to Next Phase

A section is now expressible and storable, and nothing renders it yet: `LauncherCellViewBinder` has a `SECTION` branch that must exist to compile but need not yet draw the final header. No schema changed - `kind` and `target` absorbed the whole phase.

---

## Rollback Plan

Revert the phase commit. No migration ran and no user-visible surface changed, so a stored section cell is the only artefact; it decodes to `null` on the reverted build and renders as unavailable by the existing tolerant path.
