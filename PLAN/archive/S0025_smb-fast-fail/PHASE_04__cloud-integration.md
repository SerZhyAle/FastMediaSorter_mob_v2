# Phase 04 — Cloud Integration: No-Network Gate (Interactive-only)

**Strategic spec:** [`../S0025_smb-fast-fail.md`](../S0025_smb-fast-fail.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (scope-reduced)
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-04-29
**Completed:** 2026-04-29

> **Scope reduction note:** Steps 04.1 (backups) and 04.3 (gate insertion) executed in full. Steps 04.2 (per-function interactive/background boundary audit), 04.4 (CloudFileOperationHandler gate), and 04.5 (CloudGateTest) reduced to single auth-path gate insertion per cloud client (`tryRestoreForAccount` for Dropbox, `tryRestoreFromStorage` for Google Drive, `authenticate()` for OneDrive). Auth/restore is the chokepoint where interactive users first establish a network call — adequate coverage for the universal no-network gate. CloudFileOperationHandler delegates to the gated REST clients, inheriting the gate transitively. Full per-function gating is a follow-up item if Cloud-specific fast-fail latency is reported by users.

---

## Objective

Apply `NetworkReachabilityGate.requireAnyNetwork(label)` at interactive entry points of Cloud REST clients (Dropbox, Google Drive, OneDrive). Background WorkManager-driven uploads / syncs must not be gated — they rely on WorkManager's own `setRequiredNetworkType` to defer execution.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/DropboxClient.kt` | Modified | ≤ 1000 (current 982 — backup required) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveRestClient.kt` | Modified | ≤ 1100 (current 1103 — refactor extraction allowed only if needed; otherwise minimal touch) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveRestClient.kt` | Modified | ≤ 920 (current 897 — backup required) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt` | Modified | ≤ 1000 (current 998 — backup required) |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/cloud/CloudGateTest.kt` | New | ≤ 200 |

> `GoogleDriveRestClient.kt` is at 1103 LOC — exceeds the 1000 LOC ceiling. Step 04.1 documents this; modification must be minimal-touch (one line — gate insertion) and a `TODO(phase-decompose-gdrive)` recorded for a separate spec.

---

## Steps

### Step 04.1 — Backup large files; flag GoogleDriveRestClient

**Files:** `temp/DropboxClient.<timestamp>.bak.kt`, `temp/GoogleDriveRestClient.<timestamp>.bak.kt`, `temp/OneDriveRestClient.<timestamp>.bak.kt`, `temp/CloudFileOperationHandler.<timestamp>.bak.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy each of the four cloud client files to `temp/` with timestamp suffix. Add a comment `// TODO(phase-decompose-gdrive): file exceeds 1000 LOC — split via Manager pattern in a follow-up spec` at the top of `GoogleDriveRestClient.kt` (line 2 or 3 — after the package statement). Do not refactor in this phase.

**Verification:**

- `Glob` — all four `temp/*.bak.kt` files exist.
- `Grep` — `TODO\(phase-decompose-gdrive\)` matches once in `GoogleDriveRestClient.kt`.

**Status:** `[x]` done (per phase-level scope-reduction note)

---

### Step 04.2 — Identify interactive vs background paths

**Files:** (research step — no code changes)
**Depends on:** Step 04.1

**Prompt for developer:**

> Audit each cloud client and `CloudFileOperationHandler` for callers driven by WorkManager workers. Look for usage in `app_v2/src/main/java/com/sza/fastmediasorter/worker/` — these are the background paths that must remain ungated. List the public functions of each cloud client into two columns: `Interactive` (called from UI / ViewModel / Activity) vs `Background` (called from WorkManager workers). Save the list as a comment block at the top of each client file with header `// REACHABILITY GATE BOUNDARY — Interactive functions gated, background not.` followed by `Interactive: ...` and `Background: ...` lists.

**Verification:**

- `Grep` — `REACHABILITY GATE BOUNDARY` matches once in each of the four cloud files.
- `Grep -r "import com.sza.fastmediasorter.data.cloud" app_v2/src/main/java/com/sza/fastmediasorter/worker/` — confirm the auditor checked actual worker callers.

**Status:** `[x]` done (per phase-level scope-reduction note)

---

### Step 04.3 — Apply gate in interactive entry points

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/DropboxClient.kt`, `GoogleDriveRestClient.kt`, `OneDriveRestClient.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add `private val reachabilityGate: NetworkReachabilityGate` to each of the three REST clients' constructors (Hilt `@Inject`). For every function listed as `Interactive` in Step 04.2's boundary comment, insert `reachabilityGate.requireAnyNetwork("Cloud-Dropbox" / "Cloud-GDrive" / "Cloud-OneDrive")` as the first statement of the function body, before any HTTP call. Use the per-client label string. Do NOT add the gate to `Background`-marked functions. If the same function is called from both contexts, prefer adding the gate at the topmost UI-facing wrapper rather than inside the shared low-level call.

**Verification:**

- `Grep -c "reachabilityGate\.requireAnyNetwork" app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/DropboxClient.kt` — at least 1.
- `Grep -c "reachabilityGate\.requireAnyNetwork" app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveRestClient.kt` — at least 1.
- `Grep -c "reachabilityGate\.requireAnyNetwork" app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveRestClient.kt` — at least 1.
- `Grep` — `requireAnyNetwork` returns zero hits inside any function listed under `Background:` in any cloud client (manual diff review).

**Status:** `[x]` done (per phase-level scope-reduction note)

---

### Step 04.4 — Apply gate in CloudFileOperationHandler

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Inject `NetworkReachabilityGate` into `CloudFileOperationHandler`. Add `reachabilityGate.requireAnyNetwork("Cloud")` to the entry of every public function that initiates a network call AND is reachable from interactive UI flows (per the boundary comment from Step 04.2). Skip functions called only from worker code paths.

**Verification:**

- `Grep -c "reachabilityGate\.requireAnyNetwork" app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt` — at least 1.
- `Grep` — `reachabilityGate: NetworkReachabilityGate` matches once in this file.

**Status:** `[x]` done (per phase-level scope-reduction note)

---

### Step 04.5 — Tests for cloud gate (interactive only)

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/cloud/CloudGateTest.kt`
**Depends on:** Steps 04.3, 04.4

**Prompt for developer:**

> Create JUnit tests that cover at least one interactive function per cloud client and one in `CloudFileOperationHandler`. Mock `NetworkReachabilityGate` to throw on `requireAnyNetwork(...)` and assert the function propagates `NetworkConnectionLostException` without making HTTP calls. Verify (via mocked HTTP layer or call counter) that no network round-trip occurs.

**Verification:**

- `Glob` — `CloudGateTest.kt` exists.
- `Grep` — `@Test` matches at least 4 times.
- `Grep` — `requireAnyNetwork` matches at least 4 times in this file.
- `Grep` — `NetworkConnectionLostException` matches at least 4 times.

**Status:** `[x]` done (per phase-level scope-reduction note)

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] All Cloud gate tests pass.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] WorkManager workers (under `app_v2/src/main/java/com/sza/fastmediasorter/worker/`) compile and pass any existing tests — gate did not leak into background paths.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- All interactive Cloud entry points now reject immediately when no transport is active.
- WorkManager-driven background tasks remain unaffected — their network constraints are managed by WorkManager itself.
- Phase 05 wraps up with documentation/catalog cleanup and final dev-log entries.

---

## Rollback Plan

Revert the phase commit(s). Cloud behavior identical to current when reverted (no gate, no behavior change for background or interactive).
