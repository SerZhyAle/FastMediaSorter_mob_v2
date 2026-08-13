# Phase 01 - Dependency bundles

**Strategic spec:** [`../S1314_launcherhomeviewmodel-detekt-debt.md`](../S1314_launcherhomeviewmodel-detekt-debt.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Group ten of `LauncherHomeViewModel`'s fourteen injected dependencies into three Hilt-constructible holder
classes, bringing the constructor to seven parameters and clearing the `LongParameterList` finding without a
`@Suppress` and without touching the detekt baseline.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - none.
- [x] INDEX Pre-Implementation Blocker cleared: strategic §3.3 records the owner's ratification of the holder pattern.
- [x] Working tree is clean or on a feature branch.
- [x] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "/spec-dev S1314 phase 01"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeDependencies.kt` | New | ≤ 60 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 470 |

> `LauncherHomeViewModel.kt` measures 450 lines before this phase - below the 500-line backup threshold, so no
> backup step is required. Re-check before editing if the working tree has moved.
>
> **Flavor placement.** `src/launcherEnabled/` is mounted by `standard` and `noLegal` only; `lite`, `photos`,
> `legacy` and `vr` mount `src/launcherDisabled/` instead. The new file MUST sit beside the ViewModel in
> `src/launcherEnabled/java/`, never in `src/main/java/` - all ten held types are declared in `src/main` but are
> consumed here alone, and a `src/main` placement would ship four dead classes into the launcher-less flavors.
>
> No layout XML is touched, so the landscape-parity rule does not apply to this phase.

---

## Cluster map (measured, do not re-derive)

| Holder | Held dependency | Call sites inside the ViewModel |
|--------|-----------------|---------------------------------|
| `LauncherDesktopDependencies` | `resolveDesktop` | 94 |
| | `desktopRepository` | 238, 272, 279, 286, 292, 402 |
| | `seedLauncherDesktop` | 423 |
| | `resourceRepository` | 263, 265 |
| `LauncherTaskbarDependencies` | `queryRecentCommands` | 111 |
| | `pinsRepository` | 125, 303, 306, 312 |
| | `resolveVisual` | 128 |
| `LauncherShortcutDependencies` | `queryAppShortcuts` | 428 |
| | `startAppShortcut` | 432 |
| | `pickContactShortcut` | 435, 441 |

Stay as direct constructor parameters - each is reached from more than one cluster or from none of them:
`executeCommand` (373, the shared dispatch entry), `settingsRepository` (97, 101, 157, 167, 191, 201, 319, 418),
`observeStreams` (392), `executeScheduledOperation` (343).

---

## Steps

### Step 01.1 - Declare the three dependency holders

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeDependencies.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `LauncherHomeDependencies.kt` in the `com.sza.fastmediasorter.ui.launcher` package under
> `src/launcherEnabled/java/`, holding three classes: `LauncherDesktopDependencies` with `resolveDesktop`,
> `desktopRepository`, `seedLauncherDesktop`, `resourceRepository`; `LauncherTaskbarDependencies` with
> `queryRecentCommands`, `pinsRepository`, `resolveVisual`; `LauncherShortcutDependencies` with
> `queryAppShortcuts`, `startAppShortcut`, `pickContactShortcut`. Each is a plain `class` with an
> `@Inject constructor` and `val` members - no `@Module`, no `@Provides`, and deliberately not a `data class`
> (detekt's `LongParameterList.ignoreDataClasses` default of `true` would stop the gate ever seeing a holder
> that grows past ten). Mirror the file shape of `ui/player/VideoPlayerDependencies.kt`: several holders in one
> file named after the consumer. Add one KDoc line per holder saying which launcher surface it serves; do not
> restate the member names in prose. Keep imports lexicographically ordered (`ImportOrdering` is active).

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeDependencies.kt` exists.
- `Glob` - no file named `LauncherHomeDependencies.kt` exists anywhere under `app_v2/src/main/`.
- `Grep` - `class LauncherDesktopDependencies @Inject constructor(` matches exactly once.
- `Grep` - `class LauncherTaskbarDependencies @Inject constructor(` matches exactly once.
- `Grep` - `class LauncherShortcutDependencies @Inject constructor(` matches exactly once.
- `Grep` - `data class ` matches zero times in that file.
- `Grep` - `@Module`, `@Provides` and `@Binds` each match zero times in that file.
- Value equality - the file's longest line is `<=` 120 characters.

**Status:** `[x]` done

---

### Step 01.2 - Route the desktop cluster through its holder

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `LauncherHomeViewModel`, replace the four constructor parameters `resolveDesktop`, `desktopRepository`,
> `resourceRepository` and `seedLauncherDesktop` with a single `private val desktopDependencies: LauncherDesktopDependencies`,
> placed first. Prefix the ten call sites listed in the cluster map with `desktopDependencies.`. Delete the four
> import lines that become unused - `ResolveLauncherDesktopUseCase`, `LauncherDesktopRepository`,
> `ResourceRepository`, `SeedLauncherDesktopUseCase` - because `NoUnusedImports` is active and already cost this
> file a finding in S1087. Behaviour, KDoc and the surrounding comments stay exactly as they are.

**Verification:**

- `Grep` - `desktopDependencies: LauncherDesktopDependencies` matches exactly once.
- `Grep` - `desktopDependencies.` matches exactly 10 times.
- `Grep` - each of `import com.sza.fastmediasorter.domain.usecase.launcher.ResolveLauncherDesktopUseCase`, `import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository`, `import com.sza.fastmediasorter.domain.repository.ResourceRepository`, `import com.sza.fastmediasorter.domain.usecase.launcher.SeedLauncherDesktopUseCase` matches zero times.
- Value equality - the constructor parameter list holds 11 entries.

**Status:** `[x]` done

---

### Step 01.3 - Route the taskbar cluster through its holder

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Replace the constructor parameters `queryRecentCommands`, `pinsRepository` and `resolveVisual` with a single
> `private val taskbarDependencies: LauncherTaskbarDependencies`, placed after `desktopDependencies`. Note that
> `queryRecentCommands` is currently the one parameter without `private val` - it is read only from the
> `recentIcons` property initialiser - so it moves the same way as the other two. Prefix the six call sites from
> the cluster map with `taskbarDependencies.` and delete the three import lines that become unused:
> `QueryRecentLauncherCommandsUseCase`, `LauncherPinsRepository`, `ResolveLauncherCommandLabelUseCase`.

**Verification:**

- `Grep` - `taskbarDependencies: LauncherTaskbarDependencies` matches exactly once.
- `Grep` - `taskbarDependencies.` matches exactly 6 times.
- `Grep` - each of `import com.sza.fastmediasorter.domain.usecase.launcher.QueryRecentLauncherCommandsUseCase`, `import com.sza.fastmediasorter.domain.repository.LauncherPinsRepository`, `import com.sza.fastmediasorter.domain.usecase.launcher.ResolveLauncherCommandLabelUseCase` matches zero times.
- Value equality - the constructor parameter list holds 9 entries.

**Status:** `[x]` done

---

### Step 01.4 - Route the shortcut cluster and land the seven-parameter constructor

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Replace the constructor parameters `queryAppShortcuts`, `startAppShortcut` and `pickContactShortcut` with a
> single `private val shortcutDependencies: LauncherShortcutDependencies`, placed after `taskbarDependencies`,
> and prefix the four call sites from the cluster map. Two of them overflow detekt's 120-character ceiling once
> prefixed and must be wrapped onto a continuation line: `appShortcutsOf` (currently 103 chars, would become
> 124) and `contactPickIntent` (currently 105, would become 126). Delete the now-unused imports
> `QueryAppShortcutsUseCase` and `StartAppShortcutUseCase`, but **keep** the `PickContactShortcutUseCase`
> import - `resolveContactPick` still names `PickContactShortcutUseCase.Outcome` as its return type. Solve the
> finding by restructuring only: do not add `@Suppress` anywhere in the file, and do not edit
> `config/detekt/baseline-app_v2.xml`. Leave the `Timber.d("S1103: ..")` and `Timber.d("S1170: ..")` probe lines
> untouched - both tickets are still `BlockNeedUserTest`, so CLAUDE.md Rule 2 requires the tags to stay.

**Verification:**

- `Grep` - `shortcutDependencies: LauncherShortcutDependencies` matches exactly once.
- `Grep` - `shortcutDependencies.` matches exactly 4 times.
- `Grep` - `import com.sza.fastmediasorter.domain.usecase.launcher.QueryAppShortcutsUseCase` and `import com.sza.fastmediasorter.domain.usecase.launcher.StartAppShortcutUseCase` each match zero times.
- `Grep` - `import com.sza.fastmediasorter.domain.usecase.launcher.PickContactShortcutUseCase` matches exactly once.
- Value equality - the constructor parameter list holds **7** entries: `desktopDependencies`, `taskbarDependencies`, `shortcutDependencies`, `executeCommand`, `settingsRepository`, `observeStreams`, `executeScheduledOperation`.
- `Grep` - `@Suppress` matches zero times in `LauncherHomeViewModel.kt`.
- `Grep` - `LauncherHomeViewModel` matches zero times in `config/detekt/baseline-app_v2.xml`.
- `Grep` - `Timber.d("S1103:` matches exactly once and `Timber.d("S1170:` matches exactly once.
- `Grep` - `Log\.d\(` matches zero times in both touched files.
- Value equality - the longest line in `LauncherHomeViewModel.kt` is `<=` 120 characters.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly). **`.\a.ps1 fk` alone is not sufficient
      evidence here:** it stops at `compileStandardDebugKotlin`, while Dagger validates the graph in
      `hiltJavaCompile`. This phase adds three `@Inject constructor` types and rewrites a `@HiltViewModel`
      constructor, so the proof must be a target that runs kapt and Hilt to completion - `.\a.ps1 fc`,
      `.\a.ps1 d`, or a unit-test target.
- [x] `noLegal` also mounts `src/launcherEnabled/`, so run `.\a.ps1 fkn` as well - a green `standard` build does
      not cover the second consuming flavor.
- [x] Diff-scoped detekt gate PASS over **both** touched files, not just one:
      `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeDependencies.kt,app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`.
      `-ChangedFiles` is `[string[]]`, so pass it as one comma-separated argument under `-File` invocation.
      `post-change.ps1 -ScopeToFile` diff-scopes detekt to its single `-File` only, which is why this call is
      separate. Read the verdict line: the script exits 0 without `-Gate`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`). Focus it on the one real risk: a holder that silently fails to construct would surface as a Hilt error at `LauncherHomeActivity` creation, not at compile time.

---

## Handoff Notes to Next Phase

The launcher home surface now has a named place for new injected dependencies. Anything added later joins the
holder matching its surface rather than the constructor, which keeps three of the ten slots free instead of
spending them. `LauncherHomeViewModel`'s public API is unchanged, so the five consumers listed in INDEX were not
edited and need no regression review beyond a launch of the home surface.

---

## Rollback Plan

Revert the phase commit - no data migration, no schema change, no user-facing surface, and the ViewModel's
public API is identical before and after.
