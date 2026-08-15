# Phase 02 - Orchestrator Unification

**Strategic spec:** [`../S0243_cloud-auth-result-channel.md`](../S0243_cloud-auth-result-channel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Rewrite `UnifiedCloudAuthManager` so it subscribes to each active plugin's `results: SharedFlow<AuthResult>` under a per-attempt `Job`, removes the `consumeImmediateResult` polling, the `GoogleDriveAuthPlugin` downcast, and the dead `processIntentResult` method, and routes the single result through `processPluginResult` with a single 5-minute timeout.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (or at minimum Steps 01.1 - 01.4 complete; Step 01.5 is a no-edit fence).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/UnifiedCloudAuthManager.kt` | Modified | ≤ 200 |

---

## Steps

### Step 02.1 - Add subscription-job lifecycle and timeout constant

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/UnifiedCloudAuthManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a private mutable field `private var subscriptionJob: Job? = null` directly below `private var activeProvider: CloudProvider? = null`. Add a `companion object { private const val INTERACTIVE_AUTH_TIMEOUT_MS = 300_000L }` at the bottom of the class (5 minutes; covers Credential Manager, MSAL broker, Dropbox browser round-trip with margin). Import `kotlinx.coroutines.Job` and `kotlinx.coroutines.withTimeoutOrNull`. Do not modify any method body in this step - this step only adds the new fields and the constant so subsequent steps can reference them.

**Verification:**

- `Grep -n "private var subscriptionJob: Job?"` returns 1 hit in `UnifiedCloudAuthManager.kt`.
- `Grep -n "INTERACTIVE_AUTH_TIMEOUT_MS = 300_000L"` returns 1 hit in `UnifiedCloudAuthManager.kt`.
- `Grep -n "import kotlinx.coroutines.Job"` returns 1 hit in `UnifiedCloudAuthManager.kt`.
- `Grep -n "import kotlinx.coroutines.withTimeoutOrNull"` returns 1 hit in `UnifiedCloudAuthManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 4/4 PASS. Files: data/cloud/UnifiedCloudAuthManager.kt (174 → 184 LOC; subscriptionJob field + companion with INTERACTIVE_AUTH_TIMEOUT_MS + Job/withTimeoutOrNull imports). Dev log recorded.

---

### Step 02.2 - Rewrite startInteractiveSignIn around the results channel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/UnifiedCloudAuthManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the body of `fun startInteractiveSignIn(activity: Activity, provider: CloudProvider)` with the following sequence: (1) `subscriptionJob?.cancel()` then `subscriptionJob = null`; (2) `StructuredLogger.d("Interactive cloud auth begin", "provider" to provider.name)`; (3) `activeProvider = provider`; (4) resolve `val plugin = plugins[provider]` - if `null`, call `handleFailedAuth(provider, "No auth plugin configured for ${provider.name}")` and `return`; (5) launch `subscriptionJob = appScope.launch(Dispatchers.Main) { val result = withTimeoutOrNull(INTERACTIVE_AUTH_TIMEOUT_MS) { plugin.results.first() }; if (activeProvider != provider) { StructuredLogger.i("Interactive cloud auth result dropped - provider changed", "provider" to provider.name); return@launch }; if (result == null) { StructuredLogger.w("Interactive cloud auth timeout", "provider" to provider.name); handleFailedAuth(provider, "Interactive sign-in timed out after ${INTERACTIVE_AUTH_TIMEOUT_MS / 1000} seconds"); return@launch }; StructuredLogger.d("Interactive cloud auth end", "provider" to provider.name, "outcome" to result::class.java.simpleName); processPluginResult(provider, result) }`; (6) wrap `plugin.startInteractiveSignIn(activity)` in the existing `try { .. } catch (e: Exception) { .. handleFailedAuth(provider, ..) .. }` block, but on `catch` also call `subscriptionJob?.cancel()` and `subscriptionJob = null` before invoking `handleFailedAuth`. Remove the entire `val immediateResult = plugin.consumeImmediateResult()` block, the entire `appScope.launch(Dispatchers.Main) { delay(1_000L); .. }` poll block, and the entire `if (plugin is GoogleDriveAuthPlugin) { appScope.launch.. plugin.asyncResults.first() .. }` downcast block. Drop the `import kotlinx.coroutines.delay` and `import kotlinx.coroutines.flow.first` imports if unused after the rewrite; re-add `import kotlinx.coroutines.flow.first` because the new subscription uses it.

**Verification:**

- `Grep -n "if (plugin is GoogleDriveAuthPlugin)"` returns 0 hits in `UnifiedCloudAuthManager.kt`.
- `Grep -n "asyncResults"` returns 0 hits in `UnifiedCloudAuthManager.kt`.
- `Grep -n "consumeImmediateResult"` returns 0 hits in `UnifiedCloudAuthManager.kt`.
- `Grep -n "delay(1_000L)"` returns 0 hits in `UnifiedCloudAuthManager.kt`.
- `Grep -n "withTimeoutOrNull(INTERACTIVE_AUTH_TIMEOUT_MS)"` returns 1 hit in `UnifiedCloudAuthManager.kt`.
- `Grep -n "plugin.results.first()"` returns 1 hit in `UnifiedCloudAuthManager.kt`.
- `Grep -n "subscriptionJob?.cancel()"` returns ≥ 1 hit in `UnifiedCloudAuthManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 7/7 PASS (subscriptionJob?.cancel() = 2 - prior-attempt cancel + catch-block cancel). Files: data/cloud/UnifiedCloudAuthManager.kt (poll loop + GoogleDriveAuthPlugin downcast removed; results.first() under per-attempt Job with INTERACTIVE_AUTH_TIMEOUT_MS withTimeoutOrNull). Dev log recorded.

---

### Step 02.3 - Drop processIntentResult, forward handleResume through the contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/UnifiedCloudAuthManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Delete the entire `suspend fun processIntentResult(data: Intent?) { .. }` method (it has zero callers in `app_v2/src/main/java/` - verified during Phase 01.5). Also remove the now-unused `import android.content.Intent` if no remaining reference exists. Rewrite `suspend fun handleResume() { .. }` to forward to the active plugin's new contract method `onResume()`: `val provider = activeProvider ?: return; val plugin = plugins[provider] ?: return; StructuredLogger.d("Interactive cloud auth onResume", "provider" to provider.name); plugin.onResume()`. The plugin will emit any result to its `results` flow internally, which the subscription job launched in Step 02.2 already observes - no extra wiring needed here. Inside `processPluginResult`, at the end of the `Success` and `Cancelled` branches and inside `handleFailedAuth`, add `subscriptionJob = null` directly after the existing `activeProvider = null` assignment to release the completed job reference.

**Verification:**

- `Grep -n "fun processIntentResult"` returns 0 hits in `UnifiedCloudAuthManager.kt`.
- `Grep -n "plugin.onResume()"` returns 1 hit in `UnifiedCloudAuthManager.kt`.
- `Grep -n "subscriptionJob = null"` returns ≥ 3 hits in `UnifiedCloudAuthManager.kt` (Success / Cancelled / handleFailedAuth).
- `Grep -n "fun handleResume"` returns 1 hit in `UnifiedCloudAuthManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 4/4 PASS (subscriptionJob = null = 5 hits: 2 startInteractiveSignIn + Success + Cancelled + handleFailedAuth). Files: data/cloud/UnifiedCloudAuthManager.kt (processIntentResult method dropped, handleResume forwards to plugin.onResume(), android.content.Intent import removed). Dev log recorded.

---

### Step 02.4 - Build verification

**Files:** (no edits)
**Depends on:** Steps 02.1 - 02.3

**Prompt for developer:**

> Run `.\build-debug.PS1` via PowerShell. The build must succeed (exit code 0). If it fails, capture the last 30 lines of the FAILURE block and stop - do not "fix" anything in this step; reopen the offending prior step.

**Verification:**

- `.\build-debug.PS1` returns exit code 0.
- `Grep -n "TODO(phase-02)"` across `app_v2/src/main/java/` returns 0 hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. `.\build-debug.PS1` BUILD SUCCESSFUL in 1m 11s, assembleStandardDebug exit 0; APK at app_v2/build/outputs/apk/standard/debug/FastMediaSorter_standard_debug_v2.60.5191.217-DEBUG.apk. No TODO(phase-02) markers.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - `.\build-debug.PS1` exit 0 (already gated by Step 02.4).
- [ ] `Grep -n "TODO(phase-02)"` returns 0 hits across `app_v2/src/main/java/`.
- [ ] Dev log entry added for `UnifiedCloudAuthManager.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase 01's Phase Done Criteria "Project compiles" checkbox is now retroactively tickable - update it.

---

## Handoff Notes to Next Phase

- The orchestrator no longer downcasts on any concrete plugin, no longer polls, and no longer references `processIntentResult` or `consumeImmediateResult`.
- A new attempt to sign in always cancels the prior subscription job - covers strategic §11 criterion 5 (no leak on rapid re-clicks).
- The single `INTERACTIVE_AUTH_TIMEOUT_MS = 300_000L` constant covers strategic §11 criterion 3 (no 1-second poll anywhere).
- `identityRepository.state` is untouched - the Settings card still observes the same flow.

---

## Rollback Plan

Revert this phase's commit(s). Phase 01 alone leaves the codebase non-compiling (orchestrator references removed plugin members); pair the revert with a Phase 01 revert.
