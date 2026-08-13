# Phase 04 - Unified app action menu

**Strategic spec:** [`../S1401_launcher-all-apps-screen.md`](../S1401_launcher-all-apps-screen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Grow the existing long-press popup from "the app's published shortcuts" into one menu that also carries Launch, To desktop, Pin to taskbar, App info and Uninstall, and route both current hosts through it.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1401 phase 04"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/apps/BuildAppSystemActionIntentUseCase.kt` | New | ≤ 120 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherAppActionMenuManager.kt` | New | ≤ 260 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherAppShortcutAdapter.kt` | Modified | ≤ 110 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeDependencies.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt` | Modified | ≤ 105 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | Modified | ≤ 340 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 500 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 660 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt` | Modified | ≤ 215 |
| `app_v2/src/main/res/values/strings.xml`, `values-ru`, `values-uk` | Modified | - |

> `LauncherHomeActivity.kt` is 627 LOC - back it up to `temp/S1401/` before editing (CLAUDE.md Rule 5), covered by Step 04.4.
>
> Every UI file here lives under `src/launcherEnabled/` because the menu only exists in launcher mode; only the intent builder is flavor-neutral and therefore sits in `src/main` (strategic §3.2).

---

## Steps

### Step 04.1 - Add the system-action intent builder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/apps/BuildAppSystemActionIntentUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `BuildAppSystemActionIntentUseCase` returning the app-details intent and the uninstall intent for a package, each as a nullable value that is null when no installed activity can handle it. Resolve handlers through `resolveActivityCompat` from `util/PackageManagerCompat.kt`. Expose `canUninstall(packageName: String): Boolean` returning false for a system app, so the caller can drop the entry instead of showing one that cannot work.

**Why:**

Strategic §7 records that app-details and uninstall screens differ between manufacturer builds and that a menu entry doing nothing is the failure to avoid; resolving the handler before offering the entry is that mitigation. Strategic §5.1 also requires impossible entries to be absent rather than greyed out, which needs the availability answer before the menu is built.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/apps/BuildAppSystemActionIntentUseCase.kt` exists.
- `Grep` - `class BuildAppSystemActionIntentUseCase` matches exactly once.
- `Grep` - `resolveActivityCompat` present.
- `Grep -n "getPackageInfo\(|queryIntentActivities\(|resolveActivity\("` returns zero raw-int overload hits - CLAUDE.md Rule 21.

**Status:** `[x]` done

---

### Step 04.2 - Add the action strings in all three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add these keys with one `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En -Ru -Uk` call each: `launcher_app_action_launch`, `launcher_app_action_to_desktop`, `launcher_app_action_pin_taskbar`, `launcher_app_action_app_info`, `launcher_app_action_uninstall`, `launcher_app_action_desktop_full`, `launcher_app_action_pinned`. Check each against `docs/COMMUNICATION_POLICY.md` §2 and §6. `launcher_app_action_desktop_full` is the message shown when the desktop has no free square left; it says what to do next, not only that the action failed.

**Why:**

Strategic §3.2 makes EN/RU/UK parity mandatory and binds every user-visible message to the communication policy. The desktop-full case needs its own message because placing an app on a full desktop is a foreseeable outcome of a menu entry the owner explicitly asked for in §3.3.

**Verification:**

- `Grep` - each of the seven keys present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_app_action"` exits 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x]` done

---

### Step 04.3 - Build the unified menu manager

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherAppActionMenuManager.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherAppShortcutAdapter.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Create `LauncherAppActionMenuManager` alongside the existing `LauncherAppShortcutMenuManager`, taking a coroutine scope and the operations it needs as function parameters - the same seam shape the existing manager uses so the host keeps routing through its ViewModel. It composes one list: Launch, To desktop, Pin to taskbar, App info, Uninstall, then the app's published shortcuts. Entries whose action is unavailable are omitted, never shown disabled. Widen `LauncherAppShortcutAdapter` to render both an action row and a shortcut row, keeping icon plus label for each. The popup stays modal so D-pad, keyboard and mouse focus enter the list. Dismiss on the host's teardown edge exactly as the existing manager does.

**Why:**

Strategic §5.1 requires one menu shared by the desktop and the new screen rather than two similar ones, and §3.2 requires the surface to stay reachable by keyboard and D-pad. Reusing the existing manager's function-parameter seam is what keeps the Activity from injecting domain types itself, which CLAUDE.md Rule 3 forbids.

**Verification:**

- `Glob` - `LauncherAppActionMenuManager.kt` exists under `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/`.
- `Grep` - `class LauncherAppActionMenuManager` matches exactly once.
- `Grep` - `isModal` present.
- `Grep` - `fun dismiss` present.
- `Grep -n "isEnabled = false"` returns zero hits - unavailable entries are omitted, not disabled.

**Status:** `[x]` done

---

### Step 04.4 - Expose the actions from the ViewModel and switch the desktop host

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeDependencies.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Back up `LauncherHomeActivity.kt` to `temp/S1401/` with a timestamp first. Add `BuildAppSystemActionIntentUseCase` to `LauncherShortcutDependencies`. On `LauncherHomeViewModel` add: `placeAppOnDesktop(packageName)`, `pinAppToTaskbar(packageName)` reusing `addPin`, and accessors returning the app-info and uninstall intents. `placeAppOnDesktop` needs the first free square of the current orientation, which the desktop repository does not expose today - add that query beside the existing cell operations rather than scanning cells in the ViewModel, and emit the desktop-full message when the grid has no room. Replace the Activity's `shortcutMenuManager` with `LauncherAppActionMenuManager` wired to those operations, keeping the existing lazy construction and teardown dismissal.

**Why:**

Strategic §3.3 records the owner's menu contract - Launch, To desktop, Pin to taskbar, App info, Uninstall plus the app's own shortcuts - and the desktop long-press is one of the two places that contract must hold. Routing placement and pinning through the ViewModel keeps the data mutation visible to every reader of the flow, which is the same reason the existing add-flow comment gives for living there.

**Verification:**

- `Grep` - `LauncherAppActionMenuManager` present in `LauncherHomeActivity.kt`.
- `Grep` - `placeAppOnDesktop` and `pinAppToTaskbar` present in `LauncherHomeViewModel.kt`.
- `Grep` - `BuildAppSystemActionIntentUseCase` present in `LauncherHomeDependencies.kt`.
- `Glob` - a timestamped `LauncherHomeActivity.kt` copy exists under `temp/S1401/`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 04.5 - Switch the Start-menu host to the same menu

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Replace the fragment's `LauncherAppShortcutMenuManager` with `LauncherAppActionMenuManager`, wired to the same ViewModel operations the Activity uses. Keep the existing behaviour of leaving the sheet open while the popup is up, since the popup anchors inside it. If `LauncherAppShortcutMenuManager` now has no callers, delete it and its now-dead strings in the same step - CLAUDE.md Rule 20 forbids leaving the orphan behind.

**Why:**

Strategic §5.1 requires one shared menu, so leaving the old manager wired into the second host would be exactly the two-similar-behaviours drift the unification exists to prevent. This host also disappears in Phase 06, so switching it now keeps the codebase consistent in the window between the two phases.

**Verification:**

- `Grep` - `LauncherAppActionMenuManager` present in `LauncherStartMenuFragment.kt`.
- `Grep -rn "LauncherAppShortcutMenuManager" app_v2/src` returns zero hits, or the class still exists with at least one live caller.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles for `standard` and `noLegal` - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in every file listed in "Files Touched".
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_app_action"` exits 0.
- [ ] Dev log entry added for the phase.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings. The listener-symmetry check matters here: the popup registers a dismiss listener per show.
- [ ] `temp/CODE.LOCK` released.

---

## Step Log

- 2026-08-07 - PHASE-BOUNDARY AUDIT. Layers 1, 2 and 3 over the seven changed files.
  - Layer 3, checked and clear: the popup's dismiss listener is registered per show and cleared in it; both hosts still call `dismiss()` on their teardown edge (Activity `onStop`, fragment `onDestroyView`), and `show()` cancels the previous pending query before starting another, so no second window can stack.
  - Layer 2, P3, accepted: `pinAppToTaskbar` sends its confirmation from a second coroutine while `addPin` runs in its own, so the message can reach the screen a moment before the pin row lands. The strip renders from the pins flow either way, so the user sees no inconsistency - only a message that is briefly early.
  - Layer 1, checked and clear: the Activity and the fragment hold no logic beyond routing lambdas into the ViewModel (Rule 3); the manager is named `NounVerbManager` and lives in `helpers/`; the launcher-only files stay under `src/launcherEnabled/` and only the flavor-neutral intent builder sits in `src/main` (Rule 14).
  - Layer 1, note: `LauncherHomeActivity.kt` is 815 LOC. Under the 1500 limit, but the phase's own budget line (≤ 660) was written when the file was 627 - other tickets have grown it since. Recorded rather than acted on: splitting it is not this ticket's work.
  - Layer 4: not applicable - this phase touches no Room surface.
- 2026-08-07 - Step 04.1 done. `BuildAppSystemActionIntentUseCase` resolves both handlers through `resolveActivityCompat` and answers null when the device has none, so the menu never offers an entry the platform will refuse. `NameNotFoundException` is logged at info and answered with null - a package removed a moment ago is a stale row, not a failure. Verification: 4/4 PASS.
- 2026-08-07 - Step 04.2 done. Seven keys, one lockstep call each. `launcher_app_action_desktop_full` names the way out (free a square, or rotate to the other layout) rather than only reporting the refusal. Verification: 7 keys in three locales; `check_strings_localized.ps1 -KeyPrefix "launcher_app_action"` exit 0.
- 2026-08-07 - Step 04.3 done. `LauncherAppActionMenuManager` composes Open / Put on desktop / Pin to taskbar / App info / Uninstall, then the app's own shortcuts; the two system entries are added only when their intent resolved, so nothing is ever drawn disabled. The row type moved into the adapter as `LauncherAppMenuRow` (sealed, Action + Shortcut), which is what lets one popup and one adapter serve both kinds. `startSystemIntent` still catches `ActivityNotFoundException`: the handler was resolved when the row was built, and a package can be removed between the two. Verification: 5/5 PASS.
- 2026-08-07 - Step 04.4 done. Activity backed up to `temp/S1401/LauncherHomeActivity.kt.20260807-110500.bak`. **Plan corrected:** the step asked for a new "first free square" query on `LauncherDesktopRepository`, but `addCellInFirstFreeSlot(cell, columns)` already exists there (S1209) and does exactly that, so neither `LauncherDesktopRepository.kt` nor its implementation was touched - the two files stay off this phase's changed set. The column count is read at the moment of the tap through `currentColumns()`, not captured when the menu was built, because it belongs to the screen drawing the desktop. Verification: 5/5 PASS, `.\a.ps1 fk` exit 0.
- 2026-08-07 - Step 04.5 done. The Start menu routes through the same manager; its own placement resolves columns from `LauncherGridGeometry` because a bottom sheet has no desktop to measure, and a cell placed from there must land on the same grid the home surface draws. `LauncherAppShortcutMenuManager` had no callers left and was deleted (Rule 20). `launcher_app_shortcut_start_failed` was NOT deleted with it - the new manager still shows it, for a shortcut and for a system screen that disappeared. Verification: 3/3 PASS.

---

## Handoff Notes to Next Phase

Long-pressing an app anywhere in launcher mode now opens the full action menu. Phase 05 reuses this manager as-is; it needs no new entry points.

---

## Rollback Plan

Revert the phase commit - no schema and no persisted state changed.
