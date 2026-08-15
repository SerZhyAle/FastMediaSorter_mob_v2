# Phase 01 - Pinned-shortcut seam on LauncherApps

**Strategic spec:** [`../S1205_launcher-host-third-party-pinned-shortcuts.md`](../S1205_launcher-host-third-party-pinned-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Teach `AppShortcutDataSource` the three pinned-shortcut operations the platform offers a home app - read a pin request, accept it, and ask whether an accepted shortcut is still alive - without any caller yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1205 phase 01"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/AppShortcutDataSource.kt` | Modified | ≤ 220 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> **Flavor placement.** The file already lives in `src/main/java` alongside every other launcher data and domain class; only the launcher UI and the HOME activity live in `src/launcherEnabled`. Nothing here is flavor-specific and no `BuildConfig.IS_*` guard is introduced.

---

## Steps

### Step 01.1 - Read a pin request off the delivered intent

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/AppShortcutDataSource.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `fun pinRequestFrom(intent: Intent): LauncherApps.PinItemRequest?` returning the request only when all of these hold: the service resolves, `getPinItemRequest` returns non-null, `isValid` is true, `getRequestType() == PinItemRequest.REQUEST_TYPE_SHORTCUT`, and `getShortcutInfo()?.userHandle == Process.myUserHandle()`. Return null otherwise, logging the rejected case at `Timber.i` with the request type only. Wrap the service call the same way the existing queries are wrapped - a `SecurityException` or `IllegalStateException` yields null rather than propagating.

**Why:**

Strategic §3 states the intent behind a foreign shortcut is readable only by the default launcher and must not be copied, so the request object itself is the only handle this app ever gets on it; and the existing seam already starts shortcuts as `Process.myUserHandle()`, so a request from another profile could be accepted but never launched.

**Verification:**

- `Grep` - `fun pinRequestFrom` matches exactly once in `AppShortcutDataSource.kt`.
- `Grep` - `REQUEST_TYPE_SHORTCUT` present in that file.
- `Grep` - `myUserHandle` matches at least twice in that file (existing `start`/`query` plus the new guard).
- `Grep -n "Log\.d\("` over `AppShortcutDataSource.kt` returns zero hits.

**Status:** `[x]` done

---

### Step 01.2 - Accept a pin request and return its snapshot

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/AppShortcutDataSource.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `fun acceptPinRequest(request: LauncherApps.PinItemRequest): AppShortcut?`. Call `request.accept()`; return null when it answers false or throws `IllegalStateException`/`SecurityException`. On success build an `AppShortcut` from `request.shortcutInfo` through the existing private `toShortcut` helper so label fallback and icon decoding stay in one place. Return null when `shortcutInfo` is null.

**Why:**

Strategic §3 fixes the mechanism as "accept the pin request and store it as a cell, then start it by id later", and §4 decision 1 fixes the caption and icon as coming from the request alone, so the accepted request is the single moment at which that label can be captured.

**Verification:**

- `Grep` - `fun acceptPinRequest` matches exactly once in `AppShortcutDataSource.kt`.
- `Grep` - `request.accept()` present in that file.
- `Grep` - `toShortcut(` matches at least twice in that file (existing `query` call plus the new one).

**Status:** `[x]` done

---

### Step 01.3 - Ask whether an accepted shortcut is still alive

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/AppShortcutDataSource.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `fun pinned(packageName: String, shortcutId: String): AppShortcut?` running a `LauncherApps.ShortcutQuery` with `setPackage(packageName)`, `setShortcutIds(listOf(shortcutId))` and `setQueryFlags(FLAG_MATCH_PINNED)`. Return the mapped `AppShortcut` only when a match comes back and `isEnabled` is true; return null when the host permission is absent, the list is empty, or the match is disabled. Reuse the exception handling of the existing `query`.

**Why:**

Strategic §4 decision 3 requires a shortcut that stopped existing to be marked inactive while its cell stays, and strategic §2 records that the launcher has no disappearance handling at all today, so a liveness answer must exist before any cell can be drawn as inactive.

**Verification:**

- `Grep` - `fun pinned` matches exactly once in `AppShortcutDataSource.kt`.
- `Grep` - `FLAG_MATCH_PINNED` present in that file.
- `Grep` - `setShortcutIds` present in that file.

**Status:** `[x]` done

---

## Step Log

- 2026-08-06 - Step 01.1 PASS. `pinRequestFrom` = 1 hit, `REQUEST_TYPE_SHORTCUT` present, `myUserHandle` = 5 hits, `Log.d(` = 0 hits.
- 2026-08-06 - Step 01.2 PASS. `acceptPinRequest` = 1 hit, `request.accept()` = 1 hit, `toShortcut(` = 4 hits.
- 2026-08-06 - Step 01.3 PASS. `pinned` = 1 hit, `FLAG_MATCH_PINNED` = 1 hit, `setShortcutIds` = 1 hit.
- 2026-08-06 - Process deviation: all three methods landed in one `Edit` because they are one contiguous block in one file. Each step's Verification was still run and recorded separately, in order, before its own status flip.
- 2026-08-06 - detekt FAIL on first closure: `ReturnCount` on all three new functions (7, 4 and 4 returns against a limit of 2). Rewritten as `?.takeIf`/`?.let` chains, two returns each. Re-closure `post-change: PASS`.
- 2026-08-06 - `.\a.ps1 fk` - expected: 0 | actual: 0 (BUILD SUCCESSFUL, after the detekt rewrite).
- 2026-08-06 - Phase-boundary audit (Layer 1 only; no listener, coroutine, lifecycle or Room surface in this phase). One P2: `acceptPinRequest` and `pinned` both do binder IPC plus an icon decode, so a main-thread caller would stall. Fixed by construction rather than deferred - Phase 03's step now requires `withContext(Dispatchers.IO)` and verifies it.

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

Three new seam methods exist and nothing calls them. `pinned()` returning null is the single definition of "this pinned shortcut is gone" for the rest of the ticket - Phase 02 consumes it and must not re-derive liveness any other way.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
