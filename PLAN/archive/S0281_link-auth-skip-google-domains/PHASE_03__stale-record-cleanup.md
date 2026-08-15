# Phase 03 - Stale record cleanup for google-OAuth-only hosts

**Strategic spec:** [`../S0281_link-auth-skip-google-domains.md`](../S0281_link-auth-skip-google-domains.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Skipped
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** - / 3 (skipped)
**Started:** 2026-05-21
**Completed:** 2026-05-21
**Skip reason:** Phase 01 Decision Q3 = B. The encrypted cookie store rejects empty cookie lists at the API boundary (`AuthSessionRepositoryImpl.kt:47` and `:64`), so no `TYPE_ACTIVE` empty record can exist for google-OAuth-only hosts. `TYPE_DISMISSED` records (if any) are harmless because the Phase 02 short-circuit fires before any dismissal check. No cleanup necessary.

---

## Objective

If Phase 01 Decision Q3 = A, add a one-shot idempotent cleanup that removes empty `AuthAccountDomain` records for hosts matching `GoogleDomainMatcher.isGoogleAuthHost` on first launch of the updated version. If Decision Q3 = B, this phase is ⏭️ Skipped with the reason recorded in INDEX.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (Phase 01).
- [ ] Strategic §6 research items blocking this phase are Resolved (Q3 from Phase 01).
- [ ] Working tree is clean or on a feature branch.
- [ ] Phase 01 Decision Log contains explicit `Decision Q3: A | B`.
- [ ] If Decision Q3 = B: skip to Phase 04 / Phase 05; mark this phase ⏭️ Skipped in INDEX with reason "Decision Q3 = B: no stale records by storage construction".

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt` | Modified | ≤ 350 |
| `<one-shot trigger site - identified in Step 03.1>` | Modified | ≤ 30 lines delta |

The exact "one-shot trigger site" depends on the storage backend Phase 01 identified - typically `MainApplication.onCreate` or a `@HiltAndroidApp`-level initializer. Lock the concrete path in Step 03.1 before editing.

---

## Steps

### Step 03.1 - Lock the one-shot trigger site

**Files:** none (read-only inspection)
**Depends on:** - start of phase

**Prompt for developer:**

> Read the storage shape findings from Phase 01 Decision Log §Q3 inventory. Identify where the application bootstrap runs - inspect `app_v2/src/main/java/com/sza/fastmediasorter/MainApplication.kt` (or whichever class carries `@HiltAndroidApp`) and any existing one-shot migration / cleanup pattern in the codebase. Pick the site that fires once per process start and has access to `AuthSessionRepository` via Hilt (typically a coroutine launched in `Application.onCreate` on `ApplicationScope`, or an existing one-shot initializer). Append the concrete fully-qualified file path + method name to this phase file under a `### Trigger site lock` sub-heading.

**Verification:**

- `Grep` - this phase file contains `### Trigger site lock` followed by a line matching `^File: app_v2/src/main/java/com/sza/fastmediasorter/[^ ]+\.kt$`.
- `Grep` - the same block contains a line matching `^Function: [a-zA-Z][a-zA-Z0-9]*$`.

**Status:** `[ ]` not done

---

### Step 03.2 - Add `cleanupEmptyGoogleOAuthAccounts` to `AuthSessionRepository`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a new suspend function `suspend fun cleanupEmptyGoogleOAuthAccounts(): Int` to the domain interface `AuthSessionRepository`. Return value: count of records removed (for telemetry / log). In the implementation `AuthSessionRepositoryImpl`, iterate all stored hosts (using whichever query Phase 01 §Q3 identified as available - if no all-hosts query exists, add a private helper that lists the keys directly from the underlying store), filter to those for which `GoogleDomainMatcher.isGoogleAuthHost` returns true on the parsed host, then filter to records whose cookie count is 0 (per the `cookieCount` field already exposed in `AuthAccountDomain` - verified during Step 01.2). Delete those records via the existing primitive used by `markDismissed` / record-write path. The function must be idempotent: calling it twice returns 0 on the second call. Wrap the work in a single transaction or store-level atomic operation. Insert a Timber line `Timber.d("S0281: AuthSessionRepositoryImpl.cleanupEmptyGoogleOAuthAccounts removed=$removed")` at the end of the function body.

**Verification:**

- `Grep` - `AuthSessionRepository.kt` contains exactly one `suspend fun cleanupEmptyGoogleOAuthAccounts(): Int`.
- `Grep` - `AuthSessionRepositoryImpl.kt` contains exactly one `override suspend fun cleanupEmptyGoogleOAuthAccounts(): Int`.
- `Grep` - `AuthSessionRepositoryImpl.kt` contains the literal `Timber.d("S0281: AuthSessionRepositoryImpl.cleanupEmptyGoogleOAuthAccounts removed=`.
- `Grep` - `AuthSessionRepositoryImpl.kt` contains exactly one call to `GoogleDomainMatcher.isGoogleAuthHost` (used inside the new filter).

**Status:** `[ ]` not done

---

### Step 03.3 - Wire the cleanup to the locked trigger site, gated by a one-shot prefs flag

**Files:** path from Step 03.1 + the same `AuthSessionRepositoryImpl.kt` (for the prefs flag check) OR a colocated initializer file
**Depends on:** Step 03.2

**Prompt for developer:**

> At the trigger site locked in Step 03.1, call `cleanupEmptyGoogleOAuthAccounts()` exactly once per device install of the S0281-shipping build. Guard the call with a boolean stored in the existing app-level SharedPreferences (or whichever key-value store the app already uses for one-shot migration flags - check `app_v2/src/main/java/com/sza/fastmediasorter/core/util/` for an existing pattern before introducing a new store). Key suggestion: `s0281_oauth_only_cleanup_done`. On first run, read the flag (default false), execute the cleanup, then set the flag to true. Subsequent runs read true and skip the call. Launch the work on a background coroutine (`Dispatchers.IO`) so it does not block startup. Insert a Timber line `Timber.d("S0281: <trigger site> cleanup ran=$ran removed=$removed")` at the end of the launched block, where `ran` is `true` only on first execution.

**Verification:**

- `Grep` - the trigger file contains the literal `s0281_oauth_only_cleanup_done`.
- `Grep` - the trigger file contains exactly one call to `cleanupEmptyGoogleOAuthAccounts()`.
- `Grep` - the trigger file contains the literal `Timber.d("S0281:` with `cleanup ran=`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done` OR phase is ⏭️ Skipped with documented reason.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (the new repository method changes the public interface).

---

## Handoff Notes to Next Phase

If executed, this phase guarantees the local auth store contains no empty records under google-OAuth-only hosts after the first launch of the updated version. The cleanup is one-shot, idempotent, and safely no-op on devices that never had such records. Phase 04 is independent of this work.

---

## Rollback Plan

Revert the commit(s) for this phase. The new repository method becomes orphaned but harmless; the one-shot prefs flag, if already written `true` on any device, simply means the cleanup will not re-run on rollback - acceptable because no destructive operation persists. If a regression is observed, restoring the prior `AuthSessionRepositoryImpl.kt` from git is sufficient; no data needs to be reconstructed.
