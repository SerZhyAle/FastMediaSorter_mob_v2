# Phase 02 - Docs and catalog cleanup

**Strategic spec:** [`../S1314_launcherhomeviewmodel-detekt-debt.md`](../S1314_launcherhomeviewmodel-detekt-debt.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Register the three new holder classes in the class catalog and journal the change, so the launcher's dependency
surface is searchable and the refactor is traceable.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] `LauncherHomeDependencies.kt` exists on disk - `scan.ps1` reads the working tree, not the plan.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (regenerated) | n/a |
| `dev/CHANGELOG.md` | Modified (appended) | n/a |

> `dev/CATALOG/app_v2.jsonl` and `.md` are gitignored local indexes - regenerate them, never commit them.

---

## Steps

### Step 02.1 - Register the holders in the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Rescan the module with `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` so the three holder
> classes enter the index, then classify the new file with a single
> `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "com/sza/fastmediasorter/ui/launcher/LauncherHomeDependencies.kt" -Role "<one sentence>" -Status new -NoFlavors "lite,photos,legacy,vr"`.
> One call covers all three records because `set.ps1` applies manual fields to every record sharing a path. The
> `-NoFlavors` list is the four flavors that mount `src/launcherDisabled/` instead of `src/launcherEnabled/`, so
> the catalog states the isolation that the source-set placement already enforces. Also confirm the entry for
> `LauncherHomeViewModel` now reports a lower injected-dependency count than before the refactor.

**Verification:**

- `Grep` - `LauncherDesktopDependencies`, `LauncherTaskbarDependencies` and `LauncherShortcutDependencies` each match at least once in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `"status":"new"` appears on the records whose `path` is `com/sza/fastmediasorter/ui/launcher/LauncherHomeDependencies.kt`.
- `Grep` - `"noFlavors"` on those records equals `lite,photos,legacy,vr`.
- `Glob` - `dev/CATALOG/app_v2.md` exists and its modification time is later than `LauncherHomeDependencies.kt`'s.

**Status:** `[x]` done

---

### Step 02.2 - Journal the change

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Record one dev-log entry for the ticket, not one per file:
> `pwsh -NoProfile -File scripts/post-change.ps1 -File "app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeDependencies.kt" -Target "spec-dev S1314" -Description "Group 10 of 14 LauncherHomeViewModel dependencies into three Hilt-constructible holders; constructor 14 -> 7 params, LongParameterList cleared without @Suppress or a baseline entry" -ChangeType Kotlin -Module app_v2 -ScopeToFile`.
> Do not add a record to `docs/ALL_FEATURES.jsonl`: this ships no capability a user can reach, and the inventory
> is a capability list, not a change list. Do not touch `docs/FEATURES*.md` - those are `/skill-release`-owned and
> the strategic spec carries no FEATURES sentence. No setting changed, so Rule 22's settings-manifest
> regeneration does not apply, and no string changed, so the localisation audit does not apply.

**Verification:**

- `Grep` - `S1314` matches at least once in `dev/CHANGELOG.md`.
- `Grep` - `S1314` matches zero times in `docs/ALL_FEATURES.jsonl`.
- `Grep` - `LauncherHomeDependencies` matches zero times in `docs/FEATURES.md`, `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md`.
- `Grep` - `Timber.d("S1314` matches zero times across `app_v2/src/**/*.kt` - this ticket introduces no probe tag,
  because its outcome is proved by the detekt gate and the build rather than by a device observation. The
  predicate names the probe form deliberately: a bare `S1314` also matches a rationale comment naming the
  ticket, which is legitimate and which `assert-no-ticket-logs.ps1` accepts (it inspects log calls, not
  comments). `LauncherHomeDependencies.kt` carries exactly such a comment, explaining why the holders are
  plain classes rather than data classes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - the public API gained three classes.
- [x] `temp/CODE.LOCK` released - `post-change.ps1` does this in its closure; if the facade was skipped, call `scripts/utils/exit-code-lock.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit and re-run `scripts/catalog_sync.ps1 -Module app_v2`; the catalog files are gitignored
local indexes and are rebuilt from the working tree, so nothing is lost.
