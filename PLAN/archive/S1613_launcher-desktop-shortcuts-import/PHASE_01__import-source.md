# Phase 01 - Import source

**Strategic spec:** [`../S1613_launcher-desktop-shortcuts-import.md`](../S1613_launcher-desktop-shortcuts-import.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-08-13
**Completed:** 2026-08-13

---

## Objective

Teach the existing `LauncherApps` seam to list every live shortcut the platform still records as pinned by this launcher, across all packages, without decoding icons.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/AppShortcutDataSource.kt` | Modified | ≤ 230 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> No flavor source set is involved: this file already lives in `src/main` and is reached only from the launcher, which compiles where `SUPPORT_LAUNCHER` is on.

---

## Steps

### Step 01.1 - Make icon decoding optional in the shortcut mapper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/AppShortcutDataSource.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `decodeIcon: Boolean = true` parameter to the private `toShortcut` mapper and skip the `iconOf` call when it is false, leaving `icon = null`. Existing call sites keep the default and change behaviour in no way.

**Why:**

Strategic §3.2 caps what the seed may cost: the import runs inside desktop seeding, before the first grid draw, so decoding one drawable per pinned shortcut would put an unbounded image decode on the path to the user's first home screen. The desktop cell resolves its own icon later through the existing `pinned` lookup, so the icon is not needed at import time at all.

**Verification:**

- `Grep` - `decodeIcon: Boolean = true` present in `AppShortcutDataSource.kt`.
- `Grep` - `iconOf(` appears inside a conditional guarded by `decodeIcon`.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - AppShortcutDataSource: toShortcut gained decodeIcon (default true, no behaviour change); allPinned() lists every live shortcut pinned by this launcher across all packages, icon-free, empty without the home role. Verified by grep: 8/8 predicates PASS.

---

### Step 01.2 - Add the all-pinned query

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/AppShortcutDataSource.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `fun allPinned(): List<AppShortcut>` that returns every shortcut the platform records as pinned by this launcher across all packages. Build a `LauncherApps.ShortcutQuery` with `FLAG_MATCH_PINNED` and no package filter, call `getShortcuts` for `Process.myUserHandle()`, and map each result through `toShortcut(decodeIcon = false)` using `info.package` as the package name. Return an empty list when `isHostPermitted()` is false. Guard the service call with the same `SecurityException` and `IllegalStateException` catches the neighbouring queries use, each logging at `Timber.i` with its own message. Drop any result whose `isEnabled` is false. Apply no cap on the number returned. Give the method a KDoc opening with `S1613:` that states it is the launcher's own pinned set and that the platform offers no way to read another launcher's.

**Why:**

Strategic §5.1.1 makes this the single import source, and §2.3 forbids the import from producing a dead tile, which is what the `isEnabled` filter buys - the same liveness criterion that already gates a pinned cell before launch. §7 rules out a cap explicitly: silently dropping part of the user's shortcuts is the loss this ticket exists to stop.

**Verification:**

- `Grep` - `fun allPinned(): List<AppShortcut>` matches exactly once.
- `Grep` - `FLAG_MATCH_PINNED` present in the new method body and `setPackage` absent from it.
- `Grep` - `isEnabled` referenced inside `allPinned`.
- `Grep` - `S1613:` present in the method KDoc.
- `Grep` - `catch (e: SecurityException)` and `catch (e: IllegalStateException)` both present in the new method.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - AppShortcutDataSource: toShortcut gained decodeIcon (default true, no behaviour change); allPinned() lists every live shortcut pinned by this launcher across all packages, icon-free, empty without the home role. Verified by grep: 8/8 predicates PASS.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

`allPinned()` is the only import source and returns icon-free `AppShortcut` values whose `packageName`, `id` and `label` are exactly the three fields `LauncherCellCommand.PinnedShortcut` encodes. Without the home role it returns an empty list, which is a normal state and not an error.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
