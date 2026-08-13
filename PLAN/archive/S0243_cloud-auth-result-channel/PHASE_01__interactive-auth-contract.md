# Phase 01 - Interactive Auth Contract Refactor

**Strategic spec:** [`../S0243_cloud-auth-result-channel.md`](../S0243_cloud-auth-result-channel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (compile gate deferred to Phase 02 by design)
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 5 / 5
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Replace the three result-return paths of `InteractiveCloudAuthenticator` (`processIntentResult`, `handleResume`, `consumeImmediateResult`) with a single push channel `results: SharedFlow<AuthResult>`. Migrate all three plugins (Google Drive, Dropbox, OneDrive) to emit through the channel. The orchestrator is untouched in this phase - it continues to compile against the old surface that is still present in step-by-step transition order.

---

## Prerequisites

- [ ] All four Pre-Implementation Blockers in [INDEX.md](INDEX.md) are ticked.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/InteractiveCloudAuthenticator.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthPlugin.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/DropboxAuthPlugin.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveAuthPlugin.kt` | Modified | ≤ 100 |

---

## Steps

### Step 01.1 - Rewrite the InteractiveCloudAuthenticator contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/InteractiveCloudAuthenticator.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the body of `InteractiveCloudAuthenticator` with a four-member contract: (1) `val provider: CloudProvider`; (2) `val results: SharedFlow<AuthResult>` - the single push channel; (3) `fun startInteractiveSignIn(activity: Activity)` - kept as-is, void; (4) `suspend fun onIntentResult(data: Intent?)` - lifecycle hook for Activity result forwarding, default empty body; (5) `suspend fun onResume()` - lifecycle hook for `Activity.onResume`, default empty body. Remove `processIntentResult(data: Intent?): AuthResult?` and `consumeImmediateResult(): AuthResult?` entirely. Add a KDoc paragraph on the new `results` property stating: "Exactly one terminal `AuthResult` per attempt. The orchestrator subscribes via `first()` under a per-attempt `Job`. This channel is not a substitute for `identityRepository.state` - that flow remains the ambient observable of 'who is signed in now' and is consumed by the Settings card; `results` is the one-shot completion signal of a single interactive attempt." Import `kotlinx.coroutines.flow.SharedFlow`.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/InteractiveCloudAuthenticator.kt` exists.
- `Grep -n "val results: SharedFlow<AuthResult>"` returns ≥ 1 hit in that file.
- `Grep -n "suspend fun onIntentResult"` returns 1 hit in that file.
- `Grep -n "suspend fun onResume"` returns 1 hit in that file.
- `Grep -n "fun consumeImmediateResult"` returns 0 hits in that file.
- `Grep -n "fun processIntentResult"` returns 0 hits in that file.
- `Grep -n "fun handleResume"` returns 0 hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 7/7 PASS. Files: data/cloud/InteractiveCloudAuthenticator.kt (50 → 54 LOC; results SharedFlow added, processIntentResult/handleResume/consumeImmediateResult removed; onIntentResult/onResume hooks return Unit). Dev log recorded.

---

### Step 01.2 - Migrate GoogleDriveAuthPlugin to the results channel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthPlugin.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Rename the private `_asyncResults` field to `_results` and its public alias `asyncResults` to `results` (the contract's required property). The field stays `MutableSharedFlow<AuthResult>(extraBufferCapacity = 1)` but make it `private` and expose `override val results: SharedFlow<AuthResult> = _results.asSharedFlow()`. Drop `consumeImmediateResult` and the `lastImmediateFailure` field - the immediate-failure path (the catch block in `startInteractiveSignIn`) already emits to the flow via `_results.tryEmit(err)`; the snapshot/clear logic is no longer needed. Remove the `override` of `processIntentResult` entirely (the contract method is gone). Rename `override suspend fun handleResume(): AuthResult? = null` to `override suspend fun onResume() { /* no-op: Credential Manager produces its result inside the launched coroutine */ }`. Also add `override suspend fun onIntentResult(data: Intent?) { /* no-op: Credential Manager does not use Activity result */ }`. Replace the existing KDoc paragraph on the class explaining the hot-fix with a one-sentence summary: "Emits the terminal result of a Credential Manager sign-in attempt through [results]." Keep `DRIVE_SIGN_IN_SCOPES` and the companion intact.

**Verification:**

- `Grep -n "override val results: SharedFlow<AuthResult>"` returns 1 hit in `GoogleDriveAuthPlugin.kt`.
- `Grep -n "asyncResults"` returns 0 hits in `GoogleDriveAuthPlugin.kt`.
- `Grep -n "lastImmediateFailure"` returns 0 hits in `GoogleDriveAuthPlugin.kt`.
- `Grep -n "consumeImmediateResult"` returns 0 hits in `GoogleDriveAuthPlugin.kt`.
- `Grep -n "processIntentResult"` returns 0 hits in `GoogleDriveAuthPlugin.kt`.
- `Grep -n "override suspend fun onResume"` returns 1 hit in `GoogleDriveAuthPlugin.kt`.
- `Grep -n "override suspend fun onIntentResult"` returns 1 hit in `GoogleDriveAuthPlugin.kt`.
- `Grep -n "_results.tryEmit"` returns ≥ 3 hits in `GoogleDriveAuthPlugin.kt` (Success / Cancelled / Failed branches plus catch).

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 8/8 PASS (_results.tryEmit = 4 hits). Files: data/cloud/GoogleDriveAuthPlugin.kt (120 → 94 LOC; consumeImmediateResult + lastImmediateFailure + processIntentResult removed; results SharedFlow exposed; onResume/onIntentResult no-op). Dev log recorded.

---

### Step 01.3 - Migrate DropboxAuthPlugin to the results channel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/DropboxAuthPlugin.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a private `MutableSharedFlow<AuthResult>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)` field named `_results`, expose `override val results: SharedFlow<AuthResult> = _results.asSharedFlow()`. Remove `override suspend fun processIntentResult(data: Intent?): AuthResult? = null` entirely (contract method is gone). Replace `override suspend fun handleResume(): AuthResult? { .. return result/error .. }` with `override suspend fun onResume() { .. _results.tryEmit(result/error) .. }`: keep the `isAuthInProgress` gate, keep the `try/catch` around `client.finishAuthentication()`, keep the existing Timber lines verbatim, but instead of returning the `AuthResult` emit it to `_results`. Add `override suspend fun onIntentResult(data: Intent?) { /* no-op: Dropbox does not use Activity result */ }`. Imports to add: `kotlinx.coroutines.channels.BufferOverflow`, `kotlinx.coroutines.flow.MutableSharedFlow`, `kotlinx.coroutines.flow.SharedFlow`, `kotlinx.coroutines.flow.asSharedFlow`. Keep `client` field and `startInteractiveSignIn` body untouched.

**Verification:**

- `Grep -n "override val results: SharedFlow<AuthResult>"` returns 1 hit in `DropboxAuthPlugin.kt`.
- `Grep -n "_results.tryEmit"` returns ≥ 2 hits in `DropboxAuthPlugin.kt` (success + error branches).
- `Grep -n "override suspend fun onResume"` returns 1 hit in `DropboxAuthPlugin.kt`.
- `Grep -n "override suspend fun onIntentResult"` returns 1 hit in `DropboxAuthPlugin.kt`.
- `Grep -n "fun handleResume"` returns 0 hits in `DropboxAuthPlugin.kt`.
- `Grep -n "fun processIntentResult"` returns 0 hits in `DropboxAuthPlugin.kt`.
- `Grep -n "BufferOverflow"` returns ≥ 1 hit in `DropboxAuthPlugin.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 7/7 PASS (_results.tryEmit = 2, BufferOverflow = 2 - import + usage). Files: data/cloud/DropboxAuthPlugin.kt (56 → 65 LOC; results SharedFlow added with extraBufferCapacity=1, DROP_OLDEST; handleResume → onResume now emits to channel; processIntentResult dropped). Dev log recorded.

---

### Step 01.4 - Migrate OneDriveAuthPlugin to the results channel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveAuthPlugin.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace the `CompletableDeferred` / `consumeImmediateResult` / 3-minute `withTimeoutOrNull` machinery with a `MutableSharedFlow<AuthResult>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)` field named `_results`. Expose `override val results: SharedFlow<AuthResult> = _results.asSharedFlow()`. Inside `startInteractiveSignIn`: drop `authDeferred = CompletableDeferred()`; inside the `client.signIn(activity) { result -> .. }` callback, replace `authDeferred?.complete(result)` with `if (result != null) _results.tryEmit(result)` (keep the existing Timber line). Remove `override suspend fun processIntentResult` (contract method is gone). Replace `override suspend fun handleResume()` with `override suspend fun onResume() { /* no-op: MSAL callback emits the result directly */ }`. Add `override suspend fun onIntentResult(data: Intent?) { /* no-op: MSAL routes Activity result internally */ }`. Drop the `authDeferred` field, `@OptIn(ExperimentalCoroutinesApi::class)` annotations, and the `withTimeoutOrNull` / `kotlinx.coroutines.CompletableDeferred` / `kotlinx.coroutines.ExperimentalCoroutinesApi` / `kotlinx.coroutines.withTimeoutOrNull` imports. Add imports: `kotlinx.coroutines.channels.BufferOverflow`, `kotlinx.coroutines.flow.MutableSharedFlow`, `kotlinx.coroutines.flow.SharedFlow`, `kotlinx.coroutines.flow.asSharedFlow`. Keep `client` field, `provider`, and the existing `Timber.d("OneDriveAuthPlugin: starting interactive sign-in")` line.

**Verification:**

- `Grep -n "override val results: SharedFlow<AuthResult>"` returns 1 hit in `OneDriveAuthPlugin.kt`.
- `Grep -n "_results.tryEmit"` returns 1 hit in `OneDriveAuthPlugin.kt`.
- `Grep -n "CompletableDeferred"` returns 0 hits in `OneDriveAuthPlugin.kt`.
- `Grep -n "withTimeoutOrNull"` returns 0 hits in `OneDriveAuthPlugin.kt`.
- `Grep -n "consumeImmediateResult"` returns 0 hits in `OneDriveAuthPlugin.kt`.
- `Grep -n "fun handleResume"` returns 0 hits in `OneDriveAuthPlugin.kt`.
- `Grep -n "fun processIntentResult"` returns 0 hits in `OneDriveAuthPlugin.kt`.
- `Grep -n "override suspend fun onResume"` returns 1 hit in `OneDriveAuthPlugin.kt`.
- `Grep -n "override suspend fun onIntentResult"` returns 1 hit in `OneDriveAuthPlugin.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 9/9 PASS. Files: data/cloud/OneDriveAuthPlugin.kt (83 → 40 LOC; CompletableDeferred + withTimeoutOrNull(180s) + consumeImmediateResult removed; MSAL callback emits directly to _results SharedFlow). Dev log recorded.

---

### Step 01.5 - Compile-fence the contract change

**Files:** (no edits - validation only)
**Depends on:** Steps 01.1 - 01.4

**Prompt for developer:**

> Across the whole `app_v2/src` source tree, verify that no caller outside `data/cloud/` still references the removed contract members. The orchestrator (`UnifiedCloudAuthManager.kt`) WILL still reference them at this point - that is expected; it is rewritten in Phase 02. The purpose of this step is to confirm the blast radius is exactly `UnifiedCloudAuthManager.kt` and nothing else. Do not modify any file in this step - only run the grep predicates below.

**Verification:**

- `Grep -n "processIntentResult"` across `app_v2/src/main/java/` returns hits ONLY in `data/cloud/UnifiedCloudAuthManager.kt`. The `InteractiveCloudAuthenticator.kt` file and the three plugin files must have zero hits. Any other file with a hit → FAIL.
- `Grep -n "consumeImmediateResult"` across `app_v2/src/main/java/` returns hits ONLY in `data/cloud/UnifiedCloudAuthManager.kt`. Any other file → FAIL.
- `Grep -n "asyncResults"` across `app_v2/src/main/java/` returns hits ONLY in `data/cloud/UnifiedCloudAuthManager.kt` (dangling references to the removed `GoogleDriveAuthPlugin.asyncResults` - the orchestrator block that uses them is rewritten in Phase 02.2). Any other file → FAIL.
- `Grep -n "\.handleResume\(\)"` across `app_v2/src/main/java/` returns hits ONLY in `data/cloud/UnifiedCloudAuthManager.kt`, `ui/addresource/AddResourceConnectionManager.kt`, and `ui/addresource/AddResourceActivity.kt` (Activity → ConnectionManager → orchestrator chain - all three calls survive the refactor; the orchestrator's own method body changes in Phase 02.3 but the public signature and external callers stay). Any other file → FAIL.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 4/4 PASS. Blast radius confirmed: processIntentResult+consumeImmediateResult+asyncResults all isolated to UnifiedCloudAuthManager.kt (cleaned by Phase 02.2/02.3); .handleResume() chain Activity → ConnectionManager → orchestrator preserved. No source edits in this step. Spec predicate for asyncResults / handleResume corrected mid-step to match Phase 02 boundary intent.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `.\build-debug.PS1` (PowerShell). Phase 01 leaves `UnifiedCloudAuthManager.kt` referencing now-removed plugin members (`asyncResults`, `processIntentResult`, `consumeImmediateResult`); compilation is **expected to FAIL** at this phase boundary because the orchestrator is rewritten in Phase 02. Build check moves to the end of Phase 02. Tick this checkbox after Phase 02's build passes - record "deferred to Phase 02 - compilation green there" in the Step Log of Step 01.5.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- All three plugins now expose `results: SharedFlow<AuthResult>` as their only success path. The orchestrator in Phase 02 must subscribe to `plugin.results` via `first()` under a per-attempt `Job` on `appScope`, with `INTERACTIVE_AUTH_TIMEOUT_MS = 300_000L` (5 min) bounding the wait.
- `UnifiedCloudAuthManager.processIntentResult(data: Intent?)` has no in-app callers (verified by grep) - Phase 02 can delete it.
- `UnifiedCloudAuthManager.handleResume()` IS called by `AddResourceConnectionManager.handleResume()`; preserve the method signature and forward to `activePlugin?.onResume()`.

---

## Rollback Plan

Revert this phase's commit(s). No persistent storage, no migration, no manifest changes - source-only edits inside `data/cloud/`.
