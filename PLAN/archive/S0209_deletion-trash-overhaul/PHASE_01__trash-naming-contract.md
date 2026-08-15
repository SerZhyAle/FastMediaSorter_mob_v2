# Phase 01 — Trash naming contract

**Strategic spec:** [`../S0209_deletion-trash-overhaul.md`](../S0209_deletion-trash-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Introduce a single source-of-truth contract for trash-folder naming (`TrashFolderContract`) that owns the prefix, timestamp format, parser, recognizer (including legacy patterns), and path builder. No call site changes yet — switching happens in Phase 02.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items #1–#5 are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/trash/TrashFolderContract.kt` | New | ≤ 180 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/trash/TrashFolderContractTest.kt` | New | ≤ 200 |

---

## Steps

### Step 01.1 — Create `TrashFolderContract`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/trash/TrashFolderContract.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a Kotlin object `TrashFolderContract` under `data/transfer/trash/`. It owns the canonical naming. Fields and helpers:
> - `const val CONTAINER_NAME = ".trash"` — the canonical container directory name placed next to source files.
> - `fun buildSnapshotPath(parentPath: String, timestampMs: Long): String` returning `"$parentPath/$CONTAINER_NAME/$timestampMs"`.
> - `fun buildContainerPath(parentPath: String): String` returning `"$parentPath/$CONTAINER_NAME"`.
> - `fun isContainerDir(name: String): Boolean` returning `name == CONTAINER_NAME` (no underscore variants).
> - `fun parseSnapshotTimestamp(snapshotDirName: String): Long?` returning `snapshotDirName.toLongOrNull()` for snapshot subdirs inside `.trash/`.
> - `fun isLegacyContainerDir(name: String): Boolean` returning `name.startsWith(".trash_")` — legacy single-level pattern used by migration only.
> - `fun parseLegacyTimestamp(legacyDirName: String): Long?` returning the numeric suffix after `.trash_` as Long, or `null` if not parseable.
> Add KDoc explaining: canonical layout is `<parent>/.trash/<timestamp>/`; legacy layout `<parent>/.trash_<timestamp>/` is recognized for one-time best-effort migration.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/trash/TrashFolderContract.kt` exists.
- `Grep` — `object TrashFolderContract` matches exactly once.
- `Grep` — `const val CONTAINER_NAME = ".trash"` present.
- `Grep` — `fun buildSnapshotPath` and `fun parseSnapshotTimestamp` both present.
- `Grep` — `fun isLegacyContainerDir` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/trash/TrashFolderContract.kt (new, 79 LOC). Dev log recorded.

---

### Step 01.2 — Unit tests for contract round-trip

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/trash/TrashFolderContractTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Write unit tests covering: (a) `buildSnapshotPath("/x/y", 1700000000000L)` returns `"/x/y/.trash/1700000000000"`. (b) `parseSnapshotTimestamp("1700000000000")` returns `1700000000000L`. (c) `parseSnapshotTimestamp("garbage")` returns `null`. (d) `isContainerDir(".trash")` is true; `isContainerDir(".trash_123")` is false. (e) `isLegacyContainerDir(".trash_123")` is true; `isLegacyContainerDir(".trash")` is false. (f) `parseLegacyTimestamp(".trash_1700000000000")` returns `1700000000000L`; `parseLegacyTimestamp(".trash_x")` returns `null`. Use JUnit 4 (project standard).

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/trash/TrashFolderContractTest.kt` exists.
- `Grep` — `@Test` matches at least 6 times in that file.
- Build (target variant compiles): test source set compiles cleanly — run `/build` with the `testStandardDebugUnitTest` task or equivalent. `expected: BUILD SUCCESSFUL | actual: <observed>`.
- The new tests pass — `expected: 6 passed | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification PASS for own changes. Files: `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/trash/TrashFolderContractTest.kt` (new, 57 LOC, 7 @Test). XML report `app_v2/build/test-results/testStandardDebugUnitTest/TEST-com.sza.fastmediasorter.data.transfer.trash.TrashFolderContractTest.xml`: `tests=7 failures=0 errors=0 skipped=0`. Overall `testStandardDebugUnitTest` exited non-zero due to 26 pre-existing failures in unrelated classes (`StructuredMediaSnifferTest`, `MouseEventHandlerTest`, `SupportIntentFactoryTest`, `CommandPanelLayoutPlannerTest`, `NetworkErrorMessageMapperTest`, `ProvisionDefaultResourcesUseCaseTest`, etc.) — accepted by user policy (out of S0209 scope). Dev log recorded.

---

### Step 01.3 — Catalog scan/render

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` (generated)
**Depends on:** Step 01.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. For the new class `TrashFolderContract` set role and status via `set.ps1`: role `domain-utility`, status `Active`.

**Verification:**

- `Grep` — `TrashFolderContract` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `TrashFolderContract` present in `dev/CATALOG/app_v2.md`.
- `expected: scan.ps1 exit 0 | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification PASS. scan.ps1 → 1057 files indexed, render.ps1 → 1057 records rendered. `TrashFolderContract` row updated: role=`domain-utility`, status=`tested`. Catalog status enum is `new|tested|legacy|todo|unknown` — picked `tested` (closest to "Active"; tests exist).

---

### Step 01.4 — Dev log

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Steps 01.1, 01.2, 01.3

**Prompt for developer:**

> For every file added in Steps 01.1–01.3 run `.\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"`. Targets: `phase` for code/test files, `catalog` for catalog files.

**Verification:**

- `Grep` — newest `dev/CHANGELOG.md` block contains at least one entry per touched file.
- `expected: add_to_dev_log.ps1 exit 0 for each call | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification PASS. Dev log entries written inline at each prior step: `TrashFolderContract.kt` (Step 01.1), `TrashFolderContractTest.kt` (Step 01.2), `app_v2.jsonl` + `app_v2.md` (Step 01.3). All script calls returned exit 0.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- `TrashFolderContract` is the only public surface for trash-directory naming. Phase 02 replaces every hard-coded `.trash` / `.trash_` literal in production code with calls to this object.

---

## Rollback Plan

- Revert the phase commit. No persisted data depends on this code yet.
