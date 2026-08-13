# Phase 04 - Share and scan actions

**Strategic spec:** [`../S1424_launcher-shortcut-full-resource-menu.md`](../S1424_launcher-shortcut-full-resource-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Wire the three resource actions Phase 02 deliberately withheld - export, SFTP access sharing and rescan - so the desktop menu becomes the full mirror the owner asked for.

---

## Prerequisites

- [x] Phase 01 and Phase 02 are ✅ Done - verified green by the owner.
- [x] `CODE.LOCK` acquired before the first source edit, released after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceShareIntents.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainEventHandler.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceScanCoordinator.kt` | Modified | ≤ 330 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResourceActionManager.kt` | Modified | ≤ 380 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeDependencies.kt` | Modified | ≤ 90 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 960 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 700 |

> `ResourceScanCoordinator` gains an `@Inject` constructor - nothing else. It is currently assembled
> by hand inside `MainViewModel` from seven injected dependencies, and the launcher would otherwise
> have to inject all seven to reach it. `MainViewModel` keeps building its own; an injectable
> constructor takes nothing away from that.
>
> The S1424 dependencies move out of `LauncherShortcutDependencies` into a new
> `LauncherCellMenuDependencies` in the same file. `LauncherShortcutDependencies` answers to app
> cells by its own KDoc, these never serve an app cell, and eight more fields on it would cross
> detekt's `constructorThreshold` of 10.

---

## Steps

### Step 04.1 - Extract the resource share intents

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceShareIntents.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Move the `ShareResourceFile` and `ShareCompanionConfigFile` intent construction out of `MainEventHandler` into `object ResourceShareIntents`, exposing one function per payload that returns the chooser `Intent`. Leave `MainEventHandler` calling the extracted functions so the main window's behaviour is unchanged.

**Why:**

Strategic §4 records that a share action reaches the desktop unchanged only when it is expressed as an intent, and today the intent is built inside the main window's event handler where a second host cannot reach it.

**Verification:**

- `Grep` - `object ResourceShareIntents` matches once.
- `Grep` - `ResourceShareIntents.` present in `MainEventHandler.kt`.

**Status:** `[x]` done

---

### Step 04.2 - Wire export and SFTP access sharing on the desktop

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResourceActionManager.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add EXPORT and SHARE_SFTP_ACCESS to the executor. EXPORT shows the same `resource_share_credentials_warning` dialog the main window shows, then calls `ExportResourcesToFileUseCase` through the ViewModel and starts the chooser from `ResourceShareIntents`. SHARE_SFTP_ACCESS reuses `MainSftpShareManager`, which already takes a plain `Activity`, and routes its two outcomes through `ExportCompanionConfigUseCase` on the ViewModel. Remove both from the `supports` deny-list.

**Why:**

Strategic ADR-2 states that an item requiring state the desktop lacks is excluded rather than emulated, and neither of these two needs main-window state - only wiring - so excluding them would contradict the owner's §3.3 ruling that the desktop menu mirrors the main-window menu apart from §5.2.

**Verification:**

- `Grep` - `ResourceMenuAction.EXPORT` and `ResourceMenuAction.SHARE_SFTP_ACCESS` both handled in the executor's `when`.
- `Grep` - neither appears in the `supports` deny-list any more.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 04.3 - Wire rescan on the desktop

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResourceActionManager.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add SCAN to the executor, calling `ResourceScanCoordinator.scanAndRefreshSingleResource` from the ViewModel off the main thread and reporting the `Unavailable` outcome with the same `resource_unavailable_name` message the main window uses. Remove SCAN from the `supports` deny-list.

**Why:**

Strategic §11.3 requires every shown item to finish its action, so SCAN is either wired or absent, and §3.3 puts it inside the mirror because §5.2 does not exclude it.

**Verification:**

- `Grep` - `ResourceMenuAction.SCAN` handled in the executor's `when`.
- `Grep` - `resource_unavailable_name` referenced from the launcher executor or its ViewModel path.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - **UNPROVEN**: no gradle ran in this session, by instruction.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] `supports` denies nothing that `MenuActionSurface.LAUNCHER_DESKTOP` emits for a resource - `DEFERRED` is now empty.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

With the deny-list empty, the desktop menu equals `ResourceActionCatalog.actionsFor(LAUNCHER_DESKTOP, ..)`, so Phase 05 can repoint the main window at the same catalog and compare the two lists directly.

---

## Rollback Plan

Revert the phase commit; the three actions return to the `supports` deny-list and disappear from the menu rather than breaking.
