# Phase 02 - Pinned shortcut as a desktop cell command

**Strategic spec:** [`../S1205_launcher-host-third-party-pinned-shortcuts.md`](../S1205_launcher-host-third-party-pinned-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Add a `pin:` command kind to the stored cell target and complete both exhaustive `when` blocks over it, so a pinned shortcut renders on the desktop and launches from it.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1205 phase 02"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommand.kt` | Modified | ≤ 280 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommandTest.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt` | Modified | ≤ 300 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> **Flavor placement.** All four files are shared launcher domain code already resident in `src/main/java`; nothing here is flavor-specific.

---

## Steps

### Step 02.1 - Add the `pin:` command kind

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommand.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `data class PinnedShortcut(val packageName: String, val shortcutId: String, val label: String) : LauncherCellCommand` with `PREFIX_PIN = "pin:"`. Encode the three fields through the existing `encodeField` percent-encoder joined by `SEPARATOR`, mirroring `Contact`; decode with a fixed field count and the same tolerant contract - a wrong field count or an empty package or shortcut id yields null. Extend the KDoc prefix list at the top of the file with the new namespace.

**Why:**

Strategic §2 records that the desktop cell already recognises a `key[:parameter]` target and that a slot for a new target kind exists there, and §4 decision 1 requires the caption to travel with the cell, which a publisher's label may itself contain a `:` or a newline - the reason `Contact` percent-encodes every field rather than splitting on a fixed count of raw ones.

**Verification:**

- `Grep` - `data class PinnedShortcut` matches exactly once in `LauncherCellCommand.kt`.
- `Grep` - `PREFIX_PIN` matches at least three times in that file (constant, `encode`, `decode`).
- `Grep` - `pin:` present in the KDoc prefix list of that file.

**Status:** `[x]` done

---

### Step 02.2 - Cover the encoding round trip with a unit test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommandTest.kt`
**Depends on:** Step 02.1 (authoring), Step 02.4 (running)

> Corrected during implementation on 2026-08-06: adding a subtype to the sealed interface in Step 02.1 breaks both exhaustive `when` blocks, so the module does not compile - and this test therefore cannot run - until Steps 02.3 and 02.4 have restored them. Author the file after 02.1; run its verification after 02.4.

**Prompt for developer:**

> Create `LauncherCellCommandTest` with JUnit4. Assert: a `PinnedShortcut` whose label contains `:` and a newline survives `encode()` then `decode()` unchanged in all three fields; `decode("pin:")` is null; `decode` of a two-field `pin:` payload is null; and `decode` of an unknown prefix is null. Do not touch Android classes - the encoder is pure `java.net.URLEncoder`, so the test runs on the JVM source set.

**Why:**

Strategic §4 decision 1 makes the stored label the only caption a pinned cell will ever have, so a target string that a publisher's own label can corrupt would silently orphan the cell - and the field layout of a persisted record is exactly what a later append must not reorder.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommandTest.kt` exists.
- `Grep` - `class LauncherCellCommandTest` matches exactly once in that file.
- Run `.\a.ps1 fu -Tests "*LauncherCellCommandTest*"` - expected: exit 0, all tests pass; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

---

### Step 02.3 - Launch a pinned shortcut from its cell

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inject `AppShortcutDataSource` and add the `is LauncherCellCommand.PinnedShortcut` branch to `launch`, delegating to the existing `start(packageName, shortcutId, sourceBounds = null)`. Keep the journal call untouched - a started pin records like every other command.

**Why:**

Strategic §3 states the launcher does not need the foreign intent because a shortcut can be started by its identifier, which is precisely what the existing `start` seam does; without this branch the sealed `when` stops compiling the moment Step 02.1 lands.

**Verification:**

- `Grep` - `is LauncherCellCommand.PinnedShortcut` matches exactly once in `ExecuteLauncherCommandUseCase.kt`.
- `Grep` - `AppShortcutDataSource` present in that file.
- `.\a.ps1 fk` - expected exit 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

---

### Step 02.4 - Resolve the pinned cell's caption and icon

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`
**Depends on:** Step 02.1, Step 01.3

**Prompt for developer:**

> Inject `AppShortcutDataSource` and add the `is LauncherCellCommand.PinnedShortcut` branch. Call `pinned(packageName, shortcutId)`: on a live match return a `LauncherCommandVisual` with that shortcut's own label and icon drawable and `iconKey = "$packageName#$shortcutId"`; on null return a visual carrying the stored label with `iconRes = R.drawable.ic_launcher_mode` and no drawable. Never return null from this branch.

**Why:**

Strategic §4 decision 3 requires a vanished shortcut's cell to stay and be marked inactive rather than disappear, because the shortcut may only be away while its owning app updates or fails, and this file's own `appVisual` already resolves that same situation for an uninstalled app by keeping an identifying caption behind a placeholder glyph.

**Verification:**

- `Grep` - `is LauncherCellCommand.PinnedShortcut` matches exactly once in `ResolveLauncherCommandLabelUseCase.kt`.
- `Grep` - `pinned(` present in that file.
- `Grep` - `ic_launcher_mode` matches at least twice in that file (existing `appVisual` fallback plus the new one).
- `.\a.ps1 fk` - expected exit 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

---

## Step Log

- 2026-08-06 - Step 02.1 PASS. `data class PinnedShortcut` = 1 hit, `PREFIX_PIN` = 4 hits, KDoc prefix line present.
- 2026-08-06 - Step 02.2 authored, then blocked: `.\a.ps1 fu -Tests "*LauncherCellCommandTest*"` failed to compile because Steps 02.3/02.4 had not yet restored the two exhaustive `when` blocks. Step dependency corrected in place (authoring after 02.1, running after 02.4); no code change was needed.
- 2026-08-06 - Step 02.3 PASS. `is LauncherCellCommand.PinnedShortcut` = 1 hit, `AppShortcutDataSource` = 3 hits in `ExecuteLauncherCommandUseCase.kt`.
- 2026-08-06 - Step 02.4 PASS. `is LauncherCellCommand.PinnedShortcut` = 1 hit, `pinned(` = 1 hit, `ic_launcher_mode` = 2 hits in `ResolveLauncherCommandLabelUseCase.kt`.
- 2026-08-06 - `.\a.ps1 fk` - expected: 0 | actual: 0 (BUILD SUCCESSFUL; only pre-existing deprecation warnings, none in the touched files).
- 2026-08-06 - Step 02.2 re-run PASS. `.\a.ps1 fu -Tests "*LauncherCellCommandTest*"` - expected: 0 | actual: 0. Result XML: tests=6 failures=0 errors=0.
- 2026-08-06 - Phase-boundary audit: Layer 1 only. No listener, lifecycle or Room surface; `pinnedShortcutVisual` runs inside the resolver's existing `withContext(Dispatchers.IO)`, so the added binder call stays off the main thread. No P0/P1 findings.

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

A `pin:` cell now renders and launches; nothing writes one yet. The encoded field order in `LauncherCellCommand` is a persistence format from this point on - append, never reorder.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed; no stored target uses the `pin:` prefix until Phase 03 writes one.
