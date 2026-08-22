# Phase 02 - Seed merge

**Strategic spec:** [`../S1613_launcher-desktop-shortcuts-import.md`](../S1613_launcher-desktop-shortcuts-import.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-13
**Completed:** 2026-08-13

---

## Objective

Feed the imported shortcuts into the existing desktop seed as a second contribution laid out by the same packer, and record the reset invariant that keeps the import source alive.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 520 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt` | Modified | ≤ 120 |

> Backup / split thresholds: see Constraints. `LauncherStarterSets.kt` is above 500 LOC - take a timestamped backup into `temp/S1613/` before editing it.
>
> No `ui/**` file and no resource file is touched in this phase, per strategic §3.2 "Интерфейс" and §11.6.

---

## Steps

### Step 02.1 - Accept imported items in the starter table

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Back the file up into `temp/S1613/` first - it is over 500 LOC. Add a parameter `importedShortcuts: List<StarterItem> = emptyList()` to `itemsFor`. Immediately after the `commonThirdPartyApps(installedPackages)` line and before the `section(LauncherCellCommand.SECTION_APP_FUNCTIONS)` line, append the imported items that the list built so far does not already carry, comparing on `target`. Keep the table pure data: it must not learn what a pinned shortcut is, only that it received more `StarterItem` values. Extend the `itemsFor` KDoc with one `S1613:` sentence stating that imported items sit at the tail of the content section, never inside the app-functions section, and that a target the starter set already placed is dropped.

**Why:**

Section membership is positional (S1428), so appending at the very end of the list would file every restored shortcut under the app-functions header, where it does not belong; strategic §3.1 also fixes the order as "starter set first, imported after it". The duplicate filter lives here rather than in the caller because this is the only point where both lists exist, which is what lets strategic §7's duplicate mitigation cost one pass instead of a second `itemsFor` call. The default value keeps the existing callers and both parity tests compiling unchanged.

**Verification:**

- `Glob` - a backup of `LauncherStarterSets.kt` exists under `temp/S1613/`.
- `Grep` - `importedShortcuts: List<StarterItem> = emptyList()` matches exactly once.
- `Grep` - a line referencing `importedShortcuts` and `target` appears between the `commonThirdPartyApps` line and the `SECTION_APP_FUNCTIONS` line.
- `Grep` - `S1613:` present in the `itemsFor` KDoc.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - LauncherStarterSets.itemsFor gained importedShortcuts (defaulted, deduped on target, inserted before the app-functions section); SeedLauncherDesktopUseCase queries allPinned behind the seeded early exit and maps to pin: targets; ResetLauncherToDefaultsUseCase KDoc records that the platform pin record must never be released. 13/13 predicates PASS. Note: file measured 401 LOC, not >500 as the plan claimed - backup taken anyway.

---

### Step 02.2 - Query and merge the import in the seed use case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inject `AppShortcutDataSource` into the use case. After the `installedPackages` line and therefore behind the already-seeded early exit, call `allPinned()` and map each result to `LauncherStarterSets.StarterItem(kind = LauncherCellKind.SHORTCUT, target = LauncherCellCommand.PinnedShortcut(packageName = it.packageName, shortcutId = it.id, label = it.label).encode())`. Pass that list to `itemsFor` as `importedShortcuts`; the duplicate filter is the table's job, not this one's.

**Why:**

Strategic §5.1.2 places the merge here because this use case is already the one place that resolves ids, availability and the own-app token before handing pure data to the packer, and §3.2 requires the platform query to sit behind the seeded early exit so a desktop that will not be seeded never pays for it.

**Verification:**

- `Grep` - `AppShortcutDataSource` present in the constructor parameter list.
- `Grep` - `allPinned()` called exactly once in the file.
- `Grep` - `PinnedShortcut(` present in the file.
- `Grep` - `importedShortcuts` passed to `LauncherStarterSets.itemsFor`.
- `Grep` - the `allPinned()` call sits after the `if (state.seededPortrait && state.seededLandscape) return@runCatching` line.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - LauncherStarterSets.itemsFor gained importedShortcuts (defaulted, deduped on target, inserted before the app-functions section); SeedLauncherDesktopUseCase queries allPinned behind the seeded early exit and maps to pin: targets; ResetLauncherToDefaultsUseCase KDoc records that the platform pin record must never be released. 13/13 predicates PASS. Note: file measured 401 LOC, not >500 as the plan claimed - backup taken anyway.

---

### Step 02.3 - Record the reset invariant

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResetLauncherToDefaultsUseCase.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Extend the "Deliberately outside the inventory" paragraph of the class KDoc with the platform's record of shortcuts pinned to this launcher, marked `S1613:`. State that the reset must never release those pins because that record is what the desktop seed reads back, and that releasing them would delete the restore silently rather than fail visibly.

**Why:**

Strategic §5.1.3 makes this an invariant rather than a comment: the class KDoc is by its own wording the single inventory of launcher-owned state, and "release the pins we no longer show" is a plausible-looking addition that would destroy the whole feature without breaking a build or a test.

**Verification:**

- `Grep` - `S1613:` present in `ResetLauncherToDefaultsUseCase.kt`.
- `Grep` - the new text sits inside the class KDoc block, above the `class ResetLauncherToDefaultsUseCase` declaration line.
- `Grep` - `pinShortcuts` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - LauncherStarterSets.itemsFor gained importedShortcuts (defaulted, deduped on target, inserted before the app-functions section); SeedLauncherDesktopUseCase queries allPinned behind the seeded early exit and maps to pin: targets; ResetLauncherToDefaultsUseCase KDoc records that the platform pin record must never be released. 13/13 predicates PASS. Note: file measured 401 LOC, not >500 as the plan claimed - backup taken anyway.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

`itemsFor` now takes a defaulted `importedShortcuts` list whose items land at the tail of the content section. Everything the packer sees is still a `StarterItem`, so the existing overlap guarantee covers imported cells for free - Phase 03 asserts exactly that.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed. The desktop falls back to the profile starter set alone.
