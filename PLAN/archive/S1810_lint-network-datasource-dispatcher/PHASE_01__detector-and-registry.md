# Phase 01 - Detector and Registry

**Strategic spec:** [`../S1810_lint-network-datasource-dispatcher.md`](../S1810_lint-network-datasource-dispatcher.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-08-19
**Completed:** 2026-08-19

---

## Objective

Create `NetworkDataSourceDispatcherDetector` in `lint-rules`, register its issue in `CustomIssueRegistry`, and update documentation in `docs/DEV_OPS.md`.

---

## Prerequisites

- [x] S1812 is Verified.
- [x] Working tree is on `DEBUG-v033`.

---

## Files Touched

- `lint-rules/src/main/java/com/sza/fastmediasorter/lint/NetworkDataSourceDispatcherDetector.kt` (new)
- `lint-rules/src/main/java/com/sza/fastmediasorter/lint/CustomIssueRegistry.kt` (modified)
- `docs/DEV_OPS.md` (modified)

---

## Steps

### Step 01.1 - Implement NetworkDataSourceDispatcherDetector

**Files:** `lint-rules/src/main/java/com/sza/fastmediasorter/lint/NetworkDataSourceDispatcherDetector.kt`
**Depends on:** none

**Prompt for developer:**

> Implement `NetworkDataSourceDispatcherDetector : Detector(), SourceCodeScanner`.
> It checks all method calls whose containing class belongs to `com.hierynomus.`, `org.apache.commons.net.`, or `com.jcraft.jsch.`.
> If the call is not enclosed in a background dispatcher `withContext(Dispatchers.IO)` (or `withContext` with a field initialized to `Dispatchers.IO`), and is not inside a private helper whose callers in the file are all enclosed in `withContext`, report `NetworkDataSourceDispatcher`.
> Crucially: unlike `MainThreadIoDetector`, suspend methods without explicit `withContext` are NOT treated as background-confined.

**Why:**

> Network operations perform blocking socket I/O and must be explicitly confined to `Dispatchers.IO` in the data source rather than inheriting caller dispatcher.

**Verification:**

- `Test-Path lint-rules/src/main/java/com/sza/fastmediasorter/lint/NetworkDataSourceDispatcherDetector.kt` returns true.

**Status:** `[x]` done

**Step Log:**

- 2026-08-19 - Implemented NetworkDataSourceDispatcherDetector

---

### Step 01.2 - Register issue in CustomIssueRegistry

**Files:** `lint-rules/src/main/java/com/sza/fastmediasorter/lint/CustomIssueRegistry.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `NetworkDataSourceDispatcherDetector.ISSUE` to `CustomIssueRegistry.issues`.

**Why:**

> Lint scanner only runs issues that are registered in the IssueRegistry.

**Verification:**

- `Grep` - `NetworkDataSourceDispatcherDetector.ISSUE` matches in `CustomIssueRegistry.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-19 - Registered NetworkDataSourceDispatcherDetector.ISSUE in CustomIssueRegistry

---

### Step 01.3 - Update docs/DEV_OPS.md

**Files:** `docs/DEV_OPS.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Update `docs/DEV_OPS.md` section describing custom lint rules (split file I/O vs network data source dispatcher detector, and mention `.\a.ps1 flr`).

**Why:**

> Keeps developer operations documentation synchronized with implemented lint rules.

**Verification:**

- `Grep` - `NetworkDataSourceDispatcher` matches in `docs/DEV_OPS.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-19 - Updated DEV_OPS.md with NetworkDataSourceDispatcher lint rule

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Dev log entry added for every file in "Files Touched".
