# Phase 02 - Companion import stores all access paths

**Strategic spec:** [`../S1006_sftp-multipath-endpoint-fallback.md`](../S1006_sftp-multipath-endpoint-fallback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

On companion import, persist the whole ordered `accessPaths` set (primary in `path`, the rest in `altAccessPaths`) and register a credential row for every candidate host:port, so any resolved endpoint has a matching credential.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`altAccessPaths` field + mappers exist).
- [ ] Research 01 read (credentials keyed by host:port).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/companion/ImportCompanionConfigUseCase.kt` | Modified | ≤ 260 |

---

## Steps

### Step 02.1 - Keep the primary path, capture the remaining access paths as alternates

**Files:** `domain/usecase/companion/ImportCompanionConfigUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `import(config)` the code currently takes `config.accessPaths.orEmpty().first()` and drops the rest. Keep the first entry as the primary (host/port used for `path`), and map `accessPaths.drop(1)` into a `List<HostPort>` passed to `buildResource(..)`. Set `MediaResource.altAccessPaths` to that list on every root resource built. A single-path config yields an empty alternates list (unchanged behaviour).

**Verification:**

- `Grep` - `altAccessPaths` matches in `ImportCompanionConfigUseCase.kt`.
- `Grep` - `accessPaths` still referenced (primary + alternates both consumed).

**Status:** `[ ]` not done

---

### Step 02.2 - Save a credential row for every candidate host:port

**Files:** `domain/usecase/companion/ImportCompanionConfigUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Credentials are looked up by host:port at connect time (see Research 01), so the WAN candidate needs its own row. After saving the primary credential via `smbOperationsUseCase.saveSftpCredentials(host, port, username, password)`, also call it once per alternate `HostPort` with the SAME username/password (the companion uses one credential for all paths). Deduplicate identical host:port pairs. The primary `credentialsId` stays on the resource; alternates rely on the by-host lookup finding their own row. Any per-candidate save failure logs at `Timber.w` and does not abort the import (the primary already succeeded).

**Verification:**

- `Grep` - `saveSftpCredentials` matches at least twice (primary + per-candidate loop) OR appears inside a loop over candidates.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[ ]` not done

---

### Step 02.3 - Unit-test multi-path import mapping

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/companion/ImportCompanionConfigUseCaseTest.kt` (New or extend existing)
**Depends on:** Steps 02.1, 02.2

**Prompt for developer:**

> Add a test that imports a config with two `accessPaths` (lan + portforward) and asserts: the built resource's `path` uses the first (lan) endpoint; `altAccessPaths` contains the second (portforward) endpoint; a credential save was requested for both host:port pairs. Add a second case: a single-path config yields an empty `altAccessPaths` and one credential save.

**Verification:**

- `Glob` - the test file exists.
- `Grep` - `altAccessPaths` asserted in the test.
- Run `/build` test task for the affected module (or `.\a.ps1 fu`) - the new test passes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for the modified file(s).

---

## Handoff Notes to Next Phase

Imported companion resources now carry their full candidate set and have a credential row per candidate. Phase 04 will make connections actually use whichever candidate is reachable.

---

## Rollback Plan

Revert the phase commit(s). No schema or user-facing surface changed; already-imported resources keep working (they resolve to their primary path via the by-host credential).
