# Phase 01 - Shortcut data source and use cases

**Strategic spec:** [`../S0427_third-party-app-shortcuts.md`](../S0427_third-party-app-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Introduce the `LauncherApps`-backed data source plus the query and start use cases that expose an installed app's published shortcuts to the rest of the app. No UI yet.

---

## Prerequisites

- [ ] Strategic §4.1 and §4.2 Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/AppShortcut.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/AppShortcutDataSource.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/QueryAppShortcutsUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/StartAppShortcutUseCase.kt` | New | ≤ 60 |

> These files carry no flavor guard: `LauncherApps` exists on every flavor and the capability is gated by the home role at runtime, so `src/main` is the correct home (same placement as the rest of the launcher domain/data layer).

---

## Steps

### Step 01.1 - Add the `AppShortcut` domain model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/AppShortcut.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `data class AppShortcut(val id: String, val packageName: String, val label: String, val icon: Drawable?, val isEnabled: Boolean, val disabledMessage: String?)`. `label` is the shortcut's short label (fall back to the long label when the short one is blank). Carrying the `Drawable` on the model mirrors `LaunchableApp` in `QueryLaunchableAppsUseCase` - the icon comes from `LauncherApps`, not from a resource id the UI could resolve itself.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/AppShortcut.kt` exists.
- `Grep` - `data class AppShortcut(` matches exactly once.
- `Grep` - `val disabledMessage: String?` present.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification 3/3 PASS. Files: domain/model/launcher/AppShortcut.kt (+18 LOC).

---

### Step 01.2 - Add `AppShortcutDataSource`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/AppShortcutDataSource.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `@Singleton class AppShortcutDataSource @Inject constructor(@ApplicationContext private val context: Context)`. It is the only place in the app that touches `LauncherApps`. Expose:
>
> - `fun isHostPermitted(): Boolean` - `launcherApps?.hasShortcutHostPermission() == true`, wrapped so a `SecurityException` returns false.
> - `fun query(packageName: String): List<AppShortcut>` - build a `LauncherApps.ShortcutQuery` with `setPackage(packageName)` and `setQueryFlags(FLAG_MATCH_MANIFEST or FLAG_MATCH_DYNAMIC)`, call `getShortcuts(query, Process.myUserHandle())`, map each `ShortcutInfo` to `AppShortcut` with `getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)`, keep the order the API returned, and take at most `MAX_SHORTCUTS = 5`.
> - `fun start(packageName: String, shortcutId: String, sourceBounds: Rect?): Boolean` - `startShortcut(packageName, shortcutId, sourceBounds, null, Process.myUserHandle())`, returning false on failure.
>
> Both `query` and `start` return the empty/false result when `isHostPermitted()` is false, and both catch `SecurityException` and `IllegalStateException` separately from the happy path - the home role can be revoked between the permission check and the call, and the user must not see a crash for that. Log those catches at `Timber.i` with the package name; a revoked role is expected, not an error. This class is blocking - callers move it off the main thread.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/AppShortcutDataSource.kt` exists.
- `Grep` - `hasShortcutHostPermission` matches exactly once in that file.
- `Grep` - `FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC` present.
- `Grep` - `FLAG_MATCH_PINNED` returns zero hits in that file.
- `Grep` - `catch (e: SecurityException)` matches at least twice.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification 6/6 PASS. Files: data/launcher/AppShortcutDataSource.kt (+107 LOC). `hasShortcutHostPermission` matches twice: one call site plus one KDoc mention - the predicate's intent (a single call site) holds. `start()` also catches `ActivityNotFoundException` - a shortcut whose target activity was uninstalled throws it rather than `SecurityException`.

---

### Step 01.3 - Add `QueryAppShortcutsUseCase` and `StartAppShortcutUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/QueryAppShortcutsUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/StartAppShortcutUseCase.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> `QueryAppShortcutsUseCase @Inject constructor(private val dataSource: AppShortcutDataSource)` exposes `suspend operator fun invoke(packageName: String): List<AppShortcut>` running the data source inside `withContext(Dispatchers.IO)` - the query is an IPC plus icon decode, so it never runs on the main thread. Drop shortcuts whose `label` is blank.
>
> `StartAppShortcutUseCase @Inject constructor(private val dataSource: AppShortcutDataSource)` exposes `suspend operator fun invoke(shortcut: AppShortcut, sourceBounds: Rect?): Boolean`, also on `Dispatchers.IO`. Do not touch `LauncherJournalRepository`: the journal stores `LauncherCellCommand` values and a shortcut is not one.
>
> Neither use case needs a Hilt module - constructor injection of a `@Singleton` data source is enough.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class QueryAppShortcutsUseCase` matches exactly once.
- `Grep` - `class StartAppShortcutUseCase` matches exactly once.
- `Grep` - `withContext(Dispatchers.IO)` present in both files.
- `Grep` - `LauncherJournalRepository` returns zero hits in both files.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification 5/5 PASS. Files: QueryAppShortcutsUseCase.kt (+20 LOC), StartAppShortcutUseCase.kt (+25 LOC).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL in 23s.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added via `post-change.ps1` (PASS, all gates).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the same facade.
- [x] Phase-boundary audit run - no P0/P1. Layer 1: layering holds (UI-free data source behind two use cases, no repository needed for a stateless system-service read). Layer 2: both use cases hop to `Dispatchers.IO`; the data source itself is documented blocking. Layer 3: no listeners, no retained context - `@Singleton` holds the application context only.

---

## Handoff Notes to Next Phase

`AppShortcutDataSource` is the single `LauncherApps` seam; UI code must go through the two use cases and never call `LauncherApps` itself. The 5-item cap and the manifest+dynamic flag pair are enforced in the data source, so the UI does not re-apply them.

---

## Rollback Plan

Revert phase commit(s) - all files are new, no data migration and no user-facing surface changed.
